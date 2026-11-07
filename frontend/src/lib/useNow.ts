import { useEffect, useState } from 'react'

/**
 * The current time, updated every `intervalMs`. Rendering must be pure (the same inputs give the same
 * output), so components read "now" from state instead of calling Date.now() while rendering.
 */
export function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs])
  return now
}
