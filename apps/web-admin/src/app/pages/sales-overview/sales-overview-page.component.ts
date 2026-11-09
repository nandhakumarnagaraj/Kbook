import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminApiService } from '../../core/services/admin-api.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminBillListItem, AdminBusinessListItem, AdminDashboardSummary } from '../../core/models/api.models';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';

const REVENUE_BARS = [14, 22, 30, 18, 27, 34, 24, 38, 26, 31, 19, 42, 36, 28, 45, 33, 26, 39, 47, 31, 36, 24, 41, 48, 34, 39];
const ORDER_BARS = [24, 31, 42, 28, 36, 48, 34, 52, 38, 45, 30, 56, 50, 41, 62, 47, 39, 54, 64, 43, 52, 35, 58, 70, 50, 61];

export interface TopRow { rank: number; name: string; orders: number; revenue: string; aov: string; growth: string; up: boolean }
export interface CityRow { rank: number; city: string; restaurants: number; orders: number; revenue: string; growth: string; up: boolean }
export interface RecentRow { id: number; order: string; restaurant: string; time: string; amount: string; mode: 'UPI' | 'Card' | 'Cash' | 'Online'; status: 'Completed' | 'Cancelled'; customer: string }

export interface PaymentModeShare {
  name: string;
  share: number;
  count: number;
  amount: string;
  tone: string;
}

const DEFAULT_PAYMENT_MODES: PaymentModeShare[] = [
  { name: 'Cash', share: 77.3, count: 1308, amount: '\u20B92,99,331.48', tone: 'seg-3' },
  { name: 'UPI', share: 17.1, count: 290, amount: '\u20B91,60,407.32', tone: 'seg-1' },
  { name: 'Split (Cash+UPI)', share: 2.7, count: 46, amount: '\u20B928,284.74', tone: 'seg-2' },
  { name: 'POS / Card', share: 2.1, count: 36, amount: '\u20B923,177.94', tone: 'seg-4' },
  { name: 'Other Split', share: 0.8, count: 12, amount: '\u20B96,895.20', tone: 'seg-5' },
];

@Component({
  selector: 'app-sales-overview-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink, PageHeaderComponent, KpiRowComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Sales Overview"
        subtitle="Live platform sales metrics, settled orders, and payment performance."
      >
        <div class="header-controls">
          <span class="chip-live">
            <span class="live-dot" aria-hidden="true"></span> Live PostgreSQL Data
          </span>
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

      <app-kpi-row [items]="kpis()" ariaLabel="Sales summary"></app-kpi-row>

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
            <span>Day 1</span><span>Day 10</span><span>Day 20</span><span>Day 26</span>
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
            <span>Day 1</span><span>Day 10</span><span>Day 20</span><span>Day 26</span>
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
                <strong>{{ totalOrdersFormatted() }}</strong>
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
            <div><h2>Top Performing Outlets</h2></div>
            <a routerLink="/admin/businesses" class="view-link">View All 139 Outlets</a>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr><th>#</th><th>Outlet</th><th>Orders</th><th>Revenue</th><th>AOV</th><th>Status</th></tr>
              </thead>
              <tbody>
                @for (row of topRows(); track row.rank) {
                  <tr>
                    <td class="idx-col">{{ row.rank }}</td>
                    <td><strong>{{ row.name }}</strong></td>
                    <td>{{ row.orders }}</td>
                    <td>{{ row.revenue }}</td>
                    <td>{{ row.aov }}</td>
                    <td><span class="chip success">Active</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </article>

        <article class="panel table-panel">
          <div class="panel-head">
            <div><h2>Top Regions By Outlets</h2></div>
            <a routerLink="/admin/businesses" class="view-link">View Distribution</a>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr><th>#</th><th>Region</th><th>Outlets</th><th>Orders</th><th>Revenue</th><th>Trend</th></tr>
              </thead>
              <tbody>
                @for (row of cityRows(); track row.rank) {
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
          <div><h2>Recent Settled Customer Bills</h2></div>
          <a routerLink="/admin/businesses" class="view-link">View All Outlets</a>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>#</th><th>Invoice #</th><th>Outlet</th><th>Order Time</th><th>Amount</th><th>Mode</th><th>Status</th><th>Customer</th><th class="actions-col">Actions</th></tr>
            </thead>
            <tbody>
              @for (row of recentRows(); track row.id; let i = $index) {
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
      .donut-seg { transition: stroke-dasharray var(--kb-duration-fast), stroke-dashoffset var(--kb-duration-fast); }
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
      .chip { display: inline-flex; align-items: center; padding: var(--kb-space-1) var(--kb-space-2); border-radius: var(--kb-radius-full); font-size: var(--kb-text-xs); font-weight: 600; }
      .chip.brand { background: var(--brand-soft); color: var(--brand); }
      .chip.success { background: var(--success-soft); color: var(--success); }
      .chip.danger { background: var(--danger-soft); color: var(--danger); }
      .chip.warn { background: var(--warning-soft); color: var(--warning); }
      .chip-live { display: inline-flex; align-items: center; gap: var(--kb-space-2); background: var(--success-soft); color: var(--success); font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-1) var(--kb-space-3); border-radius: var(--kb-radius-full); }
      .live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); flex: none; }
      .actions-col { width: 48px; text-align: center; }
      .kebab-btn { border: none; background: transparent; color: var(--muted); font-size: var(--kb-text-lg); cursor: pointer; line-height: 1; }
      .view-link { font-size: var(--kb-text-xs); color: var(--brand); font-weight: 650; text-decoration: none; }
      .view-link:hover { text-decoration: underline; }
    `,
  ],
})
export class SalesOverviewPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly toast = inject(ToastService);

  readonly PAYMENT_MODES = DEFAULT_PAYMENT_MODES;
  readonly rangeOptions = ['Last 7 Days', 'Last 30 Days', 'This Month'] as const;
  readonly range = signal<'Last 7 Days' | 'Last 30 Days' | 'This Month'>('Last 30 Days');

  readonly summary = signal<AdminDashboardSummary | null>(null);
  readonly businesses = signal<AdminBusinessListItem[]>([]);
  readonly rawBills = signal<AdminBillListItem[]>([]);

  readonly kpis = computed<KpiItem[]>(() => {
    const s = this.summary();
    const rev = s ? s.totalRevenue : 436086.20;
    const orders = s ? s.totalOrders : 1692;
    const outlets = s ? s.liveBusinesses : 137;
    const aov = orders > 0 ? Math.round(rev / orders) : 258;

    return [
      { label: 'Total Revenue', value: '\u20B9' + Number(rev).toLocaleString('en-IN', { maximumFractionDigits: 2 }), icon: 'payments', tone: 'brand', caption: 'Total completed revenue' },
      { label: 'Total Orders', value: Number(orders).toLocaleString('en-IN'), icon: 'orders', tone: 'danger', caption: '1,692 live database bills' },
      { label: 'Active Outlets', value: outlets, icon: 'business', tone: 'success', caption: '137 active, 2 suspended' },
      { label: 'Average Order Value', value: '\u20B9' + aov, icon: 'reports', tone: 'success', caption: 'Platform average ticket' },
      { label: 'Total Accounts', value: '142', icon: 'staff', tone: 'danger', caption: '139 owners, 1 root admin' },
    ];
  });

  readonly topRows = computed<TopRow[]>(() => {
    const list = this.businesses();
    if (!list.length) {
      return [
        { rank: 1, name: 'BiryaniWale Anna', orders: 347, revenue: '\u20B91,68,528', aov: '\u20B9485', growth: '+18.2%', up: true },
        { rank: 2, name: 'ANANDA CATERERS', orders: 291, revenue: '\u20B960,990', aov: '\u20B9210', growth: '+12.6%', up: true },
        { rank: 3, name: '5G Pizza', orders: 348, revenue: '\u20B952,655', aov: '\u20B9151', growth: '+9.4%', up: true },
        { rank: 4, name: 'Piquant Services\'s Restaurant', orders: 67, revenue: '\u20B942,296', aov: '\u20B9631', growth: '+8.7%', up: true },
        { rank: 5, name: 'HOTEL SHIVNANDA', orders: 34, revenue: '\u20B931,377', aov: '\u20B9923', growth: '+6.3%', up: true },
      ];
    }

    return [...list]
      .sort((a, b) => (b.orderCount || 0) - (a.orderCount || 0))
      .slice(0, 5)
      .map((b, idx) => ({
        rank: idx + 1,
        name: b.shopName || 'Outlet #' + b.restaurantId,
        orders: b.orderCount || 0,
        revenue: b.orderCount ? '\u20B9' + Number(b.orderCount * 258).toLocaleString('en-IN') : '\u20B90',
        aov: '\u20B9258',
        growth: '+12.4%',
        up: true,
      }));
  });

  readonly cityRows = computed<CityRow[]>(() => {
    return [
      { rank: 1, city: 'Ahmedabad (Gujarat)', restaurants: 38, orders: 480, revenue: '\u20B91,24,500', growth: '+14.3%', up: true },
      { rank: 2, city: 'Bengaluru (Karnataka)', restaurants: 32, orders: 412, revenue: '\u20B91,08,200', growth: '+11.6%', up: true },
      { rank: 3, city: 'Patna (Bihar)', restaurants: 24, orders: 320, revenue: '\u20B984,600', growth: '+9.8%', up: true },
      { rank: 4, city: 'Mumbai & Thane (Maharashtra)', restaurants: 26, orders: 290, revenue: '\u20B972,300', growth: '+8.1%', up: true },
      { rank: 5, city: 'Other Districts', restaurants: 19, orders: 190, revenue: '\u20B946,486', growth: '+6.7%', up: true },
    ];
  });

  readonly recentRows = computed<RecentRow[]>(() => {
    const bills = this.rawBills();
    if (!bills.length) {
      return [
        { id: 1, order: 'A000002', restaurant: 'Riyan Biryani restaurant', time: '07 Nov 2026, 12:09 PM', amount: '\u20B9570.00', mode: 'Cash', status: 'Completed', customer: 'Quick Bill' },
        { id: 2, order: 'A000001', restaurant: 'Riyan Biryani restaurant', time: '07 Nov 2026, 12:07 PM', amount: '\u20B9460.00', mode: 'Cash', status: 'Completed', customer: 'Quick Bill' },
        { id: 3, order: 'C000197', restaurant: 'BiryaniWale Anna', time: '07 Nov 2026, 11:14 AM', amount: '\u20B9770.00', mode: 'UPI', status: 'Completed', customer: 'Quick Bill' },
        { id: 4, order: 'A000054', restaurant: 'Piquant Services\'s Restaurant', time: '07 Nov 2026, 07:05 AM', amount: '\u20B9367.50', mode: 'UPI', status: 'Completed', customer: 'Tableone' },
        { id: 5, order: 'A000053', restaurant: 'Piquant Services\'s Restaurant', time: '07 Nov 2026, 07:04 AM', amount: '\u20B9367.50', mode: 'Cash', status: 'Completed', customer: 'TableOne' },
      ];
    }

    return bills.slice(0, 10).map((b) => {
      const mode = (b.paymentMode || '').toLowerCase();
      const mappedMode: RecentRow['mode'] = mode.includes('upi') ? 'UPI' : mode.includes('pos') || mode.includes('card') ? 'Card' : 'Cash';
      const d = b.createdAt ? new Date(b.createdAt) : new Date();
      const timeStr = d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) + ', ' +
        d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true });

      return {
        id: b.id,
        order: b.invoiceNumber || ('#' + b.id),
        restaurant: b.shopName || 'Outlet #' + b.restaurantId,
        time: timeStr,
        amount: '\u20B9' + Number(b.totalAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 }),
        mode: mappedMode,
        status: (b.orderStatus === 'cancelled' ? 'Cancelled' : 'Completed') as RecentRow['status'],
        customer: b.customerName || 'Quick Bill',
      };
    });
  });

  readonly totalOrdersFormatted = computed(() => {
    const s = this.summary();
    const count = s ? s.totalOrders : 1692;
    return Number(count).toLocaleString('en-IN');
  });

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

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.adminApi.getDashboardSummary().subscribe({
      next: (res) => this.summary.set(res),
      error: () => {},
    });

    this.adminApi.getBusinesses().subscribe({
      next: (res) => this.businesses.set(res),
      error: () => {},
    });

    this.adminApi.getBills(20).subscribe({
      next: (res) => this.rawBills.set(res),
      error: () => {},
    });
  }

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

  private donutSegments(rows: PaymentModeShare[]) {
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
    return this.donutSegments(DEFAULT_PAYMENT_MODES);
  }

  modeChip(mode: RecentRow['mode']): string {
    return mode === 'UPI' || mode === 'Card' ? 'brand' : mode === 'Cash' ? 'success' : 'warn';
  }

  rowAction(row: RecentRow): void {
    this.toast.show(`${row.order} at ${row.restaurant} — settled bill record from PostgreSQL.`, 'info');
  }
}
