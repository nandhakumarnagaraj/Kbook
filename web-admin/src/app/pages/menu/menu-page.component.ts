import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, inject, signal, NgZone, OnDestroy, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { concatMap, forkJoin, from, tap, toArray } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessCategory, BusinessItemVariant, BusinessMenuItem, MenuExtractionItem, MenuExtractionJob } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { HeaderSearchComponent } from '../../shared/header-search.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-menu-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent, PageHeaderComponent, ApiStateComponent, HeaderSearchComponent, RefreshButtonComponent],
  template: `
    <div class="page-shell menu-page-shell">
      <!-- Canonical page header (shared design system) -->
      <app-page-header
        title="Menu &amp; Dishes Catalog"
        subtitle="Manage dish descriptions, variant pricing, and real-time counter stock availability.">
          <app-header-search
            [(value)]="searchTerm"
            (valueChange)="resetPage()"
            placeholder="Search menu items"
            ariaLabel="Search menu items"
            inputId="menu-search"
          />
          <button type="button" class="header-action-btn" *ngIf="isOwner" (click)="openCategoriesModal()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 6h16M4 12h16M4 18h7"/>
            </svg>
            Categories
          </button>
          <button type="button" class="header-action-btn header-action-btn--primary" *ngIf="isOwner" (click)="openAddModal()">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Add New Dish
          </button>
          <app-refresh-button (refresh)="loadMenu()" [loading]="refreshing" ariaLabel="Refresh menu" />
      </app-page-header>

      <!-- Stale refresh banner (prototype pattern) -->
      <div class="stale-banner" *ngIf="loadError && items.length" role="status" aria-live="polite">
        <span>Couldn't update menu. {{ loadError }}</span>
        <button type="button" class="btn-retry" (click)="loadMenu()">Retry</button>
      </div>

      <div class="panel table-wrap" *ngIf="loaded && pagedItems.length; else loading">
        <table class="data-table menu-table">
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
            <tr *ngFor="let item of pagedItems; trackBy: trackByMenuItemId">
              <td>
                <div class="item-cell">
                  <div class="thumb" [class.thumb--empty]="!hasPhoto(item)">
                    <img
                      *ngIf="hasPhoto(item)"
                      [src]="item.imageUrl"
                      [alt]="item.name + ' photo'"
                      loading="lazy"
                      (error)="onImageError(item)"
                    />
                    <span
                      *ngIf="!hasPhoto(item)"
                      class="food-dot"
                      [class.food-dot--veg]="isVeg(item)"
                      [attr.aria-label]="(isVeg(item) ? 'Veg' : 'Non-veg') + ', no photo added'"
                      role="img"
                    ></span>
                  </div>
                  <div class="stacked-meta">
                    <strong>{{ item.name }}</strong>
                    <span class="muted">{{ item.description || 'No description added yet.' }}</span>
                  </div>
                </div>
              </td>
              <td>{{ item.categoryName || '-' }}</td>
              <td>{{ item.foodType || '-' }}</td>
              <td>
                <div class="menu-price-list">
                  <span class="menu-price-row"><span class="menu-price-label">Base</span><strong>{{ formatCurrencyValue(item.basePrice) }}</strong></span>
                  <span class="menu-price-row" *ngFor="let variant of item.variants || []; trackBy: trackByVariantId">
                    <span class="menu-price-label">{{ variant.variantName }}</span><strong>{{ formatCurrencyValue(variant.price) }}</strong>
                  </span>
                </div>
              </td>
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
          <article class="mobile-data-card" *ngFor="let item of pagedItems; trackBy: trackByMenuItemId">
            <div class="mobile-data-card__head">
              <div class="item-cell">
                <div class="thumb" [class.thumb--empty]="!hasPhoto(item)">
                  <img
                    *ngIf="hasPhoto(item)"
                    [src]="item.imageUrl"
                    [alt]="item.name + ' photo'"
                    loading="lazy"
                    (error)="onImageError(item)"
                  />
                  <span
                    *ngIf="!hasPhoto(item)"
                    class="food-dot"
                    [class.food-dot--veg]="isVeg(item)"
                    [attr.aria-label]="(isVeg(item) ? 'Veg' : 'Non-veg') + ', no photo added'"
                    role="img"
                  ></span>
                </div>
                <strong>{{ item.name }}</strong>
              </div>
              <span class="chip" [class.success]="item.available" [class.danger]="!item.available">{{ item.available ? item.stockStatus : 'Unavailable' }}</span>
            </div>
            <p>{{ item.description || 'No description added yet.' }}</p>
            <dl><div><dt>Category</dt><dd>{{ item.categoryName || '-' }}</dd></div><div><dt>Type</dt><dd>{{ item.foodType || '-' }}</dd></div><div><dt>Price</dt><dd><div class="menu-price-list"><span class="menu-price-row"><span class="menu-price-label">Base</span><strong>{{ formatCurrencyValue(item.basePrice) }}</strong></span><span class="menu-price-row" *ngFor="let variant of item.variants || []; trackBy: trackByVariantId"><span class="menu-price-label">{{ variant.variantName }}</span><strong>{{ formatCurrencyValue(variant.price) }}</strong></span></div></dd></div><div><dt>Variants</dt><dd>{{ item.variantCount }}</dd></div></dl>
            <div class="mobile-data-card__actions" *ngIf="isOwner">
              <button class="ghost-btn" [disabled]="togglingId === item.menuItemId" (click)="toggleAvailability(item)">{{ item.available ? 'Set unavailable' : 'Set available' }}</button>
              <button class="ghost-btn" (click)="openEditModal(item)">Edit</button>
              <button class="ghost-btn danger-btn" (click)="openDeleteConfirm(item)">Delete</button>
            </div>
          </article>
        </div>

        <div #menuLoadMoreSentinel class="menu-load-more" aria-live="polite">
          <span *ngIf="hasMoreItems">Scroll to load more menu items</span>
          <span *ngIf="!hasMoreItems && filteredItems.length">All {{ filteredItems.length }} menu items loaded</span>
        </div>
      </div>

      <ng-template #loading>
        <app-api-state
          *ngIf="loadError && !items.length"
          [message]="loadError"
          (retry)="loadMenu()"
        ></app-api-state>
        <div class="panel loading" *ngIf="!loaded && !loadError; else menuEmpty" role="status" aria-busy="true">
          <span class="visually-hidden">Loading menu items...</span>
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4,5]; trackBy: trackByIndex"></div>
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
            <label>Photo</label>
            <div class="photo-picker">
              <div class="thumb" [class.thumb--empty]="!formPhotoPreview">
                <img *ngIf="formPhotoPreview" [src]="formPhotoPreview" [alt]="formName + ' photo'" />
                <span *ngIf="!formPhotoPreview" class="photo-picker__placeholder">No photo</span>
              </div>
              <div class="photo-picker__actions">
                <label class="ghost-btn" [class.ghost-btn--disabled]="formSaving || formPhotoBusy">
                  {{ formPhotoPreview ? 'Replace Photo' : 'Upload Photo' }}
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    [disabled]="formSaving || formPhotoBusy"
                    (change)="onPhotoSelected($event)"
                    hidden
                  />
                </label>
                <button
                  type="button"
                  class="ghost-btn"
                  [class.ghost-btn--danger]="formPhotoRemoving"
                  [disabled]="formSaving || formPhotoBusy || !formPhotoPreview"
                  (click)="onPhotoRemove()"
                >
                  Remove Photo
                </button>
              </div>
            </div>
            <p class="muted" *ngIf="formPhotoRemoving">Photo will be removed when you save.</p>
          </div>

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
                <option *ngFor="let category of categories; trackBy: trackByCategoryId" [ngValue]="category.categoryId">
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
            <label>Base Price (₹)</label>
            <input
              type="number"
              class="field-control"
              [(ngModel)]="formBasePrice"
              placeholder="0.00"
              min="1"
              max="100000"
              step="0.01"
            />
            <span class="muted" *ngIf="!editingItem">Optional when variants are added; the lowest variant price becomes the base price.</span>
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
          <div class="field">
            <label>Availability</label>
            <div style="display:flex;align-items:center;gap:0.6rem;">
              <button
                type="button"
                class="toggle-btn"
                [class.toggle-btn--on]="formAvailable"
                [class.toggle-btn--off]="!formAvailable"
                [disabled]="formSaving"
                (click)="formAvailable = !formAvailable"
              >
                {{ formAvailable ? 'Available' : 'Unavailable' }}
              </button>
              <span class="muted">Controls whether customers can order this dish.</span>
            </div>
          </div>

          <!-- Portion / Size Variants -->
          <div class="field" style="margin-top: 1rem; border-top: 1px solid var(--kb-color-border); padding-top: 1rem;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 0.5rem;">
              <label style="margin:0; font-weight:700;">Portion / Size Variants</label>
              <span class="muted" style="font-size:0.75rem;">(e.g., Half, Full, 250ml, 500ml)</span>
            </div>

            <!-- Existing Variants List -->
            <div *ngIf="editingItem && loadingVariants" class="panel loading" style="padding:0.75rem; font-size:0.82rem;">Loading variants...</div>
            <p *ngIf="editingItem && variantsLoadError" class="error-text" role="alert">
              Couldn't load variant prices. <button type="button" class="ghost-btn" (click)="loadVariants(editingItem.menuItemId)">Retry</button>
            </p>
            <div *ngIf="editingItem && !loadingVariants && itemVariants.length" style="display:flex; flex-direction:column; gap:0.4rem; margin-bottom: 0.75rem;">
              <div *ngFor="let variant of itemVariants; trackBy: trackByVariantId" style="display:flex; align-items:center; justify-content:space-between; padding: 0.45rem 0.75rem; background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border); border-radius: 8px;">
                <div>
                  <strong style="font-size:0.88rem;">{{ variant.variantName }}</strong>
                  <span class="muted" style="margin-left:0.5rem; font-variant-numeric:tabular-nums;">₹{{ variant.price }}</span>
                </div>
                <button
                  type="button"
                  class="ghost-btn danger-btn"
                  style="padding: 0.2rem 0.5rem; font-size: 0.75rem; min-height: 28px;"
                  [disabled]="deletingVariantId === variant.variantId"
                  (click)="deleteVariant(variant)"
                >
                  {{ deletingVariantId === variant.variantId ? 'Deleting...' : 'Remove' }}
                </button>
              </div>
            </div>
            <p class="muted" *ngIf="editingItem && !loadingVariants && !itemVariants.length" style="font-size:0.82rem; margin:0 0 0.5rem;">No portion variants defined for this item yet. Base price applies.</p>
            <div *ngIf="!editingItem && newMenuVariantDrafts.length" style="display:flex; flex-direction:column; gap:0.4rem; margin-bottom:0.75rem;">
              <div *ngFor="let variant of newMenuVariantDrafts; let i = index" style="display:flex; align-items:center; justify-content:space-between; padding:0.45rem 0.75rem; background:var(--kb-color-surface-2); border:1px solid var(--kb-color-border); border-radius:8px;">
                <span><strong>{{ variant.variantName }}</strong><span class="muted" style="margin-left:0.5rem;">{{ formatCurrencyValue(variant.price) }}</span></span>
                <button type="button" class="ghost-btn danger-btn" style="padding:0.2rem 0.5rem; font-size:0.75rem; min-height:28px;" (click)="removeNewMenuVariant(i)">Remove</button>
              </div>
            </div>

            <!-- Add Variant Form -->
            <div style="display:flex; gap:0.4rem; align-items:center;">
              <input
                type="text"
                class="field-control"
                [(ngModel)]="newVariantName"
                placeholder="Variant name (e.g. Full)"
                style="flex: 2; min-height:36px; font-size:0.85rem;"
              />
              <input
                type="number"
                class="field-control"
                [(ngModel)]="newVariantPrice"
                placeholder="Price (₹)"
                min="1"
                max="100000"
                step="0.01"
                style="flex: 1; min-height:36px; font-size:0.85rem;"
              />
              <button
                type="button"
                class="ghost-btn"
                [disabled]="!newVariantName.trim() || !newVariantPrice || newVariantPrice < 1 || newVariantPrice > 100000 || addingVariant"
                (click)="addVariant()"
                style="min-height:36px; padding: 0 0.85rem; font-size:0.82rem;"
              >
                {{ addingVariant ? 'Adding...' : '+ Add Variant' }}
              </button>
            </div>
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

      <!-- Manage Categories Modal -->
      <div class="modal-backdrop" *ngIf="showCategoriesModal" (click)="closeCategoriesModal()">
        <div class="modal-box" role="dialog" aria-modal="true" aria-labelledby="cat-modal-title" (click)="$event.stopPropagation()" style="max-width: 540px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 0.75rem;">
            <h3 id="cat-modal-title" style="margin:0;">Manage Categories</h3>
            <button type="button" class="close-btn" (click)="closeCategoriesModal()" aria-label="Close">✕</button>
          </div>
          <p class="muted" style="margin: -0.25rem 0 1rem; font-size: 0.85rem;">Organize dishes by section. Categories with active items cannot be deleted.</p>

          <!-- Add new category inline -->
          <div style="display:flex; gap:0.5rem; margin-bottom: 1.25rem;">
            <input
              type="text"
              class="field-control"
              [(ngModel)]="newCategoryModalName"
              placeholder="New category name (e.g. Starters, Beverages)"
              style="flex:1;"
              (keydown.enter)="createCategoryFromModal()"
            />
            <button
              type="button"
              class="primary-btn"
              [disabled]="!newCategoryModalName.trim() || creatingCategory"
              (click)="createCategoryFromModal()"
            >
              {{ creatingCategory ? 'Adding...' : 'Add' }}
            </button>
          </div>

          <!-- Category List -->
          <div class="cat-list-wrap" style="max-height: 360px; overflow-y: auto; display: flex; flex-direction: column; gap: 0.5rem;">
            <div *ngFor="let cat of categories; trackBy: trackByCategoryId" class="cat-list-row" style="display:flex; align-items:center; justify-content:space-between; padding: 0.6rem 0.85rem; background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border); border-radius: 10px;">
              <div *ngIf="renamingCategoryId !== cat.categoryId" style="display:flex; align-items:center; gap:0.6rem; flex:1; min-width:0;">
                <strong style="font-size:0.92rem; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">{{ cat.name }}</strong>
                <span class="chip" style="font-size:0.7rem;">{{ getItemsCountInCategory(cat.categoryId) }} items</span>
              </div>
              <div *ngIf="renamingCategoryId === cat.categoryId" style="display:flex; gap:0.4rem; flex:1; margin-right: 0.5rem;">
                <input
                  type="text"
                  class="field-control"
                  [(ngModel)]="renamingCategoryName"
                  style="flex:1; min-height:34px; padding: 0.3rem 0.5rem; font-size: 0.85rem;"
                  (keydown.enter)="saveRenameCategory(cat)"
                />
                <button type="button" class="primary-btn" style="min-height:34px; padding: 0.3rem 0.65rem; font-size: 0.78rem;" (click)="saveRenameCategory(cat)">Save</button>
                <button type="button" class="ghost-btn" style="min-height:34px; padding: 0.3rem 0.5rem; font-size: 0.78rem;" (click)="cancelRenameCategory()">Cancel</button>
              </div>

              <div *ngIf="renamingCategoryId !== cat.categoryId" style="display:flex; gap:0.35rem;">
                <button type="button" class="ghost-btn" style="padding: 0.3rem 0.6rem; font-size: 0.78rem; min-height:32px;" (click)="startRenameCategory(cat)">Rename</button>
                <button type="button" class="ghost-btn danger-btn" style="padding: 0.3rem 0.6rem; font-size: 0.78rem; min-height:32px;" (click)="deleteCategoryPrompt(cat)" [disabled]="getItemsCountInCategory(cat.categoryId) > 0" [title]="getItemsCountInCategory(cat.categoryId) > 0 ? 'Cannot delete category containing dishes' : 'Delete category'">Delete</button>
              </div>
            </div>
            <div *ngIf="!categories.length" style="text-align:center; padding:1.5rem; color: var(--kb-color-muted-foreground); font-size: 0.88rem;">
              No categories configured yet.
            </div>
          </div>

          <div class="modal-actions" style="margin-top: 1.25rem;">
            <button type="button" class="ghost-btn" (click)="closeCategoriesModal()">Done</button>
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
    .menu-page-shell { gap: 1.25rem; }
    /* ── Menu table column rhythm (same standard as orders history) ── */
    .menu-table th,
    .menu-table td {
      padding: 0.85rem 1rem;
      vertical-align: middle;
    }
    .menu-table th:nth-child(5),
    .menu-table td:nth-child(5) {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .menu-table th:nth-child(6),
    .menu-table td:nth-child(6) {
      text-align: center;
    }
    .menu-table th:nth-child(7),
    .menu-table td:nth-child(7) {
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }
    .menu-table th:nth-child(8),
    .menu-table td:nth-child(8) {
      text-align: right;
    }
    .menu-load-more {
      display: flex;
      justify-content: center;
      padding: 1rem;
      color: var(--kb-color-muted-foreground);
      font-size: 0.82rem;
    }
    .photo-picker {
      display: flex;
      align-items: center;
      gap: 0.85rem;
    }
    .photo-picker .thumb {
      width: 64px;
      height: 64px;
      border-radius: var(--r-lg, 12px);
    }
    .photo-picker__placeholder {
      font-size: 0.72rem;
      color: var(--muted);
      text-align: center;
      padding: 0 0.25rem;
      line-height: 1.3;
    }
    .photo-picker__actions {
      display: flex;
      gap: 0.5rem;
      align-items: center;
    }
    .ghost-btn--disabled { opacity: 0.5; cursor: not-allowed; }
    .ghost-btn--danger { color: var(--danger); border-color: var(--danger); }
    
    
    
    
    .data-table td {
      font-variant-numeric: tabular-nums;
    }
    .menu-price-list { display: grid; gap: 0.25rem; min-width: 112px; }
    .menu-price-row { display: flex; align-items: baseline; justify-content: space-between; gap: 0.75rem; }
    .menu-price-label { color: var(--kb-color-muted-foreground); font-size: 0.78rem; }
    .menu-price-row strong { white-space: nowrap; }
    .ocr-panel { margin-top: var(--kb-space-3); }
    .ocr-state {
      display: flex; align-items: flex-start; gap: var(--kb-space-2);
      margin-top: var(--kb-space-3); padding: var(--kb-space-3) var(--kb-space-4);
      border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-lg);
      background: var(--kb-color-surface-2); color: var(--kb-color-foreground);
    }
    .ocr-state strong, .ocr-state p { margin: 0; }
    .ocr-state p { margin-top: var(--kb-space-1); color: var(--kb-color-muted-foreground); font-size: 0.82rem; }
    .ocr-state .spanner { flex: 0 0 auto; margin: var(--kb-space-1) 0 0; }
    .ocr-state--error { border-color: rgba(239,68,68,0.15); background: var(--kb-color-surface-2); color: var(--kb-color-error); }
    .ocr-state--warning { border-color: rgba(245,158,11,0.15); background: var(--kb-color-surface-2); color: var(--kb-color-warning); }
    .result-header { display: flex; align-items: center; gap: var(--kb-space-2); }
    .extracted-table { margin-top: var(--kb-space-2); }
    .hint-text { color: var(--kb-color-muted-foreground); font-size: 0.85rem; margin: var(--kb-space-2) 0 0.75rem; }
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
      transition: transform 120ms var(--ease-out, ease-out), background-color 150ms ease;
    }
    .toggle-btn:active { transform: scale(0.97); }
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
  refreshing = false;
  loadError = '';
  private readonly cdr = inject(ChangeDetectorRef);
  private menuLoadMoreObserver?: IntersectionObserver;

  @ViewChild('menuLoadMoreSentinel')
  set menuLoadMoreSentinel(element: ElementRef<HTMLElement> | undefined) {
    this.menuLoadMoreObserver?.disconnect();
    if (!element || typeof IntersectionObserver === 'undefined') return;
    this.menuLoadMoreObserver = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) this.loadNextMenuItems();
    }, { rootMargin: '120px 0px' });
    this.menuLoadMoreObserver.observe(element.nativeElement);
  }

  searchTerm = '';
  pageSize = 10;
  visibleItemCount = 10;

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
  formAvailable = true;

  // Dish photo state inside the Add/Edit modal
  formPhotoFile: File | null = null;
  formPhotoPreview: string | null = null;
  formPhotoRemoving = false;
  formPhotoBusy = false;

  // New category inline creation
  showNewCategoryInput = false;
  newCategoryName = '';
  creatingCategory = false;

  // Manage Categories modal state
  showCategoriesModal = false;
  newCategoryModalName = '';
  renamingCategoryId: number | null = null;
  renamingCategoryName = '';

  // Portion variants state inside edit modal
  itemVariants: BusinessItemVariant[] = [];
  newMenuVariantDrafts: Array<{ variantName: string; price: number }> = [];
  loadingVariants = false;
  variantsLoadError = false;
  newVariantName = '';
  newVariantPrice: number | null = null;
  addingVariant = false;
  deletingVariantId: number | null = null;

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
  private pendingFormWarning = '';
  private variantLoadSequence = 0;

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

      return matchesSearch;
    });
  }

  get pagedItems(): BusinessMenuItem[] {
    return this.filteredItems.slice(0, this.visibleItemCount);
  }

  get hasMoreItems(): boolean {
    return this.pagedItems.length < this.filteredItems.length;
  }

  loadNextMenuItems(): void {
    if (!this.hasMoreItems) return;
    this.visibleItemCount = Math.min(this.visibleItemCount + this.pageSize, this.filteredItems.length);
    this.cdr.markForCheck();
  }

  constructor() {
    this.loadMenu();
  }

  loadMenu(): void {
    // Same stale-while-refresh contract as Businesses: skeleton only when
    // there is nothing on screen yet, spinner on the refresh button otherwise.
    this.refreshing = true;
    if (!this.items.length) {
      this.loaded = false;
    }
    this.loadError = '';
    forkJoin({
      items: this.api.getMenu(),
      categories: this.api.getMenuCategories()
    }).subscribe({
      next: ({ items, categories }) => {
        this.items = items;
        this.categories = categories;
        this.loaded = true;
        this.refreshing = false;
        this.loadError = '';
        this.visibleItemCount = this.pageSize;
        this.cdr.markForCheck();
      },
      error: () => {
        if (!this.items.length) {
          this.items = [];
          this.categories = [];
        }
        this.loadError = 'Unable to load the menu. Check your connection and try again.';
        this.loaded = true;
        this.refreshing = false;
        this.cdr.markForCheck();
      }
    });
  }

  // --- Category Creation (REST API) ---

  createCategory(): void {
    const name = this.newCategoryName.trim();
    if (!name || this.creatingCategory) return;
    this.creatingCategory = true;
    this.api.createCategory(name).subscribe({
      next: (cat) => {
        this.creatingCategory = false;
        this.newCategoryName = '';
        this.showNewCategoryInput = false;
        this.toast.show('Category created successfully', 'success');
        this.api.getMenuCategories().subscribe({
          next: (cats) => {
            this.categories = cats;
            this.formCategoryId = cat.categoryId;
            this.cdr.markForCheck();
          }
        });
      },
      error: (err) => {
        this.creatingCategory = false;
        const msg = err?.error?.message || 'Failed to create category. Try again.';
        this.toast.show(msg, 'error');
        this.cdr.markForCheck();
      }
    });
  }

  // --- Manage Categories Modal Handlers ---

  openCategoriesModal(): void {
    this.showCategoriesModal = true;
    this.newCategoryModalName = '';
    this.renamingCategoryId = null;
    this.renamingCategoryName = '';
  }

  closeCategoriesModal(): void {
    this.showCategoriesModal = false;
    this.renamingCategoryId = null;
    this.renamingCategoryName = '';
  }

  getItemsCountInCategory(categoryId: number): number {
    return this.items.filter(i => i.categoryId === categoryId).length;
  }

  createCategoryFromModal(): void {
    const name = this.newCategoryModalName.trim();
    if (!name || this.creatingCategory) return;
    this.creatingCategory = true;
    this.api.createCategory(name).subscribe({
      next: (cat) => {
        this.creatingCategory = false;
        this.newCategoryModalName = '';
        this.toast.show(`Category '${cat.name}' created`, 'success');
        this.api.getMenuCategories().subscribe({
          next: (cats) => {
            this.categories = cats;
            this.cdr.markForCheck();
          }
        });
      },
      error: (err) => {
        this.creatingCategory = false;
        const msg = err?.error?.message || 'Failed to create category.';
        this.toast.show(msg, 'error');
        this.cdr.markForCheck();
      }
    });
  }

  startRenameCategory(cat: BusinessCategory): void {
    this.renamingCategoryId = cat.categoryId;
    this.renamingCategoryName = cat.name;
  }

  cancelRenameCategory(): void {
    this.renamingCategoryId = null;
    this.renamingCategoryName = '';
  }

  saveRenameCategory(cat: BusinessCategory): void {
    const newName = this.renamingCategoryName.trim();
    if (!newName || newName === cat.name) {
      this.cancelRenameCategory();
      return;
    }
    this.api.updateCategory(cat.categoryId, newName).subscribe({
      next: (updated) => {
        cat.name = updated.name;
        for (const it of this.items) {
          if (it.categoryId === cat.categoryId) {
            it.categoryName = updated.name;
          }
        }
        this.cancelRenameCategory();
        this.toast.show(`Category renamed to '${updated.name}'`, 'success');
        this.cdr.markForCheck();
      },
      error: (err) => {
        const msg = err?.error?.message || 'Failed to rename category.';
        this.toast.show(msg, 'error');
        this.cdr.markForCheck();
      }
    });
  }

  deleteCategoryPrompt(cat: BusinessCategory): void {
    const count = this.getItemsCountInCategory(cat.categoryId);
    if (count > 0) {
      this.toast.show(`Cannot delete '${cat.name}' because it contains ${count} dish(es). Move or delete the dishes first.`, 'error');
      return;
    }
    this.api.deleteCategory(cat.categoryId).subscribe({
      next: () => {
        this.categories = this.categories.filter(c => c.categoryId !== cat.categoryId);
        this.toast.show(`Category '${cat.name}' deleted`, 'success');
        this.cdr.markForCheck();
      },
      error: (err) => {
        const msg = err?.error?.message || 'Failed to delete category.';
        this.toast.show(msg, 'error');
        this.cdr.markForCheck();
      }
    });
  }

  // --- Variants Management Handlers ---

  loadVariants(menuItemId: number): void {
    this.loadingVariants = true;
    this.variantsLoadError = false;
    const requestSequence = ++this.variantLoadSequence;
    const requestedMenuItemId = menuItemId;
    this.api.getItemVariants(menuItemId).subscribe({
      next: (variants) => {
        const index = this.items.findIndex(item => item.menuItemId === requestedMenuItemId);
        if (index >= 0) {
          this.items[index] = { ...this.items[index], variants, variantCount: variants.length };
          if (this.editingItem?.menuItemId === requestedMenuItemId) {
            this.editingItem = this.items[index];
          }
        }
        if (requestSequence === this.variantLoadSequence) {
          this.itemVariants = variants;
          this.loadingVariants = false;
        }
        this.cdr.markForCheck();
      },
      error: () => {
        if (requestSequence === this.variantLoadSequence) {
          this.loadingVariants = false;
          this.variantsLoadError = true;
        }
        this.cdr.markForCheck();
      }
    });
  }

  addVariant(): void {
    const variantName = this.newVariantName.trim();
    const price = Number(this.newVariantPrice);
    if (!variantName || !Number.isFinite(price) || price < 1 || price > 100000) return;
    if (!this.editingItem) {
      this.newMenuVariantDrafts = [...this.newMenuVariantDrafts, { variantName, price }];
      this.newVariantName = '';
      this.newVariantPrice = null;
      return;
    }
    this.addingVariant = true;
    this.api.createVariant(this.editingItem.menuItemId, variantName, price).subscribe({
      next: (variant) => {
        this.addingVariant = false;
        this.newVariantName = '';
        this.newVariantPrice = null;
        this.toast.show(`Variant ${variant.variantName} added`, 'success');
        this.loadVariants(this.editingItem!.menuItemId);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.addingVariant = false;
        const msg = err?.error?.message || 'Failed to add variant.';
        this.toast.show(msg, 'error');
        this.cdr.markForCheck();
      }
    });
  }

  removeNewMenuVariant(index: number): void {
    this.newMenuVariantDrafts = this.newMenuVariantDrafts.filter((_, i) => i !== index);
  }

  deleteVariant(variant: BusinessItemVariant): void {
    if (!this.editingItem) return;
    this.deletingVariantId = variant.variantId;
    this.api.deleteVariant(this.editingItem.menuItemId, variant.variantId).subscribe({
      next: () => {
        this.deletingVariantId = null;
        this.toast.show(`Variant ${variant.variantName} removed`, 'success');
        this.loadVariants(this.editingItem!.menuItemId);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.deletingVariantId = null;
        const msg = err?.error?.message || 'Failed to delete variant.';
        this.toast.show(msg, 'error');
        this.cdr.markForCheck();
      }
    });
  }

  trackByVariantId = (_: number, variant: BusinessItemVariant) => variant.variantId;

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
    this.formAvailable = true;
    this.itemVariants = [];
    this.newMenuVariantDrafts = [];
    this.newVariantName = '';
    this.newVariantPrice = null;
    this.resetFormPhoto();
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
    this.formAvailable = item.available ?? true;
    this.itemVariants = [];
    this.newMenuVariantDrafts = [];
    this.newVariantName = '';
    this.newVariantPrice = null;
    this.resetFormPhoto(item.imageUrl?.trim() || null);
    this.showFormModal = true;
    this.loadVariants(item.menuItemId);
  }

  closeFormModal(): void {
    this.showFormModal = false;
    this.editingItem = null;
    this.itemVariants = [];
    this.newMenuVariantDrafts = [];
    this.formError = '';
    this.resetFormPhoto();
  }

  private resetFormPhoto(preview?: string | null): void {
    this.formPhotoFile = null;
    this.formPhotoPreview = preview ?? null;
    this.formPhotoRemoving = false;
    this.formPhotoBusy = false;
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
      this.formError = 'Please choose a PNG, JPEG, or WebP image.';
      this.cdr.markForCheck();
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.formError = 'Image must be under 5 MB.';
      this.cdr.markForCheck();
      return;
    }

    this.formError = '';
    const reader = new FileReader();
    reader.onload = () => {
      this.formPhotoPreview = String(reader.result);
      this.formPhotoFile = file;
      this.formPhotoRemoving = false;
      this.cdr.markForCheck();
    };
    reader.readAsDataURL(file);
  }

  onPhotoRemove(): void {
    if (!this.formPhotoPreview) return;
    this.formPhotoFile = null;
    this.formPhotoPreview = null;
    this.formPhotoRemoving = true;
    this.cdr.markForCheck();
  }

  isFormValid(): boolean {
    const basePrice = this.formBasePrice ?? 0;
    const hasValidBasePrice = basePrice >= 1 && basePrice <= 100000;
    const hasPositiveBasePrice = basePrice > 0;
    const hasValidNewVariants = this.newMenuVariantDrafts.length > 0
      && this.newMenuVariantDrafts.every(variant => variant.variantName.trim().length > 0 && variant.price >= 1 && variant.price <= 100000);
    return this.formName.trim().length > 0
      && this.formCategoryId !== null
      && (this.editingItem ? hasPositiveBasePrice : hasValidBasePrice || hasValidNewVariants);
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
          this.persistFormPhoto(updated, 'Menu item updated');
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
        basePrice: (this.formBasePrice != null && this.formBasePrice >= 1 && this.formBasePrice <= 100000)
          ? this.formBasePrice
          : Math.min(...this.newMenuVariantDrafts.map(variant => variant.price)),
        ...(this.formDescription.trim() ? { description: this.formDescription.trim() } : {})
      };

      this.api.createMenuItem(payload).subscribe({
        next: (created) => {
          if (!this.newMenuVariantDrafts.length) {
            this.items = [created, ...this.items];
            this.persistFormPhoto(created, 'Menu item added');
            return;
          }

          const savedVariants: BusinessItemVariant[] = [];
          from(this.newMenuVariantDrafts).pipe(
            concatMap(draft => this.api.createVariant(created.menuItemId, draft.variantName, draft.price).pipe(
              tap(variant => savedVariants.push(variant))
            )),
            toArray()
          ).subscribe({
            next: (variants) => {
              const itemWithVariants = { ...created, variants, variantCount: variants.length };
              this.items = [itemWithVariants, ...this.items];
              this.persistFormPhoto(itemWithVariants, 'Menu item added');
            },
            error: () => {
              const itemWithSavedVariants = { ...created, variants: savedVariants, variantCount: savedVariants.length };
              this.items = [itemWithSavedVariants, ...this.items];
              this.pendingFormWarning = 'Dish was created, but some variants could not be saved. Edit the dish to add the missing variants.';
              this.persistFormPhoto(itemWithSavedVariants, 'Menu item added');
            }
          });
        },
        error: (err) => {
          this.formSaving = false;
          this.formError = err.error?.message || 'Failed to add item. Please try again.';
        }
      });
    }
  }

  private persistFormPhoto(target: BusinessMenuItem, successMessage: string): void {
    if (this.formPhotoRemoving) {
      this.formPhotoBusy = true;
      this.api.deleteMenuItemImage(target.menuItemId).subscribe({
        next: () => { this.applyPhotoToItem(target.menuItemId, null, 0); this.persistAvailability(target, successMessage); },
        error: () => { this.formPhotoBusy = false; this.formSaving = false; this.formError = 'Item saved, but the photo could not be removed. Try again.'; }
      });
      return;
    }
    if (this.formPhotoFile) {
      this.formPhotoBusy = true;
      this.api.uploadMenuItemImage(target.menuItemId, this.formPhotoFile).subscribe({
        next: (res) => { this.applyPhotoToItem(target.menuItemId, res.imageUrl, res.imageVersion); this.persistAvailability(target, successMessage); },
        error: () => { this.formPhotoBusy = false; this.formSaving = false; this.formError = 'Item saved, but the photo could not be uploaded. Try again.'; }
      });
      return;
    }
    this.persistAvailability(target, successMessage);
  }

  private persistAvailability(target: BusinessMenuItem, successMessage: string): void {
    if (this.formAvailable === target.available) {
      this.finishForm(successMessage);
      return;
    }
    this.formPhotoBusy = true;
    this.api.toggleMenuItemAvailability(target.menuItemId).subscribe({
      next: (toggled) => {
        const idx = this.items.findIndex(i => i.menuItemId === target.menuItemId);
        if (idx >= 0) {
          this.items[idx] = { ...this.items[idx], available: toggled.available };
          this.cdr.markForCheck();
        }
        this.finishForm(successMessage);
      },
      error: () => {
        this.formPhotoBusy = false;
        this.formSaving = false;
        this.formError = 'Item saved, but availability could not be updated. Try again.';
      }
    });
  }

  private applyPhotoToItem(menuItemId: number, imageUrl: string | null, imageVersion: number): void {
    const idx = this.items.findIndex(i => i.menuItemId === menuItemId);
    if (idx >= 0) {
      this.items[idx] = { ...this.items[idx], imageUrl: imageUrl ?? null, imageVersion };
      this.cdr.markForCheck();
    }
  }

  private finishForm(successMessage: string): void {
    this.formSaving = false;
    this.formPhotoBusy = false;
    this.closeFormModal();
    const warning = this.pendingFormWarning;
    this.pendingFormWarning = '';
    this.showToast(warning || successMessage, warning ? 'error' : 'success');
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
    this.visibleItemCount = this.pageSize;
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }

  // ─── Dish photo thumbnail ──────────────────────────────────────────────────
  // Items without a photo (or whose photo fails to load) fall back to a veg /
  // non-veg FSSAI dot on a neutral tile, matching the Android MenuItemThumbnail.
  private readonly failedImages = new Set<number>();

  hasPhoto(item: BusinessMenuItem): boolean {
    return !!item.imageUrl && item.imageUrl.trim().length > 0 && !this.failedImages.has(item.menuItemId);
  }

  isVeg(item: BusinessMenuItem): boolean {
    return (item.foodType || '').toLowerCase() === 'veg';
  }

  onImageError(item: BusinessMenuItem): void {
    this.failedImages.add(item.menuItemId);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }

  trackByIndex = (_: number, __: unknown) => _;
  trackByMenuItemId = (_: number, item: BusinessMenuItem) => item.menuItemId;
  trackByCategoryId = (_: number, cat: BusinessCategory) => cat.categoryId;

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
    this.menuLoadMoreObserver?.disconnect();
    this.stopPolling();
  }

  private showToast(msg: string, type: 'info' | 'error' | 'success' = 'success'): void {
    this.toast.show(msg, type);
  }
}
