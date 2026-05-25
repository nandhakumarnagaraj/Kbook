import { CommonModule } from '@angular/common';
import { Component, inject, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { of, interval, Subject } from 'rxjs';
import { catchError, map, startWith, switchMap } from 'rxjs/operators';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule, 
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatSelectModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatBadgeModule,
    MatDividerModule,
    MatFormFieldModule,
    MatListModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="page-container">
      <ng-container *ngIf="summary() as data; else loading">
        
        <div class="header-row">
          <div class="header-left">
            <div class="live-status">
              <span class="live-dot"></span>
              <span class="live-text">Live Platform Data</span>
            </div>
            <h1 class="page-title">Platform Overview</h1>
            <p class="page-subtitle">{{ liveDate() }}</p>
          </div>
          <div class="header-right">
            <mat-form-field appearance="outline" class="poll-field">
              <mat-label>Auto Refresh</mat-label>
              <mat-select [value]="pollIntervalMs()" (selectionChange)="setPollInterval($event.value)">
                <mat-option [value]="0">Off</mat-option>
                <mat-option [value]="15000">15s</mat-option>
                <mat-option [value]="30000">30s</mat-option>
                <mat-option [value]="60000">60s</mat-option>
              </mat-select>
            </mat-form-field>
            <button mat-icon-button (click)="refresh()" [disabled]="refreshing" matTooltip="Refresh Now">
              <mat-icon [class.spinning]="refreshing">refresh</mat-icon>
            </button>
          </div>
        </div>

        <div class="stats-grid">
          <mat-card class="stat-card businesses clickable" routerLink="/admin/businesses">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">business</mat-icon>
              <mat-card-title>{{ data.totalBusinesses }}</mat-card-title>
              <mat-card-subtitle>Total Businesses</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-footer">
                <span class="trend up"><mat-icon>trending_up</mat-icon> 12%</span>
                <span class="subtext">{{ data.liveBusinesses }} currently active</span>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card revenue clickable" routerLink="/admin/payment-dashboard">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">payments</mat-icon>
              <mat-card-title>{{ data.totalRevenueFormatted }}</mat-card-title>
              <mat-card-subtitle>Gross Revenue</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-footer">
                <span class="trend up"><mat-icon>trending_up</mat-icon> 8.2%</span>
                <span class="subtext">Across all time</span>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card orders clickable" routerLink="/admin/transactions">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">shopping_bag</mat-icon>
              <mat-card-title>{{ data.totalOrders }}</mat-card-title>
              <mat-card-subtitle>Total Orders</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-footer">
                <span class="trend down"><mat-icon>trending_down</mat-icon> 3.1%</span>
                <span class="subtext">{{ data.refundedOrders }} refunded</span>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card staff clickable" routerLink="/admin/businesses">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">people</mat-icon>
              <mat-card-title>{{ data.totalStaff }}</mat-card-title>
              <mat-card-subtitle>Platform Staff</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-footer">
                <span class="trend up"><mat-icon>trending_up</mat-icon> 5%</span>
                <span class="subtext">All active members</span>
              </div>
            </mat-card-content>
          </mat-card>
        </div>

        <div class="main-grid">
          <div class="left-col">
            <mat-card class="chart-card">
              <mat-card-header>
                <mat-card-title>Sub-merchant Onboarding</mat-card-title>
                <mat-card-subtitle>KYC Pipeline Status</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                <div class="pipeline-visual">
                  <div class="pipeline-labels">
                    <span>Active ({{ subMerchantCounts().active }})</span>
                    <span>Pending ({{ subMerchantCounts().pending }})</span>
                    <span>Rejected ({{ subMerchantCounts().rejected }})</span>
                  </div>
                  <div class="pipeline-bars">
                    <div class="bar active" [style.width.%]="subMerchantPct.active"></div>
                    <div class="bar pending" [style.width.%]="subMerchantPct.pending"></div>
                    <div class="bar rejected" [style.width.%]="subMerchantPct.rejected"></div>
                  </div>
                </div>

                <mat-list>
                  <mat-list-item routerLink="/admin/sub-merchants" class="clickable-item">
                    <mat-icon matListItemIcon color="primary">pending_actions</mat-icon>
                    <span matListItemTitle>{{ subMerchantCounts().pending }} KYC reviews pending</span>
                    <span matListItemLine>Action required for activation</span>
                    <mat-icon matListIconSuffix>chevron_right</mat-icon>
                  </mat-list-item>
                </mat-list>
              </mat-card-content>
            </mat-card>

            <div class="action-grid">
               <mat-card class="action-item clickable" routerLink="/admin/businesses">
                 <mat-icon>business</mat-icon>
                 <div class="action-label">Businesses</div>
               </mat-card>
               <mat-card class="action-item clickable" routerLink="/admin/payment-dashboard">
                 <mat-icon>receipt</mat-icon>
                 <div class="action-label">Payments</div>
               </mat-card>
               <mat-card class="action-item clickable" routerLink="/admin/transactions">
                 <mat-icon>history</mat-icon>
                 <div class="action-label">Transactions</div>
               </mat-card>
               <mat-card class="action-item clickable" routerLink="/admin/settlements">
                 <mat-icon>account_balance</mat-icon>
                 <div class="action-label">Settlements</div>
               </mat-card>
            </div>
          </div>

          <div class="right-col">
            <mat-card class="revenue-card">
              <mat-card-header>
                <mat-card-title>Revenue Breakdown</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="revenue-summary">
                  <div class="rev-row">
                    <span class="label">Gross</span>
                    <span class="value">{{ data.totalRevenueFormatted }}</span>
                  </div>
                  <mat-progress-bar mode="determinate" [value]="100" class="gross-bar"></mat-progress-bar>
                  
                  <div class="rev-row">
                    <span class="label">Refunded</span>
                    <span class="value text-danger">{{ data.refundedAmountFormatted }}</span>
                  </div>
                  <mat-progress-bar mode="determinate" [value]="refundPct" color="warn"></mat-progress-bar>

                  <mat-divider></mat-divider>

                  <div class="rev-row highlight">
                    <span class="label">Net Platform Revenue</span>
                    <span class="value">{{ netRevenueFormatted(data) }}</span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card class="health-card">
              <mat-card-header>
                <mat-card-title>Business Health</mat-card-title>
                <span class="health-badge" [class.good]="businessLivePct(data) > 70">
                   {{ businessLivePct(data) }}% Live
                </span>
              </mat-card-header>
              <mat-card-content>
                <div class="health-metrics">
                   <div class="metric">
                      <span class="m-val">{{ data.liveBusinesses }}</span>
                      <span class="m-label">Active Shops</span>
                   </div>
                   <div class="metric">
                      <span class="m-val">{{ avgOrdersPerBusiness(data) }}</span>
                      <span class="m-label">Orders / Shop</span>
                   </div>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </div>

      </ng-container>

      <ng-template #loading>
        <div class="skeleton-container">
          <div class="header-row">
            <div class="skeleton-title"></div>
          </div>
          <div class="stats-grid">
            <div class="skeleton-card" *ngFor="let i of [1,2,3,4]"></div>
          </div>
          <div class="main-grid">
            <div class="skeleton-card large"></div>
            <div class="skeleton-card medium"></div>
          </div>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .page-container {
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
    }

    .header-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 32px;
    }

    .page-title {
      margin: 0;
      font-size: 2rem;
      font-weight: 800;
      color: var(--ink);
      letter-spacing: -0.5px;
    }

    .page-subtitle {
      margin: 4px 0 0;
      color: var(--muted);
      font-size: 0.95rem;
    }

    .live-status {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;
    }

    .live-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #10b981;
      box-shadow: 0 0 0 rgba(16, 185, 129, 0.4);
      animation: pulse 2s infinite;
    }

    @keyframes pulse {
      0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
      70% { box-shadow: 0 0 0 10px rgba(16, 185, 129, 0); }
      100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
    }

    .live-text {
      font-size: 0.75rem;
      font-weight: 800;
      color: #10b981;
      text-transform: uppercase;
      letter-spacing: 1px;
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .poll-field {
      width: 150px;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 16px;
      margin-bottom: 32px;
    }
    @media (max-width: 1024px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 600px)  { .stats-grid { grid-template-columns: 1fr; } }

    .stat-card {
      border-radius: var(--radius-xl);
      border: 1px solid var(--line);
      box-shadow: var(--shadow-md);
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      overflow: hidden;
      position: relative;
      background: var(--panel);
      min-width: 0;
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

    .stat-card.clickable {
      cursor: pointer;
    }

    .stat-card.clickable:hover {
      transform: translateY(-6px);
      box-shadow: var(--shadow-xl);
    }

    .stat-card.clickable::after {
      content: 'arrow_forward';
      font-family: 'Material Icons';
      position: absolute;
      top: 20px;
      right: 20px;
      font-size: 18px;
      color: var(--muted);
      opacity: 0;
      transition: all 0.3s ease;
      transform: translateX(-10px);
    }

    .stat-card.clickable:hover::after {
      opacity: 1;
      transform: translateX(0);
      color: var(--brand);
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

    /* Premium colors & backgrounds for platform stats cards */
    .businesses { 
      border-color: rgba(2, 132, 199, 0.12) !important;
      background: linear-gradient(135deg, rgba(2, 132, 199, 0.04) 0%, var(--panel) 100%);
    }
    .businesses .stat-icon { background: rgba(14, 165, 233, 0.12); color: #0284c7; }
    .businesses:hover { border-color: rgba(14, 165, 233, 0.3) !important; box-shadow: 0 12px 28px -8px rgba(14, 165, 233, 0.2) !important; }

    .revenue { 
      border-color: rgba(217, 119, 6, 0.12) !important;
      background: linear-gradient(135deg, rgba(217, 119, 6, 0.04) 0%, var(--panel) 100%);
    }
    .revenue .stat-icon { background: rgba(245, 158, 11, 0.12); color: #d97706; }
    .revenue:hover { border-color: rgba(245, 158, 11, 0.3) !important; box-shadow: 0 12px 28px -8px rgba(245, 158, 11, 0.2) !important; }

    .orders { 
      border-color: rgba(22, 163, 74, 0.12) !important;
      background: linear-gradient(135deg, rgba(22, 163, 74, 0.04) 0%, var(--panel) 100%);
    }
    .orders .stat-icon { background: rgba(34, 197, 94, 0.12); color: #16a34a; }
    .orders:hover { border-color: rgba(34, 197, 94, 0.3) !important; box-shadow: 0 12px 28px -8px rgba(34, 197, 94, 0.2) !important; }

    .staff { 
      border-color: rgba(147, 51, 234, 0.12) !important;
      background: linear-gradient(135deg, rgba(147, 51, 234, 0.04) 0%, var(--panel) 100%);
    }
    .staff .stat-icon { background: rgba(168, 85, 247, 0.12); color: #9333ea; }
    .staff:hover { border-color: rgba(168, 85, 247, 0.3) !important; box-shadow: 0 12px 28px -8px rgba(168, 85, 247, 0.2) !important; }

    .stat-footer {
      display: flex;
      flex-direction: column;
      margin-top: 16px;
    }

    .trend {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 0.85rem;
      font-weight: 700;
    }

    .trend mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .trend.up { color: #16a34a; }
    .trend.down { color: #dc2626; }

    .subtext {
      font-size: 0.8rem;
      color: var(--muted);
      margin-top: 4px;
    }

    .main-grid {
      display: grid;
      grid-template-columns: 1.5fr 1fr;
      gap: 32px;
    }

    .left-col, .right-col {
      display: flex;
      flex-direction: column;
      gap: 32px;
    }

    .pipeline-visual {
      margin: 24px 0;
    }

    .pipeline-labels {
      display: flex;
      justify-content: space-between;
      font-size: 0.8rem;
      font-weight: 700;
      color: var(--ink-secondary);
      margin-bottom: 10px;
    }

    .pipeline-bars {
      display: flex;
      height: 14px;
      border-radius: 7px;
      overflow: hidden;
      background: var(--bg);
      box-shadow: inset 0 1px 3px rgba(0,0,0,0.08);
      margin-bottom: 20px;
    }
    
    .bar {
      transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
    }
    .bar.active { background: linear-gradient(90deg, #10b981, #059669); }
    .bar.pending { background: linear-gradient(90deg, #fbbf24, #d97706); }
    .bar.rejected { background: linear-gradient(90deg, #ef4444, #dc2626); }

    .action-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 20px;
    }

    .action-item {
      padding: 24px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      cursor: pointer;
      border-radius: var(--radius-xl) !important;
      border: 1px solid var(--line) !important;
      background: var(--panel) !important;
      box-shadow: var(--shadow-md);
      transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) !important;
      color: var(--ink);
    }

    .action-item:hover {
      transform: translateY(-6px) scale(1.03) !important;
      background: var(--panel-hover) !important;
      border-color: var(--brand-light) !important;
      box-shadow: 0 12px 24px rgba(199, 115, 47, 0.15) !important;
    }

    .action-item mat-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
      color: var(--brand);
      transition: all 0.3s ease;
    }
    .action-item:hover mat-icon {
      transform: scale(1.15);
    }

    .action-label {
      font-weight: 700;
      font-size: 0.95rem;
    }

    .revenue-summary {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px 0;
    }

    .rev-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .rev-row .label { color: var(--ink-secondary); font-size: 0.9rem; }
    .rev-row .value { font-weight: 700; font-size: 1.05rem; color: var(--ink); }
    
    ::ng-deep .revenue-card .mat-mdc-progress-bar {
      height: 6px !important;
      border-radius: 3px !important;
    }
    ::ng-deep .gross-bar .mdc-linear-progress__bar-inner {
      background-color: var(--brand) !important;
    }

    .rev-row.highlight { 
      margin-top: 16px; 
      padding: 12px 16px; 
      border-radius: var(--radius-md); 
      background: var(--brand-soft); 
      border: 1px solid rgba(199, 115, 47, 0.15); 
    }
    .rev-row.highlight .label { font-weight: 700; color: var(--brand-dark); }
    .rev-row.highlight .value { font-size: 1.35rem; color: var(--brand); font-weight: 800; }
    
    /* Dark theme adjustments for highlighted row */
    :host-context(.dark-theme) .rev-row.highlight .label {
      color: var(--brand-light);
    }

    .health-badge {
      font-size: 0.75rem;
      padding: 4px 12px;
      border-radius: 999px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.05);
      background: rgba(239, 68, 68, 0.12);
      color: #ef4444;
      border: 1px solid rgba(239, 68, 68, 0.2);
    }

    .health-badge.good {
      background: rgba(16, 185, 129, 0.12);
      color: #10b981;
      border: 1px solid rgba(16, 185, 129, 0.2);
    }

    .health-metrics {
      display: flex;
      justify-content: space-around;
      padding: 24px 0;
      text-align: center;
    }

    .metric { display: flex; flex-direction: column; gap: 6px; }
    .m-val { font-size: 1.75rem; font-weight: 800; color: var(--ink); }
    .m-label { font-size: 0.75rem; color: var(--muted); text-transform: uppercase; letter-spacing: 1.5px; font-weight: 700; }

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 400px;
      color: var(--muted);
    }

    /* Skeleton Loading Effects */
    .skeleton-container { width: 100%; }
    .skeleton-title { width: 300px; height: 32px; background: #e2e8f0; border-radius: 8px; margin-bottom: 24px; }
    .skeleton-card { height: 160px; background: #e2e8f0; border-radius: 20px; position: relative; overflow: hidden; }
    .skeleton-card.large { height: 400px; }
    .skeleton-card.medium { height: 300px; }

    .skeleton-card::after, .skeleton-title::after {
      content: "";
      position: absolute;
      top: 0; right: 0; bottom: 0; left: 0;
      transform: translateX(-100%);
      background-image: linear-gradient(90deg, rgba(255,255,255,0) 0, rgba(255,255,255,0.4) 50%, rgba(255,255,255,0) 100%);
      animation: shimmer 1.5s infinite;
    }

    @keyframes shimmer { 100% { transform: translateX(100%); } }

    .spinning { animation: rotate 1s linear infinite; }
    @keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

    @media (max-width: 960px) {
      .main-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class PlatformDashboardPageComponent implements OnDestroy {
  private readonly api = inject(AdminApiService);

  readonly Math = Math;
  readonly liveDate = signal(new Date().toLocaleDateString('en-IN', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
  }));
  private dateInterval: ReturnType<typeof setInterval> | null = setInterval(() => {
    this.liveDate.set(new Date().toLocaleDateString('en-IN', {
      weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    }));
  }, 60000);

  ngOnDestroy(): void {
    if (this.dateInterval) clearInterval(this.dateInterval);
  }

  private readonly refresh$ = new Subject<void>();
  refreshing = false;

  readonly pollIntervalMs = signal<number>(
    (() => {
      const stored = localStorage.getItem('kbook-admin-poll');
      const parsed = stored ? parseInt(stored, 10) : 30000;
      return [0, 15000, 30000, 60000].includes(parsed) ? parsed : 30000;
    })()
  );

  readonly summary = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => {
        const timer$ = this.pollIntervalMs() > 0 ? interval(this.pollIntervalMs()) : of(0);
        return timer$.pipe(
          startWith(0),
          switchMap(() =>
            this.api.getDashboardSummary().pipe(
              catchError(() => of(null)),
              map(s => s ? {
                ...s,
                totalRevenueFormatted: formatCurrency(s.totalRevenue),
                refundedAmountFormatted: formatCurrency(s.refundedAmount)
              } : null)
            )
          )
        );
      })
    )
  );

  readonly subMerchantCounts = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => {
        const timer$ = this.pollIntervalMs() > 0 ? interval(this.pollIntervalMs()) : of(0);
        return timer$.pipe(
          startWith(0),
          switchMap(() =>
            this.api.getSubMerchants().pipe(
              catchError(() => of([])),
              map(list => ({
                total: list.length,
                active: list.filter(s => s.status === 'ACTIVE').length,
                pending: list.filter(s => s.status === 'PENDING_KYC' || s.status === 'KYC_SUBMITTED').length,
                rejected: list.filter(s => s.status === 'REJECTED' || s.status === 'FAILED').length
              }))
            )
          )
        );
      })
    ),
    { initialValue: { total: 0, active: 0, pending: 0, rejected: 0 } }
  );

  get subMerchantPct() {
    const c = this.subMerchantCounts();
    const total = c.active + c.pending + c.rejected || 1;
    return {
      active: (c.active / total) * 100,
      pending: (c.pending / total) * 100,
      rejected: (c.rejected / total) * 100
    };
  }

  get refundPct(): number {
    const s = this.summary();
    if (!s || s.totalRevenue <= 0) return 0;
    return Math.min(100, (s.refundedAmount / s.totalRevenue) * 100);
  }

  refresh(): void {
    this.refreshing = true;
    this.refresh$.next();
    setTimeout(() => (this.refreshing = false), 800);
  }

  setPollInterval(val: number): void {
    this.pollIntervalMs.set(val);
    localStorage.setItem('kbook-admin-poll', String(val));
    this.refresh$.next();
  }

  netRevenueFormatted(data: any): string {
    return formatCurrency(data.totalRevenue - data.refundedAmount);
  }

  businessLivePct(data: any): number {
    if (data.totalBusinesses <= 0) return 0;
    return Math.round((data.liveBusinesses / data.totalBusinesses) * 100);
  }

  avgOrdersPerBusiness(data: any): string {
    if (data.totalBusinesses <= 0) return '0';
    return Math.round(data.totalOrders / data.totalBusinesses).toLocaleString();
  }
}
