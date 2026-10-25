import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, of } from 'rxjs';

import { InventoryPageComponent } from './inventory/inventory-page.component';
import { OrderHistoryTabComponent } from './orders/order-history-tab.component';
import { TerminalsPageComponent } from './terminals/terminals-page.component';
import { BusinessApiService } from '../core/services/business-api.service';
import { AuthService } from '../core/auth/auth.service';
import { ToastService } from '../core/services/toast.service';
import { BusinessOrder, PaginatedOrdersResponse } from '../core/models/api.models';

/**
 * Layout specs for the two widths that actually differ.
 *
 * These drive a real viewport by sizing the Karma iframe: CSS media queries read
 * the viewport, never the container, and Chrome's default ~749px run never
 * reaches the 768px breakpoint — which is exactly why mobile-only overflow and
 * alignment bugs can sit in a green suite indefinitely.
 */
const PHONE = 390;
const DESKTOP = 1280;

let restoreFrame: (() => void) | null = null;
const probes: HTMLElement[] = [];

function setViewport(width: number, height = 900): void {
  const frame = window.frameElement as HTMLIFrameElement | null;
  if (!frame) {
    throw new Error('these specs need the Karma iframe to drive a real viewport');
  }
  if (!restoreFrame) {
    const style = frame.style;
    const width0 = style.width;
    const height0 = style.height;
    restoreFrame = () => {
      style.width = width0;
      style.height = height0;
    };
  }
  frame.style.width = `${width}px`;
  frame.style.height = `${height}px`;
}

/** How far the element's content sticks out past its own box. */
function overflowOf(element: HTMLElement): number {
  return element.scrollWidth - element.clientWidth;
}

function trackCount(element: HTMLElement): number {
  return getComputedStyle(element).gridTemplateColumns.split(' ').filter(Boolean).length;
}

/** A throwaway node, so a shared class can be measured against the real cascade. */
function probe(className: string, referenceStyle?: (el: HTMLElement) => void): HTMLElement {
  const el = document.createElement('span');
  el.className = className;
  el.textContent = 'probe';
  referenceStyle?.(el);
  document.body.appendChild(el);
  probes.push(el);
  return el;
}

function material(overrides: Record<string, unknown> = {}) {
  return { id: 1, name: 'Basmati Rice', unit: 'kg', stockQuantity: 12, lowStockThreshold: 3, costPerUnit: 90, ...overrides };
}

function varianceRow(overrides: Record<string, unknown> = {}) {
  return { materialName: 'Basmati Rice', systemStock: 12, physicalCount: 10, variance: -2, variancePct: -16.7, ...overrides };
}

function order(overrides: Partial<BusinessOrder> = {}): BusinessOrder {
  return {
    sourceType: 'DINE_IN',
    orderType: 'DINE_IN',
    orderId: 7,
    orderCode: 'KH-1007',
    invoiceNumber: 'INV-1007',
    publicToken: null,
    customerName: 'Ravi',
    customerContact: '9999999999',
    tableNumber: 4,
    orderStatus: 'COMPLETED',
    paymentStatus: 'PAID',
    paymentMethod: 'UPI',
    totalAmount: 480,
    gatewayPaidAmount: 480,
    refundAmount: null,
    refundStatus: 'NONE',
    refundMode: null,
    cancelReason: null,
    manualRefundAllowed: true,
    gatewayRefundAllowed: false,
    createdAt: Date.now(),
    ...overrides,
  };
}

function mountInventory(options: { materials?: unknown[]; variance?: unknown[] } = {}): ComponentFixture<InventoryPageComponent> {
  const api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', ['getInventoryMaterials', 'getVendors']);
  api.getInventoryMaterials.and.returnValue(of(options.materials ?? []));
  api.getVendors.and.returnValue(of([]));

  TestBed.configureTestingModule({
    imports: [InventoryPageComponent],
    providers: [
      { provide: BusinessApiService, useValue: api },
      { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show']) },
    ],
  });

  const fixture = TestBed.createComponent(InventoryPageComponent);
  fixture.detectChanges();
  if (options.variance) {
    // OnPush: a plain property write is not picked up by detectChanges(), so the
    // tab is opened the way a user opens it — through the real control.
    fixture.componentInstance.varianceData = options.variance;
    openTab(fixture.nativeElement as HTMLElement, 'Variance Report');
    fixture.detectChanges();
  }
  return fixture;
}

/** Clicks a real tab control, which is also what marks the OnPush view dirty. */
function openTab(host: HTMLElement, label: string): void {
  const tab = Array.from(host.querySelectorAll('.tab')).find((el) => (el.textContent || '').includes(label)) as HTMLButtonElement | undefined;
  if (!tab) {
    throw new Error(`no tab labelled ${label}`);
  }
  tab.click();
}

function clickByLabel(host: HTMLElement, ariaLabel: string): void {
  const button = host.querySelector(`[aria-label="${ariaLabel}"]`) as HTMLButtonElement | null;
  if (!button) {
    throw new Error(`no control labelled ${ariaLabel}`);
  }
  button.click();
}

function clickByText(host: HTMLElement, text: string): void {
  const button = Array.from(host.querySelectorAll('button')).find((el) => (el.textContent || '').includes(text)) as HTMLButtonElement | undefined;
  if (!button) {
    throw new Error(`no button labelled ${text}`);
  }
  button.click();
}

function mountOrderHistory(orders: BusinessOrder[]): ComponentFixture<OrderHistoryTabComponent> {
  const page: PaginatedOrdersResponse = { content: orders, totalElements: orders.length, totalPages: 1, page: 0, size: 15 };
  const api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', ['getOrdersPaginated']);
  api.getOrdersPaginated.and.returnValue(of(page));

  TestBed.configureTestingModule({
    imports: [OrderHistoryTabComponent],
    providers: [
      { provide: BusinessApiService, useValue: api },
      { provide: AuthService, useValue: { session: () => null } },
      { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show']) },
    ],
  });

  const fixture = TestBed.createComponent(OrderHistoryTabComponent);
  fixture.detectChanges();
  return fixture;
}

afterEach(() => {
  while (probes.length) {
    probes.pop()!.remove();
  }
  // openManualRefund() locks body scroll; leaving it locked would affect other specs.
  document.body.style.overflow = '';
  restoreFrame?.();
  restoreFrame = null;
});

describe('Responsive layout — inventory page', () => {
  it('fits inside a phone viewport without sideways page scroll', () => {
    setViewport(PHONE);
    const host = mountInventory({ materials: [material()], variance: [varianceRow()] }).nativeElement as HTMLElement;

    expect(overflowOf(host)).withContext('the whole page must not scroll sideways on a phone').toBeLessThanOrEqual(1);
  });

  it('makes the five tabs scroll rather than widen the page on a phone', () => {
    setViewport(PHONE);
    const host = mountInventory({ materials: [material()] }).nativeElement as HTMLElement;

    const tabs = host.querySelector('.tabs') as HTMLElement;
    expect(tabs).withContext('inventory always shows its tab strip').toBeTruthy();
    expect(getComputedStyle(tabs).overflowX).toBe('auto');
    expect(tabs.scrollWidth).withContext('five tab labels cannot fit 390px, so the strip itself has to scroll').toBeGreaterThan(tabs.clientWidth);

    for (const tab of Array.from(tabs.querySelectorAll('.tab')) as HTMLElement[]) {
      expect(getComputedStyle(tab).whiteSpace).withContext('a wrapped tab label makes the strip taller, not wider').toBe('nowrap');
    }

    expect(overflowOf(host)).toBeLessThanOrEqual(1);
  });

  it('keeps the tabs on one line without scrolling on desktop', () => {
    setViewport(DESKTOP);
    const host = mountInventory({ materials: [material()] }).nativeElement as HTMLElement;

    const tabs = host.querySelector('.tabs') as HTMLElement;
    expect(tabs.scrollWidth).toBeLessThanOrEqual(tabs.clientWidth + 1);
    expect(overflowOf(host)).toBeLessThanOrEqual(1);
  });

  it('scrolls the variance table inside its own wrapper on a phone', () => {
    setViewport(PHONE);
    const host = mountInventory({ variance: [varianceRow(), varianceRow({ materialName: 'Paneer' })] }).nativeElement as HTMLElement;

    const wrapper = host.querySelector('.table-wrap') as HTMLElement;
    expect(wrapper).withContext('the variance table needs its own horizontal scroll container').toBeTruthy();
    expect(getComputedStyle(wrapper).overflowX).toBe('auto');

    const table = wrapper.querySelector('table.data-table') as HTMLElement;
    expect(table).withContext('the variance table renders').toBeTruthy();
    expect(overflowOf(wrapper)).withContext('five nowrap columns are wider than a phone').toBeGreaterThan(0);

    expect(overflowOf(host)).withContext('the table scrolls inside the card, not the page').toBeLessThanOrEqual(1);
  });

  it('fits the variance table without a scrollbar on desktop', () => {
    setViewport(DESKTOP);
    const host = mountInventory({ variance: [varianceRow(), varianceRow({ materialName: 'Paneer' })] }).nativeElement as HTMLElement;

    const wrapper = host.querySelector('.table-wrap') as HTMLElement;
    expect(overflowOf(wrapper)).toBeLessThanOrEqual(1);
    expect(overflowOf(host)).toBeLessThanOrEqual(1);
  });
});

describe('Responsive layout — order history', () => {
  it('fits inside a phone viewport without sideways page scroll', () => {
    setViewport(PHONE);
    const host = mountOrderHistory([order()]).nativeElement as HTMLElement;

    expect(overflowOf(host)).toBeLessThanOrEqual(1);
  });

  it('does not hide the order table when there is no card list to replace it', () => {
    setViewport(PHONE);
    const host = mountOrderHistory([order()]).nativeElement as HTMLElement;

    const table = host.querySelector('.data-table') as HTMLElement | null;
    const cards = host.querySelector('.mobile-order-list');
    expect(table).withContext('the orders table renders').toBeTruthy();
    if (!cards) {
      // A hidden table overflows nothing, so the phone-layout spec above stays green
      // while a phone shows an empty Order History. That is how this bug survived.
      expect(getComputedStyle(table!).display)
        .withContext('a table with no card fallback must scroll, not disappear')
        .not.toBe('none');
    }
  });

  it('hides a table at phone width only when a card list sits beside it', () => {
    setViewport(PHONE);
    const wrap = document.createElement('div');
    wrap.className = 'table-wrap';
    wrap.innerHTML =
      '<table class="data-table"><tbody><tr><td>order 7</td></tr></tbody></table>' +
      '<div class="mobile-data-list">cards</div>';
    document.body.appendChild(wrap);
    probes.push(wrap);

    expect(getComputedStyle(wrap.querySelector('.data-table') as HTMLElement).display)
      .withContext('the table stands down for the cards')
      .toBe('none');
    expect(getComputedStyle(wrap.querySelector('.mobile-data-list') as HTMLElement).display)
      .withContext('the cards take its place')
      .toBe('grid');
  });

  it('enters the shared narrow layout at 768px, not a pixel either side', () => {
    const list = probe('mobile-data-list');

    setViewport(768);
    expect(getComputedStyle(list).display).withContext('768 is the narrow boundary').toBe('grid');

    setViewport(769);
    expect(getComputedStyle(list).display).withContext('769 is no longer narrow').not.toBe('grid');
  });

  it('right-aligns the Action column with its own header on desktop', () => {
    setViewport(DESKTOP);
    const fixture = mountOrderHistory([order()]);
    const host = fixture.nativeElement as HTMLElement;

    const actionHeader = host.querySelector('.data-table th.text-right') as HTMLElement;
    expect(actionHeader).withContext('the Action header carries the alignment class').toBeTruthy();
    expect(getComputedStyle(actionHeader).textAlign).toBe('right');

    const actionCell = host.querySelector('.data-table tbody tr td:last-child') as HTMLElement;
    expect(getComputedStyle(actionCell).textAlign).withContext('cells line up under their header').toBe('right');
  });

  it('aligns the numeric and status columns with their headers on desktop', () => {
    setViewport(DESKTOP);
    const host = mountOrderHistory([order()]).nativeElement as HTMLElement;

    const total = host.querySelector('.data-table tbody tr td:nth-child(4)') as HTMLElement;
    const status = host.querySelector('.data-table tbody tr td:nth-child(5)') as HTMLElement;
    const totalHeader = host.querySelector('.data-table thead th:nth-child(4)') as HTMLElement;
    const statusHeader = host.querySelector('.data-table thead th:nth-child(5)') as HTMLElement;

    expect(getComputedStyle(total).textAlign).toBe('right');
    expect(getComputedStyle(totalHeader).textAlign).toBe('right');
    expect(getComputedStyle(status).textAlign).toBe('center');
    expect(getComputedStyle(statusHeader).textAlign).toBe('center');
  });

  it('keeps the refund review label and amount on one row on a phone', () => {
    setViewport(PHONE);
    const fixture = mountOrderHistory([order({ manualRefundAllowed: true })]);
    const host = fixture.nativeElement as HTMLElement;

    // The flow a user takes: Refund on the row, then Review Refund.
    clickByLabel(host, 'Refund order');
    fixture.detectChanges();
    clickByText(host, 'Review Refund');
    fixture.detectChanges();

    const review = host.querySelector('.refund-review') as HTMLElement;
    expect(review).withContext('the refund review step renders').toBeTruthy();
    expect(trackCount(review)).withContext('a unitless track value is invalid and collapses the grid to one column').toBe(2);

    const [label, value] = Array.from(review.children) as HTMLElement[];
    const rowDelta = Math.abs(label.getBoundingClientRect().top - value.getBoundingClientRect().top);
    expect(rowDelta).withContext('label and value belong on the same line').toBeLessThan(2);
    expect(getComputedStyle(value).textAlign).toBe('right');
  });
});

describe('Shared text helpers used by page templates', () => {
  it('keeps the loading label of a real page out of sight', () => {
    // The loading panels announce themselves with a visually-hidden sentence.
    // When the class they carry resolves to nothing (a Tailwind-style `sr-only`
    // that this project never defines globally), that sentence is painted next
    // to the skeleton on every page.
    const api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', ['getTerminals', 'getTerminalRequests']);
    api.getTerminals.and.returnValue(NEVER);
    api.getTerminalRequests.and.returnValue(NEVER);

    TestBed.configureTestingModule({
      imports: [TerminalsPageComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: { session: () => null } },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show']) },
      ],
    });

    const fixture = TestBed.createComponent(TerminalsPageComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const label = host.querySelector('.panel.loading .visually-hidden') as HTMLElement;
    expect(label).withContext('the loading panel still carries its announced label').toBeTruthy();
    expect(label.textContent).toContain('Loading terminals');

    const style = getComputedStyle(label);
    expect(style.position).toBe('absolute');
    expect(style.width).toBe('1px');
    expect(label.getBoundingClientRect().width).withContext('the sentence must not be painted').toBeLessThan(2);
  });

  it('clips visually-hidden text out of view instead of rendering it', () => {
    // Every loading panel carries a <span class="visually-hidden">Loading …</span>.
    // When the class resolves to nothing the sentence is painted next to the
    // skeleton, so the label has to be measurably clipped, not merely present.
    const el = probe('visually-hidden');
    const style = getComputedStyle(el);

    expect(style.position).toBe('absolute');
    expect(style.width).toBe('1px');
    expect(style.height).toBe('1px');
    expect(style.clip).toContain('rect');
    expect(el.getBoundingClientRect().width).toBeLessThan(2);
  });

  it('resolves text-right, font-bold, font-mono and text-brand globally', () => {
    const el = probe('text-right font-bold font-mono text-brand');
    const monoReference = probe('', (node) => (node.style.fontFamily = 'var(--kb-font-mono)'));
    // Brand-coloured TEXT uses the link tone, not the fill: the fill only reaches
    // 3.4:1 against a card, so `.text-brand` resolves to --kb-color-link.
    const brandReference = probe('', (node) => (node.style.color = 'var(--kb-color-link)'));
    const style = getComputedStyle(el);

    expect(style.textAlign).toBe('right');
    expect(style.fontWeight).toBe('700');
    expect(style.fontFamily).toBe(getComputedStyle(monoReference).fontFamily);
    expect(style.color).toBe(getComputedStyle(brandReference).color);
  });
});
