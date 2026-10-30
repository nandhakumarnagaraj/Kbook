import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/icon.component';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';

interface SubscriptionRow {
  id: number;
  business: string;
  city: string;
  owner: string;
  plan: 'Professional' | 'Business' | 'Starter' | 'Enterprise';
  planTone: 'brand' | 'default';
  status: 'Active' | 'Trial' | 'Expired';
  startDate: string;
  nextBilling: string | null;
  amount: string;
  method: string;
}

const STATUS_TABS = ['All', 'Active', 'Trial', 'Expired'] as const;

/**
 * Admin → Subscriptions (reference A04, fused chrome pass).
 * The reference renders a subscription *list* — status tabs with counts, a
 * dropdown filter bar, a dated table with payment method and footer
 * pagination — not the plan matrix first shipped here. No subscription API
 * exists yet (admin-api.service.ts), so the rows are prototype sample data,
 * labelled on screen; tabs, filters, search, pagination and the CSV export
 * of the visible sample are real local behaviour.
 */
@Component({
  selector: 'app-subscriptions-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, PageHeaderComponent, KpiRowComponent, PaginationBarComponent, IconComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Subscriptions"
        subtitle="Manage all restaurant subscriptions and billing."
      >
        <div class="header-controls">
          <span class="chip warn">Prototype sample — no API yet</span>
          <button class="header-action-btn" type="button" (click)="exportCsv()">
            <kb-icon name="download" [size]="14"></kb-icon>
            Export
          </button>
          <button class="header-action-btn header-action-btn--primary" type="button" (click)="addSubscription()">
            + Add Subscription
          </button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis" ariaLabel="Subscription summary"></app-kpi-row>

      <div class="panel list-panel">
        <!-- Status tabs with counts, exactly as the reference renders them -->
        <div class="tab-strip" role="tablist" aria-label="Subscription status filter">
          <button
            *ngFor="let tab of statusTabs; trackBy: trackByTab"
            type="button"
            role="tab"
            class="tab-btn"
            [class.tab-btn--active]="statusTab() === tab"
            [attr.aria-selected]="statusTab() === tab"
            (click)="statusTab.set(tab)"
          >
            {{ tab }} ({{ tabCount(tab) }})
          </button>
        </div>

        <!-- Dropdown filter bar from the reference -->
        <div class="filter-bar">
          <label class="filter-select">
            <select name="planFilter" [(ngModel)]="planFilter" (ngModelChange)="page.set(1)">
              <option value="">All Plans</option>
              @for (plan of planOptions; track plan) { <option [value]="plan">{{ plan }}</option> }
            </select>
          </label>
          <label class="filter-select">
            <select name="statusSelect" [(ngModel)]="statusSelect" (ngModelChange)="syncTabs($event)">
              <option value="">All Status</option>
              @for (status of statusOptions; track status) { <option [value]="status">{{ status }}</option> }
            </select>
          </label>
          <label class="filter-select">
            <select name="cycleFilter" [(ngModel)]="cycleFilter">
              <option value="">All Billing Cycles</option>
              <option value="Monthly">Monthly</option>
              <option value="Annual">Annual</option>
            </select>
          </label>
          <label class="filter-select">
            <select name="cityFilter" [(ngModel)]="cityFilter" (ngModelChange)="page.set(1)">
              <option value="">All Cities</option>
              @for (city of cityOptions; track city) { <option [value]="city">{{ city }}</option> }
            </select>
          </label>
          <label class="filter-search">
            <kb-icon name="monitoring" [size]="14"></kb-icon>
            <input
              type="search"
              name="search"
              placeholder="Search by business name, owner, or plan..."
              [(ngModel)]="search"
              (ngModelChange)="page.set(1)"
              aria-label="Search subscriptions"
            />
          </label>
          <button type="button" class="clear-filters" (click)="clearFilters()">
            <span aria-hidden="true">≡</span> Clear Filters
          </button>
        </div>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th class="idx-col">#</th>
                <th>Business Name</th>
                <th>Owner</th>
                <th>Plan</th>
                <th>Status</th>
                <th>Start Date</th>
                <th>Next Billing</th>
                <th>Amount</th>
                <th>Payment Method</th>
                <th class="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (row of pagedRows(); track row.id; let i = $index) {
                <tr>
                  <td class="idx-col">{{ (page() - 1) * pageSize() + i + 1 }}</td>
                  <td>
                    <div class="stacked">
                      <strong>{{ row.business }}</strong>
                      <span class="muted place">{{ row.city }}</span>
                    </div>
                  </td>
                  <td>{{ row.owner }}</td>
                  <td><span class="plan-name">{{ row.plan }}</span></td>
                  <td>
                    <span class="chip" [class.success]="row.status === 'Active'" [class.warn]="row.status === 'Trial'" [class.danger]="row.status === 'Expired'">
                      {{ row.status }}
                    </span>
                  </td>
                  <td>{{ row.startDate }}</td>
                  <td>{{ row.nextBilling || '—' }}</td>
                  <td>{{ row.amount }}</td>
                  <td><strong class="method">{{ row.method }}</strong></td>
                  <td class="actions-col">
                    <button type="button" class="kebab-btn" aria-label="Row actions" (click)="rowMenu(row)">⋮</button>
                  </td>
                </tr>
              }
              @if (!filteredRows().length) {
                <tr><td colspan="10" class="muted empty-cell">No subscriptions match these filters.</td></tr>
              }
            </tbody>
          </table>
        </div>

        <footer class="list-foot">
          <span class="muted count-note">
            Showing {{ rangeLabel() }} of {{ filteredRows().length }} subscriptions (sample)
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
      .header-controls { display: flex; align-items: center; gap: var(--kb-space-3); }
      .header-action-btn kb-icon { vertical-align: -2px; }
      .list-panel { margin-top: var(--kb-space-5); padding: 0 0 var(--kb-space-5); }
      .tab-strip { display: flex; gap: var(--kb-space-2); border-bottom: 1px solid var(--line); padding: var(--kb-space-2) var(--kb-space-5) 0; }
      .tab-btn {
        border: none;
        background: transparent;
        padding: var(--kb-space-2) var(--kb-space-4);
        font-size: var(--kb-text-md);
        color: var(--muted);
        cursor: pointer;
        border-bottom: 2px solid transparent;
      }
      .tab-btn--active {
        color: var(--brand);
        font-weight: 650;
        border-bottom-color: var(--brand);
      }
      .filter-bar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--kb-space-3);
        padding: var(--kb-space-4) var(--kb-space-5);
      }
      .filter-select select,
      .rpp select {
        border: 1px solid var(--line);
        border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3);
        font-size: var(--kb-text-sm);
        background: var(--kb-color-input);
        color: var(--ink);
      }
      .filter-search {
        flex: 1;
        min-width: 200px;
        display: flex;
        align-items: center;
        gap: var(--kb-space-2);
        border: 1px solid var(--line);
        border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3);
        color: var(--muted);
      }
      .filter-search input {
        flex: 1;
        border: none;
        outline: none;
        background: transparent;
        font-size: var(--kb-text-sm);
        color: var(--ink);
      }
      .clear-filters {
        display: inline-flex;
        align-items: center;
        gap: var(--kb-space-1);
        border: none;
        background: transparent;
        color: var(--brand);
        font-size: var(--kb-text-sm);
        cursor: pointer;
      }
      .idx-col { width: 48px; text-align: center; color: var(--muted); }
      .stacked { display: flex; flex-direction: column; gap: 0; }
      .place { font-size: var(--kb-text-xs); }
      .plan-name { color: var(--brand); font-weight: 650; }
      .method { font-size: var(--kb-text-sm); }
      .actions-col { width: 56px; text-align: center; }
      .kebab-btn {
        border: none;
        background: transparent;
        color: var(--muted);
        font-size: var(--kb-text-lg);
        cursor: pointer;
        line-height: 1;
      }
      .empty-cell { text-align: center; padding: var(--kb-space-6) 0; font-size: var(--kb-text-sm); }
      .list-foot { display: flex; align-items: center; gap: var(--kb-space-4); padding: var(--kb-space-4) var(--kb-space-5) 0; }
      .count-note { font-size: var(--kb-text-sm); }
      .rpp { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); color: var(--muted); margin-left: auto; }
    `,
  ],
})
export class SubscriptionsPageComponent {
  private readonly toast = inject(ToastService);

  readonly statusTabs = STATUS_TABS;
  readonly statusTab = signal<string>('All');
  readonly page = signal(1);
  readonly pageSize = signal(10);
  readonly rpp = 10;

  readonly kpis: KpiItem[] = [
    { label: 'Total Subscriptions', value: 98, icon: 'staff', tone: 'brand', caption: '+12.3% this quarter' },
    { label: 'Active', value: 82, icon: 'dashboard', tone: 'success', caption: '83.7% of the base' },
    { label: 'Trial', value: 10, icon: 'history', tone: 'warning', caption: '10.2% converting period' },
    { label: 'Expired', value: 6, icon: 'flag', tone: 'danger', caption: '6.1% awaiting renewal' },
  ];

  readonly planOptions = ['Starter', 'Business', 'Professional', 'Enterprise'] as const;
  readonly statusOptions = ['Active', 'Trial', 'Expired'] as const;
  readonly cycleFilter = '';
  readonly cityOptions = ['Chennai', 'Bengaluru', 'Coimbatore', 'Madurai', 'Kochi'];

  search = '';
  planFilter = '';
  statusSelect = '';
  cityFilter = '';

  readonly rows: SubscriptionRow[] = [
    { id: 1, business: 'Spice Villa', city: 'Chennai, TN', owner: 'Ravi Kumar', plan: 'Professional', planTone: 'brand', status: 'Active', startDate: '12 Oct 2026', nextBilling: '12 Nov 2026', amount: '₹2,499', method: 'Razorpay' },
    { id: 2, business: 'Dine Hub', city: 'Bengaluru, KA', owner: 'Suresh M', plan: 'Starter', planTone: 'default', status: 'Active', startDate: '05 Sep 2026', nextBilling: '05 Nov 2026', amount: '₹999', method: 'Razorpay' },
    { id: 3, business: 'Tasty Bites', city: 'Coimbatore, TN', owner: 'Priya S', plan: 'Business', planTone: 'brand', status: 'Trial', startDate: '21 Aug 2026', nextBilling: '21 Nov 2026', amount: '₹1,999', method: 'Stripe' },
    { id: 4, business: 'Food Junction', city: 'Madurai, TN', owner: 'Arjun R', plan: 'Professional', planTone: 'brand', status: 'Active', startDate: '10 Oct 2026', nextBilling: '10 Nov 2026', amount: '₹2,499', method: 'Razorpay' },
    { id: 5, business: 'Royal Kitchen', city: 'Trichy, TN', owner: 'Karthik V', plan: 'Enterprise', planTone: 'brand', status: 'Expired', startDate: '18 Jul 2026', nextBilling: null, amount: '₹2,499', method: '—' },
    { id: 6, business: 'Curry Corner', city: 'Salem, TN', owner: 'Divya L', plan: 'Starter', planTone: 'default', status: 'Active', startDate: '14 Sep 2026', nextBilling: '14 Nov 2026', amount: '₹999', method: 'Razorpay' },
    { id: 7, business: 'Flavors Point', city: 'Hyderabad, TS', owner: 'Mohammed A', plan: 'Business', planTone: 'brand', status: 'Active', startDate: '02 Oct 2026', nextBilling: '02 Nov 2026', amount: '₹1,999', method: 'Stripe' },
    { id: 8, business: 'Biryani House', city: 'Kochi, KL', owner: 'Vikram N', plan: 'Professional', planTone: 'brand', status: 'Active', startDate: '08 Oct 2026', nextBilling: '08 Nov 2026', amount: '₹2,499', method: 'Razorpay' },
    { id: 9, business: 'Taste of India', city: 'Mysuru, KA', owner: 'Anjali P', plan: 'Starter', planTone: 'default', status: 'Trial', startDate: '19 Oct 2026', nextBilling: '19 Nov 2026', amount: '₹999', method: 'Razorpay' },
    { id: 10, business: 'Urban Dine', city: 'Pune, MH', owner: 'Neha S', plan: 'Business', planTone: 'brand', status: 'Active', startDate: '01 Sep 2026', nextBilling: '01 Nov 2026', amount: '₹1,999', method: 'PayU' },
  ];

  readonly filteredRows = computed(() => {
    const tab = this.statusTab();
    const searchLower = this.search.trim().toLowerCase();
    const plan = this.planFilter;
    const city = this.cityFilter;
    return this.rows.filter((row) => {
      const matchesTab = tab === 'All' || row.status === tab;
      const matchesPlan = !plan || row.plan === plan;
      const matchesCity = !city || row.city.startsWith(city);
      const matchesSearch = !searchLower || `${row.business} ${row.owner} ${row.plan}`.toLowerCase().includes(searchLower);
      return matchesTab && matchesPlan && matchesCity && matchesSearch;
    });
  });

  pagedRows(): SubscriptionRow[] {
    const start = (this.page() - 1) * this.pageSize();
    return this.filteredRows().slice(start, start + this.pageSize());
  }

  rangeLabel(): string {
    const total = this.filteredRows().length;
    if (!total) return '0';
    const start = (this.page() - 1) * this.pageSize() + 1;
    const end = Math.min(total, this.page() * this.pageSize());
    return `${start}-${end}`;
  }

  tabCount(tab: string): number {
    if (tab === 'All') return this.rows.length;
    return this.rows.filter((row) => row.status === tab).length;
  }

  syncTabs(status: string): void {
    this.statusTab.set(status || 'All');
    this.page.set(1);
  }

  clearFilters(): void {
    this.search = '';
    this.planFilter = '';
    this.statusSelect = '';
    this.cityFilter = '';
    this.statusTab.set('All');
    this.page.set(1);
  }

  onRppChange(): void {
    this.pageSize.set(Number(this.rpp) || 10);
    this.page.set(1);
  }

  addSubscription(): void {
    this.toast.show('Creating subscriptions will be wired once the API exists — prototype sample data shown.', 'info');
  }

  rowMenu(row: SubscriptionRow): void {
    this.toast.show(`#B${row.id} ${row.business} — row actions pending the subscriptions API (prototype).`, 'info');
  }

  exportCsv(): void {
    // genuinely exports the visible sample list, clearly named as sample data.
    const header = 'Business,City,Owner,Plan,Status,Start Date,Next Billing,Amount,Method';
    const lines = this.filteredRows().map((row) =>
      [row.business, row.city, row.owner, row.plan, row.status, row.startDate, row.nextBilling ?? '-', row.amount, row.method].join(',')
    );
    const blob = new Blob([`KhanaBook prototype sample subscriptions\n${header}\n${lines.join('\n')}`], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'khanabook-subscriptions-sample.csv';
    anchor.click();
    URL.revokeObjectURL(url);
    this.toast.show('Exported the current sample list as CSV.', 'success');
  }

  trackByTab(index: number): number { return index; }
}
