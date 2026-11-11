import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Form, Formik, type FormikHelpers } from 'formik'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import * as Yup from 'yup'
import { adminApi, catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import type { Bookable, BookableInput, BookableType, Category, Venue } from '@/api/types'
import { Breadcrumb } from '@/components/patterns/Breadcrumb'
import { FormErrors } from '@/components/patterns/FormErrors'
import { FormField } from '@/components/patterns/FormField'
import { PageHeader } from '@/components/patterns/PageHeader'
import { ErrorState, LoadingState } from '@/components/patterns/States'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { formatMinutes } from '@/lib/rules'
import { CloseReopen } from './CloseReopen'
import { SelectField } from './fields'
import { fromDays, hoursProblem, toDays, type DayHours } from './hours'
import { applyServerError, type FormStatus } from './serverErrors'
import { WeeklyHoursEditor } from './WeeklyHoursEditor'

const DEFAULT = 'default'

interface BookableValues {
  bookableTypeId: string
  name: string
  /** "default" or "15" / "30" / "60". */
  slotMinutes: string
  /** Blank = the type's default (FR-CAT-04). */
  minDurationMinutes: string
  maxDurationMinutes: string
  bufferMinutes: string
  ownHours: boolean
  hours: DayHours[]
}

const FIELDS = [
  { name: 'bookableTypeId', label: 'Type' },
  { name: 'name', label: 'Name' },
  { name: 'slotMinutes', label: 'Slot length' },
  { name: 'minDurationMinutes', label: 'Shortest booking' },
  { name: 'maxDurationMinutes', label: 'Longest booking' },
  { name: 'bufferMinutes', label: 'Buffer' },
  { name: 'hours', label: 'Opening hours' },
]

const optionalWhole = (max: number, min = 1) =>
  Yup.string()
    .matches(/^\d*$/, 'Enter whole minutes, or leave it empty for the default.')
    .test('range', `Use ${min} to ${max} minutes.`, (value) => !value || (Number(value) >= min && Number(value) <= max))

// The same limits as CreateBookableRequest and CatalogValidator.rules; the server checks the combination again.
const schema = Yup.object({
  bookableTypeId: Yup.string().required('Choose a type.'),
  name: Yup.string().trim().required('Enter a name.').max(100, 'Use at most 100 characters.'),
  minDurationMinutes: optionalWhole(1440),
  maxDurationMinutes: optionalWhole(1440),
  bufferMinutes: optionalWhole(120, 0),
  hours: Yup.mixed<DayHours[]>().test('hours', (days, context) => {
    if (!(context.parent as BookableValues).ownHours) return true
    const problem = hoursProblem(days ?? [])
    return problem ? context.createError({ message: problem }) : true
  }),
})

/** Create a bookable (/admin/venues/:venueId/bookables/new) or edit one (/admin/bookables/:bookableId) (FR-CAT-08, FR-CAT-12). */
export function BookableEditPage() {
  const params = useParams()
  const bookableId = params.bookableId === undefined ? null : Number(params.bookableId)
  const bookable = useQuery({
    queryKey: queryKeys.adminBookable(bookableId ?? 0),
    queryFn: () => catalogApi.bookable(bookableId!),
    enabled: bookableId !== null,
  })
  const venueId = bookable.data?.venueId ?? Number(params.venueId)
  const venue = useQuery({
    queryKey: queryKeys.adminVenue(venueId),
    queryFn: () => catalogApi.venue(venueId),
    enabled: Number.isFinite(venueId) && venueId > 0,
  })
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogApi.categories, staleTime: 10 * 60_000 })

  const failed = [bookable, venue, categories].find((query) => query.isError)
  if (failed) return <ErrorState error={failed.error} onRetry={() => void failed.refetch()} />
  if ((bookableId !== null && !bookable.data) || !venue.data || !categories.data) return <LoadingState />
  const category = categories.data.find((candidate) => candidate.id === venue.data.categoryId)
  if (!category) return <LoadingState />
  return (
    <BookableEditor
      key={bookable.data?.version ?? 'new'}
      venue={venue.data}
      category={category}
      bookable={bookable.data ?? null}
      onReload={() => void bookable.refetch()}
    />
  )
}

function BookableEditor({ venue, category, bookable, onReload }: { venue: Venue; category: Category; bookable: Bookable | null; onReload: () => void }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  // Seat sessions (CAPACITY) arrive in Phase 2; the server refuses them until then.
  const types = category.bookableTypes.filter((type) => type.bookingModel === 'EXCLUSIVE')
  const asText = (value: number | null | undefined) => (value == null ? '' : String(value))
  const initial: BookableValues = {
    bookableTypeId: bookable ? String(category.bookableTypes.find((type) => type.code === bookable.typeCode)?.id ?? '') : types.length === 1 ? String(types[0]!.id) : '',
    name: bookable?.name ?? '',
    slotMinutes: bookable?.overrides.slotMinutes ? String(bookable.overrides.slotMinutes) : DEFAULT,
    minDurationMinutes: asText(bookable?.overrides.minDurationMinutes),
    maxDurationMinutes: asText(bookable?.overrides.maxDurationMinutes),
    bufferMinutes: asText(bookable?.overrides.bufferMinutes),
    ownHours: bookable?.ownOpeningHours ?? false,
    hours: toDays(bookable?.ownOpeningHours ? bookable.openingHours : venue.openingHours),
  }

  const refreshLists = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin })
    void queryClient.invalidateQueries({ queryKey: queryKeys.bookables(venue.id) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.availabilityAll })
    if (bookable) void queryClient.invalidateQueries({ queryKey: queryKeys.bookable(bookable.id) })
  }

  const onSubmit = async (values: BookableValues, helpers: FormikHelpers<BookableValues>) => {
    helpers.setStatus(null)
    const number = (value: string) => (value === '' ? null : Number(value))
    const input: BookableInput = {
      bookableTypeId: Number(values.bookableTypeId),
      name: values.name.trim(),
      slotMinutes: values.slotMinutes === DEFAULT ? null : Number(values.slotMinutes),
      minDurationMinutes: number(values.minDurationMinutes),
      maxDurationMinutes: number(values.maxDurationMinutes),
      bufferMinutes: number(values.bufferMinutes),
      openingHours: values.ownHours ? fromDays(values.hours) : null,
    }
    try {
      const saved = bookable
        ? await adminApi.updateBookable(bookable.id, { ...input, version: bookable.version })
        : await adminApi.createBookable(venue.id, input)
      toast.success(bookable ? 'Saved. New rules apply to new bookings only.' : 'Bookable created')
      refreshLists()
      if (bookable) queryClient.setQueryData(queryKeys.adminBookable(saved.id), saved)
      else navigate(`/admin/bookables/${saved.id}`, { replace: true })
    } catch (cause) {
      applyServerError(cause, helpers, FIELDS.map((field) => field.name), (field) => (field.startsWith('openingHours') ? 'hours' : field))
    }
  }

  const title = bookable?.name ?? `New bookable at ${venue.name}`
  return (
    <>
      <Breadcrumb
        items={[
          { label: 'Venues', to: '/admin/venues' },
          { label: venue.name, to: `/admin/venues/${venue.id}` },
          { label: bookable?.name ?? 'New bookable' },
        ]}
      />
      <PageHeader title={title} description={`${category.name} · times in ${venue.timeZone}`}>
      </PageHeader>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <Formik<BookableValues> initialValues={initial} validationSchema={schema} onSubmit={onSubmit}>
          {({ isSubmitting, status, values, setFieldValue }) => {
            const formStatus = status as FormStatus | null
            const type = category.bookableTypes.find((candidate) => String(candidate.id) === values.bookableTypeId)
            return (
              <Form noValidate className="flex flex-col gap-5 rounded-lg border bg-card p-5 sm:p-6">
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
                <div className="grid gap-5 sm:grid-cols-2">
                  <SelectField name="bookableTypeId" label="Type" options={types.map((candidate) => ({ value: String(candidate.id), label: candidate.label }))} />
                  <FormField name="name" label="Name" autoComplete="off" />
                </div>
                <fieldset className="flex flex-col gap-4">
                  <legend className="mb-1 text-sm font-medium">Rules</legend>
                  <p className="-mt-2 text-sm text-muted-foreground">Leave a field empty to use the {type?.label.toLowerCase() ?? 'type'}’s default.</p>
                  <RuleFields type={type} />
                </fieldset>
                <div className="flex flex-col gap-3">
                  <label className="flex items-center gap-2 text-sm font-medium">
                    <input
                      type="checkbox"
                      className="size-4 accent-primary"
                      checked={values.ownHours}
                      onChange={(event) => void setFieldValue('ownHours', event.target.checked)}
                    />
                    Own opening hours (otherwise the venue’s)
                  </label>
                  {values.ownHours ? <WeeklyHoursEditor name="hours" legend="Opening hours for this bookable" /> : null}
                </div>
                <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <Button asChild variant="ghost" size="touch">
                    <Link to={`/admin/venues/${venue.id}`}>Cancel</Link>
                  </Button>
                  <Button type="submit" size="touch" loading={isSubmitting}>
                    {bookable ? 'Save changes' : 'Create bookable'}
                  </Button>
                </div>
              </Form>
            )
          }}
        </Formik>
        {bookable ? (
          <CloseReopen
            what={bookable.label.toLowerCase()}
            name={bookable.name}
            active={bookable.active}
            onChange={(active) => adminApi.setBookableActive(bookable.id, active)}
            onDone={() => {
              refreshLists()
              onReload()
            }}
          />
        ) : null}
      </div>
    </>
  )
}

function RuleFields({ type }: { type: BookableType | undefined }) {
  const fallback = (value: number | null | undefined) => (value == null ? 'Default' : `Default ${formatMinutes(value)}`)
  return (
    <div className="grid gap-5 sm:grid-cols-2">
      <SelectField
        name="slotMinutes"
        label="Slot length"
        options={[
          { value: DEFAULT, label: type?.slotMinutes ? `Default (${type.slotMinutes} min)` : 'Default' },
          { value: '15', label: '15 min' },
          { value: '30', label: '30 min' },
          { value: '60', label: '60 min' },
        ]}
      />
      <FormField name="bufferMinutes" label="Buffer (minutes)" inputMode="numeric" placeholder={fallback(type?.bufferMinutes)} hint="Kept free after each booking." />
      <FormField name="minDurationMinutes" label="Shortest booking (minutes)" inputMode="numeric" placeholder={fallback(type?.minDurationMinutes)} />
      <FormField name="maxDurationMinutes" label="Longest booking (minutes)" inputMode="numeric" placeholder={fallback(type?.maxDurationMinutes)} />
    </div>
  )
}
