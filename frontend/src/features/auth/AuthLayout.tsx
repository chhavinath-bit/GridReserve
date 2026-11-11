import type { ReactNode } from 'react'
import { Logo } from '@/components/patterns/Logo'
import { ThemeToggle } from '@/components/patterns/ThemeToggle'

const PREVIEW = ['FREE', 'TAKEN', 'SELECTED', 'FREE', 'FREE', 'TAKEN', 'FREE', 'PAST'] as const
const PREVIEW_LOOK: Record<(typeof PREVIEW)[number], string> = {
  FREE: 'border-border bg-card',
  TAKEN: 'hatch border-transparent bg-slot-taken',
  SELECTED: 'border-primary bg-primary',
  PAST: 'border-transparent bg-card opacity-50',
}

const CATEGORIES = 'Study · Sports · Parking · Clinic · Gym'

export interface AuthLayoutProps {
  title: string
  description: string
  children: ReactNode
}

/**
 * Login / Register layout (DESIGN_SYSTEM §5). Phones: the plain form on the page. Tablets: a card
 * centred on the canvas. From 1024 px: a calm product panel on the left and the form on the right.
 */
export function AuthLayout({ title, description, children }: AuthLayoutProps) {
  return (
    <div className="grid min-h-dvh bg-background md:bg-canvas lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <section aria-label="About GridReserve" className="hidden flex-col justify-between border-r bg-background p-12 lg:flex">
        <Logo className="text-lg" />
        <div className="flex max-w-md flex-col gap-6">
          <h2 className="text-3xl font-semibold tracking-tight">Rooms, courts, bays and appointments, booked in seconds.</h2>
          <div aria-hidden className="grid w-64 grid-cols-4 gap-2">
            {PREVIEW.map((state, index) => (
              <div key={index} className={`h-8 rounded-sm border ${PREVIEW_LOOK[state]}`} />
            ))}
          </div>
          <p className="text-muted-foreground">
            One place for every venue. A slot is yours for 5 minutes while you confirm, and nobody can book it twice.
          </p>
        </div>
        <p className="text-sm text-subtle-foreground">{CATEGORIES}</p>
      </section>
      <div className="flex flex-col px-4 py-4 sm:px-6 md:items-center md:justify-center md:py-12">
        <div className="flex w-full items-center justify-between md:max-w-md lg:justify-end">
          <Logo className="lg:hidden" />
          <ThemeToggle />
        </div>
        <div className="mt-10 flex w-full flex-col gap-6 md:mt-6 md:max-w-md md:rounded-xl md:border md:bg-card md:p-8 md:shadow-sm">
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            <p className="text-sm text-muted-foreground">{description}</p>
          </div>
          {children}
        </div>
        <p className="mt-auto pt-10 text-center text-sm text-subtle-foreground md:hidden">{CATEGORIES}</p>
      </div>
    </div>
  )
}
