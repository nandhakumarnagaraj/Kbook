import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminBusinessDetail, AdminBusinessListItem } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { HeaderSearchComponent } from '../../shared/header-search.component';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';
import { FocusTrapDirective } from '../../core/components/focus-trap.directive';

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, ConfirmDialogComponent, EmptyStateComponent, ApiStateComponent, PageHeaderComponent, HeaderSearchComponent, RefreshButtonComponent, KpiRowComponent, PaginationBarComponent, FocusTrapDirective],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Businesses"
        subtitle="Platform directory with cleaner drill-down detail panel."
      >
        <div class="header-controls">
          <app-header-search
            [(value)]="searchTerm"
            (valueChange)="resetVisible()"
            placeholder="Search by shop, owner, login, email, or id"
            ariaLabel="Search businesses"
            inputId="business-search"
          />
          <button class="header-action-btn header-action-btn--primary" (click)="openCreateModal()">Add Restaurant</button>
          <app-refresh-button (refresh)="loadBusinesses()" [loading]="refreshing" ariaLabel="Refresh businesses" />
        </div>
      </app-page-header>

      <!-- Stale refresh banner (prototype pattern) -->
      <div class="stale-banner" *ngIf="loadError && businesses.length" role="status" aria-live="polite">
        <span>Couldn't update businesses. {{ loadError }}</span>
        <button type="button" class="btn-retry" (click)="loadBusinesses()">Retry</button>
      </div>

      <app-api-state
        *ngIf="loadError && !businesses.length"
        [loading]="false"
        [error]="loadError"
        (retry)="loadBusinesses()"
      ></app-api-state>

      <div class="panel table-wrap" *ngIf="loaded && pagedBusinesses.length; else loading">
        <table class="data-table businesses-table">
          <thead>
            <tr>
              <th class="business-col">Business</th>
              <th class="owner-col">Owner</th>
              <th class="num-col">Orders</th>
              <th class="num-col">Menu</th>
              <th class="num-col">Staff</th>
              <th class="date-col" [attr.aria-sort]="sortKey === 'joined' ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'">
                <button type="button" class="th-sort" (click)="toggleSort('joined')">
                  Joined
                  <span aria-hidden="true" *ngIf="sortKey === 'joined'">{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
                </button>
              </th>
              <th class="date-col" [attr.aria-sort]="sortKey === 'lastUsed' ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'">
                <button type="button" class="th-sort" (click)="toggleSort('lastUsed')">
                  Last Used
                  <span aria-hidden="true" *ngIf="sortKey === 'lastUsed'">{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
                </button>
              </th>
              <th class="action-col">Action</th>
            </tr>
          </thead>
          <tbody>
            <tr
              *ngFor="let business of pagedBusinesses; trackBy: trackByRestaurantId"
              class="clickable-row"
              tabindex="0"
              [attr.aria-label]="'View ' + business.shopName"
              (click)="showDetails(business)"
              (keydown.enter)="showDetails(business)">
              <td>
                <div class="stacked-meta">
                  <strong>{{ business.shopName || '-' }}</strong>
                  <span class="muted">#{{ business.restaurantId }}</span>
                  <span class="chip danger" *ngIf="business.isSuspended">Suspended</span>
                </div>
              </td>
              <td>
                <div class="stacked-meta">
                  <strong>{{ business.ownerName || '-' }}</strong>
                  <span class="muted" *ngIf="business.ownerLoginId">{{ business.ownerLoginId }}</span>
                </div>
              </td>
              <td class="num-col">{{ business.orderCount }}</td>
              <td class="num-col">{{ business.menuCount }}</td>
              <td class="num-col">{{ business.staffCount }}</td>
              <td class="date-col">{{ formatDateValue(business.createdAt) }}</td>
              <td class="date-col">
                <span *ngIf="business.lastUsedAt; else neverUsed">{{ formatDateValue(business.lastUsedAt) }}</span>
                <ng-template #neverUsed><span class="muted">No settled orders</span></ng-template>
              </td>
              <td class="action-col">
                <button
                  *ngIf="!business.isSuspended"
                  class="biz-action-btn biz-action-btn--danger"
                  (click)="confirmSuspend(business); $event.stopPropagation()">
                  <span>Suspend</span>
                </button>
                <button
                  *ngIf="business.isSuspended"
                  class="biz-action-btn biz-action-btn--success"
                  (click)="activateBusiness(business); $event.stopPropagation()">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                  <span>Activate</span>
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="mobile-data-list" aria-label="Businesses">
          <article class="mobile-data-card" *ngFor="let business of pagedBusinesses; trackBy: trackByRestaurantId" [class.mobile-data-card--danger]="business.isSuspended">
            <button type="button" class="mobile-data-card__primary" (click)="showDetails(business)">
              <span class="mobile-data-card__head"><strong>{{ business.shopName || 'Unnamed business' }}</strong><span class="chip" [class.success]="!business.isSuspended" [class.danger]="business.isSuspended">{{ business.isSuspended ? 'Suspended' : 'Active' }}</span></span>
              <span>{{ business.ownerName || 'No owner name' }} · #{{ business.restaurantId }}</span>
            </button>
            <dl><div><dt>Orders</dt><dd>{{ business.orderCount }}</dd></div><div><dt>Menu</dt><dd>{{ business.menuCount }}</dd></div><div><dt>Staff</dt><dd>{{ business.staffCount }}</dd></div><div><dt>Joined</dt><dd>{{ formatDateValue(business.createdAt) }}</dd></div><div><dt>Last used</dt><dd>{{ business.lastUsedAt ? formatDateValue(business.lastUsedAt) : 'No settled orders' }}</dd></div></dl>
            <div class="mobile-data-card__actions">
              <button *ngIf="!business.isSuspended" class="biz-action-btn biz-action-btn--danger" (click)="confirmSuspend(business)">
                <span>Suspend</span>
              </button>
              <button *ngIf="business.isSuspended" class="biz-action-btn biz-action-btn--success" (click)="activateBusiness(business)">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
                <span>Activate</span>
              </button>
            </div>
          </article>
        </div>

        <app-pagination-bar
          *ngIf="filteredBusinesses.length > 0"
          [currentPage]="currentPage"
          [pageSize]="pageSize"
          [totalItems]="filteredBusinesses.length"
          (pageChange)="goToPage($event)"
        />
      </div>

      <ng-template #loading>
        <div class="panel loading" *ngIf="!loaded && !loadError; else businessesEmpty" role="status" aria-busy="true">
          <span class="visually-hidden">Loading businesses...</span>
          <div class="skeleton-stack">
            <!-- Tenant row anatomy: shop name, id, owner, status, action. The
                 tenant's real name and owner stay hidden while it loads. -->
            <div class="skeleton-row skeleton-row--detail" *ngFor="let i of [1,2,3,4,5]; trackBy: trackByIndex">
              <span class="skeleton skeleton-line skeleton-line--name"></span>
              <span class="skeleton skeleton-line skeleton-line--meta"></span>
              <span class="skeleton skeleton-line skeleton-line--chip"></span>
              <span class="skeleton skeleton-line skeleton-line--grow"></span>
              <span class="skeleton skeleton-line skeleton-line--action"></span>
            </div>
          </div>
        </div>
        <ng-template #businessesEmpty>
          <app-empty-state
            *ngIf="!loadError"
            icon="🏪"
            title="No businesses match the current filters"
            text="Try a different search term to find the business you are looking for."
          ></app-empty-state>
        </ng-template>
      </ng-template>

      <div class="panel soft-section" *ngIf="selectedDetail() as detail">
        <div class="section-head">
          <div>
            <h3>{{ detail.shopName }}</h3>
            <p class="muted">Restaurant ID: {{ detail.restaurantId }}</p>
          </div>
          <div class="row row--wrap businesses-actions">
            <button class="ghost-btn" (click)="clearDetail()">Close</button>
          </div>
        </div>

        <app-kpi-row [items]="detailKpis(detail)" ariaLabel="Business summary metrics" />
      </div>

      <!-- Create Restaurant Modal -->
      <div class="modal-backdrop" *ngIf="showCreateModal" appFocusTrap (escape)="closeCreateModal()" (click)="closeCreateModal()">
        <div class="modal-box modal-content" role="dialog" aria-modal="true" aria-labelledby="create-business-title" (click)="$event.stopPropagation()">
          <h3 id="create-business-title">Add New Restaurant</h3>
          <p class="muted businesses-note">
            Provision a new restaurant tenant and initial owner account.
          </p>

          <div class="form-error" *ngIf="createError">{{ createError }}</div>

          <form [formGroup]="createForm" (ngSubmit)="submitCreate()">
            <div class="form-group">
              <label for="create-shop-name">Restaurant / Shop Name *</label>
              <!-- A flagged field also names the message beside it, so the rule the red border
                   shows is the rule a screen reader reads. The id is bound on the same
                   condition that renders the message, so a control never points at nothing. -->
              <input id="create-shop-name" class="field-control" type="text" formControlName="shopName" placeholder="e.g. Sagar Ratna Cafe"
                     [attr.aria-invalid]="createForm.get('shopName')?.touched && createForm.get('shopName')?.invalid"
                     [attr.aria-describedby]="createForm.get('shopName')?.touched && createForm.get('shopName')?.hasError('required') ? 'create-shop-name-error-required' : null" />
              <div id="create-shop-name-error-required" class="field-error" *ngIf="createForm.get('shopName')?.touched && createForm.get('shopName')?.hasError('required')">
                Restaurant name is required.
              </div>
            </div>

            <div class="form-group">
              <label for="create-owner-name">Owner Full Name *</label>
              <input id="create-owner-name" class="field-control" type="text" formControlName="ownerName" placeholder="e.g. Ramesh Sharma"
                     [attr.aria-invalid]="createForm.get('ownerName')?.touched && createForm.get('ownerName')?.invalid"
                     [attr.aria-describedby]="createForm.get('ownerName')?.touched && createForm.get('ownerName')?.hasError('required') ? 'create-owner-name-error-required' : null" />
              <div id="create-owner-name-error-required" class="field-error" *ngIf="createForm.get('ownerName')?.touched && createForm.get('ownerName')?.hasError('required')">
                Owner name is required.
              </div>
            </div>

            <div class="form-group">
              <label for="create-owner-phone">Owner Phone (10 digits) *</label>
              <input id="create-owner-phone" class="field-control" type="text" formControlName="ownerPhone" placeholder="10-digit mobile number" maxlength="10"
                     [attr.aria-invalid]="createForm.get('ownerPhone')?.touched && createForm.get('ownerPhone')?.invalid"
                     [attr.aria-describedby]="createForm.get('ownerPhone')?.touched && createForm.get('ownerPhone')?.hasError('required') ? 'create-owner-phone-error-required' : createForm.get('ownerPhone')?.touched && createForm.get('ownerPhone')?.hasError('pattern') ? 'create-owner-phone-error-pattern' : null" />
              <div id="create-owner-phone-error-required" class="field-error" *ngIf="createForm.get('ownerPhone')?.touched && createForm.get('ownerPhone')?.hasError('required')">
                Phone number is required.
              </div>
              <div id="create-owner-phone-error-pattern" class="field-error" *ngIf="createForm.get('ownerPhone')?.touched && createForm.get('ownerPhone')?.hasError('pattern') && !createForm.get('ownerPhone')?.hasError('required')">
                Must be exactly 10 digits.
              </div>
            </div>

            <div class="form-group">
              <label for="create-initial-pwd">Initial Password *</label>
              <input id="create-initial-pwd" class="field-control" type="password" formControlName="initialPassword" placeholder="Minimum 6 characters"
                     [attr.aria-invalid]="createForm.get('initialPassword')?.touched && createForm.get('initialPassword')?.invalid"
                     [attr.aria-describedby]="createForm.get('initialPassword')?.touched && createForm.get('initialPassword')?.hasError('required') ? 'create-initial-pwd-error-required' : createForm.get('initialPassword')?.touched && createForm.get('initialPassword')?.hasError('minlength') ? 'create-initial-pwd-error-minlength' : null" />
              <div id="create-initial-pwd-error-required" class="field-error" *ngIf="createForm.get('initialPassword')?.touched && createForm.get('initialPassword')?.hasError('required')">
                Initial password is required.
              </div>
              <div id="create-initial-pwd-error-minlength" class="field-error" *ngIf="createForm.get('initialPassword')?.touched && createForm.get('initialPassword')?.hasError('minlength')">
                Password must be at least 6 characters.
              </div>
            </div>

            <div class="form-group">
              <label for="create-owner-email">Owner Email (optional)</label>
              <input id="create-owner-email" class="field-control" type="email" formControlName="ownerEmail" placeholder="e.g. owner@example.com"
                     [attr.aria-invalid]="createForm.get('ownerEmail')?.touched && createForm.get('ownerEmail')?.invalid"
                     [attr.aria-describedby]="createForm.get('ownerEmail')?.touched && createForm.get('ownerEmail')?.hasError('email') ? 'create-owner-email-error-email' : null" />
              <div id="create-owner-email-error-email" class="field-error" *ngIf="createForm.get('ownerEmail')?.touched && createForm.get('ownerEmail')?.hasError('email')">
                Enter a valid email address.
              </div>
            </div>

            <div class="form-group">
              <label for="create-address">Address / City (optional)</label>
              <textarea id="create-address" class="field-control textarea-address" rows="3" formControlName="address" placeholder="e.g. Indiranagar, Bangalore"></textarea>
            </div>

            <div class="modal-actions">
              <button type="button" class="ghost-btn" (click)="closeCreateModal()" [disabled]="creating">Cancel</button>
              <button type="submit" class="primary-btn" [disabled]="createForm.invalid || creating">
                {{ creating ? 'Provisioning...' : 'Create Restaurant' }}
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Suspend confirmation dialog -->
      <app-confirm-dialog
        *ngIf="suspendTarget()"
        title="Suspend Business"
        [message]="'Are you sure you want to suspend ' + (suspendTarget()!.shopName || 'this business') + '? This will disable all operations for this business.'"
        confirmLabel="Suspend"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="executeSuspend()"
        (cancelled)="suspendTarget.set(null)"
      ></app-confirm-dialog>
    </div>
  `,
  styles: [`
    /* Sign-out twins on unique class names: no global stylesheet defines
       .biz-action-btn, so no ghost-btn variant collision can strip the fill. */
    .biz-action-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--kb-space-2);
      padding: 0.65rem var(--kb-space-4);
      border-radius: var(--kb-radius-xl);
      border: 1px solid transparent;
      font-weight: 600;
      font-size: var(--kb-text-md);
      cursor: pointer;
      transition: box-shadow 150ms ease;
      white-space: nowrap;
    }
    .biz-action-btn--danger {
      background: var(--kb-color-error);
      border-color: var(--kb-color-error);
      color: var(--kb-color-foreground-contrast);
    }
    .biz-action-btn--danger:hover {
      background: var(--kb-color-error);
      border-color: var(--kb-color-error);
      color: var(--kb-color-foreground-contrast);
    }
    .biz-action-btn--success {
      background: var(--kb-color-success);
      border-color: var(--kb-color-success);
      color: var(--kb-color-foreground-contrast);
    }
    .biz-action-btn--success:hover {
      background: var(--kb-color-success);
      border-color: var(--kb-color-success);
      color: var(--kb-color-foreground-contrast);
    }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(20, 15, 10, 0.6);
      backdrop-filter: blur(4px);
      display: grid;
      place-items: center;
      z-index: var(--kb-z-modal);
      padding: var(--kb-space-4);
    }
    .modal-box {
      background: var(--panel);
      border-radius: var(--kb-radius-xl);
      padding: 1.75rem;
      max-width: 640px;
      width: 100%;
      box-shadow: var(--shadow-lg);
      border: 1px solid var(--line);
      max-height: 90vh;
      overflow-y: auto;
    }
    .form-group {
      margin-bottom: 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .field-error {
      color: var(--kb-color-error);
      font-size: var(--kb-text-sm);
    }
    .form-error {
      background: var(--kb-color-error-soft);
      border: 1px solid var(--kb-color-error-soft);
      color: var(--kb-color-error);
      padding: 0.65rem 0.85rem;
      border-radius: var(--kb-radius-md);
      font-size: var(--kb-text-md);
      margin-bottom: 1rem;
    }
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--kb-space-3);
      margin-top: 1.5rem;
    }
    .toolbar--inline {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      flex-wrap: wrap;
      margin-bottom: 0;
    }
    /* Single-row header controls projected into app-page-header's right slot:
       compact search + action buttons sit beside the title block. */
    .header-controls {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
      flex-wrap: wrap;
    }
    /* ── Businesses table column rhythm (layout only: the design itself lives
       in the global stylesheet's "Tables" section) ── */
    .businesses-table th.business-col {
      min-width: 210px;
    }
    .businesses-table th.owner-col {
      min-width: 170px;
    }
    .businesses-table th.num-col,
    .businesses-table td.num-col {
      width: 76px;
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .businesses-table th.date-col,
    .businesses-table td.date-col {
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }
    /* .th-sort (the sortable-header affordance) is defined once in the global
       "Tables" section of styles.css. */
    .businesses-table th.action-col,
    .businesses-table td.action-col {
      text-align: right;
      white-space: nowrap;
      width: 150px;
    }
    @media (max-width: 640px) {
      .header-controls {
        width: 100%;
      }
      .toolbar--inline {
        flex-direction: column;
        align-items: stretch;
      }
    }
    .businesses-actions { gap: var(--kb-space-2); }
    .businesses-note { margin-bottom: var(--kb-space-4); font-size: var(--kb-text-md); }
  `]
})
export class BusinessesPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  businesses: AdminBusinessListItem[] = [];
  loaded = false;
  refreshing = false;
  private readonly cdr = inject(ChangeDetectorRef);
  loadError = '';
  readonly selectedDetail = signal<AdminBusinessDetail | null>(null);
  readonly suspendTarget = signal<AdminBusinessListItem | null>(null);

  // --- Create Restaurant State ---
  showCreateModal = false;
  creating = false;
  createError = '';

  createForm = this.fb.group({
    shopName: ['', [Validators.required]],
    ownerName: ['', [Validators.required]],
    ownerPhone: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    initialPassword: ['', [Validators.required, Validators.minLength(6)]],
    ownerEmail: ['', [Validators.email]],
    address: ['']
  });

  searchTerm = '';
  pageSize = 10;
  currentPage = 1;
  sortKey: 'joined' | 'lastUsed' | null = null;
  sortDir: 'asc' | 'desc' = 'desc';

  constructor() {
    this.loadBusinesses();
  }

  openCreateModal(): void {
    this.showCreateModal = true;
    this.createError = '';
    this.createForm.reset({
      shopName: '',
      ownerName: '',
      ownerPhone: '',
      initialPassword: '',
      ownerEmail: '',
      address: ''
    });
  }

  closeCreateModal(): void {
    if (this.creating) return;
    this.showCreateModal = false;
  }

  submitCreate(): void {
    if (this.createForm.invalid || this.creating) return;

    this.creating = true;
    this.createError = '';
    const formVal = this.createForm.value;

    const payload = {
      shopName: formVal.shopName!.trim(),
      ownerName: formVal.ownerName!.trim(),
      ownerPhone: formVal.ownerPhone!.trim(),
      initialPassword: formVal.initialPassword!,
      ...(formVal.ownerEmail?.trim() ? { ownerEmail: formVal.ownerEmail.trim() } : {}),
      ...(formVal.address?.trim() ? { address: formVal.address.trim() } : {})
    };

    this.api.createBusiness(payload).subscribe({
      next: (created) => {
        this.creating = false;
        this.showCreateModal = false;
        this.showToast(`Restaurant "${created.shopName || 'New Restaurant'}" created successfully.`, 'success');
        this.loadBusinesses();
      },
      error: (err) => {
        this.creating = false;
        this.createError = err.error?.message || err.error?.error || 'Failed to provision restaurant. Check phone number uniqueness.';
      }
    });
  }

  get filteredBusinesses(): AdminBusinessListItem[] {
    const search = this.searchTerm.trim().toLowerCase();

    return this.businesses.filter((business) => {
      const matchesSearch = !search || [
        business.shopName ?? '',
        business.ownerName ?? '',
        business.ownerLoginId ?? '',
        business.email ?? '',
        business.whatsappNumber ?? '',
        String(business.restaurantId)
      ].some((value) => value.toLowerCase().includes(search));

      return matchesSearch;
    });
  }

  get pagedBusinesses(): AdminBusinessListItem[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.sortedBusinesses.slice(start, start + this.pageSize);
  }

  /** Date-column sorting. Nulls (never-joined / never-used) always sink last. */
  get sortedBusinesses(): AdminBusinessListItem[] {
    const list = this.filteredBusinesses;
    if (!this.sortKey) return list;
    const dir = this.sortDir === 'asc' ? 1 : -1;
    const pick = this.sortKey === 'joined'
      ? (b: AdminBusinessListItem) => b.createdAt
      : (b: AdminBusinessListItem) => b.lastUsedAt;
    return [...list].sort((a, b) => {
      const va = pick(a);
      const vb = pick(b);
      if (va == null && vb == null) return 0;
      if (va == null) return 1;
      if (vb == null) return -1;
      return (va - vb) * dir;
    });
  }

  toggleSort(key: 'joined' | 'lastUsed'): void {
    if (this.sortKey !== key) {
      this.sortKey = key;
      this.sortDir = 'desc';
    } else {
      this.sortDir = this.sortDir === 'desc' ? 'asc' : 'desc';
    }
    this.currentPage = 1;
  }

  goToPage(page: number): void {
    const total = Math.max(1, Math.ceil(this.sortedBusinesses.length / this.pageSize));
    this.currentPage = Math.min(Math.max(1, page), total);
    this.cdr.markForCheck();
  }

  loadBusinesses(): void {
    // Clear the rows on every load, including a manual refresh, so the
    // skeletons take over and the reload is visible (as on Terminals).
    this.refreshing = true;
    this.loaded = false;
    this.loadError = '';
    this.api.getBusinesses().subscribe({
      next: (data) => {
        this.businesses = data;
        this.loaded = true;
        this.refreshing = false;
        this.loadError = '';
        this.currentPage = 1;
        this.cdr.markForCheck();
      },
      error: () => {
        if (!this.businesses.length) {
          this.businesses = [];
        }
        this.loadError = 'Unable to load businesses. Check your connection and try again.';
        this.loaded = true;
        this.refreshing = false;
        this.cdr.markForCheck();
      }
    });
  }

  resetVisible(): void {
    this.currentPage = 1;
  }

  showDetails(business: AdminBusinessListItem): void {
    this.selectedDetail.set(null);
    this.api.getBusinessDetail(business.restaurantId).subscribe({
      next: (detail) => { this.selectedDetail.set(detail); },
      error: () => this.showToast('Unable to load business details.', 'error')
    });
  }

  clearDetail(): void {
    this.selectedDetail.set(null);
  }

  confirmSuspend(business: AdminBusinessListItem): void {
    this.suspendTarget.set(business);
  }

  executeSuspend(): void {
    const target = this.suspendTarget();
    if (!target) return;
    this.suspendTarget.set(null);

    this.api.suspendBusiness(target.restaurantId).subscribe({
      next: () => {
        target.isSuspended = true;
        this.showToast(`${target.shopName || 'Business'} has been suspended.`, 'success');
      },
      error: () => {
        this.showToast(`Failed to suspend ${target.shopName || 'business'}.`, 'error');
      }
    });
  }

  activateBusiness(business: AdminBusinessListItem): void {
    this.api.activateBusiness(business.restaurantId).subscribe({
      next: () => {
        business.isSuspended = false;
        this.showToast(`${business.shopName || 'Business'} has been activated.`, 'success');
      },
      error: () => {
        this.showToast(`Failed to activate ${business.shopName || 'business'}.`, 'error');
      }
    });
  }

  private showToast(message: string, kind: 'success' | 'error'): void {
    this.toast.show(message, kind);
  }

  formatDateValue(value: number | null): string { return formatDate(value); }
  formatCurrencyValue(value: number): string { return formatCurrency(value); }

  /** The detail strip, in the shared KPI anatomy used by every page. */
  detailKpis(detail: AdminBusinessDetail): KpiItem[] {
    return [
      { label: 'Total revenue', value: this.formatCurrencyValue(detail.totalRevenue), caption: 'Settled lifetime sales' },
      { label: 'POS orders', value: detail.posOrderCount, caption: 'Bills raised at the counter' },
      { label: 'GST', value: detail.gstEnabled ? 'Enabled' : 'Disabled', tone: detail.gstEnabled ? 'success' : 'default', caption: detail.gstEnabled ? 'Tax invoicing is on' : 'Tax invoicing is off' },
      { label: 'Timezone', value: detail.timezone || '-', caption: 'Used for daily closing' },
      { label: 'Currency', value: detail.currency || '-', caption: 'Billing currency' },
    ];
  }

  trackByIndex = (_: number, __: unknown) => _;
  trackByRestaurantId = (_: number, biz: AdminBusinessListItem) => biz.restaurantId;
}
