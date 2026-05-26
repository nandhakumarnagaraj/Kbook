import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal, ViewChild, AfterViewInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBusinessDetail, AdminBusinessListItem } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

interface LocalBusinessListItem extends AdminBusinessListItem {
  isSuspended?: boolean;
}

interface LocalBusinessDetail extends AdminBusinessDetail {
  isSuspended?: boolean;
}

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatInputModule,
    MatFormFieldModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTooltipModule,
    MatMenuModule,
    MatDialogModule,
    MatSnackBarModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">Business Directory</h1>
          <p class="page-subtitle">Manage and inspect all registered businesses on the platform.</p>
        </div>
        <button mat-flat-button color="primary" (click)="loadBusinesses()">
          <mat-icon>refresh</mat-icon>
          Refresh Data
        </button>
      </div>

      <mat-card class="filter-card">
        <mat-card-content class="filter-row">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search Businesses</mat-label>
            <mat-icon matPrefix>search</mat-icon>
            <input matInput (keyup)="applyFilter($event)" placeholder="Search by name, owner, email..." #input>
          </mat-form-field>
          
          <div class="spacer"></div>
          
          <div class="stats-badge">
             <span class="label">Total Businesses:</span>
             <span class="value">{{ dataSource.data.length }}</span>
          </div>
        </mat-card-content>
      </mat-card>

      <div class="table-container mat-elevation-z2">
        <div class="loading-shade" *ngIf="!loaded">
          <mat-spinner diameter="40" *ngIf="!loadError"></mat-spinner>
          <div class="error-msg" *ngIf="loadError">{{ loadError }}</div>
        </div>

        <table mat-table [dataSource]="dataSource" matSort>
          <!-- Business Column -->
          <ng-container matColumnDef="shopName">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Business </th>
            <td mat-cell *matCellDef="let row"> 
              <div class="biz-info">
                <span class="biz-name">{{ row.shopName || '-' }}</span>
                <span class="biz-id">#{{ row.restaurantId }}</span>
              </div>
            </td>
          </ng-container>

          <!-- Owner Column -->
          <ng-container matColumnDef="ownerName">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Owner </th>
            <td mat-cell *matCellDef="let row"> 
              <div class="owner-cell">
                <mat-icon class="owner-icon">person_outline</mat-icon>
                <span>{{ row.ownerName || '-' }}</span>
              </div>
            </td>
          </ng-container>

          <!-- Status Column -->
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Status </th>
            <td mat-cell *matCellDef="let row">
              <span class="status-chip" [class.active]="row.orderCount > 0 && !row.isSuspended" [class.inactive]="row.orderCount === 0 && !row.isSuspended" [class.suspended]="row.isSuspended">
                {{ row.isSuspended ? 'Suspended' : (row.orderCount > 0 ? 'Active' : 'Idle') }}
              </span>
            </td>
          </ng-container>
 
          <!-- Metrics Columns -->
          <ng-container matColumnDef="orderCount">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Orders </th>
            <td mat-cell *matCellDef="let row" class="numeric-cell"> 
              <span class="count-badge">{{ row.orderCount }}</span>
            </td>
          </ng-container>
 
          <ng-container matColumnDef="menuCount">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Menu </th>
            <td mat-cell *matCellDef="let row" class="numeric-cell"> {{ row.menuCount }} </td>
          </ng-container>
 
          <!-- Updated Column -->
          <ng-container matColumnDef="updatedAt">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Updated </th>
            <td mat-cell *matCellDef="let row" class="date-cell"> {{ formatDateValue(row.updatedAt) }} </td>
          </ng-container>
 
          <!-- Actions Column -->
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef class="actions-cell"> Action </th>
            <td mat-cell *matCellDef="let row" class="actions-cell">
               <button mat-icon-button [matMenuTriggerFor]="menu" (click)="$event.stopPropagation()">
                 <mat-icon>more_vert</mat-icon>
               </button>
               <mat-menu #menu="matMenu" xPosition="before">
                 <button mat-menu-item (click)="showDetails(row)">
                   <mat-icon>visibility</mat-icon>
                   <span>View Details</span>
                 </button>
                 <button mat-menu-item color="primary" (click)="openEditFromRow(row)">
                   <mat-icon>edit</mat-icon>
                   <span>Edit Business</span>
                 </button>
                 <mat-divider></mat-divider>
                 <button mat-menu-item color="warn" (click)="openSuspendConfirm(row)">
                   <mat-icon>block</mat-icon>
                   <span>{{ row.isSuspended ? 'Unsuspend' : 'Suspend' }}</span>
                 </button>
               </mat-menu>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;" 
              class="clickable-row"
              [class.selected-row]="selectedDetail()?.restaurantId === row.restaurantId"
              (click)="showDetails(row)"></tr>



          <!-- Row shown when there is no matching data. -->
          <tr class="mat-row" *matNoDataRow>
            <td class="mat-cell" colspan="6">No business matching the filter "{{input.value}}"</td>
          </tr>
        </table>

        <mat-paginator [pageSizeOptions]="[5, 10, 25, 100]" aria-label="Select page of businesses"></mat-paginator>
      </div>

      <!-- Detail Dialog Template -->
      <ng-template #detailDialog>
        <h2 mat-dialog-title style="display: flex; align-items: center; gap: 12px; margin: 0; padding: 20px 24px 10px;">
          <mat-icon color="primary">store</mat-icon>
          <span style="font-weight: 800; font-size: 1.3rem;">{{ selectedDetail()?.shopName }}</span>
        </h2>
        <mat-dialog-content style="padding: 0 24px 20px;">
          <div *ngIf="selectedDetail() as detail" class="business-detail-dialog">
            <div style="font-size: 0.85rem; color: var(--muted); margin-bottom: 16px;">
              Business ID: {{ detail.restaurantId }}
            </div>
            
            <div class="detail-grid">
               <div class="detail-item">
                  <span class="label">Total Revenue</span>
                  <span class="value">{{ formatCurrencyValue(detail.totalRevenue) }}</span>
               </div>
               <div class="detail-item">
                  <span class="label">POS Orders</span>
                  <span class="value">{{ detail.posOrderCount }}</span>
               </div>
               <div class="detail-item">
                  <span class="label">Total Orders</span>
                  <span class="value">{{ detail.posOrderCount }}</span>
               </div>
               <div class="detail-item">
                  <span class="label">Menu Items</span>
                  <span class="value">{{ detail.menuCount }}</span>
               </div>
               <div class="detail-item">
                  <span class="label">GST Status</span>
                  <span class="value">{{ detail.gstEnabled ? 'Enabled' : 'Disabled' }}</span>
               </div>
               <div class="detail-item">
                  <span class="label">Timezone</span>
                  <span class="value">{{ detail.timezone || '-' }}</span>
               </div>
            </div>
          </div>
        </mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-button mat-dialog-close>Dismiss</button>
          <button mat-raised-button color="primary" (click)="openEditModal(selectedDetail()!)">Edit Business</button>
        </mat-dialog-actions>
      </ng-template>

      <!-- Edit Dialog Template -->
      <ng-template #editDialog>
        <h2 mat-dialog-title>Edit Business</h2>
        <mat-dialog-content style="padding: 16px 24px;">
          <form [formGroup]="editForm" style="display: flex; flex-direction: column; gap: 16px; min-width: 320px; padding-top: 8px;">
            <mat-form-field appearance="outline">
              <mat-label>Shop Name</mat-label>
              <input matInput formControlName="shopName">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Owner Name</mat-label>
              <input matInput formControlName="ownerName">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Timezone</mat-label>
              <input matInput formControlName="timezone">
            </mat-form-field>
          </form>
        </mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-button mat-dialog-close>Cancel</button>
          <button mat-flat-button color="primary" (click)="saveBusinessEdit()">Save Changes</button>
        </mat-dialog-actions>
      </ng-template>

      <!-- Suspend Confirm Dialog Template -->
      <ng-template #suspendDialog>
        <h2 mat-dialog-title>Confirm Action</h2>
        <mat-dialog-content>
          <p>Are you sure you want to {{ selectedDetail()?.isSuspended ? 'unsuspend' : 'suspend' }} <strong>{{ selectedDetail()?.shopName || 'this business' }}</strong>?</p>
          <p *ngIf="!selectedDetail()?.isSuspended" style="color: var(--danger); font-size: 0.85rem; margin-top: 8px;">This will temporarily halt transactions for this shop.</p>
        </mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-button mat-dialog-close>Cancel</button>
          <button mat-flat-button [color]="selectedDetail()?.isSuspended ? 'primary' : 'warn'" (click)="confirmSuspend()">
            {{ selectedDetail()?.isSuspended ? 'Unsuspend' : 'Suspend' }}
          </button>
        </mat-dialog-actions>
      </ng-template>
    </div>
  `,
  styles: [`
    .page-container {
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
    }

    .header-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
    }

    .page-title {
      margin: 0;
      font-size: 1.75rem;
      font-weight: 700;
      color: var(--ink);
    }

    .page-subtitle {
      margin: 4px 0 0;
      color: var(--muted);
      font-size: 0.9rem;
    }

    .filter-card {
      margin-bottom: 24px;
      border: none;
      box-shadow: 0 2px 8px rgba(0,0,0,0.05);
    }

    .filter-row {
      display: flex;
      align-items: center;
      padding: 16px !important;
    }

    .search-field {
      flex: 1;
      max-width: 480px;
    }

    .spacer {
      flex: 1 1 auto;
    }

    .stats-badge {
      display: flex;
      gap: 8px;
      align-items: center;
      padding: 8px 16px;
      background: var(--brand-soft);
      border-radius: 999px;
      color: var(--brand);
      font-weight: 600;
      font-size: 0.85rem;
    }

    .table-container { 
      position: relative; 
      background: var(--panel) !important; 
      border-radius: var(--radius-xl) !important; 
      border: 1px solid var(--line) !important; 
      box-shadow: var(--shadow-md) !important;
      overflow: hidden; 
    }

    .loading-shade {
      position: absolute;
      top: 0;
      left: 0;
      bottom: 56px;
      right: 0;
      background: rgba(255, 255, 255, 0.7);
      z-index: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    table {
      width: 100%;
      background: transparent !important;
    }

    th.mat-mdc-header-cell {
      background: var(--bg) !important;
      color: var(--ink-secondary) !important;
      font-size: 0.75rem !important;
      font-weight: 700 !important;
      text-transform: uppercase !important;
      letter-spacing: 1px !important;
      padding: 16px !important;
      border-bottom: 1px solid var(--line) !important;
    }
    
    td.mat-mdc-cell {
      padding: 16px !important;
      border-bottom: 1px solid var(--line) !important;
      font-size: 0.9rem !important;
      color: var(--ink) !important;
    }

    .clickable-row {
      cursor: pointer;
      transition: background 0.2s ease;
    }

    .clickable-row:hover {
      background: var(--panel-hover) !important;
    }

    .selected-row {
      background: var(--brand-soft) !important;
      border-left: 4px solid var(--brand) !important;
    }
    .selected-row td {
      font-weight: 500 !important;
    }

    .biz-info {
      display: flex;
      flex-direction: column;
    }

    .biz-name {
      font-weight: 600;
      color: var(--ink);
    }

    .biz-id {
      font-size: 0.75rem;
      color: var(--muted);
    }

    .owner-cell {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .owner-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: var(--muted);
    }

    .status-chip {
      padding: 4px 12px;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .status-chip.active { background: #dcfce7; color: #16a34a; }
    .status-chip.inactive { background: #f1f5f9; color: #64748b; }
    .status-chip.suspended { background: #fee2e2; color: #dc2626; }

    .numeric-cell {
      font-weight: 600;
      color: var(--ink);
    }

    .count-badge {
      background: #f1f5f9;
      padding: 2px 8px;
      border-radius: 6px;
      min-width: 32px;
      display: inline-block;
      text-align: center;
    }

    .date-cell {
      color: var(--muted);
      font-size: 0.85rem;
    }

    .actions-cell {
      text-align: right;
      padding-right: 24px !important;
    }

    .skeleton-row td {
      padding: 16px !important;
    }

    .skeleton-shimmer {
      height: 20px;
      width: 100%;
      background: #f1f5f9;
      border-radius: 4px;
      position: relative;
      overflow: hidden;
    }

    .skeleton-shimmer::after {
      content: "";
      position: absolute;
      top: 0; right: 0; bottom: 0; left: 0;
      transform: translateX(-100%);
      background-image: linear-gradient(90deg, rgba(255,255,255,0) 0, rgba(255,255,255,0.4) 50%, rgba(255,255,255,0) 100%);
      animation: shimmer 1.5s infinite;
    }

    @keyframes shimmer { 100% { transform: translateX(100%); } }

    ::ng-deep .business-detail-dialog {
      .detail-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
        padding: 16px 0;
      }
      .detail-item {
        display: flex;
        flex-direction: column;
        gap: 4px;
        padding: 12px 14px;
        border-radius: var(--radius-md);
        background: var(--bg);
        border: 1px solid var(--line);
        transition: all 0.2s ease;
      }
      .detail-item:hover {
        border-color: var(--brand-soft);
        background: var(--panel-hover);
      }
      .detail-item .label {
        font-size: 0.65rem;
        text-transform: uppercase;
        letter-spacing: 0.8px;
        color: var(--muted);
        font-weight: 700;
      }
      .detail-item .value {
        font-size: 0.9rem;
        font-weight: 700;
        color: var(--ink);
        word-break: break-all;
      }
    }

    @media (max-width: 768px) {
      .filter-row { flex-direction: column; align-items: stretch; gap: 16px; }
      .search-field { max-width: none; }
    }
  `]
})
export class BusinessesPageComponent implements AfterViewInit {
  private readonly api = inject(AdminApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  displayedColumns: string[] = ['shopName', 'ownerName', 'status', 'orderCount', 'menuCount', 'updatedAt', 'actions'];
  dataSource = new MatTableDataSource<LocalBusinessListItem>([]);
  loaded = false;
  loadError = '';
  readonly selectedDetail = signal<LocalBusinessDetail | null>(null);

  editForm = this.fb.group({
    shopName: ['', Validators.required],
    ownerName: ['', Validators.required],
    timezone: ['']
  });

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild('detailDialog') detailDialogTemplate!: any;
  @ViewChild('editDialog') editDialogTemplate!: any;
  @ViewChild('suspendDialog') suspendDialogTemplate!: any;

  constructor() {
    this.loadBusinesses();
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();

    if (this.dataSource.paginator) {
      this.dataSource.paginator.firstPage();
    }
  }

  loadBusinesses(): void {
    this.loaded = false;
    this.loadError = '';
    this.api.getBusinesses().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.dataSource.data = data;
        this.loaded = true;
      },
      error: () => {
        this.dataSource.data = [];
        this.loadError = 'Unable to load businesses.';
        this.loaded = true;
      }
    });
  }

  showDetails(business: LocalBusinessListItem): void {
    this.selectedDetail.set(null);
    this.api.getBusinessDetail(business.restaurantId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (detail) => {
        const fullDetail: LocalBusinessDetail = {
          ...detail,
          isSuspended: business.isSuspended || false
        };
        this.selectedDetail.set(fullDetail);
        this.dialog.open(this.detailDialogTemplate, {
          width: '600px',
          maxHeight: '90vh',
          autoFocus: false
        });
      },
      error: () => {
        const fallbackDetail: LocalBusinessDetail = {
          restaurantId: business.restaurantId,
          shopName: business.shopName,
          ownerName: business.ownerName,
          timezone: '',
          totalRevenue: 0,
          posOrderCount: business.orderCount,
          menuCount: business.menuCount,
          gstEnabled: false,
          isSuspended: business.isSuspended || false,
          ownerWhatsappNumber: null,
          shopAddress: null,
          gstin: null,
          fssaiNumber: null,
          whatsappNumber: null,
          currency: null,
          printerEnabled: false,
          createdAt: null,
          ownerLoginId: business.ownerLoginId,
          email: business.email,
          staffCount: business.staffCount,
          updatedAt: business.updatedAt,
          websiteEnabled: business.websiteEnabled,
          orderCount: business.orderCount
        };
        this.selectedDetail.set(fallbackDetail);
        this.dialog.open(this.detailDialogTemplate, {
          width: '600px',
          maxHeight: '90vh',
          autoFocus: false
        });
      }
    });
  }

  clearDetail(): void {
    this.dialog.closeAll();
    this.selectedDetail.set(null);
  }

  openEditFromRow(row: LocalBusinessListItem): void {
    this.api.getBusinessDetail(row.restaurantId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (detail) => {
        const fullDetail: LocalBusinessDetail = {
          ...detail,
          isSuspended: row.isSuspended || false
        };
        this.selectedDetail.set(fullDetail);
        this.openEditModal(fullDetail);
      },
      error: () => {
        const fallbackDetail: LocalBusinessDetail = {
          restaurantId: row.restaurantId,
          shopName: row.shopName,
          ownerName: row.ownerName,
          timezone: '',
          totalRevenue: 0,
          posOrderCount: row.orderCount,
          menuCount: row.menuCount,
          gstEnabled: false,
          isSuspended: row.isSuspended || false,
          ownerWhatsappNumber: null,
          shopAddress: null,
          gstin: null,
          fssaiNumber: null,
          whatsappNumber: null,
          currency: null,
          printerEnabled: false,
          createdAt: null,
          ownerLoginId: row.ownerLoginId,
          email: row.email,
          staffCount: row.staffCount,
          updatedAt: row.updatedAt,
          websiteEnabled: row.websiteEnabled,
          orderCount: row.orderCount
        };
        this.selectedDetail.set(fallbackDetail);
        this.openEditModal(fallbackDetail);
      }
    });
  }

  openEditModal(detail: LocalBusinessDetail): void {
    this.dialog.closeAll();
    this.editForm.patchValue({
      shopName: detail.shopName || '',
      ownerName: detail.ownerName || '',
      timezone: detail.timezone || ''
    });
    this.dialog.open(this.editDialogTemplate, {
      width: '500px',
      autoFocus: false
    });
  }

  saveBusinessEdit(): void {
    if (this.editForm.invalid) return;
    const formVal = this.editForm.value;
    const detail = this.selectedDetail();
    if (detail) {
      const updatedData = this.dataSource.data.map(item => {
        if (item.restaurantId === detail.restaurantId) {
          return {
            ...item,
            shopName: formVal.shopName || item.shopName,
            ownerName: formVal.ownerName || item.ownerName
          };
        }
        return item;
      });
      this.dataSource.data = updatedData;

      detail.shopName = formVal.shopName || detail.shopName;
      detail.ownerName = formVal.ownerName || detail.ownerName;
      detail.timezone = formVal.timezone || detail.timezone;
      this.selectedDetail.set(detail);

      this.snackBar.open('Business updated successfully (local simulation).', 'Close', { duration: 3000 });
    }
    this.dialog.closeAll();
  }

  openSuspendConfirm(business: LocalBusinessListItem): void {
    this.api.getBusinessDetail(business.restaurantId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (detail) => {
        const fullDetail: LocalBusinessDetail = {
          ...detail,
          isSuspended: business.isSuspended || false
        };
        this.selectedDetail.set(fullDetail);
        this.dialog.open(this.suspendDialogTemplate, {
          width: '450px'
        });
      },
      error: () => {
        const fallbackDetail: LocalBusinessDetail = {
          restaurantId: business.restaurantId,
          shopName: business.shopName,
          ownerName: business.ownerName,
          timezone: '',
          totalRevenue: 0,
          posOrderCount: business.orderCount,
          menuCount: business.menuCount,
          gstEnabled: false,
          isSuspended: business.isSuspended || false,
          ownerWhatsappNumber: null,
          shopAddress: null,
          gstin: null,
          fssaiNumber: null,
          whatsappNumber: null,
          currency: null,
          printerEnabled: false,
          createdAt: null,
          ownerLoginId: business.ownerLoginId,
          email: business.email,
          staffCount: business.staffCount,
          updatedAt: business.updatedAt,
          websiteEnabled: business.websiteEnabled,
          orderCount: business.orderCount
        };
        this.selectedDetail.set(fallbackDetail);
        this.dialog.open(this.suspendDialogTemplate, {
          width: '450px'
        });
      }
    });
  }

  confirmSuspend(): void {
    const detail = this.selectedDetail();
    if (detail) {
      const targetState = !detail.isSuspended;
      const updatedData = this.dataSource.data.map(item => {
        if (item.restaurantId === detail.restaurantId) {
          return {
            ...item,
            isSuspended: targetState
          };
        }
        return item;
      });
      this.dataSource.data = updatedData;

      detail.isSuspended = targetState;
      this.selectedDetail.set(detail);

      this.snackBar.open(
        `Business "${detail.shopName}" ${targetState ? 'suspended' : 'unsuspended'} successfully (local simulation).`,
        'Close',
        { duration: 3000 }
      );
    }
    this.dialog.closeAll();
  }

  formatDateValue(value: number | null): string { return formatDate(value); }
  formatCurrencyValue(value: number): string { return formatCurrency(value); }
}
