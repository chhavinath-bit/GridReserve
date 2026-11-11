import { useFormikContext } from 'formik'
import { Input } from '@/components/ui/input'
import { weekdayName } from '@/lib/rules'
import type { DayHours } from './hours'

/**
 * Seven rows: open or closed, and from–to. Text inputs (not type=time) because a day may close at
 * 24:00, which a time input cannot show. `name` is the Formik field holding DayHours[].
 */
export function WeeklyHoursEditor({ name, legend, disabled = false }: { name: string; legend: string; disabled?: boolean }) {
  const { values, errors, submitCount, setFieldValue } = useFormikContext<Record<string, unknown>>()
  const days = values[name] as DayHours[]
  const error = submitCount > 0 && typeof errors[name] === 'string' ? (errors[name] as string) : null
  const update = (index: number, change: Partial<DayHours>) =>
    void setFieldValue(
      name,
      days.map((day, at) => (at === index ? { ...day, ...change } : day)),
    )

  return (
    <fieldset
      id={name}
      tabIndex={-1}
      disabled={disabled}
      aria-describedby={error ? `${name}-error` : undefined}
      className="flex flex-col gap-2 rounded-md outline-none disabled:opacity-60"
    >
      <legend className="mb-2 text-sm font-medium">{legend}</legend>
      <ul className="flex flex-col divide-y rounded-md border bg-card">
        {days.map((day, index) => {
          const label = weekdayName(day.weekday)
          return (
            <li key={day.weekday} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-3 py-2">
              <label className="flex w-32 items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  className="size-4 accent-primary"
                  checked={day.open}
                  onChange={(event) => update(index, { open: event.target.checked })}
                />
                {label}
              </label>
              {day.open ? (
                <span className="flex items-center gap-2 text-sm">
                  <Input
                    aria-label={`${label} opens`}
                    inputMode="numeric"
                    placeholder="08:00"
                    className="w-20 tabular"
                    value={day.opensAt}
                    onChange={(event) => update(index, { opensAt: event.target.value })}
                  />
                  <span aria-hidden>–</span>
                  <Input
                    aria-label={`${label} closes`}
                    inputMode="numeric"
                    placeholder="22:00"
                    className="w-20 tabular"
                    value={day.closesAt}
                    onChange={(event) => update(index, { closesAt: event.target.value })}
                  />
                </span>
              ) : (
                <span className="text-sm text-subtle-foreground">Closed</span>
              )}
            </li>
          )
        })}
      </ul>
      {error ? (
        <p id={`${name}-error`} className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </fieldset>
  )
}
