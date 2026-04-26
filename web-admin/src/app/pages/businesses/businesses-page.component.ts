import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBusinessDetail, AdminBusinessListItem, PaymentConfig } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="page-shell">
      <div class="toolbar">
        <div>
          <h2>Businesses</h2>
          <p class="muted">Business directory for platform admins.</p>
        </div>
        <button class="ghost-btn" (click)="loadBusinesses()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="businesses.length; else loading">
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
        <div class="panel loading">Loading businesses...</div>
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
            <span class="chip" [class.success]="detail.easebuzzEnabled">
              {{ detail.easebuzzEnabled ? 'Easebuzz On' : 'Easebuzz Off' }}
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

        <!-- Payment Config Section -->
        <div style="margin-top: 1.5rem; border-top: 1px solid var(--line); padding-top: 1.5rem;">
          <h3 style="margin-bottom: 1rem;">Easebuzz Payment Config</h3>

          <!-- Loading -->
          <div class="panel loading" *ngIf="paymentConfigState() === 'loading'">
            Loading payment config...
          </div>

          <!-- Existing config display -->
          <div *ngIf="paymentConfigState() === 'loaded' && paymentConfig() as cfg" style="margin-bottom: 1rem;">
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

          <!-- Not configured notice -->
          <div *ngIf="paymentConfigState() === 'not-found'" style="margin-bottom: 1rem; color: var(--muted);">
            No Easebuzz config set up for this business yet.
          </div>

          <!-- Save form -->
          <div *ngIf="paymentConfigState() !== 'loading'">
            <p class="muted" style="margin-bottom: 0.75rem;">
              {{ paymentConfigState() === 'not-found' ? 'Set up credentials:' : 'Update credentials (salt is required every save):' }}
            </p>
            <form [formGroup]="paymentForm" (ngSubmit)="savePaymentConfig(detail.restaurantId)"
                  style="display: grid; grid-template-columns: 1fr 1fr auto auto; gap: 0.75rem; align-items: end;">
              <label style="display: grid; gap: 0.35rem; font-weight: 600; font-size: 0.9rem;">
                Merchant Key
                <input formControlName="merchantKey" placeholder="Merchant key"
                       style="border: 1px solid var(--line); border-radius: 10px; padding: 0.7rem 0.9rem;">
              </label>
              <label style="display: grid; gap: 0.35rem; font-weight: 600; font-size: 0.9rem;">
                Salt
                <input formControlName="salt" placeholder="Salt (required every save)"
                       style="border: 1px solid var(--line); border-radius: 10px; padding: 0.7rem 0.9rem;">
              </label>
              <label style="display: grid; gap: 0.35rem; font-weight: 600; font-size: 0.9rem;">
                Env
                <select formControlName="environment"
                        style="border: 1px solid var(--line); border-radius: 10px; padding: 0.7rem 0.9rem; background: #fff;">
                  <option value="TEST">TEST</option>
                  <option value="PROD">PROD</option>
                </select>
              </label>
              <button class="ghost-btn" type="submit"
                      [disabled]="paymentForm.invalid || paymentConfigSaveState() === 'saving'"
                      style="padding: 0.7rem 1.2rem;">
                {{ paymentConfigSaveState() === 'saving' ? 'Saving...' : 'Save' }}
              </button>
            </form>

            <div *ngIf="paymentConfigSaveState() === 'saved'"
                 style="margin-top: 0.75rem; color: #2d7a3a; background: #eafaf0; border-radius: 8px; padding: 0.6rem 0.9rem; font-size: 0.9rem;">
              Saved — Merchant Key: <strong>{{ paymentConfigSavedKey() }}</strong>
            </div>
            <div *ngIf="paymentConfigSaveState() === 'error'"
                 style="margin-top: 0.75rem; color: #b03030; background: #fdf0f0; border-radius: 8px; padding: 0.6rem 0.9rem; font-size: 0.9rem;">
              {{ paymentConfigSaveError() }}
            </div>
          </div>
        </div>
      </div>

    </div>
  `
})
export class BusinessesPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);

  businesses: AdminBusinessListItem[] = [];
  readonly selectedDetail = signal<AdminBusinessDetail | null>(null);
  readonly paymentConfig = signal<PaymentConfig | null>(null);
  readonly paymentConfigState = signal<'loading' | 'not-found' | 'loaded'>('loading');
  readonly paymentConfigSaveState = signal<'idle' | 'saving' | 'saved' | 'error'>('idle');
  readonly paymentConfigSaveError = signal('');
  readonly paymentConfigSavedKey = signal('');

  readonly paymentForm = this.fb.nonNullable.group({
    merchantKey: ['', Validators.required],
    salt: ['', Validators.required],
    environment: ['TEST' as 'TEST' | 'PROD', Validators.required]
  });

  constructor() {
    this.loadBusinesses();
  }

  loadBusinesses(): void {
    this.api.getBusinesses().subscribe({ next: (data) => { this.businesses = data; } });
  }

  showDetails(business: AdminBusinessListItem): void {
    this.selectedDetail.set(null);
    this.paymentConfig.set(null);
    this.paymentConfigState.set('loading');
    this.paymentConfigSaveState.set('idle');
    this.paymentForm.reset({ merchantKey: '', salt: '', environment: 'TEST' });

    this.api.getBusinessDetail(business.restaurantId).subscribe({
      next: (detail) => { this.selectedDetail.set(detail); }
    });

    this.api.getBusinessPaymentConfig(business.restaurantId).subscribe({
      next: (cfg) => {
        this.paymentConfig.set(cfg);
        this.paymentForm.patchValue({ environment: cfg.environment as 'TEST' | 'PROD' });
        this.paymentConfigState.set('loaded');
      },
      error: () => { this.paymentConfigState.set('not-found'); }
    });
  }

  clearDetail(): void {
    this.selectedDetail.set(null);
    this.paymentConfig.set(null);
  }

  savePaymentConfig(restaurantId: number): void {
    if (this.paymentForm.invalid) return;
    this.paymentConfigSaveState.set('saving');
    this.paymentConfigSaveError.set('');

    this.api.saveBusinessPaymentConfig(restaurantId, this.paymentForm.getRawValue()).subscribe({
      next: (cfg) => {
        this.paymentConfig.set(cfg);
        this.paymentConfigSavedKey.set(cfg.merchantKeyMasked);
        this.paymentConfigState.set('loaded');
        this.paymentConfigSaveState.set('saved');
        this.paymentForm.patchValue({ merchantKey: '', salt: '', environment: cfg.environment as 'TEST' | 'PROD' });
      },
      error: (err) => {
        this.paymentConfigSaveState.set('error');
        this.paymentConfigSaveError.set(err?.error?.error ?? err?.error?.message ?? 'Save failed.');
      }
    });
  }

  formatDateValue(value: number | null): string { return formatDate(value); }
  formatCurrencyValue(value: number): string { return formatCurrency(value); }
}
