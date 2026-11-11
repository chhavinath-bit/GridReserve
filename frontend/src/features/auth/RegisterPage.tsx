import { Form, Formik, type FormikHelpers } from 'formik'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '@/api/client'
import { FormErrors } from '@/components/patterns/FormErrors'
import { FormField } from '@/components/patterns/FormField'
import { Button } from '@/components/ui/button'
import { useAuth } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { safeReturnTo } from './RequireAuth'
import { registerSchema, type RegisterValues } from './schemas'

const FIELDS = [
  { name: 'displayName', label: 'Name' },
  { name: 'email', label: 'Email' },
  { name: 'password', label: 'Password' },
]

export function RegisterPage() {
  const { status, register } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const returnTo = safeReturnTo(params.get('returnTo'))

  if (status === 'signedIn') return <Navigate to={returnTo} replace />

  const onSubmit = async (values: RegisterValues, helpers: FormikHelpers<RegisterValues>) => {
    helpers.setStatus(null)
    try {
      await register(values.email.trim(), values.password, values.displayName.trim())
      navigate(returnTo, { replace: true })
    } catch (cause) {
      if (cause instanceof ApiError && cause.fieldErrors.length > 0) {
        // What only the server can check (e.g. a password over 72 bytes) shows under its field.
        helpers.setErrors(Object.fromEntries(cause.fieldErrors.map((error) => [error.field, error.message])))
      } else {
        helpers.setStatus(cause instanceof ApiError ? cause.message : 'Something went wrong. Please try again.')
      }
    }
  }

  return (
    <AuthLayout title="Create an account" description="You can book right after.">
          <Formik<RegisterValues>
            initialValues={{ displayName: '', email: '', password: '' }}
            validationSchema={registerSchema}
            onSubmit={onSubmit}
          >
            {({ isSubmitting, status: formError }) => (
              <Form className="flex flex-col gap-4" noValidate>
                <FormErrors fields={FIELDS} formError={formError as string | null} />
                <FormField name="displayName" label="Name" autoComplete="name" />
                <FormField name="email" label="Email" type="email" autoComplete="email" />
                <FormField name="password" label="Password" type="password" autoComplete="new-password" hint="8 to 72 characters." />
                <Button type="submit" loading={isSubmitting}>
                  Create account
                </Button>
                <p className="text-center text-sm text-muted-foreground">
                  Already have an account?{' '}
                  <Link className="text-primary underline-offset-4 hover:underline" to={`/login?returnTo=${encodeURIComponent(returnTo)}`}>
                    Log in
                  </Link>
                </p>
              </Form>
            )}
          </Formik>
        </AuthLayout>
  )
}
