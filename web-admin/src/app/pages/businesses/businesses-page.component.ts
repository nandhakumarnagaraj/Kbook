import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBusinessDetail, AdminBusinessListItem, PaymentConfig } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <div class="toolbar">
        <div>
          <h2>Businesses</h2>
          <p class="muted">Business directory for platform admins.</p>
        </div>
        <button class="ghost-btn" (click)="loadBusinesses()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="loaded && businesses.length; else loading">
        <table>
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
            <tr *ngFor="let business of businesses" (click)="showDetails(business)" style="cursor:pointer">
              <td>
                <strong>{{ business.shopName || '-' }}</strong><br>
                <span class="muted">#{{ business.restaurantId }}</span>
              </td>
              <td>{{ business.ownerName || '-' }}</td>
              <td>{{ business.orderCount }}</td>
              <td>{{ business.menuCount }}</td>
              <td>{{ business.staffCount }}</td>
              <td>{{ formatDateValue(business.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <ng-template #loading>
        <div class="panel loading">
          {{ loadError ? loadError : (loaded ? 'No businesses found.' : 'Loading businesses...') }}
        </div>
      </ng-template>

      <!-- Business Detail Panel -->
      <div class="panel" style="margin-top: 1.5rem;" *ngIf="selectedDetail() as detail">
        <div class="section-head">
          <div>
            <h3>{{ detail.shopName }}</h3>
            <p class="muted">Restaurant ID: {{ detail.restaurantId }}</p>
          </div>
          <div style="display: flex; gap: 0.5rem; align-items: center;">
            <span class="chip" [class.success]="detail.websiteEnabled">
              {{ detail.websiteEnabled ? 'Storefront Live' : 'Storefront Off' }}
            </span>
            <button class="ghost-btn" (click)="clearDetail()" style="margin-left: 0.5rem;">Close</button>
          </div>
        </div>

        <div class="stats-grid" style="margin-top: 1rem;">
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

        <!-- Payment Config — view only -->
        <div style="margin-top: 1.5rem; border-top: 1px solid var(--line); padding-top: 1.5rem;">
          <h3 style="margin-bottom: 1rem;">Easebuzz Payment Config</h3>

          <div class="panel loading" *ngIf="paymentConfigState() === 'loading'">
            Loading payment config...
          </div>

          <div *ngIf="paymentConfigState() === 'not-found'" class="muted">
            Not configured — owner must set this up via their Payment Settings page.
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

  constructor() {
    this.loadBusinesses();
  }

  loadBusinesses(): void {
    this.loaded = false;
    this.loadError = '';
    this.api.getBusinesses().subscribe({
      next: (data) => {
        this.businesses = data;
        this.loaded = true;
      },
      error: () => {
        this.businesses = [];
        this.loadError = 'Unable to load businesses.';
        this.loaded = true;
      }
    });
  }

  showDetails(business: AdminBusinessListItem): void {
    this.selectedDetail.set(null);
    this.paymentConfig.set(null);
    this.paymentConfigState.set('loading');

    this.api.getBusinessDetail(business.restaurantId).subscribe({
      next: (detail) => { this.selectedDetail.set(detail); }
    });

    this.api.getBusinessPaymentConfig(business.restaurantId).subscribe({
      next: (cfg) => { this.paymentConfig.set(cfg); this.paymentConfigState.set('loaded'); },
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
