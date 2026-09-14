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
      <section class="panel page-hero">
        <h2>Business Settings</h2>
        <p class="muted">Manage your restaurant profile, tax configuration, payment methods, and account security.</p>
      </section>

      <div class="panel loading" *ngIf="loading()">Loading settings...</div>
      <div class="panel loading" *ngIf="!loading() && loadError() && !profile()">{{ loadError() }} <button class="ghost-btn" (click)="load()">Retry</button></div>

      <ng-container *ngIf="profile() as p">

        <!-- Shop Profile -->
        <section class="panel settings-section">
          <h3>Shop Profile</h3>
          <div class="form-grid">
            <div class="field">
              <label>Shop Name *</label>
              <input class="field-control" [(ngModel)]="p.shopName" placeholder="Restaurant name" />
            </div>
            <div class="field">
              <label>Address</label>
              <input class="field-control" [(ngModel)]="p.shopAddress" placeholder="Full address" />
            </div>
            <div class="field">
              <label>WhatsApp Number</label>
              <input class="field-control" [(ngModel)]="p.whatsappNumber" placeholder="10-digit number" maxlength="10" />
            </div>
            <div class="field">
              <label>Email</label>
              <input class="field-control" type="email" [(ngModel)]="p.email" placeholder="Business email" />
            </div>
            <div class="field">
              <label>Invoice Footer</label>
              <input class="field-control" [(ngModel)]="p.invoiceFooter" placeholder="Thank you message on invoices" />
            </div>
            <div class="field">
              <label>Review URL</label>
              <input class="field-control" [(ngModel)]="p.reviewUrl" placeholder="Google Maps review link" />
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

        <div class="save-bar">
          <button class="primary-btn" [disabled]="saving()" (click)="saveProfile()">
            {{ saving() ? 'Saving...' : 'Save All Changes' }}
          </button>
        </div>

      </ng-container>

      <!-- Change Password -->
      <app-password-change-section />

      <!-- Merchant Agreement -->
      <section class="panel settings-section">
        <h3>Merchant Agreement</h3>
        <p class="muted">Upload the signed KhanaBook service agreement (PDF). Stored securely; only you and KhanaBook admins can access it.</p>

        <div class="panel loading" *ngIf="agreementLoading()">Loading agreement status...</div>

        <ng-container *ngIf="!agreementLoading()">
          <div class="agreement-status" *ngIf="agreement()?.hasAgreement">
            <span class="chip success">Signed agreement on file</span>
            <span class="muted" *ngIf="agreement()?.signedAt as ts"> · {{ ts | date:'medium' }}</span>
            <div class="agreement-actions">
              <button class="ghost-btn" [disabled]="agreementBusy()" (click)="downloadAgreement()">
                {{ agreementBusy() ? 'Working...' : 'Download' }}
              </button>
            </div>
          </div>
          <p class="muted" *ngIf="!agreement()?.hasAgreement">No agreement uploaded yet.</p>

          <div class="form-grid" style="max-width:500px; margin-top:1rem;">
            <div class="field">
              <label>Signer Name</label>
              <input class="field-control" [(ngModel)]="agreementSigner" placeholder="Name of the person who signed" />
            </div>
            <div class="field">
              <label>Agreement PDF *</label>
              <input class="field-control" type="file" accept="application/pdf" (change)="onAgreementFileSelected($event)" />
            </div>
            <div class="field">
              <button class="primary-btn" [disabled]="!agreementFile || agreementBusy()" (click)="uploadAgreement()">
                {{ agreementBusy() ? 'Uploading...' : (agreement()?.hasAgreement ? 'Replace Agreement' : 'Upload Agreement') }}
              </button>
            </div>
          </div>
        </ng-container>
      </section>
    </div>
  `,
  styles: [`
    .settings-section { margin-bottom: var(--kb-space-3); }
    .settings-section h3 { margin: 0 0 var(--kb-space-2); color: var(--kb-color-foreground); }
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
      gap: var(--kb-space-3);
    }
    .field label { display: block; font-size: 0.85rem; color: var(--kb-color-foreground); font-weight: 500; margin-bottom: var(--kb-space-1); }
    .toggle-label {
      display: flex; align-items: center; gap: var(--kb-space-2); cursor: pointer;
      font-size: 0.88rem; color: var(--kb-color-foreground); font-weight: 500;
    }
    .toggle-label input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--kb-color-primary); }
    .save-bar { padding: var(--kb-space-3) 0; display: flex; justify-content: flex-end; }
    .success-text { color: var(--kb-color-success); font-size: 0.85rem; }
    .error-text { color: var(--kb-color-error); font-size: 0.85rem; }
    .agreement-actions { margin-top: var(--kb-space-2); }
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
