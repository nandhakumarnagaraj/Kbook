import { ChangeDetectionStrategy, Component, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { DateRangeToolbarComponent } from '../../shared/date-range-toolbar.component';
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
  imports: [CommonModule, PageHeaderComponent, DateRangeToolbarComponent, LiveOrdersTabComponent, OrderHistoryTabComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Orders"
        subtitle="Live floor board and settled transaction history in one workspace.">
        <app-date-range-toolbar
          [initialRange]="historyDateRange()"
          [showDateSelector]="tab() === 'history'"
          [showDownload]="tab() === 'history'"
          [downloadDisabled]="!orderHistoryTab?.ordersLoaded || !orderHistoryTab?.serverTotalElements || !!orderHistoryTab?.exportingCsv"
          [downloadLabel]="'Download order history CSV'"
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
          Live Floor
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
          History &amp; Settlements
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
        <app-order-history-tab></app-order-history-tab>
      </section>
    </div>
  `,
  styles: [
    `
      .orders-tabs {
        display: flex;
        gap: 0.4rem;
        padding: 0.3rem;
        background: var(--kb-color-surface-2, #f4f4f5);
        border: 1px solid var(--kb-color-border);
        border-radius: 999px;
        width: fit-content;
        max-width: 100%;
        overflow-x: auto;
      }

      .orders-tab {
        display: inline-flex;
        align-items: center;
        gap: 0.45rem;
        padding: 0.5rem 1.05rem;
        border: none;
        background: transparent;
        border-radius: 999px;
        font-family: inherit;
        font-size: 0.86rem;
        font-weight: 700;
        color: var(--kb-color-muted-foreground);
        cursor: pointer;
        white-space: nowrap;
        transition: background 140ms ease, color 140ms ease;
      }

      .orders-tab:hover {
        color: var(--kb-color-foreground);
      }

      .orders-tab--active {
        background: #ffffff;
        color: var(--kb-color-primary);
        box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
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
    if (this.tab() === 'live') this.liveOrdersTab?.load();
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
