import { CommonModule } from '@angular/common';
import { Component, inject, signal, computed } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessOrder, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency } from '../../shared/formatters';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';

function todayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

interface PaymentSplit {
  mode: string;
  label: string;
  count: number;
  total: number;
}

interface DailyClosingData {
  date: string;
  totalOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  draftOrders: number;
  totalRevenue: number;
  refundedAmount: number;
  netRevenue: number;
  paymentSplits: PaymentSplit[];
  expectedCash: number;
  topItems: { name: string; qty: number }[];
}

@Component({
  selector: 'app-daily-closing-page',
  standalone: true,
  imports: [CommonModule, DateRangeSelectorComponent],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Daily Closing</h2>
        <p class="muted">End-of-day financial summary. Count your cash drawer and compare with the expected amount below.</p>
        <div class="hero-meta">
          <span class="chip success">Cash Reconciliation</span>
          <span class="chip">Payment Splits</span>
        </div>
      </section>

      <section class="panel filter-panel" style="display:flex;align-items:center;gap:1rem;flex-wrap:wrap;">
        <app-date-range-selector (rangeChanged)="setRange($event)"/>
        <button class="ghost-btn" [disabled]="loading()" (click)="load()">
          {{ loading() ? 'Loading...' : 'Refresh' }}
        </button>
        <button class="ghost-btn" (click)="exportClosing()" *ngIf="data()">Export CSV</button>
      </section>

      <div class="panel loading" *ngIf="loading()">Calculating daily closing...</div>
      <div class="panel loading" *ngIf="error()">{{ error() }} <button class="ghost-btn" (click)="load()">Retry</button></div>

      <ng-container *ngIf="data() as d">

        <!-- Revenue Summary -->
        <section class="kpi-row">
          <article class="kpi-card kpi-card--hero">
            <span class="kpi-label">Net Revenue</span>
            <strong class="kpi-value">{{ fmt(d.netRevenue) }}</strong>
            <span class="kpi-sub">After refunds</span>
          </article>
          <article class="kpi-card">
            <span class="kpi-label">Total Billed</span>
            <strong class="kpi-value">{{ fmt(d.totalRevenue) }}</strong>
            <span class="kpi-sub">{{ d.completedOrders }} orders completed</span>
          </article>
          <article class="kpi-card" [class.kpi-card--warn]="d.refundedAmount > 0">
            <span class="kpi-label">Refunds</span>
            <strong class="kpi-value">{{ fmt(d.refundedAmount) }}</strong>
            <span class="kpi-sub">{{ d.cancelledOrders }} cancelled</span>
          </article>
          <article class="kpi-card" [class.kpi-card--warn]="d.draftOrders > 0">
            <span class="kpi-label">Open / Draft</span>
            <strong class="kpi-value">{{ d.draftOrders }}</strong>
            <span class="kpi-sub">Unsettled tables</span>
          </article>
        </section>

        <!-- Payment Mode Breakdown -->
        <section class="panel">
          <h3 class="section-title">Payment Mode Breakdown</h3>
          <p class="muted" style="margin-bottom:1rem;">Compare physical cash with the expected amount. UPI and POS are auto-verified.</p>

          <div class="payment-grid">
            <div class="payment-card payment-card--cash" *ngFor="let split of d.paymentSplits">
              <div class="payment-card__icon">
                {{ getIcon(split.mode) }}
              </div>
              <div class="payment-card__info">
                <span class="payment-card__label">{{ split.label }}</span>
                <strong class="payment-card__amount">{{ fmt(split.total) }}</strong>
                <span class="payment-card__count">{{ split.count }} transactions</span>
              </div>
            </div>
          </div>

          <!-- Cash Reconciliation Box -->
          <div class="cash-box" *ngIf="d.expectedCash > 0">
            <div class="cash-box__header">
              <span>💰 Expected Cash in Drawer</span>
              <strong class="cash-box__amount">{{ fmt(d.expectedCash) }}</strong>
            </div>
            <p class="cash-box__note">Count your physical cash. If it matches this amount (±₹50), your day is balanced.</p>
          </div>
        </section>

        <!-- Order Status Summary -->
        <section class="panel">
          <h3 class="section-title">Order Summary</h3>
          <div class="summary-grid">
            <div class="summary-row">
              <span>Total orders</span><strong>{{ d.totalOrders }}</strong>
            </div>
            <div class="summary-row">
              <span>Completed (paid)</span><strong class="text-success">{{ d.completedOrders }}</strong>
            </div>
            <div class="summary-row">
              <span>Cancelled</span><strong class="text-danger">{{ d.cancelledOrders }}</strong>
            </div>
            <div class="summary-row">
              <span>Still open (draft)</span><strong class="text-warn">{{ d.draftOrders }}</strong>
            </div>
          </div>
        </section>

      </ng-container>
    </div>
  `,
  styles: [`
    .section-title { margin: 0 0 var(--kb-space-2); font-size: 1.05rem; color: var(--kb-color-foreground); }
    .kpi-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 180px), 1fr));
      gap: var(--kb-space-3);
      margin-bottom: var(--kb-space-3);
    }
    .kpi-card {
      background: var(--kb-color-surface); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-md); padding: var(--kb-space-3) var(--kb-space-4);
      display: flex; flex-direction: column; gap: var(--kb-space-1);
    }
    .kpi-card--hero {
      background: linear-gradient(135deg, var(--kb-color-primary) 0%, #60A5FA 100%); border-color: transparent; color: var(--kb-color-primary-foreground);
    }
    .kpi-card--hero .kpi-label, .kpi-card--hero .kpi-sub { color: rgba(255,255,255,0.8); }
    .kpi-card--warn { border-color: var(--kb-color-error); }
    .kpi-label { font-size: 0.8rem; font-weight: 600; color: var(--kb-color-muted); }
    .kpi-value { font-size: calc(1.25rem + 0.3vw); font-weight: 700; color: var(--kb-color-foreground); font-variant-numeric: tabular-nums; }
    .kpi-card--hero .kpi-value { color: var(--kb-color-primary-foreground); }
    .kpi-sub { font-size: 0.75rem; color: var(--kb-color-muted); }

    .payment-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
      gap: var(--kb-space-3);
      margin-bottom: var(--kb-space-4);
    }
    .payment-card {
      display: flex; align-items: center; gap: var(--kb-space-3);
      padding: var(--kb-space-3); border-radius: var(--kb-radius-lg);
      border: 1px solid var(--kb-color-border); background: var(--kb-color-surface);
    }
    .payment-card__icon { font-size: 1.5rem; color: var(--kb-color-primary); }
    .payment-card__info { display: flex; flex-direction: column; }
    .payment-card__label { font-size: 0.8rem; color: var(--kb-color-muted); font-weight: 600; }
    .payment-card__amount { font-size: calc(1.1rem + 0.2vw); font-weight: 700; color: var(--kb-color-foreground); }
    .payment-card__count { font-size: 0.75rem; color: var(--kb-color-muted); }

    .cash-box {
      background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg); padding: var(--kb-space-3) var(--kb-space-4);
    }
    .cash-box__header { display: flex; justify-content: space-between; align-items: center; }
    .cash-box__header span { font-weight: 600; color: var(--kb-color-foreground); }
    .cash-box__amount { font-size: calc(1.2rem + 0.2vw); font-weight: 700; color: var(--kb-color-primary); }
    .cash-box__note { font-size: 0.8rem; color: var(--kb-color-muted); margin-top: var(--kb-space-1); }

    .summary-grid { display: flex; flex-direction: column; gap: var(--kb-space-2); }
    .summary-row {
      display: flex; justify-content: space-between; align-items: center;
      padding: var(--kb-space-1) 0; border-bottom: 1px solid var(--kb-color-border);
    }
    .summary-row:last-child { border-bottom: none; }
    .summary-row span { color: var(--kb-color-foreground); font-size: 0.88rem; }
    .summary-row strong { font-variant-numeric: tabular-nums; color: var(--kb-color-foreground); }
    .text-success { color: var(--kb-color-success); }
    .text-danger { color: var(--kb-color-error); }
    .text-warn { color: var(--kb-color-primary); }
  `]
})
export class DailyClosingPageComponent {
  private readonly api = inject(BusinessApiService);

  loading = signal(false);
  error = signal('');
  data = signal<DailyClosingData | null>(null);

  private dateFrom = todayStr();
  private dateTo = todayStr();

  readonly fmt = formatCurrency;

  constructor() { this.load(); }

  setRange(range: { from: string; to: string }): void {
    this.dateFrom = range.from;
    this.dateTo = range.to;
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    // Fetch all orders for the date range (use large page size to get everything)
    this.api.getOrdersPaginated(0, 500, undefined, this.dateFrom, this.dateTo).subscribe({
      next: (res: PaginatedOrdersResponse) => {
        this.loading.set(false);
        this.data.set(this.computeClosing(res.content, this.dateFrom));
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Failed to load orders. Check connection.');
      }
    });
  }

  private computeClosing(orders: BusinessOrder[], date: string): DailyClosingData {
    const completed = orders.filter(o => o.orderStatus.toLowerCase() === 'completed' || o.paymentStatus.toLowerCase() === 'success');
    const cancelled = orders.filter(o => o.orderStatus.toLowerCase() === 'cancelled');
    const draft = orders.filter(o => o.orderStatus.toLowerCase() === 'draft');

    const totalRevenue = completed.reduce((sum, o) => sum + (o.totalAmount || 0), 0);
    const refundedAmount = orders.reduce((sum, o) => sum + (o.refundAmount || 0), 0);
    const netRevenue = totalRevenue - refundedAmount;

    // Payment mode breakdown
    const modeMap = new Map<string, { count: number; total: number }>();
    for (const order of completed) {
      const mode = (order.paymentMethod || 'unknown').toLowerCase();
      const existing = modeMap.get(mode) || { count: 0, total: 0 };
      existing.count++;
      existing.total += order.totalAmount || 0;
      modeMap.set(mode, existing);
    }

    const paymentSplits: PaymentSplit[] = Array.from(modeMap.entries())
      .map(([mode, data]) => ({
        mode,
        label: this.getModeLabel(mode),
        count: data.count,
        total: data.total
      }))
      .sort((a, b) => b.total - a.total);

    // Expected cash = cash orders + cash portion of split payments
    const expectedCash = paymentSplits
      .filter(s => s.mode.includes('cash'))
      .reduce((sum, s) => sum + s.total, 0);

    return {
      date,
      totalOrders: orders.length,
      completedOrders: completed.length,
      cancelledOrders: cancelled.length,
      draftOrders: draft.length,
      totalRevenue,
      refundedAmount,
      netRevenue,
      paymentSplits,
      expectedCash,
      topItems: []
    };
  }

  private getModeLabel(mode: string): string {
    switch (mode) {
      case 'cash': return 'Cash';
      case 'upi': return 'UPI';
      case 'pos': case 'card': return 'POS / Card';
      case 'part_cash_upi': case 'part_payment_upi_cash': return 'Cash + UPI Split';
      case 'part_cash_pos': case 'part_payment_cash_pos': return 'Cash + POS Split';
      case 'part_upi_pos': case 'part_payment_upi_pos': return 'UPI + POS Split';
      default: return mode.charAt(0).toUpperCase() + mode.slice(1);
    }
  }

  getIcon(mode: string): string {
    switch (mode) {
      case 'cash': return '💵';
      case 'upi': return '📱';
      case 'pos': case 'card': return '💳';
      default: return '💰';
    }
  }

  exportClosing(): void {
    const d = this.data();
    if (!d) return;
    const rows = [
      ['Daily Closing Report', d.date],
      [''],
      ['Metric', 'Value'],
      ['Total Orders', String(d.totalOrders)],
      ['Completed', String(d.completedOrders)],
      ['Cancelled', String(d.cancelledOrders)],
      ['Draft/Open', String(d.draftOrders)],
      [''],
      ['Total Revenue', String(d.totalRevenue)],
      ['Refunds', String(d.refundedAmount)],
      ['Net Revenue', String(d.netRevenue)],
      ['Expected Cash', String(d.expectedCash)],
      [''],
      ['Payment Mode', 'Count', 'Amount'],
      ...d.paymentSplits.map(s => [s.label, String(s.count), String(s.total)])
    ];
    const csv = rows.map(r => r.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `daily-closing-${d.date}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
