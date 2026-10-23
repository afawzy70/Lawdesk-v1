// Execute with the application's pinned Playwright toolchain once contract exists.
// No guessed routes, credentials or seeded product records.
import { test, expect } from '@playwright/test';
import fs from 'node:fs';
const file = process.env.LAWDESK_QA_UI_CONTRACT;
const c = file ? JSON.parse(fs.readFileSync(file, 'utf8')) : undefined;
test.describe('Independent LawDesk UI journeys', () => {
  test.skip(!c, 'BLOCKED: application, approved UI contract and isolated seed required');
  test('LD-007 / QA-0049: login and logout invalidate protected navigation', async ({ page }) => {
    await page.goto(c.loginUrl);
    await page.getByLabel(c.usernameLabel, { exact: true }).fill(process.env.LAWDESK_QA_USER!);
    await page.getByLabel(c.passwordLabel, { exact: true }).fill(process.env.LAWDESK_QA_PASSWORD!);
    await page.getByRole('button', { name: c.loginButton, exact: true }).click();
    await expect(page.getByRole('heading', { name: c.homeHeading, exact: true })).toBeVisible();
    await page.getByRole('button', { name: c.logoutButton, exact: true }).click();
    await page.goto(c.protectedUrl);
    await expect(page.getByRole('button', { name: c.loginButton, exact: true })).toBeVisible();
    await expect(page.getByText(c.privateCaseMarker, { exact: true })).toHaveCount(0);
  });
  test('LD-038 / QA-0297: legal workflow persists across reload', async ({ page }) => {
    test.skip(!c?.workflow, 'BLOCKED: signed legal transition/field contract required');
    // Contract supplies real UI roles/labels, not arbitrary scripts or mocks.
    for (const stage of c.workflow) {
      await page.goto(stage.url);
      for (const input of stage.inputs) {
        await page.getByLabel(input.label, { exact: true }).fill(input.value);
      }
      await page.getByRole('button', { name: stage.submit, exact: true }).click();
      await expect(page.getByText(stage.expectedText, { exact: true })).toBeVisible();
      await page.reload();
      await expect(page.getByText(stage.expectedText, { exact: true })).toBeVisible();
    }
  });
});
