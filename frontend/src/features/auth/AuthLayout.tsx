import type { ReactNode } from 'react'
import { Logo } from '@/components/patterns/Logo'
import { ThemeToggle } from '@/components/patterns/ThemeToggle'

const TAGLINE = 'Rooms, courts, bays and appointments, booked in seconds.'
const CATEGORIES = 'Study · Sports · Parking · Clinic · Gym'

// The slot-grid picture from design A's login board: free, taken (t) and selected (s) cells.
const MINI_GRID = 'fftfsfftsfffftffsf'
const MINI_LOOK: Record<string, string> = {
  f: 'border border-primary/25 bg-card',
  t: 'bg-primary/20',
  s: 'bg-primary',
}

export interface AuthLayoutProps {
  title: string
  description: string
  children: ReactNode
}

/**
 * Login / Register layout (design A, DESIGN_SYSTEM §5). Phones: the plain form. Tablets: the tagline
 * above a card on the tinted page. From 1024 px: a tinted product panel on the left, the form on white.
 */
export function AuthLayout({ title, description, children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-dvh bg-background md:bg-primary-soft lg:bg-background">
      {/* Design A is drawn up to 1440 px: a 520 px form column. Wider screens split 50/50 and centre each side's
          content, so the text doesn't hug the left edge with the form squeezed into a strip on the right. */}
      <section aria-label="About GridReserve" className="hidden flex-1 bg-primary-soft p-16 lg:flex">
        <div className="flex w-full flex-col justify-between 2xl:mx-auto 2xl:max-w-[560px]">
          <Logo className="text-lg" />
          <div className="flex flex-col gap-4">
            <h2 className="max-w-[460px] text-[40px] font-bold leading-[1.15] tracking-tight">{TAGLINE}</h2>
            <p className="max-w-[440px] text-[17px] leading-relaxed text-muted-foreground">
              One place for every venue. A slot is yours for 5 minutes while you confirm, and nobody can book it twice.
            </p>
            <div aria-hidden className="mt-6 grid w-fit grid-cols-[repeat(6,56px)] gap-2">
              {MINI_GRID.split('').map((cell, index) => (
                <span key={index} className={`h-8 rounded-md ${MINI_LOOK[cell]}`} />
              ))}
            </div>
          </div>
          <p className="text-sm text-muted-foreground">{CATEGORIES}</p>
        </div>
      </section>
      <div className="relative flex flex-1 flex-col px-4 py-4 sm:px-6 md:items-center md:justify-center md:gap-8 md:py-12 lg:w-[520px] lg:flex-none 2xl:w-auto 2xl:flex-1">
        <div className="flex items-center justify-between md:absolute md:right-6 md:top-6">
          <Logo className="md:hidden" />
          <ThemeToggle />
        </div>
        <div className="hidden text-center md:block lg:hidden">
          <Logo className="text-lg" />
          <p className="mx-auto mt-3 max-w-[480px] text-[26px] font-bold leading-tight tracking-tight">{TAGLINE}</p>
        </div>
        <div className="mt-10 flex w-full flex-col gap-5 md:mt-0 md:max-w-[440px] md:rounded-2xl md:border md:bg-card md:p-8 lg:max-w-[380px] lg:rounded-none lg:border-0 lg:bg-transparent lg:p-0">
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl font-bold tracking-tight lg:text-[26px]">{title}</h1>
            <p className="text-[15px] text-muted-foreground">{description}</p>
          </div>
          {children}
        </div>
        <p className="mt-auto pt-10 text-center text-sm text-muted-foreground md:hidden">{CATEGORIES}</p>
      </div>
    </div>
  )
}
