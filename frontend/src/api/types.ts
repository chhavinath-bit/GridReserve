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

export interface Bookable extends BookableSummary {
  venueName: string
  venueTimeZone: string
  rules: Rules
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
  venueTimeZone: string
  start: string
  end: string
  status: BookingStatus
  expiresAt: string | null
  createdAt: string
  cancelledAt: string | null
  cancelledBy: CancelledBy | null
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
