import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

const alertVariants = cva('flex gap-3 rounded-md border p-3 text-sm [&>svg]:mt-0.5 [&>svg]:size-4 [&>svg]:shrink-0', {
  variants: {
    variant: {
      info: 'border-border bg-card text-foreground',
      warning: 'border-warning bg-warning-soft text-warning-soft-foreground',
      destructive: 'border-destructive bg-destructive-soft text-destructive-soft-foreground',
      success: 'border-success bg-success-soft text-success-soft-foreground',
    },
  },
  defaultVariants: { variant: 'info' },
})

export interface AlertProps extends ComponentProps<'div'>, VariantProps<typeof alertVariants> {}

export function Alert({ className, variant, role = 'status', ...props }: AlertProps) {
  return <div role={role} className={cn(alertVariants({ variant }), className)} {...props} />
}
