import { Timer } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { formatCountdown } from '@/lib/time'
import { cn } from '@/lib/utils'

export interface HoldTimerProps {
  /** Local deadline in ms (Date.now() scale). See HoldPanel for why it is not expiresAt directly. */
  deadline: number
  onExpire: () => void
}

const WARN_AT_SECONDS = 60

/**
 * Countdown for a hold (FR-UI-06). Turns to the warning look under 60 s. Screen readers hear it only at
 * 1 minute left and at expiry (a polite live region), not every second (§4.2, §7).
 */
export function HoldTimer({ deadline, onExpire }: HoldTimerProps) {
  const [now, setNow] = useState(() => Date.now())
  const left = Math.max(0, Math.ceil((deadline - now) / 1000))
  const running = left > 0
  const expired = useRef(false)

  // A steady tick, independent of rendering; stops once the hold has run out.
  useEffect(() => {
    if (!running) return
    const id = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(id)
  }, [running])

  useEffect(() => {
    // Fire once, even if the parent re-renders with a new callback.
    if (left === 0 && !expired.current) {
      expired.current = true
      onExpire()
    }
  }, [left, onExpire])

  const announcement = left === 0 ? 'Hold expired.' : left <= WARN_AT_SECONDS ? 'One minute left to confirm.' : ''

  return (
    <div className={cn('flex items-center gap-2 text-sm tabular', left <= WARN_AT_SECONDS ? 'text-warning-soft-foreground' : 'text-muted-foreground')}>
      <Timer className="size-4" aria-hidden />
      <span>
        {left === 0 ? 'Hold expired' : <>Held for <strong className="font-semibold">{formatCountdown(left)}</strong></>}
      </span>
      <span aria-live="polite" className="sr-only">
        {announcement}
      </span>
    </div>
  )
}
