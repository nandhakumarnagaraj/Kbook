import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';
import { validateUpiHandle, UpiValidationResult } from '../../core/utils/upi-validator.util';
import { generateUpiQrDataUrl } from '../../core/utils/qr-code.util';

export interface PaymentMethodsSettings {
  cashEnabled: boolean;
  upiEnabled: boolean;
  upiHandle: string;
  upiMobile: string;
  posEnabled: boolean;
  orderPaymentFlowMode: string;
  shopName: string;
}

@Component({
  selector: 'app-payment-methods-section',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="panel settings-section">
      <div class="section-title-wrap">
        <div class="section-icon">
          <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="2" y="5" width="20" height="14" rx="2"/>
            <line x1="2" y1="10" x2="22" y2="10"/>
          </svg>
        </div>
        <div>
          <h3>Payment Channels &amp; Settlement Flow</h3>
          <p class="muted">Enable counter settlement methods, dynamic UPI QR validation, and order lifecycle behavior.</p>
        </div>
      </div>

      <div class="form-grid">
        <div class="field">
          <label class="toggle-label">
            <input type="checkbox" [ngModel]="settings().cashEnabled" (ngModelChange)="updateField('cashEnabled', $event)" />
            <span>Accept Cash</span>
          </label>
        </div>
        <div class="field">
          <label class="toggle-label">
            <input type="checkbox" [ngModel]="settings().upiEnabled" (ngModelChange)="updateField('upiEnabled', $event)" />
            <span>Accept UPI</span>
          </label>
        </div>
        <div class="field">
          <label class="toggle-label">
            <input type="checkbox" [ngModel]="settings().posEnabled" (ngModelChange)="updateField('posEnabled', $event)" />
            <span>Accept POS/Card</span>
          </label>
        </div>
        <div class="field" *ngIf="settings().upiEnabled">
          <label>UPI Handle</label>
          <input class="field-control" [ngModel]="settings().upiHandle" (ngModelChange)="onUpiHandleChange($event)" placeholder="yourname@upi" aria-label="UPI handle" aria-describedby="upi-feedback"
                 [attr.aria-invalid]="!!settings().upiHandle && upiValidation?.isValid === false" />
          <div *ngIf="upiValidation" class="upi-feedback" id="upi-feedback">
            <div *ngIf="upiValidation.isValid && upiValidation.isKnownBank" class="feedback-success" role="status">
              <span class="icon" aria-hidden="true">&#10003;</span> Verified Bank Handle (&#64;{{ upiValidation.suffix }})
            </div>
            <div *ngIf="upiValidation.suggestion" class="feedback-warning" role="status">
              <span>&#9888; Unrecognized handle &#64;{{ upiValidation.suffix }}. Did you mean:</span>
              <button type="button" class="suggestion-btn" (click)="applyUpiSuggestion(upiValidation.suggestion)" aria-label="Use this suggested UPI handle">
                {{ upiValidation.suggestion }}
              </button>
            </div>
            <div *ngIf="!upiValidation.isValid && settings().upiHandle" class="feedback-error" role="alert">
              <span aria-hidden="true">&#10007;</span> {{ upiValidation.message }}
            </div>
          </div>
        </div>
        <div class="field" *ngIf="settings().upiEnabled">
          <label>UPI Mobile</label>
          <input class="field-control" [ngModel]="settings().upiMobile" (ngModelChange)="updateField('upiMobile', $event)" placeholder="Linked mobile number" maxlength="10" aria-label="UPI linked mobile number" />
        </div>
      </div>

      <div class="flow-mode-cards" role="radiogroup" aria-label="Order payment flow mode">
        <div class="flow-mode-card card-lift" role="radio" tabindex="0" [attr.aria-checked]="settings().orderPaymentFlowMode === 'pay_before_food'" [class.active]="settings().orderPaymentFlowMode === 'pay_before_food'" (click)="updateField('orderPaymentFlowMode', 'pay_before_food')" (keydown.enter)="updateField('orderPaymentFlowMode', 'pay_before_food')">
          <div class="mode-header">
            <span class="mode-radio" aria-hidden="true"></span>
            <strong>Pay Before Food (Fast Counter / QSR)</strong>
          </div>
          <p class="muted">Customers settle payment at billing counter. Receipts print immediately upon payment success.</p>
        </div>
        <div class="flow-mode-card card-lift" role="radio" tabindex="0" [attr.aria-checked]="settings().orderPaymentFlowMode === 'pay_after_food'" [class.active]="settings().orderPaymentFlowMode === 'pay_after_food'" (click)="updateField('orderPaymentFlowMode', 'pay_after_food')" (keydown.enter)="updateField('orderPaymentFlowMode', 'pay_after_food')">
          <div class="mode-header">
            <span class="mode-radio" aria-hidden="true"></span>
            <strong>Pay After Food (Dine-In Table Service)</strong>
          </div>
          <p class="muted">Orders start as open tabs. Tables remain open in Active Orders and are settled at checkout.</p>
        </div>
      </div>

      <div *ngIf="settings().upiEnabled && upiValidation?.isValid" class="upi-test-panel">
        <div class="test-qr-card">
          <div class="test-qr-media">
            <img *ngIf="testQrDataUrl" [src]="testQrDataUrl" alt="Test UPI QR" class="test-qr-img" />
            <div *ngIf="generatingQr" class="test-qr-loading">Generating QR...</div>
          </div>
          <div class="test-qr-content">
            <div class="test-qr-badge">&#10003; ₹1 test QR</div>
            <h4>Check your UPI details</h4>
            <p class="muted">
              Scan this QR with <strong>Google Pay</strong>, <strong>PhonePe</strong>, or <strong>Paytm</strong> to check the payee shown in your UPI app. It contains a ₹1.00 payment request; approve it only if you intend to pay ₹1.00.
            </p>
            <div class="test-qr-meta">
              <span class="meta-tag">VPA: <strong>{{ settings().upiHandle }}</strong></span>
              <span class="meta-tag">Payee: <strong>{{ settings().shopName || 'Merchant' }}</strong></span>
              <span class="meta-tag">Test Amount: <strong>&#8377;1.00</strong></span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="panel settings-section">
      <div class="section-title-wrap">
        <div class="section-icon">
          <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="2" y="5" width="20" height="14" rx="2"/>
            <path d="M2 10h20"/>
            <circle cx="7" cy="15" r="1"/>
          </svg>
        </div>
        <div>
          <h3>Online Payments Gateway (Easebuzz)</h3>
          <p class="muted">Enable online payment collection via UPI, credit/debit cards, and net banking.</p>
        </div>
      </div>
      <div *ngIf="easebuzzLoading" class="loading" role="status">Loading payment config...</div>
      <div *ngIf="!easebuzzLoading && easebuzzError" class="config-error" role="alert">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        <div>
          <strong>Couldn't load payment config</strong>
          <p class="muted" style="margin:2px 0 0">You can still retry or configure this later from the Android app.</p>
        </div>
        <button type="button" class="ghost-btn" (click)="loadEasebuzzConfig()">Retry</button>
      </div>
      <div *ngIf="!easebuzzLoading && easebuzzConfig">
        <div class="form-grid">
          <div class="field">
            <label class="toggle-label">
              <input type="checkbox" [ngModel]="easebuzzEnabled" (change)="toggleEasebuzz()">
              <span>Enable Easebuzz Payments</span>
            </label>
          </div>
        </div>
        <div *ngIf="easebuzzConfig.subMerchantId" style="margin-top:1rem;padding:0.85rem 1rem;background:var(--kb-color-surface-2);border:1px solid var(--kb-color-border);border-radius:10px;font-size:0.85rem">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.4rem">
            <span style="color:var(--kb-color-muted-foreground);font-weight:500;">Sub-Merchant ID</span>
            <strong style="font-family:monospace;font-size:0.9rem;">{{ easebuzzConfig.subMerchantId }}</strong>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.4rem">
            <span style="color:var(--kb-color-muted-foreground);font-weight:500;">Status</span>
            <span class="chip" [class.chip-green]="easebuzzConfig.subMerchantStatus === 'ACTIVE'" [class.chip-amber]="easebuzzConfig.subMerchantStatus !== 'ACTIVE'">
              {{ subMerchantStatusLabel(easebuzzConfig.subMerchantStatus) }}
            </span>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span style="color:var(--kb-color-muted-foreground);font-weight:500;">KYC Status</span>
            <span class="chip" [class.chip-green]="kycStatusLabel(easebuzzConfig.kycStatus, easebuzzConfig.subMerchantStatus).tone === 'ok'" [class.chip-amber]="kycStatusLabel(easebuzzConfig.kycStatus, easebuzzConfig.subMerchantStatus).tone !== 'ok'">
              {{ kycStatusLabel(easebuzzConfig.kycStatus, easebuzzConfig.subMerchantStatus).label }}
            </span>
          </div>
        </div>
        <div *ngIf="!easebuzzConfig.subMerchantId && easebuzzEnabled" style="margin-top:1rem;padding:0.75rem;background:rgba(183,121,31,0.08);border:1px solid var(--kb-color-warning);border-radius:8px;font-size:0.85rem">
          Sub-merchant onboarding required. Use the Android app to complete Easebuzz onboarding.
        </div>
      </div>
    </section>
  `,
  styles: [`
    .loading { font-size: 0.85rem; color: var(--kb-color-muted-foreground); padding: var(--kb-space-2) 0; }
    .config-error {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
      padding: var(--kb-space-3);
      border: 1px solid rgba(192, 57, 43, 0.3);
      background: rgba(192, 57, 43, 0.05);
      border-radius: var(--kb-radius-md);
      color: var(--kb-color-error);
      font-size: 0.85rem;
    }
    .config-error > div { flex: 1; }
    .config-error button {
      background: none;
      border: 1px solid currentColor;
      color: inherit;
      border-radius: 6px;
      padding: 6px 12px;
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      min-height: 36px;
    }
    .config-error button:hover { background: rgba(192, 57, 43, 0.08); }
    .toggle-label {
      display: flex; align-items: center; gap: var(--kb-space-2); cursor: pointer;
      font-size: 0.88rem; color: var(--kb-color-foreground); font-weight: 500;
    }
    .toggle-label input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--kb-color-primary); }
    .upi-feedback { margin-top: 6px; font-size: 0.8rem; }
    .feedback-success { color: var(--kb-color-success); display: flex; align-items: center; gap: 4px; font-weight: 500; }
    .feedback-warning { color: var(--kb-color-warning); display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
    .feedback-error { color: var(--kb-color-error); font-weight: 500; }
    .suggestion-btn {
      background: none; border: 1px solid var(--kb-color-primary);
      color: var(--kb-color-primary); border-radius: 4px;
      padding: 2px 6px; font-size: 0.75rem; cursor: pointer; font-weight: 600;
    }
    .suggestion-btn:hover { background: rgba(37, 99, 235, 0.15); }
    .flow-mode-cards {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: var(--kb-space-3); margin-top: var(--kb-space-3);
    }
    .flow-mode-card {
      border: 1px solid var(--kb-color-border); border-radius: 8px;
      padding: 0.85rem; background: var(--kb-color-surface-2);
      cursor: pointer; transition: border-color 0.15s, background 0.15s;
    }
    .flow-mode-card:hover { border-color: var(--kb-color-primary); }
    .flow-mode-card.active {
      border-color: var(--kb-color-primary);
      background: rgba(37, 99, 235, 0.12);
    }
    .mode-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
    .mode-radio {
      width: 14px; height: 14px; border-radius: 50%; border: 2px solid var(--kb-color-border);
      display: inline-block; box-sizing: border-box;
    }
    .flow-mode-card.active .mode-radio {
      border-color: var(--kb-color-primary);
      background: var(--kb-color-primary);
      box-shadow: inset 0 0 0 2px var(--kb-color-primary-foreground);
    }
    .flow-mode-card p { margin: 0; font-size: 0.8rem; line-height: 1.4; }
    .upi-test-panel { margin-top: var(--kb-space-3); }
    .test-qr-card {
      display: flex; gap: var(--kb-space-3); align-items: center;
      background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border);
      border-radius: 10px; padding: 1rem; flex-wrap: wrap;
    }
    .test-qr-media {
      width: 140px; height: 140px; background: #FFFFFF; border-radius: 8px;
      padding: 6px; box-shadow: 0 1px 4px rgba(0,0,0,0.3); display: flex;
      align-items: center; justify-content: center; flex-shrink: 0;
    }
    .test-qr-img { width: 100%; height: 100%; object-fit: contain; }
    .test-qr-loading { font-size: 0.75rem; color: var(--kb-color-muted); }
    .test-qr-content { flex: 1; min-width: 240px; }
    .test-qr-badge {
      display: inline-block; font-size: 0.72rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.5px; background: rgba(16, 185, 129, 0.15); color: #34D399;
      padding: 2px 8px; border-radius: 4px; margin-bottom: 6px;
    }
    .test-qr-content h4 { margin: 0 0 4px; font-size: 1rem; color: var(--kb-color-foreground); }
    .test-qr-content p { margin: 0 0 8px; font-size: 0.82rem; }
    .test-qr-meta { display: flex; gap: 8px; flex-wrap: wrap; font-size: 0.78rem; }
    .meta-tag {
      background: var(--kb-color-surface-1); border: 1px solid var(--kb-color-border);
      padding: 3px 8px; border-radius: 4px;
    }
    .chip {
      display: inline-flex;
      align-items: center;
      padding: 3px 10px;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      font-variant-numeric: tabular-nums;
    }
    .chip-green {
      background: rgba(16, 185, 129, 0.15);
      color: #34D399;
      border: 1px solid rgba(16, 185, 129, 0.3);
    }
    .chip-amber {
      background: rgba(245, 158, 11, 0.15);
      color: #FBBF24;
      border: 1px solid rgba(245, 158, 11, 0.3);
    }
  `]
})
export class PaymentMethodsSectionComponent {
  private readonly toast = inject(ToastService);
  private readonly businessApi = inject(BusinessApiService);

  settings = input.required<PaymentMethodsSettings>();

  settingsChange = output<Partial<PaymentMethodsSettings>>();

  upiValidation: UpiValidationResult | null = null;
  testQrDataUrl: string | null = null;
  generatingQr = false;

  easebuzzConfig: any = null;
  easebuzzLoading = false;
  private readonly cdr = inject(ChangeDetectorRef);
  easebuzzError = '';
  easebuzzEnabled = false;

  constructor() {
    effect(() => {
      const s = this.settings();
      if (s.upiEnabled && s.upiHandle) {
        this.onUpiHandleChange(s.upiHandle);
      }
    }, { allowSignalWrites: true });

    this.loadEasebuzzConfig();
  }

  /**
   * Human label for the gateway's KYC status. The API returns storage codes
   * (PENDING_KYC, VERIFIED, …) and null when onboarding has not started —
   * none of which should be shown raw in an owner-facing screen. When the
   * dedicated KYC field is empty we fall back to the sub-merchant status,
   * which carries the same onboarding state.
   */
  kycStatusLabel(status: string | null | undefined, fallbackStatus?: string | null): { label: string; tone: 'ok' | 'pending' | 'none' } {
    const value = (status || fallbackStatus || '').toUpperCase();
    if (!value) return { label: 'Not started', tone: 'none' };
    if (value === 'VERIFIED' || value === 'APPROVED' || value === 'ACTIVE') {
      return { label: 'Verified', tone: 'ok' };
    }
    if (value.includes('PENDING') || value.includes('IN_PROGRESS') || value.includes('SUBMITTED')) {
      return { label: 'Pending KYC verification', tone: 'pending' };
    }
    if (value.includes('REJECT') || value.includes('FAILED')) {
      return { label: 'Verification failed', tone: 'pending' };
    }
    return { label: value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' '), tone: 'none' };
  }

  /** Human label for the sub-merchant onboarding status. */
  subMerchantStatusLabel(status: string | null | undefined): string {
    const value = (status || '').toUpperCase();
    if (!value) return 'Unknown';
    if (value === 'ACTIVE') return 'Active';
    if (value.includes('PENDING_KYC')) return 'Pending KYC';
    if (value === 'PENDING') return 'Pending';
    return value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ');
  }

  updateField<K extends keyof PaymentMethodsSettings>(field: K, value: PaymentMethodsSettings[K]): void {
    this.settingsChange.emit({ [field]: value } as Partial<PaymentMethodsSettings>);
  }

  onUpiHandleChange(handle: string): void {
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
    this.updateField('upiHandle', suggestion);
    this.onUpiHandleChange(suggestion);
  }

  generateTestQr(): void {
    const s = this.settings();
    if (!s.upiHandle) return;
    this.generatingQr = true;
    generateUpiQrDataUrl({
      vpa: s.upiHandle,
      payeeName: s.shopName || 'KhanaBook Merchant',
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

  loadEasebuzzConfig(): void {
    this.easebuzzLoading = true;
    this.easebuzzError = '';
    this.businessApi.getPaymentConfig().subscribe({
      next: (config) => {
        this.easebuzzConfig = config;
        this.easebuzzEnabled = config.easebuzzEnabled || false;
        this.easebuzzLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.easebuzzLoading = false;
        this.easebuzzError = 'Unable to reach the payments service.';
        this.cdr.markForCheck();
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
        this.cdr.markForCheck();
      },
      error: () => {
        this.easebuzzEnabled = !this.easebuzzEnabled;
        this.toast.show('Failed to update payment config', 'error');
        this.cdr.markForCheck();
      }
    });
  }
}
