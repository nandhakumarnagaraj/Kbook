import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EMPTY, expand, reduce } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessOrder, HourlySalesRow } from '../../core/models/api.models';
import { formatCurrency } from '../../shared/formatters';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { DateRangeToolbarComponent } from '../../shared/date-range-toolbar.component';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
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
  topItems: { name: string; qty: number; amount?: number | null }[];
}

interface TerminalSalesRow {
  terminal: string;
  orders: number;
  amount: number;
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
    DateRangeToolbarComponent,
    ApiStateComponent,
    KpiRowComponent
  ],
  template: `
    <div class="page-shell">
      <!-- Operational Topbar (shared design system) -->
      <!-- The title and the day badge belong on a printed slip, so the header
           itself prints; the shared print sheet hides its action cluster
           (date picker, export, refresh) instead. -->
      <app-page-header
        title="Daily Closing &amp; Settlement"
        subtitle="Count physical cash, audit payment splits, and verify recognized revenues.">
        <span
          kbHeaderBadge
          class="kpi-tag"
          [class.kpi-tag--green]="isToday()"
          [class.kpi-tag--neutral]="!isToday()">
          {{ isToday() ? 'Today · Current Business Day' : 'Historical Date · ' + selectedDate() }}
        </span>

        <app-date-range-toolbar
          mode="single"
          singleDateLabel="Closing date"
          [maxDate]="today"
          [initialRange]="closingRange()"
          [downloadDisabled]="!data()"
          downloadLabel="Download daily closing CSV"
          refreshLabel="Refresh closing figures"
          [refreshDisabled]="loading()"
          [refreshing]="loading()"
          (rangeChanged)="onClosingDateChange($event)"
          (download)="exportClosing()"
          (refresh)="load()">
        </app-date-range-toolbar>
      </app-page-header>

      <!-- The picker is hidden on paper, and "Today" is not a date on a slip
           that will be filed — so the closed day is stated in full. -->
      <p class="print-only print-meta">
        Closing date: {{ selectedDate() }}{{ isToday() ? ' (today)' : '' }}
      </p>

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

      <div class="panel loading" *ngIf="loading()" role="status" aria-busy="true">
        <span class="visually-hidden">Calculating settlement figures...</span>
        <div class="skeleton-stack">
          <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4]"></div>
        </div>
      </div>

      <app-api-state
        *ngIf="error() && !data()"
        [message]="error()"
        (retry)="load()"
      ></app-api-state>

      <ng-container *ngIf="viewData() as d">
        <app-empty-state
          *ngIf="!loading() && !error() && d.totalOrders === 0"
          icon="🧾"
          title="No sales recorded for this date"
          text="Closing figures appear once POS orders for the selected day are settled. Pick another date or refresh to try again."
          actionLabel="Refresh"
          (action)="load()"
        ></app-empty-state>

        <ng-container *ngIf="d.totalOrders > 0">

        <!-- Reference composition: KPI strip, then summary tables + closing rail -->
        <app-kpi-row [items]="closingKpis()" ariaLabel="Closing summary" />

        <!-- Reference: summary tables on the left, closing status rail on the right -->
        <div class="closing-main-grid">
          <div class="closing-summary-col">
            <section class="panel soft-section">
              <h3 class="intel-card__title">Payment Summary</h3>
              <p class="panel-sub">Breakdown of revenue by payment mode.</p>
              <div class="table-wrap">
                <table class="data-table">
                  <caption class="visually-hidden">Payment summary for the closing day</caption>
                  <thead>
                    <tr>
                      <th scope="col">Payment Mode</th>
                      <th scope="col" class="num-cell">Orders</th>
                      <th scope="col" class="num-cell">Amount (₹)</th>
                      <th scope="col" class="num-cell">% of Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let row of paymentRows(); trackBy: trackByIndex">
                      <th scope="row" class="font-semibold">{{ row.label }}</th>
                      <td class="num-cell tabular-num">{{ row.count }}</td>
                      <td class="num-cell tabular-num">{{ fmt(row.total) }}</td>
                      <td class="num-cell tabular-num">{{ row.pct }}</td>
                    </tr>
                    <tr *ngIf="!paymentRows().length">
                      <td colspan="4" class="muted">No settled payments recorded.</td>
                    </tr>
                  </tbody>
                  <tfoot>
                    <tr class="table-totals-row">
                      <td colspan="2"><strong>Total</strong></td>
                      <td class="num-cell tabular-num font-bold">{{ fmt(d.totalRevenue) }}</td>
                      <td class="num-cell tabular-num font-bold">100%</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </section>

            <section class="panel soft-section">
              <h3 class="intel-card__title">Order Status Summary</h3>
              <p class="panel-sub">Status-wise order count and value.</p>
              <div class="table-wrap">
                <table class="data-table">
                  <caption class="visually-hidden">Order status summary for the closing day</caption>
                  <thead>
                    <tr>
                      <th scope="col">Status</th>
                      <th scope="col" class="num-cell">Orders</th>
                      <th scope="col" class="num-cell">Amount (₹)</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let row of statusRows(); trackBy: trackByIndex">
                      <th scope="row" class="font-semibold"><span class="kpi-tag" [ngClass]="row.tagClass">{{ row.label }}</span></th>
                      <td class="num-cell tabular-num">{{ row.count }}</td>
                      <td class="num-cell tabular-num">{{ fmt(row.amount) }}</td>
                    </tr>
                  </tbody>
                  <tfoot>
                    <tr class="table-totals-row">
                      <td><strong>Total</strong></td>
                      <td class="num-cell tabular-num font-bold">{{ d.totalOrders }}</td>
                      <td class="num-cell tabular-num font-bold">{{ fmt(d.totalRevenue) }}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </section>
          </div>

          <!-- Closing Status rail: mirrors the reconciliation state below it -->
          <aside class="panel soft-section closing-rail" aria-label="Closing status">
            <div class="closing-rail__head">
              <h3 class="intel-card__title">Closing Status</h3>
              <span class="kpi-tag" [ngClass]="countsCounted() ? 'kpi-tag--green' : 'kpi-tag--amber'">
                {{ countsCounted() ? 'Reconciled' : 'Open' }}
              </span>
            </div>
            <p class="closing-rail__note" [ngClass]="countsCounted() ? 'closing-rail__note--ok' : 'closing-rail__note--todo'">
              {{ countsCounted()
                ? 'Cash reconciled. Generate the final report when you are ready to close the day.'
                : 'Complete cash reconciliation — count the drawer below — to generate the final report.' }}
            </p>
            <ol class="closing-steps">
              <li class="closing-step closing-step--done">
                <span class="closing-step__dot" aria-hidden="true">1</span>
                <div class="closing-step__copy"><strong>Review Sales Summary</strong><span>Verify total sales and orders</span></div>
              </li>
              <li class="closing-step" [ngClass]="{ 'closing-step--done': countsCounted() }">
                <span class="closing-step__dot" aria-hidden="true">2</span>
                <div class="closing-step__copy"><strong>Count Physical Cash</strong><span>{{ countedCash() == null ? 'Enter actual cash collected' : 'Counted ' + fmt(countedCash()!) }}</span></div>
              </li>
              <li class="closing-step" [ngClass]="{ 'closing-step--done': countsCounted() }">
                <span class="closing-step__dot" aria-hidden="true">3</span>
                <div class="closing-step__copy"><strong>Review Variance</strong><span>{{ countedCash() == null ? 'Pending cash count' : 'Difference: ' + getVarianceFormatted(d.expectedCash) }}</span></div>
              </li>
              <li class="closing-step" [ngClass]="{ 'closing-step--done': reportGenerated() }">
                <span class="closing-step__dot" aria-hidden="true">4</span>
                <div class="closing-step__copy"><strong>Close Business Day</strong><span>{{ reportGenerated() ? 'Final report generated' : 'Generate the final report' }}</span></div>
              </li>
            </ol>
            <button
              type="button"
              class="header-action-btn header-action-btn--primary closing-rail__cta"
              [disabled]="!countsCounted()"
              (click)="closeBusinessDay()"
              aria-label="Proceed to close day and generate the final closing report">
              Proceed to Close Day
            </button>
            <span class="closing-rail__hint">Closing generates the final report on this device; the cash count stays in this browser.</span>
          </aside>
        </div>

        <!-- Physical Cash Drawer Reconciliation (Tactile Benchmark) -->
        <section class="panel soft-section reconciliation-panel">
          <div class="panel-head-row">
            <div>
              <h3 class="intel-card__title">Physical Cash Drawer Reconciliation</h3>
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
              <!-- A number field prints as an empty box, so the slip carries the
                   counted value as text instead. -->
              <strong class="recon-value print-only">
                {{ countedCash() == null ? '—' : fmt(countedCash()!) }}
              </strong>
              <div class="input-with-currency no-print">
                <span class="currency-prefix" aria-hidden="true">₹</span>
                <input
                  type="number"
                  class="cash-count-field"
                  placeholder="0.00"
                  min="0"
                  step="1"
                  [ngModel]="countedCash()"
                  (ngModelChange)="setCountedCash($event)"
                />
              </div>
              <span class="recon-hint">
                {{ countedCashSavedLocally()
                  ? 'Saved in this browser only; it has not been submitted as a close record.'
                  : 'This count is kept in this browser only; it does not submit or close the day.' }}
              </span>
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

        <!-- Reference bottom strip: hourly curve and best sellers, printed on the slip -->
        <div class="closing-bottom-grid">
          <section class="panel soft-section">
            <h3 class="intel-card__title">Hourly Sales</h3>
            <div class="hourly-chart" *ngIf="hourlyRows().length; else hourlyEmpty">
              <div class="hourly-chart__area">
                <div class="hourly-bar" *ngFor="let h of hourlyRows(); trackBy: trackByIndex"
                  [ngClass]="{ 'hourly-bar--peak': h.itemsSold === hourlyPeak() }"
                  [style.height.%]="hourlyBarHeight(h)">
                  <span class="hourly-bar__tip" *ngIf="h.itemsSold === hourlyPeak()">{{ h.itemsSold }} sold</span>
                </div>
              </div>
              <div class="hourly-chart__axis" aria-hidden="true">
                <span *ngFor="let h of hourlyRows(); let i = index" [class.hourly-chart__tick--edge]="i % 3 === 0">{{ hourLabel(h) }}</span>
              </div>
            </div>
            <ng-template #hourlyEmpty>
              <p class="muted">No hourly sales recorded to plot for this day.</p>
            </ng-template>
          </section>

          <section class="panel soft-section">
            <div class="closing-rail__head">
              <h3 class="intel-card__title">Top Selling Items</h3>
            </div>
            <div class="table-wrap">
              <table class="data-table">
                <caption class="visually-hidden">Top selling items for the closing day</caption>
                <thead>
                  <tr>
                    <th scope="col" class="num-cell">#</th>
                    <th scope="col">Item Name</th>
                    <th scope="col" class="num-cell">Qty</th>
                    <th scope="col" class="num-cell">Amount (₹)</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let item of d.topItems; let rank = index; trackBy: trackByIndex">
                    <th scope="row" class="num-cell tabular-num">{{ rank + 1 }}</th>
                    <td>{{ item.name }}</td>
                    <td class="num-cell tabular-num">{{ item.qty }}</td>
                    <td class="num-cell tabular-num">{{ item.amount == null ? '—' : fmt(item.amount) }}</td>
                  </tr>
                  <tr *ngIf="!d.topItems.length">
                    <td colspan="4" class="muted">No item-level sales recorded.</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>

        <!-- Terminal-wise sales appear only when the closing source reports them -->
        <section class="panel soft-section" *ngIf="terminalSales().length">
          <h3 class="intel-card__title">Terminal-wise Sales</h3>
          <div class="table-wrap">
            <table class="data-table">
              <caption class="visually-hidden">Sales split by terminal for the closing day</caption>
              <thead>
                <tr>
                  <th scope="col">Terminal</th>
                  <th scope="col" class="num-cell">Orders</th>
                  <th scope="col" class="num-cell">Amount (₹)</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let row of terminalSales(); trackBy: trackByIndex">
                  <th scope="row" class="font-semibold">{{ row.terminal }}</th>
                  <td class="num-cell tabular-num">{{ row.orders }}</td>
                  <td class="num-cell tabular-num">{{ fmt(row.amount) }}</td>
                </tr>
              </tbody>
              <tfoot>
                <tr class="table-totals-row">
                  <td><strong>Total</strong></td>
                  <td class="num-cell tabular-num font-bold">{{ terminalSalesTotal().orders }}</td>
                  <td class="num-cell tabular-num font-bold">{{ fmt(terminalSalesTotal().amount) }}</td>
                </tr>
              </tfoot>
            </table>
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
      background: var(--pos-bg-body);
      font-family: var(--kb-font-sans);
    }

    /* Header layout and the header status badge both come from shared code —
       <app-page-header> and .kpi-tag in bento.css — so this page keeps no page-shell
       override and owns no badge styles of its own. */

    /* Date-field styles come from the shared app-date-range-selector component. */


    /* Draft warning banner */
    .draft-warning-banner {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      padding: 0.85rem var(--kb-space-5);
      margin-bottom: 1.25rem;
      border-radius: var(--kb-radius-xl);
      background: var(--kb-color-warning-soft);
      border: 1px solid var(--kb-color-warning-soft);
      color: var(--kb-color-warning);
      font-size: var(--kb-text-md);
      line-height: 1.4;
    }
    .draft-warning-banner svg {
      flex-shrink: 0;
      color: var(--kb-color-warning);
    }

    /* ── Cash Reconciliation Panel ── */
    /* The .panel supplies surface, radius, border and shadow; .soft-section
       supplies the card padding. Only the rhythm between panels is local. */
    .reconciliation-panel {
      margin-bottom: 1.5rem;
    }
    .panel-head-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1.25rem;
    }
    .panel-sub {
      margin: 0;
      font-size: var(--kb-text-sm);
      color: var(--kb-color-muted-foreground);
    }
    .reconciliation-grid {
      display: grid;
      grid-template-columns: 1fr 1.2fr 1fr;
      gap: var(--kb-space-5);
    }
    @media (max-width: 1024px) {
      .reconciliation-grid { grid-template-columns: 1fr; }
    }

    .recon-card {
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-card);
      padding: var(--kb-space-4) 1.15rem;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .recon-label {
      font-size: var(--kb-text-xs);
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
      font-size: var(--kb-text-xs);
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
      margin: var(--kb-space-1) 0;
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
      padding: 0.65rem var(--kb-space-4) 0.65rem 2.2rem;
      font-size: var(--kb-text-xl);
      font-weight: 800;
      color: var(--kb-color-foreground);
      border: 2px solid var(--kb-color-border-strong);
      border-radius: var(--kb-radius-xl);
      background: var(--kb-color-input);
      font-variant-numeric: tabular-nums;
      outline: none;
      transition: border-color 150ms ease;
    }
    .cash-count-field:focus {
      border-color: var(--kb-color-primary);
      box-shadow: 0 0 0 3px rgba(var(--kb-color-primary-rgb), 0.12);
    }

    .variance-badge {
      display: inline-block;
      padding: 0.2rem 0.65rem;
      border-radius: var(--kb-radius-lg);
      font-size: var(--kb-text-xs);
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .variance-badge--match { background: var(--kb-color-success-soft); color: var(--kb-color-success); }
    .variance-badge--over { background: var(--kb-color-warning-soft); color: var(--kb-color-warning); }
    .variance-badge--short { background: var(--kb-color-danger-soft); color: var(--kb-color-danger); }

    /* ── Payment Splits Grid ── */
    .payment-splits-panel {
      margin-bottom: 1.5rem;
    }
    .payment-splits-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      gap: var(--kb-space-4);
      margin-top: 1rem;
    }
    .split-card {
      background: var(--kb-color-bg-app);
      border: 1px solid var(--kb-color-border);
      border-radius: 14px;
      padding: 1.15rem var(--kb-space-5);
      display: flex;
      align-items: center;
      gap: var(--kb-space-4);
    }
    .split-icon-box {
      width: 44px;
      height: 44px;
      border-radius: var(--kb-radius-xl);
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--kb-color-surface);
      color: var(--kb-color-link);
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
      font-size: var(--kb-text-sm);
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
      font-size: var(--kb-text-2xs);
      color: var(--kb-color-muted-foreground);
    }

    /* ── Order Status Audit Row ── */
    .order-dist-panel {
      margin-bottom: 1.5rem;
    }
    .audit-row-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: var(--kb-space-4);
      margin-top: 1rem;
    }
    @media (max-width: 768px) {
      .audit-row-grid { grid-template-columns: 1fr 1fr; }
    }
    .audit-stat {
      background: var(--kb-color-bg-app);
      border-radius: var(--kb-radius-xl);
      padding: var(--kb-space-4) 1.2rem;
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-1);
    }
    .stat-name {
      font-size: var(--kb-text-sm);
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

    /* ── Reference layout: summary tables + closing rail ─────────── */
    .closing-main-grid {
      display: grid;
      grid-template-columns: 2.1fr 1fr;
      gap: var(--kb-space-4);
      margin-bottom: var(--kb-space-4);
    }
    .closing-summary-col {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-4);
      min-width: 0;
    }
    .closing-rail__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--kb-space-3);
      margin-bottom: var(--kb-space-2);
    }
    .closing-rail__note {
      margin: 0 0 var(--kb-space-4);
      padding: var(--kb-space-3) var(--kb-space-4);
      border-radius: var(--kb-radius-md);
      font-size: var(--kb-text-sm);
      line-height: 1.45;
    }
    .closing-rail__note--todo {
      background: var(--kb-color-warning-soft);
      color: var(--kb-color-warning);
    }
    .closing-rail__note--ok {
      background: var(--kb-color-success-soft);
      color: var(--kb-color-success);
    }
    .closing-steps {
      list-style: none;
      margin: 0 0 var(--kb-space-4);
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-3);
    }
    .closing-step {
      display: flex;
      align-items: flex-start;
      gap: var(--kb-space-3);
    }
    .closing-step__dot {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 26px;
      height: 26px;
      flex: none;
      border-radius: var(--kb-radius-full);
      border: 2px solid var(--kb-color-border);
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-sm);
      font-weight: 700;
    }
    .closing-step--done .closing-step__dot {
      border-color: var(--kb-color-primary);
      background: var(--kb-color-primary);
      color: var(--kb-color-on-primary);
    }
    .closing-step__copy {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .closing-step__copy strong {
      font-size: var(--kb-text-sm);
      color: var(--kb-color-foreground);
    }
    .closing-step__copy span {
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
    }
    .closing-rail__cta {
      width: 100%;
      justify-content: center;
    }
    .closing-rail__cta:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .closing-rail__hint {
      display: block;
      margin-top: var(--kb-space-2);
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
      line-height: 1.45;
    }

    /* ── Reference bottom strip: hourly bars + top sellers ───────── */
    .closing-bottom-grid {
      display: grid;
      grid-template-columns: 1.6fr 1fr;
      gap: var(--kb-space-4);
      margin-top: var(--kb-space-5);
    }
    .hourly-chart {
      margin-top: var(--kb-space-4);
    }
    .hourly-chart__area {
      display: flex;
      align-items: flex-end;
      gap: 6px;
      height: 180px;
      padding: var(--kb-space-4) var(--kb-space-2) 0;
      border-bottom: 1px solid var(--kb-color-border);
    }
    .hourly-bar {
      flex: 1 1 0;
      min-height: 3px;
      border-radius: var(--kb-radius-sm) var(--kb-radius-sm) 0 0;
      background: var(--kb-color-primary-soft);
      position: relative;
    }
    .hourly-bar--peak {
      background: var(--kb-color-primary);
    }
    .hourly-bar__tip {
      position: absolute;
      bottom: calc(100% + 6px);
      left: 50%;
      transform: translateX(-50%);
      white-space: nowrap;
      padding: 2px 8px;
      border-radius: var(--kb-radius-sm);
      background: var(--kb-color-foreground);
      color: var(--kb-color-foreground-contrast);
      font-size: var(--kb-text-xs);
      font-weight: 600;
    }
    .hourly-chart__axis {
      display: flex;
      gap: 6px;
      padding: var(--kb-space-2) var(--kb-space-2) 0;
    }
    .hourly-chart__axis span {
      flex: 1 1 0;
      text-align: center;
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
    }
    .hourly-chart__axis span.hourly-chart__tick--edge {
      visibility: visible;
    }

    @media (max-width: 1024px) {
      .closing-main-grid,
      .closing-bottom-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class DailyClosingPageComponent {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);

  loading = signal(false);
  error = signal('');
  data = signal<DailyClosingData | null>(null);
  hourlyRows = signal<HourlySalesRow[]>([]);
  terminalSales = signal<TerminalSalesRow[]>([]);
  reportGenerated = signal(false);

  /**
   * The closing figures are withheld while a reload is in flight so the
   * skeleton above takes over — a manual refresh then visibly reloads the page
   * (as on Terminals) instead of silently swapping the numbers underneath it.
   */
  readonly viewData = computed(() => (this.loading() ? null : this.data()));

  /** Reference strip: Total Sales · Expected Cash · Total Digital · AOV. */
  readonly closingKpis = computed<KpiItem[]>(() => {
    const d = this.viewData();
    if (!d) {
      return [];
    }
    const digital = d.paymentSplits
      .filter((s) => !s.mode.toLowerCase().includes('cash'))
      .reduce((sum, s) => sum + s.total, 0);
    return [
      { label: 'Total Sales', value: this.fmt(d.totalRevenue), caption: `${d.totalOrders} orders` },
      { label: 'Expected Cash', value: this.fmt(d.expectedCash), caption: 'From cash payments' },
      { label: 'Total Digital', value: this.fmt(digital), caption: 'UPI, Card, Wallet' },
      {
        label: 'Average Order Value',
        value: this.fmt(d.totalOrders > 0 ? d.totalRevenue / d.totalOrders : 0),
        caption: "Today's tickets",
      },
    ];
  });

  /** Reference: Payment Summary table with the share of each channel. */
  readonly paymentRows = computed<Array<PaymentSplit & { pct: string }>>(() => {
    const d = this.viewData();
    if (!d) {
      return [];
    }
    const total = d.paymentSplits.reduce((sum, s) => sum + s.total, 0);
    return d.paymentSplits.map((s) => ({
      ...s,
      pct: total > 0 ? ((s.total / total) * 100).toFixed(1) + '%' : '0.0%',
    }));
  });

  /** Reference: Order Status Summary — Completed · Cancelled · Draft. */
  readonly statusRows = computed<Array<{ label: string; count: number; amount: number; tagClass: string }>>(() => {
    const d = this.viewData();
    if (!d) {
      return [];
    }
    return [
      { label: 'Completed', count: d.completedOrders, amount: d.netRevenue, tagClass: 'kpi-tag--green' },
      { label: 'Cancelled', count: d.cancelledOrders, amount: d.refundedAmount, tagClass: 'kpi-tag--warn' },
      { label: 'Draft', count: d.draftOrders, amount: 0, tagClass: 'kpi-tag--neutral' },
    ];
  });

  /** The drawer count is what unblocks the close flow. */
  readonly countsCounted = computed(() => this.countedCash() != null);

  readonly hourlyPeak = computed(() =>
    this.hourlyRows().reduce((max, r) => Math.max(max, r.itemsSold), 0)
  );

  readonly terminalSalesTotal = computed(() =>
    this.terminalSales().reduce(
      (acc, r) => ({ orders: acc.orders + r.orders, amount: acc.amount + r.amount }),
      { orders: 0, amount: 0 }
    )
  );
  countedCash = signal<number | null>(null);
  countedCashSavedLocally = signal(false);

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

  constructor() {
    this.restoreCountedCash();
    this.load();
  }

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
    this.restoreCountedCash();
    this.load();
  }

  setCountedCash(value: number | null): void {
    const amount = value === null || value === undefined || Number.isNaN(Number(value))
      ? null
      : Number(value);
    this.countedCash.set(amount);
    const storageKey = this.countedCashStorageKey();
    try {
      if (amount === null) {
        localStorage.removeItem(storageKey);
        this.countedCashSavedLocally.set(false);
      } else {
        localStorage.setItem(storageKey, String(amount));
        this.countedCashSavedLocally.set(true);
      }
    } catch {
      this.countedCashSavedLocally.set(false);
    }
  }

  private restoreCountedCash(): void {
    try {
      const stored = localStorage.getItem(this.countedCashStorageKey());
      const amount = stored === null ? null : Number(stored);
      this.countedCash.set(amount !== null && Number.isFinite(amount) && amount >= 0 ? amount : null);
      this.countedCashSavedLocally.set(amount !== null && Number.isFinite(amount) && amount >= 0);
    } catch {
      this.countedCash.set(null);
      this.countedCashSavedLocally.set(false);
    }
  }

  private countedCashStorageKey(): string {
    const restaurantId = this.auth.session()?.restaurantId ?? 'current';
    return `khanabook:daily-closing-cash:${restaurantId}:${this.selectedDate()}`;
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.reportGenerated.set(false);
    const d = this.selectedDate();
    this.loadHourly(d);
    this.api.getDailyClosing(d).subscribe({
      next: (res: any) => {
        this.loading.set(false);
        this.terminalSales.set(Array.isArray(res.terminalSales)
          ? res.terminalSales.map((t: any) => ({ terminal: t.terminal, orders: t.orders || 0, amount: t.amount || 0 }))
          : []);
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
          topItems: (res.topItems || []).map((t: any) => ({ name: t.name, qty: t.qty || 0, amount: t.amount ?? null }))
        });
      },
      error: () => {
        this.api.getOrdersPaginated(0, 100, undefined, d, d).pipe(
          expand((page, index) => index + 1 < page.totalPages
            ? this.api.getOrdersPaginated(index + 1, 100, undefined, d, d)
            : EMPTY),
          reduce((all, page) => all.concat(page.content), [] as BusinessOrder[])
        ).subscribe({
          next: (orders: BusinessOrder[]) => {
            this.loading.set(false);
            this.data.set(this.computeClosing(orders, d));
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

  private loadHourly(date: string): void {
    this.hourlyRows.set([]);
    this.api.getHourlySales(date)
      .subscribe({
        next: (rows) => this.hourlyRows.set(Array.isArray(rows) ? rows : []),
        error: () => this.hourlyRows.set([]),
      });
  }

  /** `13` → `1 PM`: the hourly axis is labelled the way the counter reads it. */
  hourLabel(row: HourlySalesRow): string {
    const hour = Number(row.hour);
    if (!Number.isFinite(hour)) return String(row.hour);
    const suffix = hour >= 12 ? 'PM' : 'AM';
    const display = hour % 12 === 0 ? 12 : hour % 12;
    return `${display} ${suffix}`;
  }

  /** Bar height is relative to the day's peak so the largest hour always fills the plot. */
  hourlyBarHeight(row: HourlySalesRow): number {
    const peak = this.hourlyPeak();
    if (peak <= 0 || !this.hourlyRows().length) return 0;
    return Math.max(3, Math.round((row.itemsSold / peak) * 100));
  }

  /** Step 4 of the reference checklist: generate the final report. The cash count
   *  and the report stay local — the day itself is closed at the counter. */
  closeBusinessDay(): void {
    if (!this.countsCounted()) return;
    this.exportClosing();
    this.reportGenerated.set(true);
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
      ['Counted Cash (saved in this browser only)', String(this.countedCash() ?? '')],
      ['Drawer Variance', this.countedCash() === null ? 'Not counted' : this.getVarianceFormatted(d.expectedCash)],
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
