import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';

export interface TaxSettings {
  gstEnabled: boolean;
  gstin: string;
  gstPercentage: number;
  customTaxName: string;
  customTaxPercentage: number;
  fssaiNumber?: string;
  fssaiExpiryDate?: string;
  gstExpiryDate?: string;
}

interface VerificationState {
  verifying: boolean;
  valid: boolean | null;
  businessName?: string;
  expiryDate?: string;
  message?: string;
}

@Component({
  selector: 'app-tax-settings-section',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="panel settings-section">
      <div class="section-title-wrap">
        <div class="section-icon">
          <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
          </svg>
        </div>
        <div>
          <h3>Tax &amp; Compliance Configuration</h3>
          <p class="muted">Configure GST taxation, verify GSTIN and FSSAI food safety licenses via official registries.</p>
        </div>
      </div>

      <div class="form-grid">
        <div class="field field-full">
          <label class="toggle-label">
            <input type="checkbox" [ngModel]="tax().gstEnabled" (ngModelChange)="updateField('gstEnabled', $event)" />
            <span class="toggle-text"><strong>Enable Goods &amp; Services Tax (GST)</strong></span>
          </label>
        </div>

        <ng-container *ngIf="tax().gstEnabled">
          <div class="field">
            <label for="gstin-input">GSTIN (15-character GST identification) *</label>
            <div class="input-with-action">
              <input
                id="gstin-input"
                aria-label="GSTIN"
                class="field-control tabular-num"
                #gstinRef="ngModel"
                [ngModel]="tax().gstin"
                (ngModelChange)="onGstinChange($event)"
                [attr.aria-invalid]="gstState().valid === false || (gstinRef.touched && !!tax().gstin && tax().gstin.length !== 15)"
                [attr.aria-describedby]="gstState().valid === false ? 'gstin-verify-error' : null"
                placeholder="e.g. 29AAAAA0000A1Z5"
                maxlength="15"
              />
              <button
                type="button"
                class="ghost-btn action-btn"
                [disabled]="gstState().verifying || !tax().gstin || tax().gstin.length !== 15"
                (click)="verifyGst()"
              >
                {{ gstState().verifying ? 'Verifying...' : 'Verify GSTIN' }}
              </button>
            </div>
            <!-- GST Verification Result -->
            <div *ngIf="gstState().valid === true || (gstState().valid === null && tax().gstExpiryDate && tax().gstin?.length === 15)" class="verify-badge verify-badge--success" role="status">
              <span class="badge-icon" aria-hidden="true">✓</span>
              <span><strong>Verified:</strong> {{ gstState().businessName || 'Active GSTIN' }}</span>
              <span class="badge-sub">(Valid to: {{ gstState().expiryDate || tax().gstExpiryDate }})</span>
            </div>
            <div id="gstin-verify-error" *ngIf="gstState().valid === false" class="verify-badge verify-badge--danger" role="alert">
              <span class="badge-icon" aria-hidden="true">✕</span>
              <span>{{ gstState().message || 'GSTIN verification failed.' }}</span>
            </div>
          </div>

          <div class="field">
            <label for="gst-percentage-input">GST Percentage (%) *</label>
            <input
              id="gst-percentage-input"
              aria-label="GST Percentage"
              class="field-control tabular-num"
              type="number"
              [ngModel]="tax().gstPercentage"
              (ngModelChange)="updateField('gstPercentage', $event)"
              min="0"
              max="28"
              step="0.5"
            />
            <span class="field-hint">Standard restaurant GST is 5% (composite/non-ITC).</span>
          </div>
        </ng-container>

        <ng-container *ngIf="!tax().gstEnabled">
          <div class="field">
            <label for="custom-tax-name-input">Custom Tax Name</label>
            <input
              id="custom-tax-name-input"
              aria-label="Custom Tax Name"
              class="field-control"
              [ngModel]="tax().customTaxName"
              (ngModelChange)="updateField('customTaxName', $event)"
              placeholder="e.g. Service Charge / Local VAT"
            />
          </div>
          <div class="field">
            <label for="custom-tax-percentage-input">Custom Tax Percentage (%)</label>
            <input
              id="custom-tax-percentage-input"
              aria-label="Custom Tax Percentage"
              class="field-control tabular-num"
              type="number"
              [ngModel]="tax().customTaxPercentage"
              (ngModelChange)="updateField('customTaxPercentage', $event)"
              min="0"
              max="50"
              step="0.5"
            />
          </div>
        </ng-container>

        <!-- FSSAI License Section -->
        <div class="field field-full">
          <label for="fssai-input">FSSAI Food Safety License Number (14 digits)</label>
          <div class="input-with-action">
            <input
              id="fssai-input"
              aria-label="FSSAI Food Safety License Number"
              class="field-control tabular-num"
              #fssaiRef="ngModel"
              [ngModel]="tax().fssaiNumber"
              (ngModelChange)="onFssaiChange($event)"
              [attr.aria-invalid]="fssaiState().valid === false || (fssaiRef.touched && !!tax().fssaiNumber && (tax().fssaiNumber?.length ?? 0) !== 14)"
              [attr.aria-describedby]="fssaiState().valid === false ? 'fssai-verify-error' : null"
              placeholder="e.g. 10020000000000"
              maxlength="14"
            />
            <button
              type="button"
              class="ghost-btn action-btn"
              [disabled]="fssaiState().verifying || !tax().fssaiNumber || tax().fssaiNumber?.length !== 14"
              (click)="verifyFssai()"
            >
              {{ fssaiState().verifying ? 'Verifying...' : 'Verify FSSAI' }}
            </button>
          </div>
          <!-- FSSAI Verification Result -->
          <div *ngIf="fssaiState().valid === true || (fssaiState().valid === null && tax().fssaiExpiryDate && tax().fssaiNumber?.length === 14)" class="verify-badge verify-badge--success" role="status">
            <span class="badge-icon" aria-hidden="true">✓</span>
            <span><strong>Verified:</strong> {{ fssaiState().businessName || 'Active FSSAI License' }}</span>
            <span class="badge-sub">(Expiry: {{ fssaiState().expiryDate || tax().fssaiExpiryDate }})</span>
          </div>
          <div id="fssai-verify-error" *ngIf="fssaiState().valid === false" class="verify-badge verify-badge--danger" role="alert">
            <span class="badge-icon" aria-hidden="true">✕</span>
            <span>{{ fssaiState().message || 'FSSAI verification failed.' }}</span>
          </div>
          <span class="field-hint">Mandatory for all food establishments under FSSAI regulations. Printed on receipts.</span>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .toggle-label {
      display: inline-flex;
      align-items: center;
      gap: var(--kb-space-2);
      cursor: pointer;
    }
    .toggle-text {
      font-size: 0.92rem;
    }
    .field-full {
      grid-column: 1 / -1;
    }
    .input-with-action {
      display: flex;
      gap: var(--kb-space-2);
      align-items: center;
    }
    .input-with-action input {
      flex: 1;
    }
    .action-btn {
      white-space: nowrap;
      min-height: 40px;
    }
    .field-hint {
      display: block;
      margin-top: 4px;
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
    }
    .verify-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      margin-top: 6px;
      padding: var(--kb-space-1) 10px;
      border-radius: var(--kb-radius-md);
      font-size: var(--kb-text-sm);
      flex-wrap: wrap;
    }
    .verify-badge--success {
      background: rgba(47, 133, 90, 0.1);
      color: var(--kb-color-success);
      border: 1px solid rgba(47, 133, 90, 0.25);
    }
    .verify-badge--danger {
      background: rgba(192, 57, 43, 0.1);
      color: var(--kb-color-danger);
      border: 1px solid rgba(192, 57, 43, 0.25);
    }
    .badge-icon {
      font-weight: 700;
    }
    .badge-sub {
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-xs);
    }
  `]
})
export class TaxSettingsSectionComponent {
  private readonly businessApi = inject(BusinessApiService);

  tax = input.required<TaxSettings>();
  taxChange = output<Partial<TaxSettings>>();

  gstState = signal<VerificationState>({ verifying: false, valid: null });
  fssaiState = signal<VerificationState>({ verifying: false, valid: null });

  updateField<K extends keyof TaxSettings>(field: K, value: TaxSettings[K]): void {
    this.taxChange.emit({ [field]: value } as Partial<TaxSettings>);
  }

  onGstinChange(val: string): void {
    this.gstState.set({ verifying: false, valid: null });
    this.updateField('gstin', (val || '').toUpperCase().trim());
  }

  onFssaiChange(val: string): void {
    this.fssaiState.set({ verifying: false, valid: null });
    this.updateField('fssaiNumber', (val || '').trim());
  }

  verifyGst(): void {
    const gstin = this.tax().gstin?.trim();
    if (!gstin || gstin.length !== 15) return;
    this.gstState.set({ verifying: true, valid: null });
    this.businessApi.lookupGst(gstin).subscribe({
      next: (res) => {
        if (res && res.valid) {
          this.gstState.set({
            verifying: false,
            valid: true,
            businessName: res.businessName,
            expiryDate: res.expiryDate
          });
          if (res.expiryDate) {
            this.updateField('gstExpiryDate', res.expiryDate);
          }
        } else {
          this.gstState.set({
            verifying: false,
            valid: false,
            message: res?.error || 'GSTIN is invalid or unregistered.'
          });
        }
      },
      error: () => {
        this.gstState.set({
          verifying: false,
          valid: false,
          message: 'Unable to reach GST verification registry.'
        });
      }
    });
  }

  verifyFssai(): void {
    const fssai = this.tax().fssaiNumber?.trim();
    if (!fssai || fssai.length !== 14) return;
    this.fssaiState.set({ verifying: true, valid: null });
    this.businessApi.lookupFssai(fssai).subscribe({
      next: (res) => {
        if (res && res.valid) {
          this.fssaiState.set({
            verifying: false,
            valid: true,
            businessName: res.businessName || res.legalEntityName,
            expiryDate: res.expiryDate
          });
          if (res.expiryDate) {
            this.updateField('fssaiExpiryDate', res.expiryDate);
          }
        } else {
          this.fssaiState.set({
            verifying: false,
            valid: false,
            message: res?.error || 'FSSAI license number is not found or expired.'
          });
        }
      },
      error: () => {
        this.fssaiState.set({
          verifying: false,
          valid: false,
          message: 'Unable to reach FSSAI verification service.'
        });
      }
    });
  }
}

