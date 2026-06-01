import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';

interface Settlement {
  restaurantId: number;
  shopName: string;
  totalSettled: number;
  totalCommission: number;
  orderCount: number;
  lastSettlementDate: number;
}

@Component({
  selector: 'app-settlement-reports-page',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title text-balance">Settlement Reports</h1>
          <p class="page-subtitle">Per-restaurant settlement summaries and platform commission details.</p>
        </div>
        <div class="header-right">
          <a mat-stroked-button color="accent" href="https://dashboard.easebuzz.in" target="_blank" rel="noopener noreferrer">
            <mat-icon>open_in_new</mat-icon>
            Easebuzz Dashboard
          </a>
          <button mat-flat-button color="primary" (click)="loadSettlements()">
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>
        </div>
      </div>

      <div class="stats-grid" *ngIf="loaded(); else loading">
        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon settled">account_balance_wallet</mat-icon>
            <mat-card-title>{{ formatAmount(totalSettled()) }}</mat-card-title>
            <mat-card-subtitle>Total Settled</mat-card-subtitle>
          </mat-card-header>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon commission">percent</mat-icon>
            <mat-card-title>{{ formatAmount(totalCommission()) }}</mat-card-title>
            <mat-card-subtitle>Total Commission</mat-card-subtitle>
          </mat-card-header>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon orders">shopping_bag</mat-icon>
            <mat-card-title>{{ totalOrders() }}</mat-card-title>
            <mat-card-subtitle>Total Orders</mat-card-subtitle>
          </mat-card-header>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon restaurants">storefront</mat-icon>
            <mat-card-title>{{ settlements().length }}</mat-card-title>
            <mat-card-subtitle>Restaurants</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
      </div>

      <div class="card-grid" *ngIf="settlements().length; else empty">
        <mat-card class="settlement-card" *ngFor="let s of settlements()">
          <mat-card-header>
            <mat-icon mat-card-avatar color="primary">store</mat-icon>
            <mat-card-title>{{ s.shopName }}</mat-card-title>
            <mat-card-subtitle>ID: #{{ s.restaurantId }}</mat-card-subtitle>
          </mat-card-header>
          
          <mat-divider></mat-divider>
          
          <mat-card-content>
            <div class="card-metrics">
              <div class="metric-item">
                <span class="metric-label">Settled</span>
                <span class="metric-value">{{ formatAmount(s.totalSettled) }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Commission</span>
                <span class="metric-value">{{ formatAmount(s.totalCommission) }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Orders</span>
                <span class="metric-value">{{ s.orderCount }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">Last Settlement</span>
                <span class="metric-value date">{{ formatDateVal(s.lastSettlementDate) }}</span>
              </div>
            </div>
          </mat-card-content>
          
          <mat-card-footer>
             <button mat-button color="primary" class="full-width">View Details</button>
          </mat-card-footer>
        </mat-card>
      </div>

      <ng-template #loading>
        <div class="state-container">
          <mat-spinner diameter="40"></mat-spinner>
          <p>Gathering settlement data...</p>
        </div>
      </ng-template>

      <ng-template #empty>
        <div class="state-container" *ngIf="loaded()">
          <mat-icon class="empty-icon">history_toggle_off</mat-icon>
          <h3>No Settlements Yet</h3>
          <p>{{ loadError() || 'There is no settlement data available to display at this time.' }}</p>
          <button mat-stroked-button (click)="loadSettlements()">Check Again</button>
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
      font-size: 1.75rem;
      font-weight: 700;
      color: var(--ink);
    }

    .page-subtitle {
      margin: 4px 0 0;
      color: var(--muted);
      font-size: 0.9rem;
    }

    .header-right {
      display: flex;
      gap: 12px;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 24px;
      margin-bottom: 32px;
    }

    .stat-card {
      border-radius: 16px;
      border: none;
      box-shadow: 0 4px 20px rgba(0,0,0,0.05);
    }

    .stat-icon {
      width: 44px;
      height: 44px;
      line-height: 44px;
      text-align: center;
      border-radius: 12px;
      font-size: 22px;
    }

    .settled { background: var(--info-bg); color: var(--info); }
    .commission { background: var(--warn-bg); color: var(--warn); }
    .orders { background: var(--success-bg); color: var(--success); }
    .restaurants { background: var(--purple-soft); color: var(--purple); }

    .card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 24px;
    }

    .settlement-card {
      border-radius: 12px;
      border: 1px solid var(--line);
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }

    .settlement-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 8px 24px rgba(0,0,0,0.1);
    }

    .card-metrics {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
      padding: 20px 0;
    }

    .metric-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .metric-label {
      font-size: 0.7rem;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--muted);
      font-weight: 600;
    }

    .metric-value {
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--ink);
      font-variant-numeric: tabular-nums;
    }

    .metric-value.date {
      font-size: 0.9rem;
      color: var(--muted);
    }

    .full-width {
      width: 100%;
    }

    .state-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 400px;
      text-align: center;
      color: var(--muted);
    }

    .empty-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      margin-bottom: 16px;
      opacity: 0.5;
    }

    @media (max-width: 600px) {
      .header-row { flex-direction: column; gap: 16px; }
      .header-right { width: 100%; }
      .header-right button, .header-right a { flex: 1; }
    }
  `]
})
export class SettlementReportsPageComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly settlements = signal<Settlement[]>([]);
  readonly loaded = signal(false);
  readonly loadError = signal('');

  readonly totalSettled = computed(() => this.settlements().reduce((sum, s) => sum + s.totalSettled, 0));
  readonly totalCommission = computed(() => this.settlements().reduce((sum, s) => sum + s.totalCommission, 0));
  readonly totalOrders = computed(() => this.settlements().reduce((sum, s) => sum + s.orderCount, 0));

  ngOnInit(): void {
    this.loadSettlements();
  }

  loadSettlements(): void {
    this.loaded.set(false);
    this.loadError.set('');
    this.api.getSettlements().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        // Map AdminSettlement → local Settlement UI shape
        const mapped: Settlement[] = data.map(s => ({
          restaurantId: s.restaurantId,
          shopName: s.shopName ?? 'Unknown',
          totalSettled: s.amount,
          totalCommission: 0,   // not returned by this endpoint — shown as N/A
          orderCount: 0,        // not returned by this endpoint — shown as N/A
          lastSettlementDate: s.createdAt
        }));
        this.settlements.set(mapped);
        this.loaded.set(true);
      },
      error: (err) => {
        this.settlements.set([]);
        this.loadError.set(err?.error?.error ?? err?.error?.message ?? 'Failed to load settlements.');
        this.loaded.set(true);
      }
    });
  }

  formatAmount(value: number): string {
    return formatCurrency(value);
  }

  formatDateVal(value: number): string {
    return formatDate(value);
  }
}
