import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessOrder, StorefrontOrder } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-orders-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">

      <!-- POS + All Orders -->
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
              <th>Created</th>
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
              <td>{{ order.orderStatus }}</td>
              <td>{{ order.paymentMethod }} / {{ order.paymentStatus }}</td>
              <td>{{ formatCurrencyValue(order.totalAmount) }}</td>
              <td>{{ formatDateValue(order.createdAt) }}</td>
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
    .success-btn { color: #2d7a3a; border-color: #2d7a3a; }
    .danger-btn  { color: #b03030; border-color: #b03030; }
  `]
})
export class OrdersPageComponent {
  private readonly api = inject(BusinessApiService);

  orders: BusinessOrder[] = [];
  ordersLoaded = false;
  storefrontOrders: StorefrontOrder[] = [];
  storefrontLoading = false;
  updatingOrderId: number | null = null;

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

  acceptOrder(orderId: number): void { this.changeStatus(orderId, 'ACCEPTED'); }
  rejectOrder(orderId: number): void { this.changeStatus(orderId, 'REJECTED'); }

  private changeStatus(orderId: number, status: string): void {
    this.updatingOrderId = orderId;
    this.api.updateStorefrontOrderStatus(orderId, status).subscribe({
      next: () => { this.updatingOrderId = null; this.loadStorefrontOrders(); },
      error: () => { this.updatingOrderId = null; }
    });
  }

  formatCurrencyValue(value: number): string { return formatCurrency(value); }
  formatDateValue(value: number | null): string { return formatDate(value); }
}
