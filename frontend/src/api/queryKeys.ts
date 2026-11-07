// One place for TanStack Query keys (ARCHITECTURE §4), so invalidation after a mutation always
// hits the same keys the views read.
export const queryKeys = {
  me: ['me'] as const,
  categories: ['categories'] as const,
  venues: (categoryId: number) => ['venues', categoryId] as const,
  venue: (venueId: number) => ['venue', venueId] as const,
  bookables: (venueId: number) => ['bookables', venueId] as const,
  bookable: (bookableId: number) => ['bookable', bookableId] as const,
  availability: (bookableId: number, date: string) => ['availability', bookableId, date] as const,
  availabilityAll: ['availability'] as const,
  myBookings: ['bookings', 'me'] as const,
}
