import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal, ViewChild, AfterViewInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMenuItem } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ErrorStateComponent } from '../../shared/error-state.component';

@Component({
  selector: 'app-menu-page',
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
    MatSelectModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatTooltipModule,
    MatDividerModule,
    MatMenuModule,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatDialogModule,
    EmptyStateComponent,
    ErrorStateComponent
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">Menu Catalog</h1>
          <p class="page-subtitle">{{ items.length }} items · {{ availableCount() }} available on App</p>
        </div>
        <button mat-flat-button color="primary" (click)="openAddDialog()" aria-label="Add new menu item">
          <mat-icon>add</mat-icon>
          Add Item
        </button>
      </div>

      <div class="stats-grid" *ngIf="loaded">
        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon total">restaurant_menu</mat-icon>
            <mat-card-title>{{ items.length }}</mat-card-title>
            <mat-card-subtitle>Total Items</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon available">check_circle</mat-icon>
            <mat-card-title>{{ availableCount() }}</mat-card-title>
            <mat-card-subtitle>Live on App</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon low">warning</mat-icon>
            <mat-card-title>{{ lowStockCount() }}</mat-card-title>
            <mat-card-subtitle>Low Inventory</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
      </div>

      <mat-card class="filter-card mat-elevation-z1">
        <mat-card-content class="filter-row">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Find item...</mat-label>
            <mat-icon matPrefix>search</mat-icon>
            <input matInput (keyup)="applyFilter($event)" placeholder="Search by name, category..." #input aria-label="Search menu items">
          </mat-form-field>
          
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Stock Level</mat-label>
            <mat-select [(ngModel)]="stockFilter" (selectionChange)="applyFilters()">
              <mat-option value="ALL">All Levels</mat-option>
              <mat-option value="IN_STOCK">In Stock</mat-option>
              <mat-option value="RUN_LOW">Running Low</mat-option>
              <mat-option value="OUT_OF_STOCK">Out of Stock</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Status</mat-label>
            <mat-select [(ngModel)]="availabilityFilter" (selectionChange)="applyFilters()">
              <mat-option value="ALL">All Status</mat-option>
              <mat-option value="AVAILABLE">Available</mat-option>
              <mat-option value="UNAVAILABLE">Unavailable</mat-option>
            </mat-select>
          </mat-form-field>

          <div class="spacer"></div>
          
          <button mat-button (click)="clearFilters()">Reset</button>
        </mat-card-content>
      </mat-card>

      <div class="table-container mat-elevation-z2">
        <div class="loading-overlay" *ngIf="!loaded">
          <mat-spinner diameter="40"></mat-spinner>
        </div>

        <table mat-table [dataSource]="dataSource" matSort>
          <!-- Item Details -->
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef mat-sort-header class="col-name-header"> Item Details </th>
            <td mat-cell *matCellDef="let item"> 
              <div class="item-cell">
                <div class="item-avatar" [class.veg]="item.foodType === 'VEG'">
                  <mat-icon>{{ item.foodType === 'VEG' ? 'eco' : 'restaurant' }}</mat-icon>
                </div>
                <div class="item-meta">
                  <span class="item-name">{{ item.name }}</span>
                  <span class="item-desc" [matTooltip]="item.description || ''">{{ item.description || 'No description' }}</span>
                </div>
              </div>
            </td>
          </ng-container>

          <!-- Category -->
          <ng-container matColumnDef="categoryName">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Category </th>
            <td mat-cell *matCellDef="let item"> 
              <span class="category-tag">{{ item.categoryName || 'General' }}</span>
            </td>
          </ng-container>

          <!-- Base Price -->
          <ng-container matColumnDef="basePrice">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Base Price </th>
            <td mat-cell *matCellDef="let item" class="price-cell"> 
              <strong>{{ formatCurrencyValue(item.basePrice) }}</strong> 
            </td>
          </ng-container>

          <!-- Inventory Status -->
          <ng-container matColumnDef="availability">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Inventory </th>
            <td mat-cell *matCellDef="let item">
              <span class="stock-status-chip" [class]="getAvailabilityClass(item)">
                {{ item.stockStatus }}
              </span>
            </td>
          </ng-container>

          <!-- App Status (Live/Hidden slide toggle) -->
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef> App Status </th>
            <td mat-cell *matCellDef="let item">
               <div class="status-toggle-wrap">
                 <span class="status-label" [class.on]="item.available">
                   {{ item.available ? 'Live' : 'Hidden' }}
                 </span>
                 <mat-slide-toggle 
                   [checked]="item.available" 
                   (change)="toggleAvailability(item)" 
                   (click)="$event.stopPropagation()"
                   color="primary">
                 </mat-slide-toggle>
               </div>
            </td>
          </ng-container>

          <!-- Actions Column -->
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef class="col-actions-header"> Action </th>
            <td mat-cell *matCellDef="let item" class="actions-cell">
               <button mat-icon-button [matMenuTriggerFor]="menu" (click)="$event.stopPropagation()" aria-label="Menu item actions">
                 <mat-icon>more_vert</mat-icon>
               </button>
               <mat-menu #menu="matMenu" xPosition="before">
                 <button mat-menu-item (click)="openEditDialog(item)">
                   <mat-icon>edit</mat-icon>
                   <span>Edit Item</span>
                 </button>
                 <button mat-menu-item (click)="openStockDialog(item)">
                   <mat-icon>inventory_2</mat-icon>
                   <span>Update Stock</span>
                 </button>
                 <mat-divider></mat-divider>
                 <!-- Mark Available/Unavailable toggles replace raw delete button -->
                  <button mat-menu-item *ngIf="!item.available" (click)="setAvailability(item, true)">
                    <mat-icon>check_circle</mat-icon>
                    <span>Mark as Available</span>
                  </button>
                  <button mat-menu-item *ngIf="item.available" (click)="setAvailability(item, false)">
                    <mat-icon>cancel</mat-icon>
                    <span>Mark as Unavailable</span>
                  </button>
                  <mat-divider></mat-divider>
                  <button mat-menu-item (click)="deleteItem(item)" class="delete-action">
                    <mat-icon color="warn">delete</mat-icon>
                    <span class="delete-text">Delete Item</span>
                  </button>
                </mat-menu>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;" class="item-row"></tr>
        </table>

        <div *ngIf="loaded && !dataSource.data.length && !loadError" class="empty-state-wrapper">
          <app-empty-state icon="search_off" title="No items found" description="Try adjusting your search or filters to see more results."></app-empty-state>
        </div>

        <div *ngIf="loaded && loadError && !items.length" class="empty-state-wrapper">
          <app-error-state icon="error_outline" title="Failed to load menu" [description]="loadError" [retryable]="true" (retry)="loadMenu()"></app-error-state>
        </div>

        <mat-paginator [pageSizeOptions]="[10, 25, 50]" aria-label="Select page of menu items"></mat-paginator>
      </div>
    </div>

    <!-- Dialog Templates -->
    <ng-template #addDialog>
      <h2 mat-dialog-title class="dialog-title">Add Menu Item</h2>
      <mat-dialog-content class="dialog-content">
        <form class="edit-form">
          <div class="form-field-full">
            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Item Name</mat-label>
              <input matInput [(ngModel)]="editItem.name" name="addName" required>
            </mat-form-field>
          </div>

          <div class="form-field-full">
            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Description</mat-label>
              <textarea matInput [(ngModel)]="editItem.description" name="addDesc" rows="3"></textarea>
            </mat-form-field>
          </div>

          <div class="form-row-2">
            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Base Price (₹)</mat-label>
              <input matInput type="number" [(ngModel)]="editItem.basePrice" name="addPrice" required>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Food Type</mat-label>
              <mat-select [(ngModel)]="editItem.foodType" name="addFoodType">
                <mat-option value="VEG">Veg (Green)</mat-option>
                <mat-option value="NON_VEG">Non-Veg (Red)</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
        </form>
      </mat-dialog-content>
      <mat-dialog-actions align="end" class="dialog-actions">
        <button mat-button (click)="closeDialog()">Cancel</button>
        <button mat-flat-button color="primary" [disabled]="savingItem || !editItem.name || !editItem.basePrice" (click)="createItem()">
          <mat-spinner diameter="18" color="accent" *ngIf="savingItem" style="display:inline-block; margin-right:8px;"></mat-spinner>
          <span>Add Item</span>
        </button>
      </mat-dialog-actions>
    </ng-template>

    <ng-template #editDialog>
      <h2 mat-dialog-title class="dialog-title">Edit Menu Item</h2>
      <mat-dialog-content class="dialog-content">
        <form class="edit-form">
          <div class="form-field-full">
            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Item Name</mat-label>
              <input matInput [(ngModel)]="editItem.name" name="name" required>
            </mat-form-field>
          </div>

          <div class="form-field-full">
            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Description</mat-label>
              <textarea matInput [(ngModel)]="editItem.description" name="description" rows="3"></textarea>
            </mat-form-field>
          </div>

          <div class="form-row-2">
            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Base Price (₹)</mat-label>
              <input matInput type="number" [(ngModel)]="editItem.basePrice" name="basePrice" required>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Food Type</mat-label>
              <mat-select [(ngModel)]="editItem.foodType" name="foodType">
                <mat-option value="VEG">Veg (Green)</mat-option>
                <mat-option value="NON_VEG">Non-Veg (Red)</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <div class="form-row-2">
            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Category</mat-label>
              <input matInput [value]="editItem.categoryName || 'General'" name="categoryName" readonly matTooltip="Category is synchronized from POS">
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-100">
              <mat-label>Stock Level</mat-label>
              <mat-select [(ngModel)]="editItem.stockStatus" name="stockStatus">
                <mat-option value="IN_STOCK">In Stock</mat-option>
                <mat-option value="RUNNING_LOW">Running Low</mat-option>
                <mat-option value="OUT_OF_STOCK">Out of Stock</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
        </form>
      </mat-dialog-content>
      <mat-dialog-actions align="end" class="dialog-actions">
        <button mat-button (click)="closeDialog()">Cancel</button>
        <button mat-flat-button color="primary" [disabled]="savingItem" (click)="saveItem()">
          <mat-spinner diameter="18" color="accent" *ngIf="savingItem" style="display:inline-block; margin-right:8px;"></mat-spinner>
          <span>Save Changes</span>
        </button>
      </mat-dialog-actions>
    </ng-template>

    <ng-template #stockDialog>
      <h2 mat-dialog-title class="dialog-title">Update Inventory</h2>
      <mat-dialog-content class="dialog-content">
        <div class="stock-dialog-body">
          <p>Update inventory status for <strong>{{ editItem.name }}</strong></p>
          <mat-form-field appearance="outline" class="w-100" style="margin-top: 12px;">
            <mat-label>Inventory Level</mat-label>
            <mat-select [(ngModel)]="editItem.stockStatus" name="stockStatus">
              <mat-option value="IN_STOCK">In Stock (Available)</mat-option>
              <mat-option value="RUNNING_LOW">Running Low</mat-option>
              <mat-option value="OUT_OF_STOCK">Out of Stock (Hidden/Disabled)</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end" class="dialog-actions">
        <button mat-button (click)="closeDialog()">Cancel</button>
        <button mat-flat-button color="primary" [disabled]="savingItem" (click)="saveItem()">
          <mat-spinner diameter="18" color="accent" *ngIf="savingItem" style="display:inline-block; margin-right:8px;"></mat-spinner>
          <span>Update Status</span>
        </button>
      </mat-dialog-actions>
    </ng-template>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 28px; }
    .page-title { margin: 0; font-size: 2rem; font-weight: 800; color: var(--ink); letter-spacing: -0.5px; }
    .page-subtitle { margin: 4px 0 0; color: var(--muted); font-size: 0.95rem; }

    .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 24px; margin-bottom: 32px; }
    .stat-card { 
      position: relative;
      border-radius: var(--radius-xl); 
      border: 1px solid var(--line); 
      background: var(--bg-elevated);
      box-shadow: var(--shadow-sm); 
      transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1), box-shadow 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      overflow: hidden;
    }
    .stat-card:hover {
      transform: translateY(-4px);
      box-shadow: var(--shadow-lg);
    }
    .stat-icon { 
      width: 52px; 
      height: 52px; 
      line-height: 52px; 
      text-align: center; 
      border-radius: var(--radius-lg); 
      font-size: 26px; 
      transition: transform 0.3s ease;
    }
    .stat-card:hover .stat-icon {
      transform: scale(1.1) rotate(6deg);
    }
    
    .stat-icon.total { background: rgba(2, 132, 199, 0.12); color: #0284c7; }
    .stat-icon.available { background: rgba(34, 197, 94, 0.12); color: #16a34a; }
    .stat-icon.low { background: rgba(245, 158, 11, 0.12); color: #d97706; }

    .filter-card { 
      margin-bottom: 24px; 
      border-radius: var(--radius-xl); 
      border: 1px solid var(--line); 
      background: var(--bg-elevated);
      backdrop-filter: blur(12px);
      box-shadow: var(--shadow-md); 
    }
    .filter-row { display: flex; align-items: center; gap: 16px; padding: 16px 20px !important; }
    .search-field { flex: 1; max-width: 400px; }
    .filter-field { width: 160px; }
    ::ng-deep .filter-row .mat-mdc-form-field-subscript-wrapper { display: none; }
    .spacer { flex: 1; }

    .table-container { 
      position: relative; 
      background: var(--bg-elevated); 
      border-radius: var(--radius-xl); 
      border: 1px solid var(--line);
      box-shadow: var(--shadow-md); 
      overflow: hidden; 
    }
    .loading-overlay { position: absolute; inset: 0; background: rgba(255,255,255,0.7); z-index: 10; display: flex; align-items: center; justify-content: center; }
    table { width: 100%; background: transparent; }

    ::ng-deep table th.mat-mdc-header-cell {
      background: var(--surface) !important;
      font-weight: 700 !important;
      color: var(--ink) !important;
      text-transform: uppercase !important;
      font-size: 0.75rem !important;
      letter-spacing: 0.5px !important;
      border-bottom: 2px solid var(--line) !important;
      padding: 16px !important;
    }
    ::ng-deep table td.mat-mdc-cell {
      padding: 16px !important;
      border-bottom: 1px solid var(--line) !important;
      color: var(--ink-secondary) !important;
      font-size: 0.9rem !important;
    }

    ::ng-deep .mat-column-name { text-align: left !important; width: 35%; }
    ::ng-deep .mat-column-categoryName { text-align: left !important; width: 15%; }
    ::ng-deep .mat-column-basePrice { text-align: left !important; width: 12%; }
    ::ng-deep .mat-column-availability { text-align: left !important; width: 15%; }
    ::ng-deep .mat-column-status { text-align: left !important; width: 15%; }
    ::ng-deep .mat-column-actions { text-align: right !important; width: 8%; }

    ::ng-deep th.col-actions-header { text-align: right !important; }

    .item-row { transition: background 0.2s ease; background: transparent; }
    .item-row:hover { background: var(--panel-hover) !important; }

    .item-cell { display: flex; align-items: center; gap: 12px; }
    .item-avatar { width: 42px; height: 42px; border-radius: var(--radius-lg); background: rgba(239, 68, 68, 0.1); color: #be123c; display: flex; align-items: center; justify-content: center; }
    .item-avatar.veg { background: rgba(34, 197, 94, 0.1); color: #15803d; }
    .item-avatar mat-icon { font-size: 22px; width: 22px; height: 22px; }
    
    .item-meta { display: flex; flex-direction: column; }
    .item-name { font-weight: 700; color: var(--ink); }
    .item-desc { font-size: 0.75rem; color: var(--muted); max-width: 280px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

    .category-tag { background: var(--brand-soft); color: var(--brand); padding: 4px 12px; border-radius: var(--radius-md); font-size: 0.8rem; font-weight: 700; }

    .price-cell { font-weight: 700; color: var(--ink); font-variant-numeric: tabular-nums; }

    .stock-status-chip { padding: 4px 12px; border-radius: 999px; font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; }
    .stock-status-chip.success { background: rgba(34, 197, 94, 0.12); color: #16a34a; }
    .stock-status-chip.warn { background: rgba(245, 158, 11, 0.12); color: #d97706; }
    .stock-status-chip.danger { background: rgba(239, 68, 68, 0.12); color: #dc2626; }

    .status-toggle-wrap { display: flex; align-items: center; gap: 8px; }
    .status-label { font-size: 0.75rem; font-weight: 700; color: var(--muted); text-transform: uppercase; }
    .status-label.on { color: var(--brand); }
    .status-toggle-wrap mat-slide-toggle { transform: scale(0.9); }

    .actions-cell { text-align: right; }
    ::ng-deep .delete-action .delete-text { color: var(--error, #dc2626); }

    .empty-state-wrapper { padding: 48px 24px; }

    /* Dialog Styles */
    .w-100 { width: 100%; }
    .form-row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 8px; }
    .dialog-title { font-weight: 800; color: var(--ink); margin: 0; padding-bottom: 8px; }
    .dialog-content { padding-top: 16px !important; }
    .dialog-actions { padding: 12px 24px !important; }
    .stock-dialog-body p { margin: 0; color: var(--ink-secondary); font-size: 0.95rem; }

    @media (max-width: 768px) {
      .header-row { flex-direction: column; gap: 16px; }
      .filter-row { flex-direction: column; align-items: stretch; }
      .search-field { max-width: none; }
    }
  `]
})
export class MenuPageComponent implements AfterViewInit {
  private readonly api = inject(BusinessApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild('addDialog') addDialogTemplate!: any;
  @ViewChild('editDialog') editDialogTemplate!: any;
  @ViewChild('stockDialog') stockDialogTemplate!: any;

  items: BusinessMenuItem[] = [];
  loaded = false;
  loadError = '';
  dataSource = new MatTableDataSource<BusinessMenuItem>([]);
  displayedColumns = ['name', 'categoryName', 'basePrice', 'availability', 'status', 'actions'];

  searchTerm = '';
  stockFilter: string = 'ALL';
  availabilityFilter: string = 'ALL';

  editItem: any = {};
  editingItemId: number | null = null;
  savingItem = false;
  dialogRef: any = null;

  constructor() {
    this.loadMenu();
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.dataSource.filterPredicate = (data, filter) => {
      const search = filter.toLowerCase();
      const matchesSearch = !search || [
        data.name,
        data.categoryName ?? '',
        data.description ?? '',
        data.foodType ?? ''
      ].some(v => v.toLowerCase().includes(search));

      const matchesStock = this.stockFilter === 'ALL' || data.stockStatus === this.stockFilter;
      const matchesAvailability =
        this.availabilityFilter === 'ALL' ||
        (this.availabilityFilter === 'AVAILABLE' && data.available) ||
        (this.availabilityFilter === 'UNAVAILABLE' && !data.available);

      return matchesSearch && matchesStock && matchesAvailability;
    };
  }

  loadMenu(): void {
    this.loaded = false;
    this.loadError = '';
    this.api.getMenu().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.items = data;
        this.dataSource.data = data;
        this.loaded = true;
      },
      error: (err) => { 
        this.loadError = err?.error?.error ?? err?.error?.message ?? 'Failed to load menu.'; 
        this.loaded = true;
        this.items = [];
        this.dataSource.data = [];
      }
    });
  }

  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }

  applyFilters() {
    const current = this.dataSource.filter;
    this.dataSource.filter = '';
    this.dataSource.filter = current;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.stockFilter = 'ALL';
    this.availabilityFilter = 'ALL';
    this.dataSource.filter = '';
    this.applyFilters();
  }

  openAddDialog(): void {
    this.editingItemId = null;
    this.editItem = { name: '', description: '', basePrice: null, foodType: 'VEG' };
    this.dialogRef = this.dialog.open(this.addDialogTemplate, {
      width: '500px',
      autoFocus: false
    });
  }

  openEditDialog(item: BusinessMenuItem): void {
    this.editingItemId = item.menuItemId;
    this.editItem = { ...item };
    this.dialogRef = this.dialog.open(this.editDialogTemplate, {
      width: '500px',
      autoFocus: false
    });
  }

  openStockDialog(item: BusinessMenuItem): void {
    this.editItem = { ...item };
    this.dialogRef = this.dialog.open(this.stockDialogTemplate, {
      width: '400px',
      autoFocus: false
    });
  }

  closeDialog(): void {
    if (this.dialogRef) {
      this.dialogRef.close();
      this.dialogRef = null;
    }
  }

  createItem(): void {
    this.savingItem = true;
    const payload = {
      name: this.editItem.name,
      description: this.editItem.description || '',
      basePrice: this.editItem.basePrice,
      foodType: this.editItem.foodType || 'VEG'
    };
    this.api.createMenuItem(payload).subscribe({
      next: () => {
        this.snackBar.open('Menu item created successfully', 'Close', { duration: 3000 });
        this.loadMenu();
        this.closeDialog();
        this.savingItem = false;
      },
      error: (err) => {
        this.snackBar.open(err?.error?.error ?? err?.error?.message ?? 'Failed to create menu item', 'Close', { duration: 4000 });
        this.savingItem = false;
      }
    });
  }

  saveItem(): void {
    this.savingItem = true;
    const payload = {
      name: this.editItem.name,
      description: this.editItem.description,
      basePrice: this.editItem.basePrice,
      foodType: this.editItem.foodType,
      stockStatus: this.editItem.stockStatus,
      available: this.editItem.available
    };
    this.api.updateMenuItem(this.editingItemId!, payload).subscribe({
      next: () => {
        this.snackBar.open('Menu item updated successfully', 'Close', { duration: 3000 });
        this.loadMenu();
        this.closeDialog();
        this.savingItem = false;
      },
      error: (err) => {
        this.snackBar.open(err?.error?.error ?? err?.error?.message ?? 'Failed to update menu item', 'Close', { duration: 4000 });
        this.savingItem = false;
      }
    });
  }

  deleteItem(item: BusinessMenuItem): void {
    const confirmed = confirm(`Delete "${item.name}"? This action cannot be undone.`);
    if (!confirmed) return;
    this.api.deleteMenuItem(item.menuItemId).subscribe({
      next: () => {
        this.snackBar.open('Menu item deleted', 'Close', { duration: 3000 });
        this.loadMenu();
      },
      error: (err) => {
        this.snackBar.open(err?.error?.error ?? err?.error?.message ?? 'Failed to delete menu item', 'Close', { duration: 4000 });
      }
    });
  }

  setAvailability(item: BusinessMenuItem, available: boolean): void {
    this.api.updateMenuItemAvailability(item.menuItemId, available).subscribe({
      next: () => {
        this.snackBar.open(`Item marked as ${available ? 'available' : 'unavailable'}`, 'Close', { duration: 3000 });
        this.loadMenu();
      },
      error: (err) => {
        this.snackBar.open(err?.error?.error ?? err?.error?.message ?? 'Failed to update status', 'Close', { duration: 4000 });
      }
    });
  }

  toggleAvailability(item: BusinessMenuItem): void {
    const newStatus = !item.available;
    this.setAvailability(item, newStatus);
  }

  availableCount = () => this.items.filter(i => i.available).length;
  lowStockCount = () => this.items.filter(i => i.stockStatus === 'RUNNING_LOW').length;

  getAvailabilityClass(item: BusinessMenuItem) {
    if (!item.available || item.stockStatus === 'OUT_OF_STOCK') return 'danger';
    if (item.stockStatus === 'RUNNING_LOW' || item.stockStatus === 'RUN_LOW') return 'warn';
    return 'success';
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }
}
