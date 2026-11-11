import { useField } from 'formik'
import type { ReactNode } from 'react'
import { Label } from '@/components/ui/label'
import { Combobox } from '@/components/ui/combobox'

export interface Option {
  value: string
  label: string
}

/** A searchable dropdown (Combobox) bound to Formik, with the same error and hint wiring as FormField. */
export function SelectField(props: {
  name: string
  label: string
  options: Option[]
  placeholder?: string
  hint?: ReactNode
  disabled?: boolean
  /** The label stays for screen readers only, e.g. in the rules table where the row says what it is. */
  hideLabel?: boolean
  onChange?: (value: string) => void
}) {
  const [field, meta, helpers] = useField<string>(props.name)
  const error = meta.touched ? meta.error : undefined
  const describedBy = error ? `${props.name}-error` : props.hint ? `${props.name}-hint` : undefined
  return (
    <div className={props.hideLabel ? 'flex flex-col gap-1' : 'flex flex-col gap-2'}>
      <Label htmlFor={props.name} className={props.hideLabel ? 'sr-only' : undefined}>
        {props.label}
      </Label>
      <Combobox
        id={props.name}
        value={field.value || undefined}
        disabled={props.disabled}
        options={props.options}
        placeholder={props.placeholder}
        searchPlaceholder={`Search ${props.label.replace(/ override$/, '').toLowerCase()}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        onValueChange={(value) => {
          void helpers.setValue(value)
          props.onChange?.(value)
        }}
        onOpenChange={(open) => {
          if (!open) void helpers.setTouched(true, false)
        }}
      />
      {error ? (
        <p id={`${props.name}-error`} className="text-sm text-destructive">
          {error}
        </p>
      ) : props.hint ? (
        <p id={`${props.name}-hint`} className="text-sm text-subtle-foreground">
          {props.hint}
        </p>
      ) : null}
    </div>
  )
}
