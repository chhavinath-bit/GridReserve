import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { bookingApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { Booking } from '@/api/types'
import { HoldTimer } from '@/components/patterns/HoldTimer'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { StatusBadge } from '@/components/patterns/StatusBadge'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { effectiveStatus } from '@/lib/bookingStatus'
import { formatRange } from '@/lib/time'
import { useNow } from '@/lib/useNow'
import { cn } from '@/lib/utils'

type Tab = 'upcoming' | 'past' | 'closed'

const TABS: { id: Tab; label: string }[] = [
  { id: 'upcoming', label: 'Upcoming' },
  { id: 'past', label: 'Past' },
  { id: 'closed', label: 'Cancelled & expired' },
]

/** Which tab a booking belongs to (DESIGN_SYSTEM §5 My bookings). */
export function tabOf(booking: Booking, now: number): Tab {
  const status = effectiveStatus(booking, now)
  if (status === 'CANCELLED' || status === 'EXPIRED') return 'closed'
  return new Date(booking.end).getTime() > now ? 'upcoming' : 'past'
}

export function MyBookingsPage() {
  const [tab, setTab] = useState<Tab>('upcoming')
  // Re-evaluated every few seconds, so a hold that runs out moves to "Cancelled & expired" by itself.
  const now = useNow(5_000)
  const bookings = useQuery({ queryKey: queryKeys.myBookings, queryFn: () => bookingApi.mine() })

  const items = (bookings.data?.items ?? []).filter((booking) => tabOf(booking, now) === tab)
  // Upcoming soonest first; the API sends newest start first, which suits past and closed lists.
  if (tab === 'upcoming') items.reverse()

  return (
    <>
      <PageHeader title="My bookings" />
      <div role="tablist" aria-label="Bookings" className="mb-4 flex gap-1 border-b">
        {TABS.map(({ id, label }) => (
          <button
            key={id}
            role="tab"
            type="button"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors duration-[120ms]',
              tab === id ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {label}
          </button>
        ))}
      </div>
      <div role="tabpanel" aria-label={TABS.find((candidate) => candidate.id === tab)?.label}>
        {bookings.isPending ? <LoadingState /> : null}
        {bookings.isError ? <ErrorState error={bookings.error} onRetry={() => void bookings.refetch()} /> : null}
        {bookings.data && items.length === 0 ? (
          <EmptyState title={tab === 'upcoming' ? 'Nothing booked yet' : 'Nothing here'}>
            {tab === 'upcoming' ? (
              <Link to="/" className="text-primary underline-offset-4 hover:underline">
                Find a slot
              </Link>
            ) : null}
          </EmptyState>
        ) : null}
        {items.length > 0 ? (
          <ul className="flex flex-col gap-3">
            {items.map((booking) => (
              <li key={booking.id}>
                <BookingCard booking={booking} now={now} />
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </>
  )
}

/** One booking (DESIGN_SYSTEM §4.2 BookingCard). A live hold can be confirmed or released from here, e.g. after a reload. */
function BookingCard({ booking, now }: { booking: Booking; now: number }) {
  const queryClient = useQueryClient()
  const [confirmingCancel, setConfirmingCancel] = useState(false)
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
      setConfirmingCancel(false)
      settle()
    },
    onError,
  })

  const active = status === 'HELD' || (status === 'CONFIRMED' && new Date(booking.end).getTime() > now)

  return (
    <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <span className="font-semibold">{booking.bookableName}</span>
          <StatusBadge status={status} />
        </div>
        <span className="text-sm text-muted-foreground">
          {booking.venueName} · {booking.bookableLabel}
        </span>
        <span className="text-sm tabular">
          {formatRange(booking.start, booking.end, booking.venueTimeZone)}{' '}
          <span className="text-subtle-foreground">({booking.venueTimeZone})</span>
        </span>
        {status === 'HELD' ? <HoldTimer deadline={deadline} onExpire={settle} /> : null}
        {error ? (
          <Alert variant="destructive" role="alert" className="mt-1">
            {error}
          </Alert>
        ) : null}
      </div>
      {active ? (
        <div className="flex flex-wrap gap-2">
          {status === 'HELD' ? (
            <Button size="sm" onClick={() => confirm.mutate()} loading={confirm.isPending}>
              Confirm booking
            </Button>
          ) : null}
          {confirmingCancel ? (
            <>
              <Button size="sm" variant="destructive" onClick={() => cancel.mutate()} loading={cancel.isPending}>
                {status === 'HELD' ? 'Yes, release' : 'Yes, cancel'}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setConfirmingCancel(false)}>
                Keep it
              </Button>
            </>
          ) : (
            <Button size="sm" variant="outline" onClick={() => setConfirmingCancel(true)}>
              {status === 'HELD' ? 'Release' : 'Cancel booking'}
            </Button>
          )}
        </div>
      ) : null}
    </Card>
  )
}
