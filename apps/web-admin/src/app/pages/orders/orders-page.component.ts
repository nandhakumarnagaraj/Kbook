import { ChangeDetectionStrategy, Component, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { DateRangeToolbarComponent } from '../../shared/date-range-toolbar.component';
import { HeaderSearchComponent } from '../../shared/header-search.component';
import { LiveOrdersTabComponent } from './live-orders-tab.component';
import { OrderHistoryTabComponent } from './order-history-tab.component';
import { readStoredDateRange } from '../../core/utils/date-range-storage.util';

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
  imports: [CommonModule, PageHeaderComponent, DateRangeToolbarComponent, HeaderSearchComponent, LiveOrdersTabComponent, OrderHistoryTabComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Orders"
        subtitle="See live orders and review past orders and settlements.">
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
          (refresh)="refreshActiveTab()">
        </app-date-range-toolbar>
      </app-page-header>

      <nav class="orders-tabs" role="tablist" aria-label="Orders views">
        <button
          type="button"
          role="tab"
          id="orders-tab-live"
          class="orders-tab"
          [class.orders-tab--active]="tab() === 'live'"
          [attr.aria-selected]="tab() === 'live'"
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
      .orders-tabs {
        display: flex;
        gap: var(--kb-space-2);
        padding: var(--kb-space-3);
        background: var(--kb-color-surface);
        border: 1px solid var(--kb-color-border-strong);
        border-radius: var(--kb-radius-xl);
        width: fit-content;
        max-width: 100%;
        overflow-x: auto;
      }

      .orders-tab {
        display: inline-flex;
        align-items: center;
        gap: var(--kb-space-2);
        padding: var(--kb-space-2) var(--kb-space-3);
        border: none;
        background: transparent;
        border-radius: var(--kb-radius-full);
        font-family: inherit;
        font-size: var(--kb-text-sm);
        font-weight: 600;
        color: var(--kb-color-muted-foreground);
        cursor: pointer;
        white-space: nowrap;
        transition: background 150ms ease, color 150ms ease;
      }

      .orders-tab:hover {
        color: var(--kb-color-foreground);
      }

      /* The active tab uses the primary soft pill with primary ink, matching the
         dashboard filter-pill--active pattern. */
      .orders-tab--active {
        background: var(--kb-color-primary-soft);
        color: var(--kb-color-primary);
        border-color: var(--kb-color-primary);
        box-shadow: none;
      }

      .orders-tab:focus-visible {
        outline: 2px solid var(--kb-color-primary);
        outline-offset: 2px;
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

  constructor() {
    // Keep the tab in sync with browser back/forward and deep links.
    this.route.queryParamMap.subscribe((params) => {
      const next: OrdersTab = params.get('tab') === 'history' ? 'history' : 'live';
      if (next !== this.requestedTab()) {
        this.requestedTab.set(next);
      }
    });
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
