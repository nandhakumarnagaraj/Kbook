import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';
import { Subject, of } from 'rxjs';

import { OrderHistoryTabComponent, orderStatusLabel } from './order-history-tab.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder, OrderDetailResponse, PaginatedOrdersResponse } from '../../core/models/api.models';
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
 * just picked on Reports or Daily Closing, and the control could keep showing a day the
 * table was no longer on.
 *
 * The date control itself lives in the Orders page header now (the tab renders no
 * picker), so what is pinned here is the period the tab reports and requests — the value
 * that header control is seeded from. The picker's own rendering and emits are covered
 * by date-range-selector.component.spec.ts.
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

  // The stored range is shared app state, so a spec must not leave one behind for
  // whichever spec file Karma runs next.
  afterEach(() => sessionStorage.removeItem(DATE_RANGE_STORAGE_KEY));

  function mount(): void {
    fixture = TestBed.createComponent(OrderHistoryTabComponent);
    fixture.detectChanges();
  }

  /** The period the header control is seeded from. */
  function shownPeriod(): { from: string; to: string } {
    return fixture.componentInstance.currentDateRange;
  }

  function requestedPeriod(): string[] {
    return api.getOrdersPaginated.calls.mostRecent().args.slice(3, 5) as string[];
  }

  it('opens on the period the other business views are on', () => {
    storeDateRange({ from: daysAgo(5), to: daysAgo(3) });

    mount();

    expect(requestedPeriod()).toEqual([daysAgo(5), daysAgo(3)]);
    // The control has to agree with the table; disagreeing is the reported bug.
    expect(shownPeriod()).toEqual({ from: daysAgo(5), to: daysAgo(3) });
  });

  it('opens on Today when nothing has been picked yet', () => {
    mount();

    expect(requestedPeriod()).toEqual([daysAgo(0), daysAgo(0)]);
    expect(shownPeriod()).toEqual({ from: daysAgo(0), to: daysAgo(0) });
  });

  it('publishes the picked day so the next page opens on it', () => {
    mount();

    // What the page header's (rangeChanged) handler calls.
    fixture.componentInstance.onDateRangeChange({ from: daysAgo(1), to: daysAgo(1) });
    fixture.detectChanges();

    expect(readStoredDateRange()).toEqual({ from: daysAgo(1), to: daysAgo(1) });
    expect(requestedPeriod()).toEqual([daysAgo(1), daysAgo(1)]);
    expect(shownPeriod()).toEqual({ from: daysAgo(1), to: daysAgo(1) });
  });

  it('moves the control with Clear Filters instead of leaving it on the old day', () => {
    storeDateRange({ from: daysAgo(5), to: daysAgo(3) });
    mount();
    expect(shownPeriod()).toEqual({ from: daysAgo(5), to: daysAgo(3) });

    fixture.componentInstance.clearOrderFilters();
    fixture.detectChanges();

    expect(requestedPeriod()).toEqual([daysAgo(0), daysAgo(0)]);
    expect(shownPeriod()).toEqual({ from: daysAgo(0), to: daysAgo(0) });
    expect(readStoredDateRange()).toEqual({ from: daysAgo(0), to: daysAgo(0) });
  });
});

/**
 * The Status column prints this, and the API sends the stored value title-cased ("Draft") —
 * accounting jargon no counter uses. The open-bill state is the one the POS labels
 * "Preparing", so the two surfaces must say the same word for the same row.
 */
describe('OrderHistoryTabComponent — the status column wording', () => {
  it('calls the open-bill state Preparing, whatever case the API sends', () => {
    expect(orderStatusLabel('Draft')).toBe('Preparing');
    expect(orderStatusLabel('draft')).toBe('Preparing');
    expect(orderStatusLabel('DRAFT')).toBe('Preparing');
    // A row already carrying the counter's word must not come back as "Preparing" twice
    // or fall through to the raw value.
    expect(orderStatusLabel('Preparing')).toBe('Preparing');
  });

  it('leaves the terminal states — and the legacy paid alias — alone', () => {
    expect(orderStatusLabel('Completed')).toBe('Completed');
    expect(orderStatusLabel('Cancelled')).toBe('Cancelled');
    expect(orderStatusLabel('Paid')).toBe('Paid');
  });

  it('never renders an empty chip', () => {
    expect(orderStatusLabel(null)).toBe('');
    expect(orderStatusLabel(undefined)).toBe('');
    expect(orderStatusLabel('  ')).toBe('');
  });
});

/**
 * A sighted user reads a grid of cells; a screen reader needs the table's name,
 * each column's label and each row's own name spelled out. Without `scope`, a
 * cell is announced as "₹640" with no indication of which column or order it
 * belongs to.
 */
describe('OrderHistoryTabComponent — table semantics', () => {
  let fixture: ComponentFixture<OrderHistoryTabComponent>;

  beforeEach(() => {
    sessionStorage.removeItem(DATE_RANGE_STORAGE_KEY);
    const api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getOrdersPaginated',
      'voidBill',
      'getOrderDetail',
    ]);
    api.getOrdersPaginated.and.returnValue(of({
      content: [{
        sourceType: 'POS',
        orderType: 'DINE_IN',
        orderId: 41,
        orderCode: '1041',
        invoiceNumber: 'INV-1041',
        publicToken: null,
        customerName: 'Table 4',
        customerContact: null,
        tableNumber: 4,
        orderStatus: 'Completed',
        paymentStatus: 'Paid',
        paymentMethod: 'Cash',
        totalAmount: 640,
        gatewayPaidAmount: null,
        refundAmount: 0,
        refundStatus: 'NONE',
        refundMode: null,
        cancelReason: null,
        manualRefundAllowed: false,
        gatewayRefundAllowed: false,
        createdAt: Date.now(),
      }],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 15,
    } as PaginatedOrdersResponse));

    TestBed.configureTestingModule({
      imports: [OrderHistoryTabComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: { session: () => null } },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show']) },
      ],
    });

    fixture = TestBed.createComponent(OrderHistoryTabComponent);
    fixture.detectChanges();
  });

  afterEach(() => sessionStorage.removeItem(DATE_RANGE_STORAGE_KEY));

  it('names the table and scopes every header to its column', () => {
    const table: HTMLTableElement = fixture.nativeElement.querySelector('table.data-table');
    expect(table).toBeTruthy();

    const caption = table.querySelector('caption')!;
    expect(caption).withContext('a table needs an accessible name').toBeTruthy();
    expect(caption.textContent).toContain('Orders');

    const headers = Array.from(table.querySelectorAll('thead th'));
    expect(headers.length).toBe(7);
    for (const header of headers) {
      expect(header.getAttribute('scope'))
        .withContext(`"${(header.textContent ?? '').trim()}" must be scoped to its column`)
        .toBe('col');
    }
  });

  it('makes the order number the header of its own row', () => {
    const rowHeader: HTMLElement = fixture.nativeElement.querySelector('table.data-table tbody th[scope="row"]');
    expect(rowHeader).withContext('each row needs its own identifier').toBeTruthy();
    expect(rowHeader.textContent).toContain('1041');
    // The row header still reads as data, not as an uppercase column label.
    expect(getComputedStyle(rowHeader).textTransform).not.toBe('uppercase');
  });
});

/**
 * "View" used to sit on the spinner long after the API had answered. The response
 * handler wrote `selectedOrderDetail` and `orderDetailLoading` — plain fields on an
 * OnPush view whose only dirtying event, the click, was already over — so the modal
 * waited for some later interaction to be painted. These specs mount the tab under a
 * host, the way the Orders page does, and re-check with the host: that is the same
 * traversal zone.js runs after an XHR, which skips a clean OnPush view. Forcing the
 * tab's own view with its own detectChanges() would hide the bug.
 */
@Component({
  standalone: true,
  imports: [OrderHistoryTabComponent],
  template: `<app-order-history-tab></app-order-history-tab>`,
})
class OrderHistoryHostComponent {}

describe('OrderHistoryTabComponent — the order detail modal paints on the response', () => {
  let fixture: ComponentFixture<OrderHistoryHostComponent>;
  let detail$: Subject<OrderDetailResponse>;

  const order: BusinessOrder = {
    sourceType: 'POS',
    orderType: 'DINE_IN',
    orderId: 41,
    orderCode: '1041',
    invoiceNumber: 'INV-1041',
    publicToken: null,
    customerName: 'Table 4',
    customerContact: null,
    tableNumber: 4,
    orderStatus: 'Completed',
    paymentStatus: 'Paid',
    paymentMethod: 'Cash',
    totalAmount: 640,
    gatewayPaidAmount: null,
    refundAmount: 0,
    refundStatus: 'NONE',
    refundMode: null,
    cancelReason: null,
    manualRefundAllowed: false,
    gatewayRefundAllowed: false,
    createdAt: Date.now(),
  };

  beforeEach(() => {
    sessionStorage.removeItem(DATE_RANGE_STORAGE_KEY);
    detail$ = new Subject<OrderDetailResponse>();
    const api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getOrdersPaginated',
      'voidBill',
      'getOrderDetail',
    ]);
    api.getOrdersPaginated.and.returnValue(of({
      content: [order], totalElements: 1, totalPages: 1, page: 0, size: 15,
    } as PaginatedOrdersResponse));
    api.getOrderDetail.and.returnValue(detail$.asObservable());

    TestBed.configureTestingModule({
      imports: [OrderHistoryHostComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: { session: () => null } },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show']) },
      ],
    });

    fixture = TestBed.createComponent(OrderHistoryHostComponent);
    fixture.detectChanges();
  });

  afterEach(() => sessionStorage.removeItem(DATE_RANGE_STORAGE_KEY));

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function modal(): Element | null {
    return host().querySelector('app-order-detail-modal');
  }

  it('shows the modal in the pass the response lands in', () => {
    (host().querySelector('button[aria-label="View order details"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(host().querySelector('.panel.loading'))
      .withContext('the click itself must paint the loading state')
      .toBeTruthy();

    detail$.next({ order, lineItems: [] });
    fixture.detectChanges();

    expect(modal()).withContext('the answer was already on the wire — paint it now').toBeTruthy();
    expect(host().querySelector('.panel.loading'))
      .withContext('the loading panel must give way to the modal')
      .toBeFalsy();
    fixture.destroy();
  });

  it('takes the modal away as soon as it is closed', () => {
    (host().querySelector('button[aria-label="View order details"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    detail$.next({ order, lineItems: [] });
    fixture.detectChanges();
    expect(modal()).withContext('the modal has to be open before it can be closed').toBeTruthy();

    (host().querySelector('.order-modal__close') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(modal()).withContext('a closed modal must not stay painted').toBeFalsy();
    fixture.destroy();
  });
});
