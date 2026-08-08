import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

const API = environment.apiBaseUrl;

interface RestaurantProfile {
  shopName: string;
  shopAddress: string;
  whatsappNumber: string;
  email: string;
  fssaiNumber: string;
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
              <label>FSSAI Number</label>
              <input class="field-control" [(ngModel)]="p.fssaiNumber" placeholder="FSSAI license number" />
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
              <input class="field-control" [(ngModel)]="p.upiHandle" placeholder="yourname@upi" />
            </div>
            <div class="field" *ngIf="p.upiEnabled">
              <label>UPI Mobile</label>
              <input class="field-control" [(ngModel)]="p.upiMobile" placeholder="Linked mobile number" maxlength="10" />
            </div>
            <div class="field">
              <label>Order Payment Flow</label>
              <select class="field-select" [(ngModel)]="p.orderPaymentFlowMode">
                <option value="pay_before_food">Pay before food</option>
                <option value="pay_after_food">Pay after food (dine-in)</option>
              </select>
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
    </div>
  `,
  styles: [`
    .settings-section { margin-bottom: 1rem; }
    .settings-section h3 { margin: 0 0 1rem; color: var(--ink); }
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
      gap: 1rem;
    }
    .field label { display: block; font-size: 0.85rem; color: var(--ink); font-weight: 500; margin-bottom: 0.25rem; }
    .toggle-label {
      display: flex; align-items: center; gap: 0.5rem; cursor: pointer;
      font-size: 0.9rem; color: var(--ink); font-weight: 500;
    }
    .toggle-label input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--brand); }
    .save-bar { padding: 1rem 0; display: flex; justify-content: flex-end; }
    .success-text { color: var(--accent); font-size: 0.85rem; }
    .error-text { color: var(--danger); font-size: 0.85rem; }
  `]
})
export class BusinessSettingsPageComponent {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  loading = signal(true);
  loadError = signal('');
  saving = signal(false);
  profile = signal<RestaurantProfile | null>(null);

  // Change password state
  pwPhone = '';
  pwOtp = '';
  pwNew = '';
  pwOtpSent = false;
  pwLoading = false;
  pwError = '';
  pwSuccess = '';

  constructor() { this.load(); }

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
            fssaiNumber: p.fssaiNumber ?? '',
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
            shopName: '', shopAddress: '', whatsappNumber: '', email: '', fssaiNumber: '',
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
