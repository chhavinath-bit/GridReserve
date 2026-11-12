// Typed calls, one per backend endpoint used by the frontend. Paths are relative to /api/v1.
import { api } from './client'
import type {
  Activation, AdminBookingPage, AuditEntry, Availability, Bookable, BookableInput, BookableSummary, Booking, BookingStatus, Category,
  City, Country, Page, TokenResponse, User, Venue, VenueInput, VenueSummary,
} from './types'

export const authApi = {
  login: (email: string, password: string) =>
    api<TokenResponse>('/auth/login', { method: 'POST', body: { email, password }, authEndpoint: true }),
  register: (email: string, password: string, displayName: string) =>
    api<TokenResponse>('/auth/register', { method: 'POST', body: { email, password, displayName }, authEndpoint: true }),
  logout: () => api<void>('/auth/logout', { method: 'POST', authEndpoint: true }),
  me: () => api<User>('/me'),
}

export const catalogApi = {
  categories: () => api<Category[]>('/categories'),
  /** All venues, or one category's, optionally in one city (FR-CAT-16); admins may include closed ones. */
  venues: (categoryId?: number, includeInactive = false, cityId?: number) =>
    api<Page<VenueSummary>>(`/venues?${query({ categoryId, cityId, includeInactive: includeInactive || undefined, size: 100 })}`),
  venue: (venueId: number) => api<Venue>(`/venues/${venueId}`),
  bookables: (venueId: number, includeInactive = false) =>
    api<Page<BookableSummary>>(`/venues/${venueId}/bookables?${query({ includeInactive: includeInactive || undefined, size: 100 })}`),
  bookable: (bookableId: number) => api<Bookable>(`/bookables/${bookableId}`),
  availability: (bookableId: number, date: string) =>
    api<Availability>(`/bookables/${bookableId}/availability?date=${date}`),
  countries: () => api<Country[]>('/countries'),
  cities: (countryCode: string) => api<City[]>(`/countries/${countryCode}/cities`),
}

export const bookingApi = {
  /** idempotencyKey: the same for a retry of the same intent (IDEM-01), see lib/idempotency.ts. */
  hold: (bookableId: number, start: string, end: string, idempotencyKey: string) =>
    api<Booking>('/bookings', { method: 'POST', body: { bookableId, start, end }, headers: { 'Idempotency-Key': idempotencyKey } }),
  confirm: (bookingId: number) => api<Booking>(`/bookings/${bookingId}/confirm`, { method: 'POST' }),
  cancel: (bookingId: number, reason?: string) =>
    api<Booking>(`/bookings/${bookingId}/cancel`, { method: 'POST', body: reason ? { reason } : undefined }),
  /** One tab of My bookings (FR-BK-05): statuses, a start-time bound and the order; 20 per page. */
  mine: (filter: MyBookingsFilter, page = 0) =>
    api<Page<Booking>>(`/bookings/me?${query({ status: filter.statuses, from: filter.from, to: filter.to, order: filter.order, page, size: 20 })}`),
}

export interface MyBookingsFilter {
  statuses: BookingStatus[]
  from?: string
  to?: string
  order: 'asc' | 'desc'
}

export interface AdminBookingsFilter {
  venueId?: number
  status?: BookingStatus
  from?: string
  to?: string
}

/** Admin screens (FR-CAT-07..16, FR-ADM-01, FR-AUD-03). The server checks the ADMIN role on every call. */
export const adminApi = {
  createVenue: (input: VenueInput) => api<Venue>('/venues', { method: 'POST', body: input }),
  updateVenue: (id: number, input: VenueInput) => api<Venue>(`/venues/${id}`, { method: 'PUT', body: input }),
  setVenueActive: (id: number, active: boolean) =>
    api<Activation>(`/venues/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
  createBookable: (venueId: number, input: BookableInput) =>
    api<Bookable>(`/venues/${venueId}/bookables`, { method: 'POST', body: input }),
  updateBookable: (id: number, input: BookableInput) => api<Bookable>(`/bookables/${id}`, { method: 'PUT', body: input }),
  setBookableActive: (id: number, active: boolean) =>
    api<Activation>(`/bookables/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
  bookings: (filter: AdminBookingsFilter, page = 0) =>
    api<AdminBookingPage>(`/admin/bookings?${query({ ...filter, page, size: 20 })}`),
  audit: (bookingId: number) => api<AuditEntry[]>(`/admin/bookings/${bookingId}/audit`),
}

/** A query string from defined values; arrays repeat the key (?status=HELD&status=CONFIRMED). */
export function query(values: Record<string, string | number | boolean | string[] | undefined>): string {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value === undefined) return
    if (Array.isArray(value)) value.forEach((item) => params.append(key, item))
    else params.append(key, String(value))
  })
  return params.toString()
}
