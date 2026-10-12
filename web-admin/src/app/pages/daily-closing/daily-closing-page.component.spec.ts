import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';

import { DailyClosingPageComponent } from './daily-closing-page.component';
import { BusinessApiService } from '../../core/services/business-api.service';
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
      providers: [{ provide: BusinessApiService, useValue: api }],
    });
  });

  function mount(): void {
    fixture = TestBed.createComponent(DailyClosingPageComponent);
    fixture.detectChanges();
  }

  function pills(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.date-range__preset-btn'));
  }

  function inputs(): HTMLInputElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.date-range__input'));
  }

  function label(): string {
    return fixture.nativeElement.querySelector('.date-range__label')?.textContent?.trim() ?? '';
  }

  it('opens on the day the shared period ends', () => {
    storeDateRange({ from: daysAgo(5), to: daysAgo(3) });

    mount();

    expect(fixture.componentInstance.selectedDate()).toBe(daysAgo(3));
    expect(api.getDailyClosing).toHaveBeenCalledWith(daysAgo(3));
    // One day, one control: a custom closing day opens exactly one input, labelled by day.
    expect(pills()[2].classList).toContain('date-range__preset-btn--active');
    expect(inputs().length).toBe(1);
    expect(label()).toBe(daysAgo(3));
  });

  it('opens on Today when nothing has been picked yet', () => {
    mount();

    expect(fixture.componentInstance.selectedDate()).toBe(daysAgo(0));
    expect(pills()[0].classList).toContain('date-range__preset-btn--active');
  });

  it('publishes the chosen closing day so the other views open on it', () => {
    mount();

    pills()[1].click();
    fixture.detectChanges();

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
