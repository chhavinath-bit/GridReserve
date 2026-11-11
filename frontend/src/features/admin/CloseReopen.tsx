import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import type { Activation } from '@/api/types'
import { ConfirmDialog } from '@/components/ui/alert-dialog'
import { Button, type ButtonProps } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface CloseReopenProps {
  name: string
  active: boolean
  onChange: (active: boolean) => Promise<Activation>
  onDone: () => void
}

/**
 * Soft close and reopen (FR-CAT-13, decision 2026-11-05): closing stops new bookings at once; bookings
 * already made stay exactly as they are. The answer says how many are still coming, so the admin knows.
 * Closing asks first; reopening doesn't, it takes nothing away.
 */
export function CloseReopenButton({ name, active, onChange, onDone, size, long = false }: CloseReopenProps & { size?: ButtonProps['size']; long?: boolean }) {
  const [asking, setAsking] = useState(false)
  const change = useMutation({
    mutationFn: (next: boolean) => onChange(next),
    onSuccess: (result) => {
      setAsking(false)
      const coming = result.activeFutureBookings
      const kept = coming === 0 ? 'No bookings are coming up.' : `${coming} upcoming booking${coming === 1 ? '' : 's'} stay as they are.`
      toast.success(result.active ? `${name} is open again` : `${name} is closed for new bookings`, { description: kept })
      onDone()
    },
    onError: (error) => {
      setAsking(false)
      toast.error(error instanceof ApiError ? error.message : 'Something went wrong. Please try again.')
    },
  })

  return (
    <>
      {active ? (
        <Button variant="destructive-outline" size={size} onClick={() => setAsking(true)} aria-label={long ? undefined : `Close ${name}`}>
          {long ? `Close ${name}…` : 'Close'}
        </Button>
      ) : (
        <Button variant="outline" size={size} onClick={() => change.mutate(true)} loading={change.isPending} aria-label={long ? undefined : `Reopen ${name}`}>
          {long ? `Reopen ${name}` : 'Reopen'}
        </Button>
      )}
      <ConfirmDialog
        open={asking}
        onOpenChange={setAsking}
        title={`Close ${name}?`}
        confirmLabel="Close for new bookings"
        cancelLabel="Keep open"
        variant="destructive"
        busy={change.isPending}
        onConfirm={() => change.mutate(false)}
      >
        <p>Members can no longer book it, and it disappears from Browse.</p>
        <p>Bookings already made are not cancelled or changed. You can reopen it at any time.</p>
      </ConfirmDialog>
    </>
  )
}

/** The status card beside an edit form (design A). */
export function StatusPanel(props: CloseReopenProps) {
  return (
    <section aria-labelledby="status-title" className="flex flex-col gap-3 rounded-lg border bg-card p-5">
      <h2 id="status-title" className="font-semibold">
        Status
      </h2>
      <p className="flex items-center gap-2 text-sm">
        <span aria-hidden className={cn('size-2 rounded-full', props.active ? 'bg-success' : 'bg-subtle-foreground')} />
        {props.active ? 'Open for bookings' : 'Closed for new bookings'}
      </p>
      <p className="text-[13px] leading-relaxed text-muted-foreground">
        {props.active
          ? 'Closing stops new holds at once. Bookings already made stay valid.'
          : 'Hidden from members. Bookings made before closing were kept.'}
      </p>
      <CloseReopenButton {...props} long />
    </section>
  )
}
