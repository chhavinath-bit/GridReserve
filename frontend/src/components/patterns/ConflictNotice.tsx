import { AlertTriangle } from 'lucide-react'
import { Alert } from '@/components/ui/alert'

/**
 * "Someone else got there first" (409 SLOT_TAKEN / MEMBER_OVERLAP, FR-UI-05). The caller refreshes
 * availability; this explains what happened. Assertive, because the user's choice just failed (§7).
 */
export function ConflictNotice({ message }: { message: string }) {
  return (
    <Alert variant="destructive" role="alert" aria-live="assertive">
      <AlertTriangle aria-hidden />
      <div>
        <p className="font-medium">{message}</p>
        <p>The free times below have been updated.</p>
      </div>
    </Alert>
  )
}
