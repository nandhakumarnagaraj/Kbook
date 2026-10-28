import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, HostListener, Input, OnDestroy, Output, inject, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged, from, of } from 'rxjs';
import { concatMap, finalize, reduce, switchMap } from 'rxjs/operators';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder, OrderDetailResponse, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { KpiItem } from '../../shared/kpi-row.component';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';
import { FocusTrapDirective } from '../../core/components/focus-trap.directive';
import { buildInvoiceLink } from '../../core/utils/invoice-link.util';
import { readStoredDateRange, storeDateRange } from '../../core/utils/date-range-storage.util';

export function escapeCsvField(value: string): string {
  const spreadsheetSafe = /^[=+\-@]/.test(value) ? `'${value}` : value;
  if (spreadsheetSafe.includes(',') || spreadsheetSafe.includes('"')
      || spreadsheetSafe.includes('\n') || spreadsheetSafe.includes('\r')) {
    return `"${spreadsheetSafe.replace(/"/g, '""')}"`;
  }
  return spreadsheetSafe;
}

export function formatIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * The table prints the date and the time on separate lines. Both are fixed to
 * the restaurant's timezone, not the viewer's — a bill rung up at 11:50 pm IST
 * must not read as the next day because the owner opened the page from London.
 * Built once at module scope rather than per cell: a 10-row table would
 * otherwise construct 20 formatters on every change-detection pass.
 */
const ORDER_DATE_FORMAT = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  timeZone: 'Asia/Kolkata'
});

const ORDER_TIME_FORMAT = new Intl.DateTimeFormat('en-IN', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
  timeZone: 'Asia/Kolkata'
});

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
      order.orderCode ?? '',
      order.customerName ?? '',
      order.customerContact ?? '',
      order.paymentMethod ?? '',
      order.paymentStatus ?? ''
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

/**
 * What the history table prints in its Status column. The API sends the stored value
 * title-cased ("Draft"), which is accounting jargon the counter never reads: the open-bill
 * state is the same state the POS labels "Preparing", so both surfaces say the same word.
 * Anything else is passed through unchanged (underscores spaced out), so a status this
 * table has never seen still renders as something rather than an empty chip.
 */
export function orderStatusLabel(status: string | null | undefined): string {
  const value = (status || '').trim();
  if (!value) return '';
  const lower = value.toLowerCase();
  if (lower === 'draft' || lower === 'preparing') return 'Preparing';
  return value.replace('_', ' ');
}

@Component({
  selector: 'app-order-history-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, OrderDetailModalComponent, ApiStateComponent, PaginationBarComponent, FocusTrapDirective],
  template: `
    <app-order-detail-modal
      *ngIf="selectedOrderDetail"
      [order]="selectedOrderDetail"
      (billUpdated)="loadOrders()"
      (closed)="closeOrderDetail()">
    </app-order-detail-modal>

    <div class="tab-panel">
      <div class="modal-backdrop" *ngIf="refundTarget" appFocusTrap (escape)="closeRefund()" (click)="closeRefund()">
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

      <div class="panel loading" *ngIf="orderDetailLoading" role="status" aria-live="polite">
        Loading order details...
      </div>

      <!-- Order Cancellation Modal (Accessible Design-System Dialog) -->
      <div class="modal-backdrop" *ngIf="orderToCancel" appFocusTrap (escape)="closeCancelModal()" (click)="closeCancelModal()" role="dialog" aria-modal="true" aria-labelledby="cancel-modal-title">
        <section class="modal-box" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <div>
              <span class="chip danger">Void / Cancel Order</span>
              <h3 class="cancel-modal-title" id="cancel-modal-title">Cancel Order #{{ orderToCancel.orderCode }}</h3>
              <p class="muted cancel-modal-note">
                Amount: {{ formatCurrencyValue(orderToCancel.totalAmount) }} · {{ orderToCancel.customerName || 'Walk-in customer' }}
              </p>
            </div>
            <button type="button" class="icon-btn" (click)="closeCancelModal()" [disabled]="cancelSubmitting" aria-label="Close dialog">✕</button>
          </div>

          <div class="modal-body cancel-modal-body">
            <div class="field">
              <label for="cancel-preset">Cancellation Reason</label>
              <select id="cancel-preset" class="field-select" [(ngModel)]="cancelReasonPreset" [disabled]="cancelSubmitting">
                <option value="Customer requested cancellation">Customer requested cancellation</option>
                <option value="Kitchen unable to fulfill / Item out of stock">Kitchen unable to fulfill / Item out of stock</option>
                <option value="Duplicate order entry">Duplicate order entry</option>
                <option value="Entered by mistake / Billing error">Entered by mistake / Billing error</option>
                <option value="Other reason">Other reason</option>
              </select>
            </div>

            <div class="field">
              <label for="cancel-custom-note">Additional Note (Optional)</label>
              <input
                id="cancel-custom-note"
                type="text"
                class="field-control"
                [(ngModel)]="cancelReasonCustom"
                [disabled]="cancelSubmitting"
                placeholder="Optional explanation or reference note..."
              />
            </div>
          </div>

          <div class="modal-actions">
            <button type="button" class="ghost-btn" (click)="closeCancelModal()" [disabled]="cancelSubmitting">Keep Order</button>
            <button type="button" class="primary-btn danger-btn" (click)="confirmCancelOrder()" [disabled]="cancelSubmitting">
              {{ cancelSubmitting ? 'Cancelling...' : 'Confirm Cancellation' }}
            </button>
          </div>
        </section>
      </div>

      <app-api-state
        *ngIf="ordersError"
        [loading]="false"
        [error]="ordersError"
        (retry)="loadOrders()"
      ></app-api-state>

      <!-- Order History KPI cards + Filters panel -->
      <div class="history-kpi-and-filters" *ngIf="!ordersError" aria-label="Order history KPI and filters">
        <!-- KPI Cards above filters -->
        <div class="kpi-row history-kpi-row"
          *ngIf="serverTotalElements && serverTotalElements > 0"
          aria-label="Order history metrics"
        >
          <div class="kpi-card history-kpi-card"
            *ngFor="let item of historyKpiItems()"
            [class.kpi-card--default]="item.tone === 'default'"
            [class.kpi-card--success]="item.tone === 'success'"
            [class.kpi-card--danger]="item.tone === 'danger'"
            [attr.aria-label]="item.label + ': ' + item.value"
          >
            <div class="kpi-card__label" [class.kpi-card__label--secondary]="true">{{ item.label }}</div>
            <div class="kpi-card__value" [class.kpi-card__value--tone]="item.tone"
              [class.kpi-card__value--accent-default]="item.tone === 'default'"
              [class.kpi-card__value--accent-success]="item.tone === 'success'"
              [class.kpi-card__value--accent-danger]="item.tone === 'danger'"
            >{{ item.value }}</div>
            <div class="kpi-card__unit" *ngIf="item.unit">{{ item.unit }}</div>
          </div>
        </div>

        <!-- Filter panel with search, clear, and combined filters -->
        <div class="filter-panel"
          *ngIf="serverTotalElements && serverTotalElements > 0"
          aria-label="Order history filters"
        >
          <div class="filter-row"
            *ngIf="serverTotalElements && serverTotalElements > 0"
          >
            <div class="filter-item"
              *ngIf="serverTotalElements && serverTotalElements > 0"
            >
              <input
                type="text"
                class="field-control"
                placeholder="Search order #, items, guest..."
                [(ngModel)]="orderSearchTerm"
                (ngModelChange)="onSearchChange($event)"
                aria-label="Search order history"
              />
            </div>
            <div class="filter-item"
              *ngIf="serverTotalElements && serverTotalElements > 0"
            >
              <button
                type="button"
                class="btn-clear"
                (click)="clearFilters()"
                aria-label="Clear all filters"
                *ngIf="orderSearchTerm || orderSourceFilter || orderStatusFilter || orderPaymentFilter"
              >Clear</button>
            </div>
          </div>

          <div class="filter-row"
            *ngIf="serverTotalElements && serverTotalElements > 0"
          >
            <div class="filter-item"
              *ngIf="serverTotalElements && serverTotalElements > 0"
            >
              <select
                class="field-select"
                [(ngModel)]="orderSourceFilter"
                (ngModelChange)="setSourceFilter($event)"
                aria-label="Filter by source"
              >
                <option value="ALL">All</option>
                <option value="DINE_IN">Dine-in</option>
                <option value="TAKEAWAY">Takeaway</option>
                <option value="DELIVERY">Delivery</option>
              </select>
            </div>
            <div class="filter-item"
              *ngIf="serverTotalElements && serverTotalElements > 0"
            >
              <select
                class="field-select"
                [(ngModel)]="orderStatusFilter"
                (ngModelChange)="onStatusFilterChange($event)"
                aria-label="Filter by status"
              >
                <option value="ALL">All</option>
                <option value="Completed">Completed</option>
                <option value="Cancelled">Cancelled</option>
                <option value="Draft">Draft</option>
                <option value="Pending">Pending</option>
              </select>
            </div>
            <div class="filter-item"
              *ngIf="serverTotalElements && serverTotalElements > 0"
            >
              <select
                class="field-select"
                [(ngModel)]="orderPaymentFilter"
                aria-label="Filter by payment"
              >
                <option value="ALL">All</option>
                <option value="Cash">Cash</option>
                <option value="UPI">UPI</option>
                <option value="Card">Card</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      <!-- ── Order History table ── -->
      <ng-container *ngIf="!ordersError" aria-label="Order history">
        <!-- Order History table. The table, its header and the pagination bar are
             one .panel, so the page shows one bordered surface rather than a bare
             table followed by a detached page strip. -->
        <section class="panel history-card" *ngIf="ordersLoaded && pagedOrders.length > 0; else emptyOrLoadingOrders">
          <div class="table-wrap">
            <table class="data-table">
              <caption class="visually-hidden">Orders in the selected period, with date, channel, guest, total, payment, status and actions</caption>
              <thead>
                <tr>
                  <th scope="col">Order</th>
                  <th scope="col">Date &amp; time</th>
                  <th scope="col">Type &amp; table</th>
                  <th scope="col">Guest</th>
                  <th scope="col" class="text-right">Total</th>
                  <th scope="col">Status &amp; payment</th>
                  <th scope="col" class="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  *ngFor="let order of pagedOrders; trackBy: trackByOrderId"
                  class="clickable-row"
                  tabindex="0"
                  [attr.aria-label]="'View order #' + order.orderCode"
                  (click)="openOrderDetail(order)"
                  (keydown.enter)="openOrderDetail(order)">
                  <th scope="row">
                    <span class="order-hash-code">#{{ order.orderCode }}</span>
                    <div *ngIf="order.invoiceNumber && order.invoiceNumber !== order.orderCode" class="invoice-sub-badge font-mono">
                      {{ order.invoiceNumber }}
                    </div>
                  </th>
                  <td>
                    <span class="order-date">{{ formatOrderDate(order.createdAt) }}</span>
                    <span class="order-time">{{ formatOrderTime(order.createdAt) }}</span>
                  </td>
                  <td>
                    <span class="channel-chip channel-chip--{{ channelClass(order) }}">
                      {{ formatOrderType(order) }}
                    </span>
                    <span class="table-ref" *ngIf="order.tableNumber">Table {{ order.tableNumber }}</span>
                  </td>
                  <td>
                    <div class="order-guest-desc">
                      <span class="guest-title">{{ order.customerName || (isTakeaway(order) ? 'Takeaway order' : 'Walk-in guest') }}</span>
                      <span class="guest-meta" *ngIf="order.customerContact">{{ order.customerContact }}</span>
                    </div>
                  </td>
                  <td class="num-cell">
                    <strong class="order-table-amount">{{ formatCurrencyValue(order.totalAmount) }}</strong>
                    <div *ngIf="order.refundAmount && order.refundAmount > 0" class="refund-sub-badge">
                      -{{ formatCurrencyValue(order.refundAmount) }}
                    </div>
                  </td>
                  <td>
                    <span class="status-chip" [ngClass]="getOrderStatusClass(order.orderStatus)">
                      {{ orderStatusLabel(order.orderStatus) }}
                    </span>
                    <span class="payment-line" [class.payment-line--pending]="isPaymentPending(order)">
                      {{ order.paymentMethod || 'Cash' }} · {{ order.paymentStatus || 'Unpaid' }}
                    </span>
                  </td>
                  <td class="text-right" (click)="$event.stopPropagation()">
                    <div class="action-btn-group">
                      <button
                        type="button"
                        class="btn-micro btn-micro--primary"
                        (click)="openOrderDetail(order)"
                        aria-label="View order details">
                        View
                      </button>
                      <button
                        *ngIf="order.manualRefundAllowed"
                        type="button"
                        class="btn-micro btn-micro--refund"
                        (click)="openManualRefund(order)"
                        title="Issue refund"
                        aria-label="Refund order">
                        Refund
                      </button>
                      <button
                        type="button"
                        class="btn-micro"
                        (click)="shareInvoice(order)"
                        title="Share invoice"
                        aria-label="Share invoice">
                        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.7 10.7 6.6-4.4m-6.6 7 6.6 4.4"/></svg>
                        Share
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <app-pagination-bar
            *ngIf="serverTotalElements > 0"
            [currentPage]="orderCurrentPage"
            [pageSize]="orderPageSize"
            [totalItems]="serverTotalElements"
            (pageChange)="goToOrderPage($event)"
          />
        </section>

        <ng-template #emptyOrLoadingOrders>
          <div class="panel loading" *ngIf="!ordersLoaded; else emptyOrdersTable" role="status" aria-busy="true">
            <span class="visually-hidden">Loading recent orders...</span>
            <div class="skeleton-stack">
              <!-- Order row anatomy: code, channel, guest line, amount, status,
                   action. No order code or guest name is painted before it loads. -->
              <div class="skeleton-row skeleton-row--detail" *ngFor="let i of [1,2,3,4,5]; trackBy: trackByIndex">
                <span class="skeleton skeleton-line skeleton-line--name"></span>
                <span class="skeleton skeleton-line skeleton-line--chip"></span>
                <span class="skeleton skeleton-line skeleton-line--grow"></span>
                <span class="skeleton skeleton-line skeleton-line--meta"></span>
                <span class="skeleton skeleton-line skeleton-line--action"></span>
              </div>
            </div>
          </div>
          <ng-template #emptyOrdersTable>
            <div class="empty-orders-view">
              <div class="empty-icon-circle">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
                  <rect x="9" y="3" width="6" height="4" rx="1"/>
                  <path d="m9 14 2 2 4-4"/>
                </svg>
              </div>
              <h4>No orders in this period</h4>
              <p>No orders match the selected date range and search. Try a wider range, or clear the filters to see the full history.</p>
              <div class="empty-actions-row">
                <button type="button" class="header-action-btn" (click)="clearOrderFilters()">Clear Filters</button>
              </div>
            </div>
          </ng-template>
        </ng-template>

      </ng-container>
    </div>
  `,
  styles: [`
    .refunded-label { color: var(--kb-color-error); font-weight: 500; }
    .refund-meta { font-size: var(--kb-text-xs); color: var(--kb-color-link); }

    .filter-group--full { grid-column: 1 / -1; }

    .date-range-active {
      display: inline-block;
      margin-left: 0.5rem;
      padding: var(--kb-space-1) var(--kb-space-2);
      background: var(--kb-color-surface);
      border-radius: var(--kb-radius-md);
      font-size: var(--kb-text-sm);
    }

    .field { margin: var(--kb-space-3) 0; display: flex; flex-direction: column; gap: var(--kb-space-1); }
    .field label { font-size: var(--kb-text-md); font-weight: 600; color: var(--kb-color-foreground); }
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
    .error-text { color: var(--kb-color-error); font-size: var(--kb-text-md); margin: var(--kb-space-2) 0 0; }
    .hint-text { color: var(--kb-color-muted-foreground); font-size: var(--kb-text-md); margin: 0.35rem 0 0; }
    .toolbar-actions { display: flex; gap: var(--kb-space-3); align-items: center; }
    .refund-review { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--kb-space-3) var(--kb-space-2); margin: var(--kb-space-3) 0; padding: var(--kb-space-3); background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-lg); }
    .refund-review span { color: var(--kb-color-muted-foreground); }
    .refund-review strong { text-align: right; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
    .mobile-order-list { display: none; }
    /* The card list these styles describe was never written into the template — only the
       CSS was. So this block must not hide the table: at phone width it left Order History
       blank, and the phone layout spec still passed because a hidden table cannot overflow.
       Until the markup lands, the shared rule in styles.css
       (.table-wrap:not(:has(.mobile-data-list))) scrolls the table instead, exactly as it
       does for the other tables that have no card fallback. Add the markup first, then add
       .mobile-order-list { display: grid } and .table-wrap > .data-table { display: none }
       back here. */
    @media (max-width: 768px) {
      .mobile-order-card { overflow: hidden; background: var(--kb-color-surface); border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-card); box-shadow: var(--kb-shadow-xs); }
      .mobile-order-card--refunded { border-left: 4px solid var(--kb-color-error); }
      .mobile-order-card__main { width: 100%; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--kb-space-2) var(--kb-space-3); padding: var(--kb-space-3); text-align: left; color: var(--kb-color-foreground); background: transparent; border: 0; cursor: pointer; }
      .mobile-order-card__main:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: -2px; }
      .mobile-order-card__title { font-weight: 700; }
      .mobile-order-card__customer { color: var(--kb-color-muted-foreground); }
      .mobile-order-card__amount { font-weight: 700; text-align: right; font-variant-numeric: tabular-nums; }
      .mobile-order-card__meta { grid-column: 1 / -1; color: var(--kb-color-muted-foreground); font-size: var(--kb-text-xs); }
      .mobile-order-card__footer { display: flex; justify-content: space-between; align-items: center; gap: var(--kb-space-2); padding: var(--kb-space-2) var(--kb-space-3); border-top: 1px solid var(--kb-color-border); background: var(--kb-color-surface-2); }
    }

    /* Operational Orders Header (navbar.gallery style) */
    /* Tab body — the routed shell owns the page header and outer geometry. */
    .tab-panel {
      display: grid;
      gap: var(--kb-space-6);
    }
    .tab-toolbar {
      display: flex;
      justify-content: flex-end;
      gap: 0.65rem;
      flex-wrap: wrap;
    }
    .tab-toolbar__actions {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      flex-wrap: wrap;
    }

    .preset-pill-group {
      display: inline-flex;
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl);
      padding: 3px;
      gap: 3px;
      box-shadow: 0 1px 3px rgba(28, 26, 23, 0.03);
    }
    .preset-pill {
      padding: 0.45rem 0.85rem;
      border: none;
      background: transparent;
      border-radius: 9px;
      font-size: var(--kb-text-sm);
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      cursor: pointer;
      transition: all 150ms ease;
    }
    .preset-pill:hover { color: var(--kb-color-foreground); background: var(--kb-color-bg-app); }
    .preset-pill--active {
      background: var(--kb-color-primary) !important;
      color: var(--kb-color-surface) !important;
    }
    .preset-pill--outline {
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
    }
    .custom-date-picker-drawer {
      margin-bottom: 1.25rem;
      padding: var(--kb-space-4) 1.15rem;
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-card);
    }
    /* Tactile CTA Buttons (cta.gallery & 60fps.design) */
    
    
    

    
    
    
    


    /* The table chrome, channel chips and status badges are defined once in the
       global stylesheets. This block owns only the history table's layout and
       its two-line cells; the copies that used to live here had already drifted
       from the shared ones. */
    /* The card clips the table's corners; .panel already removes the inner
       .table-wrap frame, so one border wraps the whole surface. */
    .history-card { overflow: hidden; }

    /* Alignment is layout, not chrome: the total and the actions sit right (from
       the shared .num-cell / .text-right helpers) and the status column stays
       left so its badge and payment line start together. */
    .num-cell { font-variant-numeric: tabular-nums; }

    .invoice-sub-badge {
      font-size: var(--kb-text-2xs);
      color: var(--kb-color-muted-foreground);
      margin-top: 2px;
    }

    .order-date {
      display: block;
      font-size: var(--kb-text-sm);
      font-weight: 600;
      color: var(--kb-color-foreground);
      white-space: nowrap;
    }
    .order-time {
      display: block;
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }
    .table-ref {
      display: block;
      margin-top: 2px;
      font-size: var(--kb-text-xs);
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
    }
    /* Payment travels with the order status: two short lines in one column read
       faster than an eighth column of mostly-repeated method names. */
    .payment-line {
      display: block;
      margin-top: 4px;
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
    }
    .payment-line--pending {
      color: var(--kb-color-warning);
      font-weight: 600;
    }
    .refund-sub-badge {
      font-size: var(--kb-text-2xs);
      color: var(--kb-color-danger);
      font-weight: 600;
      margin-top: 0.1rem;
    }
    /* .empty-orders-view / .empty-icon-circle come from bento.css; only the
       spacing this tab needs inside its card is local. */
    .tab-panel .empty-orders-view { margin: var(--kb-space-4) 0; }
    .empty-actions-row {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      margin-top: 0.75rem;
    }
    .btn-micro--refund,
    .btn-micro--refund:hover {
      color: var(--kb-color-foreground-contrast);
      border-color: var(--kb-color-danger);
      background: var(--kb-color-danger);
    }
    .btn-micro--refund:hover { filter: brightness(0.92); }

    /* Cancel dialog: the same three carriers exist in the live-orders tab. */
    .cancel-modal-title { margin: 0.35rem 0 0; }
    .cancel-modal-note { margin: var(--kb-space-1) 0 0; font-size: var(--kb-text-md); }
    .cancel-modal-body { display: grid; gap: var(--kb-space-4); padding: var(--kb-space-4) 0; }
  `]
})
export class OrderHistoryTabComponent implements OnDestroy {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  orders: BusinessOrder[] = [];
  ordersLoaded = false;
  ordersError = '';
  private readonly cdr = inject(ChangeDetectorRef);

  refundTarget: BusinessOrder | null = null;
  refundAmountInput: number | null = null;
  refundReasonInput = '';
  refunding = false;
  refundError: string | null = null;
  refundReview = false;
  private refundTrigger: HTMLElement | null = null;

  orderSearchTerm = '';
  orderSourceFilter = 'ALL';
  orderStatusFilter = 'ALL';
  orderPaymentFilter = 'ALL';

  /**
   * Search text owned by the parent page (the input lives in the page header
   * next to the date picker). Two-way bound: typing flows in, Clear Filters
   * flows back out so the header input clears too.
   */
  @Input() set searchQuery(value: string) {
    const next = value ?? '';
    if (next !== this.orderSearchTerm) {
      this.onSearchChange(next);
    }
  }
  @Output() searchQueryChange = new EventEmitter<string>();
  orderPageSize = 10;
  orderCurrentPage = 1;
  exportingCsv = false;

  /** The period the history table is currently filtered by. */
  get currentDateRange(): { from: string; to: string } {
    const today = formatIsoDate(new Date());
    return {
      from: this.dateFrom || today,
      to: this.dateTo || today
    };
  }

  // Server-side pagination state
  serverTotalElements = 0;
  serverTotalPages = 1;

  // Date range filter state
  dateFrom: string | null = null;
  dateTo: string | null = null;

  // Order detail modal state
  selectedOrderDetail: OrderDetailResponse | null = null;
  orderDetailLoading = false;

  // Cancel order modal state
  orderToCancel: BusinessOrder | null = null;
  cancelReasonPreset = 'Customer requested cancellation';
  cancelReasonCustom = '';
  cancelSubmitting = false;

  private readonly searchSubject = new Subject<string>();
  private searchSub?: Subscription;

  constructor() {
    this.searchSub = this.searchSubject.pipe(
      debounceTime(350),
      distinctUntilChanged()
    ).subscribe(() => {
      this.orderCurrentPage = 1;
      this.loadOrders();
    });
    // This tab is destroyed and rebuilt every time it is opened, so it must open on the
    // shared period rather than snapping back to Today — that reset is what made a
    // custom day picked on Reports or Daily Closing look "lost" here. Falls back to
    // Today when nothing has been picked yet, so the table is never left unfiltered.
    const initial = readStoredDateRange() ?? this.currentDateRange;
    this.dateFrom = initial.from;
    this.dateTo = initial.to;
    this.loadOrders();
  }

  onSearchChange(value: string): void {
    this.orderSearchTerm = value ?? '';
    this.searchQueryChange.emit(this.orderSearchTerm);
    this.searchSubject.next(this.orderSearchTerm);
  }

  onStatusFilterChange(event: any): void {
    this.orderStatusFilter = event.target ? event.target.value : event;
    this.loadOrders();
    this.cdr.markForCheck();
  }

  readonly historyKpiItems = computed(() => {
    const total = this.serverTotalElements ?? 0;
    const completed = this.filteredCompletedCount ?? 0;
    const cancelled = this.filteredOrders.filter(o => (o.orderStatus || '').toLowerCase() === 'cancelled').length ?? 0;
    const sales = this.filteredTotalRevenue ?? 0;
    return [
      {
        label: 'Total Orders',
        value: total.toString(),
        tone: 'default',
      },
      {
        label: 'Total Sales',
        value: `₹${sales.toLocaleString()}`,
        tone: 'default',
      },
      {
        label: 'Completed Orders',
        value: completed.toString(),
        tone: 'success',
      },
      {
        label: 'Cancelled Orders',
        value: cancelled.toString(),
        tone: 'danger',
      },
    ] as KpiItem[];
  });

  isDineIn(order: BusinessOrder): boolean {
    const t = (order.orderType || order.sourceType || '').toLowerCase();
    return t.includes('dine') || (!t.includes('takeaway') && !t.includes('delivery') && !t.includes('parcel'));
  }

  isTakeaway(order: BusinessOrder): boolean {
    const t = (order.orderType || order.sourceType || '').toLowerCase();
    return t.includes('takeaway') || t.includes('parcel');
  }

  isDelivery(order: BusinessOrder): boolean {
    const t = (order.orderType || order.sourceType || '').toLowerCase();
    return t.includes('delivery');
  }

  formatOrderType(order: BusinessOrder): string {
    if (this.isTakeaway(order)) return 'Takeaway';
    if (this.isDelivery(order)) return 'Delivery';
    return 'Dine-In';
  }

  setSourceFilter(channel: 'ALL' | 'DINE_IN' | 'TAKEAWAY'): void {
    this.orderSourceFilter = channel;
  }

  /**
   * The Status column's wording. Exposed on the component so the template can call it; the
   * property spec imports the same standalone function rather than a copy, so the word the
   * table prints and the word the test pins cannot drift apart.
   */
  readonly orderStatusLabel = orderStatusLabel;

  getOrderStatusClass(status: string): string {
    const s = (status || '').toLowerCase();
    switch (s) {
      case 'preparing':
      case 'draft':
        return 'badge-preparing';
      case 'cooking':
        return 'badge-cooking';
      case 'served':
        return 'badge-served';
      case 'completed':
      case 'paid':
        return 'badge-completed';
      case 'cancelled':
      case 'voided':
        return 'badge-cancelled';
      default:
        return 'badge-preparing';
    }
  }

  getInvoiceUrl(order: BusinessOrder): string {
    const session = this.auth.session();
    return buildInvoiceLink(session?.restaurantId, order.orderId, order.publicToken);
  }

  async shareInvoice(order: BusinessOrder): Promise<void> {
    const url = this.getInvoiceUrl(order);
    const shareData = {
      title: `KhanaBook invoice ${order.invoiceNumber || order.orderCode}`,
      text: `Invoice for order ${order.orderCode}`,
      url
    };

    try {
      if (navigator.share) {
        await navigator.share(shareData);
      } else if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(url);
        this.toast.show('Invoice link copied to clipboard.', 'success');
      } else {
        this.toast.show('Sharing is not available in this browser.', 'error');
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      this.toast.show('Unable to share the invoice link.', 'error');
    }
  }

  get filteredOrders(): BusinessOrder[] {
    if (this.orderSourceFilter !== 'ALL') {
      return this.orders.filter(order => {
        if (this.orderSourceFilter === 'DINE_IN') return this.isDineIn(order);
        if (this.orderSourceFilter === 'TAKEAWAY') return this.isTakeaway(order);
        return true;
      });
    }
    return this.orders;
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

  setPreset(preset: 'today' | 'yesterday'): void {
    const now = new Date();
    const day = preset === 'yesterday'
      ? formatIsoDate(new Date(now.getTime() - 24 * 60 * 60 * 1000))
      : formatIsoDate(now);

    this.dateFrom = day;
    this.dateTo = day;
    this.orderCurrentPage = 1;
    storeDateRange({ from: day, to: day });
    this.loadOrders();
  }

  exportCsv(): void {
    if (this.exportingCsv || !this.ordersLoaded) return;
    this.exportingCsv = true;
    this.cdr.markForCheck();
    const pageSize = 100;
    const fromDate = this.dateFrom ?? undefined;
    const toDate = this.dateTo ?? undefined;
    const search = this.orderSearchTerm.trim() || undefined;

    this.api.getOrdersPaginated(0, pageSize, undefined, fromDate, toDate, search).pipe(
      switchMap(firstPage => {
        if (firstPage.totalPages <= 1) return of(firstPage.content);
        const remainingPages = Array.from({ length: firstPage.totalPages - 1 }, (_, index) => index + 1);
        return from(remainingPages).pipe(
          concatMap(page => this.api.getOrdersPaginated(page, pageSize, undefined, fromDate, toDate, search)),
          reduce((all, nextPage) => all.concat(nextPage.content), [...firstPage.content])
        );
      }),
      finalize(() => {
        this.exportingCsv = false;
        this.cdr.markForCheck();
      })
    ).subscribe({
      next: orders => this.downloadOrdersCsv(orders),
      error: () => this.toast.show('Could not download order history. Please try again.', 'error')
    });
  }

  private downloadOrdersCsv(orders: BusinessOrder[]): void {
    const matchingOrders = orders.filter(order => {
      if (this.orderSourceFilter === 'DINE_IN') return this.isDineIn(order);
      if (this.orderSourceFilter === 'TAKEAWAY') return this.isTakeaway(order);
      return true;
    });
    if (!matchingOrders.length) {
      this.toast.show('There are no orders to export for these filters.', 'info');
      return;
    }
    const headers = [
      'Order Code', 'Source', 'Customer Name', 'Customer Contact',
      'Order Status', 'Payment Method', 'Payment Status',
      'Total Amount', 'Refund Amount', 'Created Date'
    ];
    const rows = matchingOrders.map(order => [
      order.orderCode,
      order.sourceType,
      order.customerName ?? '',
      order.customerContact ?? '',
      order.orderStatus,
      order.paymentMethod,
      order.paymentStatus,
      String(order.totalAmount ?? 0),
      String(order.refundAmount ?? 0),
      order.createdAt ? formatDate(order.createdAt) : ''
    ]);
    const csv = [headers, ...rows]
      .map(row => row.map(field => escapeCsvField(String(field ?? ''))).join(','))
      .join('\n');
    const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `order-history-${this.dateFrom ?? 'all'}-to-${this.dateTo ?? 'all'}.csv`;
    anchor.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  loadOrders(): void {
    this.ordersLoaded = false;
    this.ordersError = '';
    // The reload is triggered from outside this OnPush component — the page
    // toolbar's Refresh button and its date range both call in here. Without
    // this notification the "loading" transition never reaches the renderer, so
    // the stale table stays on screen and the skeletons meant to replace it
    // never appear: pressing Refresh looked like it did nothing at all until
    // the response landed.
    this.cdr.markForCheck();
    const from = this.dateFrom ?? undefined;
    const to = this.dateTo ?? undefined;
    const search = this.orderSearchTerm?.trim() || undefined;
    this.api.getOrdersPaginated(this.orderCurrentPage - 1, this.orderPageSize, undefined, from, to, search).subscribe({
      next: (data: PaginatedOrdersResponse) => {
        this.orders = data.content;
        this.serverTotalElements = data.totalElements;
        this.serverTotalPages = data.totalPages;
        this.ordersLoaded = true;
        this.ordersError = '';
        this.cdr.markForCheck();
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
        this.cdr.markForCheck();
      }
    });
  }

  goToOrderPage(page: number): void {
    const total = this.orderTotalPages;
    const target = Math.min(Math.max(1, page), total);
    if (target === this.orderCurrentPage) return;
    this.orderCurrentPage = target;
    this.loadOrders();
  }

  resetOrderPage(): void {
    this.orderCurrentPage = 1;
    this.loadOrders();
  }

  clearOrderFilters(): void {
    this.orderSearchTerm = '';
    this.searchQueryChange.emit('');
    this.orderSourceFilter = 'ALL';
    this.orderPageSize = 10;
    this.orderCurrentPage = 1;
    this.setPreset('today');
  }

  // --- Date range filtering ---

  onDateRangeChange(range: { from: string; to: string }): void {
    this.dateFrom = range.from;
    this.dateTo = range.to;
    this.orderCurrentPage = 1;
    storeDateRange(range);
    this.loadOrders();
  }

  // --- Cancel Order Modal Handlers ---

  openCancelModal(order: BusinessOrder): void {
    this.orderToCancel = order;
    this.cancelReasonPreset = 'Customer requested cancellation';
    this.cancelReasonCustom = '';
    this.cancelSubmitting = false;
  }

  closeCancelModal(): void {
    if (this.cancelSubmitting) return;
    this.orderToCancel = null;
  }

  confirmCancelOrder(): void {
    if (!this.orderToCancel) return;
    const base = this.cancelReasonPreset === 'Other reason' ? '' : this.cancelReasonPreset;
    const custom = this.cancelReasonCustom.trim();
    let reason = 'Cancelled by admin';
    if (base && custom) {
      reason = `${base}: ${custom}`;
    } else if (base) {
      reason = base;
    } else if (custom) {
      reason = custom;
    }

    this.cancelSubmitting = true;
    this.api.voidBill(this.orderToCancel.orderId, reason).subscribe({
      next: () => {
        this.cancelSubmitting = false;
        this.toast.show('Order cancelled.', 'success');
        this.closeCancelModal();
        this.loadOrders();
        this.cdr.markForCheck();
      },
      error: (err: any) => {
        this.cancelSubmitting = false;
        this.cdr.markForCheck();
        const msg = err?.error?.message || err?.error?.error || 'Failed to cancel order.';
        this.toast.show(msg, 'error');
      }
    });
  }

  clearFilters(): void {
    this.orderSearchTerm = '';
    this.orderSourceFilter = 'ALL';
    this.orderStatusFilter = 'ALL';
    this.orderPaymentFilter = 'ALL';
    this.onSearchChange('');
    this.cdr.markForCheck();
  }

  // --- Order detail modal ---

  openOrderDetail(order: BusinessOrder): void {
    this.orderDetailLoading = true;
    // Same OnPush reason as loadOrders(): these fields change outside a template
    // event (the click's own change-detection pass is already over by the time the
    // response lands), so without this the spinner and the modal never reach the
    // renderer until some unrelated click marks the view dirty again. That is what
    // made a 2 s response look like a 10 s "View" on the history table.
    this.cdr.markForCheck();
    this.api.getOrderDetail(order.orderId).subscribe({
      next: (detail) => {
        this.selectedOrderDetail = detail;
        this.orderDetailLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.orderDetailLoading = false;
        this.cdr.markForCheck();
        this.toast.show('Unable to load order details.', 'error');
      }
    });
  }

  closeOrderDetail(): void {
    this.selectedOrderDetail = null;
    // Without this the modal stays painted after the user closes it: the emit
    // happens from the modal's view, so this component is never re-checked.
    this.cdr.markForCheck();
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
    this.searchSub?.unsubscribe();
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
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.refundError = err?.error?.error || 'Refund failed. Please try again.';
        this.refunding = false;
        this.cdr.markForCheck();
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

  formatOrderDate(value: number | null): string {
    return value ? ORDER_DATE_FORMAT.format(new Date(value)) : '—';
  }

  formatOrderTime(value: number | null): string {
    return value ? ORDER_TIME_FORMAT.format(new Date(value)) : '';
  }

  /** The channel chip's modifier, including delivery (the old template only knew two). */
  channelClass(order: BusinessOrder): 'dine_in' | 'takeaway' | 'delivery' {
    if (this.isDelivery(order)) return 'delivery';
    if (this.isTakeaway(order)) return 'takeaway';
    return 'dine_in';
  }

  /** True while the bill still carries money owed, whichever word the API sends. */
  isPaymentPending(order: BusinessOrder): boolean {
    const status = (order.paymentStatus || '').trim().toLowerCase();
    return status.includes('pending') || status.includes('unpaid') || status.includes('due');
  }

  trackByIndex = (_: number, __: unknown) => _;
  trackByOrderId = (_: number, order: BusinessOrder) => order.orderId;
}
