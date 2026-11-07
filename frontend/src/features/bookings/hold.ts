import type { Booking } from '@/api/types'

/**
 * When a hold ends on this device's clock. The server says expiresAt in its own time; the device clock
 * may be minutes off. The hold's length (expiresAt − createdAt, both server times) doesn't depend on
 * any clock, so: deadline = moment the answer arrived + that length. The server is still the judge:
 * a late confirm gets 409 HOLD_EXPIRED whatever this timer shows.
 */
export function holdDeadline(booking: Pick<Booking, 'expiresAt' | 'createdAt'>, receivedAt: number = Date.now()): number {
  if (!booking.expiresAt) return receivedAt
  return receivedAt + (new Date(booking.expiresAt).getTime() - new Date(booking.createdAt).getTime())
}
