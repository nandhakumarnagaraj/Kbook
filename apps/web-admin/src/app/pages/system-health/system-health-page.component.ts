import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';

interface ServiceRow { name: string; kind: string; state: 'operational' | 'degraded' | 'down'; latency: string; uptime: string; }

/**
 * Admin → System Health (reference A08).
 *
 * The only live signal available is the webhook health endpoint; service
 * uptime/latency for the rest of the platform has no backend source yet, so
 * the service table is prototype sample data and labelled as such.
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
        subtitle="Live status of the services behind KhanaBook."
      >
        <div class="header-controls">
          <span class="chip warn">Prototype sample — services table</span>
          <app-refresh-button (refresh)="refresh()" [loading]="refreshing()" ariaLabel="Refresh system health" />
        </div>
      </app-page-header>

      <div class="panel status-banner status-banner--ok" role="status">
        <span class="status-dot" aria-hidden="true"></span>
        <div>
          <strong>All systems operational</strong>
          <span class="muted banner-note" *ngIf="webhookState() === 'ok'">Payment webhooks responding</span>
          <span class="muted banner-note" *ngIf="webhookState() === 'degraded'">Payment webhooks reporting degraded performance</span>
          <span class="muted banner-note" *ngIf="webhookState() === 'unknown'">Webhook health endpoint unreachable — showing sample data</span>
        </div>
      </div>

      <app-kpi-row [items]="kpis" ariaLabel="Platform health summary"></app-kpi-row>

      <div class="panel panel-pad">
        <div class="section-head"><h3>Services</h3><span class="muted">Uptime and latency are sample values</span></div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>Service</th><th>Component</th><th>Status</th><th>Latency</th><th>30-day Uptime</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let service of services; trackBy: trackByName">
                <td><strong>{{ service.name }}</strong></td>
                <td class="muted">{{ service.kind }}</td>
                <td>
                  <span class="chip" [class.success]="service.state === 'operational'" [class.warn]="service.state === 'degraded'" [class.danger]="service.state === 'down'">
                    {{ service.state }}
                  </span>
                </td>
                <td>{{ service.latency }}</td>
                <td>{{ service.uptime }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="panel panel-pad gauges-panel">
        <div class="section-head"><h3>Resource Usage</h3><span class="muted">Sample — no host metrics endpoint yet</span></div>
        <div class="gauges">
          <div class="gauge" *ngFor="let gauge of gauges; trackBy: trackByLabel">
            <div class="gauge-head">
              <span>{{ gauge.label }}</span>
              <span class="muted">{{ gauge.value }}%</span>
            </div>
            <div class="gauge-track">
              <div class="gauge-fill" [style.width.%]="gauge.value" [style.background]="gaugeFill(gauge)"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .status-banner { margin-top: var(--kb-space-5); padding: var(--kb-space-4) var(--kb-space-5); display: flex; align-items: center; gap: var(--kb-space-3); }
      .status-dot { width: 12px; height: 12px; border-radius: var(--kb-radius-full); background: var(--success); flex: none; }
      .banner-note { margin-left: var(--kb-space-2); font-size: var(--kb-text-sm); }
      .panel-pad { margin-top: var(--kb-space-4); padding: var(--kb-space-5) var(--kb-space-6); }
      .gauges { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: var(--kb-space-5); }
      .gauge { display: flex; flex-direction: column; gap: var(--kb-space-2); }
      .gauge-head { display: flex; justify-content: space-between; font-size: var(--kb-text-sm); }
      .gauge-track { height: 10px; border-radius: var(--kb-radius-full); background: var(--panel-2); overflow: hidden; }
      .gauge-fill { height: 100%; border-radius: var(--kb-radius-full); background: var(--success); }
    `,
  ],
})
export class SystemHealthPageComponent {
  private readonly adminApi = inject(AdminApiService);

  readonly refreshing = signal(false);
  readonly webhookState = signal<'ok' | 'degraded' | 'unknown'>('unknown');

  readonly kpis: KpiItem[] = [
    { label: 'Uptime 30d', value: '99.96%', icon: 'health', tone: 'success', caption: 'Platform-wide (sample)' },
    { label: 'API Latency p95', value: '312ms', icon: 'monitoring', tone: 'default', caption: 'Sample' },
    { label: 'DB Load', value: '62%', icon: 'dashboard', tone: 'brand', caption: 'Sample' },
    { label: 'Error Rate', value: '0.12%', icon: 'flag', tone: 'success', caption: 'Sample' },
    { label: 'Open Incidents', value: 0, icon: 'notifications', tone: 'success', caption: 'None in last 24h' },
  ];

  readonly services: ServiceRow[] = [
    { name: 'REST API', kind: 'orders · menu · billing', state: 'operational', latency: '142 ms', uptime: '99.98%' },
    { name: 'PostgreSQL', kind: 'primary datastore', state: 'operational', latency: '9 ms', uptime: '99.99%' },
    { name: 'Payment Service', kind: 'Easebuzz + settlements', state: 'operational', latency: '310 ms', uptime: '99.91%' },
    { name: 'Webhook Delivery', kind: 'Easebuzz callbacks', state: 'operational', latency: '265 ms', uptime: '99.87%' },
    { name: 'Push Notifications', kind: 'Firebase Cloud Messaging', state: 'degraded', latency: '810 ms', uptime: '98.62%' },
    { name: 'Offline Sync Queue', kind: 'terminal replay', state: 'operational', latency: '86 ms', uptime: '99.97%' },
  ];

  readonly gauges = [
    { label: 'CPU', value: 38 },
    { label: 'Memory', value: 64 },
    { label: 'Disk', value: 47 },
  ];

  gaugeFill(gauge: { value: number }): string {
    if (gauge.value >= 85) return 'var(--danger)';
    if (gauge.value >= 70) return 'var(--warning)';
    return 'var(--success)';
  }

  refresh(): void {
    this.refreshing.set(true);
    this.adminApi.getWebhookHealth().subscribe({
      next: (data: any) => {
        const healthy = String(data?.status ?? data?.health ?? 'ok');
        this.webhookState.set(healthy.includes('degrad') ? 'degraded' : 'ok');
        this.refreshing.set(false);
      },
      error: () => {
        this.webhookState.set('unknown');
        this.refreshing.set(false);
      },
    });
  }

  trackByName(index: number, service: ServiceRow): string { return service.name; }
  trackByLabel(index: number, gauge: { label: string }): string { return gauge.label; }
}
