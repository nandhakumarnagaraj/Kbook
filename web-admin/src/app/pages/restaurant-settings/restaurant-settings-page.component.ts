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
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Shop Name</mat-label>
                <input matInput [(ngModel)]="form.shopName" placeholder="e.g. Khana Khazana">
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Business Address</mat-label>
                <textarea matInput [(ngModel)]="form.shopAddress" rows="3" placeholder="Full street address..."></textarea>
              </mat-form-field>

              <div class="two-col">
                <mat-form-field appearance="outline">
                  <mat-label>WhatsApp Number</mat-label>
                  <input matInput [(ngModel)]="form.whatsappNumber" placeholder="+91...">
                  <mat-icon matSuffix>chat</mat-icon>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Email Address</mat-label>
                  <input matInput [(ngModel)]="form.email" type="email" placeholder="owner@example.com">
                  <mat-icon matSuffix>email</mat-icon>
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Review / Google URL</mat-label>
                <input matInput [(ngModel)]="form.reviewUrl" placeholder="https://g.page/...">
                <mat-icon matSuffix>star</mat-icon>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Invoice Footer</mat-label>
                <textarea matInput [(ngModel)]="form.invoiceFooter" rows="2" placeholder="Thank you message..."></textarea>
              </mat-form-field>

              <div class="toggle-group">
                <mat-slide-toggle [(ngModel)]="form.showBranding" color="primary">
                  Show KhanaBook Branding
                </mat-slide-toggle>
                <mat-slide-toggle [(ngModel)]="form.maskCustomerPhone" color="primary">
                  Mask Phone on Receipts
                </mat-slide-toggle>
              </div>
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
                  <mat-select [(ngModel)]="form.currency">
                    <mat-option value="INR">INR (₹) - Indian Rupee</mat-option>
                    <mat-option value="USD">USD ($) - US Dollar</mat-option>
                    <mat-option value="AED">AED (د.إ) - UAE Dirham</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>UPI Handle</mat-label>
                  <input matInput [(ngModel)]="form.upiHandle" placeholder="business@upi">
                  <mat-icon matSuffix>qr_code</mat-icon>
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>UPI Linked Mobile</mat-label>
                <input matInput [(ngModel)]="form.upiMobile" placeholder="Used for QR generation">
              </mat-form-field>

              <h3 class="section-divider">Accepted Payment Methods</h3>
              <div class="checkbox-grid">
                <mat-checkbox [(ngModel)]="form.cashEnabled" color="primary">Cash</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.upiEnabled" color="primary">Direct UPI</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.posEnabled" color="primary">POS / Card</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.zomatoEnabled" color="primary">Zomato Orders</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.swiggyEnabled" color="primary">Swiggy Orders</mat-checkbox>
                <mat-checkbox [(ngModel)]="form.ownWebsiteEnabled" color="primary">Online Store</mat-checkbox>
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
                  <input matInput [(ngModel)]="form.country">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Timezone</mat-label>
                  <mat-select [(ngModel)]="form.timezone">
                    <mat-option value="Asia/Kolkata">Asia/Kolkata (IST)</mat-option>
                    <mat-option value="Asia/Dubai">Asia/Dubai (GST)</mat-option>
                    <mat-option value="UTC">UTC (Universal)</mat-option>
                  </mat-select>
                </mat-form-field>
              </div>

              <div class="toggle-group row">
                <mat-slide-toggle [(ngModel)]="form.gstEnabled" color="primary">Enable GST</mat-slide-toggle>
                <mat-slide-toggle [(ngModel)]="form.isTaxInclusive" color="primary">Inclusive Pricing</mat-slide-toggle>
              </div>

              <div class="two-col" *ngIf="form.gstEnabled">
                <mat-form-field appearance="outline">
                  <mat-label>GSTIN</mat-label>
                  <input matInput [(ngModel)]="form.gstin" placeholder="15-char GSTIN">
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>GST Percentage</mat-label>
                  <input matInput type="number" step="0.1" [(ngModel)]="form.gstPercentage">
                  <span matSuffix>%</span>
                </mat-form-field>
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

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>FSSAI License Number</mat-label>
                <input matInput [(ngModel)]="form.fssaiNumber">
                <mat-icon matSuffix>assignment</mat-icon>
              </mat-form-field>
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
              Save Changes
           </button>
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

    @media (max-width: 768px) {
      .two-col, .three-col { grid-template-columns: 1fr; }
      .footer-container { flex-direction: column; gap: 12px; }
      .save-btn { width: 100%; }
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

  ngOnInit(): void {
    this.api.getProfile().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (p) => {
        this.form = {
          shopName: p.shopName ?? undefined,
          shopAddress: p.shopAddress ?? undefined,
          whatsappNumber: p.whatsappNumber ?? undefined,
          email: p.email ?? undefined,
          currency: p.currency ?? undefined,
          upiEnabled: p.upiEnabled ?? undefined,
          upiHandle: p.upiHandle ?? undefined,
          upiMobile: p.upiMobile ?? undefined,
          cashEnabled: p.cashEnabled ?? undefined,
          posEnabled: p.posEnabled ?? undefined,
          zomatoEnabled: p.zomatoEnabled ?? undefined,
          swiggyEnabled: p.swiggyEnabled ?? undefined,
          ownWebsiteEnabled: p.ownWebsiteEnabled ?? undefined,
          country: p.country ?? undefined,
          timezone: p.timezone ?? undefined,
          gstEnabled: p.gstEnabled ?? undefined,
          gstin: p.gstin ?? undefined,
          isTaxInclusive: p.isTaxInclusive ?? undefined,
          gstPercentage: p.gstPercentage ?? undefined,
          customTaxName: p.customTaxName ?? undefined,
          customTaxNumber: p.customTaxNumber ?? undefined,
          customTaxPercentage: p.customTaxPercentage ?? undefined,
          fssaiNumber: p.fssaiNumber ?? undefined,
          reviewUrl: p.reviewUrl ?? undefined,
          invoiceFooter: p.invoiceFooter ?? undefined,
          showBranding: p.showBranding ?? undefined,
          maskCustomerPhone: p.maskCustomerPhone ?? undefined,
        };
        this.loading.set(false);
      },
      error: () => { 
        this.loading.set(false);
        this.snackBar.open('Failed to load profile.', 'Close', { duration: 5000 });
      }
    });
  }

  save(): void {
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
}
