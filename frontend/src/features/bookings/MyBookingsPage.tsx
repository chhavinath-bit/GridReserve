import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Tabs } from 'radix-ui'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { bookingApi, type MyBookingsFilter } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { Booking } from '@/api/types'
import { HoldTimer } from '@/components/patterns/HoldTimer'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { StatusBadge } from '@/components/patterns/StatusBadge'
import { Alert } from '@/components/ui/alert'
import { ConfirmDialog } from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { canCancel, effectiveStatus } from '@/lib/bookingStatus'
import { formatDay, formatRange, formatTime } from '@/lib/time'
import { useNow } from '@/lib/useNow'

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

export function MyBookingsPage() {
  const [tab, setTab] = useState<Tab>('upcoming')
  const [openedAt] = useState(() => new Date().toISOString())
  return (
    <>
      <PageHeader title="My bookings" description="Times are shown in each venue's time zone." />
      <Tabs.Root value={tab} onValueChange={(value) => setTab(value as Tab)}>
        <Tabs.List aria-label="Bookings" className="-mx-4 mb-5 flex gap-1 overflow-x-auto border-b px-4 sm:mx-0 sm:px-0">
          {TABS.map(({ id, label }) => (
            <Tabs.Trigger
              key={id}
              value={id}
              className="-mb-px shrink-0 border-b-2 border-transparent px-3 py-2.5 text-sm font-medium text-muted-foreground outline-none transition-colors duration-[120ms] hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring data-[state=active]:border-primary data-[state=active]:text-foreground"
            >
              {label}
            </Tabs.Trigger>
          ))}
        </Tabs.List>
        {TABS.map(({ id }) => (
          <Tabs.Content key={id} value={id} className="outline-none">
            <BookingList tab={id} openedAt={openedAt} />
          </Tabs.Content>
        ))}
      </Tabs.Root>
    </>
  )
}

function BookingList({ tab, openedAt }: { tab: Tab; openedAt: string }) {
  // Re-evaluated every few seconds, so a hold that runs out shows as expired by itself.
  const now = useNow(5_000)
  const filter = filterFor(tab, openedAt)
  const bookings = useInfiniteQuery({
    queryKey: queryKeys.myBookingsTab(tab),
    queryFn: ({ pageParam }) => bookingApi.mine(filter, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })

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
        <p className="text-sm text-subtle-foreground" aria-live="polite">
          Showing {all.length} of {total}
        </p>
        {bookings.hasNextPage ? (
          <Button variant="outline" size="touch" onClick={() => void bookings.fetchNextPage()} loading={bookings.isFetchingNextPage}>
            Load more
          </Button>
        ) : null}
        {bookings.isFetchNextPageError ? <ErrorState error={bookings.error} onRetry={() => void bookings.fetchNextPage()} /> : null}
      </div>
    </div>
  )
}

/** The day as a tile: "TUE / 4 / NOV" in the venue's zone. */
function DateTile({ booking }: { booking: Booking }) {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: booking.venueTimeZone, weekday: 'short', day: 'numeric', month: 'short' }).formatToParts(
    new Date(booking.start),
  )
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((candidate) => candidate.type === type)?.value ?? ''
  return (
    <div aria-hidden className="flex w-14 shrink-0 flex-col items-center rounded-md bg-primary-soft py-2 text-primary-soft-foreground">
      <span className="text-[11px] font-semibold uppercase">{part('weekday')}</span>
      <span className="text-xl font-bold leading-tight tabular">{part('day')}</span>
      <span className="text-[11px] uppercase">{part('month')}</span>
    </div>
  )
}

/** One booking (DESIGN_SYSTEM §4.2 BookingCard). A live hold can be confirmed or released from here, e.g. after a reload. */
function BookingCard({ booking, now }: { booking: Booking; now: number }) {
  const queryClient = useQueryClient()
  const [askingCancel, setAskingCancel] = useState(false)
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
    mutationFn: () => bookingApi.cancel(booking.id),
    onSuccess: () => {
      toast(status === 'HELD' ? 'Hold released' : 'Booking cancelled')
      setAskingCancel(false)
      settle()
    },
    onError,
  })

  const active = status === 'HELD' || (status === 'CONFIRMED' && new Date(booking.end).getTime() > now)
  // After the cutoff the button stays visible but disabled, with the reason next to it (DESIGN_SYSTEM §4.2).
  const cancelClosed = active && !canCancel(booking, now)
  const closedNote = `cancel-closed-${booking.id}`
  const when = formatRange(booking.start, booking.end, booking.venueTimeZone)
  const held = status === 'HELD'

  return (
    <article className="flex flex-col gap-4 rounded-lg border bg-card p-4 sm:flex-row sm:items-center sm:p-5">
      <div className="flex min-w-0 flex-1 gap-4">
        <DateTile booking={booking} />
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold">{booking.bookableName}</h3>
            <StatusBadge status={status} />
          </div>
          <span className="text-sm text-muted-foreground">
            {booking.venueName} · {booking.bookableLabel}
          </span>
          <span className="text-sm tabular">
            {when} <span className="text-subtle-foreground">({booking.venueTimeZone})</span>
          </span>
          {held ? <HoldTimer deadline={deadline} onExpire={settle} /> : null}
          {cancelClosed && booking.cancellableUntil ? (
            <span id={closedNote} className="text-sm text-subtle-foreground">
              Cancellations closed {formatDay(booking.cancellableUntil, booking.venueTimeZone)},{' '}
              {formatTime(booking.cancellableUntil, booking.venueTimeZone)}. Contact the venue to change this booking.
            </span>
          ) : null}
          {error ? (
            <Alert variant="destructive" role="alert" className="mt-1">
              {error}
            </Alert>
          ) : null}
        </div>
      </div>
      {active ? (
        <div className="flex flex-wrap gap-2 sm:flex-col sm:items-stretch">
          {held ? (
            <Button size="touch" onClick={() => confirm.mutate()} loading={confirm.isPending}>
              Confirm booking
            </Button>
          ) : null}
          <Button
            size="touch"
            variant="outline"
            disabled={cancelClosed}
            aria-describedby={cancelClosed ? closedNote : undefined}
            onClick={() => setAskingCancel(true)}
          >
            {held ? 'Release' : 'Cancel booking'}
          </Button>
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
      >
        <p>
          {booking.bookableName} at {booking.venueName}, {when}.
        </p>
        <p>The slot becomes free for others straight away.</p>
      </ConfirmDialog>
    </article>
  )
}
