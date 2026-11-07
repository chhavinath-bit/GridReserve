import type { ReactNode } from 'react'
import { ThemeToggle } from '@/components/patterns/ThemeToggle'

const PREVIEW = ['FREE', 'TAKEN', 'SELECTED', 'FREE', 'FREE', 'TAKEN', 'FREE', 'PAST'] as const
const PREVIEW_LOOK: Record<(typeof PREVIEW)[number], string> = {
  FREE: 'border-border bg-card',
  TAKEN: 'hatch border-transparent bg-slot-taken',
  SELECTED: 'border-primary bg-primary',
  PAST: 'border-transparent bg-card opacity-50',
}

/** Login / Register layout (DESIGN_SYSTEM §5): a calm product panel left, the form card right. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-background p-12 lg:flex">
        <span className="text-lg font-semibold">GridReserve</span>
        <div className="flex max-w-md flex-col gap-6">
          <h1 className="text-3xl font-semibold">Book the room, the court or the doctor. Never twice.</h1>
          <div aria-hidden className="grid w-64 grid-cols-4 gap-2">
            {PREVIEW.map((state, index) => (
              <div key={index} className={`h-8 rounded-sm border ${PREVIEW_LOOK[state]}`} />
            ))}
          </div>
          <p className="text-muted-foreground">One place for study rooms, courts, parking, clinics and gyms. Every slot is checked by the database, so two people never get the same one.</p>
        </div>
        <span className="text-sm text-subtle-foreground"></span>
      </div>
      <div className="flex flex-col items-center justify-center gap-6 p-4 sm:p-8">
        <div className="flex w-full max-w-sm justify-end">
          <ThemeToggle />
        </div>
        {children}
      </div>
    </div>
  )
}
