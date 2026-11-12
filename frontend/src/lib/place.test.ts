import { describe, expect, it } from 'vitest'
import type { VenueSummary } from '@/api/types'
import { parseCityId, placeLabel, placesOf } from './place'

const venue = (cityId: number, city: string, countryCode: string): VenueSummary => ({
  id: 1, categoryId: 1, name: 'V', addressLine: '', cityId, city, countryCode, timeZone: 'UTC', active: true,
})

describe('places', () => {
  it('names a city with its country', () => {
    expect(placeLabel('Hyderabad', 'IN')).toBe('Hyderabad, India')
    expect(placeLabel('London', 'GB')).toBe('London, United Kingdom')
  })

  it('lists each city once, sorted', () => {
    const places = placesOf([venue(31, 'Pune', 'IN'), venue(48, 'London', 'GB'), venue(31, 'Pune', 'IN'), venue(14, 'Hyderabad', 'IN')])
    expect(places.map((place) => place.label)).toEqual(['Hyderabad, India', 'London, United Kingdom', 'Pune, India'])
    expect(places[0]!.value).toBe('14')
  })

  it('reads a city ID from the URL, treating anything else as anywhere', () => {
    expect(parseCityId('14')).toBe(14)
    expect(parseCityId('IN|Hyderabad')).toBeUndefined() // the M1.13 form
    expect(parseCityId('0')).toBeUndefined()
    expect(parseCityId('1.5')).toBeUndefined()
    expect(parseCityId(null)).toBeUndefined()
  })
})
