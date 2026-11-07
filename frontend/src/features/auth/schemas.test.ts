import { describe, expect, it } from 'vitest'
import { loginSchema, registerSchema } from './schemas'

// The same limits as identity-service's RegisterRequest / LoginRequest. Like Formik, keep the first
// message per field (an empty password breaks "required" and "min 8"; the user sees "required").
async function errorsOf(schema: typeof registerSchema | typeof loginSchema, values: Record<string, string>) {
  try {
    await schema.validate(values, { abortEarly: false })
    return {}
  } catch (cause) {
    const error = cause as { inner: { path: string; message: string }[] }
    const first: Record<string, string> = {}
    error.inner.forEach((issue) => {
      first[issue.path] ??= issue.message
    })
    return first
  }
}

describe('register rules', () => {
  it('accepts a valid form', async () => {
    expect(await errorsOf(registerSchema, { displayName: 'Alice', email: 'alice@example.test', password: 'long-enough' })).toEqual({})
  })

  it('requires every field; spaces alone are not a name', async () => {
    expect(await errorsOf(registerSchema, { displayName: '   ', email: '', password: '' })).toEqual({
      displayName: 'Enter your name.',
      email: 'Enter your email.',
      password: 'Choose a password.',
    })
  })

  it('checks the email format and the password length (8 to 72)', async () => {
    const errors = await errorsOf(registerSchema, { displayName: 'A', email: 'not-an-email', password: 'short' })
    expect(errors.email).toBe('Enter an email like name@example.com.')
    expect(errors.password).toBe('Use at least 8 characters.')
    expect((await errorsOf(registerSchema, { displayName: 'A', email: 'a@b.test', password: 'x'.repeat(73) })).password).toBe(
      'Use at most 72 characters.',
    )
    expect(await errorsOf(registerSchema, { displayName: 'A', email: 'a@b.test', password: 'x'.repeat(72) })).toEqual({})
  })

  it('limits the name to 100 characters', async () => {
    expect((await errorsOf(registerSchema, { displayName: 'n'.repeat(101), email: 'a@b.test', password: 'long-enough' })).displayName).toBe(
      'Use at most 100 characters.',
    )
  })
})

describe('login rules', () => {
  it('requires email and password but no minimum length (older accounts still sign in)', async () => {
    expect(await errorsOf(loginSchema, { email: '', password: '' })).toEqual({
      email: 'Enter your email.',
      password: 'Enter your password.',
    })
    expect(await errorsOf(loginSchema, { email: 'alice@example.test', password: 'abc' })).toEqual({})
  })
})
