// TypeScript mirrors of the backend DTOs (CLAUDE.md §7.4). Instants are ISO-8601 UTC strings
// ("2026-11-08T04:30:00Z"); local date-times have no zone ("2026-11-08T10:00:00").

export type Role = 'MEMBER' | 'ADMIN'
export type BookingModel = 'EXCLUSIVE' | 'CAPACITY'
export type SlotStatus = 'FREE' | 'TAKEN' | 'PAST'
export type BookingStatus = 'HELD' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED'
export type CancelledBy = 'MEMBER' | 'ADMIN' | 'SYSTEM'

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

export interface User {
  id: number
  email: string
  displayName: string
  role: Role
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface BookableType {
  id: number
  code: string
  label: string
  bookingModel: BookingModel
  slotMinutes: number | null
  minDurationMinutes: number | null
  maxDurationMinutes: number | null
  bufferMinutes: number
}

export interface Category {
  id: number
  code: string
  name: string
  cancellationCutoffMinutes: number
  maxActiveBookingsPerMember: number
  bookingHorizonDays: number
  bookableTypes: BookableType[]
}

export interface VenueSummary {
  id: number
  categoryId: number
  name: string
  addressLine: string
  /** Since M1.13b (FR-CAT-16, D-48): one of the listed cities; the Location filter uses its ID. */
  cityId: number
  /** The city's name: the Location filter and "where am I booking" show it. */
  city: string
  countryCode: string
  timeZone: string
  active: boolean
}

export interface OpeningHours {
  weekday: 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'
  opensAt: string
  closesAt: string
}

export interface Venue extends VenueSummary {
  /** Sent back in an admin's update; a stale one answers 409 STALE_UPDATE (FR-CAT-15). */
  version: number
  openingHours: OpeningHours[]
}

export interface BookableSummary {
  id: number
  venueId: number
  name: string
  typeCode: string
  label: string
  bookingModel: BookingModel
  active: boolean
}

export interface Rules {
  slotMinutes: number | null
  minDurationMinutes: number | null
  maxDurationMinutes: number | null
  bufferMinutes: number
  cancellationCutoffMinutes: number
  maxActiveBookingsPerMember: number
  bookingHorizonDays: number
}

/** A bookable's own rule values; null = the type's default (FR-CAT-04). */
export interface RuleOverrides {
  slotMinutes: number | null
  minDurationMinutes: number | null
  maxDurationMinutes: number | null
  bufferMinutes: number | null
}

export interface Bookable extends BookableSummary {
  venueName: string
  venueCity: string
  venueCountryCode: string
  venueTimeZone: string
  version: number
  rules: Rules
  overrides: RuleOverrides
  openingHours: OpeningHours[]
  ownOpeningHours: boolean
}

export interface Slot {
  start: string
  end: string
  localStart: string
  localEnd: string
  status: SlotStatus
}

export interface Availability {
  bookableId: number
  date: string
  venueTimeZone: string
  label: string
  slotMinutes: number
  minDurationMinutes: number
  maxDurationMinutes: number
  bufferMinutes: number
  closed: boolean
  slots: Slot[]
}

export interface Booking {
  id: number
  bookableId: number
  bookableName: string
  bookableLabel: string
  venueName: string
  venueCity: string
  venueCountryCode: string
  venueTimeZone: string
  start: string
  end: string
  status: BookingStatus
  expiresAt: string | null
  createdAt: string
  cancelledAt: string | null
  cancelledBy: CancelledBy | null
  /** What the member or an admin wrote when cancelling; shown to the member (D-49). */
  cancelReason: string | null
  /** Last moment the owner can cancel (CXL-01/02); null once cancelling is no longer possible. */
  cancellableUntil: string | null
}

export interface FieldError {
  field: string
  message: string
}

export interface ErrorBody {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  requestId: string
  fieldErrors?: FieldError[]
}

// ---- Admin (M1.11, M1.12) ----

export interface Country {
  code: string
}

/** A city an admin can put a venue in; the venue takes its country and time zone (D-48). */
export interface City {
  id: number
  name: string
  countryCode: string
  timeZone: string
}

export interface VenueInput {
  categoryId?: number
  version?: number
  name: string
  addressLine: string | null
  cityId: number
  openingHours: OpeningHours[]
}

export interface BookableInput extends Partial<Record<keyof RuleOverrides, number | null>> {
  version?: number
  bookableTypeId: number
  name: string
  openingHours: OpeningHours[] | null
}

export interface Activation {
  id: number
  active: boolean
  version: number
  activeFutureBookings: number
}

export interface AdminBooking {
  booking: Booking
  memberId: number
  memberEmail: string | null
  memberDisplayName: string | null
}

export interface AdminBookingPage extends Page<AdminBooking> {
  /** false: identity-service couldn't be reached, so names are missing (FS-33). */
  memberDetailsAvailable: boolean
}

export type ActorType = 'MEMBER' | 'ADMIN' | 'SYSTEM'

export interface AuditEntry {
  fromStatus: BookingStatus | null
  toStatus: BookingStatus
  actorType: ActorType
  actorUserId: number | null
  reason: string | null
  occurredAt: string
}
