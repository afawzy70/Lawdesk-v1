// Launch-only diagnosis before running the local cross-browser suite.
import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium, firefox, webkit } = require('../../frontend/e2e-wp/node_modules/playwright');
const results = [];
for (const [name, engine] of Object.entries({ chromium, firefox, webkit })) {
  try {
    const browser = await engine.launch({ headless: true, timeout: 20000 });
    const page = await browser.newPage();
    await page.goto('about:blank');
    results.push({ engine: name, status: 'LAUNCHED', version: browser.version() });
    await browser.close();
  } catch (error) {
    const lines = String(error).split('\n').filter(Boolean);
    results.push({ engine: name, status: 'BLOCKED', reason: lines.find(x => /\.so\b|error while loading|EGL|Missing libraries|Operation not permitted/i.test(x)) || lines[0] });
  }
}
fs.writeFileSync(new URL('./browser-probe.json', import.meta.url), JSON.stringify(results, null, 2) + '\n');
console.log(JSON.stringify(results));
