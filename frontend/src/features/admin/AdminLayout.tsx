import { NavLink, Outlet, useLocation } from 'react-router'
import { cn } from '@/lib/utils'

// Bookables are edited inside the Venues section, so their pages keep "Venues" marked.
const SECTIONS = [
  { to: '/admin/venues', label: 'Venues', also: '/admin/bookables' },
  { to: '/admin/bookings', label: 'All bookings', also: null },
]

/** The admin area (design A): a side menu from 1024 px, tabs above the page on smaller screens. */
export function AdminLayout() {
  const { pathname } = useLocation()
  return (
    <div className="flex flex-col gap-6 lg:grid lg:grid-cols-[200px_minmax(0,1fr)] lg:gap-8">
      <nav aria-label="Admin" className="-mx-4 flex gap-1 overflow-x-auto border-b px-4 sm:mx-0 sm:px-0 lg:sticky lg:top-24 lg:flex-col lg:self-start lg:overflow-visible lg:border-b-0 lg:border-r lg:pr-4">
        {SECTIONS.map(({ to, label, also }) => (
          <NavLink
            key={to}
            to={to}
            aria-current={also && pathname.startsWith(also) ? 'page' : undefined}
            className={({ isActive: exact }) => {
              const isActive = exact || (also !== null && pathname.startsWith(also))
              return cn(
                'shrink-0 px-3 py-2.5 text-sm transition-colors duration-[120ms]',
                '-mb-px border-b-2 lg:mb-0 lg:rounded-md lg:border-b-0',
                isActive
                  ? 'border-primary font-semibold text-foreground lg:bg-primary-soft lg:text-primary-soft-foreground'
                  : 'border-transparent font-medium text-muted-foreground hover:text-foreground lg:hover:bg-muted',
              )
            }}
          >
            {label}
          </NavLink>
        ))}
      </nav>
      <div className="min-w-0">
        <Outlet />
      </div>
    </div>
  )
}
