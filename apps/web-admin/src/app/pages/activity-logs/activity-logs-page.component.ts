import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';

interface LogRow {
  id: number;
  at: string;
  actor: string;
  role: string;
  action: string;
  target: string;
  category: 'auth' | 'billing' | 'menu' | 'terminal' | 'platform';
}

const CATEGORY_FILTERS = ['All', 'Auth', 'Billing', 'Menu', 'Terminal', 'Platform'] as const;
const PAGE_SIZE = 8;

/**
 * Admin → Activity Logs (reference A09).
 *
 * The audit-log endpoint does not exist yet (feature-flag audit is the only
 * audit trail the API exposes and it is scoped to a single flag). The rows
 * below are prototype sample data, labelled on screen; category filtering
 * and pagination are real local behaviour.
 */
@Component({
  selector: 'app-activity-logs-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, PageHeaderComponent, KpiRowComponent, PaginationBarComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Activity Logs"
        subtitle="Chronological audit trail of platform-level events."
      >
        <div class="header-controls">
          <span class="chip warn">Prototype sample — no API yet</span>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis" ariaLabel="Activity summary"></app-kpi-row>

      <div class="panel logs-panel">
        <div class="filter-row" role="group" aria-label="Category filter">
          <button
            *ngFor="let category of categoryFilters; trackBy: trackByCategory"
            type="button"
            class="filter-chip"
            [class.filter-chip--active]="categoryFilter() === category"
            (click)="categoryFilter.set(category)"
          >{{ category }}</button>
        </div>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>Time</th><th>Actor</th><th>Role</th><th>Action</th><th>Target</th><th>Category</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let entry of pagedEntries(); trackBy: trackById">
                <td>{{ entry.at }}</td>
                <td><strong>{{ entry.actor }}</strong></td>
                <td class="muted">{{ entry.role }}</td>
                <td>{{ entry.action }}</td>
                <td>{{ entry.target }}</td>
                <td><span class="chip" [class.info]="entry.category === 'auth'" [class.warn]="entry.category === 'billing'" [class.neutral]="entry.category === 'menu'" [class.success]="entry.category === 'terminal'">{{ entry.category }}</span></td>
              </tr>
              <tr *ngIf="!pagedEntries().length"><td colspan="6" class="muted empty-cell">No events in this category.</td></tr>
            </tbody>
          </table>
        </div>

        <app-pagination-bar
          [currentPage]="page()"
          [pageSize]="pageSize"
          [totalItems]="filtered().length"
          (pageChange)="page.set($event)"
        ></app-pagination-bar>
      </div>
    </div>
  `,
  styles: [
    `
      .logs-panel { margin-top: var(--kb-space-5); padding: var(--kb-space-4) var(--kb-space-6) var(--kb-space-5); display: flex; flex-direction: column; gap: var(--kb-space-4); }
      .filter-row { display: flex; gap: var(--kb-space-2); flex-wrap: wrap; }
      .filter-chip {
        border: 1px solid var(--line);
        background: var(--panel);
        border-radius: var(--kb-radius-full);
        padding: var(--kb-space-1) var(--kb-space-3);
        font-size: var(--kb-text-sm);
        color: var(--ink-2);
        cursor: pointer;
      }
      .filter-chip--active { background: var(--ink); border-color: var(--ink); color: var(--panel); }
      .empty-cell { text-align: center; padding: var(--kb-space-6) 0; font-size: var(--kb-text-sm); }
    `,
  ],
})
export class ActivityLogsPageComponent {
  readonly categoryFilters = CATEGORY_FILTERS;
  readonly pageSize = PAGE_SIZE;
  readonly categoryFilter = signal<string>('All');
  readonly page = signal(1);

  readonly kpis: KpiItem[] = [
    { label: 'Events Today', value: 238, icon: 'history', tone: 'brand', caption: 'Platform-wide (sample)' },
    { label: 'Auth Events', value: 54, icon: 'manage_accounts', tone: 'default', caption: 'Logins, role changes' },
    { label: 'Billing Events', value: 31, icon: 'payments', tone: 'success', caption: 'Plan + settlement' },
    { label: 'Critical', value: 1, icon: 'flag', tone: 'danger', caption: 'Suspend + kill-switch' },
  ];

  readonly logs: LogRow[] = [
    { id: 1, at: '28 Oct 2026, 09:41 am', actor: 'Bharat Mehta', role: 'Admin', action: 'Suspended business', target: 'Anna Mess (#4)', category: 'platform' },
    { id: 2, at: '28 Oct 2026, 09:12 am', actor: 'Bharat Mehta', role: 'Admin', action: 'Changed kill switch', target: 'easebuzz_upi_v2', category: 'platform' },
    { id: 3, at: '28 Oct 2026, 08:20 am', actor: 'NandhaKumar N', role: 'Owner', action: 'Signed in', target: 'KhanaBook Restaurant', category: 'auth' },
    { id: 4, at: '28 Oct 2026, 07:45 am', actor: 'Ravi Kumar', role: 'Shop Staff', action: 'Signed in', target: 'KhanaBook Restaurant', category: 'auth' },
    { id: 5, at: '28 Oct 2026, 07:10 am', actor: 'System', role: 'Service', action: 'Settlement armed', target: 'Checkout #4821', category: 'billing' },
    { id: 6, at: '27 Oct 2026, 10:55 am', actor: 'Priya D', role: 'Shop Staff', action: 'Menu import started', target: 'Anna Mess', category: 'menu' },
    { id: 7, at: '27 Oct 2026, 09:35 am', actor: 'Karthik G', role: 'Owner', action: 'Added staff member', target: 'Chennai Tiffins', category: 'auth' },
    { id: 8, at: '27 Oct 2026, 08:40 am', actor: 'System', role: 'Service', action: 'Settled day', target: 'Chennai Tiffins', category: 'billing' },
    { id: 9, at: '26 Oct 2026, 11:15 am', actor: 'Bharat Mehta', role: 'Admin', action: 'Activated business', target: 'Anna Mess (#4)', category: 'platform' },
    { id: 10, at: '26 Oct 2026, 10:02 am', actor: 'Ashok V', role: 'Owner', action: 'Synced terminal', target: 'Madurai Kitchen · T1', category: 'terminal' },
    { id: 11, at: '26 Oct 2026, 09:30 am', actor: 'System', role: 'Service', action: 'Renewed plan', target: 'Coimbatore Bites · Pro', category: 'billing' },
  ];

  readonly filtered = computed(() => {
    const category = this.categoryFilter().toLowerCase();
    return this.logs.filter((entry) => category === 'all' || entry.category === category);
  });

  pagedEntries(): LogRow[] {
    const start = (this.page() - 1) * this.pageSize;
    return this.filtered().slice(start, start + this.pageSize);
  }

  trackById(index: number, entry: LogRow): number { return entry.id; }
  trackByCategory(index: number): number { return index; }
}
