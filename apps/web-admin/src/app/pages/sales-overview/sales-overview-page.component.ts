import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ToastService } from '../../core/services/toast.service';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';

/** Revenue series in ₹ thousands, matching the reference's violet bar chart. */
const REVENUE_BARS = [14, 22, 30, 18, 27, 34, 24, 38, 26, 31, 19, 42, 36, 28, 45, 33, 26, 39, 47, 31, 36, 24, 41, 48, 34, 39];
/** Orders series matching the reference's orange bar chart. */
const ORDER_BARS = [240, 310, 420, 280, 360, 480, 340, 520, 380, 450, 300, 560, 500, 410, 620, 470, 390, 540, 640, 430, 520, 350, 580, 700, 500, 610];

const DAY_LABELS = (() => {
  const labels: string[] = [];
  for (let d = 1; d <= 26; d++) labels.push(`${d} Oct`);
  return labels;
})();

interface TopRow { rank: number; name: string; orders: number; revenue: string; aov: string; growth: string; up: boolean }
interface CityRow { rank: number; city: string; restaurants: number; orders: number; revenue: string; growth: string; up: boolean }
interface RecentRow { id: number; order: string; restaurant: string; time: string; amount: string; mode: 'UPI' | 'Card' | 'Cash' | 'Online'; status: 'Completed' | 'Cancelled'; customer: string }

const TOP_ROWS: TopRow[] = [
  { rank: 1, name: 'Spice Villa', orders: 1245, revenue: '\u20B92,84,800', aov: '\u20B9228', growth: '+18.2%', up: true },
  { rank: 2, name: 'Dine Hub', orders: 980, revenue: '\u20B91,04,800', aov: '\u20B9201', growth: '+12.6%', up: true },
  { rank: 3, name: 'Tasty Bites', orders: 842, revenue: '\u20B91,42,200', aov: '\u20B9169', growth: '+9.4%', up: true },
  { rank: 4, name: 'Food Junction', orders: 654, revenue: '\u20B91,28,000', aov: '\u20B9187', growth: '+8.7%', up: true },
  { rank: 5, name: 'Royal Kitchen', orders: 612, revenue: '\u20B91,14,650', aov: '\u20B9187', growth: '+6.3%', up: true },
];

const CITY_ROWS: CityRow[] = [
  { rank: 1, city: 'Chennai', restaurants: 28, orders: 4070, revenue: '\u20B91,00,430', growth: '+14.3%', up: true },
  { rank: 2, city: 'Bengaluru', restaurants: 24, orders: 3980, revenue: '\u20B982,300', growth: '+11.6%', up: true },
  { rank: 3, city: 'Hyderabad', restaurants: 18, orders: 2640, revenue: '\u20B956,200', growth: '+9.8%', up: true },
  { rank: 4, city: 'Mumbai', restaurants: 16, orders: 2320, revenue: '\u20B948,900', growth: '+8.1%', up: true },
  { rank: 5, city: 'Delhi', restaurants: 12, orders: 1080, revenue: '\u20B934,800', growth: '+6.7%', up: true },
];

const RECENT_ROWS: RecentRow[] = [
  { id: 1, order: '#KB-10048', restaurant: 'Spice Villa', time: '26 Oct 2025, 10:24 AM', amount: '\u20B91,250', mode: 'UPI', status: 'Completed', customer: 'Rahul Kumar' },
  { id: 2, order: '#KB-10047', restaurant: 'Dine Hub', time: '26 Oct 2025, 09:18 AM', amount: '\u20B9980', mode: 'Card', status: 'Completed', customer: 'Priya S' },
  { id: 3, order: '#KB-10046', restaurant: 'Tasty Bites', time: '26 Oct 2025, 08:45 AM', amount: '\u20B91,340', mode: 'Cash', status: 'Completed', customer: 'Arjun R' },
  { id: 4, order: '#KB-10045', restaurant: 'Food Junction', time: '25 Oct 2025, 11:12 PM', amount: '\u20B91,120', mode: 'Online', status: 'Completed', customer: 'Karthik V' },
  { id: 5, order: '#KB-10044', restaurant: 'Royal Kitchen', time: '25 Oct 2025, 10:36 PM', amount: '\u20B9690', mode: 'UPI', status: 'Cancelled', customer: 'Divya L' },
];

const PAYMENT_MODES = [
  { name: 'UPI', share: 42.6, tone: 'seg-1' },
  { name: 'Card', share: 24.1, tone: 'seg-2' },
  { name: 'Cash', share: 18.7, tone: 'seg-3' },
  { name: 'Online', share: 10.5, tone: 'seg-4' },
  { name: 'Others', share: 4.1, tone: 'seg-5' },
];

/**
 * Admin → Sales Overview (reference, fused). Everything is labelled sample
 * data: the network endpoints behind sales have no roll-up yet, so the page
 * reproduces the reference frames honestly — the header chip says so — while
 * date-range pills, legend and local slices stay interactive.
 */
@Component({
  selector: 'app-sales-overview-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink, PageHeaderComponent, KpiRowComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Sales Overview"
        subtitle="Platform-wide sales performance across all restaurants."
      >
        <div class="header-controls">
          <span class="chip warn">Prototype sample — no API yet</span>
          <div class="range-pills" role="tablist" aria-label="Date range">
            @for (pill of rangeOptions; track pill) {
              <button type="button" class="range-pill" role="tab" [class.active]="range() === pill" [attr.aria-selected]="range() === pill" (click)="range.set(pill)">{{ pill }}</button>
            }
          </div>
          <button class="header-action-btn" type="button" (click)="refresh()">
            <span aria-hidden="true">&#8635;</span> Refresh
          </button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis" ariaLabel="Sales summary"></app-kpi-row>

      <!-- Charts row -->
      <section class="charts-row">
        <article class="panel chart-panel">
          <div class="panel-head">
            <h2>Revenue Trend</h2>
            <span class="legend"><span class="legend-dot legend-dot--revenue"></span> Revenue</span>
          </div>
          <svg class="bar-svg" viewBox="0 0 260 150" preserveAspectRatio="none" role="img" aria-label="Daily revenue for the selected range">
            <g>
              @for (bar of revenueChart(); track bar.i) {
                <rect [attr.x]="bar.x" [attr.y]="bar.y" width="5" [attr.height]="bar.height" rx="2" fill="var(--kb-chart-3)" />
              }
            </g>
          </svg>
          <div class="axis-row" aria-hidden="true">
            <span>1 Oct</span><span>10 Oct</span><span>20 Oct</span><span>26 Oct</span>
          </div>
        </article>

        <article class="panel chart-panel">
          <div class="panel-head">
            <h2>Orders Trend</h2>
            <span class="legend"><span class="legend-dot legend-dot--orders"></span> Orders</span>
          </div>
          <svg class="bar-svg" viewBox="0 0 260 150" preserveAspectRatio="none" role="img" aria-label="Daily orders for the selected range">
            <g>
              @for (bar of ordersChart(); track bar.i) {
                <rect [attr.x]="bar.x" [attr.y]="bar.y" width="5" [attr.height]="bar.height" rx="2" fill="var(--kb-chart-1)" />
              }
            </g>
          </svg>
          <div class="axis-row" aria-hidden="true">
            <span>1 Oct</span><span>10 Oct</span><span>20 Oct</span><span>26 Oct</span>
          </div>
        </article>

        <article class="panel chart-panel">
          <div class="panel-head">
            <h2>Payment Mode Distribution</h2>
          </div>
          <div class="donut-row">
            <div class="donut-svg-box" aria-hidden="true">
              <svg class="donut-svg" viewBox="0 0 120 120">
                @for (segment of paymentDonut(); track segment.label) {
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
                <strong>15,842</strong>
                <span>Orders</span>
              </div>
            </div>
            <ul class="donut-legend">
              @for (row of PAYMENT_MODES; track row.name) {
                <li>
                  <span class="legend-dot" [class]="'legend-dot--' + row.tone"></span>
                  <strong>{{ row.name }}</strong>
                  <span class="muted">{{ row.share }}%</span>
                </li>
              }
            </ul>
          </div>
        </article>
      </section>

      <!-- Top performers and cities -->
      <section class="pair-row">
        <article class="panel table-panel">
          <div class="panel-head">
            <div><h2>Top Performing Restaurants</h2></div>
            <a routerLink="/admin/businesses" class="view-link">View All</a>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr><th>#</th><th>Restaurant</th><th>Orders</th><th>Revenue</th><th>AOV</th><th>Growth</th></tr>
              </thead>
              <tbody>
                @for (row of topRows; track row.rank) {
                  <tr>
                    <td class="idx-col">{{ row.rank }}</td>
                    <td><strong>{{ row.name }}</strong></td>
                    <td>{{ row.orders }}</td>
                    <td>{{ row.revenue }}</td>
                    <td>{{ row.aov }}</td>
                    <td class="growth up">{{ row.growth }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </article>

        <article class="panel table-panel">
          <div class="panel-head">
            <div><h2>Top Cities By Revenue</h2></div>
            <a routerLink="/admin/reports" class="view-link">View All</a>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr><th>#</th><th>City</th><th>Restaurants</th><th>Orders</th><th>Revenue</th><th>Growth</th></tr>
              </thead>
              <tbody>
                @for (row of cityRows; track row.rank) {
                  <tr>
                    <td class="idx-col">{{ row.rank }}</td>
                    <td><strong>{{ row.city }}</strong></td>
                    <td>{{ row.restaurants }}</td>
                    <td>{{ row.orders }}</td>
                    <td>{{ row.revenue }}</td>
                    <td class="growth up">{{ row.growth }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </article>
      </section>

      <!-- Recent orders -->
      <section class="panel table-panel wide-panel">
        <div class="panel-head">
          <div><h2>Recent Orders</h2></div>
          <a routerLink="/admin/reports" class="view-link">View All</a>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>#</th><th>Order ID</th><th>Restaurant</th><th>Order Time</th><th>Amount</th><th>Payment Mode</th><th>Status</th><th>Customer</th><th class="actions-col">Actions</th></tr>
            </thead>
            <tbody>
              @for (row of recentRows; track row.id; let i = $index) {
                <tr>
                  <td class="idx-col">{{ i + 1 }}</td>
                  <td class="mono">{{ row.order }}</td>
                  <td><strong>{{ row.restaurant }}</strong></td>
                  <td>{{ row.time }}</td>
                  <td>{{ row.amount }}</td>
                  <td><span class="chip" [class]="modeChip(row.mode)">{{ row.mode }}</span></td>
                  <td>
                    <span class="chip" [class.success]="row.status === 'Completed'" [class.danger]="row.status === 'Cancelled'">
                      {{ row.status }}
                    </span>
                  </td>
                  <td>
                    <div class="stacked">
                      <span class="avatar" aria-hidden="true">{{ row.customer.charAt(0) }}</span>
                      {{ row.customer }}
                    </div>
                  </td>
                  <td class="actions-col"><button type="button" class="kebab-btn" aria-label="Row actions" (click)="rowAction(row)">⋮</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: [
    `
      .range-pills { display: inline-flex; border: 1px solid var(--line); border-radius: var(--kb-radius-md); overflow: hidden; }
      .range-pill { border: none; background: var(--kb-color-surface); color: var(--ink-2); padding: var(--kb-space-2) var(--kb-space-4); font-size: var(--kb-text-sm); cursor: pointer; }
      .range-pill.active { background: var(--brand); color: var(--kb-color-primary-foreground); }
      .range-pill:focus-visible { outline: 2px solid var(--brand); outline-offset: -2px; }
      .charts-row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: var(--kb-space-4); margin-top: var(--kb-space-5); }
      @media (max-width: 1100px) { .charts-row { grid-template-columns: 1fr; } }
      .chart-panel, .table-panel { padding: var(--kb-space-4) var(--kb-space-5) var(--kb-space-5); }
      .panel-head { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-3); margin-bottom: var(--kb-space-4); }
      .panel-head h2 { margin: 0; font-size: var(--kb-text-md); font-weight: 750; }
      .legend { font-size: var(--kb-text-xs); color: var(--muted); display: inline-flex; align-items: center; gap: var(--kb-space-1); }
      .legend-dot { display: inline-block; width: 10px; height: 10px; border-radius: var(--kb-radius-full); }
      .legend-dot--revenue { background: var(--kb-chart-3); }
      .legend-dot--orders { background: var(--kb-chart-1); }
      .legend-dot--seg-1 { background: var(--brand); }
      .legend-dot--seg-2 { background: var(--kb-chart-2); }
      .legend-dot--seg-3 { background: var(--success); }
      .legend-dot--seg-4 { background: var(--kb-chart-1); }
      .legend-dot--seg-5 { background: var(--kb-chart-5); }
      .bar-svg { width: 100%; height: 170px; }
      .axis-row { display: flex; justify-content: space-between; margin-top: var(--kb-space-2); color: var(--muted); font-size: var(--kb-text-xs); }
      .donut-row { display: flex; gap: var(--kb-space-4); align-items: center; }
      .donut-svg-box { position: relative; width: 130px; height: 130px; flex: none; }
      .donut-svg { width: 100%; height: 100%; }
      .donut-center { position: absolute; inset: 0; display: grid; place-content: center; text-align: center; pointer-events: none; }
      .donut-center strong { font-size: var(--kb-text-md); }
      .donut-center span { font-size: var(--kb-text-xs); color: var(--muted); }
      .donut-legend { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--kb-space-2); font-size: var(--kb-text-xs); }
      .donut-legend li { display: flex; align-items: center; gap: var(--kb-space-2); }
      .pair-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--kb-space-4); margin-top: var(--kb-space-4); }
      @media (max-width: 1100px) { .pair-row { grid-template-columns: 1fr; } }
      .wide-panel { margin-top: var(--kb-space-4); }
      .idx-col { width: 40px; text-align: center; color: var(--muted); }
      .mono { font-family: var(--kb-font-mono); font-size: var(--kb-text-xs); color: var(--ink-2); }
      .stacked { display: flex; align-items: center; gap: var(--kb-space-2); }
      .avatar { width: 26px; height: 26px; border-radius: var(--kb-radius-full); background: var(--brand-soft); color: var(--brand); display: grid; place-items: center; font-size: var(--kb-text-xs); font-weight: 700; }
      .growth { font-weight: 700; }
      .growth.up { color: var(--success); }
      .chip.brand { background: var(--brand-soft); color: var(--brand); }
      .chip.success { background: var(--success-soft); color: var(--success); }
      .chip.danger { background: var(--danger-soft); color: var(--danger); }
      .actions-col { width: 48px; text-align: center; }
      .kebab-btn { border: none; background: transparent; color: var(--muted); font-size: var(--kb-text-lg); cursor: pointer; line-height: 1; }
      .view-link { font-size: var(--kb-text-xs); color: var(--brand); font-weight: 650; text-decoration: none; }
      .view-link:hover { text-decoration: underline; }
    `,
  ],
})
export class SalesOverviewPageComponent {
  private readonly toast = inject(ToastService);

  readonly topRows = TOP_ROWS;
  readonly cityRows = CITY_ROWS;
  readonly recentRows = RECENT_ROWS;
  readonly PAYMENT_MODES = PAYMENT_MODES;

  readonly rangeOptions = ['Last 7 Days', 'Last 30 Days', 'This Month'] as const;
  readonly range = signal<'Last 7 Days' | 'Last 30 Days' | 'This Month'>('Last 30 Days');

  readonly kpis: KpiItem[] = [
    { label: 'Total Revenue', value: '\u20B93,24,650', icon: 'payments', tone: 'brand', caption: '+12.7% vs previous 30 days' },
    { label: 'Total Orders', value: '15,842', icon: 'orders', tone: 'danger', caption: '+8.4% vs previous 30 days' },
    { label: 'Active Restaurants', value: 124, icon: 'business', tone: 'success', caption: '+6.1% vs previous 30 days' },
    { label: 'Average Order Value', value: '\u20B9342', icon: 'reports', tone: 'success', caption: '+4.3% vs previous 30 days' },
    { label: 'Total Customers', value: '8,972', icon: 'staff', tone: 'danger', caption: '+11.9% vs previous 30 days' },
  ];

  /** Range shrinks the labelled sample series (deterministically) — honest to the pill. */
  readonly revenueChart = computed(() => {
    const count = this.barCount();
    const seg = REVENUE_BARS.slice(0, count);
    const max = Math.max(...seg);
    return seg.map((v, i) => {
      const height = Math.round((v / max) * 130);
      return { i, x: Math.round(i * (260 / count)) + 4, y: 150 - height, height };
    });
  });

  readonly ordersChart = computed(() => {
    const count = this.barCount();
    const seg = ORDER_BARS.slice(0, count);
    const max = Math.max(...seg);
    return seg.map((v, i) => {
      const height = Math.round((v / max) * 130);
      return { i, x: Math.round(i * (260 / count)) + 4, y: 150 - height, height };
    });
  });

  private barCount(): number {
    return this.range() === 'Last 7 Days' ? 7 : 26;
  }

  private readonly SEGMENT_COLORS: Record<string, string> = {
    'seg-1': 'var(--brand)',
    'seg-2': 'var(--kb-chart-2)',
    'seg-3': 'var(--success)',
    'seg-4': 'var(--kb-chart-1)',
    'seg-5': 'var(--kb-chart-5)',
  };

  private donutSegments(rows: typeof PAYMENT_MODES) {
    const circumference = 2 * Math.PI * 46;
    let offset = 0;
    return rows.map((row) => {
      const length = (row.share / 100) * circumference;
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

  paymentDonut() {
    return this.donutSegments(PAYMENT_MODES);
  }

  modeChip(mode: RecentRow['mode']): string {
    return mode === 'UPI' || mode === 'Card' ? 'brand' : mode === 'Cash' ? 'success' : 'warn';
  }

  refresh(): void {
    this.toast.show('Sample data refreshed — payment network metrics remain pending their API.', 'info');
  }

  rowAction(row: RecentRow): void {
    this.toast.show(`${row.order} — order actions pending the reports API (prototype).`, 'info');
  }
}
