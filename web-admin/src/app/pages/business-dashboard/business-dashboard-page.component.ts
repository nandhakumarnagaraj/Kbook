import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { combineLatest, of, Subject } from 'rxjs';
import { catchError, map, switchMap, startWith } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';
import { OrderDetailResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';

@Component({
  selector: 'app-business-dashboard-page',
  standalone: true,
  imports: [CommonModule, DateRangeSelectorComponent, OrderDetailModalComponent, EmptyStateComponent],
  template: `
    <div class="page-shell" *ngIf="dashboard() as data; else loading">
      <section class="panel page-hero">
        <h2>{{ data.shopName || 'Business Dashboard' }}</h2>
        <p class="muted">Revenue, order health, and operational readiness in one view.</p>
        <div class="hero-meta">
          <span class="chip success">Owner Overview</span>
        </div>
      </section>

      <section class="panel dashboard-overview" aria-labelledby="business-overview-title">
        <div class="overview-header">
          <div>
            <span class="eyebrow">Performance</span>
            <h3 id="business-overview-title">Business at a glance</h3>
          </div>
          <div class="overview-controls">
            <app-date-range-selector
              [initialRange]="selectedDateRange()"
              (rangeChanged)="onDateRangeChanged($event)">
            </app-date-range-selector>
            <button class="ghost-btn" (click)="refresh()" [disabled]="isRefreshing()">
              {{ isRefreshing() ? 'Refreshing...' : 'Refresh' }}
            </button>
          </div>
        </div>

        <div class="primary-kpi-grid" aria-label="Primary business metrics">
          <article class="kpi-card kpi-card--primary">
            <span class="kpi-label">Today Revenue</span>
            <strong>{{ data.todayRevenueFormatted }}</strong>
            <span class="kpi-caption">Revenue recognized today</span>
          </article>
          <article class="kpi-card kpi-card--primary">
            <span class="kpi-label">Total Revenue</span>
            <strong>{{ data.totalRevenueFormatted }}</strong>
            <span class="kpi-caption">For the selected period</span>
          </article>
          <button type="button" class="kpi-card kpi-card--primary kpi-card--clickable" (click)="navigateToOrders()">
            <span class="kpi-label">POS Orders</span>
            <strong>{{ data.posOrderCount }}</strong>
            <span class="kpi-action">View orders →</span>
          </button>
          <button
            type="button"
            class="kpi-card kpi-card--primary kpi-card--clickable"
            [class.kpi-card--attention]="data.pendingPosPayments > 0"
            (click)="navigateToOrders()">
            <span class="kpi-label">Pending Payments</span>
            <strong>{{ data.pendingPosPayments }}</strong>
            <span class="kpi-action">{{ data.pendingPosPayments ? 'Review pending →' : 'Nothing pending' }}</span>
          </button>
        </div>

        <div class="secondary-kpi-grid" aria-label="Secondary business metrics">
          <article class="kpi-card kpi-card--secondary">
            <span class="kpi-label">Refunds</span>
            <strong>{{ data.refundedAmountFormatted }}</strong>
            <span class="kpi-caption">{{ data.refundedOrders }} orders</span>
          </article>
          <button type="button" class="kpi-card kpi-card--secondary kpi-card--clickable" (click)="navigateToStaff()">
            <span class="kpi-label">Staff</span>
            <strong>{{ data.totalStaff }}</strong>
            <span class="kpi-action">Manage staff →</span>
          </button>
          <button type="button" class="kpi-card kpi-card--secondary kpi-card--clickable" (click)="navigateToMenu()">
            <span class="kpi-label">Menu</span>
            <strong>{{ data.totalMenuItems }}</strong>
            <span class="kpi-action">Manage menu →</span>
          </button>
          <button
            type="button"
            class="kpi-card kpi-card--secondary kpi-card--clickable"
            [class.kpi-card--attention]="getReadySetupCount(data.setupChecks) < data.setupChecks.length"
            (click)="scrollToSetup()">
            <span class="kpi-label">Setup</span>
            <strong>{{ getReadySetupCount(data.setupChecks) }}/{{ data.setupChecks.length }}</strong>
            <span class="kpi-action">
              {{ getReadySetupCount(data.setupChecks) < data.setupChecks.length ? 'Finish setup →' : 'Setup complete' }}
            </span>
          </button>
        </div>
      </section>

      <section id="setup-checklist" class="panel setup-panel" aria-labelledby="setup-checklist-title">
        <div class="section-head setup-panel__head">
          <div>
            <span class="eyebrow">Readiness</span>
            <h3 id="setup-checklist-title">Setup Checklist</h3>
            <p class="muted">Complete the remaining items needed for daily operations.</p>
          </div>
          <span class="setup-progress">{{ getReadySetupCount(data.setupChecks) }} of {{ data.setupChecks.length }} ready</span>
        </div>
        <ol class="setup-list" aria-label="Business setup checklist">
          <li
            class="setup-row"
            *ngFor="let item of data.setupChecks; let index = index"
            [attr.aria-current]="isNextIncomplete(data.setupChecks, index) ? 'step' : null">
            <span class="setup-dot" [class.setup-dot--ready]="item.ready" aria-hidden="true"></span>
            <div class="setup-copy">
              <div class="setup-title">
                <h4>{{ item.label }}</h4>
                <span class="chip" [class.success]="item.ready" [class.warn]="!item.ready">
                  {{ item.ready ? 'Ready' : 'Pending' }}
                </span>
              </div>
              <p>{{ item.detail }}</p>
            </div>
            <button
              *ngIf="setupAction(item.label) as action"
              type="button"
              class="setup-action"
              (click)="navigateTo(action.route)">
              {{ action.label }} →
            </button>
          </li>
        </ol>
      </section>

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
              <tr
                *ngFor="let order of data.recentOrders"
                class="clickable-row"
                tabindex="0"
                role="button"
                [attr.aria-label]="'View order ' + order.orderCode"
                (click)="openOrderDetail(order.orderId)"
                (keydown.enter)="openOrderDetail(order.orderId)">
                <td><span class="chip">{{ order.sourceType }}</span></td>
                <td>{{ order.orderCode }}</td>
                <td>{{ order.customerName || '-' }}</td>
                <td>
                  <span
                    class="chip"
                    [class.success]="order.orderStatus.toLowerCase() === 'completed'"
                    [class.danger]="order.orderStatus.toLowerCase() === 'cancelled'"
                    [class.warn]="order.orderStatus.toLowerCase() === 'draft'">
                    {{ order.orderStatus }}
                  </span>
                </td>
                <td>{{ formatCurrencyValue(order.totalAmount) }}</td>
                <td>{{ formatDateValue(order.createdAt) }}</td>
              </tr>
              <tr *ngIf="!data.recentOrders || data.recentOrders.length === 0">
                <td colspan="6">
                  <app-empty-state
                    icon="🧾"
                    title="No recent orders yet"
                    text="New POS and online orders will show up here as they come in."
                  ></app-empty-state>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <ng-template #loading>
      <div class="page-shell" *ngIf="dashboardError(); else dashboardSkeleton">
        <div class="panel loading">
          <p>{{ dashboardError() }}</p>
          <button class="primary-btn" (click)="refresh()">Retry</button>
        </div>
      </div>
      <ng-template #dashboardSkeleton>
        <div class="page-shell">
          <div class="skeleton skeleton-stat" style="height: 96px;"></div>
          <div class="stats-grid">
            <div class="skeleton skeleton-stat" *ngFor="let i of [1,2,3,4,5,6]"></div>
          </div>
          <div class="skeleton skeleton-row" style="height: 220px;"></div>
        </div>
      </ng-template>
    </ng-template>

    <app-order-detail-modal
      [order]="selectedOrderDetail()"
      (closed)="closeOrderDetail()">
    </app-order-detail-modal>
  `,
  styles: [`
    .dashboard-overview { padding: clamp(1rem, 2vw, 1.5rem); }
    .overview-header {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1.25rem;
    }
    .overview-header h3 { margin: 0.2rem 0 0; font-size: 1.25rem; }
    .eyebrow {
      color: var(--brand-deep);
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.09em;
      text-transform: uppercase;
    }
    .overview-controls { display: flex; align-items: center; justify-content: flex-end; gap: 0.75rem; flex-wrap: wrap; }
    .primary-kpi-grid, .secondary-kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0.9rem; }
    .secondary-kpi-grid { margin-top: 0.9rem; }
    .kpi-card {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      justify-content: center;
      min-width: 0;
      padding: 1rem;
      color: var(--ink);
      text-align: left;
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 10px;
      box-shadow: var(--shadow);
    }
    button.kpi-card { width: 100%; font: inherit; cursor: pointer; }
    .kpi-card--primary { min-height: 132px; }
    .kpi-card--secondary { min-height: 96px; }
    .kpi-card--primary strong { margin: 0.55rem 0 0.35rem; font-size: clamp(1.7rem, 2.5vw, 2rem); line-height: 1; }
    .kpi-card--secondary strong { margin: 0.4rem 0 0.25rem; font-size: 1.35rem; line-height: 1.1; }
    .kpi-label { color: var(--muted); font-size: 0.76rem; font-weight: 800; letter-spacing: 0.06em; text-transform: uppercase; }
    .kpi-caption, .kpi-action { color: var(--muted); font-size: 0.8rem; line-height: 1.35; }
    .kpi-action { color: var(--brand-deep); font-weight: 700; }
    .kpi-card--clickable { transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease; }
    .kpi-card--clickable:hover { border-color: var(--brand); box-shadow: var(--shadow-md); transform: translateY(-2px); }
    .kpi-card--clickable:focus-visible { outline: 3px solid var(--brand-soft); outline-offset: 2px; }
    .kpi-card--attention { border-color: rgba(181, 106, 45, 0.5); background: linear-gradient(135deg, var(--panel), var(--brand-soft)); }
    .setup-panel { padding: clamp(1rem, 2vw, 1.5rem); }
    .setup-panel__head { align-items: center; margin-bottom: 1rem; }
    .setup-panel__head h3 { margin: 0.2rem 0; }
    .setup-progress { padding: 0.5rem 0.8rem; color: var(--brand-deep); background: var(--brand-soft); border-radius: 999px; font-size: 0.8rem; font-weight: 800; white-space: nowrap; }
    .setup-list { margin: 0; padding: 0; list-style: none; border: 1px solid var(--line); border-radius: 10px; overflow: hidden; }
    .setup-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 0.85rem; padding: 0.9rem 1rem; background: var(--panel); }
    .setup-row + .setup-row { border-top: 1px solid var(--line); }
    .setup-dot { width: 0.65rem; height: 0.65rem; background: var(--brand); border-radius: 50%; box-shadow: 0 0 0 4px var(--brand-soft); }
    .setup-dot--ready { background: var(--success); box-shadow: 0 0 0 4px var(--success-soft); }
    .setup-title { display: flex; align-items: center; gap: 0.55rem; flex-wrap: wrap; }
    .setup-title h4 { margin: 0; font-size: 0.93rem; }
    .setup-copy p { margin: 0.22rem 0 0; color: var(--muted); font-size: 0.82rem; line-height: 1.4; }
    .setup-action { min-height: 38px; padding: 0.45rem 0.7rem; color: var(--brand-deep); background: transparent; border: 1px solid var(--line); border-radius: 8px; font-weight: 700; cursor: pointer; white-space: nowrap; }
    .setup-action:hover { border-color: var(--brand); background: var(--brand-soft); }
    @media (max-width: 1100px) {
      .overview-header { align-items: flex-start; flex-direction: column; }
      .overview-controls { justify-content: flex-start; width: 100%; }
      .primary-kpi-grid, .secondary-kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }
    @media (max-width: 640px) {
      .primary-kpi-grid, .secondary-kpi-grid { grid-template-columns: 1fr; }
      .overview-controls { align-items: stretch; flex-direction: column; }
      .overview-controls .ghost-btn { width: 100%; }
      .setup-panel__head { align-items: flex-start; }
      .setup-row { grid-template-columns: auto minmax(0, 1fr); }
      .setup-action { grid-column: 2; width: 100%; }
    }
  `]
})
export class BusinessDashboardPageComponent {
  private static readonly RANGE_STORAGE_KEY = 'business-dashboard-date-range';
  private readonly api = inject(BusinessApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

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

  navigateToMenu(): void {
    this.router.navigate(['/business/menu']);
  }

  navigateTo(route: string): void {
    this.router.navigate([route]);
  }

  setupAction(label: string): { label: string; route: string } | null {
    if (label === 'Marketplace Intake') {
      return { label: 'Open integrations', route: '/business/marketplace' };
    }
    return null;
  }

  getReadySetupCount(checks: Array<{ ready: boolean }>): number {
    return checks.filter((item) => item.ready).length;
  }

  isNextIncomplete(checks: Array<{ ready: boolean }>, index: number): boolean {
    return !checks[index]?.ready && checks.slice(0, index).every((item) => item.ready);
  }

  scrollToSetup(): void {
    document.getElementById('setup-checklist')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  openOrderDetail(orderId: number): void {
    this.api.getOrderDetail(orderId).subscribe({
      next: (detail) => this.selectedOrderDetail.set(detail),
      error: () => this.toast.show('Unable to load order details.', 'error')
    });
  }

  closeOrderDetail(): void {
    this.selectedOrderDetail.set(null);
  }
}
