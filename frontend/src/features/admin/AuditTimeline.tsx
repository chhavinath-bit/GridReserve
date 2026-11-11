import type { AuditEntry } from '@/api/types'
import { StatusBadge } from '@/components/patterns/StatusBadge'
import { formatDay, formatTime } from '@/lib/time'

const ACTOR: Record<AuditEntry['actorType'], string> = {
  MEMBER: 'The member',
  ADMIN: 'An admin',
  SYSTEM: 'GridReserve',
}

/** A booking's history, oldest first (FR-AUD-03): who moved it from which status to which, and when. */
export function AuditTimeline({ entries, zone }: { entries: AuditEntry[]; zone: string }) {
  if (entries.length === 0) return <p className="text-sm text-muted-foreground">No history recorded.</p>
  return (
    <ol className="relative flex flex-col gap-5 border-l pl-5">
      {entries.map((entry, index) => (
        <li key={index} className="relative">
          <span aria-hidden className="absolute -left-[1.6rem] top-1.5 size-2.5 rounded-full border-2 border-card bg-primary" />
          <div className="flex flex-wrap items-center gap-2">
            {entry.fromStatus ? (
              <>
                <StatusBadge status={entry.fromStatus} />
                <span aria-label="to">→</span>
              </>
            ) : null}
            <StatusBadge status={entry.toStatus} />
          </div>
          <p className="mt-1 text-sm">
            {ACTOR[entry.actorType]}
            {entry.actorUserId != null && entry.actorType !== 'SYSTEM' ? <span className="text-muted-foreground"> (user {entry.actorUserId})</span> : null}
          </p>
          <p className="text-sm text-muted-foreground tabular">
            {formatDay(entry.occurredAt, zone)}, {formatTime(entry.occurredAt, zone)}
          </p>
          {entry.reason ? <p className="mt-1 rounded-sm bg-muted px-2 py-1 text-sm">“{entry.reason}”</p> : null}
        </li>
      ))}
    </ol>
  )
}
