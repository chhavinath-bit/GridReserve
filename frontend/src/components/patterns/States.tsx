import { AlertTriangle, CloudOff, Inbox } from 'lucide-react'
import type { ReactNode } from 'react'
import { ApiError } from '@/api/client'
import { TEMPORARY_CODES, messageFor } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

// The three standard states every data view has (FR-UI-03).

export function LoadingState({ rows = 3, label = 'Loading' }: { rows?: number; label?: string }) {
  return (
    <div role="status" aria-label={label} className="flex flex-col gap-2">
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-12 w-full" />
      ))}
    </div>
  )
}

/** Shows the API's message; 503/504 and network errors get the "temporarily unavailable" variant (FR-UI-10). */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const apiError = error instanceof ApiError ? error : null
  const temporary = apiError ? TEMPORARY_CODES.has(apiError.code) : false
  const message = apiError?.message ?? messageFor('INTERNAL_ERROR')
  return (
    <Alert variant={temporary ? 'warning' : 'destructive'} role="alert">
      {temporary ? <CloudOff aria-hidden /> : <AlertTriangle aria-hidden />}
      <div className="flex flex-1 flex-col gap-2">
        <p>{message}</p>
        {onRetry ? (
          <div>
            <Button variant="outline" size="sm" onClick={onRetry}>
              Retry
            </Button>
          </div>
        ) : null}
      </div>
    </Alert>
  )
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-md border border-dashed p-8 text-center">
      <Inbox className="size-6 text-subtle-foreground" aria-hidden />
      <p className="font-medium">{title}</p>
      {children ? <div className="text-sm text-muted-foreground">{children}</div> : null}
    </div>
  )
}
