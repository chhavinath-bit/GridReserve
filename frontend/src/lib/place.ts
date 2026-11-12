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

/** The city ID in the URL (?place=12); anything else (also the old "IN|Hyderabad" form) means anywhere. */
export function parseCityId(key: string | null): number | undefined {
  const id = Number(key)
  return key && Number.isInteger(id) && id > 0 ? id : undefined
}

/** Every city that has a venue, once, keyed by its ID (D-48) and sorted by "City, Country". */
export function placesOf(venues: VenueSummary[]): { value: string; label: string }[] {
  const seen = new Map<string, string>()
  venues.forEach((venue) => seen.set(String(venue.cityId), placeLabel(venue.city, venue.countryCode)))
  return [...seen].map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label))
}
