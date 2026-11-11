// One user message per API error code (DESIGN_SYSTEM §6, REQUIREMENTS §15.3). Plain, specific,
// and says what to do next. Codes not listed fall back to the server's message.

const MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: 'Some details need fixing.',
  MALFORMED_REQUEST: 'Something went wrong sending that. Please try again.',
  UNAUTHENTICATED: 'Please log in to continue.',
  INVALID_CREDENTIALS: 'That email and password don’t match.',
  TOKEN_EXPIRED: 'Your session ended. Please log in again.',
  REFRESH_TOKEN_INVALID: 'Your session ended. Please log in again.',
  FORBIDDEN: 'You don’t have access to this.',
  NOT_FOUND: 'We couldn’t find that. It may have been removed.',
  EMAIL_TAKEN: 'An account with this email already exists.',
  SLOT_TAKEN: 'This slot was just taken. Pick another time.',
  MEMBER_OVERLAP: 'You already have a booking at this time. Pick another time.',
  HOLD_EXPIRED: 'Your hold ran out. Pick the slot again.',
  INVALID_STATE: 'This booking can’t be changed any more.',
  START_IN_PAST: 'That time has already started. Pick a later slot.',
  BEYOND_HORIZON: 'Choose a date within the booking window.',
  BOOKABLE_INACTIVE: 'This can’t be booked at the moment.',
  WRONG_BOOKING_MODEL: 'This can’t be booked by time slot yet.',
  OUTSIDE_OPERATING_HOURS: 'That time is outside opening hours.',
  OFF_GRID: 'Pick a start time from the slot list.',
  DURATION_INVALID: 'That length isn’t allowed here.',
  MEMBER_LIMIT_REACHED: 'You’ve reached the limit of active bookings here.',
  CUTOFF_PASSED: 'It’s too late to cancel this booking.',
  STALE_UPDATE: 'Someone else changed this in the meantime. Reload it and try again.',
  METHOD_NOT_ALLOWED: 'That action isn’t possible here.',
  IDEMPOTENCY_IN_PROGRESS: 'Your booking is still being processed. Please retry in a moment.',
  IDEMPOTENCY_KEY_REUSED: 'Something went wrong with this request. Please pick the slot again.',
  INTERNAL_ERROR: 'Something went wrong on our side. Please try again.',
  SERVICE_UNAVAILABLE: 'This is temporarily unavailable. Please retry in a moment.',
  UPSTREAM_TIMEOUT: 'This is temporarily unavailable. Please retry in a moment.',
  NETWORK_ERROR: 'We can’t reach GridReserve right now. Check your connection and retry.',
}

/**
 * Codes where the user should simply retry later, and keep what they selected (FR-UI-10). A hold retried
 * after one of these reuses its Idempotency-Key, so it can't book twice (IDEM-05: the first may still run).
 */
export const TEMPORARY_CODES = new Set(['SERVICE_UNAVAILABLE', 'UPSTREAM_TIMEOUT', 'NETWORK_ERROR', 'IDEMPOTENCY_IN_PROGRESS'])

/** Codes that mean "someone else got there first": show a notice and refresh availability (FR-UI-05). */
export const CONFLICT_CODES = new Set(['SLOT_TAKEN', 'MEMBER_OVERLAP'])

export function messageFor(code: string, serverMessage?: string): string {
  return MESSAGES[code] ?? serverMessage ?? MESSAGES.INTERNAL_ERROR!
}
