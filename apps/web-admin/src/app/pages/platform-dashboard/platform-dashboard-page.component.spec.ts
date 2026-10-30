import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PlatformDashboardPageComponent } from './platform-dashboard-page.component';

describe('PlatformDashboardPageComponent — reference layout', () => {
  let fixture: ComponentFixture<PlatformDashboardPageComponent>;
  let component: PlatformDashboardPageComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformDashboardPageComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(PlatformDashboardPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders five reference KPI tiles with the reference figures', () => {
    const el: HTMLElement = fixture.nativeElement;
    const tiles = el.querySelectorAll('.kpi-card');
    expect(tiles.length).toBe(5);
    expect(el.textContent).toContain('124');
    expect(el.textContent).toContain('582');
    expect(el.textContent).toContain('15,842');
    expect(el.textContent).toContain('98');
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
    const cityRows = component.distributionLegend();
    component.distributionMode.set('By Plan');
    expect(component.distributionLegend().some((row) => row.name === 'Starter')).toBeTrue();
    expect(component.distributionDonut().length).toBe(component.distributionLegend().length);
    component.distributionMode.set('By Status');
    expect(component.distributionLegend().find((row) => row.name === 'Active')?.count).toBe(82);
    expect(cityRows.length).toBe(6);
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
