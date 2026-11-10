// Typed calls, one per backend endpoint used by the frontend. Paths are relative to /api/v1.
import { api } from './client'
import type { Availability, Bookable, BookableSummary, Booking, Category, Page, TokenResponse, User, Venue, VenueSummary } from './types'

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
  venues: (categoryId: number) => api<Page<VenueSummary>>(`/venues?categoryId=${categoryId}&size=100`),
  venue: (venueId: number) => api<Venue>(`/venues/${venueId}`),
  bookables: (venueId: number) => api<Page<BookableSummary>>(`/venues/${venueId}/bookables?size=100`),
  bookable: (bookableId: number) => api<Bookable>(`/bookables/${bookableId}`),
  availability: (bookableId: number, date: string) =>
    api<Availability>(`/bookables/${bookableId}/availability?date=${date}`),
}

export const bookingApi = {
  /** idempotencyKey: the same for a retry of the same intent (IDEM-01), see lib/idempotency.ts. */
  hold: (bookableId: number, start: string, end: string, idempotencyKey: string) =>
    api<Booking>('/bookings', { method: 'POST', body: { bookableId, start, end }, headers: { 'Idempotency-Key': idempotencyKey } }),
  confirm: (bookingId: number) => api<Booking>(`/bookings/${bookingId}/confirm`, { method: 'POST' }),
  cancel: (bookingId: number, reason?: string) =>
    api<Booking>(`/bookings/${bookingId}/cancel`, { method: 'POST', body: reason ? { reason } : undefined }),
  mine: (page = 0) => api<Page<Booking>>(`/bookings/me?page=${page}&size=50`),
}
