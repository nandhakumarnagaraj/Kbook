#!/usr/bin/env node
/**
 * Checks that /story really scrubs, without a human watching it.
 *
 * Loads the page, waits for the sequence to report 100%, then samples the canvas
 * at the top of the track and most of the way through it. The frames change, so
 * the two snapshots must differ; a paint that never leaves frame 1 fails here.
 *
 *   npx ng serve --port 4321
 *   node scripts/verify-story.mjs [--url http://localhost:4321/story]
 */
import { chromium } from 'playwright';

const index = process.argv.indexOf('--url');
const url = index >= 0 ? process.argv[index + 1] : 'http://localhost:4321/story';

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });

try {
  await page.goto(url, { waitUntil: 'networkidle' });
  await page.waitForSelector('kb-scroll-sequence canvas', { timeout: 15000 });
  await page.waitForFunction(
    () => document.querySelector('.sequence-loader') === null,
    undefined,
    { timeout: 20000 }
  );

  const sample = () =>
    page.evaluate(() => {
      const canvas = document.querySelector('kb-scroll-sequence canvas');
      const context = canvas.getContext('2d');
      const { data } = context.getImageData(0, 0, canvas.width, canvas.height);
      let checksum = 0;
      for (let i = 0; i < data.length; i += 997) {
        checksum = (checksum * 31 + data[i]) >>> 0;
      }
      return { checksum, width: canvas.width, height: canvas.height };
    });

  const start = await sample();

  // Five sixths through the pinned travel: not the last frame, and far enough
  // that frame 1 and frame 100 cannot collide.
  await page.evaluate(() => {
    const track = document.querySelector('kb-scroll-sequence .sequence');
    const rect = track.getBoundingClientRect();
    window.scrollTo({ top: rect.top + window.scrollY + (rect.height - window.innerHeight) * (5 / 6) });
  });
  await page.waitForTimeout(900);

  const middle = await sample();

  await page.evaluate(() => {
    const track = document.querySelector('kb-scroll-sequence .sequence');
    const rect = track.getBoundingClientRect();
    window.scrollTo({ top: rect.top + window.scrollY + rect.height - window.innerHeight });
  });
  await page.waitForTimeout(900);

  const end = await sample();

  const failures = [];
  if (!start.width || !start.height) {
    failures.push('canvas has no drawing buffer');
  }
  if (start.checksum === middle.checksum) {
    failures.push('canvas did not change between progress 0 and ~0.83');
  }
  if (middle.checksum === end.checksum) {
    failures.push('canvas did not change between progress ~0.83 and 1');
  }

  console.log(`canvas ${start.width}x${start.height}`);
  console.log(`checksums: start ${start.checksum} · middle ${middle.checksum} · end ${end.checksum}`);

  if (failures.length) {
    console.error(`FAIL\n- ${failures.join('\n- ')}`);
    process.exitCode = 1;
  } else {
    console.log('PASS: the canvas paints a different frame at each scroll position');
  }
} finally {
  await browser.close();
}
