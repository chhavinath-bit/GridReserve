import { useFormikContext } from 'formik'
import { useEffect, useRef } from 'react'
import { Alert } from '@/components/ui/alert'

export interface FormErrorsProps {
  /** Field names in screen order, with their labels for the summary. */
  fields: { name: string; label: string }[]
  /** A message that belongs to the whole form, e.g. "That email and password don't match." */
  formError?: string | null
}

/**
 * After a failed submit: a summary screen readers announce, and focus on the first wrong field
 * (DESIGN_SYSTEM §7 "errors summarized on submit"). Formik counts submits, so this runs once per try.
 */
export function FormErrors({ fields, formError }: FormErrorsProps) {
  const { errors, submitCount, isSubmitting } = useFormikContext<Record<string, unknown>>()
  const wrong = fields.filter((field) => typeof errors[field.name] === 'string')

  // Focus once per finished submit, not on every keystroke while the errors change.
  const handledSubmit = useRef(0)
  useEffect(() => {
    if (isSubmitting || submitCount === handledSubmit.current) return
    handledSubmit.current = submitCount
    const first = fields.find((field) => typeof errors[field.name] === 'string')
    if (first) document.getElementById(first.name)?.focus()
  }, [submitCount, isSubmitting, errors, fields])

  if (formError) {
    return (
      <Alert variant="destructive" role="alert">
        {formError}
      </Alert>
    )
  }
  if (submitCount > 0 && wrong.length > 0) {
    return (
      <Alert variant="destructive" role="alert">
        {wrong.length === 1 ? `Check ${wrong[0]!.label.toLowerCase()}.` : `Check ${wrong.length} fields: ${wrong.map((field) => field.label.toLowerCase()).join(', ')}.`}
      </Alert>
    )
  }
  return null
}
