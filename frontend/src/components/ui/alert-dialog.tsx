import { AlertDialog as AlertDialogPrimitive } from 'radix-ui'
import type { ReactNode } from 'react'
import { Button, type ButtonProps } from './button'

// A confirmation that must be answered (cancel a booking, close a venue): focus starts on the safe choice,
// Escape means "no" (§4.1 AlertDialog).

export interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  children: ReactNode
  /** An optional input under the text, such as a reason; outside the description so it isn't read as one. */
  field?: ReactNode
  confirmLabel: string
  cancelLabel?: string
  variant?: ButtonProps['variant']
  busy?: boolean
  onConfirm: () => void
}

export function ConfirmDialog(props: ConfirmDialogProps) {
  return (
    <AlertDialogPrimitive.Root open={props.open} onOpenChange={props.onOpenChange}>
      <AlertDialogPrimitive.Portal>
        <AlertDialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/40" />
        <AlertDialogPrimitive.Content className="fixed left-1/2 top-1/2 z-50 flex w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 flex-col gap-4 rounded-lg border bg-card p-6 text-card-foreground shadow-lg">
          <AlertDialogPrimitive.Title className="text-lg font-semibold">{props.title}</AlertDialogPrimitive.Title>
          <AlertDialogPrimitive.Description asChild>
            <div className="flex flex-col gap-3 text-sm text-muted-foreground">{props.children}</div>
          </AlertDialogPrimitive.Description>
          {props.field}
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <AlertDialogPrimitive.Cancel asChild>
              <Button variant="outline">{props.cancelLabel ?? 'Keep it'}</Button>
            </AlertDialogPrimitive.Cancel>
            <Button variant={props.variant ?? 'destructive'} loading={props.busy} onClick={props.onConfirm}>
              {props.confirmLabel}
            </Button>
          </div>
        </AlertDialogPrimitive.Content>
      </AlertDialogPrimitive.Portal>
    </AlertDialogPrimitive.Root>
  )
}

