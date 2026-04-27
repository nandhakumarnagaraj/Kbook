import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-business-dashboard-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell" *ngIf="dashboard() as data; else loading">
      <div class="section-head">
        <div>
          <h2>{{ data.shopName || 'Business Dashboard' }}</h2>
          <p class="muted">Owner and manager summary view.</p>
        </div>
      </div>

      <div class="stats-grid">
        <article class="panel stat-card">
          <h3>Total Revenue</h3>
          <strong>{{ data.totalRevenueFormatted }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Today Revenue</h3>
          <strong>{{ data.todayRevenueFormatted }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Refunded Amount</h3>
          <strong>{{ data.refundedAmountFormatted }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Refunded Orders</h3>
          <strong>{{ data.refundedOrders }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>POS Orders</h3>
          <strong>{{ data.posOrderCount }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Online Orders</h3>
          <strong>{{ data.onlineOrderCount }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Pending Online</h3>
          <strong>{{ data.pendingOnlineOrders }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Staff / Menu</h3>
          <strong>{{ data.totalStaff }} / {{ data.totalMenuItems }}</strong>
        </article>
      </div>

      <section class="panel page-shell" style="margin-top: 1rem;">
        <div class="section-head">
          <div>
            <h3>Recent Orders</h3>
            <p class="muted">Latest POS and storefront activity.</p>
          </div>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Source</th>
                <th>Order</th>
                <th>Customer</th>
                <th>Status</th>
                <th>Total</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let order of data.recentOrders">
                <td><span class="chip">{{ order.sourceType }}</span></td>
                <td>{{ order.orderCode }}</td>
                <td>{{ order.customerName || '-' }}</td>
                <td>{{ order.orderStatus }}</td>
                <td>{{ formatCurrencyValue(order.totalAmount) }}</td>
                <td>{{ formatDateValue(order.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <ng-template #loading>
      <div class="page-shell">
        <div class="panel loading">Loading business dashboard...</div>
      </div>
    </ng-template>
  `
})
export class BusinessDashboardPageComponent {
  private readonly api = inject(BusinessApiService);

  readonly dashboard = toSignal(
    this.api.getDashboard().pipe(
      map((data) => ({
        ...data,
        totalRevenueFormatted: formatCurrency(data.totalRevenue),
        todayRevenueFormatted: formatCurrency(data.todayRevenue),
        refundedAmountFormatted: formatCurrency(data.refundedAmount)
      }))
    )
  );

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }
}
