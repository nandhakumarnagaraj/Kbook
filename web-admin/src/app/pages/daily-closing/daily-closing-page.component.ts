import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, DateRangeSelectorComponent],
  template: `
    <div class="page-shell">
      <!-- Operational Topbar (navbar.gallery standard) -->
      <header class="closing-header">
        <div class="header-left">
          <div class="header-title-row">
            <h2>Daily Closing &amp; Settlement</h2>
            <span class="settle-badge">● End-of-Day Reconciliation</span>
          </div>
          <p class="header-sub">Count physical cash, audit payment splits, and verify recognized revenues.</p>
        </div>
        <div class="header-right">
          <app-date-range-selector (rangeChanged)="setRange($event)"/>
          <button type="button" class="ghost-btn-tactile" [disabled]="loading()" (click)="load()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            {{ loading() ? 'Calculating...' : 'Refresh' }}
          </button>
          <button type="button" class="ghost-btn-tactile" (click)="exportClosing()" *ngIf="data()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            Export CSV
          </button>
        </div>
      </header>

      <div class="panel loading" *ngIf="loading()">Calculating settlement figures...</div>
      <div class="alert error" *ngIf="error()">{{ error() }} <button type="button" class="ghost-btn-tactile" (click)="load()">Retry</button></div>

      <ng-container *ngIf="data() as d">

        <!-- Revenue Bento KPI Row (bentogrids.com standard) -->
        <section class="closing-bento-grid" aria-label="Closing summary">
          <article class="bento-kpi bento-kpi--hero">
            <span class="bento-kpi__label">Net Realized Revenue</span>
            <strong class="bento-kpi__value">{{ fmt(d.netRevenue) }}</strong>
            <span class="bento-kpi__sub">Gross billed minus refunded deductions</span>
          </article>

          <article class="bento-kpi">
            <span class="bento-kpi__label">Gross Billed</span>
            <strong class="bento-kpi__value">{{ fmt(d.totalRevenue) }}</strong>
            <span class="bento-kpi__sub">{{ d.completedOrders }} orders completed</span>
          </article>

          <article class="bento-kpi" [class.bento-kpi--alert]="d.refundedAmount > 0">
            <span class="bento-kpi__label">Refunds &amp; Cancellations</span>
            <strong class="bento-kpi__value">{{ fmt(d.refundedAmount) }}</strong>
            <span class="bento-kpi__sub">{{ d.cancelledOrders }} orders cancelled</span>
          </article>

          <article class="bento-kpi" [class.bento-kpi--warn]="d.draftOrders > 0">
            <span class="bento-kpi__label">Open Tables / Drafts</span>
            <strong class="bento-kpi__value">{{ d.draftOrders }}</strong>
            <span class="bento-kpi__sub">Must settle before closing day</span>
          </article>
        </section>

        <!-- Physical Cash Drawer Reconciliation (Tactile Benchmark) -->
        <section class="reconciliation-panel">
          <div class="panel-head-row">
            <div>
              <h3 class="panel-heading">Physical Cash Drawer Reconciliation</h3>
              <p class="panel-sub">Compare physical counted cash against system recorded cash receipts.</p>
            </div>
            <span class="status-chip-live">● Audit Mode</span>
          </div>

          <div class="reconciliation-grid">
            <div class="recon-card recon-card--expected">
              <span class="recon-label">Expected System Cash</span>
              <strong class="recon-value">{{ fmt(d.expectedCash) }}</strong>
              <span class="recon-hint">Sum of cash-paid tickets today</span>
            </div>

            <div class="recon-card recon-card--input">
              <span class="recon-label">Counted Drawer Cash (₹)</span>
              <div class="input-with-currency">
                <span class="currency-prefix">₹</span>
                <input
                  type="number"
                  class="cash-count-field"
                  placeholder="0.00"
                  min="0"
                  step="1"
                  [ngModel]="countedCash()"
                  (ngModelChange)="countedCash.set($event)"
                />
              </div>
              <span class="recon-hint">Enter your actual physical cash count</span>
            </div>

            <div class="recon-card recon-card--variance" [ngClass]="getVarianceClass(d.expectedCash)">
              <span class="recon-label">Drawer Variance</span>
              <strong class="recon-value">{{ getVarianceFormatted(d.expectedCash) }}</strong>
              <span class="variance-badge" [ngClass]="getVarianceBadgeClass(d.expectedCash)">
                {{ getVarianceStatus(d.expectedCash) }}
              </span>
            </div>
          </div>
        </section>

        <!-- Payment Mode Breakdown Cards -->
        <section class="payment-splits-panel">
          <h3 class="panel-heading">Payment Channel Breakdown</h3>
          <p class="panel-sub">Digital collections are auto-settled via payment gateway. Cash requires manual bank deposit.</p>

          <div class="payment-splits-grid">
            <article class="split-card" *ngFor="let split of d.paymentSplits; trackBy: trackByIndex">
              <div class="split-icon-box" [ngClass]="'split-icon--' + split.mode">
                <svg *ngIf="split.mode.includes('cash')" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect width="20" height="12" x="2" y="6" rx="2"/>
                  <circle cx="12" cy="12" r="2"/>
                  <path d="M6 12h.01M18 12h.01"/>
                </svg>
                <svg *ngIf="split.mode.includes('upi')" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect width="14" height="20" x="5" y="2" rx="2" ry="2"/>
                  <path d="M12 18h.01"/>
                </svg>
                <svg *ngIf="!split.mode.includes('cash') && !split.mode.includes('upi')" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect width="20" height="14" x="2" y="5" rx="2"/>
                  <line x1="2" x2="22" y1="10" y2="10"/>
                </svg>
              </div>

              <div class="split-meta">
                <span class="split-label">{{ split.label }}</span>
                <strong class="split-amount">{{ fmt(split.total) }}</strong>
                <span class="split-count">{{ split.count }} transactions</span>
              </div>
            </article>
          </div>
        </section>

        <!-- Order Volume Summary -->
        <section class="order-dist-panel">
          <h3 class="panel-heading">Ticket Status Audit</h3>
          <div class="audit-row-grid">
            <div class="audit-stat">
              <span class="stat-name">Total Tickets Minted</span>
              <strong class="stat-digit">{{ d.totalOrders }}</strong>
            </div>
            <div class="audit-stat stat--success">
              <span class="stat-name">Settled &amp; Paid</span>
              <strong class="stat-digit">{{ d.completedOrders }}</strong>
            </div>
            <div class="audit-stat stat--danger">
              <span class="stat-name">Cancelled / Voided</span>
              <strong class="stat-digit">{{ d.cancelledOrders }}</strong>
            </div>
            <div class="audit-stat stat--warn">
              <span class="stat-name">Unsettled / Draft</span>
              <strong class="stat-digit">{{ d.draftOrders }}</strong>
            </div>
          </div>
        </section>

      </ng-container>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      background: var(--pos-bg-body, #F6F8FD);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', sans-serif;
    }

    .page-shell {
      padding: 1.5rem 2rem;
      max-width: 1440px;
      margin: 0 auto;
    }

    /* ── Header (navbar.gallery standard) ── */
    .closing-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
      margin-bottom: 1.75rem;
      padding-bottom: 1.25rem;
      border-bottom: 1px solid #EEF0F7;
    }
    .header-title-row {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      margin-bottom: 0.25rem;
    }
    .header-title-row h2 {
      margin: 0;
      font-size: 1.45rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
    }
    .settle-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.2rem 0.65rem;
      border-radius: 999px;
      font-size: 0.74rem;
      font-weight: 700;
      background: #F5F3FF;
      color: #5D45FD;
      border: 1px solid #DDD6FE;
    }
    .header-sub {
      margin: 0;
      font-size: 0.88rem;
      color: #64748B;
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .ghost-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.55rem 1rem;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      font-size: 0.84rem;
      font-weight: 600;
      color: #334155;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .ghost-btn-tactile:hover:not(:disabled) {
      background: #F8FAFC;
      border-color: #CBD5E1;
      color: #0F172A;
    }
    .ghost-btn-tactile:active:not(:disabled) {
      transform: scale(0.97);
    }

    /* ── Bento KPI Row (bentogrids.com standard) ── */
    .closing-bento-grid {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr 1fr;
      gap: 1rem;
      margin-bottom: 1.5rem;
    }
    @media (max-width: 1024px) {
      .closing-bento-grid { grid-template-columns: 1fr 1fr; }
    }
    @media (max-width: 640px) {
      .closing-bento-grid { grid-template-columns: 1fr; }
    }

    .bento-kpi {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.25rem 1.4rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .bento-kpi--hero {
      background: linear-gradient(135deg, #5D45FD 0%, #4328E0 100%);
      color: #FFFFFF;
      border: none;
      box-shadow: 0 8px 24px rgba(93, 69, 253, 0.28);
    }
    .bento-kpi--hero .bento-kpi__label, .bento-kpi--hero .bento-kpi__sub {
      color: rgba(255, 255, 255, 0.85);
    }
    .bento-kpi--hero .bento-kpi__value {
      color: #FFFFFF;
    }
    .bento-kpi__label {
      font-size: 0.78rem;
      font-weight: 600;
      color: #8F95B2;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .bento-kpi__value {
      font-size: 1.65rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
    }
    .bento-kpi__sub {
      font-size: 0.76rem;
      color: #64748B;
    }
    .bento-kpi--alert {
      border-color: #FECACA;
      background: #FFFDFD;
    }
    .bento-kpi--alert .bento-kpi__value { color: #DC2626; }
    .bento-kpi--warn {
      border-color: #FDE68A;
      background: #FFFDF7;
    }
    .bento-kpi--warn .bento-kpi__value { color: #D97706; }

    /* ── Cash Reconciliation Panel ── */
    .reconciliation-panel {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 20px;
      padding: 1.5rem 1.75rem;
      margin-bottom: 1.5rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    .panel-head-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1.25rem;
    }
    .panel-heading {
      margin: 0 0 0.2rem;
      font-size: 1.1rem;
      font-weight: 700;
      color: #0F172A;
    }
    .panel-sub {
      margin: 0;
      font-size: 0.84rem;
      color: #64748B;
    }
    .status-chip-live {
      font-size: 0.72rem;
      font-weight: 700;
      color: #059669;
      background: #ECFDF5;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
    }

    .reconciliation-grid {
      display: grid;
      grid-template-columns: 1fr 1.2fr 1fr;
      gap: 1.25rem;
    }
    @media (max-width: 860px) {
      .reconciliation-grid { grid-template-columns: 1fr; }
    }

    .recon-card {
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 16px;
      padding: 1.25rem 1.4rem;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .recon-label {
      font-size: 0.76rem;
      font-weight: 700;
      color: #64748B;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .recon-value {
      font-size: 1.55rem;
      font-weight: 800;
      color: #0F172A;
      font-variant-numeric: tabular-nums;
    }
    .recon-hint {
      font-size: 0.75rem;
      color: #94A3B8;
    }

    .input-with-currency {
      position: relative;
      display: flex;
      align-items: center;
      margin: 0.25rem 0;
    }
    .currency-prefix {
      position: absolute;
      left: 1rem;
      font-weight: 700;
      color: #64748B;
      font-size: 1.1rem;
    }
    .cash-count-field {
      width: 100%;
      padding: 0.65rem 1rem 0.65rem 2.2rem;
      font-size: 1.25rem;
      font-weight: 800;
      color: #0F172A;
      border: 2px solid #CBD5E1;
      border-radius: 12px;
      background: #FFFFFF;
      font-variant-numeric: tabular-nums;
      outline: none;
      transition: border-color 140ms ease;
    }
    .cash-count-field:focus {
      border-color: #5D45FD;
      box-shadow: 0 0 0 3px rgba(93, 69, 253, 0.12);
    }

    .variance-badge {
      display: inline-block;
      padding: 0.2rem 0.65rem;
      border-radius: 8px;
      font-size: 0.74rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .variance-badge--match { background: #ECFDF5; color: #059669; }
    .variance-badge--over { background: #EFF6FF; color: #2563EB; }
    .variance-badge--short { background: #FEF2F2; color: #DC2626; }

    /* ── Payment Splits Grid ── */
    .payment-splits-panel {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 20px;
      padding: 1.5rem 1.75rem;
      margin-bottom: 1.5rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    .payment-splits-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      gap: 1rem;
      margin-top: 1rem;
    }
    .split-card {
      background: #F8FAFC;
      border: 1px solid #EEF0F7;
      border-radius: 14px;
      padding: 1.15rem 1.25rem;
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .split-icon-box {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #FFFFFF;
      color: #5D45FD;
      border: 1px solid #E2E8F0;
      flex-shrink: 0;
    }
    .split-icon--cash { color: #D97706; background: #FFFDF7; }
    .split-icon--upi { color: #059669; background: #F0FDF4; }
    .split-meta {
      display: flex;
      flex-direction: column;
    }
    .split-label {
      font-size: 0.78rem;
      font-weight: 600;
      color: #64748B;
    }
    .split-amount {
      font-size: 1.15rem;
      font-weight: 800;
      color: #0F172A;
      font-variant-numeric: tabular-nums;
    }
    .split-count {
      font-size: 0.72rem;
      color: #94A3B8;
    }

    /* ── Order Status Audit Row ── */
    .order-dist-panel {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 20px;
      padding: 1.5rem 1.75rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    .audit-row-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 1rem;
      margin-top: 1rem;
    }
    @media (max-width: 768px) {
      .audit-row-grid { grid-template-columns: 1fr 1fr; }
    }
    .audit-stat {
      background: #F8FAFC;
      border-radius: 12px;
      padding: 1rem 1.2rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .stat-name {
      font-size: 0.78rem;
      font-weight: 600;
      color: #64748B;
    }
    .stat-digit {
      font-size: 1.4rem;
      font-weight: 800;
      color: #0F172A;
      font-variant-numeric: tabular-nums;
    }
    .stat--success .stat-digit { color: #059669; }
    .stat--danger .stat-digit { color: #DC2626; }
    .stat--warn .stat-digit { color: #D97706; }
  `]
})
export class DailyClosingPageComponent {
  private readonly api = inject(BusinessApiService);

  loading = signal(false);
  error = signal('');
  data = signal<DailyClosingData | null>(null);
  countedCash = signal<number | null>(null);

  private dateFrom = todayStr();
  private dateTo = todayStr();

  readonly fmt = formatCurrency;

  constructor() { this.load(); }

  trackByIndex = (_: number, __: unknown) => _;

  setRange(range: { from: string; to: string }): void {
    this.dateFrom = range.from;
    this.dateTo = range.to;
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.getDailyClosing(this.dateFrom).subscribe({
      next: (res: any) => {
        this.loading.set(false);
        this.data.set({
          date: res.date || this.dateFrom,
          totalOrders: res.totalOrders || 0,
          completedOrders: res.completedOrders || 0,
          cancelledOrders: res.cancelledOrders || 0,
          draftOrders: res.draftOrders || 0,
          totalRevenue: res.totalRevenue || 0,
          refundedAmount: res.refundedAmount || 0,
          netRevenue: res.netRevenue != null ? res.netRevenue : (res.totalRevenue || 0),
          paymentSplits: (res.paymentSplits || []).map((s: any) => ({
            mode: s.mode, label: this.getModeLabel(s.mode), count: s.count || 0, total: s.total || 0
          })),
          expectedCash: res.expectedCash || 0,
          topItems: res.topItems || []
        });
      },
      error: () => {
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
    });
  }

  private computeClosing(orders: BusinessOrder[], date: string): DailyClosingData {
    const completed = orders.filter(o => o.orderStatus.toLowerCase() === 'completed' || o.paymentStatus.toLowerCase() === 'success');
    const cancelled = orders.filter(o => o.orderStatus.toLowerCase() === 'cancelled');
    const draft = orders.filter(o => o.orderStatus.toLowerCase() === 'draft');

    const totalRevenue = completed.reduce((sum, o) => sum + (o.totalAmount || 0), 0);
    const refundedAmount = orders.reduce((sum, o) => sum + (o.refundAmount || 0), 0);
    const netRevenue = totalRevenue - refundedAmount;

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
      case 'cash': return 'Cash Drawer';
      case 'upi': return 'Dynamic UPI';
      case 'pos': case 'card': return 'Card Terminal (POS)';
      case 'part_cash_upi': case 'part_payment_upi_cash': return 'Cash + UPI Split';
      case 'part_cash_pos': case 'part_payment_cash_pos': return 'Cash + Card Split';
      case 'part_upi_pos': case 'part_payment_upi_pos': return 'UPI + Card Split';
      default: return mode.charAt(0).toUpperCase() + mode.slice(1);
    }
  }

  getVarianceFormatted(expectedCash: number): string {
    const counted = this.countedCash();
    if (counted === null || isNaN(counted)) return '—';
    const delta = counted - expectedCash;
    if (Math.abs(delta) < 0.01) return '₹ 0.00';
    return (delta > 0 ? '+' : '') + this.fmt(delta);
  }

  getVarianceStatus(expectedCash: number): string {
    const counted = this.countedCash();
    if (counted === null || isNaN(counted)) return 'Pending Count';
    const delta = counted - expectedCash;
    if (Math.abs(delta) < 1) return 'Balanced';
    if (delta > 0) return 'Overage';
    return 'Shortage';
  }

  getVarianceClass(expectedCash: number): string {
    const counted = this.countedCash();
    if (counted === null || isNaN(counted)) return '';
    const delta = counted - expectedCash;
    if (Math.abs(delta) < 1) return 'recon-card--match';
    if (delta > 0) return 'recon-card--overage';
    return 'recon-card--shortage';
  }

  getVarianceBadgeClass(expectedCash: number): string {
    const status = this.getVarianceStatus(expectedCash);
    if (status === 'Balanced') return 'variance-badge--match';
    if (status === 'Overage') return 'variance-badge--over';
    if (status === 'Shortage') return 'variance-badge--short';
    return '';
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
