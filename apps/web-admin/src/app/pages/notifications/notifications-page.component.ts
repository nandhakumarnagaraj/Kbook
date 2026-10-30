import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';

interface BroadcastRow { id: number; title: string; audience: string; channel: string; sentAt: string; reach: number; state: 'delivered' | 'sent' | 'failed'; }

const TAB_FILTERS = ['All', 'Broadcasts', 'Terminal Alerts', 'Billing'] as const;
type BroadcastRow_ = BroadcastRow;

/**
 * Admin → Notifications (reference A10).
 *
 * Platform push uses Firebase (kb-notification.service.ts) per business, but
 * the admin broadcast centre has no backend route yet — rows are prototype
 * sample data, labelled on screen. Tab filtering and mark-as-read styling
 * are real local behaviour.
 */
@Component({
  selector: 'app-notifications-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, PageHeaderComponent, KpiRowComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Notification Center"
        subtitle="Broadcasts and delivery health for platform-level messaging."
      >
        <div class="header-controls">
          <span class="chip warn">Prototype sample — no API yet</span>
          <button class="header-action-btn header-action-btn--primary" type="button">Compose Broadcast</button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis" ariaLabel="Notification delivery summary"></app-kpi-row>

      <div class="panel notif-panel">
        <div class="filter-row" role="group" aria-label="Notification type filter">
          <button
            *ngFor="let tab of tabFilters; trackBy: trackByTab"
            type="button"
            class="filter-chip"
            [class.filter-chip--active]="tabFilter() === tab"
            (click)="tabFilter.set(tab)"
          >{{ tab }}</button>
        </div>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>Notification</th><th>Audience</th><th>Channel</th><th>Sent</th><th>Reach</th><th>Status</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of filtered(); trackBy: trackById">
                <td><strong>{{ row.title }}</strong></td>
                <td class="muted">{{ row.audience }}</td>
                <td>{{ row.channel }}</td>
                <td>{{ row.sentAt }}</td>
                <td>{{ row.reach }}</td>
                <td>
                  <span class="chip" [class.success]="row.state === 'delivered'" [class.info]="row.state === 'sent'" [class.danger]="row.state === 'failed'">{{ row.state }}</span>
                </td>
              </tr>
              <tr *ngIf="!filtered().length"><td colspan="6" class="muted empty-cell">Nothing sent in this category.</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .notif-panel { margin-top: var(--kb-space-5); padding: var(--kb-space-4) var(--kb-space-6) var(--kb-space-5); display: flex; flex-direction: column; gap: var(--kb-space-4); }
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
export class NotificationsPageComponent {
  readonly tabFilters = TAB_FILTERS;
  readonly tabFilter = signal<string>('All');

  readonly kpis: KpiItem[] = [
    { label: 'Sent (30d)', value: '18,240', icon: 'notifications', tone: 'brand', caption: 'Sample' },
    { label: 'Delivered', value: '17,902', icon: 'orders', tone: 'success', caption: '98.2% (sample)' },
    { label: 'Read Rate', value: '61%', icon: 'monitoring', tone: 'default', caption: 'Sample' },
    { label: 'Failed', value: 46, icon: 'flag', tone: 'danger', caption: 'Mostly stale tokens' },
  ];

  readonly rows: BroadcastRow[] = [
    { id: 1, title: 'Easebuzz settlement schedule change', audience: 'All owners', channel: 'Push + Email', sentAt: '27 Oct 2026, 4:00 pm', reach: 124, state: 'delivered' },
    { id: 2, title: 'Required action: renew FSSAI', audience: '3 restaurants', channel: 'Push', sentAt: '27 Oct 2026, 9:30 am', reach: 3, state: 'delivered' },
    { id: 3, title: 'Terminal KB-AND-004 offline > 24h', audience: 'Chennai Tiffins', channel: 'Push', sentAt: '26 Oct 2026, 8:10 pm', reach: 1, state: 'delivered' },
    { id: 4, title: 'October invoices ready', audience: 'All owners', channel: 'Email', sentAt: '26 Oct 2026, 6:00 am', reach: 124, state: 'delivered' },
    { id: 5, title: 'Planned maintenance window', audience: 'All users', channel: 'In-app', sentAt: '25 Oct 2026, 5:45 pm', reach: 316, state: 'sent' },
    { id: 6, title: 'GST rate change 12% → 12.5%', audience: 'All owners', channel: 'Push + Email', sentAt: '24 Oct 2026, 11:00 am', reach: 124, state: 'failed' },
  ];

  readonly filtered = computed<BroadcastRow_[]>(() => {
    const tab = this.tabFilter();
    return this.rows.filter((row) => {
      if (tab === 'All') return true;
      if (tab === 'Broadcasts') return row.audience === 'All owners' || row.audience === 'All users';
      if (tab === 'Terminal Alerts') return row.title.toLowerCase().includes('terminal');
      return row.title.toLowerCase().includes('invoice') || row.audience === 'All owners' && row.channel.includes('Email');
    });
  });

  trackById(index: number, row: BroadcastRow_): number { return row.id; }
  trackByTab(index: number): number { return index; }
}
