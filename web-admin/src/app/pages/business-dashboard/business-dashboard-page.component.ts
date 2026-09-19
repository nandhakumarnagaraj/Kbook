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
    <div class="pos-page-shell" *ngIf="dashboard() as data; else loading">
      <!-- ── Top Utility Header Bar ── -->
      <header class="pos-topbar">
        <!-- Left: Channel Filter Dropdown -->
        <div class="channel-dropdown-wrapper">
          <button
            type="button"
            class="channel-selector-btn"
            (click)="toggleChannelDropdown()"
            aria-haspopup="listbox"
            [attr.aria-expanded]="channelDropdownOpen()">
            <span>{{ channelLabel() }}</span>
            <svg class="chevron-icon" viewBox="0 0 20 20" width="16" height="16" fill="currentColor" aria-hidden="true">
              <path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd" />
            </svg>
          </button>
          <div class="channel-menu" *ngIf="channelDropdownOpen()" role="listbox">
            <button type="button" class="channel-opt" (click)="setChannel('dine_in')">Dine In</button>
            <button type="button" class="channel-opt" (click)="setChannel('takeaway')">Takeaway</button>
            <button type="button" class="channel-opt" (click)="setChannel('delivery')">Delivery</button>
            <button type="button" class="channel-opt" (click)="setChannel('all')">All Channels</button>
          </div>
        </div>

        <!-- Center: Universal Search Bar -->
        <div class="pos-search-pill">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            type="text"
            placeholder="Search orders, dishes, tables..."
            [(ngModel)]="searchQuery"
            aria-label="Search orders and dishes"
          />
        </div>

        <!-- Right: Status Indicators & Actions -->
        <div class="topbar-actions">
          <!-- Hardware Fleet Pulse Pill -->
          <div class="terminal-pulse-pill" (click)="toggleTerminalsModal()" title="View active POS terminals">
            <span class="pulse-dot" [class.pulse-dot--online]="onlineTerminalsCount() > 0"></span>
            <span class="pulse-text"><strong>{{ onlineTerminalsCount() }}/{{ totalTerminalsCount() }}</strong> Terminals Active</span>
          </div>

          <!-- Low Stock Warning Pill (if any materials low) -->
          <div
            class="low-stock-pill"
            *ngIf="lowStockMaterials().length > 0"
            (click)="toggleLowStockModal()"
            [title]="lowStockMaterials().length + ' raw materials below threshold'">
            <span class="alert-icon">⚠️</span>
            <span class="alert-text">{{ lowStockMaterials().length }} Low Stock</span>
          </div>

          <!-- Notification Bell with Flyout Trigger -->
          <div class="notification-wrap">
            <button
              type="button"
              class="icon-btn-round"
              (click)="toggleNotifications()"
              aria-label="Notifications"
              [attr.aria-expanded]="notificationsOpen()">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
              <span class="notification-badge" *ngIf="unreadNotifsCount() > 0">{{ unreadNotifsCount() }}</span>
            </button>

            <!-- Notifications Flyout Drawer -->
            <div class="notification-flyout" *ngIf="notificationsOpen()">
              <div class="flyout-header">
                <h4>System Notifications</h4>
                <button type="button" class="link-btn" (click)="markAllNotificationsRead()" *ngIf="notifications().length > 0">Mark all read</button>
              </div>
              <div class="flyout-body" *ngIf="notifications().length > 0; else noNotifs">
                <div
                  class="notif-row"
                  *ngFor="let n of notifications()"
                  [class.notif-unread]="!n.isRead"
                  (click)="markNotificationAsRead(n)">
                  <div class="notif-dot" *ngIf="!n.isRead"></div>
                  <div class="notif-content">
                    <strong class="notif-title">{{ n.title || 'POS Alert' }}</strong>
                    <p class="notif-msg">{{ n.message }}</p>
                    <span class="notif-time">{{ formatDateValue(n.createdAt) }}</span>
                  </div>
                </div>
              </div>
              <ng-template #noNotifs>
                <div class="flyout-empty">
                  <p>All caught up! No unread notifications.</p>
                </div>
              </ng-template>
            </div>
          </div>

          <!-- User Avatar matching purple circle in reference image -->
          <div class="user-avatar-circle" [title]="data.shopName || 'KhanaBook Operator'" aria-label="Operator Profile">
            {{ (data.shopName || 'K').charAt(0).toUpperCase() }}
          </div>
        </div>
      </header>

      <!-- ── 2-Column POS Dashboard Grid ── -->
      <div class="pos-dashboard-grid">
        <!-- ── Left Column: Active Orders ── -->
        <section class="active-orders-section" aria-label="Active Orders Stream">
          <div class="section-header-flex">
            <h2 class="section-title-pos">
              Active Orders ({{ filteredActiveOrders().length }})
            </h2>
            <span class="live-indicator-badge">● Live Counter</span>
          </div>

          <div class="active-orders-list">
            <article
              class="pos-order-card"
              *ngFor="let order of filteredActiveOrders(); trackBy: trackByOrderCode"
              tabindex="0"
              role="button"
              [attr.aria-label]="'View order #' + order.orderCode + ' ' + order.status"
              (click)="openOrderDetail(order.orderId)"
              (keydown.enter)="openOrderDetail(order.orderId)">
              
              <!-- Order Type Status Avatar -->
              <div class="order-avatar" [ngClass]="'order-avatar--' + order.status.toLowerCase()">
                <svg *ngIf="order.sourceType === 'TAKEAWAY'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/>
                  <path d="M3 6h18"/>
                  <path d="M16 10a4 4 0 0 1-8 0"/>
                </svg>
                <svg *ngIf="order.sourceType === 'DELIVERY'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="1" y="3" width="15" height="13"/>
                  <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/>
                  <circle cx="5.5" cy="18.5" r="2.5"/>
                  <circle cx="18.5" cy="18.5" r="2.5"/>
                </svg>
                <svg *ngIf="order.sourceType !== 'TAKEAWAY' && order.sourceType !== 'DELIVERY'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M18 2v6a3 3 0 0 1-3 3 3 3 0 0 1-3-3V2"/>
                  <path d="M15 2v19"/>
                  <path d="M5 2c1.5 2 1.5 5 0 7v12"/>
                </svg>
              </div>

              <!-- Center Info -->
              <div class="order-info-center">
                <div class="order-id-status-line">
                  <span class="order-code-bold">#{{ order.orderCode }}</span>
                  <span class="status-chip" [ngClass]="getStatusClass(order.status)">
                    {{ order.status }}
                  </span>
                </div>
                <p class="order-items-snippet" [title]="order.itemsSummary">
                  {{ order.itemsSummary }}
                </p>
                <div class="order-quick-actions" (click)="$event.stopPropagation()">
                  <button type="button" class="btn-micro" (click)="copyInvoice(order)" title="Copy public invoice preview link">
                    📄 Invoice
                  </button>
                  <button type="button" class="btn-micro" (click)="openOrderDetail(order.orderId)">
                    View
                  </button>
                </div>
              </div>

              <!-- Right Meta: Table & Amount -->
              <div class="order-meta-col">
                <span class="order-table-type">{{ order.tableAndChannel }}</span>
                <strong class="order-amount-large">{{ formatCurrencyValue(order.totalAmount) }}</strong>
                <span class="order-time-stamp" *ngIf="order.createdAt">{{ formatDateValue(order.createdAt) }}</span>
              </div>
            </article>

            <!-- Zero State / Empty State (Anti-Slop Craft) -->
            <div class="empty-orders-card" *ngIf="filteredActiveOrders().length === 0">
              <div class="empty-orders-visual" aria-hidden="true">
                <div class="empty-icon-halo">
                  <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
                    <rect x="9" y="3" width="6" height="4" rx="1"/>
                    <path d="m9 14 2 2 4-4"/>
                  </svg>
                </div>
              </div>
              <h4 class="empty-orders-title">Kitchen Counter All Clear</h4>
              <p class="empty-orders-sub">No active tickets waiting in the queue. New orders from POS terminals will stream here in real time.</p>
              <div class="empty-orders-actions">
                <button type="button" class="primary-btn-compact" (click)="navigateToOrders()">View All Orders</button>
                <button type="button" class="ghost-btn-compact" (click)="resetFilters()" *ngIf="selectedChannel() !== 'all' || searchQuery">Clear Filters</button>
              </div>
            </div>
          </div>
        </section>

        <!-- ── Right Column: Metrics & Quick Actions ── -->
        <aside class="metrics-col" aria-label="Analytics and Actions">
          <!-- 1. Hero Card: Today's Sales with Gradient and Delta Pill -->
          <article class="hero-sales-card">
            <div class="sales-header-row">
              <div class="sales-icon-label">
                <div class="sales-icon-box" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="2" y="4" width="20" height="16" rx="3"/>
                    <path d="M16 10a2 2 0 0 1 2 2v0a2 2 0 0 1-2 2H2"/>
                  </svg>
                </div>
                <span class="sales-title">Today's Revenue</span>
              </div>
              <span class="sales-delta-pill" *ngIf="data.deltaToday !== 0" [class.sales-delta-pill--down]="data.deltaToday < 0">
                {{ data.deltaToday > 0 ? '↑' : '↓' }} {{ Math.abs(data.deltaToday) }}% vs yest.
              </span>
              <span class="sales-delta-pill sales-delta-pill--neutral" *ngIf="data.deltaToday === 0">
                Live Sales
              </span>
            </div>
            <h3 class="sales-value-hero">{{ data.todayRevenueFormatted }}</h3>

            <!-- Cash vs Online Split Reconciliation Strip -->
            <div class="sales-split-strip">
              <div class="split-item">
                <span class="split-dot split-dot--cash"></span>
                <span class="split-label">Cash:</span>
                <strong class="split-val">{{ cashDrawerFormatted() }}</strong>
              </div>
              <div class="split-divider">|</div>
              <div class="split-item">
                <span class="split-dot split-dot--upi"></span>
                <span class="split-label">Online / UPI:</span>
                <strong class="split-val">{{ onlineRevenueFormatted() }}</strong>
              </div>
            </div>
          </article>

          <!-- 2. KPI Duo Row: Total Orders & Pending KOT -->
          <div class="kpi-duo-row">
            <!-- Total Orders -->
            <article class="kpi-mini-card">
              <span class="kpi-mini-label">Total Orders</span>
              <h3 class="kpi-mini-value">{{ data.posOrderCount }}</h3>
              <span class="delta-indicator delta-neutral">
                Today's Volume
              </span>
            </article>

            <!-- Pending KOT -->
            <article class="kpi-mini-card">
              <span class="kpi-mini-label">Pending KOT</span>
              <h3 class="kpi-mini-value">{{ pendingKotCount() }}</h3>
              <span class="delta-indicator" [class.delta-red]="pendingKotCount() > 0" [class.delta-green]="pendingKotCount() === 0">
                {{ pendingKotCount() > 0 ? 'Action Required' : 'Cleared' }}
              </span>
            </article>
          </div>

          <!-- 3. Completed Trend Card with Wave Sparkline -->
          <article class="completed-trend-card">
            <div class="completed-card-head">
              <span class="kpi-mini-label">Completed</span>
              <h3 class="kpi-mini-value">{{ completedCount() }}</h3>
              <span class="delta-indicator delta-green">
                {{ completedRate() }}% Settled
              </span>
            </div>
            <div class="completed-chart-wrap" aria-hidden="true">
              <svg viewBox="0 0 320 70" class="completed-svg-wave" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="purpleAreaGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" stop-color="#5D45FD" stop-opacity="0.32"/>
                    <stop offset="100%" stop-color="#5D45FD" stop-opacity="0.0"/>
                  </linearGradient>
                </defs>
                <path
                  d="M 0 50 C 40 50, 60 62, 90 38 C 120 16, 150 48, 190 30 C 230 12, 260 42, 300 18 L 320 22 L 320 70 L 0 70 Z"
                  fill="url(#purpleAreaGrad)"
                />
                <path
                  d="M 0 50 C 40 50, 60 62, 90 38 C 120 16, 150 48, 190 30 C 230 12, 260 42, 300 18 L 320 22"
                  fill="none"
                  stroke="#5D45FD"
                  stroke-width="2.5"
                  stroke-linecap="round"
                />
              </svg>
            </div>
          </article>

          <!-- 4. Quick Action Buttons -->
          <div class="quick-actions-trio" role="group" aria-label="Quick Actions">
            <!-- New Order -->
            <button type="button" class="quick-act-card" (click)="navigateToOrders()">
              <div class="quick-act-icon-box" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="12" y1="18" x2="12" y2="12"/>
                  <line x1="9" y1="15" x2="15" y2="15"/>
                </svg>
              </div>
              <span class="quick-act-label">New Order</span>
            </button>

            <!-- Add Item -->
            <button type="button" class="quick-act-card" (click)="navigateToMenu()">
              <div class="quick-act-icon-box" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="9"/>
                  <line x1="12" y1="8" x2="12" y2="16"/>
                  <line x1="8" y1="12" x2="16" y2="12"/>
                </svg>
              </div>
              <span class="quick-act-label">Add Item</span>
            </button>

            <!-- Terminals -->
            <button type="button" class="quick-act-card" (click)="navigateToTerminals()">
              <div class="quick-act-icon-box" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="4" y="2" width="16" height="20" rx="2" ry="2"/>
                  <line x1="12" y1="18" x2="12.01" y2="18"/>
                </svg>
              </div>
              <span class="quick-act-label">Terminals</span>
            </button>
          </div>
        </aside>
      </div>

      <!-- ── Operational Intelligence Row: Peak Hour Traffic & Top Dishes ── -->
      <section class="operational-intel-grid" aria-label="Operational Analytics">
        <!-- Peak Hour Traffic -->
        <article class="hourly-traffic-card">
          <div class="card-head-row">
            <div class="card-head-title">
              <span class="card-eyebrow">Service Velocity</span>
              <h4 class="card-heading-compact">Peak Hour Traffic</h4>
            </div>
            <span class="rush-summary-pill" *ngIf="peakHourText()">{{ peakHourText() }}</span>
          </div>

          <div class="hourly-bars-container" *ngIf="hasHourlySales(); else emptyHourly">
            <div
              class="hourly-bar-col"
              *ngFor="let bar of displayHourlyBars()"
              [title]="bar.hourLabel + ': ' + bar.itemsSold + ' items sold'">
              <div class="bar-track">
                <div
                  class="bar-fill"
                  [class.bar-peak]="bar.isPeak"
                  [class.bar-current]="bar.isCurrent"
                  [style.height.%]="bar.heightPct">
                </div>
              </div>
              <span class="bar-label">{{ bar.hourLabel }}</span>
            </div>
          </div>
          <ng-template #emptyHourly>
            <div class="empty-chart-box">
              <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-subtle-icon">
                <line x1="18" y1="20" x2="18" y2="10"/>
                <line x1="12" y1="20" x2="12" y2="4"/>
                <line x1="6" y1="20" x2="6" y2="14"/>
              </svg>
              <p>No sales activity recorded yet today.</p>
              <span>Hourly order velocity will chart here as bills are closed.</span>
            </div>
          </ng-template>
        </article>

        <!-- Top Selling Dishes Today -->
        <article class="top-dishes-card">
          <div class="card-head-row">
            <div class="card-head-title">
              <span class="card-eyebrow">Kitchen Velocity</span>
              <h4 class="card-heading-compact">Top Selling Dishes Today</h4>
            </div>
            <button type="button" class="link-btn-subtle" (click)="navigateToMenu()">View Menu</button>
          </div>

          <div class="top-dishes-list" *ngIf="displayTopDishes().length > 0; else emptyDishes">
            <div class="top-dish-row" *ngFor="let dish of displayTopDishes(); let idx = index">
              <div class="dish-rank-badge">{{ idx + 1 }}</div>
              <div class="dish-details">
                <div class="dish-line-top">
                  <strong class="dish-name">{{ dish.name }}</strong>
                  <span class="dish-qty">{{ dish.quantitySold }} sold</span>
                </div>
                <div class="dish-progress-track">
                  <div class="dish-progress-bar" [style.width.%]="dish.volumePct"></div>
                </div>
              </div>
              <strong class="dish-revenue">{{ dish.revenueFormatted }}</strong>
            </div>
          </div>
          <ng-template #emptyDishes>
            <div class="empty-dishes-box">
              <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-subtle-icon">
                <path d="M18 2v6a3 3 0 0 1-3 3 3 3 0 0 1-3-3V2"/>
                <path d="M15 2v19"/>
                <path d="M5 2c1.5 2 1.5 5 0 7v12"/>
              </svg>
              <p>No dish sales recorded yet today.</p>
              <span>Top selling items by volume and revenue will populate as tickets close.</span>
            </div>
          </ng-template>
        </article>
      </section>
    </div>

    <!-- ── Terminal Fleet Status Modal / Popover ── -->
    <div class="pos-modal-overlay" *ngIf="terminalsModalOpen()" (click)="toggleTerminalsModal()">
      <div class="pos-modal-card" (click)="$event.stopPropagation()">
        <div class="modal-head">
          <h3>POS Terminals Fleet</h3>
          <button type="button" class="close-btn" (click)="toggleTerminalsModal()">✕</button>
        </div>
        <div class="modal-body">
          <div class="terminal-row-item" *ngFor="let t of terminals()">
            <div class="terminal-main-info">
              <span class="pulse-dot" [class.pulse-dot--online]="t.isActive || t.status === 'active' || t.status === 'ACTIVE'"></span>
              <div>
                <strong>{{ t.terminalName || 'Terminal ' + (t.terminalSeries || t.id) }}</strong>
                <span class="terminal-series-tag">Series: {{ t.terminalSeries || 'POS' }}</span>
              </div>
            </div>
            <div class="terminal-status-meta">
              <span class="chip-status" [class.chip-status--active]="t.isActive || t.status === 'active' || t.status === 'ACTIVE'">
                {{ (t.isActive || t.status === 'active' || t.status === 'ACTIVE') ? 'Active' : 'Offline' }}
              </span>
              <span class="last-seen-text">Updated: {{ formatDateValue(t.updatedAt) }}</span>
            </div>
          </div>
          <div class="empty-terminals-note" *ngIf="terminals().length === 0">
            <p>No POS terminals registered yet.</p>
          </div>
        </div>
        <div class="modal-foot">
          <button type="button" class="primary-btn-compact" (click)="navigateToTerminals()">Manage Hardware</button>
        </div>
      </div>
    </div>

    <!-- ── Low Stock Alert Modal ── -->
    <div class="pos-modal-overlay" *ngIf="lowStockModalOpen()" (click)="toggleLowStockModal()">
      <div class="pos-modal-card" (click)="$event.stopPropagation()">
        <div class="modal-head">
          <div class="modal-head-title">
            <span class="alert-icon">⚠️</span>
            <h3>Low Stock Ingredients</h3>
          </div>
          <button type="button" class="close-btn" (click)="toggleLowStockModal()">✕</button>
        </div>
        <div class="modal-body">
          <p class="modal-lead">The following ingredients are below their minimum threshold. Restock promptly to prevent 86ing dishes.</p>
          <div class="low-stock-table-wrap">
            <table class="low-stock-table">
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
                  <td class="text-danger font-bold">{{ m.stockQuantity }}</td>
                  <td>{{ m.lowStockThreshold }}</td>
                  <td>{{ m.unit || 'units' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="modal-foot">
          <button type="button" class="primary-btn-compact" (click)="navigateToInventory()">Go to Inventory</button>
        </div>
      </div>
    </div>

    <!-- Loading / Skeleton State -->
    <ng-template #loading>
      <div class="pos-page-shell" *ngIf="dashboardError(); else posSkeleton">
        <div class="panel loading">
          <p>{{ dashboardError() }}</p>
          <button class="primary-btn" (click)="refresh()">Retry</button>
        </div>
      </div>
      <ng-template #posSkeleton>
        <div class="pos-page-shell">
          <div class="skeleton" style="height: 48px; border-radius: 999px; margin-bottom: 1.5rem;"></div>
          <div class="pos-dashboard-grid">
            <div class="skeleton" style="height: 400px; border-radius: 18px;"></div>
            <div class="skeleton" style="height: 400px; border-radius: 18px;"></div>
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
      background: var(--pos-bg-body, #F6F8FD);
      min-height: 100vh;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .pos-page-shell {
      padding: 1.5rem 2rem;
      max-width: 1440px;
      margin: 0 auto;
    }

    /* ── Top Utility Header Bar ── */
    .pos-topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.25rem;
      margin-bottom: 1.75rem;
    }

    .channel-dropdown-wrapper {
      position: relative;
    }

    .channel-selector-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.65rem;
      padding: 0.65rem 1.15rem;
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 9999px;
      font-weight: 600;
      font-size: 0.92rem;
      color: #1F2937;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
      transition: all 150ms ease;
    }
    .channel-selector-btn:hover {
      border-color: #5D45FD;
    }
    .chevron-icon {
      color: #6B7280;
      transition: transform 150ms ease;
    }

    .channel-menu {
      position: absolute;
      top: calc(100% + 6px);
      left: 0;
      z-index: 50;
      min-width: 160px;
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 14px;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1);
      padding: 6px;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .channel-opt {
      padding: 8px 14px;
      border-radius: 8px;
      border: none;
      background: transparent;
      text-align: left;
      font-size: 0.88rem;
      font-weight: 500;
      color: #374151;
      cursor: pointer;
      transition: background 120ms ease;
    }
    .channel-opt:hover {
      background: #F3F0FF;
      color: #5D45FD;
    }

    .pos-search-pill {
      flex: 1;
      max-width: 380px;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 9999px;
      padding: 0.65rem 1.25rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    .pos-search-pill svg {
      color: #9CA3AF;
      flex-shrink: 0;
    }
    .pos-search-pill input {
      border: none;
      outline: none;
      background: transparent;
      width: 100%;
      font-size: 0.9rem;
      color: #1F2937;
    }
    .pos-search-pill input::placeholder {
      color: #9CA3AF;
    }

    .topbar-actions {
      display: flex;
      align-items: center;
      gap: 0.85rem;
    }

    /* Terminal Pulse Pill */
    .terminal-pulse-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.85rem;
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 9999px;
      font-size: 0.82rem;
      font-weight: 600;
      color: #374151;
      cursor: pointer;
      transition: all 150ms ease;
    }
    .terminal-pulse-pill:hover {
      border-color: #10B981;
      background: #F0FDF4;
    }
    .pulse-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #9CA3AF;
    }
    .pulse-dot--online {
      background: #10B981;
      box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.25);
    }

    /* Low Stock Warning Pill */
    .low-stock-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.5rem 0.85rem;
      background: #FEF3C7;
      border: 1px solid #FDE68A;
      border-radius: 9999px;
      font-size: 0.82rem;
      font-weight: 600;
      color: #92400E;
      cursor: pointer;
      transition: all 150ms ease;
    }
    .low-stock-pill:hover {
      background: #FDE68A;
    }

    /* Notification Wrap & Flyout */
    .notification-wrap {
      position: relative;
    }
    .icon-btn-round {
      position: relative;
      width: 42px;
      height: 42px;
      border-radius: 50%;
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #4B5563;
      cursor: pointer;
      transition: all 150ms ease;
    }
    .icon-btn-round:hover {
      background: #F9FAFB;
      color: #5D45FD;
    }
    .notification-badge {
      position: absolute;
      top: -2px;
      right: -2px;
      background: #EF4444;
      color: #FFFFFF;
      font-size: 0.65rem;
      font-weight: 700;
      min-width: 17px;
      height: 17px;
      border-radius: 999px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0 4px;
      box-shadow: 0 2px 4px rgba(239, 68, 68, 0.4);
    }

    .notification-flyout {
      position: absolute;
      top: calc(100% + 10px);
      right: 0;
      width: 320px;
      max-height: 400px;
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 16px;
      box-shadow: 0 14px 34px rgba(0, 0, 0, 0.12);
      z-index: 100;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .flyout-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.85rem 1rem;
      border-bottom: 1px solid #F3F4F6;
    }
    .flyout-header h4 {
      margin: 0;
      font-size: 0.92rem;
      font-weight: 700;
      color: #111827;
    }
    .link-btn {
      background: none;
      border: none;
      color: #5D45FD;
      font-size: 0.78rem;
      font-weight: 600;
      cursor: pointer;
    }
    .flyout-body {
      overflow-y: auto;
      max-height: 320px;
    }
    .notif-row {
      display: flex;
      align-items: flex-start;
      gap: 0.65rem;
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #F9FAFB;
      cursor: pointer;
      transition: background 120ms ease;
    }
    .notif-row:hover {
      background: #F9FAFB;
    }
    .notif-unread {
      background: #F5F3FF;
    }
    .notif-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #5D45FD;
      margin-top: 5px;
      flex-shrink: 0;
    }
    .notif-content {
      flex: 1;
    }
    .notif-title {
      display: block;
      font-size: 0.85rem;
      color: #1F2937;
    }
    .notif-msg {
      margin: 0.15rem 0 0.35rem;
      font-size: 0.78rem;
      color: #6B7280;
      line-height: 1.35;
    }
    .notif-time {
      font-size: 0.72rem;
      color: #9CA3AF;
    }
    .flyout-empty {
      padding: 2rem 1rem;
      text-align: center;
      color: #6B7280;
      font-size: 0.85rem;
    }

    .user-avatar-circle {
      width: 42px;
      height: 42px;
      border-radius: 50%;
      background: #5D45FD;
      color: #FFFFFF;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 1.05rem;
      border: 2px solid #FFFFFF;
      box-shadow: 0 2px 8px rgba(93, 69, 253, 0.25);
      cursor: pointer;
    }

    /* ── 2-Column POS Dashboard Grid ── */
    .pos-dashboard-grid {
      display: grid;
      grid-template-columns: minmax(0, 1.45fr) minmax(0, 1.05fr);
      gap: 1.75rem;
      align-items: start;
    }

    @media (max-width: 1080px) {
      .pos-dashboard-grid {
        grid-template-columns: 1fr;
      }
    }

    /* ── Left Column: Active Orders ── */
    .section-header-flex {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1rem;
    }
    .section-title-pos {
      font-size: 1.25rem;
      font-weight: 700;
      color: #1E293B;
      margin: 0;
    }
    .live-indicator-badge {
      font-size: 0.75rem;
      font-weight: 600;
      color: #10B981;
      background: #ECFDF5;
      padding: 0.25rem 0.65rem;
      border-radius: 9999px;
    }

    .active-orders-list {
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
    }

    .pos-order-card {
      display: flex;
      align-items: center;
      gap: 1.15rem;
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.1rem 1.35rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      transition: all 160ms ease;
      cursor: pointer;
    }
    .pos-order-card:active {
      transform: scale(0.985);
    }

    .order-avatar {
      width: 48px;
      height: 48px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      background: #F1F5F9;
      color: #475569;
      border: 1px solid #E2E8F0;
      transition: all 180ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .order-avatar--preparing {
      background: #FEF2F2;
      color: #DC2626;
      border-color: #FECACA;
    }
    .order-avatar--cooking {
      background: #FFFBEB;
      color: #D97706;
      border-color: #FDE68A;
    }
    .order-avatar--served {
      background: #F5F3FF;
      color: #7C3AED;
      border-color: #DDD6FE;
    }
    .order-avatar--completed {
      background: #ECFDF5;
      color: #059669;
      border-color: #A7F3D0;
    }

    .order-info-center {
      flex: 1;
      min-width: 0;
    }
    .order-id-status-line {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      margin-bottom: 0.25rem;
    }
    .order-code-bold {
      font-size: 1.05rem;
      font-weight: 700;
      color: #0F172A;
    }

    .status-chip {
      font-size: 0.74rem;
      font-weight: 700;
      text-transform: capitalize;
      padding: 0.22rem 0.65rem;
      border-radius: 9999px;
      letter-spacing: 0.02em;
    }
    .badge-preparing {
      background: #FFEBEB;
      color: #E53935;
    }
    .badge-cooking {
      background: #FFF3E0;
      color: #FB8C00;
    }
    .badge-served {
      background: #EDE7F6;
      color: #7E57C2;
    }
    .badge-completed {
      background: #E8F5E9;
      color: #43A047;
    }

    .order-items-snippet {
      margin: 0 0 0.45rem;
      font-size: 0.88rem;
      color: #6B7280;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .order-quick-actions {
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }
    .btn-micro {
      background: #F9FAFB;
      border: 1px solid #E5E7EB;
      border-radius: 6px;
      padding: 0.2rem 0.5rem;
      font-size: 0.74rem;
      font-weight: 600;
      color: #4B5563;
      cursor: pointer;
      transition: all 120ms ease;
    }
    .btn-micro:hover {
      background: #F3F0FF;
      color: #5D45FD;
      border-color: #DDD6FE;
    }

    .order-meta-col {
      text-align: right;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 0.35rem;
    }
    .order-table-type {
      font-size: 0.8rem;
      font-weight: 500;
      color: #9CA3AF;
    }
    .order-amount-large {
      font-size: 1.15rem;
      font-weight: 700;
      color: #111827;
      font-variant-numeric: tabular-nums;
    }

    .order-time-stamp {
      font-size: 0.72rem;
      font-weight: 500;
      color: #94A3B8;
      font-variant-numeric: tabular-nums;
    }

    .empty-orders-card {
      background: #FFFFFF;
      border: 1.5px dashed #E2E8F0;
      border-radius: 20px;
      padding: 3rem 1.75rem;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
    }
    .empty-orders-visual {
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 0.25rem;
    }
    .empty-icon-halo {
      width: 58px;
      height: 58px;
      border-radius: 18px;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      color: #94A3B8;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
    }
    .empty-orders-title {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 700;
      color: #1E293B;
    }
    .empty-orders-sub {
      margin: 0;
      font-size: 0.86rem;
      color: #64748B;
      max-width: 340px;
      line-height: 1.45;
    }
    .ghost-btn-compact {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      margin-top: 0.5rem;
      padding: 0.5rem 0.95rem;
      border-radius: 10px;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      color: #475569;
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .ghost-btn-compact:hover {
      background: #F1F5F9;
      color: #1E293B;
      border-color: #CBD5E1;
    }
    .ghost-btn-compact:active {
      transform: scale(0.97);
    }

    /* ── Right Column: Analytics & Command Center ── */
    .metrics-col {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    /* 1. Hero Card: Today's Sales */
    .hero-sales-card {
      background: linear-gradient(135deg, #5D45FD 0%, #462BDC 100%);
      border-radius: 20px;
      padding: 1.4rem 1.6rem;
      color: #FFFFFF;
      box-shadow: 0 10px 28px rgba(93, 69, 253, 0.28);
      position: relative;
      overflow: hidden;
    }
    .sales-header-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 0.65rem;
    }
    .sales-icon-label {
      display: flex;
      align-items: center;
      gap: 0.65rem;
    }
    .sales-icon-box {
      width: 34px;
      height: 34px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.16);
      display: flex;
      align-items: center;
      justify-content: center;
      backdrop-filter: blur(4px);
    }
    .sales-title {
      font-size: 0.92rem;
      font-weight: 600;
      color: rgba(255, 255, 255, 0.9);
      letter-spacing: 0.01em;
    }
    .sales-delta-pill {
      background: rgba(255, 255, 255, 0.2);
      color: #4ADE80;
      padding: 0.25rem 0.65rem;
      border-radius: 9999px;
      font-size: 0.78rem;
      font-weight: 700;
      letter-spacing: 0.02em;
    }
    .sales-value-hero {
      font-size: calc(2rem + 0.4vw);
      font-weight: 800;
      margin: 0.35rem 0 0.85rem;
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
    }

    .sales-split-strip {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      background: rgba(0, 0, 0, 0.14);
      padding: 0.55rem 0.85rem;
      border-radius: 10px;
      font-size: 0.78rem;
    }
    .split-item {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .split-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }
    .split-dot--cash {
      background: #FCD34D;
    }
    .split-dot--upi {
      background: #6EE7B7;
    }
    .split-label {
      color: rgba(255, 255, 255, 0.75);
    }
    .split-val {
      color: #FFFFFF;
      font-weight: 700;
    }
    .split-divider {
      color: rgba(255, 255, 255, 0.3);
    }

    /* 2. KPI Duo Row */
    .kpi-duo-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }
    .kpi-mini-card {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.15rem 1.25rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .kpi-mini-label {
      font-size: 0.82rem;
      font-weight: 600;
      color: #8F95B2;
    }
    .kpi-mini-value {
      font-size: 1.6rem;
      font-weight: 800;
      color: #111827;
      margin: 0;
      font-variant-numeric: tabular-nums;
    }
    .delta-indicator {
      font-size: 0.76rem;
      font-weight: 700;
    }
    .delta-green {
      color: #10B981;
    }
    .delta-red {
      color: #EF4444;
    }
    .delta-amber {
      color: #D97706;
    }

    /* 3. Completed Trend Card */
    .completed-trend-card {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.15rem 1.25rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      overflow: hidden;
    }
    .completed-card-head {
      display: flex;
      align-items: baseline;
      gap: 0.75rem;
      margin-bottom: 0.35rem;
    }
    .completed-chart-wrap {
      margin-top: 0.25rem;
      width: 100%;
      height: 50px;
    }
    .completed-svg-wave {
      width: 100%;
      height: 100%;
      display: block;
    }

    /* 4. Hourly Traffic Heatmap Card */
    .hourly-traffic-card {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.2rem 1.35rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    .card-head-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1rem;
    }
    .card-eyebrow {
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #8F95B2;
      display: block;
    }
    .card-heading-compact {
      font-size: 0.98rem;
      font-weight: 700;
      color: #1F2937;
      margin: 0.15rem 0 0;
    }
    .rush-summary-pill {
      font-size: 0.72rem;
      font-weight: 700;
      color: #5D45FD;
      background: #F3F0FF;
      padding: 0.2rem 0.55rem;
      border-radius: 9999px;
    }

    .hourly-bars-container {
      display: flex;
      align-items: flex-end;
      gap: 6px;
      height: 100px;
      padding-top: 10px;
    }
    .hourly-bar-col {
      flex: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
      gap: 6px;
      cursor: pointer;
    }
    .bar-track {
      width: 100%;
      height: 72px;
      background: #F3F4F6;
      border-radius: 6px;
      display: flex;
      align-items: flex-end;
      overflow: hidden;
    }
    .bar-fill {
      width: 100%;
      background: #C7D2FE;
      border-radius: 6px;
      transition: height 250ms ease, background 150ms ease;
    }
    .bar-fill.bar-peak {
      background: #5D45FD;
    }
    .bar-fill.bar-current {
      background: #FF7A18;
    }
    .hourly-bar-col:hover .bar-fill {
      filter: brightness(0.92);
    }
    .bar-label {
      font-size: 0.65rem;
      font-weight: 600;
      color: #9CA3AF;
    }

    .empty-chart-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 1.75rem 1rem;
      text-align: center;
      gap: 0.65rem;
    }
    .empty-chart-box p {
      margin: 0;
      font-size: 0.82rem;
      font-weight: 500;
      color: #94A3B8;
    }
    .empty-subtle-icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: #F8FAFC;
      border: 1px solid #F1F5F9;
      color: #CBD5E1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* 5. Top Dishes Card */
    .top-dishes-card {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.2rem 1.35rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    .link-btn-subtle {
      background: none;
      border: none;
      color: #5D45FD;
      font-size: 0.78rem;
      font-weight: 600;
      cursor: pointer;
    }
    .top-dishes-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      margin-top: 0.5rem;
    }
    .top-dish-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .dish-rank-badge {
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: #F3F0FF;
      color: #5D45FD;
      font-size: 0.75rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .dish-details {
      flex: 1;
      min-width: 0;
    }
    .dish-line-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 0.25rem;
    }
    .dish-name {
      font-size: 0.85rem;
      color: #1F2937;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .dish-qty {
      font-size: 0.76rem;
      font-weight: 600;
      color: #6B7280;
    }
    .dish-progress-track {
      width: 100%;
      height: 5px;
      background: #F3F4F6;
      border-radius: 999px;
      overflow: hidden;
    }
    .dish-progress-bar {
      height: 100%;
      background: linear-gradient(90deg, #5D45FD 0%, #A78BFA 100%);
      border-radius: 999px;
      transition: width 300ms ease;
    }
    .dish-revenue {
      font-size: 0.85rem;
      font-weight: 700;
      color: #111827;
      font-variant-numeric: tabular-nums;
      flex-shrink: 0;
    }

    .empty-dishes-box {
      padding: 1.5rem 1rem;
      text-align: center;
    }
    .empty-dishes-box p {
      margin: 0;
      font-size: 0.82rem;
      font-weight: 500;
      color: #94A3B8;
      line-height: 1.45;
    }

    /* 6. Quick Action Trio */
    .quick-actions-trio {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.85rem;
    }
    .quick-act-card {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 16px;
      padding: 1.15rem 0.5rem;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.65rem;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      transition: all 140ms ease;
    }
    .quick-act-card:hover {
      border-color: #5D45FD;
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(93, 69, 253, 0.08);
    }
    .quick-act-icon-box {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: #F5F3FF;
      color: #5D45FD;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 140ms ease, color 140ms ease;
    }
    .quick-act-card:hover .quick-act-icon-box {
      background: #5D45FD;
      color: #FFFFFF;
    }
    .quick-act-label {
      font-size: 0.82rem;
      font-weight: 700;
      color: #374151;
    }

    /* ── Operational Intelligence Row ── */
    .operational-intel-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.75rem;
      margin-top: 2rem;
    }
    @media (max-width: 1080px) {
      .operational-intel-grid {
        grid-template-columns: 1fr;
      }
    }

    /* ── Modals / Overlays ── */
    .pos-modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(17, 24, 39, 0.45);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 200;
      padding: 1rem;
      backdrop-filter: blur(3px);
    }
    .pos-modal-card {
      background: #FFFFFF;
      border-radius: 20px;
      width: 100%;
      max-width: 480px;
      box-shadow: 0 20px 45px rgba(0, 0, 0, 0.18);
      overflow: hidden;
      animation: modalSlide 160ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes modalSlide {
      from { opacity: 0; transform: translateY(12px) scale(0.98); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }
    .modal-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1.15rem 1.4rem;
      border-bottom: 1px solid #F3F4F6;
    }
    .modal-head h3 {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 700;
      color: #111827;
    }
    .modal-head-title {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .close-btn {
      background: none;
      border: none;
      font-size: 1.1rem;
      color: #9CA3AF;
      cursor: pointer;
    }
    .close-btn:hover {
      color: #111827;
    }
    .modal-body {
      padding: 1.25rem 1.4rem;
      max-height: 60vh;
      overflow-y: auto;
    }
    .modal-lead {
      font-size: 0.84rem;
      color: #6B7280;
      margin: 0 0 1rem;
      line-height: 1.45;
    }
    .modal-foot {
      padding: 1rem 1.4rem;
      border-top: 1px solid #F3F4F6;
      display: flex;
      justify-content: flex-end;
      background: #F9FAFB;
    }
    .primary-btn-compact {
      background: #5D45FD;
      color: #FFFFFF;
      border: none;
      border-radius: 10px;
      padding: 0.55rem 1.15rem;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      transition: background 140ms ease;
    }
    .primary-btn-compact:hover {
      background: #4D37E6;
    }

    /* Terminal Row Item in Modal */
    .terminal-row-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.75rem 0;
      border-bottom: 1px solid #F3F4F6;
    }
    .terminal-row-item:last-child {
      border-bottom: none;
    }
    .terminal-main-info {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .terminal-series-tag {
      display: block;
      font-size: 0.75rem;
      color: #6B7280;
    }
    .terminal-status-meta {
      text-align: right;
    }
    .chip-status {
      display: inline-block;
      padding: 0.15rem 0.5rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 600;
      background: #F3F4F6;
      color: #6B7280;
    }
    .chip-status--active {
      background: #ECFDF5;
      color: #10B981;
    }
    .last-seen-text {
      display: block;
      font-size: 0.7rem;
      color: #9CA3AF;
      margin-top: 0.15rem;
    }

    /* Low Stock Table */
    .low-stock-table-wrap {
      border: 1px solid #EEF0F7;
      border-radius: 12px;
      overflow: hidden;
    }
    .low-stock-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.84rem;
    }
    .low-stock-table th {
      text-align: left;
      padding: 0.6rem 0.85rem;
      background: #F9FAFB;
      color: #6B7280;
      font-weight: 600;
      border-bottom: 1px solid #EEF0F7;
    }
    .low-stock-table td {
      padding: 0.65rem 0.85rem;
      border-bottom: 1px solid #F3F4F6;
    }
    .text-danger {
      color: #DC2626;
    }
    .font-bold {
      font-weight: 700;
    }

    .empty-terminals-note {
      font-size: 0.84rem;
      color: #94A3B8;
      font-style: italic;
      padding: 1.25rem 0;
      text-align: center;
    }

    /* Tactile Micro-Interactions (Emil Kowalski Craft) */
    .channel-selector-btn:active,
    .terminal-pulse-pill:active,
    .low-stock-pill:active,
    .quick-act-card:active,
    .primary-btn-compact:active {
      transform: scale(0.97);
    }
    .btn-micro:active {
      transform: scale(0.94);
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
  readonly selectedChannel = signal<'all' | 'dine_in' | 'takeaway' | 'delivery'>('all');
  readonly channelDropdownOpen = signal(false);
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
                .slice(0, 12)
                .map((o: any) => {
                  let status: PosActiveOrder['status'] = 'Preparing';
                  const st = (o.orderStatus || '').toUpperCase();
                  if (st === 'COMPLETED' || st === 'PAID') status = 'Completed';
                  else if (st === 'SERVED') status = 'Served';
                  else if (st === 'COOKING') status = 'Cooking';

                  return {
                    orderId: o.orderId,
                    orderCode: o.orderCode || String(o.orderId),
                    status,
                    sourceType: o.sourceType || 'DINE_IN',
                    dishImg: undefined,
                    itemsSummary: o.customerName ? `Diner: ${o.customerName}` : 'Dine In Order',
                    tableAndChannel: `${o.sourceType || 'Dine In'} · ${o.paymentMethod || 'Cash'}`,
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
      case 'delivery': return 'Delivery';
      default: return 'All Channels';
    }
  });

  // Computed: Filtered Active Orders by Channel & Search Query
  readonly filteredActiveOrders = computed(() => {
    const channel = this.selectedChannel();
    const query = (this.searchQuery || '').trim().toLowerCase();
    const orders = this.activeOrders();

    return orders.filter(order => {
      if (channel !== 'all') {
        const chanText = (order.tableAndChannel || '').toLowerCase();
        if (channel === 'dine_in' && !chanText.includes('dine in')) return false;
        if (channel === 'takeaway' && !chanText.includes('takeaway')) return false;
        if (channel === 'delivery' && !chanText.includes('delivery')) return false;
      }

      if (!query) return true;
      return (order.orderCode || '').toLowerCase().includes(query)
        || (order.itemsSummary || '').toLowerCase().includes(query)
        || (order.tableAndChannel || '').toLowerCase().includes(query)
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
  readonly pendingKotCount = computed(() => {
    const data = this.dashboard();
    return Number(data?.pendingPosPayments) || 0;
  });

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

  // Dropdowns & Toggles
  toggleChannelDropdown(): void {
    this.channelDropdownOpen.update(v => !v);
  }

  setChannel(channel: 'all' | 'dine_in' | 'takeaway' | 'delivery'): void {
    this.selectedChannel.set(channel);
    this.channelDropdownOpen.set(false);
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
    const link = `https://kbook.iadv.cloud/api/v1/public/invoice/${restaurantId}/${order.orderId}/preview`;
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

  navigateToKot(): void {
    void this.router.navigate(['/business/active-orders']);
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
