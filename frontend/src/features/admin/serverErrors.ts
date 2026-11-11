import type { FormikHelpers } from 'formik'
import { ApiError } from '@/api/client'

/**
 * Puts what the server refused under the right field (FR-CAT-09). `fieldMap` renames API fields to form
 * fields (e.g. every "openingHours[2]…" error → "hours"); unknown fields go to the form-level message.
 * STALE_UPDATE and other refusals become the form message, which the page shows above the fields.
 */
export function applyServerError<T>(cause: unknown, helpers: FormikHelpers<T>, known: string[], fieldMap: (field: string) => string = (field) => field) {
  if (!(cause instanceof ApiError)) {
    helpers.setStatus({ message: 'Something went wrong. Please try again.' })
    return
  }
  const errors: Record<string, string> = {}
  const rest: string[] = []
  cause.fieldErrors.forEach(({ field, message }) => {
    const name = fieldMap(field)
    if (known.includes(name)) errors[name] ??= message
    else rest.push(message)
  })
  if (Object.keys(errors).length > 0) helpers.setErrors(errors as never)
  if (rest.length > 0 || Object.keys(errors).length === 0) {
    helpers.setStatus({ message: rest.length > 0 ? rest.join(' ') : cause.message, code: cause.code })
  }
}

export interface FormStatus {
  message: string
  code?: string
}
