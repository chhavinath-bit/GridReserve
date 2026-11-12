import { expect, test as base } from '@playwright/test'
import { Cleanup, logIn, openBookable, pickLastDay, settled } from './support'

const test = base.extend<{ cleanup: Cleanup }>({
  cleanup: async ({ request }, provide) => {
    const cleanup = new Cleanup(request)
    await provide(cleanup)
    await cleanup.run()
  },
})

// The main journey (US-01..05, FR-UI-01..07): browse, hold, confirm, see it, survive a reload, cancel.
test('a member books a court, sees it after a reload, and cancels it (US-02, FR-UI-07)', async ({ page, cleanup }) => {
  cleanup.watch(page, 'alice')
  await logIn(page, 'alice')
  await openBookable(page, 'Sports', 'Sunrise Turf', 'Court B')
  await pickLastDay(page)

  const label = (await page.getByRole('button', { name: /, free$/ }).first().getAttribute('aria-label'))!
  const time = label.replace(', free', '')
  await page.getByRole('button', { name: label }).click()
  await page.getByRole('button', { name: 'Hold slot' }).click()

  await expect(page.getByRole('button', { name: 'Confirm booking' })).toBeVisible()
  await expect(page.getByText(/Held for [0-4]:[0-5]\d/)).toBeVisible()
  await page.getByRole('button', { name: 'Confirm booking' }).click()
  await expect(page.getByText('Booked. See you there.')).toBeVisible()

  await page.getByRole('link', { name: 'See my bookings' }).click()
  const card = page.getByRole('article').filter({ hasText: 'Court B' }).filter({ hasText: time })
  await expect(card).toBeVisible()
  await expect(card.getByText('Confirmed')).toBeVisible()

  // The access token lives only in memory; the refresh cookie brings the session back (FR-UI-07).
  await page.reload()
  await settled(page)
  await expect(card).toBeVisible()

  await card.getByRole('button', { name: 'Cancel' }).click()
  const dialog = page.getByRole('alertdialog')
  // Unique per run, so this run's card is found among earlier runs' cancellations (D-49).
  const reason = `Plans changed ${Date.now()}`
  await dialog.getByRole('textbox', { name: 'Reason (optional)' }).fill(reason)
  await dialog.getByRole('button', { name: 'Yes, cancel' }).click()
  await expect(page.getByText('Booking cancelled')).toBeVisible()
  await expect(card).toHaveCount(0)

  // The member sees who cancelled, when and why. "Cancelled & expired" is paged, newest start first.
  await page.getByRole('tab', { name: 'Cancelled & expired' }).click()
  const cancelled = page.getByRole('article').filter({ hasText: reason })
  const more = page.getByRole('button', { name: 'Load more' })
  while (!(await cancelled.isVisible()) && (await more.isVisible())) {
    await more.click()
    await settled(page)
  }
  await expect(cancelled).toContainText('Cancelled by you')
  await expect(cancelled).toContainText(`“${reason}”`)
})

test('a hold that runs out offers to pick again (US-06, FR-UI-06)', async ({ page, cleanup }) => {
  // Playwright's clock moves the page's time forward; the server's hold expires by itself 5 minutes later.
  cleanup.watch(page, 'alice')
  await page.clock.install()
  await logIn(page, 'alice')
  await openBookable(page, 'Sports', 'Sunrise Turf', 'Court A')
  await pickLastDay(page)
  await page.getByRole('button', { name: /, free$/ }).last().click()
  await page.getByRole('button', { name: 'Hold slot' }).click()
  await expect(page.getByRole('button', { name: 'Confirm booking' })).toBeVisible()

  await page.clock.fastForward('05:01')
  await expect(page.getByText('Your hold ran out and the slot was released.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Confirm booking' })).toHaveCount(0)
  await page.getByRole('button', { name: 'Pick again' }).click()
  await expect(page.getByRole('button', { name: 'Hold slot' })).toBeVisible()
})

test('Browse narrows venues to one listed city and keeps it after a reload (FR-UI-14, FR-CAT-16)', async ({ page }) => {
  await logIn(page, 'alice')
  await page.getByRole('combobox', { name: 'Location' }).click()
  await page.getByRole('combobox', { name: 'Search cities' }).fill('hyderabad')
  await page.getByRole('option', { name: 'Hyderabad, India' }).click()
  await expect(page).toHaveURL(/[?&]place=\d+/)
  await expect(page.getByRole('link', { name: /CityCare Clinic/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /Sunrise Turf/ })).toHaveCount(0)

  await page.reload()
  await expect(page.getByRole('combobox', { name: 'Location' })).toHaveText('Hyderabad, India')
  await expect(page.getByRole('link', { name: /CityCare Clinic/ })).toBeVisible()
})
