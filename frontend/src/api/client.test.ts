import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, onSessionEnded, refreshSession, setAccessToken } from './client'

// The API client's contract (FR-UI-07, FR-UI-10, API-05): fetch is replaced by a fake server.

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function errorBody(status: number, code: string, message = 'server says') {
  return { timestamp: 'now', status, code, message, path: '/x', requestId: 'req-1' }
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  setAccessToken('old-token')
})

afterEach(() => {
  vi.unstubAllGlobals()
  setAccessToken(null)
})

const authHeader = (call: number) => (fetchMock.mock.calls[call]?.[1]?.headers as Record<string, string>).Authorization

describe('api', () => {
  it('sends the access token and the client header, and returns the JSON body', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { id: 1 }))

    await expect(api('/bookings/1')).resolves.toEqual({ id: 1 })
    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe('/api/v1/bookings/1')
    expect((init?.headers as Record<string, string>)['X-GridReserve-Client']).toBe('web')
    expect(authHeader(0)).toBe('Bearer old-token')
  })

  it('API-05: turns the standard error JSON into an ApiError with our message for the code', async () => {
    fetchMock.mockResolvedValueOnce(json(409, errorBody(409, 'SLOT_TAKEN')))

    const error = await api('/bookings', { method: 'POST', body: {} }).catch((caught: unknown) => caught)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: 'SLOT_TAKEN', requestId: 'req-1', message: 'This slot was just taken. Pick another time.' })
  })

  it('FR-UI-10: a non-JSON 502/503 becomes SERVICE_UNAVAILABLE, a failed fetch NETWORK_ERROR', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>Bad gateway</html>', { status: 502 }))
    await expect(api('/categories')).rejects.toMatchObject({ code: 'SERVICE_UNAVAILABLE' })

    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await expect(api('/categories')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('FR-UI-07: two requests that get TOKEN_EXPIRED share one refresh, then each retries once with the new token', async () => {
    let refreshes = 0
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/auth/refresh') {
        refreshes++
        await new Promise((resolve) => setTimeout(resolve, 10))
        return json(200, { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 900 })
      }
      const auth = (init?.headers as Record<string, string>).Authorization
      return auth === 'Bearer new-token' ? json(200, { url }) : json(401, errorBody(401, 'TOKEN_EXPIRED'))
    })

    const [first, second] = await Promise.all([api('/a'), api('/b')])
    expect(first).toEqual({ url: '/api/v1/a' })
    expect(second).toEqual({ url: '/api/v1/b' })
    expect(refreshes).toBe(1)
    expect(fetchMock).toHaveBeenCalledTimes(5) // 2 failed, 1 refresh, 2 retries
  })

  it('FR-UI-07: when the refresh fails too, the session ends and the error is passed on', async () => {
    const ended = vi.fn()
    const unsubscribe = onSessionEnded(ended)
    fetchMock.mockImplementation(async (input) =>
      String(input) === '/api/v1/auth/refresh'
        ? json(401, errorBody(401, 'REFRESH_TOKEN_INVALID'))
        : json(401, errorBody(401, 'TOKEN_EXPIRED')),
    )

    await expect(api('/bookings/me')).rejects.toMatchObject({ code: 'TOKEN_EXPIRED' })
    expect(ended).toHaveBeenCalledTimes(1)
    unsubscribe()
  })

  it('never retries or refreshes on other 4xx answers (403, 404, 409)', async () => {
    for (const [status, code] of [[403, 'FORBIDDEN'], [404, 'NOT_FOUND'], [409, 'INVALID_STATE']] as const) {
      fetchMock.mockReset()
      fetchMock.mockResolvedValueOnce(json(status, errorBody(status, code)))
      await expect(api('/x', { method: 'POST' })).rejects.toMatchObject({ code })
      expect(fetchMock).toHaveBeenCalledTimes(1)
    }
  })

  it('auth endpoints carry the cookie (credentials: include) and no access token', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { accessToken: 't', tokenType: 'Bearer', expiresIn: 900 }))
    await api('/auth/login', { method: 'POST', body: { email: 'a', password: 'b' }, authEndpoint: true })
    expect(fetchMock.mock.calls[0]![1]?.credentials).toBe('include')
    expect(authHeader(0)).toBeUndefined()
  })
})

describe('refreshSession', () => {
  it('sends the client header with the cookie and reports false when there is no session', async () => {
    fetchMock.mockResolvedValueOnce(json(401, errorBody(401, 'REFRESH_TOKEN_INVALID')))
    await expect(refreshSession()).resolves.toBe(false)
    const init = fetchMock.mock.calls[0]![1]!
    expect(init.credentials).toBe('include')
    expect((init.headers as Record<string, string>)['X-GridReserve-Client']).toBe('web')
  })
})
