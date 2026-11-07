import { lazy, Suspense } from 'react'
import { createBrowserRouter, type RouteObject } from 'react-router'
import { LoadingState } from '@/components/patterns/States'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { RequireAuth } from '@/features/auth/RequireAuth'
import { SlotPickerPage } from '@/features/availability/SlotPickerPage'
import { MyBookingsPage } from '@/features/bookings/MyBookingsPage'
import { BrowsePage } from '@/features/catalog/BrowsePage'
import { VenuePage } from '@/features/catalog/VenuePage'
import { AppLayout } from './AppLayout'
import { NotFoundPage } from './NotFoundPage'

// The component gallery exists only in development builds (DESIGN_SYSTEM §8); `import.meta.env.DEV` is
// false in `vite build`, so the gallery code is left out of the production bundle.
function devRoutes(): RouteObject[] {
  if (!import.meta.env.DEV) return []
  const Gallery = lazy(() => import('./dev/GalleryPage'))
  return [{ path: '/dev/components', element: <Suspense fallback={<LoadingState />}><Gallery /></Suspense> }]
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  ...devRoutes(),
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <BrowsePage /> },
          { path: '/venues/:venueId', element: <VenuePage /> },
          { path: '/bookables/:bookableId', element: <SlotPickerPage /> },
          { path: '/bookings', element: <MyBookingsPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
