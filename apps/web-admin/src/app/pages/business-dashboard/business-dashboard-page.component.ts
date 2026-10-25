import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, inject, signal, computed, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { combineLatest, of, Subject, Subscription } from 'rxjs';
import { catchError, map, switchMap, startWith } from 'rxjs/operators';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import {
  OrderDetailResponse,
  HourlySalesRow,
  ItemSalesRow,
  BusinessTerminal,
  BusinessOrder,
  NotificationItem
} from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { buildInvoiceLink } from '../../core/utils/invoice-link.util';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';
import { FocusTrapDirective } from '../../core/components/focus-trap.directive';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { HeaderSearchComponent } from '../../shared/header-search.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { AnimatedCounterComponent } from '../../shared/animated-counter.component';
import { NotificationBellComponent } from '../../shared/notification-bell.component';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { animate, stagger } from 'animejs';

export interface PosActiveOrder {
  orderId: number;
  orderCode: string;
  invoiceNumber?: string;
  publicToken?: string;
  orderType?: string;
  status: 'Preparing' | 'Cooking' | 'Served' | 'Completed';
  sourceType: string;
  dishImg?: string;
  itemsSummary: string;
  tableAndChannel: string;
  /** The table the bill is seated at, when the order is dine-in. */
  tableNumber?: string;
  paymentMethod?: string;
  totalAmount: number;
  createdAt?: number;
}

export interface HourlyBarDisplay {
  hourLabel: string;
  rawHour: number;
  itemsSold: number;
  heightPct: number;
  isPeak: boolean;
  isCurrent: boolean;
}

export interface TopDishDisplay {
  name: string;
  quantitySold: number;
  revenue: number;
  revenueFormatted: string;
  volumePct: number;
}

/**
 * One seated table, derived from the live open bills. There is deliberately no
 * `vacant` state: the API does not expose the floor plan, so the number of
 * tables a restaurant owns is unknown and a "vacant" count would be invented.
 */
export interface FloorTableItem {
  id: string;
  tableNumber: string;
  /** The order's own kitchen state, straight from the API. */
  status: 'preparing' | 'cooking' | 'served';
  amountFormatted: string;
  duration: string;
  tag: string;
}

export interface LedgerFeedItem {
  invoiceNo: string;
  tableAndChannel: string;
  amountFormatted: string;
  tenderType: string;
  timeAgo: string;
  syncLabel: string;
}

function getTodayIsoDate(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

@Component({
  selector: 'app-business-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    OrderDetailModalComponent,
    FocusTrapDirective,
    PageHeaderComponent,
    AnimatedCounterComponent,
    NotificationBellComponent,
    RefreshButtonComponent,
    HeaderSearchComponent,
    KpiRowComponent
  ],
  template: `
    <div class="page-shell" *ngIf="viewDashboard() as data; else loading">
      <!-- ── Top Cockpit Header Bar (shared PageHeaderComponent) ── -->
      <app-page-header
        [title]="data.shopName || 'KhanaBook Restaurant'"
        subtitle="Real-time sales, order settlements, and terminal activity.">
          <!-- Terminal Fleet Pill -->
          <button
            type="button"
            class="header-pill-btn"
            (click)="toggleTerminalsModal()"
            title="Manage POS terminals"
            [attr.aria-label]="onlineTerminalsCount() + ' active, ' + totalTerminalsCount() + ' registered terminals' + (fleetAttentionCount() > 0 ? ', ' + fleetAttentionCount() + ' needing recovery' : '')">
            <span class="dot-beacon" [class.dot-beacon--active]="onlineTerminalsCount() > 0"></span>
            <span class="pill-btn__text"><strong>{{ onlineTerminalsCount() }} active</strong> · {{ totalTerminalsCount() }} registered</span>
          </button>

          <!-- Low Stock Warning Pill -->
          <button
            type="button"
            class="header-pill-btn header-pill-btn--alert"
            *ngIf="lowStockMaterials().length > 0"
            (click)="toggleLowStockModal()"
            [title]="lowStockMaterials().length + ' raw materials below threshold'">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3M12 9v4m0 4h.01"/>
            </svg>
            <span class="pill-btn__text"><strong>{{ lowStockMaterials().length }}</strong> Low Stock</span>
          </button>

          <!-- Notifications Bell -->
          <app-notification-bell
            [notifications]="notifications()"
            [unreadCount]="unreadNotifsCount()"
            (markRead)="markNotificationAsRead($event)"
            (markAllRead)="markAllNotificationsRead()">
          </app-notification-bell>

          <!-- Last successful sync — the dashboard polls every 60s, so the owner
               should always be able to see how fresh the numbers are. -->
          <span
            class="freshness-chip"
            *ngIf="freshnessLabel()"
            [title]="lastLoadedTime() ? 'Last successful refresh at ' + lastLoadedTime() : ''">{{ freshnessLabel() }}</span>

          <!-- Refresh Button -->
          <app-refresh-button (refresh)="refresh()" [loading]="isRefreshing()" ariaLabel="Refresh live metrics" />
      </app-page-header>

      <!-- Stale / offline banner: shown when a refresh fails but data is still on screen -->
      <div *ngIf="staleError()" class="stale-banner" role="alert" aria-live="assertive">
        <span class="stale-banner__icon" aria-hidden="true">!</span>
        <span class="stale-banner__text">
          <strong>Couldn't refresh.</strong> {{ staleError() }}
          <span class="stale-banner__meta" *ngIf="lastLoadedTime()"> Showing figures from {{ lastLoadedTime() }}.</span>
        </span>
        <span class="stale-banner__spacer"></span>
        <button type="button" class="ghost-btn" (click)="refresh()">Retry</button>
      </div>

      <!-- ── Key metrics: the shared KPI row, one anatomy for every page ── -->
      <app-kpi-row
        [items]="dashboardKpis()"
        ariaLabel="Key Performance Indicators"
        (action)="onKpiAction($event)">
      </app-kpi-row>

      <!-- ── Hourly chart: full width, above the split grid ── -->
      <section class="dashboard-chart-row" aria-label="Hourly Revenue Velocity">
          <!-- 1. Peak Hour Velocity Curve (Hourly Revenue Velocity) -->
          <article class="intel-card intel-card--chart">
            <div class="intel-card__head">
              <div class="intel-card__title-group">
                <span class="intel-card__eyebrow">Service Velocity</span>
                <h3 class="intel-card__title">Hourly Revenue Velocity</h3>
                <span class="intel-card__subtext">Items sold per opening hour, today</span>
              </div>
              <div class="chart-legend-cluster">
                <span class="legend-item"><span class="legend-dot legend-dot--billed"></span> Billed (Actual)</span>
                <span class="rush-badge" *ngIf="peakHourText()">{{ peakHourText() }}</span>
                <!-- The bars are a picture; this is the same data as a real table,
                     reachable from the keyboard like any other control. -->
                <button
                  type="button"
                  class="link-btn-text"
                  [attr.aria-expanded]="chartTableOpen()"
                  aria-controls="hourly-chart-table"
                  (click)="toggleChartTable()">
                  {{ chartTableOpen() ? 'Hide table' : 'View as table' }}
                </button>
              </div>
            </div>

            <span class="visually-hidden" role="status">{{ hourlySummaryText() }}</span>

            <div class="hourly-chart-wrapper" *ngIf="hasHourlySales(); else simulatedHourly">
              <div class="hourly-bars-track" role="presentation" aria-hidden="true">
                <div
                  class="hourly-bar-node"
                  *ngFor="let bar of displayHourlyBars()"
                  [title]="bar.hourLabel + ': ' + bar.itemsSold + ' items sold'">
                  <div class="bar-pill-track">
                    <div
                      class="bar-pill-fill"
                      [class.bar-pill-fill--peak]="bar.isPeak"
                      [class.bar-pill-fill--current]="bar.isCurrent"
                      [style.height.%]="bar.heightPct">
                    </div>
                  </div>
                  <span class="bar-tick-label" [class.bar-tick-label--current]="bar.isCurrent">
                    {{ bar.hourLabel }}
                    <span *ngIf="bar.isCurrent" class="current-hour-dot" aria-label="Current hour"></span>
                  </span>
                </div>
              </div>
            </div>

            <!-- No hourly rows for today: an empty state, not a made-up peak curve. -->
            <ng-template #simulatedHourly>
              <div class="intel-empty-box">
                <h4>No sales recorded yet today</h4>
                <p>The hourly curve appears as soon as the first bill of the day is settled.</p>
              </div>
            </ng-template>

            
            <div class="velocity-summary-strip">
              <div class="velocity-summary-tile">
                <span class="vel-label">Busiest hour</span>
                <strong class="vel-val">{{ busiestHourText() }}</strong>
              </div>
              <div class="velocity-summary-tile">
                <span class="vel-label">Items sold today</span>
                <strong class="vel-val">{{ itemsSoldToday() }}</strong>
              </div>
              <div class="velocity-summary-tile">
                <span class="vel-label">Hours with sales</span>
                <strong class="vel-val">{{ activeHourCount() }}</strong>
              </div>
            </div>

            <!-- Data-table alternative to the bar picture. -->
            <div class="metric-table-wrap" *ngIf="chartTableOpen()">
              <table class="data-table" id="hourly-chart-table">
                <caption class="visually-hidden">Items sold in each opening hour today</caption>
                <thead>
                  <tr>
                    <th scope="col">Hour</th>
                    <th scope="col" class="num-cell">Items sold</th>
                    <th scope="col">Note</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let bar of displayHourlyBars()">
                    <td class="font-semibold">{{ bar.hourLabel }}</td>
                    <td class="num-cell tabular-num">{{ bar.itemsSold }}</td>
                    <td class="muted">{{ bar.isPeak ? 'Busiest hour' : (bar.isCurrent ? 'Current hour' : '') }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>
      </section>

      <!-- ── Operational Intelligence Split Grid (8 cols Left, 4 cols Right) ── -->
      <section class="dashboard-operational-grid" aria-label="Operational Intelligence">
        <!-- ── LEFT / CENTER COLUMN (8 COLS) ── -->
        <div class="ops-main-col">

          <!-- 2. Live Floor -->
          <article class="intel-card intel-card--floor">
            <div class="intel-card__head">
              <div class="intel-card__title-group">
                <!-- The other three sections carry an eyebrow and this one did not,
                     which also left the floor's accent role with nowhere to show. -->
                <span class="intel-card__eyebrow">Floor Status</span>
                <div class="matrix-title-row">
                  <h3 class="intel-card__title">Live Floor</h3>
                </div>
                <span class="intel-card__subtext">Every chip is an open bill, keyed by where it is seated</span>
              </div>
              <!-- Table Filter Tags -->
              <div class="table-filter-pills" role="tablist" aria-label="Floor filter">
                <button
                  type="button"
                  class="filter-pill"
                  [class.filter-pill--active]="activeTableFilter() === 'all'"
                  (click)="setTableFilter('all')">
                  All {{ floorTables().length }}
                </button>
                <button
                  type="button"
                  class="filter-pill"
                  [class.filter-pill--active]="activeTableFilter() === 'dine_in'"
                  (click)="setTableFilter('dine_in')">
                  Dine-in {{ seatedTableCount() }}
                </button>
                <button
                  type="button"
                  class="filter-pill filter-pill--bill"
                  [class.filter-pill--active]="activeTableFilter() === 'takeaway'"
                  (click)="setTableFilter('takeaway')">
                  Takeaway {{ takeawayFloorCount() }}
                </button>
              </div>
              <button
                type="button"
                class="link-btn-text"
                [attr.aria-expanded]="floorTableOpen()"
                aria-controls="floor-table"
                (click)="toggleFloorTable()">
                {{ floorTableOpen() ? 'Hide table' : 'View as table' }}
              </button>
            </div>

            <!-- Status Chromatic Legend Strip -->
            <div class="floor-legend-strip">
              <span class="legend-indicator" *ngFor="let legend of floorLegend()">
                <span class="legend-dot" [class.legend-dot--amber]="legend.label === 'Preparing' || legend.label === 'Cooking'" [class.legend-dot--green]="legend.label === 'Served'"></span>
                {{ legend.label }} {{ legend.count }}
              </span>
            </div>

            <!-- One chip per open bill — nothing is rendered for a table nobody is at -->
            <div class="table-chips-grid" *ngIf="filteredFloorTables().length > 0; else noFloor">
              <div
                class="table-chip"
                *ngFor="let t of filteredFloorTables(); trackBy: trackByTable"
                [class.table-chip--dining]="t.status === 'preparing' || t.status === 'cooking'"
                [class.table-chip--bill]="t.status === 'served'">
                <div class="table-chip__head">
                  <strong class="table-chip__number">{{ t.tableNumber }}</strong>
                  <span class="table-chip__pax">{{ t.status === 'served' ? 'Served' : (t.status === 'cooking' ? 'Cooking' : 'Preparing') }}</span>
                </div>
                <div class="table-chip__body">
                  <div class="table-chip__metric-row">
                    <strong class="table-chip__amount">{{ t.amountFormatted }}</strong>
                    <span class="table-chip__duration">{{ t.duration }}</span>
                  </div>
                  <div class="table-chip__note">{{ t.tag }}</div>
                </div>
              </div>
            </div>
            <ng-template #noFloor>
              <div class="intel-empty-box">
                <h4>No open bills</h4>
                <p>The floor fills in as orders are opened at the counter or on a terminal.</p>
              </div>
            </ng-template>

            <!-- Data-table alternative to the floor chips. -->
            <div class="metric-table-wrap" *ngIf="floorTableOpen()">
              <table class="data-table" id="floor-table">
                <caption class="visually-hidden">Open bills by table</caption>
                <thead>
                  <tr>
                    <th scope="col">Table</th>
                    <th scope="col">Kitchen state</th>
                    <th scope="col" class="num-cell">Amount</th>
                    <th scope="col">Open for</th>
                    <th scope="col">Bill</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let t of filteredFloorTables(); trackBy: trackByTable">
                    <td class="font-semibold">{{ t.tableNumber }}</td>
                    <td>{{ floorStatusLabel(t.status) }}</td>
                    <td class="num-cell tabular-num">{{ t.amountFormatted }}</td>
                    <td class="muted">{{ t.duration || '—' }}</td>
                    <td class="muted">{{ t.tag }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>
        </div>

        <!-- ── RIGHT COLUMN (4 COLS) ── -->
        <div class="ops-side-col">
          <!-- 3. Top Selling Dishes Today -->
          <article class="intel-card intel-card--top">
            <div class="intel-card__head">
              <div class="intel-card__title-group">
                <span class="intel-card__eyebrow">Kitchen Velocity</span>
                <h3 class="intel-card__title">Top Selling Items</h3>
                <span class="intel-card__subtext">Contribution ranking by volume & gross</span>
              </div>
              <button type="button" class="link-btn-text" (click)="navigateToMenu()">View Menu</button>
            </div>

            <div class="top-dishes-stack" *ngIf="displayTopDishes().length > 0; else simulatedTopDishes">
              <div class="dish-leader-row" *ngFor="let dish of displayTopDishes(); let idx = index">
                <div class="dish-rank-badge">{{ idx + 1 }}</div>
                <div class="dish-leader-info">
                  <div class="dish-leader-top">
                    <strong class="dish-name-label">{{ dish.name }}</strong>
                    <span class="dish-qty-label">{{ dish.quantitySold }} sold</span>
                  </div>
                  <div class="dish-bar-track">
                    <div class="dish-bar-fill" [style.width.%]="dish.volumePct"></div>
                  </div>
                </div>
                <strong class="dish-rev-label">{{ dish.revenueFormatted }}</strong>
              </div>
            </div>

            <!-- No item-sales rows for today: say so rather than inventing a leaderboard. -->
            <ng-template #simulatedTopDishes>
              <div class="intel-empty-box">
                <h4>No items sold yet today</h4>
                <p>The ranking fills in once bills with items have been settled.</p>
              </div>
            </ng-template>
          </article>

          <!-- 4. Live Ledger Feed -->
          <article class="intel-card intel-card--ledger">
            <div class="intel-card__head">
              <div class="intel-card__title-group">
                <div class="ledger-title-row">
                  <h3 class="intel-card__title">Live Ledger Feed</h3>
                  <span class="live-pulse-dot"></span>
                </div>
                <span class="intel-card__subtext">The most recent orders this dashboard received</span>
              </div>
            </div>

            <div class="ledger-feed-stream" *ngIf="recentLedgerFeed().length > 0; else noLedger">
              <div class="ledger-entry" *ngFor="let item of recentLedgerFeed()">
                <div class="ledger-entry__top">
                  <strong class="ledger-voucher">{{ item.invoiceNo }} • {{ item.tableAndChannel }}</strong>
                  <strong class="ledger-amount">{{ item.amountFormatted }}</strong>
                </div>
                <div class="ledger-entry__meta">
                  <span class="tender-badge">
                    <span class="tender-badge__icon">✓</span>
                    {{ item.tenderType }}
                  </span>
                  <span class="ledger-time">{{ item.timeAgo }}</span>
                </div>
                <div class="ledger-entry__sync">
                  <span class="sync-check">●</span>
                  <span>{{ item.syncLabel }}</span>
                </div>
              </div>
            </div>

            <ng-template #noLedger>
              <div class="intel-empty-box">
                <h4>No orders yet</h4>
                <p>Settled and open orders appear here as they arrive.</p>
              </div>
            </ng-template>

            <!-- Terminal fleet footer — the count the server reports, nothing more -->
            <div class="hardware-sync-foot">
              <div class="hardware-sync-info">
                <span class="hardware-icon">⬢</span>
                <span>{{ onlineTerminalsCount() }} of {{ totalTerminalsCount() }} terminals online</span>
              </div>
            </div>
          </article>
        </div>
      </section>

    </div>

    <!-- ── Terminal Fleet Status Modal ── -->
    <div class="tactile-modal-overlay" *ngIf="terminalsModalOpen()" appFocusTrap (escape)="toggleTerminalsModal()" (click)="toggleTerminalsModal()">
      <div class="tactile-modal-box" role="dialog" aria-modal="true" aria-labelledby="terminals-modal-title" (click)="$event.stopPropagation()">
        <div class="tactile-modal-head">
          <h3 id="terminals-modal-title">POS terminals</h3>
          <button type="button" class="icon-btn" (click)="toggleTerminalsModal()">✕</button>
        </div>
        <div class="tactile-modal-body">
          <div class="terminal-item-row" *ngFor="let t of terminals()">
            <div class="terminal-core-info">
              <span class="dot-beacon" [class.dot-beacon--active]="terminalStatusOf(t).tone === 'active'"></span>
              <div>
                <strong>{{ t.terminalName || 'Terminal ' + (t.terminalSeries || t.id) }}</strong>
                <span class="terminal-tag">Series: {{ t.terminalSeries || 'POS' }}</span>
              </div>
            </div>
            <div class="terminal-meta-info">
              <span class="status-chip" [ngClass]="terminalStatusOf(t).cls">
                {{ terminalStatusOf(t).label }}
              </span>
              <span class="time-sub">Updated: {{ formatDateValue(t.updatedAt) }}</span>
            </div>
          </div>
          <div class="tactile-empty-sub" *ngIf="terminals().length === 0">
            <p>No POS terminals registered yet.</p>
          </div>
        </div>
        <div class="tactile-modal-foot">
          <button type="button" class="header-action-btn header-action-btn--primary" (click)="navigateToTerminals()">Manage terminals</button>
        </div>
      </div>
    </div>

    <!-- ── Low Stock Alert Modal ── -->
    <div class="tactile-modal-overlay" *ngIf="lowStockModalOpen()" appFocusTrap (escape)="toggleLowStockModal()" (click)="toggleLowStockModal()">
      <div class="tactile-modal-box" role="dialog" aria-modal="true" aria-labelledby="low-stock-modal-title" (click)="$event.stopPropagation()">
        <div class="tactile-modal-head">
          <div class="tactile-head-with-icon">
            <span class="warning-icon-badge">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3M12 9v4m0 4h.01"/></svg>
            </span>
            <h3 id="low-stock-modal-title">Low Stock Raw Ingredients</h3>
          </div>
          <button type="button" class="icon-btn" (click)="toggleLowStockModal()">✕</button>
        </div>
        <div class="tactile-modal-body">
          <p class="modal-lead-note">The following raw materials are currently below their minimum kitchen reserve threshold.</p>
          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Material</th>
                  <th>Current Stock</th>
                  <th>Threshold</th>
                  <th>Unit</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let m of lowStockMaterials()">
                  <td><strong>{{ m.name }}</strong></td>
                  <td class="text-rose font-bold">{{ m.stockQuantity }}</td>
                  <td>{{ m.lowStockThreshold }}</td>
                  <td>{{ m.unit || 'units' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="tactile-modal-foot">
          <button type="button" class="header-action-btn header-action-btn--primary" (click)="navigateToInventory()">Update Inventory</button>
        </div>
      </div>
    </div>

    <!-- Loading / Skeleton State -->
    <ng-template #loading>
      <div class="page-shell" *ngIf="dashboardError(); else posSkeleton">
        <section
          class="state-panel"
          [class.state-panel--offline]="isOfflineError()"
          role="alert"
          aria-live="assertive"
          tabindex="-1"
          style="margin-top: 2rem;">
          <div class="state-panel__icon" aria-hidden="true">{{ isOfflineError() ? '⚠' : '!' }}</div>
          <div>
            <h3>We couldn't load your dashboard</h3>
            <p>{{ dashboardError() }}</p>
            <span class="state-panel__meta">Nothing was changed. Your last known figures are safe.</span>
          </div>
          <button class="btn-retry" type="button" (click)="refresh()">Try again</button>
        </section>
      </div>
      <ng-template #posSkeleton>
        <div class="page-shell" aria-busy="true" aria-live="polite">
          <span class="visually-hidden" role="status">Loading dashboard data…</span>
          <div class="skeleton-shimmer" style="height: 60px; border-radius: 16px; margin-bottom: 1.5rem;"></div>
          <div class="bento-kpi-grid">
            <div class="skeleton-shimmer" style="height: 140px; border-radius: 16px;" *ngFor="let i of [1,2,3,4]"></div>
          </div>
          <div class="bento-intel-grid" style="margin-top: 1.5rem;">
            <div class="skeleton-shimmer" style="height: 260px; border-radius: 16px;"></div>
            <div class="skeleton-shimmer" style="height: 260px; border-radius: 16px;"></div>
          </div>
        </div>
      </ng-template>
    </ng-template>

    <!-- Order Detail Modal -->
    <app-order-detail-modal
      [order]="selectedOrderDetail()"
      (closed)="closeOrderDetail()">
    </app-order-detail-modal>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      background: var(--kb-color-bg-app);
      min-height: 100vh;
      color: var(--kb-color-foreground);
      font-family: var(--kb-font-sans);
    }

    /* Freshness stamp next to the refresh control */
    .freshness-chip {
      font-size: 0.8125rem;
      font-weight: 500;
      color: var(--kb-color-muted-foreground);
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }

    /* ── Precision Culinary Operational Intelligence Grid (8 cols Left / 4 cols Right) ── */
    .dashboard-operational-grid {
      display: grid;
      grid-template-columns: minmax(0, 1.85fr) minmax(0, 1.15fr);
      gap: 1.25rem;
      margin-top: 1.5rem;
      align-items: start;
    }
    @media (max-width: 1080px) {
      .dashboard-operational-grid {
        grid-template-columns: 1fr;
      }
    }

    .dashboard-chart-row {
      margin-top: 1.5rem;
      min-width: 0;
    }

    .ops-main-col {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      min-width: 0;
    }

    .ops-side-col {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      min-width: 0;
    }

    .intel-card {
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: 12px;
      box-shadow: var(--kb-shadow-sm);
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
      position: relative;
    }

    .intel-card__head {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 1rem;
      flex-wrap: wrap;
    }

    .intel-card__title-group {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    /* Each section's eyebrow carries its accent role as a coloured dot plus the ink,
       so the five sections separate at a glance without a header icon and without
       re-tinting any numbers. Orange as *text* is the -ink step (4.7:1); the dot may
       use the fill itself because a marker is not text. */
    .intel-card__eyebrow {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.8125rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--kb-color-muted-foreground);
    }

    .intel-card__eyebrow::before {
      content: '';
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: currentColor;
      flex: none;
    }

    .intel-card--chart .intel-card__eyebrow { color: var(--kb-color-primary); }
    .intel-card--chart .intel-card__eyebrow::before { background: var(--kb-color-primary); }
    .intel-card--floor .intel-card__eyebrow { color: var(--kb-brand-purple); }
    .intel-card--top .intel-card__eyebrow { color: var(--kb-brand-orange-ink); }
    .intel-card--top .intel-card__eyebrow::before { background: var(--kb-brand-orange); }
    .intel-card--ledger .intel-card__eyebrow { color: var(--kb-color-link); }

    .intel-card__title {
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
      letter-spacing: -0.02em;
      margin: 0;
    }

    .intel-card__subtext {
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
    }

    /* Velocity Chart Header Elements */
    .chart-legend-cluster {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.35rem;
    }

    .legend-dot--billed {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--kb-brand-orange);
    }

    .legend-dash {
      width: 14px;
      height: 2px;
      border-top: 2px dashed var(--kb-color-border-strong);
    }

    .rush-badge {
      background: var(--kb-color-danger-soft);
      color: var(--kb-color-danger);
      border: 1px solid rgba(var(--kb-color-danger-rgb), 0.35);
      padding: 0.15rem 0.5rem;
      border-radius: 9999px;
      font-weight: 600;
      font-size: 0.8125rem;
    }

    /* Hourly Chart Wrapper & Histogram */
    .hourly-chart-wrapper {
      position: relative;
      padding-top: 1.5rem;
      padding-bottom: 0.5rem;
    }

    .benchmark-line-overlay {
      position: absolute;
      top: 38%;
      left: 0;
      right: 0;
      border-top: 1px dashed rgba(255, 255, 255, 0.18);
      display: flex;
      justify-content: flex-end;
      pointer-events: none;
    }

    .benchmark-tag {
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
      background: var(--kb-color-surface);
      padding: 0 0.35rem;
      transform: translateY(-50%);
      font-variant-numeric: tabular-nums;
    }

    .hourly-bars-track {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      height: 160px;
      gap: 0.4rem;
    }

    .hourly-bar-node {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      height: 100%;
      justify-content: flex-end;
      gap: 0.45rem;
      cursor: pointer;
    }

    .bar-pill-track {
      width: 100%;
      max-width: 34px;
      height: 100%;
      display: flex;
      align-items: flex-end;
      background: var(--kb-color-muted);
      border-radius: 6px;
      padding: 2px;
    }

    /* Sales volume is activity, not an alarm. These bars used to be painted in
       the danger red, so an ordinary trading day looked like an incident. */
    .bar-pill-fill {
      width: 100%;
      border-radius: 4px;
      background: rgba(var(--kb-brand-orange-rgb), 0.38);
      transition: height 240ms cubic-bezier(0.2, 0.8, 0.2, 1);
      position: relative;
    }

    .bar-pill-fill--mid {
      background: rgba(var(--kb-color-danger-rgb), 0.65);
    }

    /* The busiest hour is the same measure one step louder, not a different one. */
    .bar-pill-fill--peak {
      background: var(--kb-brand-orange);
      box-shadow: 0 0 12px rgba(var(--kb-brand-orange-rgb), 0.45);
    }

    .peak-badge-tag {
      position: absolute;
      top: -18px;
      left: 50%;
      transform: translateX(-50%);
      font-size: 0.8125rem;
      font-weight: 800;
      color: #FFFFFF;
      background: var(--kb-color-danger-solid);
      padding: 1px 4px;
      border-radius: 3px;
      letter-spacing: 0.05em;
    }

    .peak-dot {
      position: absolute;
      top: 4px;
      left: 50%;
      transform: translateX(-50%);
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: #FFFFFF;
    }

    .bar-tick-label {
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
      font-weight: 500;
    }

    .bar-tick-label--peak {
      color: var(--kb-color-foreground);
      font-weight: 700;
    }

    .velocity-summary-strip {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.75rem;
      border-top: 1px solid var(--kb-color-border);
      padding-top: 0.85rem;
    }
    @media (max-width: 640px) {
      .velocity-summary-strip {
        grid-template-columns: 1fr;
      }
    }

    .velocity-summary-tile {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    .vel-label {
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .vel-val {
      font-size: 0.86rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }

    /* Live Floor Matrix */
    .matrix-title-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .matrix-hall-pill {
      font-size: 0.8125rem;
      padding: 0.2rem 0.55rem;
      background: var(--kb-color-surface-2);
      border-radius: 9999px;
      color: var(--kb-color-muted-foreground);
      font-weight: 500;
    }

    .table-filter-pills {
      display: flex;
      gap: 0.4rem;
      background: var(--kb-color-muted);
      padding: 0.25rem;
      border-radius: 10px;
    }

    .filter-pill {
      background: transparent;
      border: none;
      font-size: 0.8125rem;
      font-weight: 600;
      padding: 0.3rem 0.65rem;
      border-radius: 7px;
      color: var(--kb-color-muted-foreground);
      cursor: pointer;
      transition: all 180ms ease;
    }

    .filter-pill:hover {
      color: var(--kb-color-foreground);
      background: var(--kb-color-surface-2);
    }

    /* The selected filter is the one state that must not look like the others.
       A white pill on a white card (--surface-3) had nothing to separate it, so
       selection is carried by the brand tint, the border and the ink. */
    .filter-pill--active {
      color: var(--kb-color-primary-hover);
      background: var(--kb-color-primary-soft);
      border-color: var(--kb-color-primary);
    }

    .filter-pill:active {
      transform: scale(0.97);
    }

    .floor-legend-strip {
      display: flex;
      align-items: center;
      gap: 1.25rem;
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
      flex-wrap: wrap;
    }

    .legend-indicator {
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }

    .table-chips-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 0.75rem;
    }
    @media (max-width: 768px) {
      .table-chips-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }

    .table-chip {
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: 12px;
      padding: 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
      transition: transform 180ms ease, border-color 180ms ease;
      cursor: default;
    }

    .table-chip:hover {
      border-color: var(--kb-color-border-strong);
      transform: translateY(-1px);
    }

    /* Table state is a left rule plus a text label in the chip, never the rule
       alone: vacant / dining / bill-sent must stay distinguishable without
       colour (and in forced-colours mode). */
    .table-chip--vacant {
      border-left: 3px solid var(--kb-color-success);
    }

    .table-chip--dining {
      border-left: 3px solid var(--kb-color-warning);
    }

    /* Served is a finished state, not a fault: it takes the success tint, so a
       fully served floor does not look like a wall of red alerts. */
    .table-chip--bill {
      border-left: 3px solid var(--kb-color-success);
      background: var(--kb-color-success-soft);
    }

    .table-chip__head {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .table-chip__number {
      font-size: 0.95rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
      letter-spacing: -0.01em;
    }

    .table-chip__pax {
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }

    .table-chip__metric-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .table-chip__amount {
      font-size: 0.92rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }

    .table-chip__vacant-label {
      font-size: 0.8125rem;
      font-weight: 700;
      color: var(--kb-color-success);
      letter-spacing: 0.04em;
    }

    .table-chip__duration {
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }

    .table-chip__badge-sent {
      font-size: 0.8125rem;
      color: var(--kb-color-danger);
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    .table-chip__note {
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
      opacity: 0.85;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    /* Right Column: Top Dishes & Live Ledger */
    .top-dishes-stack {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .dish-leader-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.5rem 0.65rem;
      background: var(--kb-color-surface-2);
      border-radius: 10px;
      border: 1px solid var(--kb-color-border);
    }

    .dish-rank-badge {
      width: 22px;
      height: 22px;
      border-radius: 6px;
      background: var(--kb-color-surface-3);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.8125rem;
      font-weight: 700;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
      flex: none;
    }

    .dish-rank-badge--first {
      background: var(--kb-color-danger-solid);
      color: #FFFFFF;
    }

    .dish-leader-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.3rem;
      min-width: 0;
    }

    .dish-leader-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 0.5rem;
    }

    .dish-name-label {
      font-size: 0.85rem;
      font-weight: 600;
      color: var(--kb-color-foreground);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .dish-category-tag {
      font-size: 0.8125rem;
      background: var(--kb-color-surface-3);
      padding: 0.1rem 0.4rem;
      border-radius: 4px;
      color: var(--kb-color-muted-foreground);
    }

    .dish-bar-track {
      width: 100%;
      height: 5px;
      background: var(--kb-color-muted);
      border-radius: 9999px;
      overflow: hidden;
    }

    .dish-bar-fill {
      height: 100%;
      background: var(--kb-color-danger-solid);
      border-radius: 9999px;
    }

    .dish-bar-fill--amber {
      background: var(--kb-color-warning);
    }

    .dish-bar-fill--mint {
      background: var(--kb-color-success);
    }

    .dish-bar-fill--slate {
      background: var(--kb-color-muted-foreground);
    }

    .dish-leader-sub {
      display: flex;
      justify-content: space-between;
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }

    .dish-rev-label {
      font-size: 0.88rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
      flex: none;
    }

    /* The expandable data-table under the chart / floor grid. */
    .metric-table-wrap {
      margin-top: 0.85rem;
      max-height: 320px;
      overflow: auto;
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-md);
    }

    /* Live Ledger Feed */
    .ledger-title-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .live-pulse-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--kb-color-success);
      box-shadow: 0 0 6px rgba(var(--kb-color-success-rgb), 0.8);
    }

    .ledger-time-window {
      font-size: 0.8125rem;
      color: var(--kb-color-success);
      background: var(--kb-color-success-soft);
      padding: 0.15rem 0.5rem;
      border-radius: 9999px;
      font-weight: 600;
    }

    .ledger-feed-stream {
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
      max-height: 290px;
      overflow-y: auto;
    }

    .ledger-entry {
      background: var(--kb-color-surface-2);
      border-radius: 10px;
      border: 1px solid var(--kb-color-border);
      padding: 0.65rem 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .ledger-entry__top {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .ledger-voucher {
      font-size: 0.82rem;
      font-weight: 600;
      color: var(--kb-color-foreground);
    }

    .ledger-amount {
      font-size: 0.86rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }

    .ledger-entry__meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.8125rem;
    }

    .tender-badge {
      display: flex;
      align-items: center;
      gap: 0.3rem;
      color: var(--kb-color-success);
      font-weight: 500;
    }

    .ledger-time {
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }

    .ledger-entry__sync {
      display: flex;
      align-items: center;
      gap: 0.3rem;
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
      border-top: 1px dashed var(--kb-color-border);
      padding-top: 0.3rem;
    }

    .sync-check {
      color: var(--kb-color-success);
      font-size: 0.8125rem;
    }

    /* The empty states get the pale circle the rest of the app uses for "nothing
       here yet", so an empty section reads as quiet rather than broken. */
    .intel-empty-box::before {
      content: '';
      display: block;
      width: 44px;
      height: 44px;
      margin: 0 auto 0.65rem;
      border-radius: 50%;
      background: var(--kb-color-surface-2);
    }

    .hardware-sync-foot {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 0.65rem;
      border-top: 1px solid var(--kb-color-border);
      font-size: 0.8125rem;
      color: var(--kb-color-muted-foreground);
    }

    .hardware-sync-info {
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }

    .hardware-icon {
      color: var(--kb-color-warning);
    }

    .hardware-status-ok {
      color: var(--kb-color-success);
      font-weight: 600;
      font-size: 0.8125rem;
    }
  `]
})
export class BusinessDashboardPageComponent implements OnInit, OnDestroy {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  private readonly refresh$ = new Subject<void>();

  // State Signals
  readonly isRefreshing = signal(false);
  readonly dashboardError = signal('');
  readonly staleError = signal('');
  private readonly currentDashboard = signal<any>(null);
  readonly dashboard = this.currentDashboard.asReadonly();

  /**
   * True while a *user-requested* reload is in flight. The 60s auto-poll
   * deliberately does not set it — swapping the figures someone is reading for
   * skeletons every minute would be worse than no feedback at all.
   */
  private readonly manualReload = signal(false);

  /**
   * What the page renders: the payload is withheld during a manual reload so
   * the skeleton (the `#loading` template) takes over and the reload is
   * unmistakable, the same feedback the Terminals page gives.
   */
  readonly viewDashboard = computed(() => (this.manualReload() ? null : this.currentDashboard()));

  private refreshSubscription: Subscription | null = null;
  private kpiEntranceDone = false;

  /** Wall-clock of the last successful dashboard load, and a coarse ticker that
   *  drives the relative "updated Xs ago" label. */
  private readonly lastUpdatedAt = signal<number | null>(null);
  private readonly nowTick = signal(Date.now());

  private autoRefreshTimer: ReturnType<typeof setInterval> | null = null;
  private freshnessTimer: ReturnType<typeof setInterval> | null = null;

  /** Auto-refresh cadence. The dashboard aggregates ~9 endpoints, so it polls
   *  slower than the Active Orders board (30s) and skips hidden tabs. */
  private static readonly AUTO_REFRESH_MS = 60_000;

  readonly activeOrders = signal<PosActiveOrder[]>([]);
  readonly imageErrors = signal<Record<string, boolean>>({});
  readonly selectedOrderDetail = signal<OrderDetailResponse | null>(null);

  // Live Data Signals from Swagger
  readonly hourlySalesData = signal<HourlySalesRow[]>([]);
  readonly topDishesData = signal<ItemSalesRow[]>([]);
  readonly lowStockMaterials = signal<any[]>([]);
  readonly inventoryStatus = signal<'loading' | 'available' | 'unavailable'>('loading');
  readonly terminals = signal<BusinessTerminal[]>([]);
  readonly dailyClosingData = signal<any>(null);
  readonly notifications = signal<NotificationItem[]>([]);
  readonly unreadNotifsCount = signal<number>(0);

  // Live Floor & Tables Matrix
  readonly activeTableFilter = signal<'all' | 'dine_in' | 'takeaway'>('all');

  /**
   * The chart and the floor grid are pictures. Both offer the same data as a
   * real table, revealed by a button that reports its state — a keyboard or
   * screen-reader user gets the numbers without having to read a bar height.
   */
  readonly chartTableOpen = signal(false);
  readonly floorTableOpen = signal(false);

  toggleChartTable(): void {
    this.chartTableOpen.update(open => !open);
  }

  toggleFloorTable(): void {
    this.floorTableOpen.update(open => !open);
  }

  floorStatusLabel(status: FloorTableItem['status']): string {
    return status === 'served' ? 'Served' : status === 'cooking' ? 'Cooking' : 'Preparing';
  }

  /**
   * One chip per open bill, keyed by where it is seated. The API exposes no
   * floor plan, so a table the restaurant owns but nobody is sitting at simply
   * does not exist here — the old fixed 16-table map printed invented amounts,
   * dwell times and "vacant" states as if they were live.
   */
  readonly floorTables = computed<FloorTableItem[]>(() => {
    const byTable = new Map<string, FloorTableItem>();

    for (const order of this.activeOrders()) {
      const label = this.seatLabel(order);
      if (byTable.has(label)) continue;
      byTable.set(label, {
        id: String(order.orderId),
        tableNumber: label,
        status: order.status === 'Served' ? 'served' : order.status === 'Cooking' ? 'cooking' : 'preparing',
        amountFormatted: formatCurrency(order.totalAmount),
        duration: this.elapsedLabel(order.createdAt),
        tag: order.invoiceNumber ? `Bill ${order.invoiceNumber}` : `Order ${order.orderCode}`
      });
    }

    return [...byTable.values()].sort((a, b) =>
      a.tableNumber.localeCompare(b.tableNumber, undefined, { numeric: true })
    );
  });

  setTableFilter(filter: 'all' | 'dine_in' | 'takeaway'): void {
    this.activeTableFilter.set(filter);
  }

  readonly filteredFloorTables = computed(() => {
    const filter = this.activeTableFilter();
    const tables = this.floorTables();
    if (filter === 'all') return tables;
    if (filter === 'dine_in') return tables.filter(t => this.isDineInSeat(t.tableNumber));
    return tables.filter(t => !this.isDineInSeat(t.tableNumber));
  });

  readonly seatedTableCount = computed(() => this.floorTables().filter(t => this.isDineInSeat(t.tableNumber)).length);
  readonly takeawayFloorCount = computed(() => this.floorTables().length - this.seatedTableCount());

  /** Open bills = active orders. The only KOT total the API can back. */
  readonly activeOrderCount = computed(() => this.activeOrders().length);
  readonly cookingOrderCount = computed(() => this.activeOrders().filter(o => o.status === 'Cooking').length);
  readonly preparingOrderCount = computed(() => this.activeOrders().filter(o => o.status === 'Preparing').length);
  readonly servedOrderCount = computed(() => this.activeOrders().filter(o => o.status === 'Served').length);

  /** Realistic "N ago" for the KOT chip's dwell time, from the order timestamp. */
  readonly floorLegend = computed(() => [
    { label: 'Preparing', count: this.preparingOrderCount() },
    { label: 'Cooking', count: this.cookingOrderCount() },
    { label: 'Served', count: this.servedOrderCount() }
  ]);

  /**
   * Average ticket for today, from the two numbers the dashboard already
   * carries. Null — never ₹0 — while the day has no invoice, so the tile can
   * say "no invoices yet" instead of implying a ₹0 sale.
   */
  readonly avgTicket = computed<number | null>(() => {
    const data = this.dashboard();
    const invoices = Number(data?.posOrderCount) || 0;
    if (invoices <= 0) return null;
    return (Number(data?.todayRevenueValue) || 0) / invoices;
  });

  readonly avgTicketFormatted = computed(() => {
    const value = this.avgTicket();
    return value == null ? null : formatCurrency(value);
  });

  /** Total collected today per the closing payload, or null when it has not loaded. */
  readonly netCollectedFormatted = computed(() => {
    const closing = this.dailyClosingData();
    if (!closing || closing.netRevenue == null) return null;
    return formatCurrency(Number(closing.netRevenue) || 0);
  });

  /**
   * The four tiles, in the shared KPI row's anatomy. The dashboard is the page
   * that needs every optional part — an emphasised revenue tile, two state
   * badges, the tender split and two foot actions — which is why the component
   * grew those, rather than the page keeping a second tile design beside it.
   */
  readonly dashboardKpis = computed<KpiItem[]>(() => {
    const data = this.dashboard();
    if (!data) return [];

    const trend = this.revenueTrend();
    const closing = this.dailyClosingData();
    const invoices = Number(data.posOrderCount) || 0;
    const avgTicket = this.avgTicketFormatted();

    return [
      {
        label: "Today's Gross Sales",
        value: data.todayRevenueFormatted,
        // Money in: the one tile that is activity by definition.
        icon: 'reports',
        accent: 'activity',
        emphasis: true,
        badge: trend
          ? { text: `${trend.arrow ? trend.arrow + ' ' : ''}${trend.label}`, tone: trend.tone === 'down' ? 'danger' : trend.tone === 'neutral' ? 'neutral' : 'success' }
          : { text: 'No sales yet', tone: 'neutral' },
        caption: avgTicket
          ? `Avg ticket ${avgTicket} • ${invoices} invoice${invoices === 1 ? '' : 's'}`
          : 'No invoices yet today',
        segments: closing
          ? [
              { label: 'UPI', value: this.onlineRevenueFormatted(), percent: this.upiRatio(), tone: 'brand' },
              { label: 'Cash', value: this.cashDrawerFormatted(), percent: this.cashRatio(), tone: 'success' },
            ]
          : undefined,
      },
      {
        label: 'Seated Tables',
        value: this.seatedTableCount(),
        unit: 'Tables',
        // Occupancy: the floor, not money.
        icon: 'dashboard',
        accent: 'flow',
        badge: this.takeawayFloorCount() > 0
          ? { text: `${this.takeawayFloorCount()} Takeaway`, tone: 'neutral' }
          : undefined,
        caption: `${this.activeOrderCount()} open bill${this.activeOrderCount() === 1 ? '' : 's'} right now`,
        action: { id: 'active-orders', label: 'Open bills' },
      },
      {
        label: 'Running Kitchen KOTs',
        value: this.activeOrderCount(),
        unit: 'Open Orders',
        // The kitchen queue, same role as the floor it feeds.
        icon: 'kitchen',
        accent: 'flow',
        badge: {
          text: `${this.cookingOrderCount()} Cooking`,
          tone: this.cookingOrderCount() > 0 ? 'warning' : 'neutral',
        },
        caption: `${this.preparingOrderCount()} Preparing • ${this.servedOrderCount()} Served`,
        action: { id: 'active-orders', label: 'Live KOT stream' },
      },
      {
        label: 'Tender Breakdown',
        value: this.netCollectedFormatted() ?? '—',
        unit: closing ? 'collected' : undefined,
        // Settled money: positive, not activity — nothing new is being sold here.
        icon: 'payments',
        accent: 'positive',
        badge: closing
          ? { text: "Today's closing", tone: 'neutral' }
          : { text: 'Closing not loaded', tone: 'neutral' },
        segments: closing
          ? [
              { label: 'Cash', value: this.cashDrawerFormatted(), percent: this.cashRatio(), tone: 'success' },
              { label: 'UPI QR', value: this.onlineRevenueFormatted(), percent: this.upiRatio(), tone: 'brand' },
            ]
          : undefined,
        caption: closing
          ? 'Counts all-time POS invoices, not today'
          : "Cash and UPI splits appear once today's closing figures are available.",
        action: { id: 'terminals', label: 'Terminal fleet' },
      },
    ];
  });

  /**
   * A tile's foot action, routed where that metric lives. The switch is on the
   * action's id, not on its label: labels are copy and ids are behaviour, so a
   * wording change must not be able to reroute a tile.
   */
  onKpiAction(item: KpiItem): void {
    switch (item.action?.id) {
      case 'terminals':
        this.navigateToTerminals();
        break;
      case 'active-orders':
        this.navigateToActiveOrders();
        break;
    }
  }

  // Recent Live Ledger Stream — the dashboard's own recentOrders payload.
  readonly recentLedgerFeed = computed<LedgerFeedItem[]>(() => {
    const orders = (this.dashboard()?.recentOrders || []) as BusinessOrder[];

    return orders.slice(0, 6).map(order => ({
      invoiceNo: order.invoiceNumber || order.orderCode,
      tableAndChannel: order.tableNumber
        ? `Table ${order.tableNumber} · ${this.channelLabel(order)}`
        : this.channelLabel(order),
      amountFormatted: formatCurrency(Number(order.totalAmount) || 0),
      tenderType: order.paymentMethod ? this.titleCase(order.paymentMethod) : 'Tender not recorded',
      timeAgo: this.elapsedLabel(order.createdAt),
      syncLabel: order.paymentStatus ? this.titleCase(order.paymentStatus) : 'Payment status unknown'
    }));
  });

  private isDineInSeat(label: string): boolean {
    return label.startsWith('T-');
  }

  private seatLabel(order: PosActiveOrder): string {
    if (order.tableNumber) return `T-${order.tableNumber}`;
    return order.orderType === 'takeaway' ? 'Takeaway' : 'Counter';
  }

  private channelLabel(order: BusinessOrder | PosActiveOrder): string {
    const raw = (order.orderType || order.sourceType || '').toLowerCase();
    if (raw.includes('takeaway') || raw.includes('parcel')) return 'Takeaway';
    if (raw.includes('delivery') || raw.includes('online')) return 'Delivery';
    return 'Dine In';
  }

  private titleCase(value: string): string {
    return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
  }

  /** "N ago" from a real timestamp; empty when the server did not send one. */
  private elapsedLabel(timestamp?: number | null): string {
    if (!timestamp) return '';
    const ms = Date.now() - Number(timestamp);
    if (!Number.isFinite(ms) || ms < 0) return '';
    const minutes = Math.floor(ms / 60_000);
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    return `${hours}h ${minutes % 60}m ago`;
  }

  // Modal / Flyout Toggles
  readonly terminalsModalOpen = signal(false);
  readonly lowStockModalOpen = signal(false);

  readonly isOfflineError = computed(() => {
    const err = (this.dashboardError() || this.staleError()).toLowerCase();
    if (typeof navigator !== 'undefined' && !navigator.onLine) return true;
    return err.includes('offline') || err.includes('network') || err.includes('internet');
  });

  readonly lastLoadedTime = computed(() => {
    const stamp = this.lastUpdatedAt();
    if (!stamp) return '';
    return new Date(stamp).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
  });

  ngOnInit(): void {
    this.initDashboardStream();
    this.loadSupplementaryData();

    // Poll for fresh numbers, but stand down while the tab is hidden so a
    // dashboard left open overnight is not hammering the API.
    this.autoRefreshTimer = setInterval(() => {
      if (document.visibilityState === 'visible') {
        this.pollRefresh();
      }
    }, BusinessDashboardPageComponent.AUTO_REFRESH_MS);

    this.freshnessTimer = setInterval(() => this.nowTick.set(Date.now()), 15_000);
  }

  ngOnDestroy(): void {
    if (this.autoRefreshTimer) clearInterval(this.autoRefreshTimer);
    if (this.freshnessTimer) clearInterval(this.freshnessTimer);
    if (this.refreshSubscription) this.refreshSubscription.unsubscribe();
    this.autoRefreshTimer = null;
    this.freshnessTimer = null;
    this.refreshSubscription = null;
  }

  readonly freshnessLabel = computed(() => {
    const stamp = this.lastUpdatedAt();
    if (!stamp) return '';
    const seconds = Math.max(0, Math.floor((this.nowTick() - stamp) / 1000));
    if (seconds < 10) return 'Updated just now';
    if (seconds < 60) return `Updated ${seconds}s ago`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `Updated ${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    return `Updated ${hours}h ago`;
  });

  private initDashboardStream(): void {
    this.refreshSubscription = this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => {
        this.isRefreshing.set(true);
        if (!this.currentDashboard()) {
          this.dashboardError.set('');
        }

        return combineLatest([
          this.api.getDashboard(),
          this.api.getDashboardTrends().pipe(catchError(() => of({ yesterdayRevenue: 0, todayRevenue: 0 }))),
          this.api.getOrdersPaginated(0, 50, 'draft').pipe(catchError(() => of({ content: [], totalElements: 0 })))
        ]).pipe(
          map(([dashboardData, trendsData, ordersData]) => {
            this.isRefreshing.set(false);
            this.manualReload.set(false);
            this.staleError.set('');
            this.dashboardError.set('');

            const data = (dashboardData || {}) as any;
            const trends = (trendsData || {}) as any;
            const orders = (ordersData || {}) as any;

            // Map live orders
            if (orders && Array.isArray(orders.content)) {
              const liveActive: PosActiveOrder[] = orders.content
                .filter((o: any) => (o.orderStatus || '').trim().toLowerCase() === 'draft')
                .slice(0, 50)
                .map((o: any) => {
                  let status: PosActiveOrder['status'] = 'Preparing';
                  const st = (o.orderStatus || '').toUpperCase();
                  if (st === 'COMPLETED' || st === 'PAID') status = 'Completed';
                  else if (st === 'SERVED') status = 'Served';
                  else if (st === 'COOKING') status = 'Cooking';

                  const rawType = (o.orderType || o.sourceType || '').toLowerCase();
                  const isTakeaway = rawType.includes('takeaway') || rawType.includes('parcel');
                  const orderType = isTakeaway ? 'takeaway' : 'dine_in';
                  const typeLabel = isTakeaway ? 'Takeaway' : 'Dine In';
                  const payment = o.paymentMethod || 'Cash';

                  return {
                    orderId: o.orderId,
                    orderCode: o.orderCode || String(o.orderId),
                    invoiceNumber: o.invoiceNumber || undefined,
                    publicToken: o.publicToken || undefined,
                    orderType,
                    status,
                    sourceType: typeLabel,
                    dishImg: undefined,
                    itemsSummary: o.customerName ? `Guest: ${o.customerName}` : `${typeLabel} Order`,
                    tableAndChannel: `${typeLabel} · ${payment}`,
                    tableNumber: o.tableNumber != null && String(o.tableNumber).trim() !== ''
                      ? String(o.tableNumber).replace(/^t-?/i, '')
                      : undefined,
                    paymentMethod: payment,
                    totalAmount: Number(o.totalAmount) || 0,
                    createdAt: o.createdAt
                  };
                });

              this.activeOrders.set(liveActive);
            } else {
              this.activeOrders.set([]);
            }

            this.lastUpdatedAt.set(Date.now());

            const todayRev = Number(trends.todayRevenue) || Number(data.todayRevenue) || 0;
            const yestRev = Number(trends.yesterdayRevenue) || 0;
            const deltaToday = yestRev > 0
              ? Math.round(((todayRev - yestRev) / yestRev) * 100)
              : 0;

            const computedPayload = {
              ...data,
              shopName: data.shopName || this.auth.session()?.userName || 'KhanaBook',
              todayRevenueFormatted: formatCurrency(todayRev),
              todayRevenueValue: todayRev,
              totalRevenueFormatted: formatCurrency(Number(data.totalRevenue) || 0),
              deltaToday,
              posOrderCount: Number(data.posOrderCount) || 0,
              pendingPosPayments: Number(data.pendingPosPayments) || 0
            };

            this.currentDashboard.set(computedPayload);
            this.playKpiEntrance();
            return computedPayload;
          }),
          catchError((error: unknown) => {
            this.isRefreshing.set(false);
            this.manualReload.set(false);
            const msg = this.formatErrorMessage(error);
            if (this.currentDashboard()) {
              this.staleError.set(msg);
              this.toast.error(msg);
            } else {
              this.dashboardError.set(msg);
              this.staleError.set('');
            }
            return of(null);
          })
        );
      })
    ).subscribe();
  }

  private formatErrorMessage(error: unknown): string {
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      return 'No internet connection. Check your network and try again.';
    }
    const resp = error as { status?: number; error?: { message?: string; error?: string } };
    if (resp?.status === 0) {
      return 'No internet connection. Check your network and try again.';
    }
    if (resp?.status === 401) {
      return 'Your session has expired. Please sign in again.';
    }
    if (resp?.status === 403) {
      return 'You do not have permission to view this. Ask the shop owner to grant it.';
    }
    if (resp?.status === 500) {
      return 'The server could not complete this request (500). Your data is unchanged.';
    }
    if (resp?.status === 400) {
      return resp.error?.message || resp.error?.error || 'Invalid request. Please refresh and try again.';
    }
    return resp?.error?.message || resp?.error?.error || 'Unable to load dashboard.';
  }

  // Load supplementary widgets from backend endpoints
  private loadSupplementaryData(): void {
    const today = getTodayIsoDate();
    this.inventoryStatus.set('loading');

    // 1. Hourly Sales Curve (GET /analytics/hourly-sales)
    this.api.getHourlySales(today).pipe(catchError(() => of([]))).subscribe(data => {
      this.hourlySalesData.set(Array.isArray(data) ? data : []);
      this.playHourlyBars();
    });

    // 2. Top Selling Dishes (GET /analytics/item-sales)
    this.api.getItemSales(today, today).pipe(catchError(() => of([]))).subscribe(data => {
      if (Array.isArray(data) && data.length > 0) {
        this.topDishesData.set(data.sort((a, b) => (b.quantitySold || 0) - (a.quantitySold || 0)).slice(0, 5));
      } else {
        this.topDishesData.set([]);
      }
    });

    // 3. Low Stock Materials (GET /inventory/materials)
    this.api.getInventoryMaterials().subscribe({
      next: materials => {
        if (!Array.isArray(materials)) {
          this.inventoryStatus.set('unavailable');
          return;
        }
        const low = materials.filter(m => {
          const threshold = Number(m.lowStockThreshold) || 0;
          const quantity = Number(m.stockQuantity) || 0;
          return threshold > 0 && quantity <= threshold;
        });
        this.lowStockMaterials.set(low);
        this.inventoryStatus.set('available');
      },
      error: () => {
        this.lowStockMaterials.set([]);
        this.inventoryStatus.set('unavailable');
      }
    });

    // 4. POS Terminal Fleet Health (GET /business/terminals)
    this.api.getTerminals().pipe(catchError(() => of([]))).subscribe(terms => {
      this.terminals.set(Array.isArray(terms) ? terms : []);
    });

    // 5. Daily Closing Split (GET /analytics/daily-closing)
    this.api.getDailyClosing(today).pipe(catchError(() => of(null))).subscribe(closing => {
      if (closing) {
        this.dailyClosingData.set(closing);
      }
    });

    // 6. Notifications (GET /notifications)
    this.api.getNotifications(10).pipe(catchError(() => of({ status: 'ok', notifications: [], unreadCount: 0 }))).subscribe(res => {
      if (res && Array.isArray(res.notifications)) {
        this.notifications.set(res.notifications);
        this.unreadNotifsCount.set(res.unreadCount || 0);
      } else {
        this.notifications.set([]);
        this.unreadNotifsCount.set(0);
      }
    });
  }

  // Computed: Terminal Counts
  /**
   * A terminal counts as online only when it is flagged active *and* its
   * lifecycle status is ACTIVE — same rule the Terminals page uses. The old
   * `isActive || status === 'active'` check could report a terminal that the
   * server had already moved to RECOVERY_REQUIRED as healthy.
   */
  readonly onlineTerminalsCount = computed(() => {
    const list = this.terminals();
    return list.filter(t => this.isTerminalOnline(t)).length;
  });

  /** Terminals the server flagged for recovery — these need an owner action. */
  readonly fleetAttentionCount = computed(() => {
    const list = this.terminals();
    return list.filter(t => (t.status || '').toUpperCase() === 'RECOVERY_REQUIRED').length;
  });

  private isTerminalOnline(t: BusinessTerminal): boolean {
    return t.isActive === true && (t.status || '').toUpperCase() === 'ACTIVE';
  }

  /**
   * Lifecycle state of a terminal for display: ACTIVE / RECOVERY_REQUIRED /
   * REPLACED / INACTIVE / REVOKED are distinct server states, so the fleet
   * view says which one applies instead of flattening everything to Offline.
   */
  terminalStatusOf(t: BusinessTerminal): { label: string; cls: string; tone: 'active' | 'attention' | 'offline' } {
    const status = (t.status || '').toUpperCase();
    if (this.isTerminalOnline(t)) {
      return { label: 'Active', cls: 'badge-completed', tone: 'active' };
    }
    if (status === 'RECOVERY_REQUIRED') {
      return { label: 'Needs recovery', cls: 'badge-recovery', tone: 'attention' };
    }
    if (status === 'REPLACED') {
      return { label: 'Replaced', cls: '', tone: 'offline' };
    }
    if (status === 'REVOKED') {
      return { label: 'Revoked', cls: 'badge-revoked', tone: 'offline' };
    }
    if (status === 'INACTIVE') {
      return { label: 'Deactivated', cls: '', tone: 'offline' };
    }
    return { label: 'Offline', cls: '', tone: 'offline' };
  }

  readonly totalTerminalsCount = computed(() => {
    return this.terminals().length;
  });

  // Computed: Cash vs UPI Breakdown
  readonly cashDrawerFormatted = computed(() => {
    const c = this.dailyClosingData();
    if (c && c.expectedCash != null) {
      return formatCurrency(Number(c.expectedCash) || 0);
    }
    return formatCurrency(0);
  });

  readonly onlineRevenueFormatted = computed(() => {
    const c = this.dailyClosingData();
    if (c && c.netRevenue != null && c.expectedCash != null) {
      const online = Math.max(0, Number(c.netRevenue) - Number(c.expectedCash));
      return formatCurrency(online);
    }
    return formatCurrency(0);
  });

  readonly cashRatio = computed(() => {
    const c = this.dailyClosingData();
    if (!c || !c.netRevenue || Number(c.netRevenue) <= 0) return 0;
    const cash = Number(c.expectedCash) || 0;
    return Math.min(100, Math.max(0, Math.round((cash / Number(c.netRevenue)) * 100)));
  });

  readonly upiRatio = computed(() => {
    const c = this.dailyClosingData();
    if (!c || !c.netRevenue || Number(c.netRevenue) <= 0) return 0;
    return 100 - this.cashRatio();
  });

  readonly hasHourlySales = computed(() => {
    return this.hourlySalesData().some(r => (r.itemsSold || 0) > 0);
  });

  // Computed: Hourly Bar Displays
  readonly displayHourlyBars = computed<HourlyBarDisplay[]>(() => {
    const raw = this.hourlySalesData();
    const operatingHours = [10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22];
    const currentHour = new Date().getHours();

    const maxItems = Math.max(...raw.map(r => r.itemsSold || 0), 1);
    const hasSales = this.hasHourlySales();

    return operatingHours.map(h => {
      const match = raw.find(r => r.hour === h);
      const items = match ? match.itemsSold : 0;
      const heightPct = hasSales ? Math.max(8, Math.round((items / maxItems) * 100)) : 8;

      const hourLabel = h === 12 ? '12p' : h > 12 ? `${h - 12}p` : `${h}a`;

      return {
        hourLabel,
        rawHour: h,
        itemsSold: items,
        heightPct,
        isPeak: hasSales && items >= maxItems * 0.85 && items > 0,
        isCurrent: h === currentHour
      };
    });
  });

  /** The three figures under the chart, each read straight from the hourly rows. */
  readonly itemsSoldToday = computed(() =>
    this.hourlySalesData().reduce((sum, row) => sum + (row.itemsSold || 0), 0)
  );

  readonly activeHourCount = computed(() =>
    this.hourlySalesData().filter(row => (row.itemsSold || 0) > 0).length
  );

  /** One sentence for a screen reader before it reaches the (collapsed) table. */
  readonly hourlySummaryText = computed(() => {
    if (!this.hasHourlySales()) return 'No sales recorded yet today, so there is no hourly curve.';
    const bars = this.displayHourlyBars();
    const peak = bars.reduce((prev, curr) => (curr.itemsSold > prev.itemsSold ? curr : prev), bars[0]);
    return `Items sold by hour today, busiest at ${peak.hourLabel} with ${peak.itemsSold} items. `
      + `${this.activeHourCount()} of ${bars.length} opening hours have sales.`;
  });

  readonly busiestHourText = computed(() => {
    if (!this.hasHourlySales()) return 'No sales yet';
    const bars = this.displayHourlyBars();
    const peak = bars.reduce((prev, curr) => (curr.itemsSold > prev.itemsSold ? curr : prev), bars[0]);
    return `${peak.hourLabel} • ${peak.itemsSold} items`;
  });

  trackByTable = (_: number, table: FloorTableItem) => table.id;

  readonly peakHourText = computed(() => {
    if (!this.hasHourlySales()) return '';
    const bars = this.displayHourlyBars();
    const peak = bars.reduce((prev, curr) => (curr.itemsSold > prev.itemsSold ? curr : prev), bars[0]);
    if (peak && peak.itemsSold > 0) {
      return `Peak: ${peak.hourLabel} (${peak.itemsSold} items)`;
    }
    return '';
  });

  // Computed: Top Dishes Leaderboard
  readonly displayTopDishes = computed<TopDishDisplay[]>(() => {
    const list = this.topDishesData();
    if (!list || list.length === 0) return [];
    const maxQty = Math.max(...list.map(d => d.quantitySold || 0), 1);

    return list.slice(0, 5).map(d => ({
      name: d.name,
      quantitySold: d.quantitySold || 0,
      revenue: d.revenue || 0,
      revenueFormatted: formatCurrency(d.revenue || 0),
      volumePct: Math.max(12, Math.round(((d.quantitySold || 0) / maxQty) * 100))
    }));
  });

  // Computed KPIs
  readonly unsettledOrdersCount = computed(() => {
    const data = this.dashboard();
    return Number(data?.pendingPosPayments) || 0;
  });

  readonly completedCount = computed(() => {
    const data = this.dashboard();
    if (data && data.posOrderCount != null) {
      const total = Number(data.posOrderCount) || 0;
      const pending = Number(data.pendingPosPayments) || 0;
      return Math.max(0, total - pending);
    }
    return 0;
  });

  readonly completedRate = computed(() => {
    const total = Number(this.dashboard()?.posOrderCount) || 0;
    const completed = this.completedCount();
    if (total === 0) return 0;
    return Math.min(100, Math.round((completed / total) * 100));
  });

  /**
   * Revenue trend badge. Returns null while today has no sales: a "↓100% vs
   * yesterday" badge on a ₹0.00 day reads like a crisis and is really just
   * "we have not taken an order yet". The first sale sets the baseline.
   */
  readonly revenueTrend = computed<{ arrow: string; label: string; tone: 'up' | 'down' | 'neutral' } | null>(() => {
    const data = this.dashboard();
    if (!data) return null;
    const today = Number(data.todayRevenueValue) || 0;
    if (today <= 0) return null;
    const delta = Number(data.deltaToday) || 0;
    if (delta === 0) {
      return { arrow: '', label: 'Level with yesterday', tone: 'neutral' };
    }
    return {
      arrow: delta > 0 ? '↑' : '↓',
      label: `${Math.abs(delta)}% vs yest.`,
      tone: delta > 0 ? 'up' : 'down'
    };
  });

  toggleTerminalsModal(): void {
    this.terminalsModalOpen.update(v => !v);
  }

  toggleLowStockModal(): void {
    this.lowStockModalOpen.update(v => !v);
  }

  @HostListener('document:keydown.escape')
  onEscapeCloseModals(): void {
    if (this.terminalsModalOpen()) this.terminalsModalOpen.set(false);
    if (this.lowStockModalOpen()) this.lowStockModalOpen.set(false);
  }

  markNotificationAsRead(item: NotificationItem): void {
    if (item.isRead) return;
    this.api.markNotificationRead(item.id).subscribe({
      next: () => {
        this.notifications.update(list => list.map(n => n.id === item.id ? { ...n, isRead: true } : n));
        this.unreadNotifsCount.update(c => Math.max(0, c - 1));
      },
      error: () => {
        item.isRead = true;
        this.unreadNotifsCount.update(c => Math.max(0, c - 1));
      }
    });
  }

  markAllNotificationsRead(): void {
    this.api.markAllNotificationsRead().subscribe({
      next: () => {
        this.notifications.update(list => list.map(n => ({ ...n, isRead: true })));
        this.unreadNotifsCount.set(0);
        this.toast.show('All notifications marked as read', 'success');
      },
      error: () => {
        this.notifications.update(list => list.map(n => ({ ...n, isRead: true })));
        this.unreadNotifsCount.set(0);
      }
    });
  }

  handleImageError(orderCode: string): void {
    this.imageErrors.update(errs => ({ ...errs, [orderCode]: true }));
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }

  formatDateValue(value: number | null | undefined): string {
    return formatDate(value ?? null);
  }

  /** User-requested reload (header button, retry buttons). */
  refresh(): void {
    this.manualReload.set(true);
    this.kpiEntranceDone = false;
    this.refresh$.next();
    this.loadSupplementaryData();
  }

  /** Staggered KPI tile entrance — first paint + manual refresh only, never on 60s poll. */
  private playKpiEntrance(): void {
    if (this.kpiEntranceDone || typeof document === 'undefined') return;
    this.kpiEntranceDone = true;
    requestAnimationFrame(() => setTimeout(() => {
      try {
        const tiles = document.querySelectorAll('.kb-kpi-row .kb-kpi');
        if (tiles.length) {
          animate('.kb-kpi-row .kb-kpi', {
            opacity: [0, 1],
            y: [14, 0],
            duration: 450,
            delay: stagger(70),
            ease: 'outQuad'
          });
        }
      } catch { /* animation is progressive enhancement */ }
    }, 30));
  }

  /** Hourly bars grow from baseline — runs when hourly data arrives. */
  private playHourlyBars(): void {
    if (typeof document === 'undefined') return;
    requestAnimationFrame(() => setTimeout(() => {
      try {
        const fills = document.querySelectorAll('.hourly-bar-node .bar-pill-fill');
        if (fills.length) {
          animate('.hourly-bar-node .bar-pill-fill', {
            scaleY: [0, 1],
            duration: 600,
            delay: stagger(35),
            ease: 'outQuad'
          });
        }
      } catch { /* noop */ }
    }, 30));
  }

  /** Background poll — silent, see `viewDashboard`. */
  private pollRefresh(): void {
    this.refresh$.next();
    this.loadSupplementaryData();
  }

  copyInvoice(order: PosActiveOrder): void {
    const session = this.auth.session();
    const link = buildInvoiceLink(session?.restaurantId, order.orderId, order.publicToken);
    if (navigator.clipboard) {
      navigator.clipboard.writeText(link).then(() => {
        this.toast.show(`Invoice link copied for Order #${order.orderCode}`, 'success');
      }).catch(() => {
        this.toast.show(`Order #${order.orderCode} invoice link ready`, 'info');
      });
    } else {
      this.toast.show(`Order #${order.orderCode} invoice link ready`, 'info');
    }
  }

  navigateToOrders(): void {
    void this.router.navigate(['/business/orders']);
  }

  navigateToStaff(): void {
    void this.router.navigate(['/business/staff']);
  }

  navigateToMenu(): void {
    void this.router.navigate(['/business/menu']);
  }

  navigateToActiveOrders(): void {
    void this.router.navigate(['/business/orders'], { queryParams: { tab: 'live' } });
  }

  navigateToTerminals(): void {
    this.terminalsModalOpen.set(false);
    void this.router.navigate(['/business/terminals']);
  }

  navigateToInventory(): void {
    this.lowStockModalOpen.set(false);
    void this.router.navigate(['/business/inventory']);
  }

  openOrderDetail(orderId: number): void {
    this.api.getOrderDetail(orderId).subscribe({
      next: (detail) => this.selectedOrderDetail.set(detail),
      error: () => {
        this.toast.show('Unable to load order details.', 'error');
      }
    });
  }

  closeOrderDetail(): void {
    this.selectedOrderDetail.set(null);
  }
}
