import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, Subject, startWith, switchMap } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { DateRangeToolbarComponent } from '../../shared/date-range-toolbar.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { formatCurrency } from '../../shared/formatters';
import { buildInvoiceLink } from '../../core/utils/invoice-link.util';
import { readStoredDateRange, storeDateRange } from '../../core/utils/date-range-storage.util';
import { ItemSalesRow, GstLedgerResponse, GstLedgerEntry } from '../../core/models/api.models';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';

function formatLocalDate(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function todayRange(): { from: string; to: string } {
  const today = formatLocalDate(new Date());
  return { from: today, to: today };
}

@Component({
  selector: 'app-reports-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, DateRangeToolbarComponent, PageHeaderComponent, ApiStateComponent, KpiRowComponent],
  template: `
    <div class="page-shell">
      <!-- Canonical page header (shared design system) -->
      <app-page-header
        title="Financial &amp; Sales Reports"
        subtitle="Sales, refunds, item performance, and GST for the selected period."
      >
        <app-date-range-toolbar
          [initialRange]="selectedRange()"
          [downloadDisabled]="!report()"
          downloadLabel="Export report CSV"
          refreshLabel="Refresh report data"
          [refreshDisabled]="refreshing()"
          [refreshing]="refreshing()"
          (rangeChanged)="setRange($event)"
          (download)="exportCsv()"
          (refresh)="refresh()">
        </app-date-range-toolbar>
      </app-page-header>

      <!-- The period the report covers. On screen the date picker says it; on
           paper the picker is gone, so the same range is stated in words. -->
      <p class="print-only print-period">
        Period: {{ selectedRange().from }} to {{ selectedRange().to }}
      </p>

      <!-- Report View Navigation Tabs -->
      <nav class="report-tabs-nav no-print" aria-label="Report Views">
        <button
          type="button"
          class="tab-pill"
          [class.tab-pill--active]="activeTab() === 'overview'"
          (click)="activeTab.set('overview')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7"></rect>
            <rect x="14" y="3" width="7" height="7"></rect>
            <rect x="14" y="14" width="7" height="7"></rect>
            <rect x="3" y="14" width="7" height="7"></rect>
          </svg>
          Overview &amp; Revenue
        </button>
        <button
          type="button"
          class="tab-pill"
          [class.tab-pill--active]="activeTab() === 'gst'"
          (click)="activeTab.set('gst')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
            <line x1="16" y1="13" x2="8" y2="13"></line>
            <line x1="16" y1="17" x2="8" y2="17"></line>
            <polyline points="10 9 9 9 8 9"></polyline>
          </svg>
          GST Tax Ledger
        </button>
        <button
          type="button"
          class="tab-pill"
          [class.tab-pill--active]="activeTab() === 'items'"
          (click)="activeTab.set('items')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="20" x2="18" y2="10"></line>
            <line x1="12" y1="20" x2="12" y2="4"></line>
            <line x1="6" y1="20" x2="6" y2="14"></line>
          </svg>
          Item-Wise Sales
        </button>
      </nav>

      <ng-container *ngIf="viewReport() as data; else reportState">

        <!-- TAB 1: OVERVIEW -->
        <ng-container *ngIf="activeTab() === 'overview'">
          <!-- The shared KPI row: one anatomy with every other page. -->
          <app-kpi-row [items]="overviewKpis()" ariaLabel="Report summary"></app-kpi-row>

          <section class="panel financial-strip-card">
            <div class="strip-item">
              <span class="strip-label">Refunded Tickets</span>
              <strong class="strip-val" [class.text-danger]="data.refundedOrders > 0">{{ data.refundedOrders }}</strong>
              <span class="strip-hint">Orders reversed or refunded</span>
            </div>
            <div class="strip-item">
              <span class="strip-label">Total Refund Deductions</span>
              <strong class="strip-val" [class.text-danger]="data.refundedOrders > 0">{{ data.refundedAmount }}</strong>
              <span class="strip-hint">Returned to customers</span>
            </div>
            <div class="strip-item">
              <span class="strip-label">Refund Rate</span>
              <strong class="strip-val" [class.text-warn]="data.refundedOrders > 0">{{ data.refundRate }}</strong>
              <span class="strip-hint">Percentage of overall order volume</span>
            </div>
          </section>

          <!-- Financial Audit Note (unsection.com aesthetic) -->
          <section class="panel audit-note-card">
            <div class="note-icon-halo">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="16" x2="12" y2="12"/>
                <line x1="12" y1="8" x2="12.01" y2="8"/>
              </svg>
            </div>
            <div class="note-copy">
              <h4>Financial Integrity &amp; Settlement Standard</h4>
              <p>
                Recognized Revenue includes all orders settled through Cash, Dynamic UPI QR, and paired card terminals. Unsettled draft tickets and cancelled orders are excluded from net revenue to prevent double-counting.
              </p>
            </div>
          </section>
        </ng-container>

        <!-- TAB 2: GST TAX LEDGER -->
        <ng-container *ngIf="activeTab() === 'gst'">
          <div class="gst-ledger-shell" *ngIf="data.gstLedger as ledger">
            <app-kpi-row [items]="gstKpis()" ariaLabel="GST summary"></app-kpi-row>

            
            <div class="panel soft-section table-section">
              <header class="section-head">
                <div>
                  <h3>Tax Invoices &amp; Bill Breakdown</h3>
                  <p class="muted">All completed invoices in selected period with applicable tax proportions.</p>
                </div>
                <span class="kpi-tag tabular-num">{{ ledger.entries.length }} Invoices</span>
              </header>

              <div class="table-wrap" *ngIf="ledger.entries.length > 0; else noGstEntries">
                <table class="data-table">
                  <caption class="visually-hidden">Tax invoices in the selected period, with the tax split for each bill</caption>
                  <thead>
                    <tr>
                      <th scope="col">Invoice / Bill #</th>
                      <th scope="col">Date &amp; Time</th>
                      <th scope="col">Customer</th>
                      <th scope="col" class="num-cell">Taxable Value</th>
                      <th scope="col" class="num-cell">GST %</th>
                      <th scope="col" class="num-cell">CGST</th>
                      <th scope="col" class="num-cell">SGST</th>
                      <th scope="col" class="num-cell">Tax Total</th>
                      <th scope="col" class="num-cell">Invoice Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let row of ledger.entries; trackBy: trackByBillId">
                      <th scope="row" class="font-bold">
                        <a [href]="getInvoiceUrl(row)" target="_blank" rel="noopener noreferrer" class="invoice-link" title="View tax invoice">
                          {{ row.invoiceNumber }}
                        </a>
                      </th>
                      <td class="muted">{{ row.createdAt | date:'short' }}</td>
                      <td>{{ row.customerName || 'Walk-in' }}</td>
                      <td class="num-cell tabular-num">{{ formatMoney(row.subtotal) }}</td>
                      <td class="num-cell tabular-num">{{ row.gstRate }}%</td>
                      <td class="num-cell tabular-num">{{ formatMoney(row.cgst) }}</td>
                      <td class="num-cell tabular-num">{{ formatMoney(row.sgst) }}</td>
                      <td class="num-cell tabular-num font-semibold text-brand">{{ formatMoney(row.totalTax) }}</td>
                      <td class="num-cell tabular-num font-bold">{{ formatMoney(row.totalAmount) }}</td>
                    </tr>
                  </tbody>
                  <tfoot>
                    <tr class="table-totals-row">
                      <td colspan="3"><strong>Total ({{ ledger.entries.length }} bills)</strong></td>
                      <td class="num-cell tabular-num font-bold">{{ formatMoney(ledger.totalTaxable) }}</td>
                      <td></td>
                      <td class="num-cell tabular-num font-bold">{{ formatMoney(ledger.totalCgst) }}</td>
                      <td class="num-cell tabular-num font-bold">{{ formatMoney(ledger.totalSgst) }}</td>
                      <td class="num-cell tabular-num font-bold text-brand">{{ formatMoney(ledger.totalTax) }}</td>
                      <td class="num-cell tabular-num font-bold">{{ formatMoney(ledger.totalTaxable + ledger.totalTax) }}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
              <ng-template #noGstEntries>
                <div class="empty-table-state">
                  <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="empty-icon-glyph" aria-hidden="true">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <path d="M14 2v6h6"/>
                    <path d="M8 13h8M8 17h5"/>
                  </svg>
                  <h4>No tax invoices in this date range</h4>
                  <p class="muted">GST entries are written when an invoice is issued. Try a wider date range, or take an order on the POS to start recording them.</p>
                </div>
              </ng-template>
            </div>
          </div>
        </ng-container>

        <!-- TAB 3: ITEM SALES -->
        <ng-container *ngIf="activeTab() === 'items'">
          <div class="items-ledger-shell">
            <div class="panel soft-section table-section">
              <header class="section-head">
                <div>
                  <h3>Item-Wise Sales &amp; Quantity Volume</h3>
                  <p class="muted">Aggregate sales breakdown sorted by highest quantity sold.</p>
                </div>
                <span class="kpi-tag tabular-num">{{ data.itemSales.length }} Menu Items</span>
              </header>

              <div class="table-wrap" *ngIf="data.itemSales.length > 0; else noItemSales">
                <table class="data-table">
                  <caption class="visually-hidden">Items sold in the selected period, ranked by quantity, with revenue and average price</caption>
                  <thead>
                    <tr>
                      <th scope="col" style="width: 60px;"># Rank</th>
                      <th scope="col">Dish / Item Name</th>
                      <th scope="col" class="num-cell">Units Sold</th>
                      <th scope="col" class="num-cell">Total Revenue</th>
                      <th scope="col" class="num-cell">Average Price</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let row of data.itemSales; let idx = index; trackBy: trackByItemId">
                      <td class="muted tabular-num">{{ idx + 1 }}</td>
                      <th scope="row" class="font-semibold">{{ row.name }}</th>
                      <td class="num-cell tabular-num font-bold">{{ row.quantitySold }}</td>
                      <td class="num-cell tabular-num font-bold text-brand">{{ formatMoney(row.revenue) }}</td>
                      <td class="num-cell tabular-num muted">
                        {{ row.quantitySold ? formatMoney(row.revenue / row.quantitySold) : '-' }}
                      </td>
                    </tr>
                  </tbody>
                  <tfoot>
                    <tr class="table-totals-row">
                      <td colspan="2"><strong>Total ({{ data.itemSales.length }} items)</strong></td>
                      <td class="num-cell tabular-num font-bold">{{ totalItemsSold(data.itemSales) }}</td>
                      <td class="num-cell tabular-num font-bold text-brand">{{ formatMoney(totalItemsRevenue(data.itemSales)) }}</td>
                      <td></td>
                    </tr>
                  </tfoot>
                </table>
              </div>
              <ng-template #noItemSales>
                <div class="empty-table-state">
                  <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="empty-icon-glyph" aria-hidden="true">
                    <path d="M18 2v6a3 3 0 0 1-3 3 3 3 0 0 1-3-3V2"/>
                    <path d="M15 2v19"/>
                    <path d="M5 2c1.5 2 1.5 5 0 7v12"/>
                  </svg>
                  <h4>No item sales in this date range</h4>
                  <p class="muted">Completed orders appear here ranked by quantity sold. Try a wider date range, or take an order on the POS to start tracking.</p>
                </div>
              </ng-template>
            </div>
          </div>
        </ng-container>

      </ng-container>

      <ng-template #reportState>
        <app-api-state
          *ngIf="error(); else loadingState"
          [message]="error()"
          (retry)="refresh()"
        ></app-api-state>
        <ng-template #loadingState>
          <div class="kpi-skeleton-row" role="status" aria-busy="true" aria-label="Loading reports">
            <span class="visually-hidden">Loading financial reports...</span>
            <div class="skeleton skeleton-stat" *ngFor="let item of [1,2,3,4]; trackBy: trackByIndex"></div>
          </div>
        </ng-template>
      </ng-template>
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

    /* Page geometry + page header now come from the shared design system
       (styles.css .page-shell + shared/page-header.component). */

    /* Print-only: the period line stands in for the hidden date picker, so it
       only exists on paper. The shared sheet turns .print-only on. */
    .print-period {
      margin: 0 0 1rem;
      font-size: 0.85rem;
      font-weight: 600;
      letter-spacing: 0.02em;
      color: var(--kb-color-foreground);
    }

    /* Loading state mirrors the shared KPI row's track, so the skeletons land
       exactly where the tiles will. */
    .kpi-skeleton-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(215px, 1fr));
      gap: 0.85rem;
      margin-bottom: 1.5rem;
    }


    /* ── Secondary Financial Strip ──
       A .panel supplies the surface, radius and border; this only lays out the
       three columns and their dividers. */
    .financial-strip-card {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      overflow: hidden;
      margin-bottom: 1.5rem;
    }
    .table-section { margin-top: 1.25rem; }
    @media (max-width: 768px) {
      .financial-strip-card { grid-template-columns: 1fr; }
    }
    .strip-item {
      padding: 1.25rem 1.35rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      border-right: 1px solid var(--kb-color-border);
    }
    .strip-item:last-child {
      border-right: none;
    }
    .strip-label {
      font-size: 0.78rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .strip-val {
      font-size: 1.3rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }
    .strip-hint {
      font-size: 0.76rem;
      color: var(--kb-color-muted-foreground);
    }
    .text-danger { color: var(--kb-color-danger) !important; }
    .text-warn { color: var(--kb-color-warning) !important; }

    /* ── Audit Note Card (unsection.com aesthetic) ── */
    .audit-note-card {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      padding: 1.25rem 1.35rem;
      margin-bottom: 1.5rem;
    }
    .note-icon-halo {
      width: 38px;
      height: 38px;
      border-radius: 10px;
      background: var(--kb-color-primary-soft);
      color: var(--kb-color-link);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .note-copy h4 {
      margin: 0 0 0.25rem;
      font-size: 0.95rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
    }
    .note-copy p {
      margin: 0;
      font-size: 0.84rem;
      color: var(--kb-color-muted-foreground);
      line-height: 1.5;
    }

    /* ── Tab Navigation ── */
    .report-tabs-nav {
      display: flex;
      gap: 0.5rem;
      margin-bottom: 1.5rem;
      border-bottom: 1px solid var(--kb-color-border);
      padding-bottom: 0.75rem;
      overflow-x: auto;
    }
    .tab-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.55rem 1.1rem;
      border-radius: 999px;
      font-size: 0.84rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      background: #FFFFFF;
      border: 1px solid var(--kb-color-border);
      cursor: pointer;
      transition: all 150ms ease;
      white-space: nowrap;
      min-height: 40px;
    }
    .tab-pill:hover {
      color: var(--kb-color-foreground);
      border-color: var(--kb-color-border-strong);
    }
    .tab-pill--active {
      background: var(--kb-color-primary);
      color: #FFFFFF;
      border-color: var(--kb-color-primary);
      box-shadow: 0 2px 8px rgba(var(--kb-color-primary-rgb), 0.25);
    }
    .tab-pill--active:hover {
      color: #FFFFFF;
    }

    /* ── Tables & Cards ── */
    /* The invoice number is the row header of the GST table, so it uses the link
       tone rather than an inline style. */
    .invoice-link {
      color: var(--kb-color-link);
      text-decoration: underline;
    }

    /* The table design is defined once in the global stylesheet (see the
       "Tables" section of styles.css). Only column layout belongs here. */
    .empty-table-state {
      padding: 3rem 1.5rem;
      text-align: center;
    }
    .empty-table-state svg.empty-icon-glyph {
      color: var(--kb-color-muted-foreground);
      opacity: 0.65;
      margin-bottom: 0.6rem;
    }
    .empty-table-state h4 {
      margin: 0 0 0.35rem;
      font-size: 1rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
    }
    .empty-table-state p { max-width: 46ch; margin: 0 auto; }

    .skeleton { background: var(--kb-color-border); border-radius: 14px; animation: pulse 1.5s ease-in-out infinite; }
    .skeleton-stat { height: 120px; }
    @keyframes pulse { 0%, 100% { opacity: 0.4; } 50% { opacity: 0.7; } }
  `]
})
export class ReportsPageComponent {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly trigger$ = new Subject<void>();
  /** Restored from the range shared with Daily Closing and Orders history. */
  readonly selectedRange = signal<{ from: string; to: string }>(readStoredDateRange() ?? todayRange());
  readonly activeTab = signal<'overview' | 'gst' | 'items'>('overview');
  readonly refreshing = signal(false);

  /**
   * The report is withheld while a reload is in flight so `#reportState` (the
   * skeleton) takes over — a manual refresh then visibly reloads the page
   * (as on Terminals) instead of silently swapping the figures underneath it.
   */
  readonly viewReport = computed(() => (this.refreshing() ? null : this.report()));

  /** The overview strip, as shared KPI items rather than a page-local card grid. */
  readonly overviewKpis = computed<KpiItem[]>(() => {
    const data = this.viewReport();
    if (!data) return [];
    return [
      {
        label: 'Net Realized Revenue',
        value: data.netRevenue,
        emphasis: true,
        badge: { text: 'Net Realized', tone: 'neutral' },
        caption: 'After all refunds',
      },
      {
        label: 'Gross Billed',
        value: data.revenue,
        badge: { text: 'Gross Volume', tone: 'neutral' },
        caption: 'Total sales before deductions',
      },
      {
        label: 'Bills Minted',
        value: data.billCount,
        badge: { text: 'POS Tickets', tone: 'neutral' },
        caption: 'Completed & draft orders',
      },
      {
        label: 'Unsettled / Pending',
        value: data.pendingPayments,
        tone: data.pendingPayments > 0 ? 'warning' : 'default',
        alert: data.pendingPayments > 0,
        badge: data.pendingPayments > 0
          ? { text: `${data.pendingPayments} Pending`, tone: 'warning' }
          : { text: 'All Cleared', tone: 'success' },
        caption: 'Awaiting customer payment',
      },
    ];
  });

  /** The GST strip uses the same row, so the tax figures scan like sales figures. */
  readonly gstKpis = computed<KpiItem[]>(() => {
    const ledger = this.viewReport()?.gstLedger;
    if (!ledger) return [];
    return [
      {
        label: 'Total Tax Liability',
        value: this.formatMoney(ledger.totalTax),
        emphasis: true,
        caption: 'CGST + SGST combined',
      },
      {
        label: 'Taxable Turnover',
        value: this.formatMoney(ledger.totalTaxable),
        caption: 'Net assessable sales value',
      },
      {
        label: 'CGST (Central)',
        value: this.formatMoney(ledger.totalCgst),
        caption: 'Central GST collected',
      },
      {
        label: 'SGST (State)',
        value: this.formatMoney(ledger.totalSgst),
        caption: 'State GST collected',
      },
    ];
  });
  readonly error = signal('');
  readonly report = toSignal(this.trigger$.pipe(startWith(undefined), switchMap(() => {
    this.refreshing.set(true); this.error.set('');
    const range = this.selectedRange();
    const fromDate = range.from;
    const toDate = range.to;

    return forkJoin({
      dashboard: this.api.getDashboard(fromDate, toDate),
      itemSales: this.api.getItemSales(fromDate, toDate).pipe(catchError(() => of([]))),
      gstLedger: this.api.getGstLedger(fromDate, toDate).pipe(catchError(() => of(null)))
    }).pipe(
      map(({ dashboard, itemSales, gstLedger }) => {
        this.refreshing.set(false);
        const totalRev = Number(dashboard.totalRevenue) || 0;
        const refunded = Number(dashboard.refundedAmount) || 0;
        const net = Math.max(0, totalRev - refunded);
        const billCount = Number(dashboard.posOrderCount) || 0;
        const pending = Number(dashboard.pendingPosPayments) || 0;

        const sortedItems = [...(itemSales || [])].sort((a, b) => (Number(b.quantitySold) || 0) - (Number(a.quantitySold) || 0));

        return {
          revenue: formatCurrency(totalRev),
          revenueRaw: totalRev,
          billCount: billCount,
          pendingPayments: pending,
          refundedOrders: dashboard.refundedOrders || 0,
          refundedAmount: formatCurrency(refunded),
          refundedAmountRaw: refunded,
          netRevenue: formatCurrency(net),
          netRevenueRaw: net,
          // No bills is not a 0% refund rate — there is no denominator yet.
          refundRate: billCount ? `${(((dashboard.refundedOrders || 0) / billCount) * 100).toFixed(1)}%` : '—',
          itemSales: sortedItems,
          gstLedger: gstLedger
        };
      }),
      catchError((err: unknown) => {
        this.refreshing.set(false);
        const response = err as { error?: { message?: string; error?: string } };
        this.error.set(response.error?.message || response.error?.error || 'Unable to load reports.');
        return of(null);
      })
    );
  })));

  setRange(range: { from: string; to: string }): void {
    this.selectedRange.set(range);
    storeDateRange(range);
    this.trigger$.next();
  }

  refresh(): void { this.trigger$.next(); }

  formatMoney(val: number | null | undefined): string {
    return formatCurrency(Number(val) || 0);
  }

  trackByBillId(_: number, item: GstLedgerEntry): number {
    return item.billId;
  }

  trackByItemId(_: number, item: ItemSalesRow): number {
    return item.menuItemId;
  }

  trackByIndex = (_: number, __: unknown) => _;

  totalItemsSold(items: ItemSalesRow[]): number {
    return (items || []).reduce((sum, i) => sum + (Number(i.quantitySold) || 0), 0);
  }

  totalItemsRevenue(items: ItemSalesRow[]): number {
    return (items || []).reduce((sum, i) => sum + (Number(i.revenue) || 0), 0);
  }

  exportCsv(): void {
    const data = this.report();
    if (!data) return;
    const range = this.selectedRange();
    const period = range ? `${range.from} to ${range.to}` : 'Default Period';

    const lines: string[] = [];

    // Section 1: Overview
    lines.push('--- EXECUTIVE SUMMARY ---');
    lines.push('Metric,Value');
    lines.push(`Period,${period}`);
    lines.push(`Gross Billed Revenue,${data.revenueRaw}`);
    lines.push(`Net Realized Revenue,${data.netRevenueRaw}`);
    lines.push(`Bills Minted,${data.billCount}`);
    lines.push(`Pending Payments,${data.pendingPayments}`);
    lines.push(`Refunded Orders,${data.refundedOrders}`);
    lines.push(`Refunded Amount,${data.refundedAmountRaw}`);
    lines.push(`Refund Rate,${data.refundRate}`);
    lines.push('');

    // Section 2: GST Ledger
    if (data.gstLedger) {
      lines.push('--- GST COMPLIANCE LEDGER ---');
      lines.push(`Total Taxable Turnover,${data.gstLedger.totalTaxable}`);
      lines.push(`Total CGST,${data.gstLedger.totalCgst}`);
      lines.push(`Total SGST,${data.gstLedger.totalSgst}`);
      lines.push(`Total Tax,${data.gstLedger.totalTax}`);
      lines.push(`Total Invoices,${data.gstLedger.invoiceCount}`);
      lines.push('');
      lines.push('Invoice Number,Created At,Customer,Taxable Amount,GST %,CGST,SGST,Total Tax,Total Amount');
      for (const e of data.gstLedger.entries) {
        const dateStr = new Date(e.createdAt).toISOString();
        const cust = `"${(e.customerName || 'Walk-in').replace(/"/g, '""')}"`;
        lines.push(`"${e.invoiceNumber}",${dateStr},${cust},${e.subtotal},${e.gstRate},${e.cgst},${e.sgst},${e.totalTax},${e.totalAmount}`);
      }
      lines.push('');
    }

    // Section 3: Item Sales
    if (data.itemSales?.length) {
      lines.push('--- ITEM SALES BREAKDOWN ---');
      lines.push('Menu Item ID,Item Name,Units Sold,Total Revenue');
      for (const item of data.itemSales) {
        const itemName = `"${item.name.replace(/"/g, '""')}"`;
        lines.push(`${item.menuItemId},${itemName},${item.quantitySold},${item.revenue}`);
      }
    }

    const csv = lines.join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `khanabook-report-${formatLocalDate(new Date())}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  getInvoiceUrl(row: GstLedgerEntry): string {
    const session = this.auth.session();
    return buildInvoiceLink(session?.restaurantId, row.billId, row.publicToken);
  }
}
