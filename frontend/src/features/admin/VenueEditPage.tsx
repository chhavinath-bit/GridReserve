import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Form, Formik, type FormikHelpers } from 'formik'
import { Plus } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import * as Yup from 'yup'
import { adminApi, catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { Category, Venue, VenueInput } from '@/api/types'
import { Breadcrumb } from '@/components/patterns/Breadcrumb'
import { FormErrors } from '@/components/patterns/FormErrors'
import { FormField } from '@/components/patterns/FormField'
import { PageHeader } from '@/components/patterns/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { OpenBadge } from './AdminVenuesPage'
import { StatusPanel } from './CloseReopen'
import { CountryZoneFields } from './CountryZoneFields'
import { SelectField } from './fields'
import { fromDays, hoursProblem, toDays, type DayHours } from './hours'
import { applyServerError, type FormStatus } from './serverErrors'
import { WeeklyHoursEditor } from './WeeklyHoursEditor'

interface VenueValues {
  categoryId: string
  name: string
  addressLine: string
  /** Only narrows the city list; the venue's country is its city's (D-48). */
  countryCode: string
  cityId: string
  hours: DayHours[]
}

const FIELDS = [
  { name: 'categoryId', label: 'Category' },
  { name: 'name', label: 'Name' },
  { name: 'addressLine', label: 'Address' },
  { name: 'countryCode', label: 'Country' },
  { name: 'cityId', label: 'City' },
  { name: 'hours', label: 'Opening hours' },
]

// The same limits as CreateVenueRequest / UpdateVenueRequest; the server still decides.
const schema = Yup.object({
  categoryId: Yup.string().required('Choose a category.'),
  name: Yup.string().trim().required('Enter a name.').max(100, 'Use at most 100 characters.'),
  addressLine: Yup.string().max(200, 'Use at most 200 characters.'),
  countryCode: Yup.string().required('Choose a country.'),
  cityId: Yup.string().required('Choose the city, so members see where it is.'),
  hours: Yup.mixed<DayHours[]>().test('hours', (days, context) => {
    const problem = hoursProblem(days ?? [])
    return problem ? context.createError({ message: problem }) : true
  }),
})

/** Create a venue (/admin/venues/new) or edit one (/admin/venues/:venueId) (FR-CAT-07, FR-CAT-15, FR-CAT-16). */
export function VenueEditPage() {
  const id = useParams().venueId
  const venueId = id === undefined ? null : Number(id)
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogApi.categories, staleTime: 10 * 60_000 })
  const venue = useQuery({
    queryKey: queryKeys.adminVenue(venueId ?? 0),
    queryFn: () => catalogApi.venue(venueId!),
    enabled: venueId !== null,
  })

  if (categories.isPending || (venueId !== null && venue.isPending)) return <LoadingState />
  if (categories.isError) return <ErrorState error={categories.error} onRetry={() => void categories.refetch()} />
  if (venue.isError) return <ErrorState error={venue.error} onRetry={() => void venue.refetch()} />
  // A new key per version: after a save or a reload the form starts from what the server has.
  return <VenueEditor key={venue.data?.version ?? 'new'} categories={categories.data} venue={venue.data ?? null} onReload={() => void venue.refetch()} />
}

function VenueEditor({ categories, venue, onReload }: { categories: Category[]; venue: Venue | null; onReload: () => void }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const initial: VenueValues = {
    categoryId: venue ? String(venue.categoryId) : '',
    name: venue?.name ?? '',
    addressLine: venue?.addressLine ?? '',
    countryCode: venue?.countryCode ?? '',
    cityId: venue ? String(venue.cityId) : '',
    hours: toDays(venue?.openingHours ?? []),
  }

  const refreshLists = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin })
    void queryClient.invalidateQueries({ queryKey: queryKeys.venuesAll })
    if (venue) void queryClient.invalidateQueries({ queryKey: queryKeys.venue(venue.id) })
  }

  const onSubmit = async (values: VenueValues, helpers: FormikHelpers<VenueValues>) => {
    helpers.setStatus(null)
    const input: VenueInput = {
      name: values.name.trim(),
      addressLine: values.addressLine.trim() || null,
      cityId: Number(values.cityId),
      openingHours: fromDays(values.hours),
    }
    try {
      const saved = venue
        ? await adminApi.updateVenue(venue.id, { ...input, version: venue.version })
        : await adminApi.createVenue({ ...input, categoryId: Number(values.categoryId) })
      toast.success(venue ? 'Venue saved' : 'Venue created')
      refreshLists()
      if (venue) queryClient.setQueryData(queryKeys.adminVenue(saved.id), saved)
      else navigate(`/admin/venues/${saved.id}`, { replace: true })
    } catch (cause) {
      applyServerError(cause, helpers, FIELDS.map((field) => field.name), (field) => (field.startsWith('openingHours') ? 'hours' : field))
    }
  }

  const category = categories.find((candidate) => String(candidate.id) === initial.categoryId)
  return (
    <div className="mx-auto max-w-[1080px]">
      <Breadcrumb items={[{ label: 'Venues', to: '/admin/venues' }, { label: venue?.name ?? 'New venue' }]} />
      <PageHeader
        title={venue ? `Edit ${venue.name}` : 'New venue'}
        description={venue ? `${category?.name ?? ''} · ${venue.timeZone}` : 'Its category can’t be changed later.'}
      />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_300px] lg:items-start">
        <Formik<VenueValues> initialValues={initial} validationSchema={schema} onSubmit={onSubmit}>
          {({ isSubmitting, status }) => {
            const formStatus = status as FormStatus | null
            return (
              <Form noValidate className="flex flex-col gap-4">
                {formStatus?.code === 'STALE_UPDATE' ? (
                  <Alert variant="warning" role="alert">
                    <div className="flex flex-1 flex-col gap-2">
                      <p>{formStatus.message}</p>
                      <div>
                        <Button type="button" variant="outline" size="sm" onClick={onReload}>
                          Reload
                        </Button>
                      </div>
                    </div>
                  </Alert>
                ) : (
                  <FormErrors fields={FIELDS} formError={formStatus?.message ?? null} />
                )}
                <section className="grid gap-4 rounded-lg border bg-card p-5 sm:grid-cols-2">
                  <FormField name="name" label="Name" autoComplete="off" />
                  <SelectField
                    name="categoryId"
                    label="Category"
                    disabled={venue !== null}
                    options={categories.map((candidate) => ({ value: String(candidate.id), label: candidate.name }))}
                  />
                  <FormField name="addressLine" label="Address" autoComplete="off" hint="Street and number." />
                </section>
                <section aria-labelledby="location-title" className="flex flex-col gap-4 rounded-lg border bg-card p-5">
                  <h2 id="location-title" className="font-semibold">
                    Location
                  </h2>
                  <CountryZoneFields />
                </section>
                <section className="rounded-lg border bg-card p-5">
                  <WeeklyHoursEditor name="hours" legend="Opening hours (in the venue’s time zone; unticked days are closed)" />
                </section>
                <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <Button asChild variant="outline">
                    <Link to="/admin/venues">Cancel</Link>
                  </Button>
                  <Button type="submit" loading={isSubmitting}>
                    {venue ? 'Save changes' : 'Create venue'}
                  </Button>
                </div>
              </Form>
            )
          }}
        </Formik>
        {venue ? (
          <div className="flex flex-col gap-4">
            <StatusPanel
              name={venue.name}
              active={venue.active}
              onChange={(active) => adminApi.setVenueActive(venue.id, active)}
              onDone={() => {
                refreshLists()
                onReload()
              }}
            />
            <VenueBookables venueId={venue.id} />
          </div>
        ) : null}
      </div>
    </div>
  )
}

function VenueBookables({ venueId }: { venueId: number }) {
  const bookables = useQuery({ queryKey: queryKeys.adminBookables(venueId), queryFn: () => catalogApi.bookables(venueId, true) })
  return (
    <section aria-labelledby="bookables-title" className="flex flex-col gap-3 rounded-lg border bg-card p-5">
      <div className="flex items-center justify-between gap-2">
        <h2 id="bookables-title" className="font-semibold">
          Bookables
        </h2>
        <Button asChild variant="outline" size="sm">
          <Link to={`/admin/venues/${venueId}/bookables/new`}>
            <Plus aria-hidden />
            Add
          </Link>
        </Button>
      </div>
      {bookables.isPending ? <LoadingState rows={2} /> : null}
      {bookables.isError ? <ErrorState error={bookables.error} onRetry={() => void bookables.refetch()} /> : null}
      {bookables.data && bookables.data.items.length === 0 ? <EmptyState title="None yet" /> : null}
      {bookables.data && bookables.data.items.length > 0 ? (
        <ul className="flex flex-col divide-y">
          {bookables.data.items.map((bookable) => (
            <li key={bookable.id} className="flex items-center justify-between gap-2 py-2">
              <Link to={`/admin/bookables/${bookable.id}`} className="min-w-0 rounded-sm text-sm font-medium hover:underline underline-offset-4">
                {bookable.name}
                <span className="block text-xs font-normal text-muted-foreground">{bookable.label}</span>
              </Link>
              <OpenBadge active={bookable.active} />
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  )
}
