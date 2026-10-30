import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../core/services/toast.service';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';

interface BusinessRow {
  id: number;
  name: string;
  owner: string;
  city: string;
  plan: 'Professional' | 'Starter' | 'Business' | 'Enterprise';
  status: 'Active' | 'Inactive' | 'Trial' | 'Expiring';
  users: number;
  joinedOn: string;
}

const SAMPLE_BUSINESSES: BusinessRow[] = [
  { id: 1, name: 'Spice Villa', owner: 'Ravi Kumar', city: 'Chennai', plan: 'Professional', status: 'Active', users: 5, joinedOn: '12 Oct 2024' },
  { id: 2, name: 'Dine Hub', owner: 'Suresh M', city: 'Bengaluru', plan: 'Starter', status: 'Active', users: 3, joinedOn: '05 Sep 2024' },
  { id: 3, name: 'Tasty Bites', owner: 'Priya S', city: 'Hyderabad', plan: 'Professional', status: 'Active', users: 4, joinedOn: '21 Aug 2024' },
  { id: 4, name: 'Food Junction', owner: 'Arjun R', city: 'Mumbai', plan: 'Business', status: 'Trial', users: 2, joinedOn: '10 Oct 2024' },
  { id: 5, name: 'Royal Kitchen', owner: 'Karthik V', city: 'Coimbatore', plan: 'Professional', status: 'Active', users: 6, joinedOn: '18 Jul 2024' },
  { id: 6, name: 'Cafe Corner', owner: 'Meena K', city: 'Chennai', plan: 'Starter', status: 'Inactive', users: 1, joinedOn: '02 Jun 2024' },
  { id: 7, name: 'Dosa Plaza', owner: 'Mahesh P', city: 'Bengaluru', plan: 'Professional', status: 'Active', users: 4, joinedOn: '28 May 2024' },
  { id: 8, name: 'Tiffin Box', owner: 'Lakshmi N', city: 'Madurai', plan: 'Starter', status: 'Active', users: 3, joinedOn: '16 May 2024' },
  { id: 9, name: 'The Grill House', owner: 'Vikram S', city: 'Delhi', plan: 'Business', status: 'Expiring', users: 7, joinedOn: '10 Apr 2024' },
  { id: 10, name: 'Madras Meals', owner: 'Ganesh R', city: 'Chennai', plan: 'Professional', status: 'Active', users: 5, joinedOn: '02 Mar 2024' },
  { id: 11, name: 'Biryani House', owner: 'Vikram N', city: 'Kochi', plan: 'Starter', status: 'Active', users: 2, joinedOn: '14 Feb 2024' },
  { id: 12, name: 'Urban Dine', owner: 'Neha S', city: 'Pune', plan: 'Business', status: 'Inactive', users: 4, joinedOn: '26 Jan 2024' },
];

/**
 * Admin → Businesses Management (reference A02, fused to the reference's
 * drill-down layout: five KPI tiles, status tabs with counts, a filter bar,
 * the numbered checkbox table and a right-hand detail panel with per-business
 * tabs). No businesses-admin endpoint surfaces this view yet, so rows are
 * prototype sample data labelled in-page; filtering, tab counts, selection,
 * pagination and rows-per-page are real local behaviour and mutating actions
 * are explicit prototype toasts.
 */
@Component({
  selector: 'app-businesses-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, PageHeaderComponent, KpiRowComponent, PaginationBarComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Businesses Management"
        subtitle="View, manage and support all restaurants on the platform."
      >
        <div class="header-controls">
          <span class="chip warn">Prototype sample — no API yet</span>
          <label class="header-search">
            <input
              type="search"
              name="search"
              placeholder="Search by restaurant name, owner, city, phone or plan..."
              [(ngModel)]="search"
              (ngModelChange)="page.set(1)"
              aria-label="Search businesses"
            />
          </label>
          <button class="header-action-btn" type="button" (click)="toggleFilters()">
            <span aria-hidden="true">&#9636;</span> Filters
          </button>
          <button class="header-action-btn header-action-btn--primary" type="button" (click)="addBusiness()">
            + Add New Business
          </button>
          <button class="header-action-btn" type="button" (click)="exportCsv()">
            <span aria-hidden="true">&#8595;</span> Export
          </button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis" ariaLabel="Business summary"></app-kpi-row>

      <div class="panel list-panel">
        <!-- Reference tabs with counts -->
        <div class="status-tabs" role="tablist" aria-label="Business status">
          @for (tab of tabs; track tab) {
            <button
              type="button"
              class="status-tab"
              role="tab"
              [class.active]="statusTab() === tab"
              [attr.aria-selected]="statusTab() === tab"
              (click)="selectTab(tab)">
              {{ tab }} <span class="tab-count">({{ tabCount(tab) }})</span>
            </button>
          }
        </div>

        <div class="filter-bar" [class.filter-bar--off]="!filtersOpen()">
          <label class="filter-select" aria-label="Plan filter">
            <select name="planFilter" [(ngModel)]="planFilter" (ngModelChange)="page.set(1)">
              <option value="">All Plans</option>
              @for (plan of planOptions; track plan) { <option [value]="plan">{{ plan }}</option> }
            </select>
          </label>
          <label class="filter-select" aria-label="Status filter">
            <select name="statusFilter" [(ngModel)]="statusFilter" (ngModelChange)="page.set(1)">
              <option value="">All Status</option>
              @for (status of statusOptions; track status) { <option [value]="status">{{ status }}</option> }
            </select>
          </label>
          <label class="filter-select" aria-label="City filter">
            <select name="cityFilter" [(ngModel)]="cityFilter" (ngModelChange)="page.set(1)">
              <option value="">All Cities</option>
              @for (city of cityOptions; track city) { <option [value]="city">{{ city }}</option> }
            </select>
          </label>
          <button type="button" class="join-date-btn" (click)="joinDateSort()">
            <span aria-hidden="true">&#128197;</span> Join Date
          </button>
          <button type="button" class="reset-btn" (click)="reset()">Reset</button>
        </div>

        <div class="biz-grid">
          <div class="table-wrap biz-table">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="check-col"><input type="checkbox" aria-label="Select all visible businesses" /></th>
                  <th class="idx-col">#</th>
                  <th>Business Name</th>
                  <th>Owner</th>
                  <th>City</th>
                  <th>Plan</th>
                  <th>Status</th>
                  <th>Users</th>
                  <th>Joined On</th>
                  <th class="actions-col">Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (biz of pagedRows(); track biz.id; let i = $index) {
                  <tr [class.selected-row]="selected()?.id === biz.id">
                    <td class="check-col"><input type="checkbox" [attr.aria-label]="'Select ' + biz.name" /></td>
                    <td class="idx-col">{{ (page() - 1) * pageSize() + i + 1 }}</td>
                    <td>
                      <div class="stacked">
                        <span class="logo-dot" [class]="'logo-dot--' + logoTone(biz.id)" aria-hidden="true">{{ biz.name.charAt(0) }}</span>
                        <strong>{{ biz.name }}</strong>
                      </div>
                    </td>
                    <td>{{ biz.owner }}</td>
                    <td>{{ biz.city }}</td>
                    <td>
                      <span class="plan-chip" [class]="'plan-chip--' + biz.plan.toLowerCase()">{{ biz.plan }}</span>
                    </td>
                    <td>
                      <span class="status-chip" [class]="'status-chip--' + biz.status.toLowerCase()">{{ biz.status }}</span>
                    </td>
                    <td>{{ biz.users }}</td>
                    <td>{{ biz.joinedOn }}</td>
                    <td class="actions-col">
                      <button type="button" class="kebab-btn" aria-label="Row actions" (click)="rowMenu(biz)">⋮</button>
                    </td>
                  </tr>
                }
                @if (!filteredRows().length) {
                  <tr><td colspan="10" class="muted empty-cell">No businesses match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>

          <!-- Reference right-rail detail panel -->
          <aside class="detail-panel" [class.detail-panel--empty]="!selected()">
            @if (selected(); as biz) {
              <div class="detail-head">
                <div class="detail-identity">
                  <span class="logo-dot logo-dot--big" [class]="'logo-dot--' + logoTone(biz.id)" aria-hidden="true">{{ biz.name.charAt(0) }}</span>
                  <div>
                    <strong class="detail-name">{{ biz.name }}</strong>
                    <span class="muted detail-id">ID: KB-{{ 1000 + biz.id }}</span>
                  </div>
                </div>
                <span class="status-chip" [class]="'status-chip--' + biz.status.toLowerCase()">{{ biz.status }}</span>
                <button type="button" class="detail-close" aria-label="Close details" (click)="selected.set(null)">×</button>
              </div>

              <div class="detail-tabs" role="tablist" aria-label="Business detail sections">
                @for (tab of detailTabs; track tab) {
                  <button
                    type="button"
                    role="tab"
                    class="status-tab"
                    [class.active]="detailTab() === tab"
                    [attr.aria-selected]="detailTab() === tab"
                    (click)="detailTab.set(tab)">{{ tab }}</button>
                }
              </div>

              <div class="detail-actions">
                <button type="button" class="detail-action" (click)="openDashboard(biz)"><span aria-hidden="true">&#9635;</span> Open Dashboard</button>
                <button type="button" class="detail-action" (click)="editDetails(biz)"><span aria-hidden="true">&#9998;</span> Edit Details</button>
                <button type="button" class="detail-action" (click)="manageUsers(biz)"><span aria-hidden="true">&#9786;</span> Manage Users</button>
                <button type="button" class="detail-action detail-action--more" aria-label="More actions" (click)="rowMenu(biz)">···</button>
              </div>

              <div class="detail-section">
                <header class="detail-section__head">
                  <h3>Business Information</h3>
                  <button type="button" class="view-link" (click)="toggleInfo()">View All / Edit</button>
                </header>
                <dl class="detail-fields">
                  <div><dt>Business Name</dt><dd>{{ biz.name }}</dd></div>
                  <div><dt>Owner Name</dt><dd>{{ biz.owner }}</dd></div>
                  <div><dt>City</dt><dd>{{ biz.city }}</dd></div>
                  <div><dt>Status</dt><dd>{{ biz.status }}</dd></div>
                  <div><dt>Joined On</dt><dd>{{ biz.joinedOn }}</dd></div>
                </dl>
              </div>

              <div class="detail-section">
                <header class="detail-section__head">
                  <h3>Subscription Details</h3>
                  <button type="button" class="view-link" (click)="editSubscription(biz)">Edit</button>
                </header>
                <dl class="detail-fields">
                  <div><dt>Current Plan</dt><dd><span class="plan-chip" [class]="'plan-chip--' + biz.plan.toLowerCase()">{{ biz.plan }}</span></dd></div>
                  <div><dt>Monthly Amount</dt><dd>{{ monthlyAmount(biz.plan) }}</dd></div>
                  <div><dt>Payment Status</dt><dd><span class="status-chip status-chip--active">Paid</span></dd></div>
                  <div><dt>Auto Renew</dt><dd><span class="status-chip status-chip--active">Enabled</span></dd></div>
                </dl>
              </div>
            } @else {
              <p class="muted detail-empty">Select a business row to see its details here.</p>
            }
          </aside>
        </div>

        <footer class="list-foot">
          <span class="muted count-note">
            Showing {{ showingLabel() }} of {{ filteredRows().length }} sample businesses
          </span>
          <app-pagination-bar
            [currentPage]="page()"
            [pageSize]="pageSize()"
            [totalItems]="filteredRows().length"
            (pageChange)="page.set($event)"
          ></app-pagination-bar>
          <label class="rpp" aria-label="Rows per page">
            Rows per page
            <select name="rowsPerPage" [(ngModel)]="rpp" (ngModelChange)="onRppChange()">
              <option [value]="10">10</option>
              <option [value]="25">25</option>
              <option [value]="50">50</option>
            </select>
          </label>
        </footer>
      </div>
    </div>
  `,
  styles: [
    `
      .list-panel { margin-top: var(--kb-space-5); padding: var(--kb-space-4) var(--kb-space-5) var(--kb-space-5); }
      .status-tabs { display: flex; flex-wrap: wrap; gap: var(--kb-space-2); border-bottom: 1px solid var(--line); padding-bottom: var(--kb-space-3); margin-bottom: var(--kb-space-4); }
      .status-tab {
        border: none; background: transparent; color: var(--muted);
        font-size: var(--kb-text-sm); font-weight: 650;
        padding: var(--kb-space-2) var(--kb-space-3); border-radius: var(--kb-radius-md); cursor: pointer;
      }
      .status-tab.active { background: var(--brand-soft); color: var(--brand); }
      .tab-count { color: var(--muted); font-weight: 500; }
      .filter-bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--kb-space-3); margin-bottom: var(--kb-space-4); }
      .filter-bar--off { display: none; }
      .filter-select select {
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm);
        background: var(--kb-color-input); color: var(--ink);
      }
      .join-date-btn, .reset-btn {
        border: 1px solid var(--line); background: var(--kb-color-surface); color: var(--ink);
        border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm); cursor: pointer;
      }
      .reset-btn { color: var(--brand); }
      .filter-select select:focus, .join-date-btn:focus, .reset-btn:focus { outline: 2px solid var(--brand); outline-offset: 1px; }
      .biz-grid { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); align-items: start; }
      @media (min-width: 1180px) { .biz-grid { grid-template-columns: 2fr 1fr; } }
      .biz-table :where(td, th) { white-space: nowrap; }
      .check-col { width: 36px; }
      .idx-col { width: 40px; }
      .stacked { display: flex; align-items: center; gap: var(--kb-space-2); }
      .logo-dot {
        flex: none; width: 26px; height: 26px; border-radius: var(--kb-radius-md);
        display: grid; place-items: center; font-size: var(--kb-text-xs); font-weight: 700;
      }
      .logo-dot--big { width: 38px; height: 38px; font-size: var(--kb-text-md); }
      .logo-dot--0 { background: var(--brand-soft); color: var(--brand); }
      .logo-dot--1 { background: var(--success-soft); color: var(--success); }
      .logo-dot--2 { background: var(--info-soft); color: var(--info); }
      .logo-dot--3 { background: var(--warning-soft); color: var(--warning); }
      .plan-chip { font-size: var(--kb-text-xs); font-weight: 700; }
      .plan-chip--professional { color: var(--brand); }
      .plan-chip--starter { color: var(--info); }
      .plan-chip--business { color: var(--success); }
      .plan-chip--enterprise { color: var(--accent); }
      .status-chip { font-size: var(--kb-text-xs); font-weight: 650; padding: 2px var(--kb-space-2); border-radius: var(--kb-radius-full); }
      .status-chip--active { background: var(--success-soft); color: var(--success); }
      .status-chip--inactive { background: var(--danger-soft); color: var(--danger); }
      .status-chip--trial { background: var(--warning-soft); color: var(--warning); }
      .status-chip--expiring { background: var(--warning-soft); color: var(--warning); }
      .actions-col { width: 48px; text-align: center; }
      .kebab-btn { border: none; background: transparent; color: var(--muted); font-size: var(--kb-text-lg); cursor: pointer; line-height: 1; }
      .selected-row :where(td) { background: var(--brand-soft); }
      .empty-cell { text-align: center; padding: var(--kb-space-6) 0; font-size: var(--kb-text-sm); }
      .detail-panel {
        border: 1px solid var(--line); border-radius: var(--kb-radius-lg);
        background: var(--kb-color-surface); padding: var(--kb-space-4);
        min-height: 200px;
      }
      .detail-panel--empty { display: grid; place-content: center; text-align: center; }
      .detail-head { display: flex; align-items: center; gap: var(--kb-space-2); }
      .detail-identity { display: flex; align-items: center; gap: var(--kb-space-2); flex: 1; }
      .detail-name { display: block; }
      .detail-id { font-size: var(--kb-text-xs); }
      .detail-close { border: none; background: transparent; color: var(--muted); font-size: var(--kb-text-lg); cursor: pointer; margin-left: var(--kb-space-2); }
      .detail-tabs { display: flex; gap: var(--kb-space-1); border-bottom: 1px solid var(--line); margin: var(--kb-space-3) 0; overflow-x: auto; }
      .detail-actions { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--kb-space-2); margin-bottom: var(--kb-space-4); }
      .detail-action {
        display: inline-flex; flex-direction: column; align-items: center; gap: var(--kb-space-1);
        border: 1px solid var(--line); background: var(--kb-color-surface); color: var(--ink);
        border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-1);
        font-size: var(--kb-text-xs); cursor: pointer; text-align: center;
      }
      .detail-action--more { justify-content: center; }
      .detail-action:hover { border-color: var(--brand); color: var(--brand); }
      .detail-section__head { display: flex; align-items: center; justify-content: space-between; margin: var(--kb-space-4) 0 var(--kb-space-2); }
      .detail-section__head h3 { margin: 0; font-size: var(--kb-text-sm); }
      .detail-fields { margin: 0; display: grid; gap: var(--kb-space-2); }
      .detail-fields > div { display: flex; justify-content: space-between; gap: var(--kb-space-3); font-size: var(--kb-text-sm); }
      .detail-fields dt { color: var(--muted); }
      .detail-fields dd { margin: 0; color: var(--ink); text-align: right; }
      .detail-empty { font-size: var(--kb-text-sm); }
      .list-foot { display: flex; align-items: center; gap: var(--kb-space-4); padding-top: var(--kb-space-4); }
      .count-note { font-size: var(--kb-text-sm); }
      .rpp { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); color: var(--muted); margin-left: auto; }
      .rpp select {
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm);
        background: var(--kb-color-input); color: var(--ink);
      }
      .header-search { display: flex; align-items: center; border: 1px solid var(--line); border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-3); }
      .header-search input { border: none; outline: none; background: transparent; font-size: var(--kb-text-sm); color: var(--ink); min-width: 260px; }
      .header-search:focus-within { outline: 2px solid var(--brand); outline-offset: 1px; }
    `,
  ],
})
export class BusinessesPageComponent {
  private readonly toast = inject(ToastService);

  readonly page = signal(1);
  readonly pageSize = signal(10);
  readonly rpp = 10;
  readonly statusTab = signal<'All Businesses' | 'Active' | 'Inactive' | 'Trial' | 'Expiring'>('All Businesses');
  readonly tabs = ['All Businesses', 'Active', 'Inactive', 'Trial', 'Expiring'] as const;
  readonly detailTab = signal<'Overview' | 'Subscription' | 'Users' | 'Settings'>('Overview');
  readonly detailTabs = ['Overview', 'Subscription', 'Users', 'Settings'] as const;

  readonly selected = signal<BusinessRow | null>(SAMPLE_BUSINESSES[0]);
  readonly filtersOpen = signal(false);
  readonly sortDir = signal<'asc' | 'desc' | null>(null);

  readonly kpis: KpiItem[] = [
    { label: 'Total Businesses', value: 124, icon: 'business', tone: 'brand', caption: '+12.7% vs previous 30 days' },
    { label: 'Active', value: 98, icon: 'health', tone: 'success', caption: '79.0% of total' },
    { label: 'Inactive', value: 16, icon: 'business', tone: 'danger', caption: '12.9% of total' },
    { label: 'Trial / Expiring', value: 10, icon: 'flag', tone: 'warning', caption: '8.1% of total' },
    { label: 'Monthly Recurring Revenue', value: '\u20B93,24,650', icon: 'payments', tone: 'brand', caption: '+22.1% vs previous 30 days' },
  ];

  readonly planOptions = ['Professional', 'Starter', 'Business', 'Enterprise'] as const;
  readonly statusOptions = ['Active', 'Inactive', 'Trial', 'Expiring'] as const;
  readonly cityOptions = [...new Set(SAMPLE_BUSINESSES.map((b) => b.city))];

  search = '';
  planFilter = '';
  statusFilter = '';
  cityFilter = '';

  logoTone(id: number): string {
    return String(id % 4);
  }

  monthlyAmount(plan: BusinessRow['plan']): string {
    const perPlan = { Professional: '\u20B9949', Starter: '\u20B9499', Business: '\u20B91,499', Enterprise: '\u20B92,499' } as const;
    return perPlan[plan];
  }

  selectTab(tab: this['tabs'][number]): void {
    this.statusTab.set(tab);
    this.page.set(1);
  }

  tabCount(tab: string): number {
    if (tab === 'All Businesses') return this.filteredRowsFor('').length;
    return this.filteredRowsFor(tab).length;
  }

  private filteredRowsFor(tab: string): BusinessRow[] {
    const searchLower = this.search.trim().toLowerCase();
    const tabMatcher = tab === '' || tab === 'All Businesses' ? null : tab;
    return [...SAMPLE_BUSINESSES]
      .sort((a, b) => this.sortDir() === 'desc' ? b.joinedOn.localeCompare(a.joinedOn) : a.joinedOn.localeCompare(b.joinedOn))
      .filter((biz) => {
        const matchesTab = !tabMatcher || biz.status === tabMatcher;
        const matchesPlan = !this.planFilter || biz.plan === this.planFilter;
        const matchesStatus = !this.statusFilter || biz.status === this.statusFilter;
        const matchesCity = !this.cityFilter || biz.city === this.cityFilter;
        const haystack = `${biz.name} ${biz.owner} ${biz.city} ${biz.plan}`.toLowerCase();
        return matchesTab && matchesPlan && matchesStatus && matchesCity && (!searchLower || haystack.includes(searchLower));
      });
  }

  filteredRows(): BusinessRow[] { return [...this.filteredRowsFor(this.statusTab())]; }

  pagedRows(): BusinessRow[] {
    const start = (this.page() - 1) * this.pageSize();
    return this.filteredRows().slice(start, start + this.pageSize());
  }

  showingLabel(): string {
    const len = this.filteredRows().length;
    if (!len) return '0 results';
    const end = Math.min(this.page() * this.pageSize(), len);
    return `${(this.page() - 1) * this.pageSize() + 1} - ${end}`;
  }

  toggleFilters(): void {
    this.filtersOpen.set(!this.filtersOpen());
  }

  joinDateSort(): void {
    const next = this.sortDir() === 'asc' ? 'desc' : 'asc';
    this.sortDir.set(next);
    this.toast.show(`Sample rows sorted by join date (${next === 'asc' ? 'oldest first' : 'newest first'}).`, 'info');
  }

  reset(): void {
    this.search = '';
    this.planFilter = '';
    this.statusFilter = '';
    this.cityFilter = '';
    this.sortDir.set(null);
    this.page.set(1);
  }

  onRppChange(): void {
    this.pageSize.set(Number(this.rpp) || 10);
    this.page.set(1);
  }

  addBusiness(): void {
    this.toast.show('Business creation will be wired once the businesses API exists — prototype sample data shown.', 'info');
  }

  exportCsv(): void {
    const header = 'Business Name,Owner,City,Plan,Status,Users,Joined On';
    const csv = [header, ...this.filteredRows().map((b) => `${b.name},${b.owner},${b.city},${b.plan},${b.status},${b.users},${b.joinedOn}`)].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'khanabook-businesses-sample.csv';
    link.click();
    URL.revokeObjectURL(url);
    this.toast.show('Sample business list exported as CSV.', 'success');
  }

  rowMenu(biz: BusinessRow): void {
    this.toast.show(`${biz.name} — row actions pending the businesses API (prototype).`, 'info');
  }

  openDashboard(biz: BusinessRow): void {
    this.toast.show(`${biz.name} console hand-off pending the business-switch API (prototype).`, 'info');
  }

  editDetails(biz: BusinessRow): void {
    this.toast.show(`${biz.name} editing will be wired once the businesses API exists (prototype).`, 'info');
  }

  manageUsers(biz: BusinessRow): void {
    this.toast.show(`${biz.name} user management pending the users API (prototype).`, 'info');
  }

  toggleInfo(): void {
    this.toast.show('Full profile editing arrives with the businesses API — sample fields shown (prototype).', 'info');
  }

  editSubscription(biz: BusinessRow): void {
    this.toast.show(`${biz.name} subscription changes pending the subscriptions API (prototype).`, 'info');
  }
}
