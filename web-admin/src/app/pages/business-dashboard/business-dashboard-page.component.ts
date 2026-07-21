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
      <section class="page-header">
        <div>
          <span class="eyebrow">Owner overview</span>
          <h2>{{ data.shopName || 'Business Dashboard' }}</h2>
          <p class="muted">Revenue, order health, and operational readiness in one view.</p>
        </div>
        <div class="header-controls">
          <app-date-range-selector
            [initialRange]="selectedDateRange()"
            (rangeChanged)="onDateRangeChanged($event)">
          </app-date-range-selector>
          <button class="ghost-btn" (click)="refresh()" [disabled]="isRefreshing()">
            {{ isRefreshing() ? 'Refreshing…' : 'Refresh' }}
          </button>
        </div>
      </section>

      <section class="kpi-primary" aria-label="Primary business metrics">
        <article class="kpi-card kpi-card--hero">
          <span class="kpi-label">Today Revenue</span>
          <strong class="kpi-value">{{ data.todayRevenueFormatted }}</strong>
          <span class="kpi-foot">Recognized today</span>
        </article>
        <article class="kpi-card">
          <span class="kpi-label">Total Revenue</span>
          <strong class="kpi-value">{{ data.totalRevenueFormatted }}</strong>
          <span class="kpi-foot">For the selected period</span>
        </article>
        <button type="button" class="kpi-card kpi-card--clickable" (click)="navigateToOrders()">
          <span class="kpi-label">POS Orders</span>
          <strong class="kpi-value">{{ data.posOrderCount }}</strong>
          <span class="kpi-action">View orders →</span>
        </button>
        <button
          type="button"
          class="kpi-card kpi-card--clickable"
          [class.kpi-card--warn]="data.pendingPosPayments > 0"
          (click)="navigateToOrders()">
          <span class="kpi-label">Pending Payments</span>
          <strong class="kpi-value">{{ data.pendingPosPayments }}</strong>
          <span class="kpi-action">{{ data.pendingPosPayments ? 'Review pending →' : 'Nothing pending' }}</span>
        </button>
      </section>

      <section class="kpi-secondary" aria-label="Secondary business metrics">
        <div class="kpi-mini">
          <span class="kpi-mini-label">Refunds</span>
          <span class="kpi-mini-value">{{ data.refundedAmountFormatted }}</span>
          <span class="kpi-mini-foot">{{ data.refundedOrders }} orders</span>
        </div>
        <button type="button" class="kpi-mini kpi-mini--btn" (click)="navigateToStaff()">
          <span class="kpi-mini-label">Staff</span>
          <span class="kpi-mini-value">{{ data.totalStaff }}</span>
          <span class="kpi-mini-foot">Manage →</span>
        </button>
        <button type="button" class="kpi-mini kpi-mini--btn" (click)="navigateToMenu()">
          <span class="kpi-mini-label">Menu</span>
          <span class="kpi-mini-value">{{ data.totalMenuItems }}</span>
          <span class="kpi-mini-foot">Manage →</span>
        </button>
        <button
          type="button"
          class="kpi-mini kpi-mini--btn"
          [class.kpi-mini--attention]="getReadySetupCount(data.setupChecks) < data.setupChecks.length"
          (click)="scrollToSetup()">
          <span class="kpi-mini-label">Setup</span>
          <span class="kpi-mini-value">{{ getReadySetupCount(data.setupChecks) }}/{{ data.setupChecks.length }}</span>
          <span class="kpi-mini-foot">
            {{ getReadySetupCount(data.setupChecks) < data.setupChecks.length ? 'Finish setup →' : 'Complete' }}
          </span>
        </button>
      </section>

      <section id="setup-checklist" class="section" aria-labelledby="setup-checklist-title">
        <header class="section-head">
          <div>
            <span class="eyebrow">Readiness</span>
            <h3 id="setup-checklist-title">Setup Checklist</h3>
            <p class="muted">Complete the remaining items needed for daily operations.</p>
          </div>
          <span class="progress-pill">{{ getReadySetupCount(data.setupChecks) }} of {{ data.setupChecks.length }} ready</span>
        </header>
        <ol class="setup-list" aria-label="Business setup checklist">
          <li
            class="setup-row"
            *ngFor="let item of data.setupChecks; let index = index"
            [attr.aria-current]="isNextIncomplete(data.setupChecks, index) ? 'step' : null"
            [class.is-ready]="item.ready">
            <span class="setup-dot" [class.setup-dot--ready]="item.ready" aria-hidden="true">
              <svg *ngIf="item.ready" viewBox="0 0 12 12" width="12" height="12"><path d="M2 6.5L4.5 9L10 3.5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </span>
            <div class="setup-copy">
              <div class="setup-title">
                <h4>{{ item.label }}</h4>
                <span class="chip-pill" [class.chip-pill--ok]="item.ready" [class.chip-pill--pending]="!item.ready">
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

      <section class="section">
        <header class="section-head">
          <div>
            <h3>Recent Orders</h3>
            <p class="muted">Latest POS activity.</p>
          </div>
        </header>
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
                <td><span class="tag tag--muted">{{ order.sourceType }}</span></td>
                <td class="mono">{{ order.orderCode }}</td>
                <td>{{ order.customerName || '—' }}</td>
                <td>
                  <span
                    class="chip-pill"
                    [class.chip-pill--ok]="order.orderStatus.toLowerCase() === 'completed'"
                    [class.chip-pill--danger]="order.orderStatus.toLowerCase() === 'cancelled'"
                    [class.chip-pill--pending]="order.orderStatus.toLowerCase() === 'draft'">
                    {{ order.orderStatus }}
                  </span>
                </td>
                <td class="mono">{{ formatCurrencyValue(order.totalAmount) }}</td>
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
          <div class="skeleton skeleton-stat" style="height: 84px;"></div>
          <div class="kpi-primary">
            <div class="skeleton skeleton-stat" *ngFor="let i of [1,2,3,4]"></div>
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
    :host { display: block; }
    .page-shell { display: grid; gap: 1.5rem; }

    .page-header {
      display: flex; justify-content: space-between; align-items: end; gap: 1rem; flex-wrap: wrap;
    }
    .page-header h2 { margin: 0.25rem 0 0.35rem; font-size: 1.75rem; letter-spacing: -0.01em; }
    .page-header p { margin: 0; }
    .eyebrow { text-transform: uppercase; letter-spacing: 0.08em; font-size: 0.72rem; font-weight: 700; color: var(--brand, #d97706); }
    .header-controls { display: flex; align-items: center; gap: 0.65rem; flex-wrap: wrap; }
    @media (max-width: 720px) {
      .page-header { flex-direction: column; align-items: stretch; }
      .header-controls { justify-content: flex-start; }
      .header-controls .ghost-btn { flex: 1; }
    }

    .kpi-primary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; }
    @media (max-width: 1100px) { .kpi-primary { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 560px)  { .kpi-primary { grid-template-columns: 1fr; } }

    .kpi-card {
      background: #fff; border: 1px solid var(--line, #e6e4df);
      border-radius: 14px; padding: 1.15rem 1.25rem;
      display: grid; gap: 0.35rem; text-align: left; color: inherit; font: inherit;
      transition: border-color .18s, transform .18s, box-shadow .18s;
    }
    button.kpi-card { cursor: pointer; }
    .kpi-card--hero { background: linear-gradient(160deg, #fff7ed 0%, #ffffff 60%); border-color: #fed7aa; }
    .kpi-card--hero .kpi-value { font-size: 2.1rem; color: #7c2d12; }
    .kpi-card--warn { border-color: #fecaca; background: linear-gradient(160deg, #fef2f2 0%, #ffffff 60%); }
    .kpi-card--clickable:hover { transform: translateY(-1px); box-shadow: 0 12px 28px -20px rgba(217,119,6,0.4); border-color: #fed7aa; }
    .kpi-card--clickable:focus-visible { outline: 3px solid rgba(217,119,6,0.25); outline-offset: 2px; }

    .kpi-label { font-size: 0.78rem; color: var(--muted, #6b7280); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600; }
    .kpi-value { font-size: 1.85rem; font-weight: 700; color: var(--ink, #111827); letter-spacing: -0.01em; font-variant-numeric: tabular-nums; }
    .kpi-foot { font-size: 0.82rem; color: var(--muted, #6b7280); }
    .kpi-action { font-size: 0.82rem; color: #b45309; font-weight: 600; }

    .kpi-secondary {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 0;
      background: #fff; border: 1px solid var(--line, #e6e4df);
      border-radius: 12px; overflow: hidden;
    }
    @media (max-width: 720px) { .kpi-secondary { grid-template-columns: repeat(2, 1fr); } }
    .kpi-mini {
      padding: 0.9rem 1.1rem; display: grid; gap: 0.2rem;
      border-right: 1px solid var(--line, #e6e4df);
      background: transparent; text-align: left; color: inherit; font: inherit;
    }
    .kpi-mini:last-child { border-right: none; }
    .kpi-mini--btn { cursor: pointer; transition: background .15s; }
    .kpi-mini--btn:hover { background: #fafaf9; }
    .kpi-mini--attention { background: #fef3c7; }
    .kpi-mini--attention:hover { background: #fde68a; }
    @media (max-width: 720px) {
      .kpi-mini:nth-child(2n) { border-right: none; }
      .kpi-mini:nth-child(-n+2) { border-bottom: 1px solid var(--line, #e6e4df); }
    }
    .kpi-mini-label { font-size: 0.75rem; color: var(--muted, #6b7280); font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; }
    .kpi-mini-value { font-size: 1.15rem; font-weight: 700; color: var(--ink, #111827); font-variant-numeric: tabular-nums; }
    .kpi-mini-foot { font-size: 0.75rem; color: var(--muted, #6b7280); }

    .section {
      background: #fff; border: 1px solid var(--line, #e6e4df);
      border-radius: 14px; padding: 1.35rem 1.5rem;
    }
    .section-head {
      display: flex; align-items: center; justify-content: space-between;
      gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap;
    }
    .section-head h3 { margin: 0.2rem 0 0.25rem; font-size: 1.1rem; }
    .section-head p { margin: 0; }
    .progress-pill {
      padding: 0.35rem 0.75rem; background: #fef3c7; color: #92400e;
      border-radius: 999px; font-size: 0.78rem; font-weight: 700; letter-spacing: 0.02em;
    }

    .setup-list { margin: 0; padding: 0; list-style: none; border: 1px solid var(--line, #e6e4df); border-radius: 12px; overflow: hidden; }
    .setup-row {
      display: grid; grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center; gap: 0.85rem; padding: 0.95rem 1.1rem; background: #fff;
    }
    .setup-row + .setup-row { border-top: 1px solid var(--line, #e6e4df); }
    .setup-row.is-ready { background: #fafaf9; }
    .setup-dot {
      width: 22px; height: 22px; border-radius: 50%;
      background: #fff; border: 2px solid var(--line, #e6e4df);
      display: grid; place-items: center; color: #fff;
    }
    .setup-dot--ready { background: #10b981; border-color: #10b981; }
    .setup-copy { min-width: 0; }
    .setup-title { display: flex; align-items: center; gap: 0.55rem; flex-wrap: wrap; }
    .setup-title h4 { margin: 0; font-size: 0.95rem; }
    .setup-copy p { margin: 0.22rem 0 0; color: var(--muted, #6b7280); font-size: 0.85rem; line-height: 1.45; }
    .setup-action {
      min-height: 36px; padding: 0.45rem 0.85rem;
      color: #b45309; background: #fff7ed; border: 1px solid #fed7aa;
      border-radius: 8px; font-weight: 600; cursor: pointer; white-space: nowrap; font-size: 0.85rem;
    }
    .setup-action:hover { background: #ffedd5; border-color: #fdba74; }

    .chip-pill {
      display: inline-flex; align-items: center;
      padding: 0.2rem 0.6rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase;
      background: #f3f4f6; color: #374151;
    }
    .chip-pill--ok { background: #d1fae5; color: #065f46; }
    .chip-pill--pending { background: #fef3c7; color: #92400e; }
    .chip-pill--danger { background: #fee2e2; color: #991b1b; }
    .tag {
      display: inline-flex; align-items: center;
      padding: 0.18rem 0.55rem; border-radius: 6px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.02em;
      background: #f3f4f6; color: #374151;
    }
    .tag--muted { background: #fafaf9; color: #6b7280; border: 1px solid var(--line, #e6e4df); }
    .mono { font-variant-numeric: tabular-nums; }

    .table-wrap { border: 1px solid var(--line, #e6e4df); border-radius: 12px; overflow: auto; }
    .data-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
    .data-table thead th {
      text-align: left; padding: 0.7rem 0.9rem; background: #fafaf9;
      font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.06em;
      color: var(--muted, #6b7280); font-weight: 700;
      border-bottom: 1px solid var(--line, #e6e4df);
    }
    .data-table tbody td {
      padding: 0.75rem 0.9rem; border-bottom: 1px solid var(--line, #e6e4df); vertical-align: middle;
    }
    .data-table tbody tr:last-child td { border-bottom: none; }
    .clickable-row { cursor: pointer; transition: background .15s; }
    .clickable-row:hover { background: #fafaf9; }
    .clickable-row:focus-visible { outline: 2px solid rgba(217,119,6,0.4); outline-offset: -2px; }

    @media (max-width: 640px) {
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

  formatCurrencyValue(value: number): string { return formatCurrency(value); }
  formatDateValue(value: number | null): string { return formatDate(value); }

  navigateToOrders(): void { this.router.navigate(['/business/orders']); }
  navigateToStaff(): void { this.router.navigate(['/business/staff']); }
  navigateToMenu(): void { this.router.navigate(['/business/menu']); }
  navigateTo(route: string): void { this.router.navigate([route]); }

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
