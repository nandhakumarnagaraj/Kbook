import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-instant-settlements-page',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSnackBarModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row"><div><h1 class="page-title">Instant Settlements</h1><p class="page-subtitle">Request instant settlement of your available balance.</p></div></div>
      @if (estimate(); as e) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon green">account_balance_wallet</mat-icon><mat-card-title>{{ formatCurrencyValue(e.totalSettled) }}</mat-card-title><mat-card-subtitle>Total Settled</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">percent</mat-icon><mat-card-title>{{ e.feeRate }}%</mat-card-title><mat-card-subtitle>Instant Fee Rate</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon blue">payments</mat-icon><mat-card-title>{{ formatCurrencyValue(e.netPayout) }}</mat-card-title><mat-card-subtitle>Available for Instant</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar [class.green]="e.eligible" [class.red]="!e.eligible"><mat-icon>{{ e.eligible ? 'check_circle' : 'cancel' }}</mat-icon></mat-icon><mat-card-title>{{ e.eligible ? 'Eligible' : 'Not Eligible' }}</mat-card-title><mat-card-subtitle>Min {{ formatCurrencyValue(e.minimumAmount) }}</mat-card-subtitle></mat-card-header></mat-card>
        </div>
        <mat-card class="form-card">
          <mat-card-header><mat-card-title>Request Instant Settlement</mat-card-title></mat-card-header>
          <mat-card-content>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Settlement Amount (INR)</mat-label>
              <input matInput type="number" [(ngModel)]="settlementAmount" [max]="e.totalSettled">
            </mat-form-field>
            @if (settlementAmount > 0) {
              <div class="fee-breakdown">
                <div><span>Fee ({{ e.feeRate }}%)</span><span>{{ formatCurrencyValue(settlementAmount * e.feeRate / 100) }}</span></div>
                <div class="net"><span>Net Payout</span><span>{{ formatCurrencyValue(settlementAmount - (settlementAmount * e.feeRate / 100)) }}</span></div>
              </div>
            }
            <button mat-flat-button color="primary" [disabled]="!e.eligible || settlementAmount <= 0" (click)="requestSettlement()"><mat-icon>bolt</mat-icon>Request Instant Settlement</button>
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
    .green{background:var(--success-bg);color:var(--success)}.amber{background:var(--warn-bg);color:var(--warn)}.blue{background:var(--info-bg);color:var(--info)}.red{background:var(--danger-bg);color:var(--danger)}
    .form-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}.full-width{width:100%}
    .fee-breakdown{display:flex;flex-direction:column;gap:8px;margin:16px 0;padding:16px;border-radius:12px;background:var(--bg-elevated);font-size:0.9rem}
    .fee-breakdown div{display:flex;justify-content:space-between}.net{font-weight:700;font-size:1.1rem;border-top:1px solid var(--line);padding-top:8px}
    .loading{display:flex;justify-content:center;padding:60px}
  `]
})
export class InstantSettlementsPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);
  readonly estimate = toSignal(this.api.getSettlementEstimate().pipe(catchError(() => of(null))));
  settlementAmount = 0;
  requestSettlement() { this.api.requestInstantSettlement(this.settlementAmount).subscribe({ next: () => { this.snack.open('Settlement initiated', 'Close', { duration: 3000 }); }, error: () => this.snack.open('Failed', 'Close', { duration: 3000 }) }); }
  formatCurrencyValue(v: number): string { return formatCurrency(v); }
}
