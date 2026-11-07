import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '@/api/client'
import type { FieldError } from '@/api/types'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { safeReturnTo } from './RequireAuth'

const FIELDS = [
  { name: 'displayName', label: 'Name', type: 'text', autoComplete: 'name' },
  { name: 'email', label: 'Email', type: 'email', autoComplete: 'email' },
  { name: 'password', label: 'Password', type: 'password', autoComplete: 'new-password', hint: '8 to 72 characters.' },
] as const

type FieldName = (typeof FIELDS)[number]['name']

export function RegisterPage() {
  const { status, register } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const returnTo = safeReturnTo(params.get('returnTo'))
  const [values, setValues] = useState<Record<FieldName, string>>({ displayName: '', email: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([])
  const [submitting, setSubmitting] = useState(false)

  if (status === 'signedIn') return <Navigate to={returnTo} replace />

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setFieldErrors([])
    setSubmitting(true)
    try {
      await register(values.email.trim(), values.password, values.displayName.trim())
      navigate(returnTo, { replace: true })
    } catch (cause) {
      if (cause instanceof ApiError) {
        setError(cause.message)
        setFieldErrors(cause.fieldErrors)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Create an account</CardTitle>
          <CardDescription>You can book right after.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-4" onSubmit={onSubmit} noValidate>
            {error ? (
              <Alert variant="destructive" role="alert">
                {error}
              </Alert>
            ) : null}
            {FIELDS.map((field) => {
              const fieldError = fieldErrors.find((candidate) => candidate.field === field.name)
              const describedBy = fieldError ? `${field.name}-error` : 'hint' in field ? `${field.name}-hint` : undefined
              return (
                <div key={field.name} className="flex flex-col gap-2">
                  <Label htmlFor={field.name}>{field.label}</Label>
                  <Input
                    id={field.name}
                    type={field.type}
                    autoComplete={field.autoComplete}
                    required
                    aria-invalid={fieldError ? true : undefined}
                    aria-describedby={describedBy}
                    value={values[field.name]}
                    onChange={(event) => setValues((current) => ({ ...current, [field.name]: event.target.value }))}
                  />
                  {fieldError ? (
                    <p id={`${field.name}-error`} className="text-sm text-destructive">
                      {fieldError.message}
                    </p>
                  ) : 'hint' in field ? (
                    <p id={`${field.name}-hint`} className="text-sm text-subtle-foreground">
                      {field.hint}
                    </p>
                  ) : null}
                </div>
              )
            })}
            <Button type="submit" loading={submitting}>
              Create account
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              Already have an account?{' '}
              <Link className="text-primary underline-offset-4 hover:underline" to={`/login?returnTo=${encodeURIComponent(returnTo)}`}>
                Log in
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </AuthLayout>
  )
}
