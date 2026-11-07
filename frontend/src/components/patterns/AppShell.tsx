import { LogOut } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { ThemeToggle } from './ThemeToggle'

export interface AppShellProps {
  userName: string | null
  onLogout: () => void
  children: ReactNode
}

const NAV = [
  { to: '/', label: 'Browse', end: true },
  { to: '/bookings', label: 'My bookings', end: false },
]

/** Top bar and content area (DESIGN_SYSTEM §4.2). Knows nothing about auth; the app passes the user in. */
export function AppShell({ userName, onLogout, children }: AppShellProps) {
  return (
    <div className="min-h-dvh">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-sm focus:bg-card focus:p-2">
        Skip to content
      </a>
      <header className="border-b bg-background">
        <div className="mx-auto flex h-14 max-w-[1200px] items-center gap-4 px-4 sm:px-8">
          <span className="font-semibold">GridReserve</span>
          <nav aria-label="Main" className="flex gap-1">
            {NAV.map(({ to, label, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  cn(
                    'rounded-sm px-3 py-1.5 text-sm font-medium transition-colors duration-[120ms]',
                    isActive ? 'bg-primary-soft text-primary-soft-foreground' : 'text-muted-foreground hover:text-foreground',
                  )
                }
              >
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <ThemeToggle />
            {userName ? <span className="hidden text-sm text-muted-foreground sm:inline">{userName}</span> : null}
            <Button variant="ghost" size="sm" onClick={onLogout}>
              <LogOut aria-hidden />
              Log out
            </Button>
          </div>
        </div>
      </header>
      <main id="main" className="mx-auto max-w-[1200px] px-4 py-8 sm:px-8">
        {children}
      </main>
    </div>
  )
}
