import { Navigate, Outlet, useLocation } from 'react-router'
import { LoadingState } from '@/components/patterns/States'
import { useAuth } from './AuthContext'

/** Pages behind login. Signed out → login, remembering where the user was (FR-UI-07). */
export function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'restoring') {
    return (
      <div className="mx-auto max-w-md p-8">
        <LoadingState rows={2} label="Restoring your session" />
      </div>
    )
  }
  if (status === 'signedOut') {
    const returnTo = location.pathname + location.search
    return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />
  }
  return <Outlet />
}

/** Only same-site paths are allowed as a return target, so a crafted link can't send users elsewhere. */
export function safeReturnTo(value: string | null): string {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : '/'
}
