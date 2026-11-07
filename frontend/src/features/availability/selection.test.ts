import { describe, expect, it } from 'vitest'
import type { Slot, SlotStatus } from '@/api/types'
import { nextSelection } from './selection'

const slots = (statuses: SlotStatus[]): Slot[] =>
  statuses.map((status, index) => ({ start: `s${index}`, end: `e${index}`, localStart: '', localEnd: '', status }))

const COURT = { slotMinutes: 60, minDurationMinutes: 60, maxDurationMinutes: 120 }
const TRAINER = { slotMinutes: 15, minDurationMinutes: 45, maxDurationMinutes: 45 }

describe('picking slots', () => {
  it('a click selects the minimum length; a later free slot extends up to the maximum', () => {
    const day = slots(['FREE', 'FREE', 'FREE', 'FREE'])
    expect(nextSelection(day, [], 1, COURT)).toEqual({ ok: true, selected: [1] })
    expect(nextSelection(day, [1], 2, COURT)).toEqual({ ok: true, selected: [1, 2] })
    // Past the 2-hour maximum: starts again at the clicked slot.
    expect(nextSelection(day, [1, 2], 3, COURT)).toEqual({ ok: true, selected: [3] })
  })

  it('clicking a selected slot clears the selection', () => {
    expect(nextSelection(slots(['FREE', 'FREE']), [0, 1], 1, COURT)).toEqual({ ok: true, selected: [] })
  })

  it('does not extend over a taken slot', () => {
    const day = slots(['FREE', 'TAKEN', 'FREE'])
    expect(nextSelection(day, [0], 2, COURT)).toEqual({ ok: true, selected: [2] })
  })

  it('trainer: one click picks 45 minutes (three 15-minute slots) when they are free in a row', () => {
    expect(nextSelection(slots(['FREE', 'FREE', 'FREE', 'FREE']), [], 0, TRAINER)).toEqual({ ok: true, selected: [0, 1, 2] })
  })

  it('refuses a start that has no room for the minimum length, with a reason', () => {
    const result = nextSelection(slots(['FREE', 'FREE', 'TAKEN']), [], 0, TRAINER)
    expect(result.ok).toBe(false)
    expect(!result.ok && result.reason).toContain('45 minutes')
  })
})
