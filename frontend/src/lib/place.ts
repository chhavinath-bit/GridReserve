import type { VenueSummary } from '@/api/types'

// Where a venue is, in words (FR-CAT-16): "Hyderabad, India". Country names come from the browser
// (Intl.DisplayNames), so there is no country table to keep.

const countryNames = new Intl.DisplayNames(['en'], { type: 'region' })

export function countryName(code: string): string {
  return countryNames.of(code) ?? code
}

/** "Hyderabad, India". */
export function placeLabel(city: string, countryCode: string): string {
  return `${city}, ${countryName(countryCode)}`
}

/** The URL form of a place, "IN|Hyderabad", and back. */
export function placeKey(countryCode: string, city: string): string {
  return `${countryCode}|${city}`
}

export function parsePlace(key: string | null): { countryCode: string; city: string } | undefined {
  if (!key) return undefined
  const [countryCode, city] = key.split('|')
  return countryCode && city ? { countryCode, city } : undefined
}

/** Every city that has a venue, once, sorted by country and then city name. */
export function placesOf(venues: VenueSummary[]): { value: string; label: string }[] {
  const seen = new Map<string, string>()
  venues.forEach((venue) => seen.set(placeKey(venue.countryCode, venue.city), placeLabel(venue.city, venue.countryCode)))
  return [...seen].map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label))
}
