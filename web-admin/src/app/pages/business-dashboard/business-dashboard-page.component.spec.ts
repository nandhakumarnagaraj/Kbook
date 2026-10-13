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

    const bentoGrid = fixture.nativeElement.querySelector('.bento-kpi-grid');
    expect(bentoGrid).toBeTruthy();
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
    expect(fixture.nativeElement.querySelector('.bento-kpi-grid')).toBeTruthy();

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
});
