import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-customer-cdp-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatChipsModule, MatTableModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row"><div><h1 class="page-title">Customer Data Platform</h1><p class="page-subtitle">Unified customer profiles, segments, and churn risk analysis.</p></div></div>
      @if (insights(); as i) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon green">people</mat-icon><mat-card-title>{{ i.totalCustomers }}</mat-card-title><mat-card-subtitle>Total Customers</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon blue">replay</mat-icon><mat-card-title>{{ i.repeatCustomers }}</mat-card-title><mat-card-subtitle>Repeat Customers</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">percent</mat-icon><mat-card-title>{{ i.retentionRate }}%</mat-card-title><mat-card-subtitle>Retention Rate</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon green">payments</mat-icon><mat-card-title>{{ formatCurrencyValue(i.averageLtv) }}</mat-card-title><mat-card-subtitle>Average LTV</mat-card-subtitle></mat-card-header></mat-card>
        </div>
        <div class="two-col">
          <mat-card class="segments-card">
            <mat-card-header><mat-card-title>Customer Segments</mat-card-title></mat-card-header>
            <mat-card-content>
              <div class="segment-grid">
                @for (key of objectKeys(i.segments); track key) {
                  <div class="segment-item"><span class="segment-name">{{ key }}</span><span class="segment-count">{{ i.segments[key] }}</span></div>
                }
              </div>
            </mat-card-content>
          </mat-card>
          <mat-card class="top-card">
            <mat-card-header><mat-card-title>Top Customers</mat-card-title></mat-card-header>
            <mat-card-content>
              <div class="top-list">
                @for (c of i.topCustomers; track c.displayName) {
                  <div class="top-item">
                    <div class="top-name">{{ c.displayName }}</div>
                    <div class="top-meta">{{ c.totalOrders }} orders · {{ formatCurrencyValue(c.totalSpend) }}</div>
                    <span class="segment-badge" [class]="c.segment">{{ c.segment }}</span>
                  </div>
                }
              </div>
            </mat-card-content>
          </mat-card>
        </div>
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
    .two-col{display:grid;grid-template-columns:1fr 1.5fr;gap:24px}
    .segments-card,.top-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}
    .segment-grid{display:flex;gap:12px;flex-wrap:wrap}
    .segment-item{padding:16px 24px;border-radius:12px;background:var(--bg-elevated);text-align:center}
    .segment-name{display:block;font-size:0.75rem;color:var(--muted);text-transform:uppercase;margin-bottom:4px}
    .segment-count{font-size:1.3rem;font-weight:700;color:var(--ink)}
    .top-list{display:flex;flex-direction:column;gap:8px;max-height:400px;overflow-y:auto}
    .top-item{display:flex;align-items:center;gap:12px;padding:10px 12px;border-radius:10px;transition:background 0.15s}
    .top-item:hover{background:var(--bg-elevated)}
    .top-name{font-weight:600;color:var(--ink);flex:1}.top-meta{font-size:0.8rem;color:var(--muted)}
    .segment-badge{padding:2px 10px;border-radius:999px;font-size:0.7rem;font-weight:600;background:var(--brand-soft);color:var(--brand)}
    .loading{display:flex;justify-content:center;padding:60px}
    @media(max-width:960px){.two-col{grid-template-columns:1fr}}
  `]
})
export class CustomerCdpPageComponent {
  private readonly api = inject(AdminApiService);
  readonly insights = toSignal(this.api.getCustomerInsights().pipe(catchError(() => of(null))));
  objectKeys(obj: any): string[] { return obj ? Object.keys(obj) : []; }
  formatCurrencyValue(v: number): string { return formatCurrency(v); }
}
