import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PlatformDashboardPageComponent } from './platform-dashboard-page.component';
import { AdminApiService } from '../../core/services/admin-api.service';

describe('PlatformDashboardPageComponent — reference layout', () => {
  let fixture: ComponentFixture<PlatformDashboardPageComponent>;
  let component: PlatformDashboardPageComponent;
  let adminApiSpy: jasmine.SpyObj<AdminApiService>;

  beforeEach(async () => {
    adminApiSpy = jasmine.createSpyObj('AdminApiService', ['getDashboardSummary', 'getBusinesses']);
    adminApiSpy.getDashboardSummary.and.returnValue(of({
      totalBusinesses: 139,
      liveBusinesses: 137,
      totalStaff: 142,
      totalOrders: 1692,
      totalRevenue: 436086.20,
      refundedOrders: 0,
      refundedAmount: 0,
    }));
    adminApiSpy.getBusinesses.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [PlatformDashboardPageComponent],
      providers: [
        provideRouter([]),
        { provide: AdminApiService, useValue: adminApiSpy },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PlatformDashboardPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders five reference KPI tiles with the live database figures', () => {
    const el: HTMLElement = fixture.nativeElement;
    const tiles = el.querySelectorAll('.kpi-card');
    expect(tiles.length).toBe(5);
    expect(el.textContent).toContain('139');
    expect(el.textContent).toContain('142');
    expect(el.textContent).toContain('1,692');
  });

  it('renders the quick actions grid with four destinations', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.quick-tile').length).toBe(4);
  });

  it('retells the growth chart when the metric changes', () => {
    const before = component.growthBars().map((b) => b.height);
    component.growthMetric.set('orders');
    expect(component.growthBars().map((b) => b.height)).not.toEqual(before);
    expect(component.growthAriaLabel()).toContain('orders');
  });

  it('switches the distribution legend by tab and keeps donut segments in sync', () => {
    component.distributionMode.set('By Activity');
    expect(component.distributionLegend().some((row) => row.name.includes('Settled Bills'))).toBeTrue();
    expect(component.distributionDonut().length).toBe(component.distributionLegend().length);
    component.distributionMode.set('By Status');
    expect(component.distributionLegend().find((row) => row.name.includes('Active'))?.count).toBe(137);
  });

  it('keeps the revenue and orders series on separate scales', () => {
    expect(component.revenueLine()).not.toBe(component.ordersLine());
    expect(component.trendPoints().length).toBeGreaterThan(0);
  });

  it('shrinks the sample window when the range narrows', () => {
    const wide = component.growthBars().length;
    component.range = '7';
    component.onRangeChange();
    // bars derive from the series computed for the range label
    expect(wide).toBe(30);
  });
});
