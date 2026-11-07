import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

// Status looks from DESIGN_SYSTEM §4.3; StatusBadge picks the variant from a booking status.
const badgeVariants = cva(
  'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium [&_svg]:size-3',
  {
    variants: {
      variant: {
        neutral: 'border border-border text-foreground',
        muted: 'border border-border text-subtle-foreground',
        primary: 'bg-primary-soft text-primary-soft-foreground',
        success: 'bg-success-soft text-success-soft-foreground',
        warning: 'bg-warning-soft text-warning-soft-foreground',
        destructive: 'bg-destructive-soft text-destructive-soft-foreground',
      },
    },
    defaultVariants: { variant: 'neutral' },
  },
)

export interface BadgeProps extends ComponentProps<'span'>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />
}
