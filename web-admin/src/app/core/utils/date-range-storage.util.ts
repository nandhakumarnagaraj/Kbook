/**
 * The one period the business-day views are looking at.
 *
 * Reports, Daily Closing and Orders history all answer the same question — "which
 * business day am I on?" — but each used to keep its own private answer: Reports
 * persisted a range in sessionStorage, Orders history reset itself to Today every
 * time its tab was mounted, and Daily Closing always started at Today. Picking a
 * custom day on one page therefore landed you on a different day on the next, which
 * is what made the same Custom pill behave differently depending on the page.
 *
 * They now share this single stored range. Daily Closing is a one-day view, so it
 * reads the *end* of the stored period (the most recent day you were looking at) and
 * writes its closing day back as a one-day period.
 */
export const DATE_RANGE_STORAGE_KEY = 'business-date-range';

export interface StoredDateRange {
  from: string;
  to: string;
}

function isIsoDate(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
}

export function readStoredDateRange(): StoredDateRange | null {
  try {
    const raw = sessionStorage.getItem(DATE_RANGE_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { from?: unknown; to?: unknown };
    if (isIsoDate(parsed.from) && isIsoDate(parsed.to)) {
      return { from: parsed.from, to: parsed.to };
    }
  } catch {
    // Unreadable or blocked storage is the same as nothing stored.
  }
  return null;
}

export function storeDateRange(range: StoredDateRange): void {
  try {
    sessionStorage.setItem(DATE_RANGE_STORAGE_KEY, JSON.stringify(range));
  } catch {
    // A full or blocked sessionStorage must not break the page.
  }
}
