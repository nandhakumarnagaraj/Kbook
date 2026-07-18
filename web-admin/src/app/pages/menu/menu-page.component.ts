import { CommonModule } from '@angular/common';
import { Component, inject, signal, NgZone } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessCategory, BusinessMenuItem, MenuExtractionItem, MenuExtractionJob } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-menu-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Menu</h2>
        <p class="muted">Current business menu with cleaner alignment for descriptions, pricing, and availability status.</p>
        <div class="hero-meta">
          <span class="chip">Catalog Review</span>
          <span class="chip success">Stock Visibility</span>
          <span class="chip">OCR Import</span>
        </div>
      </section>

      <div class="toolbar">
        <div>
          <h3>Menu Snapshot</h3>
          <p class="muted">Use this list to spot missing descriptions, low stock, and stale updates.</p>
        </div>
        <div class="toolbar-actions">
          <button class="primary-btn" *ngIf="isOwner" (click)="openAddModal()">+ Add Item</button>
          <button class="ghost-btn" (click)="loadMenu()">Refresh</button>
        </div>
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
              <th *ngIf="isOwner">Actions</th>
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
                <span
                  class="chip"
                  [class.success]="item.available"
                  [class.danger]="!item.available"
                  [class.warn]="item.stockStatus === 'RUNNING_LOW'"
                >
                  {{ item.available ? item.stockStatus : 'UNAVAILABLE' }}
                </span>
              </td>
              <td>{{ formatDateValue(item.updatedAt) }}</td>
              <td *ngIf="isOwner">
                <div class="action-stack">
                  <button
                    class="toggle-btn"
                    [class.toggle-btn--on]="item.available"
                    [class.toggle-btn--off]="!item.available"
                    (click)="toggleAvailability(item)"
                    [disabled]="togglingId === item.menuItemId"
                  >
                    {{ item.available ? 'On' : 'Off' }}
                  </button>
                  <button class="ghost-btn" (click)="openEditModal(item)">Edit</button>
                  <button class="ghost-btn danger-btn" (click)="openDeleteConfirm(item)">Delete</button>
                </div>
              </td>
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
        <div class="panel loading" *ngIf="!loaded; else menuEmpty">
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4,5]"></div>
          </div>
        </div>
        <ng-template #menuEmpty>
          <app-empty-state
            icon="🍽️"
            title="No menu items match the current filters"
            text="Try a different search or clear the filters. Owners can also add a new item."
            [actionLabel]="isOwner ? 'Add Item' : ''"
            (action)="openAddModal()"
          ></app-empty-state>
        </ng-template>
      </ng-template>

      <!-- Add/Edit Menu Item Modal -->
      <div class="modal-backdrop" *ngIf="showFormModal" (click)="closeFormModal()">
        <div class="modal-box" (click)="$event.stopPropagation()">
          <h3>{{ editingItem ? 'Edit Menu Item' : 'Add Menu Item' }}</h3>
          <p class="muted" *ngIf="editingItem">Editing: {{ editingItem.name }}</p>

          <div class="field">
            <label>Name *</label>
            <input
              type="text"
              class="field-control"
              [(ngModel)]="formName"
              placeholder="Item name"
            />
          </div>
          <div class="field">
            <label>Category</label>
            <select class="field-select" [(ngModel)]="formCategoryId">
              <option [ngValue]="null" disabled>Select a category</option>
              <option *ngFor="let category of categories" [ngValue]="category.categoryId">
                {{ category.name }}
              </option>
            </select>
          </div>
          <div class="field">
            <label>Food Type *</label>
            <select class="field-select" [(ngModel)]="formFoodType">
              <option value="veg">Veg</option>
              <option value="non-veg">Non-Veg</option>
            </select>
          </div>
          <div class="field">
            <label>Base Price (₹) *</label>
            <input
              type="number"
              class="field-control"
              [(ngModel)]="formBasePrice"
              placeholder="0.00"
              min="0.01"
              step="0.01"
            />
          </div>
          <div class="field">
            <label>Description</label>
            <input
              type="text"
              class="field-control"
              [(ngModel)]="formDescription"
              placeholder="Optional description"
            />
          </div>

          <p class="error-text" *ngIf="formError">{{ formError }}</p>

          <div class="modal-actions">
            <button class="ghost-btn" (click)="closeFormModal()">Cancel</button>
            <button
              class="primary-btn"
              [disabled]="formSaving || !isFormValid()"
              (click)="submitForm()"
            >
              {{ formSaving ? 'Saving...' : (editingItem ? 'Update' : 'Add Item') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Delete Confirmation Dialog -->
      <app-confirm-dialog
        *ngIf="deleteTarget"
        title="Delete Menu Item"
        [message]="'Are you sure you want to delete \\'' + deleteTarget.name + '\\'? This action cannot be undone.'"
        confirmLabel="Delete"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="confirmDelete()"
        (cancelled)="closeDeleteConfirm()"
      ></app-confirm-dialog>

      <section class="panel filter-panel ocr-panel">
        <div class="toolbar">
          <div>
            <h3>Import Menu from File</h3>
            <p class="muted">Upload a menu PDF or image (JPG/PNG, max 10 MB). We extract the item table and show a preview.</p>
          </div>
          <label class="primary-btn upload-btn">
            {{ uploading() ? 'Uploading...' : 'Choose file' }}
            <input type="file" accept=".pdf,.jpg,.jpeg,.png" (change)="onFileSelected($event)" [disabled]="uploading()" hidden />
          </label>
        </div>

        <div class="loading" *ngIf="uploading()">Uploading menu file...</div>

        <div class="ocr-progress" *ngIf="job() && (job()!.status === 'PENDING' || job()!.status === 'PROCESSING')">
          <p class="muted">Extracting items... ({{ job()!.status }})</p>
          <div class="spinner"></div>
        </div>

        <div class="ocr-error" *ngIf="job() && job()!.status === 'FAILED'">
          <p class="error-text">Extraction failed: {{ job()!.errorMessage || 'Unknown error' }}</p>
          <button class="ghost-btn" (click)="resetJob()">Try another file</button>
        </div>

        <div class="ocr-result" *ngIf="job() && job()!.status === 'COMPLETED'">
          <div class="result-header">
            <p class="muted">{{ extractedItems().length }} items extracted.</p>
            <span class="chip">Preview only</span>
          </div>
          <p class="hint-text">
            Review the extracted items below. Importing into the live menu happens on the
            Android POS app (sync). This web preview is read-only for v1.
          </p>

          <div class="panel table-wrap extracted-table" *ngIf="extractedItems().length">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Half</th>
                  <th>Full</th>
                  <th>Price</th>
                  <th>Description</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let row of extractedItems()">
                  <td><strong>{{ row.itemName }}</strong></td>
                  <td>{{ row.halfPrice || '-' }}</td>
                  <td>{{ row.fullPrice || '-' }}</td>
                  <td>{{ row.price || '-' }}</td>
                  <td class="muted">{{ row.description || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="modal-actions">
            <button class="ghost-btn" (click)="resetJob()">Done</button>
          </div>
        </div>
      </section>

    </div>
  `,
  styles: [`
    .ocr-panel { margin-top: 1.25rem; }
    .upload-btn { display: inline-flex; align-items: center; cursor: pointer; }
    .ocr-result { margin-top: 0.5rem; }
    .result-header { display: flex; align-items: center; gap: 0.75rem; }
    .extracted-table { margin-top: 0.75rem; }
    .hint-text { color: var(--muted); font-size: 0.85rem; margin: 0.4rem 0 0.75rem; }
    .error-text { color: var(--danger); font-size: 0.85rem; margin: 0.5rem 0 0; }
    .toolbar-actions { display: flex; gap: 0.5rem; align-items: center; }
    .action-stack { display: flex; gap: 0.4rem; align-items: center; flex-wrap: wrap; }
    .toggle-btn {
      border: 1px solid var(--line, #E2E8F0);
      background: var(--panel, #FFFFFF);
      border-radius: 8px;
      padding: 0.3rem 0.6rem;
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      transition: opacity 0.2s;
    }
    .toggle-btn:disabled { opacity: 0.5; cursor: default; }
    .toggle-btn--on { border-color: var(--accent); color: var(--accent); }
    .toggle-btn--off { border-color: var(--danger, #EF4444); color: var(--danger, #EF4444); }
    @media (max-width: 480px) {
      .action-stack { flex-direction: column; align-items: stretch; }
    }
  `]
})
export class MenuPageComponent {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly zone = inject(NgZone);

  items: BusinessMenuItem[] = [];
  categories: BusinessCategory[] = [];
  loaded = false;

  searchTerm = '';
  stockFilter = 'ALL';
  availabilityFilter = 'ALL';
  pageSize = 10;
  currentPage = 1;

  // Form modal state
  showFormModal = false;
  editingItem: BusinessMenuItem | null = null;
  formName = '';
  formCategoryId: number | null = null;
  formFoodType: 'veg' | 'non-veg' = 'veg';
  formBasePrice: number | null = null;
  formDescription = '';
  formError = '';
  formSaving = false;

  // Delete state
  deleteTarget: BusinessMenuItem | null = null;

  // Availability toggle state
  togglingId: number | null = null;

  // OCR state
  uploading = signal(false);
  job = signal<MenuExtractionJob | null>(null);
  extractedItems = signal<MenuExtractionItem[]>([]);

  private readonly toast = inject(ToastService);

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  get isOwner(): boolean {
    return this.auth.session()?.role === 'OWNER';
  }

  get filteredItems(): BusinessMenuItem[] {
    const search = this.searchTerm.trim().toLowerCase();
    return this.items.filter(item => {
      const matchesSearch = !search || [
        item.name,
        item.categoryName ?? '',
        item.description ?? ''
      ].some(v => v.toLowerCase().includes(search));

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

  constructor() {
    this.loadMenu();
  }

  loadMenu(): void {
    this.loaded = false;
    forkJoin({
      items: this.api.getMenu(),
      categories: this.api.getMenuCategories()
    }).subscribe({
      next: ({ items, categories }) => {
        this.items = items;
        this.categories = categories;
        this.loaded = true;
        this.currentPage = 1;
      },
      error: () => { this.loaded = true; }
    });
  }

  // --- Add/Edit Modal ---

  openAddModal(): void {
    this.editingItem = null;
    this.formName = '';
    this.formCategoryId = null;
    this.formFoodType = 'veg';
    this.formBasePrice = null;
    this.formDescription = '';
    this.formError = '';
    this.formSaving = false;
    this.showFormModal = true;
  }

  openEditModal(item: BusinessMenuItem): void {
    this.editingItem = item;
    this.formName = item.name;
    this.formCategoryId = item.categoryId;
    this.formFoodType = (item.foodType === 'non-veg' ? 'non-veg' : 'veg');
    this.formBasePrice = item.basePrice;
    this.formDescription = item.description || '';
    this.formError = '';
    this.formSaving = false;
    this.showFormModal = true;
  }

  closeFormModal(): void {
    this.showFormModal = false;
    this.editingItem = null;
    this.formError = '';
  }

  isFormValid(): boolean {
    return this.formName.trim().length > 0
      && this.formCategoryId !== null
      && (this.formBasePrice ?? 0) > 0;
  }

  submitForm(): void {
    if (!this.isFormValid() || this.formSaving) return;

    this.formSaving = true;
    this.formError = '';

    const categoryId = this.formCategoryId!;

    if (this.editingItem) {
      const payload = {
        name: this.formName.trim(),
        categoryId,
        foodType: this.formFoodType as 'veg' | 'non-veg',
        basePrice: this.formBasePrice!,
        ...(this.formDescription.trim() ? { description: this.formDescription.trim() } : {})
      };

      this.api.updateMenuItem(this.editingItem.menuItemId, payload).subscribe({
        next: (updated) => {
          const idx = this.items.findIndex(i => i.menuItemId === updated.menuItemId);
          if (idx >= 0) this.items[idx] = updated;
          this.formSaving = false;
          this.closeFormModal();
          this.showToast('Menu item updated');
        },
        error: (err) => {
          this.formSaving = false;
          this.formError = err.error?.message || 'Failed to update item. Please try again.';
        }
      });
    } else {
      const payload = {
        name: this.formName.trim(),
        categoryId,
        foodType: this.formFoodType as 'veg' | 'non-veg',
        basePrice: this.formBasePrice!,
        ...(this.formDescription.trim() ? { description: this.formDescription.trim() } : {})
      };

      this.api.createMenuItem(payload).subscribe({
        next: (created) => {
          this.items = [created, ...this.items];
          this.formSaving = false;
          this.closeFormModal();
          this.showToast('Menu item added');
        },
        error: (err) => {
          this.formSaving = false;
          this.formError = err.error?.message || 'Failed to add item. Please try again.';
        }
      });
    }
  }

  // --- Delete ---

  openDeleteConfirm(item: BusinessMenuItem): void {
    this.deleteTarget = item;
  }

  closeDeleteConfirm(): void {
    this.deleteTarget = null;
  }

  confirmDelete(): void {
    if (!this.deleteTarget) return;

    const id = this.deleteTarget.menuItemId;
    this.deleteTarget = null;

    this.api.deleteMenuItem(id).subscribe({
      next: () => {
        this.items = this.items.filter(i => i.menuItemId !== id);
        this.showToast('Menu item deleted');
      },
      error: () => {
        this.showToast('Failed to delete item', 'error');
      }
    });
  }

  // --- Availability Toggle (optimistic) ---

  toggleAvailability(item: BusinessMenuItem): void {
    if (this.togglingId === item.menuItemId) return;

    this.togglingId = item.menuItemId;
    const previousState = item.available;

    // Optimistic update
    item.available = !item.available;

    this.api.toggleMenuItemAvailability(item.menuItemId).subscribe({
      next: (updated) => {
        const idx = this.items.findIndex(i => i.menuItemId === updated.menuItemId);
        if (idx >= 0) this.items[idx] = updated;
        this.togglingId = null;
        this.showToast(updated.available ? 'Item marked available' : 'Item marked unavailable');
      },
      error: () => {
        // Revert on error
        item.available = previousState;
        this.togglingId = null;
        this.showToast('Failed to update availability', 'error');
      }
    });
  }

  // --- Helpers ---

  // --- Filters and Pagination ---

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

  // --- OCR Upload ---

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const maxSize = 10 * 1024 * 1024;
    if (file.size > maxSize) {
      this.showToast('File exceeds 10 MB limit');
      input.value = '';
      return;
    }

    this.uploading.set(true);
    this.job.set(null);
    this.extractedItems.set([]);

    this.api.uploadMenuFile(file).subscribe({
      next: (res) => {
        this.uploading.set(false);
        input.value = '';
        this.pollJobStatus(res.jobId);
      },
      error: () => {
        this.uploading.set(false);
        input.value = '';
        this.showToast('Upload failed. Please try again.');
      }
    });
  }

  private pollJobStatus(jobId: number): void {
    this.stopPolling();

    const check = () => {
      this.api.getMenuJobStatus(jobId).subscribe({
        next: (j) => {
          this.zone.run(() => {
            this.job.set(j);
            if (j.status === 'COMPLETED') {
              this.stopPolling();
              this.parseExtractedData(j.extractedDataJson);
            } else if (j.status === 'FAILED') {
              this.stopPolling();
            }
          });
        },
        error: () => { this.stopPolling(); }
      });
    };

    check();
    this.pollTimer = setInterval(check, 3000);
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  private parseExtractedData(json: string | null): void {
    if (!json) { this.extractedItems.set([]); return; }
    try {
      const parsed = JSON.parse(json);
      this.extractedItems.set(Array.isArray(parsed) ? parsed : []);
    } catch {
      this.extractedItems.set([]);
    }
  }

  resetJob(): void {
    this.job.set(null);
    this.extractedItems.set([]);
    this.stopPolling();
  }

  private showToast(msg: string, type: 'info' | 'error' | 'success' = 'success'): void {
    this.toast.show(msg, type);
  }
}
