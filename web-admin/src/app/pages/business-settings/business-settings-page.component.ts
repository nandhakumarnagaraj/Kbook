import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessApiService, MerchantAgreementStatus } from '../../core/services/business-api.service';
import { TaxSettingsSectionComponent, TaxSettings } from './tax-settings-section.component';
import { PaymentMethodsSectionComponent, PaymentMethodsSettings } from './payment-methods-section.component';
import { PasswordChangeSectionComponent } from './password-change-section.component';
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
  imports: [CommonModule, FormsModule, TaxSettingsSectionComponent, PaymentMethodsSectionComponent, PasswordChangeSectionComponent],
  template: `
    <div class="page-shell">
      <!-- Operational Header (navbar.gallery standard) -->
      <header class="operational-settings-header">
        <div class="header-left">
          <div class="header-title-row">
            <h2>Business &amp; Restaurant Configuration</h2>
            <span class="sync-state-badge" *ngIf="!loading()">
              <span class="status-pulse-dot"></span>
              Live Sync Ready
            </span>
          </div>
          <p class="header-sub">Configure restaurant identity, automated GST compliance, payment settlement channels, and merchant agreements.</p>
        </div>
        <div class="header-right">
          <button type="button" class="primary-btn-tactile" [disabled]="saving() || loading()" (click)="saveProfile()">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
              <polyline points="17 21 17 13 7 13 7 21"/>
              <polyline points="7 3 7 8 15 8"/>
            </svg>
            {{ saving() ? 'Saving Changes...' : 'Save All Changes' }}
          </button>
          <button type="button" class="ghost-btn-tactile" (click)="load(); loadAgreement()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            Refresh
          </button>
        </div>
      </header>

      <div class="panel loading" *ngIf="loading()">Loading settings...</div>
      <div class="panel loading" *ngIf="!loading() && loadError() && !profile()">
        {{ loadError() }} <button class="ghost-btn-tactile" (click)="load()">Retry</button>
      </div>

      <ng-container *ngIf="profile() as p">

        <!-- Shop Profile (unsection.com tonal layout) -->
        <section class="panel settings-section">
          <div class="section-title-wrap">
            <div class="section-icon">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
            </div>
            <div>
              <h3>Restaurant Identity &amp; Invoicing</h3>
              <p class="muted">Information printed on thermal receipts, customer invoices, and online menus.</p>
            </div>
          </div>

          <div class="form-grid">
            <div class="field">
              <label>Shop Name *</label>
              <input class="field-control" [(ngModel)]="p.shopName" placeholder="Restaurant name" />
            </div>
            <div class="field">
              <label>Shop Address</label>
              <input class="field-control" [(ngModel)]="p.shopAddress" placeholder="Full address" />
            </div>
            <div class="field">
              <label>WhatsApp Contact Number</label>
              <input class="field-control tabular-num" [(ngModel)]="p.whatsappNumber" placeholder="10-digit mobile number" maxlength="10" />
            </div>
            <div class="field">
              <label>Official Email</label>
              <input class="field-control" type="email" [(ngModel)]="p.email" placeholder="Business email" />
            </div>
            <div class="field">
              <label>Thermal Receipt Footer Note</label>
              <input class="field-control" [(ngModel)]="p.invoiceFooter" placeholder="Thank you message on receipts" />
            </div>
            <div class="field">
              <label>Google Maps Review URL</label>
              <input class="field-control" [(ngModel)]="p.reviewUrl" placeholder="https://g.page/r/your-restaurant/review" />
            </div>
          </div>
        </section>

        <!-- Tax Configuration -->
        <app-tax-settings-section
          [tax]="{ gstEnabled: p.gstEnabled, gstin: p.gstin, gstPercentage: p.gstPercentage, customTaxName: p.customTaxName, customTaxPercentage: p.customTaxPercentage }"
          (taxChange)="applyTaxSettings($event)"
        />

        <!-- Payment Methods -->
        <app-payment-methods-section
          [settings]="{ cashEnabled: p.cashEnabled, upiEnabled: p.upiEnabled, upiHandle: p.upiHandle, upiMobile: p.upiMobile, posEnabled: p.posEnabled, orderPaymentFlowMode: p.orderPaymentFlowMode, shopName: p.shopName }"
          (settingsChange)="applyPaymentSettings($event)"
        />

        <div class="sticky-save-dock">
          <div class="save-dock-inner">
            <span class="dock-hint">Pending changes will sync automatically to all POS registers.</span>
            <button class="primary-btn-tactile" [disabled]="saving()" (click)="saveProfile()">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              {{ saving() ? 'Saving Changes...' : 'Save All Changes' }}
            </button>
          </div>
        </div>

      </ng-container>

      <!-- Change Password -->
      <app-password-change-section />

      <!-- Merchant Agreement -->
      <section class="panel settings-section">
        <div class="section-title-wrap">
          <div class="section-icon">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10 9 9 9 8 9"/>
            </svg>
          </div>
          <div>
            <h3>Merchant Service Agreement</h3>
            <p class="muted">Upload and review the signed KhanaBook service contract (PDF). Encrypted at rest; accessible only to shop owners.</p>
          </div>
        </div>

        <div class="panel loading" *ngIf="agreementLoading()">Loading agreement status...</div>

        <ng-container *ngIf="!agreementLoading()">
          <div class="agreement-status-card" *ngIf="agreement()?.hasAgreement">
            <div class="status-card-left">
              <span class="signed-badge">
                <span class="status-pulse-dot"></span>
                Signed Contract on File
              </span>
              <span class="muted tabular-num" *ngIf="agreement()?.signedAt as ts">
                Signed on {{ ts | date:'medium' }}
              </span>
            </div>
            <div class="agreement-actions">
              <button class="ghost-btn-tactile" [disabled]="agreementBusy()" (click)="downloadAgreement()">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="7 10 12 15 17 10"/>
                  <line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
                {{ agreementBusy() ? 'Downloading...' : 'Download PDF' }}
              </button>
            </div>
          </div>

          <p class="muted" *ngIf="!agreement()?.hasAgreement" style="margin: var(--kb-space-3) 0;">No active service agreement has been uploaded yet.</p>

          <div class="agreement-upload-box">
            <div class="field">
              <label>Signatory Full Name</label>
              <input class="field-control" [(ngModel)]="agreementSigner" placeholder="Name of the person who signed" />
            </div>
            <div class="field">
              <label>Signed Contract PDF *</label>
              <input class="field-control" type="file" accept="application/pdf" (change)="onAgreementFileSelected($event)" />
            </div>
            <div class="field-btn">
              <button class="primary-btn-tactile" [disabled]="!agreementFile || agreementBusy()" (click)="uploadAgreement()">
                {{ agreementBusy() ? 'Uploading...' : (agreement()?.hasAgreement ? 'Replace Agreement' : 'Upload Agreement') }}
              </button>
            </div>
          </div>
        </ng-container>
      </section>
    </div>
  `,
  styles: [`
    .operational-settings-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: var(--kb-space-3);
      margin-bottom: var(--kb-space-4);
      padding-bottom: var(--kb-space-3);
      border-bottom: 1px solid var(--kb-color-border);
      flex-wrap: wrap;
    }
    .header-title-row {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      flex-wrap: wrap;
    }
    .header-title-row h2 {
      margin: 0;
      font-size: 1.35rem;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    .sync-state-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 0.75rem;
      font-weight: 600;
      padding: 3px 10px;
      border-radius: var(--kb-radius-full);
      background: rgba(34, 197, 94, 0.08);
      border: 1px solid rgba(34, 197, 94, 0.25);
      color: #16a34a;
    }
    .status-pulse-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: #16a34a;
      box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.7);
      animation: pulse 2s infinite;
    }
    @keyframes pulse {
      0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.7); }
      70% { transform: scale(1); box-shadow: 0 0 0 5px rgba(34, 197, 94, 0); }
      100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(34, 197, 94, 0); }
    }
    .header-sub {
      margin: 4px 0 0 0;
      font-size: 0.85rem;
      color: var(--kb-color-muted-foreground);
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
      flex-wrap: wrap;
    }
    .primary-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--kb-color-primary);
      color: var(--kb-color-primary-foreground, #ffffff);
      border: none;
      border-radius: var(--kb-radius-md);
      font-size: 0.82rem;
      font-weight: 600;
      padding: 8px 16px;
      cursor: pointer;
      transition: transform 120ms ease, opacity 120ms ease;
    }
    .primary-btn-tactile:active { transform: scale(0.97); }
    .primary-btn-tactile:disabled { opacity: 0.5; cursor: not-allowed; }
    .ghost-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--kb-color-surface);
      color: var(--kb-color-foreground);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-md);
      font-size: 0.82rem;
      font-weight: 500;
      padding: 8px 14px;
      cursor: pointer;
      transition: transform 120ms ease, background-color 150ms ease;
    }
    .ghost-btn-tactile:active { transform: scale(0.97); }
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
      margin: var(--kb-space-4) 0;
      padding: var(--kb-space-3) var(--kb-space-4);
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
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
    .agreement-status-card {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: var(--kb-space-3) var(--kb-space-4);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-md);
      margin-bottom: var(--kb-space-4);
      flex-wrap: wrap;
      gap: var(--kb-space-3);
    }
    .status-card-left {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      flex-wrap: wrap;
    }
    .signed-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 3px 10px;
      border-radius: var(--kb-radius-full);
      font-size: 0.76rem;
      font-weight: 600;
      background: rgba(34, 197, 94, 0.1);
      color: #16a34a;
      border: 1px solid rgba(34, 197, 94, 0.3);
    }
    .agreement-upload-box {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 250px), 1fr));
      gap: var(--kb-space-3);
      align-items: flex-end;
      max-width: 700px;
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
  profile = signal<RestaurantProfile | null>(null);

  agreement = signal<MerchantAgreementStatus | null>(null);
  agreementLoading = signal(true);
  agreementBusy = signal(false);
  agreementSigner = '';
  agreementFile: File | null = null;

  constructor() { this.load(); this.loadAgreement(); }

  load(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.http.get<any[]>(`${API}/sync/restaurantprofile/pull?lastSyncTimestamp=0&deviceId=web-admin&ignoreDeviceId=true`).subscribe({
      next: (profiles) => {
        const p = profiles?.[0];
        if (p) {
          this.profile.set({
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
            upiEnabled: p.upiEnabled ?? false,
            upiHandle: p.upiHandle ?? '',
            upiMobile: p.upiMobile ?? '',
            cashEnabled: p.cashEnabled ?? true,
            posEnabled: p.posEnabled ?? false,
            orderPaymentFlowMode: p.orderPaymentFlowMode ?? 'pay_before_food',
            invoiceFooter: p.invoiceFooter ?? '',
            reviewUrl: p.reviewUrl ?? ''
          });
        } else {
          this.profile.set({
            shopName: '', shopAddress: '', whatsappNumber: '', email: '',
            logoUrl: '', gstEnabled: false, gstin: '', gstPercentage: 5, customTaxName: '',
            customTaxPercentage: 0, upiEnabled: false, upiHandle: '', upiMobile: '',
            cashEnabled: true, posEnabled: false, orderPaymentFlowMode: 'pay_before_food',
            invoiceFooter: '', reviewUrl: ''
          });
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
    const now = Date.now();
    const payload = [{
      ...p,
      localId: 1,
      deviceId: 'web-admin',
      restaurantId: 0,
      updatedAt: now,
      createdAt: now,
      isDeleted: false,
      serverUpdatedAt: 0
    }];
    this.http.post<any>(`${API}/sync/restaurantprofile/push`, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.show('Settings saved successfully.', 'success');
      },
      error: () => {
        this.saving.set(false);
        this.toast.show('Failed to save. Try again.', 'error');
      }
    });
  }

  loadAgreement(): void {
    this.agreementLoading.set(true);
    this.businessApi.getMerchantAgreementStatus().subscribe({
      next: (status) => { this.agreement.set(status); this.agreementLoading.set(false); },
      error: () => { this.agreement.set({ hasAgreement: false }); this.agreementLoading.set(false); }
    });
  }

  onAgreementFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (file && file.type !== 'application/pdf') {
      this.toast.show('Only PDF files are allowed for the agreement.', 'error');
      this.agreementFile = null;
      input.value = '';
      return;
    }
    this.agreementFile = file;
  }

  uploadAgreement(): void {
    if (!this.agreementFile) return;
    this.agreementBusy.set(true);
    this.businessApi.uploadMerchantAgreement(this.agreementFile, this.agreementSigner.trim() || undefined).subscribe({
      next: () => {
        this.toast.show('Agreement uploaded successfully.', 'success');
        this.agreementFile = null;
        this.agreementBusy.set(false);
        this.loadAgreement();
      },
      error: (err) => {
        this.toast.show(err?.error?.message ?? 'Failed to upload agreement. Try again.', 'error');
        this.agreementBusy.set(false);
      }
    });
  }

  downloadAgreement(): void {
    this.agreementBusy.set(true);
    this.businessApi.downloadMerchantAgreement().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = this.agreement()?.originalFilename ?? 'merchant-agreement.pdf';
        a.click();
        URL.revokeObjectURL(url);
        this.agreementBusy.set(false);
      },
      error: () => {
        this.toast.show('Failed to download agreement.', 'error');
        this.agreementBusy.set(false);
      }
    });
  }
}
