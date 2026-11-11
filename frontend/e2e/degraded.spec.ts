import { expect, test } from '@playwright/test'
import { logIn, openBookable } from './support'

// What a member sees when a backend answers 503 (FR-UI-10): "temporarily unavailable" and a Retry that
// works once the service is back. The real outage scenarios run in the system tests (FS-32..38).
test('a temporarily unavailable service shows Retry, and Retry recovers (FR-UI-10)', async ({ page }) => {
  await logIn(page, 'alice')
  await page.route('**/api/v1/bookables/*/availability**', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 503,
        code: 'SERVICE_UNAVAILABLE',
        message: 'Service unavailable.',
        path: '/api/v1/bookables/0/availability',
        requestId: 'e2e',
      }),
    }),
  )
  await openBookable(page, 'Sports', 'Sunrise Turf', 'Court A')
  await expect(page.getByText('This is temporarily unavailable. Please retry in a moment.')).toBeVisible()

  await page.unroute('**/api/v1/bookables/*/availability**')
  await page.getByRole('button', { name: 'Retry' }).click()
  await expect(page.getByRole('group', { name: /^Time slots/ })).toBeVisible()
})
