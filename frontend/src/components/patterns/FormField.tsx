import { useField } from 'formik'
import type { ComponentProps } from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export interface FormFieldProps extends Omit<ComponentProps<'input'>, 'name' | 'id'> {
  name: string
  label: string
  hint?: string
}

/**
 * One labelled input bound to Formik (DESIGN_SYSTEM §7 forms): the error shows once the field was
 * left or the form submitted, is linked with aria-describedby, and marks the input aria-invalid.
 */
export function FormField({ name, label, hint, ...inputProps }: FormFieldProps) {
  const [field, meta] = useField(name)
  const error = meta.touched ? meta.error : undefined
  const describedBy = error ? `${name}-error` : hint ? `${name}-hint` : undefined
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={name}>{label}</Label>
      <Input id={name} {...field} {...inputProps} aria-invalid={error ? true : undefined} aria-describedby={describedBy} />
      {error ? (
        <p id={`${name}-error`} className="text-sm text-destructive">
          {error}
        </p>
      ) : hint ? (
        <p id={`${name}-hint`} className="text-sm text-subtle-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
