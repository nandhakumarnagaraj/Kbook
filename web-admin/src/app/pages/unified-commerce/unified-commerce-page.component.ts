import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-unified-commerce-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row"><div><h1 class="page-title">Unified Commerce Hub</h1><p class="page-subtitle">Cross-channel operations: POS, Swiggy, and Zomato in one view.</p></div></div>
      @if (dashboard(); as d) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon green">shopping_bag</mat-icon><mat-card-title>{{ d.today.total }}</mat-card-title><mat-card-subtitle>Today's Orders (All Channels)</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">payments</mat-icon><mat-card-title>{{ formatCurrencyValue(d.todayRevenue.total) }}</mat-card-title><mat-card-subtitle>Today's Revenue</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon blue">receipt_long</mat-icon><mat-card-title>{{ d.allTime.total }}</mat-card-title><mat-card-subtitle>All-Time Orders</mat-card-subtitle></mat-card-header></mat-card>
        </div>
        <mat-card class="channel-card">
          <mat-card-header><mat-card-title>Channel Breakdown</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="channel-grid">
              @for (ch of d.channelBreakdown; track ch.channel) {
                <div class="channel-item">
                  <div class="channel-name">{{ ch.channel }}</div>
                  <div class="channel-today">{{ ch.todayOrders }} today</div>
                  <div class="channel-total">{{ ch.totalOrders }} total</div>
                </div>
              }
            </div>
          </mat-card-content>
        </mat-card>
      } @else { <div class="loading"><mat-spinner diameter="40"></mat-spinner></div> }
    </div>
  `,
  styles: [`
    .page-container{padding:24px;max-width:1400px;margin:0 auto}
    .header-row{margin-bottom:24px}.page-title{margin:0 0 4px;font-size:1.75rem;font-weight:700;color:var(--ink)}.page-subtitle{margin:0;color:var(--muted);font-size:0.85rem}
    .stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;margin-bottom:24px}
    .stat-card{border-radius:12px;border:none;box-shadow:0 4px 16px rgba(0,0,0,0.05)}
    ::ng-deep .stat-card .mat-mdc-card-header{padding:12px 16px!important;gap:12px!important}
    ::ng-deep .stat-card .mat-mdc-card-title{font-size:1.25rem!important;font-weight:700!important;margin:0!important}
    ::ng-deep .stat-card .mat-mdc-card-subtitle{font-size:0.8rem!important;color:var(--muted)!important;margin-top:2px!important}
    .stat-icon{width:36px;height:36px;border-radius:8px;font-size:18px;display:flex;align-items:center;justify-content:center}
    .green{background:var(--success-bg);color:var(--success)}.amber{background:var(--warn-bg);color:var(--warn)}.blue{background:var(--info-bg);color:var(--info)}
    .channel-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}
    .channel-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-top:16px}
    .channel-item{padding:24px;border-radius:16px;background:var(--bg-elevated);text-align:center}
    .channel-name{font-size:1rem;font-weight:700;color:var(--ink);margin-bottom:8px}
    .channel-today{font-size:1.5rem;font-weight:700;color:var(--brand);margin-bottom:4px}
    .channel-total{font-size:0.8rem;color:var(--muted)}
    .loading{display:flex;justify-content:center;padding:60px}
    @media(max-width:768px){.channel-grid{grid-template-columns:1fr}}
  `]
})
export class UnifiedCommercePageComponent {
  private readonly api = inject(AdminApiService);
  readonly dashboard = toSignal(this.api.getUnifiedDashboard().pipe(catchError(() => of(null))));
  formatCurrencyValue(v: number): string { return formatCurrency(v); }
}
