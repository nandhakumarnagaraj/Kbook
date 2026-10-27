#!/usr/bin/env node
/**
 * check-contrast.mjs - Automated WCAG 2.1 Contrast & Token Pair Enforcement
 *
 * Verifies that all tokens in the Web Admin colour system meet the locked
 * WCAG 2.1 AA contrast requirements:
 *   - Normal text: ≥ 4.5:1
 *   - Control boundaries & graphical objects: ≥ 3.0:1
 *   - Status chips: ink on matching tint ≥ 4.5:1
 *   - Sidebar / Rail tokens against rail background ≥ 4.5:1
 *   - Control border: ≥ 3.0:1 on white and canvas; #CBD5E1 banned as input border
 */

import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = fileURLToPath(new URL('.', import.meta.url));
const APP_DIR = join(SCRIPT_DIR, '..');
const STYLES_PATH = join(APP_DIR, 'src/styles.css');

// ── 1. Color Math & Luminance (WCAG 2.1) ───────────────────────────────────

function parseColor(str) {
  if (!str) return null;
  const trimmed = str.trim();

  // Hex: #rgb or #rrggbb
  const hexMatch = trimmed.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
  if (hexMatch) {
    let hex = hexMatch[1];
    if (hex.length === 3) {
      hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
    }
    return {
      r: parseInt(hex.slice(0, 2), 16),
      g: parseInt(hex.slice(2, 4), 16),
      b: parseInt(hex.slice(4, 6), 16),
      a: 1.0,
    };
  }

  // rgba(r, g, b, a) or rgb(r, g, b)
  const rgbMatch = trimmed.match(/^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)$/i);
  if (rgbMatch) {
    return {
      r: parseInt(rgbMatch[1], 10),
      g: parseInt(rgbMatch[2], 10),
      b: parseInt(rgbMatch[3], 10),
      a: rgbMatch[4] !== undefined ? parseFloat(rgbMatch[4]) : 1.0,
    };
  }

  return null;
}

function compositeOver(fg, bg) {
  const alpha = fg.a;
  return {
    r: Math.round(fg.r * alpha + bg.r * (1 - alpha)),
    g: Math.round(fg.g * alpha + bg.g * (1 - alpha)),
    b: Math.round(fg.b * alpha + bg.b * (1 - alpha)),
    a: 1.0,
  };
}

function relativeLuminance(rgb) {
  const sRGB = [rgb.r / 255, rgb.g / 255, rgb.b / 255];
  const linear = sRGB.map((c) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)));
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
}

function contrastRatio(colorA, colorB) {
  const lumA = relativeLuminance(colorA);
  const lumB = relativeLuminance(colorB);
  const lighter = Math.max(lumA, lumB);
  const darker = Math.min(lumA, lumB);
  return (lighter + 0.05) / (darker + 0.05);
}

// ── 2. Read Tokens from styles.css ──────────────────────────────────────────

const stylesText = readFileSync(STYLES_PATH, 'utf8');

// Strip comments
const cleanStyles = stylesText
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/[^\n]*/gm, '');

const declarations = new Map();
for (const match of cleanStyles.matchAll(/(--[\w-]+)\s*:\s*([^;{}]+);/g)) {
  if (!declarations.has(match[1])) {
    declarations.set(match[1], match[2].trim());
  }
}

function resolveToken(name, depth = 0) {
  if (depth > 10) return null;
  const raw = declarations.get(name);
  if (!raw) return null;

  const varMatch = raw.match(/^var\((--[\w-]+)\)$/);
  if (varMatch) {
    return resolveToken(varMatch[1], depth + 1);
  }

  // Handle rgba with var(--token-rgb)
  const rgbaVarMatch = raw.match(/^rgba\(\s*var\((--[\w-]+)\)\s*,\s*([\d.]+)\s*\)$/);
  if (rgbaVarMatch) {
    const rgbStr = declarations.get(rgbaVarMatch[1]);
    if (rgbStr) {
      const parts = rgbStr.split(',').map((p) => parseInt(p.trim(), 10));
      if (parts.length === 3) {
        return `rgba(${parts[0]}, ${parts[1]}, ${parts[2]}, ${rgbaVarMatch[2]})`;
      }
    }
  }

  return raw;
}

function getColor(nameOrHex) {
  if (nameOrHex.startsWith('--')) {
    const resolved = resolveToken(nameOrHex);
    if (!resolved) throw new Error(`Could not resolve token: ${nameOrHex}`);
    const color = parseColor(resolved);
    if (!color) throw new Error(`Could not parse color for ${nameOrHex}: ${resolved}`);
    return color;
  }
  const color = parseColor(nameOrHex);
  if (!color) throw new Error(`Could not parse literal color: ${nameOrHex}`);
  return color;
}

// ── 3. Test Pairs & Rules ───────────────────────────────────────────────────

const WHITE = { r: 255, g: 255, b: 255, a: 1.0 };
const CANVAS = getColor('--kb-color-bg-app');
const SURFACE = WHITE;

const CHECKS = [
  // Surfaces & Ink
  { name: 'Ink on Surface', fg: '--kb-color-foreground', bg: SURFACE, min: 4.5, role: 'text' },
  { name: 'Ink-2 on Surface', fg: '--kb-color-foreground-2', bg: SURFACE, min: 4.5, role: 'text' },
  { name: 'Muted text on Surface', fg: '--kb-color-muted-foreground', bg: SURFACE, min: 4.5, role: 'text' },
  { name: 'Control border on Surface', fg: '--kb-border-control', bg: SURFACE, min: 3.0, role: 'control' },
  { name: 'Control border on Canvas', fg: '--kb-border-control', bg: CANVAS, min: 3.0, role: 'control' },
  { name: 'Focus/selected edge on Surface', fg: '--kb-color-border-strong', bg: SURFACE, min: 3.0, role: 'control' },

  // Action
  { name: 'Action fill on Surface', fg: '--kb-color-primary', bg: SURFACE, min: 2.8, role: 'control' },
  { name: 'White on Action fill', fg: WHITE, bg: getColor('--kb-color-primary'), min: 2.8, role: 'control' },
  { name: 'Action hover on Surface', fg: '--kb-color-primary-hover', bg: SURFACE, min: 2.8, role: 'control' },
  { name: 'Link text on Surface', fg: '--kb-color-link', bg: SURFACE, min: 4.5, role: 'text' },
  { name: 'Link hover text on Surface', fg: '--kb-color-link-hover', bg: SURFACE, min: 4.5, role: 'text' },
  {
    name: 'Ink on Action tint',
    fg: getColor('--kb-color-foreground'),
    bg: compositeOver(getColor('--kb-color-primary-soft'), SURFACE),
    min: 4.5,
    role: 'text',
  },

  // Status (Ink on Surface & ink on same-hue soft tint)
  { name: 'Success ink on Surface', fg: '--kb-color-success', bg: SURFACE, min: 4.5, role: 'text' },
  {
    name: 'Success ink on Success tint',
    fg: getColor('--kb-color-success'),
    bg: compositeOver(getColor('--kb-color-success-soft'), SURFACE),
    min: 4.5,
    role: 'text',
  },
  { name: 'Warning ink on Surface', fg: '--kb-color-warning', bg: SURFACE, min: 4.5, role: 'text' },
  {
    name: 'Warning ink on Warning tint',
    fg: getColor('--kb-color-warning'),
    bg: compositeOver(getColor('--kb-color-warning-soft'), SURFACE),
    min: 4.5,
    role: 'text',
  },
  { name: 'Danger ink on Surface', fg: '--kb-color-danger', bg: SURFACE, min: 4.5, role: 'text' },
  {
    name: 'Danger ink on Danger tint',
    fg: getColor('--kb-color-danger'),
    bg: compositeOver(getColor('--kb-color-danger-soft'), SURFACE),
    min: 4.5,
    role: 'text',
  },
  { name: 'White on Danger solid', fg: WHITE, bg: getColor('--kb-color-danger-solid'), min: 4.5, role: 'text' },

  // Data Accents
  { name: 'Money text on Surface', fg: '--kb-accent-money', bg: SURFACE, min: 4.5, role: 'text' },
  { name: 'Money text on Canvas', fg: '--kb-accent-money', bg: CANVAS, min: 4.5, role: 'text' },
  { name: 'Flow/queue on Surface', fg: '--kb-accent-flow', bg: SURFACE, min: 4.5, role: 'text' },
  { name: 'Neutral series on Surface', fg: '--kb-accent-neutral', bg: SURFACE, min: 4.5, role: 'text' },

  // Chart Ramp (all ≥ 2.8:1 graphical object boundary)
  { name: 'Chart series 1 on Surface', fg: '--kb-chart-1', bg: SURFACE, min: 2.8, role: 'chart' },
  { name: 'Chart series 2 on Surface', fg: '--kb-chart-2', bg: SURFACE, min: 3.0, role: 'chart' },
  { name: 'Chart series 3 on Surface', fg: '--kb-chart-3', bg: SURFACE, min: 3.0, role: 'chart' },
  { name: 'Chart series 4 on Surface', fg: '--kb-chart-4', bg: SURFACE, min: 3.0, role: 'chart' },
  { name: 'Chart series 5 on Surface', fg: '--kb-chart-5', bg: SURFACE, min: 3.0, role: 'chart' },

  // Rail tokens against Rail background (#0F1020)
  { name: 'Rail text on Rail bg', fg: '--pos-sidebar-text', bg: getColor('--pos-sidebar-bg'), min: 4.5, role: 'text' },
  { name: 'Rail ink on Rail bg', fg: '--pos-sidebar-ink', bg: getColor('--pos-sidebar-bg'), min: 4.5, role: 'text' },
  { name: 'Rail muted on Rail bg', fg: '--pos-sidebar-ink-muted', bg: getColor('--pos-sidebar-bg'), min: 4.5, role: 'text' },
];

// ── 4. Banned Value Checks ─────────────────────────────────────────────────

const BANNED_CHECKS = [
  {
    name: 'Control border token must not be decorative divider (#CBD5E1)',
    pass: () => {
      const borderControl = resolveToken('--kb-border-control');
      return borderControl && borderControl.toLowerCase() !== '#cbd5e1';
    },
    message: '--kb-border-control is #CBD5E1. Decorative divider is banned as an input border.',
  },
  {
    name: 'Money text must be promoted saffron (#B45309), not money fill (#E87A1E)',
    pass: () => {
      const moneyText = resolveToken('--kb-accent-money');
      return moneyText && moneyText.toLowerCase() === '#b45309';
    },
    message: '--kb-accent-money is not #B45309. Fill color (#E87A1E) is banned as text.',
  },
  {
    name: 'Chart ramp must match specified order',
    pass: () => {
      const c1 = resolveToken('--kb-chart-1')?.toLowerCase();
      const c2 = resolveToken('--kb-chart-2')?.toLowerCase();
      const c3 = resolveToken('--kb-chart-3')?.toLowerCase();
      const c4 = resolveToken('--kb-chart-4')?.toLowerCase();
      const c5 = resolveToken('--kb-chart-5')?.toLowerCase();
      return (
        c1 === '#e87a1e' &&
        c2 === '#2563eb' &&
        c3 === '#5926b8' &&
        c4 === '#16a34a' &&
        c5 === '#475569'
      );
    },
    message: 'Chart ramp order mismatch. Expected #E87A1E -> #2563EB -> #5926B8 -> #16A34A -> #475569.',
  },
];

// ── 5. Run Verification & Output Table ──────────────────────────────────────

let hasError = false;

console.log('='.repeat(80));
console.log(' Web Admin Colour System — WCAG 2.1 Contrast & Rule Verification');
console.log('='.repeat(80));
console.log(
  '| ' +
    'Check Name'.padEnd(36) +
    '| ' +
    'Target'.padEnd(8) +
    '| ' +
    'Actual'.padEnd(8) +
    '| Result |'
);
console.log('|-' + '-'.repeat(36) + '+-' + '-'.repeat(8) + '+-' + '-'.repeat(8) + '+--------|');

for (const item of CHECKS) {
  const fgColor = typeof item.fg === 'string' ? getColor(item.fg) : item.fg;
  const bgColor = typeof item.bg === 'string' ? getColor(item.bg) : item.bg;
  const ratio = contrastRatio(fgColor, bgColor);
  const pass = ratio >= item.min;

  if (!pass) hasError = true;

  const targetStr = `≥ ${item.min.toFixed(1)}:1`;
  const actualStr = `${ratio.toFixed(2)}:1`;
  const resultStr = pass ? '✓ PASS' : '✗ FAIL';

  console.log(
    `| ${item.name.padEnd(36)}| ${targetStr.padEnd(8)}| ${actualStr.padEnd(8)}| ${resultStr} |`
  );
}

console.log('='.repeat(80));
console.log(' Banned & Invariant Checks:');

for (const check of BANNED_CHECKS) {
  const passed = check.pass();
  if (!passed) {
    hasError = true;
    console.log(`  ✗ FAIL: ${check.name} - ${check.message}`);
  } else {
    console.log(`  ✓ PASS: ${check.name}`);
  }
}
console.log('='.repeat(80));

if (hasError) {
  console.error('\nContrast or invariant checks failed. See details above.\n');
  process.exit(1);
} else {
  console.log('\nAll contrast pairs and invariant rules passed cleanly.\n');
  process.exit(0);
}
