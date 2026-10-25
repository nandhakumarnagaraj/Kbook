#!/usr/bin/env node
/**
 * One ladder of widths, and a ratchet on the rest.
 *
 * Fourteen widths were answering "is this narrow?" in this app: 480, 520, 560, 640, 680,
 * 720, 768, 860, 900, 960, 1024/1025, 1080 and 1100. At 600px wide, one page stacked into
 * cards while another kept a seven-column table, because each page had picked its own
 * threshold. 767 and 768 also existed side by side — a one-pixel disagreement that meant at
 * exactly 768 a table stayed a table while the grid above it had already collapsed.
 *
 * The ladder is 480 / 640 / 768 / 1024 / 1100:
 *
 *   480   a phone in portrait, the widths where a control has to be full-bleed
 *   640   phone landscape, where a two-up grid becomes one column
 *   768   tablet, where a table gives way to cards
 *   1024  laptop; below it the nav rail becomes a drawer (paired with min-width: 1025)
 *   1100  the widest collapse, for the dashboards' four-up KPI rows
 *
 * The remaining widths are drift. They are budgeted, not removed: every one of them is a
 * "collapse to one column" rule, and moving a threshold changes composition at widths no
 * one has looked at. Moving a max-width *up* is overflow-safe (the element gets more room
 * than it was designed for) but still restyles a laptop; moving one *down* can overflow.
 * That is a rendered decision, so this check holds the line instead of guessing.
 *
 * Rules:
 *   - no `max-width: 767`; the tablet step is 768
 *   - the laptop split is `max-width: 1024` against `min-width: 1025`, never 1024/1024
 *   - a width that is not on the ladder must be in BUDGET, and no higher than budgeted
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const EXTENSIONS = ['.css', '.ts'];

/** The ladder. `1025` is the complement of 1024, not a sixth step. */
const LADDER = new Set([480, 640, 768, 1024, 1100]);

/** Drift that is already shipped, per width, in queries. Reduce these, never grow them. */
const BUDGET = {};

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

const queries = [];
for (const path of walk(SRC)) {
  const text = blankComments(readFileSync(path, 'utf8'));
  for (const match of text.matchAll(/@media\s*\(([^)]*)\)/g)) {
    const width = match[1].match(/(min|max)-width\s*:\s*(\d+)px/);
    if (!width) continue;
    queries.push({
      path: relative(ROOT, path),
      line: text.slice(0, match.index).split('\n').length,
      kind: width[1],
      width: Number(width[2]),
    });
  }
}

const problems = [];
for (const query of queries) {
  const where = `${query.path}:${query.line}`;

  if (query.kind === 'max' && query.width === 767) {
    problems.push(`${where}  max-width: 767px  ->  the tablet step is 768`);
    continue;
  }

  if (query.width === 1025 && query.kind === 'min') continue;

  if (LADDER.has(query.width)) continue;

  if (query.width === 1025) {
    problems.push(
      `${where}  ${query.kind}-width: 1025px  ->  laptop split is max-width: 1024 / min-width: 1025`
    );
    continue;
  }

  const budgeted = BUDGET[query.width] ?? 0;
  if (!budgeted) {
    problems.push(
      `${where}  ${query.kind}-width: ${query.width}px  ->  not on the ladder and not budgeted; ` +
        'use 480 / 640 / 768 / 1024 / 1100'
    );
  }
}

const used = new Map();
for (const query of queries) {
  if (LADDER.has(query.width) || query.width === 1025) continue;
  used.set(query.width, (used.get(query.width) || 0) + 1);
}

for (const [width, allowed] of Object.entries(BUDGET)) {
  const count = used.get(Number(width)) || 0;
  if (count > allowed) {
    problems.push(
      `max-width: ${width}px  -> used ${count}× but the budget allows ${allowed}×; ` +
        'a new off-ladder breakpoint is drift, not a budget increase'
    );
  }
}

if (problems.length) {
  console.error(`Breakpoint check failed: ${problems.length} problem(s)\n`);
  for (const problem of problems) console.error(`  ${problem}`);
  console.error(
    '\nThe ladder is 480 / 640 / 768 / 1024 / 1100 (with min-width: 1025 as the laptop\n' +
      'complement of 1024). Pick the nearest step, or add a deliberate width to BUDGET\n' +
      'with the reason it cannot move.'
  );
  process.exit(1);
}

const onLadder = queries.filter(
  (q) => LADDER.has(q.width) || (q.width === 1025 && q.kind === 'min')
);
const drift = [...used].sort((a, b) => a[0] - b[0]);
console.log(
  `Breakpoint check passed: ${onLadder.length}/${queries.length} width quer${queries.length === 1 ? 'y' : 'ies'} ` +
    `on the ladder, ${queries.length - onLadder.length} budgeted in ${drift.length} off-ladder width(s).`
);
if (drift.length) {
  console.log('\nStill off-ladder:');
  for (const [width, count] of drift) console.log(`  ${String(count).padStart(3)}x  ${width}px`);
}
