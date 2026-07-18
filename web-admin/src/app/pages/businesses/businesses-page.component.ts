import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminBusinessDetail, AdminBusinessListItem } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent],
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
        <button class="ghost-btn" (click)="loadBusinesses()">Refresh</button>
      </div>

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
      color: var(--danger, #EF4444);
      border-color: var(--danger, #EF4444);
    }
    .ghost-btn--accent {
      color: var(--accent, #14B8A6);
      border-color: var(--accent, #14B8A6);
    }
  `]
})
export class BusinessesPageComponent {
  private readonly api = inject(AdminApiService);

  businesses: AdminBusinessListItem[] = [];
  loaded = false;
  loadError = '';
  readonly selectedDetail = signal<AdminBusinessDetail | null>(null);
  readonly suspendTarget = signal<AdminBusinessListItem | null>(null);
  private readonly toast = inject(ToastService);

  searchTerm = '';
  pageSize = 10;
  currentPage = 1;

  constructor() {
    this.loadBusinesses();
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
      next: (detail) => { this.selectedDetail.set(detail); }
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
