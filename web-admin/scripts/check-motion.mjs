#!/usr/bin/env node
/**
 * One motion vocabulary: an interactive transition lasts between 150ms and 300ms.
 *
 * That band is declared once in styles.css (`--kb-duration-fast: 150ms`,
 * `--kb-duration-base: 200ms`, `--kb-duration-slow: 300ms`) and asserted for the pages
 * micro-interactions.spec.ts happens to mount. A spec can only cover the components it
 * mounts, so this walks every stylesheet and every inline component template instead,
 * resolves the `--kb-duration-*` tokens, and fails on any transition outside the band.
 *
 * Animations are out of scope on purpose: a spinner, a skeleton shimmer and the animated
 * counter run for as long as the thing they describe is still happening (800ms, 560ms by
 * design), and the `prefers-reduced-motion` block collapses durations to 0.01ms to switch
 * motion off rather than to time it.
 *
 * Progress indicators are the same kind of exception as a transition: their fill is timed
 * by how long the job takes, not by the interaction, which is why the reduced-motion block
 * in styles.css deliberately keeps them moving. They are named here instead of being
 * tolerated by lowering the band.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const MIN_MS = 150;
const MAX_MS = 300;
const PROGRESS_INDICATORS = [/\.progress-bar\b/, /\.spinning\b/, /\.spinner\b/];
const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const EXTENSIONS = ['.css', '.ts'];

/** `--kb-duration-fast: 150ms` -> 150, so a token cannot smuggle a duration past the band. */
function toMs(amount, unit) {
  return unit.toLowerCase() === 's' ? parseFloat(amount) * 1000 : parseFloat(amount);
}

function walk(dir) {
  const found = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) found.push(...walk(full));
    else if (EXTENSIONS.some((ext) => entry.endsWith(ext))) found.push(full);
  }
  return found;
}

const files = walk(SRC).map((path) => ({ path, text: readFileSync(path, 'utf8') }));

const tokens = new Map();
for (const { text } of files) {
  for (const [, name, amount, unit] of text.matchAll(
    /(--[a-z0-9-]*duration[a-z0-9-]*)\s*:\s*(\d+(?:\.\d+)?)(ms|s)\b/gi
  )) {
    tokens.set(name, toMs(amount, unit));
  }
}

/** The ranges the check steps over: motion is switched off there, not timed. */
function reducedMotionRanges(text) {
  const ranges = [];
  const opener = /@media[^{]*prefers-reduced-motion[^{]*\{/g;
  let match;
  while ((match = opener.exec(text))) {
    let depth = 1;
    let index = match.index + match[0].length;
    while (index < text.length && depth > 0) {
      if (text[index] === '{') depth += 1;
      else if (text[index] === '}') depth -= 1;
      index += 1;
    }
    ranges.push([match.index, index]);
    opener.lastIndex = index;
  }
  return ranges;
}

function lineOf(text, index) {
  return text.slice(0, index).split('\n').length;
}

/** The selector that owns a declaration: its block starts after the previous closing brace. */
function enclosingSelector(text, index) {
  return text.slice(text.lastIndexOf('}', index) + 1, index);
}

const problems = [];
let checked = 0;

for (const { path, text } of files) {
  const skipped = reducedMotionRanges(text);
  const isSkipped = (index) => skipped.some(([start, end]) => index >= start && index < end);

  for (const declaration of text.matchAll(
    /(?<![\w-])(?:(?:-webkit-|-moz-|-ms-|-o-)?transition(?:-duration)?)\s*:\s*([^;{}]+)/g
  )) {
    if (isSkipped(declaration.index)) continue;
    if (PROGRESS_INDICATORS.some((pattern) => pattern.test(enclosingSelector(text, declaration.index)))) continue;
    const value = declaration[1];

    const durations = [];
    for (const [, amount, unit] of value.matchAll(/(?<![\w.])(\d+(?:\.\d+)?)(ms|s)\b/g)) {
      durations.push({ ms: toMs(amount, unit), raw: `${amount}${unit}` });
    }
    for (const [, name] of value.matchAll(/var\(\s*(--[a-z0-9-]+)/gi)) {
      // A token this script cannot resolve (an undeclared or non-duration variable) is
      // left alone rather than guessed at.
      if (tokens.has(name)) durations.push({ ms: tokens.get(name), raw: `var(${name})` });
    }

    for (const duration of durations) {
      checked += 1;
      if (duration.ms >= MIN_MS && duration.ms <= MAX_MS) continue;
      problems.push(
        `${relative(ROOT, path)}:${lineOf(text, declaration.index)}  ` +
          `transition: ${value.trim()}  ->  ${duration.raw} is outside ${MIN_MS}-${MAX_MS}ms`
      );
    }
  }
}

if (problems.length) {
  console.error(`Motion band check failed: ${problems.length} duration(s) outside ${MIN_MS}-${MAX_MS}ms\n`);
  for (const problem of problems) console.error(`  ${problem}`);
  console.error('\nUse a duration in the band, or a token: fast 150ms, base 200ms, slow 300ms.');
  process.exit(1);
}

console.log(
  `Motion band check passed: ${checked} transition duration(s) in ${files.length} file(s) ` +
    `sit within ${MIN_MS}-${MAX_MS}ms.`
);
