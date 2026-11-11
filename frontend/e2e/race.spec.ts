import { expect, test } from '@playwright/test'
import { Cleanup, logIn, openBookable, pickLastDay } from './support'

// Two members press "Hold slot" on the same slot at the same moment (US-03, FR-UI-05). The database
// decides; the loser is told and sees fresh availability.
test('of two members racing for one slot, one gets it and the other is told (US-03)', async ({ browser, request }) => {
  const cleanup = new Cleanup(request)
  const alice = await (await browser.newContext()).newPage()
  const bob = await (await browser.newContext()).newPage()
  cleanup.watch(alice, 'alice')
  cleanup.watch(bob, 'bob')
  try {
    await logIn(alice, 'alice')
    await logIn(bob, 'bob')
    for (const page of [alice, bob]) {
      await openBookable(page, 'Sports', 'Sunrise Turf', 'Court B')
      await pickLastDay(page)
    }
    const label = (await alice.getByRole('button', { name: /, free$/ }).nth(1).getAttribute('aria-label'))!
    for (const page of [alice, bob]) await page.getByRole('button', { name: label }).click()

    await Promise.all([alice, bob].map((page) => page.getByRole('button', { name: 'Hold slot' }).click()))

    const won = await Promise.all(
      [alice, bob].map((page) =>
        Promise.race([
          page.getByRole('button', { name: 'Confirm booking' }).waitFor().then(() => true),
          page.getByText('This slot was just taken. Pick another time.').waitFor().then(() => false),
        ]),
      ),
    )
    expect(won.filter(Boolean)).toHaveLength(1)

    const winner = won[0] ? alice : bob
    const loser = won[0] ? bob : alice
    await expect(loser.getByRole('button', { name: label.replace('free', 'taken') })).toBeVisible()
    await winner.getByRole('button', { name: 'Release' }).click()
    await expect(winner.getByText('Hold released')).toBeVisible()
  } finally {
    await cleanup.run()
  }
})
