import * as yup from 'yup'

// Client-side checks that mirror identity-service's request rules (RegisterRequest, LoginRequest),
// so mistakes show at once instead of after a round trip. The server still validates everything:
// these only make the form friendlier, they are not the guard.

const email = yup
  .string()
  .trim()
  .required('Enter your email.')
  .email('Enter an email like name@example.com.')
  .max(254, 'Use at most 254 characters.')

export const loginSchema = yup.object({
  email,
  // No length minimum on login: an account made under older rules must still be able to sign in.
  password: yup.string().required('Enter your password.').max(72, 'Use at most 72 characters.'),
})

export const registerSchema = yup.object({
  displayName: yup.string().trim().required('Enter your name.').max(100, 'Use at most 100 characters.'),
  email,
  // 8 to 72 characters. BCrypt's real limit is 72 bytes, so a password with accents or emoji can still
  // be refused by the server; that check stays server-side and shows under this field.
  password: yup
    .string()
    .required('Choose a password.')
    .min(8, 'Use at least 8 characters.')
    .max(72, 'Use at most 72 characters.'),
})

export type LoginValues = yup.InferType<typeof loginSchema>
export type RegisterValues = yup.InferType<typeof registerSchema>
