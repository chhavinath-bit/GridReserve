import { useQuery } from '@tanstack/react-query'
import { ArrowRight, MapPin } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { Category, VenueSummary } from '@/api/types'
import { ChipGroup } from '@/components/patterns/ChipGroup'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Badge } from '@/components/ui/badge'
import { Combobox } from '@/components/ui/combobox'
import { parseCityId, placeLabel, placesOf } from '@/lib/place'

/**
 * Category → venues (FR-UI-01/02), narrowed by place (FR-CAT-16). The category chips act as the dropdown:
 * one press filters the list; Location is a searchable list of the cities that have venues. Both live in
 * the URL (?category=2&place=14, a city ID), so back/forward and reload keep them; none = everything.
 */
export function BrowsePage() {
  const [params, setParams] = useSearchParams()
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogApi.categories, staleTime: 10 * 60_000 })
  // Every venue once, for the Location options (all venues fit in one page of 100).
  const everyVenue = useQuery({ queryKey: queryKeys.venues(), queryFn: () => catalogApi.venues() })
  const categoryId = Number(params.get('category')) || undefined
  const cityId = parseCityId(params.get('place'))
  const places = placesOf(everyVenue.data?.items ?? [])

  const setFilter = (key: 'category' | 'place', value: string | undefined) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next)
  }

  return (
    <>
      <PageHeader title="Find a place to book" description="Pick a category and a location, then a venue. Times are shown in each venue's own time zone." />
      {categories.isPending ? <LoadingState rows={1} /> : null}
      {categories.isError ? <ErrorState error={categories.error} onRetry={() => void categories.refetch()} /> : null}
      {categories.data ? (
        <div className="flex flex-col gap-6">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <ChipGroup
              label="Category"
              chips={[{ value: undefined, label: 'All' }, ...categories.data.map((category) => ({ value: category.id, label: category.name }))]}
              value={categoryId}
              onChange={(id) => setFilter('category', id ? String(id) : undefined)}
            />
            <div className="flex items-center gap-2 lg:w-72">
              <MapPin className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <div className="min-w-0 flex-1">
                <Combobox
                  aria-label="Location"
                  searchPlaceholder="Search cities"
                  value={cityId ? String(cityId) : ANYWHERE}
                  onValueChange={(value) => setFilter('place', value === ANYWHERE ? undefined : value)}
                  options={[{ value: ANYWHERE, label: 'Anywhere' }, ...places]}
                />
              </div>
            </div>
          </div>
          <VenueList
            categories={categories.data}
            categoryId={categoryId}
            cityId={cityId}
            placeName={places.find((place) => place.value === String(cityId))?.label}
          />
        </div>
      ) : null}
    </>
  )
}

const ANYWHERE = 'anywhere'

function VenueList(props: { categories: Category[]; categoryId: number | undefined; cityId: number | undefined; placeName: string | undefined }) {
  const { categories, categoryId, cityId, placeName } = props
  const venues = useQuery({
    queryKey: queryKeys.venues(categoryId, cityId ? String(cityId) : undefined),
    queryFn: () => catalogApi.venues(categoryId, false, cityId),
  })
  if (venues.isPending) return <LoadingState />
  if (venues.isError) return <ErrorState error={venues.error} onRetry={() => void venues.refetch()} />
  if (venues.data.items.length === 0) {
    if (!cityId) return <EmptyState title="No venues in this category yet" />
    return <EmptyState title={placeName ? `No venues here in ${placeName}` : 'No venues here in this city'} />
  }
  const categoryName = new Map(categories.map((category) => [category.id, category.name]))
  return (
    <ul className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {venues.data.items.map((venue) => (
        <li key={venue.id}>
          <VenueCard venue={venue} categoryName={categoryName.get(venue.categoryId)} />
        </li>
      ))}
    </ul>
  )
}

/** The whole card is one link (DESIGN_SYSTEM §4.2 VenueCard). */
function VenueCard({ venue, categoryName }: { venue: VenueSummary; categoryName: string | undefined }) {
  return (
    <Link
      to={`/venues/${venue.id}`}
      className="group flex h-full flex-col gap-3 rounded-lg border bg-card p-5 transition-colors duration-[120ms] hover:border-primary"
    >
      <span className="flex items-center justify-between gap-2">
        {categoryName ? (
          <Badge variant="primary">{categoryName}</Badge>
        ) : (
          <span />
        )}
        <span className="text-xs text-subtle-foreground">{venue.timeZone}</span>
      </span>
      <span className="flex flex-col gap-1">
        <span className="text-lg font-semibold">{venue.name}</span>
        {/* Where it is comes first and bold, so nobody books in the wrong city by mistake. */}
        <span className="flex items-center gap-1 text-sm font-semibold">
          <MapPin className="size-3.5 shrink-0 text-primary-soft-foreground" aria-hidden />
          {placeLabel(venue.city, venue.countryCode)}
        </span>
        {venue.addressLine ? <span className="pl-[18px] text-sm text-muted-foreground">{venue.addressLine}</span> : null}
      </span>
      <span className="mt-auto flex items-center justify-end gap-1 border-t pt-3 text-sm font-semibold text-primary-soft-foreground">
        View
        <ArrowRight className="size-4 transition-transform duration-[120ms] group-hover:translate-x-0.5 motion-reduce:transition-none" aria-hidden />
      </span>
    </Link>
  )
}
