#!/usr/bin/env node
/**
 * One z ladder.
 *
 * The shell used to pick its own numbers: the sidebar backdrop was 99, the
 * sidebar 100, the sticky topbar 101, the skip link 110 and the rail peek 130.
 * Nothing said which of those was a layer and which was a number somebody typed.
 * Three of the four declared `--kb-z-*` tokens were dead, and the two modal
 * systems sat at 100 and 1000 while the two toasts sat at 1100 and 9999. The
 * mobile drawer (100) also painted under the topbar (101), so the topbar's
 * surface covered the drawer's own close button.
 *
 * The ladder is declared once, in styles.css, low to high. The rule has four
 * parts:
 *
 *   1. every `z-index` is `var(--kb-z-*)` — a component picks a rung, never a
 *      number, and never a fallback;
 *   2. rungs are declared in ascending order and no two share a value (two names
 *      on one rung is exactly how `.sidebar` and `.tactile-modal-overlay` both
 *      ended up at 100);
 *   3. every rung is used at least once, so the ladder cannot grow dead steps;
 *   4. the auth card's 0/1/2 art stack is local stacking *inside* a component,
 *      not a page layer, and is the single budgeted exception.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const STYLES = join(SRC, 'styles.css');
const EXTENSIONS = ['.css', '.ts', '.html'];

/** Local stacking inside the auth card: art (0) < glow/sections (1) < copy (2). */
const LOCAL_STACK = 'src/app/pages/login/login-page.component.ts';
const LOCAL_VALUES = new Set(['0', '1', '2']);

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

const problems = [];

// ── 1. Read the ladder in declaration order ─────────────────────────────────
const stylesText = blankComments(readFileSync(STYLES, 'utf8'));
const ladder = [];
for (const match of stylesText.matchAll(/(--kb-z-[\w-]+)\s*:\s*(-?\d+)\s*(?:!important)?\s*;/g)) {
  const line = stylesText.slice(0, match.index).split('\n').length;
  ladder.push({ token: match[1], value: Number(match[2]), where: `src/styles.css:${line}` });
}

if (!ladder.length) {
  problems.push('src/styles.css  ->  no --kb-z-* rungs are declared; every z-index would fail to resolve');
}

const seen = new Map();
ladder.forEach((rung, index) => {
  if (seen.has(rung.token)) {
    problems.push(`${rung.where}  ${rung.token}  ->  declared twice`);
  }
  seen.set(rung.token, rung);
  const previous = ladder[index - 1];
  if (previous && rung.value <= previous.value) {
    problems.push(
      `${rung.where}  ${rung.token}: ${rung.value}  ->  must be greater than ` +
        `${previous.token}: ${previous.value}; the ladder is declared low to high`
    );
  }
  if (rung.value < 0) {
    problems.push(`${rung.where}  ${rung.token}: ${rung.value}  ->  a rung cannot be negative`);
  }
});

// ── 2. Every z-index names a rung ───────────────────────────────────────────
const DECLARATION = /(?<![\w-])z-index\s*:\s*([^;{}\n]+)/g;
const uses = new Map();
const localUses = [];

for (const path of walk(SRC)) {
  const text = blankComments(readFileSync(path, 'utf8'));
  const relativePath = relative(ROOT, path).replaceAll('\\', '/');
  for (const match of text.matchAll(DECLARATION)) {
    const line = text.slice(0, match.index).split('\n').length;
    const where = `${relativePath}:${line}`;
    const value = match[1].replace(/\s*!important\s*$/, '').trim();

    if (value === 'auto') continue;

    const rung = value.match(/^var\((--kb-z-[\w-]+)\)$/);
    if (rung) {
      uses.set(rung[1], (uses.get(rung[1]) || 0) + 1);
      if (!seen.has(rung[1])) {
        problems.push(`${where}  z-index: ${value}  ->  ${rung[1]} is not declared on the ladder`);
      }
      continue;
    }

    if (/^var\(/.test(value)) {
      problems.push(
        `${where}  z-index: ${value}  ->  use the bare rung, never a fallback or an expression`
      );
      continue;
    }

    if (relativePath === LOCAL_STACK && LOCAL_VALUES.has(value)) {
      localUses.push(where);
      continue;
    }

    problems.push(
      `${where}  z-index: ${value}  ->  pick a rung from the ladder in src/styles.css ` +
        `(${ladder.map((entry) => entry.token.replace('--kb-z-', '')).join(', ')})`
    );
  }
}

// ── 3. No dead rungs ────────────────────────────────────────────────────────
for (const rung of ladder) {
  if (!uses.has(rung.token)) {
    problems.push(`${rung.where}  ${rung.token}  ->  declared but never used; a dead rung is drift`);
  }
}

if (problems.length) {
  console.error(`Z-index check failed: ${problems.length} problem(s)\n`);
  for (const problem of problems) console.error(`  ${problem}`);
  console.error('\nOne ladder, low to high, in src/styles.css. Pick the rung; do not pick the number.');
  process.exit(1);
}

const rungUses = [...uses.values()].reduce((a, b) => a + b, 0);
const total = rungUses + localUses.length;
console.log(
  `Z-index check passed: ${total} declaration(s) - ${rungUses} on the ladder in ${ladder.length} rung(s), ` +
    `${localUses.length} local (auth card art stack).`
);
console.log(
  `Ladder: ${ladder.map((rung) => `${rung.token.replace('--kb-z-', '')} ${rung.value}`).join(' < ')}.`
);
