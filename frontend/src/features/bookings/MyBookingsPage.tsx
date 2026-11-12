import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Tabs } from 'radix-ui'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { bookingApi, type MyBookingsFilter } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { Booking } from '@/api/types'
import { usePhoneHeader } from '@/components/patterns/AppShell'
import { HoldTimer } from '@/components/patterns/HoldTimer'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { StatusBadge } from '@/components/patterns/StatusBadge'
import { Alert } from '@/components/ui/alert'
import { ReasonField } from '@/components/patterns/ReasonField'
import { ConfirmDialog } from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { canCancel, closedNote, effectiveStatus } from '@/lib/bookingStatus'
import { placeLabel } from '@/lib/place'
import { formatDay, formatTime, formatTimeRange } from '@/lib/time'
import { useNow } from '@/lib/useNow'
import { cn } from '@/lib/utils'

type Tab = 'upcoming' | 'past' | 'closed'

const TABS: { id: Tab; label: string }[] = [
  { id: 'upcoming', label: 'Upcoming' },
  { id: 'past', label: 'Past' },
  { id: 'closed', label: 'Cancelled & expired' },
]

/**
 * What each tab asks the server for (FR-BK-05). Each tab is its own paged list, so a member with
 * hundreds of past bookings still sees every one with "Load more". `now` is fixed when the page opens,
 * so later pages line up with the first.
 */
export function filterFor(tab: Tab, now: string): MyBookingsFilter {
  switch (tab) {
    case 'upcoming':
      return { statuses: ['HELD', 'CONFIRMED'], from: now, order: 'asc' }
    case 'past':
      return { statuses: ['CONFIRMED'], to: now, order: 'desc' }
    case 'closed':
      return { statuses: ['CANCELLED', 'EXPIRED'], order: 'desc' }
  }
}

/** Which tab a booking belongs to now (DESIGN_SYSTEM §5 My bookings); a hold that runs out leaves Upcoming. */
export function tabOf(booking: Booking, now: number): Tab {
  const status = effectiveStatus(booking, now)
  if (status === 'CANCELLED' || status === 'EXPIRED') return 'closed'
  return new Date(booking.end).getTime() > now ? 'upcoming' : 'past'
}

const TAB_SHORT: Record<Tab, string> = { upcoming: 'Upcoming', past: 'Past', closed: 'Closed' }

/** One tab's paged list. The page reads the upcoming one too, for the count on its tab (same query, fetched once). */
function useTabBookings(tab: Tab, openedAt: string) {
  const filter = filterFor(tab, openedAt)
  return useInfiniteQuery({
    queryKey: queryKeys.myBookingsTab(tab),
    queryFn: ({ pageParam }) => bookingApi.mine(filter, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

export function MyBookingsPage() {
  const [tab, setTab] = useState<Tab>('upcoming')
  const [openedAt] = useState(() => new Date().toISOString())
  const upcoming = useTabBookings('upcoming', openedAt)
  const upcomingCount = upcoming.data?.pages[0]?.totalItems
  usePhoneHeader({ title: 'My bookings' })

  return (
    <div className="mx-auto max-w-[880px]">
      <h1 className="mb-5 hidden text-[26px] font-bold tracking-tight md:block lg:text-[28px]">My bookings</h1>
      <h1 className="sr-only md:hidden">My bookings</h1>
      <Tabs.Root value={tab} onValueChange={(value) => setTab(value as Tab)}>
        {/* Phones: a segmented control with short words. From 768 px: underlined tabs (design A). */}
        <Tabs.List
          aria-label="Bookings"
          className="mb-4 grid grid-cols-3 rounded-md bg-muted p-1 md:mb-5 md:flex md:gap-6 md:rounded-none md:border-b md:bg-transparent md:p-0"
        >
          {TABS.map(({ id, label }) => (
            <Tabs.Trigger
              key={id}
              value={id}
              className="h-9 rounded-sm text-[13px] font-semibold text-muted-foreground outline-none transition-colors duration-[120ms] hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm md:-mb-px md:h-11 md:rounded-none md:border-b-2 md:border-transparent md:text-sm md:data-[state=active]:border-primary md:data-[state=active]:bg-transparent md:data-[state=active]:shadow-none"
            >
              <span className="md:hidden">{TAB_SHORT[id]}</span>
              <span className="hidden md:inline">{label}</span>
              {id === 'upcoming' && upcomingCount ? <span className="hidden md:inline"> · {upcomingCount}</span> : null}
            </Tabs.Trigger>
          ))}
        </Tabs.List>
        {TABS.map(({ id }) => (
          <Tabs.Content key={id} value={id} className="outline-none">
            <BookingList tab={id} openedAt={openedAt} />
          </Tabs.Content>
        ))}
      </Tabs.Root>
    </div>
  )
}

function BookingList({ tab, openedAt }: { tab: Tab; openedAt: string }) {
  // Re-evaluated every few seconds, so a hold that runs out shows as expired by itself.
  const now = useNow(5_000)
  const bookings = useTabBookings(tab, openedAt)

  if (bookings.isPending) return <LoadingState />
  // A failed "Load more" keeps the pages already shown; only a failed first page replaces the list.
  if (!bookings.data) return <ErrorState error={bookings.error} onRetry={() => void bookings.refetch()} />

  const all = bookings.data.pages.flatMap((page) => page.items)
  const items = tab === 'upcoming' ? all.filter((booking) => tabOf(booking, now) !== 'closed') : all
  const total = bookings.data.pages[0]?.totalItems ?? 0

  if (items.length === 0) {
    return (
      <EmptyState title={tab === 'upcoming' ? 'Nothing booked yet' : 'Nothing here'}>
        {tab === 'upcoming' ? (
          <Link to="/" className="text-primary underline-offset-4 hover:underline">
            Find a slot
          </Link>
        ) : null}
      </EmptyState>
    )
  }
  return (
    <div className="flex flex-col gap-4">
      <ul className="flex flex-col gap-3">
        {items.map((booking) => (
          <li key={booking.id}>
            <BookingCard booking={booking} now={now} />
          </li>
        ))}
      </ul>
      <div className="flex flex-col items-center gap-2 py-2">
        {bookings.hasNextPage ? (
          <Button variant="outline" onClick={() => void bookings.fetchNextPage()} loading={bookings.isFetchingNextPage}>
            Load more
          </Button>
        ) : null}
        <p className="text-xs text-subtle-foreground" aria-live="polite">
          Showing {all.length} of {total}
        </p>
        {bookings.isFetchNextPageError ? <ErrorState error={bookings.error} onRetry={() => void bookings.fetchNextPage()} /> : null}
      </div>
    </div>
  )
}

/** The day as a column: "THU / 12 / NOV" in the venue's zone (tablet and up). */
function DateColumn({ booking }: { booking: Booking }) {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: booking.venueTimeZone, weekday: 'short', day: 'numeric', month: 'short' }).formatToParts(
    new Date(booking.start),
  )
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((candidate) => candidate.type === type)?.value ?? ''
  return (
    <div aria-hidden className="hidden w-16 shrink-0 text-center md:block">
      <p className="text-xs font-semibold uppercase text-subtle-foreground">{part('weekday')}</p>
      <p className="text-2xl font-bold leading-tight tabular">{part('day')}</p>
      <p className="text-xs text-subtle-foreground">{part('month')}</p>
    </div>
  )
}

/** One booking (DESIGN_SYSTEM §4.2 BookingCard). A live hold can be confirmed or released from here, e.g. after a reload. */
function BookingCard({ booking, now }: { booking: Booking; now: number }) {
  const queryClient = useQueryClient()
  const [askingCancel, setAskingCancel] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  // A hold loaded from the list (e.g. after a reload) has no "answer arrived" moment, so the device
  // clock is compared with expiresAt directly; the server still decides at confirm.
  const deadline = booking.expiresAt ? Date.parse(booking.expiresAt) : 0
  const status = effectiveStatus(booking, now)

  const settle = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.myBookings })
    void queryClient.invalidateQueries({ queryKey: queryKeys.availabilityAll })
  }
  const onError = (cause: Error) => {
    setAskingCancel(false)
    setError(cause instanceof ApiError ? cause.message : 'Something went wrong. Please try again.')
    settle()
  }
  const confirm = useMutation({
    mutationFn: () => bookingApi.confirm(booking.id),
    onSuccess: () => {
      toast.success('Booking confirmed')
      settle()
    },
    onError,
  })
  const cancel = useMutation({
    mutationFn: () => bookingApi.cancel(booking.id, reason.trim() || undefined),
    onSuccess: () => {
      toast(status === 'HELD' ? 'Hold released' : 'Booking cancelled')
      setAskingCancel(false)
      setReason('')
      settle()
    },
    onError,
  })

  const zone = booking.venueTimeZone
  const held = status === 'HELD'
  const active = held || (status === 'CONFIRMED' && new Date(booking.end).getTime() > now)
  // After the cutoff the button stays visible but disabled, with the reason next to it (DESIGN_SYSTEM §4.2).
  const cancelClosed = active && !canCancel(booking, now)
  const noteId = `booking-note-${booking.id}`
  const times = formatTimeRange(booking.start, booking.end, zone)
  const note = held
    ? `Confirm before the hold runs out, or the ${booking.bookableLabel.toLowerCase()} is released.`
    : cancelClosed && booking.cancellableUntil
      ? `Cancellations closed ${formatDay(booking.cancellableUntil, zone)}, ${formatTime(booking.cancellableUntil, zone)}. Contact the venue to change this booking.`
      : active && booking.cancellableUntil
        ? `Free cancellation until ${formatDay(booking.cancellableUntil, zone)}, ${formatTime(booking.cancellableUntil, zone)}`
        : closedNote(booking, now)

  return (
    <article
      className={cn(
        'flex flex-col gap-3 rounded-lg border bg-card p-4 md:flex-row md:items-center md:gap-5 md:px-5 md:py-4',
        held && 'border-warning',
      )}
    >
      <DateColumn booking={booking} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <h3 className="font-semibold">{booking.bookableName}</h3>
          {held ? <HoldTimer deadline={deadline} onExpire={settle} variant="badge" /> : <StatusBadge status={status} />}
        </div>
        <p className="-mt-1 text-[13px] text-muted-foreground md:hidden">
          {booking.venueName}, <span className="font-semibold text-foreground">{placeLabel(booking.venueCity, booking.venueCountryCode)}</span>
        </p>
        <p className="text-sm text-muted-foreground">
          <span className="font-semibold text-foreground md:hidden">{formatDay(booking.start, zone)} · </span>
          <span className="tabular font-semibold text-foreground md:font-normal md:text-muted-foreground">{times}</span>
          <span className="hidden md:inline">
            {' '}
            · {booking.venueName}, <span className="font-semibold text-foreground">{placeLabel(booking.venueCity, booking.venueCountryCode)}</span>
          </span>
          <span className="text-subtle-foreground"> ({zone})</span>
        </p>
        {note ? (
          <p id={noteId} className={cn('text-[13px]', held ? 'text-warning-soft-foreground' : 'text-subtle-foreground')}>
            {note}
          </p>
        ) : null}
        {!active && booking.cancelReason ? (
          <p className="text-[13px] text-muted-foreground">
            <span className="sr-only">Reason: </span>“{booking.cancelReason}”
          </p>
        ) : null}
        {error ? (
          <Alert variant="destructive" role="alert" className="mt-1">
            {error}
          </Alert>
        ) : null}
      </div>
      {active ? (
        <div className="flex gap-2 [&>*]:flex-1 md:[&>*]:flex-none">
          <Button
            variant="outline"
            disabled={cancelClosed}
            aria-describedby={cancelClosed ? noteId : undefined}
            onClick={() => setAskingCancel(true)}
          >
            {held ? 'Release' : 'Cancel'}
          </Button>
          {held ? (
            <Button onClick={() => confirm.mutate()} loading={confirm.isPending}>
              Confirm
            </Button>
          ) : null}
        </div>
      ) : null}
      <ConfirmDialog
        open={askingCancel}
        onOpenChange={setAskingCancel}
        title={held ? 'Release this hold?' : 'Cancel this booking?'}
        confirmLabel={held ? 'Yes, release' : 'Yes, cancel'}
        cancelLabel="Keep it"
        variant="destructive"
        busy={cancel.isPending}
        onConfirm={() => cancel.mutate()}
        field={<ReasonField label="Reason (optional)" value={reason} onChange={setReason} />}
      >
        <p>
          {booking.bookableName} at {booking.venueName}, {placeLabel(booking.venueCity, booking.venueCountryCode)}: {formatDay(booking.start, zone)} · {times}.
        </p>
        <p>The slot becomes free for others straight away.</p>
      </ConfirmDialog>
    </article>
  )
}
