import { TestBed, ComponentFixture } from '@angular/core/testing';
import { SupportTicketsPageComponent } from './support-tickets-page.component';

describe('SupportTicketsPageComponent — reference list layout', () => {
  let fixture: ComponentFixture<SupportTicketsPageComponent>;
  let component: SupportTicketsPageComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SupportTicketsPageComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(SupportTicketsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders four KPI tiles and the honest sample chip', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.kb-kpi').length).toBe(4);
    expect(el.textContent).toContain('Prototype sample — no API yet');
  });

  it('matches tab counts to the sample rows', () => {
    expect(component.tabCount('All')).toBe(12);
    expect(component.tabCount('Open')).toBe(3);
    expect(component.tabCount('In Progress')).toBe(3);
    expect(component.tabCount('Resolved')).toBe(4);
    expect(component.tabCount('Closed')).toBe(2);
  });

  it('filters by tab plus search', () => {
    component.selectTab('Open');
    expect(component.filteredRows().every((t) => t.status === 'Open')).toBeTrue();
    component.search = 'printer';
    expect(component.filteredRows().map((t) => t.id)).toEqual(['TK-1041']);
  });

  it('clears tab and filters together', () => {
    component.selectTab('Open');
    component.search = 'printer';
    component.clearFilters();
    expect(component.statusTab()).toBe('All');
    expect(component.filteredRows().length).toBe(12);
  });

  it('paginates the table', () => {
    component.selectTab('All');
    expect(component.pagedRows().length).toBe(10);
    component.page.set(2);
    expect(component.pagedRows().length).toBe(2);
  });
});
