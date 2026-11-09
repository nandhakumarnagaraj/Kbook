import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, NgZone, OnInit, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBackupStatus, AdminBusinessListItem, AdminDashboardSummary, AdminDailyTrendItem } from '../../core/models/api.models';
import { catchError, forkJoin, of } from 'rxjs';
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

/** Indian short scale for KPI tiles: ₹4.36L, ₹1.23Cr. The exact rupee value rides in the tooltip. */
function formatCompactInr(value: number | null | undefined): string {
  if (value == null || isNaN(value)) return '—';
  const n = Number(value);
  if (Math.abs(n) >= 1e7) return '₹' + (n / 1e7).toFixed(2) + 'Cr';
  if (Math.abs(n) >= 1e5) return '₹' + (n / 1e5).toFixed(2) + 'L';
  return '₹' + n.toLocaleString('en-IN', { maximumFractionDigits: 0 });
}

function formatShortDate(value: string | null | undefined): string {
  if (!value) return '—';
  const parts = value.split('-');
  if (parts.length === 3) {
    const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    if (!isNaN(d.getTime())) return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
  }
  return value;
}

interface LegendSegment {
  name: string;
  count: number | null;
  share: string;
  tone: string;
}

interface GrowthBar {
  x: number;
  height: number;
  valueLabel: string;
  dateLabel: string;
  /** Tooltip anchor as a percentage of the plot width, clamped off the edges. */
  left: number;
}

@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink],
  host: { '(document:pointerdown)': 'onDocumentPointerDown($event)' },
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
          @if (lastUpdatedLabel()) {
            <span class="updated-at" aria-live="polite">Updated {{ lastUpdatedLabel() }}</span>
          }
          <button class="dash-action-btn" type="button" (click)="loadDashboard()" [disabled]="loading()"
                  aria-label="Refresh platform metrics" title="Refresh platform metrics">
            <span aria-hidden="true">&#8635;</span> {{ loading() ? 'Refreshing…' : 'Refresh' }}
          </button>
          <span class="date-pill" role="img" [attr.aria-label]="'Current date ' + todayLabel">
            <span class="cal" aria-hidden="true">&#128197;</span> {{ todayLabel }}
          </span>
          <label class="range-select" aria-label="Chart range">
            <select name="range" [(ngModel)]="range" (ngModelChange)="onRangeChange()">
              @for (option of ranges; track option) {
                <option [value]="option">{{ rangeLabel(option) }}</option>
              }
            </select>
          </label>
        </div>
      </header>

      @if (loadFailed()) {
        <div class="notice" role="alert">
          <span>Live platform metrics could not be loaded. Figures below stay at “—” until the API responds.</span>
          <button type="button" class="notice__retry" (click)="loadDashboard()">Retry</button>
        </div>
      }

      <!-- Five tiles: icon square, label, figure and one honest caption. -->
      <section class="kpi-band" aria-label="Platform summary" [attr.aria-busy]="loading()">
        @for (kpi of kpis(); track kpi.label) {
          <article class="kpi-card">
            <span class="kpi-card__icon" [class]="'kpi-card__icon--' + kpi.tone" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path [attr.d]="kpi.icon" />
              </svg>
            </span>
            <div class="kpi-card__copy">
              <span class="kpi-card__label" [attr.title]="kpi.label">{{ kpi.label }}</span>
              <strong class="kpi-card__value" [attr.title]="kpi.hint" [attr.aria-label]="kpi.hint">{{ kpi.value }}</strong>
              <span class="kpi-card__caption">{{ kpi.caption }}</span>
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
          <div
            class="bar-chart"
            role="img"
            tabindex="0"
            [attr.aria-label]="growthAriaLabel()"
            (focus)="onGrowthFocus()"
            (blur)="hoverGrowth.set(null)"
            (keydown)="onGrowthKey($event)">
            <div class="bar-axis" aria-hidden="true">
              @for (tick of growthYTicks(); track $index) {
                <span>{{ tick }}</span>
              }
            </div>
            <div
              class="bar-plot"
              (pointerdown)="onGrowthPointer($event)"
              (pointermove)="onGrowthPointer($event)"
              (pointerleave)="onGrowthLeave($event)">
              <div class="bar-grid" aria-hidden="true">
                <span></span><span></span><span></span>
              </div>
              @for (bar of growthBars(); track bar.x) {
                <div class="bar" [style.height.%]="bar.height"></div>
              }
              @if (hoverGrowth(); as hover) {
                <div class="chart-tip" [style.--tip]="hover.left + '%'">
                  <strong>{{ hover.valueLabel }}</strong>
                  <span>{{ hover.dateLabel }}</span>
                </div>
              }
            </div>
            <div class="axis-row" aria-hidden="true">
              @for (tick of growthTicks(); track tick) {
                <span class="axis-tick">{{ tick }}</span>
              }
            </div>
          </div>
          <span class="sr-only" aria-live="polite">
            @if (hoverGrowth(); as hover) { {{ hover.valueLabel }} on {{ hover.dateLabel }} }
          </span>
        </article>

        <article class="panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>Revenue &amp; Orders Trend</h2>
              <p>Settled bill volume and order velocity.</p>
            </div>
            <div class="trend-legend" aria-hidden="true">
              <span class="legend-item"><span class="legend-dot legend-dot--brand"></span> Revenue (₹)</span>
              <span class="legend-item"><span class="legend-dot legend-dot--orders"></span> Settled Bills</span>
            </div>
          </div>
          <div class="trend-chart-wrap">
            <svg
              [attr.viewBox]="'0 0 ' + trendWidth() + ' ' + trendPlot().height"
              class="trend-svg"
              role="img"
              tabindex="0"
              [attr.aria-label]="trendAriaLabel()"
              (pointerdown)="onTrendPointer($event)"
              (pointermove)="onTrendPointer($event)"
              (pointerleave)="onTrendLeave($event)"
              (focus)="onTrendFocus()"
              (blur)="hoverTrendIndex.set(null)"
              (keydown)="onTrendKey($event)">
              <g class="grid-lines" aria-hidden="true">
                @for (tick of trendYTicks; track tick.y) {
                  <line [attr.x1]="trendPlot().left" [attr.y1]="tick.y" [attr.x2]="trendPlot().right" [attr.y2]="tick.y"
                        stroke="var(--line)" stroke-dasharray="3 3" />
                  <text [attr.x]="trendPlot().left - 6" [attr.y]="tick.y + 4" text-anchor="end"
                        class="chart-label chart-label--rev">{{ tick.label }}</text>
                }
                @for (tick of trendRTicks; track tick.y) {
                  <text [attr.x]="trendPlot().right + 6" [attr.y]="tick.y + 4" text-anchor="start"
                        class="chart-label chart-label--orders">{{ tick.label }}</text>
                }
              </g>

              <polygon [attr.points]="revenueArea()" fill="var(--brand-soft)" opacity="0.45" />
              <polyline [attr.points]="ordersLine()" fill="none" stroke="var(--kb-chart-2)" stroke-width="2"
                        stroke-dasharray="6 4" stroke-linecap="round" stroke-linejoin="round" />
              <polyline [attr.points]="revenueLine()" fill="none" stroke="var(--brand)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />

              @if (trendHover(); as hover) {
                <line [attr.x1]="hover.x" [attr.y1]="trendPlot().top" [attr.x2]="hover.x" [attr.y2]="trendPlot().bottom"
                      stroke="var(--muted)" stroke-dasharray="3 3" opacity="0.6" />
                <circle [attr.cx]="hover.x" [attr.cy]="hover.yOrders" r="4" fill="var(--kb-chart-2)" stroke="#fff" stroke-width="2" />
                <circle [attr.cx]="hover.x" [attr.cy]="hover.yRev" r="4.5" fill="var(--brand)" stroke="#fff" stroke-width="2" />
              }
            </svg>
            @if (trendHover(); as hover) {
              <div class="chart-tip chart-tip--trend" [style.left.px]="hover.tipX">
                <span>{{ hover.date }}</span>
                <strong><i class="legend-dot legend-dot--brand"></i>{{ hover.revenue }}</strong>
                <strong><i class="legend-dot legend-dot--orders"></i>{{ hover.orders }}</strong>
              </div>
            }
          </div>
          <span class="sr-only" aria-live="polite">
            @if (trendHover(); as hover) {
              {{ hover.date }}: {{ hover.revenue }} revenue, {{ hover.orders }} bills
            }
          </span>
          <div class="axis-row axis-row--trend" aria-hidden="true">
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
              <p>{{ distributionScopeLabel() }}</p>
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
                <strong>{{ distributionTotal() }}</strong>
                <span class="muted">Outlets</span>
              </div>
            </div>

            <ul class="legend-list">
              @for (item of distributionLegend(); track item.name) {
                <li>
                  <span class="legend-swatch" [class]="'legend-swatch--' + item.tone"></span>
                  <span class="legend-name">{{ item.name }}</span>
                  <span class="muted legend-count">{{ item.count ?? '—' }}</span>
                  <strong class="legend-share">{{ item.share }}</strong>
                </li>
              }
            </ul>
          </div>
        </article>

        <!-- Only what this console actually measures: webhook delivery and backups.
             A green "All Systems Operational" that no endpoint produced is worse
             than an admitted unknown. -->
        <article class="panel health-panel">
          <div class="panel-head">
            <div>
              <h2>Platform Services Health</h2>
              <p>Measured from payment webhooks and database backups.</p>
            </div>
            <span
              class="chip"
              [class.success]="healthSummary().tone === 'success'"
              [class.danger]="healthSummary().tone === 'danger'"
              [class.neutral]="healthSummary().tone === 'neutral'">
              {{ healthSummary().label }}
            </span>
          </div>
          <ul class="health-list">
            @for (row of healthRows(); track row.name) {
              <li>
                <span class="health-name">{{ row.name }}</span>
                <span
                  class="chip"
                  [class.success]="row.tone === 'success'"
                  [class.danger]="row.tone === 'danger'"
                  [class.neutral]="row.tone === 'neutral'">
                  {{ row.status }}
                </span>
                <span class="muted health-uptime">{{ row.detail }}</span>
              </li>
            }
          </ul>
          <a routerLink="/admin/system-health" class="view-link">Open System Health</a>
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
      .dash-head__controls { display: flex; align-items: center; gap: var(--kb-space-3); flex-wrap: wrap; }
      .updated-at { font-size: var(--kb-text-xs); color: var(--muted); }
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
      .dash-action-btn:disabled { opacity: 0.6; cursor: progress; }
      .notice {
        display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-3);
        flex-wrap: wrap; padding: var(--kb-space-3) var(--kb-space-4);
        border: 1px solid var(--danger); border-radius: var(--kb-radius-md);
        background: var(--danger-soft); color: var(--ink); font-size: var(--kb-text-sm);
      }
      .notice__retry {
        border: 1px solid var(--danger); background: transparent; color: var(--danger);
        border-radius: var(--kb-radius-sm); padding: var(--kb-space-1) var(--kb-space-3);
        font-size: var(--kb-text-xs); font-weight: 650; cursor: pointer;
      }
      /* Five figures, laid out on purpose: one row while the copy column can hold
         a label, then 3 + 2 with the lower pair widened, then 2 + 2 + one
         full-width tile. A single auto-fit track produced a 4 + 1 orphan at
         desktop widths, which is why this is explicit. */
      .kpi-band { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--kb-space-4); }
      @media (max-width: 1199px) {
        .kpi-band { grid-template-columns: repeat(6, minmax(0, 1fr)); }
        .kpi-band > * { grid-column: span 2; }
        .kpi-band > :nth-child(4), .kpi-band > :nth-child(5) { grid-column: span 3; }
      }
      @media (max-width: 899px) {
        .kpi-band { grid-template-columns: repeat(2, minmax(0, 1fr)); }
        .kpi-band > *, .kpi-band > :nth-child(4), .kpi-band > :nth-child(5) { grid-column: auto; }
        .kpi-band > :last-child:nth-child(odd) { grid-column: span 2; }
      }
      @media (max-width: 639px) {
        .kpi-band { grid-template-columns: 1fr; }
        .kpi-band > :last-child:nth-child(odd) { grid-column: auto; }
      }
      /* Grid tracks must be allowed to shrink, or a wide table pushes the whole
         page sideways on a phone and the table's own scroller never engages. */
      .kpi-band > *, .charts-row > *, .mid-row > *, .bottom-row > * { min-width: 0; }
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
      .kpi-card__copy { display: flex; flex-direction: column; gap: var(--kb-space-1); min-width: 0; }
      /* 11px, one line. At five across a 1440 viewport the copy column is ~137px,
         and "TOTAL PLATFORM REVENUE" cannot hold that at 12px without wrapping. */
      .kpi-card__label {
        font-size: var(--kb-text-2xs); color: var(--muted); font-weight: 700;
        text-transform: uppercase; letter-spacing: 0.03em;
        white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
      }
      .kpi-card__value { font-size: var(--kb-text-2xl); font-weight: 700; color: var(--ink); line-height: 1.1; font-variant-numeric: tabular-nums; white-space: nowrap; }
      .kpi-card__caption { font-size: var(--kb-text-2xs); color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
      .charts-row { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); }
      @media (min-width: 1100px) { .charts-row { grid-template-columns: 1fr 1fr; } }
      .chart-panel { padding: var(--kb-space-4); display: flex; flex-direction: column; gap: var(--kb-space-3); }
      .panel-head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--kb-space-3); flex-wrap: wrap; }
      .panel-head h2 { margin: 0; font-size: var(--kb-text-md); font-weight: 650; color: var(--ink); }
      .panel-head p { margin: var(--kb-space-1) 0 0; font-size: var(--kb-text-xs); color: var(--muted); }
      .metric-select select {
        padding: var(--kb-space-1) var(--kb-space-2);
        border: 1px solid var(--line); border-radius: var(--kb-radius-sm);
        background: var(--kb-color-input); color: var(--ink); font-size: var(--kb-text-xs);
      }
      .bar-chart {
        display: grid; grid-template-columns: auto 1fr; grid-template-rows: 152px auto;
        column-gap: var(--kb-space-2); touch-action: pan-y;
      }
      /* The date row sits in the plot column, so "10 Oct" starts under the first
         bar instead of under the y-axis labels. */
      .bar-chart > .axis-row { grid-column: 2; grid-row: 2; }
      .bar-axis {
        display: flex; flex-direction: column; justify-content: space-between;
        padding: var(--kb-space-3) 0 1px; text-align: right;
        font-size: var(--kb-text-2xs); color: var(--muted); font-variant-numeric: tabular-nums;
      }
      .bar-plot {
        position: relative; display: flex; align-items: flex-end; gap: 3px;
        padding-top: var(--kb-space-3); border-bottom: 1px solid var(--line);
      }
      .bar-grid {
        position: absolute; inset: var(--kb-space-3) 0 0;
        display: flex; flex-direction: column; justify-content: space-between; pointer-events: none;
      }
      .bar-grid span { border-top: 1px dashed var(--line); }
      .bar {
        flex: 1; background: var(--brand); border-radius: var(--kb-radius-sm) var(--kb-radius-sm) 0 0;
        min-height: var(--kb-space-1); transition: opacity 120ms ease;
      }
      .bar:hover { opacity: 0.7; }
      .chart-tip {
        position: absolute; top: 3px; transform: translateX(-50%);
        /* Anchored at the sample but never past the plot edge, so the first and
           last tooltip cannot widen the page on a phone. */
        left: clamp(52px, var(--tip, 50%), calc(100% - 52px));
        display: grid; gap: 1px; padding: var(--kb-space-1) var(--kb-space-2);
        border: 1px solid var(--line); border-radius: var(--kb-radius-sm);
        background: var(--kb-color-surface); font-size: var(--kb-text-2xs); color: var(--muted);
        pointer-events: none; white-space: nowrap; z-index: 3;
      }
      .chart-tip strong { color: var(--ink); font-size: var(--kb-text-xs); font-weight: 650; }
      .chart-tip--trend strong { display: inline-flex; align-items: center; gap: 4px; }
      /* Both charts take focus and read out the sample under the arrow keys, so a
         keyboard user gets the same numbers a mouse hover shows. */
      .bar-chart:focus-visible, .trend-svg:focus-visible {
        outline: 2px solid var(--brand); outline-offset: 3px; border-radius: var(--kb-radius-sm);
      }
      .sr-only {
        position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
        overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
      }
      .axis-row { display: flex; justify-content: space-between; font-size: var(--kb-text-xs); color: var(--muted); padding-top: var(--kb-space-1); }
      /* Same 54/40px insets as the plot, so the date labels line up with the line ends. */
      .axis-row--trend { padding: var(--kb-space-1) 40px 0 54px; }
      .trend-chart-wrap { position: relative; height: 152px; }
      .trend-svg { width: 100%; height: 100%; display: block; overflow: visible; cursor: crosshair; touch-action: pan-y; }
      .chart-label { font-size: var(--kb-text-2xs); fill: var(--muted); }
      /* Axis labels carry the series colour: two scales on one frame are only
         readable if each axis says which line it belongs to. */
      .chart-label--rev { fill: var(--brand-orange-ink); }
      .chart-label--orders { fill: var(--kb-chart-2); }
      .trend-legend { display: flex; flex-wrap: wrap; gap: var(--kb-space-3); font-size: var(--kb-text-2xs); color: var(--muted); }
      .legend-item { display: inline-flex; align-items: center; gap: var(--kb-space-1); }
      .legend-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; flex: none; }
      .legend-dot--brand { background: var(--brand); }
      .legend-dot--orders { background: var(--kb-chart-2); width: 10px; height: 3px; border-radius: 1px; }
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
      .health-list li { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-2); flex-wrap: wrap; font-size: var(--kb-text-sm); }
      .health-name { color: var(--ink); font-weight: 500; }
      .health-uptime { font-size: var(--kb-text-xs); }
      .bottom-row { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); }
      @media (min-width: 1100px) { .bottom-row { grid-template-columns: 1fr 1fr; } }
      .table-panel { padding: var(--kb-space-4); display: flex; flex-direction: column; gap: var(--kb-space-3); }
      /* --brand (#FF5A0A) on white is 3.1:1 — below AA for 12px text. The deeper
         orange ink is the text-safe sibling of the action colour. */
      /* A text link is still a target: 12px type alone was a 15–18px hit area,
         under the 24×24 minimum. The inline-flex box gives it height without
         changing how the label sits in the panel head. */
      .view-link {
        display: inline-flex; align-items: center;
        min-height: 24px; padding: 0 var(--kb-space-1);
        margin-right: calc(var(--kb-space-1) * -1);
        font-size: var(--kb-text-xs); color: var(--brand-orange-ink);
        text-decoration: none; font-weight: 600;
      }
      .view-link:hover { text-decoration: underline; }
      .data-table { width: 100%; border-collapse: collapse; font-size: var(--kb-text-sm); }
      .data-table th, .data-table td { padding: var(--kb-space-2) var(--kb-space-3); text-align: left; border-bottom: 1px solid var(--line); }
      .data-table th { color: var(--muted); font-size: var(--kb-text-xs); font-weight: 600; text-transform: uppercase; }
      .idx-col { width: 32px; color: var(--muted); text-align: center; }
      .sub-id { display: block; font-size: var(--kb-text-xs); color: var(--muted); font-family: monospace; }
      .order-metric { color: var(--brand-orange-ink); }
      .chip { font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-1) var(--kb-space-2); border-radius: var(--kb-radius-full); }
      .chip.success { background: var(--success-soft); color: var(--success); }
      .chip.neutral { background: var(--kb-color-bg-app); color: var(--muted); }
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
      .quick-tile > div { display: grid; gap: 2px; min-width: 0; }
      .quick-tile strong { font-weight: 600; }
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
  readonly backups = signal<AdminBackupStatus | null>(null);
  readonly webhookState = signal<'ok' | 'degraded' | 'unknown'>('unknown');

  /** True while a platform load is in flight, so Refresh can state it is working. */
  readonly loading = signal(false);
  /** True when the summary could not be read: figures stay at "—", never invented. */
  readonly loadFailed = signal(false);
  readonly lastUpdated = signal<Date | null>(null);

  /** Pointer targets the nearest sample; null means no tooltip is showing. */
  readonly hoverGrowth = signal<GrowthBar | null>(null);
  readonly hoverTrendIndex = signal<number | null>(null);
  /** Touch has no hover, so a tap pins the readout until the next tap outside. */
  readonly pinGrowth = signal(false);
  readonly pinTrend = signal(false);
  /** Measured trend plot width. The SVG maps 1 unit to 1 CSS px, so 11px labels stay 11px. */
  readonly trendWidth = signal(560);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    afterNextRender(() => {
      const wrap = this.host.nativeElement.querySelector<HTMLElement>('.trend-chart-wrap');
      if (!wrap) return;
      const sync = () => {
        const width = Math.round(wrap.clientWidth);
        if (width > 0 && width !== this.trendWidth()) this.zone.run(() => this.trendWidth.set(width));
      };
      sync();
      const observer = new ResizeObserver(sync);
      observer.observe(wrap);
      this.destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  readonly todayLabel = new Date().toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });

  readonly lastUpdatedLabel = computed(() => {
    const at = this.lastUpdated();
    return at ? at.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }) : '';
  });

  /**
   * Only the signals this console actually measures. The panel used to print six
   * hardcoded "Healthy" services beside an unconditional "All Systems Operational",
   * which is a claim no endpoint backed.
   */
  readonly healthRows = computed(() => {
    const hooks = this.webhookState();
    const backup = this.backups();
    return [
      {
        name: 'Payment Webhooks',
        status: hooks === 'ok' ? 'Operational' : hooks === 'degraded' ? 'Degraded' : 'Unknown',
        tone: hooks === 'ok' ? 'success' : hooks === 'degraded' ? 'danger' : 'neutral',
        detail: hooks === 'unknown' ? 'No health response from the API' : 'Easebuzz callback delivery',
      },
      {
        name: 'Database Backups',
        status: backup ? 'Healthy' : 'Unknown',
        tone: backup ? 'success' : 'neutral',
        detail: backup
          ? `${backup.totalSnapshots} snapshots · latest ${backup.latestBackup || '—'}`
          : 'No backup status from the API',
      },
    ];
  });

  readonly healthSummary = computed(() => {
    const known = this.healthRows().filter((row) => row.tone !== 'neutral');
    if (!known.length) return { label: 'Status unavailable', tone: 'neutral' };
    if (known.some((row) => row.tone === 'danger')) return { label: 'Needs attention', tone: 'danger' };
    return { label: 'Measured services healthy', tone: 'success' };
  });

  readonly distributionTotal = computed(() => {
    const list = this.businesses();
    if (list.length) return String(list.length);
    const total = this.summary()?.totalBusinesses;
    return total != null ? String(total) : '—';
  });

  /** Says so when the loaded list is a subset of the platform total, instead of implying it is all of it. */
  readonly distributionScopeLabel = computed(() => {
    const list = this.businesses();
    const total = this.summary()?.totalBusinesses;
    if (list.length && total != null && list.length !== total) {
      return `Showing ${list.length} of ${total} outlets.`;
    }
    return 'Breakdown across live system states.';
  });

  readonly distributionTabs = ['By Status', 'By Activity'] as const;
  readonly distributionMode = signal<'By Status' | 'By Activity'>('By Status');

  readonly ranges = ['7', '30', '90'] as const;
  range: '7' | '30' | '90' = '30';
  readonly growthMetric = signal<'restaurants' | 'orders'>('restaurants');

  readonly kpis = computed(() => {
    const s = this.summary();
    // No invented fallbacks: until the API answers a figure is "—", not a plausible number.
    const dash = '—';
    const liveShare =
      s && s.totalBusinesses > 0 && s.liveBusinesses != null
        ? `${((s.liveBusinesses / s.totalBusinesses) * 100).toFixed(1)}% of outlets`
        : 'live outlets';

    // Values use the Indian short scale so the tile holds its hierarchy at a
    // crore-scale revenue; the exact rupee figure lives in the hint (title).
    return [
      {
        label: 'Restaurants',
        value: s ? String(s.totalBusinesses) : dash,
        hint: s ? `${s.totalBusinesses.toLocaleString('en-IN')} registered outlets` : 'Registered outlets',
        tone: 'brand',
        icon: 'M4 4h16v6H4zm0 8h7v8H4zm9 0h7v8h-7z',
        caption: 'registered outlets',
      },
      {
        label: 'Users',
        value: s ? String(s.totalStaff) : dash,
        hint: s ? `${s.totalStaff.toLocaleString('en-IN')} platform accounts` : 'Platform accounts',
        tone: 'success',
        icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-4 0-8 2-8 5v3h16v-3c0-3-4-5-8-5z',
        caption: 'platform accounts',
      },
      {
        label: 'Platform Revenue',
        value: s ? formatCompactInr(s.totalRevenue) : dash,
        hint: s ? `${formatInr(s.totalRevenue)} settled volume` : 'Settled payment volume',
        tone: 'brand',
        icon: 'M7 3h10v18H7zm3 3v2m4-2v2m-4 9v2m4-2v2m-2-7h.01',
        caption: 'settled volume',
      },
      {
        label: 'Orders',
        value: s ? s.totalOrders.toLocaleString('en-IN') : dash,
        hint: s ? `${s.totalOrders.toLocaleString('en-IN')} bills recorded` : 'Bills recorded',
        tone: 'warning',
        icon: 'M3 4h2l2 11h11l3-8H6m4 13a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm8 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4z',
        caption: 'bills recorded',
      },
      {
        label: 'Active Outlets',
        value: s && s.liveBusinesses != null ? String(s.liveBusinesses) : dash,
        hint:
          s && s.liveBusinesses != null
            ? `${s.liveBusinesses.toLocaleString('en-IN')} live outlets · ${liveShare}`
            : 'Live outlets',
        tone: 'brand',
        icon: 'M4 4h16v16H4zm4 4h8M8 12h8M8 16h5',
        caption: liveShare,
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
    const api = this.adminApi;
    if (!api) return;
    this.loading.set(true);
    this.loadFailed.set(false);
    const days = Number(this.range) || 30;

    // Each call degrades to null instead of failing the page: one dead endpoint
    // must not blank the dashboard, and a dead summary must not invent figures.
    forkJoin({
      summary: api.getDashboardSummary().pipe(catchError(() => of(null))),
      businesses: api.getBusinesses().pipe(catchError(() => of(null))),
      trends: api.getDailyTrends(days).pipe(catchError(() => of(null))),
      webhooks: api.getWebhookHealth().pipe(catchError(() => of(null))),
      backups: api.getBackupStatus().pipe(catchError(() => of(null))),
    }).subscribe(({ summary, businesses, trends, webhooks, backups }) => {
      this.loading.set(false);
      if (summary) {
        this.summary.set(summary);
      } else {
        this.loadFailed.set(true);
      }
      if (businesses) this.businesses.set(businesses);
      if (trends) this.dailyTrends.set(trends);
      if (backups) this.backups.set(backups);
      if (webhooks) {
        // The endpoint reports retry counts; it has no status field.
        const payload = webhooks as {
          status?: string; health?: string; deadLetterCount?: number;
        };
        const state = String(payload.status ?? payload.health ?? '').toLowerCase();
        const deadLettered = Number(payload.deadLetterCount) > 0;
        this.webhookState.set(
          deadLettered || state.includes('degrad') || state.includes('fail') ? 'degraded' : 'ok',
        );
      }
      if (summary || businesses || trends) this.lastUpdated.set(new Date());
    });
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
    // The summary endpoint has no range parameter, so this only re-reads the
    // series. Saying so is cheaper than a toast that claims the whole page moved.
    this.fetchDailyTrends();
    this.toast.show(`Charts updated for ${this.rangeLabel(this.range)}.`, 'info');
  }

  rangeLabel(range: string): string {
    return range === '90' ? 'Last 90 Days' : range === '7' ? 'Last 7 Days' : 'Last 30 Days';
  }

  /** Plot box in viewBox units; the SVG is rendered in the same pixels, so no label ever scales. */
  readonly trendPlot = computed(() => {
    const width = this.trendWidth();
    // 54px holds "₹66k" at 11px; 40px holds the right-hand counts.
    const left = 54;
    const right = Math.max(left + 80, width - 40);
    return { left, right, width: right - left, top: 8, bottom: 136, height: 152 };
  });

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
    const plot = this.trendPlot();

    for (let i = 0; i < count; i++) {
      const t = trends[i];
      const x = plot.left + Math.round((i / Math.max(1, count - 1)) * plot.width);
      const rev = Number(t.revenue) || 0;
      const ord = Number(t.orderCount) || 0;
      const scale = plot.bottom - plot.top;
      const yRev = Math.round(plot.bottom - (rev / maxRev) * scale);
      const yOrd = Math.round(plot.bottom - (ord / maxOrders) * scale);
      ptsRev.push({ x, y: Math.max(plot.top, Math.min(plot.bottom, yRev)) });
      ptsOrders.push({ x, y: Math.max(plot.top, Math.min(plot.bottom, yOrd)) });
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
    const plot = this.trendPlot();
    const at = (frac: number) => Math.round(plot.top + (plot.bottom - plot.top) * frac);
    return [
      { y: plot.top, label: `₹${Math.round(max / 1000)}k` },
      { y: at(0.34), label: `₹${Math.round((max * 0.66) / 1000)}k` },
      { y: at(0.67), label: `₹${Math.round((max * 0.33) / 1000)}k` },
      { y: plot.bottom, label: '₹0' },
    ];
  }

  get trendRTicks() {
    const max = this.trendData().maxOrders;
    const plot = this.trendPlot();
    return [
      { y: plot.top, label: String(Math.round(max)) },
      { y: Math.round(plot.top + (plot.bottom - plot.top) / 2), label: String(Math.round(max / 2)) },
      { y: plot.bottom, label: '0' },
    ];
  }

  /** The sample under the pointer, with both series resolved for the tooltip. */
  readonly trendHover = computed(() => {
    const i = this.hoverTrendIndex();
    const rows = this.dailyTrends();
    const d = this.trendData();
    const rev = i != null ? d.ptsRev[i] : undefined;
    const ord = i != null ? d.ptsOrders[i] : undefined;
    const row = i != null ? rows[i] : undefined;
    if (!rev || !ord || !row) return null;
    return {
      x: rev.x,
      yRev: rev.y,
      yOrders: ord.y,
      // Keep the tooltip's centre inside the card: half a tooltip at each edge.
      tipX: Math.min(this.trendWidth() - 64, Math.max(64, rev.x)),
      date: formatShortDate(row.date),
      revenue: formatInr(Number(row.revenue) || 0),
      orders: (Number(row.orderCount) || 0).toLocaleString('en-IN'),
    };
  });

  /** Pointer events cover mouse, pen and touch; a finger drag scrubs the sample under it. */
  onTrendPointer(event: PointerEvent): void {
    const rows = this.dailyTrends();
    if (!rows.length) return;
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
    if (!rect.width) return;
    const plot = this.trendPlot();
    const viewX = ((event.clientX - rect.left) / rect.width) * this.trendWidth();
    const frac = Math.min(1, Math.max(0, (viewX - plot.left) / plot.width));
    this.hoverTrendIndex.set(Math.round(frac * (rows.length - 1)));
    if (event.pointerType !== 'mouse') this.pinTrend.set(true);
  }

  onTrendLeave(event: PointerEvent): void {
    // A finger lifting is not "moving away": keep the value until the next tap elsewhere.
    if (event.pointerType !== 'mouse') return;
    this.hoverTrendIndex.set(null);
  }

  onGrowthPointer(event: PointerEvent): void {
    const bars = this.growthBars();
    if (!bars.length) return;
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (!rect.width) return;
    const index = Math.floor(((event.clientX - rect.left) / rect.width) * bars.length);
    this.hoverGrowth.set(bars[Math.min(bars.length - 1, Math.max(0, index))]);
    if (event.pointerType !== 'mouse') this.pinGrowth.set(true);
  }

  onGrowthLeave(event: PointerEvent): void {
    if (event.pointerType !== 'mouse') return;
    this.hoverGrowth.set(null);
  }

  /** A tap anywhere off the charts dismisses a pinned (touch) readout. */
  onDocumentPointerDown(event: PointerEvent): void {
    if (this.hoverGrowth() == null && this.hoverTrendIndex() == null) return;
    const target = event.target as Element | null;
    if (target?.closest('.bar-chart, .trend-chart-wrap')) return;
    this.hoverGrowth.set(null);
    this.hoverTrendIndex.set(null);
    this.pinGrowth.set(false);
    this.pinTrend.set(false);
  }

  onGrowthFocus(): void {
    const bars = this.growthBars();
    if (bars.length && !this.hoverGrowth()) this.hoverGrowth.set(bars[bars.length - 1]);
  }

  onGrowthKey(event: KeyboardEvent): void {
    const bars = this.growthBars();
    if (!bars.length) return;
    let index = this.hoverGrowth()?.x ?? bars.length - 1;
    if (event.key === 'ArrowLeft') index = Math.max(0, index - 1);
    else if (event.key === 'ArrowRight') index = Math.min(bars.length - 1, index + 1);
    else if (event.key === 'Home') index = 0;
    else if (event.key === 'End') index = bars.length - 1;
    else if (event.key === 'Escape') {
      this.hoverGrowth.set(null);
      this.pinGrowth.set(false);
      return;
    } else return;
    event.preventDefault();
    this.hoverGrowth.set(bars[index]);
  }

  onTrendFocus(): void {
    const rows = this.dailyTrends();
    if (rows.length && this.hoverTrendIndex() == null) this.hoverTrendIndex.set(rows.length - 1);
  }

  onTrendKey(event: KeyboardEvent): void {
    const rows = this.dailyTrends();
    if (!rows.length) return;
    let index = this.hoverTrendIndex() ?? rows.length - 1;
    if (event.key === 'ArrowLeft') index = Math.max(0, index - 1);
    else if (event.key === 'ArrowRight') index = Math.min(rows.length - 1, index + 1);
    else if (event.key === 'Home') index = 0;
    else if (event.key === 'End') index = rows.length - 1;
    else if (event.key === 'Escape') {
      this.hoverTrendIndex.set(null);
      this.pinTrend.set(false);
      return;
    } else return;
    event.preventDefault();
    this.hoverTrendIndex.set(index);
  }

  trendAriaLabel(): string {
    const d = this.trendData();
    const span = d.startDate ? `, ${d.startDate} to ${d.endDate}` : '';
    return `Revenue and settled bills for ${this.rangeLabel(this.range)}${span}`;
  }

  readonly growthBars = computed<GrowthBar[]>(() => {
    const trends = this.dailyTrends();
    if (!trends.length) return [];
    const isOrders = this.growthMetric() === 'orders';
    const values = trends.map((t) => (isOrders ? Number(t.orderCount) || 0 : Number(t.newRegistrations) || 0));
    const maxVal = Math.max(...values, 1);
    const count = values.length;
    return values.map((val, idx) => ({
      x: idx,
      height: Math.max(val > 0 ? 8 : 2, Math.round((val / maxVal) * 100)),
      valueLabel: `${val.toLocaleString('en-IN')} ${isOrders ? 'bills' : 'outlets'}`,
      dateLabel: formatShortDate(trends[idx]?.date),
      left: Math.min(90, Math.max(10, ((idx + 0.5) / count) * 100)),
    }));
  });

  /** Three ticks against the same max the bars are scaled to. */
  readonly growthYTicks = computed<string[]>(() => {
    const trends = this.dailyTrends();
    if (!trends.length) return ['—', '—', '0'];
    const isOrders = this.growthMetric() === 'orders';
    const max = Math.max(
      ...trends.map((t) => (isOrders ? Number(t.orderCount) || 0 : Number(t.newRegistrations) || 0)),
      1,
    );
    const fmt = (n: number) => n.toLocaleString('en-IN');
    return [fmt(max), fmt(Math.round(max / 2)), '0'];
  });

  growthTicks(): string[] {
    const d = this.trendData();
    return [d.startDate || 'Start', d.midDate || 'Mid', d.endDate || 'Today'];
  }

  growthAriaLabel(): string {
    const ticks = this.growthYTicks();
    const peak = ticks[0] === '—' ? '' : `, peak ${ticks[0]}`;
    return `Operational ${this.growthMetric()} volume for the last ${this.range} days${peak}`;
  }

  revenueLine(): string {
    return this.trendData().ptsRev.map((p) => `${p.x},${p.y}`).join(' ');
  }

  revenueArea(): string {
    const pts = this.trendData().ptsRev;
    if (!pts.length) return '';
    const base = this.trendPlot().bottom;
    return [...pts.map((p) => `${p.x},${p.y}`), `${pts[pts.length - 1].x},${base}`, `${pts[0].x},${base}`].join(' ');
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
    const total = list.length;
    const pct = (n: number) => `${((n / total) * 100).toFixed(1)}%`;

    if (!total) {
      // No rows loaded: state the absence instead of inventing a 139-outlet split.
      return this.distributionMode() === 'By Activity'
        ? [
            { name: 'With Settled Bills', count: null, share: '—', tone: 'seg-2' },
            { name: 'New / Onboarding', count: null, share: '—', tone: 'seg-1' },
          ]
        : [
            { name: 'Active Outlets', count: null, share: '—', tone: 'seg-2' },
            { name: 'Suspended Outlets', count: null, share: '—', tone: 'seg-4' },
          ];
    }

    if (this.distributionMode() === 'By Activity') {
      const withOrders = list.filter((b) => (b.orderCount || 0) > 0).length;
      const noOrders = total - withOrders;
      return [
        { name: 'With Settled Bills', count: withOrders, share: pct(withOrders), tone: 'seg-2' },
        { name: 'New / Onboarding', count: noOrders, share: pct(noOrders), tone: 'seg-1' },
      ];
    }
    const active = list.filter((b) => !b.isSuspended).length;
    const suspended = total - active;
    return [
      { name: 'Active Outlets', count: active, share: pct(active), tone: 'seg-2' },
      { name: 'Suspended Outlets', count: suspended, share: pct(suspended), tone: 'seg-4' },
    ];
  });

  private readonly SEGMENT_COLORS: Record<string, string> = {
    'seg-1': 'var(--kb-chart-3)',
    'seg-2': 'var(--kb-chart-4)',
    'seg-4': 'var(--danger)',
  };

  readonly distributionDonut = computed(() => {
    const rows = this.distributionLegend();
    const total = rows.reduce((sum, row) => sum + (row.count ?? 0), 0) || 1;
    const circumference = 2 * Math.PI * 46;
    let offset = 0;
    return rows.map((row) => {
      const length = ((row.count ?? 0) / total) * circumference;
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
