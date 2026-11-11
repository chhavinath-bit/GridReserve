// One place for TanStack Query keys (ARCHITECTURE §4), so invalidation after a mutation always
// hits the same keys the views read.
export const queryKeys = {
  me: ['me'] as const,
  categories: ['categories'] as const,
  venues: (categoryId?: number, place?: string) => ['venues', categoryId ?? 'all', place ?? 'anywhere'] as const,
  venuesAll: ['venues'] as const,
  venue: (venueId: number) => ['venue', venueId] as const,
  bookables: (venueId: number) => ['bookables', venueId] as const,
  bookable: (bookableId: number) => ['bookable', bookableId] as const,
  availability: (bookableId: number, date: string) => ['availability', bookableId, date] as const,
  availabilityAll: ['availability'] as const,
  myBookings: ['bookings', 'me'] as const,
  myBookingsTab: (tab: string) => ['bookings', 'me', tab] as const,
  countries: ['countries'] as const,
  timeZones: (countryCode: string) => ['countries', countryCode, 'time-zones'] as const,
  admin: ['admin'] as const,
  adminVenues: ['admin', 'venues'] as const,
  adminVenue: (venueId: number) => ['admin', 'venue', venueId] as const,
  adminBookables: (venueId: number) => ['admin', 'bookables', venueId] as const,
  adminBookable: (bookableId: number) => ['admin', 'bookable', bookableId] as const,
  adminBookings: (filter: object, page: number) => ['admin', 'bookings', filter, page] as const,
  audit: (bookingId: number) => ['admin', 'audit', bookingId] as const,
}
