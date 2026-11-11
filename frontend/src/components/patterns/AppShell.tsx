import { CalendarDays, ChevronLeft, LogOut, Search, Settings2, type LucideIcon } from 'lucide-react'
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
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

/** What a page shows in the phone top bar instead of the logo (design A): its title, and a way back. */
export interface PhoneHeader {
  title: string
  subtitle?: string
  backTo?: string
  backLabel?: string
}

const PhoneHeaderContext = createContext<(header: PhoneHeader | null) => void>(() => undefined)

/** Sets the phone top bar while the calling page is shown; tablets and desktops keep the full bar. */
export function usePhoneHeader(header: PhoneHeader | null) {
  const set = useContext(PhoneHeaderContext)
  // Compared by value, so a page passing a new object each render doesn't loop.
  const key = header ? JSON.stringify(header) : ''
  useEffect(() => {
    set(key ? (JSON.parse(key) as PhoneHeader) : null)
    return () => set(null)
  }, [set, key])
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
  const [phoneHeader, setPhoneHeader] = useState<PhoneHeader | null>(null)
  return (
    <PhoneHeaderContext.Provider value={setPhoneHeader}>
    <div className="min-h-dvh bg-canvas">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-sm focus:bg-card focus:p-2">
        Skip to content
      </a>
      <header className="sticky top-0 z-30 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        {phoneHeader ? <PhoneBar header={phoneHeader} /> : null}
        <div className={cn('mx-auto flex h-14 max-w-[1200px] items-center gap-6 px-4 sm:px-6 md:h-16 lg:px-10', phoneHeader && 'hidden md:flex')}>
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
                    'rounded-md px-3 py-2 text-sm transition-colors duration-[120ms]',
                    isActive ? 'bg-primary-soft font-semibold text-primary-soft-foreground' : 'font-medium text-muted-foreground hover:bg-muted hover:text-foreground',
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
    </PhoneHeaderContext.Provider>
  )
}

function PhoneBar({ header }: { header: PhoneHeader }) {
  return (
    <div className="flex h-14 items-center gap-1 px-2 md:hidden">
      {header.backTo ? (
        <Link to={header.backTo} aria-label={header.backLabel ?? 'Back'} className="flex size-11 shrink-0 items-center justify-center rounded-md">
          <ChevronLeft className="size-5" aria-hidden />
        </Link>
      ) : (
        <span className="w-3" />
      )}
      <div className="min-w-0">
        <p className="truncate font-semibold">{header.title}</p>
        {header.subtitle ? <p className="truncate text-xs text-muted-foreground">{header.subtitle}</p> : null}
      </div>
    </div>
  )
}

function AccountMenu({ user, onLogout }: { user: ShellUser; onLogout: () => void }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className="ml-1 flex size-9 items-center justify-center rounded-full bg-primary-soft text-xs font-semibold text-primary-soft-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
      >
        {/* The initials are decoration; the name read out is the full one (no aria-label, WCAG 2.5.3). */}
        <span aria-hidden>{initials(user.displayName)}</span>
        <span className="sr-only">Account: {user.displayName}</span>
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
