import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessApiService, MerchantAgreementStatus } from '../../core/services/business-api.service';
import { validateUpiHandle, UpiValidationResult } from '../../core/utils/upi-validator.util';
import { generateUpiQrDataUrl } from '../../core/utils/qr-code.util';
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
  imports: [CommonModule, FormsModule],
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
        <section class="panel settings-section">
          <h3>Tax Configuration</h3>
          <div class="form-grid">
            <div class="field">
              <label class="toggle-label">
                <input type="checkbox" [(ngModel)]="p.gstEnabled" />
                <span>Enable GST</span>
              </label>
            </div>
            <div class="field" *ngIf="p.gstEnabled">
              <label>GSTIN</label>
              <input class="field-control" [(ngModel)]="p.gstin" placeholder="GST Number" />
            </div>
            <div class="field" *ngIf="p.gstEnabled">
              <label>GST Percentage</label>
              <input class="field-control" type="number" [(ngModel)]="p.gstPercentage" min="0" max="28" step="0.5" />
            </div>
            <div class="field" *ngIf="!p.gstEnabled">
              <label>Custom Tax Name</label>
              <input class="field-control" [(ngModel)]="p.customTaxName" placeholder="e.g. Service Tax" />
            </div>
            <div class="field" *ngIf="!p.gstEnabled">
              <label>Custom Tax %</label>
              <input class="field-control" type="number" [(ngModel)]="p.customTaxPercentage" min="0" max="50" step="0.5" />
            </div>
          </div>
        </section>

        <!-- Payment Configuration -->
        <section class="panel settings-section">
          <h3>Payment Methods</h3>
          <div class="form-grid">
            <div class="field">
              <label class="toggle-label">
                <input type="checkbox" [(ngModel)]="p.cashEnabled" />
                <span>Accept Cash</span>
              </label>
            </div>
            <div class="field">
              <label class="toggle-label">
                <input type="checkbox" [(ngModel)]="p.upiEnabled" />
                <span>Accept UPI</span>
              </label>
            </div>
            <div class="field">
              <label class="toggle-label">
                <input type="checkbox" [(ngModel)]="p.posEnabled" />
                <span>Accept POS/Card</span>
              </label>
            </div>
            <div class="field" *ngIf="p.upiEnabled">
              <label>UPI Handle</label>
              <input class="field-control" [(ngModel)]="p.upiHandle" (ngModelChange)="onUpiHandleChange()" placeholder="yourname@upi" />
              
              <!-- Real-time UPI verification feedback -->
              <div *ngIf="upiValidation" class="upi-feedback">
                <div *ngIf="upiValidation.isValid && upiValidation.isKnownBank" class="feedback-success">
                  <span class="icon">&#10003;</span> Verified Bank Handle (&#64;{{ upiValidation.suffix }})
                </div>
                <div *ngIf="upiValidation.suggestion" class="feedback-warning">
                  <span>&#9888; Unrecognized handle &#64;{{ upiValidation.suffix }}. Did you mean:</span>
                  <button type="button" class="suggestion-btn" (click)="applyUpiSuggestion(upiValidation.suggestion)">
                    {{ upiValidation.suggestion }}
                  </button>
                </div>
                <div *ngIf="!upiValidation.isValid && p.upiHandle" class="feedback-error">
                  <span>&#10007;</span> {{ upiValidation.message }}
                </div>
              </div>
            </div>
            <div class="field" *ngIf="p.upiEnabled">
              <label>UPI Mobile</label>
              <input class="field-control" [(ngModel)]="p.upiMobile" placeholder="Linked mobile number" maxlength="10" />
            </div>
            <div class="field">
              <label>Order Payment Flow</label>
              <select class="field-select" [(ngModel)]="p.orderPaymentFlowMode">
                <option value="pay_before_food">Pay before food (Fast Counter / QSR)</option>
                <option value="pay_after_food">Pay after food (Dine-in Table Service)</option>
              </select>
            </div>
          </div>

          <!-- Flow Mode Guide Cards -->
          <div class="flow-mode-cards">
            <div class="flow-mode-card" [class.active]="p.orderPaymentFlowMode === 'pay_before_food'" (click)="p.orderPaymentFlowMode = 'pay_before_food'">
              <div class="mode-header">
                <span class="mode-radio"></span>
                <strong>Pay Before Food (Fast Counter / QSR)</strong>
              </div>
              <p class="muted">Customers settle payment at billing counter. KOTs and receipts print immediately upon payment success.</p>
            </div>
            <div class="flow-mode-card" [class.active]="p.orderPaymentFlowMode === 'pay_after_food'" (click)="p.orderPaymentFlowMode = 'pay_after_food'">
              <div class="mode-header">
                <span class="mode-radio"></span>
                <strong>Pay After Food (Dine-In Table Service)</strong>
              </div>
              <p class="muted">Orders fire straight to kitchen KDS. Tables remain open in Active Orders and are settled at checkout.</p>
            </div>
          </div>

          <!-- Interactive Live Test QR Code Preview -->
          <div *ngIf="p.upiEnabled && upiValidation?.isValid" class="upi-test-panel">
            <div class="test-qr-card">
              <div class="test-qr-media">
                <img *ngIf="testQrDataUrl" [src]="testQrDataUrl" alt="Test UPI QR" class="test-qr-img" />
                <div *ngIf="generatingQr" class="test-qr-loading">Generating QR...</div>
              </div>
              <div class="test-qr-content">
                <div class="test-qr-badge">&#10003; Ready to Scan &amp; Verify</div>
                <h4>Interactive UPI Verification Test</h4>
                <p class="muted">
                  Point your camera with <strong>Google Pay</strong>, <strong>PhonePe</strong>, or <strong>Paytm</strong> at this test QR.
                  Your UPI app will immediately resolve NPCI records and display your <strong>registered legal bank account name</strong> on your phone.
                </p>
                <div class="test-qr-meta">
                  <span class="meta-tag">VPA: <strong>{{ p.upiHandle }}</strong></span>
                  <span class="meta-tag">Payee: <strong>{{ p.shopName || 'Merchant' }}</strong></span>
                  <span class="meta-tag">Test Amount: <strong>&#8377;1.00</strong></span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- Easebuzz Online Payments -->
        <section class="panel settings-section">
          <h3>Online Payments (Easebuzz)</h3>
          <p class="muted" style="margin:0 0 1rem;font-size:0.85rem">
            Enable online payment collection via UPI, cards, and net banking through Easebuzz gateway.
          </p>
          <div *ngIf="easebuzzLoading" class="loading">Loading payment config...</div>
          <div *ngIf="!easebuzzLoading && easebuzzConfig">
            <div class="form-grid">
              <div class="field">
                <label class="toggle-label">
                  <input type="checkbox" [(ngModel)]="easebuzzEnabled" (change)="toggleEasebuzz()">
                  <span>Enable Easebuzz Payments</span>
                </label>
              </div>
            </div>
            <div *ngIf="easebuzzConfig.subMerchantId" style="margin-top:1rem;padding:0.75rem;background:var(--kb-color-surface-2);border-radius:8px;font-size:0.85rem">
              <div style="display:flex;justify-content:space-between;margin-bottom:0.25rem">
                <span>Sub-Merchant ID</span>
                <strong>{{ easebuzzConfig.subMerchantId }}</strong>
              </div>
              <div style="display:flex;justify-content:space-between;margin-bottom:0.25rem">
                <span>Status</span>
                <span class="chip" [class.chip-green]="easebuzzConfig.subMerchantStatus === 'ACTIVE'" [class.chip-amber]="easebuzzConfig.subMerchantStatus === 'PENDING'">
                  {{ easebuzzConfig.subMerchantStatus }}
                </span>
              </div>
              <div style="display:flex;justify-content:space-between">
                <span>KYC Status</span>
                <span class="chip" [class.chip-green]="easebuzzConfig.kycStatus === 'verified'" [class.chip-amber]="easebuzzConfig.kycStatus === 'pending'">
                  {{ easebuzzConfig.kycStatus || 'N/A' }}
                </span>
              </div>
            </div>
            <div *ngIf="!easebuzzConfig.subMerchantId && easebuzzEnabled" style="margin-top:1rem;padding:0.75rem;background:rgba(245,158,11,0.08);border:1px solid var(--kb-color-warning,#f59e0b);border-radius:8px;font-size:0.85rem">
              Sub-merchant onboarding required. Use the Android app to complete Easebuzz onboarding.
            </div>
          </div>
        </section>

        <div class="save-bar">
          <button class="primary-btn" [disabled]="saving()" (click)="saveProfile()">
            {{ saving() ? 'Saving...' : 'Save All Changes' }}
          </button>
        </div>

      </ng-container>

      <!-- Change Password -->
      <section class="panel settings-section">
        <h3>Change Password</h3>
        <p class="muted">Update your account password using OTP verification.</p>
        <div class="form-grid" style="max-width:400px;">
          <div class="field">
            <label>Phone Number</label>
            <input class="field-control" [(ngModel)]="pwPhone" placeholder="Registered phone" maxlength="10" />
          </div>
          <div class="field" *ngIf="!pwOtpSent">
            <button class="primary-btn" [disabled]="!pwPhone || pwPhone.length !== 10 || pwLoading" (click)="requestPasswordOtp()">
              {{ pwLoading ? 'Sending...' : 'Send OTP' }}
            </button>
          </div>
          <ng-container *ngIf="pwOtpSent">
            <div class="field">
              <label>OTP</label>
              <input class="field-control" [(ngModel)]="pwOtp" placeholder="6-digit OTP" maxlength="6" />
            </div>
            <div class="field">
              <label>New Password</label>
              <input class="field-control" type="password" [(ngModel)]="pwNew" placeholder="Min 6 characters" />
            </div>
            <div class="field">
              <button class="primary-btn" [disabled]="!pwOtp || pwOtp.length !== 6 || !pwNew || pwNew.length < 6 || pwLoading" (click)="resetPassword()">
                {{ pwLoading ? 'Updating...' : 'Update Password' }}
              </button>
            </div>
          </ng-container>
          <p class="error-text" *ngIf="pwError">{{ pwError }}</p>
          <p class="success-text" *ngIf="pwSuccess">{{ pwSuccess }}</p>
        </div>
      </section>

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
    .upi-feedback { margin-top: 6px; font-size: 0.8rem; }
    .feedback-success { color: var(--kb-color-success, #10b981); display: flex; align-items: center; gap: 4px; font-weight: 500; }
    .feedback-warning { color: var(--kb-color-warning, #d97706); display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
    .feedback-error { color: var(--kb-color-error, #ef4444); font-weight: 500; }
    .suggestion-btn {
      background: none; border: 1px solid var(--kb-color-primary, #e59500);
      color: var(--kb-color-primary, #e59500); border-radius: 4px;
      padding: 2px 6px; font-size: 0.75rem; cursor: pointer; font-weight: 600;
    }
    .suggestion-btn:hover { background: rgba(229, 149, 0, 0.1); }
    .flow-mode-cards {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: var(--kb-space-3); margin-top: var(--kb-space-3);
    }
    .flow-mode-card {
      border: 1px solid var(--kb-color-border); border-radius: 8px;
      padding: 0.85rem; background: var(--kb-color-surface-2, #f9fafb);
      cursor: pointer; transition: border-color 0.15s, background 0.15s;
    }
    .flow-mode-card:hover { border-color: var(--kb-color-primary, #e59500); }
    .flow-mode-card.active {
      border-color: var(--kb-color-primary, #e59500);
      background: rgba(229, 149, 0, 0.05);
    }
    .mode-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
    .mode-radio {
      width: 14px; height: 14px; border-radius: 50%; border: 2px solid var(--kb-color-border);
      display: inline-block; box-sizing: border-box;
    }
    .flow-mode-card.active .mode-radio {
      border-color: var(--kb-color-primary, #e59500);
      background: var(--kb-color-primary, #e59500);
      box-shadow: inset 0 0 0 2px #fff;
    }
    .flow-mode-card p { margin: 0; font-size: 0.8rem; line-height: 1.4; }
    .upi-test-panel { margin-top: var(--kb-space-3); }
    .test-qr-card {
      display: flex; gap: var(--kb-space-3); align-items: center;
      background: var(--kb-color-surface-2, #f9fafb); border: 1px solid var(--kb-color-border);
      border-radius: 10px; padding: 1rem; flex-wrap: wrap;
    }
    .test-qr-media {
      width: 140px; height: 140px; background: #ffffff; border-radius: 8px;
      padding: 6px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); display: flex;
      align-items: center; justify-content: center; flex-shrink: 0;
    }
    .test-qr-img { width: 100%; height: 100%; object-fit: contain; }
    .test-qr-loading { font-size: 0.75rem; color: var(--kb-color-muted); }
    .test-qr-content { flex: 1; min-width: 240px; }
    .test-qr-badge {
      display: inline-block; font-size: 0.72rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.5px; background: rgba(16, 185, 129, 0.12); color: var(--kb-color-success, #10b981);
      padding: 2px 8px; border-radius: 4px; margin-bottom: 6px;
    }
    .test-qr-content h4 { margin: 0 0 4px; font-size: 1rem; color: var(--kb-color-foreground); }
    .test-qr-content p { margin: 0 0 8px; font-size: 0.82rem; }
    .test-qr-meta { display: flex; gap: 8px; flex-wrap: wrap; font-size: 0.78rem; }
    .meta-tag {
      background: var(--kb-color-surface-1, #ffffff); border: 1px solid var(--kb-color-border);
      padding: 3px 8px; border-radius: 4px;
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

  // Merchant agreement state
  agreement = signal<MerchantAgreementStatus | null>(null);
  agreementLoading = signal(true);
  agreementBusy = signal(false);
  agreementSigner = '';
  agreementFile: File | null = null;

  // Easebuzz payment config
  easebuzzConfig: any = null;
  easebuzzLoading = false;
  easebuzzEnabled = false;

  // Offline UPI Validation & Test QR State
  upiValidation: UpiValidationResult | null = null;
  testQrDataUrl: string | null = null;
  generatingQr = false;

  // Change password state
  pwPhone = '';
  pwOtp = '';
  pwNew = '';
  pwOtpSent = false;
  pwLoading = false;
  pwError = '';
  pwSuccess = '';

  constructor() { this.load(); this.loadAgreement(); this.loadEasebuzzConfig(); }

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
        if (this.profile()?.upiEnabled && this.profile()?.upiHandle) {
          this.onUpiHandleChange();
        }
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set('Failed to load settings. Check connection and try again.');
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

  loadEasebuzzConfig(): void {
    this.easebuzzLoading = true;
    this.businessApi.getPaymentConfig().subscribe({
      next: (config) => {
        this.easebuzzConfig = config;
        this.easebuzzEnabled = config.easebuzzEnabled || false;
        this.easebuzzLoading = false;
      },
      error: () => {
        this.easebuzzLoading = false;
      }
    });
  }

  toggleEasebuzz(): void {
    this.businessApi.updatePaymentConfig({ easebuzzEnabled: this.easebuzzEnabled }).subscribe({
      next: (config) => {
        this.easebuzzConfig = config;
        this.toast.show(
          this.easebuzzEnabled ? 'Easebuzz enabled' : 'Easebuzz disabled',
          'success'
        );
      },
      error: () => {
        this.easebuzzEnabled = !this.easebuzzEnabled;
        this.toast.show('Failed to update payment config', 'error');
      }
    });
  }

  onUpiHandleChange(): void {
    const handle = this.profile()?.upiHandle;
    if (!handle || !handle.trim()) {
      this.upiValidation = null;
      this.testQrDataUrl = null;
      return;
    }
    this.upiValidation = validateUpiHandle(handle);
    if (this.upiValidation.isValid) {
      this.generateTestQr();
    } else {
      this.testQrDataUrl = null;
    }
  }

  applyUpiSuggestion(suggestion: string): void {
    const p = this.profile();
    if (p) {
      p.upiHandle = suggestion;
      this.onUpiHandleChange();
    }
  }

  generateTestQr(): void {
    const p = this.profile();
    if (!p || !p.upiHandle) return;
    this.generatingQr = true;
    generateUpiQrDataUrl({
      vpa: p.upiHandle,
      payeeName: p.shopName || 'KhanaBook Merchant',
      amount: 1.00,
      note: 'KhanaBook Verification',
      size: 256
    }).then((dataUrl) => {
      this.testQrDataUrl = dataUrl;
      this.generatingQr = false;
    }).catch(() => {
      this.generatingQr = false;
    });
  }

  saveProfile(): void {
    const p = this.profile();
    if (!p || !p.shopName?.trim()) {
      this.toast.show('Shop name is required.', 'error');
      return;
    }
    if (p.upiEnabled) {
      const v = validateUpiHandle(p.upiHandle);
      if (!v.isValid) {
        this.toast.show(v.message || 'Please enter a valid UPI ID (e.g. yourname@okhdfcbank)', 'error');
        return;
      }
      p.upiHandle = v.normalizedHandle;
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

  requestPasswordOtp(): void {
    this.pwLoading = true;
    this.pwError = '';
    this.http.post(`${API}/auth/forgot-password/request-otp`, { phone: this.pwPhone }).subscribe({
      next: () => { this.pwLoading = false; this.pwOtpSent = true; },
      error: () => { this.pwLoading = false; this.pwOtpSent = true; } // always proceed (security)
    });
  }

  resetPassword(): void {
    this.pwLoading = true;
    this.pwError = '';
    this.pwSuccess = '';
    this.http.post<any>(`${API}/auth/forgot-password/verify-otp`, { phone: this.pwPhone, otp: this.pwOtp }).subscribe({
      next: (res) => {
        // Got temp token, now reset
        this.http.post(`${API}/auth/forgot-password/reset-password`, { tempToken: res.tempToken, newPassword: this.pwNew }).subscribe({
          next: () => {
            this.pwLoading = false;
            this.pwSuccess = 'Password updated successfully. Use the new password on your next login.';
            this.pwOtp = '';
            this.pwNew = '';
            this.pwOtpSent = false;
          },
          error: () => { this.pwLoading = false; this.pwError = 'Failed to update password. Try again.'; }
        });
      },
      error: () => { this.pwLoading = false; this.pwError = 'Invalid OTP. Please check and try again.'; }
    });
  }
}
