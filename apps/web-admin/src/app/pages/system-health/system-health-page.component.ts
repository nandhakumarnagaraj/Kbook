import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { catchError, forkJoin, of } from 'rxjs';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBackupStatus } from '../../core/models/api.models';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';

interface ServiceRow {
  name: string;
  signal: string;
  state: 'operational' | 'degraded' | 'unknown';
  detail: string;
}

/**
 * Admin → System Health (reference A08).
 *
 * This page used to print an unconditional "All systems operational" banner, five
 * invented KPIs (99.98% uptime, 142ms p95, 10/10 pool) and six service rows with
 * made-up latency and uptime figures. None of it was measured. It now renders only
 * what `/admin/webhooks/health` and `/admin/backups` actually answer, and says so
 * when they answer nothing.
 */
@Component({
  selector: 'app-system-health-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, PageHeaderComponent, KpiRowComponent, RefreshButtonComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="System Health"
        subtitle="Measured from the health endpoints this console can reach."
      >
        <div class="header-controls">
          <span class="chip-live"><span class="live-dot" aria-hidden="true"></span> Measured signals</span>
          <app-refresh-button (refresh)="refresh()" [loading]="refreshing()" ariaLabel="Refresh system health" />
        </div>
      </app-page-header>

      <div class="panel status-banner" role="status" [class]="'panel status-banner status-banner--' + banner().tone">
        <span class="status-dot" aria-hidden="true"></span>
        <div>
          <strong>{{ banner().title }}</strong>
          <span class="muted banner-note">{{ banner().note }}</span>
        </div>
      </div>

      <app-kpi-row [items]="kpis()" ariaLabel="Measured platform health" />

      <div class="panel panel-pad">
        <div class="section-head">
          <h3>Measured services</h3>
          <span class="muted">Only signals this console can read</span>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>Service</th><th>Signal</th><th>Status</th><th>Detail</th></tr>
            </thead>
            <tbody>
              @for (row of services(); track row.name) {
                <tr>
                  <td><strong>{{ row.name }}</strong></td>
                  <td class="muted">{{ row.signal }}</td>
                  <td>
                    <span
                      class="chip"
                      [class.success]="row.state === 'operational'"
                      [class.warn]="row.state === 'degraded'"
                      [class.neutral]="row.state === 'unknown'">
                      {{ row.state }}
                    </span>
                  </td>
                  <td class="muted">{{ row.detail }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <p class="scope-note muted">
        CPU, memory, disk and per-request latency are collected by the production
        monitoring stack. This console has no endpoint for them, so it does not show
        them — a green gauge nobody measured would be worse than no gauge.
      </p>
    </div>
  `,
  styles: [
    `
      .status-banner { margin-top: var(--kb-space-5); padding: var(--kb-space-4) var(--kb-space-5); display: flex; align-items: center; gap: var(--kb-space-3); }
      .chip-live { display: inline-flex; align-items: center; gap: var(--kb-space-2); background: var(--success-soft); color: var(--success); font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-1) var(--kb-space-3); border-radius: var(--kb-radius-full); }
      .live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); flex: none; }
      .status-dot { width: 12px; height: 12px; border-radius: var(--kb-radius-full); background: var(--muted); flex: none; }
      .status-banner--ok .status-dot { background: var(--success); }
      .status-banner--warn { border-color: var(--warning); }
      .status-banner--warn .status-dot { background: var(--warning); }
      .status-banner--unknown { border-color: var(--line); }
      .banner-note { margin-left: var(--kb-space-2); font-size: var(--kb-text-sm); }
      .panel-pad { margin-top: var(--kb-space-4); padding: var(--kb-space-5) var(--kb-space-6); }
      .scope-note { margin-top: var(--kb-space-4); font-size: var(--kb-text-xs); line-height: 1.5; max-width: 72ch; }
      .data-table th, .data-table td { vertical-align: top; }
      .chip.neutral { background: var(--kb-color-bg-app); color: var(--muted); }
    `,
  ],
})
export class SystemHealthPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);

  readonly refreshing = signal(false);
  readonly webhookState = signal<'ok' | 'degraded' | 'unknown'>('unknown');
  readonly backup = signal<AdminBackupStatus | null>(null);
  /** null = not queried yet; true = at least one health endpoint answered. */
  readonly apiReachable = signal<boolean | null>(null);

  readonly banner = computed(() => {
    const hooks = this.webhookState();
    const backup = this.backup();
    if (hooks === 'unknown' && !backup) {
      return {
        tone: 'unknown',
        title: 'Health data unavailable',
        note: 'Neither the webhook nor the backup endpoint answered.',
      };
    }
    if (hooks === 'degraded') {
      return {
        tone: 'warn',
        title: 'Service degradation reported',
        note: 'Payment webhooks are reporting degraded performance.',
      };
    }
    if (hooks === 'unknown') {
      // One endpoint answering does not stand in for the other.
      return {
        tone: 'warn',
        title: 'Webhook health unavailable',
        note: backup
          ? `${backup.totalSnapshots} backup snapshots on record · webhook health did not answer`
          : 'The webhook health endpoint did not answer.',
      };
    }
    return {
      tone: 'ok',
      title: 'Measured services operational',
      note: backup
        ? `Payment webhooks responding · ${backup.totalSnapshots} backup snapshots on record`
        : 'Payment webhooks responding',
    };
  });

  readonly kpis = computed<KpiItem[]>(() => {
    const hooks = this.webhookState();
    const backup = this.backup();
    const webhookValue = hooks === 'ok' ? 'Operational' : hooks === 'degraded' ? 'Degraded' : 'Unknown';

    return [
      {
        label: 'Payment Webhooks',
        value: webhookValue,
        icon: 'health',
        tone: hooks === 'ok' ? 'success' : hooks === 'degraded' ? 'danger' : 'default',
        caption: 'Easebuzz callbacks',
      },
      {
        label: 'Snapshots',
        value: backup ? backup.totalSnapshots : '—',
        icon: 'flag',
        tone: backup ? 'success' : 'default',
        caption: 'database backups',
      },
      {
        label: 'Latest Backup',
        value: this.latestBackupLabel(backup),
        icon: 'monitoring',
        caption: 'most recent snapshot',
      },
      {
        label: 'Database Size',
        value: backup?.databaseSize || '—',
        icon: 'dashboard',
        caption: 'current footprint',
      },
      {
        label: 'Retention',
        value: backup?.retentionPolicy || '—',
        icon: 'notifications',
        caption: 'backup window',
      },
    ];
  });

  readonly services = computed<ServiceRow[]>(() => {
    const hooks = this.webhookState();
    const backup = this.backup();
    const reached = this.apiReachable();

    return [
      {
        name: 'Payment Webhooks',
        signal: 'Easebuzz callback delivery',
        state: hooks === 'ok' ? 'operational' : hooks === 'degraded' ? 'degraded' : 'unknown',
        detail:
          hooks === 'ok'
            ? 'Health endpoint reports OK'
            : hooks === 'degraded'
              ? 'Health endpoint reports degradation'
              : 'No response from the webhook health endpoint',
      },
      {
        name: 'Database Backups',
        signal: 'Automated snapshot status',
        state: backup ? 'operational' : 'unknown',
        detail: backup
          ? `${backup.totalSnapshots} snapshots · latest ${this.latestBackupLabel(backup)}`
          : 'No response from the backup status endpoint',
      },
      {
        name: 'Admin API',
        signal: 'This console’s data source',
        state: reached ? 'operational' : 'unknown',
        detail:
          reached === null
            ? 'Not queried yet'
            : reached
              ? 'Answered the health queries'
              : 'Did not answer the health queries',
      },
    ];
  });

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.refreshing.set(true);
    forkJoin({
      hooks: this.adminApi.getWebhookHealth().pipe(catchError(() => of(null))),
      backups: this.adminApi.getBackupStatus().pipe(catchError(() => of(null))),
    }).subscribe(({ hooks, backups }) => {
      this.apiReachable.set(!!hooks || !!backups);

      if (hooks) {
        const payload = hooks as {
          status?: string; health?: string; deadLetterCount?: number;
        };
        const state = String(payload.status ?? payload.health ?? '').toLowerCase();
        const deadLettered = Number(payload.deadLetterCount) > 0;
        this.webhookState.set(
          deadLettered || state.includes('degrad') || state.includes('fail') ? 'degraded' : 'ok',
        );
      } else {
        this.webhookState.set('unknown');
      }

      if (backups) this.backup.set(backups);
      this.refreshing.set(false);
    });
  }

  /** Accepts an ISO date or an already-formatted string; never invents one. */
  latestBackupLabel(backup: AdminBackupStatus | null): string {
    if (!backup?.latestBackup) return '—';
    const parsed = new Date(backup.latestBackup);
    return isNaN(parsed.getTime())
      ? backup.latestBackup
      : parsed.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
