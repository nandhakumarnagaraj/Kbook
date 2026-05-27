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
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule, MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatDate } from '../../shared/formatters';
import { EmptyStateComponent } from '../../shared/empty-state.component';

interface CommissionRecord {
  id: number;
  subMerchantId: string;
  businessName: string;
  status: string;
  commissionRate: number;
  updatedAt: number;
}

@Component({
  selector: 'app-commission-edit-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  template: `
    <h2 mat-dialog-title>Edit Commission Rate</h2>
    <mat-dialog-content>
      <p class="dialog-subtitle">
        Set the platform commission rate for <strong>{{ data.businessName }}</strong>.
      </p>
      
      <div class="form-container">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Commission Rate (%)</mat-label>
          <input matInput type="number" step="0.01" min="0" max="100" [(ngModel)]="editRate" [disabled]="saving()">
          <span matSuffix>%</span>
          <mat-hint>Percentage taken from each successful transaction.</mat-hint>
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving() || editRate < 0 || editRate > 100">
        <span *ngIf="!saving()">Save Changes</span>
        <mat-spinner *ngIf="saving()" diameter="20" color="accent"></mat-spinner>
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-subtitle { color: var(--muted); margin-bottom: 24px; font-size: 0.95rem; }
    .form-container { padding-top: 8px; }
    .full-width { width: 100%; }
  `]
})
export class CommissionEditDialogComponent {
  readonly dialogRef = inject(MatDialogRef<CommissionEditDialogComponent>);
  readonly data = inject<{ id: number, businessName: string, currentRate: number }>(MAT_DIALOG_DATA);
  readonly api = inject(AdminApiService);
  readonly destroyRef = inject(DestroyRef);
  readonly snackBar = inject(MatSnackBar);

  editRate = this.data.currentRate;
  saving = signal(false);

  save(): void {
    if (this.editRate < 0 || this.editRate > 100) return;
    
    this.saving.set(true);
    this.api.updateCommission(this.data.id, this.editRate).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.saving.set(false);
        this.snackBar.open('Commission rate updated successfully.', 'Close', { duration: 3000 });
        this.dialogRef.close(true);
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('Failed to update commission rate.', 'Close', { duration: 5000 });
      }
    });
  }
}

@Component({
  selector: 'app-commission-config-page',
  standalone: true,
  imports: [
    CommonModule, 
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatDialogModule,
    EmptyStateComponent
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title text-balance">Commission Configuration</h1>
          <p class="page-subtitle">Manage commission rates for all sub-merchants across the platform.</p>
        </div>
        <div class="header-actions">
           <button mat-flat-button color="primary" (click)="loadCommissions()" [disabled]="!loaded()" aria-label="Refresh commission data">
            <mat-icon>refresh</mat-icon>
            Refresh
          </button>
        </div>
      </div>

      <mat-card class="table-card mat-elevation-z1">
        <div class="loading-overlay" *ngIf="!loaded()">
          <mat-spinner diameter="40"></mat-spinner>
        </div>

        <table mat-table [dataSource]="dataSource" matSort *ngIf="dataSource.data.length > 0 || !loaded()">
          <ng-container matColumnDef="businessName">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Business Name </th>
            <td mat-cell *matCellDef="let rec"> <strong class="business-name">{{ rec.businessName }}</strong> </td>
          </ng-container>

          <ng-container matColumnDef="subMerchantId">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Sub-Merchant ID </th>
            <td mat-cell *matCellDef="let rec"> <code class="mono-code">{{ rec.subMerchantId || '-' }}</code> </td>
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
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Rate </th>
            <td mat-cell *matCellDef="let rec">
              <span class="rate-value">{{ rec.commissionRate }}%</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="updatedAt">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Last Updated </th>
            <td mat-cell *matCellDef="let rec" class="tabular-nums text-muted"> {{ formatDateVal(rec.updatedAt) }} </td>
          </ng-container>
          
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef class="actions-header"> Actions </th>
            <td mat-cell *matCellDef="let rec" class="actions-cell">
              <button mat-icon-button color="primary" (click)="openEditDialog(rec)" aria-label="Edit commission" matTooltip="Edit Rate">
                <mat-icon fontSet="material-icons-outlined">edit</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;" class="hover-row"></tr>
        </table>

        <app-empty-state 
            *ngIf="loaded() && !dataSource.data.length"
            icon="monetization_on"
            title="No Commission Records"
            message="There are no sub-merchants available for commission configuration.">
        </app-empty-state>

        <mat-paginator [pageSizeOptions]="[10, 25, 50, 100]" aria-label="Select page of commissions" class="table-paginator"></mat-paginator>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 32px; max-width: 1400px; margin: 0 auto; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 32px; }
    .page-title { margin: 0; font-size: 2rem; font-weight: 800; color: var(--ink); letter-spacing: -0.02em; }
    .page-subtitle { margin: 8px 0 0; color: var(--muted); font-size: 1rem; }
    
    .table-card { position: relative; overflow: hidden; border-radius: 12px; background: var(--bg-elevated); }
    .loading-overlay { position: absolute; inset: 0; background: rgba(255, 255, 255, 0.7); z-index: 10; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(4px); }
    
    table { width: 100%; }
    
    .hover-row { transition: background-color 0.2s ease; }
    .hover-row:hover { background-color: var(--surface-hover); }

    .business-name { color: var(--ink); font-weight: 600; }
    .mono-code { font-family: 'JetBrains Mono', 'Fira Code', monospace; background: var(--surface); padding: 4px 8px; border-radius: 6px; color: var(--ink-secondary); font-size: 0.85rem; border: 1px solid var(--line); }

    .rate-value { font-weight: 800; font-size: 1.15rem; font-variant-numeric: tabular-nums; color: var(--brand-saffron); }
    .tabular-nums { font-variant-numeric: tabular-nums; }
    .text-muted { color: var(--muted); }

    .actions-header { text-align: right; padding-right: 24px !important; }
    .actions-cell { text-align: right; padding-right: 12px !important; }

    .table-paginator { border-top: 1px solid var(--line); background: transparent; }

    @media (max-width: 768px) {
      .page-container { padding: 16px; }
      .header-row { flex-direction: column; align-items: flex-start; gap: 16px; }
    }
  `]
})
export class CommissionConfigPageComponent implements OnInit, AfterViewInit {
  private readonly api = inject(AdminApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  readonly loaded = signal(false);
  readonly dataSource = new MatTableDataSource<CommissionRecord>([]);
  readonly displayedColumns = ['businessName', 'subMerchantId', 'status', 'commissionRate', 'updatedAt', 'actions'];

  ngOnInit(): void { this.loadCommissions(); }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  loadCommissions(): void {
    this.loaded.set(false);
    this.api.getCommissions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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

  openEditDialog(rec: CommissionRecord): void {
    const dialogRef = this.dialog.open(CommissionEditDialogComponent, {
      width: '450px',
      data: { id: rec.id, businessName: rec.businessName, currentRate: rec.commissionRate },
      disableClose: true
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadCommissions();
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
