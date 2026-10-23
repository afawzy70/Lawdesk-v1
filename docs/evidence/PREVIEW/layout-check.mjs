// Browser layout check for the preview: tables must not need horizontal scrolling on desktop widths.
import { chromium } from 'playwright';
const browser = await chromium.launch();
let failures = 0;
for (const lang of ['en', 'ar']) {
  for (const width of [1024, 1366, 1920]) {
    const page = await browser.newPage({ viewport: { width, height: 900 } });
    await page.goto(`http://127.0.0.1:4791/?lang=${lang}`, { waitUntil: 'networkidle' });
    const overflow = await page.$eval('.table-scroll', el => el.scrollWidth - el.clientWidth);
    const ok = overflow <= 0;
    if (!ok) failures++;
    console.log(`${ok ? 'PASS' : 'FAIL'} lang=${lang} width=${width} table overflow=${overflow}px`);
    await page.close();
  }
}
await browser.close();
process.exit(failures ? 1 : 0);
