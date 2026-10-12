import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal, computed, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { combineLatest, of, Subject } from 'rxjs';
import { catchError, map, switchMap, startWith } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
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
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';

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
  imports: [CommonModule, FormsModule, OrderDetailModalComponent],
  template: `
    <div class="dash-shell" *ngIf="dashboard() as data; else loading">
      <!-- ── Top Cockpit Header Bar ── -->
      <header class="dash-header">
        <div class="dash-header__left">
          <h1 class="dash-header__title">{{ data.shopName || 'KhanaBook Restaurant' }}</h1>
          <p class="dash-header__sub">Real-time sales, order settlements, and terminal activity.</p>
        </div>

        <div class="dash-header__right">
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
          <div class="notif-wrapper">
            <button
              type="button"
              class="icon-action-btn"
              (click)="toggleNotifications()"
              aria-label="Notifications"
              [attr.aria-expanded]="notificationsOpen()">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
              <span class="notif-badge" *ngIf="unreadNotifsCount() > 0">{{ unreadNotifsCount() }}</span>
            </button>

            <!-- Notifications Drawer -->
            <div class="notif-flyout" *ngIf="notificationsOpen()">
              <div class="notif-flyout__head">
                <h4>System Notifications</h4>
                <button type="button" class="link-btn-text" (click)="markAllNotificationsRead()" *ngIf="notifications().length > 0">Mark all read</button>
              </div>
              <div class="notif-flyout__body" *ngIf="notifications().length > 0; else noNotifs">
                <div
                  class="notif-item"
                  *ngFor="let n of notifications()"
                  [class.notif-item--unread]="!n.isRead"
                  (click)="markNotificationAsRead(n)">
                  <div class="notif-unread-dot" *ngIf="!n.isRead"></div>
                  <div class="notif-copy">
                    <strong class="notif-title">{{ n.title || 'POS Alert' }}</strong>
                    <p class="notif-msg">{{ n.message }}</p>
                    <span class="notif-time">{{ formatDateValue(n.createdAt) }}</span>
                  </div>
                </div>
              </div>
              <ng-template #noNotifs>
                <div class="notif-empty">
                  <p>All caught up! No unread notifications.</p>
                </div>
              </ng-template>
            </div>
          </div>

          <!-- Refresh Button -->
          <button
            type="button"
            class="header-action-btn"
            [disabled]="isRefreshing()"
            (click)="refresh()"
            title="Refresh live metrics">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" [class.spinning]="isRefreshing()">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            <span>{{ isRefreshing() ? 'Refreshing...' : 'Refresh' }}</span>
          </button>
        </div>
      </header>

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
          <div class="bento-tile__main-metric">{{ data.todayRevenueFormatted }}</div>
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
            {{ completedCount() }}
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
            {{ unsettledOrdersCount() }}
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
            {{ onlineTerminalsCount() }}
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
      <div class="dash-shell" *ngIf="dashboardError(); else posSkeleton">
        <div class="intel-empty-box" style="margin-top: 3rem;">
          <h4>Unable to Load Dashboard</h4>
          <p>{{ dashboardError() }}</p>
          <button class="header-action-btn header-action-btn--primary" (click)="refresh()">Retry</button>
        </div>
      </div>
      <ng-template #posSkeleton>
        <div class="dash-shell">
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
      background: #F8FAFC;
      min-height: 100vh;
      color: #0F172A;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .dash-shell {
      padding: 1.75rem 2rem 3.5rem;
      max-width: 1440px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    /* ── Header Bar ── */
    .dash-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
      flex-wrap: wrap;
    }
    .dash-header__eyebrow {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.76rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748B;
      margin-bottom: 0.25rem;
    }
    .pulse-indicator {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #10B981;
      box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
    }
    .pill-divider { color: #CBD5E1; }
    .live-date-pill {
      color: #6C27D9;
      background: #F5F3FF;
      padding: 0.1rem 0.5rem;
      border-radius: 999px;
      font-weight: 600;
    }
    .dash-header__title {
      margin: 0;
      font-size: 1.65rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.03em;
    }
    .dash-header__sub {
      margin: 0.2rem 0 0;
      font-size: 0.88rem;
      color: #64748B;
    }
    .dash-header__right {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      flex-wrap: wrap;
    }

    /* Tactical Header Buttons */
    .header-pill-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.55rem 0.85rem;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 12px;
      font-size: 0.82rem;
      font-weight: 600;
      color: #334155;
      cursor: pointer;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .header-pill-btn:hover {
      border-color: #CBD5E1;
      background: #F8FAFC;
    }
    .header-pill-btn:active { transform: scale(0.98); }
    .header-pill-btn--alert {
      background: #FFFBEB;
      border-color: #FDE68A;
      color: #92400E;
    }
    .header-pill-btn--alert:hover {
      background: #FEF3C7;
      border-color: #FCD34D;
    }

    .dot-beacon {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #94A3B8;
    }
    .dot-beacon--active {
      background: #10B981;
      box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.25);
    }

    .icon-action-btn {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      border-radius: 12px;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      color: #475569;
      cursor: pointer;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
      transition: all 140ms ease;
    }
    .icon-action-btn:hover { background: #F8FAFC; border-color: #CBD5E1; }
    .icon-action-btn:active { transform: scale(0.96); }

    .notif-badge {
      position: absolute;
      top: -3px;
      right: -3px;
      padding: 0 5px;
      min-width: 16px;
      height: 16px;
      border-radius: 999px;
      background: #EF4444;
      color: #FFFFFF;
      font-size: 0.65rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .header-action-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.55rem 0.95rem;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 12px;
      font-size: 0.82rem;
      font-weight: 600;
      color: #334155;
      cursor: pointer;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .header-action-btn:hover {
      background: #F8FAFC;
      border-color: #CBD5E1;
    }
    .header-action-btn:active { transform: scale(0.98); }
    .header-action-btn--primary {
      background: #6C27D9;
      border-color: #6C27D9;
      color: #FFFFFF;
      box-shadow: 0 2px 8px rgba(108, 39, 217, 0.22);
    }
    .header-action-btn--primary:hover {
      background: #5B21B6;
      border-color: #5B21B6;
    }

    .spinning { animation: spin 1s linear infinite; }
    @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

    /* ── Bento KPI Grid ── */
    .bento-kpi-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 1.15rem;
    }
    @media (max-width: 1100px) {
      .bento-kpi-grid { grid-template-columns: repeat(2, 1fr); }
    }
    @media (max-width: 600px) {
      .bento-kpi-grid { grid-template-columns: 1fr; }
    }

    .bento-tile {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 18px;
      padding: 1.25rem 1.35rem;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03), 0 4px 12px rgba(15, 23, 42, 0.02);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      gap: 0.5rem;
      transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
    }
    .bento-tile:hover {
      border-color: #CBD5E1;
      box-shadow: 0 6px 16px rgba(15, 23, 42, 0.06);
    }

    .bento-tile--hero {
      background: linear-gradient(145deg, #0F172A 0%, #1E1B4B 100%);
      border: 1px solid #1E293B;
      color: #FFFFFF;
      box-shadow: 0 4px 20px rgba(15, 23, 42, 0.15);
    }
    .bento-tile--hero .bento-tile__eyebrow { color: #94A3B8; }
    .bento-tile--hero .bento-tile__main-metric { color: #FFFFFF; }

    .bento-tile__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .bento-tile__eyebrow {
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748B;
    }

    .trend-badge {
      display: inline-flex;
      align-items: center;
      padding: 0.15rem 0.5rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 700;
      background: rgba(16, 185, 129, 0.15);
      color: #34D399;
      font-variant-numeric: tabular-nums;
    }
    .trend-badge--down {
      background: rgba(239, 68, 68, 0.15);
      color: #F87171;
    }
    .trend-badge--neutral {
      background: rgba(148, 163, 184, 0.15);
      color: #CBD5E1;
    }

    .kpi-tag {
      padding: 0.15rem 0.55rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 700;
      background: #F1F5F9;
      color: #475569;
      font-variant-numeric: tabular-nums;
    }
    .kpi-tag--green { background: #ECFDF5; color: #059669; }
    .kpi-tag--amber { background: #FFFBEB; color: #D97706; }
    .kpi-tag--warn { background: #FEF2F2; color: #DC2626; }
    .kpi-tag--neutral { background: #F8FAFC; color: #64748B; }

    .bento-tile__main-metric {
      font-size: 1.85rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      color: #0F172A;
      font-variant-numeric: tabular-nums;
      display: flex;
      align-items: baseline;
      gap: 0.35rem;
    }
    .metric-total-sub {
      font-size: 0.95rem;
      font-weight: 600;
      color: #94A3B8;
      letter-spacing: normal;
    }
    .metric-unit-sub {
      font-size: 0.85rem;
      font-weight: 600;
      color: #64748B;
      letter-spacing: normal;
    }

    /* Revenue Split Bar */
    .revenue-split-bar {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      padding-top: 0.4rem;
      font-size: 0.78rem;
      color: #94A3B8;
      font-variant-numeric: tabular-nums;
    }
    .split-pill { display: flex; align-items: center; gap: 0.35rem; }
    .split-legend {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }
    .split-legend--cash { background: #34D399; }
    .split-legend--upi { background: #818CF8; }
    .split-val { color: #FFFFFF; font-weight: 600; }
    .split-divider { color: #475569; }

    /* Progress Meter */
    .progress-meter-container {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      padding-top: 0.2rem;
    }
    .progress-meter-track {
      width: 100%;
      height: 6px;
      background: #F1F5F9;
      border-radius: 999px;
      overflow: hidden;
    }
    .progress-meter-fill {
      height: 100%;
      background: linear-gradient(90deg, #10B981 0%, #34D399 100%);
      border-radius: 999px;
      transition: width 240ms ease;
    }
    .progress-meter-legend {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 0.74rem;
      color: #64748B;
      font-variant-numeric: tabular-nums;
    }
    .text-amber { color: #D97706 !important; font-weight: 600; }
    .text-rose { color: #E11D48 !important; }

    .bento-tile__footer-action {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: 0.25rem;
    }
    .sub-caption {
      font-size: 0.76rem;
      color: #64748B;
      font-weight: 500;
    }
    .tile-action-link {
      display: inline-flex;
      align-items: center;
      gap: 0.2rem;
      background: transparent;
      border: none;
      color: #6C27D9;
      font-size: 0.76rem;
      font-weight: 700;
      cursor: pointer;
      padding: 0;
      transition: color 140ms ease;
    }
    .tile-action-link:hover { color: #4C1D95; }

    /* ── Middle Bento Grid ── */
    .bento-intel-grid {
      display: grid;
      grid-template-columns: 1.55fr 1fr;
      gap: 1.15rem;
    }
    @media (max-width: 960px) {
      .bento-intel-grid { grid-template-columns: 1fr; }
    }

    .intel-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 18px;
      padding: 1.35rem 1.5rem;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03), 0 4px 12px rgba(15, 23, 42, 0.02);
      display: flex;
      flex-direction: column;
    }
    .intel-card__head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1.25rem;
    }
    .intel-card__eyebrow {
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748B;
      display: block;
      margin-bottom: 0.15rem;
    }
    .intel-card__title {
      margin: 0;
      font-size: 1.12rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
    }
    .rush-badge {
      padding: 0.2rem 0.65rem;
      border-radius: 999px;
      font-size: 0.74rem;
      font-weight: 700;
      background: #F5F3FF;
      color: #6C27D9;
      border: 1px solid #DDD6FE;
      font-variant-numeric: tabular-nums;
    }
    .link-btn-text {
      background: none;
      border: none;
      color: #6C27D9;
      font-size: 0.8rem;
      font-weight: 700;
      cursor: pointer;
      padding: 0;
      transition: color 140ms ease;
    }
    .link-btn-text:hover { color: #4C1D95; text-decoration: underline; }

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
      background: #F8FAFC;
      border: 1px solid #F1F5F9;
      border-radius: 8px;
      display: flex;
      align-items: flex-end;
      overflow: hidden;
    }
    .bar-pill-fill {
      width: 100%;
      background: #CBD5E1;
      border-radius: 7px;
      transition: height 240ms ease, background 140ms ease;
    }
    .bar-pill-fill--peak { background: #6C27D9; }
    .bar-pill-fill--current { background: #F97316; }
    .hourly-bar-node:hover .bar-pill-fill { filter: brightness(0.92); }
    .bar-tick-label {
      font-size: 0.68rem;
      font-weight: 600;
      color: #94A3B8;
      font-variant-numeric: tabular-nums;
    }
    .bar-tick-label--current { color: #F97316; font-weight: 700; }

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
      background: #F5F3FF;
      color: #6C27D9;
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
      color: #1E293B;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .dish-qty-label {
      font-size: 0.75rem;
      color: #64748B;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }
    .dish-bar-track {
      width: 100%;
      height: 5px;
      background: #F1F5F9;
      border-radius: 999px;
      overflow: hidden;
    }
    .dish-bar-fill {
      height: 100%;
      background: linear-gradient(90deg, #6C27D9 0%, #A855F7 100%);
      border-radius: 999px;
    }
    .dish-rev-label {
      font-size: 0.86rem;
      font-weight: 800;
      color: #0F172A;
      font-variant-numeric: tabular-nums;
      flex-shrink: 0;
    }

    .intel-empty-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 2.25rem 1.5rem;
      text-align: center;
      gap: 0.45rem;
    }
    .empty-icon-glyph { color: #CBD5E1; margin-bottom: 0.25rem; }
    .intel-empty-box h4 { margin: 0; font-size: 0.95rem; font-weight: 700; color: #334155; }
    .intel-empty-box p { margin: 0; font-size: 0.82rem; color: #64748B; max-width: 360px; line-height: 1.45; }

    /* ── Bottom Bento Card: Live Stream & Orders ── */
    .bento-orders-container {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 18px;
      padding: 1.5rem;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03), 0 4px 12px rgba(15, 23, 42, 0.02);
    }
    .orders-head-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.25rem;
      margin-bottom: 1.25rem;
      flex-wrap: wrap;
    }
    .orders-title-row {
      display: flex;
      align-items: center;
      gap: 0.65rem;
    }
    .orders-title {
      margin: 0;
      font-size: 1.2rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
    }
    .counter-badge {
      display: inline-block;
      padding: 0.18rem 0.55rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 700;
      background: #F1F5F9;
      color: #475569;
      font-variant-numeric: tabular-nums;
    }
    .orders-sub {
      margin: 0.2rem 0 0;
      font-size: 0.85rem;
      color: #64748B;
    }

    .orders-head-controls {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      flex-wrap: wrap;
    }

    .channel-filter-tabs {
      display: inline-flex;
      background: #F1F5F9;
      padding: 3px;
      border-radius: 10px;
      gap: 2px;
    }
    .channel-tab-btn {
      padding: 0.4rem 0.75rem;
      border: none;
      background: transparent;
      border-radius: 8px;
      font-size: 0.78rem;
      font-weight: 600;
      color: #64748B;
      cursor: pointer;
      transition: all 120ms ease;
    }
    .channel-tab-btn:hover { color: #1E293B; }
    .channel-tab-btn--active {
      background: #FFFFFF;
      color: #0F172A;
      font-weight: 700;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
    }

    .order-search-box {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      padding: 0.45rem 0.75rem;
      width: 220px;
      transition: all 140ms ease;
    }
    .order-search-box:focus-within {
      background: #FFFFFF;
      border-color: #6C27D9;
      box-shadow: 0 0 0 3px rgba(108, 39, 217, 0.1);
    }
    .order-search-box svg { color: #94A3B8; }
    .order-search-box input {
      border: none;
      background: transparent;
      outline: none;
      width: 100%;
      font-size: 0.82rem;
      color: #0F172A;
    }

    /* Tactile Orders Table */
    .orders-table-wrapper {
      overflow-x: auto;
      border: 1px solid #F1F5F9;
      border-radius: 12px;
    }
    .orders-tactile-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.84rem;
      text-align: left;
    }
    .orders-tactile-table th {
      padding: 0.75rem 1rem;
      background: #F8FAFC;
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #64748B;
      border-bottom: 1px solid #E2E8F0;
    }
    .orders-tactile-table td {
      padding: 0.85rem 1rem;
      border-bottom: 1px solid #F1F5F9;
      color: #334155;
      vertical-align: middle;
    }
    .order-row-interactive {
      cursor: pointer;
      transition: background 120ms ease;
    }
    .order-row-interactive:hover {
      background: #F8FAFC;
    }
    .order-row-interactive:last-child td { border-bottom: none; }

    .order-hash-code {
      font-family: monospace;
      font-weight: 700;
      color: #0F172A;
      background: #F1F5F9;
      padding: 0.2rem 0.45rem;
      border-radius: 6px;
      font-size: 0.8rem;
    }

    .channel-chip {
      display: inline-block;
      padding: 0.18rem 0.55rem;
      border-radius: 6px;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.02em;
    }
    .channel-chip--dine_in { background: #EEF2FF; color: #4F46E5; }
    .channel-chip--takeaway { background: #FEF3C7; color: #B45309; }
    .channel-chip--delivery { background: #ECFDF5; color: #047857; }

    .order-guest-desc {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
    }
    .guest-title {
      font-weight: 600;
      color: #1E293B;
      font-size: 0.84rem;
    }
    .guest-meta {
      font-size: 0.74rem;
      color: #94A3B8;
    }

    .order-table-amount {
      font-weight: 800;
      color: #0F172A;
      font-variant-numeric: tabular-nums;
    }

    .status-chip {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 700;
    }
    .badge-preparing { background: #FEF3C7; color: #92400E; }
    .badge-cooking { background: #FFEDD5; color: #C2410C; }
    .badge-served { background: #E0E7FF; color: #3730A3; }
    .badge-completed { background: #ECFDF5; color: #065F46; }

    .time-stamp-label {
      font-size: 0.76rem;
      color: #64748B;
      font-variant-numeric: tabular-nums;
    }

    .action-btn-group {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.45rem;
    }
    .btn-micro {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.35rem 0.65rem;
      border-radius: 8px;
      border: 1px solid #E2E8F0;
      background: #FFFFFF;
      color: #334155;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 120ms ease;
    }
    .btn-micro:hover { background: #F1F5F9; border-color: #CBD5E1; }
    .btn-micro--primary {
      background: #6C27D9;
      border-color: #6C27D9;
      color: #FFFFFF;
    }
    .btn-micro--primary:hover { background: #5B21B6; }

    .empty-orders-view {
      padding: 3rem 1.5rem;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 0.5rem;
    }
    .empty-icon-circle {
      width: 56px;
      height: 56px;
      border-radius: 16px;
      background: #F8FAFC;
      border: 1px solid #EEF2F6;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #94A3B8;
      margin-bottom: 0.25rem;
    }
    .empty-orders-view h4 { margin: 0; font-size: 1.05rem; font-weight: 700; color: #0F172A; }
    .empty-orders-view p { margin: 0; font-size: 0.85rem; color: #64748B; max-width: 440px; }
    .empty-actions-row {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      margin-top: 0.75rem;
    }

    /* ── Notifications Flyout ── */
    .notif-wrapper { position: relative; }
    .notif-flyout {
      position: absolute;
      top: calc(100% + 8px);
      right: 0;
      width: 320px;
      max-height: 400px;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 16px;
      box-shadow: 0 10px 30px rgba(15, 23, 42, 0.12);
      z-index: 60;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .notif-flyout__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.85rem 1rem;
      border-bottom: 1px solid #F1F5F9;
      background: #F8FAFC;
    }
    .notif-flyout__head h4 { margin: 0; font-size: 0.88rem; font-weight: 700; color: #0F172A; }
    .notif-flyout__body {
      overflow-y: auto;
      max-height: 320px;
      display: flex;
      flex-direction: column;
    }
    .notif-item {
      position: relative;
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #F8FAFC;
      display: flex;
      align-items: flex-start;
      gap: 0.65rem;
      cursor: pointer;
      transition: background 120ms ease;
    }
    .notif-item:hover { background: #F8FAFC; }
    .notif-item--unread { background: #F5F3FF; }
    .notif-unread-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #6C27D9;
      margin-top: 5px;
      flex-shrink: 0;
    }
    .notif-copy { flex: 1; }
    .notif-title { font-size: 0.8rem; color: #0F172A; display: block; margin-bottom: 0.15rem; }
    .notif-msg { margin: 0 0 0.25rem; font-size: 0.74rem; color: #64748B; line-height: 1.4; }
    .notif-time { font-size: 0.68rem; color: #94A3B8; }
    .notif-empty { padding: 2rem 1rem; text-align: center; color: #94A3B8; font-size: 0.82rem; }

    /* ── Modals Overlay ── */
    .tactile-modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.45);
      backdrop-filter: blur(4px);
      z-index: 100;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
    }
    .tactile-modal-box {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 20px;
      width: 100%;
      max-width: 520px;
      max-height: 85vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 20px 40px rgba(15, 23, 42, 0.2);
      overflow: hidden;
    }
    .tactile-modal-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1.15rem 1.4rem;
      border-bottom: 1px solid #F1F5F9;
      background: #F8FAFC;
    }
    .tactile-modal-head h3 { margin: 0; font-size: 1.05rem; font-weight: 800; color: #0F172A; }
    .tactile-head-with-icon { display: flex; align-items: center; gap: 0.65rem; }
    .warning-icon-badge {
      width: 28px;
      height: 28px;
      border-radius: 8px;
      background: #FEF3C7;
      color: #D97706;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .modal-close-icon {
      background: none;
      border: none;
      font-size: 1.1rem;
      color: #94A3B8;
      cursor: pointer;
      padding: 0;
      line-height: 1;
    }
    .modal-close-icon:hover { color: #0F172A; }
    .tactile-modal-body {
      padding: 1.25rem 1.4rem;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
    }
    .modal-lead-note { margin: 0; font-size: 0.85rem; color: #64748B; }
    .terminal-item-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.75rem 0.85rem;
      border: 1px solid #F1F5F9;
      border-radius: 12px;
      background: #F8FAFC;
    }
    .terminal-core-info { display: flex; align-items: center; gap: 0.75rem; }
    .terminal-tag { display: block; font-size: 0.72rem; color: #94A3B8; }
    .terminal-meta-info { text-align: right; }
    .time-sub { display: block; font-size: 0.7rem; color: #94A3B8; margin-top: 0.15rem; }
    .tactile-empty-sub { text-align: center; color: #94A3B8; font-size: 0.85rem; padding: 1.5rem 0; }
    .tactile-modal-foot {
      padding: 1rem 1.4rem;
      border-top: 1px solid #F1F5F9;
      display: flex;
      justify-content: flex-end;
      background: #F8FAFC;
    }

    .skeleton-shimmer {
      background: linear-gradient(90deg, #F1F5F9 25%, #E2E8F0 50%, #F1F5F9 75%);
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
    }
    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
  `]
})
export class BusinessDashboardPageComponent implements OnInit {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  private readonly refresh$ = new Subject<void>();

  // State Signals
  readonly isRefreshing = signal(false);
  readonly dashboardError = signal('');
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
  readonly notificationsOpen = signal(false);
  readonly terminalsModalOpen = signal(false);
  readonly lowStockModalOpen = signal(false);

  readonly Math = Math;

  ngOnInit(): void {
    this.loadSupplementaryData();
  }

  // Primary Dashboard Data Stream
  readonly dashboard = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => {
        this.isRefreshing.set(true);
        this.dashboardError.set('');

        return combineLatest([
          this.api.getDashboard().pipe(catchError(() => of({}))),
          this.api.getDashboardTrends().pipe(catchError(() => of({ yesterdayRevenue: 0, todayRevenue: 0 }))),
          this.api.getOrdersPaginated(0, 20).pipe(catchError(() => of({ content: [], totalElements: 0 })))
        ]).pipe(
          map(([dashboardData, trendsData, ordersData]) => {
            this.isRefreshing.set(false);

            const data = dashboardData as any;
            const trends = trendsData as any;
            const orders = ordersData as any;

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

            const todayRev = Number(trends.todayRevenue) || Number(data.todayRevenue) || 0;
            const yestRev = Number(trends.yesterdayRevenue) || 0;
            const deltaToday = yestRev > 0
              ? Math.round(((todayRev - yestRev) / yestRev) * 100)
              : 0;

            return {
              ...data,
              shopName: data.shopName || this.auth.session()?.userName || 'KhanaBook',
              todayRevenueFormatted: formatCurrency(todayRev),
              totalRevenueFormatted: formatCurrency(Number(data.totalRevenue) || 0),
              deltaToday,
              posOrderCount: Number(data.posOrderCount) || 0,
              pendingPosPayments: Number(data.pendingPosPayments) || 0
            };
          }),
          catchError((error: unknown) => {
            this.isRefreshing.set(false);
            const response = error as { error?: { message?: string; error?: string } };
            this.dashboardError.set(
              response.error?.message || response.error?.error || 'Unable to load dashboard.'
            );
            return of(null);
          })
        );
      })
    )
  );

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

  toggleNotifications(): void {
    this.notificationsOpen.update(v => !v);
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
    const restaurantId = session?.restaurantId || 1;
    const token = order.publicToken || 'preview';
    const link = `https://kbook.iadv.cloud/api/v1/public/invoice/${restaurantId}/${order.orderId}/${token}`;
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
    void this.router.navigate(['/business/active-orders']);
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
