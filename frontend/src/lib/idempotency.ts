/**
 * One Idempotency-Key per booking intent (IDEM-01, US-11). An intent is "hold this bookable at these times":
 * pressing Retry after a network error or a 503 is the same intent and sends the same key, so the server
 * answers with the first result instead of booking twice. A new selection, or the end of the attempt
 * (success or a refusal), starts a new intent with a new key.
 */
export interface IntentKey {
  intent: string
  key: string
}

export function holdIntent(bookableId: number, start: string, end: string): string {
  return `${bookableId}|${start}|${end}`
}

/** The previous key if it belongs to the same intent, otherwise a fresh random one (UUID v4). */
export function keyFor(intent: string, previous: IntentKey | null, newKey: () => string = () => crypto.randomUUID()): IntentKey {
  return previous !== null && previous.intent === intent ? previous : { intent, key: newKey() }
}
