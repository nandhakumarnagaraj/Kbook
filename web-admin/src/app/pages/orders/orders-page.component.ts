import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessOrder, StorefrontOrder } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-orders-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-shell">

      <!-- Refund Dialog -->
      <div class="modal-backdrop" *ngIf="refundTarget" (click)="closeRefund()">
        <div class="modal-box" (click)="$event.stopPropagation()">
          <h3>Refund Order {{ refundTarget.orderCode }}</h3>
          <p class="muted">Total: {{ formatCurrencyValue(refundTarget.totalAmount) }}</p>

          <div class="field">
            <label>Refund Amount</label>
            <input type="number" [(ngModel)]="refundAmountInput"
              [max]="refundTarget.totalAmount" min="0.01" step="0.01"
              placeholder="Enter amount" />
          </div>
          <div class="field">
            <label>Reason</label>
            <input type="text" [(ngModel)]="refundReasonInput" placeholder="e.g. Customer request" />
          </div>

          <p class="error-text" *ngIf="refundError">{{ refundError }}</p>

          <div class="modal-actions">
            <button class="ghost-btn" (click)="closeRefund()">Cancel</button>
            <button class="ghost-btn danger-btn"
              [disabled]="refunding || !refundAmountInput || refundAmountInput <= 0"
              (click)="confirmRefund()">
              {{ refunding ? 'Processing...' : 'Confirm Refund' }}
            </button>
          </div>
        </div>
      </div>

      <!-- POS Orders -->
      <div class="toolbar">
        <div>
          <h2>Orders</h2>
          <p class="muted">Combined POS and online storefront order list.</p>
        </div>
        <button class="ghost-btn" (click)="loadOrders()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="ordersLoaded && orders.length; else posLoading">
        <table>
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
            <tr *ngFor="let order of orders">
              <td><span class="chip">{{ order.sourceType }}</span></td>
              <td>{{ order.orderCode }}</td>
              <td>
                <strong>{{ order.customerName || '-' }}</strong><br>
                <span class="muted">{{ order.customerContact || '' }}</span>
              </td>
              <td>
                <span class="chip"
                  [class.success]="order.orderStatus.toLowerCase() === 'completed'"
                  [class.danger]="order.orderStatus.toLowerCase() === 'cancelled'"
                  [class.warn]="order.orderStatus.toLowerCase() === 'draft'">
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
                  -{{ formatCurrencyValue(order.refundAmount) }}<br>
                  <span class="muted" style="font-size:0.75rem">{{ order.cancelReason }}</span>
                </span>
                <span *ngIf="!order.refundAmount || order.refundAmount === 0" class="muted">—</span>
              </td>
              <td>{{ formatDateValue(order.createdAt) }}</td>
              <td>
                <button
                  *ngIf="order.sourceType === 'POS' && order.orderStatus.toLowerCase() === 'completed' && order.paymentStatus.toLowerCase() === 'success'"
                  class="ghost-btn danger-btn"
                  (click)="openRefund(order)">
                  Refund
                </button>
                <span *ngIf="!(order.sourceType === 'POS' && order.orderStatus.toLowerCase() === 'completed' && order.paymentStatus.toLowerCase() === 'success')"
                  class="muted">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <ng-template #posLoading>
        <div class="panel loading">{{ ordersLoaded ? 'No orders yet.' : 'Loading orders...' }}</div>
      </ng-template>

      <!-- Storefront Orders -->
      <div class="toolbar" style="margin-top: 2rem;">
        <div>
          <h2>Storefront Orders</h2>
          <p class="muted">Online orders from customers — accept or reject pending ones.</p>
        </div>
        <button class="ghost-btn" (click)="loadStorefrontOrders()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="storefrontOrders.length; else sfLoading">
        <table>
          <thead>
            <tr>
              <th>Order Code</th>
              <th>Customer</th>
              <th>Type</th>
              <th>Status</th>
              <th>Payment</th>
              <th>Total</th>
              <th>Created</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let order of storefrontOrders">
              <td><strong>{{ order.publicOrderCode }}</strong></td>
              <td>
                {{ order.customerName }}<br>
                <span class="muted">{{ order.customerPhone || '' }}</span>
              </td>
              <td>{{ order.fulfillmentType }}</td>
              <td>
                <span class="chip"
                  [class.warn]="order.orderStatus === 'PENDING_CONFIRMATION'"
                  [class.success]="order.orderStatus === 'COMPLETED' || order.orderStatus === 'ACCEPTED'"
                  [class.danger]="order.orderStatus === 'REJECTED' || order.orderStatus === 'CANCELLED'">
                  {{ order.orderStatus }}
                </span>
              </td>
              <td>{{ order.paymentMethod }} / {{ order.paymentStatus }}</td>
              <td>{{ formatCurrencyValue(order.totalAmount) }}</td>
              <td>{{ formatDateValue(order.createdAt) }}</td>
              <td>
                <ng-container *ngIf="order.orderStatus === 'PENDING_CONFIRMATION'">
                  <button class="ghost-btn success-btn"
                    [disabled]="updatingOrderId === order.orderId"
                    (click)="acceptOrder(order.orderId)">
                    {{ updatingOrderId === order.orderId ? '...' : 'Accept' }}
                  </button>
                  <button class="ghost-btn danger-btn"
                    [disabled]="updatingOrderId === order.orderId"
                    (click)="rejectOrder(order.orderId)"
                    style="margin-left: 0.4rem;">
                    Reject
                  </button>
                </ng-container>
                <span *ngIf="order.orderStatus !== 'PENDING_CONFIRMATION'" class="muted">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <ng-template #sfLoading>
        <div class="panel loading">{{ storefrontLoading ? 'Loading storefront orders...' : 'No storefront orders yet.' }}</div>
      </ng-template>

    </div>
  `,
  styles: [`
    .success-btn   { color: #2d7a3a; border-color: #2d7a3a; }
    .danger-btn    { color: #b03030; border-color: #b03030; }
    .refunded-label{ color: #b03030; font-weight: 500; }
    .chip.success  { background: #e6f4ea; color: #2d7a3a; }
    .chip.danger   { background: #fdecea; color: #b03030; }
    .chip.warn     { background: #fff8e1; color: #7a5c00; }

    .modal-backdrop {
      position: fixed; inset: 0; background: rgba(0,0,0,0.45);
      display: flex; align-items: center; justify-content: center; z-index: 1000;
    }
    .modal-box {
      background: #fff; border-radius: 10px; padding: 1.5rem 2rem;
      min-width: 340px; max-width: 420px; width: 100%; box-shadow: 0 8px 32px rgba(0,0,0,0.18);
    }
    .modal-box h3 { margin: 0 0 0.25rem; }
    .field { margin: 1rem 0; display: flex; flex-direction: column; gap: 0.3rem; }
    .field label { font-size: 0.85rem; font-weight: 600; color: #444; }
    .field input {
      padding: 0.5rem 0.75rem; border: 1px solid #ccc; border-radius: 6px;
      font-size: 0.95rem; outline: none;
    }
    .field input:focus { border-color: #4a90e2; }
    .modal-actions { display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.25rem; }
    .error-text { color: #b03030; font-size: 0.85rem; margin: 0.5rem 0 0; }
  `]
})
export class OrdersPageComponent {
  private readonly api = inject(BusinessApiService);

  orders: BusinessOrder[] = [];
  ordersLoaded = false;
  storefrontOrders: StorefrontOrder[] = [];
  storefrontLoading = false;
  updatingOrderId: number | null = null;

  refundTarget: BusinessOrder | null = null;
  refundAmountInput: number | null = null;
  refundReasonInput = '';
  refunding = false;
  refundError: string | null = null;

  constructor() {
    this.loadOrders();
    this.loadStorefrontOrders();
  }

  loadOrders(): void {
    this.ordersLoaded = false;
    this.api.getOrders().subscribe({
      next: (data) => { this.orders = data; this.ordersLoaded = true; },
      error: () => { this.ordersLoaded = true; }
    });
  }

  loadStorefrontOrders(): void {
    this.storefrontLoading = true;
    this.api.getStorefrontOrders().subscribe({
      next: (data) => { this.storefrontOrders = data; this.storefrontLoading = false; },
      error: () => { this.storefrontLoading = false; }
    });
  }

  openRefund(order: BusinessOrder): void {
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
    this.api.refundOrder(this.refundTarget.orderId, {
      refundAmount: this.refundAmountInput,
      reason: this.refundReasonInput.trim() || 'Refund issued'
    }).subscribe({
      next: (updated) => {
        const idx = this.orders.findIndex(o => o.orderId === updated.orderId);
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

  acceptOrder(orderId: number): void { this.changeStatus(orderId, 'ACCEPTED'); }
  rejectOrder(orderId: number): void { this.changeStatus(orderId, 'REJECTED'); }

  private changeStatus(orderId: number, status: string): void {
    this.updatingOrderId = orderId;
    this.api.updateStorefrontOrderStatus(orderId, status).subscribe({
      next: () => { this.updatingOrderId = null; this.loadStorefrontOrders(); },
      error: () => { this.updatingOrderId = null; }
    });
  }

  formatCurrencyValue(value: number | null): string { return formatCurrency(value ?? 0); }
  formatDateValue(value: number | null): string { return formatDate(value); }
}
