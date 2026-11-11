import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CloudOff, MapPin } from 'lucide-react'
import { useRef, useState } from 'react'
import { useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { bookingApi, catalogApi } from '@/api/endpoints'
import { CONFLICT_CODES, TEMPORARY_CODES } from '@/api/errors'
import { queryKeys } from '@/api/queryKeys'
import type { Availability, Bookable, Booking } from '@/api/types'
import { ConflictNotice } from '@/components/patterns/ConflictNotice'
import { DateStrip } from '@/components/patterns/DateStrip'
import { Breadcrumb } from '@/components/patterns/Breadcrumb'
import { usePhoneHeader } from '@/components/patterns/AppShell'
import { SlotGrid } from '@/components/patterns/SlotGrid'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { holdDeadline } from '@/features/bookings/hold'
import { holdIntent, keyFor, type IntentKey } from '@/lib/idempotency'
import { HoldPanel, type HoldPhase } from '@/features/bookings/HoldPanel'
import { placeLabel } from '@/lib/place'
import { formatMinutes, ruleChips } from '@/lib/rules'
import { formatDay, formatLongDay, formatTime, formatTimeRange, todayIn } from '@/lib/time'
import { cn } from '@/lib/utils'
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

  // The key of the hold being tried; kept while a temporary failure can be retried (IDEM-01).
  const holdKey = useRef<IntentKey | null>(null)

  const createHold = useMutation({
    mutationFn: ({ start, end }: { start: string; end: string }) => {
      holdKey.current = keyFor(holdIntent(bookable.id, start, end), holdKey.current)
      return bookingApi.hold(bookable.id, start, end, holdKey.current.key)
    },
    onSuccess: (booking) => {
      holdKey.current = null
      setHold({ booking, phase: 'held', deadline: holdDeadline(booking) })
      setSelected([])
      setPanelError(null)
      refreshAfterChange()
    },
    onError: (error) => {
      if (!(error instanceof ApiError) || !TEMPORARY_CODES.has(error.code)) {
        holdKey.current = null // the attempt is over: the next Hold is a new intent with a new key
      }
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
      setHint(null)
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
      setHint(null)
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
    if (hold?.phase === 'held') {
      // One hold at a time: the panel on the right decides what happens to it.
      setHint('Confirm or release your current hold first.')
      return
    }
    if (hold) {
      // The last booking is confirmed or expired: a new pick starts the next booking.
      setHold(null)
      setPanelError(null)
    }
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
    hold && hold.phase === 'held' && hold.booking.bookableId === bookable.id
      ? slots.flatMap((slot, index) => (overlaps(slot.start, slot.end, hold.booking.start, hold.booking.end) ? [index] : []))
      : []

  usePhoneHeader({
    title: bookable.name,
    subtitle: `${bookable.venueName}, ${bookable.venueCity} · ${zone}`,
    backTo: `/venues/${bookable.venueId}`,
    backLabel: `Back to ${bookable.venueName}`,
  })
  const chips = ruleChips(bookable.rules)

  return (
    <>
      <div className="hidden md:block">
        <Breadcrumb
          items={[
            { label: bookable.venueName, to: `/venues/${bookable.venueId}` },
            { label: bookable.name },
          ]}
        />
        <div className="mb-5 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h1 className="text-[26px] font-bold tracking-tight lg:text-[28px]">{bookable.name}</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              {bookable.label} at {bookable.venueName} ·{' '}
              <span className="inline-flex items-center gap-1 font-semibold text-foreground">
                <MapPin className="size-3.5 text-primary-soft-foreground" aria-hidden />
                {placeLabel(bookable.venueCity, bookable.venueCountryCode)}
              </span>{' '}
              · times in {zone}
            </p>
          </div>
          <ul aria-label="Rules" className="flex flex-wrap gap-2">
            {chips.map((chip) => (
              <li key={chip} className="rounded-full border bg-card px-2.5 py-1 text-[13px] text-muted-foreground">
                {chip}
              </li>
            ))}
          </ul>
        </div>
      </div>
      <h1 className="sr-only md:hidden">{bookable.name}</h1>
      <div className="grid gap-4 md:gap-5 lg:grid-cols-[minmax(0,1fr)_340px] lg:gap-6">
        <section aria-label="Pick a time" className="flex min-w-0 flex-col gap-4 md:rounded-lg md:border md:bg-card md:p-5 lg:p-6">
          <DateStrip today={today} days={bookable.rules.bookingHorizonDays} value={date} onChange={chooseDate} />
          <h2 className="font-semibold md:hidden">{formatLongDay(date)}</h2>
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
          <p className="text-xs text-muted-foreground md:hidden">{chips.join(' · ')}</p>
        </section>
        <aside aria-label="Your booking" className={cn('lg:sticky lg:top-24 lg:self-start', hold ? 'order-first md:order-none' : undefined)}>
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
                setHint(null)
              }}
            />
          ) : (
            <BookingSummary
              bookable={bookable}
              pick={first && last ? { start: first.start, end: last.end } : null}
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

/**
 * What is picked and "Hold slot" (design A). Phones: a bar fixed above the tab bar. Tablets: a row
 * under the slots. From 1024 px: a card beside them with the time, the cancel deadline and the hold length.
 */
function BookingSummary(props: {
  bookable: Bookable
  pick: { start: string; end: string } | null
  holding: boolean
  error: ApiError | null
  onHold: () => void
}) {
  const { bookable, pick } = props
  const zone = bookable.venueTimeZone
  const temporary = props.error && TEMPORARY_CODES.has(props.error.code)
  const minutes = pick ? (Date.parse(pick.end) - Date.parse(pick.start)) / 60_000 : 0
  const cancelBy = pick ? new Date(Date.parse(pick.start) - bookable.rules.cancellationCutoffMinutes * 60_000).toISOString() : null
  return (
    <>
      {/* Keeps the last row of slots clear of the fixed bar on phones. */}
      <div aria-hidden className="h-24 md:hidden" />
      <div className="fixed inset-x-0 bottom-[calc(4rem+env(safe-area-inset-bottom))] z-20 border-t bg-background px-4 py-3 shadow-lg md:static md:rounded-lg md:border md:bg-card md:p-5 md:shadow-none lg:p-6">
        <h2 className="mb-4 hidden font-semibold lg:block">Your booking</h2>
        <div className="flex items-center gap-3 md:gap-5 lg:flex-col lg:items-stretch lg:gap-4">
          <div className="min-w-0 flex-1 md:rounded-md md:bg-primary-soft md:px-4 md:py-3 lg:p-4" aria-live="polite">
            {pick ? (
              <>
                <p className="text-xs text-muted-foreground md:text-[13px] md:font-semibold md:text-primary-soft-foreground">
                  {formatDay(pick.start, zone)}
                  <span className="lg:hidden"> · {formatMinutes(minutes)}</span>
                </p>
                <p className="text-lg font-bold tabular md:text-xl lg:text-2xl">{formatTimeRange(pick.start, pick.end, zone)}</p>
                <p className="hidden text-sm text-muted-foreground lg:block">
                  {formatMinutes(minutes)} · {bookable.name}
                </p>
                <p className="hidden text-sm text-muted-foreground lg:block">
                  {bookable.venueName}, {placeLabel(bookable.venueCity, bookable.venueCountryCode)}
                </p>
              </>
            ) : (
              <p className="text-sm font-semibold md:text-base">Pick a start time.</p>
            )}
          </div>
          {pick && cancelBy ? (
            <dl className="hidden grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm lg:grid">
              <dt className="text-muted-foreground">Cancel by</dt>
              <dd className="tabular">
                {formatDay(cancelBy, zone)}, {formatTime(cancelBy, zone)}
              </dd>
              <dt className="text-muted-foreground">Hold</dt>
              <dd>5 minutes to confirm</dd>
            </dl>
          ) : (
            <p className="hidden text-sm text-muted-foreground lg:block">A slot is held for 5 minutes while you confirm.</p>
          )}
          <Button onClick={props.onHold} disabled={!pick} loading={props.holding} size="lg" className="shrink-0 lg:h-12">
            {props.error && temporary ? 'Retry' : 'Hold slot'}
          </Button>
        </div>
        {props.error ? (
          <Alert variant={temporary ? 'warning' : 'destructive'} role="alert" className="mt-3">
            {temporary ? <CloudOff aria-hidden /> : null}
            <p>{props.error.message}</p>
          </Alert>
        ) : null}
      </div>
    </>
  )
}
