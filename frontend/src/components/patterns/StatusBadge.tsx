import { Check, Clock, TimerOff, Timer, X } from 'lucide-react'
import type { BookingStatus } from '@/api/types'
import { Badge, type BadgeProps } from '@/components/ui/badge'

// DESIGN_SYSTEM §4.3: one look per status, always with an icon and a word (never color alone).
const LOOK: Record<BookingStatus, { variant: BadgeProps['variant']; label: string; Icon: typeof Clock }> = {
  HELD: { variant: 'warning', label: 'Held', Icon: Timer },
  CONFIRMED: { variant: 'success', label: 'Confirmed', Icon: Check },
  CANCELLED: { variant: 'neutral', label: 'Cancelled', Icon: X },
  EXPIRED: { variant: 'muted', label: 'Expired', Icon: TimerOff },
}

export function StatusBadge({ status }: { status: BookingStatus }) {
  const { variant, label, Icon } = LOOK[status]
  return (
    <Badge variant={variant}>
      <Icon aria-hidden />
      {label}
    </Badge>
  )
}
