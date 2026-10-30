import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { IconComponent } from '../../shared/icon.component';

interface ReportRow {
  name: string;
  cadence: string;
  window: string;
  state: 'Ready' | 'Generating' | 'Failed';
}

/**
 * Admin → Platform Reports (reference A07).
 *
 * No report-registry endpoint exists yet; the catalogue is prototype sample
 * data labelled on screen. The Export buttons are visibly wired but inert
 * until a real export endpoint lands.
 */
@Component({
  selector: 'app-platform-reports-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, PageHeaderComponent, KpiRowComponent, IconComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Platform Reports"
        subtitle="Recurring operational and financial reports across every restaurant."
      >
        <div class="header-controls">
          <span class="chip warn">Prototype sample — no API yet</span>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis" ariaLabel="Report summary"></app-kpi-row>

      <div class="report-grid" role="list">
        <article class="panel report-card" *ngFor="let report of reports; trackBy: trackByName" role="listitem">
          <div class="report-icon" [class]="'report-icon--' + report.state.toLowerCase()">
            <kb-icon name="reports" [size]="20"></kb-icon>
          </div>
          <div class="report-body">
            <h4>{{ report.name }}</h4>
            <p class="muted">{{ report.cadence }} · {{ report.window }}</p>
          </div>
          <div class="report-actions">
            <span class="chip" [class.success]="report.state === 'Ready'" [class.warn]="report.state === 'Generating'" [class.danger]="report.state === 'Failed'">{{ report.state }}</span>
            <button
              type="button"
              class="header-action-btn"
              [disabled]="report.state !== 'Ready'"
              (click)="download(report)"
            >Export</button>
          </div>
        </article>
      </div>
    </div>
  `,
  styles: [
    `
      .report-grid {
        margin-top: var(--kb-space-5);
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
        gap: var(--kb-space-4);
      }
      .report-card { display: flex; align-items: center; gap: var(--kb-space-4); padding: var(--kb-space-4) var(--kb-space-5); }
      .report-icon {
        flex: none;
        display: grid;
        place-items: center;
        width: 44px;
        height: 44px;
        border-radius: var(--kb-radius-xl);
        background: var(--brand-soft);
        color: var(--brand);
      }
      .report-icon--failed { background: var(--danger-soft); color: var(--danger); }
      .report-body { flex: 1; min-width: 0; }
      .report-body h4 { margin: 0; font-size: var(--kb-text-md); font-weight: 650; }
      .report-body p { margin: var(--kb-space-1) 0 0; font-size: var(--kb-text-xs); }
      .report-actions { display: flex; flex-direction: column; align-items: flex-end; gap: var(--kb-space-2); }
    `,
  ],
})
export class PlatformReportsPageComponent {
  readonly kpis: KpiItem[] = [
    { label: 'Reports This Cycle', value: 46, icon: 'reports', tone: 'brand', caption: 'Generated automatically' },
    { label: 'Scheduled Jobs', value: 12, icon: 'dashboard', tone: 'default', caption: 'Daily/weekly cadences' },
    { label: 'Exports Downloaded', value: 178, icon: 'orders', tone: 'success', caption: 'Last 30 days' },
    { label: 'Failed Runs', value: 2, icon: 'flag', tone: 'danger', caption: 'Retry queued' },
  ];

  readonly reports: ReportRow[] = [
    { name: 'Platform Sales Report', cadence: 'Weekly', window: 'Last 7 days', state: 'Ready' },
    { name: 'Subscription Revenue', cadence: 'Monthly', window: 'October 2026', state: 'Ready' },
    { name: 'Restaurant Ledger', cadence: 'Daily', window: '27 Oct 2026', state: 'Generating' },
    { name: 'Feature Flag Audit', cadence: 'Weekly', window: 'W43 2026', state: 'Ready' },
    { name: 'Payment Settlements', cadence: 'Daily', window: '27 Oct 2026', state: 'Failed' },
    { name: 'Terminal Utilisation', cadence: 'Weekly', window: 'W43 2026', state: 'Ready' },
  ];

  trackByName(index: number, report: ReportRow): string { return report.name; }

  download(report: ReportRow): void {
    // Inert until a real export endpoint exists; disabled everywhere the
    // report is not in a Ready state so the affordance never lies.
  }
}
