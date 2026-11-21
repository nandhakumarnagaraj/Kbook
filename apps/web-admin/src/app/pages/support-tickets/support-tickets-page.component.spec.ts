import { TestBed, ComponentFixture } from '@angular/core/testing';
import { SupportTicketsPageComponent } from './support-tickets-page.component';

const STORAGE_KEY = 'kbook_admin_support_tickets';

/** 12 local tickets: 3 open, 3 in progress, 4 resolved, 2 closed. */
const SEED = [
  { id: 'TK-1041', subject: 'Printer not working', restaurant: 'Spice Villa', category: 'Technical', priority: 'High', status: 'Open', createdOn: '28 Oct 2026, 09:12 am' },
  { id: 'TK-1040', subject: 'GST configuration', restaurant: 'Dosa Plaza', category: 'Billing', priority: 'Medium', status: 'In Progress', createdOn: '27 Oct 2026, 05:15 pm' },
  { id: 'TK-1039', subject: 'App pairing issue', restaurant: 'The Grill House', category: 'Onboarding', priority: 'High', status: 'Open', createdOn: '27 Oct 2026, 11:30 am' },
  { id: 'TK-1038', subject: 'Payment issue', restaurant: 'Tasty Bites', category: 'Billing', priority: 'Medium', status: 'In Progress', createdOn: '27 Oct 2026, 07:00 am' },
  { id: 'TK-1037', subject: 'Add new staff', restaurant: 'Cafe Corner', category: 'Onboarding', priority: 'Low', status: 'Resolved', createdOn: '26 Oct 2026, 04:38 pm' },
  { id: 'TK-1036', subject: 'Refund not reflecting', restaurant: 'Royal Kitchen', category: 'Billing', priority: 'High', status: 'Open', createdOn: '26 Oct 2026, 10:00 am' },
  { id: 'TK-1035', subject: 'Menu categories', restaurant: 'Food Junction', category: 'Feature Request', priority: 'Low', status: 'Resolved', createdOn: '25 Oct 2026, 08:45 am' },
  { id: 'TK-1034', subject: 'Slow dashboard load', restaurant: 'Dine Hub', category: 'Technical', priority: 'Medium', status: 'Resolved', createdOn: '24 Oct 2026, 06:20 pm' },
  { id: 'TK-1033', subject: 'SMS notifications', restaurant: 'Madras Meals', category: 'Feature Request', priority: 'Low', status: 'Closed', createdOn: '23 Oct 2026, 02:12 pm' },
  { id: 'TK-1032', subject: 'Terminal offline', restaurant: 'Spice Villa', category: 'Technical', priority: 'High', status: 'In Progress', createdOn: '22 Oct 2026, 09:05 am' },
  { id: 'TK-1031', subject: 'Invoice download', restaurant: 'Biryani House', category: 'Billing', priority: 'Low', status: 'Closed', createdOn: '21 Oct 2026, 12:30 pm' },
  { id: 'TK-1030', subject: 'Inventory export', restaurant: 'Urban Dine', category: 'Feature Request', priority: 'Medium', status: 'Resolved', createdOn: '20 Oct 2026, 03:00 pm' },
];

/**
 * The page has no support endpoint and used to ship twelve seeded tickets plus a
 * "139 outlets, 100% operational" tile. These specs pin the honest behaviour: an
 * empty browser shows an empty register, and only locally stored tickets appear.
 */
describe('SupportTicketsPageComponent — local register only', () => {
  let fixture: ComponentFixture<SupportTicketsPageComponent>;
  let component: SupportTicketsPageComponent;

  async function mount(seed: unknown[] | null) {
    localStorage.clear();
    if (seed) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(seed));
    }

    await TestBed.configureTestingModule({
      imports: [SupportTicketsPageComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(SupportTicketsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => localStorage.clear());

  it('shows an empty register and three KPI tiles on a fresh browser', async () => {
    await mount(null);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.kb-kpi').length).toBe(3);
    expect(component.tickets().length).toBe(0);
    expect(el.textContent).toContain('No support tickets logged');
    expect(el.textContent).not.toContain('139');
  });

  it('lists only the tickets stored in this browser', async () => {
    await mount(SEED);
    expect(component.tabCount('All')).toBe(12);
    expect(component.tabCount('Open')).toBe(3);
    expect(component.tabCount('In Progress')).toBe(3);
    expect(component.tabCount('Resolved')).toBe(4);
    expect(component.tabCount('Closed')).toBe(2);
  });

  it('filters by tab plus search, then clears both', async () => {
    await mount(SEED);
    component.selectTab('Open');
    expect(component.filteredRows().every((t) => t.status === 'Open')).toBeTrue();
    component.search = 'printer';
    expect(component.filteredRows().map((t) => t.id)).toEqual(['TK-1041']);

    component.clearFilters();
    expect(component.statusTab()).toBe('All');
    expect(component.filteredRows().length).toBe(12);
  });

  it('paginates the table', async () => {
    await mount(SEED);
    component.selectTab('All');
    expect(component.pagedRows().length).toBe(10);
    component.page.set(2);
    expect(component.pagedRows().length).toBe(2);
  });
});
