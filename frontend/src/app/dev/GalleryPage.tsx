import { useState, type ReactNode } from 'react'
import type { Slot } from '@/api/types'
import { ConflictNotice } from '@/components/patterns/ConflictNotice'
import { DateStrip } from '@/components/patterns/DateStrip'
import { HoldTimer } from '@/components/patterns/HoldTimer'
import { SlotGrid } from '@/components/patterns/SlotGrid'
import { EmptyState, ErrorState, LoadingState } from '@/components/patterns/States'
import { StatusBadge } from '@/components/patterns/StatusBadge'
import { ApiError } from '@/api/client'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

// Dev-only gallery (DESIGN_SYSTEM §8, D-UI-5): every component in every state, light and dark side by
// side. Not routed in production builds.

const ZONE = 'Asia/Kolkata'
const SLOTS: Slot[] = ['PAST', 'PAST', 'FREE', 'TAKEN', 'FREE', 'FREE', 'FREE', 'TAKEN'].map((status, index) => ({
  start: `2026-11-09T${String(index + 3).padStart(2, '0')}:30:00Z`,
  end: `2026-11-09T${String(index + 4).padStart(2, '0')}:30:00Z`,
  localStart: '',
  localEnd: '',
  status: status as Slot['status'],
}))

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-sm font-semibold text-muted-foreground">{title}</h2>
      {children}
    </section>
  )
}

function Everything() {
  const [deadlines] = useState(() => ({ long: Date.now() + 240_000, short: Date.now() + 45_000 }))
  return (
    <div className="flex flex-col gap-8">
      <Section title="Button">
        <div className="flex flex-wrap gap-2">
          {(['primary', 'secondary', 'outline', 'ghost', 'destructive', 'link'] as const).map((variant) => (
            <Button key={variant} variant={variant}>
              {variant}
            </Button>
          ))}
          <Button loading>Loading</Button>
          <Button disabled>Disabled</Button>
        </div>
      </Section>
      <Section title="Badge / StatusBadge">
        <div className="flex flex-wrap gap-2">
          {(['neutral', 'muted', 'primary', 'success', 'warning', 'destructive'] as const).map((variant) => (
            <Badge key={variant} variant={variant}>
              {variant}
            </Badge>
          ))}
          {(['HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED'] as const).map((status) => (
            <StatusBadge key={status} status={status} />
          ))}
        </div>
      </Section>
      <Section title="Input / Label">
        <div className="flex max-w-xs flex-col gap-2">
          <Label htmlFor="g-ok">Email</Label>
          <Input id="g-ok" placeholder="you@example.test" />
          <Label htmlFor="g-bad">Invalid</Label>
          <Input id="g-bad" aria-invalid defaultValue="not-an-email" />
        </div>
      </Section>
      <Section title="Card">
        <Card className="max-w-sm">
          <CardHeader>
            <CardTitle>Card title</CardTitle>
            <CardDescription>Description text</CardDescription>
          </CardHeader>
          <CardContent>Content</CardContent>
        </Card>
      </Section>
      <Section title="Alert / ConflictNotice">
        <Alert variant="info">Info</Alert>
        <Alert variant="success">Success</Alert>
        <Alert variant="warning">Warning</Alert>
        <Alert variant="destructive">Destructive</Alert>
        <ConflictNotice message="This slot was just taken. Pick another time." />
      </Section>
      <Section title="DateStrip">
        <DateStrip today="2026-11-07" days={7} value="2026-11-08" onChange={() => undefined} />
      </Section>
      <Section title="SlotGrid: past, free, taken, selected (06:30), held by me (11:30)">
        <SlotGrid slots={SLOTS} zone={ZONE} selected={[2]} heldByMe={[6]} onSelect={() => undefined} />
      </Section>
      <Section title="HoldTimer (4:00 and under a minute)">
        <HoldTimer deadline={deadlines.long} onExpire={() => undefined} />
        <HoldTimer deadline={deadlines.short} onExpire={() => undefined} />
      </Section>
      <Section title="Loading / Empty / Error / Temporary">
        <LoadingState rows={2} />
        <EmptyState title="Nothing booked yet">Find a slot</EmptyState>
        <ErrorState error={new ApiError(404, 'NOT_FOUND', 'We couldn’t find that.')} onRetry={() => undefined} />
        <ErrorState error={new ApiError(503, 'SERVICE_UNAVAILABLE', 'This is temporarily unavailable. Please retry in a moment.')} onRetry={() => undefined} />
      </Section>
    </div>
  )
}

export default function GalleryPage() {
  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="bg-canvas p-8 text-foreground">
        <h1 className="mb-6 text-2xl font-semibold">Light</h1>
        <Everything />
      </div>
      {/* The .dark class switches every token for this subtree. */}
      <div className="dark bg-canvas p-8 text-foreground">
        <h1 className="mb-6 text-2xl font-semibold">Dark</h1>
        <Everything />
      </div>
    </div>
  )
}
