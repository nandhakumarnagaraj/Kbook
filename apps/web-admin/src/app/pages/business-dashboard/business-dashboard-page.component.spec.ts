import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { BusinessDashboardPageComponent } from './business-dashboard-page.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';

describe('BusinessDashboardPageComponent — states and resilience', () => {
  let fixture: ComponentFixture<BusinessDashboardPageComponent>;
  let component: BusinessDashboardPageComponent;
  let api: jasmine.SpyObj<BusinessApiService>;
  let auth: jasmine.SpyObj<AuthService>;
  let toast: jasmine.SpyObj<ToastService>;
  let router: jasmine.SpyObj<Router>;

  const mockDashboardData: any = {
    restaurantId: 'rest-123',
    websiteEnabled: true,
    totalStaff: 5,
    totalMenuItems: 40,
    shopName: 'Spice Route Kitchen',
    todayRevenue: 42850,
    totalRevenue: 150000,
    posOrderCount: 120,
    pendingPosPayments: 10
  };

  const mockTrends: any = {
    yesterdayRevenue: 38000,
    todayRevenue: 42850,
    last7Days: [],
    thisWeekRevenue: 42850,
    lastWeekRevenue: 38000
  };

  const mockOrders: any = {
    content: [
      {
        orderId: 1042,
        orderCode: '1042',
        orderStatus: 'PREPARING',
        orderType: 'DINE_IN',
        customerName: 'Rahul',
        totalAmount: 1240,
        createdAt: Date.now()
      }
    ],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 20
  };

  beforeEach(() => {
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getDashboard',
      'getDashboardTrends',
      'getOrdersPaginated',
      'getHourlySales',
      'getItemSales',
      'getInventoryMaterials',
      'getTerminals',
      'getDailyClosing',
      'getNotifications',
      'getOrderDetail'
    ]);

    auth = jasmine.createSpyObj<AuthService>('AuthService', ['session']);
    auth.session.and.returnValue({ userName: 'Spice Route Kitchen', restaurantId: 'rest-123' } as any);

    toast = jasmine.createSpyObj<ToastService>('ToastService', ['show', 'error', 'success', 'info', 'dismiss']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    // Default mock returns for supplementary endpoints
    api.getDashboardTrends.and.returnValue(of(mockTrends));
    api.getOrdersPaginated.and.returnValue(of(mockOrders));
    api.getHourlySales.and.returnValue(of([]));
    api.getItemSales.and.returnValue(of([]));
    api.getInventoryMaterials.and.returnValue(of([]));
    api.getTerminals.and.returnValue(of([]));
    api.getDailyClosing.and.returnValue(of(null));
    api.getNotifications.and.returnValue(of({ status: 'ok', notifications: [], unreadCount: 0 }));

    TestBed.configureTestingModule({
      imports: [BusinessDashboardPageComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
        { provide: Router, useValue: router }
      ]
    });
  });

  afterEach(() => {
    if (fixture) {
      fixture.destroy();
    }
  });

  it('renders skeleton with accessibility attributes during initial load', () => {
    // Keep getDashboard pending
    const dashboardSubject = new Subject<any>();
    api.getDashboard.and.returnValue(dashboardSubject.asObservable());

    fixture = TestBed.createComponent(BusinessDashboardPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    const skeleton = fixture.nativeElement.querySelector('[aria-busy="true"]');
    expect(skeleton).toBeTruthy();
    expect(skeleton.getAttribute('aria-live')).toBe('polite');

    const hiddenStatus = skeleton.querySelector('[role="status"]');
    expect(hiddenStatus?.textContent).toContain('Loading dashboard data');
  });

  it('renders full dashboard when initial load succeeds', () => {
    api.getDashboard.and.returnValue(of(mockDashboardData));

    fixture = TestBed.createComponent(BusinessDashboardPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.dashboard()).toBeTruthy();
    expect(component.dashboard()?.shopName).toBe('Spice Route Kitchen');

    const kpiRow = fixture.nativeElement.querySelector('app-kpi-row .kb-kpi-row');
    expect(kpiRow).withContext('the dashboard renders the shared KPI row').toBeTruthy();
    expect(kpiRow.querySelectorAll('.kb-kpi').length).toBe(4);
    expect(fixture.nativeElement.querySelector('.state-panel')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('.stale-banner')).toBeFalsy();
  });

  it('renders state-panel error with alert role when initial load fails', () => {
    api.getDashboard.and.returnValue(throwError(() => ({ status: 500 })));

    fixture = TestBed.createComponent(BusinessDashboardPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.dashboard()).toBeNull();
    expect(component.dashboardError()).toBe(
      'The server could not complete this request (500). Your data is unchanged.'
    );

    const errorPanel = fixture.nativeElement.querySelector('.state-panel');
    expect(errorPanel).toBeTruthy();
    expect(errorPanel.getAttribute('role')).toBe('alert');
    expect(errorPanel.getAttribute('aria-live')).toBe('assertive');

    const retryBtn = errorPanel.querySelector('.btn-retry');
    expect(retryBtn).toBeTruthy();
    expect(retryBtn.textContent.trim()).toBe('Try again');
  });

  it('retains loaded data and renders stale-banner when a subsequent refresh fails', () => {
    api.getDashboard.and.returnValue(of(mockDashboardData));

    fixture = TestBed.createComponent(BusinessDashboardPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.dashboard()).toBeTruthy();

    // Now configure next refresh call to fail with network error
    api.getDashboard.and.returnValue(throwError(() => ({ status: 0 })));

    component.refresh();
    fixture.detectChanges();

    // Data should NOT be wiped from memory or the DOM
    expect(component.dashboard()).toBeTruthy();
    expect(component.dashboard()?.shopName).toBe('Spice Route Kitchen');
    expect(fixture.nativeElement.querySelector('.kb-kpi-row')).toBeTruthy();

    // Stale banner should appear at top
    expect(component.staleError()).toBe('No internet connection. Check your network and try again.');
    const banner = fixture.nativeElement.querySelector('.stale-banner');
    expect(banner).toBeTruthy();
    expect(banner.getAttribute('role')).toBe('alert');
    expect(banner.textContent).toContain("Couldn't refresh.");
    expect(banner.textContent).toContain('No internet connection');

    // Error toast should have fired
    expect(toast.error).toHaveBeenCalledWith('No internet connection. Check your network and try again.');
  });

  it('clears stale-banner and updates figures when refresh recovers', () => {
    api.getDashboard.and.returnValue(of(mockDashboardData));

    fixture = TestBed.createComponent(BusinessDashboardPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    // Trigger failure
    api.getDashboard.and.returnValue(throwError(() => ({ status: 500 })));
    component.refresh();
    fixture.detectChanges();

    expect(component.staleError()).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.stale-banner')).toBeTruthy();

    // Recover with fresh figures
    const freshData: any = { ...mockDashboardData, todayRevenue: 55000 };
    api.getDashboard.and.returnValue(of(freshData));
    api.getDashboardTrends.and.returnValue(of({ ...mockTrends, todayRevenue: 55000 }));
    component.refresh();
    fixture.detectChanges();

    expect(component.staleError()).toBe('');
    expect(fixture.nativeElement.querySelector('.stale-banner')).toBeFalsy();
    expect(component.dashboard()?.todayRevenueValue).toBe(55000);
  });

  describe('clarity fixes', () => {
    it('shows no revenue delta badge while today has no sales', () => {
      api.getDashboard.and.returnValue(of({ ...mockDashboardData, todayRevenue: 0 }));
      api.getDashboardTrends.and.returnValue(of({ ...mockTrends, yesterdayRevenue: 5000, todayRevenue: 0 }));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      // A "↓100% vs yesterday" badge on ₹0.00 is noise — no delta, just the
      // plain "no sales" state on the revenue tile.
      expect(component.revenueTrend()).toBeNull();
      const revenueBadge = fixture.nativeElement.querySelector('.kb-kpi--hero .kb-kpi__badge');
      expect(revenueBadge).toBeTruthy();
      expect(revenueBadge.textContent).toContain('No sales yet');
      expect(revenueBadge.textContent).not.toContain('100%');
    });

    it('reports the revenue delta once the day has sales', () => {
      api.getDashboard.and.returnValue(of(mockDashboardData)); // todayRevenue 42850
      api.getDashboardTrends.and.returnValue(of(mockTrends)); // yesterday 38000

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const trend = component.revenueTrend();
      expect(trend).not.toBeNull();
      expect(trend!.tone).toBe('up');
      expect(trend!.label).toContain('% vs yest.');
    });

    it('labels the settlement tile as an all-time count', () => {
      api.getDashboard.and.returnValue(of(mockDashboardData));
      // The caption only exists beside figures, and those come from the closing
      // payload — so this state is the one where the label has to be honest.
      api.getDailyClosing.and.returnValue(of({ netRevenue: 10000, expectedCash: 4000 } as any));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const tender = component.dashboardKpis().find(item => item.label === 'Tender Breakdown');
      expect(tender).withContext('the tender tile is part of the shared row').toBeTruthy();
      expect(tender!.caption).toContain('all-time');
      expect(fixture.nativeElement.textContent).toContain('Counts all-time POS invoices');
    });

    it('withholds the tender split, and the all-time caveat, until closing data loads', () => {
      api.getDashboard.and.returnValue(of(mockDashboardData));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const tender = component.dashboardKpis().find(item => item.label === 'Tender Breakdown');
      expect(tender!.value).toBe('—');
      expect(tender!.segments).toBeUndefined();
      expect(tender!.caption).toContain("today's closing figures");
      expect(fixture.nativeElement.querySelectorAll('.kb-kpi__segments').length).toBe(0);
    });

    it('renders the tender split as a bar plus text, never colour alone', () => {
      api.getDashboard.and.returnValue(of(mockDashboardData));
      api.getDailyClosing.and.returnValue(of({ netRevenue: 10000, expectedCash: 4000 } as any));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const heroSplit = fixture.nativeElement.querySelector('.kb-kpi--hero .kb-kpi__segments');
      expect(heroSplit).withContext('the revenue tile carries the tender split').toBeTruthy();

      const legend = heroSplit.querySelectorAll('.kb-kpi__segment');
      expect(legend.length).toBe(2);
      expect(legend[0].textContent).toContain('UPI');
      expect(legend[0].textContent).toContain('60%');
      expect(legend[1].textContent).toContain('Cash');
      expect(legend[1].textContent).toContain('40%');

      // The bar is a picture; the legend above is the same numbers as text.
      const bar = heroSplit.querySelector('.kb-kpi__segment-bar');
      expect(bar.getAttribute('aria-hidden')).toBe('true');
      expect(bar.querySelectorAll('.kb-kpi__segment-fill').length).toBe(2);
    });

    it('routes a tile foot action to the page that owns that metric', () => {
      api.getDashboard.and.returnValue(of(mockDashboardData));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const actions = Array.from(
        fixture.nativeElement.querySelectorAll('.kb-kpi__action')
      ) as HTMLButtonElement[];
      expect(actions.map(a => a.textContent!.trim().replace('→', '').trim()))
        .toEqual(['Open bills', 'Live KOT stream', 'Terminal fleet']);

      const fleet = actions.find(a => a.textContent!.includes('Terminal fleet'))!;
      fleet.click();
      expect(router.navigate).toHaveBeenCalledWith(['/business/terminals']);

      const kot = actions.find(a => a.textContent!.includes('Live KOT stream'))!;
      kot.click();
      expect(router.navigate).toHaveBeenCalledWith(
        ['/business/orders'],
        { queryParams: { tab: 'live' } }
      );
    });

    it('shows a freshness stamp after the first successful load', () => {
      api.getDashboard.and.returnValue(of(mockDashboardData));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const chip = fixture.nativeElement.querySelector('.freshness-chip');
      expect(chip).toBeTruthy();
      expect(chip.textContent).toContain('Updated');
    });

    it('counts only terminals the server reports as ACTIVE and flags recovery ones', () => {
      api.getDashboard.and.returnValue(of(mockDashboardData));
      api.getTerminals.and.returnValue(of([
        { id: 1, terminalSeries: 'POS-1', status: 'ACTIVE', isActive: true },
        { id: 2, terminalSeries: 'POS-2', status: 'RECOVERY_REQUIRED', isActive: true },
        { id: 3, terminalSeries: 'POS-3', status: 'INACTIVE', isActive: false },
        { id: 4, terminalSeries: 'POS-4', status: 'REPLACED', isActive: false }
      ] as any));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      // RECOVERY_REQUIRED is not healthy even though isActive is still true.
      expect(component.onlineTerminalsCount()).toBe(1);
      expect(component.totalTerminalsCount()).toBe(4);
      expect(component.fleetAttentionCount()).toBe(1);

      const statuses = component.terminals().map(t => component.terminalStatusOf(t));
      expect(statuses[0].label).toBe('Active');
      expect(statuses[1].label).toBe('Needs recovery');
      expect(statuses[1].tone).toBe('attention');
      expect(statuses[2].label).toBe('Deactivated');
      expect(statuses[3].label).toBe('Replaced');
    });
  });

  /**
   * §14 of the design spec: every figure on the dashboard comes from an
   * authoritative source. The page used to print a literal `Avg Ticket ₹680`,
   * a hardcoded 16-table floor map, five invented invoices and `: 8` as the
   * KOT count fallback. These specs pin the API-derived versions so the
   * placeholders cannot come back.
   */
  describe('figures are derived from the API, never hardcoded', () => {
    it('computes the average ticket from today\'s revenue and invoice count', () => {
      api.getDashboard.and.returnValue(of(mockDashboardData)); // ₹42,850 across 120 invoices

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.avgTicket()).toBeCloseTo(357.08, 2);
      const revenueCaption = fixture.nativeElement.querySelector('.kb-kpi--hero .kb-kpi__caption');
      expect(revenueCaption.textContent).toContain('Avg ticket');
      // The ticket and the invoice count live in the tile's one caption line.
      expect(revenueCaption.textContent).toContain('120 invoices');
      expect(fixture.nativeElement.textContent).not.toContain('₹680');
    });

    it('says there are no invoices instead of showing ₹0 or a sample ticket', () => {
      api.getDashboard.and.returnValue(of({ ...mockDashboardData, todayRevenue: 0, posOrderCount: 0 }));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.avgTicket()).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('No invoices yet today');
    });

    it('does not fall back to a sample KOT count when no order is open', () => {
      api.getOrdersPaginated.and.returnValue(of({ content: [], totalElements: 0 } as any));
      api.getDashboard.and.returnValue(of(mockDashboardData));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.activeOrderCount()).toBe(0);
      expect(fixture.nativeElement.querySelector('.station-tags-pill')).toBeFalsy();
      expect(fixture.nativeElement.textContent).not.toContain('Station:');
    });

    it('builds the floor chips from live open bills and invents no vacant tables', () => {
      api.getOrdersPaginated.and.returnValue(of({
        content: [
          { orderId: 7, orderCode: '1047', orderStatus: 'Draft', orderType: 'DINE_IN', tableNumber: 'T-7', totalAmount: 500, paymentStatus: 'Pending', createdAt: Date.now() },
          { orderId: 8, orderCode: '1048', orderStatus: 'Draft', orderType: 'TAKEAWAY', totalAmount: 250, paymentStatus: 'Pending', createdAt: Date.now() }
        ],
        totalElements: 2
      } as any));
      api.getDashboard.and.returnValue(of(mockDashboardData));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.floorTables().map(t => t.tableNumber)).toEqual(['T-7', 'Takeaway']);
      expect(component.seatedTableCount()).toBe(1);
      expect(component.takeawayFloorCount()).toBe(1);
      // A table the API never mentioned must not be rendered as "VACANT".
      expect(fixture.nativeElement.textContent).not.toContain('VACANT');
      expect(fixture.nativeElement.textContent).not.toContain('Pax');
    });

    it('gives the hourly bar chart a keyboard-reachable data table', () => {
      api.getHourlySales.and.returnValue(of([
        { hour: 12, itemsSold: 5 },
        { hour: 13, itemsSold: 9 }
      ] as any));
      api.getDashboard.and.returnValue(of(mockDashboardData));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-controls="hourly-chart-table"]');
      expect(toggle).withContext('the chart needs a real control, not a hover tip').toBeTruthy();
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      expect(fixture.nativeElement.querySelector('#hourly-chart-table')).toBeFalsy();
      // The bars themselves are decorative: their numbers live in the table.
      expect(fixture.nativeElement.querySelector('.hourly-bars-track').getAttribute('aria-hidden')).toBe('true');

      toggle.click();
      fixture.detectChanges();

      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      const table: HTMLTableElement = fixture.nativeElement.querySelector('#hourly-chart-table');
      expect(table).toBeTruthy();
      expect(table.querySelector('caption')!.textContent).toContain('Items sold');
      expect(table.querySelectorAll('tbody tr').length).toBe(13);
      expect(table.textContent).toContain('12p');
      expect(table.textContent).toContain('Busiest hour');
    });

    it('gives the floor grid a keyboard-reachable data table', () => {
      api.getOrdersPaginated.and.returnValue(of({
        content: [
          { orderId: 9, orderCode: '1049', orderStatus: 'Draft', orderType: 'DINE_IN', tableNumber: 'T-2', totalAmount: 640, paymentStatus: 'Pending', createdAt: Date.now() }
        ],
        totalElements: 1
      } as any));
      api.getDashboard.and.returnValue(of(mockDashboardData));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-controls="floor-table"]');
      expect(toggle).toBeTruthy();
      toggle.click();
      fixture.detectChanges();

      const table: HTMLTableElement = fixture.nativeElement.querySelector('#floor-table');
      expect(table).toBeTruthy();
      expect(table.textContent).toContain('T-2');
      expect(table.textContent).toContain('Preparing');
      expect(table.querySelectorAll('tbody tr').length).toBe(1);
    });

    it('renders the ledger from recentOrders, not from a sample invoice list', () => {
      api.getDashboard.and.returnValue(of({
        ...mockDashboardData,
        recentOrders: [
          { orderId: 1, orderCode: '9001', invoiceNumber: 'INV-9001', orderStatus: 'Paid', paymentStatus: 'Paid', paymentMethod: 'UPI', orderType: 'DINE_IN', tableNumber: 4, totalAmount: 1290, sourceType: 'POS', createdAt: Date.now() }
        ]
      }));

      fixture = TestBed.createComponent(BusinessDashboardPageComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const feed = component.recentLedgerFeed();
      expect(feed.length).toBe(1);
      expect(feed[0].invoiceNo).toBe('INV-9001');
      expect(feed[0].tableAndChannel).toBe('Table 4 · Dine In');
      expect(fixture.nativeElement.textContent).not.toContain('INV-1092');
    });
  });
});
