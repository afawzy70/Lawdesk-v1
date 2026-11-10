// Read-only DOM affordance probe for a synthetic case; no page content or credentials are saved.
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('../../frontend/e2e-wp/node_modules/playwright');
const host = 'lawdesk.167-86-90-174.sslip.io';
const credentials = JSON.parse(fs.readFileSync('/srv/ai-workspaces/secrets/lawdesk-qa/manager.json', 'utf8'));
const browser = await chromium.launch({ headless: true, args: [`--host-resolver-rules=MAP ${host} 167.86.90.174`] });
const page = await browser.newPage();
await page.goto(`https://${host}/login?lang=ar`);
await page.locator('#login').fill(credentials.username);
await page.locator('#password').fill(credentials.password);
await page.locator('button[type=submit]').click();
await page.waitForSelector('nav, aside, header');
await page.goto(`https://${host}/cases?lang=ar`);
await page.waitForSelector('h1');
console.log(JSON.stringify(await page.evaluate(() => ({
  inputs: [...document.querySelectorAll('input,select')].map(x => ({ tag: x.tagName, type: x.type,
    id: x.id, placeholder: x.getAttribute('placeholder'), ariaLabel: x.getAttribute('aria-label') })),
  buttons: [...document.querySelectorAll('button')].map(x => ({ label: (x.getAttribute('aria-label') || x.textContent || '').trim().slice(0, 60),
    type: x.type })).filter(x => x.label),
}))));
await browser.close();
