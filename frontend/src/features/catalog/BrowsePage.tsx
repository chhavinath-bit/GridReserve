import { useQuery } from '@tanstack/react-query'
import { ArrowRight, MapPin } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { Category, VenueSummary } from '@/api/types'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

/**
 * Category → venues (FR-UI-01/02). The category chips act as the dropdown: one press filters the
 * list. The chosen category is in the URL, so back/forward and reload keep it; no category = all.
 */
export function BrowsePage() {
  const [params, setParams] = useSearchParams()
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogApi.categories, staleTime: 10 * 60_000 })
  const categoryId = Number(params.get('category')) || undefined

  return (
    <>
      <PageHeader title="Find a place to book" description="Pick a category, then a venue. Times are shown in each venue's own time zone." />
      {categories.isPending ? <LoadingState rows={1} /> : null}
      {categories.isError ? <ErrorState error={categories.error} onRetry={() => void categories.refetch()} /> : null}
      {categories.data ? (
        <div className="flex flex-col gap-6">
          <CategoryChips
            categories={categories.data}
            value={categoryId}
            onChange={(id) => setParams(id ? { category: String(id) } : {})}
          />
          <VenueList categories={categories.data} categoryId={categoryId} />
        </div>
      ) : null}
    </>
  )
}

function CategoryChips({ categories, value, onChange }: { categories: Category[]; value: number | undefined; onChange: (id: number | undefined) => void }) {
  const chips = [{ id: undefined, name: 'All' }, ...categories]
  return (
    <div role="group" aria-label="Category" className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0">
      {chips.map((chip) => {
        const selected = chip.id === value
        return (
          <button
            key={chip.id ?? 'all'}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(chip.id)}
            className={cn(
              'h-9 shrink-0 rounded-full border px-4 text-sm font-semibold transition-colors duration-[120ms] outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
              selected ? 'border-foreground bg-foreground text-background' : 'bg-card text-muted-foreground hover:text-foreground',
            )}
          >
            {chip.name}
          </button>
        )
      })}
    </div>
  )
}

function VenueList({ categories, categoryId }: { categories: Category[]; categoryId: number | undefined }) {
  const venues = useQuery({ queryKey: queryKeys.venues(categoryId), queryFn: () => catalogApi.venues(categoryId) })
  if (venues.isPending) return <LoadingState />
  if (venues.isError) return <ErrorState error={venues.error} onRetry={() => void venues.refetch()} />
  if (venues.data.items.length === 0) return <EmptyState title="No venues in this category yet" />
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
        <span className="flex items-center gap-1 text-sm text-muted-foreground">
          <MapPin className="size-3.5 shrink-0" aria-hidden />
          {venue.addressLine}
        </span>
      </span>
      <span className="mt-auto flex items-center justify-end gap-1 border-t pt-3 text-sm font-semibold text-primary-soft-foreground">
        View
        <ArrowRight className="size-4 transition-transform duration-[120ms] group-hover:translate-x-0.5 motion-reduce:transition-none" aria-hidden />
      </span>
    </Link>
  )
}
