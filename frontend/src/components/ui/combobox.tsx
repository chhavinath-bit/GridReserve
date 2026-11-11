import { Check, ChevronDown, Search } from 'lucide-react'
import { Popover } from 'radix-ui'
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'

export interface ComboboxOption {
  value: string
  label: string
}

export interface ComboboxProps {
  value: string | undefined
  onValueChange: (value: string) => void
  options: ComboboxOption[]
  placeholder?: string
  searchPlaceholder?: string
  emptyText?: string
  disabled?: boolean
  id?: string
  'aria-label'?: string
  'aria-invalid'?: boolean
  'aria-describedby'?: string
  onOpenChange?: (open: boolean) => void
}

/** Lower case without accents, so "aland" finds "Åland Islands" and "sao" finds "São Tomé". */
export function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}

/**
 * Options whose label contains every typed word, in any order ("kol asia" finds "Asia/Kolkata").
 * Labels starting with the first word come first ("ind": India before British Indian Ocean Territory).
 */
export function filterOptions(options: ComboboxOption[], query: string): ComboboxOption[] {
  const words = normalize(query).split(/[\s/]+/).filter(Boolean)
  if (words.length === 0) return options
  const matches = options.filter((option) => {
    const label = normalize(option.label)
    return words.every((word) => label.includes(word))
  })
  const starts = (option: ComboboxOption) => normalize(option.label).startsWith(words[0]!)
  return [...matches.filter(starts), ...matches.filter((option) => !starts(option))]
}

/**
 * A dropdown with a search box (owner request, 2026-11-11): every list in the app, long ones like
 * countries and time zones especially. Built on Radix Popover (already installed) instead of a
 * combobox library. Keyboard: the trigger opens it, typing filters, arrows move, Enter picks,
 * Escape closes and focus returns to the trigger (WAI-ARIA combobox with a listbox popup).
 */
export function Combobox(props: ComboboxProps) {
  const { value, onValueChange, options, disabled } = props
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const listId = useId()
  const list = useRef<HTMLUListElement>(null)
  const shown = useMemo(() => filterOptions(options, query), [options, query])
  const selected = options.find((option) => option.value === value)

  const changeOpen = (next: boolean) => {
    setOpen(next)
    if (next) {
      setQuery('')
      setActive(Math.max(0, options.findIndex((option) => option.value === value)))
    }
    props.onOpenChange?.(next)
  }

  const pick = (option: ComboboxOption | undefined) => {
    if (!option) return
    onValueChange(option.value)
    changeOpen(false)
  }

  // Keep the highlighted option in view while arrowing through a long list.
  useEffect(() => {
    list.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [active, open])

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    const last = shown.length - 1
    const moves: Record<string, number> = {
      ArrowDown: Math.min(last, active + 1),
      ArrowUp: Math.max(0, active - 1),
      Home: 0,
      End: last,
      PageDown: Math.min(last, active + 8),
      PageUp: Math.max(0, active - 8),
    }
    if (event.key in moves) {
      event.preventDefault()
      setActive(moves[event.key]!)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      pick(shown[active])
    }
  }

  return (
    <Popover.Root open={open} onOpenChange={changeOpen}>
      <Popover.Trigger
        id={props.id}
        type="button"
        role="combobox"
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-controls={open ? listId : undefined}
        aria-label={props['aria-label']}
        aria-invalid={props['aria-invalid']}
        aria-describedby={props['aria-describedby']}
        disabled={disabled}
        data-placeholder={selected ? undefined : ''}
        className="flex h-11 w-full items-center justify-between gap-2 rounded-md border border-input bg-card px-3 text-left text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50 aria-invalid:border-destructive data-[placeholder]:text-subtle-foreground"
      >
        <span className="truncate">{selected?.label ?? props.placeholder ?? 'Choose'}</span>
        <ChevronDown className="size-4 shrink-0 opacity-60" aria-hidden />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="start"
          sideOffset={6}
          className="z-50 flex w-[max(var(--radix-popover-trigger-width),14rem)] flex-col overflow-hidden rounded-md border bg-popover text-popover-foreground shadow-lg data-[state=open]:animate-in data-[state=open]:fade-in-0"
        >
          <div className="flex items-center gap-2 border-b px-3">
            <Search className="size-4 shrink-0 text-subtle-foreground" aria-hidden />
            <input
              type="text"
              role="combobox"
              aria-expanded
              aria-controls={listId}
              aria-autocomplete="list"
              aria-activedescendant={shown[active] ? `${listId}-${active}` : undefined}
              aria-label={props.searchPlaceholder ?? 'Search'}
              placeholder={props.searchPlaceholder ?? 'Search…'}
              autoComplete="off"
              className="h-10 w-full bg-transparent text-base outline-none placeholder:text-subtle-foreground sm:text-sm"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value)
                setActive(0)
              }}
              onKeyDown={onKeyDown}
            />
          </div>
          <ul ref={list} id={listId} role="listbox" aria-label={props['aria-label'] ?? 'Options'} className="max-h-72 overflow-y-auto p-1">
            {shown.length === 0 ? (
              <li className="px-3 py-6 text-center text-sm text-muted-foreground">{props.emptyText ?? 'Nothing matches.'}</li>
            ) : (
              shown.map((option, index) => (
                <li
                  key={option.value}
                  id={`${listId}-${index}`}
                  data-index={index}
                  role="option"
                  aria-selected={option.value === value}
                  data-active={index === active ? '' : undefined}
                  onMouseMove={() => setActive(index)}
                  onClick={() => pick(option)}
                  className="flex cursor-pointer select-none items-center gap-2 rounded-sm py-2 pl-8 pr-2 text-sm data-[active]:bg-primary-soft data-[active]:text-primary-soft-foreground relative"
                >
                  {option.value === value ? <Check className="absolute left-2 size-4" aria-hidden /> : null}
                  {option.label}
                </li>
              ))
            )}
          </ul>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  )
}
