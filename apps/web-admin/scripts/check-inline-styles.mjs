#!/usr/bin/env node
/**
 * Inline styles are the one place a token cannot reach: an attribute value is
 * not a stylesheet, so it escapes the type, spacing, colour and breakpoint
 * checks, and it wins on specificity over the component that owns the surface.
 *
 * Batches 10–14 moved every audited attribute into its component stylesheet
 * (plus the five shared layout utilities in bento.css). This check is the
 * ratchet: a page may keep its existing attributes only up to the budget below,
 * and a converted page may never grow one back.
 *
 * Three rules:
 *   1. a file with no budget must have zero static `style="…"` attributes;
 *   2. a budgeted file may only shrink — using a budget is not an approval;
 *   3. the budget is kept exact: lower it when the count falls, and remove the
 *      entry when the file is gone or fully converted.
 *
 * `[style.prop]` bindings are not counted: they are a dynamic value, not a
 * hand-written declaration block.
 */
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const EXTENSIONS = ['.ts', '.html'];

/**
 * Current debt, measured after batch 14. Each number is the count of static
 * style attributes in that file today. Lower it when you convert the file;
 * never raise it. Empty means the audited inline-style debt is zero.
 */
const BUDGET = {};

/** Files that must stay at zero; the batch-10/11/12/13/14 proofs live here. */
const CONVERTED = [
  'app/pages/menu/menu-page.component.ts',
  'app/pages/staff/staff-permissions-modal.component.ts',
  'app/pages/inventory/inventory-page.component.ts',
  'app/pages/business-settings/payment-methods-section.component.ts',
  'app/pages/business-dashboard/business-dashboard-page.component.ts',
  'app/pages/businesses/businesses-page.component.ts',
  'app/pages/orders/live-orders-tab.component.ts',
  'app/pages/orders/order-history-tab.component.ts',
  'app/pages/business-settings/business-settings-page.component.ts',
  'app/pages/business-settings/password-change-section.component.ts',
  'app/pages/platform-dashboard/platform-dashboard-page.component.ts',
  'app/pages/login/login-page.component.ts',
  'app/pages/reports/reports-page.component.ts',
  'app/shared/order-detail-modal.component.ts',
];

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

/**
 * Count style attributes by walking tags, not by scanning the raw text: a
 * stylesheet comment or a sentence that mentions `style="…"` is not an
 * attribute. Quotes are tracked so `[disabled]="count > 0"` does not end a tag.
 */
function countInlineStyles(text) {
  const hits = [];
  let i = 0;
  while (i < text.length) {
    if (text.startsWith('<!--', i)) {
      const end = text.indexOf('-->', i + 4);
      i = end < 0 ? text.length : end + 3;
      continue;
    }
    if (text[i] !== '<' || !/[a-zA-Z/!]/.test(text[i + 1] || '')) {
      i += 1;
      continue;
    }
    let quote = null;
    let j = i + 1;
    for (; j < text.length; j += 1) {
      const ch = text[j];
      if (quote) {
        if (ch === quote) quote = null;
      } else if (ch === '"' || ch === "'") {
        quote = ch;
      } else if (ch === '>') {
        break;
      }
    }
    const tag = text.slice(i, j + 1);
    if (/\sstyle="/.test(tag)) {
      hits.push({ index: i, line: text.slice(0, i).split('\n').length });
    }
    i = j + 1;
  }
  return hits;
}

const problems = [];
const rows = [];
const counts = new Map();
let total = 0;

for (const path of walk(SRC)) {
  const key = relative(SRC, path).replaceAll('\\', '/');
  const hits = countInlineStyles(readFileSync(path, 'utf8'));
  counts.set(key, hits.length);
  if (!hits.length) continue;
  total += hits.length;
  const budget = BUDGET[key];
  if (budget === undefined) {
    problems.push(
      `${key}  -> ${hits.length} inline style(s), but the file has no budget ` +
        `(first at line ${hits[0].line}); move them into the component stylesheet`
    );
  } else if (hits.length > budget) {
    problems.push(
      `${key}  -> ${hits.length} inline style(s), budget ${budget}; ` +
        'a page may keep its debt, never grow it'
    );
  } else if (hits.length < budget) {
    problems.push(
      `${key}  -> ${hits.length} inline style(s), budget ${budget}; ` +
        'conversion done — lower the budget to the new count'
    );
  }
  rows.push({ key, count: hits.length, budget: budget ?? 0 });
}

for (const key of CONVERTED) {
  if (rows.some((row) => row.key === key)) {
    const row = rows.find((r) => r.key === key);
    problems.push(`${key}  -> ${row.count} inline style(s); this page was converted and must stay at 0`);
  }
}

// A stale budget (file gone, renamed, or fully converted) is a bookkeeping error.
for (const key of Object.keys(BUDGET)) {
  if (!existsSync(join(SRC, key))) {
    problems.push(`${key}  -> budget ${BUDGET[key]} has no matching file; remove or rename the entry`);
  } else if ((counts.get(key) ?? 0) === 0) {
    problems.push(
      `${key}  -> 0 inline style(s), budget ${BUDGET[key]}; ` +
        'remove the budget and add the file to CONVERTED'
    );
  }
}

if (problems.length) {
  console.error(`Inline-style check failed: ${problems.length} problem(s)\n`);
  for (const problem of problems) console.error(`  ${problem}`);
  console.error(
    '\nMove the declarations into the component stylesheet (or a layout utility in ' +
      'bento.css for arrangement only), then lower the budget.'
  );
  process.exit(1);
}

console.log(
  `Inline-style check passed: ${total} static style attribute(s) in ${rows.length} file(s), ` +
    `all within budget; ${CONVERTED.length} converted page(s) at 0.`
);
console.log(
  total === 0
    ? 'Remaining debt: none.'
    : 'Remaining debt: ' +
        rows
          .sort((a, b) => b.count - a.count)
          .map((row) => `${row.key} ${row.count}/${row.budget}`)
          .join(', ')
);
