import { formatCurrency, formatDate, formatAge } from './formatters';

describe('formatters', () => {
  describe('formatCurrency', () => {
    it('formats a number as INR currency', () => {
      // Uses non-breaking space / narrow no-break space between symbol and value
      // across Node ICU versions, so assert on the significant parts instead.
      const out = formatCurrency(1234.5);
      expect(out).toContain('₹');
      expect(out).toContain('1,234.5');
    });

    it('treats null/undefined as zero', () => {
      expect(formatCurrency(null)).toContain('0');
      expect(formatCurrency(undefined)).toContain('0');
    });

    it('caps at two fraction digits', () => {
      expect(formatCurrency(1.999)).toContain('2');
    });
  });

  describe('formatDate', () => {
    it('returns a dash for null/undefined/zero', () => {
      expect(formatDate(null)).toBe('-');
      expect(formatDate(undefined)).toBe('-');
      expect(formatDate(0)).toBe('-');
    });

    it('formats a valid epoch into a non-empty string', () => {
      const out = formatDate(Date.UTC(2026, 0, 15, 10, 30));
      expect(out).not.toBe('-');
      expect(out.length).toBeGreaterThan(0);
    });
  });

  describe('formatAge', () => {
    it('returns a dash for null/undefined/zero', () => {
      expect(formatAge(null)).toBe('-');
      expect(formatAge(undefined)).toBe('-');
      expect(formatAge(0)).toBe('-');
    });

    it('reports days for old timestamps', () => {
      expect(formatAge(Date.now() - 3 * 86400000)).toBe('3d ago');
    });

    it('reports hours when under a day', () => {
      expect(formatAge(Date.now() - 5 * 3600000)).toBe('5h ago');
    });

    it('reports minutes when under an hour', () => {
      expect(formatAge(Date.now() - 10 * 60000)).toBe('10m ago');
    });

    it('reports "just now" for very recent timestamps', () => {
      expect(formatAge(Date.now() - 5000)).toBe('just now');
    });
  });
});
