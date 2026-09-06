import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminBusinessDetail, AdminBusinessListItem } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ApiStateComponent } from '../../core/components/api-state.component';

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, ConfirmDialogComponent, EmptyStateComponent, ApiStateComponent],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Businesses</h2>
        <p class="muted">Platform directory with cleaner drill-down detail panel.</p>
        <div class="hero-meta">
          <span class="chip">Directory View</span>
          <span class="chip">Business Details</span>
        </div>
      </section>

      <div class="toolbar">
        <div>
          <h3>Business Directory</h3>
          <p class="muted">Select a row to inspect revenue and business details.</p>
        </div>
        <div style="display: flex; gap: 0.5rem; align-items: center;">
          <button class="primary-btn" (click)="openCreateModal()">+ Add Restaurant</button>
          <button class="ghost-btn" (click)="loadBusinesses()">Refresh</button>
        </div>
      </div>

      <app-api-state
        *ngIf="loadError"
        [loading]="false"
        [error]="loadError"
        (retry)="loadBusinesses()"
      ></app-api-state>

      <section class="panel filter-panel" *ngIf="loaded && businesses.length">
        <div class="filter-grid">
          <div class="filter-group">
            <label for="business-search">Search</label>
            <input
              id="business-search"
              class="field-control"
              type="text"
              [(ngModel)]="searchTerm"
              (ngModelChange)="resetPage()"
              placeholder="Search by shop, owner, login, email, or id"
            />
          </div>
          <div class="filter-group">
            <label for="business-size">Rows</label>
            <select id="business-size" class="field-select" [(ngModel)]="pageSize" (ngModelChange)="resetPage()">
              <option [ngValue]="5">5</option>
              <option [ngValue]="10">10</option>
              <option [ngValue]="20">20</option>
            </select>
          </div>
        </div>

        <div class="filter-summary">
          <p class="muted">{{ filteredBusinesses.length }} of {{ businesses.length }} businesses</p>
          <button class="ghost-btn" (click)="clearFilters()">Clear filters</button>
        </div>
      </section>

      <div class="panel table-wrap" *ngIf="loaded && pagedBusinesses.length; else loading">
        <table class="data-table">
          <thead>
            <tr>
              <th>Business</th>
              <th>Owner</th>
              <th>Orders</th>
              <th>Menu</th>
              <th>Staff</th>
              <th>Updated</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let business of pagedBusinesses">
              <td (click)="showDetails(business)" style="cursor: pointer;">
                <div class="stacked-meta">
                  <strong>{{ business.shopName || '-' }}</strong>
                  <span class="muted">#{{ business.restaurantId }}</span>
                  <span class="chip danger" *ngIf="business.isSuspended">Suspended</span>
                </div>
              </td>
              <td (click)="showDetails(business)" style="cursor: pointer;">{{ business.ownerName || '-' }}</td>
              <td (click)="showDetails(business)" style="cursor: pointer;">{{ business.orderCount }}</td>
              <td (click)="showDetails(business)" style="cursor: pointer;">{{ business.menuCount }}</td>
              <td (click)="showDetails(business)" style="cursor: pointer;">{{ business.staffCount }}</td>
              <td (click)="showDetails(business)" style="cursor: pointer;">{{ formatDateValue(business.updatedAt) }}</td>
              <td>
                <button
                  *ngIf="!business.isSuspended"
                  class="ghost-btn ghost-btn--danger"
                  (click)="confirmSuspend(business); $event.stopPropagation()">
                  Suspend
                </button>
                <button
                  *ngIf="business.isSuspended"
                  class="ghost-btn ghost-btn--accent"
                  (click)="activateBusiness(business); $event.stopPropagation()">
                  Activate
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="mobile-data-list" aria-label="Businesses">
          <article class="mobile-data-card" *ngFor="let business of pagedBusinesses" [class.mobile-data-card--danger]="business.isSuspended">
            <button type="button" class="mobile-data-card__primary" (click)="showDetails(business)">
              <span class="mobile-data-card__head"><strong>{{ business.shopName || 'Unnamed business' }}</strong><span class="chip" [class.success]="!business.isSuspended" [class.danger]="business.isSuspended">{{ business.isSuspended ? 'Suspended' : 'Active' }}</span></span>
              <span>{{ business.ownerName || 'No owner name' }} · #{{ business.restaurantId }}</span>
            </button>
            <dl><div><dt>Orders</dt><dd>{{ business.orderCount }}</dd></div><div><dt>Menu</dt><dd>{{ business.menuCount }}</dd></div><div><dt>Staff</dt><dd>{{ business.staffCount }}</dd></div></dl>
            <div class="mobile-data-card__actions">
              <button *ngIf="!business.isSuspended" class="ghost-btn danger-btn" (click)="confirmSuspend(business)">Suspend</button>
              <button *ngIf="business.isSuspended" class="ghost-btn success-btn" (click)="activateBusiness(business)">Activate</button>
            </div>
          </article>
        </div>

        <div class="pagination-bar" *ngIf="filteredBusinesses.length > pageSize">
          <p class="muted">Page {{ currentPage }} of {{ totalPages }}</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="currentPage === 1" (click)="goToPage(currentPage - 1)">Previous</button>
            <button class="ghost-btn" [disabled]="currentPage === totalPages" (click)="goToPage(currentPage + 1)">Next</button>
          </div>
        </div>
      </div>

      <ng-template #loading>
        <div class="panel loading" *ngIf="!loaded; else businessesEmpty">
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4,5]"></div>
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
          <div style="display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
            <button class="ghost-btn" (click)="clearDetail()">Close</button>
          </div>
        </div>

        <div class="stats-grid">
          <article class="panel stat-card">
            <h3>Total Revenue</h3>
            <strong>{{ formatCurrencyValue(detail.totalRevenue) }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>POS Orders</h3>
            <strong>{{ detail.posOrderCount }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>GST</h3>
            <strong>{{ detail.gstEnabled ? 'Enabled' : 'Disabled' }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Timezone</h3>
            <strong>{{ detail.timezone || '-' }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Currency</h3>
            <strong>{{ detail.currency || '-' }}</strong>
          </article>
        </div>
      </div>

      <!-- Create Restaurant Modal -->
      <div class="modal-backdrop" *ngIf="showCreateModal" (click)="closeCreateModal()">
        <div class="modal-box modal-content" role="dialog" aria-modal="true" aria-labelledby="create-business-title" (click)="$event.stopPropagation()">
          <h3 id="create-business-title">Add New Restaurant</h3>
          <p class="muted" style="margin-bottom: 1rem; font-size: 0.85rem;">
            Provision a new restaurant tenant and initial owner account.
          </p>

          <div class="form-error" *ngIf="createError">{{ createError }}</div>

          <form [formGroup]="createForm" (ngSubmit)="submitCreate()">
            <div class="form-group">
              <label for="create-shop-name">Restaurant / Shop Name *</label>
              <input id="create-shop-name" class="field-control" type="text" formControlName="shopName" placeholder="e.g. Sagar Ratna Cafe" />
              <div class="field-error" *ngIf="createForm.get('shopName')?.touched && createForm.get('shopName')?.hasError('required')">
                Restaurant name is required.
              </div>
            </div>

            <div class="form-group">
              <label for="create-owner-name">Owner Full Name *</label>
              <input id="create-owner-name" class="field-control" type="text" formControlName="ownerName" placeholder="e.g. Ramesh Sharma" />
              <div class="field-error" *ngIf="createForm.get('ownerName')?.touched && createForm.get('ownerName')?.hasError('required')">
                Owner name is required.
              </div>
            </div>

            <div class="form-group">
              <label for="create-owner-phone">Owner Phone (10 digits) *</label>
              <input id="create-owner-phone" class="field-control" type="text" formControlName="ownerPhone" placeholder="10-digit mobile number" maxlength="10" />
              <div class="field-error" *ngIf="createForm.get('ownerPhone')?.touched && createForm.get('ownerPhone')?.hasError('required')">
                Phone number is required.
              </div>
              <div class="field-error" *ngIf="createForm.get('ownerPhone')?.touched && createForm.get('ownerPhone')?.hasError('pattern') && !createForm.get('ownerPhone')?.hasError('required')">
                Must be exactly 10 digits.
              </div>
            </div>

            <div class="form-group">
              <label for="create-initial-pwd">Initial Password *</label>
              <input id="create-initial-pwd" class="field-control" type="password" formControlName="initialPassword" placeholder="Minimum 6 characters" />
              <div class="field-error" *ngIf="createForm.get('initialPassword')?.touched && createForm.get('initialPassword')?.hasError('required')">
                Initial password is required.
              </div>
              <div class="field-error" *ngIf="createForm.get('initialPassword')?.touched && createForm.get('initialPassword')?.hasError('minlength')">
                Password must be at least 6 characters.
              </div>
            </div>

            <div class="form-group">
              <label for="create-owner-email">Owner Email (optional)</label>
              <input id="create-owner-email" class="field-control" type="email" formControlName="ownerEmail" placeholder="e.g. owner@example.com" />
              <div class="field-error" *ngIf="createForm.get('ownerEmail')?.touched && createForm.get('ownerEmail')?.hasError('email')">
                Enter a valid email address.
              </div>
            </div>

            <div class="form-group">
              <label for="create-address">Address / City (optional)</label>
              <input id="create-address" class="field-control" type="text" formControlName="address" placeholder="e.g. Indiranagar, Bangalore" />
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
    .ghost-btn--danger {
      color: var(--kb-color-error); border-color: var(--kb-color-error);
    }
    .ghost-btn--accent {
      color: var(--kb-color-primary); border-color: var(--kb-color-primary);
    }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(20, 15, 10, 0.6);
      backdrop-filter: blur(4px);
      display: grid;
      place-items: center;
      z-index: 1000;
      padding: 1rem;
    }
    .modal-box {
      background: var(--panel);
      border-radius: var(--r-xl);
      padding: 1.75rem;
      max-width: 520px;
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
      color: var(--kb-color-error, #a6372f);
      font-size: 0.78rem;
    }
    .form-error {
      background: #fdf2f2;
      border: 1px solid #f8d7da;
      color: #a6372f;
      padding: 0.65rem 0.85rem;
      border-radius: var(--r-md);
      font-size: 0.85rem;
      margin-bottom: 1rem;
    }
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      margin-top: 1.5rem;
    }
  `]
})
export class BusinessesPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  businesses: AdminBusinessListItem[] = [];
  loaded = false;
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
    return this.filteredBusinesses.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredBusinesses.length / this.pageSize));
  }

  loadBusinesses(): void {
    this.loaded = false;
    this.loadError = '';
    this.api.getBusinesses().subscribe({
      next: (data) => {
        this.businesses = data;
        this.loaded = true;
        this.currentPage = 1;
      },
      error: () => {
        this.businesses = [];
        this.loadError = 'Unable to load businesses.';
        this.loaded = true;
      }
    });
  }

  resetPage(): void {
    this.currentPage = 1;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.pageSize = 10;
    this.currentPage = 1;
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(1, page), this.totalPages);
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
}
