import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ToastService } from '../../core/services/toast.service';

/** Sample figures match the fused reference A01 frames so the prototype reads exactly like the approved design. */
const REFERENCE_KPIS = [
  { label: 'Total Restaurants', value: '124', tone: 'brand', icon: 'M4 4h16v6H4zm0 8h7v8H4zm9 0h7v8h-7z', delta: '+12.7%', up: true },
  { label: 'Total Users', value: '582', tone: 'success', icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-4 0-8 2-8 5v3h16v-3c0-3-4-5-8-5z', delta: '+18.4%', up: true },
  { label: 'Total Platform Revenue', value: '\u20B93,24,650', tone: 'info', icon: 'M7 3h10v18H7zm3 3v2m4-2v2m-4 9v2m4-2v2m-2-7h.01', delta: '+22.1%', up: true },
  { label: 'Total Orders', value: '15,842', tone: 'warning', icon: 'M3 4h2l2 11h11l3-8H6m4 13a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm8 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4z', delta: '+19.6%', up: true },
  { label: 'Active Subscriptions', value: '98', tone: 'danger', icon: 'M4 4h16v16H4zm4 4h8M8 12h8M8 16h5', delta: '-15.3%', up: false },
] as const;

/**
 * Deterministic 90-day sample series (seeded LCG — no Math.random, so every
 * render of the same range is pixel-identical, per the deterministic-render
 * rule for prototypes).
 */
function seededSeries(days: number, min: number, max: number): number[] {
  let seed = 42;
  const next = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };
  return Array.from({ length: days }, (_, i) => {
    const wave = Math.sin(i / 4.5) * 0.22 + (i % 7 === 6 ? 0.18 : 0);
    return Math.round((min + (max - min) * next()) * (1 + wave));
  });
}

const GROWTH_90 = seededSeries(90, 2, 18);
const REVENUE_90 = seededSeries(90, 14000, 41000);
const ORDERS_90 = seededSeries(90, 180, 780);

const DAY_LABELS = (() => {
  const labels: string[] = [];
  // 90-day window ending 26 Oct 2026, so the default 30-day slice reads
  // 27 Sep..26 Oct — the reference frame's axis.
  const start = new Date(2026, 6, 29);
  for (let d = 0; d < 90; d++) {
    const date = new Date(start.getTime() + d * 86400000);
    labels.push(`${date.getDate()} ${['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][date.getMonth()]}`);
  }
  return labels;
})();

/** Reference "By City" legend (counts + shares printed in the frame). */
const CITY_DISTRIBUTION = [
  { name: 'Chennai', count: 28, share: '22.6%', tone: 'seg-1' },
  { name: 'Bengaluru', count: 24, share: '19.4%', tone: 'seg-2' },
  { name: 'Hyderabad', count: 18, share: '14.5%', tone: 'seg-3' },
  { name: 'Mumbai', count: 16, share: '12.9%', tone: 'seg-4' },
  { name: 'Delhi', count: 12, share: '9.7%', tone: 'seg-5' },
  { name: 'Others', count: 26, share: '21.0%', tone: 'seg-6' },
];

const PLAN_DISTRIBUTION = [
  { name: 'Starter', count: 38, share: '38.8%', tone: 'seg-1' },
  { name: 'Professional', count: 32, share: '32.7%', tone: 'seg-2' },
  { name: 'Business', count: 18, share: '18.4%', tone: 'seg-3' },
  { name: 'Enterprise', count: 6, share: '6.1%', tone: 'seg-4' },
  { name: 'Trial', count: 4, share: '4.1%', tone: 'seg-5' },
];

const STATUS_DISTRIBUTION = [
  { name: 'Active', count: 82, share: '83.7%', tone: 'seg-2' },
  { name: 'Trial', count: 10, share: '10.2%', tone: 'seg-1' },
  { name: 'Expired', count: 6, share: '6.1%', tone: 'seg-4' },
];

const TOP_RESTAURANTS = [
  { rank: 1, name: 'Spice Villa', city: 'Chennai', orders: 1245, revenue: '\u20B92,84,950' },
  { rank: 2, name: 'Dine Hub', city: 'Bengaluru', orders: 980, revenue: '\u20B91,96,800' },
  { rank: 3, name: 'Tasty Bites', city: 'Hyderabad', orders: 842, revenue: '\u20B91,62,300' },
  { rank: 4, name: 'Food Junction', city: 'Mumbai', orders: 654, revenue: '\u20B91,28,900' },
  { rank: 5, name: 'Royal Kitchen', city: 'Coimbatore', orders: 612, revenue: '\u20B91,14,650' },
];

const HEALTH_ROWS = [
  { name: 'API Servers', uptime: '99.9%' },
  { name: 'Database', uptime: '99.9%' },
  { name: 'File Storage', uptime: '100%' },
  { name: 'Push Notifications', uptime: '94.8%' },
  { name: 'Payment Gateways', uptime: '99.9%' },
  { name: 'Backup Service', uptime: '100%' },
];

const RECENT_RESTAURANTS = [
  { name: 'Cafe Corner', owner: 'Ravi Kumar', city: 'Chennai', plan: 'Professional', status: 'Active', created: '28 Oct 2026, 10:24 am' },
  { name: 'Dosa Plaza', owner: 'Suresh M', city: 'Bengaluru', plan: 'Starter', status: 'Active', created: '27 Oct 2026, 04:15 pm' },
  { name: 'Tiffin Box', owner: 'Priya S', city: 'Hyderabad', plan: 'Professional', status: 'Pending', created: '27 Oct 2026, 09:30 am' },
  { name: 'The Grill House', owner: 'Arun R', city: 'Chennai', plan: 'Business', status: 'Active', created: '26 Oct 2026, 02:18 pm' },
  { name: 'Madras Meals', owner: 'Karthik V', city: 'Coimbatore', plan: 'Starter', status: 'Active', created: '26 Oct 2026, 11:52 am' },
];

const RECENT_TICKETS = [
  { subject: 'Printer not working', restaurant: 'Spice Villa', status: 'Open' },
  { subject: 'GST configuration', restaurant: 'Dosa Plaza', status: 'In Progress' },
  { subject: 'Add new staff', restaurant: 'Cafe Corner', status: 'Resolved' },
  { subject: 'Payment issue', restaurant: 'Tasty Bites', status: 'Open' },
  { subject: 'App pairing issue', restaurant: 'The Grill House', status: 'Resolved' },
];

interface LegendSegment { name: string; count: number; share: string; tone: string; }

/**
 * Admin → Platform Admin Dashboard (reference A01, 100% reference language).
 *
 * Fidelity note: the previous iteration of this screen was a four-tab
 * operations console (Summary / Settlements & Commissions / Sub-Merchants &
 * KYC / Gateway Transactions). The fused reference has none of those tabs —
 * it is a single scroll — so the tab content was retired here in favour of
 * the reference structure; the settlement/sub-merchant endpoints remain in
 * AdminApiService for other surfaces. Sample figures match the reference
 * frame and are labelled as prototype data in the header chip; every control
 * EITHER navigates or acts on local sample data, never against a live API.
 */
@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="page-shell dash">
      <header class="dash-head">
        <div class="dash-head__copy">
          <h1>Platform Admin Dashboard</h1>
          <p>Monitor all restaurants, usage, revenue and platform health.</p>
          <span class="chip warn">Prototype sample — no API yet</span>
        </div>
        <div class="dash-head__controls">
          <span class="date-pill" role="img" aria-label="Date range 01 Oct 2026 to 26 Oct 2026">
            <span class="cal" aria-hidden="true">&#128197;</span> 01 Oct 2026 – 26 Oct 2026
          </span>
          <label class="range-select" aria-label="Date range">
            <select name="range" [(ngModel)]="range" (ngModelChange)="onRangeChange()">
              @for (option of ranges; track option) { <option [value]="option">{{ rangeLabel(option) }}</option> }
            </select>
          </label>
          <span class="bell" title="Notifications" aria-label="Notification centre has alerts">
            <span class="bell__dot" aria-hidden="true"></span>
          </span>
        </div>
      </header>

      <!-- Reference KPI band: five tiles, icon square + headline + delta chip -->
      <section class="kpi-band" aria-label="Platform summary">
        @for (kpi of kpis; track kpi.label) {
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
                <span class="muted">vs previous 30 days</span>
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
              <p>New restaurants registered over time.</p>
            </div>
            <label class="metric-select" aria-label="Growth metric">
              <select name="growthMetric" [(ngModel)]="growthMetric" (ngModelChange)="onGrowthMetricChange()">
                <option value="restaurants">New Restaurants</option>
                <option value="orders">New Orders</option>
              </select>
            </label>
          </div>
          <div class="bar-chart" role="img" [attr.aria-label]="growthAriaLabel()">
            @for (bar of growthBars(); track bar.x) {
              <div class="bar" [style.height.%]="bar.height"></div>
            }
          </div>
          <div class="axis-row" aria-hidden="true">
            @for (tick of growthTicks(); track tick) { <span class="axis-tick">{{ tick }}</span> }
          </div>
        </article>

        <article class="panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>Revenue Trend</h2>
              <p>Platform revenue and orders.</p>
            </div>
          </div>
          <svg class="trend-svg" viewBox="0 0 560 200" preserveAspectRatio="none" role="img" aria-label="Revenue and orders trend for the selected range">
            <defs>
              <linearGradient id="revenueGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="var(--kb-chart-3)" stop-opacity="0.30"/>
                <stop offset="100%" stop-color="var(--kb-chart-3)" stop-opacity="0"/>
              </linearGradient>
            </defs>
            <g class="chart-grid-group">
              @for (tick of trendYTicks; track tick.y) {
                <text x="4" [attr.y]="tick.y + 4" class="chart-axis-label">{{ tick.label }}</text>
                <line x1="42" [attr.y1]="tick.y" x2="500" [attr.y2]="tick.y" class="chart-grid-line" />
              }
              @for (tick of trendRTicks; track tick.y) {
                <text x="532" [attr.y]="tick.y + 4" class="chart-axis-label">{{ tick.label }}</text>
              }
            </g>
            <polygon fill="url(#revenueGrad)" [attr.points]="revenueArea()" aria-hidden="true" />
            <polyline fill="none" stroke="var(--kb-chart-3)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" [attr.points]="revenueLine()" />
            <polyline fill="none" stroke="var(--kb-chart-1)" stroke-width="2" stroke-dasharray="1 0" stroke-linecap="round" [attr.points]="ordersLine()" />
            @for (point of trendPoints(); track point.x) {
              <circle [attr.cx]="point.x" [attr.cy]="point.y" r="3" class="chart-point" />
            }
          </svg>
          <div class="trend-legend" aria-label="Series legend">
            <span class="legend-dot legend-dot--revenue"></span> Revenue (\u20B9)
            <span class="legend-dot legend-dot--orders"></span> Orders
          </div>
          <div class="axis-row" aria-hidden="true">
            @for (tick of trendXTicks(); track tick) { <span class="axis-tick">{{ tick }}</span> }
          </div>
        </article>

        <article class="panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>Restaurant Distribution</h2>
            </div>
            <div class="dist-tabs" role="tablist" aria-label="Distribution mode">
              @for (tab of distributionTabs; track tab) {
                <button type="button" class="status-tab" role="tab" [class.active]="distributionMode() === tab" [attr.aria-selected]="distributionMode() === tab" (click)="distributionMode.set(tab)">{{ tab }}</button>
              }
            </div>
          </div>
          <div class="donut-row">
            <div class="donut-svg-box" aria-hidden="true">
              <svg class="donut-svg" viewBox="0 0 120 120">
                @for (segment of distributionDonut(); track segment.label) {
                  <circle
                    cx="60" cy="60" r="46" fill="none"
                    [attr.stroke]="segment.color"
                    stroke-width="14"
                    [attr.stroke-dasharray]="segment.dash"
                    [attr.stroke-dashoffset]="segment.offset"
                    transform="rotate(-90 60 60)"
                    class="donut-seg"
                  />
                }
                <circle cx="60" cy="60" r="30" fill="var(--kb-color-surface)" />
              </svg>
              <div class="donut-center">
                <strong>124</strong>
                <span>Restaurants</span>
              </div>
            </div>
            <ul class="donut-legend">
              @for (row of distributionLegend(); track row.name) {
                <li>
                  <span class="legend-dot" [class]="'legend-dot--' + row.tone"></span>
                  <strong>{{ row.name }}</strong>
                  <span class="muted">{{ row.count }} ({{ row.share }})</span>
                </li>
              }
            </ul>
          </div>
        </article>
      </section>

      <!-- Middle row: top performers / plans / health -->
      <section class="mid-row">
        <article class="panel table-panel">
          <div class="panel-head">
            <div><h2>Top Performing Restaurants</h2></div>
            <a routerLink="/admin/sales" class="view-link">View All</a>
          </div>
          <table class="data-table">
            <thead>
              <tr><th>#</th><th>Restaurant</th><th>City</th><th>Orders</th><th>Revenue (\u20B9)</th></tr>
            </thead>
            <tbody>
              @for (row of topRestaurants; track row.rank) {
                <tr>
                  <td class="rank-cell"><span class="rank">{{ row.rank }}</span></td>
                  <td><strong>{{ row.name }}</strong></td>
                  <td>{{ row.city }}</td>
                  <td>{{ row.orders }}</td>
                  <td class="amount">{{ row.revenue }}</td>
                </tr>
              }
            </tbody>
          </table>
        </article>

        <article class="panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>Subscription Plans</h2>
              <p><a routerLink="/admin/subscriptions" class="view-link">Active subscriptions by plan type.</a></p>
            </div>
          </div>
          <div class="donut-row">
            <div class="donut-svg-box" aria-hidden="true">
              <svg class="donut-svg" viewBox="0 0 120 120">
                @for (segment of plansDonut(); track segment.label) {
                  <circle
                    cx="60" cy="60" r="46" fill="none"
                    [attr.stroke]="segment.color"
                    stroke-width="14"
                    [attr.stroke-dasharray]="segment.dash"
                    [attr.stroke-dashoffset]="segment.offset"
                    transform="rotate(-90 60 60)"
                    class="donut-seg"
                  />
                }
                <circle cx="60" cy="60" r="30" fill="var(--kb-color-surface)" />
              </svg>
              <div class="donut-center">
                <strong>98</strong>
                <span>Subscriptions</span>
              </div>
            </div>
            <ul class="donut-legend">
              @for (row of PLAN_DISTRIBUTION; track row.name) {
                <li>
                  <span class="legend-dot" [class]="'legend-dot--' + row.tone"></span>
                  <strong>{{ row.name }}</strong>
                  <span class="muted">{{ row.count }} ({{ row.share }})</span>
                </li>
              }
            </ul>
          </div>
        </article>

        <article class="panel table-panel">
          <div class="panel-head">
            <div><h2>System Health</h2></div>
            <span class="chip success">All Systems Operational</span>
          </div>
          <ul class="health-list">
            @for (row of healthRows; track row.name) {
              <li>
                <span class="health-name">{{ row.name }}</span>
                <span class="chip success">Healthy</span>
                <span class="muted health-uptime">{{ row.uptime }}</span>
              </li>
            }
          </ul>
        </article>
      </section>

      <!-- Bottom row: recent restaurants / tickets / quick actions -->
      <section class="bottom-row">
        <article class="panel table-panel">
          <div class="panel-head">
            <div><h2>Recent Restaurants</h2></div>
            <a routerLink="/admin/businesses" class="view-link">View All</a>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr><th>#</th><th>Restaurant</th><th>Owner</th><th>City</th><th>Plan</th><th>Status</th><th>Created On</th></tr>
              </thead>
              <tbody>
                @for (row of recentRestaurants; track row.name; let i = $index) {
                  <tr>
                    <td class="idx-col">{{ i + 1 }}</td>
                    <td><strong>{{ row.name }}</strong></td>
                    <td>{{ row.owner }}</td>
                    <td>{{ row.city }}</td>
                    <td><span class="chip" [class.brand-chip]="row.plan === 'Professional'">{{ row.plan }}</span></td>
                    <td>
                      <span class="chip" [class.success]="row.status === 'Active'" [class.warn]="row.status === 'Pending'">
                        {{ row.status }}
                      </span>
                    </td>
                    <td>{{ row.created }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </article>

        <article class="panel table-panel">
          <div class="panel-head">
            <div><h2>Recent Support Tickets</h2></div>
            <a routerLink="/admin/support-tickets" class="view-link">View All</a>
          </div>
          <table class="data-table">
            <thead>
              <tr><th>#</th><th>Subject</th><th>Restaurant</th><th>Status</th></tr>
            </thead>
            <tbody>
              @for (row of recentTickets; track row.subject; let i = $index) {
                <tr>
                  <td class="idx-col">{{ i + 1 }}</td>
                  <td><strong>{{ row.subject }}</strong></td>
                  <td>{{ row.restaurant }}</td>
                  <td>
                    <span class="chip" [class.danger]="row.status === 'Open'" [class.warn]="row.status === 'In Progress'" [class.success]="row.status === 'Resolved'">
                      {{ row.status }}
                    </span>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </article>

        <article class="panel quick-panel">
          <div class="panel-head">
            <div><h2>Quick Actions</h2></div>
          </div>
          <div class="quick-grid">
            <a routerLink="/admin/businesses" class="quick-tile quick-tile--primary">
              <span class="quick-plus" aria-hidden="true">+</span>
              Add New<br />Restaurant
            </a>
            <a routerLink="/admin/feature-flags" class="quick-tile quick-tile--flag">
              <span aria-hidden="true">&#9873;</span>
              Manage Feature<br />Flags
            </a>
            <a routerLink="/admin/reports" class="quick-tile">
              <span aria-hidden="true">&#9636;</span>
              View Reports
            </a>
            <a routerLink="/admin/settings" class="quick-tile">
              <span aria-hidden="true">&#9881;</span>
              Platform<br />Settings
            </a>
          </div>
        </article>
      </section>
    </div>
  `,
  styles: [
    `
      .dash { gap: var(--kb-space-5); }
      .dash-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--kb-space-4); }
      .dash-head__copy h1 { margin: 0; font-size: var(--kb-text-2xl); font-weight: 800; color: var(--ink); }
      .dash-head__copy p { margin: var(--kb-space-1) 0 0; color: var(--muted); font-size: var(--kb-text-sm); }
      .dash-head__controls { display: flex; align-items: center; gap: var(--kb-space-3); }
      .date-pill {
        display: inline-flex; align-items: center; gap: var(--kb-space-2);
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        background: var(--kb-color-surface); color: var(--ink);
        padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm); font-weight: 650;
      }
      .range-select select {
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        background: var(--kb-color-input); color: var(--ink);
        padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm);
      }
      .range-select select:focus { outline: 2px solid var(--brand); outline-offset: 1px; }
      .bell { position: relative; display: grid; place-items: center; width: 36px; height: 36px; border-radius: var(--kb-radius-full); border: 1px solid var(--line); background: var(--kb-color-surface); }
      .bell__dot { position: absolute; top: 7px; right: 8px; width: 8px; height: 8px; border-radius: var(--kb-radius-full); background: var(--danger); }
      .kpi-band { display: grid; grid-template-columns: repeat(5, 1fr); gap: var(--kb-space-4); }
      @media (max-width: 1280px) { .kpi-band { grid-template-columns: repeat(3, 1fr); } }
      @media (max-width: 720px)  { .kpi-band { grid-template-columns: 1fr; } }
      .kpi-card { display: flex; gap: var(--kb-space-3); align-items: flex-start; background: var(--kb-color-surface); border: 1px solid var(--line); border-radius: var(--kb-radius-lg); padding: var(--kb-space-4); }
      .kpi-card__icon { flex: none; width: 46px; height: 46px; border-radius: var(--kb-radius-md); display: grid; place-items: center; }
      .kpi-card__icon--brand   { background: var(--brand-soft); color: var(--brand); }
      .kpi-card__icon--success { background: var(--success-soft); color: var(--success); }
      .kpi-card__icon--info    { background: var(--info-soft); color: var(--info); }
      .kpi-card__icon--warning { background: var(--warning-soft); color: var(--warning); }
      .kpi-card__icon--danger  { background: var(--danger-soft); color: var(--danger); }
      .kpi-card__copy { display: grid; gap: var(--kb-space-1); }
      .kpi-card__label { font-size: var(--kb-text-xs); color: var(--muted); text-transform: none; }
      .kpi-card__value { font-size: var(--kb-text-xl); font-weight: 800; color: var(--ink); }
      .kpi-card__delta { font-size: var(--kb-text-xs); color: var(--success); font-weight: 650; display: flex; gap: var(--kb-space-1); align-items: baseline; flex-wrap: wrap; }
      .kpi-card__delta.down { color: var(--danger); }
      .kpi-card__delta .muted { text-transform: none; }
      .charts-row { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); }
      @media (min-width: 1200px) { .charts-row { grid-template-columns: 1.05fr 1.35fr 1fr; align-items: stretch; } }
      .chart-panel, .table-panel { padding: var(--kb-space-4) var(--kb-space-5) var(--kb-space-5); }
      .panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--kb-space-3); margin-bottom: var(--kb-space-4); }
      .panel-head h2 { margin: 0; font-size: var(--kb-text-md); font-weight: 750; color: var(--ink); }
      .panel-head p { margin: 2px 0 0; font-size: var(--kb-text-xs); color: var(--muted); }
      .metric-select select {
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        background: var(--kb-color-input); color: var(--ink);
        padding: var(--kb-space-1) var(--kb-space-3); font-size: var(--kb-text-xs);
      }
      .metric-select select:focus { outline: 2px solid var(--brand); outline-offset: 1px; }
      .bar-chart { display: flex; align-items: flex-end; gap: var(--kb-space-1); height: 150px; }
      .bar { flex: 1; min-width: 4px; background: var(--kb-chart-3); border-radius: var(--kb-radius-sm) var(--kb-radius-sm) 0 0; }
      .axis-row { display: flex; justify-content: space-between; margin-top: var(--kb-space-2); color: var(--muted); font-size: var(--kb-text-xs); }
      .trend-svg { width: 100%; height: 170px; }
      .trend-legend { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-xs); color: var(--muted); margin-top: var(--kb-space-2); }
      .legend-dot { display: inline-block; width: 10px; height: 10px; border-radius: var(--kb-radius-full); background: var(--kb-chart-3); }
      .legend-dot--revenue { background: var(--kb-chart-3); }
      .legend-dot--orders { background: var(--kb-chart-1); margin-left: var(--kb-space-3); }
      .legend-dot--seg-1 { background: var(--kb-chart-3); }
      .legend-dot--seg-2 { background: var(--kb-chart-4); }
      .legend-dot--seg-3 { background: var(--kb-chart-2); }
      .legend-dot--seg-4 { background: var(--kb-chart-1); }
      .legend-dot--seg-5 { background: var(--kb-chart-5); }
      .legend-dot--seg-6 { background: var(--danger); }
      .dist-tabs { display: flex; gap: var(--kb-space-2); }
      .donut-row { display: flex; gap: var(--kb-space-4); align-items: center; }
      .donut-svg-box { position: relative; width: 150px; height: 150px; flex: none; }
      .donut-svg { width: 100%; height: 100%; }
      .donut-seg { transition: none; }
      .donut-center { position: absolute; inset: 0; display: grid; place-content: center; text-align: center; pointer-events: none; }
      .donut-center strong { font-size: var(--kb-text-lg); color: var(--ink); }
      .donut-center span { font-size: var(--kb-text-xs); color: var(--muted); }
      .donut-legend { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--kb-space-2); font-size: var(--kb-text-xs); }
      .donut-legend li { display: flex; align-items: center; gap: var(--kb-space-2); }
      .mid-row { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); }
      @media (min-width: 1200px) { .mid-row { grid-template-columns: 1.2fr 1fr 1fr; align-items: stretch; } }
      .bottom-row { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); }
      @media (min-width: 1200px) { .bottom-row { grid-template-columns: 1.4fr 1fr 0.8fr; align-items: stretch; } }
      .rank-cell { text-align: center; }
      .rank { display: inline-grid; place-items: center; width: 22px; height: 22px; border-radius: var(--kb-radius-full); background: var(--brand-soft); color: var(--brand); font-weight: 700; }
      .amount { font-weight: 700; }
      .view-link { font-size: var(--kb-text-xs); color: var(--brand); font-weight: 650; text-decoration: none; }
      .view-link:hover { text-decoration: underline; }
      .health-list { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--kb-space-3); }
      .health-list li { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); }
      .health-name { flex: 1; color: var(--ink); }
      .health-uptime { font-size: var(--kb-text-xs); min-width: 42px; text-align: right; }
      .quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--kb-space-3); }
      .quick-tile {
        display: grid; place-content: center; text-align: center; gap: var(--kb-space-1);
        min-height: 84px; padding: var(--kb-space-3); border-radius: var(--kb-radius-lg);
        background: var(--kb-color-surface); border: 1px solid var(--line);
        color: var(--ink); font-size: var(--kb-text-xs); font-weight: 650; text-decoration: none;
      }
      .quick-tile:hover { border-color: var(--brand); }
      .quick-tile--primary { background: var(--brand); color: var(--kb-color-primary-foreground); border-color: var(--brand); }
      .quick-tile--flag { background: var(--brand-soft); color: var(--brand); border-color: transparent; }
      .quick-plus { font-size: var(--kb-text-xl); font-weight: 700; line-height: 1; }
      .brand-chip { background: var(--brand-soft); color: var(--brand); }
      .chip.brand-chip { font-size: var(--kb-text-xs); }
    `,
  ],
})
export class PlatformDashboardPageComponent {
  private readonly toast = inject(ToastService);

  readonly kpis = REFERENCE_KPIS;
  readonly PLAN_DISTRIBUTION = PLAN_DISTRIBUTION;
  readonly topRestaurants = TOP_RESTAURANTS;
  readonly healthRows = HEALTH_ROWS;
  readonly recentRestaurants = RECENT_RESTAURANTS;
  readonly recentTickets = RECENT_TICKETS;
  readonly distributionTabs = ['By City', 'By Plan', 'By Status'] as const;
  readonly distributionMode = signal<'By City' | 'By Plan' | 'By Status'>('By City');

  readonly ranges = ['7', '30', '90'] as const;
  range: '7' | '30' | '90' = '30';

  readonly growthMetric = signal<'restaurants' | 'orders'>('restaurants');

  /** Sample series and axis ticks derived deterministically from the chosen range. */
  private readonly series = computed(() => {
    const days = Number(this.range);
    return {
      growth: GROWTH_90.slice(-days),
      revenue: REVENUE_90.slice(-days),
      orders: ORDERS_90.slice(-days),
      labels: DAY_LABELS.slice(-days),
    };
  });

  readonly growthBars = computed(() => {
    const series = this.growthMetric() === 'restaurants' ? this.series().growth : this.series().orders;
    const max = Math.max(...series);
    return series.map((value, i) => ({ x: i, height: Math.round((value / max) * 100) }));
  });

  growthTicks(): string[] {
    const labels = this.series().labels;
    return [0, Math.floor(labels.length / 2), labels.length - 1].map((i) => labels[i]);
  }

  growthAriaLabel(): string {
    return `${this.growthMetric() === 'restaurants' ? 'New registrations' : 'New orders'} per day for the last ${this.range} days`;
  }

  onGrowthMetricChange(): void {
    this.toast.show('Sample series — both metrics are prototype data labelled in the header chip.', 'info');
  }

  onRangeChange(): void {
    this.toast.show(`Showing the last ${this.rangeLabel(this.range)} of the sample series.`, 'info');
  }

  rangeLabel(range: string): string {
    return range === '90' ? 'Last 90 Days' : range === '7' ? 'Last 7 Days' : 'Last 30 Days';
  }

  /** Chart geometry (kept in component, not inline styles — house rule). */
  readonly trendYTicks = [
    { y: 30, label: '\u20B940k' },
    { y: 60, label: '\u20B930k' },
    { y: 90, label: '\u20B920k' },
    { y: 120, label: '\u20B90' },
  ];
  readonly trendRTicks = [
    { y: 30, label: '800' },
    { y: 75, label: '400' },
    { y: 120, label: '0' },
  ];

  readonly trendPoints = computed(() => {
    const seg = this.series().revenue;
    const count = seg.length;
    const count2 = this.series().orders.length;
    const pts: { x: number; y: number }[] = [];
    for (let i = 0; i < count; i++) {
      const max = 41000;
      const y = 120 - (seg[i] / max) * 90;
      pts.push({ x: 42 + Math.round((i / (count - 1)) * 458), y: Math.round(y) });
    }
    void count2;
    return pts;
  });

  revenueLine(): string {
    return this.trendPoints().map((p) => `${p.x},${p.y}`).join(' ');
  }

  /** Area fill under the revenue line (baseline y=120). */
  revenueArea(): string {
    const pts = this.trendPoints();
    return [...pts.map((p) => `${p.x},${p.y}`), `${pts[pts.length - 1].x},120`, `${pts[0].x},120`].join(' ');
  }

  ordersLine(): string {
    const seg = this.series().orders;
    const count = seg.length;
    return seg
      .map((value, i) => {
        const x = 42 + Math.round((i / (count - 1)) * 458);
        const y = 120 - Math.round((value / 800) * 90);
        return `${x},${y}`;
      })
      .join(' ');
  }

  trendXTicks(): string[] {
    const labels = this.series().labels;
    return [0, Math.floor(labels.length / 2), labels.length - 1].map((i) => labels[i]);
  }

  readonly legend = computed<LegendSegment[]>(() => {
    switch (this.distributionMode()) {
      case 'By Plan': return PLAN_DISTRIBUTION;
      case 'By Status': return STATUS_DISTRIBUTION;
      default: return CITY_DISTRIBUTION;
    }
  });

  distributionLegend(): LegendSegment[] {
    return this.legend();
  }

  private readonly SEGMENT_COLORS: Record<string, string> = {
    'seg-1': 'var(--kb-chart-3)',
    'seg-2': 'var(--kb-chart-4)',
    'seg-3': 'var(--kb-chart-2)',
    'seg-4': 'var(--kb-chart-1)',
    'seg-5': 'var(--kb-chart-5)',
    'seg-6': 'var(--danger)',
  };

  private donutSegments(rows: LegendSegment[]) {
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
  }

  distributionDonut() {
    return this.donutSegments(this.legend());
  }

  plansDonut() {
    return this.donutSegments(PLAN_DISTRIBUTION);
  }
}
