import { useField } from 'formik'
import type { ReactNode } from 'react'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

export interface Option {
  value: string
  label: string
}

/** A Radix Select bound to Formik, with the same error and hint wiring as FormField. */
export function SelectField(props: {
  name: string
  label: string
  options: Option[]
  placeholder?: string
  hint?: ReactNode
  disabled?: boolean
  onChange?: (value: string) => void
}) {
  const [field, meta, helpers] = useField<string>(props.name)
  const error = meta.touched ? meta.error : undefined
  const describedBy = error ? `${props.name}-error` : props.hint ? `${props.name}-hint` : undefined
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={props.name}>{props.label}</Label>
      <Select
        value={field.value || undefined}
        disabled={props.disabled}
        onValueChange={(value) => {
          void helpers.setValue(value)
          props.onChange?.(value)
        }}
        onOpenChange={(open) => {
          if (!open) void helpers.setTouched(true, false)
        }}
      >
        <SelectTrigger id={props.name} aria-invalid={error ? true : undefined} aria-describedby={describedBy}>
          <SelectValue placeholder={props.placeholder ?? 'Choose'} />
        </SelectTrigger>
        <SelectContent>
          {props.options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
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
