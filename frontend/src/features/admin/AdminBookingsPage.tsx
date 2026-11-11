import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CloudOff, History } from 'lucide-react'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableCell, TableHead } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { addDays, formatRange } from '@/lib/time'
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
      <div className="mb-5 grid gap-4 rounded-lg border bg-card p-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="flex flex-col gap-2">
          <Label htmlFor="filter-venue">Venue</Label>
          <Select value={params.get('venue') ?? ANY} onValueChange={(value) => setParam('venue', value === ANY ? null : value)}>
            <SelectTrigger id="filter-venue">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Any venue</SelectItem>
              {venues.data?.items.map((venue) => (
                <SelectItem key={venue.id} value={String(venue.id)}>
                  {venue.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="filter-status">Status</Label>
          <Select value={filter.status ?? ANY} onValueChange={(value) => setParam('status', value === ANY ? null : value)}>
            <SelectTrigger id="filter-status">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Any status</SelectItem>
              {STATUSES.map((status) => (
                <SelectItem key={status} value={status}>
                  {status[0] + status.slice(1).toLowerCase()}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="filter-from">Starts from (UTC)</Label>
          <Input id="filter-from" type="date" value={params.get('from') ?? ''} onChange={(event) => setParam('from', event.target.value || null)} />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="filter-to">Starts until (UTC)</Label>
          <Input id="filter-to" type="date" value={params.get('to') ?? ''} onChange={(event) => setParam('to', event.target.value || null)} />
        </div>
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
            <BookingRows items={bookings.data.items} onHistory={setHistoryOf} onCancel={setCancelling} />
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

      <HistorySheet row={historyOf} onClose={() => setHistoryOf(null)} />
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

function BookingRows({ items, onHistory, onCancel }: { items: AdminBooking[]; onHistory: (row: AdminBooking) => void; onCancel: (row: AdminBooking) => void }) {
  const actions = (row: AdminBooking) => (
    <div className="flex flex-wrap justify-end gap-2">
      <Button variant="ghost" size="sm" onClick={() => onHistory(row)} aria-label={`History of booking ${row.booking.id}`}>
        <History aria-hidden />
        History
      </Button>
      {cancellable(row) ? (
        <Button variant="outline" size="sm" onClick={() => onCancel(row)} aria-label={`Cancel booking ${row.booking.id}`}>
          Cancel
        </Button>
      ) : null}
    </div>
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
              <TableHead>
                <span className="sr-only">Actions</span>
              </TableHead>
            </tr>
          </thead>
          <tbody>
            {items.map((row) => {
              const who = member(row)
              return (
                <tr key={row.booking.id}>
                  <TableCell className="tabular">
                    {formatRange(row.booking.start, row.booking.end, row.booking.venueTimeZone)}
                    <span className="block text-xs text-subtle-foreground">{row.booking.venueTimeZone}</span>
                  </TableCell>
                  <TableCell>
                    <span className="block font-medium">{row.booking.bookableName}</span>
                    <span className="block text-muted-foreground">{row.booking.venueName}</span>
                  </TableCell>
                  <TableCell>
                    <span className="block">{who.name}</span>
                    {who.detail ? <span className="block text-muted-foreground">{who.detail}</span> : null}
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={row.booking.status} />
                  </TableCell>
                  <TableCell>{actions(row)}</TableCell>
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
            <li key={row.booking.id} className="flex flex-col gap-2 rounded-lg border bg-card p-4">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold">{row.booking.bookableName}</p>
                  <p className="text-sm text-muted-foreground">{row.booking.venueName}</p>
                </div>
                <StatusBadge status={row.booking.status} />
              </div>
              <p className="text-sm tabular">{formatRange(row.booking.start, row.booking.end, row.booking.venueTimeZone)}</p>
              <p className="text-sm text-muted-foreground">{who.detail ? `${who.name} · ${who.detail}` : who.name}</p>
              {actions(row)}
            </li>
          )
        })}
      </ul>
    </>
  )
}

function HistorySheet({ row, onClose }: { row: AdminBooking | null; onClose: () => void }) {
  const audit = useQuery({
    queryKey: queryKeys.audit(row?.booking.id ?? 0),
    queryFn: () => adminApi.audit(row!.booking.id),
    enabled: row !== null,
  })
  return (
    <Dialog open={row !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent side="right">
        <DialogTitle>Booking history</DialogTitle>
        {row ? (
          <>
            <DialogDescription>
              {row.booking.bookableName} at {row.booking.venueName}, {formatRange(row.booking.start, row.booking.end, row.booking.venueTimeZone)}
            </DialogDescription>
            {audit.isPending ? <LoadingState rows={3} label="Loading history" /> : null}
            {audit.isError ? <ErrorState error={audit.error} onRetry={() => void audit.refetch()} /> : null}
            {audit.data ? <AuditTimeline entries={audit.data} zone={row.booking.venueTimeZone} /> : null}
          </>
        ) : null}
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
            {row.booking.bookableName} at {row.booking.venueName}, {formatRange(row.booking.start, row.booking.end, row.booking.venueTimeZone)}, for {member(row).name}. The slot becomes free straight away.
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
