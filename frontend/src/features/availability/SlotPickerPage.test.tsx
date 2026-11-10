import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import type { Availability, Bookable, Booking } from '@/api/types'
import { SlotPickerPage } from './SlotPickerPage'

// The page against a fake API: the journey after a confirm must allow the next booking.
const ZONE = 'Asia/Kolkata'
const slot = (hourUtc: number) => ({
  start: `2099-01-05T${String(hourUtc).padStart(2, '0')}:30:00Z`,
  end: `2099-01-05T${String(hourUtc + 1).padStart(2, '0')}:30:00Z`,
  localStart: '',
  localEnd: '',
  status: 'FREE' as const,
})

const bookable: Bookable = {
  id: 7, venueId: 3, name: 'Visitor Bay 1', typeCode: 'BAY', label: 'Bay', bookingModel: 'EXCLUSIVE', active: true,
  venueName: 'Palm Apartments', venueTimeZone: ZONE,
  rules: { slotMinutes: 60, minDurationMinutes: 60, maxDurationMinutes: 720, bufferMinutes: 0, cancellationCutoffMinutes: 120, maxActiveBookingsPerMember: 3, bookingHorizonDays: 7 },
}
const availability: Availability = {
  bookableId: 7, date: '2099-01-05', venueTimeZone: ZONE, label: 'Bay', slotMinutes: 60, minDurationMinutes: 60,
  maxDurationMinutes: 720, bufferMinutes: 0, closed: false, slots: [slot(10), slot(11), slot(12), slot(13)],
}
const booking = (status: Booking['status'], at = slot(10)): Booking => ({
  id: 99, bookableId: 7, bookableName: 'Visitor Bay 1', bookableLabel: 'Bay', venueName: 'Palm Apartments', venueTimeZone: ZONE,
  start: at.start, end: at.end, status, expiresAt: status === 'HELD' ? '2099-01-05T00:05:00Z' : null,
  createdAt: '2099-01-05T00:00:00Z', cancelledAt: null, cancelledBy: null,
  cancellableUntil: status === 'HELD' ? '2099-01-05T00:05:00Z' : '2099-01-05T08:30:00Z',
})

const hold = vi.fn()
const confirm = vi.fn()
vi.mock('@/api/endpoints', () => ({
  catalogApi: { bookable: () => Promise.resolve(bookable), availability: () => Promise.resolve(availability) },
  bookingApi: { hold: (...args: unknown[]) => hold(...args), confirm: (...args: unknown[]) => confirm(...args), cancel: vi.fn() },
}))
vi.mock('sonner', () => ({ toast: Object.assign(vi.fn(), { success: vi.fn() }) }))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/bookables/7?date=2099-01-05']}>
        <Routes>
          <Route path="/bookables/:bookableId" element={<SlotPickerPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

// The Idempotency-Key sent with the n-th hold (0-based).
const keyOfHold = (n: number): unknown => hold.mock.calls[n]?.[3]

beforeEach(() => {
  hold.mockReset()
  confirm.mockReset()
})

describe('SlotPickerPage', () => {
  it('after a booking is confirmed, picking another slot brings back "Hold slot" and sends the next hold', async () => {
    hold.mockResolvedValueOnce(booking('HELD'))
    confirm.mockResolvedValueOnce(booking('CONFIRMED'))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '16:00, free' }))
    await user.click(screen.getByRole('button', { name: 'Hold slot' }))
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }))
    expect(await screen.findByText('Booked. See you there.')).toBeInTheDocument()
    // A confirmed booking is no longer "held by you".
    expect(screen.queryByRole('button', { name: /held by you/ })).not.toBeInTheDocument()

    hold.mockResolvedValueOnce(booking('HELD', slot(12)))
    await user.click(screen.getByRole('button', { name: '18:00, free' }))
    await user.click(await screen.findByRole('button', { name: 'Hold slot' }))
    expect(hold).toHaveBeenLastCalledWith(7, slot(12).start, slot(12).end, expect.any(String))
    // A new booking intent gets a new Idempotency-Key.
    expect(keyOfHold(1)).not.toBe(keyOfHold(0))
  })

  it('IDEM-01: Retry after a temporary failure sends the same Idempotency-Key, so the server can\'t book twice', async () => {
    hold.mockRejectedValueOnce(new ApiError(503, 'SERVICE_UNAVAILABLE', 'This is temporarily unavailable.'))
    hold.mockResolvedValueOnce(booking('HELD'))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '16:00, free' }))
    await user.click(screen.getByRole('button', { name: 'Hold slot' }))
    await user.click(await screen.findByRole('button', { name: 'Retry' }))

    expect(await screen.findByRole('button', { name: 'Confirm booking' })).toBeInTheDocument()
    expect(hold).toHaveBeenCalledTimes(2)
    expect(keyOfHold(0)).toMatch(/^[0-9a-f-]{36}$/)
    expect(keyOfHold(1)).toBe(keyOfHold(0))
  })

  it('while a hold is live, picking another slot explains what to do instead of doing nothing', async () => {
    hold.mockResolvedValueOnce(booking('HELD'))
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '16:00, free' }))
    await user.click(screen.getByRole('button', { name: 'Hold slot' }))
    await screen.findByRole('button', { name: 'Confirm booking' })
    expect(screen.getByRole('button', { name: '16:00, held by you' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '18:00, free' }))
    expect(screen.getByText('Confirm or release your current hold first.')).toBeInTheDocument()
  })
})
