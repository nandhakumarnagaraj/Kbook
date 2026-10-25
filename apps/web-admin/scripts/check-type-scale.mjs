#!/usr/bin/env node
/**
 * One type scale: nine steps, and nothing in between.
 *
 * Before this check the app painted 65 distinct font sizes, fifteen of them inside a
 * single visual tier (0.68 → 0.88rem, i.e. 10.9 → 14.1px). Two of those, 0.8125rem and
 * 0.82rem, were the same 13px written two ways, and 0.85rem appeared 38 times as the
 * de-facto body size. styles.css now declares nine steps (`--kb-text-2xs` … `--kb-text-4xl`,
 * each a size the app already rendered) and stylesheets and inline templates consume them.
 *
 * A spec can only measure the components it mounts, so this walks every stylesheet and
 * every component template instead, resolves the tokens, and fails on:
 *
 *   1. a literal that is already one of the nine steps (e.g. `font-size: 12px`) — the
 *      step exists, so writing the number is drift by construction;
 *   2. an off-scale literal that the budget below does not name;
 *   3. a budgeted size used more often than its budget.
 *
 * The budget is a ratchet, not an approval. It records the sizes that were deliberately
 * left alone because snapping them is a design decision — values, icons, body copy and
 * controls, plus a few sizes outside the 15–19px band — together with the exact count in
 * the tree today. Shrinking a number is the work; growing one is a regression. Fluid
 * values (`clamp()` / `calc()`) are exempt because a range cannot be a single step, and
 * `font-size: 0` is a reset.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const EXTENSIONS = ['.css', '.ts'];

/**
 * Sizes that are not on the scale yet, and how many declarations each owns today.
 *
 * When you snap one of these to a step, lower or delete its entry — the check will tell
 * you if the count drifted. Never raise one to make a build pass.
 */
const BUDGET = {
  '0.95rem': 5,
  '1.05rem': 3,
  '1.1rem': 3,
  '0.92rem': 3,
  '1.15rem': 2,
  '15px': 3,
  '0.65rem': 3,
  '1.2rem': 3,
  '0.6rem': 1,
  '1.3rem': 1,
  '1.35rem': 1,
  '1.4rem': 1,
  '1.45rem': 1,
  '1.55rem': 1,
  '1.8rem': 1,
  '1.85rem': 1,
  '17px': 1,
  '19px': 1,
  '0.66rem': 1,
};

const ROOT_FONT_PX = 16;

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

/** Blanks comments in place so prose such as "0.85rem → 14px" is never read as code. */
function blankComments(text) {
  const blank = (chunk) => chunk.replace(/[^\n]/g, ' ');
  return text
    .replace(/\/\*[\s\S]*?\*\//g, blank)
    .replace(/^[ \t]*\/\/[^\n]*/gm, blank);
}

function lineOf(text, index) {
  return text.slice(0, index).split('\n').length;
}

/** `0.8125rem` -> 13, `15px` -> 15, `1.5` -> 1.5×root. */
function toPx(numeric, unit) {
  const n = parseFloat(numeric);
  return unit.toLowerCase() === 'px' ? n : n * ROOT_FONT_PX;
}

const files = walk(SRC).map((path) => ({ path, text: blankComments(readFileSync(path, 'utf8')) }));

/** The scale, straight from styles.css — a token cannot smuggle a size past the check. */
const steps = new Map();
for (const { text } of files) {
  for (const [, name, numeric, unit] of text.matchAll(
    /(--kb-text-[a-z0-9-]+)\s*:\s*(\d*\.?\d+)(rem|px)\b/gi
  )) {
    steps.set(name, toPx(numeric, unit));
  }
}

if (steps.size === 0) {
  console.error('Type-scale check failed: no --kb-text-* steps found in src/styles.css.');
  process.exit(1);
}

const stepByPx = new Map([...steps].map(([name, px]) => [px, name]));

function nearestStep(px) {
  let best = null;
  for (const [name, stepPx] of steps) {
    const distance = Math.abs(px - stepPx);
    if (!best || distance < best.distance) best = { name, stepPx, distance };
  }
  return best;
}

const problems = [];
const used = new Map();
let tokenised = 0;
let fluid = 0;
let total = 0;

for (const { path, text } of files) {
  for (const declaration of text.matchAll(/font-size\s*:\s*([^;{}\n"']+)/g)) {
    const value = declaration[1].trim().replace(/\s*!important$/, '');
    total += 1;
    const where = `${relative(ROOT, path)}:${lineOf(text, declaration.index)}`;

    if (/^var\(--kb-text-[a-z0-9-]+\)$/i.test(value)) {
      tokenised += 1;
      continue;
    }
    if (/\b(?:clamp|calc|min|max)\(/i.test(value)) {
      fluid += 1;
      continue;
    }
    if (value === '0') continue;

    const literal = value.match(/^(\d*\.?\d+)(rem|px)$/i);
    if (!literal) {
      problems.push(`${where}  font-size: ${value}  -> not a step and not a fluid range`);
      continue;
    }

    const px = toPx(literal[1], literal[2]);
    used.set(value, (used.get(value) || 0) + 1);

    const stepName = stepByPx.get(px);
    if (stepName) {
      problems.push(
        `${where}  font-size: ${value}  -> that is exactly ${stepName} (${px}px); write var(${stepName})`
      );
      continue;
    }

    const budget = BUDGET[value];
    if (budget === undefined) {
      const nearest = nearestStep(px);
      problems.push(
        `${where}  font-size: ${value} (${px}px)  -> off-scale and not budgeted; ` +
          `nearest step is ${nearest.name} (${nearest.stepPx}px, ${nearest.distance.toFixed(2)}px away)`
      );
    }
  }
}

/**
 * The `font:` shorthand can carry a size the `font-size` walk never sees. There is one
 * such declaration in the tree today; it is longhand now, and this rule keeps the
 * shorthand from becoming a side door around the scale.
 */
for (const { path, text } of files) {
  for (const declaration of text.matchAll(/(?<![\w-])font\s*:\s*([^;{}\n"']+)/g)) {
    const value = declaration[1].trim();
    if (/^inherit$/i.test(value)) continue;
    if (!/\d*\.?\d+(?:rem|px|em|pt|%)\b/i.test(value)) continue;
    problems.push(
      `${relative(ROOT, path)}:${lineOf(text, declaration.index)}  font: ${value}  -> ` +
        'the shorthand hides a size from the scale; write font-weight / font-size / line-height / font-family'
    );
  }
}

for (const [value, allowed] of Object.entries(BUDGET)) {
  const count = used.get(value) || 0;
  if (count > allowed) {
    problems.push(
      `font-size: ${value}  -> used ${count}× but the budget allows ${allowed}×; ` +
        'a new off-scale size is a regression, not a budget increase'
    );
  }
}

const offScale = Object.entries(BUDGET)
  .map(([value, allowed]) => ({ value, count: used.get(value) || 0, allowed }))
  .filter((entry) => entry.count > 0);

if (problems.length) {
  console.error(`Type-scale check failed: ${problems.length} problem(s)\n`);
  for (const problem of problems) console.error(`  ${problem}`);
  console.error(
    '\nThe nine steps are --kb-text-2xs (11px), xs (12), sm (13), md (14), lg (16), ' +
      'xl (20), 2xl (24), 3xl (28), 4xl (32).\n' +
      'Pick the nearest step, or add a deliberate one and update this check with it.'
  );
  process.exit(1);
}

console.log(
  `Type scale check passed: ${tokenised}/${total} font-size declaration(s) use one of ` +
    `${steps.size} steps (${fluid} fluid range(s) exempt).`
);

if (offScale.length) {
  const remaining = offScale.reduce((sum, entry) => sum + entry.count, 0);
  console.log(`\nStill off-scale: ${remaining} declaration(s) in ${offScale.length} size(s).`);
  for (const entry of offScale.sort((a, b) => b.count - a.count)) {
    console.log(`  ${String(entry.count).padStart(3)}×  ${entry.value}`);
  }
}
