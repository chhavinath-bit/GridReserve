import { expect, type APIRequestContext, type Page } from '@playwright/test'

// Demo-only accounts seeded by the migrations and listed in the README (never real people).
export const USERS = {
  alice: { email: 'alice@gridreserve.test', password: 'demo-alice-pass' },
  bob: { email: 'bob@gridreserve.test', password: 'demo-bob-pass' },
  admin: { email: 'admin@gridreserve.test', password: 'demo-admin-pass' },
} as const

export async function logIn(page: Page, who: keyof typeof USERS) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(USERS[who].email)
  await page.getByLabel('Password').fill(USERS[who].password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('heading', { name: 'Find a place to book' })).toBeVisible()
}

/** Waits until no loading skeleton is left on the page. */
export async function settled(page: Page) {
  await expect(page.locator('[role=status][aria-label^="Loading"]')).toHaveCount(0)
}

/** Browse → venue → bookable, the way a member does it. */
export async function openBookable(page: Page, category: string, venue: string, bookable: string) {
  await page.getByRole('group', { name: 'Category' }).getByRole('button', { name: category }).click()
  await page.getByRole('link', { name: new RegExp(venue) }).click()
  await page.getByRole('link', { name: `See times for ${bookable}` }).click()
  await expect(page.getByRole('heading', { name: bookable, level: 1 })).toBeVisible()
}

/** The last day of the booking window: far from today's real bookings, so tests rarely meet them. */
export async function pickLastDay(page: Page) {
  const last = page.getByRole('group', { name: 'Date' }).getByRole('button').last()
  await last.click()
  await expect(last).toHaveAttribute('aria-pressed', 'true')
  // The new day's slots replace the old ones only after its request; wait for them, not for a moment.
  await settled(page)
  await expect(page.getByRole('group', { name: /^Time slots/ })).toBeVisible()
}

export async function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
}

/**
 * Bookings a test created, cancelled again afterwards through the API, so a failed or interrupted run
 * never leaves holds or bookings behind (they would count against the member limit in the next run).
 */
export class Cleanup {
  private readonly created: { who: keyof typeof USERS; id: number }[] = []

  constructor(private readonly request: APIRequestContext) {}

  /** Records the booking id from every successful hold this page makes. */
  watch(page: Page, who: keyof typeof USERS) {
    page.on('response', async (response) => {
      if (response.request().method() === 'POST' && /\/api\/v1\/bookings$/.test(response.url()) && response.status() === 201) {
        const booking = (await response.json()) as { id: number }
        this.created.push({ who, id: booking.id })
      }
    })
  }

  async run() {
    for (const { who, id } of this.created) {
      const login = await this.request.post('/api/v1/auth/login', {
        data: USERS[who],
        headers: { 'X-GridReserve-Client': 'web' },
      })
      const { accessToken } = (await login.json()) as { accessToken: string }
      // 409 INVALID_STATE when the test already cancelled it: fine.
      await this.request.post(`/api/v1/bookings/${id}/cancel`, { headers: { Authorization: `Bearer ${accessToken}` } })
    }
  }
}
