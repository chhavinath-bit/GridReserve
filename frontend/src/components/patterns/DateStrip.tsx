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
    <div role="group" aria-label="Date" className="grid gap-1.5 sm:gap-2" style={{ gridTemplateColumns: `repeat(${dates.length}, minmax(0, 1fr))` }}>
      {dates.map((date) => {
        const { weekday, day } = formatCalendarDay(date)
        const active = date === value
        return (
          <button
            key={date}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(date)}
            className={cn(
              'flex h-14 min-w-0 flex-col items-center justify-center rounded-lg border tabular transition-colors duration-[120ms]',
              active ? 'border-foreground bg-foreground text-background' : 'border-border bg-card hover:border-primary',
            )}
          >
            {/* The visible words are the name ("Today, Wed 11", "Thu 12"), so speech input can say what it sees (WCAG 2.5.3). */}
            <span className="text-[11px] opacity-80 sm:text-xs">{date === today ? <>Today<span className="sr-only">, {weekday}</span></> : weekday}</span>{' '}
            <span className="text-base font-bold sm:text-lg">{day}</span>
          </button>
        )
      })}
    </div>
  )
}
