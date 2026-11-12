import type { Booking, BookingStatus } from '@/api/types'
import { formatDay, formatTime } from './time'

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

/**
 * How a closed booking ended, for its owner: who cancelled it and when, in the venue's zone (D-49). The reason,
 * if any, is shown separately. Null for a booking that is still open.
 */
export function closedNote(booking: Pick<Booking, 'status' | 'expiresAt' | 'cancelledAt' | 'cancelledBy' | 'venueTimeZone'>, now: number): string | null {
  const status = effectiveStatus(booking, now)
  if (status === 'EXPIRED') return 'Hold ran out before it was confirmed.'
  if (status !== 'CANCELLED') return null
  const who = booking.cancelledBy === 'ADMIN' ? 'Cancelled by the venue’s admin' : 'Cancelled by you'
  if (!booking.cancelledAt) return who
  const zone = booking.venueTimeZone
  return `${who} · ${formatDay(booking.cancelledAt, zone)}, ${formatTime(booking.cancelledAt, zone)}`
}
