import { cn } from '@/lib/utils'

export interface Chip<T> {
  value: T
  label: string
}

/** Toggle chips where exactly one is pressed (category filter, DESIGN_SYSTEM §5 Browse). Scrolls sideways on phones. */
export function ChipGroup<T extends string | number | undefined>({
  label,
  chips,
  value,
  onChange,
}: {
  label: string
  chips: Chip<T>[]
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div role="group" aria-label={label} className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0">
      {chips.map((chip) => {
        const selected = chip.value === value
        return (
          <button
            key={String(chip.value ?? 'all')}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(chip.value)}
            className={cn(
              'h-9 shrink-0 rounded-full border px-4 text-sm font-semibold transition-colors duration-[120ms] outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
              selected ? 'border-foreground bg-foreground text-background' : 'bg-card text-muted-foreground hover:text-foreground',
            )}
          >
            {chip.label}
          </button>
        )
      })}
    </div>
  )
}
