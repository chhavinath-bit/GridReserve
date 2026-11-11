import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import type { Activation } from '@/api/types'
import { Alert } from '@/components/ui/alert'
import { ConfirmDialog } from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { OpenBadge } from './AdminVenuesPage'

/**
 * Soft close and reopen (FR-CAT-13, decision 2026-11-05): closing stops new bookings at once; bookings
 * already made stay exactly as they are. The answer says how many are still coming, so the admin knows.
 */
export function CloseReopen(props: {
  what: string
  name: string
  active: boolean
  onChange: (active: boolean) => Promise<Activation>
  onDone: () => void
}) {
  const [asking, setAsking] = useState(false)
  const change = useMutation({
    mutationFn: (active: boolean) => props.onChange(active),
    onSuccess: (result) => {
      setAsking(false)
      const coming = result.activeFutureBookings
      const kept = coming === 0 ? 'No bookings are coming up.' : `${coming} upcoming booking${coming === 1 ? '' : 's'} stay as they are.`
      toast.success(result.active ? `${props.name} is open again` : `${props.name} is closed for new bookings`, { description: kept })
      props.onDone()
    },
    onError: () => setAsking(false),
  })

  return (
    <section aria-labelledby="status-title" className="flex flex-col gap-3 rounded-lg border bg-card p-5">
      <div className="flex items-center justify-between gap-2">
        <h2 id="status-title" className="font-semibold">
          Status
        </h2>
        <OpenBadge active={props.active} />
      </div>
      <p className="text-sm text-muted-foreground">
        {props.active
          ? `Members can book this ${props.what}. Closing it stops new bookings; existing ones are kept.`
          : `Hidden from members and closed for new bookings. Existing bookings were kept.`}
      </p>
      {change.error ? (
        <Alert variant="destructive" role="alert">
          {change.error instanceof ApiError ? change.error.message : 'Something went wrong. Please try again.'}
        </Alert>
      ) : null}
      <div>
        {props.active ? (
          <Button variant="outline" onClick={() => setAsking(true)}>
            Close for new bookings
          </Button>
        ) : (
          <Button variant="outline" onClick={() => change.mutate(true)} loading={change.isPending}>
            Reopen
          </Button>
        )}
      </div>
      <ConfirmDialog
        open={asking}
        onOpenChange={setAsking}
        title={`Close ${props.name}?`}
        confirmLabel="Close for new bookings"
        cancelLabel="Keep open"
        variant="destructive"
        busy={change.isPending}
        onConfirm={() => change.mutate(false)}
      >
        <p>Members can no longer book it, and it disappears from Browse.</p>
        <p>Bookings already made are not cancelled or changed. You can reopen it at any time.</p>
      </ConfirmDialog>
    </section>
  )
}
