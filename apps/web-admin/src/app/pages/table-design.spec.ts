import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { BusinessesPageComponent } from './businesses/businesses-page.component';
import { BusinessDashboardPageComponent } from './business-dashboard/business-dashboard-page.component';
import { InventoryPageComponent } from './inventory/inventory-page.component';
import { MenuPageComponent } from './menu/menu-page.component';
import { OrdersPageComponent } from './orders/orders-page.component';
import { PlatformDashboardPageComponent } from './platform-dashboard/platform-dashboard-page.component';
import { ReportsPageComponent } from './reports/reports-page.component';
import { StaffPageComponent } from './staff/staff-page.component';
import { OrderDetailModalComponent } from '../shared/order-detail-modal.component';
import { AdminApiService } from '../core/services/admin-api.service';
import { BusinessApiService } from '../core/services/business-api.service';
import { AuthService } from '../core/auth/auth.service';
import { ToastService } from '../core/services/toast.service';

/**
 * One table design, measured rather than asserted from reading CSS.
 *
 * The app had sixteen tables across six designs: `.data-table`, a local
 * `.admin-table`, `.report-table`, `table.styled` on inventory's variance table,
 * `.order-modal__table` in the shared modal, and `.orders-tactile-table` — which
 * was itself two designs, because the orders history tab overrode the global
 * rules. They are all `.data-table` now, defined once in the "Tables" section of
 * styles.css.
 *
 * These specs mount every former family, read the *computed* style of a header
 * and a body cell in each, and require all of them to be identical. A page may
 * still lay its columns out (widths, per-column alignment); it may not restate
 * the design. Two body rows are used everywhere on purpose: with a single row
 * `tr:last-child td` drops the bottom border, which would make one table's cells
 * look different for a reason that is not the design.
 */
const authStub = {
  session: () => ({ role: 'OWNER', restaurantId: 1, loginId: 'owner', restaurantName: 'Test Kitchen' }),
} as unknown as AuthService;

function mount<T>(component: unknown, providers: unknown[] = []): ComponentFixture<T> {
  // Each family is mounted in the same spec, and a testing module can only be
  // configured once — so tear the previous one down instead of reusing it.
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [component as never],
    providers: [
      provideHttpClient(),
      { provide: AuthService, useValue: authStub },
      { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show', 'error', 'success']) },
      ...(providers as never[]),
    ],
  });
  const fixture = TestBed.createComponent(component as never) as ComponentFixture<T>;
  fixture.detectChanges();
  return fixture;
}

const routerStub = { navigate: () => Promise.resolve(true), events: of(), createUrlTree: () => null, serializeUrl: () => '' };

/** The design as the browser actually computes it, for the first data cell. */
interface TableDesign {
  th: Record<string, string>;
  td: Record<string, string>;
  rowsTransition: string;
}

const TH_PROPS = [
  'fontSize', 'fontWeight', 'textTransform', 'letterSpacing', 'color', 'backgroundColor',
  'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
  'borderBottomWidth', 'borderBottomColor', 'borderBottomStyle', 'whiteSpace', 'position', 'textAlign',
] as const;

const TD_PROPS = [
  'fontSize', 'color', 'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
  'borderBottomWidth', 'borderBottomColor', 'borderBottomStyle', 'verticalAlign', 'whiteSpace',
] as const;

/** Opens a tab through the real control: these pages are OnPush, so a plain
 *  property write is not picked up by detectChanges(). */
function clickTab(host: HTMLElement, label: string): void {
  const tab = Array.from(host.querySelectorAll('.tab'))
    .find((el) => (el.textContent || '').includes(label)) as HTMLButtonElement | undefined;
  expect(tab).withContext(`tab "${label}" should exist`).toBeTruthy();
  tab!.click();
}

function pick(style: CSSStyleDeclaration, props: readonly string[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const prop of props) {
    out[prop] = style[prop as never] as string;
  }
  return out;
}

function designOf(host: HTMLElement, label: string): TableDesign {
  const table = host.querySelector('table') as HTMLTableElement | null;
  expect(table).withContext(`${label}: should render a table`).toBeTruthy();
  expect(table!.classList.contains('data-table'))
    .withContext(`${label}: every table carries the one design class`)
    .toBeTrue();

  const th = table!.querySelector('thead th') as HTMLElement | null;
  // Measure a plain data cell. `.muted` is a deliberate secondary-value
  // modifier (a timestamp, a rank) and is not the table's design; a row header
  // now occupies the first cell of some tables, so the first `td` can be either
  // of those rather than the body cell the design defines.
  const td = (table!.querySelector('tbody td:not(.muted)') ?? table!.querySelector('tbody td')) as HTMLElement | null;
  const firstRow = table!.querySelector('tbody tr') as HTMLElement | null;
  expect(th).withContext(`${label}: header cell`).toBeTruthy();
  expect(td).withContext(`${label}: body cell`).toBeTruthy();

  return {
    th: pick(getComputedStyle(th!), TH_PROPS),
    td: pick(getComputedStyle(td!), TD_PROPS),
    rowsTransition: getComputedStyle(firstRow!).transitionProperty
      + ' ' + getComputedStyle(firstRow!).transitionDuration,
  };
}

/**
 * Every family previously had its own cell padding, type scale and border
 * colour. These fixtures give each table two rows and one header.
 */
function measureAll(): { label: string; design: TableDesign }[] {
  const results: { label: string; design: TableDesign }[] = [];

  const staffApi = jasmine.createSpyObj('BusinessApiService', ['getStaff']);
  staffApi.getStaff.and.returnValue(of([
    { userId: 1, name: 'Ravi', loginId: 'ravi', role: 'STAFF', isActive: true },
    { userId: 2, name: 'Meena', loginId: 'meena', role: 'OWNER', isActive: true },
  ]));
  const staff = mount<StaffPageComponent>(StaffPageComponent, [{ provide: BusinessApiService, useValue: staffApi }]);
  results.push({ label: 'staff (.data-table, unchanged baseline)', design: designOf(staff.nativeElement, 'staff') });
  staff.destroy();

  const menuApi = jasmine.createSpyObj('BusinessApiService', ['getMenu', 'getMenuCategories']);
  menuApi.getMenu.and.returnValue(of([
    { id: 1, name: 'Dosa', price: 90, categoryId: 1, isAvailable: true },
    { id: 2, name: 'Idli', price: 60, categoryId: 1, isAvailable: true },
  ]));
  menuApi.getMenuCategories.and.returnValue(of([]));
  const menu = mount<MenuPageComponent>(MenuPageComponent, [{ provide: BusinessApiService, useValue: menuApi }]);
  results.push({ label: 'menu (.data-table + local overrides, now layout only)', design: designOf(menu.nativeElement, 'menu') });
  menu.destroy();

  const businessesApi = jasmine.createSpyObj('AdminApiService', ['getBusinesses']);
  businessesApi.getBusinesses.and.returnValue(of([
    { restaurantId: 1, shopName: 'Test Kitchen', isSuspended: false },
    { restaurantId: 2, shopName: 'Second Kitchen', isSuspended: false },
  ]));
  const businesses = mount<BusinessesPageComponent>(BusinessesPageComponent, [
    { provide: AdminApiService, useValue: businessesApi },
  ]);
  results.push({ label: 'businesses (was a brand-coloured header band)', design: designOf(businesses.nativeElement, 'businesses') });
  businesses.destroy();

  const inventoryApi = jasmine.createSpyObj('BusinessApiService', ['getInventoryMaterials', 'getVendors']);
  inventoryApi.getInventoryMaterials.and.returnValue(of([
    { id: 1, name: 'Rice', unit: 'kg', stockQuantity: 5, lowStockThreshold: 1 },
    { id: 2, name: 'Dal', unit: 'kg', stockQuantity: 8, lowStockThreshold: 2 },
  ]));
  inventoryApi.getVendors.and.returnValue(of([]));
  const inventory = mount<InventoryPageComponent>(InventoryPageComponent, [
    { provide: BusinessApiService, useValue: inventoryApi },
  ]);
  inventory.componentInstance.varianceData = [
    { materialName: 'Rice', systemStock: 12, physicalCount: 10, variance: -2, variancePct: -16.7 },
    { materialName: 'Dal', systemStock: 8, physicalCount: 8, variance: 0, variancePct: 0 },
  ];
  clickTab(inventory.nativeElement as HTMLElement, 'Variance Report');
  inventory.detectChanges();
  results.push({ label: 'inventory variance (was table.styled, all centred)', design: designOf(inventory.nativeElement, 'inventory') });
  inventory.destroy();

  const ordersApi = jasmine.createSpyObj('BusinessApiService', ['getOrdersPaginated', 'getOrderDetail']);
  ordersApi.getOrdersPaginated.and.returnValue(of({
    content: [
      { orderId: 1, orderCode: 'C01', orderStatus: 'COMPLETED', totalAmount: 100, createdAt: Date.now() },
      { orderId: 2, orderCode: 'C02', orderStatus: 'COMPLETED', totalAmount: 220, createdAt: Date.now() },
    ],
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 20,
  }));
  ordersApi.getOrderDetail.and.returnValue(of({}));
  const orders = mount<OrdersPageComponent>(OrdersPageComponent, [
    { provide: BusinessApiService, useValue: ordersApi },
    {
      provide: ActivatedRoute,
      useValue: {
        snapshot: { queryParamMap: convertToParamMap({ tab: 'history' }) },
        queryParamMap: of(convertToParamMap({ tab: 'history' })),
      },
    },
    { provide: Router, useValue: routerStub },
  ]);
  results.push({ label: 'orders history (was .orders-tactile-table, locally overridden)', design: designOf(orders.nativeElement, 'orders history') });
  orders.destroy();

  const platformApi = jasmine.createSpyObj('AdminApiService', [
    'getDashboardSummary', 'getSettlements', 'getSubMerchants', 'getTransactions',
  ]);
  platformApi.getDashboardSummary.and.returnValue(of({ totalRevenue: 0, refundedAmount: 0, totalBusinesses: 0, liveBusinesses: 0 }));
  platformApi.getSettlements.and.returnValue(of([
    { id: 1, restaurantName: 'Test Kitchen', totalSettled: 100, totalCommission: 2, orderCount: 3 },
    { id: 2, restaurantName: 'Second Kitchen', totalSettled: 250, totalCommission: 5, orderCount: 4 },
  ]));
  platformApi.getSubMerchants.and.returnValue(of([]));
  platformApi.getTransactions.and.returnValue(of([]));
  const platform = mount<PlatformDashboardPageComponent>(PlatformDashboardPageComponent, [
    { provide: AdminApiService, useValue: platformApi },
    { provide: Router, useValue: routerStub },
  ]);
  platform.componentInstance.setTab('settlements');
  platform.detectChanges();
  results.push({ label: 'platform settlements (was .admin-table)', design: designOf(platform.nativeElement, 'platform') });
  platform.destroy();

  const reportsApi = jasmine.createSpyObj('BusinessApiService', ['getDashboard', 'getItemSales', 'getGstLedger']);
  reportsApi.getDashboard.and.returnValue(of({ totalRevenue: 100, refundedAmount: 0, posOrderCount: 2, pendingPosPayments: 0 }));
  reportsApi.getItemSales.and.returnValue(of([]));
  reportsApi.getGstLedger.and.returnValue(of({
    entries: [
      { billId: 1, invoiceNumber: 'INV-1', createdAt: Date.now(), customerName: 'Ravi', subtotal: 100, taxAmount: 5, cgst: 2.5, sgst: 2.5, total: 105 },
      { billId: 2, invoiceNumber: 'INV-2', createdAt: Date.now(), customerName: 'Meena', subtotal: 200, taxAmount: 10, cgst: 5, sgst: 5, total: 210 },
    ],
  }));
  const reports = mount<ReportsPageComponent>(ReportsPageComponent, [
    { provide: BusinessApiService, useValue: reportsApi },
  ]);
  reports.componentInstance.activeTab.set('gst');
  reports.detectChanges();
  results.push({ label: 'reports GST ledger (was .report-table)', design: designOf(reports.nativeElement, 'reports') });
  reports.destroy();

  const modal = mount<OrderDetailModalComponent>(OrderDetailModalComponent);
  modal.componentRef.setInput('order', {
    order: { orderId: 1, orderCode: 'C01', orderType: 'DINE_IN', invoiceNumber: 'INV-1' },
    lineItems: [
      { itemName: 'Dosa', quantity: 1, price: 90, itemTotal: 90 },
      { itemName: 'Idli', quantity: 2, price: 60, itemTotal: 120 },
    ],
  });
  modal.detectChanges();
  results.push({ label: 'order detail modal (was .order-modal__table)', design: designOf(modal.nativeElement, 'order modal') });
  modal.destroy();

  return results;
}

/** The dashboard's low-stock modal is the last table, and the last tactile one. */
function measureDashboardLowStockModal(): { label: string; design: TableDesign } {
  const api = jasmine.createSpyObj('BusinessApiService', [
    'getDashboard', 'getDashboardTrends', 'getOrdersPaginated', 'getHourlySales',
    'getItemSales', 'getInventoryMaterials', 'getTerminals', 'getDailyClosing', 'getNotifications',
  ]);
  api.getDashboard.and.returnValue(of({ posOrderCount: 4 }));
  api.getDashboardTrends.and.returnValue(of({ yesterdayRevenue: 0, todayRevenue: 0 }));
  api.getOrdersPaginated.and.returnValue(of({ content: [], totalElements: 0 }));
  api.getHourlySales.and.returnValue(of([]));
  api.getItemSales.and.returnValue(of([]));
  api.getInventoryMaterials.and.returnValue(of([
    { id: 1, name: 'Rice', stockQuantity: 1, lowStockThreshold: 5, unit: 'kg' },
    { id: 2, name: 'Dal', stockQuantity: 2, lowStockThreshold: 5, unit: 'kg' },
  ]));
  api.getTerminals.and.returnValue(of([]));
  api.getDailyClosing.and.returnValue(of(null));
  api.getNotifications.and.returnValue(of({ status: 'ok', notifications: [], unreadCount: 0 }));

  const fixture = mount<BusinessDashboardPageComponent>(BusinessDashboardPageComponent, [
    { provide: BusinessApiService, useValue: api },
    { provide: Router, useValue: routerStub },
  ]);
  fixture.componentInstance.toggleLowStockModal();
  fixture.detectChanges();
  const result = { label: 'dashboard low-stock modal (was the global .orders-tactile-table)', design: designOf(fixture.nativeElement, 'dashboard modal') };
  fixture.destroy();
  return result;
}

describe('one table design across the whole admin app', () => {
  it('computes identical header, body and row-motion styles for every former design family', () => {
    const measured = measureAll();
    measured.push(measureDashboardLowStockModal());

    expect(measured.length).withContext('every family is represented').toBe(9);

    const [baseline, ...rest] = measured;
    for (const entry of rest) {
      expect(entry.design.th)
        .withContext(`${entry.label}: header must match ${baseline.label}`)
        .toEqual(baseline.design.th);
      expect(entry.design.td)
        .withContext(`${entry.label}: body cell must match ${baseline.label}`)
        .toEqual(baseline.design.td);
      expect(entry.design.rowsTransition)
        .withContext(`${entry.label}: row feedback must match ${baseline.label}`)
        .toEqual(baseline.design.rowsTransition);
    }
  });
});
