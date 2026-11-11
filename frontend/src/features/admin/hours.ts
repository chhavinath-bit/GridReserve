import type { OpeningHours } from '@/api/types'
import { WEEKDAYS, shortTime, weekdayName } from '@/lib/rules'

/** One row of the weekly hours editor; a closed day has no row in the API (FR-CAT-06). */
export interface DayHours {
  weekday: OpeningHours['weekday']
  open: boolean
  opensAt: string
  closesAt: string
}

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/
const CLOSING = /^(([01]\d|2[0-3]):[0-5]\d|24:00)$/

export function toDays(hours: OpeningHours[]): DayHours[] {
  return WEEKDAYS.map((weekday) => {
    const day = hours.find((row) => row.weekday === weekday)
    return day
      ? { weekday, open: true, opensAt: shortTime(day.opensAt), closesAt: shortTime(day.closesAt) }
      : { weekday, open: false, opensAt: '08:00', closesAt: '20:00' }
  })
}

export function fromDays(days: DayHours[]): OpeningHours[] {
  return days.filter((day) => day.open).map(({ weekday, opensAt, closesAt }) => ({ weekday, opensAt, closesAt }))
}

/**
 * The same checks as the server (OpeningHoursRequest, CatalogValidator.hours), as one message for the
 * whole week, so the form summary can name it. The server still decides.
 */
export function hoursProblem(days: DayHours[]): string | null {
  for (const day of days) {
    if (!day.open) continue
    const name = weekdayName(day.weekday)
    if (!TIME.test(day.opensAt)) return `${name}: opening must be a time like 08:00.`
    if (!CLOSING.test(day.closesAt)) return `${name}: closing must be a time like 22:00 (or 24:00).`
    if (day.opensAt >= day.closesAt) return `${name}: must open before it closes.`
  }
  return null
}
