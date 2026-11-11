import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Search } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { adminApi, catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { VenueSummary } from '@/api/types'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Combobox } from '@/components/ui/combobox'
import { Table, TableCell, TableHead } from '@/components/ui/table'
import { CloseReopenButton } from './CloseReopen'

const ALL = 'all'

/** Every venue, closed ones included (FR-CAT-07, FR-CAT-13). Table from 768 px, cards on phones (design A). */
export function AdminVenuesPage() {
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState('')
  const categoryId = Number(params.get('category')) || undefined
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogApi.categories, staleTime: 10 * 60_000 })
  const venues = useQuery({
    queryKey: [...queryKeys.adminVenues, categoryId ?? 'all'],
    queryFn: () => catalogApi.venues(categoryId, true),
  })
  const categoryName = new Map(categories.data?.map((category) => [category.id, category.name]))
  // All venues fit on one page (size 100), so the name search runs here.
  const needle = search.trim().toLowerCase()
  const shown = venues.data?.items.filter((venue) => !needle || `${venue.name} ${venue.addressLine}`.toLowerCase().includes(needle)) ?? []
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin })
    void queryClient.invalidateQueries({ queryKey: queryKeys.venuesAll })
  }

  return (
    <>
      <PageHeader title="Venues" description="Closing a venue stops new bookings. Existing bookings stay.">
        <Button asChild>
          <Link to="/admin/venues/new">
            <Plus aria-hidden />
            New venue
          </Link>
        </Button>
      </PageHeader>
      <div className="mb-4 flex flex-col gap-2.5 sm:flex-row">
        <div className="relative sm:w-72">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-subtle-foreground" aria-hidden />
          <Input type="search" aria-label="Search venues" placeholder="Search venues" className="pl-9" value={search} onChange={(event) => setSearch(event.target.value)} />
        </div>
        <div className="sm:w-52">
          <Combobox
            aria-label="Category"
            searchPlaceholder="Search categories"
            value={categoryId ? String(categoryId) : ALL}
            onValueChange={(value) => setParams(value === ALL ? {} : { category: value })}
            options={[{ value: ALL, label: 'All categories' }, ...(categories.data ?? []).map((category) => ({ value: String(category.id), label: category.name }))]}
          />
        </div>
      </div>
      {venues.isPending ? <LoadingState /> : null}
      {venues.isError ? <ErrorState error={venues.error} onRetry={() => void venues.refetch()} /> : null}
      {venues.data && shown.length === 0 ? <EmptyState title={needle ? 'No venue matches that search' : 'No venues yet'} /> : null}
      {shown.length > 0 ? (
        <>
          <div className="hidden md:block">
            <Table>
              <thead>
                <tr>
                  <TableHead>Venue</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Country · zone</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </tr>
              </thead>
              <tbody>
                {shown.map((venue) => (
                  <tr key={venue.id}>
                    <TableCell>
                      <span className="block font-semibold">{venue.name}</span>
                      <span className="block text-[13px] text-muted-foreground">{venue.addressLine}</span>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{categoryName.get(venue.categoryId) ?? '—'}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {venue.countryCode} · {venue.timeZone}
                    </TableCell>
                    <TableCell>
                      <OpenBadge active={venue.active} />
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-right">
                      <RowActions venue={venue} onDone={refresh} />
                    </TableCell>
                  </tr>
                ))}
              </tbody>
            </Table>
          </div>
          <ul className="flex flex-col gap-3 md:hidden">
            {shown.map((venue) => (
              <li key={venue.id} className="flex flex-col gap-3 rounded-lg border bg-card p-4">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold">{venue.name}</span>
                    <OpenBadge active={venue.active} />
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {categoryName.get(venue.categoryId) ?? '—'} · {venue.countryCode} · {venue.timeZone}
                  </p>
                </div>
                <RowActions venue={venue} onDone={refresh} />
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

function RowActions({ venue, onDone }: { venue: VenueSummary; onDone: () => void }) {
  return (
    <div className="inline-flex gap-1.5">
      <Button asChild variant="outline" size="sm">
        <Link to={`/admin/venues/${venue.id}`} aria-label={`Edit ${venue.name}`}>
          Edit
        </Link>
      </Button>
      <CloseReopenButton
        name={venue.name}
        active={venue.active}
        size="sm"
        onChange={(active) => adminApi.setVenueActive(venue.id, active)}
        onDone={onDone}
      />
    </div>
  )
}
