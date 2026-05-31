import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-payment-routing-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatChipsModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row"><div><h1 class="page-title">Payment Routing</h1><p class="page-subtitle">Smart recommendations to maximize payment success rates.</p></div></div>
      @if (data(); as d) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon green">check_circle</mat-icon><mat-card-title>{{ d.currentSuccessRate24h }}%</mat-card-title><mat-card-subtitle>24h Success Rate</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon blue">query_stats</mat-icon><mat-card-title>{{ d.currentSuccessRate7d }}%</mat-card-title><mat-card-subtitle>7d Success Rate</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">receipt_long</mat-icon><mat-card-title>{{ d.totalTransactions24h }}</mat-card-title><mat-card-subtitle>24h Transactions</mat-card-subtitle></mat-card-header></mat-card>
        </div>
        <mat-card class="rec-card">
          <mat-card-header><mat-card-title>Recommendations</mat-card-title></mat-card-header>
          <mat-card-content>
            @for (rec of d.recommendations; track rec.type) {
              <div class="rec-item" [class.critical]="rec.severity === 'critical'" [class.warning]="rec.severity === 'warning'" [class.ok]="rec.type === 'ok'">
                <mat-icon>{{ rec.type === 'ok' ? 'check_circle' : (rec.severity === 'critical' ? 'error' : 'warning') }}</mat-icon>
                <span>{{ rec.message }}</span>
              </div>
            }
          </mat-card-content>
        </mat-card>
      } @else { <div class="loading"><mat-spinner diameter="40"></mat-spinner></div> }
    </div>
  `,
  styles: [`
    .page-container{padding:24px;max-width:1400px;margin:0 auto}
    .header-row{margin-bottom:24px}.page-title{margin:0 0 4px;font-size:1.75rem;font-weight:700;color:var(--ink)}.page-subtitle{margin:0;color:var(--muted);font-size:0.85rem}
    .stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px;margin-bottom:24px}
    .stat-card{border-radius:12px;border:none;box-shadow:0 4px 16px rgba(0,0,0,0.05)}
    ::ng-deep .stat-card .mat-mdc-card-header{padding:12px 16px!important;gap:12px!important}
    ::ng-deep .stat-card .mat-mdc-card-title{font-size:1.25rem!important;font-weight:700!important;margin:0!important}
    ::ng-deep .stat-card .mat-mdc-card-subtitle{font-size:0.8rem!important;color:var(--muted)!important;margin-top:2px!important}
    .stat-icon{width:36px;height:36px;border-radius:8px;font-size:18px;display:flex;align-items:center;justify-content:center}
    .green{background:#dcfce7;color:#16a34a}.amber{background:#fef3c7;color:#d97706}.blue{background:#e0f2fe;color:#0284c7}
    .rec-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}
    .rec-item{display:flex;align-items:center;gap:12px;padding:12px 16px;border-radius:10px;margin-bottom:8px;font-size:0.9rem}
    .rec-item.critical{background:#fef2f2;color:#dc2626}.rec-item.warning{background:#fffbeb;color:#d97706}.rec-item.ok{background:#f0fdf4;color:#16a34a}
    .loading{display:flex;justify-content:center;padding:60px}
  `]
})
export class PaymentRoutingPageComponent {
  private readonly api = inject(AdminApiService);
  readonly data = toSignal(this.api.getPaymentRoutingRecommendations().pipe(catchError(() => of(null))));
}
