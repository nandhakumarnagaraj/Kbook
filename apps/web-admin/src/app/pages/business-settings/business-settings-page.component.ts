import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../core/services/toast.service';
import { BusinessApiService } from '../../core/services/business-api.service';
import { TaxSettingsSectionComponent, TaxSettings } from './tax-settings-section.component';
import { PaymentMethodsSectionComponent, PaymentMethodsSettings } from './payment-methods-section.component';
import { PasswordChangeSectionComponent } from './password-change-section.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { ApiStateComponent } from '../../core/components/api-state.component';

interface RestaurantProfile {
  shopName: string;
  shopAddress: string;
  whatsappNumber: string;
  email: string;
  logoUrl: string;
  gstEnabled: boolean;
  gstin: string;
  gstPercentage: number;
  customTaxName: string;
  customTaxPercentage: number;
  fssaiNumber: string;
  fssaiExpiryDate: string;
  gstExpiryDate: string;
  upiEnabled: boolean;
  upiHandle: string;
  upiMobile: string;
  cashEnabled: boolean;
  posEnabled: boolean;
  orderPaymentFlowMode: string;
  invoiceFooter: string;
  reviewUrl: string;
}

@Component({
  selector: 'app-business-settings-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    TaxSettingsSectionComponent,
    PaymentMethodsSectionComponent,
    PasswordChangeSectionComponent,
    PageHeaderComponent,
    ApiStateComponent,
    RefreshButtonComponent
  ],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Business &amp; Restaurant Settings"
        subtitle="Restaurant identity, GST compliance and payment channels. Changes sync to every POS register after you save."
      >
        <span kbHeaderBadge class="kpi-tag kpi-tag--green" *ngIf="!loading() && !loadError()">
          <span class="pulse-indicator" aria-hidden="true"></span>
          Live sync ready
        </span>
        <app-refresh-button (refresh)="load()" [loading]="loading()" ariaLabel="Reload settings from server" />
      </app-page-header>

      <!-- Shown when a reload fails but previously loaded values are still on screen -->
      <div class="stale-banner" *ngIf="!loading() && loadError() && profile()" role="alert">
        <span>Couldn't refresh settings. You're seeing the last values loaded. {{ loadError() }}</span>
        <button type="button" class="btn-retry" (click)="load()">Retry</button>
      </div>

      <div class="panel loading loading-panel" *ngIf="loading()" role="status" aria-busy="true">
        <span class="visually-hidden">Loading settings...</span>
        <div class="skeleton-stack">
          <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4]"></div>
        </div>
      </div>

      <app-api-state
        *ngIf="!loading() && loadError() && !profile()"
        [message]="loadError()"
        (retry)="load()"
      ></app-api-state>

      <ng-container *ngIf="viewProfile() as p">

        <!-- Restaurant identity -->
        <section class="panel settings-section" aria-labelledby="identity-heading">
          <header class="section-head">
            <div class="section-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
            </div>
            <div>
              <h3 id="identity-heading">Restaurant Identity</h3>
              <p class="muted">Shown on thermal receipts, customer invoices and online menus.</p>
            </div>
          </header>

          <div class="logo-card">
            <div class="logo-preview-box">
              <img *ngIf="p.logoUrl" [src]="p.logoUrl" alt="Restaurant logo" class="logo-preview-img" />
              <div *ngIf="!p.logoUrl" class="logo-empty-box">
                <svg aria-hidden="true" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                  <circle cx="8.5" cy="8.5" r="1.5"/>
                  <polyline points="21 15 16 10 5 21"/>
                </svg>
                <span>No logo</span>
              </div>
            </div>
            <div class="logo-meta">
              <h4>Logo</h4>
              <p class="muted">PNG, JPG, WebP or SVG, up to 2MB. Used on bills and digital menus.</p>
              <div class="logo-actions">
                <label class="ghost-btn file-picker-label" [class.is-disabled]="logoBusy()">
                  <input type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" (change)="onLogoFileSelected($event)" class="visually-hidden" [disabled]="logoBusy()" aria-label="Choose restaurant logo file" />
                  <svg aria-hidden="true" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                    <polyline points="17 8 12 3 7 8"/>
                    <line x1="12" y1="3" x2="12" y2="15"/>
                  </svg>
                  {{ logoBusy() ? 'Uploading...' : (p.logoUrl ? 'Change logo' : 'Upload logo') }}
                </label>
                <button *ngIf="p.logoUrl" type="button" class="ghost-btn danger-btn" [disabled]="logoBusy()" (click)="removeLogo()">
                  Remove logo
                </button>
              </div>
            </div>
          </div>

          <div class="form-grid">
            <div class="field">
              <label for="shop-name">Shop name <span class="required" aria-hidden="true">*</span></label>
              <input id="shop-name" class="field-control" [(ngModel)]="p.shopName" name="shopName" placeholder="Restaurant name" required autocomplete="organization" />
            </div>
            <div class="field">
              <label for="whatsapp-number">WhatsApp contact</label>
              <input id="whatsapp-number" class="field-control tabular-num" type="tel" inputmode="numeric" [(ngModel)]="p.whatsappNumber" name="whatsappNumber" placeholder="10-digit mobile number" maxlength="10" autocomplete="tel" />
            </div>
            <div class="field field--wide">
              <label for="shop-address">Shop address</label>
              <input id="shop-address" class="field-control" [(ngModel)]="p.shopAddress" name="shopAddress" placeholder="Full address" autocomplete="street-address" />
            </div>
            <div class="field">
              <label for="official-email">Official email</label>
              <input id="official-email" class="field-control" type="email" [(ngModel)]="p.email" name="email" placeholder="name@restaurant.com" autocomplete="email" />
            </div>
            <div class="field">
              <label for="review-url">Google review link</label>
              <input id="review-url" class="field-control" type="url" [(ngModel)]="p.reviewUrl" name="reviewUrl" placeholder="https://g.page/r/your-restaurant/review" />
            </div>
            <div class="field field--wide">
              <label for="invoice-footer">Receipt footer note</label>
              <input id="invoice-footer" class="field-control" [(ngModel)]="p.invoiceFooter" name="invoiceFooter" placeholder="e.g. Thank you, visit again!" />
              <p class="field-hint">Printed at the bottom of every thermal receipt.</p>
            </div>
          </div>
        </section>

        <!-- Tax configuration -->
        <app-tax-settings-section
          [tax]="{
            gstEnabled: p.gstEnabled,
            gstin: p.gstin,
            gstPercentage: p.gstPercentage,
            customTaxName: p.customTaxName,
            customTaxPercentage: p.customTaxPercentage,
            fssaiNumber: p.fssaiNumber,
            fssaiExpiryDate: p.fssaiExpiryDate,
            gstExpiryDate: p.gstExpiryDate
          }"
          (taxChange)="applyTaxSettings($event)"
        />

        <!-- Payment methods -->
        <app-payment-methods-section
          [settings]="{ cashEnabled: p.cashEnabled, upiEnabled: p.upiEnabled, upiHandle: p.upiHandle, upiMobile: p.upiMobile, posEnabled: p.posEnabled, orderPaymentFlowMode: p.orderPaymentFlowMode, shopName: p.shopName }"
          (settingsChange)="applyPaymentSettings($event)"
        />

        <div class="sticky-save-dock" [class.sticky-save-dock--dirty]="isDirty()">
          <div class="save-dock-inner">
            <span class="dock-status" role="status" aria-live="polite">
              <span class="dock-dot" aria-hidden="true"></span>
              {{ isDirty() ? 'You have unsaved changes' : 'All changes saved' }}
            </span>
            <div class="dock-actions">
              <button type="button" class="ghost-btn" [disabled]="saving() || !isDirty()" (click)="discardChanges()">Discard</button>
              <button class="primary-btn" type="button" [disabled]="saving() || !isDirty()" (click)="saveProfile()" aria-label="Save all business settings">
                {{ saving() ? 'Saving...' : 'Save changes' }}
              </button>
            </div>
          </div>
        </div>

      </ng-container>

      <!-- Password is saved on its own, so it sits outside the save dock -->
      <app-password-change-section />
    </div>
  `,
  styles: [`
    .loading-panel { margin-bottom: var(--kb-space-4); }
    .skeleton-stack { display: flex; flex-direction: column; gap: var(--kb-space-3); }
    .skeleton { background: var(--kb-color-surface-2); border-radius: var(--kb-radius-md); animation: settings-pulse 1.5s ease-in-out infinite; }
    .skeleton-row { height: 48px; }
    @keyframes settings-pulse { 0%, 100% { opacity: 0.45; } 50% { opacity: 0.8; } }

    .settings-section { margin-bottom: var(--kb-space-4); }
    .section-head {
      display: flex;
      align-items: flex-start;
      gap: var(--kb-space-3);
      margin-bottom: var(--kb-space-4);
      padding-bottom: var(--kb-space-3);
      border-bottom: 1px solid var(--kb-color-border);
    }
    .section-head h3 { margin: 0; font-size: 1.1rem; font-weight: 600; letter-spacing: -0.02em; }
    .section-head p { margin: 2px 0 0; font-size: 0.82rem; }
    .section-icon {
      width: 34px;
      height: 34px;
      border-radius: var(--kb-radius-md);
      background: rgba(var(--kb-color-primary-rgb, 37, 99, 235), 0.08);
      color: var(--kb-color-primary);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .logo-card {
      display: flex;
      gap: var(--kb-space-4);
      align-items: center;
      padding: var(--kb-space-3);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
      margin-bottom: var(--kb-space-4);
      flex-wrap: wrap;
    }
    .logo-preview-box {
      width: 80px;
      height: 80px;
      border-radius: var(--kb-radius-md);
      overflow: hidden;
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .logo-preview-img { width: 100%; height: 100%; object-fit: contain; }
    .logo-empty-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: var(--kb-color-muted-foreground);
      font-size: 0.72rem;
      gap: 4px;
    }
    .logo-meta { flex: 1; min-width: 220px; }
    .logo-meta h4 { margin: 0 0 2px; font-size: 0.95rem; font-weight: 600; }
    .logo-meta p { margin: 0 0 var(--kb-space-2); font-size: 0.82rem; }
    .logo-actions { display: flex; gap: var(--kb-space-2); align-items: center; flex-wrap: wrap; }
    .file-picker-label { cursor: pointer; display: inline-flex; align-items: center; gap: 6px; }
    .file-picker-label.is-disabled { opacity: 0.6; cursor: not-allowed; }
    .danger-btn { color: var(--kb-color-error); border-color: rgba(192, 57, 43, 0.3); }
    .danger-btn:hover { background: rgba(192, 57, 43, 0.08); }

    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
      gap: var(--kb-space-4) var(--kb-space-3);
    }
    .field--wide { grid-column: 1 / -1; }
    .field label {
      display: block;
      font-size: 0.82rem;
      color: var(--kb-color-foreground);
      font-weight: 500;
      margin-bottom: var(--kb-space-1);
    }
    .required { color: var(--kb-color-error); }
    .field-hint { margin: var(--kb-space-1) 0 0; font-size: 0.75rem; color: var(--kb-color-muted-foreground); }
    .tabular-num { font-variant-numeric: tabular-nums; }

    .sticky-save-dock {
      position: sticky;
      bottom: var(--kb-space-4);
      z-index: var(--kb-z-topbar, 30);
      margin: var(--kb-space-4) 0;
      padding: var(--kb-space-3) var(--kb-space-4);
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
      animation: dock-enter var(--kb-duration-slow, 300ms) var(--kb-ease-standard, ease-out) both;
    }
    .settings-section { animation: section-enter var(--kb-duration-base, 200ms) var(--kb-ease-standard, ease-out) both; }
    @keyframes dock-enter { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: none; } }
    @keyframes section-enter { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
    @media (prefers-reduced-motion: reduce) {
      .sticky-save-dock, .settings-section, .skeleton { animation: none; }
    }
    .sticky-save-dock--dirty { border-color: rgba(217, 119, 6, 0.45); }
    .save-dock-inner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--kb-space-3);
      flex-wrap: wrap;
    }
    .dock-status {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font-size: 0.85rem;
      color: var(--kb-color-muted-foreground);
    }
    .dock-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--kb-color-success, #16a34a);
    }
    .sticky-save-dock--dirty .dock-dot { background: #d97706; }
    .sticky-save-dock--dirty .dock-status { color: var(--kb-color-foreground); font-weight: 500; }
    .dock-actions { display: flex; gap: var(--kb-space-2); }
    .primary-btn:disabled, .ghost-btn:disabled { opacity: 0.5; cursor: not-allowed; }

    @media (max-width: 640px) {
      .save-dock-inner { flex-direction: column; align-items: stretch; }
      .dock-actions { justify-content: flex-end; }
    }
  `]
})
export class BusinessSettingsPageComponent {
  private readonly toast = inject(ToastService);
  private readonly businessApi = inject(BusinessApiService);

  loading = signal(true);
  loadError = signal('');
  saving = signal(false);
  logoBusy = signal(false);
  profile = signal<RestaurantProfile | null>(null);

  /** Last values confirmed by the server (or loaded), used to detect unsaved edits. */
  private readonly savedSnapshot = signal('');

  /**
   * The settings form is withheld while a reload is in flight so the skeleton
   * above takes over: a manual refresh then visibly reloads the page instead of
   * leaving the old values on screen behind a spinning icon.
   */
  readonly viewProfile = computed(() => (this.loading() ? null : this.profile()));

  /** True when the form differs from the last saved values. The logo is excluded
   *  because logo upload and removal are persisted immediately on their own. */
  isDirty(): boolean {
    const p = this.profile();
    return !!p && this.comparable(p) !== this.savedSnapshot();
  }

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.businessApi.getProfile().subscribe({
      next: (p) => {
        const loaded: RestaurantProfile = p
          ? {
              shopName: p.shopName ?? '',
              shopAddress: p.shopAddress ?? '',
              whatsappNumber: p.whatsappNumber ?? '',
              email: p.email ?? '',
              logoUrl: p.logoUrl ?? '',
              gstEnabled: p.gstEnabled ?? false,
              gstin: p.gstin ?? '',
              gstPercentage: p.gstPercentage ?? 5,
              customTaxName: p.customTaxName ?? '',
              customTaxPercentage: p.customTaxPercentage ?? 0,
              fssaiNumber: p.fssaiNumber ?? '',
              fssaiExpiryDate: p.fssaiExpiryDate ?? '',
              gstExpiryDate: p.gstExpiryDate ?? '',
              upiEnabled: p.upiEnabled ?? false,
              upiHandle: p.upiHandle ?? '',
              upiMobile: p.upiMobile ?? '',
              cashEnabled: p.cashEnabled ?? true,
              posEnabled: p.posEnabled ?? false,
              orderPaymentFlowMode: p.orderPaymentFlowMode ?? 'pay_before_food',
              invoiceFooter: p.invoiceFooter ?? '',
              reviewUrl: p.reviewUrl ?? ''
            }
          : {
              shopName: '', shopAddress: '', whatsappNumber: '', email: '',
              logoUrl: '', gstEnabled: false, gstin: '', gstPercentage: 5, customTaxName: '',
              customTaxPercentage: 0, fssaiNumber: '', fssaiExpiryDate: '', gstExpiryDate: '',
              upiEnabled: false, upiHandle: '', upiMobile: '', cashEnabled: true,
              posEnabled: false, orderPaymentFlowMode: 'pay_before_food',
              invoiceFooter: '', reviewUrl: ''
            };
        this.profile.set(loaded);
        this.savedSnapshot.set(this.comparable(loaded));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set('Failed to load settings. Check your connection and try again.');
      }
    });
  }

  applyTaxSettings(patch: Partial<TaxSettings>): void {
    const p = this.profile();
    if (!p) return;
    this.profile.set({ ...p, ...patch });
  }

  applyPaymentSettings(patch: Partial<PaymentMethodsSettings>): void {
    const p = this.profile();
    if (!p) return;
    this.profile.set({ ...p, ...patch });
  }

  /** Reverts unsaved edits to the last saved values. */
  discardChanges(): void {
    const saved = this.savedSnapshot();
    if (!saved) return;
    // The logo is persisted on its own, so keep whatever is currently shown.
    const logoUrl = this.profile()?.logoUrl ?? '';
    this.profile.set({ ...(JSON.parse(saved) as RestaurantProfile), logoUrl });
    this.toast.show('Unsaved changes discarded.', 'info');
  }

  saveProfile(): void {
    const p = this.profile();
    if (!p || !p.shopName?.trim()) {
      this.toast.show('Shop name is required.', 'error');
      return;
    }
    this.saving.set(true);
    this.businessApi.updateProfile(p).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.toast.show('Restaurant settings saved.', 'success');
        const merged = saved ? { ...p, ...saved } : p;
        this.profile.set(merged);
        this.savedSnapshot.set(this.comparable(merged));
      },
      error: (err) => {
        this.saving.set(false);
        this.toast.show(err?.error?.message ?? 'Failed to save settings. Try again.', 'error');
      }
    });
  }

  onLogoFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      this.toast.show('Logo file must be smaller than 2MB.', 'error');
      input.value = '';
      return;
    }
    this.logoBusy.set(true);
    this.businessApi.uploadLogo(file).subscribe({
      next: (res) => {
        const logoUrl = res?.url || res?.logoUrl || '';
        const p = this.profile();
        if (p) {
          this.profile.set({ ...p, logoUrl });
        }
        this.logoBusy.set(false);
        this.toast.show('Logo uploaded.', 'success');
        input.value = '';
      },
      error: (err) => {
        this.logoBusy.set(false);
        this.toast.show(err?.error?.message ?? 'Failed to upload logo. Try again.', 'error');
        input.value = '';
      }
    });
  }

  removeLogo(): void {
    this.logoBusy.set(true);
    this.businessApi.deleteLogo().subscribe({
      next: () => {
        const p = this.profile();
        if (p) {
          this.profile.set({ ...p, logoUrl: '' });
        }
        this.logoBusy.set(false);
        this.toast.show('Logo removed.', 'success');
      },
      error: (err) => {
        this.logoBusy.set(false);
        this.toast.show(err?.error?.message ?? 'Failed to remove logo.', 'error');
      }
    });
  }

  private comparable(p: RestaurantProfile): string {
    return JSON.stringify({ ...p, logoUrl: '' });
  }
}
