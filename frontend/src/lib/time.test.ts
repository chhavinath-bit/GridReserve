import { describe, expect, it } from 'vitest'
import { addDays, formatCountdown, formatRange, formatTime, secondsUntil, todayIn } from './time'

describe('time in the venue zone (FR-UI-04)', () => {
  it('shows the venue’s wall clock, not the browser’s', () => {
    // 04:30 UTC is 10:00 in Kolkata (UTC+05:30) and 04:30 in London in November (UTC+0).
    expect(formatTime('2026-11-09T04:30:00Z', 'Asia/Kolkata')).toBe('10:00')
    expect(formatTime('2026-11-09T04:30:00Z', 'Europe/London')).toBe('04:30')
  })

  it('formats a range as "Mon 9 Nov · 10:00–11:00"', () => {
    expect(formatRange('2026-11-09T04:30:00Z', '2026-11-09T05:30:00Z', 'Asia/Kolkata')).toBe('Mon 9 Nov · 10:00–11:00')
  })

  it('"today" is the venue’s date: 20:00 UTC is already tomorrow in Kolkata', () => {
    const lateEvening = new Date('2026-11-07T20:00:00Z')
    expect(todayIn('Asia/Kolkata', lateEvening)).toBe('2026-11-08')
    expect(todayIn('Europe/London', lateEvening)).toBe('2026-11-07')
  })

  it('adds calendar days across a month end', () => {
    expect(addDays('2026-11-29', 3)).toBe('2026-12-02')
  })

  it('counts down in whole seconds and never below zero', () => {
    const now = Date.parse('2026-11-07T10:00:00Z')
    expect(secondsUntil('2026-11-07T10:04:05Z', now)).toBe(245)
    expect(secondsUntil('2026-11-07T09:59:00Z', now)).toBe(0)
    expect(formatCountdown(245)).toBe('4:05')
    expect(formatCountdown(9)).toBe('0:09')
  })
})
