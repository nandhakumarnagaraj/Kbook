import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessProfile, UpdateBusinessProfileRequest } from '../../core/models/api.models';

@Component({
  selector: 'app-restaurant-settings-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Business Settings</h2>
        <p class="muted">Manage your restaurant profile, payments, and tax configuration.</p>
      </section>

      <div *ngIf="saving" class="saving-bar">Saving...</div>

      <!-- PROFILE SECTION -->
      <section class="panel soft-section">
        <div class="section-head">
          <h3>Profile</h3>
          <p class="muted">Shop name, address, and contact details.</p>
        </div>

        <div class="field">
          <label for="shopName">Shop Name</label>
          <input id="shopName" class="field-control" type="text" [(ngModel)]="form.shopName" />
        </div>

        <div class="field">
          <label for="shopAddress">Address</label>
          <textarea id="shopAddress" class="field-control" rows="2" [(ngModel)]="form.shopAddress"></textarea>
        </div>

        <div class="field-row">
          <div class="field">
            <label for="whatsappNumber">WhatsApp Number</label>
            <input id="whatsappNumber" class="field-control" type="text" [(ngModel)]="form.whatsappNumber" />
          </div>
          <div class="field">
            <label for="email">Email</label>
            <input id="email" class="field-control" type="email" [(ngModel)]="form.email" />
          </div>
        </div>

        <div class="field">
          <label for="reviewUrl">Review / Google URL</label>
          <input id="reviewUrl" class="field-control" type="text" [(ngModel)]="form.reviewUrl" placeholder="https://..." />
        </div>

        <div class="field">
          <label for="invoiceFooter">Invoice Footer</label>
          <textarea id="invoiceFooter" class="field-control" rows="2" [(ngModel)]="form.invoiceFooter" placeholder="Thank you, visit again!"></textarea>
        </div>

        <div class="field-row">
          <label class="toggle-row">
            <span>Show KhanaBook Branding</span>
            <input type="checkbox" [(ngModel)]="form.showBranding" />
          </label>
          <label class="toggle-row">
            <span>Mask Customer Phone on Receipt</span>
            <input type="checkbox" [(ngModel)]="form.maskCustomerPhone" />
          </label>
        </div>
      </section>

      <!-- PAYMENT SECTION -->
      <section class="panel soft-section">
        <div class="section-head">
          <h3>Payments</h3>
          <p class="muted">Currency, UPI, and accepted payment methods.</p>
        </div>

        <div class="field-row">
          <div class="field">
            <label for="currency">Currency</label>
            <select id="currency" class="field-select" [(ngModel)]="form.currency">
              <option value="INR">INR (₹)</option>
              <option value="USD">USD ($)</option>
              <option value="AED">AED (د.إ)</option>
            </select>
          </div>
          <div class="field">
            <label for="upiHandle">UPI Handle / ID</label>
            <input id="upiHandle" class="field-control" type="text" [(ngModel)]="form.upiHandle" placeholder="example@upi" />
          </div>
        </div>

        <div class="field">
          <label for="upiMobile">UPI Mobile Number</label>
          <input id="upiMobile" class="field-control" type="text" [(ngModel)]="form.upiMobile" />
        </div>

        <div class="section-head" style="margin-top: 1rem;">
          <h3>Accepted Payment Methods</h3>
        </div>
        <div class="field-row">
          <label class="toggle-row">
            <span>Cash</span>
            <input type="checkbox" [(ngModel)]="form.cashEnabled" />
          </label>
          <label class="toggle-row">
            <span>UPI</span>
            <input type="checkbox" [(ngModel)]="form.upiEnabled" />
          </label>
          <label class="toggle-row">
            <span>POS / Card</span>
            <input type="checkbox" [(ngModel)]="form.posEnabled" />
          </label>
        </div>
        <div class="field-row">
          <label class="toggle-row">
            <span>Zomato</span>
            <input type="checkbox" [(ngModel)]="form.zomatoEnabled" />
          </label>
          <label class="toggle-row">
            <span>Swiggy</span>
            <input type="checkbox" [(ngModel)]="form.swiggyEnabled" />
          </label>
          <label class="toggle-row">
            <span>Own Website</span>
            <input type="checkbox" [(ngModel)]="form.ownWebsiteEnabled" />
          </label>
        </div>
      </section>

      <!-- TAX SECTION -->
      <section class="panel soft-section">
        <div class="section-head">
          <h3>Tax & Compliance</h3>
          <p class="muted">GST, custom tax, FSSAI, and regional settings.</p>
        </div>

        <div class="field-row">
          <div class="field">
            <label for="country">Country</label>
            <input id="country" class="field-control" type="text" [(ngModel)]="form.country" />
          </div>
          <div class="field">
            <label for="timezone">Timezone</label>
            <select id="timezone" class="field-select" [(ngModel)]="form.timezone">
              <option value="Asia/Kolkata">Asia/Kolkata (IST)</option>
              <option value="Asia/Dubai">Asia/Dubai</option>
              <option value="UTC">UTC</option>
            </select>
          </div>
        </div>

        <div class="field-row">
          <label class="toggle-row">
            <span>Enable GST</span>
            <input type="checkbox" [(ngModel)]="form.gstEnabled" />
          </label>
          <label class="toggle-row">
            <span>Tax Inclusive Pricing</span>
            <input type="checkbox" [(ngModel)]="form.isTaxInclusive" />
          </label>
        </div>

        <div class="field-row">
          <div class="field">
            <label for="gstin">GSTIN</label>
            <input id="gstin" class="field-control" type="text" [(ngModel)]="form.gstin" placeholder="15-character GSTIN" />
          </div>
          <div class="field">
            <label for="gstPercentage">GST %</label>
            <input id="gstPercentage" class="field-control" type="number" step="0.01" [(ngModel)]="form.gstPercentage" />
          </div>
        </div>

        <div class="field-row">
          <div class="field">
            <label for="customTaxName">Custom Tax Name</label>
            <input id="customTaxName" class="field-control" type="text" [(ngModel)]="form.customTaxName" />
          </div>
          <div class="field">
            <label for="customTaxNumber">Custom Tax Number</label>
            <input id="customTaxNumber" class="field-control" type="text" [(ngModel)]="form.customTaxNumber" />
          </div>
          <div class="field">
            <label for="customTaxPercentage">Custom Tax %</label>
            <input id="customTaxPercentage" class="field-control" type="number" step="0.01" [(ngModel)]="form.customTaxPercentage" />
          </div>
        </div>

        <div class="field">
          <label for="fssaiNumber">FSSAI License Number</label>
          <input id="fssaiNumber" class="field-control" type="text" [(ngModel)]="form.fssaiNumber" />
        </div>
      </section>

      <div class="sticky-save">
        <button class="primary-btn" (click)="save()" [disabled]="saving">
          {{ saving ? 'Saving...' : 'Save Settings' }}
        </button>
        <span class="save-msg" *ngIf="saveSuccess">Saved successfully!</span>
        <span class="save-msg error" *ngIf="saveError">{{ saveError }}</span>
      </div>
    </div>
  `,
  styles: [`
    .soft-section { padding: 1.5rem; }
    .field-row { display: flex; gap: 0.85rem; flex-wrap: wrap; }
    .field-row .field { flex: 1; min-width: 180px; }
    .field { display: flex; flex-direction: column; gap: 0.35rem; margin-bottom: 0.85rem; }
    .field label { font-size: 0.82rem; font-weight: 700; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
    .field-control, .field-select {
      padding: 0.6rem 0.75rem; border-radius: 10px; border: 1px solid var(--line);
      background: var(--bg); color: var(--ink); font-size: 0.92rem;
    }
    .field-select { cursor: pointer; }
    .toggle-row {
      display: flex; align-items: center; gap: 0.6rem; padding: 0.6rem 1rem;
      border-radius: 10px; background: var(--bg); border: 1px solid var(--line);
      cursor: pointer; font-size: 0.9rem; font-weight: 600; white-space: nowrap;
    }
    .toggle-row input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--brand); }
    .sticky-save {
      position: sticky; bottom: 1rem; display: flex; align-items: center; gap: 1rem;
      padding: 1rem 1.5rem; background: var(--panel); border: 1px solid var(--line);
      border-radius: 16px; box-shadow: var(--shadow-soft); margin-top: 1rem;
    }
    .primary-btn {
      background: var(--brand); color: #fff; border: none; padding: 0.65rem 1.5rem;
      border-radius: 999px; font-weight: 700; font-size: 0.92rem; cursor: pointer;
      transition: background 0.15s;
    }
    .primary-btn:hover { background: var(--brand-deep); }
    .primary-btn:disabled { opacity: 0.6; cursor: not-allowed; }
    .save-msg { font-size: 0.85rem; color: var(--accent); font-weight: 600; }
    .save-msg.error { color: var(--danger); }
    .saving-bar {
      text-align: center; padding: 0.5rem; background: rgba(181,106,45,0.1);
      border-radius: 10px; font-weight: 600; font-size: 0.85rem; margin-bottom: 0.5rem;
    }
  `]
})
export class RestaurantSettingsPageComponent implements OnInit {
  private readonly api = inject(BusinessApiService);

  form: UpdateBusinessProfileRequest = {};
  saving = false;
  saveSuccess = false;
  saveError: string | null = null;

  ngOnInit(): void {
    this.api.getProfile().subscribe({
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
      },
      error: () => { this.saveError = 'Failed to load profile.'; }
    });
  }

  save(): void {
    this.saving = true;
    this.saveSuccess = false;
    this.saveError = null;

    this.api.updateProfile(this.form).subscribe({
      next: () => {
        this.saving = false;
        this.saveSuccess = true;
        setTimeout(() => { this.saveSuccess = false; }, 3000);
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err?.error?.error || 'Failed to save. Please try again.';
      }
    });
  }
}
