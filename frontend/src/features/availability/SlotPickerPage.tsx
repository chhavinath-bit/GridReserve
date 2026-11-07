import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CloudOff } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { bookingApi, catalogApi } from '@/api/endpoints'
import { CONFLICT_CODES, TEMPORARY_CODES } from '@/api/errors'
import { queryKeys } from '@/api/queryKeys'
import type { Availability, Bookable, Booking } from '@/api/types'
import { ConflictNotice } from '@/components/patterns/ConflictNotice'
import { DateStrip } from '@/components/patterns/DateStrip'
import { PageHeader } from '@/components/patterns/PageHeader'
import { SlotGrid } from '@/components/patterns/SlotGrid'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { holdDeadline } from '@/features/bookings/hold'
import { HoldPanel, type HoldPhase } from '@/features/bookings/HoldPanel'
import { formatRange, todayIn } from '@/lib/time'
import { nextSelection } from './selection'

interface CurrentHold {
  booking: Booking
  phase: HoldPhase
  deadline: number
}

/**
 * Pick a date and a time, hold it, confirm it (US-02..07). The date is in the URL. Availability is a
 * view that can be out of date: the hold request is the real decision, and a 409 refreshes the view.
 */
export function SlotPickerPage() {
  const bookableId = Number(useParams().bookableId)
  const bookable = useQuery({ queryKey: queryKeys.bookable(bookableId), queryFn: () => catalogApi.bookable(bookableId) })

  if (bookable.isPending) return <LoadingState />
  if (bookable.isError) return <ErrorState error={bookable.error} onRetry={() => void bookable.refetch()} />
  return <SlotPicker bookable={bookable.data} />
}

function SlotPicker({ bookable }: { bookable: Bookable }) {
  const queryClient = useQueryClient()
  const zone = bookable.venueTimeZone
  const today = todayIn(zone)
  const [params, setParams] = useSearchParams()
  const date = params.get('date') ?? today

  const [selected, setSelected] = useState<number[]>([])
  const [hint, setHint] = useState<string | null>(null)
  const [conflict, setConflict] = useState<string | null>(null)
  const [holdError, setHoldError] = useState<ApiError | null>(null)
  const [hold, setHold] = useState<CurrentHold | null>(null)
  const [panelError, setPanelError] = useState<string | null>(null)

  const availability = useQuery({
    queryKey: queryKeys.availability(bookable.id, date),
    queryFn: () => catalogApi.availability(bookable.id, date),
  })

  const refreshAfterChange = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.availability(bookable.id, date) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.myBookings })
  }

  const createHold = useMutation({
    mutationFn: ({ start, end }: { start: string; end: string }) => bookingApi.hold(bookable.id, start, end),
    onSuccess: (booking) => {
      setHold({ booking, phase: 'held', deadline: holdDeadline(booking) })
      setSelected([])
      setPanelError(null)
      refreshAfterChange()
    },
    onError: (error) => {
      if (error instanceof ApiError && CONFLICT_CODES.has(error.code)) {
        // Someone got there first: say so, drop the stale pick, show fresh availability (FR-UI-05).
        setConflict(error.message)
        setSelected([])
        refreshAfterChange()
      } else {
        // Temporary failures keep the selection, so "Retry" sends the same request (FR-UI-10).
        setHoldError(error instanceof ApiError ? error : null)
      }
    },
  })

  const confirm = useMutation({
    mutationFn: (bookingId: number) => bookingApi.confirm(bookingId),
    onSuccess: (booking) => {
      setHold((current) => (current ? { ...current, booking, phase: 'confirmed' } : current))
      toast.success('Booking confirmed')
      refreshAfterChange()
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'HOLD_EXPIRED') {
        setHold((current) => (current ? { ...current, phase: 'expired' } : current))
        refreshAfterChange()
      }
      setPanelError(error instanceof ApiError ? error.message : 'Something went wrong. Please try again.')
    },
  })

  const release = useMutation({
    mutationFn: (bookingId: number) => bookingApi.cancel(bookingId),
    onSuccess: () => {
      setHold(null)
      toast('Hold released')
      refreshAfterChange()
    },
    onError: (error) => setPanelError(error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'),
  })

  const onExpire = () => {
    setHold((current) => (current && current.phase === 'held' ? { ...current, phase: 'expired' } : current))
    refreshAfterChange()
  }

  const chooseDate = (next: string) => {
    setSelected([])
    setHint(null)
    setConflict(null)
    setHoldError(null)
    setParams({ date: next }, { replace: true })
  }

  const onSelect = (index: number) => {
    if (!availability.data) return
    setConflict(null)
    setHoldError(null)
    const result = nextSelection(availability.data.slots, selected, index, availability.data)
    if (result.ok) {
      setSelected(result.selected)
      setHint(null)
    } else {
      setHint(result.reason)
    }
  }

  const slots = availability.data?.slots ?? []
  const first = slots[selected[0] ?? -1]
  const last = slots[selected[selected.length - 1] ?? -1]
  const heldByMe =
    hold && hold.phase !== 'expired' && hold.booking.bookableId === bookable.id
      ? slots.flatMap((slot, index) => (overlaps(slot.start, slot.end, hold.booking.start, hold.booking.end) ? [index] : []))
      : []

  return (
    <>
      <PageHeader
        title={bookable.name}
        description={
          <>
            <Link to={`/venues/${bookable.venueId}`} className="underline-offset-4 hover:underline">
              {bookable.venueName}
            </Link>{' '}
            · {bookable.label} · times in {zone}
          </>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="flex flex-col gap-4">
          <DateStrip today={today} days={bookable.rules.bookingHorizonDays} value={date} onChange={chooseDate} />
          {conflict ? <ConflictNotice message={conflict} /> : null}
          {hint ? (
            <Alert variant="info" role="status">
              {hint}
            </Alert>
          ) : null}
          <SlotsView
            key={date}
            availability={availability.data}
            pending={availability.isPending}
            error={availability.isError ? availability.error : null}
            onRetry={() => void availability.refetch()}
            selected={selected}
            heldByMe={heldByMe}
            onSelect={onSelect}
          />
        </div>
        <aside className="lg:sticky lg:top-6 lg:self-start">
          {hold ? (
            <HoldPanel
              booking={hold.booking}
              phase={hold.phase}
              deadline={hold.deadline}
              confirming={confirm.isPending}
              releasing={release.isPending}
              error={panelError}
              onConfirm={() => confirm.mutate(hold.booking.id)}
              onRelease={() => release.mutate(hold.booking.id)}
              onExpire={onExpire}
              onPickAgain={() => {
                setHold(null)
                setPanelError(null)
              }}
            />
          ) : (
            <BookingSummary
              bookable={bookable}
              range={first && last ? formatRange(first.start, last.end, zone) : null}
              holding={createHold.isPending}
              error={holdError}
              onHold={() => first && last && createHold.mutate({ start: first.start, end: last.end })}
            />
          )}
        </aside>
      </div>
    </>
  )
}

// Half-open ranges, compared as moments (not strings: the same instant can be written several ways).
function overlaps(start: string, end: string, otherStart: string, otherEnd: string): boolean {
  return Date.parse(start) < Date.parse(otherEnd) && Date.parse(end) > Date.parse(otherStart)
}

function SlotsView(props: {
  availability: Availability | undefined
  pending: boolean
  error: unknown
  onRetry: () => void
  selected: number[]
  heldByMe: number[]
  onSelect: (index: number) => void
}) {
  if (props.pending) return <LoadingState rows={4} label="Loading free times" />
  if (props.error) return <ErrorState error={props.error} onRetry={props.onRetry} />
  const availability = props.availability
  if (!availability) return null
  if (availability.closed) return <EmptyState title="Closed on this day">Pick another date.</EmptyState>
  if (availability.slots.every((slot) => slot.status !== 'FREE') && props.heldByMe.length === 0) {
    return (
      <>
        <EmptyState title="No free times left on this day">Pick another date.</EmptyState>
        <SlotGrid slots={availability.slots} zone={availability.venueTimeZone} selected={[]} onSelect={() => undefined} />
      </>
    )
  }
  return (
    <SlotGrid
      slots={availability.slots}
      zone={availability.venueTimeZone}
      selected={props.selected}
      heldByMe={props.heldByMe}
      onSelect={props.onSelect}
    />
  )
}

function BookingSummary(props: {
  bookable: Bookable
  range: string | null
  holding: boolean
  error: ApiError | null
  onHold: () => void
}) {
  const { rules } = props.bookable
  const temporary = props.error && TEMPORARY_CODES.has(props.error.code)
  return (
    <Card>
      <CardHeader>
        <CardTitle>Your booking</CardTitle>
        <p className="text-sm text-muted-foreground tabular">{props.range ?? 'Pick a start time.'}</p>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <ul className="flex flex-col gap-1 text-sm text-muted-foreground">
          <li>
            {rules.minDurationMinutes === rules.maxDurationMinutes
              ? `${rules.minDurationMinutes} minutes`
              : `${rules.minDurationMinutes} to ${rules.maxDurationMinutes} minutes; click a later slot to extend`}
          </li>
          <li>Held for 5 minutes while you confirm</li>
          <li>Free cancellation until {rules.cancellationCutoffMinutes / 60} hours before</li>
        </ul>
        {props.error ? (
          <Alert variant={temporary ? 'warning' : 'destructive'} role="alert">
            {temporary ? <CloudOff aria-hidden /> : null}
            <p>{props.error.message}</p>
          </Alert>
        ) : null}
        <Button onClick={props.onHold} disabled={!props.range} loading={props.holding}>
          {props.error && temporary ? 'Retry' : 'Hold slot'}
        </Button>
      </CardContent>
    </Card>
  )
}
