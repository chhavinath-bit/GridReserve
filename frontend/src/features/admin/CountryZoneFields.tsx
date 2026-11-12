import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useFormikContext } from 'formik'
import { catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import { SelectField } from './fields'

const countryNames = new Intl.DisplayNames(['en'], { type: 'region' })

/**
 * Country, then one of that country's listed cities (FR-CAT-16, D-48). The country only narrows the city
 * list: the venue takes its country and time zone from the city, so the zone is shown, not chosen. A country
 * with a single listed city picks it.
 */
export function CountryZoneFields() {
  const queryClient = useQueryClient()
  const { values, setFieldValue } = useFormikContext<{ countryCode: string; cityId: string }>()
  const countries = useQuery({ queryKey: queryKeys.countries, queryFn: catalogApi.countries, staleTime: Infinity })
  const cities = useQuery({
    queryKey: queryKeys.cities(values.countryCode),
    queryFn: () => catalogApi.cities(values.countryCode),
    enabled: values.countryCode !== '',
    staleTime: Infinity,
  })

  const countryOptions = (countries.data ?? [])
    .map(({ code }) => ({ value: code, label: `${countryNames.of(code) ?? code} (${code})` }))
    .sort((a, b) => a.label.localeCompare(b.label))
  const cityOptions = (cities.data ?? []).map((city) => ({ value: String(city.id), label: city.name }))
  const city = cities.data?.find((candidate) => String(candidate.id) === values.cityId)
  const noCities = cities.data !== undefined && cities.data.length === 0

  return (
    <div className="grid gap-5 sm:grid-cols-2">
      <SelectField
        name="countryCode"
        label="Country"
        placeholder={countries.isPending ? 'Loading…' : 'Choose a country'}
        options={countryOptions}
        onChange={async (code) => {
          const list = await queryClient
            .fetchQuery({ queryKey: queryKeys.cities(code), queryFn: () => catalogApi.cities(code), staleTime: Infinity })
            .catch(() => null)
          void setFieldValue('cityId', list?.length === 1 ? String(list[0]!.id) : '')
        }}
      />
      <SelectField
        name="cityId"
        label="City"
        disabled={values.countryCode === '' || noCities}
        placeholder={values.countryCode === '' ? 'Choose a country first' : noCities ? 'No cities listed yet' : 'Choose a city'}
        options={cityOptions}
        hint={
          city
            ? `Time zone ${city.timeZone}: all rules and times at this venue use it.`
            : noCities
              ? 'Cities are added to the list by the GridReserve team.'
              : 'Members filter and see venues by city. The time zone follows from it.'
        }
      />
    </div>
  )
}
