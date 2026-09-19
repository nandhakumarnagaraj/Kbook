import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder, OrderDetailResponse, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ApiStateComponent } from '../../core/components/api-state.component';

export function escapeCsvField(value: string): string {
  const spreadsheetSafe = /^[=+\-@]/.test(value) ? `'${value}` : value;
  if (spreadsheetSafe.includes(',') || spreadsheetSafe.includes('"')
      || spreadsheetSafe.includes('\n') || spreadsheetSafe.includes('\r')) {
    return `"${spreadsheetSafe.replace(/"/g, '""')}"`;
  }
  return spreadsheetSafe;
}

export interface BusinessOrderFilters {
  searchTerm: string;
  statusFilter: string;
  sourceFilter: string;
  dateFrom: string | null;
  dateTo: string | null;
}

export function filterBusinessOrders(
  orders: BusinessOrder[],
  filters: BusinessOrderFilters
): BusinessOrder[] {
  const search = filters.searchTerm.trim().toLowerCase();
  return orders.filter(order => {
    const matchesSearch = !search || [
      order.orderCode,
      order.customerName ?? '',
      order.customerContact ?? '',
      order.paymentMethod,
      order.paymentStatus
    ].some(value => value.toLowerCase().includes(search));
    const matchesStatus = filters.statusFilter === 'ALL' || order.orderStatus === filters.statusFilter;
    const matchesSource = filters.sourceFilter === 'ALL' || order.sourceType === filters.sourceFilter;
    const matchesDate = !filters.dateFrom || !filters.dateTo
      ? true
      : Boolean(order.createdAt)
        && new Date(order.createdAt!) >= new Date(filters.dateFrom + 'T00:00:00')
        && new Date(order.createdAt!) <= new Date(filters.dateTo + 'T23:59:59.999');
    return matchesSearch && matchesStatus && matchesSource && matchesDate;
  });
}

@Component({
  selector: 'app-orders-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, DateRangeSelectorComponent, OrderDetailModalComponent, EmptyStateComponent, ApiStateComponent],
  template: `
    <div class="page-shell">
      <header class="operational-orders-header">
        <div class="header-left">
          <div class="header-title-row">
            <h2>Order History &amp; Settlements</h2>
            <span class="record-counter-pill" *ngIf="ordersLoaded">{{ filteredOrders.length }} Records</span>
          </div>
          <p class="header-sub">POS transactions, refund reconciliation, and customer invoice tracking.</p>
        </div>
        <div class="header-right">
          <button
            type="button"
            class="ghost-btn-tactile"
            [disabled]="filteredOrders.length === 0"
            (click)="exportCsv()"
            title="Export filtered orders as CSV">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            Export CSV
          </button>
          <button type="button" class="primary-btn-tactile" (click)="loadOrders()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            Refresh
          </button>
        </div>
      </header>

      <div class="modal-backdrop" *ngIf="refundTarget" (click)="closeRefund()">
        <section
          class="modal-box"
          role="dialog"
          aria-modal="true"
          aria-labelledby="refund-dialog-title"
          (click)="$event.stopPropagation()"
        >
          <h3 id="refund-dialog-title">Record Manual Refund</h3>
          <p class="muted">Order {{ refundTarget.orderCode }} · Total {{ formatCurrencyValue(refundTarget.totalAmount) }}</p>
          <p class="hint-text">
            Use this only after the money has been returned outside KhanaBook. This action records the refund; it does not contact a payment gateway.
          </p>

          <ng-container *ngIf="!refundReview; else refundReviewStep">
          <div class="field">
            <label for="refund-amount">Refund Amount</label>
            <input
              id="refund-amount"
              class="field-control"
              type="number"
              [(ngModel)]="refundAmountInput"
              [max]="refundTarget.totalAmount"
              min="0.01"
              step="0.01"
              placeholder="Enter amount"
              autofocus
            />
          </div>
          <div class="field">
            <label for="refund-reason">Reason</label>
            <input id="refund-reason" class="field-control" type="text" [(ngModel)]="refundReasonInput" placeholder="For example, customer request" />
          </div>

          <p class="error-text" role="alert" *ngIf="refundAmountInput && refundAmountInput > refundTarget.totalAmount">
            Refund amount cannot exceed the order total.
          </p>
          <p class="error-text" role="alert" *ngIf="refundError">{{ refundError }}</p>

          </ng-container>
          <ng-template #refundReviewStep>
            <div class="refund-review" role="status">
              <span>Refund amount</span><strong>{{ formatCurrencyValue(refundAmountInput) }}</strong>
              <span>Reason</span><strong>{{ refundReasonInput.trim() || 'Refund handled manually' }}</strong>
            </div>
            <p class="hint-text">Review these details carefully. KhanaBook will record this refund immediately after confirmation.</p>
            <p class="error-text" role="alert" *ngIf="refundError">{{ refundError }}</p>
          </ng-template>

          <div class="modal-actions">
            <button type="button" class="ghost-btn" (click)="refundReview ? returnToRefundForm() : closeRefund()">{{ refundReview ? 'Back' : 'Cancel' }}</button>
            <button
              type="button"
              class="ghost-btn danger-btn"
              [attr.aria-busy]="refunding"
              [disabled]="refunding || !refundAmountInput || refundAmountInput <= 0 || refundAmountInput > refundTarget.totalAmount"
              (click)="refundReview ? confirmRefund() : reviewRefund()"
            >
              {{ refunding ? 'Recording...' : (refundReview ? 'Confirm Refund' : 'Review Refund') }}
            </button>
          </div>
        </section>
      </div>

      <!-- Order Detail Modal -->
      <app-order-detail-modal
        [order]="selectedOrderDetail"
        (closed)="closeOrderDetail()">
      </app-order-detail-modal>
      <div class="panel loading" *ngIf="orderDetailLoading" role="status" aria-live="polite">
        Loading order details...
      </div>

      <!-- Bento KPI Strip (bentogrids.com standard) -->
      <section class="orders-bento-grid" *ngIf="ordersLoaded && filteredOrders.length" aria-label="Orders Analytics Overview">
        <article class="bento-tile bento-tile--hero">
          <div class="bento-tile__head">
            <span class="bento-tile__label">Filtered Gross Revenue</span>
            <span class="bento-pulse-tag">● Live</span>
          </div>
          <strong class="bento-tile__value">{{ formatCurrencyValue(filteredTotalRevenue) }}</strong>
          <div class="bento-split-bar">
            <span class="split-sub">Cash: {{ formatCurrencyValue(cashVolume) }}</span>
            <span class="split-dot">&bull;</span>
            <span class="split-sub">Digital / UPI: {{ formatCurrencyValue(digitalVolume) }}</span>
          </div>
        </article>

        <article class="bento-tile">
          <span class="bento-tile__label">Settlement Rate</span>
          <strong class="bento-tile__value">{{ settlementRate }}%</strong>
          <span class="bento-tile__sub">{{ filteredCompletedCount }} of {{ filteredOrders.length }} orders completed</span>
        </article>

        <article class="bento-tile" [class.bento-tile--alert]="filteredRefundedTotal > 0">
          <span class="bento-tile__label">Manual Refunds</span>
          <strong class="bento-tile__value">{{ formatCurrencyValue(filteredRefundedTotal) }}</strong>
          <span class="bento-tile__sub">{{ refundedOrdersCount }} orders refunded</span>
        </article>
      </section>

      <app-api-state
        *ngIf="ordersError"
        [loading]="false"
        [error]="ordersError"
        (retry)="loadOrders()"
      ></app-api-state>

      <section class="panel filter-panel" *ngIf="ordersLoaded">
        <div class="filter-grid">
          <div class="filter-group">
            <label for="order-search">Search</label>
            <input
              id="order-search"
              class="field-control"
              type="text"
              [(ngModel)]="orderSearchTerm"
              placeholder="Search by order code, customer, or invoice number"
            />
          </div>
          <div class="filter-group">
            <label for="order-status">Status</label>
            <select id="order-status" class="field-select" [(ngModel)]="orderStatusFilter" (ngModelChange)="resetOrderPage()">
              <option value="ALL">All statuses</option>
              <option *ngFor="let status of businessOrderStatuses; trackBy: trackByIndex" [value]="status">{{ status }}</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="order-source">Source</label>
            <select id="order-source" class="field-select" [(ngModel)]="orderSourceFilter" (ngModelChange)="resetOrderPage()">
              <option value="ALL">All sources</option>
              <option *ngFor="let source of businessOrderSources; trackBy: trackByIndex" [value]="source">{{ source }}</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="order-size">Rows</label>
            <select id="order-size" class="field-select" [(ngModel)]="orderPageSize" (ngModelChange)="resetOrderPage()">
              <option [ngValue]="10">10</option>
              <option [ngValue]="20">20</option>
              <option [ngValue]="50">50</option>
            </select>
          </div>
        </div>

        <!-- Date Range Filter -->
        <div class="filter-group filter-group--full">
          <label>Date Range</label>
          <app-date-range-selector (rangeChanged)="onDateRangeChange($event)"></app-date-range-selector>
        </div>

        <div class="filter-summary">
          <p class="muted">
            {{ filteredOrders.length }} of {{ serverTotalElements }} orders
            <span *ngIf="dateRangeLabel" class="date-range-active">&#x1f4c5; {{ dateRangeLabel }}</span>
          </p>
          <button class="ghost-btn" (click)="clearOrderFilters()">Clear filters</button>
        </div>
      </section>

      <div class="panel table-wrap" *ngIf="ordersLoaded && pagedOrders.length; else posLoading">
        <table class="data-table">
          <thead>
            <tr>
              <th>Source</th>
              <th>Order</th>
              <th>Customer</th>
              <th>Status</th>
              <th>Payment</th>
              <th>Total</th>
              <th>Refund</th>
              <th>Created</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
             <tr *ngFor="let order of pagedOrders; trackBy: trackByOrderId" class="clickable-row" tabindex="0" role="button" [attr.aria-label]="'View order ' + order.orderCode" (click)="openOrderDetail(order)" (keydown.enter)="openOrderDetail(order)">
              <td><span class="chip">{{ order.sourceType }}</span></td>
              <td>{{ order.orderCode }}</td>
              <td>
                <div class="stacked-meta">
                  <strong>{{ order.customerName || '-' }}</strong>
                  <span class="muted">{{ order.customerContact || 'No contact provided' }}</span>
                </div>
              </td>
              <td>
                <span
                  class="chip"
                  [class.success]="order.orderStatus.toLowerCase() === 'completed'"
                  [class.danger]="order.orderStatus.toLowerCase() === 'cancelled'"
                  [class.warn]="order.orderStatus.toLowerCase() === 'draft'"
                >
                  {{ order.orderStatus }}
                </span>
              </td>
              <td>
                {{ order.paymentMethod }} /
                <span [class.refunded-label]="order.paymentStatus.toLowerCase() === 'refunded'">
                  {{ order.paymentStatus }}
                </span>
              </td>
              <td>{{ formatCurrencyValue(order.totalAmount) }}</td>
              <td>
                <span *ngIf="order.refundAmount && order.refundAmount > 0" class="refunded-label">
                  -{{ formatCurrencyValue(order.refundAmount) }}<br />
                  <span class="refund-meta">
                    {{ order.refundStatus }}<span *ngIf="order.refundMode"> / {{ order.refundMode }}</span>
                  </span><br />
                  <span class="muted" style="font-size: 0.75rem;">{{ order.cancelReason }}</span>
                </span>
                <span *ngIf="!order.refundAmount || order.refundAmount === 0" class="muted">{{ order.refundStatus || '-' }}</span>
              </td>
              <td>{{ formatDateValue(order.createdAt) }}</td>
              <td>
                <div class="action-stack">
                  <button
                    *ngIf="order.manualRefundAllowed"
                    class="ghost-btn danger-btn"
                    (click)="openManualRefund(order); $event.stopPropagation()"
                  >
                    Manual Refund
                  </button>
                  <button
                    *ngIf="order.orderStatus === 'draft' || order.orderStatus === 'completed'"
                    class="ghost-btn danger-btn"
                    (click)="cancelOrder(order); $event.stopPropagation()"
                  >
                    Cancel
                  </button>
                  <span *ngIf="!order.manualRefundAllowed && order.orderStatus !== 'draft'" class="muted">-</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="mobile-order-list" aria-label="Orders">
          <article *ngFor="let order of pagedOrders; trackBy: trackByOrderId" class="mobile-order-card" [class.mobile-order-card--refunded]="order.refundAmount && order.refundAmount > 0">
            <button type="button" class="mobile-order-card__main" (click)="openOrderDetail(order)" [attr.aria-label]="'View order ' + order.orderCode">
              <span class="mobile-order-card__title">{{ order.orderCode }}</span>
              <span class="chip" [class.success]="order.orderStatus.toLowerCase() === 'completed'" [class.danger]="order.orderStatus.toLowerCase() === 'cancelled'" [class.warn]="order.orderStatus.toLowerCase() === 'draft'">{{ order.orderStatus }}</span>
              <span class="mobile-order-card__customer">{{ order.customerName || 'Walk-in customer' }}</span>
              <span class="mobile-order-card__amount">{{ formatCurrencyValue(order.totalAmount) }}</span>
              <span class="mobile-order-card__meta">{{ order.sourceType }} · {{ order.paymentMethod }} · {{ formatDateValue(order.createdAt) }}</span>
            </button>
            <div class="mobile-order-card__footer" *ngIf="order.manualRefundAllowed || (order.refundAmount && order.refundAmount > 0)">
              <span *ngIf="order.refundAmount && order.refundAmount > 0" class="refunded-label">Refunded {{ formatCurrencyValue(order.refundAmount) }}</span>
              <button *ngIf="order.manualRefundAllowed" type="button" class="ghost-btn danger-btn" (click)="openManualRefund(order)">Manual Refund</button>
            </div>
          </article>
        </div>

        <div class="pagination-bar" *ngIf="serverTotalPages > 1">
          <p class="muted">Page {{ orderCurrentPage }} of {{ orderTotalPages }} ({{ serverTotalElements }} total orders)</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="orderCurrentPage === 1" (click)="goToOrderPage(orderCurrentPage - 1)">Previous</button>
            <button class="ghost-btn" [disabled]="orderCurrentPage === orderTotalPages" (click)="goToOrderPage(orderCurrentPage + 1)">Next</button>
          </div>
        </div>
      </div>
      <ng-template #posLoading>
        <div class="panel loading" *ngIf="!ordersLoaded; else ordersEmpty">
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4,5]; trackBy: trackByIndex"></div>
          </div>
        </div>
        <ng-template #ordersEmpty>
          <div class="receipt-empty-state" *ngIf="!ordersError">
            <div class="receipt-empty-halo">
              <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
            </div>
            <h3 class="receipt-empty-title">No Orders Match Current Filters</h3>
            <p class="receipt-empty-sub">Adjust your search query, status selector, or date window to inspect more records.</p>
            <button type="button" class="ghost-btn-tactile" (click)="clearOrderFilters()">Reset Filters</button>
          </div>
        </ng-template>
      </ng-template>
    </div>
  `,
  styles: [`
    .refunded-label { color: var(--kb-color-error); font-weight: 500; }
    .refund-meta { font-size: 0.75rem; color: var(--kb-color-primary); }
    .clickable-row { cursor: pointer; transition: background 0.15s; }
    .clickable-row:hover { background: var(--kb-color-surface-2); }
    .clickable-row:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: -2px; }

    .filter-group--full { grid-column: 1 / -1; }

    .date-range-active {
      display: inline-block;
      margin-left: 0.5rem;
      padding: var(--kb-space-1) var(--kb-space-2);
      background: var(--kb-color-surface);
      border-radius: var(--kb-radius-md);
      font-size: 0.8rem;
    }

    .field { margin: var(--kb-space-3) 0; display: flex; flex-direction: column; gap: var(--kb-space-1); }
    .field label { font-size: 0.85rem; font-weight: 600; color: var(--kb-color-foreground); }
    .field input {
      padding: var(--kb-space-2) var(--kb-space-3);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
      font-size: 0.95rem;
      outline: none;
      min-height: 44px;
      background: var(--kb-color-surface);
      color: var(--kb-color-foreground);
      transition: border-color 0.15s;
    }
    .field input:focus { border-color: var(--kb-color-primary); }
    .error-text { color: var(--kb-color-error); font-size: 0.85rem; margin: 0.5rem 0 0; }
    .hint-text { color: var(--kb-color-muted-foreground); font-size: 0.85rem; margin: 0.35rem 0 0; }
    .toolbar-actions { display: flex; gap: var(--kb-space-3); align-items: center; }
    .refund-review { display: grid; grid-template-columns: minmax(0, 111) auto; gap: var(--kb-space-3) var(--kb-space-2); margin: var(--kb-space-3) 0; padding: var(--kb-space-3); background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-lg); }
    .refund-review span { color: var(--kb-color-muted-foreground); }
    .refund-review strong { text-align: right; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
    .mobile-order-list { display: none; }
    @media (max-width: 767px) {
      .table-wrap > .data-table { display: none; }
      .mobile-order-list { display: grid; gap: var(--kb-space-3); padding: var(--kb-space-3); }
      .mobile-order-card { overflow: hidden; background: var(--kb-color-surface); border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-lg); box-shadow: var(--kb-shadow-xs); }
      .mobile-order-card--refunded { border-left: 4px solid var(--kb-color-error); }
      .mobile-order-card__main { width: 100%; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--kb-space-2) var(--kb-space-3); padding: var(--kb-space-3); text-align: left; color: var(--kb-color-foreground); background: transparent; border: 0; cursor: pointer; }
      .mobile-order-card__main:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: -2px; }
      .mobile-order-card__title { font-weight: 700; }
      .mobile-order-card__customer { color: var(--kb-color-muted-foreground); }
      .mobile-order-card__amount { font-weight: 700; text-align: right; font-variant-numeric: tabular-nums; }
      .mobile-order-card__meta { grid-column: 1 / -1; color: var(--kb-color-muted-foreground); font-size: 0.75rem; }
      .mobile-order-card__footer { display: flex; justify-content: space-between; align-items: center; gap: var(--kb-space-2); padding: var(--kb-space-2) var(--kb-space-3); border-top: 1px solid var(--kb-color-border); background: var(--kb-color-surface-2); }
    }

    /* Operational Orders Header (navbar.gallery style) */
    .operational-orders-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
      margin-bottom: 1.5rem;
      padding-bottom: 1.25rem;
      border-bottom: 1px solid #EEF0F7;
    }
    .header-title-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 0.25rem;
    }
    .header-title-row h2 {
      margin: 0;
      font-size: 1.45rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
    }
    .record-counter-pill {
      display: inline-block;
      padding: 0.2rem 0.65rem;
      border-radius: 999px;
      font-size: 0.74rem;
      font-weight: 700;
      background: #F1F5F9;
      color: #475569;
      border: 1px solid #E2E8F0;
      font-variant-numeric: tabular-nums;
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

    /* Bento KPI Grid (bentogrids.com standard) */
    .orders-bento-grid {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr;
      gap: 1rem;
      margin-bottom: 1.5rem;
    }
    @media (max-width: 900px) {
      .orders-bento-grid { grid-template-columns: 1fr; }
    }
    .bento-tile {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.25rem 1.4rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      transition: transform 140ms ease, box-shadow 140ms ease;
    }
    .bento-tile:hover {
      box-shadow: 0 6px 18px rgba(0, 0, 0, 0.05);
    }
    .bento-tile--hero {
      background: linear-gradient(135deg, #1E1B4B 0%, #312E81 100%);
      color: #FFFFFF;
      border: none;
      box-shadow: 0 8px 24px rgba(49, 46, 129, 0.25);
    }
    .bento-tile--hero .bento-tile__label {
      color: rgba(255, 255, 255, 0.75);
    }
    .bento-tile--hero .bento-tile__value {
      color: #FFFFFF;
    }
    .bento-tile__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .bento-tile__label {
      font-size: 0.8rem;
      font-weight: 600;
      color: #8F95B2;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .bento-pulse-tag {
      font-size: 0.7rem;
      font-weight: 700;
      color: #34D399;
      background: rgba(16, 185, 129, 0.15);
      padding: 0.15rem 0.45rem;
      border-radius: 999px;
    }
    .bento-tile__value {
      font-size: 1.65rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
    }
    .bento-split-bar {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-top: 0.25rem;
      font-size: 0.78rem;
      color: rgba(255, 255, 255, 0.75);
      font-variant-numeric: tabular-nums;
    }
    .split-dot {
      color: rgba(255, 255, 255, 0.4);
    }
    .bento-tile__sub {
      font-size: 0.78rem;
      color: #64748B;
      font-weight: 500;
    }
    .bento-tile--alert {
      border-color: #FECACA;
      background: #FFFBFB;
    }
    .bento-tile--alert .bento-tile__value {
      color: #DC2626;
    }

    /* Tactile CTA Buttons (cta.gallery & 60fps.design) */
    .primary-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.55rem 1rem;
      background: #5D45FD;
      color: #FFFFFF;
      border: none;
      border-radius: 10px;
      font-size: 0.84rem;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(93, 69, 253, 0.22);
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .primary-btn-tactile:hover {
      background: #4D37E6;
      box-shadow: 0 4px 12px rgba(93, 69, 253, 0.3);
    }
    .primary-btn-tactile:active {
      transform: scale(0.97);
    }

    .ghost-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.55rem 1rem;
      background: #FFFFFF;
      color: #334155;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      font-size: 0.84rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .ghost-btn-tactile:hover:not(:disabled) {
      background: #F8FAFC;
      border-color: #CBD5E1;
      color: #0F172A;
    }
    .ghost-btn-tactile:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .ghost-btn-tactile:active:not(:disabled) {
      transform: scale(0.97);
    }

    /* Thermal Receipt Empty State (404s.design) */
    .receipt-empty-state {
      background: #FFFFFF;
      border: 1.5px dashed #CBD5E1;
      border-radius: 20px;
      padding: 3.5rem 1.5rem;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.65rem;
      margin: 1.5rem 0;
    }
    .receipt-empty-halo {
      width: 60px;
      height: 60px;
      border-radius: 18px;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      color: #94A3B8;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
    }
    .receipt-empty-title {
      margin: 0;
      font-size: 1.1rem;
      font-weight: 700;
      color: #0F172A;
    }
    .receipt-empty-sub {
      margin: 0;
      font-size: 0.86rem;
      color: #64748B;
      max-width: 380px;
      line-height: 1.45;
    }
  `]
})
export class OrdersPageComponent implements OnDestroy {
  private readonly api = inject(BusinessApiService);
  private readonly toast = inject(ToastService);

  orders: BusinessOrder[] = [];
  ordersLoaded = false;
  ordersError = '';

  refundTarget: BusinessOrder | null = null;
  refundAmountInput: number | null = null;
  refundReasonInput = '';
  refunding = false;
  refundError: string | null = null;
  refundReview = false;
  private refundTrigger: HTMLElement | null = null;

  orderSearchTerm = '';
  orderStatusFilter = 'ALL';
  orderSourceFilter = 'ALL';
  orderPageSize = 10;
  orderCurrentPage = 1;

  // Server-side pagination state
  serverTotalElements = 0;
  serverTotalPages = 1;

  // Date range filter state
  dateFrom: string | null = null;
  dateTo: string | null = null;
  dateRangeLabel = '';

  // Order detail modal state
  selectedOrderDetail: OrderDetailResponse | null = null;
  orderDetailLoading = false;

  constructor() {
    this.loadOrders();
  }

  get businessOrderStatuses(): string[] {
    return [...new Set(this.orders.map((order) => order.orderStatus))].sort();
  }

  get businessOrderSources(): string[] {
    return [...new Set(this.orders.map((order) => order.sourceType))].sort();
  }

  get filteredOrders(): BusinessOrder[] {
    return filterBusinessOrders(this.orders, {
      searchTerm: this.orderSearchTerm,
      statusFilter: 'ALL',
      sourceFilter: this.orderSourceFilter,
      dateFrom: null,
      dateTo: null
    });
  }

  get pagedOrders(): BusinessOrder[] {
    return this.filteredOrders;
  }

  get filteredTotalRevenue(): number {
    return this.filteredOrders.reduce((acc, o) => acc + (o.totalAmount || 0), 0);
  }

  get filteredCompletedCount(): number {
    return this.filteredOrders.filter(o => (o.orderStatus || '').toLowerCase() === 'completed').length;
  }

  get filteredRefundedTotal(): number {
    return this.filteredOrders.reduce((acc, o) => acc + (o.refundAmount || 0), 0);
  }

  get refundedOrdersCount(): number {
    return this.filteredOrders.filter(o => o.refundAmount && o.refundAmount > 0).length;
  }

  get cashVolume(): number {
    return this.filteredOrders
      .filter(o => (o.paymentMethod || '').toLowerCase().includes('cash'))
      .reduce((acc, o) => acc + (o.totalAmount || 0), 0);
  }

  get digitalVolume(): number {
    return this.filteredOrders
      .filter(o => !(o.paymentMethod || '').toLowerCase().includes('cash'))
      .reduce((acc, o) => acc + (o.totalAmount || 0), 0);
  }

  get settlementRate(): number {
    if (!this.filteredOrders.length) return 0;
    return Math.round((this.filteredCompletedCount / this.filteredOrders.length) * 100);
  }

  get orderTotalPages(): number {
    return Math.max(1, this.serverTotalPages);
  }

  loadOrders(): void {
    this.ordersLoaded = false;
    this.ordersError = '';
    const serverStatus = this.orderStatusFilter !== 'ALL' ? this.orderStatusFilter : undefined;
    const from = this.dateFrom ?? undefined;
    const to = this.dateTo ?? undefined;
    this.api.getOrdersPaginated(this.orderCurrentPage - 1, this.orderPageSize, serverStatus, from, to).subscribe({
      next: (data: PaginatedOrdersResponse) => {
        this.orders = data.content;
        this.serverTotalElements = data.totalElements;
        this.serverTotalPages = data.totalPages;
        this.ordersLoaded = true;
      },
      error: (err) => {
        this.orders = [];
        this.serverTotalElements = 0;
        this.serverTotalPages = 1;
        // If 404 or empty response, treat as "no orders" (not a connection error)
        if (err?.status === 404 || err?.status === 0) {
          this.ordersError = err?.status === 0
            ? 'Unable to load orders. Check your connection and try again.'
            : '';
        } else {
          this.ordersError = 'Unable to load orders. Check your connection and try again.';
        }
        this.ordersLoaded = true;
      }
    });
  }

  resetOrderPage(): void {
    this.orderCurrentPage = 1;
    this.loadOrders();
  }

  clearOrderFilters(): void {
    this.orderSearchTerm = '';
    this.orderStatusFilter = 'ALL';
    this.orderSourceFilter = 'ALL';
    this.orderPageSize = 10;
    this.orderCurrentPage = 1;
    this.dateFrom = null;
    this.dateTo = null;
    this.dateRangeLabel = '';
    this.loadOrders();
  }

  goToOrderPage(page: number): void {
    this.orderCurrentPage = Math.min(Math.max(1, page), this.orderTotalPages);
    this.loadOrders();
  }

  // --- Date range filtering ---

  onDateRangeChange(range: { from: string; to: string }): void {
    this.dateFrom = range.from;
    this.dateTo = range.to;
    this.dateRangeLabel = `${range.from} → ${range.to}`;
    this.orderCurrentPage = 1;
    this.loadOrders();
  }

  // --- Cancel Order ---

  cancelOrder(order: BusinessOrder): void {
    const reason = prompt('Enter cancellation reason:');
    if (reason === null) return; // user clicked Cancel
    this.api.voidBill(order.orderId, reason || 'Cancelled by admin').subscribe({
      next: () => {
        this.toast.show('Order cancelled.', 'success');
        this.loadOrders();
      },
      error: (err: any) => {
        const msg = err?.error?.message || err?.error?.error || 'Failed to cancel order.';
        this.toast.show(msg, 'error');
      }
    });
  }

  // --- Order detail modal ---

  openOrderDetail(order: BusinessOrder): void {
    this.orderDetailLoading = true;
    this.api.getOrderDetail(order.orderId).subscribe({
      next: (detail) => {
        this.selectedOrderDetail = detail;
        this.orderDetailLoading = false;
      },
      error: () => {
        this.orderDetailLoading = false;
        this.toast.show('Unable to load order details.', 'error');
      }
    });
  }

  closeOrderDetail(): void {
    this.selectedOrderDetail = null;
  }

  // --- CSV Export ---

  exportCsv(): void {
    const orders = this.filteredOrders;
    if (orders.length === 0) return;

    const headers = [
      'Order Code', 'Source', 'Customer Name', 'Customer Contact',
      'Order Status', 'Payment Method', 'Payment Status',
      'Total Amount', 'Refund Amount', 'Created Date'
    ];

    const rows = orders.map(order => [
      order.orderCode,
      order.sourceType,
      order.customerName ?? '',
      order.customerContact ?? '',
      order.orderStatus,
      order.paymentMethod,
      order.paymentStatus,
      String(order.totalAmount ?? 0),
      String(order.refundAmount ?? 0),
      order.createdAt ? this.formatDateValue(order.createdAt) : ''
    ]);

    const csvContent = [headers, ...rows]
      .map(row => row.map(field => escapeCsvField(field)).join(','))
      .join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = this.getCsvFilename();
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  private getCsvFilename(): string {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    return `orders_${yyyy}-${mm}-${dd}.csv`;
  }

  // --- Refund ---

  openManualRefund(order: BusinessOrder): void {
    this.refundTrigger = document.activeElement as HTMLElement | null;
    document.body.style.overflow = 'hidden';
    this.refundTarget = order;
    this.refundAmountInput = order.totalAmount;
    this.refundReasonInput = '';
    this.refundError = null;
    this.refundReview = false;
  }

  closeRefund(): void {
    if (this.refunding) return;
    this.refundTarget = null;
    this.refundError = null;
    this.refundReview = false;
    document.body.style.overflow = '';
    this.refundTrigger?.focus();
    this.refundTrigger = null;
  }

  @HostListener('document:keydown.escape')
  handleEscape(): void {
    if (this.refundTarget && !this.refunding) this.closeRefund();
  }

  ngOnDestroy(): void {
    // Release the refund dialog's body scroll-lock when navigating away mid-dialog.
    document.body.style.overflow = '';
  }

  confirmRefund(): void {
    if (!this.refundTarget || !this.refundAmountInput) return;
    if (this.refundAmountInput <= 0 || this.refundAmountInput > this.refundTarget.totalAmount) return;
    this.refunding = true;
    this.refundError = null;

    this.api.manualRefundOrder(this.refundTarget.orderId, {
      refundAmount: this.refundAmountInput,
      reason: this.refundReasonInput.trim() || 'Refund handled manually'
    }).subscribe({
      next: (updated) => {
        const idx = this.orders.findIndex((o) => o.orderId === updated.orderId);
        if (idx !== -1) this.orders[idx] = updated;
        this.refunding = false;
        this.closeRefund();
      },
      error: (err) => {
        this.refundError = err?.error?.error || 'Refund failed. Please try again.';
        this.refunding = false;
      }
    });
  }

  reviewRefund(): void {
    if (!this.refundAmountInput || this.refundAmountInput <= 0) return;
    if (!this.refundTarget || this.refundAmountInput > this.refundTarget.totalAmount) return;
    this.refundError = null;
    this.refundReview = true;
  }

  returnToRefundForm(): void {
    if (this.refunding) return;
    this.refundReview = false;
    this.refundError = null;
  }

  formatCurrencyValue(value: number | null): string { return formatCurrency(value ?? 0); }
  formatDateValue(value: number | null): string { return formatDate(value); }

  trackByIndex = (_: number, __: unknown) => _;
  trackByOrderId = (_: number, order: BusinessOrder) => order.orderId;
}
