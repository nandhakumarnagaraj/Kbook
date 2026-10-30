import { TestBed, ComponentFixture } from '@angular/core/testing';
import { BusinessesPageComponent } from './businesses-page.component';

describe('BusinessesPageComponent — reference drill-down layout', () => {
  let fixture: ComponentFixture<BusinessesPageComponent>;
  let component: BusinessesPageComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BusinessesPageComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(BusinessesPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders five reference KPI tiles and the honest sample chip', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.kb-kpi').length).toBe(5);
    expect(el.textContent).toContain('Prototype sample — no API yet');
  });

  it('matches tab counts to the sample rows', () => {
    expect(component.tabCount('All Businesses')).toBe(12);
    expect(component.tabCount('Active')).toBe(8);
    expect(component.tabCount('Inactive')).toBe(2);
    expect(component.tabCount('Trial')).toBe(1);
    expect(component.tabCount('Expiring')).toBe(1);
  });

  it('filters rows by search, plan and city', () => {
    component.search = 'spice';
    expect(component.filteredRows().map((b) => b.name)).toEqual(['Spice Villa']);
    component.search = '';
    component.planFilter = 'Professional';
    expect(component.filteredRows().every((b) => b.plan === 'Professional')).toBeTrue();
  });

  it('clears every filter on reset', () => {
    component.search = 'spice';
    component.planFilter = 'Professional';
    component.cityFilter = 'Chennai';
    component.reset();
    expect(component.filteredRows().length).toBe(12);
  });

  it('selects a business into the detail rail and closes it', () => {
    const first = component.pagedRows()[0];
    component.selected.set(first);
    expect(component.selected()?.name).toBe(first.name);
    component.selected.set(null);
    expect(component.selected()).toBeNull();
  });

  it('labels monthly amounts per plan', () => {
    expect(component.monthlyAmount('Professional')).toContain('949');
    expect(component.monthlyAmount('Business')).toContain('\u20B9');
  });
});
