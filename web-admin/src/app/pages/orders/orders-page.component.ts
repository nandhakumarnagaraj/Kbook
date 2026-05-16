import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessOrder, MarketplaceOrder } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

type OrderTab = 'pos' | 'online';

@Component({
  selector: 'app-orders-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Orders</h2>
        <p class="muted">POS and online marketplace order management.</p>
        <div class="hero-meta">
          <span class="chip">POS Orders</span>
          <span class="chip success">Online Orders</span>
          <span class="chip" *ngIf="onlineCounts">Pending: {{ onlineCounts.pending }}</span>
        </div>
      </section>

      <div class="tab-bar">
        <button class="tab-btn" [class.active]="activeTab === 'pos'" (click)="switchTab('pos')">POS Orders</button>
        <button class="tab-btn" [class.active]="activeTab === 'online'" (click)="switchTab('online')">Online Orders</button>
      </div>

      <!-- ====== POS ORDERS TAB ====== -->
      <ng-container *ngIf="activeTab === 'pos'">

        <div class="modal-backdrop" *ngIf="refundTarget" (click)="closeRefund()">
          <div class="modal-box" (click)="$event.stopPropagation()">
            <h3>Manual Refund {{ refundTarget.orderCode }}</h3>
            <p class="muted">Total: {{ formatCurrencyValue(refundTarget.totalAmount) }}</p>
            <p class="hint-text">This only records a refund already handled outside the gateway.</p>

            <div class="field">
              <label>Refund Amount</label>
              <input type="number" [(ngModel)]="refundAmountInput" [max]="refundTarget.totalAmount" min="0.01" step="0.01" placeholder="Enter amount" />
            </div>
            <div class="field">
              <label>Reason</label>
              <input type="text" [(ngModel)]="refundReasonInput" placeholder="e.g. Customer request" />
            </div>

            <p class="error-text" *ngIf="refundError">{{ refundError }}</p>

            <div class="modal-actions">
              <button class="ghost-btn" (click)="closeRefund()">Cancel</button>
              <button class="ghost-btn danger-btn" [disabled]="refunding || !refundAmountInput || refundAmountInput <= 0" (click)="confirmRefund()">
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
              <input id="order-search" class="field-control" type="text" [(ngModel)]="orderSearchTerm" (ngModelChange)="resetOrderPage()" placeholder="Search by order, customer, contact, or payment" />
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
                  <span class="chip" [class.success]="order.orderStatus.toLowerCase() === 'completed'" [class.danger]="order.orderStatus.toLowerCase() === 'cancelled'" [class.warn]="order.orderStatus.toLowerCase() === 'draft'">
                    {{ order.orderStatus }}
                  </span>
                </td>
                <td>
                  {{ order.paymentMethod }} /
                  <span [class.refunded-label]="order.paymentStatus.toLowerCase() === 'refunded'">{{ order.paymentStatus }}</span>
                </td>
                <td>{{ formatCurrencyValue(order.totalAmount) }}</td>
                <td>
                  <span *ngIf="order.refundAmount && order.refundAmount > 0" class="refunded-label">
                    -{{ formatCurrencyValue(order.refundAmount) }}<br />
                    <span class="refund-meta">{{ order.refundStatus }}<span *ngIf="order.refundMode"> / {{ order.refundMode }}</span></span><br />
                    <span class="muted" style="font-size: 0.75rem;">{{ order.cancelReason }}</span>
                  </span>
                  <span *ngIf="!order.refundAmount || order.refundAmount === 0" class="muted">{{ order.refundStatus || '-' }}</span>
                </td>
                <td>{{ formatDateValue(order.createdAt) }}</td>
                <td>
                  <div class="action-stack">
                    <button *ngIf="order.manualRefundAllowed" class="ghost-btn danger-btn" (click)="openManualRefund(order)">Manual Refund</button>
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
      </ng-container>

      <!-- ====== ONLINE ORDERS TAB ====== -->
      <ng-container *ngIf="activeTab === 'online'">

        <div class="toolbar">
          <div>
            <h3>Marketplace Orders</h3>
            <p class="muted">Orders from Swiggy, Zomato and other platforms.</p>
          </div>
          <button class="ghost-btn" (click)="loadMarketplaceOrders()">Refresh</button>
        </div>

        <div class="panel stats-grid online-stats" *ngIf="onlineCounts">
          <div class="stat-card">
            <h3>Pending</h3>
            <strong>{{ onlineCounts.pending }}</strong>
          </div>
          <div class="stat-card">
            <h3>Accepted</h3>
            <strong>{{ onlineCounts.accepted }}</strong>
          </div>
          <div class="stat-card">
            <h3>Ready</h3>
            <strong>{{ onlineCounts.ready }}</strong>
          </div>
          <div class="stat-card">
            <h3>Rejected</h3>
            <strong>{{ onlineCounts.rejected }}</strong>
          </div>
        </div>

        <!-- Reject reason modal -->
        <div class="modal-backdrop" *ngIf="rejectTarget" (click)="cancelReject()">
          <div class="modal-box" (click)="$event.stopPropagation()">
            <h3>Reject Order #{{ rejectTarget.platformOrderId }}</h3>
            <p class="muted">Provide an optional reason for rejecting this order.</p>

            <div class="field">
              <label>Reason</label>
              <input type="text" [(ngModel)]="rejectReasonInput" placeholder="e.g. Out of stock" />
            </div>

            <p class="error-text" *ngIf="rejectError">{{ rejectError }}</p>

            <div class="modal-actions">
              <button class="ghost-btn" (click)="cancelReject()">Cancel</button>
              <button class="ghost-btn danger-btn" [disabled]="rejecting" (click)="confirmReject()">
                {{ rejecting ? 'Rejecting...' : 'Confirm Reject' }}
              </button>
            </div>
          </div>
        </div>

        <div class="panel table-wrap" *ngIf="marketplaceOrders.length; else onlineLoading">
          <table class="data-table">
            <thead>
              <tr>
                <th>Platform</th>
                <th>Order ID</th>
                <th>Customer</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Time</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let order of marketplaceOrders">
                <td>
                  <span class="chip" [class.swiggy]="order.platform === 'SWIGGY'" [class.zomato]="order.platform === 'ZOMATO'">
                    {{ order.platform }}
                  </span>
                </td>
                <td>{{ order.platformOrderId }}</td>
                <td>
                  <div class="stacked-meta">
                    <strong>{{ order.customerName || '-' }}</strong>
                    <span class="muted">{{ order.customerPhone || '' }}</span>
                  </div>
                </td>
                <td>{{ formatCurrencyValue(order.totalAmount) }}</td>
                <td>
                  <span class="chip" [class.warn]="order.orderStatus === 'PENDING'" [class.info]="order.orderStatus === 'ACCEPTED'" [class.success]="order.orderStatus === 'READY'" [class.danger]="order.orderStatus === 'REJECTED'" [class.muted-chip]="order.orderStatus === 'COMPLETED'">
                    {{ order.orderStatus }}
                  </span>
                </td>
                <td>{{ formatDateValue(order.createdAt) }}</td>
                <td>
                  <div class="action-stack">
                    <button *ngIf="order.orderStatus === 'PENDING'" class="primary-btn accept-btn" (click)="acceptOrder(order)">Accept</button>
                    <button *ngIf="order.orderStatus === 'PENDING'" class="ghost-btn danger-btn" (click)="startReject(order)">Reject</button>
                    <button *ngIf="order.orderStatus === 'ACCEPTED'" class="ghost-btn ready-btn" (click)="markReady(order)">Mark Ready</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <ng-template #onlineLoading>
          <div class="panel loading">{{ marketplaceOrdersLoaded ? 'No marketplace orders yet.' : 'Loading marketplace orders...' }}</div>
        </ng-template>
      </ng-container>
    </div>
  `,
  styles: [`
    .tab-bar {
      display: flex;
      gap: 0;
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 18px;
      overflow: hidden;
      box-shadow: var(--shadow-soft);
      position: sticky;
      top: 0.75rem;
      z-index: 5;
    }

    .tab-btn {
      flex: 1;
      border: none;
      padding: 0.85rem 1.5rem;
      font-weight: 700;
      font-size: 0.95rem;
      cursor: pointer;
      background: transparent;
      color: var(--muted);
      transition: background 0.18s ease, color 0.18s ease;
    }

    .tab-btn.active {
      background: linear-gradient(135deg, rgba(181, 106, 45, 0.16), rgba(126, 68, 23, 0.12));
      color: var(--brand-deep);
    }

    .tab-btn:hover:not(.active) {
      background: rgba(181, 106, 45, 0.06);
    }

    .online-stats {
      padding: 1rem;
    }

    .chip.info {
      background: rgba(74, 144, 226, 0.14);
      color: var(--info);
    }

    .chip.muted-chip {
      background: #f0eae2;
      color: var(--muted);
    }

    .chip.swiggy {
      background: rgba(255, 128, 0, 0.14);
      color: #cc6600;
    }

    .chip.zomato {
      background: rgba(226, 27, 60, 0.14);
      color: #b81b30;
    }

    .danger-btn { color: var(--danger); border-color: var(--danger); }

    .accept-btn {
      font-size: 0.8rem;
      padding: 0.5rem 1rem;
    }

    .ready-btn {
      background: rgba(29, 123, 95, 0.14);
      color: var(--accent);
      font-size: 0.8rem;
      padding: 0.5rem 1rem;
    }

    .refunded-label { color: var(--danger); font-weight: 500; }
    .refund-meta { font-size: 0.75rem; color: #7a5c00; }
    .chip.success { background: #e6f4ea; color: #2d7a3a; }
    .chip.danger { background: var(--danger-soft); color: var(--danger); }
    .chip.warn { background: var(--warn-soft); color: #7a5c00; }
    .action-stack { display: flex; flex-direction: column; align-items: flex-start; gap: 0.35rem; min-width: 140px; }

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
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 18px;
      padding: 1.5rem;
      width: min(100%, 520px);
      max-width: 520px;
      max-height: min(88vh, 720px);
      overflow-y: auto;
      box-shadow: 0 18px 42px rgba(0, 0, 0, 0.18);
    }

    .modal-box h3 { margin: 0 0 0.25rem; }
    .field { margin: 1rem 0; display: flex; flex-direction: column; gap: 0.3rem; }
    .field label { font-size: 0.85rem; font-weight: 600; color: #444; }

    .field input {
      min-height: 44px;
      padding: 0.75rem 0.9rem;
      border: 1px solid var(--line);
      border-radius: 10px;
      font-size: 0.95rem;
      outline: none;
    }

    .field input:focus {
      border-color: var(--brand);
      box-shadow: 0 0 0 3px rgba(181, 106, 45, 0.14);
    }
    .modal-actions { display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.25rem; }
    .error-text { color: var(--danger); font-size: 0.85rem; margin: 0.5rem 0 0; }
    .hint-text { color: #6b7280; font-size: 0.85rem; margin: 0.35rem 0 0; }

    @media (max-width: 960px) {
      .tab-bar { top: 0.5rem; }
    }

    @media (max-width: 720px) {
      .tab-bar { border-radius: 16px; }
      .tab-btn {
        padding: 0.8rem 0.9rem;
        font-size: 0.88rem;
      }
      .action-stack {
        min-width: 0;
        width: 100%;
      }
      .action-stack .ghost-btn,
      .action-stack .primary-btn {
        width: 100%;
        justify-content: center;
      }
      .modal-box {
        padding: 1rem;
        width: calc(100vw - 1rem);
      }
      .modal-actions { flex-direction: column-reverse; }
      .modal-actions .ghost-btn { width: 100%; }
    }
  `]
})
export class OrdersPageComponent {
  private readonly api = inject(BusinessApiService);

  activeTab: OrderTab = 'pos';

  // POS orders
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

  // Online orders
  marketplaceOrders: MarketplaceOrder[] = [];
  marketplaceOrdersLoaded = false;
  onlineCounts: { pending: number; accepted: number; ready: number; rejected: number } | null = null;

  rejectTarget: MarketplaceOrder | null = null;
  rejectReasonInput = '';
  rejecting = false;
  rejectError: string | null = null;

  constructor() {
    this.loadOrders();
    this.loadMarketplaceCounts();
  }

  switchTab(tab: OrderTab): void {
    this.activeTab = tab;
    if (tab === 'online') {
      this.loadMarketplaceOrders();
      this.loadMarketplaceCounts();
    }
  }

  // ---- POS order methods ----

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

  // ---- Marketplace order methods ----

  loadMarketplaceOrders(): void {
    this.marketplaceOrdersLoaded = false;
    this.api.getMarketplaceOrders().subscribe({
      next: (data) => {
        this.marketplaceOrders = data;
        this.marketplaceOrdersLoaded = true;
      },
      error: () => {
        this.marketplaceOrders = [];
        this.marketplaceOrdersLoaded = true;
      }
    });
  }

  loadMarketplaceCounts(): void {
    this.api.getMarketplaceOrderCounts().subscribe({
      next: (data) => { this.onlineCounts = data; },
      error: () => { this.onlineCounts = null; }
    });
  }

  acceptOrder(order: MarketplaceOrder): void {
    this.api.acceptMarketplaceOrder(order.id).subscribe({
      next: (updated) => {
        const idx = this.marketplaceOrders.findIndex((o) => o.id === updated.id);
        if (idx !== -1) this.marketplaceOrders[idx] = updated;
        this.loadMarketplaceCounts();
      }
    });
  }

  startReject(order: MarketplaceOrder): void {
    this.rejectTarget = order;
    this.rejectReasonInput = '';
    this.rejectError = null;
  }

  cancelReject(): void {
    this.rejectTarget = null;
    this.rejectError = null;
  }

  confirmReject(): void {
    if (!this.rejectTarget) return;
    this.rejecting = true;
    this.rejectError = null;

    this.api.rejectMarketplaceOrder(this.rejectTarget.id, this.rejectReasonInput.trim() || undefined).subscribe({
      next: (updated) => {
        const idx = this.marketplaceOrders.findIndex((o) => o.id === updated.id);
        if (idx !== -1) this.marketplaceOrders[idx] = updated;
        this.rejecting = false;
        this.cancelReject();
        this.loadMarketplaceCounts();
      },
      error: (err) => {
        this.rejectError = err?.error?.error || 'Reject failed. Please try again.';
        this.rejecting = false;
      }
    });
  }

  markReady(order: MarketplaceOrder): void {
    this.api.markMarketplaceOrderReady(order.id).subscribe({
      next: (updated) => {
        const idx = this.marketplaceOrders.findIndex((o) => o.id === updated.id);
        if (idx !== -1) this.marketplaceOrders[idx] = updated;
        this.loadMarketplaceCounts();
      }
    });
  }

  formatCurrencyValue(value: number | null): string { return formatCurrency(value ?? 0); }
  formatDateValue(value: number | null): string { return formatDate(value); }
}
