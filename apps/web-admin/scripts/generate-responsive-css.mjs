#!/usr/bin/env node
/**
 * generate-responsive-css.mjs
 * ---------------------------
 * Build-time program that calculates screen sizes and font sizes for the
 * KhanaBook webadmin dashboard, then emits a single CSS file with custom
 * properties keyed to the project's breakpoint ladder.
 *
 * Why build-time instead of runtime JS:
 *   - Zero runtime cost: the browser only sees static CSS custom properties.
 *   - No layout shift: values are present before the first paint.
 *   - Single source of truth: one ladder (480/640/768/1024/1100) shared by
 *     both the breakpoint checker and the generated values.
 *
 * Output: src/styles/responsive.css
 *   - --kb-font-size-{breakpoint}   : base font size at that width
 *   - --kb-container-width-{breakpoint}: max content width at that width
 *   - --kb-gap-{breakpoint}          : grid gap at that width
 *   - --kb-line-height-{breakpoint}  : line height at that width
 *   - --kb-vh-safe-{breakpoint}      : safe viewport height (accounts for
 *                                      mobile browser chrome)
 *   All values clamp between the smallest and largest step so nothing
 *   overflows vertically on any device.
 */

import { writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = process.cwd();
const OUT_DIR = join(ROOT, 'src', 'styles');
const OUT_FILE = join(OUT_DIR, 'responsive.css');

/** The project's breakpoint ladder (matches docs/design and check-breakpoints.mjs). */
const BREAKPOINTS = [480, 640, 768, 1024, 1100];

/** Base font size in px at the smallest breakpoint. */
const BASE_FONT_SIZE = 14;

/** Maximum font size in px at the largest breakpoint. */
const MAX_FONT_SIZE = 18;

/** Container width as a fraction of viewport width at each breakpoint. */
const CONTAINER_FRAC = {
  480: 0.92,   // phone portrait: near full width
  640: 0.90,   // phone landscape
  768: 0.85,   // tablet
  1024: 0.80,  // laptop
  1100: 0.75,  // wide desktop
};

/** Grid gap as px at each breakpoint. */
const GAP = {
  480: 12,
  640: 16,
  768: 20,
  1024: 24,
  1100: 28,
};

/** Safe viewport height as fraction (accounts for mobile browser chrome). */
const SAFE_VH_FRAC = 0.92;

/** Minimum line height (unitless multiplier). */
const MIN_LINE_HEIGHT = 1.45;

/** Maximum line height (unitless multiplier). */
const MAX_LINE_HEIGHT = 1.6;

/**
 * Linear interpolation between smallest and largest breakpoint.
 * Returns the value at `width` assuming linear scaling across the ladder.
 */
function lerp(width, minVal, maxVal) {
  const minW = BREAKPOINTS[0];
  const maxW = BREAKPOINTS[BREAKPOINTS.length - 1];
  const t = (width - minW) / (maxW - minW);
  return minVal + t * (maxVal - minVal);
}

/** Round to 2 decimal places for CSS output. */
function r2(v) {
  return Math.round(v * 100) / 100;
}

/** Round to 3 decimal places for CSS output (used by unitless line-height). */
function r3(v) {
  return Math.round(v * 1000) / 1000;
}

/** Generate the CSS file content. */
function generateCss() {
  const lines = [];
  lines.push('/* ============================================================================');
  lines.push('   KB Responsive Tokens — GENERATED FILE, DO NOT EDIT BY HAND');
  lines.push('   ----------------------------------------------------------------------------');
  lines.push('   Build-time calculated screen sizes and font sizes for the breakpoint ladder:');
  lines.push(`   ${BREAKPOINTS.join(', ')}px`);
  lines.push('');
  lines.push('   Regenerate with: npm run generate:responsive');
  lines.push('   ============================================================================ */');
  lines.push('');
  lines.push(':root {');

  // Base values (no breakpoint qualifier — used as fallbacks)
  lines.push(`  /* Fallback values (smallest breakpoint) */`);
  lines.push(`  --kb-font-size: ${r2(BASE_FONT_SIZE)}px;`);
  lines.push(`  --kb-container-width: ${r2(windowWidth(480))}px;`);
  lines.push(`  --kb-gap: ${GAP[480]}px;`);
  lines.push(`  --kb-line-height: ${MIN_LINE_HEIGHT};`);
  lines.push(`  --kb-vh-safe: ${r2(windowHeight(480) * SAFE_VH_FRAC)}px;`);
  lines.push('');

  for (const bp of BREAKPOINTS) {
    const fontSize = r2(lerp(bp, BASE_FONT_SIZE, MAX_FONT_SIZE));
    const containerW = r2(windowWidth(bp));
    const gap = GAP[bp];
    const lineHeight = r3(MIN_LINE_HEIGHT + (bp - 480) / (1100 - 480) * (MAX_LINE_HEIGHT - MIN_LINE_HEIGHT));
    const safeVh = r2(windowHeight(bp) * SAFE_VH_FRAC);

    lines.push(`  /* ${bp}px breakpoint */`);
    lines.push(`  --kb-font-size-${bp}: ${fontSize}px;`);
    lines.push(`  --kb-container-width-${bp}: ${containerW}px;`);
    lines.push(`  --kb-gap-${bp}: ${gap}px;`);
    lines.push(`  --kb-line-height-${bp}: ${lineHeight};`);
    lines.push(`  --kb-vh-safe-${bp}: ${safeVh}px;`);
    lines.push('');
  }

  lines.push('}');
  lines.push('');
  lines.push('/* ============================================================================');
  lines.push('   Usage:');
  lines.push('   - Font size:   font-size: var(--kb-font-size, var(--kb-font-size-768));');
  lines.push('   - Container:   max-width: var(--kb-container-width, var(--kb-container-width-768));');
  lines.push('   - Grid gap:    gap: var(--kb-gap, var(--kb-gap-768));');
  lines.push('   - Line height: line-height: var(--kb-line-height, var(--kb-line-height-768));');
  lines.push('   - Safe VH:     min-height: var(--kb-vh-safe, var(--kb-vh-safe-768));');
  lines.push('   ============================================================================ */');

  return lines.join('\n') + '\n';
}

/** Container width in px at a given viewport width. */
function windowWidth(bp) {
  return bp * CONTAINER_FRAC[bp];
}

/** Viewport height estimate at a given width (rough aspect ratio). */
function windowHeight(bp) {
  // Assume 16:9 aspect ratio, then scale down for mobile chrome
  return bp * (9 / 16);
}

function main() {
  if (!existsSync(OUT_DIR)) {
    mkdirSync(OUT_DIR, { recursive: true });
  }

  const css = generateCss();
  writeFileSync(OUT_FILE, css, 'utf8');

  console.log(`✓ Generated ${OUT_FILE}`);
  console.log(`  Breakpoints: ${BREAKPOINTS.join(', ')}px`);
  console.log(`  Font size range: ${BASE_FONT_SIZE}px → ${MAX_FONT_SIZE}px`);
  console.log(`  Container width range: ${r2(windowWidth(480))}px → ${r2(windowWidth(1100))}px`);
}

main();
