import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal, computed, OnDestroy, OnInit } from '@angular/core';
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
  NotificationItem
} from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { buildInvoiceLink } from '../../core/utils/invoice-link.util';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { AnimatedCounterComponent } from '../../shared/animated-counter.component';
import { NotificationBellComponent } from '../../shared/notification-bell.component';

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
    PageHeaderComponent,
    AnimatedCounterComponent,
    NotificationBellComponent
  ],
  template: `
    <div class="page-shell" *ngIf="dashboard() as data; else loading">
      <!-- ── Top Cockpit Header Bar (shared PageHeaderComponent) ── -->
      <app-page-header
        [title]="data.shopName || 'KhanaBook Restaurant'"
        subtitle="Real-time sales, order settlements, and terminal activity.">
          <!-- Terminal Fleet Pill -->
          <button
            type="button"
            class="header-pill-btn"
            (click)="toggleTerminalsModal()"
            title="View active POS terminals fleet"
            aria-label="POS terminals">
            <span class="dot-beacon" [class.dot-beacon--active]="onlineTerminalsCount() > 0"></span>
            <span class="pill-btn__text"><strong>{{ onlineTerminalsCount() }}/{{ totalTerminalsCount() }}</strong> Terminals</span>
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

          <!-- Refresh Button -->
          <button
            type="button"
            class="refresh-btn"
            [disabled]="isRefreshing()"
            (click)="refresh()"
            title="Refresh live metrics">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" [class.spinning]="isRefreshing()">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            <span>Refresh</span>
          </button>
          <span class="freshness-stamp" *ngIf="freshnessLabel()" role="status">{{ freshnessLabel() }}</span>
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

      <!-- ── Bento KPI Row (4 Key Metrics) ── -->
      <section class="bento-kpi-grid" aria-label="Key Performance Indicators">
        <!-- 1. Revenue Tile (Hero Tile) -->
        <article class="bento-tile bento-tile--hero">
          <div class="bento-tile__head">
            <span class="bento-tile__eyebrow">Today's Revenue</span>
            <span class="trend-badge" *ngIf="data.deltaToday !== 0" [class.trend-badge--down]="data.deltaToday < 0">
              {{ data.deltaToday > 0 ? '↑' : '↓' }} {{ Math.abs(data.deltaToday) }}% vs yest.
            </span>
            <span class="trend-badge trend-badge--neutral" *ngIf="data.deltaToday === 0">
              Live Intake
            </span>
          </div>
          <div class="bento-tile__main-metric">
            <app-animated-counter [value]="data.todayRevenueValue" format="currency"></app-animated-counter>
          </div>
          <div class="revenue-split-bar">
            <div class="split-pill">
              <span class="split-legend split-legend--cash"></span>
              <span class="split-label">Cash:</span>
              <strong class="split-val">{{ cashDrawerFormatted() }}</strong>
            </div>
            <span class="split-divider">|</span>
            <div class="split-pill">
              <span class="split-legend split-legend--upi"></span>
              <span class="split-label">UPI/Online:</span>
              <strong class="split-val">{{ onlineRevenueFormatted() }}</strong>
            </div>
          </div>
        </article>

        <!-- 2. Settlement Health Tile -->
        <article class="bento-tile">
          <div class="bento-tile__head">
            <span class="bento-tile__eyebrow">Settlement Rate</span>
            <span class="kpi-tag" [class.kpi-tag--green]="completedRate() >= 80" [class.kpi-tag--amber]="completedRate() < 80">
              {{ completedRate() }}% Settled
            </span>
          </div>
          <div class="bento-tile__main-metric">
            <app-animated-counter [value]="completedCount()"></app-animated-counter>
            <span class="metric-total-sub">/ {{ data.posOrderCount }}</span>
          </div>
          <div class="progress-meter-container">
            <div class="progress-meter-track" role="progressbar" [attr.aria-valuenow]="completedRate()" aria-valuemin="0" aria-valuemax="100">
              <div class="progress-meter-fill" [style.width.%]="completedRate()"></div>
            </div>
            <div class="progress-meter-legend">
              <span><strong>{{ completedCount() }}</strong> Paid</span>
              <span [class.text-amber]="unsettledOrdersCount() > 0"><strong>{{ unsettledOrdersCount() }}</strong> Unsettled</span>
            </div>
          </div>
        </article>

        <!-- 3. Active & Unsettled Orders -->
        <article class="bento-tile">
          <div class="bento-tile__head">
            <span class="bento-tile__eyebrow">Active Orders</span>
            <span class="kpi-tag" [class.kpi-tag--warn]="unsettledOrdersCount() > 0" [class.kpi-tag--neutral]="unsettledOrdersCount() === 0">
              {{ unsettledOrdersCount() > 0 ? 'Pending Settlement' : 'All Clear' }}
            </span>
          </div>
          <div class="bento-tile__main-metric">
            <app-animated-counter [value]="unsettledOrdersCount()"></app-animated-counter>
            <span class="metric-unit-sub">Unsettled</span>
          </div>
          <div class="bento-tile__footer-action">
            <span class="sub-caption">{{ filteredActiveOrders().length }} live orders</span>
            <button type="button" class="tile-action-link" (click)="navigateToActiveOrders()">
              View Active
              <svg viewBox="0 0 20 20" width="14" height="14" fill="currentColor"><path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd"/></svg>
            </button>
          </div>
        </article>

        <!-- 4. Fleet & System Health -->
        <article class="bento-tile">
          <div class="bento-tile__head">
            <span class="bento-tile__eyebrow">Terminal Fleet</span>
            <span class="kpi-tag" [class.kpi-tag--green]="onlineTerminalsCount() > 0" [class.kpi-tag--neutral]="onlineTerminalsCount() === 0">
              {{ onlineTerminalsCount() }} Online
            </span>
          </div>
          <div class="bento-tile__main-metric">
            <app-animated-counter [value]="onlineTerminalsCount()"></app-animated-counter>
            <span class="metric-total-sub">/ {{ totalTerminalsCount() }}</span>
          </div>
          <div class="bento-tile__footer-action">
            <span class="sub-caption" [class.text-amber]="lowStockMaterials().length > 0">
              {{ lowStockMaterials().length > 0 ? (lowStockMaterials().length + ' raw items low') : 'Inventory optimal' }}
            </span>
            <button type="button" class="tile-action-link" (click)="navigateToTerminals()">
              Hardware
              <svg viewBox="0 0 20 20" width="14" height="14" fill="currentColor"><path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd"/></svg>
            </button>
          </div>
        </article>
      </section>

      <!-- ── Middle Bento Grid: Service Velocity & Bestsellers ── -->
      <section class="bento-intel-grid" aria-label="Operational Intelligence">
        <!-- Peak Hour Velocity Curve -->
        <article class="intel-card intel-card--chart">
          <div class="intel-card__head">
            <div class="intel-card__title-group">
              <span class="intel-card__eyebrow">Service Velocity</span>
              <h3 class="intel-card__title">Peak Hour Traffic Curve</h3>
            </div>
            <span class="rush-badge" *ngIf="peakHourText()">{{ peakHourText() }}</span>
          </div>

          <div class="hourly-chart-wrapper" *ngIf="hasHourlySales(); else emptyHourly">
            <div class="hourly-bars-track">
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
                <span class="bar-tick-label" [class.bar-tick-label--current]="bar.isCurrent">{{ bar.hourLabel }}</span>
              </div>
            </div>
          </div>
          <ng-template #emptyHourly>
            <div class="intel-empty-box">
              <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="empty-icon-glyph">
                <line x1="18" y1="20" x2="18" y2="10"/>
                <line x1="12" y1="20" x2="12" y2="4"/>
                <line x1="6" y1="20" x2="6" y2="14"/>
              </svg>
              <h4>No sales velocity recorded yet today</h4>
              <p>Completed POS orders will plot hourly order volume and identify peak dining rush here.</p>
            </div>
          </ng-template>
        </article>

        <!-- Top Selling Dishes Today -->
        <article class="intel-card">
          <div class="intel-card__head">
            <div class="intel-card__title-group">
              <span class="intel-card__eyebrow">Kitchen Velocity</span>
              <h3 class="intel-card__title">Top Selling Dishes Today</h3>
            </div>
            <button type="button" class="link-btn-text" (click)="navigateToMenu()">View Menu</button>
          </div>

          <div class="top-dishes-stack" *ngIf="displayTopDishes().length > 0; else emptyDishes">
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
          <ng-template #emptyDishes>
            <div class="intel-empty-box">
              <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="empty-icon-glyph">
                <path d="M18 2v6a3 3 0 0 1-3 3 3 3 0 0 1-3-3V2"/>
                <path d="M15 2v19"/>
                <path d="M5 2c1.5 2 1.5 5 0 7v12"/>
              </svg>
              <h4>No dish sales recorded yet today</h4>
              <p>Top items ranked by volume and revenue will populate as dining tickets close.</p>
            </div>
          </ng-template>
        </article>
      </section>

      <!-- ── Live Floor Activity ── -->
      <section class="floor-strip" aria-label="Live floor activity">
        <div class="floor-strip__head">
          <div class="floor-strip__title-group">
            <span class="intel-card__eyebrow">Live Floor</span>
            <h3 class="intel-card__title">Recent Orders</h3>
            <span class="floor-strip__count">{{ filteredActiveOrders().length }} of {{ activeOrders().length }} shown</span>
          </div>

          <div class="floor-strip__controls">
            <div class="floor-chip-group" role="group" aria-label="Filter orders by channel">
              <button
                type="button"
                class="floor-chip"
                *ngFor="let c of channels"
                [class.floor-chip--active]="selectedChannel() === c.value"
                [attr.aria-pressed]="selectedChannel() === c.value"
                (click)="setChannel(c.value)">
                {{ c.label }}
              </button>
            </div>
            <label class="floor-search">
              <span class="visually-hidden">Search live orders</span>
              <input
                type="search"
                class="floor-search__input"
                placeholder="Search order #"
                [ngModel]="searchQuery"
                (ngModelChange)="onSearchChange($event)"
                aria-label="Search live orders" />
            </label>
            <button type="button" class="link-btn-text" (click)="resetFilters()" *ngIf="selectedChannel() !== 'all' || searchQuery">
              Reset
            </button>
          </div>
        </div>

        <div class="floor-list" *ngIf="filteredActiveOrders().length > 0; else noLiveOrders">
          <button
            type="button"
            class="floor-row"
            *ngFor="let order of visibleActiveOrders(); trackBy: trackByOrderCode"
            (click)="openOrderDetail(order.orderId)">
            <span class="floor-row__code">{{ order.orderCode }}</span>
            <span class="floor-row__summary">{{ order.itemsSummary }}</span>
            <span class="floor-row__channel">{{ order.tableAndChannel }}</span>
            <span class="status-chip" [ngClass]="getStatusClass(order.status)">{{ order.status }}</span>
            <strong class="floor-row__amount">{{ formatCurrencyValue(order.totalAmount) }}</strong>
          </button>
          <button type="button" class="tile-action-link floor-more" (click)="navigateToActiveOrders()">
            View all on Active Orders
            <svg viewBox="0 0 20 20" width="14" height="14" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd"/></svg>
          </button>
        </div>
        <ng-template #noLiveOrders>
          <div class="intel-empty-box">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="empty-icon-glyph" aria-hidden="true">
              <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
              <rect x="9" y="3" width="6" height="4" rx="1"/>
            </svg>
            <h4>No orders match this view</h4>
            <p>Clear the search or channel filter to see recent orders from your POS terminals.</p>
          </div>
        </ng-template>
      </section>
    </div>

    <!-- ── Terminal Fleet Status Modal ── -->
    <div class="tactile-modal-overlay" *ngIf="terminalsModalOpen()" (click)="toggleTerminalsModal()">
      <div class="tactile-modal-box" (click)="$event.stopPropagation()">
        <div class="tactile-modal-head">
          <h3>POS Hardware Terminals</h3>
          <button type="button" class="modal-close-icon" (click)="toggleTerminalsModal()">✕</button>
        </div>
        <div class="tactile-modal-body">
          <div class="terminal-item-row" *ngFor="let t of terminals()">
            <div class="terminal-core-info">
              <span class="dot-beacon" [class.dot-beacon--active]="t.isActive || t.status === 'active' || t.status === 'ACTIVE'"></span>
              <div>
                <strong>{{ t.terminalName || 'Terminal ' + (t.terminalSeries || t.id) }}</strong>
                <span class="terminal-tag">Series: {{ t.terminalSeries || 'POS' }}</span>
              </div>
            </div>
            <div class="terminal-meta-info">
              <span class="status-chip" [class.badge-completed]="t.isActive || t.status === 'active' || t.status === 'ACTIVE'">
                {{ (t.isActive || t.status === 'active' || t.status === 'ACTIVE') ? 'Active' : 'Offline' }}
              </span>
              <span class="time-sub">Updated: {{ formatDateValue(t.updatedAt) }}</span>
            </div>
          </div>
          <div class="tactile-empty-sub" *ngIf="terminals().length === 0">
            <p>No POS terminals registered yet.</p>
          </div>
        </div>
        <div class="tactile-modal-foot">
          <button type="button" class="header-action-btn header-action-btn--primary" (click)="navigateToTerminals()">Manage Hardware Fleet</button>
        </div>
      </div>
    </div>

    <!-- ── Low Stock Alert Modal ── -->
    <div class="tactile-modal-overlay" *ngIf="lowStockModalOpen()" (click)="toggleLowStockModal()">
      <div class="tactile-modal-box" (click)="$event.stopPropagation()">
        <div class="tactile-modal-head">
          <div class="tactile-head-with-icon">
            <span class="warning-icon-badge">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3M12 9v4m0 4h.01"/></svg>
            </span>
            <h3>Low Stock Raw Ingredients</h3>
          </div>
          <button type="button" class="modal-close-icon" (click)="toggleLowStockModal()">✕</button>
        </div>
        <div class="tactile-modal-body">
          <p class="modal-lead-note">The following raw materials are currently below their minimum kitchen reserve threshold.</p>
          <div class="tactile-table-wrapper">
            <table class="orders-tactile-table">
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

    /* Revenue Split Bar */
    .revenue-split-bar {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      padding-top: 0.4rem;
      font-size: 0.78rem;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }
    .split-pill { display: flex; align-items: center; gap: 0.35rem; }
    .split-legend {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }
    .split-legend--cash { background: var(--kb-color-success); }
    .split-legend--upi { background: var(--kb-color-primary); }
    .split-val { color: #FFFFFF; font-weight: 600; }
    .split-divider { color: var(--kb-color-muted-foreground); }

    /* ── Live Floor strip ── */
    .floor-strip {
      background: #FFFFFF;
      border: 1px solid var(--kb-color-border);
      border-radius: 18px;
      padding: 1.2rem 1.5rem 1.35rem;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03), 0 4px 12px rgba(15, 23, 42, 0.02);
    }

    .floor-strip__head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
      margin-bottom: 0.9rem;
    }

    .floor-strip__title-group {
      display: flex;
      align-items: baseline;
      gap: 0.6rem;
      flex-wrap: wrap;
    }

    .floor-strip__count {
      font-size: 0.78rem;
      color: var(--kb-color-muted-foreground);
    }

    .floor-strip__controls {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      flex-wrap: wrap;
    }

    /* Channel filter buttons for the Live Floor strip. These were previously
       called .channel-chip, which collided with the .channel-chip table badge
       (same class name, different element) — the badge rule silently overrode
       the button padding. Renamed so each name means exactly one thing. */
    .floor-chip-group {
      display: inline-flex;
      gap: 0.25rem;
      padding: 0.15rem;
      background: var(--kb-color-surface-2);
      border-radius: 10px;
    }

    .floor-chip {
      border: none;
      background: transparent;
      color: var(--kb-color-muted-foreground);
      font-size: 0.8rem;
      font-weight: 600;
      padding: 0.3rem 0.7rem;
      border-radius: 8px;
      cursor: pointer;
    }

    .floor-chip--active {
      background: #FFFFFF;
      color: var(--kb-color-primary);
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
    }

    .floor-search__input {
      min-height: 34px;
      padding: 0.3rem 0.6rem;
      border: 1px solid var(--kb-color-border-strong);
      border-radius: 8px;
      background: #FFFFFF;
      color: var(--kb-color-foreground);
      font-size: 0.82rem;
    }

    .floor-search__input:focus {
      outline: none;
      border-color: var(--kb-color-primary);
      box-shadow: 0 0 0 2px rgba(108, 39, 217, 0.15);
    }

    .floor-list {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .floor-row {
      display: grid;
      grid-template-columns: 84px 1.4fr 1fr auto auto;
      align-items: center;
      gap: 0.75rem;
      width: 100%;
      padding: 0.55rem 0.7rem;
      background: transparent;
      border: 1px solid transparent;
      border-radius: 10px;
      text-align: left;
      cursor: pointer;
      transition: background 120ms ease, border-color 120ms ease;
    }

    .floor-row:hover {
      background: var(--kb-color-surface-2);
      border-color: var(--kb-color-border);
    }

    .floor-row__code {
      font-weight: 700;
      font-size: 0.82rem;
      font-variant-numeric: tabular-nums;
    }

    .floor-row__summary {
      font-size: 0.82rem;
      color: var(--kb-color-foreground);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .floor-row__channel {
      font-size: 0.76rem;
      color: var(--kb-color-muted-foreground);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .floor-row__amount {
      font-size: 0.85rem;
      font-variant-numeric: tabular-nums;
    }

    .floor-more {
      align-self: flex-start;
      margin-top: 0.35rem;
    }

    @media (max-width: 860px) {
      .floor-row {
        grid-template-columns: 72px 1fr auto;
        row-gap: 0.2rem;
      }
      .floor-row__channel { display: none; }
    }

    /* Hourly Chart */
    .hourly-chart-wrapper {
      flex: 1;
      display: flex;
      align-items: flex-end;
      min-height: 140px;
    }
    .hourly-bars-track {
      width: 100%;
      height: 120px;
      display: flex;
      align-items: flex-end;
      gap: 8px;
    }
    .hourly-bar-node {
      flex: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
      gap: 6px;
      cursor: pointer;
    }
    .bar-pill-track {
      width: 100%;
      height: 90px;
      background: var(--kb-color-bg-app);
      border: 1px solid var(--kb-color-surface-2);
      border-radius: 8px;
      display: flex;
      align-items: flex-end;
      overflow: hidden;
    }
    .bar-pill-fill {
      width: 100%;
      background: var(--kb-color-border-strong);
      border-radius: 7px;
      transition: height 240ms ease, background 140ms ease;
    }
    .bar-pill-fill--peak { background: var(--kb-color-primary); }
    .bar-pill-fill--current { background: var(--kb-color-burnt); }
    .hourly-bar-node:hover .bar-pill-fill { filter: brightness(0.92); }
    .bar-tick-label {
      font-size: 0.68rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }
    .bar-tick-label--current { color: var(--kb-color-burnt); font-weight: 700; }

    /* Top Dishes Stack */
    .top-dishes-stack {
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
    }
    .dish-leader-row {
      display: flex;
      align-items: center;
      gap: 0.85rem;
    }
    .dish-rank-badge {
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: var(--kb-color-primary-soft);
      color: var(--kb-color-primary);
      font-size: 0.74rem;
      font-weight: 800;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .dish-leader-info {
      flex: 1;
      min-width: 0;
    }
    .dish-leader-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      margin-bottom: 0.25rem;
    }
    .dish-name-label {
      font-size: 0.86rem;
      color: var(--kb-color-foreground);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .dish-qty-label {
      font-size: 0.75rem;
      color: var(--kb-color-muted-foreground);
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }
    .dish-bar-track {
      width: 100%;
      height: 5px;
      background: var(--kb-color-surface-2);
      border-radius: 999px;
      overflow: hidden;
    }
    .dish-bar-fill {
      height: 100%;
      background: linear-gradient(90deg, var(--kb-color-primary) 0%, var(--kb-color-primary) 100%);
      border-radius: 999px;
    }
    .dish-rev-label {
      font-size: 0.86rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
      flex-shrink: 0;
    }

    /* .channel-chip table badge + its channel modifiers now live in the global
       styles/bento.css (identical values) so every page shares one definition. */

    /* ── Modals Overlay ── */
    .terminal-item-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.75rem 0.85rem;
      border: 1px solid var(--kb-color-surface-2);
      border-radius: 12px;
      background: var(--kb-color-bg-app);
    }
    .terminal-core-info { display: flex; align-items: center; gap: 0.75rem; }
    .terminal-tag { display: block; font-size: 0.72rem; color: var(--kb-color-muted-foreground); }
    .terminal-meta-info { text-align: right; }
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

  private refreshSubscription: Subscription | null = null;

  /** Wall-clock of the last successful dashboard load, and a coarse ticker that
   *  drives the relative "updated Xs ago" label. */
  private readonly lastUpdatedAt = signal<number | null>(null);
  private readonly nowTick = signal(Date.now());

  private autoRefreshTimer: ReturnType<typeof setInterval> | null = null;
  private freshnessTimer: ReturnType<typeof setInterval> | null = null;

  /** Auto-refresh cadence. The dashboard aggregates ~9 endpoints, so it polls
   *  slower than the Active Orders board (30s) and skips hidden tabs. */
  private static readonly AUTO_REFRESH_MS = 60_000;

  readonly channels: ReadonlyArray<{ label: string; value: 'all' | 'dine_in' | 'takeaway' }> = [
    { label: 'All', value: 'all' },
    { label: 'Dine In', value: 'dine_in' },
    { label: 'Takeaway', value: 'takeaway' }
  ];
  readonly selectedChannel = signal<'all' | 'dine_in' | 'takeaway'>('all');
  searchQuery = '';

  readonly activeOrders = signal<PosActiveOrder[]>([]);
  readonly imageErrors = signal<Record<string, boolean>>({});
  readonly selectedOrderDetail = signal<OrderDetailResponse | null>(null);

  // Live Data Signals from Swagger
  readonly hourlySalesData = signal<HourlySalesRow[]>([]);
  readonly topDishesData = signal<ItemSalesRow[]>([]);
  readonly lowStockMaterials = signal<any[]>([]);
  readonly terminals = signal<BusinessTerminal[]>([]);
  readonly dailyClosingData = signal<any>(null);
  readonly notifications = signal<NotificationItem[]>([]);
  readonly unreadNotifsCount = signal<number>(0);

  // Modal / Flyout Toggles
  readonly terminalsModalOpen = signal(false);
  readonly lowStockModalOpen = signal(false);

  readonly Math = Math;

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
        this.refresh();
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

  /** Only the first few orders are rendered; the full list lives on Active Orders. */
  readonly visibleActiveOrders = computed(() => this.filteredActiveOrders().slice(0, 5));

  onSearchChange(value: string): void {
    this.searchQuery = value ?? '';
  }

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
          this.api.getOrdersPaginated(0, 20).pipe(catchError(() => of({ content: [], totalElements: 0 })))
        ]).pipe(
          map(([dashboardData, trendsData, ordersData]) => {
            this.isRefreshing.set(false);
            this.staleError.set('');
            this.dashboardError.set('');

            const data = (dashboardData || {}) as any;
            const trends = (trendsData || {}) as any;
            const orders = (ordersData || {}) as any;

            // Map live orders
            if (orders && Array.isArray(orders.content)) {
              const liveActive: PosActiveOrder[] = orders.content
                .filter((o: any) => o.orderStatus !== 'CANCELLED' && o.orderStatus !== 'REFUNDED' && o.orderStatus !== 'VOIDED')
                .slice(0, 15)
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
            return computedPayload;
          }),
          catchError((error: unknown) => {
            this.isRefreshing.set(false);
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

    // 1. Hourly Sales Curve (GET /analytics/hourly-sales)
    this.api.getHourlySales(today).pipe(catchError(() => of([]))).subscribe(data => {
      this.hourlySalesData.set(Array.isArray(data) ? data : []);
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
    this.api.getInventoryMaterials().pipe(catchError(() => of([]))).subscribe(materials => {
      if (Array.isArray(materials)) {
        const low = materials.filter(m => {
          const thresh = Number(m.lowStockThreshold) || 0;
          const qty = Number(m.stockQuantity) || 0;
          return thresh > 0 && qty <= thresh;
        });
        this.lowStockMaterials.set(low);
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

  // Computed: Channel Label
  readonly channelLabel = computed(() => {
    switch (this.selectedChannel()) {
      case 'dine_in': return 'Dine In';
      case 'takeaway': return 'Takeaway';
      default: return 'All Orders';
    }
  });

  // Computed: Filtered Active Orders by Channel & Search Query
  readonly filteredActiveOrders = computed(() => {
    const channel = this.selectedChannel();
    const query = (this.searchQuery || '').trim().toLowerCase();
    const orders = this.activeOrders();

    return orders.filter(order => {
      if (channel !== 'all') {
        const orderType = (order.orderType || '').toLowerCase();
        if (channel === 'dine_in' && orderType !== 'dine_in') return false;
        if (channel === 'takeaway' && orderType !== 'takeaway') return false;
      }

      if (!query) return true;
      return (order.orderCode || '').toLowerCase().includes(query)
        || (order.itemsSummary || '').toLowerCase().includes(query)
        || (order.tableAndChannel || '').toLowerCase().includes(query)
        || (order.sourceType || '').toLowerCase().includes(query)
        || (order.status || '').toLowerCase().includes(query);
    });
  });

  // Computed: Terminal Counts
  readonly onlineTerminalsCount = computed(() => {
    const list = this.terminals();
    return list.filter(t => t.isActive || (t as any).status === 'active' || (t as any).status === 'ACTIVE').length;
  });

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
  readonly pendingKotCount = this.unsettledOrdersCount;

  readonly completedCount = computed(() => {
    const c = this.dailyClosingData();
    if (c && c.completedOrders != null) {
      return Number(c.completedOrders) || 0;
    }
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
    if (total === 0) return 100;
    return Math.min(100, Math.round((completed / total) * 100));
  });

  setChannel(channel: 'all' | 'dine_in' | 'takeaway'): void {
    this.selectedChannel.set(channel);
  }

  resetFilters(): void {
    this.selectedChannel.set('all');
    this.searchQuery = '';
  }

  toggleTerminalsModal(): void {
    this.terminalsModalOpen.update(v => !v);
  }

  toggleLowStockModal(): void {
    this.lowStockModalOpen.update(v => !v);
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

  getStatusClass(status: PosActiveOrder['status']): string {
    switch (status) {
      case 'Preparing': return 'badge-preparing';
      case 'Cooking': return 'badge-cooking';
      case 'Served': return 'badge-served';
      case 'Completed': return 'badge-completed';
      default: return 'badge-preparing';
    }
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

  trackByOrderCode = (_: number, order: PosActiveOrder) => order.orderCode;

  refresh(): void {
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

  navigateToKot(): void {
    this.navigateToActiveOrders();
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
