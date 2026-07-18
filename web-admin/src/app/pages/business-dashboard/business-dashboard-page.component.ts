import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { combineLatest, of, Subject } from 'rxjs';
import { catchError, map, switchMap, startWith } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { BusinessApiService } from '../../core/services/business-api.service';
import { OrderDetailResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';

@Component({
  selector: 'app-business-dashboard-page',
  standalone: true,
  imports: [CommonModule, DateRangeSelectorComponent, OrderDetailModalComponent],
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

      <section class="panel toolbar">
        <div class="toolbar__left">
          <app-date-range-selector
            [initialRange]="selectedDateRange()"
            (rangeChanged)="onDateRangeChanged($event)">
          </app-date-range-selector>
        </div>
        <div class="toolbar__right">
          <button class="primary-btn" (click)="refresh()" [disabled]="isRefreshing()">
            {{ isRefreshing() ? 'Refreshing...' : 'Refresh' }}
          </button>
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
        <article class="panel stat-card stat-card--clickable" (click)="navigateToOrders()" role="button" tabindex="0" (keydown.enter)="navigateToOrders()">
          <h3>POS Orders</h3>
          <strong>{{ data.posOrderCount }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Pending POS Pay</h3>
          <strong>{{ data.pendingPosPayments }}</strong>
        </article>
        <article class="panel stat-card stat-card--clickable" (click)="navigateToStaff()" role="button" tabindex="0" (keydown.enter)="navigateToStaff()">
          <h3>Staff / Menu</h3>
          <strong>{{ data.totalStaff }} / {{ data.totalMenuItems }}</strong>
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
              <tr *ngFor="let order of data.recentOrders" class="clickable-row" (click)="openOrderDetail(order.orderId)">
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
        <div class="panel loading">
          <ng-container *ngIf="dashboardError(); else loadingMessage">
            <p>{{ dashboardError() }}</p>
            <button class="primary-btn" (click)="refresh()">Retry</button>
          </ng-container>
          <ng-template #loadingMessage>Loading business dashboard...</ng-template>
        </div>
      </div>
    </ng-template>

    <app-order-detail-modal
      [order]="selectedOrderDetail()"
      (closed)="closeOrderDetail()">
    </app-order-detail-modal>
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

    .toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }

    .toolbar__left {
      flex: 1;
      min-width: 0;
    }

    .toolbar__right {
      flex-shrink: 0;
    }

    .stat-card--clickable {
      cursor: pointer;
      transition: box-shadow 0.2s, transform 0.15s;
    }

    .stat-card--clickable:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      transform: translateY(-1px);
    }

    .stat-card--clickable:active {
      transform: translateY(0);
    }

    .clickable-row {
      cursor: pointer;
      transition: background 0.15s;
    }

    .clickable-row:hover {
      background: var(--bg, #f6f1e8);
    }

    @media (max-width: 480px) {
      .toolbar {
        flex-direction: column;
        align-items: stretch;
      }

      .toolbar__right {
        align-self: flex-end;
      }
    }
  `]
})
export class BusinessDashboardPageComponent {
  private static readonly RANGE_STORAGE_KEY = 'business-dashboard-date-range';
  private readonly api = inject(BusinessApiService);
  private readonly router = inject(Router);

  private readonly refresh$ = new Subject<void>();

  readonly selectedDateRange = signal<{ from: string; to: string } | null>(this.readStoredRange());
  readonly isRefreshing = signal(false);
  readonly dashboardError = signal('');
  readonly selectedOrderDetail = signal<OrderDetailResponse | null>(null);

  readonly dashboard = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => {
        this.isRefreshing.set(true);
        this.dashboardError.set('');
        const range = this.selectedDateRange();
        const from = range?.from;
        const to = range?.to;
        return combineLatest([
          this.api.getDashboard(from, to),
          this.api.getMarketplaceConfig().pipe(catchError(() => of(null)))
        ]).pipe(
          map(([data, marketplace]) => {
            this.isRefreshing.set(false);
            this.dashboardError.set('');
            return {
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
              ]
            };
          }),
          catchError((error: unknown) => {
            this.isRefreshing.set(false);
            const response = error as { error?: { message?: string; error?: string } };
            this.dashboardError.set(
              response.error?.message || response.error?.error || 'Unable to load the dashboard.'
            );
            return of(null);
          })
        );
      })
    )
  );

  onDateRangeChanged(range: { from: string; to: string }): void {
    this.selectedDateRange.set(range);
    sessionStorage.setItem(BusinessDashboardPageComponent.RANGE_STORAGE_KEY, JSON.stringify(range));
    this.refresh$.next();
  }

  refresh(): void {
    this.refresh$.next();
  }

  private readStoredRange(): { from: string; to: string } | null {
    try {
      const value = sessionStorage.getItem(BusinessDashboardPageComponent.RANGE_STORAGE_KEY);
      if (!value) return null;
      const range = JSON.parse(value) as { from?: string; to?: string };
      const datePattern = /^\d{4}-\d{2}-\d{2}$/;
      return range.from && range.to && datePattern.test(range.from) && datePattern.test(range.to)
        ? { from: range.from, to: range.to }
        : null;
    } catch {
      return null;
    }
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }

  navigateToOrders(): void {
    this.router.navigate(['/business/orders']);
  }

  navigateToStaff(): void {
    this.router.navigate(['/business/staff']);
  }

  openOrderDetail(orderId: number): void {
    this.api.getOrderDetail(orderId).subscribe({
      next: (detail) => this.selectedOrderDetail.set(detail),
      error: () => {} // silently handle – could add toast later
    });
  }

  closeOrderDetail(): void {
    this.selectedOrderDetail.set(null);
  }
}
