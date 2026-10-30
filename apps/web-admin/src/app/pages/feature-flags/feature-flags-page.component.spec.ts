import { TestBed, ComponentFixture } from '@angular/core/testing';
import { FeatureFlagsPageComponent } from './feature-flags-page.component';

describe('FeatureFlagsPageComponent — reference editor layout', () => {
  let fixture: ComponentFixture<FeatureFlagsPageComponent>;
  let component: FeatureFlagsPageComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeatureFlagsPageComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(FeatureFlagsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders four KPI tiles and the honest sample chip', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.kb-kpi').length).toBe(4);
    expect(el.textContent).toContain('Prototype sample — no API yet');
  });

  it('lists 28 sample flags with 15 visible per page', () => {
    expect(component.flags.length).toBe(28);
    expect(component.pagedRows().length).toBe(15);
    component.page.set(2);
    expect(component.pagedRows().length).toBe(13);
  });

  it('filters by category, status and search', () => {
    component.categoryFilter = 'Payments';
    expect(component.filteredRows().every((f) => f.category === 'Payments')).toBeTrue();
    component.categoryFilter = '';
    component.statusFilter = 'off';
    expect(component.filteredRows().every((f) => !f.enabled)).toBeTrue();
    component.statusFilter = '';
    component.search = 'ai.menu';
    expect(component.filteredRows().map((f) => f.key)).toEqual(['ai.menu.import']);
  });

  it('toggles a flag and keeps the draft in sync', () => {
    const flag = component.flags[0];
    const before = flag.enabled;
    component.selectFlag(flag);
    component.draftEnabled.set(!component.draftEnabled());
    expect(component.draftEnabled()).toBe(!before);
  });

  it('maps plan selection from a flag row', () => {
    const flag = component.flags.find((f) => f.name === 'Inventory Management')!;
    component.selectFlag(flag);
    expect(component.draftPlans()['Business']).toBeTrue();
    expect(component.draftPlans()['Starter']).toBeFalse();
  });
});
