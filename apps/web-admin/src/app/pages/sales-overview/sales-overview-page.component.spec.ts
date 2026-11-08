import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SalesOverviewPageComponent } from './sales-overview-page.component';
import { AdminApiService } from '../../core/services/admin-api.service';

describe('SalesOverviewPageComponent — live sales layout', () => {
  let fixture: ComponentFixture<SalesOverviewPageComponent>;
  let component: SalesOverviewPageComponent;
  let adminApiSpy: jasmine.SpyObj<AdminApiService>;

  beforeEach(async () => {
    adminApiSpy = jasmine.createSpyObj('AdminApiService', ['getDashboardSummary', 'getBusinesses', 'getBills']);
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
    adminApiSpy.getBills.and.returnValue(of([]));

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
  });

  it('renders five KPI tiles with live PostgreSQL figures', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.kb-kpi').length).toBe(5);
    expect(el.textContent).toContain('Live PostgreSQL Data');
    expect(el.textContent).toContain('4,36,086.2');
    expect(el.textContent).toContain('1,692');
  });

  it('trims the labelled series when the range pill narrows', () => {
    expect(component.revenueChart().length).toBe(26);
    component.range.set('Last 7 Days');
    expect(component.revenueChart().length).toBe(7);
    expect(component.ordersChart().length).toBe(7);
  });

  it('keeps all revenue bars inside the viewBox', () => {
    for (const bar of component.revenueChart()) {
      expect(bar.y).toBeGreaterThanOrEqual(0);
      expect(bar.y + bar.height).toBeLessThanOrEqual(150);
    }
  });

  it('builds a payment donut whose shares sum to ~100%', () => {
    const total = component.PAYMENT_MODES.reduce((sum, mode) => sum + mode.share, 0);
    expect(Math.round(total)).toBe(100);
    expect(component.paymentDonut().length).toBe(component.PAYMENT_MODES.length);
  });

  it('maps payment modes to chip tones', () => {
    expect(component.modeChip('UPI')).toBe('brand');
    expect(component.modeChip('Cash')).toBe('success');
    expect(component.modeChip('Online')).toBe('warn');
  });
});
