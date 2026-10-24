import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessApiService } from '../../core/services/business-api.service';
import { TaxSettingsSectionComponent, TaxSettings } from './tax-settings-section.component';
import { PaymentMethodsSectionComponent, PaymentMethodsSettings } from './payment-methods-section.component';
import { PasswordChangeSectionComponent } from './password-change-section.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';
import { environment } from '../../../environments/environment';

const API = environment.apiBaseUrl;

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
    RefreshButtonComponent,
    ConfirmDialogComponent
  ],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Business &amp; Restaurant Configuration"
        subtitle="Configure restaurant identity, automated GST compliance, and payment settlement channels."
      >
        <span kbHeaderBadge class="kpi-tag kpi-tag--green" *ngIf="!loading()">
          <span class="pulse-indicator" aria-hidden="true"></span>
          Live Sync Ready
        </span>

        <button type="button" class="header-action-btn header-action-btn--primary" [disabled]="saving() || loading()" (click)="saveProfile()" aria-label="Save All Changes">
            <svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
              <polyline points="17 21 17 13 7 13 7 21"/>
              <polyline points="7 3 7 8 15 8"/>
            </svg>
            {{ saving() ? 'Saving Changes...' : 'Save All Changes' }}
          </button>
        <app-refresh-button (refresh)="refreshSettings()" [loading]="loading()" ariaLabel="Reload settings from server" />
      </app-page-header>

      <!-- Stale refresh banner (prototype pattern) -->
      <div class="stale-banner" *ngIf="!loading() && loadError() && profile()" role="status" aria-live="polite">
        <span>Couldn't update settings. {{ loadError() }}</span>
        <button type="button" class="btn-retry" (click)="refreshSettings()">Retry</button>
      </div>

      <div class="panel loading" *ngIf="loading()" role="status" aria-busy="true">
        <span class="visually-hidden">Loading settings...</span>
        <div class="skeleton-stack">
          <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4]"></div>
        </div>
      </div>

      <app-api-state
        *ngIf="!loading() && loadError() && !profile()"
        [message]="loadError()"
        (retry)="refreshSettings()"
      ></app-api-state>

      <ng-container *ngIf="viewProfile() as p">

        <!-- Shop Profile (unsection.com tonal layout) -->
        <section class="panel settings-section">
          <div class="section-title-wrap">
            <div class="section-icon">
              <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
            </div>
            <div>
              <h3>Restaurant Identity &amp; Invoicing</h3>
              <p class="muted">Information printed on thermal receipts, customer invoices, and online menus.</p>
            </div>
          </div>

          <!-- Logo Management -->
          <div class="logo-uploader-card">
            <div class="logo-preview-box">
              <img *ngIf="p.logoUrl" [src]="p.logoUrl" alt="Restaurant logo" class="logo-preview-img" />
              <div *ngIf="!p.logoUrl" class="logo-empty-box">
                <svg aria-hidden="true" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                  <circle cx="8.5" cy="8.5" r="1.5"/>
                  <polyline points="21 15 16 10 5 21"/>
                </svg>
                <span>No Logo</span>
              </div>
            </div>
            <div class="logo-meta">
              <h4>Storefront &amp; Thermal Receipt Logo</h4>
              <p class="muted">Upload your brand mark. Displayed on bills, printed thermal receipts, and digital menus (PNG, JPG, max 2MB).</p>
              <div class="logo-actions">
                <label class="ghost-btn file-picker-label">
                  <input type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" (change)="onLogoFileSelected($event)" style="display:none;" aria-label="Choose restaurant logo file" />
                  <svg aria-hidden="true" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                    <polyline points="17 8 12 3 7 8"/>
                    <line x1="12" y1="3" x2="12" y2="15"/>
                  </svg>
                  {{ logoBusy() ? 'Uploading...' : (p.logoUrl ? 'Change Logo' : 'Upload Logo') }}
                </label>
                <button *ngIf="p.logoUrl" type="button" class="ghost-btn danger-btn" [disabled]="logoBusy()" (click)="removeLogo()">
                  <svg aria-hidden="true" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                  Remove Logo
                </button>
              </div>
            </div>
          </div>

          <div class="form-grid">
            <div class="field">
              <label>Shop Name *</label>
              <input class="field-control" [(ngModel)]="p.shopName" placeholder="Restaurant name" aria-label="Shop name (required)" />
            </div>
            <div class="field">
              <label>Shop Address</label>
              <textarea class="field-control" [(ngModel)]="p.shopAddress" placeholder="Full address" aria-label="Shop address" rows="3" style="resize: vertical; min-height: 72px; font-family: inherit;"></textarea>
            </div>
            <div class="field">
              <label>WhatsApp Contact Number</label>
              <input class="field-control tabular-num" [(ngModel)]="p.whatsappNumber" placeholder="10-digit mobile number" maxlength="10" aria-label="WhatsApp contact number" />
            </div>
            <div class="field">
              <label>Official Email</label>
              <input class="field-control" type="email" [(ngModel)]="p.email" placeholder="Business email" aria-label="Official business email" />
            </div>
            <div class="field">
              <label>Thermal Receipt Footer Note</label>
              <input class="field-control" [(ngModel)]="p.invoiceFooter" placeholder="Thank you message on receipts" aria-label="Thermal receipt footer note" />
            </div>
            <div class="field">
              <label>Google Maps Review URL</label>
              <input class="field-control" [(ngModel)]="p.reviewUrl" placeholder="https://g.page/r/your-restaurant/review" aria-label="Google Maps review URL" />
            </div>
          </div>
        </section>

        <!-- Tax Configuration -->
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

        <!-- Payment Methods -->
        <app-payment-methods-section
          [settings]="{ cashEnabled: p.cashEnabled, upiEnabled: p.upiEnabled, upiHandle: p.upiHandle, upiMobile: p.upiMobile, posEnabled: p.posEnabled, orderPaymentFlowMode: p.orderPaymentFlowMode, shopName: p.shopName }"
          (settingsChange)="applyPaymentSettings($event)"
        />

        <div class="sticky-save-dock">
          <div class="save-dock-inner">
            <span class="dock-hint">Changes sync to POS registers after you save.</span>
            <button class="primary-btn" type="button" [disabled]="saving()" (click)="saveProfile()" aria-label="Save All Changes">
              <svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              {{ saving() ? 'Saving Changes...' : 'Save All Changes' }}
            </button>
          </div>
        </div>

      </ng-container>

      <!-- Change Password -->
      <app-password-change-section />

      <!-- Remove Logo Confirmation -->
      <app-confirm-dialog
        *ngIf="confirmRemoveLogo()"
        title="Remove Logo"
        message="Are you sure you want to remove the business logo? This action cannot be undone."
        confirmLabel="Remove"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="executeRemoveLogo()"
        (cancelled)="cancelRemoveLogo()"
      ></app-confirm-dialog>

    </div>
  `,
  styles: [`
    .section-title-wrap {
      display: flex;
      align-items: flex-start;
      gap: var(--kb-space-3);
      margin-bottom: var(--kb-space-4);
      padding-bottom: var(--kb-space-3);
      border-bottom: 1px solid var(--kb-color-border);
    }
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
    .section-title-wrap h3 {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 600;
    }
    .section-title-wrap p {
      margin: 2px 0 0 0;
      font-size: 0.82rem;
    }
    .settings-section { margin-bottom: var(--kb-space-4); }
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
      gap: var(--kb-space-3);
    }
    .field label { display: block; font-size: 0.82rem; color: var(--kb-color-foreground); font-weight: 500; margin-bottom: var(--kb-space-1); }
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
      box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.05), 0 4px 12px rgba(0, 0, 0, 0.06);
    }
    .save-dock-inner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--kb-space-3);
      flex-wrap: wrap;
    }
    .dock-hint {
      font-size: 0.82rem;
      color: var(--kb-color-muted-foreground);
    }
    .logo-uploader-card {
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
    .logo-preview-img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
    .logo-empty-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: var(--kb-color-muted-foreground);
      font-size: 0.72rem;
      gap: 4px;
    }
    .logo-meta {
      flex: 1;
      min-width: 220px;
    }
    .logo-meta h4 {
      margin: 0 0 2px;
      font-size: 0.95rem;
      font-weight: 600;
    }
    .logo-meta p {
      margin: 0 0 var(--kb-space-2);
      font-size: 0.82rem;
    }
    .logo-actions {
      display: flex;
      gap: var(--kb-space-2);
      align-items: center;
      flex-wrap: wrap;
    }
    .file-picker-label {
      cursor: pointer;
    }
    .danger-btn {
      color: var(--kb-color-error);
      border-color: rgba(192, 57, 43, 0.3);
    }
    .danger-btn:hover {
      background: rgba(192, 57, 43, 0.08);
    }
    .field-btn {
      display: flex;
      align-items: flex-end;
      padding-bottom: 1px;
    }
  `]
})
export class BusinessSettingsPageComponent {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly businessApi = inject(BusinessApiService);

  loading = signal(true);
  loadError = signal('');
  saving = signal(false);
  logoBusy = signal(false);
  confirmRemoveLogo = signal(false);
  profile = signal<RestaurantProfile | null>(null);
  private readonly originalProfile = signal<RestaurantProfile | null>(null);

  readonly dirty = computed(() => {
    const current = this.profile();
    const original = this.originalProfile();
    if (!current || !original) return false;
    return JSON.stringify(current) !== JSON.stringify(original);
  });

  canDeactivate(): boolean {
    return !this.dirty();
  }

  /**
   * The settings form is withheld while a reload is in flight so the skeleton
   * above takes over: a manual refresh then visibly reloads the page instead of
   * leaving the old values on screen behind a spinning icon.
   */
  readonly viewProfile = computed(() => (this.loading() ? null : this.profile()));

  constructor() { this.load(); }

  refreshSettings(): void {
    if (this.loading()) return;
    if (this.dirty() && !window.confirm('Reload settings from the server? Unsaved changes will be lost.')) return;
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.businessApi.getProfile().subscribe({
      next: (p) => {
        if (p) {
          const loaded: RestaurantProfile = {
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
          };
          this.profile.set(loaded);
          this.originalProfile.set({ ...loaded });
        } else {
          const empty: RestaurantProfile = {
            shopName: '', shopAddress: '', whatsappNumber: '', email: '',
            logoUrl: '', gstEnabled: false, gstin: '', gstPercentage: 5, customTaxName: '',
            customTaxPercentage: 0, fssaiNumber: '', fssaiExpiryDate: '', gstExpiryDate: '',
            upiEnabled: false, upiHandle: '', upiMobile: '', cashEnabled: true,
            posEnabled: false, orderPaymentFlowMode: 'pay_before_food',
            invoiceFooter: '', reviewUrl: ''
          };
          this.profile.set(empty);
          this.originalProfile.set({ ...empty });
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set('Failed to load settings. Check connection and try again.');
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
        this.toast.show('Restaurant settings saved successfully.', 'success');
        if (saved) {
          const updated = { ...p, ...saved };
          this.profile.set(updated);
          this.originalProfile.set({ ...updated });
        }
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
      this.toast.show('Logo file size must be less than 2MB.', 'error');
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
        this.toast.show('Logo uploaded successfully.', 'success');
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
    this.confirmRemoveLogo.set(true);
  }

  executeRemoveLogo(): void {
    this.confirmRemoveLogo.set(false);
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

  cancelRemoveLogo(): void {
    this.confirmRemoveLogo.set(false);
  }
}


