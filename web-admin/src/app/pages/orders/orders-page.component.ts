import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessOrder } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-orders-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <div class="toolbar">
        <div>
          <h2>Orders</h2>
          <p class="muted">Combined POS and online storefront order list.</p>
        </div>
        <button class="ghost-btn" (click)="loadOrders()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="orders.length; else loading">
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

      <ng-template #loading>
        <div class="panel loading">Loading orders...</div>
      </ng-template>
    </div>
  `
})
export class OrdersPageComponent {
  private readonly api = inject(BusinessApiService);

  orders: BusinessOrder[] = [];

  constructor() {
    this.loadOrders();
  }

  loadOrders(): void {
    this.api.getOrders().subscribe((data) => {
      this.orders = data;
    });
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }
}
