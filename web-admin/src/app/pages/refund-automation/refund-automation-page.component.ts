import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-refund-automation-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatTableModule, MatChipsModule, MatSelectModule, MatFormFieldModule, MatInputModule, MatDialogModule, MatSnackBarModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">Refund Automation</h1>
          <p class="page-subtitle">Manage refunds, track status, and automate cancellation refunds.</p>
        </div>
      </div>

      @if (data(); as d) {
        <div class="stats-grid">
          <mat-card class="stat-card">
            <mat-card-header><mat-icon mat-card-avatar class="stat-icon orders">receipt_long</mat-icon><mat-card-title>{{ d.totalOrders }}</mat-card-title><mat-card-subtitle>Total Orders</mat-card-subtitle></mat-card-header>
          </mat-card>
          <mat-card class="stat-card">
            <mat-card-header><mat-icon mat-card-avatar class="stat-icon refunded">undo</mat-icon><mat-card-title>{{ d.refundedOrders }}</mat-card-title><mat-card-subtitle>Refunded Orders</mat-card-subtitle></mat-card-header>
          </mat-card>
          <mat-card class="stat-card">
            <mat-card-header><mat-icon mat-card-avatar class="stat-icon amount">payments</mat-icon><mat-card-title>{{ formatCurrencyValue(d.totalRefundAmount) }}</mat-card-title><mat-card-subtitle>Total Refund Amount</mat-card-subtitle></mat-card-header>
          </mat-card>
          <mat-card class="stat-card">
            <mat-card-header><mat-icon mat-card-avatar class="stat-icon rate">percent</mat-icon><mat-card-title>{{ d.refundRate }}%</mat-card-title><mat-card-subtitle>Refund Rate</mat-card-subtitle></mat-card-header>
          </mat-card>
        </div>

        <mat-card class="table-card">
          <mat-card-header><mat-card-title>Refundable Orders</mat-card-title></mat-card-header>
          <mat-card-content>
            @if (refundableOrders()?.length) {
              <table mat-table [dataSource]="refundableOrders()!">
                <ng-container matColumnDef="orderCode"><th mat-header-cell *matHeaderCellDef>Order</th><td mat-cell *matCellDef="let o">{{ o.orderCode }}</td></ng-container>
                <ng-container matColumnDef="customerName"><th mat-header-cell *matHeaderCellDef>Customer</th><td mat-cell *matCellDef="let o">{{ o.customerName || 'Walk-in' }}</td></ng-container>
                <ng-container matColumnDef="totalAmount"><th mat-header-cell *matHeaderCellDef>Amount</th><td mat-cell *matCellDef="let o">{{ formatCurrencyValue(o.totalAmount) }}</td></ng-container>
                <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef>Action</th><td mat-cell *matCellDef="let o"><button mat-stroked-button color="warn" (click)="refundOrder(o.billId, o.totalAmount)"><mat-icon>undo</mat-icon>Refund</button></td></ng-container>
                <tr mat-header-row *matHeaderRowDef="['orderCode','customerName','totalAmount','action']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['orderCode','customerName','totalAmount','action'];"></tr>
              </table>
            } @else { <div class="no-data">No refundable orders found.</div> }
          </mat-card-content>
        </mat-card>
      } @else { <div class="loading"><mat-spinner diameter="40"></mat-spinner></div> }
    </div>
  `,
  styles: [`
    .page-container{padding:24px;max-width:1400px;margin:0 auto}
    .header-row{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}
    .page-title{margin:0 0 4px;font-size:1.75rem;font-weight:700;color:var(--ink)}
    .page-subtitle{margin:0;color:var(--muted);font-size:0.85rem}
    .stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px;margin-bottom:24px}
    .stat-card{border-radius:12px;border:none;box-shadow:0 4px 16px rgba(0,0,0,0.05)}
    ::ng-deep .stat-card .mat-mdc-card-header{padding:12px 16px!important;gap:12px!important}
    ::ng-deep .stat-card .mat-mdc-card-title{font-size:1.25rem!important;font-weight:700!important;margin:0!important}
    ::ng-deep .stat-card .mat-mdc-card-subtitle{font-size:0.8rem!important;color:var(--muted)!important;margin-top:2px!important}
    .stat-icon{background:var(--brand-soft);color:var(--brand);width:36px;height:36px;border-radius:8px;font-size:18px;display:flex;align-items:center;justify-content:center}
    .orders.stat-icon{background:#f3e8ff;color:#9333ea}.refunded.stat-icon{background:#fee2e2;color:#dc2626}
    .amount.stat-icon{background:#fef3c7;color:#d97706}.rate.stat-icon{background:#e0f2fe;color:#0284c7}
    .table-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}
    table{width:100%}.no-data{padding:32px;text-align:center;color:var(--muted)}
    .loading{display:flex;justify-content:center;padding:60px}
  `]
})
export class RefundAutomationPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);

  readonly data = toSignal(this.api.getRefundSummary().pipe(catchError(() => of(null))));
  readonly refundableOrders = signal<any[]>([]);
  constructor() {
    this.api.getRefundableOrders().pipe(catchError(() => of(null))).subscribe((r: any) => this.refundableOrders.set(r?.orders || []));
  }

  refundOrder(billId: number, totalAmount: number) {
    this.api.initiateRefund(billId, totalAmount, 'CUSTOMER_REQUEST').subscribe({
      next: () => { this.snack.open('Refund initiated', 'Close', { duration: 3000 }); this.refundableOrders.set([]); },
      error: () => this.snack.open('Refund failed', 'Close', { duration: 3000 })
    });
  }

  formatCurrencyValue(v: number): string { return formatCurrency(v); }
}
