import { AfterViewInit, Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { of, Subject, interval } from 'rxjs';
import { catchError, map, startWith, switchMap } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatMenuModule } from '@angular/material/menu';
import { Chart, registerables } from 'chart.js';
import { DashboardShellComponent } from '../../shared/dashboard-shell.component';

Chart.register(...registerables);

interface PaymentMethodMetric {
  method: string; total: number; successful: number; successRate: number;
}

interface AnomalyAlert {
  type: string; severity: string; message: string;
  restaurantId?: number; shopName?: string;
  yesterdayRate?: number; todayRate?: number; drop?: number;
}

@Component({
  selector: 'app-payment-dashboard-page',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    MatCardModule, MatIconModule, MatDividerModule, MatProgressBarModule,
    MatTableModule, MatTooltipModule, MatProgressSpinnerModule, MatButtonModule,
    MatChipsModule, MatSelectModule, MatFormFieldModule, MatMenuModule,
    DashboardShellComponent,
  ],
  template: `
    <app-dashboard-shell
      [title]="'Payment Dashboard'"
      [subtitle]="'Real-time payment gateway analytics and transaction insights'"
      [loading]="!data()"
    >
      <ng-container header-actions>
        <div class="header-controls">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-select [value]="trendPeriod()" (selectionChange)="trendPeriod.set($event.value); rebuildTrendChart()">
              <mat-option value="hourly">Hourly</mat-option>
              <mat-option value="daily">Daily</mat-option>
              <mat-option value="weekly">Weekly</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-select [value]="trendDays()" (selectionChange)="trendDays.set($event.value); rebuildTrendChart()">
              <mat-option [value]="1">1 Day</mat-option>
              <mat-option [value]="7">7 Days</mat-option>
              <mat-option [value]="30">30 Days</mat-option>
            </mat-select>
          </mat-form-field>
          <div class="status-indicator">
            <span class="dot pulse success"></span>
            <span class="status-label">Operational</span>
          </div>
        </div>
      </ng-container>

      <!-- Anomaly Alerts -->
      @if (anomalies()?.length) {
        <div class="alerts-bar">
          @for (a of anomalies(); track a.type + (a.restaurantId ?? '') + a.message) {
            <div class="alert-item" [class.critical]="a.severity === 'critical'" [class.warning]="a.severity === 'warning'">
              <mat-icon>{{ a.severity === 'critical' ? 'error' : 'warning' }}</mat-icon>
              <span>{{ a.message }}</span>
            </div>
          }
        </div>
      }

      @if (data(); as d) {
        <div class="stats-grid">
          <mat-card class="stat-card">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon transactions">query_stats</mat-icon>
              <mat-card-title>{{ d.totalTransactions }}</mat-card-title>
              <mat-card-subtitle>Total Transactions</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-detail">
                <span class="up">{{ d.successfulTransactions }} success</span>
                <span class="sep">·</span>
                <span class="down">{{ d.failedTransactions }} failed</span>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon success-rate">check_circle</mat-icon>
              <mat-card-title>{{ d.overallSuccessRate }}%</mat-card-title>
              <mat-card-subtitle>Overall Success Rate</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <mat-progress-bar mode="determinate" [value]="d.overallSuccessRate" class="rate-bar" [class.good]="d.overallSuccessRate >= 95" [class.warn]="d.overallSuccessRate < 95 && d.overallSuccessRate >= 80" [class.danger]="d.overallSuccessRate < 80"></mat-progress-bar>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon revenue">payments</mat-icon>
              <mat-card-title>{{ formatCurrencyValue(d.todayRevenue) }}</mat-card-title>
              <mat-card-subtitle>Today's Revenue</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-detail">
                <span>{{ d.todayOrders }} orders today</span>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon last-hour">schedule</mat-icon>
              <mat-card-title>{{ d.lastHourTotal }}</mat-card-title>
              <mat-card-subtitle>Last Hour ({{ d.lastHourSuccessRate }}% success)</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <mat-progress-bar mode="determinate" [value]="d.lastHourSuccessRate" class="rate-bar" [class.good]="d.lastHourSuccessRate >= 95" [class.warn]="d.lastHourSuccessRate < 95 && d.lastHourSuccessRate >= 80" [class.danger]="d.lastHourSuccessRate < 80"></mat-progress-bar>
            </mat-card-content>
          </mat-card>
        </div>

        <div class="main-grid">
          <!-- Trend Chart -->
          <mat-card class="chart-card" #trendChartContainer>
            <mat-card-header>
              <mat-card-title>Success Rate Trend</mat-card-title>
              <mat-card-subtitle>{{ trendPeriod() | titlecase }} view &middot; Last {{ trendDays() }} days</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="chart-wrapper">
                <canvas #trendCanvas></canvas>
              </div>
            </mat-card-content>
          </mat-card>

          <!-- Payment Method Breakdown + Failed Transactions -->
          <div class="right-col">
            <mat-card class="breakdown-card">
              <mat-card-header>
                <mat-card-title>Payment Method Breakdown</mat-card-title>
              </mat-card-header>
              <mat-card-content>
                @if (d.byPaymentMethod.length) {
                  <table mat-table [dataSource]="d.byPaymentMethod">
                    <ng-container matColumnDef="method">
                      <th mat-header-cell *matHeaderCellDef>Method</th>
                      <td mat-cell *matCellDef="let m">
                        <span class="method-chip" [class]="m.method">{{ m.method }}</span>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="total">
                      <th mat-header-cell *matHeaderCellDef>Total</th>
                      <td mat-cell *matCellDef="let m">{{ m.total }}</td>
                    </ng-container>
                    <ng-container matColumnDef="successRate">
                      <th mat-header-cell *matHeaderCellDef>Success Rate</th>
                      <td mat-cell *matCellDef="let m">
                        <span class="rate-chip" [class.good]="m.successRate >= 90" [class.warn]="m.successRate < 90 && m.successRate >= 70" [class.danger]="m.successRate < 70">{{ m.successRate }}%</span>
                      </td>
                    </ng-container>
                    <tr mat-header-row *matHeaderRowDef="['method', 'total', 'successRate']"></tr>
                    <tr mat-row *matRowDef="let row; columns: ['method', 'total', 'successRate'];"></tr>
                  </table>
                } @else {
                  <div class="no-data">No payment data available.</div>
                }
              </mat-card-content>
            </mat-card>

            <!-- Failed Transactions -->
            <mat-card class="failed-card">
              <mat-card-header>
                <mat-card-title>Failed Transactions</mat-card-title>
                <mat-card-subtitle>Most recent failures</mat-card-subtitle>
              </mat-card-header>
              <mat-card-content>
                @if (failedTxns()?.content?.length) {
                  <div class="failed-list">
                    @for (tx of failedTxns()!.content; track tx.id) {
                      <div class="failed-item">
                        <div class="failed-left">
                          <mat-icon class="failed-icon">cancel</mat-icon>
                        </div>
                        <div class="failed-body">
                          <div class="failed-txnid">{{ tx.txnId }}</div>
                          <div class="failed-meta">{{ tx.shopName || 'Business #' + tx.restaurantId }} · {{ formatCurrencyValue(tx.amount ?? 0) }}</div>
                        </div>
                        <div class="failed-time">{{ tx.receivedAt | date:'short' }}</div>
                      </div>
                    }
                  </div>
                } @else {
                  <div class="no-data">No failed transactions.</div>
                }
              </mat-card-content>
            </mat-card>
          </div>
        </div>
      }
    </app-dashboard-shell>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; gap: 16px; flex-wrap: wrap; }
    .header-left { min-width: 0; }
    .page-title { margin: 0 0 4px; font-size: 1.75rem; font-weight: 700; color: var(--ink); }
    .page-subtitle { margin: 0; color: var(--muted); font-size: 0.85rem; }
    .header-right { flex-shrink: 0; }
    .header-controls { display: flex; align-items: center; gap: 12px; }
    .header-controls ::ng-deep .mat-mdc-form-field { width: 100px; }
    .header-controls ::ng-deep .mat-mdc-form-field-infix { min-height: 36px !important; padding-top: 8px !important; padding-bottom: 8px !important; }
    .header-controls ::ng-deep .mat-mdc-text-field-wrapper { height: 36px !important; }

    .status-indicator { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: var(--bg-elevated); border-radius: 999px; border: 1px solid var(--line); }
    .dot { width: 8px; height: 8px; border-radius: 50%; }
    .dot.success { background: var(--success); box-shadow: 0 0 0 4px rgba(22, 163, 74, 0.1); }
    .dot.pulse { animation: pulse 2s infinite; }
    @keyframes pulse { 0% { box-shadow: 0 0 0 0px rgba(22, 163, 74, 0.4); } 70% { box-shadow: 0 0 0 10px rgba(22, 163, 74, 0); } 100% { box-shadow: 0 0 0 0px rgba(22, 163, 74, 0); } }
    .status-label { font-size: 0.85rem; font-weight: 600; color: var(--ink); }

    .alerts-bar { display: flex; flex-direction: column; gap: 8px; margin-bottom: 20px; }
    .alert-item { display: flex; align-items: center; gap: 10px; padding: 10px 16px; border-radius: 10px; font-size: 0.85rem; font-weight: 500; }
    .alert-item.critical { background: var(--danger-bg); color: var(--danger); border: 1px solid var(--danger-border); }
    .alert-item.warning { background: var(--warn-bg); color: var(--warn); border: 1px solid var(--warn-border); }
    :root.dark-theme .alert-item.critical { background: var(--danger-bg); color: var(--danger); border: 1px solid var(--danger-border); }
    :root.dark-theme .alert-item.warning { background: #451a03; color: var(--warn); border-color: #78350f; }

    .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .stat-card { border-radius: 12px; border: none; box-shadow: 0 4px 16px rgba(0,0,0,0.05); }
    ::ng-deep .stat-card .mat-mdc-card-header { padding: 12px 16px !important; gap: 12px !important; }
    ::ng-deep .stat-card .mat-mdc-card-content { padding: 0 16px 12px !important; }
    ::ng-deep .stat-card .mat-mdc-card-title { font-size: 1.25rem !important; font-weight: 700 !important; margin: 0 !important; }
    ::ng-deep .stat-card .mat-mdc-card-subtitle { font-size: 0.8rem !important; color: var(--muted) !important; margin-top: 2px !important; }

    .stat-icon { background: var(--brand-soft); color: var(--brand); width: 36px; height: 36px; line-height: 36px; text-align: center; border-radius: 8px; font-size: 18px; display: flex; align-items: center; justify-content: center; }
    .transactions.stat-icon { background: var(--purple-soft); color: var(--purple); }
    .success-rate.stat-icon { background: var(--success-bg); color: var(--success); }
    .revenue.stat-icon { background: var(--warn-bg); color: var(--warn); }
    .last-hour.stat-icon { background: var(--info-bg); color: var(--info); }

    .stat-detail { margin-top: 6px; font-size: 0.8rem; color: var(--muted); display: flex; gap: 6px; align-items: center; }
    .stat-detail .up { color: var(--success); font-weight: 500; }
    .stat-detail .down { color: var(--danger); font-weight: 500; }
    .stat-detail .sep { color: var(--line); }
    .rate-bar { height: 6px; border-radius: 3px; margin-top: 4px; }
    .rate-bar.good ::ng-deep .mat-mdc-progress-bar-fill { background: var(--success) !important; }
    .rate-bar.warn ::ng-deep .mat-mdc-progress-bar-fill { background: var(--warn) !important; }
    .rate-bar.danger ::ng-deep .mat-mdc-progress-bar-fill { background: var(--danger) !important; }

    .main-grid { display: grid; grid-template-columns: 1.5fr 1fr; gap: 24px; }

    .chart-card { border-radius: 16px; border: none; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
    .chart-wrapper { position: relative; height: 280px; margin-top: 8px; }

    .right-col { display: flex; flex-direction: column; gap: 24px; }
    .breakdown-card { border-radius: 16px; border: none; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
    table { width: 100%; }
    .method-chip { padding: 2px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 600; background: var(--brand-soft); color: var(--brand); text-transform: capitalize; }
    .rate-chip { padding: 4px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 600; }
    .rate-chip.good { background: var(--success-bg); color: var(--success); }
    .rate-chip.warn { background: var(--warn-bg); color: var(--warn); }
    .rate-chip.danger { background: var(--danger-bg); color: var(--danger); }

    .failed-card { border-radius: 16px; border: none; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
    .failed-list { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; max-height: 320px; overflow-y: auto; }
    .failed-item { display: flex; align-items: center; gap: 12px; padding: 8px 12px; border-radius: 8px; transition: background 0.15s; }
    .failed-item:hover { background: var(--bg-elevated); }
    .failed-icon { color: var(--danger); font-size: 18px; width: 18px; height: 18px; }
    .failed-body { flex: 1; min-width: 0; }
    .failed-txnid { font-size: 0.8rem; font-weight: 600; color: var(--ink); font-family: monospace; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .failed-meta { font-size: 0.7rem; color: var(--muted); }
    .failed-time { font-size: 0.7rem; color: var(--muted); white-space: nowrap; }

    .loading-container { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 400px; color: var(--muted); gap: 16px; }
    .no-data { padding: 32px; text-align: center; color: var(--muted); font-size: 0.85rem; }

    @media (max-width: 960px) { .main-grid { grid-template-columns: 1fr; } }

    @media (max-width: 600px) {
      .page-container { padding: 16px; }
      .page-title { font-size: 1.35rem; }
      .chart-wrapper { height: 180px; }
      ::ng-deep .stat-card .mat-mdc-card-title { font-size: 1rem; }
      .header-controls { flex-wrap: wrap; gap: 8px; }
    }

    @media (prefers-reduced-motion: reduce) {
      .dot.pulse { animation: none !important; }
      *, *::before, *::after { animation-duration: 0.01ms !important; animation-iteration-count: 1 !important; transition-duration: 0.01ms !important; }
    }
  `]
})
export class PaymentDashboardPageComponent implements AfterViewInit {
  private readonly api = inject(AdminApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly trendPeriod = signal<'hourly' | 'daily' | 'weekly'>('daily');
  readonly trendDays = signal<number>(7);

  private readonly refresh$ = new Subject<void>();
  private trendChart: Chart | null = null;

  readonly trendCanvas = viewChild<ElementRef<HTMLCanvasElement>>('trendCanvas');

  readonly data = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => interval(30000).pipe(
        startWith(0),
        switchMap(() => this.api.getPaymentMetricsOverview().pipe(catchError(() => of(null))))
      ))
    )
  );

  readonly anomalies = toSignal(
    this.api.getPaymentAnomalies().pipe(catchError(() => of([])))
  );

  readonly failedTxns = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => interval(60000).pipe(
        startWith(0),
        switchMap(() => this.api.getFailedTransactions(0, 10).pipe(catchError(() => of(null))))
      ))
    )
  );

  readonly trends = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => interval(60000).pipe(
        startWith(0),
        switchMap(() => this.api.getPaymentTrends(this.trendPeriod(), this.trendDays()).pipe(catchError(() => of([] as any[]))))
      ))
    )
  );

  ngAfterViewInit() {
    this.rebuildTrendChart();
  }

  rebuildTrendChart() {
    if (this.trendChart) {
      this.trendChart.destroy();
      this.trendChart = null;
    }

    this.api.getPaymentTrends(this.trendPeriod(), this.trendDays()).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(trends => this.renderChart(trends));
  }

  private renderChart(trends: any[]) {
    const canvas = this.trendCanvas();
    if (!canvas || !trends?.length) return;

    const ctx = canvas.nativeElement.getContext('2d');
    if (!ctx) return;

    const computedStyle = getComputedStyle(document.documentElement);
    const brand = computedStyle.getPropertyValue('--brand').trim() || '#F97316';
    const ink = computedStyle.getPropertyValue('--ink').trim() || '#1a1a2e';
    const muted = computedStyle.getPropertyValue('--muted').trim() || '#94a3b8';
    const line = computedStyle.getPropertyValue('--line').trim() || '#e2e8f0';

    const labels = trends.map((t: any) => {
      const d = new Date(t.timestamp);
      if (this.trendPeriod() === 'hourly') return d.toLocaleTimeString([], { hour: '2-digit' });
      if (this.trendPeriod() === 'weekly') return 'W' + this.getWeekNumber(d);
      return d.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
    });

    this.trendChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Success Rate %',
            data: trends.map((t: any) => t.successRate),
            borderColor: brand,
            backgroundColor: brand + '20',
            fill: true,
            tension: 0.4,
            pointRadius: 3,
            pointHoverRadius: 5,
            pointBackgroundColor: brand,
          },
          {
            label: 'Total Transactions',
            data: trends.map((t: any) => t.total),
            borderColor: muted,
            backgroundColor: 'transparent',
            borderDash: [4, 4],
            tension: 0.4,
            pointRadius: 0,
            yAxisID: 'y1',
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: true, position: 'top', labels: { color: ink, usePointStyle: true, boxWidth: 8, font: { size: 11 } } } },
        scales: {
          x: { grid: { color: line }, ticks: { color: muted, font: { size: 10 }, maxRotation: 45 } },
          y: { beginAtZero: true, max: 100, grid: { color: line }, ticks: { color: muted, font: { size: 10 }, callback: (v) => v + '%' } },
          y1: { position: 'right', grid: { display: false }, ticks: { color: muted, font: { size: 10 } } }
        }
      }
    });
  }

  private getWeekNumber(d: Date): number {
    const start = new Date(d.getFullYear(), 0, 1);
    return Math.ceil(((d.getTime() - start.getTime()) / 86400000 + start.getDay() + 1) / 7);
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }
}
