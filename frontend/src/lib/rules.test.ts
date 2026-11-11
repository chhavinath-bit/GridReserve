import { describe, expect, it } from 'vitest'
import { describeCutoff, describeDuration, formatMinutes, weeklyHours } from './rules'

describe('rule wording', () => {
  it('formats minutes as hours where it reads better', () => {
    expect(formatMinutes(45)).toBe('45 min')
    expect(formatMinutes(120)).toBe('2 h')
    expect(formatMinutes(90)).toBe('1 h 30 min')
  })

  it('describes the slot grid and the allowed length', () => {
    expect(describeDuration({ slotMinutes: 60, minDurationMinutes: 60, maxDurationMinutes: 120 })).toBe('60-min slots · 1 h to 2 h')
    expect(describeDuration({ slotMinutes: 30, minDurationMinutes: 30, maxDurationMinutes: 30 })).toBe('30-min slots · 30 min')
    expect(describeDuration({ slotMinutes: null, minDurationMinutes: null, maxDurationMinutes: null })).toBeNull()
  })

  it('describes the cancellation cutoff', () => {
    expect(describeCutoff(120)).toBe('Cancel up to 2 h before the start')
  })

  it('lists every weekday and marks missing days closed', () => {
    const week = weeklyHours([{ weekday: 'MONDAY', opensAt: '06:00:00', closesAt: '23:00:00' }])
    expect(week).toHaveLength(7)
    expect(week[0]).toEqual({ weekday: 'MONDAY', text: '06:00 – 23:00' })
    expect(week[6]).toEqual({ weekday: 'SUNDAY', text: 'Closed' })
  })
})
