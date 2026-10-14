import { CommonModule } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
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
import { RefreshButtonComponent } from '../../shared/refresh-button.component';

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, ConfirmDialogComponent, EmptyStateComponent, ApiStateComponent, PageHeaderComponent, HeaderSearchComponent, RefreshButtonComponent],
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

      <div class="panel table-wrap" *ngIf="loaded && visibleBusinesses.length; else loading">
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
            <tr *ngFor="let business of visibleBusinesses; trackBy: trackByRestaurantId">
              <td (click)="showDetails(business)" style="cursor: pointer;">
                <div class="stacked-meta">
                  <strong>{{ business.shopName || '-' }}</strong>
                  <span class="muted">#{{ business.restaurantId }}</span>
                  <span class="chip danger" *ngIf="business.isSuspended">Suspended</span>
                </div>
              </td>
              <td (click)="showDetails(business)" style="cursor: pointer;">
                <div class="stacked-meta">
                  <strong>{{ business.ownerName || '-' }}</strong>
                  <span class="muted" *ngIf="business.ownerLoginId">{{ business.ownerLoginId }}</span>
                </div>
              </td>
              <td class="num-col" (click)="showDetails(business)" style="cursor: pointer;">{{ business.orderCount }}</td>
              <td class="num-col" (click)="showDetails(business)" style="cursor: pointer;">{{ business.menuCount }}</td>
              <td class="num-col" (click)="showDetails(business)" style="cursor: pointer;">{{ business.staffCount }}</td>
              <td class="date-col" (click)="showDetails(business)" style="cursor: pointer;">{{ formatDateValue(business.createdAt) }}</td>
              <td class="date-col" (click)="showDetails(business)" style="cursor: pointer;">
                <span *ngIf="business.lastUsedAt; else neverUsed">{{ formatDateValue(business.lastUsedAt) }}</span>
                <ng-template #neverUsed><span class="muted">No orders yet</span></ng-template>
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
          <article class="mobile-data-card" *ngFor="let business of visibleBusinesses; trackBy: trackByRestaurantId" [class.mobile-data-card--danger]="business.isSuspended">
            <button type="button" class="mobile-data-card__primary" (click)="showDetails(business)">
              <span class="mobile-data-card__head"><strong>{{ business.shopName || 'Unnamed business' }}</strong><span class="chip" [class.success]="!business.isSuspended" [class.danger]="business.isSuspended">{{ business.isSuspended ? 'Suspended' : 'Active' }}</span></span>
              <span>{{ business.ownerName || 'No owner name' }} · #{{ business.restaurantId }}</span>
            </button>
            <dl><div><dt>Orders</dt><dd>{{ business.orderCount }}</dd></div><div><dt>Menu</dt><dd>{{ business.menuCount }}</dd></div><div><dt>Staff</dt><dd>{{ business.staffCount }}</dd></div><div><dt>Joined</dt><dd>{{ formatDateValue(business.createdAt) }}</dd></div><div><dt>Last used</dt><dd>{{ business.lastUsedAt ? formatDateValue(business.lastUsedAt) : 'No orders yet' }}</dd></div></dl>
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

        <div class="infinite-footer" aria-live="polite">
          <div #infiniteSentinel class="infinite-sentinel" aria-hidden="true"></div>
          <p class="muted infinite-status" *ngIf="hasMore">
            Showing {{ visibleBusinesses.length }} of {{ filteredBusinesses.length }} — scroll for more
          </p>
          <p class="muted infinite-status" *ngIf="!hasMore && filteredBusinesses.length > pageBatch">
            All {{ filteredBusinesses.length }} businesses shown
          </p>
        </div>
      </div>

      <ng-template #loading>
        <div class="panel loading" *ngIf="!loaded && !loadError; else businessesEmpty" role="status" aria-busy="true">
          <span class="sr-only">Loading businesses...</span>
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4,5]; trackBy: trackByIndex"></div>
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
    /* Sign-out twins on unique class names: no global stylesheet defines
       .biz-action-btn, so no ghost-btn variant collision can strip the fill. */
    .biz-action-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.65rem 1rem;
      border-radius: 12px;
      border: 1px solid transparent;
      font-weight: 600;
      font-size: 0.85rem;
      cursor: pointer;
      transition: box-shadow 140ms ease;
      white-space: nowrap;
    }
    .biz-action-btn--danger {
      background: var(--kb-color-error);
      border-color: var(--kb-color-error);
      color: #FFFFFF;
      box-shadow: 0 2px 8px rgba(192, 57, 43, 0.25);
    }
    .biz-action-btn--danger:hover {
      background: var(--kb-color-error);
      border-color: var(--kb-color-error);
      color: #FFFFFF;
      box-shadow: 0 4px 14px rgba(192, 57, 43, 0.4);
    }
    .biz-action-btn--success {
      background: var(--kb-color-success);
      border-color: var(--kb-color-success);
      color: #FFFFFF;
      box-shadow: 0 2px 8px rgba(47, 133, 90, 0.25);
    }
    .biz-action-btn--success:hover {
      background: var(--kb-color-success);
      border-color: var(--kb-color-success);
      color: #FFFFFF;
      box-shadow: 0 4px 14px rgba(47, 133, 90, 0.4);
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
      color: var(--kb-color-error);
      font-size: 0.78rem;
    }
    .form-error {
      background: var(--kb-color-error-soft);
      border: 1px solid var(--kb-color-error-soft);
      color: var(--kb-color-error);
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
    .toolbar--inline {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      flex-wrap: wrap;
      margin-bottom: 0;
    }
    /* Single-row header controls projected into app-page-header's right slot:
       compact search + action buttons sit beside the title block. */
    .header-controls {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .infinite-footer {
      padding: 0.85rem 1rem 1rem;
      text-align: center;
    }
    .infinite-sentinel {
      height: 1px;
    }
    .infinite-status {
      margin: 0.35rem 0 0;
      font-size: 0.82rem;
    }
    /* ── Businesses table column rhythm ── */
    .businesses-table th,
    .businesses-table td {
      padding: 0.8rem 1rem;
      vertical-align: middle;
    }
    .businesses-table thead th {
      background: var(--brand-soft);
      color: var(--brand-deep);
      font-weight: 700;
      border-bottom: 2px solid var(--brand);
    }
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
    .th-sort {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0;
      background: none;
      border: none;
      color: inherit;
      font: inherit;
      font-weight: inherit;
      letter-spacing: inherit;
      text-transform: inherit;
      cursor: pointer;
    }
    .th-sort:focus-visible {
      outline: 2px solid currentColor;
      outline-offset: 2px;
    }
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
  `]
})
export class BusinessesPageComponent implements AfterViewInit, OnDestroy {
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
  pageBatch = 20;
  visibleCount = 20;
  sortKey: 'joined' | 'lastUsed' | null = null;
  sortDir: 'asc' | 'desc' = 'desc';

  @ViewChild('infiniteSentinel') private infiniteSentinel?: ElementRef<HTMLElement>;
  private infiniteObserver?: IntersectionObserver;

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

  get visibleBusinesses(): AdminBusinessListItem[] {
    return this.sortedBusinesses.slice(0, this.visibleCount);
  }

  get hasMore(): boolean {
    return this.visibleCount < this.sortedBusinesses.length;
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
    this.visibleCount = this.pageBatch;
    setTimeout(() => this.observeInfiniteSentinel());
  }

  @HostListener('window:scroll', [])
  onWindowScroll(): void {
    if (!this.loaded || !this.hasMore) return;
    // Fallback for browsers without IntersectionObserver + extra safety net.
    const thresholdPx = 500;
    const scrolledTo = window.innerHeight + window.scrollY;
    const pageHeight = document.documentElement.scrollHeight;
    if (pageHeight - scrolledTo < thresholdPx) {
      this.loadMore();
    }
  }

  ngAfterViewInit(): void {
    this.observeInfiniteSentinel();
  }

  ngOnDestroy(): void {
    this.infiniteObserver?.disconnect();
  }

  private observeInfiniteSentinel(): void {
    if (typeof IntersectionObserver === 'undefined') return;
    this.infiniteObserver?.disconnect();
    const target = this.infiniteSentinel?.nativeElement;
    if (!target) return;
    this.infiniteObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          this.loadMore();
        }
      },
      { rootMargin: '500px 0px' }
    );
    this.infiniteObserver.observe(target);
  }

  loadMore(): void {
    if (!this.hasMore) return;
    this.visibleCount = Math.min(this.visibleCount + this.pageBatch, this.filteredBusinesses.length);
    this.cdr.markForCheck();
  }

  loadBusinesses(): void {
    // Keep stale rows on screen during a manual refresh: full skeleton only
    // on the very first load when there is nothing to show yet.
    this.refreshing = true;
    if (!this.businesses.length) {
      this.loaded = false;
    }
    this.loadError = '';
    this.api.getBusinesses().subscribe({
      next: (data) => {
        this.businesses = data;
        this.loaded = true;
        this.refreshing = false;
        this.loadError = '';
        this.visibleCount = this.pageBatch;
        this.cdr.markForCheck();
        // Sentinel is re-created by *ngIf; observe on next tick.
        setTimeout(() => this.observeInfiniteSentinel());
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
    this.visibleCount = this.pageBatch;
    // Re-arm sentinel after filter shrinks/grows the list.
    setTimeout(() => this.observeInfiniteSentinel());
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

  trackByIndex = (_: number, __: unknown) => _;
  trackByRestaurantId = (_: number, biz: AdminBusinessListItem) => biz.restaurantId;
}
