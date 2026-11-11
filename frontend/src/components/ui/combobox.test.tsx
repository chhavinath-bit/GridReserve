import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { Combobox, filterOptions } from './combobox'

const COUNTRIES = [
  { value: 'AX', label: 'Åland Islands (AX)' },
  { value: 'IO', label: 'British Indian Ocean Territory (IO)' },
  { value: 'IN', label: 'India (IN)' },
  { value: 'ID', label: 'Indonesia (ID)' },
  { value: 'GB', label: 'United Kingdom (GB)' },
]

function Harness() {
  const [value, setValue] = useState<string | undefined>()
  return (
    <>
      <label htmlFor="country">Country</label>
      <Combobox id="country" value={value} onValueChange={setValue} options={COUNTRIES} placeholder="Choose a country" searchPlaceholder="Search countries" />
    </>
  )
}

describe('Combobox', () => {
  it('matches every typed word, ignoring case and accents', () => {
    expect(filterOptions(COUNTRIES, 'aland').map((option) => option.value)).toEqual(['AX'])
    // Names starting with the typed text come first.
    expect(filterOptions(COUNTRIES, 'ind').map((option) => option.value)).toEqual(['IN', 'ID', 'IO'])
    expect(filterOptions(COUNTRIES, 'kingdom united').map((option) => option.value)).toEqual(['GB'])
    expect(filterOptions(COUNTRIES, '  ')).toHaveLength(5)
  })

  it('filters as you type and picks with the keyboard', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    const trigger = screen.getByRole('combobox', { name: 'Country' })
    expect(trigger).toHaveTextContent('Choose a country')

    await user.click(trigger)
    const search = screen.getByRole('combobox', { name: 'Search countries' })
    expect(search).toHaveFocus()
    await user.type(search, 'ind')
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['India (IN)', 'Indonesia (ID)', 'British Indian Ocean Territory (IO)'])

    await user.keyboard('{ArrowDown}{Enter}')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(trigger).toHaveTextContent('Indonesia (ID)')
    expect(trigger).toHaveFocus()
  })

  it('says when nothing matches, and Escape closes without changing the value', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    await user.click(screen.getByRole('combobox', { name: 'Country' }))
    await user.type(screen.getByRole('combobox', { name: 'Search countries' }), 'zzz')
    expect(screen.getByText('Nothing matches.')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Country' })).toHaveTextContent('Choose a country')
  })
})
