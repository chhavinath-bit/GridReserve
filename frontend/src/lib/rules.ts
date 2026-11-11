import type { OpeningHours } from '@/api/types'

// Human wording for the booking rules (DESIGN_SYSTEM §5). The numbers come from the API, never from here.

/** "45 min", "1 h", "1 h 30 min". */
export function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`
}

/** "60-min slots · 1 h to 2 h", or "30-min slots · 30 min" when there is one length only. */
export function describeDuration(rules: {
  slotMinutes: number | null
  minDurationMinutes: number | null
  maxDurationMinutes: number | null
}): string | null {
  const { slotMinutes, minDurationMinutes: min, maxDurationMinutes: max } = rules
  if (slotMinutes == null || min == null || max == null) return null
  const length = min === max ? formatMinutes(min) : `${formatMinutes(min)} to ${formatMinutes(max)}`
  return `${slotMinutes}-min slots · ${length}`
}

/** "Free cancellation up to 2 h before the start". */
export function describeCutoff(cutoffMinutes: number): string {
  return cutoffMinutes === 0 ? 'Cancel any time before the start' : `Cancel up to ${formatMinutes(cutoffMinutes)} before the start`
}

export const WEEKDAYS: OpeningHours['weekday'][] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

export function weekdayName(weekday: OpeningHours['weekday']): string {
  return weekday[0] + weekday.slice(1).toLowerCase()
}

/** "06:00" from the API's "06:00:00" or "06:00". */
export function shortTime(time: string): string {
  return time.slice(0, 5)
}

/** One line per weekday, with "Closed" where there is no row (FR-CAT-06). */
export function weeklyHours(hours: OpeningHours[]): { weekday: OpeningHours['weekday']; text: string }[] {
  return WEEKDAYS.map((weekday) => {
    const day = hours.find((row) => row.weekday === weekday)
    return { weekday, text: day ? `${shortTime(day.opensAt)} – ${shortTime(day.closesAt)}` : 'Closed' }
  })
}

/** The rules as short pills for the booking page header: "60-min slots", "1 h to 2 h", "Cancel up to 2 h before". */
export function ruleChips(rules: {
  slotMinutes: number | null
  minDurationMinutes: number | null
  maxDurationMinutes: number | null
  cancellationCutoffMinutes: number
}): string[] {
  const chips: string[] = []
  if (rules.slotMinutes != null) chips.push(`${rules.slotMinutes}-min slots`)
  const { minDurationMinutes: min, maxDurationMinutes: max } = rules
  if (min != null && max != null) chips.push(min === max ? formatMinutes(min) : `${formatMinutes(min)} to ${formatMinutes(max)}`)
  chips.push(rules.cancellationCutoffMinutes === 0 ? 'Cancel any time before' : `Cancel up to ${formatMinutes(rules.cancellationCutoffMinutes)} before`)
  return chips
}

/** Today's hours at the venue ("06:00 – 23:00" or "Closed"), today being the venue's local weekday. */
export function hoursToday(hours: OpeningHours[], zone: string, now: Date = new Date()): string {
  const weekday = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'long' }).format(now).toUpperCase()
  return weeklyHours(hours).find((day) => day.weekday === weekday)?.text ?? 'Closed'
}

/** "open daily 06:00–23:00" when every day has the same hours, else null (then the week is listed). */
export function sameEveryDay(hours: OpeningHours[]): string | null {
  const week = weeklyHours(hours)
  return week.every((day) => day.text === week[0]!.text) && week[0]!.text !== 'Closed' ? `open daily ${week[0]!.text.replace(' – ', '–')}` : null
}

/** "Courts", "Seats", "Classes": a group heading from a bookable type's label. */
export function plural(label: string): string {
  return /(s|x|ch|sh)$/i.test(label) ? `${label}es` : `${label}s`
}
