import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

// A plain, accessible table (no table library, D-UI-3). Wide tables scroll inside their card on small
// screens instead of widening the page; lists that matter on phones also render as cards (DataTable use).

export function Table({ className, ...props }: ComponentProps<'table'>) {
  return (
    <div className="w-full overflow-x-auto rounded-lg border bg-card">
      <table className={cn('w-full border-collapse text-sm', className)} {...props} />
    </div>
  )
}

export function TableHead({ className, ...props }: ComponentProps<'th'>) {
  return (
    <th
      className={cn('border-b px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-subtle-foreground', className)}
      {...props}
    />
  )
}

export function TableCell({ className, ...props }: ComponentProps<'td'>) {
  return <td className={cn('border-b px-4 py-3 align-middle [tr:last-child_&]:border-b-0', className)} {...props} />
}
