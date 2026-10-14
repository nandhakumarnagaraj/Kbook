import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, EventEmitter, HostListener, Input, OnDestroy, Output, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged, from, of } from 'rxjs';
import { concatMap, finalize, reduce, switchMap } from 'rxjs/operators';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder, OrderDetailResponse, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
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

@Component({
  selector: 'app-order-history-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, OrderDetailModalComponent, ApiStateComponent],
  template: `
    <app-order-detail-modal
      *ngIf="selectedOrderDetail"
      [order]="selectedOrderDetail"
      (billUpdated)="loadOrders()"
      (closed)="closeOrderDetail()">
    </app-order-detail-modal>

    <div class="tab-panel">
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

      <div class="panel loading" *ngIf="orderDetailLoading" role="status" aria-live="polite">
        Loading order details...
      </div>

      <!-- Order Cancellation Modal (Accessible Design-System Dialog) -->
      <div class="modal-backdrop" *ngIf="orderToCancel" (click)="closeCancelModal()" role="dialog" aria-modal="true" aria-labelledby="cancel-modal-title">
        <section class="modal-box" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <div>
              <span class="chip danger">Void / Cancel Order</span>
              <h3 id="cancel-modal-title" style="margin: 0.35rem 0 0;">Cancel Order #{{ orderToCancel.orderCode }}</h3>
              <p class="muted" style="margin: 0.25rem 0 0; font-size: 0.85rem;">
                Amount: {{ formatCurrencyValue(orderToCancel.totalAmount) }} · {{ orderToCancel.customerName || 'Walk-in customer' }}
              </p>
            </div>
            <button type="button" class="close-btn" (click)="closeCancelModal()" [disabled]="cancelSubmitting" aria-label="Close dialog">✕</button>
          </div>

          <div class="modal-body" style="display: grid; gap: 1rem; padding: 1rem 0;">
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

      <!-- ── Order History Table (no container chrome: bare table only) ── -->
      <ng-container aria-label="Order history">
        <!-- Orders Tactile Table -->
        <div class="orders-table-wrapper" *ngIf="ordersLoaded && pagedOrders.length > 0; else emptyOrLoadingOrders">
          <table class="orders-tactile-table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Channel &amp; Table</th>
                <th>Guest &amp; Summary</th>
                <th>Total</th>
                <th>Status</th>
                <th>Time</th>
                <th class="text-right">Action</th>
              </tr>
            </thead>
            <tbody>
              <tr
                *ngFor="let order of pagedOrders; trackBy: trackByOrderId"
                class="order-row-interactive"
                (click)="openOrderDetail(order)">
                <td>
                  <span class="order-hash-code">#{{ order.orderCode }}</span>
                  <div *ngIf="order.invoiceNumber && order.invoiceNumber !== order.orderCode" class="invoice-sub-badge font-mono">
                    {{ order.invoiceNumber }}
                  </div>
                </td>
                <td>
                  <span class="channel-chip channel-chip--{{ isTakeaway(order) ? 'takeaway' : 'dine_in' }}">
                    {{ isTakeaway(order) ? 'Takeaway' : 'Dine In' }}
                  </span>
                </td>
                <td>
                  <div class="order-guest-desc">
                    <span class="guest-title">{{ order.customerName ? 'Guest: ' + order.customerName : (isTakeaway(order) ? 'Takeaway Order' : 'Dine In Order') }}</span>
                    <span class="guest-meta">{{ isTakeaway(order) ? 'Takeaway' : (order.tableNumber ? 'Table ' + order.tableNumber : 'Dine In') }} · {{ order.paymentMethod || 'Cash' }}</span>
                  </div>
                </td>
                <td>
                  <strong class="order-table-amount">{{ formatCurrencyValue(order.totalAmount) }}</strong>
                  <div *ngIf="order.refundAmount && order.refundAmount > 0" class="refund-sub-badge">
                    -{{ formatCurrencyValue(order.refundAmount) }}
                  </div>
                </td>
                <td>
                  <span class="status-chip" [ngClass]="getOrderStatusClass(order.orderStatus)">
                    {{ order.orderStatus }}
                  </span>
                </td>
                <td>
                  <span class="time-stamp-label">{{ formatDateValue(order.createdAt) }}</span>
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

        <ng-template #emptyOrLoadingOrders>
          <div class="panel loading" *ngIf="!ordersLoaded; else emptyOrdersTable" role="status" aria-busy="true">
            <span class="visually-hidden">Loading recent orders...</span>
            <div class="skeleton-stack">
              <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4,5]; trackBy: trackByIndex"></div>
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
              <h4>Kitchen &amp; Floor Counter Clear</h4>
              <p>No orders matching the filter or date window. Transactions generated from POS terminals appear here instantly.</p>
              <div class="empty-actions-row">
                <button type="button" class="header-action-btn" (click)="clearOrderFilters()">Clear Filters</button>
              </div>
            </div>
          </ng-template>
        </ng-template>

        <div #ordersLoadMoreSentinel class="orders-load-more" aria-live="polite">
          <span *ngIf="!loadingMoreOrders && loadMoreOrdersError" class="load-more-error">{{ loadMoreOrdersError }}</span>
          <button *ngIf="!loadingMoreOrders && loadMoreOrdersError" type="button" class="ghost-btn" (click)="loadNextOrderPage()">Retry</button>
        </div>
      </ng-container>
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
    .refund-review { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--kb-space-3) var(--kb-space-2); margin: var(--kb-space-3) 0; padding: var(--kb-space-3); background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-lg); }
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
    /* Tab body — the routed shell owns the page header and outer geometry. */
    .tab-panel {
      display: grid;
      gap: 1.5rem;
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
      border-radius: 12px;
      padding: 3px;
      gap: 3px;
      box-shadow: 0 1px 3px rgba(28, 26, 23, 0.03);
    }
    .preset-pill {
      padding: 0.45rem 0.85rem;
      border: none;
      background: transparent;
      border-radius: 9px;
      font-size: 0.82rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      cursor: pointer;
      transition: all 120ms ease;
    }
    .preset-pill:hover { color: var(--kb-color-foreground); background: var(--kb-color-bg-app); }
    .preset-pill--active {
      background: var(--kb-color-primary) !important;
      color: var(--kb-color-surface) !important;
      box-shadow: 0 2px 6px rgba(var(--kb-color-primary-rgb), 0.25);
    }
    .preset-pill--outline {
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      box-shadow: 0 1px 2px rgba(28, 26, 23, 0.04);
    }
    .custom-date-picker-drawer {
      margin-bottom: 1.25rem;
      padding: 1rem;
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: 14px;
      box-shadow: 0 2px 8px rgba(28, 26, 23, 0.04);
    }
    /* Tactile CTA Buttons (cta.gallery & 60fps.design) */
    
    
    

    
    
    
    

    /* Thermal Receipt Empty State (404s.design) */
    .receipt-empty-state {
      background: var(--kb-color-surface);
      border: 1.5px dashed var(--kb-color-border-strong);
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
      background: var(--kb-color-bg-app);
      border: 1px solid var(--kb-color-border);
      color: var(--kb-color-muted-foreground);
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
    }
    .receipt-empty-title {
      margin: 0;
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
    }
    .receipt-empty-sub {
      margin: 0;
      font-size: 0.86rem;
      color: var(--kb-color-muted-foreground);
      max-width: 380px;
      line-height: 1.45;
    }

    .chip--dine-in {
      background: var(--kb-color-primary-soft);
      color: var(--kb-color-primary);
      border: 1px solid var(--kb-color-primary-soft);
    }
    .chip--takeaway {
      background: var(--kb-color-warning-soft);
      color: var(--kb-color-warning);
      border: 1px solid var(--kb-color-warning-soft);
    }
    .chip--delivery {
      background: var(--kb-color-success-soft);
      color: var(--kb-color-success);
      border: 1px solid var(--kb-color-success-soft);
    }

    /* Tactile Table (bare: no container card around it) */
    .orders-table-wrapper {
      overflow-x: auto;
      border: 1px solid var(--kb-color-surface-2);
      border-radius: 12px;
    }
    .orders-tactile-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.84rem;
      text-align: left;
    }
    .orders-tactile-table th {
      padding: 0.85rem 1rem;
      background: var(--kb-color-bg-app);
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--kb-color-muted-foreground);
      border-bottom: 1px solid var(--kb-color-border);
      vertical-align: middle;
    }
    .orders-tactile-table td {
      padding: 0.85rem 1rem;
      border-bottom: 1px solid var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      vertical-align: middle;
    }
    /* Numeric total right-aligned, status centered, action right — header and
       cells share the same alignment so columns read straight. */
    .orders-tactile-table th:nth-child(4),
    .orders-tactile-table td:nth-child(4) {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .orders-tactile-table th:nth-child(5),
    .orders-tactile-table td:nth-child(5) {
      text-align: center;
    }
    .order-row-interactive {
      cursor: pointer;
      transition: background 120ms ease;
    }
    .order-row-interactive:hover { background: var(--kb-color-bg-app); }
    .order-row-interactive:last-child td { border-bottom: none; }
    .order-hash-code {
      font-family: var(--kb-font-mono);
      font-weight: 700;
      color: var(--kb-color-foreground);
      background: var(--kb-color-surface-2);
      padding: 0.2rem 0.45rem;
      border-radius: 6px;
      font-size: 0.8rem;
      display: inline-block;
    }
    .invoice-sub-badge {
      font-size: 0.72rem;
      color: var(--kb-color-muted-foreground);
      margin-top: 0.15rem;
    }
    .channel-chip {
      display: inline-block;
      padding: 0.18rem 0.55rem;
      border-radius: 6px;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.02em;
    }
    .channel-chip--dine_in { background: var(--kb-color-primary-soft); color: var(--kb-color-primary); }
    .channel-chip--takeaway { background: var(--kb-color-warning-soft); color: var(--kb-color-warning); }
    .order-guest-desc {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
    }
    .guest-title {
      font-weight: 600;
      color: var(--kb-color-foreground);
      font-size: 0.84rem;
    }
    .guest-meta {
      font-size: 0.74rem;
      color: var(--kb-color-muted-foreground);
    }
    .order-table-amount {
      font-weight: 800;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }
    .refund-sub-badge {
      font-size: 0.72rem;
      color: var(--kb-color-danger);
      font-weight: 600;
      margin-top: 0.1rem;
    }
    .badge-preparing { background: var(--kb-color-warning-soft); color: var(--kb-color-warning); }
    .badge-cooking { background: var(--kb-color-warning-soft); color: var(--kb-color-warning); }
    .badge-served { background: var(--kb-color-primary-soft); color: var(--kb-color-primary); }
    .badge-completed { background: var(--kb-color-success-soft); color: var(--kb-color-success); }
    .badge-cancelled { background: var(--kb-color-danger-soft); color: var(--kb-color-danger); }
    .time-stamp-label {
      font-size: 0.76rem;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }
    .action-btn-group {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.45rem;
    }
    .empty-orders-view {
      padding: 3rem 1.5rem;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 0.5rem;
    }
    .empty-icon-circle {
      width: 56px;
      height: 56px;
      border-radius: 16px;
      background: var(--kb-color-bg-app);
      border: 1px solid var(--kb-color-border-strong);
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--kb-color-muted-foreground);
      margin-bottom: 0.25rem;
    }
    .empty-actions-row {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      margin-top: 0.75rem;
    }
    .orders-load-more {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.65rem;
      padding-top: 1rem;
      margin-top: 1rem;
      border-top: 1px solid var(--kb-color-surface-2);
      flex-wrap: wrap;
      min-height: 2.5rem;
      color: var(--kb-color-muted-foreground);
      font-size: 0.82rem;
    }
    .load-more-error { color: var(--kb-color-error); }
    .btn-micro--refund,
    .btn-micro--refund:hover {
      color: #fff;
      border-color: var(--kb-color-danger, #c0392b);
      background: var(--kb-color-danger, #c0392b);
    }
    .btn-micro--refund:hover { filter: brightness(0.92); }
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
  private loadMoreObserver?: IntersectionObserver;

  @ViewChild('ordersLoadMoreSentinel')
  set ordersLoadMoreSentinel(element: ElementRef<HTMLElement> | undefined) {
    this.loadMoreObserver?.disconnect();
    if (!element || typeof IntersectionObserver === 'undefined') return;
    this.loadMoreObserver = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) this.loadNextOrderPage();
    }, { rootMargin: '120px 0px' });
    this.loadMoreObserver.observe(element.nativeElement);
  }

  refundTarget: BusinessOrder | null = null;
  refundAmountInput: number | null = null;
  refundReasonInput = '';
  refunding = false;
  refundError: string | null = null;
  refundReview = false;
  private refundTrigger: HTMLElement | null = null;

  orderSearchTerm = '';
  orderSourceFilter = 'ALL';

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
  orderPageSize = 15;
  orderCurrentPage = 1;
  exportingCsv = false;
  loadingMoreOrders = false;
  loadMoreOrdersError = '';

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

  get businessOrderSources(): string[] {
    return [...new Set(this.orders.map((order) => this.formatOrderType(order)))].sort();
  }

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

  get hasMoreOrders(): boolean {
    return this.orderCurrentPage < this.serverTotalPages;
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
    this.loadMoreOrdersError = '';
    this.loadingMoreOrders = false;
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

  loadNextOrderPage(): void {
    if (!this.ordersLoaded || this.loadingMoreOrders || !this.hasMoreOrders) return;
    this.loadingMoreOrders = true;
    this.loadMoreOrdersError = '';
    const from = this.dateFrom ?? undefined;
    const to = this.dateTo ?? undefined;
    const search = this.orderSearchTerm?.trim() || undefined;
    const requestedPage = this.orderCurrentPage;
    this.api.getOrdersPaginated(requestedPage, this.orderPageSize, undefined, from, to, search).subscribe({
      next: (data: PaginatedOrdersResponse) => {
        const existingIds = new Set(this.orders.map(order => order.orderId));
        this.orders = [...this.orders, ...data.content.filter(order => !existingIds.has(order.orderId))];
        this.serverTotalElements = data.totalElements;
        this.serverTotalPages = data.totalPages;
        this.orderCurrentPage = requestedPage + 1;
        this.loadingMoreOrders = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingMoreOrders = false;
        this.loadMoreOrdersError = 'Could not load more orders.';
        this.cdr.markForCheck();
      }
    });
  }

  resetOrderPage(): void {
    this.orderCurrentPage = 1;
    this.loadOrders();
  }

  clearOrderFilters(): void {
    this.orderSearchTerm = '';
    this.searchQueryChange.emit('');
    this.orderSourceFilter = 'ALL';
    this.orderPageSize = 15;
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
      },
      error: (err: any) => {
        this.cancelSubmitting = false;
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
    this.loadMoreObserver?.disconnect();
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
