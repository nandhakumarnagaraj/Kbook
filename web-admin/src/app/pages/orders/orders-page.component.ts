import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessOrder } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-orders-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
        <div class="modal-box" (click)="$event.stopPropagation()">
          <h3>Manual Refund {{ refundTarget.orderCode }}</h3>
          <p class="muted">Total: {{ formatCurrencyValue(refundTarget.totalAmount) }}</p>
          <p class="hint-text">
            This only records a refund already handled outside the gateway.
          </p>

          <div class="field">
            <label>Refund Amount</label>
            <input
              type="number"
              [(ngModel)]="refundAmountInput"
              [max]="refundTarget.totalAmount"
              min="0.01"
              step="0.01"
              placeholder="Enter amount"
            />
          </div>
          <div class="field">
            <label>Reason</label>
            <input type="text" [(ngModel)]="refundReasonInput" placeholder="e.g. Customer request" />
          </div>

          <p class="error-text" *ngIf="refundError">{{ refundError }}</p>

          <div class="modal-actions">
            <button class="ghost-btn" (click)="closeRefund()">Cancel</button>
            <button
              class="ghost-btn danger-btn"
              [disabled]="refunding || !refundAmountInput || refundAmountInput <= 0"
              (click)="confirmRefund()"
            >
              {{ refunding ? 'Processing...' : 'Confirm Manual Refund' }}
            </button>
          </div>
        </div>
      </div>

      <div class="toolbar">
        <div>
          <h3>POS and Business Orders</h3>
          <p class="muted">POS order list.</p>
        </div>
        <button class="ghost-btn" (click)="loadOrders()">Refresh</button>
      </div>

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

        <div class="filter-summary">
          <p class="muted">{{ filteredOrders.length }} of {{ orders.length }} orders</p>
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
            <tr *ngFor="let order of pagedOrders">
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
                    (click)="openManualRefund(order)"
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
        <div class="panel loading">{{ ordersLoaded ? 'No orders match the current filters.' : 'Loading orders...' }}</div>
      </ng-template>
    </div>
  `,
  styles: [`
    .danger-btn { color: #b03030; border-color: #b03030; }
    .refunded-label { color: #b03030; font-weight: 500; }
    .refund-meta { font-size: 0.75rem; color: #7a5c00; }
    .chip.success { background: #e6f4ea; color: #2d7a3a; }
    .chip.danger { background: #fdecea; color: #b03030; }
    .chip.warn { background: #fff8e1; color: #7a5c00; }
    .action-stack { display: flex; flex-direction: column; align-items: flex-start; gap: 0.35rem; }

    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.45);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal-box {
      background: #fff;
      border-radius: 18px;
      padding: 1.5rem 2rem;
      min-width: 340px;
      max-width: 460px;
      width: 100%;
      box-shadow: 0 18px 42px rgba(0, 0, 0, 0.18);
    }

    .modal-box h3 { margin: 0 0 0.25rem; }
    .field { margin: 1rem 0; display: flex; flex-direction: column; gap: 0.3rem; }
    .field label { font-size: 0.85rem; font-weight: 600; color: #444; }

    .field input {
      padding: 0.5rem 0.75rem;
      border: 1px solid #ccc;
      border-radius: 6px;
      font-size: 0.95rem;
      outline: none;
    }

    .field input:focus { border-color: #4a90e2; }
    .modal-actions { display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.25rem; }
    .error-text { color: #b03030; font-size: 0.85rem; margin: 0.5rem 0 0; }
    .hint-text { color: #6b7280; font-size: 0.85rem; margin: 0.35rem 0 0; }
  `]
})
export class OrdersPageComponent {
  private readonly api = inject(BusinessApiService);

  orders: BusinessOrder[] = [];
  ordersLoaded = false;

  refundTarget: BusinessOrder | null = null;
  refundAmountInput: number | null = null;
  refundReasonInput = '';
  refunding = false;
  refundError: string | null = null;

  orderSearchTerm = '';
  orderStatusFilter = 'ALL';
  orderSourceFilter = 'ALL';
  orderPageSize = 10;
  orderCurrentPage = 1;

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
    const search = this.orderSearchTerm.trim().toLowerCase();

    return this.orders.filter((order) => {
      const matchesSearch = !search || [
        order.orderCode,
        order.customerName ?? '',
        order.customerContact ?? '',
        order.paymentMethod,
        order.paymentStatus
      ].some((value) => value.toLowerCase().includes(search));

      const matchesStatus = this.orderStatusFilter === 'ALL' || order.orderStatus === this.orderStatusFilter;
      const matchesSource = this.orderSourceFilter === 'ALL' || order.sourceType === this.orderSourceFilter;

      return matchesSearch && matchesStatus && matchesSource;
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
    this.api.getOrders().subscribe({
      next: (data) => {
        this.orders = data;
        this.ordersLoaded = true;
        this.orderCurrentPage = 1;
      },
      error: () => { this.ordersLoaded = true; }
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
  }

  goToOrderPage(page: number): void {
    this.orderCurrentPage = Math.min(Math.max(1, page), this.orderTotalPages);
  }

  openManualRefund(order: BusinessOrder): void {
    this.refundTarget = order;
    this.refundAmountInput = order.totalAmount;
    this.refundReasonInput = '';
    this.refundError = null;
  }

  closeRefund(): void {
    this.refundTarget = null;
    this.refundError = null;
  }

  confirmRefund(): void {
    if (!this.refundTarget || !this.refundAmountInput) return;
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
