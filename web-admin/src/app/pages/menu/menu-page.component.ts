import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMenuItem } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-menu-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Menu</h2>
        <p class="muted">Current business menu with cleaner alignment for descriptions, pricing, and availability status.</p>
        <div class="hero-meta">
          <span class="chip">Catalog Review</span>
          <span class="chip success">Stock Visibility</span>
        </div>
      </section>

      <div class="toolbar">
        <div>
          <h3>Menu Snapshot</h3>
          <p class="muted">Use this list to spot missing descriptions, low stock, and stale updates.</p>
        </div>
        <button class="ghost-btn" (click)="loadMenu()">Refresh</button>
      </div>

      <section class="panel filter-panel" *ngIf="loaded && items.length">
        <div class="filter-grid">
          <div class="filter-group">
            <label for="menu-search">Search</label>
            <input
              id="menu-search"
              class="field-control"
              type="text"
              [(ngModel)]="searchTerm"
              (ngModelChange)="resetPage()"
              placeholder="Search by item, category, or description"
            />
          </div>
          <div class="filter-group">
            <label for="menu-stock">Stock</label>
            <select
              id="menu-stock"
              class="field-select"
              [(ngModel)]="stockFilter"
              (ngModelChange)="resetPage()"
            >
              <option value="ALL">All stock states</option>
              <option value="IN_STOCK">In stock</option>
              <option value="RUNNING_LOW">Running low</option>
              <option value="OUT_OF_STOCK">Out of stock</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="menu-availability">Availability</label>
            <select
              id="menu-availability"
              class="field-select"
              [(ngModel)]="availabilityFilter"
              (ngModelChange)="resetPage()"
            >
              <option value="ALL">All items</option>
              <option value="AVAILABLE">Available</option>
              <option value="UNAVAILABLE">Unavailable</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="menu-size">Rows</label>
            <select
              id="menu-size"
              class="field-select"
              [(ngModel)]="pageSize"
              (ngModelChange)="resetPage()"
            >
              <option [ngValue]="5">5</option>
              <option [ngValue]="10">10</option>
              <option [ngValue]="20">20</option>
            </select>
          </div>
        </div>

        <div class="filter-summary">
          <p class="muted">{{ filteredItems.length }} of {{ items.length }} menu items</p>
          <button class="ghost-btn" (click)="clearFilters()">Clear filters</button>
        </div>
      </section>

      <div class="panel table-wrap" *ngIf="loaded && pagedItems.length; else loading">
        <table class="data-table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Category</th>
              <th>Type</th>
              <th>Price</th>
              <th>Variants</th>
              <th>Availability</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let item of pagedItems">
              <td>
                <div class="stacked-meta">
                  <strong>{{ item.name }}</strong>
                  <span class="muted">{{ item.description || 'No description added yet.' }}</span>
                </div>
              </td>
              <td>{{ item.categoryName || '-' }}</td>
              <td>{{ item.foodType || '-' }}</td>
              <td>{{ formatCurrencyValue(item.basePrice) }}</td>
              <td>{{ item.variantCount }}</td>
              <td>
                <span class="chip" [class.success]="item.available" [class.warn]="item.stockStatus === 'RUNNING_LOW'" [class.danger]="item.stockStatus === 'OUT_OF_STOCK'">
                  {{ item.stockStatus }}
                </span>
              </td>
              <td>{{ formatDateValue(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>

        <div class="pagination-bar" *ngIf="filteredItems.length > pageSize">
          <p class="muted">Page {{ currentPage }} of {{ totalPages }}</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="currentPage === 1" (click)="goToPage(currentPage - 1)">Previous</button>
            <button class="ghost-btn" [disabled]="currentPage === totalPages" (click)="goToPage(currentPage + 1)">Next</button>
          </div>
        </div>
      </div>

      <ng-template #loading>
        <div class="panel loading">{{ loaded ? 'No menu items match the current filters.' : 'Loading menu...' }}</div>
      </ng-template>
    </div>
  `
})
export class MenuPageComponent {
  private readonly api = inject(BusinessApiService);

  items: BusinessMenuItem[] = [];
  loaded = false;

  searchTerm = '';
  stockFilter: 'ALL' | 'IN_STOCK' | 'RUNNING_LOW' | 'OUT_OF_STOCK' = 'ALL';
  availabilityFilter: 'ALL' | 'AVAILABLE' | 'UNAVAILABLE' = 'ALL';
  pageSize = 10;
  currentPage = 1;

  constructor() {
    this.loadMenu();
  }

  get filteredItems(): BusinessMenuItem[] {
    const search = this.searchTerm.trim().toLowerCase();

    return this.items.filter((item) => {
      const matchesSearch = !search || [
        item.name,
        item.categoryName ?? '',
        item.description ?? '',
        item.foodType ?? ''
      ].some((value) => value.toLowerCase().includes(search));

      const matchesStock = this.stockFilter === 'ALL' || item.stockStatus === this.stockFilter;
      const matchesAvailability =
        this.availabilityFilter === 'ALL' ||
        (this.availabilityFilter === 'AVAILABLE' && item.available) ||
        (this.availabilityFilter === 'UNAVAILABLE' && !item.available);

      return matchesSearch && matchesStock && matchesAvailability;
    });
  }

  get pagedItems(): BusinessMenuItem[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredItems.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredItems.length / this.pageSize));
  }

  loadMenu(): void {
    this.loaded = false;
    this.api.getMenu().subscribe({
      next: (data) => {
        this.items = data;
        this.loaded = true;
        this.currentPage = 1;
      },
      error: () => { this.loaded = true; }
    });
  }

  resetPage(): void {
    this.currentPage = 1;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.stockFilter = 'ALL';
    this.availabilityFilter = 'ALL';
    this.pageSize = 10;
    this.currentPage = 1;
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(1, page), this.totalPages);
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }
}
