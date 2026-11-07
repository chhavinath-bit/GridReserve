import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { onSessionEnded, refreshSession, setAccessToken } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { User } from '@/api/types'

type AuthStatus = 'restoring' | 'signedIn' | 'signedOut'

interface AuthState {
  status: AuthStatus
  user: User | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

/**
 * Who is signed in. The access token stays inside api/client.ts; this context only knows the user.
 * On start it tries the refresh cookie once (FR-UI-07), so a reload keeps the session without the
 * token ever being stored in the browser.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AuthStatus>('restoring')
  const [user, setUser] = useState<User | null>(null)

  const signOutLocally = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    setStatus('signedOut')
    queryClient.clear()
  }, [queryClient])

  useEffect(() => onSessionEnded(signOutLocally), [signOutLocally])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      const restored = await refreshSession()
      if (cancelled) return
      if (!restored) {
        setStatus('signedOut')
        return
      }
      try {
        const me = await authApi.me()
        if (!cancelled) {
          setUser(me)
          setStatus('signedIn')
        }
      } catch {
        if (!cancelled) setStatus('signedOut')
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const startSession = useCallback(async (accessToken: string) => {
    setAccessToken(accessToken)
    const me = await authApi.me()
    setUser(me)
    setStatus('signedIn')
  }, [])

  const login = useCallback(
    async (email: string, password: string) => startSession((await authApi.login(email, password)).accessToken),
    [startSession],
  )

  const register = useCallback(
    async (email: string, password: string, displayName: string) =>
      startSession((await authApi.register(email, password, displayName)).accessToken),
    [startSession],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      signOutLocally()
    }
  }, [signOutLocally])

  const value = useMemo(() => ({ status, user, login, register, logout }), [status, user, login, register, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const state = useContext(AuthContext)
  if (!state) throw new Error('useAuth must be used inside AuthProvider')
  return state
}
