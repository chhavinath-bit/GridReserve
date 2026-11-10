// The only place the app talks HTTP (CLAUDE.md §7.4). Components never call fetch.
//
// Tokens (FR-UI-07, FR-AUTH-09..13):
// - The access token lives in this module's memory only: never localStorage or sessionStorage, so a
//   script injected into the page can't read a stored copy, and it disappears when the tab closes.
// - The refresh token is an HttpOnly cookie the browser sends to /api/v1/auth/* by itself; this code
//   never sees it. A reload loses the access token, so the app calls refresh once at start.
// - On 401 TOKEN_EXPIRED, one refresh runs and every request that failed meanwhile waits for that
//   same refresh (single flight), then retries once. Without that, five parallel requests would
//   start five refreshes.

import type { ErrorBody, FieldError, TokenResponse } from './types'
import { messageFor } from './errors'

const CLIENT_HEADER = { 'X-GridReserve-Client': 'web' }

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  readonly requestId: string | undefined

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = [], requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.requestId = requestId
  }
}

let accessToken: string | null = null
let refreshInFlight: Promise<boolean> | null = null
const sessionEndedListeners = new Set<() => void>()

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function hasAccessToken(): boolean {
  return accessToken !== null
}

/** Called when the session can't be recovered (refresh failed): the app goes back to login. */
export function onSessionEnded(listener: () => void): () => void {
  sessionEndedListeners.add(listener)
  return () => sessionEndedListeners.delete(listener)
}

function endSession(): void {
  accessToken = null
  sessionEndedListeners.forEach((listener) => listener())
}

/**
 * Gets a new access token with the refresh cookie. Concurrent callers share one request. Resolves
 * true when a new token is in memory, false when the session is over (no cookie, expired, logged out).
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch('/api/v1/auth/refresh', {
          method: 'POST',
          credentials: 'include',
          headers: CLIENT_HEADER,
        })
        if (!response.ok) {
          accessToken = null
          return false
        }
        const body = (await response.json()) as TokenResponse
        accessToken = body.accessToken
        return true
      } catch {
        accessToken = null
        return false
      } finally {
        refreshInFlight = null
      }
    })()
  }
  return refreshInFlight
}

export interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  /** Auth endpoints carry the cookie and no access token. */
  authEndpoint?: boolean
  /** Extra request headers, e.g. Idempotency-Key; sent again unchanged if the call is repeated after a refresh. */
  headers?: Record<string, string>
  signal?: AbortSignal
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options)
  if (response.status === 401 && !options.authEndpoint) {
    const error = await toApiError(response)
    if (error.code === 'TOKEN_EXPIRED' && (await refreshSession())) {
      return parse<T>(await send(path, options))
    }
    endSession()
    throw error
  }
  return parse<T>(response)
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { ...CLIENT_HEADER, ...options.headers }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (accessToken && !options.authEndpoint) {
    headers.Authorization = `Bearer ${accessToken}`
  }
  try {
    return await fetch(`/api/v1${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: options.authEndpoint ? 'include' : 'same-origin',
      signal: options.signal,
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause
    }
    throw new ApiError(0, 'NETWORK_ERROR', messageFor('NETWORK_ERROR'))
  }
}

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/**
 * Our services and the gateway answer errors in one JSON shape (API-05). Anything else (an HTML 502
 * from a proxy, an empty body) still becomes an ApiError with a code taken from the status.
 */
export async function toApiError(response: Response): Promise<ApiError> {
  let body: Partial<ErrorBody> | null = null
  try {
    body = (await response.json()) as Partial<ErrorBody>
  } catch {
    body = null
  }
  const code = body?.code ?? fallbackCode(response.status)
  return new ApiError(response.status, code, messageFor(code, body?.message), body?.fieldErrors ?? [], body?.requestId)
}

function fallbackCode(status: number): string {
  if (status === 503 || status === 502) return 'SERVICE_UNAVAILABLE'
  if (status === 504) return 'UPSTREAM_TIMEOUT'
  if (status === 401) return 'UNAUTHENTICATED'
  if (status === 403) return 'FORBIDDEN'
  if (status === 404) return 'NOT_FOUND'
  return 'INTERNAL_ERROR'
}
