import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { horizontalOverflow, logIn, openBookable, settled } from './support'

// Every screen at the five supported widths (DESIGN_SYSTEM §2): nothing wider than the screen, and no
// axe WCAG 2.2 AA violations at the smallest and largest width, in light and dark. SCREENSHOTS=1 also
// saves a picture of each screen to docs/screenshots/.

const WIDTHS = [360, 640, 768, 1024, 1440]
const AXE_WIDTHS = new Set([360, 1440])
// Screenshots only at the smallest and largest width, to keep the repository small.
const SAVE = process.env.SCREENSHOTS === '1'

async function check(page: Page, name: string, width: number, scheme: 'light' | 'dark') {
  await settled(page)
  expect(await horizontalOverflow(page), `${name} at ${width}px is wider than the screen`).toBe(0)
  if (AXE_WIDTHS.has(width)) {
    // CSS transitions are animations too: measure colours only once none is running.
    await page.waitForFunction(() => document.getAnimations().length === 0)
    const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze()
    expect(result.violations.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target).join(', ')}`), `${name} ${width} ${scheme}`).toEqual([])
  }
  if (SAVE && AXE_WIDTHS.has(width)) await page.screenshot({ path: `../docs/screenshots/${scheme}-${width}-${name}.png`, fullPage: true })
}

for (const scheme of ['light', 'dark'] as const) {
  for (const width of WIDTHS) {
    test(`member screens at ${width}px, ${scheme}`, async ({ page }) => {
      await page.setViewportSize({ width, height: width < 768 ? 800 : 900 })
      await page.emulateMedia({ colorScheme: scheme })
      await page.goto('/login')
      await check(page, 'login', width, scheme)
      await logIn(page, 'alice')
      await check(page, 'browse', width, scheme)
      await openBookable(page, 'Sports', 'Sunrise Turf', 'Court A')
      await page.getByRole('button', { name: /, free$/ }).first().click()
      await expect(page.getByRole('button', { name: /, selected$/ })).toBeVisible()
      await check(page, 'book', width, scheme)
      await page.goto('/venues/' + (await venueIdOf(page)))
      await check(page, 'venue', width, scheme)
      await page.goto('/bookings')
      await check(page, 'my-bookings', width, scheme)
    })

    test(`admin screens at ${width}px, ${scheme}`, async ({ page }) => {
      await page.setViewportSize({ width, height: width < 768 ? 800 : 900 })
      await page.emulateMedia({ colorScheme: scheme })
      await logIn(page, 'admin')
      await page.goto('/admin/venues')
      await check(page, 'admin-venues', width, scheme)
      await page.getByRole('link', { name: 'Edit Sunrise Turf' }).click()
      await check(page, 'admin-venue', width, scheme)
      await page.getByRole('link', { name: /^Court A/ }).click()
      await check(page, 'admin-bookable', width, scheme)
      await page.goto('/admin/bookings')
      await check(page, 'admin-bookings', width, scheme)
    })
  }
}

// The booking page links back to its venue (breadcrumb from 768 px, back button on phones).
async function venueIdOf(page: Page): Promise<string> {
  const href = await page.locator('a[href^="/venues/"]').first().getAttribute('href')
  return href!.split('/').pop()!
}
