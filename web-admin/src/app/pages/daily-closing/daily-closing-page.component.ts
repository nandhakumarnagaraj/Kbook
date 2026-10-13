import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessOrder, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency } from '../../shared/formatters';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { readStoredDateRange, storeDateRange } from '../../core/utils/date-range-storage.util';

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
  imports: [
    CommonModule,
    FormsModule,
    EmptyStateComponent,
    PageHeaderComponent,
    DateRangeSelectorComponent,
    ApiStateComponent
  ],
  template: `
    <div class="page-shell">
      <!-- Operational Topbar (shared design system) -->
      <app-page-header
        class="no-print"
        title="Daily Closing &amp; Settlement"
        subtitle="Count physical cash, audit payment splits, and verify recognized revenues.">
        <span
          kbHeaderBadge
          class="kpi-tag"
          [class.kpi-tag--green]="isToday()"
          [class.kpi-tag--neutral]="!isToday()">
          {{ isToday() ? '● Live Session (Today)' : '✓ Closed Business Day (' + selectedDate() + ')' }}
        </span>

        <ng-container>
          <!-- One date, one day: the shared selector in single mode, so Daily Closing
               offers the same Today / Yesterday / Custom pills as Reports and Orders. -->
          <app-date-range-selector
            mode="single"
            singleDateLabel="Closing date"
            [maxDate]="today"
            [initialRange]="closingRange()"
            (rangeChanged)="onClosingDateChange($event)">
          </app-date-range-selector>
          <button type="button" class="header-action-btn" [disabled]="loading()" (click)="load()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            Refresh
          </button>
          <button type="button" class="header-action-btn" (click)="printSlip()" *ngIf="data()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <polyline points="6 9 6 2 18 2 18 9"/>
              <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/>
              <rect x="6" y="14" width="12" height="8"/>
            </svg>
            Print Slip
          </button>
          <button type="button" class="header-action-btn" (click)="exportClosing()" *ngIf="data()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            Export CSV
          </button>
        </ng-container>
      </app-page-header>

      <!-- Alert if Draft Orders are Open -->
      <div class="draft-warning-banner no-print" *ngIf="data() && data()!.draftOrders > 0">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3M12 9v4m0 4h.01"/>
        </svg>
        <span>
          <strong>Action Required:</strong> {{ data()!.draftOrders }} draft/open table order(s) remain unbilled for {{ selectedDate() }}. Please settle or void them in Active Orders to finalize drawer reconciliation.
        </span>
      </div>

      <!-- Stale refresh banner (prototype pattern) -->
      <div class="stale-banner" *ngIf="error() && data()" role="status" aria-live="polite">
        <span>Couldn't update closing figures. {{ error() }}</span>
        <button type="button" class="btn-retry" (click)="load()">Retry</button>
      </div>

      <div class="panel loading" *ngIf="loading() && !data()" role="status" aria-busy="true">
        <span class="sr-only">Calculating settlement figures...</span>
        <div class="skeleton-stack">
          <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4]"></div>
        </div>
      </div>

      <app-api-state
        *ngIf="error() && !data()"
        [message]="error()"
        (retry)="load()"
      ></app-api-state>

      <ng-container *ngIf="data() as d">
        <app-empty-state
          *ngIf="!loading() && !error() && d.totalOrders === 0"
          icon="🧾"
          title="No sales recorded for this date"
          text="Closing figures appear once POS orders for the selected day are settled. Pick another date or refresh to try again."
          actionLabel="Refresh"
          (action)="load()"
        ></app-empty-state>

        <ng-container *ngIf="d.totalOrders > 0">

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
                <span class="currency-prefix" aria-hidden="true">₹</span>
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
                <svg *ngIf="split.mode.includes('cash')" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <rect width="20" height="12" x="2" y="6" rx="2"/>
                  <circle cx="12" cy="12" r="2"/>
                  <path d="M6 12h.01M18 12h.01"/>
                </svg>
                <svg *ngIf="split.mode.includes('upi')" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <rect width="14" height="20" x="5" y="2" rx="2" ry="2"/>
                  <path d="M12 18h.01"/>
                </svg>
                <svg *ngIf="!split.mode.includes('cash') && !split.mode.includes('upi')" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
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
      </ng-container>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      background: var(--pos-bg-body, var(--kb-color-surface-2));
      font-family: var(--kb-font-sans);
    }

    /* Header layout and the header status badge both come from shared code —
       <app-page-header> and .kpi-tag in bento.css — so this page keeps no page-shell
       override and owns no badge styles of its own. */

    /* The date control is the shared selector (see app-date-range-selector), so this page
       deliberately owns no pill or date-input styles of its own. */

    /* Draft warning banner */
    .draft-warning-banner {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.85rem 1.25rem;
      margin-bottom: 1.25rem;
      border-radius: 12px;
      background: var(--kb-color-warning-soft);
      border: 1px solid var(--kb-color-warning-soft);
      color: var(--kb-color-warning);
      font-size: 0.86rem;
      line-height: 1.4;
    }
    .draft-warning-banner svg {
      flex-shrink: 0;
      color: var(--kb-color-warning);
    }

    @media print {
      .no-print { display: none !important; }
      body, .page-shell { background: #FFFFFF !important; padding: 0 !important; }
      .closing-bento-grid, .reconciliation-panel, .payment-splits-panel, .order-dist-panel {
        box-shadow: none !important;
        border: 1px solid var(--kb-color-border) !important;
      }
      .bento-kpi--hero {
        background: #FFFFFF !important;
        color: var(--kb-color-foreground) !important;
        border: 2px solid var(--kb-color-foreground) !important;
        box-shadow: none !important;
      }
      .bento-kpi--hero .bento-kpi__value { color: var(--kb-color-foreground) !important; }
      .bento-kpi--hero .bento-kpi__label, .bento-kpi--hero .bento-kpi__sub { color: var(--kb-color-muted-foreground) !important; }
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
      border: 1px solid var(--kb-color-surface-2);
      border-radius: 18px;
      padding: 1.25rem 1.4rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .bento-kpi--hero {
      background: linear-gradient(135deg, var(--kb-color-primary) 0%, var(--kb-color-primary) 100%);
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
      color: var(--kb-color-muted-foreground);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .bento-kpi__value {
      font-size: 1.65rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
    }
    .bento-kpi__sub {
      font-size: 0.76rem;
      color: var(--kb-color-muted-foreground);
    }
    .bento-kpi--alert {
      border-color: var(--kb-color-danger-soft);
      background: var(--kb-color-surface);
    }
    .bento-kpi--alert .bento-kpi__value { color: var(--kb-color-danger); }
    .bento-kpi--warn {
      border-color: var(--kb-color-warning-soft);
      background: var(--kb-color-surface);
    }
    .bento-kpi--warn .bento-kpi__value { color: var(--kb-color-warning); }

    /* ── Cash Reconciliation Panel ── */
    .reconciliation-panel {
      background: #FFFFFF;
      border: 1px solid var(--kb-color-surface-2);
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
      color: var(--kb-color-foreground);
    }
    .panel-sub {
      margin: 0;
      font-size: 0.84rem;
      color: var(--kb-color-muted-foreground);
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
      background: var(--kb-color-bg-app);
      border: 1px solid var(--kb-color-border);
      border-radius: 16px;
      padding: 1.25rem 1.4rem;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .recon-label {
      font-size: 0.76rem;
      font-weight: 700;
      color: var(--kb-color-muted-foreground);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .recon-value {
      font-size: 1.55rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }
    .recon-hint {
      font-size: 0.75rem;
      color: var(--kb-color-muted-foreground);
    }
    .recon-card--match { border-color: var(--kb-color-success-soft); background: var(--kb-color-success-soft); }
    .recon-card--match .recon-value { color: var(--kb-color-success); }
    .recon-card--overage { border-color: var(--kb-color-warning-soft); background: var(--kb-color-warning-soft); }
    .recon-card--overage .recon-value { color: var(--kb-color-warning); }
    .recon-card--shortage { border-color: var(--kb-color-danger-soft); background: var(--kb-color-danger-soft); }
    .recon-card--shortage .recon-value { color: var(--kb-color-danger); }

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
      color: var(--kb-color-muted-foreground);
      font-size: 1.1rem;
    }
    .cash-count-field {
      width: 100%;
      padding: 0.65rem 1rem 0.65rem 2.2rem;
      font-size: 1.25rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      border: 2px solid var(--kb-color-border-strong);
      border-radius: 12px;
      background: #FFFFFF;
      font-variant-numeric: tabular-nums;
      outline: none;
      transition: border-color 140ms ease;
    }
    .cash-count-field:focus {
      border-color: var(--kb-color-primary);
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
    .variance-badge--match { background: var(--kb-color-success-soft); color: var(--kb-color-success); }
    .variance-badge--over { background: var(--kb-color-warning-soft); color: var(--kb-color-warning); }
    .variance-badge--short { background: var(--kb-color-danger-soft); color: var(--kb-color-danger); }

    /* ── Payment Splits Grid ── */
    .payment-splits-panel {
      background: #FFFFFF;
      border: 1px solid var(--kb-color-surface-2);
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
      background: var(--kb-color-bg-app);
      border: 1px solid var(--kb-color-surface-2);
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
      color: var(--kb-color-primary);
      border: 1px solid var(--kb-color-border);
      flex-shrink: 0;
    }
    .split-icon--cash { color: var(--kb-color-warning); background: var(--kb-color-surface); }
    .split-icon--upi { color: var(--kb-color-success); background: var(--kb-color-success-soft); }
    .split-meta {
      display: flex;
      flex-direction: column;
    }
    .split-label {
      font-size: 0.78rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
    }
    .split-amount {
      font-size: 1.15rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }
    .split-count {
      font-size: 0.72rem;
      color: var(--kb-color-muted-foreground);
    }

    /* ── Order Status Audit Row ── */
    .order-dist-panel {
      background: #FFFFFF;
      border: 1px solid var(--kb-color-surface-2);
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
      background: var(--kb-color-bg-app);
      border-radius: 12px;
      padding: 1rem 1.2rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .stat-name {
      font-size: 0.78rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
    }
    .stat-digit {
      font-size: 1.4rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }
    .stat--success .stat-digit { color: var(--kb-color-success); }
    .stat--danger .stat-digit { color: var(--kb-color-danger); }
    .stat--warn .stat-digit { color: var(--kb-color-warning); }
  `]
})
export class DailyClosingPageComponent {
  private readonly api = inject(BusinessApiService);

  loading = signal(false);
  error = signal('');
  data = signal<DailyClosingData | null>(null);
  countedCash = signal<number | null>(null);

  readonly today = todayStr();
  /**
   * Opening on Today ignored the day the user had just picked on Reports or Orders, so
   * the closing figures silently described a different date than the page they came
   * from. The stored period is shared with those pages; its end is the day they were
   * last looking at. Clamped to today because a closing cannot be counted for a future
   * day.
   */
  readonly selectedDate = signal<string>(this.initialClosingDate());
  readonly isToday = computed(() => this.selectedDate() === this.today);

  readonly fmt = formatCurrency;

  constructor() { this.load(); }

  trackByIndex = (_: number, __: unknown) => _;

  /** The single day the closing is counting, as the shared selector's range. */
  readonly closingRange = computed<{ from: string; to: string }>(() => {
    const day = this.selectedDate();
    return { from: day, to: day };
  });

  onClosingDateChange(range: { from: string; to: string }): void {
    this.selectDate(range.from);
  }

  private initialClosingDate(): string {
    const stored = readStoredDateRange()?.to;
    return stored && stored <= this.today ? stored : this.today;
  }

  selectDate(d: string): void {
    if (!d || d === this.selectedDate()) return;
    this.selectedDate.set(d);
    storeDateRange({ from: d, to: d });
    this.load();
  }

  printSlip(): void {
    window.print();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    const d = this.selectedDate();
    this.api.getDailyClosing(d).subscribe({
      next: (res: any) => {
        this.loading.set(false);
        this.data.set({
          date: res.date || d,
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
        this.api.getOrdersPaginated(0, 500, undefined, d, d).subscribe({
          next: (res: PaginatedOrdersResponse) => {
            this.loading.set(false);
            this.data.set(this.computeClosing(res.content, d));
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
