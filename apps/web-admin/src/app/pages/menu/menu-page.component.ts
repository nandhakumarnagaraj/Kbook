import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, inject, signal, NgZone, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { catchError, concatMap, forkJoin, from, map, of, tap, toArray } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessCategory, BusinessItemVariant, BusinessMenuItem, MenuExtractionItem, MenuExtractionJob, UpdateMenuItemRequest } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { HeaderSearchComponent } from '../../shared/header-search.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { FocusTrapDirective } from '../../core/components/focus-trap.directive';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-menu-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent, PageHeaderComponent, ApiStateComponent, HeaderSearchComponent, RefreshButtonComponent, KpiRowComponent, PaginationBarComponent, FocusTrapDirective],
  template: `
    <div class="page-shell">
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
          <!--
            The "wishlist": one place that shows what the shop has starred, instead of making
            the owner scroll the whole catalog looking for stars. Reads isFavourite from the
            already-loaded lists - no extra request.
          -->
          <button
            type="button"
            class="header-action-btn fav-toggle"
            [class.fav-toggle--on]="favouritesOnly"
            [attr.aria-pressed]="favouritesOnly"
            (click)="toggleFavouritesOnly()"
            [title]="favouritesOnly ? 'Show the whole menu' : 'Show only starred dishes and pinned categories'"
          >
            <span aria-hidden="true">{{ favouritesOnly ? '★' : '☆' }}</span>
            Favourites
            <span class="fav-toggle__count" *ngIf="favouriteCount">{{ favouriteCount }}</span>
          </button>
          <button type="button" class="header-action-btn" *ngIf="isOwner" (click)="openCategoriesModal()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 6h16M4 12h16M4 18h7"/>
            </svg>
            Categories
          </button>
          <button type="button" class="header-action-btn header-action-btn--primary" *ngIf="isOwner" (click)="openAddModal()">
            Add New Dish
          </button>
          <app-refresh-button (refresh)="loadMenu()" [loading]="refreshing" ariaLabel="Refresh menu" />
      </app-page-header>

      <!-- Stale refresh banner (prototype pattern) -->
      <div class="stale-banner" *ngIf="loadError && items.length" role="status" aria-live="polite">
        <span>Couldn't update menu. {{ loadError }}</span>
        <button type="button" class="btn-retry" (click)="loadMenu()">Retry</button>
      </div>

      <!-- Summary: every figure is counted from the menu already loaded, so it
           cannot drift from the table below it. -->
      <app-kpi-row *ngIf="loaded" [items]="menuKpis" ariaLabel="Menu summary"></app-kpi-row>

      <!-- Filters narrow the same list that search and Favourites narrow. They sit
           above the table so they stay reachable when nothing matches. -->
      <section class="menu-filters" *ngIf="loaded" aria-label="Filter menu items">
        <div class="menu-filters__group" role="group" aria-label="Filter by availability">
          <button
            type="button"
            class="filter-chip"
            [class.filter-chip--active]="statusFilter === 'all'"
            [attr.aria-pressed]="statusFilter === 'all'"
            (click)="setStatusFilter('all')">
            All items <span class="filter-chip__count">{{ items.length }}</span>
          </button>
          <button
            type="button"
            class="filter-chip"
            [class.filter-chip--active]="statusFilter === 'available'"
            [attr.aria-pressed]="statusFilter === 'available'"
            (click)="setStatusFilter('available')">
            Available <span class="filter-chip__count">{{ availableCount }}</span>
          </button>
          <button
            type="button"
            class="filter-chip"
            [class.filter-chip--active]="statusFilter === 'out'"
            [attr.aria-pressed]="statusFilter === 'out'"
            (click)="setStatusFilter('out')">
            Out of stock <span class="filter-chip__count">{{ outOfStockCount }}</span>
          </button>
        </div>

        <div class="menu-filters__group">
          <label class="menu-filters__label" for="menu-category-filter">Category</label>
          <select
            id="menu-category-filter"
            class="menu-filter-select"
            [ngModel]="categoryFilterId"
            (ngModelChange)="setCategoryFilter($event)">
            <option [ngValue]="null">All categories</option>
            <option *ngFor="let cat of categories; trackBy: trackByCategoryId" [ngValue]="cat.categoryId">
              {{ cat.name }}
            </option>
          </select>
        </div>

        <button type="button" class="menu-filters__reset" *ngIf="filtersActive" (click)="clearFilters()">
          Clear filters
        </button>
      </section>

      <!-- Favourites panel: pinned categories first, then the starred dishes in the table below -->
      <div class="panel fav-panel" *ngIf="favouritesOnly && loaded" aria-live="polite">
        <div class="fav-panel__head">
          <strong>Favourites</strong>
          <span class="muted">
            {{ favouriteItems.length }} starred {{ favouriteItems.length === 1 ? 'dish' : 'dishes' }} ·
            {{ favouriteCategories.length }} pinned {{ favouriteCategories.length === 1 ? 'category' : 'categories' }}
          </span>
        </div>
        <div class="fav-chips" *ngIf="favouriteCategories.length">
          <span class="fav-chip" *ngFor="let cat of favouriteCategories; trackBy: trackByCategoryId">★ {{ cat.name }}</span>
        </div>
        <p class="fav-panel__hint" *ngIf="!favouriteItems.length && !favouriteCategories.length">
          Nothing starred yet. Tap ☆ on a dish row to add it here, or pin a category from the Categories dialog.
        </p>
      </div>

      <div class="panel table-wrap" *ngIf="loaded && pagedItems.length; else loading">
        <table class="data-table menu-table">
          <caption class="visually-hidden">
            Menu items with their category, dietary type, every price variant, availability and last update
          </caption>
          <thead>
            <tr>
              <th scope="col">Item</th>
              <th scope="col">Category</th>
              <th scope="col">Type</th>
              <th scope="col">Price</th>
              <th scope="col">Availability</th>
              <th scope="col">Updated</th>
              <th scope="col" *ngIf="isOwner">Actions</th>
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
              <td>
                <span class="type-badge">{{ item.foodType || 'Not set' }}</span>
              </td>
              <!-- Base is the anchor (one weight up); the variants below it are the
                   detail. They share one label/value grid, so a 1-variant and a
                   4-variant dish still line up on the same two columns. -->
              <td>
                <div class="menu-price-list">
                  <span class="menu-price-row menu-price-row--base"><span class="menu-price-label">Base</span><strong>{{ formatCurrencyValue(item.basePrice) }}</strong></span>
                  <span class="menu-price-row" *ngFor="let variant of item.variants || []; trackBy: trackByVariantId">
                    <span class="menu-price-label">{{ variant.variantName }}</span><strong>{{ formatCurrencyValue(variant.price) }}</strong>
                  </span>
                </div>
              </td>
              <!-- Status lives in its own column and is also the control that
                   changes it, so the row does not state availability twice. -->
              <td>
                <button
                  type="button"
                  class="chip chip--status"
                  [class.success]="item.available && item.stockStatus !== 'OUT_OF_STOCK' && item.stockStatus !== 'RUNNING_LOW'"
                  [class.danger]="!item.available || item.stockStatus === 'OUT_OF_STOCK'"
                  [class.warn]="item.available && item.stockStatus === 'RUNNING_LOW'"
                  [disabled]="togglingId === item.menuItemId"
                  [attr.aria-pressed]="item.available"
                  [title]="item.available ? 'Mark ' + item.name + ' unavailable' : 'Mark ' + item.name + ' available'"
                  (click)="toggleAvailability(item)"
                >
                  {{ item.available ? stockStatusLabel(item.stockStatus) : 'Unavailable' }}
                </button>
              </td>
              <td>
                <span [title]="formatDateValue(item.updatedAt)">{{ formatDateValue(item.updatedAt) }}</span>
              </td>
              <td *ngIf="isOwner">
                <div class="action-stack">
                  <button
                    type="button"
                    class="icon-btn fav-btn"
                    [class.fav-btn--on]="item.favourite"
                    [attr.aria-pressed]="!!item.favourite"
                    [attr.aria-label]="item.favourite ? 'Starred - Remove ' + item.name + ' from favourites' : 'Star - Mark ' + item.name + ' as favourite'"
                    [title]="item.favourite ? 'Starred: leads its category at the counter' : 'Star this dish so it leads its category at the counter'"
                    [disabled]="togglingItemFavouriteId === item.menuItemId"
                    (click)="toggleItemFavourite(item)"
                  >{{ item.favourite ? '★' : '☆' }}</button>
                  <button class="action-btn" (click)="openEditModal(item)">Edit</button>
                  <button class="action-btn action-btn--danger" (click)="openDeleteConfirm(item)">Delete</button>
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
              <span
                class="chip"
                [class.success]="item.available && item.stockStatus !== 'OUT_OF_STOCK' && item.stockStatus !== 'RUNNING_LOW'"
                [class.danger]="!item.available || item.stockStatus === 'OUT_OF_STOCK'"
                [class.warn]="item.available && item.stockStatus === 'RUNNING_LOW'"
              >{{ item.available ? stockStatusLabel(item.stockStatus) : 'Unavailable' }}</span>
            </div>
            <p>{{ item.description || 'No description added yet.' }}</p>
            <dl><div><dt>Category</dt><dd>{{ item.categoryName || '-' }}</dd></div><div><dt>Type</dt><dd>{{ item.foodType || '-' }}</dd></div><div><dt>Price</dt><dd><div class="menu-price-list"><span class="menu-price-row"><span class="menu-price-label">Base</span><strong>{{ formatCurrencyValue(item.basePrice) }}</strong></span><span class="menu-price-row" *ngFor="let variant of item.variants || []; trackBy: trackByVariantId"><span class="menu-price-label">{{ variant.variantName }}</span><strong>{{ formatCurrencyValue(variant.price) }}</strong></span></div></dd></div></dl>
            <div class="mobile-data-card__actions" *ngIf="isOwner">
              <button
                type="button"
                class="ghost-btn fav-btn"
                [class.fav-btn--on]="item.favourite"
                [attr.aria-pressed]="!!item.favourite"
                [attr.aria-label]="item.favourite ? 'Starred - Remove ' + item.name + ' from favourites' : 'Star - Mark ' + item.name + ' as favourite'"
                [disabled]="togglingItemFavouriteId === item.menuItemId"
                (click)="toggleItemFavourite(item)"
              >{{ item.favourite ? '★ Starred' : '☆ Star' }}</button>
              <button class="ghost-btn" [disabled]="togglingId === item.menuItemId" (click)="toggleAvailability(item)">{{ item.available ? 'Mark unavailable' : 'Make available' }}</button>
              <button class="ghost-btn" (click)="openEditModal(item)">Edit</button>
              <button class="ghost-btn danger-btn" (click)="openDeleteConfirm(item)">Delete</button>
            </div>
          </article>
        </div>

        <app-pagination-bar
          *ngIf="filteredItems.length > 0"
          [currentPage]="currentPage"
          [pageSize]="pageSize"
          [totalItems]="filteredItems.length"
          (pageChange)="goToPage($event)"
        />
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
            <!-- Photo tile, dish name, description, price and availability:
                 one empty shape each. No dish photo, name or price is shown
                 before it loads. -->
            <div class="skeleton-row skeleton-row--detail" *ngFor="let i of [1,2,3,4,5]; trackBy: trackByIndex">
              <span class="skeleton skeleton-thumb"></span>
              <span class="skeleton skeleton-line skeleton-line--name"></span>
              <span class="skeleton skeleton-line skeleton-line--grow"></span>
              <span class="skeleton skeleton-line skeleton-line--chip"></span>
              <span class="skeleton skeleton-line skeleton-line--action"></span>
            </div>
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
      <div class="modal-backdrop" *ngIf="showFormModal" appFocusTrap (escape)="closeFormModal()" (click)="closeFormModal()">
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
            <div class="row menu-category-picker">
              <select class="field-select flex-1" [(ngModel)]="formCategoryId">
                <option [ngValue]="null" disabled>Select a category</option>
                <option *ngFor="let category of categories; trackBy: trackByCategoryId" [ngValue]="category.categoryId">
                  {{ category.name }}
                </option>
              </select>
              <button type="button" class="ghost-btn new-category-toggle" (click)="showNewCategoryInput = !showNewCategoryInput">
                {{ showNewCategoryInput ? 'Cancel' : '+ New' }}
              </button>
            </div>
            <div class="new-category-row" *ngIf="showNewCategoryInput">
              <input
                type="text"
                class="field-control flex-1"
                [(ngModel)]="newCategoryName"
                placeholder="New category name"
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
            <div class="row availability-row">
              <button
                type="button"
                class="availability-toggle"
                [class.availability-toggle--on]="formAvailable"
                [class.availability-toggle--off]="!formAvailable"
                [disabled]="formSaving"
                (click)="formAvailable = !formAvailable"
                [attr.aria-pressed]="formAvailable"
                [attr.aria-label]="'Item availability: ' + (formAvailable ? 'Available' : 'Unavailable')"
              >
                {{ formAvailable ? 'Available' : 'Unavailable' }}
              </button>
              <span class="muted">Controls whether customers can order this dish.</span>
            </div>
          </div>

          <!-- Portion / Size Variants -->
          <div class="field variants-section">
            <div class="row row--between variants-head">
              <label class="variants-label">Portion / Size Variants</label>
              <span class="muted variants-hint">(e.g., Half, Full, 250ml, 500ml)</span>
            </div>

            <!-- Existing Variants List -->
            <div *ngIf="editingItem && loadingVariants" class="panel loading variants-loading">Loading variants...</div>
            <p *ngIf="editingItem && variantsLoadError" class="error-text" role="alert">
              Couldn't load variant prices. <button type="button" class="ghost-btn" (click)="loadVariants(editingItem.menuItemId)">Retry</button>
            </p>
            <div class="stack variant-list" *ngIf="editingItem && !loadingVariants && itemVariants.length">
              <div class="row row--between variant-row" *ngFor="let variant of itemVariants; trackBy: trackByVariantId">
                <div>
                  <strong class="variant-name">{{ variant.variantName }}</strong>
                  <span class="muted variant-price">₹{{ variant.price }}</span>
                </div>
                <button
                  type="button"
                  class="ghost-btn danger-btn variant-remove"
                  [disabled]="deletingVariantId === variant.variantId"
                  (click)="deleteVariant(variant)"
                >
                  {{ deletingVariantId === variant.variantId ? 'Deleting...' : 'Remove' }}
                </button>
              </div>
            </div>
            <p class="muted variants-empty" *ngIf="editingItem && !loadingVariants && !itemVariants.length">No portion variants defined for this item yet. Base price applies.</p>
            <div class="stack variant-list" *ngIf="!editingItem && newMenuVariantDrafts.length">
              <div class="row row--between variant-row" *ngFor="let variant of newMenuVariantDrafts; let i = index">
                <span><strong>{{ variant.variantName }}</strong><span class="muted draft-price">{{ formatCurrencyValue(variant.price) }}</span></span>
                <button type="button" class="ghost-btn danger-btn variant-remove" (click)="removeNewMenuVariant(i)">Remove</button>
              </div>
            </div>

            <!-- Add Variant Form -->
            <div class="row variant-add-form">
              <input
                type="text"
                class="field-control variant-input variant-input--name"
                [(ngModel)]="newVariantName"
                placeholder="Variant name (e.g. Full)"
              />
              <input
                type="number"
                class="field-control variant-input variant-input--price"
                [(ngModel)]="newVariantPrice"
                placeholder="Price (₹)"
                min="1"
                max="100000"
                step="0.01"
              />
              <button
                type="button"
                class="ghost-btn variant-add-btn"
                [disabled]="!newVariantName.trim() || !newVariantPrice || newVariantPrice < 1 || newVariantPrice > 100000 || addingVariant"
                (click)="addVariant()"
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
      <div class="modal-backdrop" *ngIf="showCategoriesModal" appFocusTrap (escape)="closeCategoriesModal()" (click)="closeCategoriesModal()">
        <div class="modal-box categories-modal" role="dialog" aria-modal="true" aria-labelledby="cat-modal-title" (click)="$event.stopPropagation()">
          <div class="row row--between menu-modal-head">
            <h3 class="menu-modal-heading" id="cat-modal-title">Manage Categories</h3>
            <button type="button" class="icon-btn" (click)="closeCategoriesModal()" aria-label="Close">✕</button>
          </div>
          <p class="muted modal-intro">Organize dishes by section. Categories with active items cannot be deleted. Star a category to pin it to the top of the list on the POS terminals.</p>

          <!-- Add new category inline -->
          <div class="category-create-row">
            <input
              type="text"
              class="field-control flex-1"
              [(ngModel)]="newCategoryModalName"
              placeholder="New category name (e.g. Starters, Beverages)"
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
          <div class="cat-list-wrap stack">
            <div *ngFor="let cat of categories; trackBy: trackByCategoryId" class="cat-list-row row row--between">
              <div class="row cat-name-row flex-1" *ngIf="renamingCategoryId !== cat.categoryId">
                <button
                  type="button"
                  class="icon-btn cat-fav-btn"
                  [class.cat-fav-btn--on]="cat.isFavourite"
                  [attr.aria-pressed]="!!cat.isFavourite"
                  [attr.aria-label]="cat.isFavourite ? 'Remove ' + cat.name + ' from favourites' : 'Mark ' + cat.name + ' as favourite'"
                  [title]="cat.isFavourite ? 'Pinned to the top of the POS list' : 'Pin to the top of the POS list'"
                  [disabled]="togglingFavouriteId === cat.categoryId"
                  (click)="toggleCategoryFavourite(cat)"
                >{{ cat.isFavourite ? '★' : '☆' }}</button>
                <strong class="cat-name"
                  [title]="cat.name">{{ cat.name }}</strong>
                <span class="chip cat-count">{{ categoryItemCountLabel(cat.categoryId) }}</span>
              </div>
              <div class="cat-edit-row flex-1" *ngIf="renamingCategoryId === cat.categoryId">
                <input
                  type="text"
                  class="field-control flex-1 cat-rename-input"
                  [(ngModel)]="renamingCategoryName"
                  (keydown.enter)="saveRenameCategory(cat)"
                />
                <button type="button" class="primary-btn cat-btn cat-btn--save" (click)="saveRenameCategory(cat)">Save</button>
                <button type="button" class="ghost-btn cat-btn cat-btn--cancel" (click)="cancelRenameCategory()">Cancel</button>
              </div>

              <div class="cat-actions" *ngIf="renamingCategoryId !== cat.categoryId">
                <button type="button" class="ghost-btn cat-action-btn" *ngIf="getItemsCountInCategory(cat.categoryId) > 0" (click)="startBulkMove(cat)" [title]="'Move every dish out of ' + cat.name + ' into another category'">Move dishes</button>
                <button type="button" class="ghost-btn cat-action-btn" (click)="startRenameCategory(cat)">Rename</button>
                <button type="button" class="ghost-btn danger-btn cat-action-btn" (click)="deleteCategoryPrompt(cat)" [disabled]="getItemsCountInCategory(cat.categoryId) > 0" [title]="getItemsCountInCategory(cat.categoryId) > 0 ? 'Cannot delete category containing dishes' : 'Delete category'">Delete</button>
              </div>
            </div>
            <div class="cat-empty" *ngIf="!categories.length">
              No categories configured yet.
            </div>
          </div>

          <div class="modal-actions modal-actions--spaced">
            <button type="button" class="ghost-btn" (click)="closeCategoriesModal()">Done</button>
          </div>
        </div>
      </div>

      <!-- Bulk move: the way out of "cannot delete a category containing dishes" -->
      <div class="modal-backdrop" *ngIf="bulkMoveSource" appFocusTrap (escape)="cancelBulkMove()" (click)="cancelBulkMove()">
        <div class="modal-box bulk-modal" role="dialog" aria-modal="true" aria-labelledby="bulk-move-title" (click)="$event.stopPropagation()">
          <div class="row row--between menu-modal-head">
            <h3 class="menu-modal-heading" id="bulk-move-title">Move dishes out of '{{ bulkMoveSource.name }}'</h3>
            <button type="button" class="icon-btn" (click)="cancelBulkMove()" aria-label="Close">✕</button>
          </div>
          <p class="muted modal-intro">
            {{ bulkMoveItems.length }} dish(es) move to the category you pick. A category has to be empty before it can be deleted, so this is how one is retired.
          </p>

          <div class="form-group">
            <label for="bulk-move-target">Move to</label>
            <select id="bulk-move-target" class="field-select" [(ngModel)]="bulkMoveTargetCategoryId" [disabled]="bulkMoving">
              <option [ngValue]="null">Choose category…</option>
              <option *ngFor="let target of bulkMoveTargetCategories" [ngValue]="target.categoryId">{{ target.name }}</option>
            </select>
          </div>
          <p class="error-text" *ngIf="!bulkMoveTargetCategories.length">No other category exists yet. Create one, then move the dishes into it.</p>

          <!-- Per-dish result. An owner emptying a category has to see which dishes did not move and why. -->
          <div class="bulk-summary" *ngIf="bulkMoveSummary">
            <p *ngIf="bulkMoveSummary.movedCount" class="muted bulk-summary-line">
              Moved {{ bulkMoveSummary.movedCount }} dish(es).
            </p>
            <ul class="bulk-failures" *ngIf="bulkMoveSummary.failed.length">
              <li class="error-text" *ngFor="let failure of bulkMoveSummary.failed">{{ failure.name }} — {{ failure.reason }}</li>
            </ul>
          </div>

          <div class="modal-actions modal-actions--spaced">
            <button class="ghost-btn" (click)="cancelBulkMove()" [disabled]="bulkMoving">Close</button>
            <button class="ghost-btn danger-btn" *ngIf="bulkMoveCanDelete" (click)="deleteAfterBulkMove()">Delete empty category</button>
            <button class="primary-btn" (click)="confirmBulkMove()" [disabled]="bulkMoving || bulkMoveTargetCategoryId == null || !bulkMoveItems.length">
              {{ bulkMoving ? 'Moving…' : 'Move ' + bulkMoveItems.length + ' dish(es)' }}
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

      <!-- Delete Category Confirmation -->
      <app-confirm-dialog
        *ngIf="confirmCategory"
        title="Delete Category"
        [message]="'Are you sure you want to delete \\'' + confirmCategory.name + '\\'? This action cannot be undone.'"
        confirmLabel="Delete"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="executeDeleteCategory()"
        (cancelled)="cancelDeleteCategory()"
      ></app-confirm-dialog>

      <!-- Delete Variant Confirmation -->
      <app-confirm-dialog
        *ngIf="confirmVariant"
        title="Delete Variant"
        [message]="'Are you sure you want to delete variant \\'' + confirmVariant.variantName + '\\'? This action cannot be undone.'"
        confirmLabel="Delete"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="executeDeleteVariant()"
        (cancelled)="cancelDeleteVariant()"
      ></app-confirm-dialog>

      <!-- Remove Image Confirmation -->
      <app-confirm-dialog
        *ngIf="confirmRemoveImage"
        title="Remove Photo"
        [message]="'Are you sure you want to remove the photo for \\'' + confirmRemoveImage.name + '\\'? This action cannot be undone.'"
        confirmLabel="Remove"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="executeRemoveImage()"
        (cancelled)="cancelRemoveImage()"
      ></app-confirm-dialog>
    </div>
  `,
  styles: [`
    /* ── Workspace bands ──
       Summary, filters and table are one vertical stack with one gap, so the page
       reads as three bands instead of three unrelated blocks. Every rule here is
       component-scoped: nothing below can reach the sidebar or another page. */
    app-kpi-row { display: block; margin-bottom: 1rem; }

    .menu-filters {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--kb-space-3) var(--kb-space-4);
      margin-bottom: 1rem;
      padding: 0.6rem var(--kb-space-3);
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-card);
    }
    .menu-filters__group { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
    .menu-filters__label {
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--kb-color-muted-foreground);
    }

    .filter-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      min-height: 34px;
      padding: 0.3rem var(--kb-space-3);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-surface);
      color: var(--kb-color-foreground-2);
      font: inherit;
      font-size: var(--kb-text-sm);
      font-weight: 600;
      cursor: pointer;
      transition: border-color 150ms ease, background 150ms ease, color 150ms ease;
    }
    .filter-chip:hover { border-color: var(--kb-color-border-strong); color: var(--kb-color-foreground); }
    .filter-chip:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: 2px; }
    /* Only the active filter wears the accent, so the current view is readable at a
       glance. It is the action blue, not the brand orange: orange next to stock
       states reads as a warning. */
    .filter-chip--active {
      border-color: var(--kb-color-primary);
      background: var(--kb-color-primary-soft);
      color: var(--kb-color-link);
    }
    .filter-chip__count {
      padding: 0 0.35rem;
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .filter-chip--active .filter-chip__count {
      background: var(--kb-color-surface);
      color: var(--kb-color-link);
    }

    .menu-filter-select {
      min-height: 34px;
      padding: 0 0.6rem;
      border: 1px solid var(--kb-color-border-strong);
      border-radius: var(--kb-radius-md);
      background: var(--kb-color-surface);
      color: var(--kb-color-foreground);
      font: inherit;
      font-size: var(--kb-text-sm);
    }
    .menu-filter-select:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: 2px; }

    .menu-filters__reset {
      margin-left: auto;
      padding: 0.3rem 0.2rem;
      border: 0;
      background: none;
      color: var(--kb-color-link);
      font: inherit;
      font-size: var(--kb-text-sm);
      font-weight: 600;
      cursor: pointer;
    }
    .menu-filters__reset:hover { text-decoration: underline; }
    .menu-filters__reset:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: 2px; border-radius: var(--kb-radius-sm); }

    /* Dietary type is text first; the border gives it a badge shape without
       turning a dietary fact into a colour code. */
    .type-badge {
      display: inline-block;
      padding: 0.1rem 0.45rem;
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-surface-2);
      color: var(--kb-color-foreground-2);
      font-size: var(--kb-text-2xs);
      font-weight: 600;
      white-space: nowrap;
    }

    @media (max-width: 640px) {
      .menu-filters { align-items: stretch; }
      .menu-filters__reset { margin-left: 0; }
    }

    /* ── Menu table column rhythm (same standard as orders history) ── */
    /* The table design is defined once in the global stylesheet (see the
       "Tables" section of styles.css). Column alignment is layout and stays. */
    .menu-table tbody tr {
      min-height: 56px;
    }
    /* Padding, type and borders come from the one table design in styles.css;
       this page owns column layout only (see table-design.spec.ts). */
    .menu-table td, .menu-table th {
      vertical-align: middle;
    }
    /* Column count is now 7 (Item, Category, Type, Price, Availability, Updated,
       Actions). The variant *count* column is gone: the price cell already lists
       every variant, so a number beside it repeated the same fact in a competing
       column — and it spent the widest gap in the table on that repeat. */
    .menu-table th:nth-child(6),
    .menu-table td:nth-child(6) {
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }
    .menu-table th:nth-child(7),
    .menu-table td:nth-child(7) {
      text-align: right;
    }
    .photo-picker {
      display: flex;
      align-items: center;
      gap: 0.85rem;
    }
    .photo-picker .thumb {
      width: 64px;
      height: 64px;
      border-radius: var(--kb-radius-lg);
    }
    .photo-picker__placeholder {
      font-size: var(--kb-text-2xs);
      color: var(--muted);
      text-align: center;
      padding: 0 var(--kb-space-1);
      line-height: 1.3;
    }
    .photo-picker__actions {
      display: flex;
      gap: var(--kb-space-2);
      align-items: center;
    }
    .ghost-btn--disabled { opacity: 0.5; cursor: not-allowed; }
    .ghost-btn--danger { color: var(--danger); border-color: var(--danger); }
    
    
    
    
    /* One aligned two-column grid for base + variants. Prices are right-aligned
       on tabular figures so a ₹90 and a ₹1,250 still end on the same edge. */
    .menu-price-list { display: grid; gap: 0.22rem; min-width: 150px; }
    .menu-price-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: baseline;
      gap: 0.9rem;
    }
    .menu-price-label {
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-sm);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .menu-price-row strong {
      color: var(--kb-color-foreground-2);
      font-weight: 600;
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }
    /* Base is the anchor price: one step up in ink and weight, and the rule
       beneath it separates the anchor from the variant detail. */
    .menu-price-row--base { padding-bottom: 0.28rem; border-bottom: 1px solid var(--kb-color-border); }
    .menu-price-row--base strong { color: var(--kb-color-foreground); font-weight: 700; }

    /* Column 1: the dish is the record. Name readable, description one muted
       line, so a long description cannot reflow the row. */
    .menu-table .item-cell strong { color: var(--kb-color-foreground); font-weight: 600; }
    .menu-table .item-cell .muted {
      display: block;
      max-width: 34ch;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: var(--kb-text-sm);
    }

    /* Status chip doubles as the availability control in its own column: same
       reachable hit area, no second duplicate toggle in the action stack. */
    .chip--status {
      cursor: pointer;
      font: inherit;
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.02em;
      text-transform: uppercase;
      min-height: 30px;
      padding: var(--kb-space-1) 0.7rem;
    }
    .chip--status:disabled { opacity: 0.55; cursor: progress; }
    .chip--status:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: 2px; }
    .ocr-panel { margin-top: var(--kb-space-3); }
    .ocr-state {
      display: flex; align-items: flex-start; gap: var(--kb-space-2);
      margin-top: var(--kb-space-3); padding: var(--kb-space-3) var(--kb-space-4);
      border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-lg);
      background: var(--kb-color-surface-2); color: var(--kb-color-foreground);
    }
    .ocr-state strong, .ocr-state p { margin: 0; }
    .ocr-state p { margin-top: var(--kb-space-1); color: var(--kb-color-muted-foreground); font-size: var(--kb-text-sm); }
    .ocr-state .spanner { flex: 0 0 auto; margin: var(--kb-space-1) 0 0; }
    .ocr-state--error { border-color: rgba(239,68,68,0.15); background: var(--kb-color-surface-2); color: var(--kb-color-error); }
    .ocr-state--warning { border-color: rgba(245,158,11,0.15); background: var(--kb-color-surface-2); color: var(--kb-color-warning); }
    .result-header { display: flex; align-items: center; gap: var(--kb-space-2); }
    .extracted-table { margin-top: var(--kb-space-2); }
    .hint-text { color: var(--kb-color-muted-foreground); font-size: var(--kb-text-md); margin: var(--kb-space-2) 0 var(--kb-space-3); }
    .error-text { color: var(--kb-color-error); font-size: var(--kb-text-md); margin: var(--kb-space-2) 0 0; }
    .toolbar-actions { display: flex; gap: var(--kb-space-2); align-items: center; }
    .action-stack { display: flex; gap: 0.4rem; align-items: center; flex-wrap: nowrap; white-space: nowrap; }
    .toggle-btn {
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      border-radius: var(--kb-radius-md);
      padding: var(--kb-space-1) var(--kb-space-2);
      font-size: var(--kb-text-sm);
      font-weight: 500;
      cursor: pointer;
      transition: transform 150ms var(--ease-out), background-color 150ms ease;
    }
    .toggle-btn:active { transform: scale(0.97); }
    .toggle-btn:disabled { opacity: 0.5; cursor: default; }
    .toggle-btn--on {
      border-color: var(--kb-color-primary);
      background: var(--kb-color-primary);
      color: var(--kb-color-on-primary);
      font-weight: 600;
    }
    .toggle-btn--off {
      border-color: var(--kb-color-error);
      background: var(--kb-color-surface);
      color: var(--kb-color-error);
      font-weight: 600;
    }
    /* Star affordances: the wishlist toggle in the header, the per-dish star, and the panel
       that collects them. Sizes match the category star in the Categories dialog so the two
       stars read as the same control in two places. */
    .fav-toggle { gap: 0.35rem; }
    .fav-toggle--on { border-color: var(--kb-color-primary); color: var(--kb-color-link); }
    .fav-toggle__count {
      font-size: var(--kb-text-2xs);
      font-weight: 600;
      padding: 0 0.35rem;
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
    }
    .fav-btn {
      min-width: 34px;
      min-height: 34px;
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-md);
      background: var(--kb-color-surface);
      color: var(--kb-color-muted-foreground);
      font-size: 0.95rem;
      line-height: 1;
      cursor: pointer;
    }
    .fav-btn:disabled { opacity: 0.5; cursor: default; }
    .fav-btn--on { border-color: var(--kb-color-primary); color: var(--kb-color-link); }
    .fav-panel {
      display: grid;
      gap: 0.6rem;
      margin-bottom: var(--kb-space-3);
    }
    .fav-panel__head { display: flex; align-items: baseline; gap: 0.6rem; flex-wrap: wrap; }
    .fav-panel__hint { margin: 0; color: var(--kb-color-muted-foreground); font-size: var(--kb-text-md); }
    .fav-chips { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .fav-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.2rem 0.6rem;
      border-radius: var(--kb-radius-full);
      border: 1px solid var(--kb-color-primary);
      color: var(--kb-color-link);
      font-size: var(--kb-text-sm);
      font-weight: 500;
    }

    /* ── Add/Edit and category dialogs ──
       These declarations used to sit in inline style attributes. Values are
       unchanged; the carrier moved into the component so the page keeps one
       vocabulary and the stylesheet checks can see the values. */
    .menu-category-picker { gap: var(--kb-space-2); }
    .new-category-toggle { white-space: nowrap; }
    .new-category-row { display: flex; align-items: stretch; margin-top: var(--kb-space-2); gap: var(--kb-space-2); }
    .availability-row { gap: 0.6rem; }
    .variants-section {
      margin-top: var(--kb-space-4);
      border-top: 1px solid var(--kb-color-border);
      padding-top: var(--kb-space-4);
    }
    .variants-head { margin-bottom: var(--kb-space-2); }
    .variants-label { margin: 0; font-weight: 700; }
    .variants-hint { font-size: var(--kb-text-xs); }
    .variants-loading { padding: var(--kb-space-3); font-size: var(--kb-text-sm); }
    .variant-list { gap: 0.4rem; margin-bottom: 0.75rem; }
    .variant-row {
      padding: 0.45rem var(--kb-space-3);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
    }
    .variant-name { font-size: var(--kb-text-md); }
    .variant-price { margin-left: var(--kb-space-2); font-variant-numeric: tabular-nums; }
    .draft-price { margin-left: var(--kb-space-2); }
    .variant-remove {
      padding: 0.2rem var(--kb-space-2);
      font-size: var(--kb-text-xs);
      min-height: 28px;
    }
    .variants-empty { margin: 0 0 var(--kb-space-2); font-size: var(--kb-text-sm); }
    .variant-add-form { gap: 0.4rem; }
    .variant-input { min-height: 36px; font-size: var(--kb-text-md); }
    .variant-input--name { flex: 2; }
    .variant-input--price { flex: 1; }
    .variant-add-btn { min-height: 36px; padding: 0 0.85rem; font-size: var(--kb-text-sm); }
    .categories-modal { max-width: 540px; }
    .bulk-modal { max-width: 640px; }
    .menu-modal-head { margin-bottom: 0.75rem; }
    .menu-modal-heading { margin: 0; }
    .modal-intro { margin: -0.25rem 0 var(--kb-space-4); font-size: var(--kb-text-md); }
    .category-create-row { display: flex; align-items: stretch; gap: var(--kb-space-2); margin-bottom: 1.25rem; }
    .cat-list-wrap { max-height: 360px; overflow-y: auto; gap: var(--kb-space-2); }
    .cat-list-row {
      padding: 0.6rem 0.85rem;
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: 10px;
    }
    .cat-name-row { gap: 0.6rem; min-width: 0; }
    .cat-name {
      font-size: var(--kb-text-lg);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .cat-count { font-size: var(--kb-text-2xs); }
    .cat-edit-row { display: flex; align-items: stretch; gap: 0.4rem; margin-right: var(--kb-space-2); }
    .cat-rename-input {
      min-height: 34px;
      padding: 0.3rem var(--kb-space-2);
      font-size: var(--kb-text-md);
    }
    .cat-btn { min-height: 34px; font-size: var(--kb-text-sm); }
    .cat-btn--save { padding: 0.3rem 0.65rem; }
    .cat-btn--cancel { padding: 0.3rem var(--kb-space-2); }
    .cat-actions { display: flex; align-items: stretch; gap: 0.35rem; }
    .cat-action-btn { padding: 0.3rem 0.6rem; font-size: var(--kb-text-sm); min-height: 32px; }
    .cat-empty {
      text-align: center;
      padding: var(--kb-space-6);
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-md);
    }
    .modal-actions--spaced { margin-top: 1.25rem; }
    .bulk-summary { margin-top: 0.9rem; font-size: var(--kb-text-md); }
    .bulk-summary-line { margin: 0 0 0.35rem; }
    .bulk-failures { margin: 0; padding-left: 1.1rem; }

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

  searchTerm = '';
  pageSize = 10;
  currentPage = 1;

  /** Availability filter. 'all' shows the whole menu; the other two use isSellable. */
  statusFilter: 'all' | 'available' | 'out' = 'all';
  /** Category filter; null means every category. */
  categoryFilterId: number | null = null;

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
  togglingFavouriteId: number | null = null;
  /** The dish whose star write is in flight, so a second tap cannot race the first. */
  togglingItemFavouriteId: number | null = null;
  /** The "wishlist": show only starred dishes and pinned categories. */
  favouritesOnly = false;

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
  confirmCategory: BusinessCategory | null = null;
  confirmVariant: BusinessItemVariant | null = null;
  confirmRemoveImage: BusinessMenuItem | null = null;

  // Bulk move state: emptying a category so it can be deleted (see startBulkMove)
  bulkMoveSource: BusinessCategory | null = null;
  bulkMoveTargetCategoryId: number | null = null;
  bulkMoving = false;
  bulkMoveSummary: { movedCount: number; failed: Array<{ name: string; reason: string }> } | null = null;

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

  /**
   * The one availability rule the page uses: a dish is sellable when the owner has
   * it switched on and the kitchen has not marked it out of stock. Running low is
   * still sellable, which is why it is not folded in here.
   */
  private isSellable(item: BusinessMenuItem): boolean {
    return !!item.available && (item.stockStatus || '').toUpperCase() !== 'OUT_OF_STOCK';
  }

  /** Counted from the loaded menu, never from a literal: the tiles and the filter
   *  chips must agree with the table they sit above. */
  get availableCount(): number {
    return this.items.filter(item => this.isSellable(item)).length;
  }

  get outOfStockCount(): number {
    return this.items.length - this.availableCount;
  }

  get filtersActive(): boolean {
    return this.statusFilter !== 'all'
      || this.categoryFilterId !== null
      || !!this.searchTerm.trim()
      || this.favouritesOnly;
  }

  /** The four summary tiles, in the shared KPI row's anatomy. */
  get menuKpis(): KpiItem[] {
    const outOfStock = this.outOfStockCount;
    return [
      { label: 'Total Dishes', value: this.items.length, caption: 'Every dish on the menu' },
      { label: 'Categories', value: this.categories.length, tone: 'brand', caption: 'Sections dishes are filed under' },
      { label: 'Available', value: this.availableCount, tone: 'success', caption: 'Sellable at the counter' },
      {
        label: 'Out of Stock',
        value: outOfStock,
        tone: outOfStock > 0 ? 'danger' : 'default',
        alert: outOfStock > 0,
        caption: outOfStock > 0 ? 'Hidden from the counter' : 'Nothing is out of stock',
      },
    ];
  }

  get filteredItems(): BusinessMenuItem[] {
    const search = this.searchTerm.trim().toLowerCase();
    return this.items.filter(item => {
      const matchesSearch = !search || [
        item.name,
        item.categoryName ?? '',
        item.description ?? ''
      ].some(v => v.toLowerCase().includes(search));

      const matchesStatus = this.statusFilter === 'all'
        || (this.statusFilter === 'available' ? this.isSellable(item) : !this.isSellable(item));

      const matchesCategory = this.categoryFilterId === null
        || item.categoryId === this.categoryFilterId;

      return matchesSearch
        && matchesStatus
        && matchesCategory
        && (!this.favouritesOnly || !!item.favourite);
    });
  }

  setStatusFilter(filter: 'all' | 'available' | 'out'): void {
    this.statusFilter = filter;
    this.resetPage();
    this.cdr.markForCheck();
  }

  setCategoryFilter(categoryId: number | null): void {
    this.categoryFilterId = categoryId ?? null;
    this.resetPage();
    this.cdr.markForCheck();
  }

  /** Back to the whole menu: every filter this page offers, including the search. */
  clearFilters(): void {
    this.statusFilter = 'all';
    this.categoryFilterId = null;
    this.searchTerm = '';
    this.favouritesOnly = false;
    this.resetPage();
    this.cdr.markForCheck();
  }

  /** The starred dishes, in the order the table already shows them. */
  get favouriteItems(): BusinessMenuItem[] {
    return this.items.filter(item => !!item.favourite);
  }

  /** The pinned categories, which `loadMenu` already sorts to the front. */
  get favouriteCategories(): BusinessCategory[] {
    return this.categories.filter(cat => !!cat.isFavourite);
  }

  get favouriteCount(): number {
    return this.favouriteItems.length + this.favouriteCategories.length;
  }

  get pagedItems(): BusinessMenuItem[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredItems.slice(start, start + this.pageSize);
  }

  goToPage(page: number): void {
    const total = Math.max(1, Math.ceil(this.filteredItems.length / this.pageSize));
    this.currentPage = Math.min(Math.max(1, page), total);
    this.cdr.markForCheck();
  }

  constructor() {
    this.loadMenu();
  }

  loadMenu(): void {
    // Clear the list on every load, including a manual refresh, so the
    // skeletons take over and the reload is visible (as on Terminals).
    this.refreshing = true;
    this.loaded = false;
    this.loadError = '';
    forkJoin({
      items: this.api.getMenu(),
      categories: this.api.getMenuCategories()
    }).subscribe({
      next: ({ items, categories }) => {
        this.items = items;
        this.categories = categories;
        this.sortCategoriesFavouritesFirst();
        this.loaded = true;
        this.refreshing = false;
        this.loadError = '';
        this.currentPage = 1;
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

  /** "1 item" / "3 items" — the chip style uppercases the text, so the count
   *  must carry its own plural or the card reads "1 ITEMS". */
  categoryItemCountLabel(categoryId: number): string {
    const count = this.getItemsCountInCategory(categoryId);
    return `${count} ${count === 1 ? 'item' : 'items'}`;
  }

  /**
   * Human label for the server's stock status codes. The raw values
   * (IN_STOCK, RUNNING_LOW, OUT_OF_STOCK) are storage detail, not copy.
   */
  stockStatusLabel(status: string | null | undefined): string {
    switch ((status || '').toUpperCase()) {
      case 'IN_STOCK': return 'In stock';
      case 'RUNNING_LOW': return 'Running low';
      case 'OUT_OF_STOCK': return 'Out of stock';
      default: return status ? status.charAt(0) + status.slice(1).toLowerCase().replace(/_/g, ' ') : 'Unknown';
    }
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

  /**
   * Pin or unpin a category so it sorts to the top of the category list everywhere.
   * isFavourite is server-authoritative (terminals push whole category rows and the server
   * restores this field rather than trusting them), so the response is applied rather than
   * optimistically flipping the star here.
   */
  /**
   * Shows or hides the favourites view. Paging is reset because the two views have different
   * lengths: keeping the previous window would show only part of a short favourites list.
   */
  toggleFavouritesOnly(): void {
    this.favouritesOnly = !this.favouritesOnly;
    this.resetPage();
    this.cdr.markForCheck();
  }

  /**
   * Stars or unstars a dish.
   *
   * The server is authoritative, so the row is only updated once the API answers - and a
   * failed write deliberately leaves the star as it was rather than showing an optimistic lie
   * that the next reload would reverse.
   */
  toggleItemFavourite(item: BusinessMenuItem): void {
    if (this.togglingItemFavouriteId !== null) return;
    const next = !item.favourite;
    this.togglingItemFavouriteId = item.menuItemId;
    this.api.setMenuItemFavourite(item.menuItemId, next).subscribe({
      next: (updated) => {
        item.favourite = updated?.favourite ?? next;
        this.togglingItemFavouriteId = null;
        this.toast.show(
          item.favourite ? `'${item.name}' starred` : `'${item.name}' unstarred`,
          'success',
        );
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.togglingItemFavouriteId = null;
        this.toast.show(err?.error?.message || 'Failed to update favourite.', 'error');
        this.cdr.markForCheck();
      }
    });
  }

  toggleCategoryFavourite(cat: BusinessCategory): void {
    if (this.togglingFavouriteId !== null) return;
    this.togglingFavouriteId = cat.categoryId;
    this.api.setCategoryFavourite(cat.categoryId, !cat.isFavourite).subscribe({
      next: (updated) => {
        cat.isFavourite = updated.isFavourite;
        this.togglingFavouriteId = null;
        this.sortCategoriesFavouritesFirst();
        this.toast.show(
          updated.isFavourite ? `'${updated.name}' pinned to the top` : `'${updated.name}' unpinned`,
          'success');
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.togglingFavouriteId = null;
        this.toast.show(err?.error?.message || 'Failed to update favourite.', 'error');
        this.cdr.markForCheck();
      }
    });
  }

  /** Mirrors the server ordering so a newly starred row visibly moves to the top. */
  private sortCategoriesFavouritesFirst(): void {
    this.categories = [...this.categories].sort((a, b) => {
      const byFavourite = Number(!!b.isFavourite) - Number(!!a.isFavourite);
      return byFavourite !== 0 ? byFavourite : a.name.localeCompare(b.name);
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

  /** Dishes still in the category being emptied. */
  get bulkMoveItems(): BusinessMenuItem[] {
    const source = this.bulkMoveSource;
    return source ? this.items.filter(item => item.categoryId === source.categoryId) : [];
  }

  get bulkMoveTargetCategories(): BusinessCategory[] {
    return this.categories.filter(cat => cat.categoryId !== this.bulkMoveSource?.categoryId);
  }

  /**
   * The category is empty and something actually moved, so the delete that was impossible a
   * moment ago is now the natural next step of the same flow.
   */
  get bulkMoveCanDelete(): boolean {
    return !!this.bulkMoveSummary && this.bulkMoveSummary.movedCount > 0 && this.bulkMoveItems.length === 0;
  }

  startBulkMove(cat: BusinessCategory): void {
    this.bulkMoveSource = cat;
    this.bulkMoveTargetCategoryId = null;
    this.bulkMoveSummary = null;
    this.bulkMoving = false;
    this.cdr.markForCheck();
  }

  cancelBulkMove(): void {
    if (this.bulkMoving) return;
    this.bulkMoveSource = null;
    this.bulkMoveSummary = null;
    this.cdr.markForCheck();
  }

  /**
   * Moves every dish out of the source category, one PUT at a time.
   *
   * Sequential on purpose: each update is its own server transaction with its own name-clash
   * check, so firing them together would report an outcome for a dish whose destination another
   * in-flight move had already changed. Each result is collected, success or failure, so the
   * owner gets a per-dish answer instead of a single opaque error.
   */
  confirmBulkMove(): void {
    const source = this.bulkMoveSource;
    const targetCategoryId = this.bulkMoveTargetCategoryId;
    if (!source || targetCategoryId == null || this.bulkMoving) return;
    const dishes = this.bulkMoveItems;
    if (!dishes.length) return;

    this.bulkMoving = true;
    this.bulkMoveSummary = null;
    const failed: Array<{ name: string; reason: string }> = [];

    from(dishes).pipe(
      concatMap(dish => this.api.updateMenuItem(dish.menuItemId, this.bulkMovePayload(dish, targetCategoryId)).pipe(
        map(updated => ({ dish, updated, reason: '' })),
        catchError(error => of({
          dish,
          updated: null as BusinessMenuItem | null,
          reason: error?.error?.message || error?.message || 'Failed to move this dish.'
        }))
      )),
      toArray()
    ).subscribe({
      next: (results) => {
        let movedCount = 0;
        for (const result of results) {
          if (result.updated) {
            const index = this.items.findIndex(item => item.menuItemId === result.dish.menuItemId);
            if (index >= 0) this.items[index] = result.updated;
            movedCount++;
          } else {
            failed.push({ name: result.dish.name, reason: result.reason });
          }
        }
        this.bulkMoving = false;
        this.bulkMoveSummary = { movedCount, failed };
        this.items = [...this.items];
        const where = this.categories.find(cat => cat.categoryId === targetCategoryId)?.name;
        if (!movedCount) {
          this.toast.show('No dishes were moved. See the list for the reason.', 'error');
        } else if (failed.length) {
          this.toast.show(`Moved ${movedCount} of ${results.length} dishes${where ? ' to ' + where : ''}. ${failed.length} could not be moved.`, 'error');
        } else {
          this.toast.show(`Moved ${movedCount} dish(es)${where ? ' to ' + where : ''}`, 'success');
        }
        this.cdr.markForCheck();
      },
      error: () => {
        this.bulkMoving = false;
        this.bulkMoveSummary = { movedCount: 0, failed };
        this.toast.show('Failed to move the dishes. Please try again.', 'error');
        this.cdr.markForCheck();
      }
    });
  }

  deleteAfterBulkMove(): void {
    const source = this.bulkMoveSource;
    if (!source) return;
    this.bulkMoveSource = null;
    this.bulkMoveSummary = null;
    this.deleteCategoryPrompt(source);
  }

  /**
   * Only the category changes: this action moves dishes, it does not re-price or re-describe
   * them, so every other field is carried over exactly as the dish already is.
   */
  private bulkMovePayload(dish: BusinessMenuItem, targetCategoryId: number): UpdateMenuItemRequest {
    return {
      name: dish.name,
      categoryId: targetCategoryId,
      foodType: dish.foodType === 'non-veg' ? 'non-veg' : 'veg',
      basePrice: dish.basePrice,
      ...(dish.description ? { description: dish.description } : {})
    };
  }

  deleteCategoryPrompt(cat: BusinessCategory): void {
    const count = this.getItemsCountInCategory(cat.categoryId);
    if (count > 0) {
      this.toast.show(`Cannot delete '${cat.name}' because it contains ${count} dish(es). Move or delete the dishes first.`, 'error');
      return;
    }
    this.confirmCategory = cat;
  }

  executeDeleteCategory(): void {
    const cat = this.confirmCategory;
    if (!cat) return;
    this.confirmCategory = null;
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

  cancelDeleteCategory(): void {
    this.confirmCategory = null;
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
    this.confirmVariant = variant;
  }

  executeDeleteVariant(): void {
    const variant = this.confirmVariant;
    if (!variant || !this.editingItem) return;
    this.confirmVariant = null;
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
      }
    });
  }

  cancelDeleteVariant(): void {
    this.confirmVariant = null;
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

  /**
   * Escape closes the top-most dialog on this page. Modals that could only be
   * dismissed with Cancel or a backdrop click trapped keyboard users.
   */
  @HostListener('document:keydown.escape')
  onEscapeCloseMenuDialogs(): void {
    if (this.bulkMoveSource) {
      this.cancelBulkMove();
      return;
    }
    if (this.showCategoriesModal) {
      this.closeCategoriesModal();
      return;
    }
    if (this.showFormModal) {
      this.closeFormModal();
    }
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
    this.confirmRemoveImage = this.editingItem ?? null;
  }

  executeRemoveImage(): void {
    const target = this.confirmRemoveImage;
    if (!target) return;
    this.confirmRemoveImage = null;
    this.formPhotoFile = null;
    this.formPhotoPreview = null;
    this.formPhotoRemoving = true;
    this.cdr.markForCheck();
  }

  cancelRemoveImage(): void {
    this.confirmRemoveImage = null;
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
        this.cdr.markForCheck();
        this.showToast(updated.available ? 'Item marked available' : 'Item marked unavailable');
      },
      error: () => {
        // Revert on error
        item.available = previousState;
        this.togglingId = null;
        this.cdr.markForCheck();
        this.showToast('Failed to update availability', 'error');
      }
    });
  }

  // --- Helpers ---

  // --- Filters and Pagination ---

  resetPage(): void {
    this.currentPage = 1;
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
    this.stopPolling();
  }

  private showToast(msg: string, type: 'info' | 'error' | 'success' = 'success'): void {
    this.toast.show(msg, type);
  }
}
