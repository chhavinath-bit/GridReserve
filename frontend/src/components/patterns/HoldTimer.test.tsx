import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HoldTimer } from './HoldTimer'

describe('HoldTimer (FR-UI-06)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-07T10:00:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  it('counts down, warns screen readers at one minute, and reports expiry once', () => {
    const onExpire = vi.fn()
    render(<HoldTimer deadline={Date.now() + 61_000} onExpire={onExpire} />)
    expect(screen.getByText('1:01')).toBeInTheDocument()
    expect(screen.queryByText('One minute left to confirm.')).not.toBeInTheDocument()

    act(() => vi.advanceTimersByTime(2_000))
    expect(screen.getByText('0:59')).toBeInTheDocument()
    expect(screen.getByText('One minute left to confirm.')).toBeInTheDocument()

    act(() => vi.advanceTimersByTime(60_000))
    expect(screen.getByText('Hold expired')).toBeInTheDocument()
    expect(onExpire).toHaveBeenCalledTimes(1)

    act(() => vi.advanceTimersByTime(5_000))
    expect(onExpire).toHaveBeenCalledTimes(1)
  })
})
