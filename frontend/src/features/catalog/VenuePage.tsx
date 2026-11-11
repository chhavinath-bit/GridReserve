import { useQuery } from '@tanstack/react-query'
import { MapPin } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { BookableSummary, BookableType, Category, Venue } from '@/api/types'
import { usePhoneHeader } from '@/components/patterns/AppShell'
import { Breadcrumb } from '@/components/patterns/Breadcrumb'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { placeLabel } from '@/lib/place'
import { describeCutoff, describeDuration, hoursToday, plural, sameEveryDay, weekdayName, weeklyHours } from '@/lib/rules'

/** A venue's bookables, grouped by their type's label ("Court", "Seat"); never a hard-coded word (FR-UI-02). */
export function VenuePage() {
  const venueId = Number(useParams().venueId)
  const venue = useQuery({ queryKey: queryKeys.venue(venueId), queryFn: () => catalogApi.venue(venueId) })
  const bookables = useQuery({ queryKey: queryKeys.bookables(venueId), queryFn: () => catalogApi.bookables(venueId) })
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogApi.categories, staleTime: 10 * 60_000 })

  const category = categories.data?.find((candidate) => candidate.id === venue.data?.categoryId)
  usePhoneHeader(venue.data ? { title: venue.data.name, backTo: category ? `/?category=${category.id}` : '/', backLabel: 'Back to Browse' } : null)

  if (venue.isPending) return <LoadingState />
  if (venue.isError) return <ErrorState error={venue.error} onRetry={() => void venue.refetch()} />
  const daily = sameEveryDay(venue.data.openingHours)

  return (
    <>
      <div className="hidden md:block">
        <Breadcrumb
          items={[
            { label: category?.name ?? 'Browse', to: category ? `/?category=${category.id}` : '/' },
            { label: venue.data.name },
          ]}
        />
        <PageHeader
          title={venue.data.name}
          description={
            <>
              <span className="inline-flex items-center gap-1 font-semibold text-foreground">
                <MapPin className="size-3.5 text-primary-soft-foreground" aria-hidden />
                {placeLabel(venue.data.city, venue.data.countryCode)}
              </span>
              {venue.data.addressLine ? ` · ${venue.data.addressLine}` : ''} · times in {venue.data.timeZone}
              {daily ? <span className="lg:hidden"> · {daily}</span> : null}
            </>
          }
        />
      </div>
      <h1 className="sr-only md:hidden">{venue.data.name}</h1>
      <div className="mb-4 flex flex-col gap-3 md:hidden">
        <p className="text-sm text-muted-foreground">
          <span className="inline-flex items-center gap-1 font-semibold text-foreground">
                <MapPin className="size-3.5 text-primary-soft-foreground" aria-hidden />
                {placeLabel(venue.data.city, venue.data.countryCode)}
              </span>
          {venue.data.addressLine ? ` · ${venue.data.addressLine}` : ''} · times in {venue.data.timeZone}
        </p>
        <p className="flex justify-between rounded-md border bg-card px-3 py-2.5 text-sm">
          <span className="text-muted-foreground">Open today</span>
          <span className="font-semibold tabular">{hoursToday(venue.data.openingHours, venue.data.timeZone)}</span>
        </p>
      </div>
      <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
        <div className="min-w-0 flex-1">
          {bookables.isPending ? <LoadingState /> : null}
          {bookables.isError ? <ErrorState error={bookables.error} onRetry={() => void bookables.refetch()} /> : null}
          {bookables.data ? <BookableGroups bookables={bookables.data.items} category={category} /> : null}
          {category ? (
            <p className="mt-4 text-[13px] leading-relaxed text-subtle-foreground md:hidden">
              {describeCutoff(category.cancellationCutoffMinutes)}. Book up to {category.bookingHorizonDays} days ahead.
            </p>
          ) : null}
        </div>
        {/* Tablets read "open daily …" in the header; the week is listed when days differ. */}
        <div className={daily ? 'hidden lg:block' : 'hidden md:block'}>
          <OpeningHoursCard venue={venue.data} />
        </div>
      </div>
    </>
  )
}

/** "Court A" → "A", "Group Room 1" → "1", "Court 1 (clay)" → "1": the part that tells siblings apart. */
export function initialOf(name: string): string {
  const words = name.replace(/\(.*?\)/g, '').trim().split(/\s+/)
  return (words[words.length - 1]?.[0] ?? '?').toUpperCase()
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
            {plural(label)}
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
  const duration = type ? describeDuration(type) : null
  const exclusive = bookable.bookingModel === 'EXCLUSIVE'
  return (
    <article className="flex flex-col gap-3 rounded-lg border bg-card p-4 md:flex-row md:items-center md:gap-5 md:p-5">
      <div className="flex min-w-0 flex-1 items-center gap-3 md:gap-4">
        <span aria-hidden className="flex size-11 shrink-0 items-center justify-center rounded-md bg-primary-soft font-bold text-primary-soft-foreground md:size-12">
          {initialOf(bookable.name)}
        </span>
        <div className="min-w-0">
          <h3 className="font-semibold md:text-[17px]">{bookable.name}</h3>
          <p className="text-[13px] text-muted-foreground md:text-sm">
            <span className="hidden md:inline">{bookable.label} · </span>
            {duration}
            {category ? <span className="hidden md:inline"> · {describeCutoff(category.cancellationCutoffMinutes).toLowerCase()}</span> : null}
          </p>
        </div>
      </div>
      {/* CAPACITY bookables (reading halls, classes) are booked by session from Phase 2. */}
      {exclusive ? (
        <Button asChild size="lg" className="w-full md:w-auto">
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
