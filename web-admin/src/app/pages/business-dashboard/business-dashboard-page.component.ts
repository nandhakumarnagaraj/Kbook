import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { combineLatest, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMarketplaceSetup, MarketplaceOrder } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-business-dashboard-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <div class="dash-header">
        <div>
          <h2>{{ (vm()?.shopName) || 'Dashboard' }}</h2>
        </div>
        <div class="header-badges" *ngIf="vm() as data">
          <span class="badge" [class.green]="data.subMerchantStatus === 'ACTIVE'" [class.amber]="data.subMerchantStatus !== 'ACTIVE'">
            {{ data.subMerchantStatus === 'ACTIVE' ? 'Easebuzz Active' : 'Easebuzz: ' + (data.subMerchantStatus || 'Not Setup') }}
          </span>
        </div>
      </div>

      <div *ngIf="vm() as data; else loading">
        <div class="metric-grid">
          <div class="metric-card gradient-gold">
            <div class="metric-icon">💰</div>
            <div class="metric-body">
              <span class="metric-label">Total Revenue</span>
              <span class="metric-value">{{ data.totalRevenueFormatted }}</span>
              <span class="metric-sub">All time</span>
            </div>
          </div>
          <div class="metric-card gradient-green">
            <div class="metric-icon">📈</div>
            <div class="metric-body">
              <span class="metric-label">Today's Revenue</span>
              <span class="metric-value">{{ data.todayRevenueFormatted }}</span>
              <span class="metric-sub">{{ data.posOrderCount }} POS orders</span>
            </div>
          </div>
          <div class="metric-card gradient-blue">
            <div class="metric-icon">🛵</div>
            <div class="metric-body">
              <span class="metric-label">Online Orders</span>
              <span class="metric-value">{{ data.marketplaceOrders.total }}</span>
              <span class="metric-sub" *ngIf="data.marketplaceOrders.pending > 0; else noPending">
                {{ data.marketplaceOrders.pending }} pending action
              </span>
              <ng-template #noPending><span class="metric-sub">All processed</span></ng-template>
            </div>
          </div>
          <div class="metric-card gradient-purple">
            <div class="metric-icon">🔄</div>
            <div class="metric-body">
              <span class="metric-label">Refunds</span>
              <span class="metric-value">{{ data.refundedOrders }}</span>
              <span class="metric-sub">{{ data.refundedAmountFormatted }}</span>
            </div>
          </div>
        </div>

        <div class="alert-banner" *ngIf="data.marketplaceOrders.pending > 0">
          <div class="alert-icon">🛵</div>
          <div class="alert-body">
            <strong>{{ data.marketplaceOrders.pending }} Online Order{{ data.marketplaceOrders.pending > 1 ? 's' : '' }} Pending</strong>
            <span>Swiggy/Zomato {{ data.marketplaceOrders.pending > 1 ? 'orders are' : 'order is' }} waiting for action</span>
          </div>
          <a class="alert-btn" href="/business/orders">View & Accept →</a>
        </div>

        <div class="action-strip">
          <a class="action-chip primary" href="/business/orders">📋 View Orders</a>
          <a class="action-chip" href="/business/marketplace-setup">🔗 Marketplace</a>
          <a class="action-chip" href="/business/menu">🍽️ Menu</a>
          <a class="action-chip" href="/business/settings">⚙️ Settings</a>
        </div>

        <div class="dual-grid">
          <div class="card">
            <div class="card-head">
              <h3>Setup Checklist</h3>
              <span class="progress-text">{{ completedChecks(data) }}/{{ data.setupChecks.length }}</span>
            </div>
            <div class="progress-track">
              <div class="progress-fill" [style.width.%]="completedChecks(data) / data.setupChecks.length * 100"></div>
            </div>
            <div class="checklist" *ngIf="data.setupChecks.length > 0">
              <div class="check-row" *ngFor="let item of data.setupChecks">
                <span class="check-icon">{{ item.ready ? '✓' : '○' }}</span>
                <div class="check-info">
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.detail }}</span>
                </div>
                <span class="status-chip" [class.done]="item.ready" [class.pending]="!item.ready">
                  {{ item.ready ? 'Done' : 'Pending' }}
                </span>
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-head">
              <h3>Recent Orders</h3>
              <span class="muted" *ngIf="data.recentOrders.length > 0">{{ data.recentOrders.length }} latest</span>
            </div>

            <ng-container *ngIf="data.recentOrders.length > 0; else noOrders">
              <div class="order-list">
                <div class="order-row" *ngFor="let order of data.recentOrders">
                  <div class="order-left">
                    <span class="order-id">{{ order.orderCode }}</span>
                    <span class="order-cust">{{ order.customerName || 'Walk-in' }}</span>
                  </div>
                  <div class="order-right">
                    <span class="order-status" [class.paid]="order.paymentStatus === 'Paid'" [class.pending-status]="order.paymentStatus === 'Pending'">
                      {{ order.orderStatus }}
                    </span>
                    <span class="order-amount">{{ formatCurrencyValue(order.totalAmount) }}</span>
                  </div>
                </div>
              </div>
            </ng-container>
            <ng-template #noOrders>
              <div class="empty-small">No recent orders</div>
            </ng-template>
          </div>
        </div>
      </div>

      <ng-template #loading>
        <div class="load-state">
          <div class="spinner"></div>
          <p>Loading dashboard...</p>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .dash-header { display:flex; justify-content:space-between; align-items:flex-start; gap:1rem; flex-wrap:wrap; margin-bottom:0; }
    .dash-header h2 { margin:0 0 .25rem; font-size:clamp(1.4rem,2.2vw,1.8rem); }
    .dash-header p { margin:0; }
    .header-badges { display:flex; gap:.5rem; flex-wrap:wrap; }
    .badge { padding:.3rem .8rem; border-radius:999px; font-size:.76rem; font-weight:700; }
    .badge.green { background:rgba(29,123,95,.12); color:var(--accent); }
    .badge.amber { background:rgba(230,126,34,.12); color:#b56a2d; }

    .metric-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:1rem; margin-top:0; }
    .metric-card { display:flex; align-items:center; gap:1rem; padding:1.2rem; border-radius:16px; color:#fff; }
    .gradient-gold { background:linear-gradient(135deg,#b56a2d,#7e4417); }
    .gradient-green { background:linear-gradient(135deg,#1d7b5f,#0d4a38); }
    .gradient-blue { background:linear-gradient(135deg,#4a90d9,#2d6cb5); }
    .gradient-purple { background:linear-gradient(135deg,#8b5cf6,#6d3fd1); }
    .metric-icon { font-size:1.8rem; line-height:1; }
    .metric-body { display:flex; flex-direction:column; }
    .metric-label { font-size:.75rem; opacity:.85; text-transform:uppercase; letter-spacing:.04em; }
    .metric-value { font-size:1.6rem; font-weight:800; line-height:1.2; }
    .metric-sub { font-size:.75rem; opacity:.75; }

    .action-strip { display:flex; gap:.5rem; margin-top:1rem; flex-wrap:wrap; }
    .action-chip { padding:.5rem 1.1rem; border-radius:999px; background:var(--panel); border:1px solid var(--line); font-size:.85rem; font-weight:600; text-decoration:none; color:var(--ink); transition:all .15s; }
    .action-chip:hover { background:rgba(181,106,45,.08); border-color:var(--brand); }
    .action-chip.primary { background:var(--brand); color:#fff; border-color:var(--brand); }
    .action-chip.primary:hover { background:var(--brand-deep); }

    .dual-grid { display:grid; grid-template-columns:1fr 1fr; gap:1rem; margin-top:1rem; }
    @media(max-width:800px){ .dual-grid { grid-template-columns:1fr; } }
    .card { background:var(--panel); border:1px solid var(--line); border-radius:16px; padding:1.25rem; box-shadow:var(--shadow-soft); }
    .card-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:.6rem; }
    .card-head h3 { margin:0; font-size:1rem; }
    .progress-text { font-size:.82rem; font-weight:700; color:var(--brand); }
    .progress-track { height:6px; border-radius:999px; background:#f0e8dc; overflow:hidden; margin-bottom:1rem; }
    .progress-fill { height:100%; border-radius:999px; background:linear-gradient(90deg,var(--brand),var(--accent)); transition:width .5s ease; }

    .checklist { display:flex; flex-direction:column; gap:.5rem; }
    .check-row { display:flex; align-items:center; gap:.75rem; padding:.6rem .75rem; border-radius:10px; background:var(--bg); border:1px solid var(--line); }
    .check-icon { font-size:1rem; font-weight:700; width:20px; text-align:center; color:#1d7b5f; }
    .check-info { flex:1; min-width:0; }
    .check-info strong { display:block; font-size:.85rem; }
    .check-info span { display:block; font-size:.75rem; color:var(--muted); white-space:normal; }
    .status-chip { font-size:.72rem; font-weight:700; padding:.25rem .6rem; border-radius:999px; white-space:nowrap; }
    .status-chip.done { background:rgba(29,123,95,.12); color:var(--accent); }
    .status-chip.pending { background:rgba(230,126,34,.12); color:#b56a2d; }

    .order-list { display:flex; flex-direction:column; gap:.4rem; }
    .order-row { display:flex; justify-content:space-between; align-items:center; padding:.7rem .8rem; border-radius:10px; background:var(--bg); border:1px solid var(--line); transition:background .15s; }
    .order-row:hover { background:rgba(181,106,45,.04); }
    .order-left { display:flex; flex-direction:column; gap:.1rem; }
    .order-id { font-weight:700; font-size:.88rem; }
    .order-cust { font-size:.78rem; color:var(--muted); }
    .order-right { display:flex; align-items:center; gap:.75rem; }
    .order-amount { font-weight:700; font-size:.92rem; }
    .order-status { font-size:.72rem; padding:.2rem .55rem; border-radius:999px; background:#f4ece1; }
    .order-status.paid { background:rgba(29,123,95,.12); color:var(--accent); }
    .order-status.pending-status { background:rgba(230,126,34,.12); color:#b56a2d; }
    .empty-small { text-align:center; padding:2rem; color:var(--muted); font-size:.9rem; }

    .load-state { text-align:center; padding:3rem; color:var(--muted); }
    .spinner { width:24px; height:24px; border:3px solid var(--line); border-top-color:var(--brand); border-radius:50%; animation:spin .7s linear infinite; margin:0 auto .75rem; }
    @keyframes spin { to { transform:rotate(360deg); } }
    .alert-banner { display:flex; align-items:center; gap:1rem; padding:.9rem 1.1rem; border-radius:12px; background:rgba(230,126,34,.1); border:1px solid rgba(230,126,34,.25); margin-top:1rem; }
    .alert-icon { font-size:1.5rem; line-height:1; }
    .alert-body { flex:1; }
    .alert-body strong { display:block; font-size:.9rem; }
    .alert-body span { font-size:.82rem; color:var(--muted); }
    .alert-btn { background:#e67e22; color:#fff; padding:.45rem 1rem; border-radius:999px; text-decoration:none; font-weight:700; font-size:.82rem; white-space:nowrap; transition:background .15s; }
    .alert-btn:hover { background:#c96a15; }
  `]
})
export class BusinessDashboardPageComponent {
  private readonly api = inject(BusinessApiService);

  readonly vm = toSignal(
    combineLatest([
      this.api.getDashboard(),
      this.api.getMarketplaceSetup().pipe(catchError(() => of(null as BusinessMarketplaceSetup | null))),
      this.api.getMarketplaceOrders().pipe(catchError(() => of([] as MarketplaceOrder[])))
    ]).pipe(
      map(([data, setup, orders]) => ({
        ...data,
        totalRevenueFormatted: formatCurrency(data.totalRevenue),
        todayRevenueFormatted: formatCurrency(data.todayRevenue),
        refundedAmountFormatted: formatCurrency(data.refundedAmount),
        subMerchantStatus: setup?.subMerchantStatus || 'NOT_STARTED',
        marketplaceOrders: {
          total: orders.length,
          pending: orders.filter(o => o.orderStatus === 'pending').length,
          ready: orders.filter(o => o.orderStatus === 'ready').length,
          completed: orders.filter(o => o.orderStatus === 'completed').length
        },
        setupChecks: [
          { label: 'Marketplace', ready: false, detail: 'Configure in Marketplace Setup' },
          { label: 'Easebuzz', ready: setup?.subMerchantStatus === 'ACTIVE', detail: 'Payment gateway' },
          { label: 'Staff & Menu', ready: data.totalStaff > 0 && data.totalMenuItems > 0, detail: `${data.totalStaff} staff, ${data.totalMenuItems} items` }
        ]
      }))
    )
  );

  completedChecks(data: any): number {
    return data.setupChecks?.filter((c: any) => c.ready).length || 0;
  }

  formatCurrencyValue(v: number): string { return formatCurrency(v); }
}
