import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-chargebacks-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatSnackBarModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row"><div><h1 class="page-title">Chargeback Prevention</h1><p class="page-subtitle">Fraud scoring, chargeback tracking, and resolution workflow.</p></div></div>
      @if (summary(); as s) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon red">gavel</mat-icon><mat-card-title>{{ s.totalChargebacks }}</mat-card-title><mat-card-subtitle>Total Chargebacks</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">payments</mat-icon><mat-card-title>{{ formatCurrencyValue(s.unresolvedAmount) }}</mat-card-title><mat-card-subtitle>Unresolved Amount</mat-card-subtitle></mat-card-header></mat-card>
        </div>
        <mat-card class="status-card">
          <mat-card-header><mat-card-title>Status Breakdown</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="status-grid">
              @for (entry of objectKeys(s.byStatus); track entry) {
                <div class="status-item"><span class="status-label">{{ entry }}</span><span class="status-count">{{ s.byStatus[entry] }}</span></div>
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
    .stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px;margin-bottom:24px}
    .stat-card{border-radius:12px;border:none;box-shadow:0 4px 16px rgba(0,0,0,0.05)}
    ::ng-deep .stat-card .mat-mdc-card-header{padding:12px 16px!important;gap:12px!important}
    ::ng-deep .stat-card .mat-mdc-card-title{font-size:1.25rem!important;font-weight:700!important;margin:0!important}
    ::ng-deep .stat-card .mat-mdc-card-subtitle{font-size:0.8rem!important;color:var(--muted)!important;margin-top:2px!important}
    .stat-icon{width:36px;height:36px;border-radius:8px;font-size:18px;display:flex;align-items:center;justify-content:center}
    .red{background:#fee2e2;color:#dc2626}.amber{background:#fef3c7;color:#d97706}
    .status-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}
    .status-grid{display:flex;gap:16px;flex-wrap:wrap}
    .status-item{padding:12px 20px;border-radius:10px;background:var(--bg-elevated);text-align:center}
    .status-label{display:block;font-size:0.75rem;color:var(--muted);text-transform:uppercase;margin-bottom:4px}
    .status-count{font-size:1.2rem;font-weight:700;color:var(--ink)}
    .loading{display:flex;justify-content:center;padding:60px}
  `]
})
export class ChargebacksPageComponent {
  private readonly api = inject(AdminApiService);
  readonly summary = toSignal(this.api.getChargebackSummary().pipe(catchError(() => of(null))));
  objectKeys(obj: any): string[] { return obj ? Object.keys(obj) : []; }
  formatCurrencyValue(v: number): string { return formatCurrency(v); }
}
