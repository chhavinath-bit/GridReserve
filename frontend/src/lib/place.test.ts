import { describe, expect, it } from 'vitest'
import type { VenueSummary } from '@/api/types'
import { parsePlace, placeLabel, placesOf } from './place'

const venue = (city: string, countryCode: string): VenueSummary => ({
  id: 1, categoryId: 1, name: 'V', addressLine: '', city, countryCode, timeZone: 'UTC', active: true,
})

describe('places', () => {
  it('names a city with its country', () => {
    expect(placeLabel('Hyderabad', 'IN')).toBe('Hyderabad, India')
    expect(placeLabel('London', 'GB')).toBe('London, United Kingdom')
  })

  it('lists each city once, sorted', () => {
    const places = placesOf([venue('Pune', 'IN'), venue('London', 'GB'), venue('Pune', 'IN'), venue('Hyderabad', 'IN')])
    expect(places.map((place) => place.label)).toEqual(['Hyderabad, India', 'London, United Kingdom', 'Pune, India'])
    expect(places[0]!.value).toBe('IN|Hyderabad')
  })

  it('reads the URL form back, refusing a broken one', () => {
    expect(parsePlace('IN|Hyderabad')).toEqual({ countryCode: 'IN', city: 'Hyderabad' })
    expect(parsePlace('IN')).toBeUndefined()
    expect(parsePlace(null)).toBeUndefined()
  })
})
