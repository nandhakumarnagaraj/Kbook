import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal, ViewChild, AfterViewInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';

interface Transaction {
  txnId: string;
  restaurantId: number;
  restaurantName: string;
  amount: number;
  status: string;
  paymentMode: string;
  createdAt: number;
}

@Component({
  selector: 'app-transaction-monitor-page',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatButtonModule,
    MatTooltipModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">Transaction Monitor</h1>
          <p class="page-subtitle">View all Easebuzz payment transactions across the platform.</p>
        </div>
        <div class="header-right">
          <a mat-stroked-button href="https://dashboard.easebuzz.in" target="_blank" rel="noopener noreferrer">
            <mat-icon>open_in_new</mat-icon>
            Easebuzz Dashboard
          </a>
        </div>
      </div>

      <mat-card class="filter-card">
        <mat-card-content class="filter-row">
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Status</mat-label>
            <mat-select [(ngModel)]="statusFilter" (selectionChange)="onFilterChange()">
              <mat-option value="">All Statuses</mat-option>
              <mat-option value="SUCCESS">Success</mat-option>
              <mat-option value="FAILED">Failed</mat-option>
              <mat-option value="PENDING">Pending</mat-option>
              <mat-option value="PROCESSING">Processing</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Restaurant ID</mat-label>
            <input matInput type="number" [(ngModel)]="restaurantIdFilter" (ngModelChange)="onFilterChange()" placeholder="Enter ID">
          </mat-form-field>

          <div class="spacer"></div>
          
          <button mat-icon-button (click)="clearFilters()" matTooltip="Clear Filters">
            <mat-icon>filter_list_off</mat-icon>
          </button>
          <button mat-icon-button (click)="loadTransactions()" matTooltip="Refresh">
            <mat-icon>refresh</mat-icon>
          </button>
        </mat-card-content>
      </mat-card>

      <div class="table-container mat-elevation-z2">
        <div class="loading-shade" *ngIf="!loaded()">
          <mat-spinner diameter="40"></mat-spinner>
        </div>

        <table mat-table [dataSource]="dataSource">
          <ng-container matColumnDef="txnId">
            <th mat-header-cell *matHeaderCellDef> Txn ID </th>
            <td mat-cell *matCellDef="let txn"> <code>{{ txn.txnId }}</code> </td>
          </ng-container>

          <ng-container matColumnDef="restaurant">
            <th mat-header-cell *matHeaderCellDef> Restaurant </th>
            <td mat-cell *matCellDef="let txn"> 
              <div class="stacked-meta">
                <span class="main-text">{{ txn.restaurantName }}</span>
                <span class="sub-text">#{{ txn.restaurantId }}</span>
              </div>
            </td>
          </ng-container>

          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef> Amount </th>
            <td mat-cell *matCellDef="let txn"> <strong>{{ formatAmount(txn.amount) }}</strong> </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef> Status </th>
            <td mat-cell *matCellDef="let txn">
              <span class="status-chip" [class]="getStatusChipClass(txn.status)">
                {{ txn.status }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="mode">
            <th mat-header-cell *matHeaderCellDef> Mode </th>
            <td mat-cell *matCellDef="let txn"> {{ txn.paymentMode || '-' }} </td>
          </ng-container>

          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef> Date </th>
            <td mat-cell *matCellDef="let txn"> {{ formatDateVal(txn.createdAt) }} </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>

          <tr class="mat-row" *matNoDataRow>
            <td class="mat-cell" colspan="6" style="padding: 48px; text-align: center; color: var(--muted);">
               No transactions found matching the filters.
            </td>
          </tr>
        </table>

        <mat-paginator [length]="totalCount"
                       [pageSize]="pageSize"
                       [pageSizeOptions]="[10, 25, 50, 100]"
                       (page)="onPageEvent($event)"
                       aria-label="Select page">
        </mat-paginator>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
    .page-title { margin: 0; font-size: 1.75rem; font-weight: 700; color: var(--ink); }
    .page-subtitle { margin: 4px 0 0; color: var(--muted); font-size: 0.9rem; }

    .filter-card { margin-bottom: 24px; border: none; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }
    .filter-row { display: flex; align-items: center; gap: 16px; padding: 16px !important; }
    .filter-field { width: 200px; }
    .spacer { flex: 1; }

    .table-container { position: relative; background: white; border-radius: 8px; overflow: hidden; }
    .loading-shade { position: absolute; top: 0; left: 0; bottom: 56px; right: 0; background: rgba(255, 255, 255, 0.7); z-index: 1; display: flex; align-items: center; justify-content: center; }
    table { width: 100%; }

    .stacked-meta { display: flex; flex-direction: column; }
    .main-text { font-weight: 600; color: var(--ink); }
    .sub-text { font-size: 0.75rem; color: var(--muted); }

    .status-chip { padding: 4px 10px; border-radius: 999px; font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; }
    .status-chip.success { background: #dcfce7; color: #16a34a; }
    .status-chip.warn { background: #fef3c7; color: #d97706; }
    .status-chip.danger { background: #fee2e2; color: #dc2626; }

    code { font-family: monospace; font-size: 0.85rem; background: #f1f5f9; padding: 2px 6px; border-radius: 4px; }

    @media (max-width: 768px) { .filter-row { flex-direction: column; align-items: stretch; } .filter-field { width: 100%; } }
  `]
})
export class TransactionMonitorPageComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly destroyRef = inject(DestroyRef);

  dataSource = new MatTableDataSource<Transaction>([]);
  displayedColumns = ['txnId', 'restaurant', 'amount', 'status', 'mode', 'date'];
  
  readonly loaded = signal(false);
  currentPage = 0;
  pageSize = 50;
  totalCount = 1000; // Mock total, ideally API returns this

  statusFilter = '';
  restaurantIdFilter: number | null = null;

  ngOnInit(): void {
    this.loadTransactions();
  }

  loadTransactions(): void {
    this.loaded.set(false);
    this.api.getTransactions(this.currentPage, this.pageSize, this.statusFilter || undefined, this.restaurantIdFilter ?? undefined).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.dataSource.data = data;
        this.loaded.set(true);
      },
      error: () => {
        this.dataSource.data = [];
        this.loaded.set(true);
      }
    });
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadTransactions();
  }

  onPageEvent(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadTransactions();
  }

  clearFilters(): void {
    this.statusFilter = '';
    this.restaurantIdFilter = null;
    this.currentPage = 0;
    this.loadTransactions();
  }

  getStatusChipClass(status: string): string {
    switch (status) {
      case 'SUCCESS': case 'CAPTURED': return 'success';
      case 'FAILED': case 'FAILURE': return 'danger';
      case 'PENDING': case 'PENDING_VPA': case 'PENDING_NETBANKING': case 'PENDING_CARD': case 'PROCESSING': return 'warn';
      default: return '';
    }
  }

  formatAmount(value: number): string { return formatCurrency(value); }
  formatDateVal(value: number): string { return formatDate(value); }
}
