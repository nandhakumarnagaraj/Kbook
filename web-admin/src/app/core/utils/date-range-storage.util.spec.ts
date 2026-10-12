import { DATE_RANGE_STORAGE_KEY, readStoredDateRange, storeDateRange } from './date-range-storage.util';

describe('date-range-storage.util', () => {
  afterEach(() => sessionStorage.removeItem(DATE_RANGE_STORAGE_KEY));

  it('round-trips the range every business-day view shares', () => {
    storeDateRange({ from: '2026-10-01', to: '2026-10-13' });

    expect(readStoredDateRange()).toEqual({ from: '2026-10-01', to: '2026-10-13' });
  });

  it('reports nothing when no range has been stored', () => {
    expect(readStoredDateRange()).toBeNull();
  });

  it('refuses anything that is not two ISO dates, so a bad value cannot reach the API', () => {
    sessionStorage.setItem(DATE_RANGE_STORAGE_KEY, JSON.stringify({ from: 'yesterday', to: '2026-10-13' }));
    expect(readStoredDateRange()).toBeNull();

    sessionStorage.setItem(DATE_RANGE_STORAGE_KEY, JSON.stringify({ from: '2026-10-13' }));
    expect(readStoredDateRange()).toBeNull();

    sessionStorage.setItem(DATE_RANGE_STORAGE_KEY, 'not json');
    expect(readStoredDateRange()).toBeNull();
  });
});
