import { describe, expect, it } from 'vitest'
import { fromDays, hoursProblem, toDays } from './hours'

describe('weekly hours', () => {
  it('turns API rows into seven days and back, leaving closed days out', () => {
    const days = toDays([{ weekday: 'TUESDAY', opensAt: '06:00:00', closesAt: '24:00' }])
    expect(days).toHaveLength(7)
    expect(days[1]).toEqual({ weekday: 'TUESDAY', open: true, opensAt: '06:00', closesAt: '24:00' })
    expect(fromDays(days)).toEqual([{ weekday: 'TUESDAY', opensAt: '06:00', closesAt: '24:00' }])
  })

  it('checks open days like the server does (FR-CAT-09)', () => {
    const days = toDays([])
    expect(hoursProblem(days)).toBeNull()
    days[0] = { weekday: 'MONDAY', open: true, opensAt: '22:00', closesAt: '08:00' }
    expect(hoursProblem(days)).toBe('Monday: must open before it closes.')
    days[0] = { weekday: 'MONDAY', open: true, opensAt: '8:00', closesAt: '20:00' }
    expect(hoursProblem(days)).toBe('Monday: opening must be a time like 08:00.')
    days[0] = { weekday: 'MONDAY', open: true, opensAt: '24:00', closesAt: '24:00' }
    expect(hoursProblem(days)).toBe('Monday: opening must be a time like 08:00.')
  })
})
