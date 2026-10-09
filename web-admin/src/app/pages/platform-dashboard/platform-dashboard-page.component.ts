import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, Subject, startWith, switchMap } from 'rxjs';
import { formatCurrency } from '../../shared/formatters';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { AdminSettlement, AdminTransaction, EasebuzzSubMerchant } from '../../core/models/api.models';

// Deterministic sparkline from a value — produces a stable upward-sloping path.
// Replace with real time-series data when the API supports it.
function sparkPath(value: number): string {
  if (!value || value <= 0) {
    return '0,24 72,24';
  }
  const base = value;
  const pts = Array.from({length: 7}, (_, i) => base * (0.85 + (i * 0.03)));
  const max = Math.max(...pts), min = Math.min(...pts), range = max - min || 1;
  return pts.map((v, i) => {
    const x = (i / 6) * 72, y = 24 - ((v - min) / range) * 24;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
}

@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, EmptyStateComponent],
  template: `
    <div class="page-shell">
      <section class="page-header">
        <div>
          <span class="eyebrow">Admin Platform Operations</span>
          <h2>Platform Dashboard</h2>
          <p class="muted">Cross-business snapshot of revenue, settlements, merchant onboarding, and gateway health.</p>
        </div>
        <div class="header-actions">
          <button class="ghost-btn" (click)="refreshActiveTab()">
            Refresh
          </button>
        </div>
      </section>

      <!-- Operator Navigation Tabs -->
      <nav class="operator-tabs-nav" aria-label="Platform Sections">
        <button
          type="button"
          class="tab-pill"
          [class.tab-pill--active]="activeTab() === 'overview'"
          (click)="setTab('overview')">
          Platform Overview
        </button>
        <button
          type="button"
          class="tab-pill"
          [class.tab-pill--active]="activeTab() === 'settlements'"
          (click)="setTab('settlements')">
          Settlements &amp; Commissions
        </button>
        <button
          type="button"
          class="tab-pill"
          [class.tab-pill--active]="activeTab() === 'submerchants'"
          (click)="setTab('submerchants')">
          Sub-Merchants &amp; KYC
        </button>
        <button
          type="button"
          class="tab-pill"
          [class.tab-pill--active]="activeTab() === 'transactions'"
          (click)="setTab('transactions')">
          Gateway Transactions
        </button>
      </nav>

      <!-- TAB 1: OVERVIEW -->
      <ng-container *ngIf="activeTab() === 'overview'">
        <ng-container *ngIf="summary() as data; else loading">
          <section class="kpi-row">
            <article class="kpi-card kpi-card--hero" (click)="navigateToBusinesses()" role="button" tabindex="0" (keydown.enter)="navigateToBusinesses()">
              <div class="kpi-head">
                <span class="kpi-label">Total Revenue</span>
                <svg width="72" height="24" viewBox="0 0 72 24" class="kpi-spark" aria-hidden="true">
                  <polyline fill="none" stroke="rgba(124,45,18,0.5)" stroke-width="1.5" [attr.points]="data.sparkRevenue" />
                </svg>
              </div>
              <strong class="kpi-value">{{ data.totalRevenueFormatted }}</strong>
              <div class="kpi-delta">
                <span class="kpi-compare">All-time</span>
              </div>
            </article>
            <article class="kpi-card">
              <div class="kpi-head">
                <span class="kpi-label">Total Businesses</span>
                <svg width="72" height="24" viewBox="0 0 72 24" class="kpi-spark" aria-hidden="true">
                  <polyline fill="none" stroke="var(--success)" stroke-width="1.5" [attr.points]="data.sparkBusinesses" />
                </svg>
              </div>
              <strong class="kpi-value">{{ data.totalBusinesses }}</strong>
              <div class="kpi-delta">
                <span class="kpi-compare">All-time</span>
              </div>
            </article>
            <article class="kpi-card">
              <div class="kpi-head">
                <span class="kpi-label">Total Orders</span>
                <svg width="72" height="24" viewBox="0 0 72 24" class="kpi-spark" aria-hidden="true">
                  <polyline fill="none" stroke="var(--brand)" stroke-width="1.5" [attr.points]="data.sparkOrders" />
                </svg>
              </div>
              <strong class="kpi-value">{{ data.totalOrders }}</strong>
              <div class="kpi-delta">
                <span class="kpi-foot">All-time volume</span>
              </div>
            </article>
            <article class="kpi-card" [class.kpi-card--warn]="data.refundedOrders > 0">
              <div class="kpi-head">
                <span class="kpi-label">Refunds</span>
                <svg width="72" height="24" viewBox="0 0 72 24" class="kpi-spark" aria-hidden="true">
                  <polyline fill="none" stroke="var(--danger)" stroke-width="1.5" [attr.points]="data.sparkRefunds" />
                </svg>
              </div>
              <strong class="kpi-value">{{ data.refundedAmountFormatted }}</strong>
              <div class="kpi-delta">
                <span class="kpi-compare">{{ data.refundedOrders }} orders</span>
              </div>
            </article>
          </section>

          <section class="kpi-secondary">
            <div class="kpi-mini">
              <span class="kpi-mini-label">Live storefronts</span>
              <span class="kpi-mini-value" style="color: var(--success);">{{ data.liveBusinesses }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">Total staff</span>
              <span class="kpi-mini-value">{{ data.totalStaff }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">Refunded orders</span>
              <span class="kpi-mini-value" [style.color]="data.refundedOrders > 0 ? 'var(--danger)' : 'var(--ink)'">{{ data.refundedOrders }}</span>
            </div>
            <div class="kpi-mini">
              <span class="kpi-mini-label">Refunded amount</span>
              <span class="kpi-mini-value" [style.color]="data.refundedOrders > 0 ? 'var(--danger)' : 'var(--ink)'">{{ data.refundedAmountFormatted }}</span>
            </div>
          </section>

          <section class="focus-panel">
            <header>
              <h3>Suggested focus</h3>
              <p class="muted">Weekly checks to keep the platform view actionable.</p>
            </header>
            <div class="focus-grid">
              <article class="focus-card">
                <span class="focus-tag focus-tag--warn">Weekly</span>
                <h4>Audit low-activity stores</h4>
                <p>Review businesses that are configured but not actively transacting.</p>
              </article>
              <article class="focus-card">
                <span class="focus-tag focus-tag--danger">Risk</span>
                <h4>Watch refund drift</h4>
                <p>Track refunded orders beside revenue to catch payment issues early.</p>
              </article>
              <article class="focus-card">
                <span class="focus-tag focus-tag--success">Ops</span>
                <h4>Review staffing spread</h4>
                <p>Business growth with flat staff counts signals onboarding gaps.</p>
              </article>
            </div>
          </section>
        </ng-container>

        <ng-template #loading>
          <div *ngIf="!summaryError(); else summaryErrorState" class="kpi-row">
            <div class="skeleton skeleton-stat" *ngFor="let i of [1,2,3,4]; trackBy: trackByIndex"></div>
          </div>
          <ng-template #summaryErrorState>
            <div class="panel loading">
              <p>{{ summaryError() }}</p>
              <button class="primary-btn" (click)="refresh()">Retry</button>
            </div>
          </ng-template>
        </ng-template>
      </ng-container>

      <!-- TAB 2: SETTLEMENTS & COMMISSIONS -->
      <ng-container *ngIf="activeTab() === 'settlements'">
        <div class="table-card" *ngIf="!loadingTab(); else tabLoadingState">
          <header class="table-card__header">
            <div>
              <h3>Merchant Settlements &amp; Platform Commissions</h3>
              <p class="muted">Realized merchant volumes and platform revenue retention per restaurant.</p>
            </div>
            <span class="records-pill tabular-num">{{ settlements().length }} Restaurants</span>
          </header>

          <div *ngIf="tabError()" class="panel loading">
            <p>{{ tabError() }}</p>
            <button class="primary-btn" (click)="loadSettlements()">Retry</button>
          </div>

          <div class="table-container" *ngIf="!tabError() && settlements().length > 0; else noSettlementsState">
            <table class="admin-table">
              <thead>
                <tr>
                  <th>Restaurant / Shop</th>
                  <th class="num-cell">Total Settled Volume</th>
                  <th class="num-cell">Commission Earned</th>
                  <th class="num-cell">Orders Settled</th>
                  <th>Last Settlement</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let row of settlements(); trackBy: trackByRestaurantId">
                  <td class="font-bold">{{ row.shopName || ('Restaurant #' + row.restaurantId) }}</td>
                  <td class="num-cell tabular-num font-semibold">{{ formatMoney(row.totalSettled ?? row.amount) }}</td>
                  <td class="num-cell tabular-num font-bold text-brand">{{ formatMoney(row.totalCommission) }}</td>
                  <td class="num-cell tabular-num">{{ row.orderCount ?? '-' }}</td>
                  <td class="muted">{{ row.lastSettledAt ? (row.lastSettledAt | date:'medium') : 'Pending settlement' }}</td>
                </tr>
              </tbody>
              <tfoot>
                <tr class="table-totals-row">
                  <td><strong>Platform Total</strong></td>
                  <td class="num-cell tabular-num font-bold">{{ formatMoney(totalSettledAmount()) }}</td>
                  <td class="num-cell tabular-num font-bold text-brand">{{ formatMoney(totalCommissionAmount()) }}</td>
                  <td colspan="2"></td>
                </tr>
              </tfoot>
            </table>
          </div>
          <ng-template #noSettlementsState>
            <div *ngIf="!tabError()" class="empty-state-box">
              <p class="muted">No settlement records available yet.</p>
            </div>
          </ng-template>
        </div>
      </ng-container>

      <!-- TAB 3: SUB-MERCHANTS & KYC -->
      <ng-container *ngIf="activeTab() === 'submerchants'">
        <div class="table-card" *ngIf="!loadingTab(); else tabLoadingState">
          <header class="table-card__header">
            <div>
              <h3>Easebuzz Sub-Merchants &amp; KYC Onboarding</h3>
              <p class="muted">Payment gateway sub-merchant IDs, bank account binding, and KYC compliance status.</p>
            </div>
            <span class="records-pill tabular-num">{{ subMerchants().length }} Sub-Merchants</span>
          </header>

          <div *ngIf="tabError()" class="panel loading">
            <p>{{ tabError() }}</p>
            <button class="primary-btn" (click)="loadSubMerchants()">Retry</button>
          </div>

          <div class="table-container" *ngIf="!tabError() && subMerchants().length > 0; else noSubMerchantsState">
            <table class="admin-table">
              <thead>
                <tr>
                  <th>Restaurant / Shop</th>
                  <th>Sub-Merchant ID</th>
                  <th>Gateway Status</th>
                  <th>Legal Entity</th>
                  <th>PAN / GST</th>
                  <th>Bank Details</th>
                  <th>Onboarded</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let sm of subMerchants(); trackBy: trackBySubMerchantId">
                  <td class="font-bold">{{ sm.shopName || ('Restaurant #' + sm.restaurantId) }}</td>
                  <td>
                    <code class="code-badge">{{ sm.subMerchantId || 'Unassigned' }}</code>
                  </td>
                  <td>
                    <span class="status-pill" [class.status-pill--active]="sm.status === 'ACTIVE'" [class.status-pill--warn]="sm.status === 'PENDING' || sm.status === 'DRAFT'">
                      {{ sm.status || 'DRAFT' }}
                    </span>
                  </td>
                  <td>{{ sm.businessName || sm.beneficiaryName || '-' }}</td>
                  <td class="tabular-num">{{ (sm.pan || '-') + ' / ' + (sm.gst || '-') }}</td>
                  <td class="tabular-num muted">
                    {{ sm.bankAccountNo ? ('••••' + sm.bankAccountNo.slice(-4)) : '-' }}
                    <span *ngIf="sm.ifsc">({{ sm.ifsc }})</span>
                  </td>
                  <td class="muted">{{ sm.createdAt ? (sm.createdAt | date:'shortDate') : '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <ng-template #noSubMerchantsState>
            <div *ngIf="!tabError()" class="empty-state-box">
              <p class="muted">No sub-merchant accounts created yet.</p>
            </div>
          </ng-template>
        </div>
      </ng-container>

      <!-- TAB 4: GATEWAY TRANSACTIONS -->
      <ng-container *ngIf="activeTab() === 'transactions'">
        <div class="table-card" *ngIf="!loadingTab(); else tabLoadingState">
          <header class="table-card__header">
            <div>
              <h3>Live Gateway Transactions</h3>
              <p class="muted">Recent digital payment attempts, gateway reference codes, and real-time webhook status.</p>
            </div>
            <span class="records-pill tabular-num">{{ transactions().length }} Transactions</span>
          </header>

          <div *ngIf="tabError()" class="panel loading">
            <p>{{ tabError() }}</p>
            <button class="primary-btn" (click)="loadTransactions()">Retry</button>
          </div>

          <div class="table-container" *ngIf="!tabError() && transactions().length > 0; else noTransactionsState">
            <table class="admin-table">
              <thead>
                <tr>
                  <th>Transaction ID</th>
                  <th>Restaurant / Shop</th>
                  <th>Easebuzz Ref</th>
                  <th class="num-cell">Amount</th>
                  <th>Status</th>
                  <th>Received At</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let tx of transactions(); trackBy: trackByTxnId">
                  <td>
                    <code class="code-badge">{{ tx.txnId || ('TXN-' + tx.id) }}</code>
                  </td>
                  <td class="font-bold">{{ tx.shopName || ('Restaurant #' + tx.restaurantId) }}</td>
                  <td class="muted">{{ tx.easebuzzId || tx.gatewayTransactionId || '-' }}</td>
                  <td class="num-cell tabular-num font-bold">{{ formatMoney(tx.amount) }}</td>
                  <td>
                    <span class="status-pill" [class.status-pill--active]="tx.status === 'success' || tx.status === 'SUCCESS'" [class.status-pill--danger]="tx.status === 'failure' || tx.status === 'FAILED'">
                      {{ tx.status }}
                    </span>
                  </td>
                  <td class="muted">{{ (tx.receivedAt || tx.createdAt) ? ((tx.receivedAt || tx.createdAt)! | date:'medium') : '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <ng-template #noTransactionsState>
            <div *ngIf="!tabError()" class="empty-state-box">
              <p class="muted">No recent gateway transactions logged.</p>
            </div>
          </ng-template>
        </div>
      </ng-container>

      <ng-template #tabLoadingState>
        <div class="kpi-row" style="margin-top: var(--kb-space-4);">
          <div class="skeleton skeleton-stat" *ngFor="let i of [1,2,3,4]; trackBy: trackByIndex"></div>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .page-shell { display: grid; gap: calc(var(--kb-space-6)); }

    .page-header { display: flex; justify-content: space-between; align-items: end; gap: var(--kb-space-4); flex-wrap: wrap; }
    .page-header h2 { margin: 0.25rem 0 0.35rem; font-size: 1.75rem; letter-spacing: -0.01em; }
    .page-header p { margin: 0; }
    .eyebrow {
      text-transform: uppercase; letter-spacing: 0.08em;
      font-size: 0.72rem; font-weight: 700; color: var(--kb-color-primary);
    }
    .header-actions { display: flex; align-items: center; gap: var(--kb-space-2); }

    /* ── Tab Navigation ── */
    .operator-tabs-nav {
      display: flex;
      gap: 0.5rem;
      border-bottom: 1px solid var(--kb-color-border);
      padding-bottom: 0.75rem;
      overflow-x: auto;
    }
    .tab-pill {
      display: inline-flex;
      align-items: center;
      padding: 0.55rem 1.1rem;
      border-radius: 999px;
      font-size: 0.84rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      cursor: pointer;
      transition: all 140ms ease;
      white-space: nowrap;
      min-height: 40px;
    }
    .tab-pill:hover {
      color: var(--kb-color-foreground);
      border-color: var(--kb-color-primary);
    }
    .tab-pill--active {
      background: var(--kb-color-primary);
      color: var(--kb-color-primary-foreground, #ffffff);
      border-color: var(--kb-color-primary);
      box-shadow: 0 2px 8px rgba(93, 69, 253, 0.25);
    }
    .tab-pill--active:hover {
      color: var(--kb-color-primary-foreground, #ffffff);
    }

    /* ── KPI Grid ── */
    .kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--kb-space-4); }
    @media (max-width: 1100px) { .kpi-row { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 560px)  { .kpi-row { grid-template-columns: 1fr; } }

    .kpi-card {
      background: var(--kb-color-surface); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl); padding: var(--kb-space-3) var(--kb-space-4);
      display: grid; gap: var(--kb-space-2);
      transition: border-color .18s, transform .18s, box-shadow .18s;
      box-shadow: var(--kb-shadow-xs);
    }
    .kpi-card--hero {
      background: var(--kb-gradient-hero); border-color: transparent; color: var(--kb-color-primary-foreground);
    }
    .kpi-card--hero:hover { transform: translateY(-1px); box-shadow: var(--kb-shadow-md); border-color: var(--kb-color-primary); }
    .kpi-card--warn {
      border-color: var(--kb-color-error-soft);
      background: linear-gradient(160deg, var(--kb-color-error-soft) 0%, var(--kb-color-surface) 60%);
    }

    .kpi-head { display: flex; justify-content: space-between; align-items: flex-start; }
    .kpi-spark { flex-shrink: 0; opacity: 0.6; }

    .kpi-label {
      font-size: 0.78rem; color: var(--kb-color-muted-foreground);
      text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600;
    }
    .kpi-value {
      font-size: calc(1.5rem + 0.3vw); font-weight: 700; color: var(--kb-color-foreground);
      letter-spacing: -0.01em; font-variant-numeric: tabular-nums;
    }
    .kpi-card--hero .kpi-value { font-size: calc(1.8rem + 0.3vw); color: var(--kb-color-primary-foreground); }
    .kpi-foot { font-size: 0.82rem; color: var(--muted); }
    .kpi-delta { display: flex; align-items: center; gap: 0.4rem; font-size: 0.78rem; color: var(--muted); }
    .kpi-compare { color: var(--muted); }

    .kpi-secondary {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 0;
      background: var(--kb-color-surface); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg); overflow: hidden;
    }
    @media (max-width: 720px) { .kpi-secondary { grid-template-columns: repeat(2, 1fr); } }
    .kpi-mini {
      padding: var(--kb-space-3) var(--kb-space-4); display: grid; gap: var(--kb-space-2);
      border-right: 1px solid var(--kb-color-border);
    }
    .kpi-mini:last-child { border-right: none; }
    @media (max-width: 720px) {
      .kpi-mini:nth-child(2n) { border-right: none; }
      .kpi-mini:nth-child(-n+2) { border-bottom: 1px solid var(--kb-color-border); }
    }
    .kpi-mini-label { font-size: 0.76rem; color: var(--kb-color-muted-foreground); font-weight: 600; }
    .kpi-mini-value { font-size: 1.05rem; font-weight: 700; color: var(--kb-color-foreground); font-variant-numeric: tabular-nums; }

    .focus-panel {
      background: var(--kb-color-surface); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl); padding: var(--kb-space-4) var(--kb-space-5);
    }
    .focus-panel header { margin-bottom: var(--kb-space-3); }
    .focus-panel h3 { margin: 0 0 0.25rem; font-size: 1.1rem; }
    .focus-panel p { margin: 0; }
    .focus-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--kb-space-3); }
    @media (max-width: 900px) { .focus-grid { grid-template-columns: 1fr; } }
    .focus-card {
      padding: var(--kb-space-3) var(--kb-space-4);
      background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg); display: grid; gap: var(--kb-space-2);
    }
    .focus-card h4 { margin: 0.1rem 0 0; font-size: 0.98rem; }
    .focus-card p { margin: 0; color: var(--kb-color-muted-foreground); font-size: 0.88rem; line-height: 1.5; }
    .focus-tag {
      justify-self: start; font-size: 0.7rem; font-weight: 700;
      letter-spacing: 0.06em; text-transform: uppercase;
      padding: var(--kb-space-1) var(--kb-space-2); border-radius: var(--kb-radius-md);
    }
    .focus-tag--warn { background: var(--kb-color-warning-soft); color: var(--kb-color-warning); }
    .focus-tag--danger { background: var(--kb-color-error-soft); color: var(--kb-color-error); }
    .focus-tag--success { background: var(--kb-color-success-soft); color: var(--kb-color-success); }

    /* ── Table Card & Responsive Tables ── */
    .table-card {
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl);
      padding: var(--kb-space-4);
      box-shadow: var(--kb-shadow-xs);
    }
    .table-card__header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      margin-bottom: var(--kb-space-3);
      flex-wrap: wrap;
    }
    .table-card__header h3 {
      margin: 0 0 2px;
      font-size: 1.15rem;
      font-weight: 700;
    }
    .records-pill {
      display: inline-flex;
      padding: 3px 10px;
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      font-size: 0.78rem;
      font-weight: 700;
      border: 1px solid var(--kb-color-border);
    }
    .table-container {
      overflow-x: auto;
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
    }
    .admin-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.88rem;
      text-align: left;
    }
    .admin-table th {
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      font-weight: 700;
      font-size: 0.76rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      padding: 10px 14px;
      border-bottom: 1px solid var(--kb-color-border);
      white-space: nowrap;
    }
    .admin-table td {
      padding: 10px 14px;
      border-bottom: 1px solid var(--kb-color-border);
      color: var(--kb-color-foreground);
      min-height: 44px;
    }
    .admin-table tbody tr:hover {
      background: var(--kb-color-surface-2);
    }
    .num-cell { text-align: right; }
    .table-totals-row td {
      background: var(--kb-color-surface-2);
      border-top: 2px solid var(--kb-color-border);
      font-size: 0.92rem;
    }
    .font-bold { font-weight: 700; }
    .font-semibold { font-weight: 600; }
    .text-brand { color: var(--kb-color-primary); }
    .code-badge {
      font-family: monospace;
      font-size: 0.8rem;
      padding: 2px 6px;
      border-radius: var(--kb-radius-sm);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
    }
    .status-pill {
      display: inline-flex;
      align-items: center;
      padding: 2px 8px;
      border-radius: var(--kb-radius-full);
      font-size: 0.74rem;
      font-weight: 700;
      text-transform: uppercase;
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      border: 1px solid var(--kb-color-border);
    }
    .status-pill--active {
      background: rgba(34, 197, 94, 0.1);
      color: #16a34a;
      border-color: rgba(34, 197, 94, 0.3);
    }
    .status-pill--warn {
      background: rgba(245, 158, 11, 0.1);
      color: #d97706;
      border-color: rgba(245, 158, 11, 0.3);
    }
    .status-pill--danger {
      background: rgba(239, 68, 68, 0.1);
      color: #dc2626;
      border-color: rgba(239, 68, 68, 0.3);
    }
    .empty-state-box {
      padding: 3rem 1rem;
      text-align: center;
    }

    .skeleton { background: var(--line); border-radius: var(--r-md); animation: pulse 1.5s ease-in-out infinite; }
    .skeleton-stat { height: 130px; }
    @keyframes pulse { 0%, 100% { opacity: 0.4; } 50% { opacity: 0.7; } }
    .loading { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--kb-space-4); padding: var(--kb-space-8); text-align: center; }
  `]
})
export class PlatformDashboardPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly router = inject(Router);

  readonly summaryError = signal('');
  private readonly refresh$ = new Subject<void>();

  readonly activeTab = signal<'overview' | 'settlements' | 'submerchants' | 'transactions'>('overview');
  readonly settlements = signal<AdminSettlement[]>([]);
  readonly subMerchants = signal<EasebuzzSubMerchant[]>([]);
  readonly transactions = signal<AdminTransaction[]>([]);
  readonly loadingTab = signal(false);
  readonly tabError = signal('');

  readonly summary = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => {
        this.summaryError.set('');
        return this.api.getDashboardSummary().pipe(
          map((summary) => ({
            ...summary,
            totalRevenueFormatted: formatCurrency(summary.totalRevenue),
            refundedAmountFormatted: formatCurrency(summary.refundedAmount),
            sparkRevenue: sparkPath(summary.totalRevenue ?? 0),
            sparkBusinesses: sparkPath(summary.totalBusinesses ?? 0),
            sparkOrders: sparkPath(summary.totalOrders ?? 0),
            sparkRefunds: sparkPath(summary.refundedAmount ?? 0),
            liveBusinesses: summary.liveBusinesses ?? summary.totalBusinesses ?? 0,
          })),
          catchError((error: unknown) => {
            const response = error as { error?: { message?: string; error?: string } };
            this.summaryError.set(
              response.error?.message || response.error?.error || 'Unable to load the platform dashboard.'
            );
            return of(null);
          })
        );
      })
    )
  );

  setTab(tab: 'overview' | 'settlements' | 'submerchants' | 'transactions'): void {
    this.activeTab.set(tab);
    if (tab === 'settlements' && this.settlements().length === 0) {
      this.loadSettlements();
    } else if (tab === 'submerchants' && this.subMerchants().length === 0) {
      this.loadSubMerchants();
    } else if (tab === 'transactions' && this.transactions().length === 0) {
      this.loadTransactions();
    }
  }

  refreshActiveTab(): void {
    const tab = this.activeTab();
    if (tab === 'overview') this.refresh();
    else if (tab === 'settlements') this.loadSettlements();
    else if (tab === 'submerchants') this.loadSubMerchants();
    else if (tab === 'transactions') this.loadTransactions();
  }

  loadSettlements(): void {
    this.loadingTab.set(true);
    this.tabError.set('');
    this.api.getSettlements().subscribe({
      next: (data) => {
        this.settlements.set(data || []);
        this.loadingTab.set(false);
      },
      error: (err) => {
        this.tabError.set(err?.error?.message ?? 'Failed to load settlements.');
        this.loadingTab.set(false);
      }
    });
  }

  loadSubMerchants(): void {
    this.loadingTab.set(true);
    this.tabError.set('');
    this.api.getSubMerchants().subscribe({
      next: (data) => {
        this.subMerchants.set(data || []);
        this.loadingTab.set(false);
      },
      error: (err) => {
        this.tabError.set(err?.error?.message ?? 'Failed to load sub-merchants.');
        this.loadingTab.set(false);
      }
    });
  }

  loadTransactions(): void {
    this.loadingTab.set(true);
    this.tabError.set('');
    this.api.getTransactions(0, 50).subscribe({
      next: (data) => {
        this.transactions.set(data || []);
        this.loadingTab.set(false);
      },
      error: (err) => {
        this.tabError.set(err?.error?.message ?? 'Failed to load transactions.');
        this.loadingTab.set(false);
      }
    });
  }

  formatMoney(amount: number | null | undefined): string {
    return formatCurrency(Number(amount) || 0);
  }

  totalSettledAmount(): number {
    return this.settlements().reduce((sum, s) => sum + (Number(s.totalSettled ?? s.amount) || 0), 0);
  }

  totalCommissionAmount(): number {
    return this.settlements().reduce((sum, s) => sum + (Number(s.totalCommission) || 0), 0);
  }

  trackByRestaurantId(_: number, item: AdminSettlement): number {
    return item.restaurantId;
  }

  trackBySubMerchantId(_: number, item: EasebuzzSubMerchant): number {
    return item.id;
  }

  trackByTxnId(_: number, item: AdminTransaction): number {
    return item.id;
  }

  refresh(): void { this.refresh$.next(); }
  navigateToBusinesses(): void { this.router.navigate(['/admin/businesses']); }
  trackByIndex = (_: number, __: unknown) => _;
}

