import { expect, test } from '@playwright/test'
import { logIn, settled } from './support'

test('an admin sees every venue and a booking’s history (FR-ADM-01, FR-AUD-03)', async ({ page }) => {
  await logIn(page, 'admin')
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Admin' }).click()
  await expect(page.getByRole('heading', { name: 'Venues', level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Edit Sunrise Turf' })).toBeVisible()

  await page.getByRole('navigation', { name: 'Admin' }).getByRole('link', { name: 'All bookings' }).click()
  await settled(page)
  await page.getByRole('button', { name: /^History of booking/ }).first().click()
  const sheet = page.getByRole('dialog', { name: 'Booking history' })
  await expect(sheet).toBeVisible()
  await expect(sheet.getByRole('listitem').first()).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(sheet).toBeHidden()
})

test('the venue form checks its fields before sending (FR-CAT-09)', async ({ page }) => {
  await logIn(page, 'admin')
  await page.goto('/admin/venues/new')
  await page.getByRole('checkbox', { name: 'Monday' }).check()
  await page.getByRole('textbox', { name: 'Monday opens' }).fill('22:00')
  await page.getByRole('textbox', { name: 'Monday closes' }).fill('08:00')
  await page.getByRole('button', { name: 'Create venue' }).click()
  await expect(page.getByRole('alert')).toContainText('Check 5 fields')
  await expect(page.getByText('Monday: must open before it closes.')).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Category' })).toBeFocused()
})

test('a member never sees the admin area (FR-ADM-01)', async ({ page }) => {
  await logIn(page, 'alice')
  await expect(page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Admin' })).toHaveCount(0)
  await page.goto('/admin/venues')
  await expect(page.getByText('This page doesn’t exist')).toBeVisible()
})
