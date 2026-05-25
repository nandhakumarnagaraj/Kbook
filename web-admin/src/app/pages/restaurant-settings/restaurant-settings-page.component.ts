import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BusinessApiService } from '../../core/services/business-api.service';
import { UpdateBusinessProfileRequest } from '../../core/models/api.models';

@Component({
  selector: 'app-restaurant-settings-page',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatSlideToggleModule,
    MatCheckboxModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">Restaurant Settings</h1>
          <p class="page-subtitle">Configure your business profile, payment preferences, and tax compliance.</p>
        </div>
      </div>

      <div class="settings-grid">
        <!-- PROFILE SECTION -->
        <mat-card class="settings-card">
          <mat-card-header>
            <mat-icon mat-card-avatar color="primary">storefront</mat-icon>
            <mat-card-title>Business Profile</mat-card-title>
            <mat-card-subtitle>Core identity and contact information.</mat-card-subtitle>
          </mat-card-header>
          
          <mat-card-content>
            <div class="form-grid">
              <!-- Shop Logo Upload & Controls -->
              <div class="logo-section">
                <div class="logo-preview-container">
                  <img *ngIf="logoUrl()" [src]="logoUrl()" alt="Shop Logo" class="logo-preview">
                  <div *ngIf="!logoUrl() && !uploadingLogo()" class="logo-placeholder">
                    <mat-icon>storefront</mat-icon>
                  </div>
                  <div *ngIf="uploadingLogo()" class="logo-loading">
                    <mat-spinner diameter="32"></mat-spinner>
                  </div>
                </div>
                <div class="logo-actions">
                  <button mat-stroked-button type="button" (click)="logoInput.click()" [disabled]="uploadingLogo()">
                    <mat-icon>upload</mat-icon>
                    {{ logoUrl() ? 'Change Logo' : 'Upload Logo' }}
                  </button>
                  <button mat-stroked-button color="warn" type="button" *ngIf="logoUrl()" (click)="removeLogo()" [disabled]="uploadingLogo()">
                    <mat-icon>delete</mat-icon>
                    Remove
                  </button>
                  <input #logoInput type="file" (change)="onLogoSelected($event)" accept="image/*" style="display: none;">
                </div>
              </div>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Shop Name</mat-label>
                <input matInput [(ngModel)]="form.shopName" placeholder="e.g. Khana Khazana">
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Business Address</mat-label>
                <textarea matInput [(ngModel)]="form.shopAddress" rows="3" placeholder="Full street address..."></textarea>
              </mat-form-field>

              <div class="two-col">
                <!-- WhatsApp OTP Verification field -->
                <div class="whatsapp-container">
                  <div class="whatsapp-input-row">
                    <mat-form-field appearance="outline" style="flex: 1;">
                      <mat-label>WhatsApp Number</mat-label>
                      <input matInput [(ngModel)]="form.whatsappNumber" (ngModelChange)="onWhatsappChanged()" placeholder="10-digit number" type="tel" maxlength="10">
                      <mat-icon matSuffix>chat</mat-icon>
                    </mat-form-field>
                    <button mat-flat-button color="primary" type="button" class="verify-btn" 
                            *ngIf="numberChanged() && !otpSent && !isOtpVerified" 
                            [disabled]="sendingOtp() || !isValidWhatsapp()" (click)="sendOtp()">
                      <span *ngIf="!sendingOtp()">Send OTP</span>
                      <mat-spinner diameter="18" *ngIf="sendingOtp()"></mat-spinner>
                    </button>
                    <div class="verified-badge" *ngIf="isOtpVerified && !numberChanged() && form.whatsappNumber">
                      <mat-icon>check_circle</mat-icon>
                      <span>Verified</span>
                    </div>
                  </div>

                  <!-- OTP Input Box -->
                  <div class="otp-verification-box animate-fade-in-up" *ngIf="otpSent">
                    <p class="otp-instructions">Enter the 6-digit OTP sent to WhatsApp number <strong>{{ form.whatsappNumber }}</strong></p>
                    <div class="otp-input-row">
                      <mat-form-field appearance="outline" class="otp-field">
                        <mat-label>Enter OTP</mat-label>
                        <input matInput [ngModel]="otpValue" (ngModelChange)="onOtpInput($event)" placeholder="6-digit OTP" maxlength="6" type="tel">
                      </mat-form-field>
                      <span class="timer-text" *ngIf="otpTimer > 0">Resend in {{ getFormattedTimer() }}</span>
                      <button mat-button color="primary" type="button" *ngIf="otpTimer === 0" (click)="sendOtp()" [disabled]="sendingOtp()">
                        Resend OTP
                      </button>
                    </div>
                  </div>
                </div>

                <mat-form-field appearance="outline">
                  <mat-label>Email Address</mat-label>
                  <input matInput [(ngModel)]="form.email" type="email" placeholder="owner@example.com" [disabled]="true">
                  <mat-icon matSuffix>email</mat-icon>
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Google Review Link</mat-label>
                <input matInput [(ngModel)]="form.reviewUrl" placeholder="https://g.page/r/...">
                <mat-icon matSuffix>star</mat-icon>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Invoice Footer Message</mat-label>
                <textarea matInput [(ngModel)]="form.invoiceFooter" rows="3" placeholder="Thank you for your visit!"></textarea>
                <mat-hint>Shown at the bottom of printed and public invoices.</mat-hint>
              </mat-form-field>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- PAYMENTS SECTION -->
        <mat-card class="settings-card">
          <mat-card-header>
            <mat-icon mat-card-avatar color="primary">payments</mat-icon>
            <mat-card-title>Payment Configuration</mat-card-title>
            <mat-card-subtitle>Manage currency and accepted payment methods.</mat-card-subtitle>
          </mat-card-header>
          
          <mat-card-content>
            <div class="form-grid">
              <div class="two-col">
                <mat-form-field appearance="outline">
                  <mat-label>Default Currency</mat-label>
                  <mat-select [(ngModel)]="form.currency" [disabled]="true">
                    <mat-option value="INR">INR (₹) - Indian Rupee</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>UPI Handle</mat-label>
                  <input matInput [(ngModel)]="form.upiHandle" placeholder="business@upi" [disabled]="!form.upiEnabled">
                  <mat-icon matSuffix>qr_code</mat-icon>
                </mat-form-field>
              </div>

              <h3 class="section-divider">Accepted Payment Methods</h3>
              <div class="checkbox-grid">
                <mat-checkbox [(ngModel)]="form.cashEnabled" color="primary">Cash</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.upiEnabled" color="primary">Offline UPI</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.posEnabled" color="primary">POS Machine</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.zomatoEnabled" color="primary" [disabled]="true">Zomato Orders</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.swiggyEnabled" color="primary" [disabled]="true">Swiggy Orders</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.ownWebsiteEnabled" color="primary" [disabled]="true">Online Store</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.easebuzzEnabled" color="primary" [disabled]="true">Easebuzz Payments</mat-checkbox>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- TAX & COMPLIANCE -->
        <mat-card class="settings-card">
          <mat-card-header>
            <mat-icon mat-card-avatar color="primary">verified</mat-icon>
            <mat-card-title>Tax & Compliance</mat-card-title>
            <mat-card-subtitle>GST, FSSAI, and regional compliance settings.</mat-card-subtitle>
          </mat-card-header>
          
          <mat-card-content>
            <div class="form-grid">
              <div class="two-col">
                <mat-form-field appearance="outline">
                  <mat-label>Country</mat-label>
                  <input matInput [(ngModel)]="form.country" [disabled]="true">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Timezone</mat-label>
                  <mat-select [(ngModel)]="form.timezone" [disabled]="true">
                    <mat-option value="Asia/Kolkata">Asia/Kolkata (IST)</mat-option>
                    <mat-option value="Asia/Dubai">Asia/Dubai (GST)</mat-option>
                    <mat-option value="UTC">UTC (Universal)</mat-option>
                  </mat-select>
                </mat-form-field>
              </div>

              <!-- FSSAI License with Fetch Button -->
              <div class="fetch-row">
                <mat-form-field appearance="outline" style="flex: 1;">
                  <mat-label>FSSAI License Number</mat-label>
                  <input matInput [(ngModel)]="form.fssaiNumber" (input)="onFssaiInput($event)" placeholder="Enter 14 digits" maxlength="14">
                  <mat-icon matSuffix>assignment</mat-icon>
                </mat-form-field>
                <button mat-stroked-button type="button" class="fetch-btn" [disabled]="!isValidFssai() || lookupLoading()" (click)="fetchFssai()">
                  Fetch
                </button>
              </div>
              <div *ngIf="form.fssaiExpiryDate" class="expiry-notice" [class.expiring]="isFssaiExpiringSoon()" [class.expired]="isFssaiExpired()">
                <mat-icon class="notice-icon">{{ isFssaiExpired() ? 'error' : (isFssaiExpiringSoon() ? 'warning' : 'check_circle') }}</mat-icon>
                <span *ngIf="isFssaiExpired()">FSSAI License EXPIRED on {{ formatExpiryDate(form.fssaiExpiryDate) }}</span>
                <span *ngIf="isFssaiExpiringSoon()">FSSAI License Expiring soon: {{ formatExpiryDate(form.fssaiExpiryDate) }}</span>
                <span *ngIf="!isFssaiExpired() && !isFssaiExpiringSoon()">FSSAI License valid till {{ formatExpiryDate(form.fssaiExpiryDate) }}</span>
              </div>

              <div class="toggle-group row">
                <mat-slide-toggle [(ngModel)]="form.gstEnabled" color="primary">Enable GST</mat-slide-toggle>
                <mat-slide-toggle [(ngModel)]="form.isTaxInclusive" color="primary">Inclusive Pricing</mat-slide-toggle>
              </div>

              <!-- GSTIN with Fetch Button -->
              <div class="two-col" *ngIf="form.gstEnabled">
                <div>
                  <div class="fetch-row">
                    <mat-form-field appearance="outline" style="flex: 1;">
                      <mat-label>GSTIN</mat-label>
                      <input matInput [(ngModel)]="form.gstin" (input)="onGstinInput($event)" placeholder="15-char GSTIN" maxlength="15">
                    </mat-form-field>
                    <button mat-stroked-button type="button" class="fetch-btn" [disabled]="!isValidGstin() || lookupLoading()" (click)="fetchGst()">
                      Fetch
                    </button>
                  </div>
                  <div *ngIf="form.gstExpiryDate" class="expiry-notice" [class.expiring]="isGstExpiringSoon()" [class.expired]="isGstExpired()">
                    <mat-icon class="notice-icon">{{ isGstExpired() ? 'error' : (isGstExpiringSoon() ? 'warning' : 'check_circle') }}</mat-icon>
                    <span *ngIf="isGstExpired()">GSTIN Expired on {{ formatExpiryDate(form.gstExpiryDate) }}</span>
                    <span *ngIf="isGstExpiringSoon()">GSTIN Expiring soon: {{ formatExpiryDate(form.gstExpiryDate) }}</span>
                    <span *ngIf="!isGstExpired() && !isGstExpiringSoon()">GSTIN valid till {{ formatExpiryDate(form.gstExpiryDate) }}</span>
                  </div>
                </div>
                <mat-form-field appearance="outline">
                  <mat-label>GST Percentage</mat-label>
                  <input matInput type="number" step="0.1" [(ngModel)]="form.gstPercentage">
                  <span matSuffix>%</span>
                </mat-form-field>
              </div>

              <!-- Fetch Both button if both valid -->
              <div class="fetch-both-container" *ngIf="isValidFssai() && form.gstEnabled && isValidGstin()">
                <button mat-stroked-button type="button" class="fetch-both-btn" [disabled]="lookupLoading()" (click)="fetchBoth()">
                  Fetch Both (GST + FSSAI)
                </button>
              </div>

              <h3 class="section-divider">Custom Taxes</h3>
              <div class="three-col">
                <mat-form-field appearance="outline">
                  <mat-label>Tax Name</mat-label>
                  <input matInput [(ngModel)]="form.customTaxName" placeholder="e.g. Service Charge">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Tax %</mat-label>
                  <input matInput type="number" step="0.1" [(ngModel)]="form.customTaxPercentage">
                  <span matSuffix>%</span>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Tax ID (Optional)</mat-label>
                  <input matInput [(ngModel)]="form.customTaxNumber">
                </mat-form-field>
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      </div>

      <div class="action-footer mat-elevation-z4">
        <div class="footer-container">
           <span class="status-text" *ngIf="loading()">Loading configuration...</span>
           <span class="spacer"></span>
           <button mat-flat-button color="primary" class="save-btn" (click)="save()" [disabled]="saving()">
              <mat-icon *ngIf="!saving()">save</mat-icon>
              <mat-spinner diameter="18" *ngIf="saving()"></mat-spinner>
              Save
           </button>
        </div>
      </div>
    </div>

    <!-- Lookup Results Dialog Overlay -->
    <div class="modal-overlay" *ngIf="showLookupResult()">
      <div class="modal-card">
        <h2>Lookup Result</h2>
        <div class="modal-content">
          <div *ngIf="lookupLoading()" class="modal-loading">
            <mat-spinner diameter="32"></mat-spinner>
            <p>Fetching details...</p>
          </div>
          <div *ngIf="!lookupLoading() && lookupResult() as result">
            <p *ngIf="result.businessName"><strong>Business Name:</strong> {{ result.businessName }}</p>
            <p *ngIf="result.address"><strong>Address:</strong> {{ result.address }}</p>
            <p *ngIf="result.gstin"><strong>GSTIN:</strong> {{ result.gstin }}</p>
            <p *ngIf="result.fssaiNo"><strong>FSSAI:</strong> {{ result.fssaiNo }}</p>
            <p *ngIf="!result.businessName && !result.address" class="info-text">No name/address returned, but details are valid.</p>
          </div>
          <div *ngIf="!lookupLoading() && lookupError()">
            <p class="error-text">{{ lookupError() }}</p>
          </div>
        </div>
        <div class="modal-actions">
          <button mat-stroked-button (click)="closeLookup()">Dismiss</button>
          <button mat-flat-button color="primary" *ngIf="!lookupLoading() && lookupResult() && !lookupError()" (click)="applyLookupResult()">Apply</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1000px; margin: 0 auto; padding-bottom: 120px; }
    .header-row { margin-bottom: 32px; }
    .page-title { margin: 0; font-size: 2rem; font-weight: 800; color: var(--ink); letter-spacing: -0.5px; }
    .page-subtitle { margin: 4px 0 0; color: var(--muted); font-size: 0.95rem; }

    .settings-grid { display: flex; flex-direction: column; gap: 32px; }
    .settings-card { 
      border: 1px solid var(--line); 
      background: var(--panel);
      backdrop-filter: blur(12px);
      box-shadow: var(--shadow-md);
      border-radius: var(--radius-xl); 
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
    }
    .settings-card:hover {
      box-shadow: var(--shadow-lg);
    }
    
    .form-grid { padding-top: 16px; }
    .full-width { width: 100%; margin-bottom: 8px; }
    .two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .three-col { display: grid; grid-template-columns: 1.5fr 1fr 1.5fr; gap: 16px; }

    .section-divider { font-size: 0.85rem; font-weight: 800; text-transform: uppercase; color: var(--muted); letter-spacing: 1.5px; margin: 28px 0 16px; border-bottom: 1px solid var(--line); padding-bottom: 8px; }

    .toggle-group { display: flex; flex-direction: column; gap: 16px; padding: 16px; background: var(--bg); border: 1px solid var(--line); border-radius: 12px; margin: 8px 0; }
    .toggle-group.row { flex-direction: row; gap: 32px; }

    .checkbox-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 16px; padding: 8px; }

    .action-footer { position: fixed; bottom: 0; left: 0; right: 0; background: var(--panel); padding: 16px 24px; z-index: 100; border-top: 1px solid var(--line); backdrop-filter: blur(12px); box-shadow: var(--shadow-lg); }
    .footer-container { max-width: 1000px; margin: 0 auto; display: flex; align-items: center; }
    .save-btn { min-width: 180px; height: 48px; font-weight: 700; font-size: 1rem; border-radius: var(--radius-lg); }
    
    .status-text { color: var(--muted); font-size: 0.9rem; font-weight: 600; }
    .spacer { flex: 1; }

    /* Logo Uploader styling */
    .logo-section { display: flex; align-items: center; gap: 20px; margin-bottom: 24px; padding: 12px; background: var(--bg); border: 1px dashed var(--line); border-radius: var(--radius-lg); }
    .logo-preview-container { width: 80px; height: 80px; border-radius: var(--radius-md); overflow: hidden; background: #ffffff; border: 1px solid var(--line); display: flex; align-items: center; justify-content: center; position: relative; }
    .logo-preview { width: 100%; height: 100%; object-fit: cover; }
    .logo-placeholder { color: var(--muted); display: flex; align-items: center; justify-content: center; }
    .logo-placeholder mat-icon { font-size: 40px; width: 40px; height: 40px; }
    .logo-loading { display: flex; align-items: center; justify-content: center; }
    .logo-actions { display: flex; gap: 12px; }

    /* WhatsApp OTP styling */
    .whatsapp-container { display: flex; flex-direction: column; gap: 8px; }
    .whatsapp-input-row { display: flex; align-items: center; gap: 12px; }
    .verify-btn { height: 52px; font-weight: 700; border-radius: var(--radius-md); }
    .verified-badge { display: flex; align-items: center; gap: 6px; color: var(--accent); font-weight: 700; padding: 8px 12px; background: var(--accent-soft); border-radius: var(--radius-md); font-size: 0.9rem; }
    .verified-badge mat-icon { font-size: 20px; width: 20px; height: 20px; color: var(--accent); }
    .otp-verification-box { padding: 16px; background: var(--bg); border: 1px solid var(--line); border-radius: var(--radius-md); margin-top: 8px; }
    .otp-instructions { margin: 0 0 12px; font-size: 0.85rem; color: var(--muted); }
    .otp-input-row { display: flex; align-items: center; gap: 16px; }
    .otp-field { width: 140px; margin-bottom: 0; }
    .timer-text { font-size: 0.85rem; color: var(--muted); font-weight: 600; }

    /* Fetch buttons styling */
    .fetch-row { display: flex; align-items: flex-start; gap: 12px; width: 100%; }
    .fetch-btn { height: 56px; font-weight: 700; border-radius: var(--radius-md); border: 1px solid var(--line); }
    .fetch-both-container { width: 100%; display: flex; justify-content: center; margin-top: 12px; margin-bottom: 8px; }
    .fetch-both-btn { width: 100%; height: 48px; font-weight: 700; border-radius: var(--radius-md); border: 1px solid var(--line); }
    
    .expiry-notice { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; font-weight: 600; color: var(--muted); margin-top: 4px; margin-bottom: 12px; padding: 6px 12px; background: var(--bg); border: 1px solid var(--line); border-radius: 6px; width: fit-content; }
    .expiry-notice.expiring { color: #f59e0b; background: rgba(245, 158, 11, 0.08); border-color: rgba(245, 158, 11, 0.3); }
    .expiry-notice.expired { color: #ef4444; background: rgba(239, 68, 68, 0.08); border-color: rgba(239, 68, 68, 0.3); }
    .expiry-notice mat-icon { font-size: 16px; width: 16px; height: 16px; }

    /* Lookup modal styling */
    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.4); z-index: 1000; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(4px); }
    .modal-card { background: var(--panel); border: 1px solid var(--line); border-radius: var(--radius-xl); padding: 24px; width: 90%; max-width: 450px; box-shadow: var(--shadow-xl); animation: fadeInUp 0.3s ease; }
    .modal-card h2 { margin-top: 0; color: var(--brand); font-size: 1.3rem; font-weight: 800; }
    .modal-loading { display: flex; flex-direction: column; align-items: center; gap: 12px; justify-content: center; padding: 20px; }
    .modal-content { margin: 16px 0 24px; color: var(--ink); }
    .modal-content p { margin: 8px 0; font-size: 0.95rem; }
    .modal-actions { display: flex; justify-content: flex-end; gap: 12px; }
    .error-text { color: var(--danger); font-weight: 600; }
    .info-text { color: var(--muted); font-style: italic; }

    @keyframes fadeInUp {
      from { opacity: 0; transform: translateY(16px); }
      to { opacity: 1; transform: translateY(0); }
    }

    @media (max-width: 768px) {
      .two-col, .three-col { grid-template-columns: 1fr; }
      .footer-container { flex-direction: column; gap: 12px; }
      .save-btn { width: 100%; }
      .whatsapp-input-row { flex-direction: column; align-items: stretch; }
      .verify-btn { width: 100%; }
      .fetch-row { flex-direction: column; align-items: stretch; }
      .fetch-btn { width: 100%; }
    }
  `]
})
export class RestaurantSettingsPageComponent implements OnInit {
  private readonly api = inject(BusinessApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);

  form: UpdateBusinessProfileRequest = {};
  saving = signal(false);
  loading = signal(true);

  logoUrl = signal<string | null>(null);
  uploadingLogo = signal(false);

  originalWhatsappNumber = '';
  otpSent = false;
  otpValue = '';
  otpTimer = 0;
  timerInterval: any;
  isOtpVerified = false;
  sendingOtp = signal(false);
  verifyingOtp = signal(false);

  showLookupResult = signal(false);
  lookupLoading = signal(false);
  lookupResult = signal<any>(null);
  lookupError = signal<string | null>(null);

  isValidWhatsapp(): boolean {
    return !!this.form.whatsappNumber && this.form.whatsappNumber.length === 10 && /^\d+$/.test(this.form.whatsappNumber);
  }

  isValidFssai(): boolean {
    return !!this.form.fssaiNumber && this.form.fssaiNumber.length === 14 && /^\d+$/.test(this.form.fssaiNumber);
  }

  isValidGstin(): boolean {
    return !!this.form.gstin && this.form.gstin.length === 15;
  }

  onWhatsappChanged(): void {
    if (this.form.whatsappNumber) {
      this.form.whatsappNumber = this.form.whatsappNumber.replace(/\D/g, '').slice(0, 10);
    }
    if (this.form.whatsappNumber !== this.originalWhatsappNumber) {
      this.otpSent = false;
      this.isOtpVerified = false;
      this.otpValue = '';
      if (this.timerInterval) {
        clearInterval(this.timerInterval);
      }
    } else {
      this.isOtpVerified = true;
    }
  }

  numberChanged(): boolean {
    return this.form.whatsappNumber !== this.originalWhatsappNumber;
  }

  sendOtp(): void {
    if (!this.isValidWhatsapp()) return;
    this.sendingOtp.set(true);
    this.api.requestUpdateMobileOtp(this.form.whatsappNumber!).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.sendingOtp.set(false);
        this.otpSent = true;
        this.isOtpVerified = false;
        this.startOtpTimer();
        this.snackBar.open('OTP sent to WhatsApp.', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.sendingOtp.set(false);
        this.snackBar.open(err?.error?.error || 'Failed to send OTP. Please try again.', 'Close', { duration: 5000 });
      }
    });
  }

  startOtpTimer(): void {
    this.otpTimer = 120;
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
    this.timerInterval = setInterval(() => {
      if (this.otpTimer > 0) {
        this.otpTimer--;
      } else {
        clearInterval(this.timerInterval);
      }
    }, 1000);
  }

  getFormattedTimer(): string {
    const minutes = Math.floor(this.otpTimer / 60);
    const seconds = this.otpTimer % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }

  onOtpInput(value: string): void {
    this.otpValue = value.replace(/\D/g, '').slice(0, 6);
    if (this.otpValue.length === 6) {
      this.verifyOtp();
    }
  }

  verifyOtp(): void {
    this.verifyingOtp.set(true);
    this.api.confirmUpdateMobile(this.form.whatsappNumber!, this.otpValue).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.verifyingOtp.set(false);
        this.isOtpVerified = true;
        this.originalWhatsappNumber = this.form.whatsappNumber!;
        this.otpSent = false;
        this.otpValue = '';
        if (this.timerInterval) clearInterval(this.timerInterval);
        this.snackBar.open('WhatsApp number verified successfully.', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.verifyingOtp.set(false);
        this.isOtpVerified = false;
        this.snackBar.open(err?.error?.error || 'Invalid OTP code.', 'Close', { duration: 5000 });
      }
    });
  }

  onLogoSelected(event: any): void {
    const file = event.target.files?.[0];
    if (file) {
      this.uploadingLogo.set(true);
      this.api.uploadLogo(file).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (res) => {
          this.logoUrl.set(res.logoUrl);
          this.uploadingLogo.set(false);
          this.snackBar.open('Logo uploaded successfully.', 'Close', { duration: 3000 });
        },
        error: (err) => {
          this.uploadingLogo.set(false);
          this.snackBar.open(err?.error?.error || 'Failed to upload logo.', 'Close', { duration: 5000 });
        }
      });
    }
  }

  removeLogo(): void {
    this.uploadingLogo.set(true);
    this.api.deleteLogo().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.logoUrl.set(null);
        this.uploadingLogo.set(false);
        this.snackBar.open('Logo removed successfully.', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.uploadingLogo.set(false);
        this.snackBar.open(err?.error?.error || 'Failed to remove logo.', 'Close', { duration: 5000 });
      }
    });
  }

  onFssaiInput(event: any): void {
    const val = event.target.value;
    this.form.fssaiNumber = val.replace(/\D/g, '').slice(0, 14);
  }

  onGstinInput(event: any): void {
    const val = event.target.value;
    this.form.gstin = val.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 15);
  }

  fetchFssai(): void {
    if (!this.isValidFssai()) return;
    this.showLookupResult.set(true);
    this.lookupLoading.set(true);
    this.lookupError.set(null);
    this.lookupResult.set(null);
    this.api.lookupFssai(this.form.fssaiNumber!).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.lookupLoading.set(false);
        if (res.valid) {
          this.lookupResult.set({
            businessName: res.businessName,
            address: res.address,
            fssaiNo: this.form.fssaiNumber,
            fssaiExpiryDate: res.expiryDate || null
          });
        } else {
          this.lookupError.set(res.error || 'No data found');
        }
      },
      error: (err) => {
        this.lookupLoading.set(false);
        this.lookupError.set(err?.error?.error || 'Lookup service unavailable');
      }
    });
  }

  fetchGst(): void {
    if (!this.isValidGstin()) return;
    this.showLookupResult.set(true);
    this.lookupLoading.set(true);
    this.lookupError.set(null);
    this.lookupResult.set(null);
    this.api.lookupGst(this.form.gstin!).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.lookupLoading.set(false);
        if (res.valid) {
          this.lookupResult.set({
            businessName: res.businessName,
            address: res.address,
            gstin: this.form.gstin,
            gstExpiryDate: res.expiryDate || null
          });
        } else {
          this.lookupError.set(res.error || 'No data found');
        }
      },
      error: (err) => {
        this.lookupLoading.set(false);
        this.lookupError.set(err?.error?.error || 'Lookup service unavailable');
      }
    });
  }

  fetchBoth(): void {
    if (!this.isValidFssai() || !this.isValidGstin()) return;
    this.showLookupResult.set(true);
    this.lookupLoading.set(true);
    this.lookupError.set(null);
    this.lookupResult.set(null);
    this.api.lookupBoth(this.form.gstin!, this.form.fssaiNumber!).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.lookupLoading.set(false);
        const gstValid = res.gst?.valid;
        const fssaiValid = res.fssai?.valid;
        if (gstValid || fssaiValid) {
          this.lookupResult.set({
            businessName: res.businessName || res.fssai?.businessName || res.gst?.businessName,
            address: res.address || res.fssai?.address || res.gst?.address,
            gstin: this.form.gstin,
            fssaiNo: this.form.fssaiNumber,
            fssaiExpiryDate: res.fssai?.expiryDate || null,
            gstExpiryDate: res.gst?.expiryDate || null
          });
        } else {
          this.lookupError.set(res.gst?.error || res.fssai?.error || 'No data found');
        }
      },
      error: (err) => {
        this.lookupLoading.set(false);
        this.lookupError.set(err?.error?.error || 'Lookup service unavailable');
      }
    });
  }

  closeLookup(): void {
    this.showLookupResult.set(false);
    this.lookupResult.set(null);
    this.lookupError.set(null);
  }

  applyLookupResult(): void {
    const res = this.lookupResult();
    if (res) {
      if (res.businessName) this.form.shopName = res.businessName;
      if (res.address) this.form.shopAddress = res.address;
      if (res.fssaiExpiryDate) this.form.fssaiExpiryDate = res.fssaiExpiryDate;
      if (res.gstExpiryDate) this.form.gstExpiryDate = res.gstExpiryDate;
      this.snackBar.open('Fetched details applied.', 'Close', { duration: 3000 });
    }
    this.closeLookup();
  }

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => {
      if (this.timerInterval) {
        clearInterval(this.timerInterval);
      }
    });
    this.api.getProfile().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (p) => {
        this.form = {
          shopName: p.shopName ?? undefined,
          shopAddress: p.shopAddress ?? undefined,
          whatsappNumber: p.whatsappNumber ?? undefined,
          email: p.email ?? undefined,
          currency: 'INR',
          upiEnabled: p.upiEnabled ?? undefined,
          upiHandle: p.upiHandle ?? undefined,
          upiMobile: p.upiMobile ?? undefined,
          cashEnabled: p.cashEnabled ?? undefined,
          posEnabled: p.posEnabled ?? undefined,
          zomatoEnabled: p.zomatoEnabled ?? undefined,
          swiggyEnabled: p.swiggyEnabled ?? undefined,
          ownWebsiteEnabled: p.ownWebsiteEnabled ?? undefined,
          easebuzzEnabled: p.easebuzzEnabled ?? undefined,
          country: 'India',
          timezone: 'Asia/Kolkata',
          gstEnabled: p.gstEnabled ?? undefined,
          gstin: p.gstin ?? undefined,
          isTaxInclusive: p.isTaxInclusive ?? undefined,
          gstPercentage: p.gstPercentage ?? undefined,
          customTaxName: p.customTaxName ?? undefined,
          customTaxNumber: p.customTaxNumber ?? undefined,
          customTaxPercentage: p.customTaxPercentage ?? undefined,
          fssaiNumber: p.fssaiNumber ?? undefined,
          fssaiExpiryDate: p.fssaiExpiryDate ?? undefined,
          gstExpiryDate: p.gstExpiryDate ?? undefined,
          reviewUrl: p.reviewUrl ?? undefined,
          invoiceFooter: p.invoiceFooter ?? undefined,
          showBranding: p.showBranding ?? undefined,
          maskCustomerPhone: p.maskCustomerPhone ?? undefined,
        };
        this.logoUrl.set(p.logoUrl ?? null);
        this.originalWhatsappNumber = p.whatsappNumber ?? '';
        this.isOtpVerified = true;
        this.loading.set(false);
      },
      error: () => { 
        this.loading.set(false);
        this.snackBar.open('Failed to load profile.', 'Close', { duration: 5000 });
      }
    });
  }

  save(): void {
    if (this.numberChanged() && !this.isOtpVerified) {
      this.snackBar.open('Verify the new WhatsApp number', 'Close', { duration: 5000 });
      return;
    }
    this.saving.set(true);
    this.api.updateProfile(this.form).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.snackBar.open('Settings updated successfully.', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.saving.set(false);
        this.snackBar.open(err?.error?.error || 'Failed to save. Please try again.', 'Close', { duration: 5000 });
      }
    });
  }

  isFssaiExpiringSoon(): boolean { return this.isExpiringSoon(this.form.fssaiExpiryDate ?? undefined); }
  isFssaiExpired(): boolean      { return this.isExpired(this.form.fssaiExpiryDate ?? undefined); }
  isGstExpiringSoon(): boolean   { return this.isExpiringSoon(this.form.gstExpiryDate ?? undefined); }
  isGstExpired(): boolean        { return this.isExpired(this.form.gstExpiryDate ?? undefined); }

  private isExpiringSoon(dateStr: string | undefined): boolean {
    if (!dateStr) return false;
    const expiry = new Date(dateStr);
    const today = new Date();
    expiry.setHours(0,0,0,0);
    today.setHours(0,0,0,0);
    const diffDays = Math.round((expiry.getTime() - today.getTime()) / 86400000);
    return diffDays > 0 && diffDays <= 30;
  }

  private isExpired(dateStr: string | undefined): boolean {
    if (!dateStr) return false;
    const expiry = new Date(dateStr);
    const today = new Date();
    expiry.setHours(0,0,0,0);
    today.setHours(0,0,0,0);
    return expiry.getTime() < today.getTime();
  }

  formatExpiryDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
  }
}
