import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useFormikContext } from 'formik'
import { catalogApi } from '@/api/endpoints'
import { queryKeys } from '@/api/queryKeys'
import { SelectField } from './fields'

const countryNames = new Intl.DisplayNames(['en'], { type: 'region' })

/**
 * Country, then one of that country's IANA time zones (FR-CAT-14). Choosing a country picks its zone
 * when it has only one; the server checks the pair again.
 */
export function CountryZoneFields() {
  const queryClient = useQueryClient()
  const { values, setFieldValue } = useFormikContext<{ countryCode: string; timeZone: string }>()
  const countries = useQuery({ queryKey: queryKeys.countries, queryFn: catalogApi.countries, staleTime: Infinity })
  const zones = useQuery({
    queryKey: queryKeys.timeZones(values.countryCode),
    queryFn: () => catalogApi.timeZones(values.countryCode),
    enabled: values.countryCode !== '',
    staleTime: Infinity,
  })

  const countryOptions = (countries.data ?? [])
    .map(({ code }) => ({ value: code, label: `${countryNames.of(code) ?? code} (${code})` }))
    .sort((a, b) => a.label.localeCompare(b.label))
  const zoneOptions = (zones.data?.timeZones ?? []).map((zone) => ({ value: zone, label: zone }))

  return (
    <div className="grid gap-5 sm:grid-cols-2">
      <SelectField
        name="countryCode"
        label="Country"
        placeholder={countries.isPending ? 'Loading…' : 'Choose a country'}
        options={countryOptions}
        onChange={async (code) => {
          const list = await queryClient
            .fetchQuery({ queryKey: queryKeys.timeZones(code), queryFn: () => catalogApi.timeZones(code), staleTime: Infinity })
            .catch(() => null)
          void setFieldValue('timeZone', list?.timeZones.length === 1 ? list.timeZones[0] : '')
        }}
      />
      <SelectField
        name="timeZone"
        label="Time zone"
        disabled={values.countryCode === ''}
        placeholder={values.countryCode === '' ? 'Choose a country first' : 'Choose a time zone'}
        options={zoneOptions}
        hint="All rules and times at this venue use this zone."
      />
    </div>
  )
}
