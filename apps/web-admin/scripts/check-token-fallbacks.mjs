#!/usr/bin/env node
/**
 * No `var(--token, <fallback>)`. A fallback is a second value for the same decision.
 *
 * `bento.css` and fifteen component stylesheets used to carry 119 of them, holding the
 * cream/blue palette this app retired. Every one of those tokens is declared in
 * `styles.css`, so the fallback never painted — which is exactly why it was dangerous:
 * nothing on screen would have told you the app was one renamed token away from
 * repainting itself in a theme nobody had reviewed. Some of the pairs also disagreed
 * loudly: `var(--kb-color-primary, #0066CC)` and `var(--kb-color-primary, #4F46E5)` in
 * the same tree, neither of them the indigo the token actually resolves to.
 *
 * The rule is now "declare the token, or don't use it". If a token is genuinely optional,
 * declare it: that is one place to look, and a missing one is a visible bug rather than a
 * silent repaint in the wrong palette.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const EXTENSIONS = ['.css', '.ts'];

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

/** Blanks comments in place, preserving offsets, so prose about a fallback is not one. */
function blankComments(text) {
  const blank = (chunk) => chunk.replace(/[^\n]/g, ' ');
  return text
    .replace(/\/\*[\s\S]*?\*\//g, blank)
    .replace(/^[ \t]*\/\/[^\n]*/gm, blank);
}

/** Paren-aware: `var(--x, rgba(0,0,0,.5))` has a comma at depth 1, not depth 3. */
function fallbacks(text) {
  const found = [];
  for (let i = 0; i < text.length; i++) {
    if (!text.startsWith('var(', i)) continue;
    let depth = 0;
    let end = i;
    for (; end < text.length; end++) {
      const c = text[end];
      if (c === '(') depth += 1;
      else if (c === ')') {
        depth -= 1;
        if (depth === 0) break;
      }
    }
    const body = text.slice(i + 4, end);
    const comma = body.indexOf(',');
    if (comma > 0) {
      found.push({
        index: i,
        token: body.slice(0, comma).trim(),
        fallback: body.slice(comma + 1).trim(),
      });
    }
    i = end;
  }
  return found;
}

const problems = [];
let checked = 0;

for (const path of walk(SRC)) {
  const text = blankComments(readFileSync(path, 'utf8'));
  for (const entry of fallbacks(text)) {
    checked += 1;
    const line = text.slice(0, entry.index).split('\n').length;
    problems.push(
      `${relative(ROOT, path)}:${line}  var(${entry.token}, ${entry.fallback})  -> ` +
        `declare ${entry.token} and use var(${entry.token})`
    );
  }
}

if (problems.length) {
  console.error(`Token fallback check failed: ${problems.length} fallback(s) in ${checked} var() call(s)\n`);
  for (const problem of problems) console.error(`  ${problem}`);
  console.error(
    '\nA fallback is a second palette waiting for a typo to wake it up. ' +
      'Add the token to :root in styles.css instead.'
  );
  process.exit(1);
}

console.log('Token fallback check passed: no var() fallbacks in src.');
