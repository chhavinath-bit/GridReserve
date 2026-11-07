import { Form, Formik, type FormikHelpers } from 'formik'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '@/api/client'
import { FormErrors } from '@/components/patterns/FormErrors'
import { FormField } from '@/components/patterns/FormField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { safeReturnTo } from './RequireAuth'
import { loginSchema, type LoginValues } from './schemas'

const FIELDS = [
  { name: 'email', label: 'Email' },
  { name: 'password', label: 'Password' },
]

export function LoginPage() {
  const { status, login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const returnTo = safeReturnTo(params.get('returnTo'))

  if (status === 'signedIn') return <Navigate to={returnTo} replace />

  // Formik runs this only when loginSchema passes, so an empty or malformed form never reaches the server.
  const onSubmit = async (values: LoginValues, helpers: FormikHelpers<LoginValues>) => {
    helpers.setStatus(null)
    try {
      await login(values.email.trim(), values.password)
      navigate(returnTo, { replace: true })
    } catch (cause) {
      helpers.setStatus(cause instanceof ApiError ? cause.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <AuthLayout>
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Log in</CardTitle>
          <CardDescription>Welcome back. Pick up where you left off.</CardDescription>
        </CardHeader>
        <CardContent>
          <Formik<LoginValues> initialValues={{ email: '', password: '' }} validationSchema={loginSchema} onSubmit={onSubmit}>
            {({ isSubmitting, status: formError }) => (
              <Form className="flex flex-col gap-4" noValidate>
                <FormErrors fields={FIELDS} formError={formError as string | null} />
                <FormField name="email" label="Email" type="email" autoComplete="email" />
                <FormField name="password" label="Password" type="password" autoComplete="current-password" />
                <Button type="submit" loading={isSubmitting}>
                  Log in
                </Button>
                <p className="text-center text-sm text-muted-foreground">
                  New here?{' '}
                  <Link className="text-primary underline-offset-4 hover:underline" to={`/register?returnTo=${encodeURIComponent(returnTo)}`}>
                    Create an account
                  </Link>
                </p>
              </Form>
            )}
          </Formik>
        </CardContent>
      </Card>
    </AuthLayout>
  )
}
