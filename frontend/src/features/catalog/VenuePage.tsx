import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { BookableSummary, BookableType, Category, Venue } from '@/api/types'
import { Breadcrumb } from '@/components/patterns/Breadcrumb'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { describeCutoff, describeDuration, weekdayName, weeklyHours } from '@/lib/rules'

/** A venue's bookables, grouped by their type's label ("Court", "Seat"); never a hard-coded word (FR-UI-02). */
export function VenuePage() {
  const venueId = Number(useParams().venueId)
  const venue = useQuery({ queryKey: queryKeys.venue(venueId), queryFn: () => catalogApi.venue(venueId) })
  const bookables = useQuery({ queryKey: queryKeys.bookables(venueId), queryFn: () => catalogApi.bookables(venueId) })
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogApi.categories, staleTime: 10 * 60_000 })

  if (venue.isPending) return <LoadingState />
  if (venue.isError) return <ErrorState error={venue.error} onRetry={() => void venue.refetch()} />
  const category = categories.data?.find((candidate) => candidate.id === venue.data.categoryId)

  return (
    <>
      <Breadcrumb
        items={[
          { label: category?.name ?? 'Browse', to: category ? `/?category=${category.id}` : '/' },
          { label: venue.data.name },
        ]}
      />
      <PageHeader
        title={venue.data.name}
        description={`${venue.data.addressLine} · ${venue.data.countryCode} · times in ${venue.data.timeZone}`}
      />
      <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
        <div className="min-w-0 flex-1">
          {bookables.isPending ? <LoadingState /> : null}
          {bookables.isError ? <ErrorState error={bookables.error} onRetry={() => void bookables.refetch()} /> : null}
          {bookables.data ? <BookableGroups bookables={bookables.data.items} category={category} /> : null}
        </div>
        <OpeningHoursCard venue={venue.data} />
      </div>
    </>
  )
}

function BookableGroups({ bookables, category }: { bookables: BookableSummary[]; category: Category | undefined }) {
  if (bookables.length === 0) return <EmptyState title="Nothing to book here yet" />
  const groups = new Map<string, BookableSummary[]>()
  bookables.forEach((bookable) => groups.set(bookable.label, [...(groups.get(bookable.label) ?? []), bookable]))
  const types = new Map(category?.bookableTypes.map((type) => [type.code, type]))
  return (
    <div className="flex flex-col gap-8">
      {[...groups].map(([label, items]) => (
        <section key={label} aria-labelledby={`group-${label}`} className="flex flex-col gap-3">
          <h2 id={`group-${label}`} className="text-lg font-semibold">
            {label}
          </h2>
          <ul className="flex flex-col gap-3">
            {items.map((bookable) => (
              <li key={bookable.id}>
                <BookableCard bookable={bookable} type={types.get(bookable.typeCode)} category={category} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

/** The type's default rules; a bookable's own overrides show on its booking page. */
function BookableCard({ bookable, type, category }: { bookable: BookableSummary; type: BookableType | undefined; category: Category | undefined }) {
  const rules = [type ? describeDuration(type) : null, category ? describeCutoff(category.cancellationCutoffMinutes) : null].filter(Boolean)
  const exclusive = bookable.bookingModel === 'EXCLUSIVE'
  return (
    <article className="flex flex-col gap-4 rounded-lg border bg-card p-4 sm:flex-row sm:items-center sm:p-5">
      <div className="flex min-w-0 flex-1 items-center gap-4">
        <span aria-hidden className="flex size-12 shrink-0 items-center justify-center rounded-md bg-primary-soft font-bold text-primary-soft-foreground">
          {bookable.name.trim().slice(-1).toUpperCase()}
        </span>
        <div className="min-w-0">
          <h3 className="font-semibold">{bookable.name}</h3>
          {rules.length > 0 ? <p className="text-sm text-muted-foreground">{rules.join(' · ')}</p> : null}
        </div>
      </div>
      {/* CAPACITY bookables (reading halls, classes) are booked by session from Phase 2. */}
      {exclusive ? (
        <Button asChild size="touch">
          <Link to={`/bookables/${bookable.id}`} aria-label={`See times for ${bookable.name}`}>
            See times
          </Link>
        </Button>
      ) : (
        <Badge variant="muted" className="self-start sm:self-center">
          Sessions coming soon
        </Badge>
      )}
    </article>
  )
}

function OpeningHoursCard({ venue }: { venue: Venue }) {
  return (
    <aside aria-labelledby="hours-title" className="rounded-lg border bg-card p-5 lg:sticky lg:top-24 lg:w-80 lg:shrink-0">
      <h2 id="hours-title" className="mb-3 font-semibold">
        Opening hours
      </h2>
      <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-2 text-sm tabular-nums">
        {weeklyHours(venue.openingHours).map(({ weekday, text }) => (
          <div key={weekday} className="contents">
            <dt className="text-muted-foreground">{weekdayName(weekday)}</dt>
            <dd className={text === 'Closed' ? 'text-subtle-foreground' : undefined}>{text}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-3 text-xs text-subtle-foreground">In {venue.timeZone}</p>
    </aside>
  )
}
