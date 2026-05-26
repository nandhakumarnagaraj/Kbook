import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';

interface PaymentDashboardData {
  totalTransactions: number;
  successRate: number;
  todayRevenue: number;
  todayOrders: number;
  paymentMethods: { method: string; count: number; successRate: number }[];
  easebuzzSuccessRate: number;
}

@Component({
  selector: 'app-payment-dashboard-page',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatDividerModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatButtonModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <div class="title-container">
            <h1 class="page-title">Payment Dashboard</h1>
            <p class="page-subtitle">Real-time payment gateway analytics and transaction insights.</p>
          </div>
        </div>
        <div class="header-right">
          <div class="status-indicator">
            <span class="dot pulse success"></span>
            <span class="status-label">Easebuzz Gateway: Operational</span>
          </div>
        </div>
      </div>

      <div *ngIf="data() as d; else loading">
        <div class="stats-grid">
          <mat-card class="stat-card transactions">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">query_stats</mat-icon>
              <mat-card-title>{{ d.totalTransactions }}</mat-card-title>
              <mat-card-subtitle>Total Transactions</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-footer">
                <span class="subtext">Processed all-time</span>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card success-rate">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">check_circle</mat-icon>
              <mat-card-title>{{ d.successRate }}%</mat-card-title>
              <mat-card-subtitle>Success Rate</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-footer">
                <mat-progress-bar mode="determinate" [value]="d.successRate" class="rate-bar"></mat-progress-bar>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card revenue">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">payments</mat-icon>
              <mat-card-title>{{ formatCurrencyValue(d.todayRevenue) }}</mat-card-title>
              <mat-card-subtitle>Today's Revenue</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-footer">
                <span class="subtext">Daily collection</span>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card orders">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">shopping_bag</mat-icon>
              <mat-card-title>{{ d.todayOrders }}</mat-card-title>
              <mat-card-subtitle>Today's Orders</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-footer">
                <span class="subtext">Payment orders</span>
              </div>
            </mat-card-content>
          </mat-card>
        </div>

        <div class="main-grid">
          <mat-card class="breakdown-card">
            <mat-card-header>
              <mat-card-title>Payment Method Breakdown</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="table-container" *ngIf="d.paymentMethods.length; else noMethods">
                <table mat-table [dataSource]="d.paymentMethods">
                  <ng-container matColumnDef="method">
                    <th mat-header-cell *matHeaderCellDef> Method </th>
                    <td mat-cell *matCellDef="let pm"> {{ pm.method }} </td>
                  </ng-container>

                  <ng-container matColumnDef="count">
                    <th mat-header-cell *matHeaderCellDef> Count </th>
                    <td mat-cell *matCellDef="let pm"> {{ pm.count }} </td>
                  </ng-container>

                  <ng-container matColumnDef="rate">
                    <th mat-header-cell *matHeaderCellDef> Success Rate </th>
                    <td mat-cell *matCellDef="let pm">
                      <span class="rate-chip" [class.good]="pm.successRate >= 90" [class.warn]="pm.successRate < 90 && pm.successRate >= 70" [class.danger]="pm.successRate < 70">
                        {{ pm.successRate }}%
                      </span>
                    </td>
                  </ng-container>

                  <tr mat-header-row *matHeaderRowDef="['method', 'count', 'rate']"></tr>
                  <tr mat-row *matRowDef="let row; columns: ['method', 'count', 'rate'];"></tr>
                </table>
              </div>
              <ng-template #noMethods>
                <div class="no-data">No payment method data available.</div>
              </ng-template>
            </mat-card-content>
          </mat-card>

          <div class="health-col">
            <mat-card class="health-card">
              <mat-card-header>
                <mat-card-title>Gateway Health</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="gateway-health">
                  <div class="health-circle">
                    <mat-progress-spinner mode="determinate" [value]="d.easebuzzSuccessRate" [diameter]="100" [strokeWidth]="8"></mat-progress-spinner>
                    <div class="health-value">
                      <strong>{{ d.easebuzzSuccessRate }}%</strong>
                      <span>Uptime</span>
                    </div>
                  </div>
                  <div class="health-info">
                    <div class="health-status" [class.good]="d.easebuzzSuccessRate >= 95" [class.warn]="d.easebuzzSuccessRate < 95 && d.easebuzzSuccessRate >= 80" [class.danger]="d.easebuzzSuccessRate < 80">
                      {{ d.easebuzzSuccessRate >= 95 ? 'Gateway is healthy' : (d.easebuzzSuccessRate >= 80 ? 'Gateway needs attention' : 'Gateway critical') }}
                    </div>
                    <p class="subtext">Easebuzz gateway success rate for current period.</p>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <mat-card class="action-card">
              <mat-card-header>
                <mat-card-title>Quick Actions</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                <div class="action-list">
                  <button mat-stroked-button color="primary" class="full-width">
                    <mat-icon>history</mat-icon>
                    View Transaction Log
                  </button>
                  <button mat-stroked-button color="primary" class="full-width">
                    <mat-icon>account_balance</mat-icon>
                    Settlement Reports
                  </button>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </div>
      </div>

      <ng-template #loading>
        <div class="loading-container">
          <mat-spinner diameter="40"></mat-spinner>
          <p>Loading payment analytics...</p>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .header-row { 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
      margin-bottom: 32px; 
      gap: 16px;
      flex-wrap: nowrap;
    }
    .header-left {
      min-width: 0;
      flex: 1;
    }
    .header-right {
      flex-shrink: 0;
    }
    .title-container {
      display: flex;
      align-items: baseline;
      gap: 12px;
      flex-wrap: nowrap;
      min-width: 0;
    }

    .page-title { 
      margin: 0; 
      font-size: 1.75rem; 
      font-weight: 700; 
      color: var(--ink); 
      white-space: nowrap;
      flex-shrink: 0;
    }
    .page-subtitle { 
      margin: 0; 
      color: var(--muted); 
      font-size: 0.85rem; 
      white-space: nowrap;
      text-overflow: ellipsis;
      overflow: hidden;
    }

    .status-indicator { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: var(--bg-elevated); border-radius: 999px; border: 1px solid var(--line); }
    .dot { width: 8px; height: 8px; border-radius: 50%; }
    .dot.success { background: #16a34a; box-shadow: 0 0 0 4px rgba(22, 163, 74, 0.1); }
    .dot.pulse { animation: pulse 2s infinite; }
    @keyframes pulse { 0% { box-shadow: 0 0 0 0px rgba(22, 163, 74, 0.4); } 70% { box-shadow: 0 0 0 10px rgba(22, 163, 74, 0); } 100% { box-shadow: 0 0 0 0px rgba(22, 163, 74, 0); } }
    .status-label { font-size: 0.85rem; font-weight: 600; color: var(--ink); }

    .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .stat-card { border-radius: 12px; border: none; box-shadow: 0 4px 16px rgba(0,0,0,0.05); }
    
    ::ng-deep .stat-card .mat-mdc-card-header {
      padding: 12px 16px !important;
      gap: 12px !important;
    }
    ::ng-deep .stat-card .mat-mdc-card-content {
      padding: 0 16px 12px !important;
    }
    ::ng-deep .stat-card .mat-mdc-card-title {
      font-size: 1.25rem !important;
      font-weight: 700 !important;
      margin: 0 !important;
    }
    ::ng-deep .stat-card .mat-mdc-card-subtitle {
      font-size: 0.8rem !important;
      color: var(--muted) !important;
      margin-top: 2px !important;
    }

    .stat-icon { 
      background: var(--brand-soft); 
      color: var(--brand); 
      width: 36px; 
      height: 36px; 
      line-height: 36px; 
      text-align: center; 
      border-radius: 8px; 
      font-size: 18px; 
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .transactions .stat-icon { background: #f3e8ff; color: #9333ea; }
    .success-rate .stat-icon { background: #dcfce7; color: #16a34a; }
    .revenue .stat-icon { background: #fef3c7; color: #d97706; }
    .orders .stat-icon { background: #e0f2fe; color: #0284c7; }
 
    .stat-footer { margin-top: 8px; }
    .subtext { font-size: 0.75rem; color: var(--muted); }
    .rate-bar { height: 6px; border-radius: 3px; }

    .main-grid { display: grid; grid-template-columns: 1.5fr 1fr; gap: 32px; }
    .breakdown-card { border-radius: 16px; border: none; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
    .table-container { margin-top: 16px; }
    table { width: 100%; }

    .rate-chip { padding: 4px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 600; }
    .rate-chip.good { background: #dcfce7; color: #16a34a; }
    .rate-chip.warn { background: #fef3c7; color: #d97706; }
    .rate-chip.danger { background: #fee2e2; color: #dc2626; }

    .health-col { display: flex; flex-direction: column; gap: 24px; }
    .health-card { border-radius: 16px; border: none; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
    .gateway-health { display: flex; align-items: center; gap: 24px; padding: 16px 0; }
    .health-circle { position: relative; display: flex; align-items: center; justify-content: center; }
    .health-value { position: absolute; display: flex; flex-direction: column; align-items: center; }
    .health-value strong { font-size: 1.25rem; color: var(--ink); }
    .health-value span { font-size: 0.65rem; color: var(--muted); text-transform: uppercase; }
    .health-info { flex: 1; }
    .health-status { font-weight: 700; font-size: 1.1rem; margin-bottom: 4px; }
    .health-status.good { color: #16a34a; }
    .health-status.warn { color: #d97706; }
    .health-status.danger { color: #dc2626; }

    .action-card { border-radius: 16px; border: none; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
    .action-list { display: flex; flex-direction: column; gap: 12px; padding: 16px 0; }
    .full-width { width: 100%; justify-content: flex-start; gap: 12px; }

    .loading-container { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 400px; color: var(--muted); }
    .no-data { padding: 32px; text-align: center; color: var(--muted); }

    @media (max-width: 960px) { .main-grid { grid-template-columns: 1fr; } }
  `]
})
export class PaymentDashboardPageComponent {
  private readonly api = inject(AdminApiService);

  readonly data = toSignal(
    this.api.getPaymentDashboard().pipe(
      catchError(() => of(null)),
      map((raw: any) => raw ? {
        totalTransactions: raw.totalTransactions ?? 0,
        successRate: raw.successRate ?? 0,
        todayRevenue: raw.todayRevenue ?? 0,
        todayOrders: raw.todayOrders ?? 0,
        paymentMethods: raw.paymentMethods ?? [],
        easebuzzSuccessRate: raw.easebuzzSuccessRate ?? 0
      } as PaymentDashboardData : null)
    )
  );

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }
}
