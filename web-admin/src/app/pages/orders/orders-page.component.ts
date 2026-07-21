import { CommonModule } from '@angular/common';
import { Component, HostListener, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder, OrderDetailResponse } from '../../core/models/api.models';
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
  imports: [CommonModule, FormsModule, DateRangeSelectorComponent, OrderDetailModalComponent, EmptyStateComponent, ApiStateComponent],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Orders</h2>
        <p class="muted">POS order management with refund actions and status visibility.</p>
        <div class="hero-meta">
          <span class="chip">Refund Tools</span>
          <span class="chip success">Unified Order View</span>
        </div>
      </section>

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

          <div class="modal-actions">
            <button type="button" class="ghost-btn" (click)="closeRefund()">Cancel</button>
            <button
              type="button"
              class="ghost-btn danger-btn"
              [disabled]="refunding || !refundAmountInput || refundAmountInput <= 0 || refundAmountInput > refundTarget.totalAmount"
              (click)="confirmRefund()"
            >
              {{ refunding ? 'Recording...' : 'Record Manual Refund' }}
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

      <div class="toolbar">
        <div>
          <h3>POS and Business Orders</h3>
          <p class="muted">POS order list.</p>
        </div>
        <div class="toolbar-actions">
          <button
            class="ghost-btn"
            [disabled]="filteredOrders.length === 0"
            [title]="filteredOrders.length === 0 ? 'No data to export' : 'Export filtered orders as CSV'"
            (click)="exportCsv()"
          >Export CSV</button>
          <button class="ghost-btn" (click)="loadOrders()">Refresh</button>
        </div>
      </div>

      <app-api-state
        *ngIf="ordersError"
        [loading]="false"
        [error]="ordersError"
        (retry)="loadOrders()"
      ></app-api-state>

      <section class="panel filter-panel" *ngIf="ordersLoaded && orders.length">
        <div class="filter-grid">
          <div class="filter-group">
            <label for="order-search">Search</label>
            <input
              id="order-search"
              class="field-control"
              type="text"
              [(ngModel)]="orderSearchTerm"
              (ngModelChange)="resetOrderPage()"
              placeholder="Search by order, customer, contact, or payment"
            />
          </div>
          <div class="filter-group">
            <label for="order-status">Status</label>
            <select id="order-status" class="field-select" [(ngModel)]="orderStatusFilter" (ngModelChange)="resetOrderPage()">
              <option value="ALL">All statuses</option>
              <option *ngFor="let status of businessOrderStatuses" [value]="status">{{ status }}</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="order-source">Source</label>
            <select id="order-source" class="field-select" [(ngModel)]="orderSourceFilter" (ngModelChange)="resetOrderPage()">
              <option value="ALL">All sources</option>
              <option *ngFor="let source of businessOrderSources" [value]="source">{{ source }}</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="order-size">Rows</label>
            <select id="order-size" class="field-select" [(ngModel)]="orderPageSize" (ngModelChange)="resetOrderPage()">
              <option [ngValue]="5">5</option>
              <option [ngValue]="10">10</option>
              <option [ngValue]="20">20</option>
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
            {{ filteredOrders.length }} of {{ orders.length }} orders
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
             <tr *ngFor="let order of pagedOrders" class="clickable-row" tabindex="0" role="button" [attr.aria-label]="'View order ' + order.orderCode" (click)="openOrderDetail(order)" (keydown.enter)="openOrderDetail(order)">
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
                  <span *ngIf="!order.manualRefundAllowed" class="muted">-</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="pagination-bar" *ngIf="filteredOrders.length > orderPageSize">
          <p class="muted">Page {{ orderCurrentPage }} of {{ orderTotalPages }}</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="orderCurrentPage === 1" (click)="goToOrderPage(orderCurrentPage - 1)">Previous</button>
            <button class="ghost-btn" [disabled]="orderCurrentPage === orderTotalPages" (click)="goToOrderPage(orderCurrentPage + 1)">Next</button>
          </div>
        </div>
      </div>
      <ng-template #posLoading>
        <div class="panel loading" *ngIf="!ordersLoaded; else ordersEmpty">
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4,5]"></div>
          </div>
        </div>
        <ng-template #ordersEmpty>
          <app-empty-state
            *ngIf="!ordersError"
            icon="🧾"
            title="No orders match the current filters"
            text="Try adjusting the search, status, source, or date range to see more results."
          ></app-empty-state>
        </ng-template>
      </ng-template>
    </div>
  `,
  styles: [`
    .refunded-label { color: var(--danger); font-weight: 500; }
    .refund-meta { font-size: 0.75rem; color: var(--brand-deep); }
    .clickable-row { cursor: pointer; transition: background 0.15s; }
    .clickable-row:hover { background: var(--bg, #F8FAFC); }
    .clickable-row:focus-visible { outline: 2px solid var(--brand, #F97316); outline-offset: -2px; }

    .filter-group--full { grid-column: 1 / -1; }

    .date-range-active {
      display: inline-block;
      margin-left: 0.5rem;
      padding: 0.15rem 0.5rem;
      background: var(--bg, #F8FAFC);
      border-radius: 6px;
      font-size: 0.8rem;
    }

    .field { margin: 1rem 0; display: flex; flex-direction: column; gap: 0.3rem; }
    .field label { font-size: 0.85rem; font-weight: 600; color: var(--ink); }
    .field input {
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--line);
      border-radius: 8px;
      font-size: 0.95rem;
      outline: none;
      min-height: 44px;
    }
    .field input:focus { border-color: var(--brand); }
    .error-text { color: var(--danger); font-size: 0.85rem; margin: 0.5rem 0 0; }
    .hint-text { color: var(--muted); font-size: 0.85rem; margin: 0.35rem 0 0; }
    .toolbar-actions { display: flex; gap: 0.5rem; align-items: center; }
  `]
})
export class OrdersPageComponent {
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
  private refundTrigger: HTMLElement | null = null;

  orderSearchTerm = '';
  orderStatusFilter = 'ALL';
  orderSourceFilter = 'ALL';
  orderPageSize = 10;
  orderCurrentPage = 1;

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
      statusFilter: this.orderStatusFilter,
      sourceFilter: this.orderSourceFilter,
      dateFrom: this.dateFrom,
      dateTo: this.dateTo
    });
  }

  get pagedOrders(): BusinessOrder[] {
    const start = (this.orderCurrentPage - 1) * this.orderPageSize;
    return this.filteredOrders.slice(start, start + this.orderPageSize);
  }

  get orderTotalPages(): number {
    return Math.max(1, Math.ceil(this.filteredOrders.length / this.orderPageSize));
  }

  loadOrders(): void {
    this.ordersLoaded = false;
    this.ordersError = '';
    this.api.getOrders().subscribe({
      next: (data) => {
        this.orders = data;
        this.ordersLoaded = true;
        this.orderCurrentPage = 1;
      },
      error: () => {
        this.orders = [];
        this.ordersError = 'Unable to load orders. Check your connection and try again.';
        this.ordersLoaded = true;
      }
    });
  }

  resetOrderPage(): void {
    this.orderCurrentPage = 1;
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
  }

  goToOrderPage(page: number): void {
    this.orderCurrentPage = Math.min(Math.max(1, page), this.orderTotalPages);
  }

  // --- Date range filtering ---

  onDateRangeChange(range: { from: string; to: string }): void {
    this.dateFrom = range.from;
    this.dateTo = range.to;
    this.dateRangeLabel = `${range.from} → ${range.to}`;
    this.resetOrderPage();
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
  }

  closeRefund(): void {
    if (this.refunding) return;
    this.refundTarget = null;
    this.refundError = null;
    document.body.style.overflow = '';
    this.refundTrigger?.focus();
    this.refundTrigger = null;
  }

  @HostListener('document:keydown.escape')
  handleEscape(): void {
    if (this.refundTarget && !this.refunding) this.closeRefund();
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

  formatCurrencyValue(value: number | null): string { return formatCurrency(value ?? 0); }
  formatDateValue(value: number | null): string { return formatDate(value); }
}
