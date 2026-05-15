import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { formatCurrency } from '../../shared/formatters';

@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <div class="dash-header">
        <div>
          <h2>Platform Dashboard</h2>
          <p class="muted">Full overview of your platform — businesses, payments, and operations</p>
        </div>
        <div class="header-badges">
          <span class="badge-gold">KBOOK_ADMIN</span>
          <span class="badge-green">All Systems Normal</span>
        </div>
      </div>

      <div *ngIf="summary() as data; else loading">
        <div class="metric-grid">
          <div class="metric-card gradient-gold">
            <div class="metric-icon">🏪</div>
            <div class="metric-body">
              <span class="metric-label">Total Businesses</span>
              <span class="metric-value">{{ data.totalBusinesses }}</span>
              <span class="metric-sub">{{ data.liveBusinesses }} active</span>
            </div>
          </div>
          <div class="metric-card gradient-green">
            <div class="metric-icon">💰</div>
            <div class="metric-body">
              <span class="metric-label">Total Revenue</span>
              <span class="metric-value">{{ data.totalRevenueFormatted }}</span>
              <span class="metric-sub">All time across platform</span>
            </div>
          </div>
          <div class="metric-card gradient-blue">
            <div class="metric-icon">📦</div>
            <div class="metric-body">
              <span class="metric-label">Total Orders</span>
              <span class="metric-value">{{ data.totalOrders }}</span>
              <span class="metric-sub">{{ data.refundedOrders }} refunded</span>
            </div>
          </div>
          <div class="metric-card gradient-purple">
            <div class="metric-icon">👥</div>
            <div class="metric-body">
              <span class="metric-label">Total Staff</span>
              <span class="metric-value">{{ data.totalStaff }}</span>
              <span class="metric-sub">Across all restaurants</span>
            </div>
          </div>
        </div>

        <div class="insight-grid">
          <div class="insight-card">
            <div class="insight-header">
              <h3>Sub-Merchant Status</h3>
              <span class="insight-total">{{ subMerchantCounts().total }}</span>
            </div>
            <div class="status-bar">
              <div class="bar-segment bar-active" [style.width.%]="subMerchantPct().active"></div>
              <div class="bar-segment bar-pending" [style.width.%]="subMerchantPct().pending"></div>
              <div class="bar-segment bar-rejected" [style.width.%]="subMerchantPct().rejected"></div>
            </div>
            <div class="status-legend">
              <span><span class="dot dot-active"></span> Active {{ subMerchantCounts().active }}</span>
              <span><span class="dot dot-pending"></span> Pending {{ subMerchantCounts().pending }}</span>
              <span><span class="dot dot-rejected"></span> Rejected {{ subMerchantCounts().rejected }}</span>
            </div>
          </div>

          <div class="insight-card">
            <div class="insight-header">
              <h3>Online Orders</h3>
              <span class="insight-total">{{ marketplaceCounts().total }}</span>
            </div>
            <div class="status-bar">
              <div class="bar-segment bar-pending" [style.width.%]="marketplacePct().pending"></div>
              <div class="bar-segment bar-ready" [style.width.%]="marketplacePct().ready"></div>
              <div class="bar-segment bar-completed" [style.width.%]="marketplacePct().completed"></div>
            </div>
            <div class="status-legend">
              <span><span class="dot dot-pending"></span> Pending {{ marketplaceCounts().pending }}</span>
              <span><span class="dot dot-ready"></span> Ready {{ marketplaceCounts().ready }}</span>
              <span><span class="dot dot-completed"></span> Done {{ marketplaceCounts().completed }}</span>
            </div>
          </div>

          <div class="insight-card">
            <div class="insight-header">
              <h3>Refunds</h3>
              <span class="insight-total">{{ data.refundedOrders }}</span>
            </div>
            <div class="refund-metrics">
              <div class="refund-row">
                <span>Orders refunded</span>
                <strong>{{ data.refundedOrders }}</strong>
              </div>
              <div class="refund-row">
                <span>Total amount</span>
                <strong>{{ data.refundedAmountFormatted }}</strong>
              </div>
              <div class="refund-row" *ngIf="data.totalOrders > 0">
                <span>Refund rate</span>
                <strong>{{ (data.refundedOrders / data.totalOrders * 100).toFixed(1) }}%</strong>
              </div>
            </div>
          </div>

          <div class="insight-card">
            <div class="insight-header">
              <h3>Revenue vs Refunds</h3>
              <span class="insight-total" style="font-size:1rem;">Net</span>
            </div>
            <div class="revenue-compare">
              <div class="rev-bar">
                <div class="rev-bar-fill rev-revenue" [style.height.%]="100"></div>
                <span>Revenue</span>
              </div>
              <div class="rev-bar">
                <div class="rev-bar-fill rev-refund" [style.height.%]="refundRatio()"></div>
                <span>Refunds</span>
              </div>
            </div>
            <div class="revenue-values">
              <span><strong>Revenue:</strong> {{ data.totalRevenueFormatted }}</span>
              <span><strong>Refunded:</strong> {{ data.refundedAmountFormatted }}</span>
            </div>
          </div>
        </div>

        <div class="pending-feed" *ngIf="hasPendingActions()">
          <div class="feed-header">⚡ Pending Actions</div>
          <div class="feed-list">
            <a class="feed-item" href="/admin/sub-merchants" *ngIf="subMerchantCounts().pending > 0">
              <span class="feed-dot orange"></span>
              <span class="feed-text"><strong>{{ subMerchantCounts().pending }}</strong> sub-merchant{{ subMerchantCounts().pending > 1 ? 's' : '' }} pending KYC review</span>
              <span class="feed-arrow">→</span>
            </a>
            <a class="feed-item" href="/admin/sub-merchants" *ngIf="subMerchantCounts().rejected > 0">
              <span class="feed-dot red"></span>
              <span class="feed-text"><strong>{{ subMerchantCounts().rejected }}</strong> sub-merchant{{ subMerchantCounts().rejected > 1 ? 's' : '' }} KYC rejected — needs re-submission</span>
              <span class="feed-arrow">→</span>
            </a>
            <div class="feed-item" *ngIf="marketplaceCounts().pending > 0">
              <span class="feed-dot blue"></span>
              <span class="feed-text"><strong>{{ marketplaceCounts().pending }}</strong> online order{{ marketplaceCounts().pending > 1 ? 's' : '' }} waiting for acceptance</span>
              <span class="chip" style="margin-left:auto;">{{ marketplaceCounts().pending }} pending</span>
            </div>
          </div>
        </div>

        <div class="action-grid">
          <a class="action-card" href="/admin/businesses">
            <div class="action-icon-wrap">🏪</div>
            <div>
              <strong>Browse Businesses</strong>
              <span>{{ data.totalBusinesses }} registered</span>
            </div>
            <span class="action-arrow">→</span>
          </a>
          <a class="action-card" href="/admin/sub-merchants">
            <div class="action-icon-wrap">👥</div>
            <div>
              <strong>Sub-Merchants</strong>
              <span>{{ subMerchantCounts().total }} total, {{ subMerchantCounts().active }} active</span>
            </div>
            <span class="action-arrow">→</span>
          </a>
          <a class="action-card" href="/admin/sub-merchants" *ngIf="subMerchantCounts().pending > 0">
            <div class="action-icon-wrap warn">⏳</div>
            <div>
              <strong>Review KYC</strong>
              <span>{{ subMerchantCounts().pending }} pending approval</span>
            </div>
            <span class="action-chip urgent">ACT NOW</span>
          </a>
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
    .dash-header { display:flex; justify-content:space-between; align-items:flex-start; gap:1rem; flex-wrap:wrap; }
    .dash-header h2 { margin:0 0 .25rem; font-size:clamp(1.5rem,2.5vw,2rem); }
    .dash-header p { margin:0; }
    .header-badges { display:flex; gap:.5rem; flex-wrap:wrap; }
    .badge-gold { background:rgba(181,106,45,.12); color:var(--brand-deep); padding:.3rem .8rem; border-radius:999px; font-size:.78rem; font-weight:700; }
    .badge-green { background:rgba(29,123,95,.12); color:var(--accent); padding:.3rem .8rem; border-radius:999px; font-size:.78rem; font-weight:700; }

    .metric-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:1rem; margin-top:1.5rem; }
    .metric-card { display:flex; align-items:center; gap:1rem; padding:1.25rem; border-radius:16px; color:#fff; }
    .gradient-gold { background:linear-gradient(135deg,#b56a2d,#7e4417); }
    .gradient-green { background:linear-gradient(135deg,#1d7b5f,#0d4a38); }
    .gradient-blue { background:linear-gradient(135deg,#4a90d9,#2d6cb5); }
    .gradient-purple { background:linear-gradient(135deg,#8b5cf6,#6d3fd1); }
    .metric-icon { font-size:2.2rem; line-height:1; }
    .metric-body { display:flex; flex-direction:column; }
    .metric-label { font-size:.78rem; opacity:.85; text-transform:uppercase; letter-spacing:.04em; }
    .metric-value { font-size:1.8rem; font-weight:800; line-height:1.2; }
    .metric-sub { font-size:.78rem; opacity:.75; }

    .insight-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:1rem; margin-top:1rem; }
    .insight-card { background:var(--panel); border:1px solid var(--line); border-radius:16px; padding:1.25rem; box-shadow:var(--shadow-soft); }
    .insight-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:.75rem; }
    .insight-header h3 { margin:0; font-size:.95rem; color:var(--muted); }
    .insight-total { font-size:1.4rem; font-weight:800; }
    .status-bar { display:flex; height:8px; border-radius:999px; overflow:hidden; background:#f0e8dc; }
    .bar-segment { transition:width .4s ease; }
    .bar-active { background:#1d7b5f; }
    .bar-pending { background:#e67e22; }
    .bar-rejected { background:#a6372f; }
    .bar-ready { background:#4a90d9; }
    .bar-completed { background:#8b5cf6; }
    .status-legend { display:flex; gap:1rem; margin-top:.6rem; font-size:.82rem; flex-wrap:wrap; }
    .dot { display:inline-block; width:8px; height:8px; border-radius:50%; margin-right:.3rem; }
    .dot-active { background:#1d7b5f; }
    .dot-pending { background:#e67e22; }
    .dot-rejected { background:#a6372f; }
    .dot-ready { background:#4a90d9; }
    .dot-completed { background:#8b5cf6; }

    .refund-metrics { display:flex; flex-direction:column; gap:.6rem; }
    .refund-row { display:flex; justify-content:space-between; align-items:center; padding:.4rem 0; border-bottom:1px solid var(--line); }
    .refund-row:last-child { border-bottom:none; }
    .refund-row span { font-size:.88rem; color:var(--muted); }
    .refund-row strong { font-size:1rem; }

    .revenue-compare { display:flex; justify-content:center; gap:2rem; padding:.5rem 0; }
    .rev-bar { display:flex; flex-direction:column; align-items:center; gap:.4rem; }
    .rev-bar-fill { width:40px; border-radius:8px 8px 4px 4px; transition:height .4s; min-height:4px; }
    .rev-revenue { background:linear-gradient(180deg,#b56a2d,#7e4417); }
    .rev-refund { background:linear-gradient(180deg,#a6372f,#7a2822); }
    .rev-bar span { font-size:.72rem; color:var(--muted); }
    .revenue-values { display:flex; justify-content:space-between; font-size:.82rem; margin-top:.5rem; padding-top:.5rem; border-top:1px solid var(--line); }

    .action-grid { display:grid; gap:.75rem; margin-top:1rem; }
    .action-card { display:flex; align-items:center; gap:1rem; background:var(--panel); border:1px solid var(--line); border-radius:12px; padding:1rem 1.25rem; text-decoration:none; color:inherit; transition:box-shadow .18s,transform .18s; }
    .action-card:hover { box-shadow:var(--shadow-soft); transform:translateY(-1px); }
    .action-card strong { display:block; font-size:.92rem; }
    .action-card span { font-size:.82rem; color:var(--muted); }
    .action-arrow { margin-left:auto; font-size:1.2rem; color:var(--brand); font-weight:700; }
    .action-icon-wrap { width:40px; height:40px; border-radius:12px; background:rgba(181,106,45,.1); display:flex; align-items:center; justify-content:center; font-size:1.3rem; flex-shrink:0; }
    .action-icon-wrap.warn { background:rgba(230,126,34,.12); }

    .load-state { text-align:center; padding:3rem; color:var(--muted); }
    .spinner { width:24px; height:24px; border:3px solid var(--line); border-top-color:var(--brand); border-radius:50%; animation:spin .7s linear infinite; margin:0 auto .75rem; }
    @keyframes spin { to { transform:rotate(360deg); } }
    .pending-feed { background:var(--panel); border:1px solid var(--line); border-radius:16px; padding:1rem 1.25rem; box-shadow:var(--shadow-soft); margin-top:1rem; }
    .feed-header { font-weight:700; font-size:.9rem; margin-bottom:.6rem; }
    .feed-list { display:flex; flex-direction:column; gap:.4rem; }
    .feed-item { display:flex; align-items:center; gap:.65rem; padding:.55rem .75rem; border-radius:10px; background:var(--bg); border:1px solid var(--line); text-decoration:none; color:inherit; transition:background .15s; }
    .feed-item:hover { background:rgba(181,106,45,.05); }
    .feed-dot { width:8px; height:8px; border-radius:50%; flex-shrink:0; }
    .feed-dot.orange { background:#e67e22; }
    .feed-dot.red { background:#a6372f; }
    .feed-dot.blue { background:#4a90d9; }
    .feed-text { flex:1; font-size:.85rem; }
    .feed-text strong { font-weight:700; }
    .feed-arrow { color:var(--brand); font-weight:700; }
  `]
})
export class PlatformDashboardPageComponent {
  private readonly api = inject(AdminApiService);

  readonly summary = toSignal(
    this.api.getDashboardSummary().pipe(
      map(s => ({
        ...s,
        totalRevenueFormatted: formatCurrency(s.totalRevenue),
        refundedAmountFormatted: formatCurrency(s.refundedAmount)
      }))
    )
  );

  readonly subMerchantCounts = toSignal(
    this.api.getSubMerchants().pipe(
      map(list => ({
        total: list.length,
        active: list.filter(s => s.status === 'ACTIVE').length,
        pending: list.filter(s => s.status === 'PENDING_KYC' || s.status === 'KYC_SUBMITTED').length,
        rejected: list.filter(s => s.status === 'REJECTED' || s.status === 'FAILED').length
      }))
    ),
    { initialValue: { total: 0, active: 0, pending: 0, rejected: 0 } }
  );

  readonly marketplaceCounts = signal({ total: 0, pending: 0, ready: 0, completed: 0 });

  readonly subMerchantPct = signal({ active: 0, pending: 0, rejected: 0 });
  readonly marketplacePct = signal({ pending: 0, ready: 0, completed: 0 });
  readonly refundRatio = signal(0);

  hasPendingActions(): boolean {
    return this.subMerchantCounts().pending > 0 || this.subMerchantCounts().rejected > 0 || this.marketplaceCounts().pending > 0;
  }
}
