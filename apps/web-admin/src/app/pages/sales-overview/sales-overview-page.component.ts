import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminApiService } from '../../core/services/admin-api.service';
import { ToastService } from '../../core/services/toast.service';
import {
  AdminBillListItem,
  AdminBusinessListItem,
  AdminDailyTrendItem,
  AdminDashboardSummary,
} from '../../core/models/api.models';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';

const NOT_LOADED = '—';

export interface TopRow { rank: number; name: string; orders: number }
export interface RecentRow { id: number; order: string; restaurant: string; time: string; amount: string; mode: 'UPI' | 'Card' | 'Cash' | 'Online'; status: 'Completed' | 'Cancelled'; customer: string }

export interface PaymentModeShare {
  name: string;
  share: number;
  count: number;
  amount: string;
  tone: string;
}

/** How many bills are pulled to build the payment split; also stated in the UI. */
const BILL_SAMPLE_SIZE = 100;

/**
 * Sales Overview.
 *
 * WHY: this page used to show hardcoded chart bars, a fixed payment split, a
 * fabricated city ranking, invented KPI totals and sample invoices, all under a
 * "Live PostgreSQL Data" chip. A platform admin could not tell which numbers
 * were real. Every figure now comes from the API, and a missing or failed
 * response renders as empty or "not loaded" rather than as plausible fiction.
 *
 * WHAT: summary → KPI row; daily trends → both charts and their axis labels;
 * businesses → busiest outlets; bills → the payment split and the recent-bill
 * table. City/region data is not exposed by any endpoint, so that panel is gone.
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
        subtitle="Platform sales, settled bills and payment performance from the admin API."
      >
        <div class="header-controls">
          <span class="chip-live">
            <span class="live-dot" aria-hidden="true"></span> PostgreSQL Data
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

      @if (error(); as message) {
        <div class="page-error" role="alert">
          <span>{{ message }}</span>
          <button class="header-action-btn" type="button" (click)="refresh()">Retry</button>
        </div>
      }

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
          @if (!revenueChart().length) {
            <p class="chart-empty">No daily trend returned for this range.</p>
          }
          @if (axisLabels(); as axis) {
            <div class="axis-row" aria-hidden="true">
              <span>{{ axis.first }}</span><span>{{ axis.last }}</span>
            </div>
          }
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
          @if (!ordersChart().length) {
            <p class="chart-empty">No daily trend returned for this range.</p>
          }
          @if (axisLabels(); as axis) {
            <div class="axis-row" aria-hidden="true">
              <span>{{ axis.first }}</span><span>{{ axis.last }}</span>
            </div>
          }
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
              @for (row of paymentModes(); track row.name) {
                <li>
                  <span class="legend-dot" [class]="'legend-dot--' + row.tone"></span>
                  <strong>{{ row.name }}</strong>
                  <span class="muted">{{ row.share }}%</span>
                </li>
              } @empty {
                <li class="muted">No settled bills returned.</li>
              }
            </ul>
          </div>
          @if (billsLoaded(); as count) {
            <p class="chart-empty">Split across the last {{ count }} settled bills.</p>
          }
        </article>
      </section>

      <!-- Busiest outlets -->
      <section class="pair-row">
        <article class="panel table-panel">
          <div class="panel-head">
            <div><h2>Busiest Outlets</h2></div>
            <a routerLink="/admin/businesses" class="view-link">View all outlets</a>
          </div>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr><th>#</th><th>Outlet</th><th>Orders</th></tr>
              </thead>
              <tbody>
                @for (row of topRows(); track row.rank) {
                  <tr>
                    <td class="idx-col">{{ row.rank }}</td>
                    <td><strong>{{ row.name }}</strong></td>
                    <td>{{ row.orders }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="3" class="muted empty-cell">No outlets returned by the businesses endpoint.</td></tr>
                }
              </tbody>
            </table>
          </div>
          <p class="chart-empty">Ranked by the order count each outlet reports; revenue per outlet is not exposed by this endpoint.</p>
        </article>
      </section>

      <!-- Recent orders -->
      <section class="panel table-panel wide-panel">
        <div class="panel-head">
          <div><h2>Recent Settled Customer Bills</h2></div>
          <a routerLink="/admin/businesses" class="view-link">View all outlets</a>
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
              } @empty {
                <tr><td colspan="9" class="muted empty-cell">No settled bills returned by the bills endpoint.</td></tr>
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
      .page-error { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-4); margin-top: var(--kb-space-4); padding: var(--kb-space-3) var(--kb-space-4); border-radius: var(--kb-radius-lg); background: var(--danger-soft); color: var(--danger); font-size: var(--kb-text-sm); font-weight: 600; }
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
      .chart-empty { margin: var(--kb-space-2) 0 0; font-size: var(--kb-text-xs); color: var(--muted); }
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
      .pair-row { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); margin-top: var(--kb-space-4); }
      .wide-panel { margin-top: var(--kb-space-4); }
      .idx-col { width: 40px; text-align: center; color: var(--muted); }
      .mono { font-family: var(--kb-font-mono); font-size: var(--kb-text-xs); color: var(--ink-2); }
      .stacked { display: flex; align-items: center; gap: var(--kb-space-2); }
      .avatar { width: 26px; height: 26px; border-radius: var(--kb-radius-full); background: var(--brand-soft); color: var(--brand); display: grid; place-items: center; font-size: var(--kb-text-xs); font-weight: 700; }
      .empty-cell { text-align: center; padding: var(--kb-space-6) 0; font-size: var(--kb-text-sm); }
    `,
  ],
})
export class SalesOverviewPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly toast = inject(ToastService);

  readonly rangeOptions = ['Last 7 Days', 'Last 30 Days', 'This Month'] as const;
  readonly range = signal<'Last 7 Days' | 'Last 30 Days' | 'This Month'>('Last 30 Days');

  readonly summary = signal<AdminDashboardSummary | null>(null);
  readonly businesses = signal<AdminBusinessListItem[]>([]);
  readonly rawBills = signal<AdminBillListItem[]>([]);
  readonly dailyTrends = signal<AdminDailyTrendItem[]>([]);
  readonly error = signal<string | null>(null);

  readonly kpis = computed<KpiItem[]>(() => {
    const s = this.summary();
    // "—" until the summary arrives; a zero would claim there was no revenue.
    const revenue = s ? '₹' + Number(s.totalRevenue || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 }) : NOT_LOADED;
    const orders = s ? Number(s.totalOrders || 0).toLocaleString('en-IN') : NOT_LOADED;
    const outlets = s ? Number(s.liveBusinesses || 0).toLocaleString('en-IN') : NOT_LOADED;
    const aov = s && Number(s.totalOrders) > 0
      ? '₹' + Math.round(Number(s.totalRevenue) / Number(s.totalOrders)).toLocaleString('en-IN')
      : NOT_LOADED;
    const refunds = s ? Number(s.refundedOrders || 0).toLocaleString('en-IN') : NOT_LOADED;

    return [
      { label: 'Total Revenue', value: revenue, icon: 'payments', tone: 'brand', caption: 'Completed revenue reported by the summary' },
      { label: 'Total Orders', value: orders, icon: 'orders', tone: 'danger', caption: 'Orders reported by the summary' },
      { label: 'Active Outlets', value: outlets, icon: 'business', tone: 'success', caption: 'Live businesses in the summary' },
      { label: 'Average Order Value', value: aov, icon: 'reports', tone: 'success', caption: 'Revenue divided by orders' },
      { label: 'Refunded Orders', value: refunds, icon: 'history', tone: 'default', caption: 'Refunds reported by the summary' },
    ];
  });

  readonly topRows = computed<TopRow[]>(() =>
    this.businesses()
      .toSorted((a, b) => (b.orderCount || 0) - (a.orderCount || 0))
      .slice(0, 5)
      .map((b, idx) => ({
        rank: idx + 1,
        name: b.shopName || 'Outlet #' + b.restaurantId,
        orders: b.orderCount || 0,
      })),
  );

  /** Kept as its own signal so the template can name the sample the split covers. */
  readonly billsLoaded = computed(() => this.rawBills().length);

  readonly paymentModes = computed<PaymentModeShare[]>(() => {
    const bills = this.rawBills();
    if (!bills.length) {
      return [];
    }

    const buckets = new Map<string, { count: number; amount: number; tone: string }>();
    const add = (name: string, tone: string, amount: number) => {
      const bucket = buckets.get(name) ?? { count: 0, amount: 0, tone };
      bucket.count += 1;
      bucket.amount += amount;
      buckets.set(name, bucket);
    };

    for (const bill of bills) {
      const mode = (bill.paymentMode || '').toLowerCase();
      const amount = Number(bill.totalAmount || 0);
      if (mode.includes('split')) add('Split (Cash+UPI)', 'seg-2', amount);
      else if (mode.includes('upi')) add('UPI', 'seg-1', amount);
      else if (mode.includes('card') || mode.includes('pos')) add('POS / Card', 'seg-4', amount);
      else if (mode.includes('cash')) add('Cash', 'seg-3', amount);
      else add('Other', 'seg-5', amount);
    }

    const total = bills.length;
    const rows = [...buckets.entries()]
      .map(([name, bucket]) => ({ name, ...bucket }))
      .sort((a, b) => b.count - a.count);

    // Shares are rounded to one decimal, and the last row takes the remainder so
    // they always add up to exactly 100 rather than 99.9 or 100.1.
    let assigned = 0;
    return rows.map((row, index) => {
      // Floor at zero: rounding the rows above can overshoot 100 by a tenth, and a
      // negative arc is worse than a 0.1% inaccuracy.
      const share = index === rows.length - 1
        ? Math.max(0, Math.round((100 - assigned) * 10) / 10)
        : Math.round((row.count / total) * 1000) / 10;
      assigned += share;
      return {
        name: row.name,
        share,
        count: row.count,
        amount: '₹' + row.amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
        tone: row.tone,
      };
    });
  });

  readonly recentRows = computed<RecentRow[]>(() => {
    return this.rawBills().slice(0, 10).map((b) => {
      const mode = (b.paymentMode || '').toLowerCase();
      const mappedMode: RecentRow['mode'] = mode.includes('upi') ? 'UPI' : mode.includes('pos') || mode.includes('card') ? 'Card' : mode.includes('cash') ? 'Cash' : 'Online';
      const d = b.createdAt ? new Date(b.createdAt) : new Date();
      const timeStr = d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) + ', ' +
        d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true });

      return {
        id: b.id,
        order: b.invoiceNumber || ('#' + b.id),
        restaurant: b.shopName || 'Outlet #' + b.restaurantId,
        time: timeStr,
        amount: '₹' + Number(b.totalAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 }),
        mode: mappedMode,
        status: (b.orderStatus === 'cancelled' ? 'Cancelled' : 'Completed') as RecentRow['status'],
        customer: b.customerName || 'Quick Bill',
      };
    });
  });

  readonly totalOrdersFormatted = computed(() => {
    const s = this.summary();
    return s ? Number(s.totalOrders || 0).toLocaleString('en-IN') : NOT_LOADED;
  });

  readonly revenueChart = computed(() => this.barsFrom(this.trendSlice(), (point) => point.revenue));
  readonly ordersChart = computed(() => this.barsFrom(this.trendSlice(), (point) => point.orderCount));

  readonly axisLabels = computed(() => {
    const points = this.trendSlice();
    if (!points.length) {
      return null;
    }
    const label = (point: AdminDailyTrendItem) =>
      new Date(point.date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
    return { first: label(points[0]), last: label(points[points.length - 1]) };
  });

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.error.set(null);

    // Any failure is reported once; the panels that did load keep their data.
    const fail = (err: unknown) => this.error.set(
      (err as { error?: { message?: string } })?.error?.message ||
        'Could not load sales data from the server.',
    );

    this.adminApi.getDashboardSummary().subscribe({
      next: (res) => this.summary.set(res),
      error: (err) => fail(err),
    });

    this.adminApi.getBusinesses().subscribe({
      next: (res) => this.businesses.set(Array.isArray(res) ? res : []),
      error: (err) => fail(err),
    });

    this.adminApi.getBills(BILL_SAMPLE_SIZE).subscribe({
      next: (res) => this.rawBills.set(Array.isArray(res) ? res : []),
      error: (err) => fail(err),
    });

    this.adminApi.getDailyTrends(this.rangeDays()).subscribe({
      next: (res) => this.dailyTrends.set(Array.isArray(res) ? res : []),
      error: (err) => fail(err),
    });
  }

  /** "This Month" is the days elapsed so far, not a rolling 30, so the label is true. */
  private rangeDays(): number {
    if (this.range() === 'Last 7 Days') {
      return 7;
    }
    return this.range() === 'This Month' ? Math.max(1, new Date().getDate()) : 30;
  }

  private trendSlice(): AdminDailyTrendItem[] {
    const days = this.rangeDays();
    return this.dailyTrends().slice(-days);
  }

  private barsFrom(points: AdminDailyTrendItem[], value: (point: AdminDailyTrendItem) => number) {
    const values = points.map((point) => Number(value(point) || 0));
    const max = Math.max(1, ...values);
    const count = Math.max(1, values.length);
    return values.map((v, i) => {
      const height = Math.round((v / max) * 130);
      return { i, x: Math.round(i * (260 / count)) + 4, y: 150 - height, height };
    });
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
    return this.donutSegments(this.paymentModes());
  }

  modeChip(mode: RecentRow['mode']): string {
    return mode === 'UPI' || mode === 'Card' ? 'brand' : mode === 'Cash' ? 'success' : 'warn';
  }

  rowAction(row: RecentRow): void {
    this.toast.show(`${row.order} · ${row.restaurant} · ${row.amount} · ${row.mode}`, 'info');
  }
}
