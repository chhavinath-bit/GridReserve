import { describe, expect, it } from 'vitest'
import { describeCutoff, describeDuration, formatMinutes, hoursToday, plural, ruleChips, sameEveryDay, WEEKDAYS, weeklyHours } from './rules'

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

describe('rule chips', () => {
  it('splits the rules into short pills', () => {
    expect(ruleChips({ slotMinutes: 60, minDurationMinutes: 60, maxDurationMinutes: 120, cancellationCutoffMinutes: 120 })).toEqual([
      '60-min slots',
      '1 h to 2 h',
      'Cancel up to 2 h before',
    ])
  })
})

describe('venue hours and labels', () => {
  const daily = WEEKDAYS.map((weekday) => ({ weekday, opensAt: '06:00', closesAt: '23:00' }))

  it('finds today in the venue zone', () => {
    // 2026-11-11T20:00Z is already Thursday in Kolkata, still Wednesday in New York.
    const hours = [{ weekday: 'THURSDAY' as const, opensAt: '08:00', closesAt: '20:00' }]
    expect(hoursToday(hours, 'Asia/Kolkata', new Date('2026-11-11T20:00:00Z'))).toBe('08:00 – 20:00')
    expect(hoursToday(hours, 'America/New_York', new Date('2026-11-11T20:00:00Z'))).toBe('Closed')
  })

  it('says "open daily" only when all seven days match', () => {
    expect(sameEveryDay(daily)).toBe('open daily 06:00–23:00')
    expect(sameEveryDay(daily.slice(1))).toBeNull()
  })

  it('makes group headings plural', () => {
    expect(plural('Court')).toBe('Courts')
    expect(plural('Class')).toBe('Classes')
  })
})
