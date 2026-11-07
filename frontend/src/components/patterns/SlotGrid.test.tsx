import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Slot, SlotStatus } from '@/api/types'
import { SlotGrid } from './SlotGrid'

const ZONE = 'Asia/Kolkata'
// 04:30Z + n hours = 10:00, 11:00, ... in Kolkata.
const slots = (statuses: SlotStatus[]): Slot[] =>
  statuses.map((status, index) => ({
    start: `2026-11-09T${String(4 + index).padStart(2, '0')}:30:00Z`,
    end: `2026-11-09T${String(5 + index).padStart(2, '0')}:30:00Z`,
    localStart: '',
    localEnd: '',
    status,
  }))

describe('SlotGrid', () => {
  it('names every state in words, not only by color (§3.3), in venue time', () => {
    render(<SlotGrid slots={slots(['PAST', 'FREE', 'TAKEN', 'FREE'])} zone={ZONE} selected={[3]} heldByMe={[1]} onSelect={() => undefined} />)
    expect(screen.getByRole('button', { name: '10:00, past' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '11:00, held by you' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '12:00, taken' })).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByRole('button', { name: '13:00, selected' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('group')).toHaveAccessibleName('Time slots, times in Asia/Kolkata')
  })

  it('selects free slots only', async () => {
    const onSelect = vi.fn()
    render(<SlotGrid slots={slots(['FREE', 'TAKEN', 'PAST'])} zone={ZONE} selected={[]} onSelect={onSelect} />)
    await userEvent.click(screen.getByRole('button', { name: '11:00, taken' }))
    await userEvent.click(screen.getByRole('button', { name: '12:00, past' }))
    expect(onSelect).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: '10:00, free' }))
    expect(onSelect).toHaveBeenCalledWith(0)
  })

  it('keyboard: one cell in the tab order, arrows move focus, Enter selects (§7)', async () => {
    const onSelect = vi.fn()
    render(<SlotGrid slots={slots(['TAKEN', 'FREE', 'FREE', 'FREE', 'FREE', 'FREE', 'FREE', 'FREE'])} zone={ZONE} selected={[]} onSelect={onSelect} />)
    const user = userEvent.setup()

    await user.tab()
    expect(screen.getByRole('button', { name: '11:00, free' })).toHaveFocus() // first free slot
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('button', { name: '12:00, free' })).toHaveFocus()
    await user.keyboard('{ArrowDown}') // 4 columns: one row down, from index 2 to 6
    expect(screen.getByRole('button', { name: '16:00, free' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(onSelect).toHaveBeenCalledWith(6)
    await user.keyboard('{End}')
    expect(screen.getByRole('button', { name: '17:00, free' })).toHaveFocus()
    expect(screen.getAllByRole('button').filter((cell) => cell.tabIndex === 0)).toHaveLength(1)
  })
})
