import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminActivityLogItem } from '../../core/models/api.models';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';

export interface DisplayLogRow {
  id: number;
  at: string;
  actor: string;
  role: string;
  action: string;
  target: string;
  category: 'auth' | 'billing' | 'menu' | 'terminal' | 'platform';
}

const CATEGORY_FILTERS = ['All', 'Auth', 'Billing', 'Menu', 'Terminal', 'Platform'] as const;
const PAGE_SIZE = 10;

@Component({
  selector: 'app-activity-logs-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, PageHeaderComponent, KpiRowComponent, PaginationBarComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Activity Logs"
        subtitle="Chronological audit trail of platform and terminal-level events from PostgreSQL."
      >
        <div class="header-controls">
          <!-- "Live" was removed: the page is a snapshot fetched on load and on
               Refresh, not a stream, and the chip must not claim more freshness
               than the table has. -->
          <span class="chip-live">
            <span class="live-dot" aria-hidden="true"></span> Audit Trail
          </span>
          <button class="header-action-btn" type="button" (click)="loadLogs()">
            <span aria-hidden="true">&#8635;</span> Refresh
          </button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis()" ariaLabel="Activity summary"></app-kpi-row>

      <div class="panel logs-panel">
        <div class="filter-row" role="group" aria-label="Category filter">
          <button
            *ngFor="let category of categoryFilters; trackBy: trackByCategory"
            type="button"
            class="filter-chip"
            [class.filter-chip--active]="categoryFilter() === category"
            (click)="setFilter(category)"
          >{{ category }}</button>
        </div>

        <div class="logs-error" role="alert" *ngIf="error() as message">
          <span>{{ message }}</span>
          <button class="header-action-btn" type="button" (click)="loadLogs()">Retry</button>
        </div>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>Time</th><th>Actor</th><th>Outlet / Scope</th><th>Action</th><th>Target / Outcome</th><th>Category</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let entry of pagedEntries(); trackBy: trackById">
                <td>{{ entry.at }}</td>
                <td><strong>{{ entry.actor }}</strong></td>
                <td class="muted">{{ entry.role }}</td>
                <td>{{ entry.action }}</td>
                <td>{{ entry.target }}</td>
                <td>
                  <span class="chip"
                    [class.info]="entry.category === 'auth'"
                    [class.warn]="entry.category === 'billing'"
                    [class.neutral]="entry.category === 'menu'"
                    [class.success]="entry.category === 'terminal'"
                    [class.brand]="entry.category === 'platform'">
                    {{ entry.category }}
                  </span>
                </td>
              </tr>
              <tr *ngIf="loading()"><td colspan="6" class="muted empty-cell">Loading the audit trail…</td></tr>
              <tr *ngIf="!loading() && !pagedEntries().length"><td colspan="6" class="muted empty-cell">No events found matching this filter.</td></tr>
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
      .filter-chip--active { background: var(--brand); border-color: var(--brand); color: var(--kb-color-primary-foreground); }
      .chip-live { display: inline-flex; align-items: center; gap: var(--kb-space-2); background: var(--success-soft); color: var(--success); font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-1) var(--kb-space-3); border-radius: var(--kb-radius-full); }
      .live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); flex: none; }
      .chip { display: inline-flex; align-items: center; padding: var(--kb-space-1) var(--kb-space-2); border-radius: var(--kb-radius-full); font-size: var(--kb-text-xs); font-weight: 600; }
      .chip.brand { background: var(--brand-soft); color: var(--brand); }
      .chip.info { background: var(--kb-chart-2); color: var(--kb-color-primary-foreground); }
      .chip.warn { background: var(--warning-soft); color: var(--warning); }
      .chip.success { background: var(--success-soft); color: var(--success); }
      .chip.neutral { background: var(--surface-2); color: var(--ink-2); }
      .empty-cell { text-align: center; padding: var(--kb-space-6) 0; font-size: var(--kb-text-sm); }
      .logs-error { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-4); padding: var(--kb-space-3) var(--kb-space-4); border-radius: var(--kb-radius-lg); background: var(--danger-soft); color: var(--danger); font-size: var(--kb-text-sm); font-weight: 600; }
    `,
  ],
})
export class ActivityLogsPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);

  readonly categoryFilters = CATEGORY_FILTERS;
  readonly pageSize = PAGE_SIZE;
  readonly categoryFilter = signal<string>('All');
  readonly page = signal(1);

  readonly rawLogs = signal<AdminActivityLogItem[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /**
   * Counts come from the loaded rows only. They used to fall back to invented
   * totals (3,837 / 2,480 / 1,200 / 157) whenever the list was empty, which made
   * a failed request look like a busy audit trail. An empty list now reads 0.
   */
  readonly kpis = computed<KpiItem[]>(() => {
    const list = this.rawLogs();
    const authCount = list.filter((l) => (l.action || '').includes('AUTH') || (l.action || '').includes('LOGIN')).length;
    const syncCount = list.filter((l) => (l.action || '').includes('SYNC') || (l.action || '').includes('TERMINAL')).length;
    const permCount = list.filter((l) => (l.action || '').includes('PERMISSION')).length;

    return [
      { label: 'Total Audit Logs', value: list.length.toLocaleString('en-IN'), icon: 'history', tone: 'brand', caption: 'Rows returned by the audit trail' },
      { label: 'Terminal Syncs', value: syncCount.toLocaleString('en-IN'), icon: 'flag', tone: 'success', caption: 'Sync and terminal events' },
      { label: 'Access Grants', value: permCount.toLocaleString('en-IN'), icon: 'manage_accounts', tone: 'default', caption: 'Permission events' },
      { label: 'Auth Checkpoints', value: authCount.toLocaleString('en-IN'), icon: 'payments', tone: 'brand', caption: 'Login and auth events' },
    ];
  });

  /**
   * Rows come from the API only. Four hardcoded events used to stand in for an
   * empty or failed response, so a broken request looked like real audit data
   * ("System Daemon", "BiryaniWale Anna", "User #45"). Nothing is invented now:
   * an empty result renders the empty state.
   */
  readonly displayLogs = computed<DisplayLogRow[]>(() => {
    return this.rawLogs().map((l) => {
      const d = l.createdAt ? new Date(l.createdAt) : new Date();
      const timeStr = d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }) + ', ' +
        d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true });

      const act = (l.action || '').toUpperCase();
      let cat: DisplayLogRow['category'] = 'platform';
      if (act.includes('LOGIN') || act.includes('AUTH') || act.includes('PERMISSION')) cat = 'auth';
      else if (act.includes('BILL') || act.includes('PAYMENT')) cat = 'billing';
      else if (act.includes('MENU') || act.includes('ITEM')) cat = 'menu';
      else if (act.includes('TERMINAL') || act.includes('SYNC')) cat = 'terminal';

      const targetText = [l.targetCanonicalId, l.outcome].filter(Boolean).join(' · ') || 'System state preserved';

      return {
        id: l.id,
        at: timeStr,
        actor: l.userId || 'System Daemon',
        role: l.shopName || 'Platform Core',
        action: l.action || 'AUDIT_RECORD',
        target: targetText,
        category: cat,
      };
    });
  });

  readonly filtered = computed(() => {
    const category = this.categoryFilter().toLowerCase();
    return this.displayLogs().filter((entry) => category === 'all' || entry.category === category);
  });

  pagedEntries(): DisplayLogRow[] {
    const start = (this.page() - 1) * this.pageSize;
    return this.filtered().slice(start, start + this.pageSize);
  }

  ngOnInit(): void {
    this.loadLogs();
  }

  loadLogs(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminApi.getActivityLogs(100).subscribe({
      next: (res) => {
        this.rawLogs.set(Array.isArray(res) ? res : []);
        this.loading.set(false);
      },
      // A failed request must say so: silently keeping the previous rows (or the
      // old demo rows) is how a stale page gets mistaken for a live one.
      error: (err) => {
        this.rawLogs.set([]);
        this.error.set(err?.error?.message || 'Could not load the audit trail from the server.');
        this.loading.set(false);
      },
    });
  }

  setFilter(category: string): void {
    this.categoryFilter.set(category);
    this.page.set(1);
  }

  trackById(index: number, entry: DisplayLogRow): number { return entry.id; }
  trackByCategory(index: number, category: string): string { return category; }
}
