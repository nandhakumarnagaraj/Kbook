#!/usr/bin/env node
/**
 * check-accent-budget.mjs - Automated Area Budget & Accent Ceiling Enforcement
 *
 * Verifies that the colour budget conforms to §4 of the Web Admin colour system:
 *   - 60% Canvas + White + Surface-2
 *   - 30% Rail + Ink + Borders
 *   - ≤ 6% Indigo action (cap: 1 filled primary per view)
 *   - ≤ 3% Data accents (cap: ≤ 3 elements per card)
 *   - ≤ 1% Status chips
 *   - Total accent ceiling: ≤ 11% across any viewport (1440, 1024, 375)
 *
 * Scans captured viewports via pixel-bucket counter and fails CI above 11% accent.
 */

import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { PNG } = require('pngjs');

const SCRIPT_DIR = fileURLToPath(new URL('.', import.meta.url));
const APP_DIR = resolve(SCRIPT_DIR, '..');
const REPO_ROOT = resolve(APP_DIR, '../..');

// Find the latest audit screenshot directory or use custom --dir argument
const dirArg = process.argv.find((arg) => arg.startsWith('--dir='));
const customDir = dirArg ? resolve(dirArg.split('=')[1]) : null;

const candidateDirs = [
  customDir,
  join(REPO_ROOT, 'docs/audit/phase2'),
  join(REPO_ROOT, 'docs/audit/phase1'),
  join(REPO_ROOT, 'docs/audit/baseline-phase0'),
].filter(Boolean);

let targetDir = candidateDirs.find((dir) => existsSync(dir) && readdirSync(dir).some((f) => f.endsWith('.png')));

if (!targetDir) {
  console.error('Error: No screenshot audit directory found in docs/audit/.');
  console.error('Expected one of:', candidateDirs);
  process.exit(1);
}

const ACCENT_CAP_PCT = 11.0;

function classifyPixel(r, g, b, a) {
  if (a < 128) return 'surface';

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  const lum = (max + min) / 2;

  // Near-neutral / grayscale
  if (d < 35 || lum < 25 || lum > 245) {
    if (lum > 230) return 'surface'; // canvas, white, surface-2
    return 'structure'; // rail, ink, borders
  }

  const sat = d / (255 - Math.abs(max + min - 255));
  if (sat < 0.25) return 'structure';

  // Hue calculation [0, 360)
  let h = 0;
  if (max === r) h = ((g - b) / d) % 6;
  else if (max === g) h = (b - r) / d + 2;
  else h = (r - g) / d + 4;
  h = Math.round(h * 60);
  if (h < 0) h += 360;

  // The brand rail (logo violet #2E1470: h~257, lum~66) belongs to the 60%
  // Primary Background base, not to the 10% accent: it is the largest single
  // field on the page, and counting it as accent would fail every view.
  // Navy #1E3A8A sits in the same window and is kept for older screenshots.
  if (h >= 210 && h <= 275 && lum < 110) return 'structure';

  if (h >= 225 && h <= 255) return 'indigo_action';
  if ((h >= 20 && h <= 50) || (h >= 256 && h <= 290) || (h >= 170 && h <= 190)) return 'data_accent';
  if ((h >= 100 && h <= 160) || h >= 350 || h <= 15) return 'status';
  return 'other_accent';
}

function analyzeImage(filePath) {
  const buf = readFileSync(filePath);
  const png = PNG.sync.read(buf);
  const total = png.width * png.height;
  const counts = {
    surface: 0,
    structure: 0,
    indigo_action: 0,
    data_accent: 0,
    status: 0,
    other_accent: 0,
  };

  for (let i = 0; i < png.data.length; i += 4) {
    const bucket = classifyPixel(png.data[i], png.data[i + 1], png.data[i + 2], png.data[i + 3]);
    counts[bucket]++;
  }

  const accentPixels = counts.indigo_action + counts.data_accent + counts.status + counts.other_accent;
  const accentPct = (accentPixels / total) * 100;

  return {
    width: png.width,
    height: png.height,
    total,
    counts,
    accentPct,
  };
}

console.log('='.repeat(80));
console.log(' Web Admin Colour System — Area Budget & Accent Ceiling Check (≤ 11%)');
console.log(` Audit directory: ${targetDir}`);
console.log('='.repeat(80));
console.log(
  '| ' +
    'Screenshot View'.padEnd(24) +
    '| ' +
    'Dim (WxH)'.padEnd(12) +
    '| ' +
    'Accent %'.padEnd(10) +
    '| ' +
    'Ceiling'.padEnd(10) +
    '| Result |'
);
console.log('|-' + '-'.repeat(24) + '+-' + '-'.repeat(12) + '+-' + '-'.repeat(10) + '+-' + '-'.repeat(10) + '+--------|');

const files = readdirSync(targetDir)
  .filter((f) => f.endsWith('.png'))
  .sort();

let failed = false;

for (const file of files) {
  const filePath = join(targetDir, file);
  const stats = analyzeImage(filePath);
  const pass = stats.accentPct <= ACCENT_CAP_PCT;
  if (!pass) failed = true;

  const dimStr = `${stats.width}x${stats.height}`;
  const pctStr = `${stats.accentPct.toFixed(2)}%`;
  const capStr = `≤ ${ACCENT_CAP_PCT.toFixed(1)}%`;
  const resultStr = pass ? '✓ PASS' : '✗ FAIL';

  console.log(
    `| ${file.padEnd(24)}| ${dimStr.padEnd(12)}| ${pctStr.padEnd(10)}| ${capStr.padEnd(10)}| ${resultStr} |`
  );
}

console.log('='.repeat(80));

if (failed) {
  console.error(`\nOne or more views exceeded the 11% accent area ceiling!\n`);
  process.exit(1);
} else {
  console.log(`\nAll ${files.length} views comply with the 60/30/10 area budget (all ≤ 11% accent).\n`);
  process.exit(0);
}
