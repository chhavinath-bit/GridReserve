import { CheckCircle2 } from 'lucide-react'
import { Link } from 'react-router'
import type { Booking } from '@/api/types'
import { HoldTimer } from '@/components/patterns/HoldTimer'
import { StatusBadge } from '@/components/patterns/StatusBadge'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { formatDay, formatTimeRange } from '@/lib/time'

export type HoldPhase = 'held' | 'confirmed' | 'expired'

export interface HoldPanelProps {
  booking: Booking
  phase: HoldPhase
  deadline: number
  confirming: boolean
  releasing: boolean
  error: string | null
  onConfirm: () => void
  onRelease: () => void
  onExpire: () => void
  onPickAgain: () => void
}

/** After "Hold slot": countdown, Confirm and Release (DESIGN_SYSTEM §4.2 HoldPanel). */
export function HoldPanel(props: HoldPanelProps) {
  const { booking, phase } = props
  const zone = booking.venueTimeZone
  return (
    <section aria-label="Your hold" className="flex flex-col gap-4 rounded-lg border bg-card p-5 lg:p-6">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-semibold">Your booking</h2>
        <StatusBadge status={phase === 'confirmed' ? 'CONFIRMED' : phase === 'expired' ? 'EXPIRED' : 'HELD'} />
      </div>
      <div className="rounded-md bg-primary-soft p-4">
        <p className="text-[13px] font-semibold text-primary-soft-foreground">{formatDay(booking.start, zone)}</p>
        <p className="text-2xl font-bold tabular">{formatTimeRange(booking.start, booking.end, zone)}</p>
        <p className="text-sm text-muted-foreground">
          {booking.bookableName} · {booking.venueName}
        </p>
      </div>
      {props.error ? (
        <Alert variant="destructive" role="alert">
          {props.error}
        </Alert>
      ) : null}
      {phase === 'held' ? (
        <>
          <HoldTimer deadline={props.deadline} onExpire={props.onExpire} />
          <p className="text-sm text-muted-foreground">This {booking.bookableLabel.toLowerCase()} is yours until the timer runs out. Confirm to keep it.</p>
          <div className="grid grid-cols-2 gap-2">
            <Button variant="outline" size="lg" onClick={props.onRelease} loading={props.releasing} disabled={props.confirming}>
              Release
            </Button>
            <Button size="lg" onClick={props.onConfirm} loading={props.confirming} disabled={props.releasing}>
              Confirm booking
            </Button>
          </div>
        </>
      ) : null}
      {phase === 'confirmed' ? (
        <Alert variant="success">
          <CheckCircle2 aria-hidden />
          <div className="flex flex-col gap-2">
            <p>Booked. See you there.</p>
            <Link to="/bookings" className="font-medium underline underline-offset-4">
              See my bookings
            </Link>
          </div>
        </Alert>
      ) : null}
      {phase === 'expired' ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm">Your hold ran out and the slot was released.</p>
          <div>
            <Button variant="outline" onClick={props.onPickAgain}>
              Pick again
            </Button>
          </div>
        </div>
      ) : null}
    </section>
  )
}
