import { useId } from 'react'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

/** The longest reason the API accepts (CancelRequest, FR-BK-04). */
export const REASON_MAX = 200

/**
 * An optional cancellation reason with a live character count. The browser stops typing at the limit, so the
 * request can't be refused for its length. Used by members and admins; the member sees it on the booking (D-49).
 */
export function ReasonField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  const id = useId()
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Textarea id={id} value={value} maxLength={REASON_MAX} onChange={(event) => onChange(event.target.value)} aria-describedby={`${id}-hint`} />
      <p id={`${id}-hint`} className="text-sm text-subtle-foreground">
        {value.length} / {REASON_MAX} characters
      </p>
    </div>
  )
}
