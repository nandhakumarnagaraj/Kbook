import { ChangeDetectionStrategy, Component, inject, signal, computed, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { DateRangeToolbarComponent } from '../../shared/date-range-toolbar.component';
import { HeaderSearchComponent } from '../../shared/header-search.component';
import { LiveOrdersTabComponent } from './live-orders-tab.component';
import { OrderHistoryTabComponent } from './order-history-tab.component';
import { readStoredDateRange } from '../../core/utils/date-range-storage.util';
import { KpiRowComponent, KpiItem } from '../../shared/kpi-row.component';

export type OrdersTab = 'live' | 'history';

/**
 * Single Orders workspace.
 *
 * Orders and Active Orders were two separate sidebar destinations showing two
 * halves of the same job (unsettled floor bills and settled history). They are
 * now two tabs of one page, each rendered by its own child component so the
 * tab that is not visible is destroyed (which also stops the live board's
 * 30-second polling instead of leaving it running in the background).
 *
 * The active tab is reflected in the `?tab=` query parameter, so the old
 * `/business/active-orders` bookmark redirects here and lands on `live`.
 */
@Component({
  selector: 'app-orders-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, PageHeaderComponent, DateRangeToolbarComponent, HeaderSearchComponent, LiveOrdersTabComponent, OrderHistoryTabComponent, KpiRowComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Orders"
        subtitle="Monitor live floor orders and review past transactions."
      >
        <app-header-search
          *ngIf="tab() === 'history'"
          [(value)]="historySearch"
          placeholder="Search order #, items, guest..."
          ariaLabel="Filter order history"
          inputId="order-history-search"
        />
        <app-date-range-toolbar
          [initialRange]="historyDateRange()"
          [showDateSelector]="tab() === 'history'"
          [showDownload]="tab() === 'history'"
          [downloadDisabled]="!orderHistoryTab?.ordersLoaded || !orderHistoryTab?.serverTotalElements || !!orderHistoryTab?.exportingCsv"
          [downloadLabel]="'Download order history CSV'"
          refreshLabel="Refresh orders"
          [refreshDisabled]="isRefreshing()"
          [refreshing]="isRefreshing()"
          (rangeChanged)="onHistoryDateRangeChange($event)"
          (download)="orderHistoryTab?.exportCsv()"
          (refresh)="refreshActiveTab()"
        ></app-date-range-toolbar>
      </app-page-header>

      <nav class="orders-tabs" role="tablist" aria-label="Orders views" (keydown)="onTabKeydown($event)">
        <button
          type="button"
          role="tab"
          id="orders-tab-live"
          class="orders-tab"
          [class.orders-tab--active]="tab() === 'live'"
          [attr.aria-selected]="tab() === 'live'"
          [attr.tabindex]="tab() === 'live' ? 0 : -1"
          aria-controls="orders-panel-live"
          (click)="selectTab('live')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M4 20V10m5 10V4m5 16v-7m5 7V8" />
          </svg>
          Live orders
        </button>

        <button
          type="button"
          role="tab"
          id="orders-tab-history"
          class="orders-tab"
          [class.orders-tab--active]="tab() === 'history'"
          [attr.aria-selected]="tab() === 'history'"
          [attr.tabindex]="tab() === 'history' ? 0 : -1"
          aria-controls="orders-panel-history"
          (click)="selectTab('history')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M3 3v5h5" />
            <path d="M3.05 13a9 9 0 1 0 2.5-6.4L3 8" />
            <path d="M12 7v5l4 2" />
          </svg>
          Order history
        </button>
      </nav>

      <!-- KPI Row -->
      <app-kpi-row
        *ngIf="tab() === 'live'"
        [items]="liveKpiItems()"
      />

      <app-kpi-row
        *ngIf="tab() === 'history'"
        [items]="historyKpiItems()"
        />
      <section
        id="orders-panel-live"
        role="tabpanel"
        aria-labelledby="orders-tab-live"
        *ngIf="tab() === 'live'">
        <app-live-orders-tab></app-live-orders-tab>
      </section>

      <section
        id="orders-panel-history"
        role="tabpanel"
        aria-labelledby="orders-tab-history"
        *ngIf="tab() === 'history'">
        <app-order-history-tab [(searchQuery)]="historySearch"></app-order-history-tab>
      </section>
    </div>
  `,
  styles: [
    `
      /* A segmented control, not a floating card: the recessed track groups the
         two views and the active pill is the only filled surface. The heavy
         1px #4B5563 frame this replaces read as a third content card above the
         board. */
      .orders-tabs {
        display: inline-flex;
        gap: var(--kb-space-1);
        padding: var(--kb-space-1);
        background: var(--kb-color-surface-2);
        border: 1px solid var(--kb-color-border);
        border-radius: var(--kb-radius-full);
        width: fit-content;
        max-width: 100%;
        overflow-x: auto;
      }

      .orders-tab {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: var(--kb-space-2);
        min-height: 36px;
        padding: var(--kb-space-1) var(--kb-space-4);
        border: 1px solid transparent;
        background: transparent;
        border-radius: var(--kb-radius-full);
        font-family: inherit;
        font-size: var(--kb-text-sm);
        font-weight: 600;
        line-height: 1.2;
        color: var(--kb-color-muted-foreground);
        cursor: pointer;
        white-space: nowrap;
        transition: background 150ms ease, color 150ms ease, border-color 150ms ease;
      }

      .orders-tab:hover:not(.orders-tab--active) {
        background: var(--kb-color-surface);
        color: var(--kb-color-foreground);
      }

      .orders-tab--active {
        background: var(--kb-color-primary);
        border-color: var(--kb-color-primary);
        color: var(--kb-color-primary-foreground);
      }

      .orders-tab:focus-visible {
        outline: 2px solid var(--kb-color-primary);
        outline-offset: 2px;
      }

      @media (max-width: 640px) {
        .orders-tabs { width: 100%; }
        .orders-tab { flex: 1 1 0; }
      }

      section[role='tabpanel'] {
        display: block;
      }
    `,
  ],
})
export class OrdersPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  @ViewChild(LiveOrdersTabComponent) private liveOrdersTab?: LiveOrdersTabComponent;
  @ViewChild(OrderHistoryTabComponent) orderHistoryTab?: OrderHistoryTabComponent;

  private readonly requestedTab = signal<OrdersTab>(this.readTabFromUrl());
  readonly tab = this.requestedTab.asReadonly();

  /** History-tab search text, owned here so the input can live in the page header. */
  historySearch = '';

  isRefreshing(): boolean {
    return this.tab() === 'live'
      ? this.liveOrdersTab?.loading() ?? false
      : !!this.orderHistoryTab && !this.orderHistoryTab.ordersLoaded;
  }

  historyDateRange(): { from: string; to: string } {
    const today = new Date();
    const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    return this.orderHistoryTab?.currentDateRange ?? readStoredDateRange() ?? { from: todayStr, to: todayStr };
  }

  onHistoryDateRangeChange(range: { from: string; to: string }): void {
    this.orderHistoryTab?.onDateRangeChange(range);
  }

  refreshActiveTab(): void {
    if (this.tab() === 'live') this.liveOrdersTab?.load(true);
    else this.orderHistoryTab?.loadOrders();
  }

  /** KPI items for the Live Orders view. */
  readonly liveKpiItems = computed<KpiItem[]>(() => {
    const orders = this.liveOrdersTab?.activeOrders() ?? [];
    return [
      {
        label: 'Active Orders',
        value: orders.length.toString(),
        tone: 'success' as const,
        accent: 'positive',
        badge: { text: '+new', tone: 'success' },
      } as KpiItem,
      {
        label: 'Preparing',
        value: this._preparingCount().toString(),
        tone: 'warning' as const,
        accent: 'flow',
      } as KpiItem,
      {
        label: 'Completed (Today)',
        value: this._completedTodayCount().toString(),
        tone: 'success' as const,
        accent: 'positive',
      } as KpiItem,
      {
        label: 'Payment Due',
        value: this._paymentDueCount().toString(),
        tone: 'danger' as const,
        accent: 'activity',
      } as KpiItem,
    ] as KpiItem[];
  });

  /** KPI items for the Order History view. */
  readonly historyKpiItems = computed<KpiItem[]>(() => {
    const total = this.orderHistoryTab?.serverTotalElements ?? 0;
    const completed = this.orderHistoryTab?.filteredCompletedCount ?? 0;
    const cancelled = this.orderHistoryTab?.filteredOrders.filter(o => (o.orderStatus || '').toLowerCase() === 'cancelled').length ?? 0;
    const sales = this.orderHistoryTab?.filteredTotalRevenue ?? 0;
    return [
      {
        label: 'Total Orders',
        value: total.toString(),
        tone: 'default' as const,
      } as KpiItem,
      {
        label: 'Total Sales',
        value: `₹${sales.toLocaleString()}`,
        tone: 'default' as const,
      } as KpiItem,
      {
        label: 'Completed Orders',
        value: completed.toString(),
        tone: 'success' as const,
      } as KpiItem,
      {
        label: 'Cancelled Orders',
        value: cancelled.toString(),
        tone: 'danger' as const,
      } as KpiItem,
    ] as KpiItem[];
  });

  /** Helper counts for live KPI items. */
  private _preparingCount = computed(() => {
    const orders = this.liveOrdersTab?.activeOrders() ?? [];
    return orders.filter(o => (o.orderStatus || '').trim().toLowerCase() === 'draft').length;
  });

  private _completedTodayCount = computed(() => {
    const orders = this.liveOrdersTab?.activeOrders() ?? [];
    const today = new Date();
    const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    return orders.filter(o => {
      const created = o.createdAt ? new Date(o.createdAt) : null;
      return created && created.toISOString().startsWith(todayStr) && (o.orderStatus || '').trim().toLowerCase() === 'completed';
    }).length;
  });

  private _paymentDueCount = computed(() => {
    const orders = this.liveOrdersTab?.activeOrders() ?? [];
    return orders.filter(o => (o.paymentStatus || '').trim().toLowerCase() === 'pending').length;
  });

  constructor() {
    // Keep the tab in sync with browser back/forward and deep links.
    this.route.queryParamMap.subscribe((params) => {
      const next: OrdersTab = params.get('tab') === 'history' ? 'history' : 'live';
      if (next !== this.requestedTab()) {
        this.requestedTab.set(next);
      }
    });
  }

  /** Arrow keys move between the two tabs, as the WAI-ARIA tablist pattern expects. Both tabs used to sit in the tab order and arrows did nothing, so a keyboard user had to Tab through the inactive one to reach the toolbar. */
  onTabKeydown(event: KeyboardEvent): void {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft' && event.key !== 'Home' && event.key !== 'End') {
      return;
    }
    event.preventDefault();
    const next: OrdersTab = event.key === 'Home'
      ? 'live'
      : event.key === 'End'
        ? 'history'
        : this.tab() === 'live' ? 'history' : 'live';
    this.selectTab(next);
    requestAnimationFrame(() => document.getElementById(`orders-tab-${next}`)?.focus());
  }

  selectTab(tab: OrdersTab): void {
    if (this.requestedTab() === tab) return;
    this.requestedTab.set(tab);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private readTabFromUrl(): OrdersTab {
    return this.route.snapshot.queryParamMap.get('tab') === 'history' ? 'history' : 'live';
  }
}