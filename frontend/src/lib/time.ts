// All times are shown in the venue's zone (FR-UI-04), with Intl.DateTimeFormat; never the browser's.

const LOCALE = 'en-GB'

function formatter(zone: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat(LOCALE, { timeZone: zone, ...options })
}

/** "18:00" in the venue's zone. */
export function formatTime(instant: string, zone: string): string {
  return formatter(zone, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(instant))
}

/** "Tue 4 Nov" in the venue's zone. */
export function formatDay(instant: string, zone: string): string {
  return formatter(zone, { weekday: 'short', day: 'numeric', month: 'short' }).format(new Date(instant))
}

/** "Tue 4 Nov · 18:00–19:00" (DESIGN_SYSTEM §5); the zone is shown once per screen, not here. */
export function formatRange(start: string, end: string, zone: string): string {
  return `${formatDay(start, zone)} · ${formatTime(start, zone)}–${formatTime(end, zone)}`
}

/** Today's date at the venue as "YYYY-MM-DD" (en-CA formats dates in ISO order). */
export function todayIn(zone: string, now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now)
}

/** "YYYY-MM-DD" plus n days, as calendar dates (no time zone involved). */
export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

/** "Tue 4" for a "YYYY-MM-DD" calendar date. */
export function formatCalendarDay(isoDate: string): { weekday: string; day: string } {
  const date = new Date(`${isoDate}T12:00:00Z`)
  const parts = new Intl.DateTimeFormat(LOCALE, { timeZone: 'UTC', weekday: 'short', day: 'numeric' }).formatToParts(date)
  return {
    weekday: parts.find((part) => part.type === 'weekday')?.value ?? '',
    day: parts.find((part) => part.type === 'day')?.value ?? '',
  }
}

/** Whole seconds left until `instant` (never negative). */
export function secondsUntil(instant: string, now: number = Date.now()): number {
  return Math.max(0, Math.ceil((new Date(instant).getTime() - now) / 1000))
}

/** "4:05" for a countdown. */
export function formatCountdown(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`
}
