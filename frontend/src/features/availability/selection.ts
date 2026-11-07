import type { Slot } from '@/api/types'

export interface SelectionRules {
  slotMinutes: number
  minDurationMinutes: number
  maxDurationMinutes: number
}

export type SelectionResult = { ok: true; selected: number[] } | { ok: false; reason: string }

/**
 * What a click on slot `clicked` does to the current selection (a contiguous run of slot indexes).
 * Slots on one day are back to back, so neighbouring indexes are neighbouring times.
 * - Click a selected slot: clear the selection.
 * - Click right after the selection, within the maximum length, all free in between: extend to it.
 * - Otherwise: start a new selection of the minimum length at the clicked slot, if it fits.
 * The server still checks every rule; this only keeps the picker from offering what can't work.
 */
export function nextSelection(slots: Slot[], current: number[], clicked: number, rules: SelectionRules): SelectionResult {
  const minSlots = Math.max(1, Math.ceil(rules.minDurationMinutes / rules.slotMinutes))
  const maxSlots = Math.max(minSlots, Math.floor(rules.maxDurationMinutes / rules.slotMinutes))

  if (current.includes(clicked)) {
    return { ok: true, selected: [] }
  }

  const first = current[0]
  const last = current[current.length - 1]
  if (first !== undefined && last !== undefined && clicked > last && clicked - first + 1 <= maxSlots && allFree(slots, last + 1, clicked)) {
    return { ok: true, selected: range(first, clicked) }
  }

  const end = clicked + minSlots - 1
  if (end < slots.length && allFree(slots, clicked, end)) {
    return { ok: true, selected: range(clicked, end) }
  }
  return { ok: false, reason: `This needs ${rules.minDurationMinutes} minutes free in a row from here.` }
}

function allFree(slots: Slot[], from: number, to: number): boolean {
  for (let index = from; index <= to; index++) {
    if (slots[index]?.status !== 'FREE') return false
  }
  return true
}

function range(from: number, to: number): number[] {
  return Array.from({ length: to - from + 1 }, (_, offset) => from + offset)
}
