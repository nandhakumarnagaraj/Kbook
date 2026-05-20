import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { formatCurrency } from '../../shared/formatters';

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
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>💳 Payment Dashboard</h2>
        <p class="muted">Real-time payment gateway analytics and transaction insights</p>
        <div class="hero-meta">
          <span class="chip">Easebuzz</span>
          <span class="chip success">Live</span>
          <span class="chip">Gateway Health</span>
        </div>
      </section>

      <div *ngIf="data() as d; else loading">
        <div class="stat-grid">
          <article class="panel stat-card">
            <div class="stat-icon">📊</div>
            <div class="stat-body">
              <span class="stat-label">Total Transactions</span>
              <span class="stat-value">{{ d.totalTransactions }}</span>
            </div>
          </article>
          <article class="panel stat-card">
            <div class="stat-icon">✅</div>
            <div class="stat-body">
              <span class="stat-label">Success Rate</span>
              <span class="stat-value">{{ d.successRate }}%</span>
              <div class="progress-track">
                <div class="progress-fill" [style.width.%]="d.successRate"></div>
              </div>
            </div>
          </article>
          <article class="panel stat-card">
            <div class="stat-icon">💰</div>
            <div class="stat-body">
              <span class="stat-label">Today's Revenue</span>
              <span class="stat-value">{{ formatCurrencyValue(d.todayRevenue) }}</span>
            </div>
          </article>
          <article class="panel stat-card">
            <div class="stat-icon">📦</div>
            <div class="stat-body">
              <span class="stat-label">Today's Orders</span>
              <span class="stat-value">{{ d.todayOrders }}</span>
            </div>
          </article>
        </div>

        <div class="detail-grid">
          <section class="panel">
            <div class="section-head">
              <h3>Payment Method Breakdown</h3>
            </div>
            <div class="table-wrap" *ngIf="d.paymentMethods.length; else noMethods">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Method</th>
                    <th>Count</th>
                    <th>Success Rate</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let pm of d.paymentMethods">
                    <td>{{ pm.method }}</td>
                    <td>{{ pm.count }}</td>
                    <td>
                      <span class="chip" [class.success]="pm.successRate >= 90" [class.warn]="pm.successRate < 90 && pm.successRate >= 70" [class.danger]="pm.successRate < 70">
                        {{ pm.successRate }}%
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <ng-template #noMethods>
              <div class="panel loading">No payment method data available.</div>
            </ng-template>
          </section>

          <section class="panel">
            <div class="section-head">
              <h3>Easebuzz Gateway Health</h3>
            </div>
            <div class="easebuzz-card">
              <div class="easebuzz-icon">🔌</div>
              <div class="easebuzz-info">
                <span class="stat-label">Gateway Success Rate</span>
                <span class="stat-value easebuzz-pct">{{ d.easebuzzSuccessRate }}%</span>
              </div>
              <div class="progress-track large">
                <div class="progress-fill easebuzz-fill" [style.width.%]="d.easebuzzSuccessRate"></div>
              </div>
              <p class="muted" style="margin: 0.5rem 0 0;">
                <span *ngIf="d.easebuzzSuccessRate >= 95">✅ Gateway is healthy</span>
                <span *ngIf="d.easebuzzSuccessRate < 95 && d.easebuzzSuccessRate >= 80">⚠️ Gateway needs attention</span>
                <span *ngIf="d.easebuzzSuccessRate < 80">🚨 Gateway critical — investigate immediately</span>
              </p>
            </div>
          </section>
        </div>
      </div>

      <ng-template #loading>
        <div class="load-state">
          <div class="spinner"></div>
          <p>Loading payment dashboard...</p>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .stat-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:1rem; margin-top:1.5rem; }
    .stat-card { display:flex; align-items:center; gap:1rem; padding:1.25rem; border-radius:16px; }
    .stat-icon { font-size:2rem; line-height:1; }
    .stat-body { display:flex; flex-direction:column; flex:1; }
    .stat-label { font-size:.78rem; color:var(--muted); text-transform:uppercase; letter-spacing:.04em; }
    .stat-value { font-size:1.6rem; font-weight:800; line-height:1.2; }
    .progress-track { height:6px; border-radius:999px; background:var(--line); margin-top:.5rem; overflow:hidden; }
    .progress-fill { height:100%; border-radius:999px; background:var(--accent); transition:width .5s ease; }
    .progress-track.large { height:10px; }
    .easebuzz-fill { background:linear-gradient(90deg,var(--brand),var(--accent)); }
    .easebuzz-pct { color:var(--brand); }
    .detail-grid { display:grid; grid-template-columns:1fr 1fr; gap:1rem; margin-top:1rem; }
    .easebuzz-card { display:flex; flex-direction:column; gap:.75rem; padding:.5rem 0; }
    .easebuzz-icon { font-size:2.5rem; }
    .easebuzz-info { display:flex; justify-content:space-between; align-items:center; gap:.75rem; flex-wrap:wrap; }
    .section-head { display:flex; justify-content:space-between; align-items:center; margin-bottom:.75rem; }
    .section-head h3 { margin:0; font-size:1rem; }
    .table-wrap { overflow-x:auto; }
    .chip.success { background:rgba(29,123,95,.12); color:var(--accent); }
    .chip.warn { background: var(--warn-soft); color: var(--warn); }
    .chip.danger { background:rgba(166,55,47,.12); color:var(--danger); }
    .load-state { text-align:center; padding:3rem; color:var(--muted); }
    .spinner { width:24px; height:24px; border:3px solid var(--line); border-top-color:var(--brand); border-radius:50%; animation:spin .7s linear infinite; margin:0 auto .75rem; }
    .panel.loading { padding:2rem; text-align:center; color:var(--muted); }
    @media (max-width: 720px) {
      .detail-grid { grid-template-columns:1fr; }
      .stat-grid { margin-top: 1rem; }
      .stat-card { align-items: flex-start; }
    }
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
