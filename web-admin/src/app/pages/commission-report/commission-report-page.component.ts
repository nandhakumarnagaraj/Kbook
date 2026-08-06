import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal, ViewChild, AfterViewInit } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { CommissionReport } from '../../core/models/api.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatTooltipModule } from '@angular/material/tooltip';

interface CommissionRow {
  restaurantName: string;
  totalRevenue: number;
  commissionEarned: number;
  effectiveRate: number;
  orderCount: number;
}

interface CommissionSummary {
  totalCommission: number;
  totalRevenue: number;
  avgRate: number;
}

@Component({
  selector: 'app-commission-report-page',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatTableModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatSortModule,
    MatTooltipModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">Commission Report</h1>
          <p class="page-subtitle">Platform commission earnings across all registered restaurants.</p>
        </div>
        <button mat-flat-button color="primary" (click)="loadReport()">
          <mat-icon>refresh</mat-icon>
          Refresh Data
        </button>
      </div>

      <div *ngIf="loaded(); else loading">
        <div class="stats-grid">
          <mat-card class="stat-card commission">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">payments</mat-icon>
              <mat-card-title>{{ formatCurrencyValue(summary().totalCommission) }}</mat-card-title>
              <mat-card-subtitle>Total Commission</mat-card-subtitle>
            </mat-card-header>
          </mat-card>

          <mat-card class="stat-card revenue">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">store</mat-icon>
              <mat-card-title>{{ formatCurrencyValue(summary().totalRevenue) }}</mat-card-title>
              <mat-card-subtitle>Total Gross Revenue</mat-card-subtitle>
            </mat-card-header>
          </mat-card>

          <mat-card class="stat-card rate">
            <mat-card-header>
              <mat-icon mat-card-avatar class="stat-icon">percent</mat-icon>
              <mat-card-title>{{ summary().avgRate }}%</mat-card-title>
              <mat-card-subtitle>Avg. Effective Rate</mat-card-subtitle>
            </mat-card-header>
          </mat-card>
        </div>

        <mat-card class="table-card mat-elevation-z2">
          <mat-card-header>
            <mat-card-title>Earnings by Restaurant</mat-card-title>
            <mat-card-subtitle>{{ dataSource.data.length }} restaurants processed</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="table-container" *ngIf="dataSource.data.length; else noData">
              <table mat-table [dataSource]="dataSource" matSort>
                <ng-container matColumnDef="restaurantName">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Restaurant </th>
                  <td mat-cell *matCellDef="let r"> <strong>{{ r.restaurantName }}</strong> </td>
                </ng-container>

                <ng-container matColumnDef="totalRevenue">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Revenue </th>
                  <td mat-cell *matCellDef="let r"> {{ formatCurrencyValue(r.totalRevenue) }} </td>
                </ng-container>

                <ng-container matColumnDef="commissionEarned">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Commission </th>
                  <td mat-cell *matCellDef="let r"> {{ formatCurrencyValue(r.commissionEarned) }} </td>
                </ng-container>

                <ng-container matColumnDef="effectiveRate">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Rate </th>
                  <td mat-cell *matCellDef="let r">
                    <span class="rate-chip" [class.good]="r.effectiveRate >= 5" [class.warn]="r.effectiveRate < 5 && r.effectiveRate >= 2" [class.danger]="r.effectiveRate < 2">
                      {{ r.effectiveRate }}%
                    </span>
                  </td>
                </ng-container>

                <ng-container matColumnDef="orderCount">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Orders </th>
                  <td mat-cell *matCellDef="let r"> {{ r.orderCount }} </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
              </table>
            </div>
            <ng-template #noData>
              <div class="no-data">No commission data available yet.</div>
            </ng-template>
          </mat-card-content>
        </mat-card>
      </div>

      <ng-template #loading>
        <div class="loading-container" *ngIf="!loadError(); else errorState">
          <mat-spinner diameter="40"></mat-spinner>
          <p>Analyzing commission data...</p>
        </div>
        <ng-template #errorState>
          <div class="error-container">
            <mat-icon color="warn">error</mat-icon>
            <p>{{ loadError() }}</p>
            <button mat-button color="primary" (click)="loadReport()">Try Again</button>
          </div>
        </ng-template>
      </ng-template>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
    .page-title { margin: 0; font-size: 1.75rem; font-weight: 700; color: var(--ink); }
    .page-subtitle { margin: 4px 0 0; color: var(--muted); font-size: 0.9rem; }

    .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 24px; margin-bottom: 32px; }
    .stat-card { border-radius: 16px; border: none; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
    .stat-icon { background: var(--brand-soft); color: var(--brand); width: 48px; height: 48px; line-height: 48px; text-align: center; border-radius: 12px; font-size: 24px; }
    .commission .stat-icon { background: var(--success-bg); color: var(--success); }
    .revenue .stat-icon { background: var(--info-bg); color: var(--info); }
    .rate .stat-icon { background: var(--purple-soft); color: var(--purple); }

    .table-card { border-radius: 16px; border: none; }
    .table-container { margin-top: 16px; }
    table { width: 100%; }

    .rate-chip { padding: 4px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 600; }
    .rate-chip.good { background: var(--success-bg); color: var(--success); }
    .rate-chip.warn { background: var(--warn-bg); color: var(--warn); }
    .rate-chip.danger { background: var(--danger-bg); color: var(--danger); }

    .loading-container, .error-container { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 400px; color: var(--muted); gap: 16px; }
    .no-data { padding: 48px; text-align: center; color: var(--muted); }

    @media (max-width: 768px) { .stats-grid { grid-template-columns: 1fr; } }
  `]
})
export class CommissionReportPageComponent implements AfterViewInit {
  private readonly api = inject(AdminApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loaded = signal(false);
  readonly loadError = signal('');
  readonly summary = signal<CommissionSummary>({ totalCommission: 0, totalRevenue: 0, avgRate: 0 });
  
  dataSource = new MatTableDataSource<CommissionRow>([]);
  displayedColumns = ['restaurantName', 'totalRevenue', 'commissionEarned', 'effectiveRate', 'orderCount'];

  @ViewChild(MatSort) sort!: MatSort;

  constructor() {
    this.loadReport();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  loadReport(): void {
    this.loaded.set(false);
    this.loadError.set('');
    this.api.getCommissionReport().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.processData(data);
        this.loaded.set(true);
      },
      error: (err) => {
        this.loadError.set(err?.error?.error ?? err?.error?.message ?? 'Failed to load commission report.');
        this.loaded.set(true);
      }
    });
  }

  private processData(data: CommissionReport[]): void {
    const rows: CommissionRow[] = data.map((item) => ({
      // CommissionReport fields: restaurantId, shopName, totalTransactions,
      // totalAmount, commissionRate, commissionEarned, period
      restaurantName: item.shopName ?? `Restaurant #${item.restaurantId}`,
      totalRevenue: item.totalAmount ?? 0,
      commissionEarned: item.commissionEarned ?? 0,
      effectiveRate: item.commissionRate ?? 0,
      orderCount: item.totalTransactions ?? 0
    }));

    const totalCommission = rows.reduce((s, r) => s + r.commissionEarned, 0);
    const totalRevenue = rows.reduce((s, r) => s + r.totalRevenue, 0);
    const avgRate = totalRevenue > 0 ? parseFloat(((totalCommission / totalRevenue) * 100).toFixed(2)) : 0;

    this.dataSource.data = rows;
    this.summary.set({ totalCommission, totalRevenue, avgRate });
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }
}
