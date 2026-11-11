import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

export function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  return (
    <textarea
      className={cn(
        'min-h-20 w-full rounded-sm border border-input bg-card px-3 py-2 text-base placeholder:text-subtle-foreground disabled:opacity-50 sm:text-sm',
        className,
      )}
      {...props}
    />
  )
}
