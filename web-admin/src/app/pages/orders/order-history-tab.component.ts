import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder, OrderDetailResponse, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { buildInvoiceLink } from '../../core/utils/invoice-link.util';

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
  imports: [CommonModule, FormsModule, DateRangeSelectorComponent, OrderDetailModalComponent, ApiStateComponent],
  template: `
    <div class="tab-panel">
      <div class="tab-toolbar">
        <div class="tab-toolbar__actions">
          <button
            type="button"
            class="header-action-btn"
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
          <button type="button" class="header-action-btn" (click)="loadOrders()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            Refresh
          </button>
        </div>
      </div>

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

      <!-- Period Filter for Owner: Today, Yesterday, Custom -->
      <div class="period-presets-bar" *ngIf="ordersLoaded">
        <app-date-range-selector [initialRange]="currentDateRange" (rangeChanged)="onDateRangeChange($event)"></app-date-range-selector>
      </div>

      <app-api-state
        *ngIf="ordersError"
        [loading]="false"
        [error]="ordersError"
        (retry)="loadOrders()"
      ></app-api-state>

      <!-- ── Live Orders & Recent Transactions Section ── -->
      <section class="bento-orders-container" aria-label="Recent Orders and Transactions">
        <div class="orders-head-bar">
          <div class="orders-head-left">
            <div class="orders-title-row">
              <h3 class="orders-title">Live Orders &amp; Recent Transactions</h3>
              <span class="counter-badge">{{ serverTotalElements || pagedOrders.length }} Orders</span>
            </div>
            <p class="orders-sub">Active dining tickets, takeaway pickups, and today's latest POS settlements.</p>
          </div>

          <!-- Channel Filter Tabs, Search & Status -->
          <div class="orders-head-controls">
            <div class="channel-filter-tabs" role="tablist">
              <button
                type="button"
                class="channel-tab-btn"
                [class.channel-tab-btn--active]="orderSourceFilter === 'ALL'"
                (click)="setSourceFilter('ALL')">
                All
              </button>
              <button
                type="button"
                class="channel-tab-btn"
                [class.channel-tab-btn--active]="orderSourceFilter === 'DINE_IN'"
                (click)="setSourceFilter('DINE_IN')">
                Dine In
              </button>
              <button
                type="button"
                class="channel-tab-btn"
                [class.channel-tab-btn--active]="orderSourceFilter === 'TAKEAWAY'"
                (click)="setSourceFilter('TAKEAWAY')">
                Takeaway
              </button>
            </div>

            <div class="order-search-box">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input
                type="text"
                placeholder="Search order #, items, guest..."
                [(ngModel)]="orderSearchTerm"
                (ngModelChange)="onSearchChange($event)"
                aria-label="Filter recent orders"
              />
            </div>

            <div class="order-status-select-wrap">
              <select class="field-select-compact" [(ngModel)]="orderStatusFilter" (ngModelChange)="resetOrderPage()" aria-label="Filter by status">
                <option value="ALL">All Statuses</option>
                <option *ngFor="let s of businessOrderStatuses; trackBy: trackByIndex" [value]="s">{{ s }}</option>
              </select>
            </div>
          </div>
        </div>

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
                    <a
                      [href]="getInvoiceUrl(order)"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="btn-micro"
                      (click)="$event.stopPropagation()"
                      title="View printable tax invoice">
                      <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z"/><path d="M14 2v5a1 1 0 0 0 1 1h5M10 9H8m8 4H8m8 4H8"/></svg>
                      Invoice
                    </a>
                    <button
                      type="button"
                      class="btn-micro btn-micro--primary"
                      (click)="openOrderDetail(order)">
                      Details
                    </button>
                    <button
                      *ngIf="order.manualRefundAllowed"
                      type="button"
                      class="btn-micro btn-micro--danger"
                      (click)="openManualRefund(order)"
                      title="Issue refund">
                      Refund
                    </button>
                    <button
                      *ngIf="order.orderStatus.toLowerCase() === 'draft' || order.orderStatus.toLowerCase() === 'completed'"
                      type="button"
                      class="btn-micro btn-micro--danger"
                      (click)="openCancelModal(order)"
                      title="Cancel / Void order">
                      Cancel
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <ng-template #emptyOrLoadingOrders>
          <div class="panel loading" *ngIf="!ordersLoaded; else emptyOrdersTable">
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

        <!-- Pagination Bar -->
        <div class="orders-pagination-bar" *ngIf="serverTotalPages > 1">
          <p class="muted font-mono" style="margin: 0; font-size: 0.82rem;">Page {{ orderCurrentPage }} of {{ orderTotalPages }} ({{ serverTotalElements }} total orders)</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="orderCurrentPage === 1" (click)="goToOrderPage(orderCurrentPage - 1)">Previous</button>
            <button class="ghost-btn" [disabled]="orderCurrentPage === orderTotalPages" (click)="goToOrderPage(orderCurrentPage + 1)">Next</button>
          </div>
        </div>
      </section>
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

    /* Period Presets Bar */
    .period-presets-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      margin-bottom: 1.15rem;
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
      box-shadow: 0 2px 6px rgba(108, 39, 217, 0.25);
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

    /* Bento Orders Container & Tactile Table */
    .bento-orders-container {
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: 18px;
      padding: 1.5rem;
      margin-top: 1.5rem;
      box-shadow: 0 1px 3px rgba(28, 26, 23, 0.03), 0 4px 12px rgba(28, 26, 23, 0.02);
    }
    .orders-head-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.25rem;
      margin-bottom: 1.25rem;
      flex-wrap: wrap;
    }
    .orders-title-row {
      display: flex;
      align-items: center;
      gap: 0.65rem;
    }
    .orders-title {
      margin: 0;
      font-size: 1.2rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      letter-spacing: -0.02em;
    }
    .counter-badge {
      display: inline-block;
      padding: 0.18rem 0.55rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 700;
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }
    .orders-sub {
      margin: 0.2rem 0 0;
      font-size: 0.85rem;
      color: var(--kb-color-muted-foreground);
    }
    .orders-head-controls {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      flex-wrap: wrap;
    }
    .channel-filter-tabs {
      display: inline-flex;
      background: var(--kb-color-surface-2);
      padding: 3px;
      border-radius: 10px;
      gap: 2px;
    }
    .channel-tab-btn {
      padding: 0.4rem 0.75rem;
      border: none;
      background: transparent;
      border-radius: 8px;
      font-size: 0.78rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      cursor: pointer;
      transition: all 120ms ease;
    }
    .channel-tab-btn:hover { color: var(--kb-color-foreground); }
    .channel-tab-btn--active {
      background: var(--kb-color-surface);
      color: var(--kb-color-foreground);
      font-weight: 700;
      box-shadow: 0 1px 3px rgba(28, 26, 23, 0.08);
    }
    .order-search-box {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      background: var(--kb-color-bg-app);
      border: 1px solid var(--kb-color-border);
      border-radius: 10px;
      padding: 0.45rem 0.75rem;
      width: 220px;
      transition: all 140ms ease;
    }
    .order-search-box:focus-within {
      background: var(--kb-color-surface);
      border-color: var(--kb-color-primary);
      box-shadow: 0 0 0 3px rgba(108, 39, 217, 0.1);
    }
    .order-search-box svg { color: var(--kb-color-muted-foreground); }
    .order-search-box input {
      border: none;
      background: transparent;
      outline: none;
      width: 100%;
      font-size: 0.82rem;
      color: var(--kb-color-foreground);
    }
    .order-status-select-wrap {
      display: inline-flex;
      align-items: center;
    }
    .field-select-compact {
      padding: 0.45rem 0.75rem;
      border: 1px solid var(--kb-color-border);
      border-radius: 10px;
      background: var(--kb-color-bg-app);
      color: var(--kb-color-muted-foreground);
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      outline: none;
      min-height: 36px;
    }
    .field-select-compact:focus {
      border-color: var(--kb-color-primary);
      background: var(--kb-color-surface);
    }
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
      padding: 0.75rem 1rem;
      background: var(--kb-color-bg-app);
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--kb-color-muted-foreground);
      border-bottom: 1px solid var(--kb-color-border);
    }
    .orders-tactile-table td {
      padding: 0.85rem 1rem;
      border-bottom: 1px solid var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      vertical-align: middle;
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
    .status-chip {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 700;
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
    .btn-micro {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.35rem 0.65rem;
      border-radius: 8px;
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      color: var(--kb-color-muted-foreground);
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      transition: all 120ms ease;
    }
    .btn-micro:hover { background: var(--kb-color-surface-2); border-color: var(--kb-color-border-strong); }
    .btn-micro--primary {
      background: var(--kb-color-primary);
      border-color: var(--kb-color-primary);
      color: var(--kb-color-surface);
    }
    .btn-micro--primary:hover { background: var(--kb-color-primary-hover); }
    .btn-micro--danger {
      color: var(--kb-color-danger);
      border-color: var(--kb-color-danger-soft);
      background: var(--kb-color-danger-soft);
    }
    .btn-micro--danger:hover {
      background: var(--kb-color-danger-soft);
      border-color: var(--kb-color-danger);
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
    .orders-pagination-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding-top: 1rem;
      margin-top: 1rem;
      border-top: 1px solid var(--kb-color-surface-2);
      flex-wrap: wrap;
    }
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
  orderStatusFilter = 'ALL';
  orderSourceFilter = 'ALL';
  orderPageSize = 15;
  orderCurrentPage = 1;

  // Period Preset & Summary State for Owner
  activePreset: 'today' | 'yesterday' | 'custom' = 'today';
  showCustomRange = false;

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
    this.setPreset('today');
  }

  onSearchChange(value: string): void {
    this.searchSubject.next(value);
  }

  get businessOrderStatuses(): string[] {
    return [...new Set(this.orders.map((order) => order.orderStatus))].sort();
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

  setPreset(preset: 'today' | 'yesterday' | 'custom'): void {
    this.activePreset = preset;
    this.showCustomRange = preset === 'custom';
    const now = new Date();

    if (preset === 'today') {
      const todayStr = formatIsoDate(now);
      this.dateFrom = todayStr;
      this.dateTo = todayStr;
    } else if (preset === 'yesterday') {
      const y = new Date(now.getTime() - 24 * 60 * 60 * 1000);
      const yStr = formatIsoDate(y);
      this.dateFrom = yStr;
      this.dateTo = yStr;
    }

    this.orderCurrentPage = 1;
    this.loadOrders();
  }

  loadOrders(): void {
    this.ordersLoaded = false;
    this.ordersError = '';
    const serverStatus = this.orderStatusFilter !== 'ALL' ? this.orderStatusFilter : undefined;
    const from = this.dateFrom ?? undefined;
    const to = this.dateTo ?? undefined;
    const search = this.orderSearchTerm?.trim() || undefined;
    this.api.getOrdersPaginated(this.orderCurrentPage - 1, this.orderPageSize, serverStatus, from, to, search).subscribe({
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

  resetOrderPage(): void {
    this.orderCurrentPage = 1;
    this.loadOrders();
  }

  clearOrderFilters(): void {
    this.orderSearchTerm = '';
    this.orderStatusFilter = 'ALL';
    this.orderSourceFilter = 'ALL';
    this.orderPageSize = 15;
    this.orderCurrentPage = 1;
    this.setPreset('today');
  }

  goToOrderPage(page: number): void {
    this.orderCurrentPage = Math.min(Math.max(1, page), this.orderTotalPages);
    this.loadOrders();
  }

  // --- Date range filtering ---

  onDateRangeChange(range: { from: string; to: string }): void {
    const now = new Date();
    const today = formatIsoDate(now);
    const yest = formatIsoDate(new Date(now.getTime() - 24 * 60 * 60 * 1000));

    if (range.from === today && range.to === today) {
      this.activePreset = 'today';
    } else if (range.from === yest && range.to === yest) {
      this.activePreset = 'yesterday';
    } else {
      this.activePreset = 'custom';
    }

    this.dateFrom = range.from;
    this.dateTo = range.to;
    this.orderCurrentPage = 1;
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
