import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { validateUpiHandle, UpiValidationResult } from '../../core/utils/upi-validator.util';
import { generateUpiQrDataUrl } from '../../core/utils/qr-code.util';

export interface PaymentMethodsSettings {
  cashEnabled: boolean;
  upiEnabled: boolean;
  upiHandle: string;
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
  `,
  styles: [`
    .loading { font-size: var(--kb-text-md); color: var(--kb-color-muted-foreground); padding: var(--kb-space-2) 0; }
    .config-error {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
      padding: var(--kb-space-3);
      border: 1px solid rgba(192, 57, 43, 0.3);
      background: rgba(192, 57, 43, 0.05);
      border-radius: var(--kb-radius-md);
      color: var(--kb-color-error);
      font-size: var(--kb-text-md);
    }
    .config-error > div { flex: 1; }
    .config-error button {
      background: none;
      border: 1px solid currentColor;
      color: inherit;
      border-radius: var(--kb-radius-md);
      padding: 6px var(--kb-space-3);
      font-size: var(--kb-text-sm);
      font-weight: 600;
      cursor: pointer;
      min-height: 36px;
    }
    .config-error button:hover { background: rgba(192, 57, 43, 0.08); }
    .toggle-label {
      display: flex; align-items: center; gap: var(--kb-space-2); cursor: pointer;
      font-size: var(--kb-text-md); color: var(--kb-color-foreground); font-weight: 500;
    }
    .toggle-label input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--kb-color-primary); }
    .upi-feedback { margin-top: var(--kb-space-1); font-size: var(--kb-text-sm); }
    .feedback-success { color: var(--kb-color-success); display: flex; align-items: center; gap: var(--kb-space-1); font-weight: 500; }
    .feedback-warning { color: var(--kb-color-warning); display: flex; align-items: center; gap: var(--kb-space-1); flex-wrap: wrap; }
    .feedback-error { color: var(--kb-color-error); font-weight: 500; }
    .icon { font-size: var(--kb-text-sm); line-height: 1; }
    .suggestion-btn {
      background: none; border: 1px solid var(--kb-color-primary);
      color: var(--kb-color-link); border-radius: var(--kb-radius-sm);
      padding: 2px 6px; font-size: var(--kb-text-xs); cursor: pointer; font-weight: 600;
    }
    .suggestion-btn:hover { background: rgba(79, 70, 229, 0.15); }
    .flow-mode-cards {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: var(--kb-space-3); margin-top: var(--kb-space-3);
    }
    .flow-mode-card {
      border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-lg);
      padding: 0.85rem; background: var(--kb-color-surface-2);
      cursor: pointer; transition: border-color 0.15s, background 0.15s;
    }
    .flow-mode-card:hover { border-color: var(--kb-color-primary); }
    .flow-mode-card.active {
      border-color: var(--kb-color-primary);
      background: rgba(79, 70, 229, 0.12);
    }
    .mode-header { display: flex; align-items: center; gap: var(--kb-space-2); margin-bottom: 4px; }
    .mode-radio {
      width: 14px; height: 14px; border-radius: 50%; border: 2px solid var(--kb-color-border);
      display: inline-block; box-sizing: border-box;
    }
    .flow-mode-card.active .mode-radio {
      border-color: var(--kb-color-primary);
      background: var(--kb-color-primary);
      box-shadow: inset 0 0 0 2px var(--kb-color-primary-foreground);
    }
    .flow-mode-card p { margin: 0; font-size: var(--kb-text-sm); line-height: 1.4; }
    .upi-test-panel { margin-top: var(--kb-space-3); }
    .test-qr-card {
      display: flex; gap: var(--kb-space-3); align-items: center;
      background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border);
      border-radius: 10px; padding: var(--kb-space-4); flex-wrap: wrap;
    }
    .test-qr-media {
      width: 140px; height: 140px; background: var(--kb-color-surface); border-radius: var(--kb-radius-lg);
      padding: 6px; box-shadow: 0 1px 4px rgba(0,0,0,0.3); display: flex;
      align-items: center; justify-content: center; flex-shrink: 0;
    }
    .test-qr-img { width: 100%; height: 100%; object-fit: contain; }
    .test-qr-loading { font-size: var(--kb-text-xs); color: var(--kb-color-muted); }
    .test-qr-content { flex: 1; min-width: 240px; }
    .test-qr-badge {
      display: inline-block; font-size: var(--kb-text-2xs); font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.5px; background: rgba(16, 185, 129, 0.15); color: var(--kb-color-success);
      padding: 2px var(--kb-space-2); border-radius: var(--kb-radius-sm); margin-bottom: 6px;
    }
    .test-qr-content h4 { margin: 0 0 var(--kb-space-1); font-size: var(--kb-text-lg); color: var(--kb-color-foreground); }
    .test-qr-content p { margin: 0 0 var(--kb-space-2); font-size: var(--kb-text-sm); }
    .test-qr-meta { display: flex; gap: var(--kb-space-2); flex-wrap: wrap; font-size: var(--kb-text-sm); }
    .meta-tag {
      background: var(--kb-color-surface-1); border: 1px solid var(--kb-color-border);
      padding: 3px var(--kb-space-2); border-radius: var(--kb-radius-sm);
    }
    .chip {
      display: inline-flex;
      align-items: center;
      padding: 3px 10px;
      border-radius: var(--kb-radius-full);
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      font-variant-numeric: tabular-nums;
    }
    .chip-green {
      background: rgba(16, 185, 129, 0.15);
      color: var(--kb-color-success);
      border: 1px solid rgba(16, 185, 129, 0.3);
    }
    .chip-amber {
      background: rgba(245, 158, 11, 0.15);
      color: var(--kb-color-warning);
      border: 1px solid rgba(245, 158, 11, 0.3);
    }

    /* ── Payment-methods carriers ──
       These declarations used to be inline style attributes. Values are
       unchanged; the carrier moved into the component so the stylesheet
       checks can see them. */
    .config-error-note { margin: 2px 0 0; }
    .submerchant-card {
      margin-top: var(--kb-space-4);
      padding: 0.85rem var(--kb-space-4);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: 10px;
      font-size: var(--kb-text-md);
    }
    .submerchant-row { margin-bottom: 0.4rem; }
    .submerchant-label { color: var(--kb-color-muted-foreground); font-weight: 500; }
    .submerchant-value { font-family: monospace; font-size: var(--kb-text-md); }
    .submerchant-onboarding {
      margin-top: var(--kb-space-4);
      padding: var(--kb-space-3);
      background: rgba(183,121,31,0.08);
      border: 1px solid var(--kb-color-warning);
      border-radius: var(--kb-radius-lg);
      font-size: var(--kb-text-md);
    }
  `]
})
export class PaymentMethodsSectionComponent {
  settings = input.required<PaymentMethodsSettings>();

  settingsChange = output<Partial<PaymentMethodsSettings>>();

  upiValidation: UpiValidationResult | null = null;
  testQrDataUrl: string | null = null;
  generatingQr = false;

  constructor() {
    effect(() => {
      const s = this.settings();
      if (s.upiEnabled && s.upiHandle) {
        this.onUpiHandleChange(s.upiHandle);
      }
    }, { allowSignalWrites: true });
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
}
