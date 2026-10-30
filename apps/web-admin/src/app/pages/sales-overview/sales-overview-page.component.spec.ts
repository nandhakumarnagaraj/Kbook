import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SalesOverviewPageComponent } from './sales-overview-page.component';

describe('SalesOverviewPageComponent — reference charts layout', () => {
  let fixture: ComponentFixture<SalesOverviewPageComponent>;
  let component: SalesOverviewPageComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalesOverviewPageComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(SalesOverviewPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders five KPI tiles with reference figures and the honest sample chip', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.kb-kpi').length).toBe(5);
    expect(el.textContent).toContain('Prototype sample — no API yet');
    expect(el.textContent).toContain('3,24,650');
    expect(el.textContent).toContain('15,842');
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
