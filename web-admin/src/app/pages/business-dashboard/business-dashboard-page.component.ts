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
      <section class="panel page-hero">
        <h2>{{ data.shopName || 'Business Dashboard' }}</h2>
        <p class="muted">Owner and manager view with aligned KPIs, recent order visibility, and practical next-step suggestions.</p>
        <div class="hero-meta">
          <span class="chip">Daily Revenue</span>
          <span class="chip">Order Health</span>
          <span class="chip success">Staff and Menu Coverage</span>
        </div>
      </section>

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
          <h3>Pending POS Pay</h3>
          <strong>{{ data.pendingPosPayments }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Staff / Menu</h3>
          <strong>{{ data.totalStaff }} / {{ data.totalMenuItems }}</strong>
        </article>
        <section class="panel soft-section" style="grid-column: 1 / -1;">
          <div class="section-head">
            <div>
              <h3>Suggested Next Steps</h3>
              <p class="muted">Quick checks based on the metrics on this page.</p>
            </div>
          </div>
          <div class="suggestion-grid">
            <article class="suggestion-card">
              <h3>Review Pending Online Orders</h3>
              <p>Use the orders page to clear pending online requests quickly so storefront customers do not stall at confirmation.</p>
              <span class="chip warn">{{ data.pendingOnlineOrders }} pending</span>
            </article>
            <article class="suggestion-card">
              <h3>Resume Pending POS Payments</h3>
              <p>Draft POS bills with pending payments should be resumed or cancelled before starting new gateway attempts.</p>
              <span class="chip warn">{{ data.pendingPosPayments }} pending</span>
            </article>
            <article class="suggestion-card">
              <h3>Balance Menu Against Sales</h3>
              <p>Check whether menu size is growing faster than order volume. That is usually a sign the catalog needs cleanup.</p>
              <span class="chip">{{ data.totalMenuItems }} items listed</span>
            </article>
            <article class="suggestion-card">
              <h3>Watch Refund Frequency</h3>
              <p>Refund spikes are easier to catch here than after reconciliation. Verify causes before they repeat.</p>
              <span class="chip danger">{{ data.refundedOrders }} refunded</span>
            </article>
          </div>
        </section>
      </div>

      <section class="panel soft-section">
        <div class="section-head">
          <div>
            <h3>Recent Orders</h3>
            <p class="muted">Latest POS and storefront activity.</p>
          </div>
        </div>

        <div class="table-wrap">
          <table class="data-table">
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
