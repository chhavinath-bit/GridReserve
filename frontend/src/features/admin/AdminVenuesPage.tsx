import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { VenueSummary } from '@/api/types'
import { ChipGroup } from '@/components/patterns/ChipGroup'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableCell, TableHead } from '@/components/ui/table'

/** Every venue, closed ones included (FR-CAT-07, FR-CAT-13). Table from 768 px, cards on phones. */
export function AdminVenuesPage() {
  const [params, setParams] = useSearchParams()
  const categoryId = Number(params.get('category')) || undefined
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogApi.categories, staleTime: 10 * 60_000 })
  const venues = useQuery({
    queryKey: [...queryKeys.adminVenues, categoryId ?? 'all'],
    queryFn: () => catalogApi.venues(categoryId, true),
  })
  const categoryName = new Map(categories.data?.map((category) => [category.id, category.name]))

  return (
    <>
      <PageHeader title="Venues" description="Create venues, change their hours, or close them for new bookings.">
        <Button asChild size="touch">
          <Link to="/admin/venues/new">
            <Plus aria-hidden />
            New venue
          </Link>
        </Button>
      </PageHeader>
      {categories.data ? (
        <div className="mb-4">
          <ChipGroup
            label="Category"
            chips={[{ value: undefined, label: 'All' }, ...categories.data.map((category) => ({ value: category.id, label: category.name }))]}
            value={categoryId}
            onChange={(id) => setParams(id ? { category: String(id) } : {})}
          />
        </div>
      ) : null}
      {venues.isPending ? <LoadingState /> : null}
      {venues.isError ? <ErrorState error={venues.error} onRetry={() => void venues.refetch()} /> : null}
      {venues.data && venues.data.items.length === 0 ? <EmptyState title="No venues yet" /> : null}
      {venues.data && venues.data.items.length > 0 ? (
        <>
          <div className="hidden md:block">
            <Table>
              <thead>
                <tr>
                  <TableHead>Venue</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Time zone</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </tr>
              </thead>
              <tbody>
                {venues.data.items.map((venue) => (
                  <tr key={venue.id}>
                    <TableCell>
                      <span className="block font-medium">{venue.name}</span>
                      <span className="block text-muted-foreground">{venue.addressLine}</span>
                    </TableCell>
                    <TableCell>{categoryName.get(venue.categoryId) ?? '—'}</TableCell>
                    <TableCell className="text-muted-foreground">{venue.timeZone}</TableCell>
                    <TableCell>
                      <OpenBadge active={venue.active} />
                    </TableCell>
                    <TableCell className="text-right">
                      <EditLink venue={venue} />
                    </TableCell>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
          <ul className="flex flex-col gap-3 md:hidden">
            {venues.data.items.map((venue) => (
              <li key={venue.id} className="flex items-center gap-3 rounded-lg border bg-card p-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold">{venue.name}</span>
                    <OpenBadge active={venue.active} />
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {categoryName.get(venue.categoryId) ?? '—'} · {venue.timeZone}
                  </p>
                </div>
                <EditLink venue={venue} />
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </>
  )
}

export function OpenBadge({ active }: { active: boolean }) {
  return <Badge variant={active ? 'success' : 'muted'}>{active ? 'Open' : 'Closed'}</Badge>
}

function EditLink({ venue }: { venue: VenueSummary }) {
  return (
    <Button asChild variant="outline" size="sm">
      <Link to={`/admin/venues/${venue.id}`} aria-label={`Edit ${venue.name}`}>
        Edit
      </Link>
    </Button>
  )
}
