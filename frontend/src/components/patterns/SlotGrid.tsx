import { cva } from 'class-variance-authority'
import { Check, Timer } from 'lucide-react'
import { useRef, useState, type KeyboardEvent } from 'react'
import type { Slot } from '@/api/types'
import { formatTime } from '@/lib/time'
import { cn } from '@/lib/utils'

// Slot looks from design A (DESIGN_SYSTEM §3.3); every state has a cue besides color (check icon, dashed border and strike-through, label).
export type CellState = 'FREE' | 'SELECTED' | 'HELD_BY_ME' | 'TAKEN' | 'PAST'

const cellVariants = cva(
  'relative flex h-12 items-center justify-center gap-1 rounded-md border text-sm font-semibold tabular transition-colors duration-[120ms] sm:h-13 sm:text-base lg:h-14',
  {
    variants: {
      state: {
        FREE: 'border-input bg-card text-foreground hover:border-primary hover:bg-primary-soft hover:text-primary-soft-foreground',
        SELECTED: 'border-primary bg-primary text-primary-foreground',
        HELD_BY_ME: 'cursor-default border-warning bg-warning-soft text-warning-soft-foreground',
        TAKEN: 'cursor-not-allowed border-dashed border-subtle-foreground/60 bg-muted font-medium text-subtle-foreground',
        PAST: 'cursor-not-allowed border-transparent bg-transparent font-medium text-subtle-foreground opacity-60',
      },
    },
  },
)

const STATE_TEXT: Record<CellState, string> = {
  FREE: 'free',
  SELECTED: 'selected',
  HELD_BY_ME: 'held by you',
  TAKEN: 'taken',
  PAST: 'past',
}

export const COLUMNS = 4

export interface SlotGridProps {
  slots: Slot[]
  zone: string
  /** Indexes of the selected slots (a contiguous range), or empty. */
  selected: number[]
  /** Slots covered by the member's current hold: shown as theirs, not as "taken". */
  heldByMe?: number[]
  onSelect: (index: number) => void
}

/**
 * The slots of one day in venue time. Keyboard (§7): one cell is in the tab order (roving tabindex);
 * arrow keys move between cells, Home/End jump, Enter/Space select. TAKEN and PAST cells can be focused
 * so screen-reader users hear them, but they can't be selected.
 */
export function SlotGrid({ slots, zone, selected, heldByMe = [], onSelect }: SlotGridProps) {
  const [focusIndex, setFocusIndex] = useState(() => Math.max(0, slots.findIndex((slot) => slot.status === 'FREE')))
  const cells = useRef<(HTMLButtonElement | null)[]>([])

  const stateOf = (index: number): CellState => {
    if (heldByMe.includes(index)) return 'HELD_BY_ME'
    if (selected.includes(index)) return 'SELECTED'
    return slots[index]!.status
  }

  const moveTo = (index: number) => {
    const next = Math.min(slots.length - 1, Math.max(0, index))
    setFocusIndex(next)
    cells.current[next]?.focus()
  }

  const grid = useRef<HTMLDivElement>(null)

  // 3 columns on phones, 4 from 640 px: up and down move by whatever the grid shows right now.
  const columns = () => {
    const template = grid.current ? getComputedStyle(grid.current).gridTemplateColumns : ''
    return template && template !== 'none' ? template.split(' ').length : COLUMNS
  }

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const moves: Record<string, number> = {
      ArrowRight: index + 1,
      ArrowLeft: index - 1,
      ArrowDown: index + columns(),
      ArrowUp: index - columns(),
      Home: 0,
      End: slots.length - 1,
    }
    const target = moves[event.key]
    if (target !== undefined) {
      event.preventDefault()
      moveTo(target)
    }
  }

  return (
    <div role="group" aria-label={`Time slots, times in ${zone}`} ref={grid} className="grid grid-cols-3 gap-2 sm:grid-cols-4">
      {slots.map((slot, index) => {
        const state = stateOf(index)
        const selectable = state === 'FREE' || state === 'SELECTED'
        const time = formatTime(slot.start, zone)
        return (
          <button
            key={slot.start}
            ref={(element) => {
              cells.current[index] = element
            }}
            type="button"
            tabIndex={index === focusIndex ? 0 : -1}
            aria-pressed={state === 'SELECTED'}
            aria-disabled={!selectable || undefined}
            aria-label={`${time}, ${STATE_TEXT[state]}`}
            data-state={state}
            className={cellVariants({ state })}
            onFocus={() => setFocusIndex(index)}
            onKeyDown={(event) => onKeyDown(event, index)}
            onClick={() => {
              if (selectable) onSelect(index)
            }}
          >
            {state === 'SELECTED' ? <Check className="size-3.5" aria-hidden /> : null}
            {state === 'HELD_BY_ME' ? <Timer className="size-3.5" aria-hidden /> : null}
            <span className={cn(state === 'TAKEN' && 'line-through')}>{time}</span>
          </button>
        )
      })}
    </div>
  )
}
