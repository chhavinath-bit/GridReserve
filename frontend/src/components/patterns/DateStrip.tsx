import { addDays, formatCalendarDay } from '@/lib/time'
import { cn } from '@/lib/utils'

export interface DateStripProps {
  /** Today at the venue, "YYYY-MM-DD". */
  today: string
  /** Bookable days from today (the horizon). */
  days: number
  value: string
  onChange: (date: string) => void
}

/** The next `days` dates as compact buttons (DESIGN_SYSTEM §4.2); later dates aren't shown at all. */
export function DateStrip({ today, days, value, onChange }: DateStripProps) {
  const dates = Array.from({ length: days }, (_, index) => addDays(today, index))
  return (
    <div role="group" aria-label="Date" className="flex gap-2 overflow-x-auto pb-1">
      {dates.map((date) => {
        const { weekday, day } = formatCalendarDay(date)
        const active = date === value
        return (
          <button
            key={date}
            type="button"
            aria-pressed={active}
            aria-label={date === today ? `Today, ${weekday} ${day}` : `${weekday} ${day}`}
            onClick={() => onChange(date)}
            className={cn(
              'flex h-14 min-w-12 flex-col items-center justify-center rounded-sm border text-sm tabular transition-colors duration-[120ms]',
              active
                ? 'border-primary bg-primary text-primary-foreground'
                : 'border-border bg-card hover:bg-primary-soft hover:text-primary-soft-foreground',
            )}
          >
            <span className="text-xs">{date === today ? 'Today' : weekday}</span>
            <span className="font-semibold">{day}</span>
          </button>
        )
      })}
    </div>
  )
}
