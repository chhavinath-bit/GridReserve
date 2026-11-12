import type { FormikHelpers } from 'formik'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { initials } from '@/components/patterns/AppShell'
import { filterFor } from '@/features/bookings/MyBookingsPage'
import { filterFromParams } from './AdminBookingsPage'
import { applyServerError } from './serverErrors'

function helpers() {
  return { setErrors: vi.fn(), setStatus: vi.fn() } as unknown as FormikHelpers<unknown> & { setErrors: ReturnType<typeof vi.fn>; setStatus: ReturnType<typeof vi.fn> }
}

describe('server errors on admin forms (FR-CAT-09, FR-CAT-15)', () => {
  it('puts field errors under their fields and every opening-hours error under "hours"', () => {
    const form = helpers()
    const error = new ApiError(400, 'VALIDATION_FAILED', 'Some details need fixing.', [
      { field: 'cityId', message: 'No such city.' },
      { field: 'openingHours[2]', message: 'Must open before it closes.' },
    ])
    applyServerError(error, form, ['cityId', 'hours'], (field) => (field.startsWith('openingHours') ? 'hours' : field))
    expect(form.setErrors).toHaveBeenCalledWith({ cityId: 'No such city.', hours: 'Must open before it closes.' })
    expect(form.setStatus).not.toHaveBeenCalled()
  })

  it('shows a refusal without fields, such as STALE_UPDATE, above the form with its code', () => {
    const form = helpers()
    applyServerError(new ApiError(409, 'STALE_UPDATE', 'Someone else changed this in the meantime.'), form, ['name'])
    expect(form.setStatus).toHaveBeenCalledWith({ message: 'Someone else changed this in the meantime.', code: 'STALE_UPDATE' })
  })
})

describe('list filters', () => {
  it('asks the server for each My bookings tab (FR-BK-05)', () => {
    const now = '2026-11-11T10:00:00Z'
    expect(filterFor('upcoming', now)).toEqual({ statuses: ['HELD', 'CONFIRMED'], from: now, order: 'asc' })
    expect(filterFor('past', now)).toEqual({ statuses: ['CONFIRMED'], to: now, order: 'desc' })
    expect(filterFor('closed', now)).toEqual({ statuses: ['CANCELLED', 'EXPIRED'], order: 'desc' })
  })

  it('turns the admin list URL into API filters; "until" includes its whole day (FR-ADM-01)', () => {
    const filter = filterFromParams(new URLSearchParams('venue=6&status=CONFIRMED&from=2026-11-01&to=2026-11-30'))
    expect(filter).toEqual({ venueId: 6, status: 'CONFIRMED', from: '2026-11-01T00:00:00Z', to: '2026-12-01T00:00:00Z' })
    expect(filterFromParams(new URLSearchParams('status=NOPE'))).toEqual({ venueId: undefined, status: undefined, from: undefined, to: undefined })
  })
})

describe('account initials', () => {
  it('uses the first and last name', () => {
    expect(initials('Alice Demo')).toBe('AD')
    expect(initials('  bob ')).toBe('B')
    expect(initials('Mary Ann Lee')).toBe('ML')
  })
})
