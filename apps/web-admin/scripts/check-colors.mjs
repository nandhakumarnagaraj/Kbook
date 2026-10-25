#!/usr/bin/env node
/**
 * One palette, one spelling of white.
 *
 * The system declares eleven names for #FFFFFF (surface, surface-3, input,
 * foreground-contrast, on-primary, plus legacy aliases) and until now 58 rules
 * retyped the value anyway: 33 colours, 14 backgrounds, 5 outlines, 4 gradient
 * stops and a spinner. Every one of those tokens resolves to #FFFFFF today, so
 * the swap is a rename - but a literal cannot follow the surface it paints, and
 * a later theme change would have left the whites behind. The inventory also
 * found `--kb-color-info-rgb: 29, 78, 216`, a blue that disagreed with both
 * `--kb-color-info` and `--kb-color-info-soft` (#4338CA), so one alpha border
 * was being drawn in a different blue from the ink and tint it belongs to.
 *
 * Three rules:
 *
 *   1. an alpha twin agrees with its solid token - `--x-rgb` must carry the
 *      channels of `--x`, and `--x-soft` must tint the channels of `--x`;
 *   2. white is a token, not a value - no #fff/#ffffff outside a custom-property
 *      declaration (the palette defines white; rules consume it). The QR
 *      encoder is exempt: it takes colour options, not CSS;
 *   3. a literal keeps its budget. A literal equal to exactly one token value
 *      fails (there is a name for it), except the near-black high-contrast ring
 *      listed below. Ambiguous values - shared by several names - and values
 *      with no token at all are budgeted per value, so the remainder can only
 *      shrink.
 *
 * Text is not CSS: `Kitchen Ticket #404` is not a colour. Only declaration
 * values, inline style attributes and SVG paint attributes are read.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const STYLES = join(SRC, 'styles.css');
const EXTENSIONS = ['.css', '.ts', '.html'];

/** The QR encoder receives colour strings; a CSS custom property cannot reach it. */
const NON_CSS_FILES = new Set(['src/app/core/utils/qr-code.util.ts']);

/**
 * Values that are deliberately not a palette role. Each is a per-value budget
 * of one, so a second copy still fails.
 */
const PINNED = new Map([
  [
    '#0f172a',
    'the prefers-contrast focus ring pins near-black on purpose; it must not follow a theme',
  ],
]);

/** Remaining literal usage, by value. Shared/unknown values only; may shrink. */
const LITERAL_BUDGET = new Map([
  ['#000000', 5], // prefers-contrast overrides in the auth screen
  ['#34d399', 2], // dark-theme pastel used as a chip ink
  ['#ff851b', 2], // active pagination fill (page-local orange)
  ['#f04438', 1], // collapsed-rail badge
  ['#fbbf24', 1], // amber chip ink
  ['#ffb31a', 1], // login SVG star
  ['#20195f', 1], // auth brand gradient stop
  ['#222b8a', 1], // auth brand gradient stop
  ['#3325b8', 1], // auth brand gradient stop
  ['#6847e8', 1], // login feature badge
  ['#0d9f83', 1], // login feature badge
  ['#d6dce5', 1], // audit stat edge
  ['#b45309', 1], // unsaved dock badge ink (three names share this value)
  ['#e2e8f0', 1], // KPI segment track (border value used as a fill)
]);

function walk(dir) {
  const found = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) found.push(...walk(full));
    else if (EXTENSIONS.some((ext) => entry.endsWith(ext)) && !entry.endsWith('.spec.ts')) {
      found.push(full);
    }
  }
  return found;
}

function blankComments(text) {
  const blank = (chunk) => chunk.replace(/[^\n]/g, ' ');
  return text
    .replace(/\/\*[\s\S]*?\*\//g, blank)
    .replace(/^[ \t]*\/\/[^\n]*/gm, blank);
}

function normaliseHex(hex) {
  const lower = hex.toLowerCase();
  return lower.length === 4
    ? '#' + lower[1] + lower[1] + lower[2] + lower[2] + lower[3] + lower[3]
    : lower;
}

const problems = [];

// ── 1. Read the palette and resolve aliases to a hex ────────────────────────
const stylesText = blankComments(readFileSync(STYLES, 'utf8'));
const declarations = new Map();
for (const match of stylesText.matchAll(/(--[\w-]+)\s*:\s*([^;{}]+);/g)) {
  if (!declarations.has(match[1])) declarations.set(match[1], match[2].trim());
}

function resolveHex(name, depth = 0) {
  if (depth > 8) return null;
  const value = declarations.get(name);
  if (!value) return null;
  const alias = value.match(/^var\((--[\w-]+)\)$/);
  if (alias) return resolveHex(alias[1], depth + 1);
  return /^#[0-9a-f]{3}(?:[0-9a-f]{3})?$/i.test(value) ? normaliseHex(value) : null;
}

const channels = (hex) => {
  const value = normaliseHex(hex);
  return [1, 3, 5].map((i) => parseInt(value.slice(i, i + 2), 16));
};

// ── 2. Channel twins agree with their solid token ───────────────────────────
let twins = 0;
for (const [name, value] of declarations) {
  const rgb = name.match(/^(--[\w-]+)-rgb$/);
  const soft = name.match(/^(--[\w-]+)-soft$/);

  if (rgb) {
    const base = rgb[1];
    const solid = resolveHex(base);
    const triple = value.match(/^\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*$/);
    if (!declarations.has(base)) {
      problems.push(`src/styles.css  ${name}  ->  no ${base} token to project`);
    } else if (!solid) {
      problems.push(`src/styles.css  ${name}  ->  ${base} does not resolve to a hex colour`);
    } else if (!triple) {
      problems.push(`src/styles.css  ${name}: ${value}  ->  expected "r, g, b" channels`);
    } else {
      const expected = channels(solid);
      const actual = triple.slice(1).map(Number);
      if (actual.some((channel, i) => channel !== expected[i])) {
        problems.push(
          `src/styles.css  ${name}: ${actual.join(', ')}  ->  ${base} is ${solid} ` +
            `(${expected.join(', ')}); an alpha twin must tint its own token`
        );
      }
      twins++;
    }
  }

  if (soft) {
    const base = soft[1];
    const solid = resolveHex(base);
    const tint = value.match(/^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*[\d.]+)?\)$/);
    if (!tint) continue; // an alias such as --kb-color-error-soft resolves elsewhere
    if (!solid) {
      problems.push(`src/styles.css  ${name}  ->  ${base} does not resolve to a hex colour`);
      continue;
    }
    const expected = channels(solid);
    const actual = tint.slice(1).map(Number);
    if (actual.some((channel, i) => channel !== expected[i])) {
      problems.push(
        `src/styles.css  ${name}: rgb(${actual.join(', ')})  ->  ${base} is ${solid} ` +
          `(${expected.join(', ')}); a soft tint must tint its own token`
      );
    }
    twins++;
  }
}

// ── 3. Walk every literal; classify CSS usage, declaration, or text ─────────
const valueToTokens = new Map();
for (const name of declarations.keys()) {
  const value = resolveHex(name);
  if (!value) continue;
  if (!valueToTokens.has(value)) valueToTokens.set(value, []);
  valueToTokens.get(value).push(name);
}

const HEX = /#(?:[0-9a-f]{3}|[0-9a-f]{6})\b/gi;

/** The declaration (if any) whose value contains this literal. */
function declarationOf(text, index) {
  const before = text.slice(0, index);
  const cut = Math.max(
    before.lastIndexOf(';'),
    before.lastIndexOf('{'),
    before.lastIndexOf('}'),
    before.lastIndexOf('\n')
  );
  const match = before.slice(cut + 1).match(/^\s*(--)?([\w-]+)\s*:\s*[^;{}]*$/);
  return match ? { custom: Boolean(match[1]), name: match[2] } : null;
}

/** Is this literal painted by an SVG/HTML attribute or an inline style? */
function isMarkupPaint(text, index) {
  const before = text.slice(0, index);
  return (
    /(?:fill|stroke|stop-color|flood-color|lighting-color|color|bgcolor)\s*=\s*["']?$/i.test(
      before
    ) || /style\s*=\s*["'][^"']*$/i.test(before)
  );
}

/** A value continued from the previous line (gradient stop lists, shadow lists). */
function isContinuation(text, index) {
  const before = text.slice(0, index);
  const lineStart = before.lastIndexOf('\n');
  const previousStart = before.lastIndexOf('\n', lineStart - 1);
  const previous = before.slice(previousStart + 1, lineStart).trim();
  const segment = before.slice(lineStart + 1);
  return /[,([]\s*$/.test(previous) && /^\s*[^:;{}]*$/.test(segment);
}

const literalUsages = [];
const ignoredText = [];
const whiteUsages = [];

for (const path of walk(SRC)) {
  const relativePath = relative(ROOT, path).replaceAll('\\', '/');
  if (NON_CSS_FILES.has(relativePath)) continue;

  const text = blankComments(readFileSync(path, 'utf8'));
  for (const match of text.matchAll(HEX)) {
    const line = text.slice(0, match.index).split('\n').length;
    const where = `${relativePath}:${line}`;
    const value = normaliseHex(match[0]);

    const declaration = declarationOf(text, match.index);
    const paint = isMarkupPaint(text, match.index);
    const continuation = isContinuation(text, match.index);

    if (!declaration && !paint && !continuation) {
      ignoredText.push(`${where}  ${match[0]}`);
      continue;
    }

    // The palette itself defines values; rules consume tokens.
    if (declaration?.custom) continue;

    if (value === '#ffffff') {
      whiteUsages.push(`${where}  ${match[0]}  ->  use a white token (surface, input, on-primary, foreground-contrast)`);
      continue;
    }

    literalUsages.push({ where, value, source: match[0] });
  }
}

problems.push(...whiteUsages);

// ── 4. A literal with exactly one name must use that name; the rest is budget ─
const literalCounts = new Map();
for (const usage of literalUsages) {
  const candidates = valueToTokens.get(usage.value) ?? [];
  if (candidates.length === 1 && !PINNED.has(usage.value)) {
    problems.push(
      `${usage.where}  ${usage.source}  ->  exactly one token holds this value ` +
        `(${candidates[0]}); use the token`
    );
    continue;
  }
  if (PINNED.has(usage.value)) continue;
  literalCounts.set(usage.value, (literalCounts.get(usage.value) || 0) + 1);
}

for (const [value, count] of literalCounts) {
  const budget = LITERAL_BUDGET.get(value);
  if (budget === undefined) {
    problems.push(
      `${count} use(s) of ${value}  ->  not in the literal budget; add a token or budget the value ` +
        `with a reason`
    );
  } else if (count > budget) {
    problems.push(`${count} use(s) of ${value}  ->  the budget is ${budget}; a new literal needs a token`);
  }
}
for (const [value, reason] of PINNED) {
  const count = literalUsages.filter((usage) => usage.value === value).length;
  if (count > 1) {
    problems.push(`${count} use(s) of ${value}  ->  pinned values may appear once (${reason})`);
  }
}

if (problems.length) {
  console.error(`Colour check failed: ${problems.length} problem(s)\n`);
  for (const problem of problems) console.error(`  ${problem}`);
  console.error('\nThe palette lives in src/styles.css. Consume it; do not retype it.');
  process.exit(1);
}

const budgeted = [...literalCounts.values()].reduce((a, b) => a + b, 0);
const pinned = [...PINNED.keys()].reduce(
  (sum, value) => sum + literalUsages.filter((usage) => usage.value === value).length,
  0
);
console.log(
  `Colour check passed: ${twins} channel twin(s) agree with their solid token; ` +
    `0 white literal(s) outside the palette; ${budgeted} literal usage(s) budgeted across ` +
    `${literalCounts.size} value(s)` +
    (pinned ? `, ${pinned} pinned high-contrast value(s)` : '') +
    `.`
);
console.log(
  `Remaining values: ${[...literalCounts.keys()].sort().join(', ')}. ` +
    `${ignoredText.length} hex-like string(s) in text were ignored.`
);
