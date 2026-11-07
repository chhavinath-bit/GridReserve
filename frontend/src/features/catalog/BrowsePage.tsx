import { useQuery } from '@tanstack/react-query'
import { ChevronRight, MapPin } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { Category } from '@/api/types'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Card } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

/** Category → venues (FR-UI-01/02). The chosen category is in the URL, so back/forward and reload keep it. */
export function BrowsePage() {
  const [params, setParams] = useSearchParams()
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogApi.categories, staleTime: 10 * 60_000 })
  const categoryId = Number(params.get('category')) || categories.data?.[0]?.id

  return (
    <>
      <PageHeader title="Find a place to book" description="Choose a category, then a venue." />
      {categories.isPending ? <LoadingState rows={1} /> : null}
      {categories.isError ? <ErrorState error={categories.error} onRetry={() => void categories.refetch()} /> : null}
      {categories.data ? (
        <div className="flex flex-col gap-6">
          <CategorySelect
            categories={categories.data}
            value={categoryId}
            onChange={(id) => setParams({ category: String(id) })}
          />
          {categoryId ? <VenueList categoryId={categoryId} /> : <EmptyState title="No categories yet" />}
        </div>
      ) : null}
    </>
  )
}

function CategorySelect({ categories, value, onChange }: { categories: Category[]; value: number | undefined; onChange: (id: number) => void }) {
  return (
    <div className="flex max-w-xs flex-col gap-2">
      <Label htmlFor="category">Category</Label>
      <Select value={value ? String(value) : undefined} onValueChange={(id) => onChange(Number(id))}>
        <SelectTrigger id="category">
          <SelectValue placeholder="Choose a category" />
        </SelectTrigger>
        <SelectContent>
          {categories.map((category) => (
            <SelectItem key={category.id} value={String(category.id)}>
              {category.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

function VenueList({ categoryId }: { categoryId: number }) {
  const venues = useQuery({ queryKey: queryKeys.venues(categoryId), queryFn: () => catalogApi.venues(categoryId) })
  if (venues.isPending) return <LoadingState />
  if (venues.isError) return <ErrorState error={venues.error} onRetry={() => void venues.refetch()} />
  if (venues.data.items.length === 0) return <EmptyState title="No venues in this category yet" />
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {venues.data.items.map((venue) => (
        <li key={venue.id}>
          {/* The whole card is one link (DESIGN_SYSTEM §4.2 VenueCard). */}
          <Link to={`/venues/${venue.id}`} className="block rounded-md">
            <Card className="flex items-center justify-between gap-3 p-4 transition-colors duration-[120ms] hover:border-primary">
              <div className="flex flex-col gap-1">
                <span className="font-semibold">{venue.name}</span>
                <span className="flex items-center gap-1 text-sm text-muted-foreground">
                  <MapPin className="size-3.5" aria-hidden />
                  {venue.addressLine}
                </span>
                <span className="text-xs text-subtle-foreground">{venue.timeZone}</span>
              </div>
              <ChevronRight className="size-4 text-subtle-foreground" aria-hidden />
            </Card>
          </Link>
        </li>
      ))}
    </ul>
  )
}
