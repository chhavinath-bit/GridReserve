import { CalendarDays, LogOut, Search, Settings2, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { Logo } from './Logo'
import { ThemeToggle } from './ThemeToggle'

export interface ShellUser {
  displayName: string
  email: string
  isAdmin: boolean
}

export interface AppShellProps {
  user: ShellUser | null
  onLogout: () => void
  children: ReactNode
}

interface NavItem {
  to: string
  label: string
  end: boolean
  Icon: LucideIcon
}

function navItems(isAdmin: boolean): NavItem[] {
  const items: NavItem[] = [
    { to: '/', label: 'Browse', end: true, Icon: Search },
    { to: '/bookings', label: 'My bookings', end: false, Icon: CalendarDays },
  ]
  if (isAdmin) items.push({ to: '/admin', label: 'Admin', end: false, Icon: Settings2 })
  return items
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '')).toUpperCase() || '?'
}

/**
 * Top bar and content area (DESIGN_SYSTEM §4.2). From 768 px the navigation sits in the top bar;
 * on phones it moves to a bottom tab bar within thumb reach. Knows nothing about auth; the app passes the user in.
 */
export function AppShell({ user, onLogout, children }: AppShellProps) {
  const items = navItems(user?.isAdmin ?? false)
  return (
    <div className="min-h-dvh bg-canvas">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-sm focus:bg-card focus:p-2">
        Skip to content
      </a>
      <header className="sticky top-0 z-30 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="mx-auto flex h-14 max-w-[1200px] items-center gap-6 px-4 sm:px-6 md:h-16 lg:px-10">
          <NavLink to="/" aria-label="GridReserve home" className="rounded-sm">
            <Logo />
          </NavLink>
          <nav aria-label="Main" className="hidden flex-1 gap-1 md:flex">
            {items.map(({ to, label, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  cn(
                    'rounded-sm px-3 py-2 text-sm font-medium transition-colors duration-[120ms]',
                    isActive ? 'bg-primary-soft text-primary-soft-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )
                }
              >
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1">
            <ThemeToggle />
            {user ? <AccountMenu user={user} onLogout={onLogout} /> : null}
          </div>
        </div>
      </header>
      <main id="main" className="mx-auto max-w-[1200px] px-4 pb-28 pt-6 sm:px-6 md:pb-12 md:pt-8 lg:px-10 lg:pt-10">
        {children}
      </main>
      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-30 border-t bg-background pb-[env(safe-area-inset-bottom)] md:hidden"
      >
        <ul className="grid" style={{ gridTemplateColumns: `repeat(${items.length}, minmax(0, 1fr))` }}>
          {items.map(({ to, label, end, Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                end={end}
                className={({ isActive }) =>
                  cn(
                    'flex h-16 flex-col items-center justify-center gap-1 text-xs font-medium',
                    isActive ? 'text-primary-soft-foreground' : 'text-muted-foreground',
                  )
                }
              >
                <Icon className="size-5" aria-hidden />
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  )
}

function AccountMenu({ user, onLogout }: { user: ShellUser; onLogout: () => void }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`Account: ${user.displayName}`}
        className="ml-1 flex size-9 items-center justify-center rounded-full bg-primary-soft text-xs font-semibold text-primary-soft-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
      >
        {initials(user.displayName)}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>
          <span className="block font-medium">{user.displayName}</span>
          <span className="block truncate text-muted-foreground">{user.email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={onLogout}>
          <LogOut aria-hidden />
          Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
