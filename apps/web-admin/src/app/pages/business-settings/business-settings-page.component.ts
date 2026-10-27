import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, HostListener, inject, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ToastService } from '../../core/services/toast.service';
import { BusinessApiService } from '../../core/services/business-api.service';
import { TaxSettingsSectionComponent, TaxSettings } from './tax-settings-section.component';
import { PaymentMethodsSectionComponent, PaymentMethodsSettings } from './payment-methods-section.component';
import { PasswordChangeSectionComponent } from './password-change-section.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { IconComponent } from '../../shared/icon.component';

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
  fssaiNumber: string;
  fssaiExpiryDate: string;
  gstExpiryDate: string;
  upiEnabled: boolean;
  upiHandle: string;
  upiMobile: string;
  cashEnabled: boolean;
  posEnabled: boolean;
  orderPaymentFlowMode: string;
  invoiceFooter: string;
  reviewUrl: string;
}

interface SettingsCategory {
  id: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-business-settings-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    TaxSettingsSectionComponent,
    PaymentMethodsSectionComponent,
    PasswordChangeSectionComponent,
    PageHeaderComponent,
    ApiStateComponent,
    RefreshButtonComponent,
    ConfirmDialogComponent,
    IconComponent
  ],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Shop Configuration"
        subtitle="Identity, tax compliance and payment collection for this restaurant, applied to every terminal."
      >
        <span kbHeaderBadge class="kpi-tag kpi-tag--green" *ngIf="!loading()">
          <span class="pulse-indicator" aria-hidden="true"></span>
          Live Sync Ready
        </span>

        <button type="button" class="header-action-btn header-action-btn--primary" [disabled]="saving() || loading()" (click)="saveProfile()" aria-label="Save configuration">
          <svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
            <polyline points="17 21 17 13 7 13 7 21"/>
            <polyline points="7 3 7 8 15 8"/>
          </svg>
          {{ saving() ? 'Saving...' : 'Save changes' }}
        </button>
        <app-refresh-button (refresh)="refreshSettings()" [loading]="loading()" ariaLabel="Reload settings from server" />
      </app-page-header>

      <!-- Stale refresh banner: the form below still holds the last good copy. -->
      <div class="stale-banner" *ngIf="!loading() && loadError() && profile()" role="status" aria-live="polite">
        <span>Couldn't update settings. {{ loadError() }}</span>
        <button type="button" class="btn-retry" (click)="refreshSettings()">Retry</button>
      </div>

      <div class="panel loading" *ngIf="loading()" role="status" aria-busy="true">
        <span class="visually-hidden">Loading settings...</span>
        <div class="skeleton-stack">
          <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4]"></div>
        </div>
      </div>

      <app-api-state
        *ngIf="!loading() && loadError() && !profile()"
        [message]="loadError()"
        (retry)="refreshSettings()"
      ></app-api-state>

      <ng-container *ngIf="viewProfile() as p">
        <div class="settings-layout">
          <!-- Secondary configuration nav: one row per section in this page. -->
          <aside class="settings-nav" aria-label="Configuration categories">
            <p class="settings-nav__heading">Configuration</p>
            <nav class="settings-nav__list" aria-label="Settings sections">
              <a
                *ngFor="let item of categories"
                [href]="'#' + item.id"
                class="settings-nav__link"
                [class.settings-nav__link--active]="activeSection() === item.id"
                [attr.aria-current]="activeSection() === item.id ? 'true' : null"
                (click)="selectSection($event, item.id)">
                <kb-icon [name]="item.icon" [size]="16"></kb-icon>
                <span class="settings-nav__label">{{ item.label }}</span>
              </a>
            </nav>

            <div class="settings-nav__divider" aria-hidden="true"></div>

            <a class="settings-nav__link settings-nav__link--out" routerLink="/business/menu">
              <kb-icon name="menu" [size]="16"></kb-icon>
              <span class="settings-nav__label">Menu Configuration</span>
              <span class="settings-nav__chevron" aria-hidden="true">↗</span>
            </a>
            <p class="settings-nav__note">Menu editing and AI import open in the Menu workspace.</p>
          </aside>

          <div class="settings-main">
            <!-- 1. Restaurant Profile -->
            <section id="profile" class="settings-block" data-settings-section aria-labelledby="profile-section-title">
              <div class="panel settings-section">
                <div class="section-title-wrap">
                  <div class="section-icon">
                    <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                      <polyline points="9 22 9 12 15 12 15 22"/>
                    </svg>
                  </div>
                  <div>
                    <h3 id="profile-section-title">Restaurant profile</h3>
                    <p class="muted">Shown on thermal receipts, tax invoices and the customer-facing menu.</p>
                  </div>
                </div>

                <div class="settings-group">
                  <h4 class="settings-group__title">Basic information</h4>
                  <div class="form-grid">
                    <div class="field">
                      <label for="field-shopName">
                        Restaurant name
                        <span class="required-mark" aria-hidden="true">*</span>
                      </label>
                      <input
                        id="field-shopName"
                        class="field-control"
                        [(ngModel)]="p.shopName"
                        (ngModelChange)="onFieldChange('shopName')"
                        placeholder="Restaurant name"
                        autocomplete="organization"
                        required
                        [attr.aria-invalid]="errors()['shopName'] ? 'true' : null"
                        [attr.aria-describedby]="errors()['shopName'] ? 'err-shopName' : null" />
                      <p class="field-error" id="err-shopName" role="alert" *ngIf="errors()['shopName']">{{ errors()['shopName'] }}</p>
                    </div>

                    <div class="field">
                      <label for="field-email">Business email</label>
                      <input
                        id="field-email"
                        class="field-control"
                        type="email"
                        [(ngModel)]="p.email"
                        (ngModelChange)="onFieldChange('email')"
                        placeholder="billing@restaurant.com"
                        autocomplete="email"
                        [attr.aria-invalid]="errors()['email'] ? 'true' : null"
                        [attr.aria-describedby]="errors()['email'] ? 'err-email' : null" />
                      <p class="field-error" id="err-email" role="alert" *ngIf="errors()['email']">{{ errors()['email'] }}</p>
                    </div>

                    <div class="field">
                      <label for="field-whatsappNumber">WhatsApp number</label>
                      <input
                        id="field-whatsappNumber"
                        class="field-control tabular-num"
                        inputmode="numeric"
                        [ngModel]="p.whatsappNumber"
                        (ngModelChange)="onPhoneChange($event)"
                        placeholder="10-digit mobile number"
                        maxlength="10"
                        autocomplete="tel-national"
                        [attr.aria-invalid]="errors()['whatsappNumber'] ? 'true' : null"
                        [attr.aria-describedby]="errors()['whatsappNumber'] ? 'err-whatsappNumber' : 'hint-whatsappNumber'" />
                      <p class="field-hint" id="hint-whatsappNumber" *ngIf="!errors()['whatsappNumber']">Used for customer updates and account recovery.</p>
                      <p class="field-error" id="err-whatsappNumber" role="alert" *ngIf="errors()['whatsappNumber']">{{ errors()['whatsappNumber'] }}</p>
                    </div>
                  </div>
                </div>

                <div class="settings-group">
                  <h4 class="settings-group__title">Address</h4>
                  <div class="form-grid">
                    <div class="field field--full">
                      <label for="field-shopAddress">Address line</label>
                      <textarea
                        id="field-shopAddress"
                        class="field-control textarea-address"
                        [(ngModel)]="p.shopAddress"
                        (ngModelChange)="onFieldChange('shopAddress')"
                        placeholder="Street, area, landmark"
                        rows="3"
                        aria-describedby="hint-shopAddress"></textarea>
                      <p class="field-hint" id="hint-shopAddress">Printed on every receipt and invoice.</p>
                    </div>
                  </div>
                </div>

                <div class="settings-group">
                  <h4 class="settings-group__title">Customer engagement</h4>
                  <div class="form-grid">
                    <div class="field field--full">
                      <label for="field-reviewUrl">Google review link</label>
                      <input
                        id="field-reviewUrl"
                        class="field-control"
                        type="url"
                        [(ngModel)]="p.reviewUrl"
                        (ngModelChange)="onFieldChange('reviewUrl')"
                        placeholder="https://g.page/r/your-restaurant/review"
                        inputmode="url"
                        [attr.aria-invalid]="errors()['reviewUrl'] ? 'true' : null"
                        [attr.aria-describedby]="errors()['reviewUrl'] ? 'err-reviewUrl' : 'hint-reviewUrl'" />
                      <p class="field-hint" id="hint-reviewUrl" *ngIf="!errors()['reviewUrl']">Customers are invited to leave a review from the receipt. Leave blank to hide the prompt.</p>
                      <p class="field-error" id="err-reviewUrl" role="alert" *ngIf="errors()['reviewUrl']">{{ errors()['reviewUrl'] }}</p>
                    </div>
                  </div>
                </div>

                <div class="settings-group">
                  <h4 class="settings-group__title">Branding &amp; invoice</h4>
                  <div class="branding-grid">
                    <div class="branding-fields">
                      <div class="logo-uploader-card">
                        <div class="logo-preview-box">
                          <img *ngIf="p.logoUrl" [src]="p.logoUrl" alt="Restaurant logo" class="logo-preview-img" />
                          <div *ngIf="!p.logoUrl" class="logo-empty-box">
                            <svg aria-hidden="true" viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                              <circle cx="8.5" cy="8.5" r="1.5"/>
                              <polyline points="21 15 16 10 5 21"/>
                            </svg>
                            <span>No logo</span>
                          </div>
                        </div>
                        <div class="logo-meta">
                          <h5>Restaurant logo</h5>
                          <p class="muted">PNG, JPG or WebP, up to 2MB. Used on bills, receipts and the digital menu.</p>
                          <div class="logo-actions">
                            <label class="ghost-btn file-picker-label">
                              <input class="logo-file-input" type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" (change)="onLogoFileSelected($event)" aria-label="Choose restaurant logo file" />
                              <svg aria-hidden="true" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                                <polyline points="17 8 12 3 7 8"/>
                                <line x1="12" y1="3" x2="12" y2="15"/>
                              </svg>
                              {{ logoBusy() ? 'Uploading...' : (p.logoUrl ? 'Change logo' : 'Upload logo') }}
                            </label>
                            <button *ngIf="p.logoUrl" type="button" class="ghost-btn danger-btn" [disabled]="logoBusy()" (click)="removeLogo()">
                              <svg aria-hidden="true" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                                <line x1="18" y1="6" x2="6" y2="18"/>
                                <line x1="6" y1="6" x2="18" y2="18"/>
                              </svg>
                              Remove logo
                            </button>
                          </div>
                        </div>
                      </div>

                      <div class="field">
                        <label for="field-invoiceFooter">Receipt footer note</label>
                        <input
                          id="field-invoiceFooter"
                          class="field-control"
                          [(ngModel)]="p.invoiceFooter"
                          (ngModelChange)="onFieldChange('invoiceFooter')"
                          placeholder="Thank you for dining with us"
                          maxlength="120"
                          aria-describedby="hint-invoiceFooter" />
                        <p class="field-hint" id="hint-invoiceFooter">Printed at the bottom of customer receipts.</p>
                      </div>
                    </div>

                    <aside class="receipt-preview" aria-label="Customer receipt preview">
                      <p class="receipt-preview__title">Receipt preview</p>
                      <div class="receipt-preview__paper">
                        <img *ngIf="p.logoUrl" [src]="p.logoUrl" alt="" class="receipt-preview__logo" />
                        <p class="receipt-preview__shop">{{ p.shopName || 'Restaurant name' }}</p>
                        <p class="receipt-preview__meta" *ngIf="p.shopAddress">{{ p.shopAddress }}</p>
                        <p class="receipt-preview__meta" *ngIf="p.gstEnabled && p.gstin">GSTIN {{ p.gstin }}</p>
                        <p class="receipt-preview__meta" *ngIf="p.fssaiNumber">FSSAI {{ p.fssaiNumber }}</p>
                        <div class="receipt-preview__rule" aria-hidden="true"></div>
                        <p class="receipt-preview__placeholder">Bill items appear here</p>
                        <div class="receipt-preview__rule" aria-hidden="true"></div>
                        <p class="receipt-preview__footer">{{ p.invoiceFooter || 'Receipt footer note' }}</p>
                      </div>
                      <p class="receipt-preview__hint">Layout preview only — amounts come from the saved bill.</p>
                    </aside>
                  </div>
                </div>
              </div>
            </section>

            <!-- 2. Tax & Compliance -->
            <section id="tax" class="settings-block" data-settings-section aria-label="Tax and compliance">
              <app-tax-settings-section
                [tax]="{
                  gstEnabled: p.gstEnabled,
                  gstin: p.gstin,
                  gstPercentage: p.gstPercentage,
                  customTaxName: p.customTaxName,
                  customTaxPercentage: p.customTaxPercentage,
                  fssaiNumber: p.fssaiNumber,
                  fssaiExpiryDate: p.fssaiExpiryDate,
                  gstExpiryDate: p.gstExpiryDate
                }"
                (taxChange)="applyTaxSettings($event)"
              />
            </section>

            <!-- 3. Payments & Collection -->
            <section id="payments" class="settings-block" data-settings-section aria-label="Payments and collection">
              <app-payment-methods-section
                [settings]="{ cashEnabled: p.cashEnabled, upiEnabled: p.upiEnabled, upiHandle: p.upiHandle, upiMobile: p.upiMobile, posEnabled: p.posEnabled, orderPaymentFlowMode: p.orderPaymentFlowMode, shopName: p.shopName }"
                (settingsChange)="applyPaymentSettings($event)"
              />
            </section>

            <!-- Existing owner-password flow, unchanged. Device app-lock and printer
                 bindings are terminal-local in the Android app, so they are not
                 represented here as if the server owned them. -->
            <app-password-change-section />

            <div class="sticky-save-dock" *ngIf="dirty()">
              <div class="save-dock-inner">
                <div class="dock-info">
                  <span class="dock-unsaved-badge">Unsaved changes</span>
                  <span class="dock-hint">Changes reach POS registers after you save.</span>
                </div>
                <div class="dock-actions">
                  <button class="ghost-btn" type="button" [disabled]="saving()" (click)="discardChanges()" aria-label="Discard changes">
                    Discard
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </ng-container>

      <app-confirm-dialog
        *ngIf="confirmRemoveLogo()"
        title="Remove logo"
        message="Are you sure you want to remove the business logo? This action cannot be undone."
        confirmLabel="Remove"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="executeRemoveLogo()"
        (cancelled)="cancelRemoveLogo()"
      ></app-confirm-dialog>
    </div>
  `,
  styles: [`
    .tabular-num { font-variant-numeric: tabular-nums; }

    /* ── Two-column workspace: configuration nav + section stack ── */
    .settings-layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: var(--kb-space-4);
      align-items: start;
    }

    .settings-nav {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-1);
      padding: var(--kb-space-3);
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl);
      box-shadow: var(--shadow-xs);
    }
    .settings-nav__heading {
      margin: 0;
      padding: 0 var(--kb-space-2);
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--kb-color-muted-foreground);
    }
    .settings-nav__list { display: flex; flex-direction: column; gap: var(--kb-space-1); }
    .settings-nav__link {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
      padding: var(--kb-space-2);
      border-radius: var(--kb-radius-lg);
      color: var(--kb-color-foreground-2);
      font-size: var(--kb-text-sm);
      font-weight: 500;
      text-decoration: none;
      transition: background-color var(--kb-duration-fast) var(--ease-out),
                  color var(--kb-duration-fast) var(--ease-out);
    }
    .settings-nav__link:hover { background: var(--kb-color-surface-2); color: var(--kb-color-foreground); }
    .settings-nav__link:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: 2px; }
    .settings-nav__link--active {
      background: var(--kb-color-primary-soft);
      color: var(--kb-color-primary);
      font-weight: 600;
    }
    .settings-nav__link--out { color: var(--kb-color-link); }
    .settings-nav__chevron { margin-left: auto; line-height: 1; }
    .settings-nav__divider {
      height: 1px;
      margin: var(--kb-space-1) 0;
      background: var(--kb-color-border);
    }
    .settings-nav__note {
      margin: 0;
      padding: var(--kb-space-1) var(--kb-space-2) 0;
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
    }

    @media (min-width: 1025px) {
      .settings-layout { grid-template-columns: 232px minmax(0, 1fr); gap: var(--kb-space-6); }
      .settings-nav { position: sticky; top: var(--kb-space-4); }
    }

    /* Tablet and below: the secondary rail becomes a scrollable chip row so the
       form keeps the full width. */
    @media (max-width: 1024px) {
      .settings-nav {
        flex-direction: row;
        align-items: center;
        overflow-x: auto;
        padding: var(--kb-space-2);
      }
      .settings-nav__heading,
      .settings-nav__note,
      .settings-nav__divider { display: none; }
      .settings-nav__list { flex-direction: row; }
      .settings-nav__link { white-space: nowrap; }
    }

    .settings-block { scroll-margin-top: var(--kb-space-4); }
    .settings-main { min-width: 0; }

    /* ── Grouped form blocks inside a panel ── */
    .settings-group { display: grid; gap: var(--kb-space-3); }
    .settings-group + .settings-group { margin-top: var(--kb-space-6); }
    .settings-group__title { margin: 0; font-size: var(--kb-text-md); font-weight: 600; }
    .branding-fields { display: grid; gap: var(--kb-space-4); }

    .required-mark { color: var(--kb-color-error); }
    .field-hint {
      margin: 0;
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
    }
    .field-error {
      margin: 0;
      font-size: var(--kb-text-xs);
      color: var(--kb-color-error);
    }

    /* ── Logo card ── */
    .logo-uploader-card {
      display: flex;
      gap: var(--kb-space-4);
      align-items: center;
      padding: var(--kb-space-4);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
      flex-wrap: wrap;
    }
    .logo-preview-box {
      width: 80px;
      height: 80px;
      border-radius: var(--kb-radius-md);
      overflow: hidden;
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .logo-preview-img { width: 100%; height: 100%; object-fit: contain; }
    .logo-empty-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: var(--kb-space-1);
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-2xs);
    }
    .logo-meta { flex: 1; min-width: 220px; }
    .logo-meta h5 { margin: 0 0 var(--kb-space-1); font-size: var(--kb-text-lg); font-weight: 600; }
    .logo-meta p { margin: 0 0 var(--kb-space-2); font-size: var(--kb-text-sm); }
    .logo-actions { display: flex; gap: var(--kb-space-2); align-items: center; flex-wrap: wrap; }
    .file-picker-label { cursor: pointer; }
    .logo-file-input { display: none; }
    .danger-btn { color: var(--kb-color-error); border-color: var(--kb-color-border-strong); }
    .danger-btn:hover { background: var(--kb-color-danger-soft); }

    /* ── Receipt preview ── */
    .branding-grid {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: var(--kb-space-4);
      align-items: start;
    }
    @media (min-width: 1100px) {
      .branding-grid { grid-template-columns: minmax(0, 1fr) 260px; }
    }
    .receipt-preview {
      padding: var(--kb-space-3);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
    }
    .receipt-preview__title {
      margin: 0 0 var(--kb-space-2);
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--kb-color-muted-foreground);
    }
    .receipt-preview__paper {
      display: grid;
      gap: var(--kb-space-1);
      padding: var(--kb-space-3);
      background: var(--kb-color-surface);
      border: 1px dashed var(--kb-color-border);
      border-radius: var(--kb-radius-md);
      text-align: center;
    }
    .receipt-preview__logo {
      width: 48px;
      height: 48px;
      object-fit: contain;
      margin-inline: auto;
      margin-bottom: var(--kb-space-1);
    }
    .receipt-preview__shop {
      margin: 0;
      font-size: var(--kb-text-sm);
      font-weight: 700;
      color: var(--kb-color-foreground);
      overflow-wrap: anywhere;
    }
    .receipt-preview__meta {
      margin: 0;
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
      overflow-wrap: anywhere;
    }
    .receipt-preview__rule { border-top: 1px dashed var(--kb-color-border); margin: var(--kb-space-1) 0; }
    .receipt-preview__placeholder { margin: 0; font-size: var(--kb-text-xs); color: var(--kb-color-muted-foreground); }
    .receipt-preview__footer { margin: 0; font-size: var(--kb-text-xs); font-weight: 600; color: var(--kb-color-foreground-2); }
    .receipt-preview__hint {
      margin: var(--kb-space-2) 0 0;
      font-size: var(--kb-text-2xs);
      color: var(--kb-color-muted-foreground);
    }

    /* ── Unsaved changes dock ── */
    .sticky-save-dock {
      position: sticky;
      bottom: var(--kb-space-4);
      z-index: var(--kb-z-dock);
      margin: var(--kb-space-5) 0;
      padding: var(--kb-space-3) var(--kb-space-5);
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-primary);
      border-radius: var(--kb-radius-lg);
      box-shadow: var(--shadow-md);
      animation: slideUpDock var(--kb-duration-base) var(--ease-out);
    }
    @keyframes slideUpDock {
      from { transform: translateY(12px); opacity: 0; }
      to { transform: translateY(0); opacity: 1; }
    }
    .save-dock-inner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--kb-space-3);
      flex-wrap: wrap;
    }
    .dock-info { display: flex; align-items: center; gap: var(--kb-space-3); flex-wrap: wrap; }
    .dock-unsaved-badge {
      display: inline-flex;
      align-items: center;
      padding: var(--kb-space-1) var(--kb-space-2);
      border-radius: var(--kb-radius-full);
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      background: var(--kb-color-warning-soft);
      color: var(--kb-color-warning);
    }
    .dock-actions { display: flex; align-items: center; gap: var(--kb-space-2); }
    .dock-hint { font-size: var(--kb-text-sm); color: var(--kb-color-muted-foreground); }
    @media (max-width: 640px) {
      .sticky-save-dock { bottom: calc(5rem + env(safe-area-inset-bottom)); }
    }
  `]
})
export class BusinessSettingsPageComponent implements OnDestroy {
  private readonly toast = inject(ToastService);
  private readonly businessApi = inject(BusinessApiService);

  loading = signal(true);
  loadError = signal('');
  saving = signal(false);
  logoBusy = signal(false);
  confirmRemoveLogo = signal(false);
  profile = signal<RestaurantProfile | null>(null);
  private readonly originalProfile = signal<RestaurantProfile | null>(null);

  /** One entry per section rendered in the main column, in scroll order. */
  readonly categories: readonly SettingsCategory[] = [
    { id: 'profile', label: 'Restaurant Profile', icon: 'profile' },
    { id: 'tax', label: 'Tax & Compliance', icon: 'reports' },
    { id: 'payments', label: 'Payments & Collection', icon: 'payments' }
  ];
  readonly activeSection = signal<string>('profile');
  readonly errors = signal<Record<string, string>>({});

  private scrollFrame: number | null = null;

  readonly dirty = computed(() => {
    const current = this.profile();
    const original = this.originalProfile();
    if (!current || !original) return false;
    return JSON.stringify(current) !== JSON.stringify(original);
  });

  canDeactivate(): boolean {
    return !this.dirty();
  }

  /**
   * The form is withheld while a reload is in flight so the skeleton above takes
   * over: a manual refresh visibly reloads the page instead of leaving old values
   * on screen behind a spinning icon.
   */
  readonly viewProfile = computed(() => (this.loading() ? null : this.profile()));

  constructor() { this.load(); }

  @HostListener('window:scroll')
  onWindowScroll(): void {
    if (this.scrollFrame !== null) return;
    this.scrollFrame = window.requestAnimationFrame(() => {
      this.scrollFrame = null;
      this.syncActiveSection();
    });
  }

  ngOnDestroy(): void {
    if (this.scrollFrame !== null) window.cancelAnimationFrame(this.scrollFrame);
  }

  /**
   * A section becomes current once its top passes the upper third of the
   * viewport, so the highlighted row matches the panel the reader is looking at
   * rather than the one that merely happens to be at the very top.
   */
  private syncActiveSection(): void {
    const sections = Array.from(document.querySelectorAll<HTMLElement>('[data-settings-section]'));
    if (!sections.length) return;
    const marker = Math.max(96, window.innerHeight * 0.3);
    let current = sections[0].id;
    for (const section of sections) {
      if (section.getBoundingClientRect().top <= marker) current = section.id;
    }
    this.activeSection.set(current);
  }

  selectSection(event: Event, id: string): void {
    event.preventDefault();
    const section = document.getElementById(id);
    if (!section) return;
    this.activeSection.set(id);
    const reduceMotion = typeof window.matchMedia === 'function'
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    section.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
  }

  refreshSettings(): void {
    if (this.loading()) return;
    if (this.dirty() && !window.confirm('Reload settings from the server? Unsaved changes will be lost.')) return;
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.businessApi.getProfile().subscribe({
      next: (p) => {
        const loaded: RestaurantProfile = p ? {
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
          fssaiNumber: p.fssaiNumber ?? '',
          fssaiExpiryDate: p.fssaiExpiryDate ?? '',
          gstExpiryDate: p.gstExpiryDate ?? '',
          upiEnabled: p.upiEnabled ?? false,
          upiHandle: p.upiHandle ?? '',
          upiMobile: p.upiMobile ?? '',
          cashEnabled: p.cashEnabled ?? true,
          posEnabled: p.posEnabled ?? false,
          orderPaymentFlowMode: p.orderPaymentFlowMode ?? 'pay_before_food',
          invoiceFooter: p.invoiceFooter ?? '',
          reviewUrl: p.reviewUrl ?? ''
        } : this.emptyProfile();
        this.profile.set(loaded);
        this.originalProfile.set({ ...loaded });
        this.errors.set({});
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

  onFieldChange(field?: string): void {
    const p = this.profile();
    if (!p) return;
    this.profile.set({ ...p });
    if (field) this.clearError(field);
  }

  /** Phone fields keep the 10-digit national number, so a pasted "+91 98765 43210" still validates. */
  onPhoneChange(value: string): void {
    const p = this.profile();
    if (!p) return;
    let digits = (value || '').replace(/\D/g, '');
    if (digits.length === 12 && digits.startsWith('91')) digits = digits.slice(2);
    else if (digits.length === 11 && digits.startsWith('0')) digits = digits.slice(1);
    this.profile.set({ ...p, whatsappNumber: digits.slice(0, 10) });
    this.clearError('whatsappNumber');
  }

  discardChanges(): void {
    const orig = this.originalProfile();
    if (orig) {
      this.profile.set({ ...orig });
      this.errors.set({});
      this.toast.info('Unsaved changes reverted to server state.');
    }
  }

  saveProfile(): void {
    const p = this.profile();
    if (!p) return;

    const errors = this.validate(p);
    this.errors.set(errors);
    const firstInvalid = Object.keys(errors)[0];
    if (firstInvalid) {
      this.toast.show('Fix the highlighted fields before saving.', 'error');
      document.getElementById(`field-${firstInvalid}`)?.scrollIntoView({ block: 'center' });
      return;
    }

    this.saving.set(true);
    this.businessApi.updateProfile(p).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.toast.show('Restaurant settings saved successfully.', 'success');
        if (saved) {
          const updated = { ...p, ...saved };
          this.profile.set(updated);
          this.originalProfile.set({ ...updated });
        }
      },
      error: (err) => {
        this.saving.set(false);
        this.toast.show(err?.error?.message ?? 'Failed to save settings. Try again.', 'error');
      }
    });
  }

  onLogoFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      this.toast.show('Logo file size must be less than 2MB.', 'error');
      input.value = '';
      return;
    }
    this.logoBusy.set(true);
    this.businessApi.uploadLogo(file).subscribe({
      next: (res) => {
        const logoUrl = res?.url || res?.logoUrl || '';
        const p = this.profile();
        if (p) this.profile.set({ ...p, logoUrl });
        this.logoBusy.set(false);
        this.toast.show('Logo uploaded successfully.', 'success');
        input.value = '';
      },
      error: (err) => {
        this.logoBusy.set(false);
        this.toast.show(err?.error?.message ?? 'Failed to upload logo. Try again.', 'error');
        input.value = '';
      }
    });
  }

  removeLogo(): void {
    this.confirmRemoveLogo.set(true);
  }

  executeRemoveLogo(): void {
    this.confirmRemoveLogo.set(false);
    this.logoBusy.set(true);
    this.businessApi.deleteLogo().subscribe({
      next: () => {
        const p = this.profile();
        if (p) this.profile.set({ ...p, logoUrl: '' });
        this.logoBusy.set(false);
        this.toast.show('Logo removed.', 'success');
      },
      error: (err) => {
        this.logoBusy.set(false);
        this.toast.show(err?.error?.message ?? 'Failed to remove logo.', 'error');
      }
    });
  }

  cancelRemoveLogo(): void {
    this.confirmRemoveLogo.set(false);
  }

  private clearError(field: string): void {
    const current = this.errors();
    if (!current[field]) return;
    const next = { ...current };
    delete next[field];
    this.errors.set(next);
  }

  private validate(p: RestaurantProfile): Record<string, string> {
    const errors: Record<string, string> = {};

    if (!p.shopName.trim()) errors['shopName'] = 'Restaurant name is required.';

    const email = p.email.trim();
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
      errors['email'] = 'Enter a valid email address, for example billing@restaurant.com.';
    }

    const phone = p.whatsappNumber.trim();
    if (phone && !/^\d{10}$/.test(phone)) {
      errors['whatsappNumber'] = 'Enter a 10-digit mobile number.';
    }

    const reviewUrl = p.reviewUrl.trim();
    if (reviewUrl && !this.isWebUrl(reviewUrl)) {
      errors['reviewUrl'] = 'Enter a full link starting with https://.';
    }

    return errors;
  }

  private isWebUrl(value: string): boolean {
    try {
      const url = new URL(value);
      return url.protocol === 'https:' || url.protocol === 'http:';
    } catch {
      return false;
    }
  }

  private emptyProfile(): RestaurantProfile {
    return {
      shopName: '', shopAddress: '', whatsappNumber: '', email: '',
      logoUrl: '', gstEnabled: false, gstin: '', gstPercentage: 5, customTaxName: '',
      customTaxPercentage: 0, fssaiNumber: '', fssaiExpiryDate: '', gstExpiryDate: '',
      upiEnabled: false, upiHandle: '', upiMobile: '', cashEnabled: true,
      posEnabled: false, orderPaymentFlowMode: 'pay_before_food',
      invoiceFooter: '', reviewUrl: ''
    };
  }
}
