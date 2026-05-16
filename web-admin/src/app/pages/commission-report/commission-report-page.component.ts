import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { formatCurrency } from '../../shared/formatters';

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
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>📈 Commission Report</h2>
        <p class="muted">Platform commission earnings across all restaurants</p>
        <div class="hero-meta">
          <span class="chip">Admin Access</span>
          <span class="chip success">Financial Report</span>
          <span class="chip">Platform Earnings</span>
        </div>
      </section>

      <div *ngIf="loaded(); else loading">
        <div class="summary-grid">
          <article class="panel summary-card">
            <span class="summary-icon">💰</span>
            <div>
              <span class="summary-label">Total Commission</span>
              <span class="summary-value accent">{{ formatCurrencyValue(summary().totalCommission) }}</span>
            </div>
          </article>
          <article class="panel summary-card">
            <span class="summary-icon">🏪</span>
            <div>
              <span class="summary-label">Total Revenue</span>
              <span class="summary-value">{{ formatCurrencyValue(summary().totalRevenue) }}</span>
            </div>
          </article>
          <article class="panel summary-card">
            <span class="summary-icon">📊</span>
            <div>
              <span class="summary-label">Avg Effective Rate</span>
              <span class="summary-value brand">{{ summary().avgRate }}%</span>
            </div>
          </article>
        </div>

        <section class="panel">
          <div class="section-head">
            <h3>Commission by Restaurant</h3>
            <p class="muted">{{ rows().length }} restaurants</p>
          </div>
          <div class="table-wrap" *ngIf="rows().length; else noData">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Restaurant Name</th>
                  <th>Total Revenue</th>
                  <th>Commission Earned</th>
                  <th>Effective Rate</th>
                  <th>Order Count</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let r of rows()">
                  <td><strong>{{ r.restaurantName }}</strong></td>
                  <td>{{ formatCurrencyValue(r.totalRevenue) }}</td>
                  <td>{{ formatCurrencyValue(r.commissionEarned) }}</td>
                  <td><span class="chip" [class.success]="r.effectiveRate >= 5" [class.warn]="r.effectiveRate < 5 && r.effectiveRate >= 2" [class.danger]="r.effectiveRate < 2">{{ r.effectiveRate }}%</span></td>
                  <td>{{ r.orderCount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <ng-template #noData>
            <div class="panel loading">No commission data available yet.</div>
          </ng-template>
        </section>
      </div>

      <ng-template #loading>
        <div class="load-state">
          <div class="spinner"></div>
          <p>Loading commission report...</p>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .summary-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:1rem; margin-top:1.5rem; }
    .summary-card { display:flex; align-items:center; gap:1rem; padding:1.25rem; border-radius:16px; }
    .summary-icon { font-size:2rem; line-height:1; }
    .summary-label { display:block; font-size:.78rem; color:var(--muted); text-transform:uppercase; letter-spacing:.04em; margin-bottom:.15rem; }
    .summary-value { font-size:1.5rem; font-weight:800; }
    .summary-value.accent { color:var(--accent); }
    .summary-value.brand { color:var(--brand); }
    .section-head { display:flex; justify-content:space-between; align-items:center; gap:.75rem; flex-wrap:wrap; margin-bottom:.75rem; }
    .section-head h3 { margin:0; font-size:1rem; }
    .section-head p { margin:0; }
    .table-wrap { overflow-x:auto; }
    .chip.success { background:rgba(29,123,95,.12); color:var(--accent); }
    .chip.warn { background: var(--warn-soft); color: var(--warn); }
    .chip.danger { background:rgba(166,55,47,.12); color:var(--danger); }
    .load-state { text-align:center; padding:3rem; color:var(--muted); }
    .spinner { width:24px; height:24px; border:3px solid var(--line); border-top-color:var(--brand); border-radius:50%; animation:spin .7s linear infinite; margin:0 auto .75rem; }
    @keyframes spin { to { transform:rotate(360deg); } }
    .panel.loading { padding:2rem; text-align:center; color:var(--muted); }
    @media (max-width: 720px) {
      .summary-grid { margin-top: 1rem; }
      .summary-card { align-items: flex-start; }
    }
  `]
})
export class CommissionReportPageComponent {
  private readonly api = inject(AdminApiService);

  readonly rawData = signal<any[]>([]);
  readonly loaded = signal(false);

  readonly summary = signal<CommissionSummary>({ totalCommission: 0, totalRevenue: 0, avgRate: 0 });
  readonly rows = signal<CommissionRow[]>([]);

  constructor() {
    this.api.getCommissionReport().subscribe({
      next: (data: any) => {
        this.rawData.set(data);
        this.loaded.set(true);
        this.processData(data);
      },
      error: () => {
        this.loaded.set(true);
      }
    });
  }

  private processData(data: any[]): void {
    const rows: CommissionRow[] = data.map((item: any) => ({
      restaurantName: item.restaurantName ?? item.shopName ?? 'Unknown',
      totalRevenue: item.totalRevenue ?? 0,
      commissionEarned: item.commissionEarned ?? 0,
      effectiveRate: item.effectiveRate ?? 0,
      orderCount: item.orderCount ?? 0
    }));

    const totalCommission = rows.reduce((s, r) => s + r.commissionEarned, 0);
    const totalRevenue = rows.reduce((s, r) => s + r.totalRevenue, 0);
    const avgRate = totalRevenue > 0 ? parseFloat(((totalCommission / totalRevenue) * 100).toFixed(2)) : 0;

    this.rows.set(rows);
    this.summary.set({ totalCommission, totalRevenue, avgRate });
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }
}
