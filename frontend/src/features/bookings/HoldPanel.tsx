import { CheckCircle2 } from 'lucide-react'
import { Link } from 'react-router'
import type { Booking } from '@/api/types'
import { HoldTimer } from '@/components/patterns/HoldTimer'
import { StatusBadge } from '@/components/patterns/StatusBadge'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatRange } from '@/lib/time'

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
  const when = formatRange(booking.start, booking.end, booking.venueTimeZone)
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>{booking.bookableName}</CardTitle>
          <StatusBadge status={phase === 'confirmed' ? 'CONFIRMED' : phase === 'expired' ? 'EXPIRED' : 'HELD'} />
        </div>
        <p className="text-sm text-muted-foreground tabular">{when}</p>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {props.error ? (
          <Alert variant="destructive" role="alert">
            {props.error}
          </Alert>
        ) : null}
        {phase === 'held' ? (
          <>
            <HoldTimer deadline={props.deadline} onExpire={props.onExpire} />
            <p className="text-sm text-muted-foreground">This {booking.bookableLabel.toLowerCase()} is yours until the timer runs out. Confirm to keep it.</p>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button onClick={props.onConfirm} loading={props.confirming} disabled={props.releasing}>
                Confirm booking
              </Button>
              <Button variant="outline" onClick={props.onRelease} loading={props.releasing} disabled={props.confirming}>
                Release
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
      </CardContent>
    </Card>
  )
}
