import { CommonModule } from '@angular/common';
import { Component, inject, signal, NgZone, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessCategory, BusinessMenuItem, MenuExtractionItem, MenuExtractionJob } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-menu-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent, ApiStateComponent],
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

      <app-api-state
        *ngIf="loadError"
        [loading]="false"
        [error]="loadError"
        (retry)="loadMenu()"
      ></app-api-state>

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

        <div class="mobile-data-list" aria-label="Menu items">
          <article class="mobile-data-card" *ngFor="let item of pagedItems">
            <div class="mobile-data-card__head"><strong>{{ item.name }}</strong><span class="chip" [class.success]="item.available" [class.danger]="!item.available">{{ item.available ? item.stockStatus : 'Unavailable' }}</span></div>
            <p>{{ item.description || 'No description added yet.' }}</p>
            <dl><div><dt>Category</dt><dd>{{ item.categoryName || '-' }}</dd></div><div><dt>Type</dt><dd>{{ item.foodType || '-' }}</dd></div><div><dt>Price</dt><dd>{{ formatCurrencyValue(item.basePrice) }}</dd></div><div><dt>Variants</dt><dd>{{ item.variantCount }}</dd></div></dl>
            <div class="mobile-data-card__actions" *ngIf="isOwner">
              <button class="ghost-btn" [disabled]="togglingId === item.menuItemId" (click)="toggleAvailability(item)">{{ item.available ? 'Set unavailable' : 'Set available' }}</button>
              <button class="ghost-btn" (click)="openEditModal(item)">Edit</button>
              <button class="ghost-btn danger-btn" (click)="openDeleteConfirm(item)">Delete</button>
            </div>
          </article>
        </div>

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
            *ngIf="!loadError"
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
        <div class="modal-box" role="dialog" aria-modal="true" aria-labelledby="menu-form-title" (click)="$event.stopPropagation()">
          <h3 id="menu-form-title">{{ editingItem ? 'Edit Menu Item' : 'Add Menu Item' }}</h3>
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
            <div style="display:flex;gap:0.5rem;align-items:center;">
              <select class="field-select" [(ngModel)]="formCategoryId" style="flex:1;">
                <option [ngValue]="null" disabled>Select a category</option>
                <option *ngFor="let category of categories" [ngValue]="category.categoryId">
                  {{ category.name }}
                </option>
              </select>
              <button type="button" class="ghost-btn" style="white-space:nowrap;" (click)="showNewCategoryInput = !showNewCategoryInput">
                {{ showNewCategoryInput ? 'Cancel' : '+ New' }}
              </button>
            </div>
            <div *ngIf="showNewCategoryInput" style="margin-top:0.5rem;display:flex;gap:0.5rem;">
              <input
                type="text"
                class="field-control"
                [(ngModel)]="newCategoryName"
                placeholder="New category name"
                style="flex:1;"
              />
              <button
                type="button"
                class="primary-btn"
                [disabled]="!newCategoryName.trim() || creatingCategory"
                (click)="createCategory()"
              >
                {{ creatingCategory ? 'Adding...' : 'Add' }}
              </button>
            </div>
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
    </div>
  `,
  styles: [`
    .ocr-panel { margin-top: var(--kb-space-3); }
    .ocr-state {
      display: flex; align-items: flex-start; gap: var(--kb-space-2);
      margin-top: var(--kb-space-3); padding: var(--kb-space-3) var(--kb-space-4);
      border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-lg);
      background: var(--kb-color-surface-2); color: var(--kb-color-foreground);
    }
    .ocr-state strong, .ocr-state p { margin: 0; }
    .ocr-state p { margin-top: var(--kb-space-1); color: var(--kb-color-muted); font-size: 0.82rem; }
    .ocr-state .spanner { flex: 0 0 auto; margin: var(--kb-space-1) 0 0; }
    .ocr-state--error { border-color: rgba(239,68,68,0.15); background: var(--kb-color-surface-2); color: var(--kb-color-error); }
    .ocr-state--warning { border-color: rgba(245,158,11,0.15); background: var(--kb-color-surface-2); color: var(--kb-color-warning); }
    .result-header { display: flex; align-items: center; gap: var(--kb-space-2); }
    .extracted-table { margin-top: var(--kb-space-2); }
    .hint-text { color: var(--kb-color-muted); font-size: 0.85rem; margin: var(--kb-space-2) 0 0.75rem; }
    .error-text { color: var(--kb-color-error); font-size: 0.85rem; margin: 0.5rem 0 0; }
    .toolbar-actions { display: flex; gap: 0.5rem; align-items: center; }
    .action-stack { display: flex; gap: 0.4rem; align-items: center; flex-wrap: wrap; }
    .toggle-btn {
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      border-radius: var(--kb-radius-md);
      padding: var(--kb-space-1) var(--kb-space-2);
      font-size: 0.78rem;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.15s ease;
    }
    .toggle-btn:disabled { opacity: 0.5; cursor: default; }
    .toggle-btn--on { border-color: var(--kb-color-primary); color: var(--kb-color-primary-foreground); }
    .toggle-btn--off { border-color: var(--kb-color-error); color: var(--kb-color-error); }
    @media (max-width: 480px) {
      .action-stack { flex-direction: column; align-items: stretch; }
    }
  `]
})
export class MenuPageComponent implements OnDestroy {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly zone = inject(NgZone);
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = environment.apiBaseUrl;

  items: BusinessMenuItem[] = [];
  categories: BusinessCategory[] = [];
  loaded = false;
  loadError = '';

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

  // New category inline creation
  showNewCategoryInput = false;
  newCategoryName = '';
  creatingCategory = false;

  // Delete state
  deleteTarget: BusinessMenuItem | null = null;

  // Availability toggle state
  togglingId: number | null = null;

  // OCR state
  uploading = signal(false);
  job = signal<MenuExtractionJob | null>(null);
  extractedItems = signal<MenuExtractionItem[]>([]);
  ocrUploadError = signal('');

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
    this.loadError = '';
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
      error: () => {
        this.items = [];
        this.categories = [];
        this.loadError = 'Unable to load the menu. Check your connection and try again.';
        this.loaded = true;
      }
    });
  }

  // --- Category Creation ---

  createCategory(): void {
    const name = this.newCategoryName.trim();
    if (!name || this.creatingCategory) return;
    this.creatingCategory = true;
    // Use the sync push endpoint to create a category
    const now = Date.now();
    const payload = [{
      name,
      isVeg: null,
      sortOrder: this.categories.length,
      isActive: true,
      localId: now,
      deviceId: 'web-admin',
      restaurantId: 0,
      updatedAt: now,
      createdAt: now,
      isDeleted: false,
      serverUpdatedAt: 0
    }];
    this.http.post<any>(`${this.apiBaseUrl}/sync/menu/categories/push`, payload).subscribe({
      next: () => {
        this.creatingCategory = false;
        this.newCategoryName = '';
        this.showNewCategoryInput = false;
        this.toast.show('Category created successfully', 'success');
        // Reload categories
        this.api.getMenuCategories().subscribe({
          next: (cats) => { this.categories = cats; }
        });
      },
      error: () => {
        this.creatingCategory = false;
        this.toast.show('Failed to create category. Try again.', 'error');
      }
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
    const allowedTypes = ['application/pdf', 'image/jpeg', 'image/png'];
    if (!allowedTypes.includes(file.type)) {
      this.ocrUploadError.set('Choose a PDF, JPG, or PNG file.');
      input.value = '';
      return;
    }
    if (file.size > maxSize) {
      this.ocrUploadError.set('The selected file exceeds the 10 MB limit.');
      input.value = '';
      return;
    }

    this.ocrUploadError.set('');
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
        this.ocrUploadError.set('Upload failed. Check your connection and try again.');
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
    this.ocrUploadError.set('');
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  private showToast(msg: string, type: 'info' | 'error' | 'success' = 'success'): void {
    this.toast.show(msg, type);
  }
}
