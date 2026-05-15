import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { combineLatest, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMarketplaceSetup } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-business-dashboard-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell" *ngIf="dashboard() as data; else loading">
      <section class="panel page-hero">
        <h2>{{ data.shopName || 'Business Dashboard' }}</h2>
        <p class="muted">Owner and manager view with aligned KPIs, recent order visibility, setup confirmation, and practical next-step suggestions.</p>
        <div class="hero-meta">
          <span class="chip">Daily Revenue</span>
          <span class="chip">Order Health</span>
          <span class="chip success">Setup Confirmation</span>
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
          <h3>Pending POS Pay</h3>
          <strong>{{ data.pendingPosPayments }}</strong>
        </article>
          <article class="panel stat-card">
            <h3>Staff / Menu</h3>
            <strong>{{ data.totalStaff }} / {{ data.totalMenuItems }}</strong>
          </article>
          <article class="panel stat-card" *ngIf="data.subMerchantSetup as setup">
            <h3>Settlement Status</h3>
            <strong>
              <span class="chip"
                [class.warn]="setup.subMerchantStatus === 'PENDING' || setup.subMerchantStatus === 'KYC_SUBMITTED' || !setup.subMerchantStatus"
                [class.success]="setup.subMerchantStatus === 'ACTIVE'"
                [class.danger]="setup.subMerchantStatus === 'REJECTED' || setup.subMerchantStatus === 'SUSPENDED'">
                {{ setup.subMerchantStatus || 'NOT_STARTED' }}
              </span>
            </strong>
            <p class="muted" style="margin:0.35rem 0 0; font-size:0.85rem;" *ngIf="setup.kycPortalUrl">
              KYC: <a [href]="setup.kycPortalUrl" target="_blank" rel="noopener noreferrer" style="color:var(--brand);">Portal</a>
            </p>
          </article>

        <section class="panel soft-section" style="grid-column: 1 / -1;">
          <div class="section-head">
            <div>
              <h3>Setup Confirmation</h3>
              <p class="muted">Confirm the business is ready for website orders, kitchen handling, and marketplace intake.</p>
            </div>
          </div>
          <div class="suggestion-grid">
            <article class="suggestion-card" *ngFor="let item of data.setupChecks">
              <div class="setup-head">
                <h3>{{ item.label }}</h3>
                <span class="chip" [class.success]="item.ready" [class.warn]="!item.ready">
                  {{ item.ready ? 'Ready' : 'Pending' }}
                </span>
              </div>
              <p>{{ item.detail }}</p>
            </article>
          </div>
        </section>

        <section class="panel soft-section" style="grid-column: 1 / -1;">
          <div class="section-head">
            <div>
              <h3>Suggested Next Steps</h3>
              <p class="muted">Quick checks based on the metrics and setup state on this page.</p>
            </div>
          </div>
          <div class="suggestion-grid">
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
            <p class="muted">Latest POS activity.</p>
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
  `,
  styles: [`
    .setup-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      margin-bottom: 0.35rem;
    }

    .setup-head h3 {
      margin: 0;
    }
  `]
})
export class BusinessDashboardPageComponent {
  private readonly api = inject(BusinessApiService);

  readonly dashboard = toSignal(
    combineLatest([
      this.api.getDashboard(),
      this.api.getMarketplaceConfig().pipe(catchError(() => of(null))),
      this.api.getMarketplaceSetup().pipe(catchError(() => of(null as BusinessMarketplaceSetup | null)))
    ]).pipe(
      map(([data, marketplace, marketplaceSetup]) => ({
        ...data,
        totalRevenueFormatted: formatCurrency(data.totalRevenue),
        todayRevenueFormatted: formatCurrency(data.todayRevenue),
        refundedAmountFormatted: formatCurrency(data.refundedAmount),
        setupChecks: [
          {
            label: 'Website Checkout',
            ready: data.websiteEnabled,
            detail: data.websiteEnabled
              ? 'Website ordering is enabled for this business.'
              : 'Enable own website checkout before expecting direct online orders.'
          },
          {
            label: 'Customer Printer',
            ready: data.printerEnabled,
            detail: data.printerEnabled
              ? 'Customer receipt printing is configured.'
              : 'Configure the customer printer to avoid manual receipt handling.'
          },
          {
            label: 'Kitchen KDS Printer',
            ready: data.kitchenPrinterEnabled,
            detail: data.kitchenPrinterEnabled
              ? 'Kitchen printing is configured for KDS dispatch.'
              : 'Configure the kitchen printer so accepted online orders can print instantly.'
          },
          {
            label: 'Marketplace Intake',
            ready: Boolean(marketplace?.zomatoEnabled || marketplace?.swiggyEnabled),
            detail: marketplace?.zomatoEnabled || marketplace?.swiggyEnabled
              ? `Marketplace config active: ${[
                  marketplace?.zomatoEnabled ? 'Zomato' : '',
                  marketplace?.swiggyEnabled ? 'Swiggy' : ''
                ].filter(Boolean).join(' + ')}.`
              : 'No Zomato or Swiggy marketplace channel is enabled yet.'
          },
          {
            label: 'Operating Baseline',
            ready: data.totalStaff > 0 && data.totalMenuItems > 0,
            detail: data.totalStaff > 0 && data.totalMenuItems > 0
              ? 'The business has staff access and menu data in place.'
              : 'Add at least one staff account and one menu item to complete setup.'
          }
        ],
        subMerchantSetup: marketplaceSetup
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
