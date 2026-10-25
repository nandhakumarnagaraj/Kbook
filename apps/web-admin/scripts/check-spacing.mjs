#!/usr/bin/env node
/**
 * Spacing and radius must spend the scale, not invent a value next to it.
 *
 * Freeze rule 6 fixes spacing at 4/8/12/16/20/24/32/48/64 and the radius ladder at
 * 4/6/8/12/16/full. Before these checks, `gap` was 14% tokens, `border-radius` 35%,
 * `padding` and `margin` had never been measured: 38 distinct radii and 49 distinct gaps,
 * 999px next to 9999px, and 0.375rem in a gap while the radius ladder owned 6px. None of
 * that was a decision anyone made twice.
 *
 * The rule has four parts:
 *
 *   1. a literal that equals a step is a regression — `gap: 8px` is `var(--kb-space-2)`
 *      spelled by hand, and nothing makes the next author use the token;
 *   2. an off-scale literal is allowed only inside BUDGET, and only up to its count;
 *   3. `var(--r-*)` is gone (the five aliases were deleted), so a reference to it fails;
 *   4. a declaration is only judged when every part is CSS. `margin: 2,` in the
 *      QR-code options and an inline shorthand that runs into template text are
 *      skipped, not reported — a false failure teaches people to ignore the check.
 *
 * Off-scale values are not snapped here. The gap ladder is 4/8/12/16/20/24/32/48/64, but
 * the CSS repeatedly asks for 0.35rem (5.6px), 0.45rem (7.2px) and 0.65rem (10.4px) — a
 * tighter rhythm than the ladder, used on purpose. Moving those is a visual decision, not
 * a rename.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const EXTENSIONS = ['.css', '.ts'];

/** Steps, in px. `full` is the pill and is never written as a literal. */
const RADIUS_STEPS = new Map([
  [4, '--kb-radius-sm'],
  [6, '--kb-radius-md'],
  [8, '--kb-radius-lg'],
  [12, '--kb-radius-xl'],
  [16, '--kb-radius-2xl'],
]);
const SPACE_STEPS = new Map([
  [4, '--kb-space-1'],
  [8, '--kb-space-2'],
  [12, '--kb-space-3'],
  [16, '--kb-space-4'],
  [20, '--kb-space-5'],
  [24, '--kb-space-6'],
  [32, '--kb-space-8'],
  [40, '--kb-space-10'],
  [48, '--kb-space-12'],
  [64, '--kb-space-16'],
]);

const GROUPS = [
  {
    name: 'radius',
    props: ['border-radius'],
    steps: RADIUS_STEPS,
    budget: {
      '10px': 19,
      '14px': 4,
      '9px': 3,
      '18px': 3,
      '7px': 2,
      '2px': 2,
      '1px': 1,
      '3px': 1,
      '11px': 1,
      '15px': 1,
      '20px': 1,
      '22px': 1,
    },
  },
  {
    name: 'gap',
    props: ['gap', 'row-gap', 'column-gap'],
    steps: SPACE_STEPS,
    budget: {
      '0.35rem': 21,
      '0.4rem': 18,
      '0.65rem': 15,
      '0.45rem': 15,
      '0.6rem': 12,
      '6px': 11,
      '0.85rem': 11,
      '0.3rem': 10,
      '0.15rem': 5,
      '3px': 4,
      '5px': 3,
      '18px': 3,
      '1.15rem': 3,
      '0.2rem': 2,
      '14px': 2,
      '0.7rem': 2,
      '0.1rem': 1,
      '0.22rem': 1,
      '0.375rem': 1,
      '0.8rem': 1,
      '0.9rem': 1,
      '1.125rem': 1,
      '22px': 1,
      '7px': 1,
    },
  },
  {
    name: 'padding',
    props: ['padding'],
    steps: SPACE_STEPS,
    budget: {
      '0.85rem': 19,
      '0.55rem': 18,
      '0.65rem': 15,
      '0.2rem': 15,
      '2px': 14,
      '0.45rem': 13,
      '0.6rem': 13,
      '0.3rem': 12,
      '1.15rem': 10,
      '6px': 8,
      '3px': 7,
      '0.9rem': 7,
      '0.15rem': 7,
      '0.35rem': 7,
      '0.7rem': 6,
      '0.4rem': 6,
      '10px': 6,
      '1.35rem': 6,
      '1.1rem': 5,
      '0.8rem': 5,
      '0.1rem': 3,
      '1.4rem': 3,
      '5px': 2,
      '1px': 2,
      '1.75rem': 2,
      '14px': 2,
      '2.25rem': 2,
      '0.18rem': 2,
      '0.95rem': 2,
      '18px': 2,
      '2.2rem': 1,
      '1.2rem': 1,
      '23px': 1,
      '0.08rem': 1,
      '0.12rem': 1,
      '9px': 1,
      '13px': 1,
      '30px': 1,
      '3.5rem': 1,
      '0.22rem': 1,
      '0.32rem': 1,
    },
  },
  {
    name: 'margin',
    props: ['margin'],
    steps: SPACE_STEPS,
    budget: {
      '0.35rem': 7,
      '-1px': 3,
      '0.2rem': 2,
      // The payment-methods error note's 2px top margin was already in the
      // file, but the inline shorthand ran into template text and was skipped.
      // Moving it into the stylesheet makes it visible; pixels are unchanged.
      '2px': 3,
      '-0.25rem': 2,
      '-1.5rem': 2,
      '0.65rem': 1,
      '0.55rem': 1,
      '0.1rem': 1,
      '0.45rem': 1,
      '3px': 1,
      '0.12rem': 1,
      '0.15rem': 1,
    },
  },
];

const BY_PROPERTY = new Map();
for (const group of GROUPS) for (const prop of group.props) BY_PROPERTY.set(prop, group);

const PROPERTY_PATTERN = [...BY_PROPERTY.keys()].join('|');
const DECLARATION = new RegExp(
  `(?<![\\w-])(${PROPERTY_PATTERN})\\s*:\\s*([^;{}\\n]+)`,
  'g'
);

/** A single part that is a length with any unit — the only shapes we judge are px/rem. */
const LENGTH = /^-?\d*\.?\d+(px|rem|em|%|vh|vw|svh|svw|dvh|dvw|ch|ex|pt|pc|in|cm|mm|q)$/i;
const KEYWORDS = /^(0|auto|inherit|initial|revert|unset|none|50%|100%)$/;
const FUNCTION = /^(calc|min|max|clamp|env|var)\(/;
const PX_OR_REM = /^(-?\d*\.?\d+)(px|rem)$/i;

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

/** Whitespace split that keeps `var(--a, calc(1px + 2px))` in one part. */
function parts(value) {
  const out = [];
  let depth = 0;
  let current = '';
  for (const ch of value) {
    if (ch === '(') depth += 1;
    if (ch === ')') depth -= 1;
    if (/\s/.test(ch) && depth === 0) {
      if (current) out.push(current);
      current = '';
    } else current += ch;
  }
  if (current) out.push(current);
  return out;
}

function toPx(value) {
  const match = value.match(PX_OR_REM);
  if (!match) return null;
  return match[2].toLowerCase() === 'rem' ? Number(match[1]) * 16 : Number(match[1]);
}

const stats = new Map(
  GROUPS.map((group) => [group.name, { token: 0, exempt: 0, otherUnit: 0, literal: new Map() }])
);
const problems = [];
let skipped = 0;

for (const path of walk(SRC)) {
  const text = blankComments(readFileSync(path, 'utf8'));
  for (const match of text.matchAll(DECLARATION)) {
    const group = BY_PROPERTY.get(match[1]);
    const bucket = stats.get(group.name);
    const line = text.slice(0, match.index).split('\n').length;
    const where = `${relative(ROOT, path)}:${line}`;
    const value = match[2].replace(/\s*!important\s*$/, '').trim();
    const valueParts = parts(value);

    // Only judge a declaration when every part is CSS. See rule 4 in the header.
    const isCss = valueParts.every(
      (part) => part.startsWith('var(') || FUNCTION.test(part) || KEYWORDS.test(part) || LENGTH.test(part)
    );
    if (!isCss) {
      skipped += 1;
      continue;
    }

    for (const part of valueParts) {
      if (part.startsWith('var(')) {
        bucket.token += 1;
        if (/^var\(--r-/.test(part)) {
          problems.push(`${where}  ${match[1]}: ${part}  ->  --r-* was deleted; use --kb-radius-*`);
        }
        continue;
      }
      if (FUNCTION.test(part) || KEYWORDS.test(part)) {
        bucket.exempt += 1;
        continue;
      }
      if (!PX_OR_REM.test(part)) {
        // mm in the print stylesheet, em in a one-off — not drift on this ladder.
        bucket.otherUnit += 1;
        continue;
      }

      bucket.literal.set(part, (bucket.literal.get(part) || 0) + 1);
      const px = toPx(part);
      if (px !== null && px >= 0 && group.steps.has(px)) {
        problems.push(
          `${where}  ${match[1]}: ${part}  ->  that is ${group.steps.get(px)}; the literal is the token spelled by hand`
        );
        continue;
      }
      if (group.budget[part] === undefined) {
        problems.push(
          `${where}  ${match[1]}: ${part}  ->  not a step and not budgeted; ` +
            'use a token, or add a deliberate value to the budget'
        );
      }
    }
  }
}

for (const group of GROUPS) {
  const bucket = stats.get(group.name);
  for (const [value, allowed] of Object.entries(group.budget)) {
    const count = bucket.literal.get(value) || 0;
    if (count > allowed) {
      problems.push(
        `${group.name}: ${value}  -> used ${count}× but the budget allows ${allowed}×; ` +
          'an existing off-scale value may shrink, never grow'
      );
    }
  }
}

function percent(part, total) {
  return total ? Math.round((part / total) * 100) : 100;
}

const totals = { token: 0, exempt: 0, otherUnit: 0, literal: 0 };
for (const bucket of stats.values()) {
  const literal = [...bucket.literal.values()].reduce((a, b) => a + b, 0);
  totals.token += bucket.token;
  totals.exempt += bucket.exempt;
  totals.otherUnit += bucket.otherUnit;
  totals.literal += literal;
}
const all = totals.token + totals.exempt + totals.otherUnit + totals.literal;

if (problems.length) {
  console.error(`Spacing and radius check failed: ${problems.length} problem(s)\n`);
  for (const problem of problems) console.error(`  ${problem}`);
  console.error(
    '\nRadius: 4/6/8/12/16/full.  Spacing: 4/8/12/16/20/24/32/48/64.  ' +
      'Pick the nearest token, or budget a value you can defend.'
  );
  process.exit(1);
}

const summary = GROUPS.map((group) => {
  const bucket = stats.get(group.name);
  const literal = [...bucket.literal.values()].reduce((a, b) => a + b, 0);
  const total = bucket.token + bucket.exempt + bucket.otherUnit + literal;
  return `${group.name} ${bucket.token}/${total} (${percent(bucket.token, total)}%)`;
});
const offScale = GROUPS.map((group) => {
  const bucket = stats.get(group.name);
  const literal = [...bucket.literal.values()].reduce((a, b) => a + b, 0);
  return `${literal} in ${bucket.literal.size}`;
});

console.log(`Spacing and radius check passed: ${summary.join(', ')}.`);
console.log(`Overall ${totals.token}/${all} parts tokenised (${percent(totals.token, all)}%).`);
console.log(
  `Still off-scale (all budgeted): ${offScale.map((entry, i) => `${GROUPS[i].name} ${entry}`).join(', ')}.`
);
if (skipped) {
  console.log(`${skipped} declaration(s) skipped as non-CSS (template text, JS options).`);
}
