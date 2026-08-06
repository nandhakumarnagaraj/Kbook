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
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

@Component({
  selector: 'app-financing-page',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, MatTableModule],
  template: `
    <div class="page-container">
      <div class="header-row"><div><h1 class="page-title">Working Capital Financing</h1><p class="page-subtitle">Pre-approved credit lines and flexible repayment options.</p></div></div>
      @if (eligibility(); as e) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon green">account_balance</mat-icon><mat-card-title>{{ formatCurrencyValue(e.estimatedCreditLimit) }}</mat-card-title><mat-card-subtitle>Credit Limit</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">trending_up</mat-icon><mat-card-title>{{ formatCurrencyValue(e.monthlyAverage) }}</mat-card-title><mat-card-subtitle>Monthly Average</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon blue">receipt_long</mat-icon><mat-card-title>{{ e.totalTransactions }}</mat-card-title><mat-card-subtitle>3-Month Transactions</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar [class.green]="e.eligible" [class.red]="!e.eligible"><mat-icon>{{ e.eligible ? 'check_circle' : 'cancel' }}</mat-icon></mat-icon><mat-card-title>{{ e.eligible ? 'Eligible' : 'Not Eligible' }}</mat-card-title><mat-card-subtitle>Min ₹10,000 revenue</mat-card-subtitle></mat-card-header></mat-card>
        </div>
        <mat-card class="form-card">
          <mat-card-header><mat-card-title>Calculate Loan Options</mat-card-title></mat-card-header>
          <mat-card-content>
            <mat-form-field appearance="outline" class="full-width"><mat-label>Amount (INR)</mat-label><input matInput type="number" [(ngModel)]="loanAmount" [max]="e.estimatedCreditLimit"></mat-form-field>
            <button mat-flat-button color="primary" [disabled]="!e.eligible || loanAmount <= 0" (click)="calculateOptions()"><mat-icon>calculate</mat-icon>Calculate</button>
            @if (options()?.length) {
              <table mat-table [dataSource]="options()!" class="options-table">
                <ng-container matColumnDef="days"><th mat-header-cell *matHeaderCellDef>Term</th><td mat-cell *matCellDef="let o">{{ o.days }} days</td></ng-container>
                <ng-container matColumnDef="requestedAmount"><th mat-header-cell *matHeaderCellDef>Amount</th><td mat-cell *matCellDef="let o">{{ formatCurrencyValue(o.requestedAmount) }}</td></ng-container>
                <ng-container matColumnDef="interest"><th mat-header-cell *matHeaderCellDef>Interest</th><td mat-cell *matCellDef="let o">{{ formatCurrencyValue(o.interest) }}</td></ng-container>
                <ng-container matColumnDef="totalRepayment"><th mat-header-cell *matHeaderCellDef>Total Repayment</th><td mat-cell *matCellDef="let o" class="bold">{{ formatCurrencyValue(o.totalRepayment) }}</td></ng-container>
                <tr mat-header-row *matHeaderRowDef="['days','requestedAmount','interest','totalRepayment']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['days','requestedAmount','interest','totalRepayment'];"></tr>
              </table>
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
    .green{background:var(--success-bg);color:var(--success)}.amber{background:var(--warn-bg);color:var(--warn)}.blue{background:var(--info-bg);color:var(--info)}.red{background:var(--danger-bg);color:var(--danger)}
    .form-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}.full-width{width:100%}
    .options-table{width:100%;margin-top:24px}.bold{font-weight:700}
    .loading{display:flex;justify-content:center;padding:60px}
  `]
})
export class FinancingPageComponent {
  private readonly api = inject(AdminApiService);
  readonly eligibility = toSignal(this.api.getCreditEligibility().pipe(catchError(() => of(null))));
  readonly options = signal<any[] | null>(null);
  loanAmount = 0;
  calculateOptions() { this.api.getLoanOptions(this.loanAmount).pipe(catchError(() => of(null))).subscribe(r => this.options.set(r?.options || null)); }
  formatCurrencyValue(v: number): string { return formatCurrency(v); }
}
