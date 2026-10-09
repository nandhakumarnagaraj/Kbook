import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, Subject, startWith, switchMap } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';
import { formatCurrency } from '../../shared/formatters';
import { ItemSalesRow, GstLedgerResponse, GstLedgerEntry } from '../../core/models/api.models';

@Component({
  selector: 'app-reports-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, DateRangeSelectorComponent],
  template: `
    <div class="page-shell">
      <!-- Operational Header (navbar.gallery standard) -->
      <header class="reports-header">
        <div class="header-left">
          <div class="header-title-row">
            <h2>Financial &amp; Sales Reports</h2>
            <span class="owner-pill">● Executive View</span>
          </div>
          <p class="header-sub">Recognized revenues, billing volume, refund impact, and gateway reconciliation.</p>
        </div>
        <div class="header-right">
          <app-date-range-selector [initialRange]="selectedRange()" (rangeChanged)="setRange($event)"/>
          <button type="button" class="ghost-btn-tactile" [disabled]="refreshing()" (click)="refresh()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            {{ refreshing() ? 'Calculating...' : 'Refresh' }}
          </button>
          <button type="button" class="ghost-btn-tactile" (click)="exportCsv()" *ngIf="report()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            Export CSV
          </button>
        </div>
      </header>

      <!-- Report View Navigation Tabs -->
      <nav class="report-tabs-nav" aria-label="Report Views">
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

      <ng-container *ngIf="report() as data; else reportState">

        <!-- TAB 1: OVERVIEW -->
        <ng-container *ngIf="activeTab() === 'overview'">
          <!-- Bento Analytics Row (bentogrids.com standard) -->
          <section class="reports-bento-grid" aria-label="Report summary">
            <!-- Hero Tile: Net Realized -->
            <article class="bento-card bento-card--hero">
              <div class="bento-head">
                <span class="bento-label">Net Realized Revenue</span>
                <svg width="68" height="22" viewBox="0 0 64 20" class="bento-spark" aria-hidden="true">
                  <polyline fill="none" stroke="rgba(255,255,255,0.85)" stroke-width="2" [attr.points]="data.sparkNet"/>
                </svg>
              </div>
              <strong class="bento-value">{{ data.netRevenue }}</strong>
              <div class="bento-foot">
                <span class="delta-tag delta-tag--up">&#9650; Active Realized</span>
                <span class="delta-sub">After all refunds</span>
              </div>
            </article>

            <!-- Gross Billed -->
            <article class="bento-card">
              <div class="bento-head">
                <span class="bento-label">Gross Billed</span>
                <svg width="64" height="20" viewBox="0 0 64 20" class="bento-spark" aria-hidden="true">
                  <polyline fill="none" stroke="#5D45FD" stroke-width="1.75" [attr.points]="data.sparkRevenue"/>
                </svg>
              </div>
              <strong class="bento-value">{{ data.revenue }}</strong>
              <div class="bento-foot">
                <span class="delta-sub">Total sales before deductions</span>
              </div>
            </article>

            <!-- Bill Records -->
            <article class="bento-card">
              <div class="bento-head">
                <span class="bento-label">Bills Minted</span>
                <svg width="64" height="20" viewBox="0 0 64 20" class="bento-spark" aria-hidden="true">
                  <polyline fill="none" stroke="#10B981" stroke-width="1.75" [attr.points]="data.sparkBills"/>
                </svg>
              </div>
              <strong class="bento-value">{{ data.billCount }}</strong>
              <div class="bento-foot">
                <span class="delta-sub">Completed &amp; draft orders</span>
              </div>
            </article>

            <!-- Pending Payments -->
            <article class="bento-card" [class.bento-card--warn]="data.pendingPayments > 0">
              <div class="bento-head">
                <span class="bento-label">Unsettled / Pending</span>
                <svg width="64" height="20" viewBox="0 0 64 20" class="bento-spark" aria-hidden="true">
                  <polyline fill="none" stroke="#D97706" stroke-width="1.75" [attr.points]="data.sparkPending"/>
                </svg>
              </div>
              <strong class="bento-value">{{ data.pendingPayments }}</strong>
              <div class="bento-foot">
                <span class="delta-sub">Awaiting customer payment</span>
              </div>
            </article>
          </section>

          <!-- Secondary Financial Metrics Strip -->
          <section class="financial-strip-card">
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
          <section class="audit-note-card">
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
            <!-- GST Summary Bento Strip -->
            <section class="reports-bento-grid">
              <article class="bento-card bento-card--hero">
                <div class="bento-head">
                  <span class="bento-label">Total Tax Liability</span>
                </div>
                <strong class="bento-value">{{ formatMoney(ledger.totalTax) }}</strong>
                <div class="bento-foot">
                  <span class="delta-sub">CGST + SGST combined</span>
                </div>
              </article>
              <article class="bento-card">
                <div class="bento-head">
                  <span class="bento-label">Taxable Turnover</span>
                </div>
                <strong class="bento-value">{{ formatMoney(ledger.totalTaxable) }}</strong>
                <div class="bento-foot">
                  <span class="delta-sub">Net assessable sales value</span>
                </div>
              </article>
              <article class="bento-card">
                <div class="bento-head">
                  <span class="bento-label">CGST (Central)</span>
                </div>
                <strong class="bento-value">{{ formatMoney(ledger.totalCgst) }}</strong>
                <div class="bento-foot">
                  <span class="delta-sub">Central GST collected</span>
                </div>
              </article>
              <article class="bento-card">
                <div class="bento-head">
                  <span class="bento-label">SGST (State)</span>
                </div>
                <strong class="bento-value">{{ formatMoney(ledger.totalSgst) }}</strong>
                <div class="bento-foot">
                  <span class="delta-sub">State GST collected</span>
                </div>
              </article>
            </section>

            <!-- GST Invoices Table -->
            <div class="table-card">
              <header class="table-card__header">
                <div>
                  <h3>Tax Invoices &amp; Bill Breakdown</h3>
                  <p class="muted">All completed invoices in selected period with applicable tax proportions.</p>
                </div>
                <span class="records-pill tabular-num">{{ ledger.entries.length }} Invoices</span>
              </header>

              <div class="table-container" *ngIf="ledger.entries.length > 0; else noGstEntries">
                <table class="report-table">
                  <thead>
                    <tr>
                      <th>Invoice / Bill #</th>
                      <th>Date &amp; Time</th>
                      <th>Customer</th>
                      <th class="num-cell">Taxable Value</th>
                      <th class="num-cell">GST %</th>
                      <th class="num-cell">CGST</th>
                      <th class="num-cell">SGST</th>
                      <th class="num-cell">Tax Total</th>
                      <th class="num-cell">Invoice Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let row of ledger.entries; trackBy: trackByBillId">
                      <td class="font-bold">{{ row.invoiceNumber }}</td>
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
                  <p class="muted">No tax invoice entries recorded in this date range.</p>
                </div>
              </ng-template>
            </div>
          </div>
        </ng-container>

        <!-- TAB 3: ITEM SALES -->
        <ng-container *ngIf="activeTab() === 'items'">
          <div class="items-ledger-shell">
            <div class="table-card">
              <header class="table-card__header">
                <div>
                  <h3>Item-Wise Sales &amp; Quantity Volume</h3>
                  <p class="muted">Aggregate sales breakdown sorted by highest quantity sold.</p>
                </div>
                <span class="records-pill tabular-num">{{ data.itemSales.length }} Menu Items</span>
              </header>

              <div class="table-container" *ngIf="data.itemSales.length > 0; else noItemSales">
                <table class="report-table">
                  <thead>
                    <tr>
                      <th style="width: 60px;"># Rank</th>
                      <th>Dish / Item Name</th>
                      <th class="num-cell">Units Sold</th>
                      <th class="num-cell">Total Revenue</th>
                      <th class="num-cell">Average Price</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let row of data.itemSales; let idx = index; trackBy: trackByItemId">
                      <td class="muted tabular-num">{{ idx + 1 }}</td>
                      <td class="font-semibold">{{ row.name }}</td>
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
                  <p class="muted">No item sales recorded in this date range.</p>
                </div>
              </ng-template>
            </div>
          </div>
        </ng-container>

      </ng-container>

      <ng-template #reportState>
        <div class="panel loading" *ngIf="error(); else loadingState" role="alert">
          <p>{{ error() }}</p>
          <button type="button" class="primary-btn-tactile" (click)="refresh()">Try again</button>
        </div>
        <ng-template #loadingState>
          <div class="reports-bento-grid" role="status" aria-label="Loading reports">
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
      background: var(--pos-bg-body, #F6F8FD);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', sans-serif;
    }

    .page-shell {
      padding: 1.5rem 2rem;
      max-width: 1440px;
      margin: 0 auto;
    }

    /* ── Header (navbar.gallery standard) ── */
    .reports-header {
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
    .owner-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.2rem 0.65rem;
      border-radius: 999px;
      font-size: 0.74rem;
      font-weight: 700;
      background: #F3F0FF;
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

    .primary-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.55rem 1.15rem;
      background: #5D45FD;
      color: #FFFFFF;
      border: none;
      border-radius: 10px;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(93, 69, 253, 0.25);
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .primary-btn-tactile:active {
      transform: scale(0.97);
    }

    /* ── Bento Analytics Grid (bentogrids.com standard) ── */
    .reports-bento-grid {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr 1fr;
      gap: 1rem;
      margin-bottom: 1.5rem;
    }
    @media (max-width: 1080px) {
      .reports-bento-grid { grid-template-columns: 1fr 1fr; }
    }
    @media (max-width: 640px) {
      .reports-bento-grid { grid-template-columns: 1fr; }
    }

    .bento-card {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.25rem 1.4rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .bento-card--hero {
      background: linear-gradient(135deg, #1E1B4B 0%, #312E81 100%);
      color: #FFFFFF;
      border: none;
      box-shadow: 0 8px 24px rgba(49, 46, 129, 0.25);
    }
    .bento-card--hero .bento-label { color: rgba(255, 255, 255, 0.75); }
    .bento-card--hero .bento-value { color: #FFFFFF; }
    .bento-card--hero .delta-sub { color: rgba(255, 255, 255, 0.7); }
    .bento-card--warn {
      border-color: #FDE68A;
      background: #FFFDF7;
    }
    .bento-card--warn .bento-value { color: #D97706; }

    .bento-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .bento-label {
      font-size: 0.78rem;
      font-weight: 600;
      color: #8F95B2;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .bento-spark {
      opacity: 0.8;
    }
    .bento-value {
      font-size: 1.65rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
    }
    .bento-foot {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.78rem;
    }
    .delta-tag {
      font-size: 0.72rem;
      font-weight: 700;
      padding: 0.15rem 0.45rem;
      border-radius: 999px;
    }
    .delta-tag--up {
      background: rgba(16, 185, 129, 0.2);
      color: #34D399;
    }
    .delta-sub {
      color: #64748B;
      font-size: 0.76rem;
    }

    /* ── Secondary Financial Strip ── */
    .financial-strip-card {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      overflow: hidden;
      margin-bottom: 1.5rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    @media (max-width: 768px) {
      .financial-strip-card { grid-template-columns: 1fr; }
    }
    .strip-item {
      padding: 1.25rem 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      border-right: 1px solid #EEF0F7;
    }
    .strip-item:last-child {
      border-right: none;
    }
    .strip-label {
      font-size: 0.78rem;
      font-weight: 600;
      color: #64748B;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .strip-val {
      font-size: 1.3rem;
      font-weight: 800;
      color: #0F172A;
      font-variant-numeric: tabular-nums;
    }
    .strip-hint {
      font-size: 0.76rem;
      color: #94A3B8;
    }
    .text-danger { color: #DC2626 !important; }
    .text-warn { color: #D97706 !important; }

    /* ── Audit Note Card (unsection.com aesthetic) ── */
    .audit-note-card {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.25rem 1.5rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    .note-icon-halo {
      width: 38px;
      height: 38px;
      border-radius: 10px;
      background: #F5F3FF;
      color: #5D45FD;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .note-copy h4 {
      margin: 0 0 0.25rem;
      font-size: 0.95rem;
      font-weight: 700;
      color: #0F172A;
    }
    .note-copy p {
      margin: 0;
      font-size: 0.84rem;
      color: #64748B;
      line-height: 1.5;
    }

    /* ── Tab Navigation ── */
    .report-tabs-nav {
      display: flex;
      gap: 0.5rem;
      margin-bottom: 1.5rem;
      border-bottom: 1px solid #EEF0F7;
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
      color: #64748B;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      cursor: pointer;
      transition: all 140ms ease;
      white-space: nowrap;
      min-height: 40px;
    }
    .tab-pill:hover {
      color: #0F172A;
      border-color: #CBD5E1;
    }
    .tab-pill--active {
      background: #5D45FD;
      color: #FFFFFF;
      border-color: #5D45FD;
      box-shadow: 0 2px 8px rgba(93, 69, 253, 0.25);
    }
    .tab-pill--active:hover {
      color: #FFFFFF;
    }

    /* ── Tables & Cards ── */
    .table-card {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.5rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      margin-top: 1.25rem;
    }
    .table-card__header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      margin-bottom: 1.25rem;
      flex-wrap: wrap;
    }
    .table-card__header h3 {
      margin: 0 0 0.25rem;
      font-size: 1.15rem;
      font-weight: 800;
      color: #0F172A;
    }
    .records-pill {
      display: inline-flex;
      padding: 0.25rem 0.75rem;
      border-radius: 999px;
      background: #F1F5F9;
      color: #475569;
      font-size: 0.78rem;
      font-weight: 700;
    }
    .table-container {
      overflow-x: auto;
      border: 1px solid #F1F5F9;
      border-radius: 12px;
    }
    .report-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.88rem;
      text-align: left;
    }
    .report-table th {
      background: #F8FAFC;
      color: #475569;
      font-weight: 700;
      font-size: 0.78rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      padding: 0.85rem 1rem;
      border-bottom: 1px solid #E2E8F0;
      white-space: nowrap;
    }
    .report-table td {
      padding: 0.85rem 1rem;
      border-bottom: 1px solid #F1F5F9;
      color: #1E293B;
      min-height: 44px;
    }
    .report-table tbody tr:hover {
      background: #F8FAFC;
    }
    .num-cell {
      text-align: right;
    }
    .table-totals-row td {
      background: #F8FAFC;
      border-top: 2px solid #E2E8F0;
      font-size: 0.92rem;
    }
    .font-bold { font-weight: 700; }
    .font-semibold { font-weight: 600; }
    .text-brand { color: #5D45FD; }
    .empty-table-state {
      padding: 3rem 1.5rem;
      text-align: center;
    }

    .skeleton { background: #E2E8F0; border-radius: 14px; animation: pulse 1.5s ease-in-out infinite; }
    .skeleton-stat { height: 120px; }
    @keyframes pulse { 0%, 100% { opacity: 0.4; } 50% { opacity: 0.7; } }
  `]
})
export class ReportsPageComponent {
  private static readonly RANGE_KEY = 'business-reports-date-range';
  private readonly api = inject(BusinessApiService);
  private readonly trigger$ = new Subject<void>();
  readonly selectedRange = signal<{ from: string; to: string } | null>(this.readRange());
  readonly activeTab = signal<'overview' | 'gst' | 'items'>('overview');
  readonly refreshing = signal(false);
  readonly error = signal('');
  readonly report = toSignal(this.trigger$.pipe(startWith(undefined), switchMap(() => {
    this.refreshing.set(true); this.error.set('');
    const range = this.selectedRange();
    const now = new Date();
    const today = now.toISOString().slice(0, 10);
    const firstDayOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
    const fromDate = range?.from || firstDayOfMonth;
    const toDate = range?.to || today;

    return forkJoin({
      dashboard: this.api.getDashboard(range?.from, range?.to),
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

        function sp(v: number): string {
          if (v <= 0) return '0,20 64,20';
          const pts = Array.from({length:6}, (_,i) => v * (0.85 + (i * 0.05)));
          const mx=Math.max(...pts), mn=Math.min(...pts), rn=mx-mn||1;
          return pts.map((p,i)=>{const x=(i/5)*64,y=20-((p-mn)/rn)*20;return `${x.toFixed(1)},${y.toFixed(1)}`;}).join(' ');
        }

        const sortedItems = [...(itemSales || [])].sort((a, b) => (Number(b.quantitySold) || 0) - (Number(a.quantitySold) || 0));

        return {
          sparkRevenue: sp(totalRev),
          sparkBills: sp(billCount),
          sparkPending: sp(pending),
          sparkNet: sp(net),
          revenue: formatCurrency(totalRev),
          revenueRaw: totalRev,
          billCount: billCount,
          pendingPayments: pending,
          refundedOrders: dashboard.refundedOrders || 0,
          refundedAmount: formatCurrency(refunded),
          refundedAmountRaw: refunded,
          netRevenue: formatCurrency(net),
          netRevenueRaw: net,
          refundRate: billCount ? `${(((dashboard.refundedOrders || 0) / billCount) * 100).toFixed(1)}%` : '0%',
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
    sessionStorage.setItem(ReportsPageComponent.RANGE_KEY, JSON.stringify(range));
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
    a.download = `khanabook-report-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  private readRange(): { from: string; to: string } | null {
    try {
      const raw = sessionStorage.getItem(ReportsPageComponent.RANGE_KEY);
      if (!raw) return null;
      const range = JSON.parse(raw) as { from?: string; to?: string };
      return /^\d{4}-\d{2}-\d{2}$/.test(range.from ?? '') && /^\d{4}-\d{2}-\d{2}$/.test(range.to ?? '')
        ? { from: range.from!, to: range.to! } : null;
    } catch { return null; }
  }
}
