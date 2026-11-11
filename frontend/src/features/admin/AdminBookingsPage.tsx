import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CloudOff } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { adminApi, bookingApi, catalogApi, type AdminBookingsFilter } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { AdminBooking, BookingStatus } from '@/api/types'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { StatusBadge } from '@/components/patterns/StatusBadge'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Combobox } from '@/components/ui/combobox'
import { Table, TableCell, TableHead } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { placeLabel } from '@/lib/place'
import { addDays, formatDay, formatRange, formatTimeRange } from '@/lib/time'
import { AuditTimeline } from './AuditTimeline'

const ANY = 'any'
const STATUSES: BookingStatus[] = ['HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED']
const REASON_MAX = 200

/** The URL's filters as the API's: dates are whole UTC days, "to" includes its day. */
export function filterFromParams(params: URLSearchParams): AdminBookingsFilter {
  const from = params.get('from')
  const to = params.get('to')
  return {
    venueId: Number(params.get('venue')) || undefined,
    status: (STATUSES as string[]).includes(params.get('status') ?? '') ? (params.get('status') as BookingStatus) : undefined,
    from: from ? `${from}T00:00:00Z` : undefined,
    to: to ? `${addDays(to, 1)}T00:00:00Z` : undefined,
  }
}

/** Every member's bookings, newest start first, with filters, history and cancel (FR-ADM-01, FR-AUD-03). */
export function AdminBookingsPage() {
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const filter = filterFromParams(params)
  const page = Math.max(0, Number(params.get('page')) || 0)
  const venues = useQuery({ queryKey: [...queryKeys.adminVenues, 'all'], queryFn: () => catalogApi.venues(undefined, true) })
  const bookings = useQuery({
    queryKey: queryKeys.adminBookings(filter, page),
    queryFn: () => adminApi.bookings(filter, page),
    placeholderData: keepPreviousData,
  })
  const [historyOf, setHistoryOf] = useState<AdminBooking | null>(null)
  const [cancelling, setCancelling] = useState<AdminBooking | null>(null)

  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  return (
    <>
      <PageHeader title="All bookings" description="Newest start first. Times are in each venue’s zone." />
      <div className="mb-4 grid gap-2.5 sm:grid-cols-2 lg:flex lg:flex-wrap">
        <div className="lg:w-52">
          <Combobox
            aria-label="Venue"
            searchPlaceholder="Search venues"
            value={params.get('venue') ?? ANY}
            onValueChange={(value) => setParam('venue', value === ANY ? null : value)}
            options={[{ value: ANY, label: 'All venues' }, ...(venues.data?.items ?? []).map((venue) => ({ value: String(venue.id), label: venue.name }))]}
          />
        </div>
        <div className="lg:w-44">
          <Combobox
            aria-label="Status"
            searchPlaceholder="Search statuses"
            value={filter.status ?? ANY}
            onValueChange={(value) => setParam('status', value === ANY ? null : value)}
            options={[{ value: ANY, label: 'Any status' }, ...STATUSES.map((status) => ({ value: status, label: status[0] + status.slice(1).toLowerCase() }))]}
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <span className="w-12 shrink-0 lg:w-auto">From</span>
          <Input type="date" aria-label="Starts from (UTC)" value={params.get('from') ?? ''} onChange={(event) => setParam('from', event.target.value || null)} />
        </label>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <span className="w-12 shrink-0 lg:w-auto">Until</span>
          <Input type="date" aria-label="Starts until (UTC)" value={params.get('to') ?? ''} onChange={(event) => setParam('to', event.target.value || null)} />
        </label>
      </div>

      {bookings.isPending ? <LoadingState /> : null}
      {bookings.isError ? <ErrorState error={bookings.error} onRetry={() => void bookings.refetch()} /> : null}
      {bookings.data ? (
        <div className="flex flex-col gap-4">
          {/* identity-service down: the list still works, only names are missing (FS-33). */}
          {!bookings.data.memberDetailsAvailable ? (
            <Alert variant="warning">
              <CloudOff aria-hidden />
              <p>Member names are temporarily unavailable. Bookings are shown by member number.</p>
            </Alert>
          ) : null}
          {bookings.data.items.length === 0 ? (
            <EmptyState title="No bookings match these filters" />
          ) : (
            <BookingRows items={bookings.data.items} onOpen={setHistoryOf} />
          )}
          {bookings.data.totalPages > 1 ? (
            <nav aria-label="Pages" className="flex items-center justify-between gap-2">
              <Button variant="outline" size="touch" disabled={page === 0} onClick={() => setParam('page', String(page - 1))}>
                Previous
              </Button>
              <span className="text-sm text-muted-foreground tabular">
                Page {page + 1} of {bookings.data.totalPages} · {bookings.data.totalItems} bookings
              </span>
              <Button
                variant="outline"
                size="touch"
                disabled={page + 1 >= bookings.data.totalPages}
                onClick={() => setParam('page', String(page + 1))}
              >
                Next
              </Button>
            </nav>
          ) : null}
        </div>
      ) : null}

      <BookingDrawer
        row={historyOf}
        onClose={() => setHistoryOf(null)}
        onCancel={(row) => {
          setHistoryOf(null)
          setCancelling(row)
        }}
      />
      <CancelDialog
        row={cancelling}
        onClose={() => setCancelling(null)}
        onDone={() => {
          void queryClient.invalidateQueries({ queryKey: queryKeys.admin })
          void queryClient.invalidateQueries({ queryKey: queryKeys.availabilityAll })
        }}
      />
    </>
  )
}

function member(row: AdminBooking): { name: string; detail: string | null } {
  return row.memberDisplayName
    ? { name: row.memberDisplayName, detail: row.memberEmail }
    : { name: `Member ${row.memberId}`, detail: null }
}

function cancellable(row: AdminBooking): boolean {
  const { status, end } = row.booking
  return (status === 'HELD' || status === 'CONFIRMED') && Date.parse(end) > Date.now()
}

/** Click a row (or its bookable's name, for keyboards) to see its history and actions (design A). */
function BookingRows({ items, onOpen }: { items: AdminBooking[]; onOpen: (row: AdminBooking) => void }) {
  const opener = (row: AdminBooking) => (
    <button
      type="button"
      onClick={(event) => {
        event.stopPropagation()
        onOpen(row)
      }}
      className="rounded-sm text-left font-semibold underline-offset-4 hover:underline"
    >
      {row.booking.bookableName}
    </button>
  )
  return (
    <>
      <div className="hidden md:block">
        <Table>
          <thead>
            <tr>
              <TableHead>When</TableHead>
              <TableHead>Bookable</TableHead>
              <TableHead>Member</TableHead>
              <TableHead>Status</TableHead>
            </tr>
          </thead>
          <tbody>
            {items.map((row) => {
              const who = member(row)
              return (
                <tr key={row.booking.id} onClick={() => onOpen(row)} className="cursor-pointer transition-colors hover:bg-muted/60">
                  <TableCell className="tabular">
                    {formatRange(row.booking.start, row.booking.end, row.booking.venueTimeZone)}
                    <span className="block text-xs text-subtle-foreground">{row.booking.venueTimeZone}</span>
                  </TableCell>
                  <TableCell>
                    {opener(row)}
                    <span className="block text-[13px] text-muted-foreground">
                      {row.booking.venueName}, {row.booking.venueCity}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className="block">{who.name}</span>
                    {who.detail ? <span className="block text-[13px] text-muted-foreground">{who.detail}</span> : null}
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={row.booking.status} />
                  </TableCell>
                </tr>
              )
            })}
          </tbody>
        </Table>
      </div>
      <ul className="flex flex-col gap-3 md:hidden">
        {items.map((row) => {
          const who = member(row)
          return (
            <li key={row.booking.id} onClick={() => onOpen(row)} className="flex cursor-pointer flex-col gap-1.5 rounded-lg border bg-card p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  {opener(row)}
                  <p className="text-[13px] text-muted-foreground">
                    {row.booking.venueName}, {row.booking.venueCity}
                  </p>
                </div>
                <StatusBadge status={row.booking.status} />
              </div>
              <p className="text-sm font-semibold tabular">{formatRange(row.booking.start, row.booking.end, row.booking.venueTimeZone)}</p>
              <p className="text-[13px] text-muted-foreground">{who.detail ? `${who.name} · ${who.detail}` : who.name}</p>
            </li>
          )
        })}
      </ul>
    </>
  )
}

function BookingDrawer({ row, onClose, onCancel }: { row: AdminBooking | null; onClose: () => void; onCancel: (row: AdminBooking) => void }) {
  const audit = useQuery({
    queryKey: queryKeys.audit(row?.booking.id ?? 0),
    queryFn: () => adminApi.audit(row!.booking.id),
    enabled: row !== null,
  })
  const zone = row?.booking.venueTimeZone ?? 'UTC'
  return (
    <Dialog open={row !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent side="right" className="gap-5">
        {row ? (
          <>
            <div className="flex flex-col gap-1 pr-8">
              <DialogTitle>
                {row.booking.bookableName} · {formatDay(row.booking.start, zone)}
              </DialogTitle>
              <DialogDescription>
                {formatTimeRange(row.booking.start, row.booking.end, zone)} · {member(row).name} · {row.booking.venueName}, {placeLabel(row.booking.venueCity, row.booking.venueCountryCode)}
              </DialogDescription>
              <div className="mt-1">
                <StatusBadge status={row.booking.status} />
              </div>
            </div>
            <h3 className="text-xs font-semibold uppercase tracking-wide text-subtle-foreground">History</h3>
            {audit.isPending ? <LoadingState rows={3} label="Loading history" /> : null}
            {audit.isError ? <ErrorState error={audit.error} onRetry={() => void audit.refetch()} /> : null}
            {audit.data ? <AuditTimeline entries={audit.data} zone={zone} /> : null}
            {cancellable(row) ? (
              <Button variant="destructive-outline" className="mt-auto" onClick={() => onCancel(row)}>
                Cancel this booking…
              </Button>
            ) : null}
          </>
        ) : (
          <DialogTitle className="sr-only">Booking</DialogTitle>
        )}
      </DialogContent>
    </Dialog>
  )
}

/** Admins may cancel any booking at any time, with an optional reason kept in the audit log (CXL-03). */
function CancelDialog({ row, onClose, onDone }: { row: AdminBooking | null; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('')
  const cancel = useMutation({
    mutationFn: (bookingId: number) => bookingApi.cancel(bookingId, reason.trim() || undefined),
    onSuccess: () => {
      toast.success('Booking cancelled')
      close()
      onDone()
    },
    onError: () => onDone(),
  })
  const close = () => {
    setReason('')
    cancel.reset()
    onClose()
  }
  const tooLong = reason.length > REASON_MAX
  return (
    <Dialog open={row !== null} onOpenChange={(open) => !open && close()}>
      <DialogContent>
        <DialogTitle>Cancel this booking?</DialogTitle>
        {row ? (
          <DialogDescription>
            {row.booking.bookableName} at {row.booking.venueName}, {row.booking.venueCity}: {formatRange(row.booking.start, row.booking.end, row.booking.venueTimeZone)}, for {member(row).name}. The slot becomes free straight away.
          </DialogDescription>
        ) : null}
        {cancel.error ? (
          <Alert variant="destructive" role="alert">
            {cancel.error instanceof ApiError ? cancel.error.message : 'Something went wrong. Please try again.'}
          </Alert>
        ) : null}
        <div className="flex flex-col gap-2">
          <Label htmlFor="cancel-reason">Reason (optional, kept in the history)</Label>
          <Textarea
            id="cancel-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            aria-invalid={tooLong ? true : undefined}
            aria-describedby="cancel-reason-hint"
          />
          <p id="cancel-reason-hint" className={tooLong ? 'text-sm text-destructive' : 'text-sm text-subtle-foreground'}>
            {reason.length} / {REASON_MAX} characters
          </p>
        </div>
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="ghost" onClick={close}>
            Keep it
          </Button>
          <Button variant="destructive" disabled={tooLong} loading={cancel.isPending} onClick={() => row && cancel.mutate(row.booking.id)}>
            Cancel booking
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
