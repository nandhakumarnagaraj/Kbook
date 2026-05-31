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
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-tax-compliance-page',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row"><div><h1 class="page-title">Tax Compliance</h1><p class="page-subtitle">GST reports, quarterly returns, and tax summary.</p></div></div>
      @if (summary(); as s) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">receipt</mat-icon><mat-card-title>{{ formatCurrencyValue(s.monthRevenue) }}</mat-card-title><mat-card-subtitle>Month Revenue</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon red">percent</mat-icon><mat-card-title>{{ formatCurrencyValue(s.monthTax) }}</mat-card-title><mat-card-subtitle>Month GST</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon green">account_balance</mat-icon><mat-card-title>{{ formatCurrencyValue(s.yearRevenue) }}</mat-card-title><mat-card-subtitle>Year Revenue</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon blue">paid</mat-icon><mat-card-title>{{ formatCurrencyValue(s.yearTax) }}</mat-card-title><mat-card-subtitle>Year GST</mat-card-subtitle></mat-card-header></mat-card>
        </div>
      }
      <mat-card class="form-card">
        <mat-card-header><mat-card-title>GST Report</mat-card-title></mat-card-header>
        <mat-card-content>
          <div class="report-controls">
            <mat-form-field appearance="outline"><mat-label>Year</mat-label><input matInput type="number" [(ngModel)]="reportYear" [value]="currentYear"></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Month</mat-label><mat-select [(ngModel)]="reportMonth"><mat-option [value]="1">January</mat-option><mat-option [value]="2">February</mat-option><mat-option [value]="3">March</mat-option><mat-option [value]="4">April</mat-option><mat-option [value]="5">May</mat-option><mat-option [value]="6">June</mat-option><mat-option [value]="7">July</mat-option><mat-option [value]="8">August</mat-option><mat-option [value]="9">September</mat-option><mat-option [value]="10">October</mat-option><mat-option [value]="11">November</mat-option><mat-option [value]="12">December</mat-option></mat-select></mat-form-field>
            <button mat-flat-button color="primary" (click)="loadReport()"><mat-icon>download</mat-icon>Generate</button>
          </div>
          @if (report(); as r) {
            <div class="report-grid">
              <div class="report-item"><span class="label">Period</span><span class="value">{{ r.period }}</span></div>
              <div class="report-item"><span class="label">Total Orders</span><span class="value">{{ r.totalOrders }}</span></div>
              <div class="report-item"><span class="label">Taxable Amount</span><span class="value">{{ formatCurrencyValue(r.taxableAmount) }}</span></div>
              <div class="report-item"><span class="label">CGST</span><span class="value">{{ formatCurrencyValue(r.totalCgst) }}</span></div>
              <div class="report-item"><span class="label">SGST</span><span class="value">{{ formatCurrencyValue(r.totalSgst) }}</span></div>
              <div class="report-item highlight"><span class="label">Total Tax</span><span class="value">{{ formatCurrencyValue(r.totalTax) }}</span></div>
            </div>
          }
        </mat-card-content>
      </mat-card>
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
    .green{background:#dcfce7;color:#16a34a}.amber{background:#fef3c7;color:#d97706}.blue{background:#e0f2fe;color:#0284c7}.red{background:#fee2e2;color:#dc2626}
    .form-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}
    .report-controls{display:flex;gap:16px;align-items:center;flex-wrap:wrap}
    .report-controls mat-form-field{width:140px}
    .report-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin-top:24px}
    .report-item{padding:16px;border-radius:12px;background:var(--bg-elevated);display:flex;flex-direction:column;gap:4px}
    .report-item .label{font-size:0.75rem;color:var(--muted);text-transform:uppercase;letter-spacing:0.5px}
    .report-item .value{font-size:1.1rem;font-weight:700;color:var(--ink)}
    .report-item.highlight{border:2px solid var(--brand);background:var(--brand-soft)}
    .loading{display:flex;justify-content:center;padding:60px}
  `]
})
export class TaxCompliancePageComponent {
  private readonly api = inject(AdminApiService);
  readonly summary = toSignal(this.api.getTaxSummary().pipe(catchError(() => of(null))));
  readonly report = signal<any>(null);
  reportYear = new Date().getFullYear();
  reportMonth = new Date().getMonth() + 1;
  currentYear = new Date().getFullYear();
  loadReport() { this.api.getGstReport(this.reportYear, this.reportMonth).pipe(catchError(() => of(null))).subscribe(r => this.report.set(r)); }
  formatCurrencyValue(v: number): string { return formatCurrency(v); }
}
