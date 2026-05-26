import { AfterViewInit, Component, DestroyRef, effect, ElementRef, inject, OnDestroy, signal, ViewChild, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { MatFormFieldModule } from '@angular/material/form-field';
import { combineLatest, of, interval, Subject } from 'rxjs';
import { catchError, map, startWith, switchMap } from 'rxjs/operators';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMarketplaceSetup, BusinessProfile, MarketplaceOrder } from '../../core/models/api.models';
import { formatCurrency } from '../../shared/formatters';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-business-dashboard-page',
  standalone: true,
  encapsulation: ViewEncapsulation.None,
  imports: [
    CommonModule, 
    FormsModule, 
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatDividerModule,
    MatChipsModule,
    MatTooltipModule,
    MatMenuModule,
    MatFormFieldModule
  ],
  template: `
    <div class="page-container">
      <ng-container *ngIf="vm() as data; else loading">
        <div class="header-row">

          <!-- Compliance expiry banner -->
          <div class="compliance-banner expired" *ngIf="fssaiExpired()">
            <mat-icon>error</mat-icon>
            FSSAI License has EXPIRED — renew immediately to stay compliant.
            <a routerLink="/business/settings">Go to Settings →</a>
          </div>
          <div class="compliance-banner expiring" *ngIf="!fssaiExpired() && fssaiExpiringSoon()">
            <mat-icon>warning</mat-icon>
            FSSAI License expiring soon.
            <a routerLink="/business/settings">Go to Settings →</a>
          </div>
          <div class="compliance-banner expired" *ngIf="!fssaiExpired() && gstExpired()">
            <mat-icon>error</mat-icon>
            GST Registration has EXPIRED — renew immediately to stay compliant.
            <a routerLink="/business/settings">Go to Settings →</a>
          </div>
          <div class="compliance-banner expiring" *ngIf="!fssaiExpired() && !gstExpired() && gstExpiringSoon()">
            <mat-icon>warning</mat-icon>
            GST Registration expiring soon.
            <a routerLink="/business/settings">Go to Settings →</a>
          </div>

          <div class="header-left">
            <div class="live-status">
              <span class="live-dot"></span>
              <span class="live-text">Live Console</span>
            </div>
            <h1 class="page-title">{{ data.shopName || 'Business Dashboard' }}</h1>
            <p class="page-subtitle">{{ liveDate() }}</p>
          </div>
          <div class="header-actions">
            <mat-form-field appearance="outline" class="poll-field">
              <mat-label>Auto-refresh</mat-label>
              <mat-select [value]="pollIntervalMs()" (selectionChange)="setPollInterval($event.value)">
                <mat-option [value]="0">Off</mat-option>
                <mat-option [value]="15000">15s</mat-option>
                <mat-option [value]="30000">30s</mat-option>
                <mat-option [value]="60000">60s</mat-option>
              </mat-select>
            </mat-form-field>
            
            <button mat-icon-button (click)="refresh()" matTooltip="Refresh now">
              <mat-icon [class.spinning]="refreshing">refresh</mat-icon>
            </button>
          </div>
        </div>

        <div class="stats-grid">
          <mat-card class="stat-card revenue clickable" routerLink="/business/orders">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">payments</mat-icon>
              <mat-card-title>{{ data.todayRevenueFormatted }}</mat-card-title>
              <mat-card-subtitle>Today's Revenue</mat-card-subtitle>
              <div class="trend-badge" [class.up]="data.revenueTrend > 0" [class.down]="data.revenueTrend < 0">
                <mat-icon>{{ data.revenueTrend > 0 ? 'trending_up' : (data.revenueTrend < 0 ? 'trending_down' : 'trending_flat') }}</mat-icon>
                {{ Math.abs(data.revenueTrend) }}%
              </div>
            </mat-card-header>
          </mat-card>

          <mat-card class="stat-card orders clickable" routerLink="/business/orders">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">shopping_bag</mat-icon>
              <mat-card-title>{{ data.totalOrders }}</mat-card-title>
              <mat-card-subtitle>Total Orders</mat-card-subtitle>
              <div class="trend-badge" [class.up]="data.orderTrend > 0" [class.down]="data.orderTrend < 0">
                <mat-icon>{{ data.orderTrend > 0 ? 'trending_up' : (data.orderTrend < 0 ? 'trending_down' : 'trending_flat') }}</mat-icon>
                {{ Math.abs(data.orderTrend) }}%
              </div>
            </mat-card-header>
          </mat-card>

          <mat-card class="stat-card avg-value clickable" routerLink="/business/orders">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">analytics</mat-icon>
              <mat-card-title>{{ data.avgOrderValueFormatted }}</mat-card-title>
              <mat-card-subtitle>Avg Order Value</mat-card-subtitle>
              <div class="trend-badge up" *ngIf="data.totalOrders > 0">
                <mat-icon>analytics</mat-icon>
                {{ data.totalOrders }} orders
              </div>
            </mat-card-header>
          </mat-card>

          <mat-card class="stat-card online clickable" routerLink="/business/orders">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">language</mat-icon>
              <mat-card-title>{{ data.marketplaceOrders.total }}</mat-card-title>
              <mat-card-subtitle>Online Orders</mat-card-subtitle>
              <div class="online-status" *ngIf="data.marketplaceOrders.pending > 0">
                {{ data.marketplaceOrders.pending }} pending
              </div>
            </mat-card-header>
          </mat-card>
        </div>

        <div class="main-grid">
          <div class="grid-left">
            <mat-card class="chart-card">
              <mat-card-header>
                <mat-card-title>Weekly Revenue Trend</mat-card-title>
                <mat-card-subtitle>Last 7 days performance</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <div class="chart-container">
                  <canvas #salesChart></canvas>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card class="recent-orders-card">
              <mat-card-header>
                <mat-card-title>Recent Transactions</mat-card-title>
                <span class="spacer"></span>
                <a mat-button color="primary" routerLink="/business/orders">
                  All Orders <mat-icon>chevron_right</mat-icon>
                </a>
              </mat-card-header>
              <mat-card-content>
                <div class="order-list" *ngIf="data.recentOrders.length > 0; else noOrders">
                  <div class="order-item" *ngFor="let order of data.recentOrders.slice(0, 8)"
                       [class.paid-row]="order.paymentStatus === 'Paid'"
                       [class.pending-row]="order.paymentStatus !== 'Paid'"
                       [class.refunded-row]="order.refundStatus === 'REFUNDED'">
                    <div class="order-info">
                      <div class="order-code">{{ order.orderCode }}</div>
                      <div class="order-customer">{{ order.customerName || 'Walk-in Customer' }}</div>
                    </div>
                    <div class="order-status">
                      <span class="status-chip" [class.success]="order.paymentStatus === 'Paid'">
                        {{ order.orderStatus }}
                      </span>
                    </div>
                    <div class="order-amount">{{ formatCurrencyValue(order.totalAmount) }}</div>
                  </div>
                </div>
                <ng-template #noOrders>
                  <div class="empty-state">
                    <mat-icon>description</mat-icon>
                    <p>No transactions found for today.</p>
                  </div>
                </ng-template>
              </mat-card-content>
            </mat-card>
          </div>

          <div class="grid-right">
            <mat-card class="chart-card doughnut-card">
              <mat-card-header>
                <mat-card-title>Order Summary</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="doughnut-container">
                  <canvas #orderChart></canvas>
                  <div class="chart-empty" *ngIf="orderChartData().values.length === 0">
                    <p>No data</p>
                  </div>
                </div>
                <div class="legend" *ngIf="orderChartData().values.length > 0">
                  <div class="legend-item" *ngFor="let label of orderChartData().labels; let i = index">
                    <span class="dot" [style.background-color]="orderChartData().colors[i]"></span>
                    <span class="label">{{ label }}</span>
                    <span class="value">{{ orderChartData().values[i] }}</span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card class="setup-card">
              <mat-card-header>
                <mat-card-title>Onboarding Progress</mat-card-title>
                <div class="progress-percent">{{ data.setupPct }}%</div>
              </mat-card-header>
              <mat-card-content>
                <mat-progress-bar mode="determinate" [value]="data.setupPct" color="primary"></mat-progress-bar>
                <div class="checklist">
                  <div class="check-item" *ngFor="let item of data.setupChecks" [class.done]="item.ready">
                    <mat-icon>{{ item.ready ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon>
                    <div class="check-info">
                      <div class="check-label">{{ item.label }}</div>
                      <div class="check-detail">{{ item.detail }}</div>
                    </div>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card class="alert-card low-stock" *ngIf="data.lowStockItems.length > 0">
              <mat-card-header>
                <mat-icon mat-card-avatar color="warn">warning</mat-icon>
                <mat-card-title>Inventory Alert</mat-card-title>
                <mat-card-subtitle>{{ data.lowStockItems.length }} items are low or out of stock</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <div class="stock-list">
                  <div class="stock-item" *ngFor="let item of data.lowStockItems.slice(0, 5)">
                    <span>{{ item.name }}</span>
                    <span class="stock-status" [class.critical]="item.stockStatus === 'OUT_OF_STOCK'">{{ stockLabel(item.stockStatus) }}</span>
                  </div>
                </div>
              </mat-card-content>
              <mat-card-actions align="end">
                <a mat-button color="primary" routerLink="/business/menu">Manage Inventory</a>
              </mat-card-actions>
            </mat-card>
          </div>
        </div>

        <div class="quick-actions-bar">
          <mat-card class="action-card clickable" routerLink="/business/orders">
            <mat-icon>receipt</mat-icon>
            <div class="action-label">Orders</div>
          </mat-card>
          <mat-card class="action-card clickable" routerLink="/business/menu">
            <mat-icon>restaurant_menu</mat-icon>
            <div class="action-label">Menu</div>
          </mat-card>
          <mat-card class="action-card clickable" routerLink="/business/marketplace-setup">
            <mat-icon>store</mat-icon>
            <div class="action-label">Marketplace</div>
          </mat-card>
          <mat-card class="action-card clickable" routerLink="/business/staff">
            <mat-icon>people</mat-icon>
            <div class="action-label">Staff</div>
          </mat-card>
          <mat-card class="action-card clickable" routerLink="/business/settings">
            <mat-icon>settings</mat-icon>
            <div class="action-label">Settings</div>
          </mat-card>
        </div>
      </ng-container>

      <ng-template #loading>
        <div class="skeleton-container animate-fade-in-up">
          <div class="header-row" style="margin-bottom: 28px;">
            <div class="header-left">
              <div class="skeleton-cell shimmer-bg" style="width: 250px; height: 32px; margin-bottom: 8px;"></div>
              <div class="skeleton-cell shimmer-bg" style="width: 150px; height: 16px;"></div>
            </div>
          </div>
          
          <div class="stats-grid">
            <div class="skeleton-card shimmer-bg" style="height: 110px;" *ngFor="let i of [1,2,3,4]"></div>
          </div>

          <div class="main-grid">
            <div class="grid-left">
              <div class="skeleton-card shimmer-bg" style="height: 380px;"></div>
              <div class="skeleton-card shimmer-bg" style="height: 250px;"></div>
            </div>
            <div class="grid-right">
              <div class="skeleton-card shimmer-bg" style="height: 300px;"></div>
              <div class="skeleton-card shimmer-bg" style="height: 330px;"></div>
            </div>
          </div>
        </div>
      </ng-template>

    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; position: relative; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 28px; }
    .page-title { margin: 0; font-size: 2rem; font-weight: 800; color: var(--ink); letter-spacing: -0.5px; }
    .page-subtitle { margin: 4px 0 0; color: var(--muted); font-size: 0.95rem; }
    .header-actions { display: flex; align-items: center; gap: 16px; }
    
    .live-status { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
    .live-dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; animation: pulse 2s infinite; }
    .live-text { font-size: 0.75rem; font-weight: 800; color: #10b981; text-transform: uppercase; letter-spacing: 1px; }
    @keyframes pulse { 0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); } 70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); } 100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); } }

    .poll-field { width: 150px; }
    .poll-field .mat-mdc-form-field-subscript-wrapper { display: none; }
    .poll-field .mat-mdc-text-field-wrapper { height: 40px !important; padding: 0 12px !important; border-radius: var(--radius-md) !important; }

    .stats-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 32px; }
    @media (max-width: 1024px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 600px)  { .stats-grid { grid-template-columns: 1fr; } }
    .stat-card { 
      position: relative;
      min-width: 0;
      border-radius: var(--radius-xl) !important; 
      border: 1px solid var(--line) !important; 
      background: rgba(255, 255, 255, 0.7) !important;
      backdrop-filter: blur(16px) saturate(120%) !important;
      -webkit-backdrop-filter: blur(16px) saturate(120%) !important;
      box-shadow: var(--shadow-md) !important; 
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1) !important;
      overflow: hidden;
    }
    .stat-card .mat-mdc-card-header {
      min-width: 0;
      padding: 14px 16px;
      gap: 10px;
    }
    .stat-card .mat-mdc-card-header-text {
      min-width: 0;
      overflow: hidden;
    }
    :host-context(.dark-theme) .stat-card {
      background: rgba(33, 26, 20, 0.65) !important;
      border: 1px solid rgba(247, 243, 238, 0.05) !important;
    }
    .stat-card:hover {
      transform: translateY(-6px);
      box-shadow: var(--shadow-xl);
    }
    .stat-icon { 
      background: var(--brand-soft); 
      color: var(--brand); 
      width: 44px; 
      height: 44px; 
      line-height: 44px; 
      text-align: center; 
      border-radius: var(--radius-lg); 
      font-size: 22px; 
      transition: all 0.3s ease;
      flex-shrink: 0;
    }
    .stat-card:hover .stat-icon {
      transform: scale(1.1) rotate(6deg);
    }
    .stat-card .mat-mdc-card-title {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    
    /* Premium Themed Cards */
    .revenue { 
      border-color: rgba(22, 163, 74, 0.12) !important; 
      background: linear-gradient(135deg, rgba(22, 163, 74, 0.04) 0%, var(--panel) 100%);
    }
    .revenue .stat-icon { background: rgba(34, 197, 94, 0.12); color: #16a34a; }
    .revenue:hover { border-color: rgba(34, 197, 94, 0.3) !important; box-shadow: 0 12px 28px -8px rgba(34, 197, 94, 0.2) !important; }
    
    .orders { 
      border-color: rgba(217, 119, 6, 0.12) !important;
      background: linear-gradient(135deg, rgba(217, 119, 6, 0.04) 0%, var(--panel) 100%);
    }
    .orders .stat-icon { background: rgba(245, 158, 11, 0.12); color: #d97706; }
    .orders:hover { border-color: rgba(245, 158, 11, 0.3) !important; box-shadow: 0 12px 28px -8px rgba(245, 158, 11, 0.2) !important; }

    .avg-value { 
      border-color: rgba(2, 132, 199, 0.12) !important;
      background: linear-gradient(135deg, rgba(2, 132, 199, 0.04) 0%, var(--panel) 100%);
    }
    .avg-value .stat-icon { background: rgba(14, 165, 233, 0.12); color: #0284c7; }
    .avg-value:hover { border-color: rgba(14, 165, 233, 0.3) !important; box-shadow: 0 12px 28px -8px rgba(14, 165, 233, 0.2) !important; }

    .online { 
      border-color: rgba(147, 51, 234, 0.12) !important;
      background: linear-gradient(135deg, rgba(147, 51, 234, 0.04) 0%, var(--panel) 100%);
    }
    .online .stat-icon { background: rgba(168, 85, 247, 0.12); color: #9333ea; }
    .online:hover { border-color: rgba(168, 85, 247, 0.3) !important; box-shadow: 0 12px 28px -8px rgba(168, 85, 247, 0.2) !important; }

    .trend-badge { position: absolute; top: 16px; right: 16px; display: flex; align-items: center; gap: 2px; font-size: 0.75rem; font-weight: 700; padding: 3px 10px; border-radius: 999px; }
    .trend-badge.up { background: rgba(34, 197, 94, 0.12); color: #16a34a; }
    .trend-badge.down { background: rgba(239, 68, 68, 0.12); color: #dc2626; }
    .trend-badge mat-icon { font-size: 14px; width: 14px; height: 14px; }
    
    .online-status { position: absolute; top: 16px; right: 16px; font-size: 0.7rem; font-weight: 700; color: #dc2626; background: rgba(239, 68, 68, 0.12); padding: 3px 10px; border-radius: 999px; }

    .main-grid { display: grid; grid-template-columns: 1.6fr 1fr; gap: 32px; margin-bottom: 32px; }
    .grid-left, .grid-right { display: flex; flex-direction: column; gap: 32px; }

    .chart-card { border-radius: var(--radius-xl); border: 1px solid var(--line); box-shadow: var(--shadow-md); background: var(--panel); }
    .chart-container { height: 280px; position: relative; }
    .doughnut-container { height: 180px; position: relative; display: flex; justify-content: center; align-items: center; }
    .chart-empty { position: absolute; font-size: 0.8rem; color: var(--muted); }

    .legend { display: flex; justify-content: center; gap: 16px; margin-top: 16px; flex-wrap: wrap; }
    .legend-item { display: flex; align-items: center; gap: 6px; font-size: 0.85rem; color: var(--ink-secondary); }
    .dot { width: 10px; height: 10px; border-radius: 50%; }
    .value { font-weight: 700; margin-left: 2px; color: var(--ink); }

    .recent-orders-card { border-radius: var(--radius-xl); border: 1px solid var(--line); box-shadow: var(--shadow-md); background: var(--panel); flex: 1; }
    .order-list { display: flex; flex-direction: column; gap: 8px; }
    .order-item { 
      display: flex; 
      align-items: center; 
      padding: 12px 16px; 
      border-radius: var(--radius-md); 
      border: 1px solid var(--line); 
      background: var(--bg); 
      transition: all 0.2s ease;
    }
    .order-item:hover { 
      transform: translateX(4px); 
      border-color: var(--brand-soft); 
      background: var(--panel-hover); 
    }
    .order-item.paid-row { border-left: 4px solid #16a34a !important; }
    .order-item.pending-row { border-left: 4px solid #d97706 !important; }
    .order-item.refunded-row { border-left: 4px solid #dc2626 !important; }

    .status-indicator { width: 4px; height: 32px; border-radius: 2px; background: #e2e8f0; margin-right: 12px; display: none; } /* Replaced with border-left */
    .order-info { flex: 1; }
    .order-code { font-weight: 700; color: var(--ink); font-size: 0.95rem; }
    .order-customer { font-size: 0.8rem; color: var(--muted); margin-top: 2px; }
    .order-status { margin: 0 16px; }
    .order-amount { font-weight: 700; color: var(--ink); font-size: 0.95rem; }

    .status-chip { padding: 4px 10px; border-radius: 999px; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; background: #f1f5f9; color: #64748b; letter-spacing: 0.5px; }
    .status-chip.success { background: rgba(34, 197, 94, 0.12); color: #16a34a; }

    .setup-card { border-radius: var(--radius-xl); border: 1px solid var(--line); box-shadow: var(--shadow-md); background: var(--panel); }
    .setup-card .mat-mdc-card-header { display: flex; justify-content: space-between; align-items: center; width: 100%; }
    .setup-card .mat-mdc-card-header-text { margin: 0; }
    .progress-percent { font-size: 1.4rem; font-weight: 800; color: var(--brand); margin-left: 16px; }
    .setup-card .mat-mdc-progress-bar { height: 8px !important; border-radius: 4px !important; overflow: hidden !important; }
    .checklist { margin-top: 20px; display: flex; flex-direction: column; gap: 12px; }
    .check-item { 
      display: flex; 
      align-items: flex-start; 
      gap: 12px; 
      opacity: 0.55; 
      padding: 10px 12px; 
      border-radius: var(--radius-md); 
      border: 1px solid transparent; 
      transition: all 0.2s ease;
    }
    .check-item:hover { background: var(--bg); }
    .check-item.done { opacity: 1; color: var(--ink); border-color: rgba(34, 197, 94, 0.1); background: rgba(34, 197, 94, 0.02); }
    .check-item.done mat-icon { color: #16a34a; }
    .check-info { flex: 1; }
    .check-label { font-weight: 700; font-size: 0.9rem; }
    .check-detail { font-size: 0.8rem; color: var(--muted); margin-top: 2px; }

    .alert-card.low-stock { 
      border-radius: var(--radius-xl); 
      border: 1px solid rgba(239, 68, 68, 0.2); 
      background: linear-gradient(135deg, rgba(239, 68, 68, 0.04) 0%, var(--panel) 100%);
      box-shadow: var(--shadow-md); 
    }
    .stock-list { display: flex; flex-direction: column; gap: 8px; }
    .stock-item { 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
      padding: 10px 14px; 
      background: var(--bg); 
      border: 1px solid var(--line);
      border-radius: var(--radius-md); 
      font-size: 0.85rem; 
      transition: all 0.2s ease;
    }
    .stock-item:hover {
      transform: scale(1.02);
      border-color: rgba(239, 68, 68, 0.3);
    }
    .stock-status { font-weight: 800; font-size: 0.7rem; color: #dc2626; letter-spacing: 0.5px; }
    .stock-status.critical { color: #be123c; text-decoration: none; font-weight: 900; }

    .quick-actions-bar { display: grid; grid-template-columns: repeat(5, 1fr); gap: 20px; }
    .action-card { 
      display: flex; 
      flex-direction: column; 
      align-items: center; 
      justify-content: center; 
      padding: 24px; 
      gap: 12px; 
      cursor: pointer; 
      border-radius: var(--radius-xl) !important;
      border: 1px solid var(--line) !important; 
      background: rgba(255, 255, 255, 0.7) !important;
      backdrop-filter: blur(16px) saturate(120%) !important;
      -webkit-backdrop-filter: blur(16px) saturate(120%) !important;
      box-shadow: var(--shadow-md) !important; 
      transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) !important; 
      color: var(--ink);
    }
    :host-context(.dark-theme) .action-card {
      background: rgba(33, 26, 20, 0.65) !important;
      border: 1px solid rgba(247, 243, 238, 0.05) !important;
    }
    .action-card:hover { 
      transform: translateY(-8px) scale(1.04) !important; 
      box-shadow: 0 16px 32px rgba(199, 115, 47, 0.25) !important; 
      background: linear-gradient(135deg, var(--brand) 0%, var(--brand-dark) 100%) !important; 
      color: #fff !important; 
      border-color: var(--brand-light) !important;
    }
    .action-card mat-icon { font-size: 32px; width: 32px; height: 32px; color: var(--brand); transition: all 0.3s ease; }
    .action-card:hover mat-icon { transform: scale(1.15); color: #fff !important; }
    .action-label { font-weight: 700; font-size: 0.95rem; }

    .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 100px; color: var(--muted); }
    
    /* Skeleton Loading Effects */
    .skeleton-container { width: 100%; }
    .skeleton-card { background: var(--line) !important; border-radius: var(--radius-xl) !important; position: relative; overflow: hidden; border: 1px solid var(--line); }
    .skeleton-cell { background: var(--line-strong) !important; border-radius: 4px; }

    .shimmer-bg { position: relative; overflow: hidden; background: var(--line) !important; }
    .shimmer-bg::after {
      content: ''; position: absolute; inset: 0;
      background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.12) 50%, transparent 100%);
      animation: shimmer 1.5s infinite;
    }
    @keyframes shimmer { from { transform: translateX(-100%); } to { transform: translateX(100%); } }

    .empty-state { padding: 40px; text-align: center; color: var(--muted); }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 12px; opacity: 0.3; }



    /* Compliance banner */
    .compliance-banner { display: flex; align-items: center; gap: 10px; padding: 12px 20px; margin-bottom: 16px; border-radius: var(--radius-lg); font-size: 0.9rem; font-weight: 700; }
    .compliance-banner.expired { background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.3); color: #dc2626; }
    .compliance-banner.expiring { background: rgba(245, 158, 11, 0.1); border: 1px solid rgba(245, 158, 11, 0.3); color: #d97706; }
    .compliance-banner a { color: inherit; font-weight: 700; text-decoration: underline; margin-left: auto; }

    .spinning { animation: rotate 1s linear infinite; }
    @keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
    .spacer { flex: 1; }

    @media (max-width: 1024px) {
      .main-grid { grid-template-columns: 1fr; }
      .quick-actions-bar { grid-template-columns: repeat(3, 1fr); }
    }
    @media (max-width: 768px) {
      .header-row { flex-direction: column; gap: 16px; }
      .quick-actions-bar { grid-template-columns: repeat(2, 1fr); }
    }
  `]
})
export class BusinessDashboardPageComponent implements AfterViewInit, OnDestroy {
  @ViewChild('salesChart') salesChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('orderChart') orderChartRef!: ElementRef<HTMLCanvasElement>;

  private readonly api = inject(BusinessApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  readonly Math = Math;

  readonly profileFssaiExpiry = signal<string | null>(null);
  readonly profileGstExpiry = signal<string | null>(null);

  readonly fssaiExpired = () => this.isExpired(this.profileFssaiExpiry());
  readonly fssaiExpiringSoon = () => this.isExpiringSoon(this.profileFssaiExpiry());
  readonly gstExpired = () => this.isExpired(this.profileGstExpiry());
  readonly gstExpiringSoon = () => this.isExpiringSoon(this.profileGstExpiry());

  private isExpiringSoon(dateStr: string | null): boolean {
    if (!dateStr) return false;
    const expiry = new Date(dateStr);
    const today = new Date();
    expiry.setHours(0,0,0,0);
    today.setHours(0,0,0,0);
    const diffDays = Math.round((expiry.getTime() - today.getTime()) / 86400000);
    return diffDays > 0 && diffDays <= 30;
  }

  private isExpired(dateStr: string | null): boolean {
    if (!dateStr) return false;
    const expiry = new Date(dateStr);
    const today = new Date();
    expiry.setHours(0,0,0,0);
    today.setHours(0,0,0,0);
    return expiry.getTime() < today.getTime();
  }

  stockLabel(status: string): string {
    switch (status) {
      case 'OUT_OF_STOCK': return 'Out of stock';
      case 'UNAVAILABLE': return 'Unavailable';
      case 'LOW_STOCK': return 'Low stock';
      default: return status.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    }
  }

  readonly liveDate = signal(this.formatDate(new Date()));
  private dateInterval: ReturnType<typeof setInterval> | null = null;

  private readonly refresh$ = new Subject<void>();
  refreshing = false;

  readonly pollIntervalMs = signal<number>(
    (() => {
      if (typeof localStorage === 'undefined') return 30000;
      const stored = localStorage.getItem('kbook-biz-poll');
      const parsed = stored ? parseInt(stored, 10) : 30000;
      return [0, 15000, 30000, 60000].includes(parsed) ? parsed : 30000;
    })()
  );

  private chartInstance: Chart | null = null;
  private orderChartInstance: Chart | null = null;
  readonly salesLabels = signal<string[]>([]);
  readonly salesData = signal<number[]>([]);
  readonly orderChartData = signal<{ labels: string[]; values: number[]; colors: string[] }>({ labels: [], values: [], colors: [] });
  readonly lowStockItems = signal<{ name: string; stockStatus: string }[]>([]);



  constructor() {
    this.dateInterval = setInterval(() => {
      this.liveDate.set(this.formatDate(new Date()));
    }, 3600000); // update once per hour — date text doesn't change mid-day



    this.api.getMenu().pipe(
      takeUntilDestroyed(this.destroyRef),
      catchError(() => of([]))
    ).subscribe(items => {
      const low = items
        .filter(i => i.stockStatus !== 'IN_STOCK' || !i.available)
        .map(i => ({ name: i.name, stockStatus: !i.available ? 'UNAVAILABLE' : i.stockStatus }))
        .slice(0, 8);
      this.lowStockItems.set(low);
    });

    effect(() => {
      const labels = this.salesLabels();
      const data = this.salesData();
      if (labels.length > 0 && data.length > 0) {
        setTimeout(() => this.renderSalesChart(), 0);
      }
    });

    effect(() => {
      const chartData = this.orderChartData();
      if (chartData.values.length > 0) {
        setTimeout(() => this.renderOrderChart(), 0);
      }
    });
  }

  private formatDate(d: Date): string {
    return d.toLocaleDateString('en-IN', {
      weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });
  }

  ngOnDestroy(): void {
    if (this.dateInterval) clearInterval(this.dateInterval);
    if (this.chartInstance) this.chartInstance.destroy();
    if (this.orderChartInstance) this.orderChartInstance.destroy();

  }

  ngAfterViewInit(): void {
    if (this.salesLabels().length > 0) {
      this.renderSalesChart();
    }
    if (this.orderChartData().values.length > 0) {
      this.renderOrderChart();
    }
  }



  readonly vm = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => {
        const timer$ = this.pollIntervalMs() > 0 ? interval(this.pollIntervalMs()) : of(0);
        return timer$.pipe(
          startWith(0),
          switchMap(() => combineLatest([
            this.api.getDashboard().pipe(catchError(() => of(null))),
            this.api.getMarketplaceSetup().pipe(catchError(() => of(null as BusinessMarketplaceSetup | null))),
            this.api.getMarketplaceOrders().pipe(catchError(() => of([] as MarketplaceOrder[]))),
            this.api.getProfile().pipe(catchError(() => of(null as BusinessProfile | null)))
          ]))
        );
      })
    ).pipe(
      map(([data, setup, orders, profile]) => {
        this.refreshing = false;
        if (!data) return null;

        const today = new Date();
        const labels: string[] = [];
        const values: number[] = [];
        let yesterdayTotal = 0;
        let todayTotal = 0;

        for (let i = 6; i >= 0; i--) {
          const d = new Date(today);
          d.setDate(d.getDate() - i);
          labels.push(d.toLocaleDateString('en-IN', { weekday: 'short' }));
          const val = (i === 0) ? Math.round(data.todayRevenue) : 0;
          values.push(val);
          if (i === 1) yesterdayTotal = val;
          if (i === 0) todayTotal = val;
        }

        this.salesLabels.set(labels);
        this.salesData.set(values);

        const totalOrders = data.posOrderCount + orders.length;
        const paidOrders = totalOrders > 0 ? data.recentOrders.filter(o => o.paymentStatus === 'Paid').length : 0;
        const pendingOrders = totalOrders > 0 ? data.recentOrders.filter(o => o.paymentStatus !== 'Paid').length : 0;
        const refundedCount = totalOrders > 0 ? data.recentOrders.filter(o => o.refundStatus === 'REFUNDED' || (o.refundAmount && o.refundAmount > 0)).length : 0;
        
        if (totalOrders > 0 && data.recentOrders.length > 0) {
          this.orderChartData.set({
            labels: ['Paid', 'Pending', 'Refunded'],
            values: [paidOrders, pendingOrders, refundedCount],
            colors: ['#16a34a', '#d97706', '#dc2626']
          });
        } else {
          this.orderChartData.set({ labels: [], values: [], colors: [] });
        }

        const revenueTrend = yesterdayTotal > 0 ? Math.round(((todayTotal - yesterdayTotal) / yesterdayTotal) * 100) : 0;
        const totalMarketplaceOrders = orders.length;
        const pendingMarketplace = orders.filter(o => o.orderStatus === 'pending').length;
        const readyMarketplace = orders.filter(o => o.orderStatus === 'ready').length;
        const orderTrend = 0; // TODO: calculate from historical data when API provides daily order counts

        // Compliance state from profile
        if (profile) {
          this.profileFssaiExpiry.set(profile.fssaiExpiryDate ?? null);
          this.profileGstExpiry.set(profile.gstExpiryDate ?? null);
        }

        return {
          ...data,
          totalRevenueFormatted: formatCurrency(data.totalRevenue),
          todayRevenueFormatted: formatCurrency(data.todayRevenue),
          refundedAmountFormatted: formatCurrency(data.refundedAmount),
          avgOrderValueFormatted: totalOrders > 0 ? formatCurrency(data.totalRevenue / totalOrders) : formatCurrency(0),
          totalOrders,
          paidOrders,
          pendingOrders,
          refundedCount,
          revenueTrend,
          orderTrend,
          subMerchantStatus: setup?.subMerchantStatus || 'NOT_STARTED',
          marketplaceOrders: {
            total: totalMarketplaceOrders,
            pending: pendingMarketplace,
            ready: readyMarketplace
          },
          lowStockItems: this.lowStockItems(),
          setupChecks: [
            { label: 'Marketplace Integration', ready: !!(setup?.subMerchantId || setup?.subMerchantStatus !== 'NOT_STARTED'), detail: 'Configure in marketplace setup' },
            { label: 'Payment Gateway', ready: setup?.subMerchantStatus === 'ACTIVE', detail: 'Easebuzz ready for settlements' },
            { label: 'Staff & Menu', ready: data.totalStaff > 0 && data.totalMenuItems > 0, detail: `${data.totalStaff} staff, ${data.totalMenuItems} items` }
          ],
          setupPct: (() => {
            const checks = [
              { label: 'Marketplace Integration', ready: !!(setup?.subMerchantId || setup?.subMerchantStatus !== 'NOT_STARTED') },
              { label: 'Payment Gateway', ready: setup?.subMerchantStatus === 'ACTIVE' },
              { label: 'Staff & Menu', ready: data.totalStaff > 0 && data.totalMenuItems > 0 }
            ];
            const done = checks.filter(c => c.ready).length;
            return Math.round((done / checks.length) * 100);
          })()
        };
      })
    )
  );

  refresh(): void {
    this.refreshing = true;
    this.refresh$.next();
    // spinner clears on next data emission via vm pipeline
  }

  setPollInterval(val: number): void {
    this.pollIntervalMs.set(val);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('kbook-biz-poll', String(val));
    }
    this.refresh$.next();
  }

  setupPercent(data: any): number {
    const total = data.setupChecks?.length || 1;
    const done = data.setupChecks?.filter((c: any) => c.ready).length || 0;
    return Math.round((done / total) * 100);
  }

  formatCurrencyValue(v: number): string {
    return formatCurrency(v);
  }

  private renderSalesChart(): void {
    const canvasEl = this.salesChartRef?.nativeElement;
    if (!canvasEl) return;
    if (this.chartInstance) this.chartInstance.destroy();

    const ctx = canvasEl.getContext('2d');
    if (!ctx) return;

    this.chartInstance = new Chart(ctx, {
      type: 'line',
      data: {
        labels: this.salesLabels(),
        datasets: [{
          label: 'Revenue',
          data: this.salesData(),
          borderColor: '#b56a2d',
          backgroundColor: 'rgba(181,106,45,0.08)',
          fill: true,
          tension: 0.4,
          pointRadius: 4,
          pointBackgroundColor: '#b56a2d',
          borderWidth: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { display: true, grid: { display: false }, ticks: { font: { size: 10 }, color: '#64748b' } },
          y: { display: true, grid: { color: '#f1f5f9' }, ticks: { font: { size: 10 }, color: '#64748b' } }
        },
        interaction: { intersect: false, mode: 'index' }
      }
    });
  }

  private renderOrderChart(): void {
    const canvasEl = this.orderChartRef?.nativeElement;
    if (!canvasEl) return;
    if (this.orderChartInstance) this.orderChartInstance.destroy();

    const ctx = canvasEl.getContext('2d');
    if (!ctx) return;

    const data = this.orderChartData();

    this.orderChartInstance = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: data.labels,
        datasets: [{
          data: data.values,
          backgroundColor: data.colors,
          borderColor: 'transparent',
          hoverOffset: 4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '70%',
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => `${ctx.label}: ${ctx.parsed}`
            }
          }
        }
      }
    });
  }
}
