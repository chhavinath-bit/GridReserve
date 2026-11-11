import { NavLink, Outlet } from 'react-router'
import { cn } from '@/lib/utils'

const SECTIONS = [
  { to: '/admin/venues', label: 'Venues' },
  { to: '/admin/bookings', label: 'All bookings' },
]

/** The admin area's own sub-navigation, under the app's top bar. */
export function AdminLayout() {
  return (
    <div className="flex flex-col gap-6">
      <nav aria-label="Admin" className="-mx-4 flex gap-1 overflow-x-auto border-b px-4 sm:mx-0 sm:px-0">
        {SECTIONS.map(({ to, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              cn(
                '-mb-px shrink-0 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors duration-[120ms]',
                isActive ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
              )
            }
          >
            {label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </div>
  )
}
