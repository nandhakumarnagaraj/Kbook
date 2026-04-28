import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBusinessDetail, AdminBusinessListItem, PaymentConfig } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Businesses</h2>
        <p class="muted">Platform directory with clearer alignment for business details and a cleaner drill-down detail panel.</p>
        <div class="hero-meta">
          <span class="chip">Directory View</span>
          <span class="chip success">Storefront Health</span>
          <span class="chip">Payment Config Review</span>
        </div>
      </section>

      <div class="toolbar">
        <div>
          <h3>Business Directory</h3>
          <p class="muted">Select a row to inspect revenue, storefront status, and payment configuration.</p>
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
            <label for="business-storefront">Storefront</label>
            <select id="business-storefront" class="field-select" [(ngModel)]="storefrontFilter" (ngModelChange)="resetPage()">
              <option value="ALL">All storefront states</option>
              <option value="LIVE">Live</option>
              <option value="OFF">Off</option>
            </select>
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
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let business of pagedBusinesses" (click)="showDetails(business)" style="cursor: pointer;">
              <td>
                <div class="stacked-meta">
                  <strong>{{ business.shopName || '-' }}</strong>
                  <span class="muted">#{{ business.restaurantId }}</span>
                </div>
              </td>
              <td>{{ business.ownerName || '-' }}</td>
              <td>{{ business.orderCount }}</td>
              <td>{{ business.menuCount }}</td>
              <td>{{ business.staffCount }}</td>
              <td>{{ formatDateValue(business.updatedAt) }}</td>
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
        <div class="panel loading">
          {{ loadError ? loadError : (loaded ? 'No businesses match the current filters.' : 'Loading businesses...') }}
        </div>
      </ng-template>

      <div class="panel soft-section" *ngIf="selectedDetail() as detail">
        <div class="section-head">
          <div>
            <h3>{{ detail.shopName }}</h3>
            <p class="muted">Restaurant ID: {{ detail.restaurantId }}</p>
          </div>
          <div style="display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
            <span class="chip" [class.success]="detail.websiteEnabled">
              {{ detail.websiteEnabled ? 'Storefront Live' : 'Storefront Off' }}
            </span>
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
            <h3>Online Orders</h3>
            <strong>{{ detail.onlineOrderCount }}</strong>
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

        <div style="margin-top: 1.5rem; border-top: 1px solid var(--line); padding-top: 1.5rem;">
          <h3 style="margin-bottom: 1rem;">Easebuzz Payment Config</h3>

          <div class="panel loading" *ngIf="paymentConfigState() === 'loading'">
            Loading payment config...
          </div>

          <div *ngIf="paymentConfigState() === 'not-found'" class="muted">
            Not configured - owner must set this up via their Payment Settings page.
          </div>

          <div *ngIf="paymentConfigState() === 'loaded' && paymentConfig() as cfg">
            <div class="stats-grid">
              <article class="panel stat-card">
                <h3>Merchant Key</h3>
                <strong style="font-family: monospace;">{{ cfg.merchantKeyMasked }}</strong>
              </article>
              <article class="panel stat-card">
                <h3>Environment</h3>
                <strong>
                  <span class="chip" [class.warn]="cfg.environment === 'TEST'" [class.success]="cfg.environment === 'PROD'">
                    {{ cfg.environment }}
                  </span>
                </strong>
              </article>
              <article class="panel stat-card">
                <h3>Status</h3>
                <strong>
                  <span class="chip" [class.success]="cfg.active" [class.danger]="!cfg.active">
                    {{ cfg.active ? 'Active' : 'Inactive' }}
                  </span>
                </strong>
              </article>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class BusinessesPageComponent {
  private readonly api = inject(AdminApiService);

  businesses: AdminBusinessListItem[] = [];
  loaded = false;
  loadError = '';
  readonly selectedDetail = signal<AdminBusinessDetail | null>(null);
  readonly paymentConfig = signal<PaymentConfig | null>(null);
  readonly paymentConfigState = signal<'loading' | 'not-found' | 'loaded'>('loading');

  searchTerm = '';
  storefrontFilter: 'ALL' | 'LIVE' | 'OFF' = 'ALL';
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

      const matchesStorefront =
        this.storefrontFilter === 'ALL' ||
        (this.storefrontFilter === 'LIVE' && business.websiteEnabled) ||
        (this.storefrontFilter === 'OFF' && !business.websiteEnabled);

      return matchesSearch && matchesStorefront;
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
    this.storefrontFilter = 'ALL';
    this.pageSize = 10;
    this.currentPage = 1;
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(1, page), this.totalPages);
  }

  showDetails(business: AdminBusinessListItem): void {
    this.selectedDetail.set(null);
    this.paymentConfig.set(null);
    this.paymentConfigState.set('loading');

    this.api.getBusinessDetail(business.restaurantId).subscribe({
      next: (detail) => { this.selectedDetail.set(detail); }
    });

    this.api.getBusinessPaymentConfig(business.restaurantId).subscribe({
      next: (cfg) => {
        this.paymentConfig.set(cfg);
        this.paymentConfigState.set('loaded');
      },
      error: () => { this.paymentConfigState.set('not-found'); }
    });
  }

  clearDetail(): void {
    this.selectedDetail.set(null);
    this.paymentConfig.set(null);
  }

  formatDateValue(value: number | null): string { return formatDate(value); }
  formatCurrencyValue(value: number): string { return formatCurrency(value); }
}
