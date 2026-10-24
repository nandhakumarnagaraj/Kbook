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
    FocusTrapDirective,
    PageHeaderComponent,
    AnimatedCounterComponent,
    NotificationBellComponent,
    RefreshButtonComponent,
    HeaderSearchComponent
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

      <!-- ── Bento KPI Row (4 Key Metrics) ── -->
      <section class="bento-kpi-grid" aria-label="Key Performance Indicators">
        <!-- 1. Revenue Tile (Hero Tile) -->
        <article class="bento-tile bento-tile--hero">
          <div class="bento-tile__head">
            <span class="bento-tile__eyebrow">Today's Revenue</span>
            <span class="trend-badge" *ngIf="revenueTrend() as t" [class.trend-badge--down]="t.tone === 'down'">
              {{ t.arrow }} {{ t.label }}
            </span>
            <!-- No delta while today's number is 0 — a "↓100%" on ₹0.00 is noise,
                 not insight (first sale sets the baseline). -->
            <span class="trend-badge trend-badge--neutral" *ngIf="dashboard() && !revenueTrend()">
              No sales yet
            </span>
          </div>
          <div class="bento-tile__main-metric">
            <app-animated-counter [value]="data.todayRevenueValue" format="currency"></app-animated-counter>
          </div>
          <div class="revenue-split-bar">
            <!-- Proportional Segmented Meter -->
            <div class="split-meter" *ngIf="dailyClosingData() && (dailyClosingData()?.netRevenue > 0); else emptySplitBar">
              <div class="split-meter-segment split-meter-segment--upi" [style.width.%]="upiRatio()" [title]="'UPI/Online: ' + upiRatio() + '%'"></div>
              <div class="split-meter-segment split-meter-segment--cash" [style.width.%]="cashRatio()" [title]="'Cash: ' + cashRatio() + '%'"></div>
            </div>
            <ng-template #emptySplitBar>
              <div class="split-meter split-meter--neutral"></div>
            </ng-template>

            <div class="split-legend-row">
              <div class="split-pill">
                <span class="split-legend split-legend--upi"></span>
                <span class="split-label">UPI:</span>
                <strong class="split-val">{{ onlineRevenueFormatted() }}</strong>
                <span class="split-pct" *ngIf="upiRatio() > 0">({{ upiRatio() }}%)</span>
              </div>
              <span class="split-divider">|</span>
              <div class="split-pill">
                <span class="split-legend split-legend--cash"></span>
                <span class="split-label">Cash:</span>
                <strong class="split-val">{{ cashDrawerFormatted() }}</strong>
                <span class="split-pct" *ngIf="cashRatio() > 0">({{ cashRatio() }}%)</span>
              </div>
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
            <div class="progress-meter-track" role="progressbar" aria-label="Order completion rate" [attr.aria-valuenow]="completedRate()" aria-valuemin="0" aria-valuemax="100">
              <div class="progress-meter-fill" [style.width.%]="completedRate()"></div>
            </div>
            <div class="progress-meter-legend">
              <span><strong>{{ completedCount() }}</strong> Paid</span>
              <span [class.text-amber]="unsettledOrdersCount() > 0"><strong>{{ unsettledOrdersCount() }}</strong> Unsettled</span>
            </div>
            <!-- Scope note: posOrderCount/pendingPosPayments are lifetime counts on
                 the server, so 291/291 can sit next to ₹0.00 today without being a bug. -->
            <span class="sub-caption settlement-scope-note">Counts all-time POS invoices, not today</span>
          </div>
        </article>

        <!-- 3. Active & Unsettled Orders -->
        <article class="bento-tile">
          <div class="bento-tile__head">
            <span class="bento-tile__eyebrow">Unsettled Orders</span>
            <span class="kpi-tag" [class.kpi-tag--warn]="unsettledOrdersCount() > 0" [class.kpi-tag--green]="unsettledOrdersCount() === 0">
              {{ unsettledOrdersCount() > 0 ? 'Pending Settlement' : 'All Clear' }}
            </span>
          </div>
          <div class="bento-tile__main-metric">
            <app-animated-counter [value]="unsettledOrdersCount()"></app-animated-counter>
            <span class="metric-unit-sub">Unsettled</span>
          </div>
          <div class="bento-tile__footer-action">
            <span class="sub-caption">{{ activeOrders().length }} active orders</span>
            <button type="button" class="tile-action-link" (click)="navigateToActiveOrders()">
              View active orders
              <svg viewBox="0 0 20 20" width="14" height="14" fill="currentColor"><path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd"/></svg>
            </button>
          </div>
        </article>

        <!-- 4. Fleet & System Health -->
        <article class="bento-tile">
          <div class="bento-tile__head">
            <span class="bento-tile__eyebrow">Terminal Fleet</span>
            <span class="kpi-tag" [class.kpi-tag--warn]="fleetAttentionCount() > 0" *ngIf="fleetAttentionCount() > 0; else fleetOkTag">
              {{ fleetAttentionCount() }} Need recovery
            </span>
            <ng-template #fleetOkTag>
              <span class="kpi-tag" [class.kpi-tag--green]="onlineTerminalsCount() > 0" [class.kpi-tag--neutral]="onlineTerminalsCount() === 0">
                {{ onlineTerminalsCount() }} Active
              </span>
            </ng-template>
          </div>
          <div class="bento-tile__main-metric">
            <app-animated-counter [value]="onlineTerminalsCount()"></app-animated-counter>
            <span class="metric-total-sub">/ {{ totalTerminalsCount() }}</span>
          </div>
          <div class="bento-tile__footer-action">
            <span class="sub-caption" [class.text-amber]="lowStockMaterials().length > 0">
              {{ inventoryStatus() === 'loading'
                ? 'Checking stock…'
                : inventoryStatus() === 'unavailable'
                  ? 'Stock levels unavailable'
                  : lowStockMaterials().length > 0
                    ? (lowStockMaterials().length + ' raw items low')
                    : 'No low-stock alerts' }}
            </span>
            <button type="button" class="tile-action-link" (click)="navigateToTerminals()">
              Manage terminals
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
                <span class="bar-tick-label" [class.bar-tick-label--current]="bar.isCurrent">
                  {{ bar.hourLabel }}
                  <span *ngIf="bar.isCurrent" class="current-hour-dot" aria-label="Current hour"></span>
                </span>
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

    </div>

    <!-- ── Terminal Fleet Status Modal ── -->
    <div class="tactile-modal-overlay" *ngIf="terminalsModalOpen()" appFocusTrap (click)="toggleTerminalsModal()">
      <div class="tactile-modal-box" (click)="$event.stopPropagation()">
        <div class="tactile-modal-head">
          <h3>POS terminals</h3>
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
    <div class="tactile-modal-overlay" *ngIf="lowStockModalOpen()" appFocusTrap (click)="toggleLowStockModal()">
      <div class="tactile-modal-box" (click)="$event.stopPropagation()">
        <div class="tactile-modal-head">
          <div class="tactile-head-with-icon">
            <span class="warning-icon-badge">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3M12 9v4m0 4h.01"/></svg>
            </span>
            <h3>Low Stock Raw Ingredients</h3>
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
      font-size: 0.76rem;
      font-weight: 500;
      color: var(--kb-color-muted-foreground);
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }

    /* Clarifies that the settlement tile counts all-time invoices */
    .settlement-scope-note {
      margin-top: 0.1rem;
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
          this.api.getOrdersPaginated(0, 15, 'draft').pipe(catchError(() => of({ content: [], totalElements: 0 })))
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
        const tiles = document.querySelectorAll('.bento-kpi-grid .bento-tile');
        if (tiles.length) {
          animate('.bento-kpi-grid .bento-tile', {
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
