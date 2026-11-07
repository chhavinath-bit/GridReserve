import { describe, expect, it } from 'vitest'
import type { Booking } from '@/api/types'
import { tabOf } from '@/features/bookings/MyBookingsPage'
import { holdDeadline } from '@/features/bookings/hold'
import { effectiveStatus } from './bookingStatus'

const NOW = Date.parse('2026-11-07T10:00:00Z')

const booking = (overrides: Partial<Booking>): Booking => ({
  id: 1,
  bookableId: 5,
  bookableName: 'Court A',
  bookableLabel: 'Court',
  venueName: 'Sunrise Turf',
  venueTimeZone: 'Asia/Kolkata',
  start: '2026-11-08T04:30:00Z',
  end: '2026-11-08T05:30:00Z',
  status: 'CONFIRMED',
  expiresAt: null,
  createdAt: '2026-11-07T09:58:00Z',
  cancelledAt: null,
  cancelledBy: null,
  ...overrides,
})

describe('booking status shown to the member', () => {
  it('a HELD booking past its expiresAt shows as EXPIRED before the server marks it', () => {
    expect(effectiveStatus(booking({ status: 'HELD', expiresAt: '2026-11-07T10:03:00Z' }), NOW)).toBe('HELD')
    expect(effectiveStatus(booking({ status: 'HELD', expiresAt: '2026-11-07T09:59:00Z' }), NOW)).toBe('EXPIRED')
  })

  it('sorts bookings into Upcoming, Past and Cancelled & expired', () => {
    expect(tabOf(booking({}), NOW)).toBe('upcoming')
    expect(tabOf(booking({ start: '2026-11-06T04:30:00Z', end: '2026-11-06T05:30:00Z' }), NOW)).toBe('past')
    expect(tabOf(booking({ status: 'CANCELLED' }), NOW)).toBe('closed')
    expect(tabOf(booking({ status: 'HELD', expiresAt: '2026-11-07T09:59:00Z' }), NOW)).toBe('closed')
  })

  it('the hold deadline uses the hold’s length, so a wrong device clock doesn’t matter', () => {
    // Server: created 09:58:00, expires 10:03:00 (5 minutes). Device clock says 12:00:00 when the answer arrives.
    const hold = booking({ status: 'HELD', createdAt: '2026-11-07T09:58:00Z', expiresAt: '2026-11-07T10:03:00Z' })
    const arrived = Date.parse('2026-11-07T12:00:00Z')
    expect(holdDeadline(hold, arrived)).toBe(arrived + 5 * 60_000)
  })
})
