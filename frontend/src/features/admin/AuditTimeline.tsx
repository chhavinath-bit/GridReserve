import type { AuditEntry, BookingStatus } from '@/api/types'
import { formatDay, formatTime } from '@/lib/time'
import { cn } from '@/lib/utils'

const ACTOR: Record<AuditEntry['actorType'], string> = {
  MEMBER: 'Member',
  ADMIN: 'Admin',
  SYSTEM: 'GridReserve',
}

const WORD: Record<BookingStatus, string> = { HELD: 'Held', CONFIRMED: 'Confirmed', CANCELLED: 'Cancelled', EXPIRED: 'Expired' }

const DOT: Record<BookingStatus, string> = {
  HELD: 'bg-warning',
  CONFIRMED: 'bg-success',
  CANCELLED: 'bg-destructive',
  EXPIRED: 'bg-subtle-foreground',
}

/** A booking's history, oldest first (FR-AUD-03): what changed, who did it, when, and why if given (design A). */
export function AuditTimeline({ entries, zone }: { entries: AuditEntry[]; zone: string }) {
  if (entries.length === 0) return <p className="text-sm text-muted-foreground">No history recorded.</p>
  return (
    <ol className="flex flex-col">
      {entries.map((entry, index) => (
        <li key={index} className="flex gap-3 pb-4">
          <span aria-hidden className={cn('mt-1.5 size-2.5 shrink-0 rounded-full', DOT[entry.toStatus])} />
          <div className="min-w-0">
            <p className="text-sm font-semibold">{entry.fromStatus ? `${WORD[entry.fromStatus]} → ${WORD[entry.toStatus]}` : WORD[entry.toStatus]}</p>
            <p className="text-[13px] text-muted-foreground tabular">
              {ACTOR[entry.actorType]}
              {entry.actorUserId != null && entry.actorType !== 'SYSTEM' ? ` #${entry.actorUserId}` : ''} · {formatDay(entry.occurredAt, zone)},{' '}
              {formatTime(entry.occurredAt, zone)}
            </p>
            {entry.reason ? <p className="mt-1 rounded-sm bg-muted px-2 py-1 text-[13px]">“{entry.reason}”</p> : null}
          </div>
        </li>
      ))}
    </ol>
  )
}
