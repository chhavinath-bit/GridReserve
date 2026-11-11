import { Outlet, useNavigate } from 'react-router'
import { AppShell } from '@/components/patterns/AppShell'
import { useAuth } from '@/features/auth/AuthContext'

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <AppShell
      user={user ? { displayName: user.displayName, email: user.email, isAdmin: user.role === 'ADMIN' } : null}
      onLogout={() => {
        void logout().finally(() => navigate('/login', { replace: true }))
      }}
    >
      <Outlet />
    </AppShell>
  )
}
