import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal, ViewChild, AfterViewInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
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
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBusinessDetail, AdminBusinessListItem } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
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
    MatMenuModule
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
              <span class="status-chip" [class.active]="row.orderCount > 0" [class.inactive]="row.orderCount === 0">
                {{ row.orderCount > 0 ? 'Active' : 'Idle' }}
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
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row" class="actions-cell">
               <button mat-icon-button [matMenuTriggerFor]="menu" (click)="$event.stopPropagation()">
                 <mat-icon>more_vert</mat-icon>
               </button>
               <mat-menu #menu="matMenu" xPosition="before">
                 <button mat-menu-item (click)="showDetails(row)">
                   <mat-icon>visibility</mat-icon>
                   <span>View Details</span>
                 </button>
                 <button mat-menu-item color="primary">
                   <mat-icon>edit</mat-icon>
                   <span>Edit Business</span>
                 </button>
                 <mat-divider></mat-divider>
                 <button mat-menu-item color="warn">
                   <mat-icon>block</mat-icon>
                   <span>Suspend</span>
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

      <!-- Detail Panel -->
      <div class="detail-panel" *ngIf="selectedDetail() as detail">
        <mat-card class="detail-card">
          <mat-card-header>
            <mat-icon mat-card-avatar color="primary">store</mat-icon>
            <mat-card-title>{{ detail.shopName }}</mat-card-title>
            <mat-card-subtitle>ID: {{ detail.restaurantId }}</mat-card-subtitle>
            <span class="spacer"></span>
            <button mat-icon-button (click)="clearDetail()">
              <mat-icon>close</mat-icon>
            </button>
          </mat-card-header>
          
          <mat-divider></mat-divider>
          
          <mat-card-content>
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
          </mat-card-content>
          
          <mat-card-actions align="end">
            <button mat-button (click)="clearDetail()">Dismiss</button>
            <button mat-raised-button color="primary">Manage Business</button>
          </mat-card-actions>
        </mat-card>
      </div>
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
      background: white;
      border-radius: 8px;
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
    }

    .clickable-row {
      cursor: pointer;
      transition: background 0.2s ease;
    }

    .clickable-row:hover {
      background: #f8fafc;
    }

    .selected-row {
      background: #f1f5f9 !important;
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

    .detail-panel {
      margin-top: 32px;
      animation: slideUp 0.3s ease;
    }

    .detail-card {
      border-radius: 12px;
      border: 1px solid var(--line);
    }

    .detail-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 24px;
      padding: 24px 0;
    }

    .detail-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .detail-item .label {
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--muted);
      font-weight: 600;
    }

    .detail-item .value {
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--ink);
    }

    @keyframes slideUp {
      from { transform: translateY(20px); opacity: 0; }
      to { transform: translateY(0); opacity: 1; }
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

  displayedColumns: string[] = ['shopName', 'ownerName', 'status', 'orderCount', 'menuCount', 'updatedAt', 'actions'];
  dataSource = new MatTableDataSource<AdminBusinessListItem>([]);
  loaded = false;
  loadError = '';
  readonly selectedDetail = signal<AdminBusinessDetail | null>(null);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

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

  showDetails(business: AdminBusinessListItem): void {
    this.selectedDetail.set(null);
    this.api.getBusinessDetail(business.restaurantId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (detail) => { this.selectedDetail.set(detail); }
    });
  }

  clearDetail(): void { this.selectedDetail.set(null); }

  formatDateValue(value: number | null): string { return formatDate(value); }
  formatCurrencyValue(value: number): string { return formatCurrency(value); }
}
