import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject, of } from 'rxjs';

import { BusinessesPageComponent } from './businesses/businesses-page.component';
import { BusinessDashboardPageComponent } from './business-dashboard/business-dashboard-page.component';
import { BusinessSettingsPageComponent } from './business-settings/business-settings-page.component';
import { DailyClosingPageComponent } from './daily-closing/daily-closing-page.component';
import { InventoryPageComponent } from './inventory/inventory-page.component';
import { MenuPageComponent } from './menu/menu-page.component';
import { OrdersPageComponent } from './orders/orders-page.component';
import { ReportsPageComponent } from './reports/reports-page.component';
import { StaffPageComponent } from './staff/staff-page.component';
import { AdminApiService } from '../core/services/admin-api.service';
import { BusinessApiService } from '../core/services/business-api.service';
import { AuthService } from '../core/auth/auth.service';
import { ToastService } from '../core/services/toast.service';

/**
 * "When I hit refresh, the data should visibly reload — you should feel it."
 *
 * Terminals is the reference: the table is swapped for skeletons on *every*
 * load, so a refresh is unmistakable. Most other pages only spun the little
 * icon and silently replaced the numbers underneath — several had even written
 * that down as a deliberate "stale-while-refresh" contract.
 *
 * These specs drive the contract through the real header control, against an
 * API that stays pending, and assert which of the two is on screen: the
 * skeleton that owns the page while loading, or the data otherwise.
 *
 * The two auto-polling panels (Active Orders every 30s, the dashboard every
 * 60s) must NOT flash skeletons at someone who is just reading the screen, so
 * they are pinned separately — including the trap that their refresh button is
 * disabled while a background poll is in flight.
 */
function deferred<T>() {
  const subject = new Subject<T>();
  return { subject, stream: subject.asObservable() };
}

const authStub = {
  session: () => ({ role: 'OWNER', restaurantId: 1, loginId: 'owner', restaurantName: 'Test Kitchen' }),
} as unknown as AuthService;

function toastStub(): ToastService {
  return jasmine.createSpyObj('ToastService', ['show', 'error', 'success']) as unknown as ToastService;
}

function mount<T>(component: unknown, providers: unknown[] = []): ComponentFixture<T> {
  TestBed.configureTestingModule({
    imports: [component as never],
    providers: [
      provideHttpClient(),
      { provide: AuthService, useValue: authStub },
      { provide: ToastService, useValue: toastStub() },
      ...(providers as never[]),
    ],
  });
  const fixture = TestBed.createComponent(component as never) as ComponentFixture<T>;
  fixture.detectChanges();
  return fixture;
}

function host<T>(fixture: ComponentFixture<T>): HTMLElement {
  return fixture.nativeElement as HTMLElement;
}

/** The skeleton that owns the screen while loading. */
function skeleton<T>(fixture: ComponentFixture<T>, selector = '.panel.loading'): Element | null {
  return host(fixture).querySelector(selector);
}

/** Clicks the shared header refresh button, which is how a user asks for this. */
function clickRefresh<T>(fixture: ComponentFixture<T>, selector = 'app-refresh-button button'): void {
  const button = host(fixture).querySelector(selector) as HTMLButtonElement | null;
  expect(button).withContext('the page should expose the shared refresh button').toBeTruthy();
  button!.click();
  fixture.detectChanges();
}

describe('manual refresh visibly reloads the data', () => {
  it('staff: the table is replaced by skeletons while reloading', () => {
    const staff = deferred<unknown[]>();
    const api = jasmine.createSpyObj('BusinessApiService', ['getStaff']);
    api.getStaff.and.returnValue(staff.stream);

    const fixture = mount<StaffPageComponent>(StaffPageComponent, [{ provide: BusinessApiService, useValue: api }]);
    const rows = () => host(fixture).querySelector('table.data-table');

    expect(skeleton(fixture)).withContext('staff: first load shows the skeleton').toBeTruthy();
    expect(rows()).withContext('staff: no rows while the first load is pending').toBeFalsy();

    staff.subject.next([{ userId: 1, name: 'Ravi', loginId: 'ravi', role: 'STAFF', isActive: true }]);
    fixture.detectChanges();
    expect(rows()).withContext('staff: the rows render once loaded').toBeTruthy();
    expect(skeleton(fixture)).toBeFalsy();

    clickRefresh(fixture);
    expect(skeleton(fixture))
      .withContext('staff: a manual refresh must bring the skeleton back so the reload is felt')
      .toBeTruthy();
    expect(rows()).withContext('staff: stale rows must not stay on screen during a refresh').toBeFalsy();

    staff.subject.next([{ userId: 1, name: 'Ravi', loginId: 'ravi', role: 'STAFF', isActive: true }]);
    fixture.detectChanges();
    expect(rows()).withContext('staff: the rows come back once the reload settles').toBeTruthy();
    fixture.destroy();
  });

  it('staff: the sign-out-all-devices button is gone from the header', () => {
    const api = jasmine.createSpyObj('BusinessApiService', ['getStaff', 'revokeAllSessions']);
    api.getStaff.and.returnValue(of([]));

    const fixture = mount<StaffPageComponent>(StaffPageComponent, [{ provide: BusinessApiService, useValue: api }]);
    const header = host(fixture).querySelector('app-page-header') as HTMLElement;

    expect(header.textContent).withContext('the bulk sign-out button should be gone').not.toContain('Sign Out All Devices');
    // Only the per-staff row action should be left able to sign a device out.
    expect(api.revokeAllSessions).withContext('nothing should call the bulk revoke on load').not.toHaveBeenCalled();
    expect(header.querySelector('.danger-btn')).withContext('no danger button left in the header').toBeFalsy();
    fixture.destroy();
  });

  it('businesses: the table is replaced by skeletons while reloading', () => {
    const businesses = deferred<unknown[]>();
    const api = jasmine.createSpyObj('AdminApiService', ['getBusinesses']);
    api.getBusinesses.and.returnValue(businesses.stream);

    const fixture = mount<BusinessesPageComponent>(BusinessesPageComponent, [
      { provide: AdminApiService, useValue: api },
    ]);
    const rows = () => host(fixture).querySelector('table.data-table');

    expect(skeleton(fixture)).withContext('businesses: first load shows the skeleton').toBeTruthy();

    businesses.subject.next([{ restaurantId: 1, shopName: 'Test Kitchen', isSuspended: false }]);
    fixture.detectChanges();
    expect(rows()).withContext('businesses: the rows render once loaded').toBeTruthy();

    clickRefresh(fixture);
    expect(skeleton(fixture)).withContext('businesses: a manual refresh reloads visibly').toBeTruthy();
    expect(rows()).withContext('businesses: stale rows must not stay on screen').toBeFalsy();

    businesses.subject.next([{ restaurantId: 1, shopName: 'Test Kitchen', isSuspended: false }]);
    fixture.detectChanges();
    expect(rows()).withContext('businesses: the rows come back').toBeTruthy();
    expect(skeleton(fixture)).toBeFalsy();
    fixture.destroy();
  });

  it('menu: the table is replaced by skeletons while reloading', () => {
    const items = deferred<unknown[]>();
    const api = jasmine.createSpyObj('BusinessApiService', ['getMenu', 'getMenuCategories']);
    api.getMenu.and.returnValue(items.stream);
    api.getMenuCategories.and.returnValue(of([]));

    const fixture = mount<MenuPageComponent>(MenuPageComponent, [{ provide: BusinessApiService, useValue: api }]);

    expect(skeleton(fixture)).withContext('menu: first load shows the skeleton').toBeTruthy();

    clickRefresh(fixture);
    expect(skeleton(fixture)).withContext('menu: a manual refresh reloads visibly').toBeTruthy();

    // loadMenu() uses forkJoin, which only emits once every source completes.
    items.subject.next([{ id: 1, name: 'Dosa', price: 90, categoryId: 1, isAvailable: true }]);
    items.subject.complete();
    fixture.detectChanges();
    expect(host(fixture).querySelector('table.data-table')).withContext('menu: rows return').toBeTruthy();
    expect(skeleton(fixture)).toBeFalsy();
    fixture.destroy();
  });

  it('inventory: the material grid is replaced by skeletons while reloading', () => {
    const materials = deferred<unknown[]>();
    const api = jasmine.createSpyObj('BusinessApiService', ['getInventoryMaterials', 'getVendors']);
    api.getInventoryMaterials.and.returnValue(materials.stream);
    api.getVendors.and.returnValue(of([]));

    const fixture = mount<InventoryPageComponent>(InventoryPageComponent, [
      { provide: BusinessApiService, useValue: api },
    ]);
    const cards = () => host(fixture).querySelector('.materials-grid');

    expect(skeleton(fixture)).withContext('inventory: first load shows the skeleton').toBeTruthy();
    // The page used to answer a refresh with a bare spinner. Skeletons are the
    // house style, so the placeholder has to be real rows.
    expect(host(fixture).querySelectorAll('.panel.loading .skeleton-row').length)
      .withContext('inventory: the loading state is a skeleton stack, not a lone spinner')
      .toBeGreaterThan(0);

    materials.subject.next([{ id: 1, name: 'Rice', unit: 'kg', stockQuantity: 5, lowStockThreshold: 1 }]);
    fixture.detectChanges();
    expect(cards()).withContext('inventory: the cards render once loaded').toBeTruthy();

    clickRefresh(fixture);
    expect(skeleton(fixture)).withContext('inventory: a manual refresh reloads visibly').toBeTruthy();
    expect(cards()).withContext('inventory: stale cards must not stay on screen').toBeFalsy();

    materials.subject.next([{ id: 1, name: 'Rice', unit: 'kg', stockQuantity: 5, lowStockThreshold: 1 }]);
    fixture.detectChanges();
    expect(cards()).withContext('inventory: the cards come back').toBeTruthy();
    fixture.destroy();
  });

  it('business settings: the form is withheld while reloading', () => {
    const profile = deferred<unknown>();
    // The payment section child loads its own config on construction.
    const api = jasmine.createSpyObj('BusinessApiService', ['getProfile', 'getPaymentConfig']);
    api.getProfile.and.returnValue(profile.stream);
    api.getPaymentConfig.and.returnValue(of({}));

    const fixture = mount<BusinessSettingsPageComponent>(BusinessSettingsPageComponent, [
      { provide: BusinessApiService, useValue: api },
    ]);
    // The profile payload's own form. The password section is a static form
    // that deliberately stays put, so it is not what this pins.
    const form = () => (host(fixture).textContent || '').includes('Restaurant Identity');

    expect(skeleton(fixture)).withContext('settings: first load shows the skeleton').toBeTruthy();
    expect(form()).withContext('settings: no settings form while the first load is pending').toBeFalsy();

    profile.subject.next({ shopName: 'Test Kitchen' });
    fixture.detectChanges();
    expect(form()).withContext('settings: the form renders once loaded').toBeTruthy();
    expect(skeleton(fixture)).toBeFalsy();

    clickRefresh(fixture);
    expect(skeleton(fixture)).withContext('settings: a manual refresh reloads visibly').toBeTruthy();
    expect(form()).withContext('settings: the old values must not stay on screen').toBeFalsy();

    profile.subject.next({ shopName: 'Test Kitchen' });
    fixture.detectChanges();
    expect(form()).withContext('settings: the form comes back after the reload').toBeTruthy();
    expect(skeleton(fixture)).toBeFalsy();
    fixture.destroy();
  });

  it('daily closing: the figures are withheld while reloading', () => {
    const closing = deferred<unknown>();
    const api = jasmine.createSpyObj('BusinessApiService', ['getDailyClosing']);
    api.getDailyClosing.and.returnValue(closing.stream);

    const fixture = mount<DailyClosingPageComponent>(DailyClosingPageComponent, [
      { provide: BusinessApiService, useValue: api },
    ]);
    const figures = () => host(fixture).querySelector('.closing-bento-grid');

    expect(skeleton(fixture)).withContext('daily closing: first load shows the skeleton').toBeTruthy();

    closing.subject.next({ date: '2026-10-14', totalOrders: 3, completedOrders: 3 });
    fixture.detectChanges();
    expect(figures()).withContext('daily closing: the figures render once loaded').toBeTruthy();

    clickRefresh(fixture);
    expect(skeleton(fixture)).withContext('daily closing: a manual refresh reloads visibly').toBeTruthy();
    expect(figures()).withContext('daily closing: stale figures must not stay on screen').toBeFalsy();

    closing.subject.next({ date: '2026-10-14', totalOrders: 3, completedOrders: 3 });
    fixture.detectChanges();
    expect(figures()).withContext('daily closing: the figures come back').toBeTruthy();
    expect(skeleton(fixture)).toBeFalsy();
    fixture.destroy();
  });

  it('order history: the refresh button reloads the table visibly', () => {
    const history = deferred<unknown>();
    const api = jasmine.createSpyObj('BusinessApiService', ['getOrdersPaginated']);
    api.getOrdersPaginated.and.returnValue(history.stream);

    const fixture = mount<OrdersPageComponent>(OrdersPageComponent, [
      { provide: BusinessApiService, useValue: api },
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { queryParamMap: convertToParamMap({ tab: 'history' }) },
          queryParamMap: of(convertToParamMap({ tab: 'history' })),
        },
      },
      { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
    ]);
    const table = () => host(fixture).querySelector('.orders-table-wrapper');
    const skeletonRow = () => host(fixture).querySelector('.panel.loading .skeleton-row');

    expect(skeletonRow()).withContext('order history: the first load shows skeletons').toBeTruthy();

    history.subject.next({
      content: [{ orderId: 1, orderCode: 'C01', orderStatus: 'COMPLETED', totalAmount: 100, createdAt: Date.now() }],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    });
    fixture.detectChanges();
    expect(table()).withContext('order history: the table renders once loaded').toBeTruthy();
    expect(skeletonRow()).toBeFalsy();

    clickRefresh(fixture, 'app-date-range-toolbar app-refresh-button button');
    expect(api.getOrdersPaginated.calls.count())
      .withContext('order history: the refresh button refetches from the server')
      .toBe(2);
    expect(skeletonRow())
      .withContext('order history: the refresh button must reload the table visibly')
      .toBeTruthy();
    expect(table()).withContext('order history: stale rows must not stay on screen').toBeFalsy();
    fixture.destroy();
  });

  it('reports: the report is withheld while reloading', () => {
    const report = deferred<unknown>();
    const api = jasmine.createSpyObj('BusinessApiService', ['getDashboard', 'getItemSales', 'getGstLedger']);
    api.getDashboard.and.returnValue(report.stream);
    api.getItemSales.and.returnValue(of([]));
    api.getGstLedger.and.returnValue(of(null));

    const fixture = mount<ReportsPageComponent>(ReportsPageComponent, [
      { provide: BusinessApiService, useValue: api },
    ]);
    const loading = () => host(fixture).querySelector('[aria-label="Loading reports"]');

    expect(loading()).withContext('reports: first load shows the skeleton').toBeTruthy();

    // The report is a forkJoin, which only emits once every source completes.
    report.subject.next({ totalRevenue: 100, refundedAmount: 0, posOrderCount: 2, pendingPosPayments: 0 });
    report.subject.complete();
    fixture.detectChanges();
    expect(loading()).withContext('reports: the skeleton gives way to the report').toBeFalsy();

    clickRefresh(fixture);
    expect(loading()).withContext('reports: a manual refresh reloads visibly').toBeTruthy();
    fixture.destroy();
  });
});

/**
 * The two panels that reload themselves on a timer. Terminals-style skeletons
 * are right for a user-requested reload and wrong for a background one: a
 * skeleton blinking over figures someone is reading is worse than no feedback.
 */
describe('background polls stay silent', () => {
  it('active orders: the page refresh button reloads visibly, the 30s poll does not', fakeAsync(() => {
    const orders = deferred<unknown>();
    const api = jasmine.createSpyObj('BusinessApiService', ['getOrdersPaginated']);
    api.getOrdersPaginated.and.returnValue(orders.stream);

    const fixture = mount<OrdersPageComponent>(OrdersPageComponent, [
      { provide: BusinessApiService, useValue: api },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap({}) }, queryParamMap: of(convertToParamMap({})) },
      },
      { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
    ]);
    const grid = () => host(fixture).querySelector('.active-orders-grid');

    expect(skeleton(fixture)).withContext('active orders: first load shows the skeleton').toBeTruthy();

    orders.subject.next({ content: [{ orderId: 1, orderCode: 'C01' }], totalElements: 1 });
    fixture.detectChanges();
    expect(grid()).withContext('active orders: the cards render once loaded').toBeTruthy();

    // A user-requested reload: the Orders page forwards it as a manual load.
    clickRefresh(fixture, 'app-date-range-toolbar app-refresh-button button');
    expect(skeleton(fixture)).withContext('active orders: the refresh button reloads visibly').toBeTruthy();
    expect(grid()).withContext('active orders: the refresh button clears the stale cards').toBeFalsy();

    orders.subject.next({ content: [{ orderId: 1, orderCode: 'C01' }], totalElements: 1 });
    fixture.detectChanges();
    expect(grid()).withContext('active orders: the cards come back').toBeTruthy();

    // ...and the 30s poll must leave them alone.
    tick(30_000);
    fixture.detectChanges();
    expect(skeleton(fixture)).withContext('active orders: a poll must not flash the skeleton').toBeFalsy();
    expect(grid()).withContext('active orders: a poll must leave the cards on screen').toBeTruthy();

    fixture.destroy();
  }));

  it('business dashboard: the refresh button reloads visibly, the 60s poll does not', fakeAsync(() => {
    const dashboard = deferred<unknown>();
    const api = jasmine.createSpyObj('BusinessApiService', [
      'getDashboard',
      'getDashboardTrends',
      'getOrdersPaginated',
      'getHourlySales',
      'getItemSales',
      'getInventoryMaterials',
      'getTerminals',
      'getDailyClosing',
      'getNotifications',
    ]);
    api.getDashboard.and.returnValue(dashboard.stream);
    api.getDashboardTrends.and.returnValue(of({ yesterdayRevenue: 0, todayRevenue: 0 }));
    api.getOrdersPaginated.and.returnValue(of({ content: [], totalElements: 0 }));
    api.getHourlySales.and.returnValue(of([]));
    api.getItemSales.and.returnValue(of([]));
    api.getInventoryMaterials.and.returnValue(of([]));
    api.getTerminals.and.returnValue(of([]));
    api.getDailyClosing.and.returnValue(of(null));
    api.getNotifications.and.returnValue(of({ status: 'ok', notifications: [], unreadCount: 0 }));

    const fixture = mount<BusinessDashboardPageComponent>(BusinessDashboardPageComponent, [
      { provide: BusinessApiService, useValue: api },
      { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
    ]);
    const kpis = () => host(fixture).querySelector('[aria-label="Key Performance Indicators"]');
    const shimmer = () => host(fixture).querySelector('.skeleton-shimmer');

    expect(shimmer()).withContext('dashboard: first load shows the skeleton').toBeTruthy();

    dashboard.subject.next({ posOrderCount: 4 });
    fixture.detectChanges();
    expect(kpis()).withContext('dashboard: the figures render once loaded').toBeTruthy();
    expect(shimmer()).toBeFalsy();

    clickRefresh(fixture);
    expect(shimmer()).withContext('dashboard: the refresh button reloads visibly').toBeTruthy();
    expect(kpis()).withContext('dashboard: the refresh button clears the stale figures').toBeFalsy();

    dashboard.subject.next({ posOrderCount: 4 });
    fixture.detectChanges();
    expect(kpis()).withContext('dashboard: the figures come back').toBeTruthy();

    // ...and the 60s poll must leave them alone.
    tick(60_000);
    fixture.detectChanges();
    expect(shimmer()).withContext('dashboard: the 60s poll must not flash the skeleton over live figures').toBeFalsy();
    expect(kpis()).withContext('dashboard: the 60s poll must leave the figures on screen').toBeTruthy();
    expect(host(fixture).querySelector('app-refresh-button button'))
      .withContext('dashboard: a background poll must not disable the refresh button')
      .toBeTruthy();

    fixture.destroy();
  }));
});
