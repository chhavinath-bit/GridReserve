import type { Booking, BookingStatus } from '@/api/types'

/**
 * The status to show. A HELD booking whose hold has run out is expired, even if no request has
 * marked the row EXPIRED yet (the backend expires lazily until the job in M1.7).
 */
export function effectiveStatus(booking: Pick<Booking, 'status' | 'expiresAt'>, now: number): BookingStatus {
  if (booking.status === 'HELD' && booking.expiresAt && new Date(booking.expiresAt).getTime() <= now) {
    return 'EXPIRED'
  }
  return booking.status
}

/**
 * Whether the owner can still cancel (or release) the booking now. The server decides; this only keeps
 * the button from offering what would be refused with 422 CUTOFF_PASSED.
 */
export function canCancel(booking: Pick<Booking, 'status' | 'expiresAt' | 'cancellableUntil'>, now: number): boolean {
  const status = effectiveStatus(booking, now)
  if (status !== 'HELD' && status !== 'CONFIRMED') return false
  return booking.cancellableUntil !== null && Date.parse(booking.cancellableUntil) > now
}
