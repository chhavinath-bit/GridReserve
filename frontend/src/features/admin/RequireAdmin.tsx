import { Outlet } from 'react-router'
import { NotFoundPage } from '@/app/NotFoundPage'
import { useAuth } from '@/features/auth/AuthContext'

/**
 * Admin pages. A member sees "not found", as the API would answer (no hint that the page exists).
 * This is only the UI; every admin call is checked again by the server (FR-ADM-01).
 */
export function RequireAdmin() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <Outlet /> : <NotFoundPage />
}
