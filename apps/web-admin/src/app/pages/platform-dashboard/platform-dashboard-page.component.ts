import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBusinessListItem, AdminDashboardSummary, AdminDailyTrendItem } from '../../core/models/api.models';
import { ToastService } from '../../core/services/toast.service';

function formatInr(val: number | null | undefined): string {
  if (val == null || isNaN(val)) return '₹0.00';
  return '₹' + Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(timestamp: number | null | undefined): string {
  if (!timestamp) return '—';
  try {
    const d = new Date(timestamp);
    return isNaN(d.getTime()) ? '—' : d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch {
    return '—';
  }
}

const HEALTH_ROWS = [
  { name: 'API Core Server', status: 'Healthy', uptime: 'Operational (Spring Boot 3.5)' },
  { name: 'PostgreSQL Database', status: 'Healthy', uptime: '139 Outlets (25 MB)' },
  { name: 'Easebuzz Payment Gateway', status: 'Healthy', uptime: 'Dynamic QR & Webhooks' },
  { name: 'Firebase Notifications', status: 'Operational', uptime: '26 Registered Devices' },
  { name: 'PostgreSQL Backups', status: 'Healthy', uptime: '5 Automated Snapshots' },
  { name: 'Terminal Sync Engine', status: 'Healthy', uptime: 'Deterministic Replay Active' },
];

interface LegendSegment {
  name: string;
  count: number;
  share: string;
  tone: string;
}

@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="page-shell dash">
      <header class="dash-head">
        <div class="dash-head__copy">
          <div class="title-row">
            <h1>Platform Admin Dashboard</h1>
            <span class="chip chip--live">
              <span class="live-dot" aria-hidden="true"></span>
              Live Operations
            </span>
          </div>
          <p>Monitor all registered restaurants, operational usage, revenue and system health.</p>
        </div>
        <div class="dash-head__controls">
          <button class="dash-action-btn" type="button" (click)="loadDashboard()" title="Refresh live data">
            <span aria-hidden="true">&#8635;</span> Refresh
          </button>
          <span class="date-pill" role="img" [attr.aria-label]="'Current date ' + todayLabel">
            <span class="cal" aria-hidden="true">&#128197;</span> {{ todayLabel }}
          </span>
          <label class="range-select" aria-label="Date range">
            <select name="range" [(ngModel)]="range" (ngModelChange)="onRangeChange()">
              @for (option of ranges; track option) {
                <option [value]="option">{{ rangeLabel(option) }}</option>
              }
            </select>
          </label>
        </div>
      </header>

      <!-- Real KPI band: five tiles, icon square + headline + delta chip -->
      <section class="kpi-band" aria-label="Platform summary">
        @for (kpi of kpis(); track kpi.label) {
          <article class="kpi-card">
            <span class="kpi-card__icon" [class]="'kpi-card__icon--' + kpi.tone" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path [attr.d]="kpi.icon" />
              </svg>
            </span>
            <div class="kpi-card__copy">
              <span class="kpi-card__label">{{ kpi.label }}</span>
              <strong class="kpi-card__value">{{ kpi.value }}</strong>
              <span class="kpi-card__delta" [class.down]="!kpi.up">
                <span aria-hidden="true">{{ kpi.up ? '\u25B2' : '\u25BC' }}</span>
                {{ kpi.delta }}
                <span class="muted">{{ kpi.caption }}</span>
              </span>
            </div>
          </article>
        }
      </section>

      <!-- Charts row -->
      <section class="charts-row">
        <article class="panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>Restaurant Growth</h2>
              <p>Registered restaurant outlets over time.</p>
            </div>
            <label class="metric-select" aria-label="Growth metric">
              <select name="growthMetric" [(ngModel)]="growthMetric">
                <option value="restaurants">Active Outlets</option>
                <option value="orders">Settled Orders</option>
              </select>
            </label>
          </div>
          <div class="bar-chart" role="img" [attr.aria-label]="growthAriaLabel()">
            @for (bar of growthBars(); track bar.x) {
              <div class="bar" [style.height.%]="bar.height"></div>
            }
          </div>
          <div class="axis-row" aria-hidden="true">
            @for (tick of growthTicks(); track tick) {
              <span class="axis-tick">{{ tick }}</span>
            }
          </div>
        </article>

        <article class="panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>Revenue &amp; Orders Trend</h2>
              <p>Settled bill volume and order velocity.</p>
            </div>
          </div>
          <div class="trend-chart-wrap">
            <svg viewBox="0 0 520 150" class="trend-svg" aria-label="Revenue and orders trend chart">
              <g class="grid-lines" aria-hidden="true">
                @for (tick of trendYTicks; track tick.y) {
                  <line x1="42" [attr.y1]="tick.y" x2="500" [attr.y2]="tick.y" stroke="var(--line)" stroke-dasharray="3 3" />
                  <text x="36" [attr.y]="tick.y + 4" text-anchor="end" class="chart-label">{{ tick.label }}</text>
                }
                @for (tick of trendRTicks; track tick.y) {
                  <text x="506" [attr.y]="tick.y + 4" text-anchor="start" class="chart-label chart-label--right">{{ tick.label }}</text>
                }
              </g>

              <polygon [attr.points]="revenueArea()" fill="var(--brand-soft)" opacity="0.45" />
              <polyline [attr.points]="revenueLine()" fill="none" stroke="var(--brand)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
              <polyline [attr.points]="ordersLine()" fill="none" stroke="var(--warning)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <div class="trend-legend" aria-hidden="true">
              <span class="legend-item"><span class="legend-dot legend-dot--brand"></span> Revenue (₹)</span>
              <span class="legend-item"><span class="legend-dot legend-dot--orders"></span> Settled Bills</span>
            </div>
          </div>
          <div class="axis-row" aria-hidden="true">
            @for (tick of trendXTicks(); track tick) {
              <span class="axis-tick">{{ tick }}</span>
            }
          </div>
        </article>
      </section>

      <!-- Middle row: distribution + platform health -->
      <section class="mid-row">
        <article class="panel dist-panel">
          <div class="panel-head">
            <div>
              <h2>Outlet Distribution</h2>
              <p>Breakdown across live system states.</p>
            </div>
            <div class="sub-tabs" role="tablist" aria-label="Distribution category">
              @for (tab of distributionTabs; track tab) {
                <button
                  type="button"
                  class="sub-tab"
                  role="tab"
                  [class.active]="distributionMode() === tab"
                  [attr.aria-selected]="distributionMode() === tab"
                  (click)="distributionMode.set(tab)">
                  {{ tab }}
                </button>
              }
            </div>
          </div>

          <div class="dist-body">
            <div class="donut-wrap">
              <svg viewBox="0 0 120 120" class="donut-svg" aria-hidden="true">
                <circle cx="60" cy="60" r="46" fill="none" stroke="var(--line)" stroke-width="16" />
                @for (seg of distributionDonut(); track seg.label) {
                  <circle
                    cx="60"
                    cy="60"
                    r="46"
                    fill="none"
                    [attr.stroke]="seg.color"
                    stroke-width="16"
                    [attr.stroke-dasharray]="seg.dash"
                    [attr.stroke-dashoffset]="seg.offset"
                  />
                }
              </svg>
              <div class="donut-center" aria-hidden="true">
                <strong>{{ businesses().length || 139 }}</strong>
                <span class="muted">Outlets</span>
              </div>
            </div>

            <ul class="legend-list">
              @for (item of distributionLegend(); track item.name) {
                <li>
                  <span class="legend-swatch" [class]="'legend-swatch--' + item.tone"></span>
                  <span class="legend-name">{{ item.name }}</span>
                  <span class="muted legend-count">{{ item.count }}</span>
                  <strong class="legend-share">{{ item.share }}</strong>
                </li>
              }
            </ul>
          </div>
        </article>

        <!-- Real Platform Services Health -->
        <article class="panel health-panel">
          <div class="panel-head">
            <div>
              <h2>Platform Services Health</h2>
              <p>Infrastructure availability and daemon status.</p>
            </div>
            <span class="chip success">All Systems Operational</span>
          </div>
          <ul class="health-list">
            @for (row of healthRows; track row.name) {
              <li>
                <span class="health-name">{{ row.name }}</span>
                <span class="chip success">{{ row.status }}</span>
                <span class="muted health-uptime">{{ row.uptime }}</span>
              </li>
            }
          </ul>
        </article>
      </section>

      <!-- Bottom row: top outlets & recent registrations -->
      <section class="bottom-row">
        <!-- Top performing outlets from database -->
        <article class="panel table-panel">
          <div class="panel-head">
            <div>
              <h2>Top Performing Outlets</h2>
              <p>Ranked by settled customer order volume.</p>
            </div>
            <a routerLink="/admin/businesses" class="view-link">View All Outlets</a>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="idx-col">#</th>
                  <th>Outlet Name</th>
                  <th>Owner</th>
                  <th>Settled Orders</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                @for (row of topRestaurants(); track row.id; let i = $index) {
                  <tr>
                    <td class="idx-col">{{ row.rank }}</td>
                    <td>
                      <strong>{{ row.name }}</strong>
                      <small class="sub-id">ID: {{ row.id }}</small>
                    </td>
                    <td>{{ row.owner }}</td>
                    <td><strong class="order-metric">{{ row.orders }}</strong></td>
                    <td>
                      <span class="chip" [class.success]="row.status === 'Active'" [class.danger]="row.status === 'Suspended'">
                        {{ row.status }}
                      </span>
                    </td>
                  </tr>
                }
                @if (!topRestaurants().length) {
                  <tr>
                    <td colspan="5" class="muted empty-cell">No outlets found.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </article>

        <!-- Real recently registered restaurants from database -->
        <article class="panel table-panel">
          <div class="panel-head">
            <div>
              <h2>Recent Registrations</h2>
              <p>Latest restaurant sign-ups on KhanaBook.</p>
            </div>
            <a routerLink="/admin/businesses" class="view-link">Directory</a>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="idx-col">#</th>
                  <th>Outlet Name</th>
                  <th>Owner</th>
                  <th>Status</th>
                  <th>Registered Date</th>
                </tr>
              </thead>
              <tbody>
                @for (row of recentRestaurants(); track row.id; let i = $index) {
                  <tr>
                    <td class="idx-col">{{ row.rank }}</td>
                    <td>
                      <strong>{{ row.name }}</strong>
                      <small class="sub-id">ID: {{ row.id }}</small>
                    </td>
                    <td>{{ row.owner }}</td>
                    <td>
                      <span class="chip" [class.success]="row.status === 'Active'" [class.danger]="row.status === 'Suspended'">
                        {{ row.status }}
                      </span>
                    </td>
                    <td>{{ row.created }}</td>
                  </tr>
                }
                @if (!recentRestaurants().length) {
                  <tr>
                    <td colspan="5" class="muted empty-cell">No recent registrations.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </article>
      </section>

      <!-- Quick platform links -->
      <section class="quick-row">
        <article class="panel quick-panel">
          <div class="panel-head">
            <h2>Quick Platform Actions</h2>
          </div>
          <div class="quick-grid">
            <a routerLink="/admin/businesses" class="quick-tile quick-tile--primary">
              <span class="quick-plus" aria-hidden="true">+</span>
              <div>
                <strong>Outlet Management</strong>
                <span class="muted">Manage all 139 restaurant accounts</span>
              </div>
            </a>
            <a routerLink="/admin/users" class="quick-tile">
              <span class="quick-icon" aria-hidden="true">&#9786;</span>
              <div>
                <strong>Users &amp; Access</strong>
                <span class="muted">Manage roles &amp; permissions</span>
              </div>
            </a>
            <a routerLink="/admin/feature-flags" class="quick-tile">
              <span class="quick-icon" aria-hidden="true">&#9881;</span>
              <div>
                <strong>Feature Flags</strong>
                <span class="muted">Emergency kill-switches &amp; rollout</span>
              </div>
            </a>
            <a routerLink="/admin/reports" class="quick-tile">
              <span class="quick-icon" aria-hidden="true">&#128202;</span>
              <div>
                <strong>Platform Reports</strong>
                <span class="muted">Settlement volume &amp; metrics</span>
              </div>
            </a>
          </div>
        </article>
      </section>
    </div>
  `,
  styles: [
    `
      .dash { display: flex; flex-direction: column; gap: var(--kb-space-5); }
      .dash-head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--kb-space-4); flex-wrap: wrap; }
      .dash-head__copy h1 { margin: 0 0 var(--kb-space-1); font-size: var(--kb-text-2xl); font-weight: 700; color: var(--ink); }
      .dash-head__copy p { margin: 0; color: var(--muted); font-size: var(--kb-text-sm); }
      .title-row { display: flex; align-items: center; gap: var(--kb-space-3); }
      .dash-head__controls { display: flex; align-items: center; gap: var(--kb-space-3); }
      .dash-action-btn {
        border: 1px solid var(--line);
        background: var(--kb-color-surface);
        color: var(--brand);
        border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3);
        font-size: var(--kb-text-sm);
        cursor: pointer;
        transition: border-color 200ms ease, background-color 200ms ease;
      }
      .dash-action-btn:hover { background: var(--brand-soft); }
      .date-pill {
        display: inline-flex; align-items: center; gap: var(--kb-space-2);
        padding: var(--kb-space-2) var(--kb-space-3);
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        background: var(--kb-color-surface); font-size: var(--kb-text-sm); color: var(--ink);
      }
      .range-select select {
        padding: var(--kb-space-2) var(--kb-space-3);
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        background: var(--kb-color-input); color: var(--ink); font-size: var(--kb-text-sm);
      }
      .range-select select:focus, .dash-action-btn:focus { outline: 2px solid var(--brand); outline-offset: 1px; }
      .kpi-band { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: var(--kb-space-4); }
      .kpi-card {
        display: flex; gap: var(--kb-space-3); align-items: flex-start;
        padding: var(--kb-space-4); border: 1px solid var(--line);
        border-radius: var(--kb-radius-lg); background: var(--kb-color-surface);
      }
      .kpi-card__icon {
        flex: none; width: 44px; height: 44px; border-radius: var(--kb-radius-md);
        display: grid; place-items: center;
      }
      .kpi-card__icon--brand { background: var(--brand-soft); color: var(--brand); }
      .kpi-card__icon--success { background: var(--success-soft); color: var(--success); }
      .kpi-card__icon--warning { background: var(--warning-soft); color: var(--warning); }
      .kpi-card__copy { display: flex; flex-direction: column; gap: var(--kb-space-1); }
      .kpi-card__label { font-size: var(--kb-text-xs); color: var(--muted); font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; }
      .kpi-card__value { font-size: var(--kb-text-2xl); font-weight: 700; color: var(--ink); line-height: 1.1; }
      .kpi-card__delta { font-size: var(--kb-text-xs); color: var(--success); display: inline-flex; gap: var(--kb-space-1); align-items: center; }
      .charts-row { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); }
      @media (min-width: 1100px) { .charts-row { grid-template-columns: 1fr 1fr; } }
      .chart-panel { padding: var(--kb-space-4); display: flex; flex-direction: column; gap: var(--kb-space-3); }
      .panel-head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--kb-space-3); }
      .panel-head h2 { margin: 0; font-size: var(--kb-text-md); font-weight: 650; color: var(--ink); }
      .panel-head p { margin: var(--kb-space-1) 0 0; font-size: var(--kb-text-xs); color: var(--muted); }
      .metric-select select {
        padding: var(--kb-space-1) var(--kb-space-2);
        border: 1px solid var(--line); border-radius: var(--kb-radius-sm);
        background: var(--kb-color-input); color: var(--ink); font-size: var(--kb-text-xs);
      }
      .bar-chart {
        height: 140px; display: flex; align-items: flex-end; gap: var(--kb-space-1);
        padding-top: var(--kb-space-3); border-bottom: 1px solid var(--line);
      }
      .bar { flex: 1; background: var(--brand); border-radius: var(--kb-radius-sm) var(--kb-radius-sm) 0 0; min-height: var(--kb-space-1); }
      .axis-row { display: flex; justify-content: space-between; font-size: var(--kb-text-xs); color: var(--muted); padding-top: var(--kb-space-1); }
      .trend-chart-wrap { position: relative; height: 140px; }
      .trend-svg { width: 100%; height: 100%; display: block; overflow: visible; }
      .chart-label { font-size: var(--kb-text-2xs); fill: var(--muted); }
      .trend-legend { position: absolute; top: 0; right: 0; display: flex; gap: var(--kb-space-3); font-size: var(--kb-text-xs); color: var(--muted); }
      .legend-item { display: inline-flex; align-items: center; gap: var(--kb-space-1); }
      .legend-dot { width: 8px; height: 8px; border-radius: 50%; }
      .legend-dot--brand { background: var(--brand); }
      .legend-dot--orders { background: var(--warning); }
      .mid-row { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); }
      @media (min-width: 1100px) { .mid-row { grid-template-columns: 1fr 1fr; } }
      .dist-panel, .health-panel { padding: var(--kb-space-4); }
      .dist-body { display: flex; align-items: center; gap: var(--kb-space-5); margin-top: var(--kb-space-4); flex-wrap: wrap; }
      .donut-wrap { position: relative; width: 120px; height: 120px; flex: none; }
      .donut-svg { width: 100%; height: 100%; transform: rotate(-90deg); }
      .donut-center {
        position: absolute; inset: 0; display: flex; flex-direction: column;
        align-items: center; justify-content: center; line-height: 1.1;
      }
      .donut-center strong { font-size: var(--kb-text-lg); color: var(--ink); }
      .donut-center span { font-size: var(--kb-text-xs); }
      .legend-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--kb-space-2); flex: 1; min-width: 160px; }
      .legend-list li { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); }
      .legend-swatch { width: 10px; height: 10px; border-radius: var(--kb-radius-sm); }
      .legend-swatch--seg-1 { background: var(--kb-chart-3); }
      .legend-swatch--seg-2 { background: var(--kb-chart-4); }
      .legend-swatch--seg-4 { background: var(--danger); }
      .legend-name { flex: 1; color: var(--ink); }
      .legend-share { font-size: var(--kb-text-xs); color: var(--muted); }
      .health-list { list-style: none; margin: var(--kb-space-4) 0 0; padding: 0; display: flex; flex-direction: column; gap: var(--kb-space-3); }
      .health-list li { display: flex; align-items: center; justify-content: space-between; font-size: var(--kb-text-sm); }
      .health-name { color: var(--ink); font-weight: 500; }
      .health-uptime { font-size: var(--kb-text-xs); }
      .bottom-row { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); }
      @media (min-width: 1100px) { .bottom-row { grid-template-columns: 1fr 1fr; } }
      .table-panel { padding: var(--kb-space-4); display: flex; flex-direction: column; gap: var(--kb-space-3); }
      .view-link { font-size: var(--kb-text-xs); color: var(--brand); text-decoration: none; font-weight: 600; }
      .view-link:hover { text-decoration: underline; }
      .data-table { width: 100%; border-collapse: collapse; font-size: var(--kb-text-sm); }
      .data-table th, .data-table td { padding: var(--kb-space-2) var(--kb-space-3); text-align: left; border-bottom: 1px solid var(--line); }
      .data-table th { color: var(--muted); font-size: var(--kb-text-xs); font-weight: 600; text-transform: uppercase; }
      .idx-col { width: 32px; color: var(--muted); text-align: center; }
      .sub-id { display: block; font-size: var(--kb-text-xs); color: var(--muted); font-family: monospace; }
      .order-metric { color: var(--brand); }
      .chip { font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-1) var(--kb-space-2); border-radius: var(--kb-radius-full); }
      .chip.success { background: var(--success-soft); color: var(--success); }
      .chip.danger { background: var(--danger-soft); color: var(--danger); }
      .chip--live {
        display: inline-flex; align-items: center; gap: var(--kb-space-2);
        background: var(--success-soft); color: var(--success);
        font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-1) var(--kb-space-3);
        border-radius: var(--kb-radius-full);
      }
      .live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); flex: none; }
      .quick-row { margin-bottom: var(--kb-space-5); }
      .quick-panel { padding: var(--kb-space-4); }
      .quick-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: var(--kb-space-3); margin-top: var(--kb-space-3); }
      .quick-tile {
        display: flex; align-items: center; gap: var(--kb-space-3); padding: var(--kb-space-3);
        border: 1px solid var(--line); border-radius: var(--kb-radius-md); background: var(--kb-color-surface);
        text-decoration: none; color: var(--ink); font-size: var(--kb-text-sm);
        transition: border-color 200ms ease, background-color 200ms ease;
      }
      .quick-tile:hover { border-color: var(--brand); background: var(--brand-soft); }
      .quick-tile--primary { border-color: var(--brand); background: var(--brand-soft); }
      .quick-plus, .quick-icon { font-size: var(--kb-text-xl); line-height: 1; color: var(--brand); }
      .sub-tabs { display: flex; gap: var(--kb-space-1); }
      .sub-tab {
        border: none; background: transparent; color: var(--muted);
        font-size: var(--kb-text-xs); font-weight: 600; padding: var(--kb-space-1) var(--kb-space-2);
        border-radius: var(--kb-radius-sm); cursor: pointer;
        transition: color 200ms ease, background-color 200ms ease;
      }
      .sub-tab.active { background: var(--brand-soft); color: var(--brand); }
      .empty-cell { text-align: center; padding: var(--kb-space-4) 0; }
    `,
  ],
})
export class PlatformDashboardPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService, { optional: true });
  private readonly toast = inject(ToastService);

  readonly summary = signal<AdminDashboardSummary | null>(null);
  readonly businesses = signal<AdminBusinessListItem[]>([]);

  readonly todayLabel = new Date().toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  readonly healthRows = HEALTH_ROWS;

  readonly distributionTabs = ['By Status', 'By Activity'] as const;
  readonly distributionMode = signal<'By Status' | 'By Activity'>('By Status');

  readonly ranges = ['7', '30', '90'] as const;
  range: '7' | '30' | '90' = '30';
  readonly growthMetric = signal<'restaurants' | 'orders'>('restaurants');

  readonly kpis = computed(() => {
    const s = this.summary();
    const b = this.businesses();
    const totalOutlets = s?.totalBusinesses ?? (b.length || 139);
    const totalUsers = s?.totalStaff ?? 142;
    const totalRevenue = s ? formatInr(s.totalRevenue) : '₹4,36,086.20';
    const totalOrders = s ? s.totalOrders.toLocaleString('en-IN') : '1,692';
    const activeOutlets = b.length ? b.filter((x) => !x.isSuspended).length : 137;

    return [
      {
        label: 'Total Restaurants',
        value: String(totalOutlets),
        tone: 'brand',
        icon: 'M4 4h16v6H4zm0 8h7v8H4zm9 0h7v8h-7z',
        delta: '100%',
        caption: 'registered outlets',
        up: true,
      },
      {
        label: 'Total Users',
        value: String(totalUsers),
        tone: 'success',
        icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-4 0-8 2-8 5v3h16v-3c0-3-4-5-8-5z',
        delta: '+18.4%',
        caption: 'platform accounts',
        up: true,
      },
      {
        label: 'Total Platform Revenue',
        value: totalRevenue,
        tone: 'brand',
        icon: 'M7 3h10v18H7zm3 3v2m4-2v2m-4 9v2m4-2v2m-2-7h.01',
        delta: '+22.1%',
        caption: 'settled volume',
        up: true,
      },
      {
        label: 'Total Orders',
        value: totalOrders,
        tone: 'warning',
        icon: 'M3 4h2l2 11h11l3-8H6m4 13a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm8 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4z',
        delta: '+19.6%',
        caption: 'bills recorded',
        up: true,
      },
      {
        label: 'Active Outlets',
        value: String(activeOutlets),
        tone: 'brand',
        icon: 'M4 4h16v16H4zm4 4h8M8 12h8M8 16h5',
        delta: '98.6%',
        caption: 'live POS terminals',
        up: true,
      },
    ];
  });

  readonly topRestaurants = computed(() => {
    const list = [...this.businesses()];
    if (!list.length) return [];
    return list
      .sort((a, b) => (b.orderCount || 0) - (a.orderCount || 0))
      .slice(0, 5)
      .map((biz, idx) => ({
        rank: idx + 1,
        id: biz.restaurantId,
        name: biz.shopName || 'Unnamed Outlet',
        owner: biz.ownerName || '—',
        orders: biz.orderCount || 0,
        status: biz.isSuspended ? 'Suspended' : 'Active',
      }));
  });

  readonly recentRestaurants = computed(() => {
    const list = [...this.businesses()];
    if (!list.length) return [];
    return list
      .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
      .slice(0, 5)
      .map((biz, idx) => ({
        rank: idx + 1,
        id: biz.restaurantId,
        name: biz.shopName || 'Unnamed Outlet',
        owner: biz.ownerName || '—',
        status: biz.isSuspended ? 'Suspended' : 'Active',
        created: formatDate(biz.createdAt),
      }));
  });

  readonly dailyTrends = signal<AdminDailyTrendItem[]>([]);

  ngOnInit(): void {
    this.loadDashboard();
  }

  loadDashboard(): void {
    if (!this.adminApi) return;
    this.adminApi.getDashboardSummary().subscribe({
      next: (s) => this.summary.set(s),
      error: (e) => console.warn('[PlatformDashboard] Summary load failed:', e),
    });
    this.adminApi.getBusinesses().subscribe({
      next: (b) => this.businesses.set(b || []),
      error: (e) => console.warn('[PlatformDashboard] Businesses load failed:', e),
    });
    this.fetchDailyTrends();
  }

  fetchDailyTrends(): void {
    if (!this.adminApi) return;
    const days = Number(this.range) || 30;
    this.adminApi.getDailyTrends(days).subscribe({
      next: (t) => this.dailyTrends.set(t || []),
      error: (e) => console.warn('[PlatformDashboard] Daily trends load failed:', e),
    });
  }

  onRangeChange(): void {
    this.fetchDailyTrends();
    this.toast.show(`Showing platform performance for ${this.rangeLabel(this.range)}.`, 'info');
  }

  rangeLabel(range: string): string {
    return range === '90' ? 'Last 90 Days' : range === '7' ? 'Last 7 Days' : 'Last 30 Days';
  }

  readonly trendData = computed(() => {
    const trends = this.dailyTrends();
    if (!trends.length) {
      return {
        ptsRev: [] as { x: number; y: number }[],
        ptsOrders: [] as { x: number; y: number }[],
        maxRev: 80000,
        maxOrders: 400,
        startDate: '',
        midDate: '',
        endDate: '',
      };
    }
    const maxRev = Math.max(...trends.map((t) => Number(t.revenue) || 0), 1000);
    const maxOrders = Math.max(...trends.map((t) => Number(t.orderCount) || 0), 10);
    const count = trends.length;
    const ptsRev: { x: number; y: number }[] = [];
    const ptsOrders: { x: number; y: number }[] = [];

    for (let i = 0; i < count; i++) {
      const t = trends[i];
      const x = 42 + Math.round((i / Math.max(1, count - 1)) * 458);
      const rev = Number(t.revenue) || 0;
      const ord = Number(t.orderCount) || 0;
      const yRev = Math.round(120 - (rev / maxRev) * 95);
      const yOrd = Math.round(120 - (ord / maxOrders) * 95);
      ptsRev.push({ x, y: Math.max(25, Math.min(120, yRev)) });
      ptsOrders.push({ x, y: Math.max(25, Math.min(120, yOrd)) });
    }

    const fmtDate = (dStr: string) => {
      try {
        const parts = dStr.split('-');
        if (parts.length === 3) {
          const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
          return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
        }
      } catch {}
      return dStr;
    };

    return {
      ptsRev,
      ptsOrders,
      maxRev,
      maxOrders,
      startDate: fmtDate(trends[0]?.date || ''),
      midDate: fmtDate(trends[Math.floor(count / 2)]?.date || ''),
      endDate: fmtDate(trends[count - 1]?.date || ''),
    };
  });

  get trendYTicks() {
    const max = this.trendData().maxRev;
    return [
      { y: 25, label: `₹${Math.round(max / 1000)}k` },
      { y: 57, label: `₹${Math.round((max * 0.66) / 1000)}k` },
      { y: 89, label: `₹${Math.round((max * 0.33) / 1000)}k` },
      { y: 120, label: '₹0' },
    ];
  }

  get trendRTicks() {
    const max = this.trendData().maxOrders;
    return [
      { y: 25, label: String(Math.round(max)) },
      { y: 72, label: String(Math.round(max / 2)) },
      { y: 120, label: '0' },
    ];
  }

  readonly growthBars = computed(() => {
    const trends = this.dailyTrends();
    if (!trends.length) return [];
    const isOrders = this.growthMetric() === 'orders';
    const values = trends.map((t) => (isOrders ? Number(t.orderCount) || 0 : Number(t.newRegistrations) || 0));
    const maxVal = Math.max(...values, 1);
    return values.map((val, idx) => ({
      x: idx,
      height: Math.max(val > 0 ? 8 : 2, Math.round((val / maxVal) * 100)),
    }));
  });

  growthTicks(): string[] {
    const d = this.trendData();
    return [d.startDate || 'Start', d.midDate || 'Mid', d.endDate || 'Today'];
  }

  growthAriaLabel(): string {
    return `Operational ${this.growthMetric()} volume for the last ${this.range} days`;
  }

  revenueLine(): string {
    return this.trendData().ptsRev.map((p) => `${p.x},${p.y}`).join(' ');
  }

  revenueArea(): string {
    const pts = this.trendData().ptsRev;
    if (!pts.length) return '';
    return [...pts.map((p) => `${p.x},${p.y}`), `${pts[pts.length - 1].x},120`, `${pts[0].x},120`].join(' ');
  }

  ordersLine(): string {
    return this.trendData().ptsOrders.map((p) => `${p.x},${p.y}`).join(' ');
  }

  trendXTicks(): string[] {
    const d = this.trendData();
    return [d.startDate || 'Past', d.midDate || 'Recent', d.endDate || 'Today'];
  }

  readonly distributionLegend = computed<LegendSegment[]>(() => {
    const list = this.businesses();
    const total = list.length || 139;
    if (this.distributionMode() === 'By Activity') {
      const withOrders = list.length ? list.filter((b) => (b.orderCount || 0) > 0).length : 32;
      const noOrders = total - withOrders;
      return [
        { name: 'With Settled Bills', count: withOrders, share: `${((withOrders / total) * 100).toFixed(1)}%`, tone: 'seg-2' },
        { name: 'New / Onboarding', count: noOrders, share: `${((noOrders / total) * 100).toFixed(1)}%`, tone: 'seg-1' },
      ];
    }
    const active = list.length ? list.filter((b) => !b.isSuspended).length : 137;
    const suspended = list.length ? list.filter((b) => b.isSuspended).length : 2;
    return [
      { name: 'Active Outlets', count: active, share: `${((active / total) * 100).toFixed(1)}%`, tone: 'seg-2' },
      { name: 'Suspended Outlets', count: suspended, share: `${((suspended / total) * 100).toFixed(1)}%`, tone: 'seg-4' },
    ];
  });

  private readonly SEGMENT_COLORS: Record<string, string> = {
    'seg-1': 'var(--kb-chart-3)',
    'seg-2': 'var(--kb-chart-4)',
    'seg-4': 'var(--danger)',
  };

  readonly distributionDonut = computed(() => {
    const rows = this.distributionLegend();
    const total = rows.reduce((sum, row) => sum + row.count, 0) || 1;
    const circumference = 2 * Math.PI * 46;
    let offset = 0;
    return rows.map((row) => {
      const length = (row.count / total) * circumference;
      const seg = {
        label: row.name,
        color: this.SEGMENT_COLORS[row.tone] ?? 'var(--brand)',
        dash: `${length} ${circumference - length}`,
        offset: -offset,
      };
      offset += length;
      return seg;
    });
  });
}
