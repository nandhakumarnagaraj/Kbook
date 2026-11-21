import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { SalesOverviewPageComponent } from './sales-overview-page.component';
import { AdminApiService } from '../../core/services/admin-api.service';
import {
  AdminBillListItem,
  AdminBusinessListItem,
  AdminDailyTrendItem,
  AdminDashboardSummary,
} from '../../core/models/api.models';

const SUMMARY = {
  totalBusinesses: 139,
  liveBusinesses: 137,
  totalStaff: 142,
  totalOrders: 1692,
  totalRevenue: 436086.20,
  refundedOrders: 3,
  refundedAmount: 900,
};

const TRENDS: AdminDailyTrendItem[] = Array.from({ length: 30 }, (_, i) => ({
  date: `2026-11-${String(i + 1).padStart(2, '0')}`,
  revenue: 1000 + i * 100,
  orderCount: 20 + i,
  newRegistrations: 1,
}));

const BUSINESSES = [
  { restaurantId: '1', shopName: 'Riyan Biryani', orderCount: 40 },
  { restaurantId: '2', shopName: '5G Pizza', orderCount: 120 },
] as AdminBusinessListItem[];

function bill(id: number, paymentMode: string, totalAmount: number): AdminBillListItem {
  return {
    id,
    restaurantId: 1,
    shopName: 'Riyan Biryani',
    invoiceNumber: `A00000${id}`,
    totalAmount,
    paymentMode,
    paymentStatus: 'success',
    orderStatus: 'completed',
    createdAt: Date.UTC(2026, 10, 20, 18, 30, 0),
  };
}

/**
 * The page used to ship hardcoded chart bars, a fixed payment split, a
 * fabricated city ranking and sample invoices. These specs pin it to the API:
 * figures come from the response, and an empty or failed response renders as
 * empty rather than as plausible fiction.
 */
describe('SalesOverviewPageComponent — API figures only', () => {
  let fixture: ComponentFixture<SalesOverviewPageComponent>;
  let component: SalesOverviewPageComponent;
  let adminApiSpy: jasmine.SpyObj<AdminApiService>;

  async function mount(options: {
    bills?: AdminBillListItem[];
    trends?: AdminDailyTrendItem[];
    summaryFails?: boolean;
  } = {}) {
    adminApiSpy = jasmine.createSpyObj('AdminApiService', [
      'getDashboardSummary', 'getBusinesses', 'getBills', 'getDailyTrends',
    ]);
    const summary$: Observable<AdminDashboardSummary> = options.summaryFails
      ? throwError(() => ({ error: { message: 'Forbidden' } }))
      : of(SUMMARY as AdminDashboardSummary);
    adminApiSpy.getDashboardSummary.and.returnValue(summary$);
    adminApiSpy.getBusinesses.and.returnValue(of(BUSINESSES));
    adminApiSpy.getBills.and.returnValue(of(options.bills ?? []));
    adminApiSpy.getDailyTrends.and.returnValue(of(options.trends ?? []));

    await TestBed.configureTestingModule({
      imports: [SalesOverviewPageComponent],
      providers: [
        provideRouter([]),
        { provide: AdminApiService, useValue: adminApiSpy },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SalesOverviewPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('renders five KPI tiles from the summary response', async () => {
    await mount();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.kb-kpi').length).toBe(5);
    expect(el.textContent).toContain('4,36,086.2');
    expect(el.textContent).toContain('1,692');
    expect(el.textContent).toContain('3');
  });

  it('draws exactly the trend points the endpoint returned', async () => {
    await mount({ trends: TRENDS });
    expect(component.revenueChart().length).toBe(30);
    component.range.set('Last 7 Days');
    expect(component.revenueChart().length).toBe(7);
    expect(component.ordersChart().length).toBe(7);
    for (const bar of component.revenueChart()) {
      expect(bar.y).toBeGreaterThanOrEqual(0);
      expect(bar.y + bar.height).toBeLessThanOrEqual(150);
    }
  });

  it('draws nothing when no trend came back', async () => {
    await mount({ trends: [] });
    expect(component.revenueChart()).toEqual([]);
    expect(component.ordersChart()).toEqual([]);
    expect(component.axisLabels()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No daily trend returned');
  });

  it('builds the payment split from the bills and sums to exactly 100%', async () => {
    await mount({
      bills: [
        bill(1, 'cash', 100),
        bill(2, 'cash', 200),
        bill(3, 'upi', 300),
      ],
    });
    const modes = component.paymentModes();
    expect(modes.map((m) => m.name)).toEqual(['Cash', 'UPI']);
    expect(modes.reduce((sum, m) => sum + m.share, 0)).toBe(100);
    expect(modes[0].count).toBe(2);
    expect(modes[0].amount).toContain('300');
    expect(component.paymentDonut().length).toBe(2);
  });

  it('shows no payment split when no bill came back', async () => {
    await mount({ bills: [] });
    expect(component.paymentModes()).toEqual([]);
    expect(component.paymentDonut()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('No settled bills returned');
  });

  it('ranks outlets by the order count they report, without invented revenue', async () => {
    await mount();
    expect(component.topRows().map((row) => row.name)).toEqual(['5G Pizza', 'Riyan Biryani']);
    expect(fixture.nativeElement.textContent).not.toContain('AOV');
  });

  it('renders no sample invoice when the bills endpoint returns nothing', async () => {
    await mount({ bills: [] });
    expect(component.recentRows()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('No settled bills returned by the bills endpoint');
  });

  it('says a failed summary could not load instead of showing a total', async () => {
    await mount({ summaryFails: true });
    expect(component.error()).toBe('Forbidden');
    expect(component.kpis()[0].value).toBe('—');
  });
});
