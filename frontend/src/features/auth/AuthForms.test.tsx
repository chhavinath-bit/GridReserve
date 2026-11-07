import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { ThemeProvider } from '@/lib/theme'
import { LoginPage } from './LoginPage'
import { RegisterPage } from './RegisterPage'

// The pages talk to the session only through useAuth, so a fake one shows whether a request was sent.
const login = vi.fn()
const register = vi.fn()
vi.mock('./AuthContext', () => ({
  useAuth: () => ({ status: 'signedOut', user: null, login, register, logout: vi.fn() }),
}))

function renderPage(page: 'login' | 'register') {
  return render(
    <ThemeProvider>
      <MemoryRouter>{page === 'login' ? <LoginPage /> : <RegisterPage />}</MemoryRouter>
    </ThemeProvider>,
  )
}

beforeEach(() => {
  login.mockReset()
  register.mockReset()
})

describe('LoginPage', () => {
  it('an empty submit sends nothing, shows each error, summarizes and focuses the first field', async () => {
    renderPage('login')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByText('Enter your email.')).toBeInTheDocument()
    expect(screen.getByText('Enter your password.')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Check 2 fields: email, password.')
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Enter your email.')
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveFocus())
    expect(login).not.toHaveBeenCalled()
  })

  it('a valid form logs in; wrong credentials show the server’s message for the whole form', async () => {
    login.mockRejectedValueOnce(new ApiError(401, 'INVALID_CREDENTIALS', 'That email and password don’t match.'))
    renderPage('login')
    await userEvent.type(screen.getByLabelText('Email'), ' alice@example.test ')
    await userEvent.type(screen.getByLabelText('Password'), 'secret-pass')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That email and password don’t match.')
    expect(login).toHaveBeenCalledWith('alice@example.test', 'secret-pass')
  })
})

describe('RegisterPage', () => {
  it('a bad email and a short password are caught before any request', async () => {
    renderPage('register')
    await userEvent.type(screen.getByLabelText('Name'), 'Alice')
    await userEvent.type(screen.getByLabelText('Email'), 'alice-at-example')
    await userEvent.type(screen.getByLabelText('Password'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Enter an email like name@example.com.')).toBeInTheDocument()
    expect(screen.getByText('Use at least 8 characters.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveFocus())
    expect(register).not.toHaveBeenCalled()
  })

  it('a field error only the server can find (72 bytes) shows under that field', async () => {
    register.mockRejectedValueOnce(
      new ApiError(400, 'VALIDATION_FAILED', 'Some details need fixing.', [{ field: 'password', message: 'Use at most 72 bytes.' }]),
    )
    renderPage('register')
    await userEvent.type(screen.getByLabelText('Name'), 'Alice')
    await userEvent.type(screen.getByLabelText('Email'), 'alice@example.test')
    await userEvent.type(screen.getByLabelText('Password'), 'ééééééééééééé')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Use at most 72 bytes.')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true')
    expect(register).toHaveBeenCalledTimes(1)
  })
})
