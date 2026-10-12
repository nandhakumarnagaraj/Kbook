import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';

import { OrderHistoryTabComponent } from './order-history-tab.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatLocalDate } from '../../shared/date-range-selector.component';
import { DATE_RANGE_STORAGE_KEY, readStoredDateRange, storeDateRange } from '../../core/utils/date-range-storage.util';

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return formatLocalDate(date);
}

const emptyPage: PaginatedOrdersResponse = { content: [], totalElements: 0, totalPages: 1, page: 0, size: 15 };

/**
 * The tab is destroyed and rebuilt every time it is opened, so the period it starts on
 * is the period the user sees. It used to start on Today regardless of the day they had
 * just picked on Reports or Daily Closing, and the pills could keep showing a day the
 * table was no longer on.
 */
describe('OrderHistoryTabComponent — the period it opens on', () => {
  let fixture: ComponentFixture<OrderHistoryTabComponent>;
  let api: jasmine.SpyObj<BusinessApiService>;

  beforeEach(() => {
    sessionStorage.removeItem(DATE_RANGE_STORAGE_KEY);
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getOrdersPaginated',
      'voidBill',
      'getOrderDetail',
    ]);
    api.getOrdersPaginated.and.returnValue(of(emptyPage));

    TestBed.configureTestingModule({
      imports: [OrderHistoryTabComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: { session: () => null } },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show']) },
      ],
    });
  });

  function mount(): void {
    fixture = TestBed.createComponent(OrderHistoryTabComponent);
    fixture.detectChanges();
  }

  function pills(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.date-range__preset-btn'));
  }

  function label(): string {
    return fixture.nativeElement.querySelector('.date-range__label')?.textContent?.trim() ?? '';
  }

  function requestedPeriod(): string[] {
    return api.getOrdersPaginated.calls.mostRecent().args.slice(3, 5) as string[];
  }

  it('opens on the period the other business views are on', () => {
    storeDateRange({ from: daysAgo(5), to: daysAgo(3) });

    mount();

    expect(requestedPeriod()).toEqual([daysAgo(5), daysAgo(3)]);
    // The control has to agree with the table; disagreeing is the reported bug.
    expect(pills()[2].classList).toContain('date-range__preset-btn--active');
    expect(label()).toBe(`${daysAgo(5)} → ${daysAgo(3)}`);
  });

  it('opens on Today when nothing has been picked yet', () => {
    mount();

    expect(requestedPeriod()).toEqual([daysAgo(0), daysAgo(0)]);
    expect(pills()[0].classList).toContain('date-range__preset-btn--active');
  });

  it('publishes the picked day so the next page opens on it', () => {
    mount();

    pills()[1].click();
    fixture.detectChanges();

    expect(readStoredDateRange()).toEqual({ from: daysAgo(1), to: daysAgo(1) });
    expect(requestedPeriod()).toEqual([daysAgo(1), daysAgo(1)]);
  });

  it('moves the control with Clear Filters instead of leaving it on the old day', () => {
    storeDateRange({ from: daysAgo(5), to: daysAgo(3) });
    mount();
    expect(pills()[2].classList).toContain('date-range__preset-btn--active');

    fixture.componentInstance.clearOrderFilters();
    fixture.detectChanges();

    expect(requestedPeriod()).toEqual([daysAgo(0), daysAgo(0)]);
    expect(pills()[0].classList).toContain('date-range__preset-btn--active');
    expect(label()).toBe(daysAgo(0));
    expect(readStoredDateRange()).toEqual({ from: daysAgo(0), to: daysAgo(0) });
  });
});
