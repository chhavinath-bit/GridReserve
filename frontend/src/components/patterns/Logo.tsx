import { cn } from '@/lib/utils'

/** The GridReserve mark: four slots of a grid, two of them booked (DESIGN_SYSTEM §5). */
export function Logo({ className, withName = true }: { className?: string; withName?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-2.5 font-semibold tracking-tight', className)}>
      <svg viewBox="0 0 24 24" className="size-6 shrink-0" aria-hidden>
        <rect x="2" y="2" width="9" height="9" rx="2" className="fill-primary" />
        <rect x="13" y="2" width="9" height="9" rx="2" className="fill-primary-soft-foreground/25" />
        <rect x="2" y="13" width="9" height="9" rx="2" className="fill-primary-soft-foreground/25" />
        <rect x="13" y="13" width="9" height="9" rx="2" className="fill-primary" />
      </svg>
      {withName ? <span>GridReserve</span> : <span className="sr-only">GridReserve</span>}
    </span>
  )
}
