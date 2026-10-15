import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';

import { DailyClosingPageComponent } from './daily-closing-page.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { formatLocalDate } from '../../shared/date-range-selector.component';
import { DATE_RANGE_STORAGE_KEY, readStoredDateRange, storeDateRange } from '../../core/utils/date-range-storage.util';

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return formatLocalDate(date);
}

/**
 * Daily Closing counts one business day, so it reads the end of the shared period —
 * the day the user was last looking at on Reports or Orders. Opening on Today instead
 * meant the closing figures described a different date than the page they came from.
 */
describe('DailyClosingPageComponent — the day it opens on', () => {
  let fixture: ComponentFixture<DailyClosingPageComponent>;
  let api: jasmine.SpyObj<BusinessApiService>;

  beforeEach(() => {
    sessionStorage.removeItem(DATE_RANGE_STORAGE_KEY);
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getDailyClosing',
      'getOrdersPaginated',
    ]);
    api.getDailyClosing.and.callFake((date: string) => of({ date, totalOrders: 0 }));
    api.getOrdersPaginated.and.returnValue(of({ content: [], totalElements: 0, totalPages: 1, page: 0, size: 15 }));

    TestBed.configureTestingModule({
      imports: [DailyClosingPageComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        // The page reads the signed-in restaurant for its cash-count storage key. Real
        // AuthService injects HttpClient, so without this stint the whole fixture fails
        // to construct with NullInjectorError.
        { provide: AuthService, useValue: { session: () => null } },
      ],
    });
  });

  // The stored range is shared app state, so a spec must not leave one behind for
  // whichever spec file Karma runs next.
  afterEach(() => sessionStorage.removeItem(DATE_RANGE_STORAGE_KEY));

  function mount(): void {
    fixture = TestBed.createComponent(DailyClosingPageComponent);
    fixture.detectChanges();
  }

  /**
   * Closing is a one-day view, so its control is the single-date picker: exactly one
   * input, seeded with the day on screen and bounded so a future day cannot be picked.
   * The pills this spec used to drive are gone (commit 1e94bdc5 "date picker").
   */
  function dateInputs(): HTMLInputElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.date-range__input'));
  }

  function inputValue(): string {
    return dateInputs()[0]?.value ?? '';
  }

  function pick(day: string): void {
    const input = dateInputs()[0];
    input.value = day;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('opens on the day the shared period ends', async () => {
    storeDateRange({ from: daysAgo(5), to: daysAgo(3) });

    mount();
    // NgModel writes the model into the DOM one microtask after the view updates.
    await fixture.whenStable();

    expect(fixture.componentInstance.selectedDate()).toBe(daysAgo(3));
    expect(api.getDailyClosing).toHaveBeenCalledWith(daysAgo(3));
    // One day, one control: the input names the day the page is closing, and cannot be
    // moved to the future.
    expect(dateInputs().length).toBe(1);
    expect(inputValue()).toBe(daysAgo(3));
    expect(dateInputs()[0].getAttribute('max')).toBe(daysAgo(0));
  });

  it('opens on Today when nothing has been picked yet', async () => {
    mount();
    await fixture.whenStable();

    expect(fixture.componentInstance.selectedDate()).toBe(daysAgo(0));
    expect(inputValue()).toBe(daysAgo(0));
  });

  it('publishes the chosen closing day so the other views open on it', async () => {
    mount();
    await fixture.whenStable();

    pick(daysAgo(1));

    expect(fixture.componentInstance.selectedDate()).toBe(daysAgo(1));
    expect(api.getDailyClosing).toHaveBeenCalledWith(daysAgo(1));
    expect(readStoredDateRange()).toEqual({ from: daysAgo(1), to: daysAgo(1) });
  });

  it('falls back to Today when the stored period ends in the future', () => {
    storeDateRange({ from: daysAgo(-1), to: daysAgo(-1) });

    mount();


    expect(fixture.componentInstance.selectedDate()).toBe(daysAgo(0));
  });
});
