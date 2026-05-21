import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal, ViewChild, AfterViewInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatDate } from '../../shared/formatters';

interface CommissionRecord {
  id: number;
  subMerchantId: string;
  businessName: string;
  status: string;
  commissionRate: number;
  updatedAt: number;
}

@Component({
  selector: 'app-commission-config-page',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatChipsModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">Commission Configuration</h1>
          <p class="page-subtitle">Manage commission rates for all sub-merchants across the platform.</p>
        </div>
        <div class="header-actions">
           <button mat-icon-button (click)="loadCommissions()" matTooltip="Refresh Data">
            <mat-icon>refresh</mat-icon>
          </button>
        </div>
      </div>

      <div class="table-container mat-elevation-z2">
        <div class="loading-shade" *ngIf="!loaded()">
          <mat-spinner diameter="40"></mat-spinner>
        </div>

        <table mat-table [dataSource]="dataSource" matSort>
          <ng-container matColumnDef="businessName">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Business Name </th>
            <td mat-cell *matCellDef="let rec"> <strong>{{ rec.businessName }}</strong> </td>
          </ng-container>

          <ng-container matColumnDef="subMerchantId">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Sub-Merchant ID </th>
            <td mat-cell *matCellDef="let rec"> <code>{{ rec.subMerchantId || '-' }}</code> </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Status </th>
            <td mat-cell *matCellDef="let rec">
              <span class="status-chip" [class]="getChipClass(rec.status)">
                {{ rec.status }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="commissionRate">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Commission Rate </th>
            <td mat-cell *matCellDef="let rec">
              <div class="commission-cell">
                <span *ngIf="editingId() !== rec.id" class="rate-value">
                  {{ rec.commissionRate }}%
                </span>
                
                <div class="edit-inline" *ngIf="editingId() === rec.id">
                  <mat-form-field appearance="outline" class="inline-field">
                    <input matInput type="number" step="0.01" min="0" max="100" [(ngModel)]="editRate">
                    <span matSuffix>%</span>
                  </mat-form-field>
                  <button mat-icon-button color="primary" (click)="saveCommission(rec.id)" [disabled]="saving()">
                    <mat-icon>check</mat-icon>
                  </button>
                  <button mat-icon-button color="warn" (click)="cancelEdit()">
                    <mat-icon>close</mat-icon>
                  </button>
                </div>

                <button mat-icon-button *ngIf="editingId() !== rec.id" (click)="startEdit(rec)">
                  <mat-icon fontSet="material-icons-outlined">edit</mat-icon>
                </button>
              </div>
            </td>
          </ng-container>

          <ng-container matColumnDef="updatedAt">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Last Updated </th>
            <td mat-cell *matCellDef="let rec"> {{ formatDateVal(rec.updatedAt) }} </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        <div class="empty-state" *ngIf="loaded() && !dataSource.data.length">
          <mat-icon>inbox</mat-icon>
          <p>No commission records found.</p>
        </div>

        <mat-paginator [pageSizeOptions]="[10, 25, 50, 100]" aria-label="Select page of commissions"></mat-paginator>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
    .page-title { margin: 0; font-size: 1.75rem; font-weight: 700; color: var(--ink); }
    .page-subtitle { margin: 4px 0 0; color: var(--muted); font-size: 0.9rem; }
    .header-actions { display: flex; gap: 8px; }

    .table-container { position: relative; background: white; border-radius: 8px; overflow: hidden; }
    .loading-shade { position: absolute; top: 0; left: 0; bottom: 56px; right: 0; background: rgba(255, 255, 255, 0.7); z-index: 1; display: flex; align-items: center; justify-content: center; }
    table { width: 100%; }

    code { font-family: monospace; background: #f1f5f9; padding: 2px 6px; border-radius: 4px; color: #475569; }

    .status-chip { padding: 4px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
    .status-chip.success { background: #dcfce7; color: #16a34a; }
    .status-chip.warn { background: #fef3c7; color: #d97706; }
    .status-chip.danger { background: #fee2e2; color: #dc2626; }

    .commission-cell { display: flex; align-items: center; gap: 8px; }
    .rate-value { font-weight: 700; font-size: 1.1rem; }
    
    .edit-inline { display: flex; align-items: center; gap: 4px; }
    .inline-field { width: 100px; }
    ::ng-deep .inline-field .mat-mdc-form-field-subscript-wrapper { display: none; }
    ::ng-deep .inline-field .mat-mdc-text-field-wrapper { height: 40px !important; padding: 0 8px !important; }

    .empty-state { padding: 48px; text-align: center; color: var(--muted); }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 16px; opacity: 0.5; }

    @media (max-width: 768px) {
      .header-row { flex-direction: column; gap: 16px; }
    }
  `]
})
export class CommissionConfigPageComponent implements OnInit, AfterViewInit {
  private readonly api = inject(AdminApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  readonly records = signal<CommissionRecord[]>([]);
  readonly loaded = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly dataSource = new MatTableDataSource<CommissionRecord>([]);
  readonly displayedColumns = ['businessName', 'subMerchantId', 'status', 'commissionRate', 'updatedAt'];

  editRate = 0;

  ngOnInit(): void { this.loadCommissions(); }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  loadCommissions(): void {
    this.loaded.set(false);
    this.api.getCommissions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.records.set(data);
        this.dataSource.data = data;
        this.loaded.set(true);
      },
      error: () => {
        this.records.set([]);
        this.dataSource.data = [];
        this.loaded.set(true);
      }
    });
  }

  startEdit(rec: CommissionRecord): void {
    this.editingId.set(rec.id);
    this.editRate = rec.commissionRate;
  }

  cancelEdit(): void { this.editingId.set(null); this.editRate = 0; }

  saveCommission(id: number): void {
    this.saving.set(true);
    this.api.updateCommission(id, this.editRate).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelEdit();
        this.loadCommissions();
        this.snackBar.open('Commission rate updated successfully.', 'Close', { duration: 3000 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('Failed to update commission rate.', 'Close', { duration: 5000 });
      }
    });
  }

  getChipClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'PENDING_KYC': case 'KYC_SUBMITTED': return 'warn';
      case 'SUSPENDED': case 'REJECTED': case 'FAILED': case 'INACTIVE': return 'danger';
      default: return '';
    }
  }

  formatDateVal(value: number): string { return formatDate(value); }
}
