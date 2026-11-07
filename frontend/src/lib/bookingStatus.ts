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
