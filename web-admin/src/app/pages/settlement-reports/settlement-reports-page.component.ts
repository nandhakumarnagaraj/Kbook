import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency, formatDate } from '../../shared/formatters';

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
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:1rem;flex-wrap:wrap;">
          <div>
            <h2>Settlement Reports</h2>
            <p class="muted">Per-restaurant settlement summaries and platform commission details. Full settlement data is available in the Easebuzz dashboard.</p>
            <div class="hero-meta">
              <span class="chip">Admin Access</span>
              <span class="chip success">Settlements</span>
              <span class="chip warn">CSV export coming soon</span>
            </div>
          </div>
          <a href="https://dashboard.easebuzz.in" target="_blank" rel="noopener noreferrer" style="display:inline-flex;align-items:center;gap:.4rem;padding:.5rem 1rem;border-radius:999px;background:rgba(29,123,95,.1);color:var(--accent);text-decoration:none;font-weight:700;font-size:.85rem;border:1px solid rgba(29,123,95,.2);white-space:nowrap;">🔗 Open Easebuzz Dashboard</a>
        </div>
      </section>

      <ng-template #loading>
        <div class="panel loading">{{ loadError() || 'Loading settlements...' }}</div>
      </ng-template>

      <div *ngIf="settlements().length; else loading">
        <div class="stats-grid">
          <article class="panel stat-card">
            <h3>Total Settled</h3>
            <strong>{{ formatAmount(totalSettled()) }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Total Commission</h3>
            <strong>{{ formatAmount(totalCommission()) }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Total Orders</h3>
            <strong>{{ totalOrders() }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Restaurants</h3>
            <strong>{{ settlements().length }}</strong>
          </article>
        </div>

        <div class="card-grid">
          <article class="panel settlement-card" *ngFor="let s of settlements()">
            <div class="card-header">
              <h3>🏪 {{ s.shopName }}</h3>
              <span class="muted">#{{ s.restaurantId }}</span>
            </div>
            <div class="card-metrics">
              <div class="metric-item">
                <span class="metric-label">Settled</span>
                <strong>{{ formatAmount(s.totalSettled) }}</strong>
              </div>
              <div class="metric-item">
                <span class="metric-label">Commission</span>
                <strong>{{ formatAmount(s.totalCommission) }}</strong>
              </div>
              <div class="metric-item">
                <span class="metric-label">Orders</span>
                <strong>{{ s.orderCount }}</strong>
              </div>
              <div class="metric-item">
                <span class="metric-label">Last Settlement</span>
                <span>{{ formatDateVal(s.lastSettlementDate) }}</span>
              </div>
            </div>
          </article>
        </div>
      </div>

      <div class="panel loading" *ngIf="!settlements().length && loaded()">
        <span class="empty-icon">📭</span>
        <p>{{ loadError() || 'No settlement data available.' }}</p>
      </div>
    </div>
  `,
  styles: [`
    .card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
      gap: 1rem;
    }
    .settlement-card {
      padding: 1.25rem;
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1rem;
    }
    .card-header h3 {
      margin: 0;
      font-size: 1.05rem;
    }
    .card-metrics {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.85rem;
    }
    .metric-item {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }
    .metric-label {
      font-size: 0.78rem;
      color: var(--muted);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .metric-item strong {
      font-size: 1.1rem;
    }
    .metric-item span:last-child {
      font-size: 0.88rem;
      color: var(--muted);
    }
    .empty-icon {
      font-size: 2.5rem;
      display: block;
      margin-bottom: 0.5rem;
    }
    @media (max-width: 720px) {
      .card-grid {
        grid-template-columns: 1fr;
      }
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
        this.settlements.set(data);
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
