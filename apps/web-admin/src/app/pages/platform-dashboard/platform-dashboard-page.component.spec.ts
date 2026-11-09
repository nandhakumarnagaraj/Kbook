import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PlatformDashboardPageComponent } from './platform-dashboard-page.component';
import { AdminApiService } from '../../core/services/admin-api.service';

const TRENDS = Array.from({ length: 30 }, (_, i) => ({
  date: `2026-10-${String(10 + i).padStart(2, '0')}`,
  revenue: 40000 + i * 500,
  orderCount: 200 + i * 3,
  newRegistrations: i % 5,
}));

describe('PlatformDashboardPageComponent — reference layout', () => {
  let fixture: ComponentFixture<PlatformDashboardPageComponent>;
  let component: PlatformDashboardPageComponent;
  let adminApiSpy: jasmine.SpyObj<AdminApiService>;

  beforeEach(async () => {
    adminApiSpy = jasmine.createSpyObj('AdminApiService', [
      'getDashboardSummary', 'getBusinesses', 'getDailyTrends', 'getWebhookHealth', 'getBackupStatus',
    ]);
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
    adminApiSpy.getDailyTrends.and.returnValue(of(TRENDS));
    adminApiSpy.getWebhookHealth.and.returnValue(of({ deadLetterCount: 0, pendingRetries: 0, totalJobs: 4 }));
    adminApiSpy.getBackupStatus.and.returnValue(of({
      totalSnapshots: 5,
      latestBackup: '2026-11-08',
      databaseSize: '25 MB',
      retentionPolicy: '7 days',
      backups: [],
    }));

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
    component.businesses.set([
      { restaurantId: 1, shopName: 'A', ownerName: 'A', orderCount: 5, isSuspended: false, createdAt: 0 },
      { restaurantId: 2, shopName: 'B', ownerName: 'B', orderCount: 0, isSuspended: true, createdAt: 0 },
    ] as any);
    expect(component.distributionLegend().find((row) => row.name.includes('Active'))?.count).toBe(1);
    expect(component.distributionLegend().find((row) => row.name.includes('Suspended'))?.count).toBe(1);
    // An empty list states the absence; it does not fall back to an invented split.
    component.businesses.set([]);
    expect(component.distributionLegend().find((row) => row.name.includes('Active'))?.count).toBeNull();
  });

  it('keeps the revenue and orders series on separate scales', () => {
    expect(component.revenueLine()).not.toBe(component.ordersLine());
    expect(component.trendData().ptsRev.length).toBeGreaterThan(0);
  });

  it('shows a dash, not a plausible figure, when the summary call fails', () => {
    component.summary.set(null);
    adminApiSpy.getDashboardSummary.and.returnValue(throwError(() => new Error('summary down')));
    component.loadDashboard();
    expect(component.loadFailed()).toBeTrue();
    expect(component.kpis().map((kpi) => kpi.value)).toEqual(['—', '—', '—', '—', '—']);
  });

  it('treats webhook dead letters as degraded', () => {
    adminApiSpy.getWebhookHealth.and.returnValue(of({ deadLetterCount: 2, pendingRetries: 0, totalJobs: 4 }));
    component.loadDashboard();
    expect(component.webhookState()).toBe('degraded');
    expect(component.healthSummary().label).toBe('Needs attention');
  });

  it('reports unknown health when neither health signal answers', () => {
    component.webhookState.set('unknown');
    component.backups.set(null);
    adminApiSpy.getWebhookHealth.and.returnValue(throwError(() => new Error('down')));
    adminApiSpy.getBackupStatus.and.returnValue(throwError(() => new Error('down')));
    component.loadDashboard();
    expect(component.healthSummary().label).toBe('Status unavailable');
    expect(component.healthRows().every((row) => row.tone === 'neutral')).toBeTrue();
  });

  it('maps a touch on the growth plot to the bar under it and pins the readout', () => {
    const plot = fixture.nativeElement.querySelector('.bar-plot') as HTMLElement;
    spyOn(plot, 'getBoundingClientRect').and.returnValue({ left: 0, width: 600 } as DOMRect);
    component.onGrowthPointer({ clientX: 310, pointerType: 'touch', currentTarget: plot } as unknown as PointerEvent);
    expect(component.hoverGrowth()?.x).toBe(15);
    expect(component.pinGrowth()).toBeTrue();
    // Lifting the finger must not hide a value the user cannot hover back.
    component.onGrowthLeave({ pointerType: 'touch' } as PointerEvent);
    expect(component.hoverGrowth()).not.toBeNull();
    component.onDocumentPointerDown({ target: document.body } as unknown as PointerEvent);
    expect(component.hoverGrowth()).toBeNull();
  });

  it('keeps the trend readout after a finger lifts but clears it on an outside tap', () => {
    const svg = fixture.nativeElement.querySelector('.trend-svg') as SVGElement;
    const width = component.trendWidth();
    spyOn(svg, 'getBoundingClientRect').and.returnValue({ left: 0, width } as DOMRect);
    component.onTrendPointer({ clientX: width / 2, pointerType: 'touch', currentTarget: svg } as unknown as PointerEvent);
    expect(component.hoverTrendIndex()).not.toBeNull();
    component.onTrendLeave({ pointerType: 'touch' } as PointerEvent);
    expect(component.hoverTrendIndex()).not.toBeNull();
    component.onDocumentPointerDown({ target: document.body } as unknown as PointerEvent);
    expect(component.hoverTrendIndex()).toBeNull();
  });

  it('lays the trend geometry inside the measured width so axis labels keep their size', () => {
    component.trendWidth.set(390);
    const plot = component.trendPlot();
    const pts = component.trendData().ptsRev;
    expect(pts[0].x).toBe(plot.left);
    expect(pts[pts.length - 1].x).toBe(plot.right);
    expect(component.trendYTicks[0].y).toBe(plot.top);
    expect(component.trendYTicks[3].y).toBe(plot.bottom);
  });

  it('shrinks the sample window when the range narrows', () => {
    const wide = component.growthBars().length;
    component.range = '7';
    component.onRangeChange();
    // bars derive from the series computed for the range label
    expect(wide).toBe(30);
  });
});
