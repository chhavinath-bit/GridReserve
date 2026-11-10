import { describe, expect, it } from 'vitest'
import { holdIntent, keyFor } from './idempotency'

describe('keyFor (IDEM-01)', () => {
  const intent = holdIntent(7, '2099-01-05T10:30:00Z', '2099-01-05T11:30:00Z')

  it('keeps the key for the same intent, so a retry is recognised by the server', () => {
    const first = keyFor(intent, null, () => 'key-1')
    expect(keyFor(intent, first, () => 'key-2')).toBe(first)
  })

  it('makes a new key for another slot, another bookable, or after the attempt ended', () => {
    const first = keyFor(intent, null, () => 'key-1')
    expect(keyFor(holdIntent(7, '2099-01-05T12:30:00Z', '2099-01-05T13:30:00Z'), first, () => 'key-2').key).toBe('key-2')
    expect(keyFor(holdIntent(8, '2099-01-05T10:30:00Z', '2099-01-05T11:30:00Z'), first, () => 'key-3').key).toBe('key-3')
    expect(keyFor(intent, null, () => 'key-4').key).toBe('key-4')
  })

  it('uses a random UUID by default', () => {
    expect(keyFor(intent, null).key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })
})
