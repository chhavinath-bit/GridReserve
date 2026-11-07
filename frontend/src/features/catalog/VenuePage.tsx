import { useQuery } from '@tanstack/react-query'
import { ChevronRight } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { BookableSummary } from '@/api/types'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'

/** A venue's bookables, grouped by their type's label ("Court", "Seat"); never a hard-coded word (FR-UI-02). */
export function VenuePage() {
  const venueId = Number(useParams().venueId)
  const venue = useQuery({ queryKey: queryKeys.venue(venueId), queryFn: () => catalogApi.venue(venueId) })
  const bookables = useQuery({ queryKey: queryKeys.bookables(venueId), queryFn: () => catalogApi.bookables(venueId) })

  if (venue.isPending) return <LoadingState />
  if (venue.isError) return <ErrorState error={venue.error} onRetry={() => void venue.refetch()} />

  return (
    <>
      <PageHeader title={venue.data.name} description={`${venue.data.addressLine} · times in ${venue.data.timeZone}`} />
      {bookables.isPending ? <LoadingState /> : null}
      {bookables.isError ? <ErrorState error={bookables.error} onRetry={() => void bookables.refetch()} /> : null}
      {bookables.data ? <BookableGroups bookables={bookables.data.items} /> : null}
    </>
  )
}

function BookableGroups({ bookables }: { bookables: BookableSummary[] }) {
  if (bookables.length === 0) return <EmptyState title="Nothing to book here yet" />
  const groups = new Map<string, BookableSummary[]>()
  bookables.forEach((bookable) => groups.set(bookable.label, [...(groups.get(bookable.label) ?? []), bookable]))
  return (
    <div className="flex flex-col gap-8">
      {[...groups].map(([label, items]) => (
        <section key={label} aria-labelledby={`group-${label}`} className="flex flex-col gap-3">
          <h2 id={`group-${label}`} className="text-xl font-semibold">
            {label}
          </h2>
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((bookable) => (
              <li key={bookable.id}>
                <BookableLink bookable={bookable} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

function BookableLink({ bookable }: { bookable: BookableSummary }) {
  const content = (
    <Card className="flex items-center justify-between gap-3 p-4 transition-colors duration-[120ms] hover:border-primary">
      <span className="font-medium">{bookable.name}</span>
      {bookable.bookingModel === 'EXCLUSIVE' ? (
        <ChevronRight className="size-4 text-subtle-foreground" aria-hidden />
      ) : (
        <Badge variant="muted">Sessions coming soon</Badge>
      )}
    </Card>
  )
  // CAPACITY bookables (reading halls, classes) are booked by session from Phase 2.
  return bookable.bookingModel === 'EXCLUSIVE' ? (
    <Link to={`/bookables/${bookable.id}`} className="block rounded-md">
      {content}
    </Link>
  ) : (
    content
  )
}
