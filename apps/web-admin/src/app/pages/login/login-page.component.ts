import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, OnDestroy, NgZone, inject, signal, ChangeDetectorRef, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService, LAST_LOGIN_ID_STORAGE_KEY } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

declare const google: any;

type ForgotStep = 'none' | 'phone' | 'otp' | 'password' | 'success';

export function isPasswordResetSubmissionValid(newPassword: string, confirmPassword: string): boolean {
  return newPassword.length >= 6
    && confirmPassword.length >= 6
    && newPassword === confirmPassword;
}

@Component({
  selector: 'app-login-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="auth-shell">
      <!-- Left Brand Panel (Desktop Showcase) -->
      <aside class="auth-brand" aria-label="KhanaBook Restaurant Platform Overview">
        <div class="brand-top">
          <a href="https://khanabook.in" class="brand-mark" title="Back to KhanaBook website" aria-label="KhanaBook - Back to Homepage">
            <div class="brand-logo">
              <img src="/khanabook_logo.png" alt="KhanaBook" />
            </div>
            <span class="brand-title">KhanaBook</span>
          </a>

          <span class="brand-context-pill">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="var(--kb-color-primary)" aria-hidden="true"><path d="m12 2.5 2.85 5.78 6.38.93-4.62 4.5 1.09 6.36L12 17.06l-5.7 3 1.09-6.36-4.62-4.5 6.38-.93L12 2.5Z"/></svg>
            Trusted by 250+ Restaurants
          </span>
        </div>

        <div class="brand-body">
          <div class="brand-copy">
            <h1 class="brand-hero-title">
              Run your restaurant<br/>with <span class="brand-hero-accent">confidence.</span>
            </h1>
            <p class="brand-hero-sub">
              One unified workspace for menu, orders, staff, payments, and real-time operations.
            </p>
          </div>

          <!-- Interactive Live Showcase Widgets -->
          <div class="brand-showcase" aria-hidden="true">
            <!-- Top Live POS Order Card -->
            <div class="pos-preview-card">
              <div class="pos-preview-header">
                <div class="pos-preview-badge">
                  <span class="live-dot"></span>
                  <span>Live POS • Table 04</span>
                </div>
                <span class="pos-order-id">#KB-1048</span>
              </div>
              <div class="pos-preview-items">
                <div class="pos-item-row">
                  <span class="pos-item-name"><span class="pos-item-qty">2×</span> Paneer Butter Masala</span>
                  <span class="pos-item-price">₹560</span>
                </div>
                <div class="pos-item-row">
                  <span class="pos-item-name"><span class="pos-item-qty">4×</span> Butter Garlic Naan</span>
                  <span class="pos-item-price">₹160</span>
                </div>
                <div class="pos-item-row">
                  <span class="pos-item-name"><span class="pos-item-qty">2×</span> Mango Lassi</span>
                  <span class="pos-item-price">₹140</span>
                </div>
              </div>
              <div class="pos-preview-total">
                <span class="pos-total-label">Total Amount</span>
                <span class="pos-total-val">₹860.00</span>
              </div>
              <div class="pos-preview-footer">
                <span class="pos-chip pos-chip--success">✓ KOT Printed</span>
                <span class="pos-chip">UPI / Cash</span>
                <span class="pos-chip pos-chip--sync">Cloud Synced</span>
              </div>
            </div>

            <!-- Side Floating Metric Widget -->
            <div class="metric-preview-card">
              <div class="metric-preview-top">
                <span class="metric-label">Today's Revenue</span>
                <span class="metric-trend">+18.5%</span>
              </div>
              <div class="metric-val">₹48,250<span class="metric-sub">.00</span></div>
              <div class="metric-sparkline">
                <svg viewBox="0 0 160 36" fill="none" class="sparkline-svg">
                  <path d="M0 28 Q 20 25, 40 18 T 80 14 T 120 8 T 160 4" stroke="var(--kb-color-primary)" stroke-width="2.5" stroke-linecap="round"/>
                  <path d="M0 28 Q 20 25, 40 18 T 80 14 T 120 8 T 160 4 L 160 36 L 0 36 Z" fill="url(#sparkGrad)" opacity="0.15"/>
                  <defs>
                    <linearGradient id="sparkGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stop-color="var(--kb-color-primary)"/>
                      <stop offset="100%" stop-color="var(--kb-color-primary)" stop-opacity="0"/>
                    </linearGradient>
                  </defs>
                </svg>
              </div>
              <div class="metric-footnote">
                <span>142 Orders</span>
                <span>•</span>
                <span>Avg ₹340</span>
              </div>
            </div>
          </div>

          <!-- Product benefits in modern chips -->
          <div class="brand-feature-stack">
            <div class="brand-feature-card">
              <div class="feature-icon-badge">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="10" cy="7" r="4"/>
                  <path d="M20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
                </svg>
              </div>
              <div class="feature-text">
                <strong class="feature-heading">Customer CRM</strong>
                <span class="feature-sub">Repeat loyalty</span>
              </div>
            </div>

            <div class="brand-feature-card">
              <div class="feature-icon-badge">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M3 3v18h18"/>
                  <path d="m7 14 4-4 4 3 6-7"/>
                  <path d="M16 6h5v5"/>
                </svg>
              </div>
              <div class="feature-text">
                <strong class="feature-heading">Real-Time POS</strong>
                <span class="feature-sub">Instant sync</span>
              </div>
            </div>

            <div class="brand-feature-card">
              <div class="feature-icon-badge">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/>
                  <path d="m9 12 2 2 4-4"/>
                </svg>
              </div>
              <div class="feature-text">
                <strong class="feature-heading">Secure Data</strong>
                <span class="feature-sub">End-to-end safe</span>
              </div>
            </div>
          </div>
        </div>

        <div class="brand-foot-row">
          <span>&copy; {{ year }} KhanaBook Platform.</span>
        </div>
      </aside>

      <!-- Right Form Panel -->
      <main class="auth-main">
        <div class="auth-topbar">
          <div class="mobile-brand-container">
            <a href="https://khanabook.in" class="mobile-brand" title="Back to KhanaBook website" aria-label="KhanaBook - Back to Homepage">
              <div class="brand-logo brand-logo--sm">
                <img src="/khanabook_logo.png" alt="KhanaBook" />
              </div>
              <div class="mobile-brand-meta">
                <span class="brand-title brand-title--dark">KhanaBook</span>
                <span class="brand-subtitle">Restaurant Owner Workspace</span>
              </div>
            </a>
          </div>
        </div>

        <div class="auth-card">
          <div *ngIf="loginSuccessMessage()" class="alert-box success" role="status">{{ loginSuccessMessage() }}</div>

          <!-- Sign-In Form State -->
          <ng-container *ngIf="forgotStep() === 'none'">
            <header class="auth-head">
              <h2 class="login-heading">Welcome back</h2>
              <p class="login-sub">Sign in to your restaurant owner workspace.</p>
            </header>

            <div class="google-wrap">
              <div #googleButtonHost class="google-button-host"></div>
              <div *ngIf="googleLoading()" class="google-loading">Signing in with Google...</div>
              <div *ngIf="googleError()" class="alert-box error" role="alert">{{ googleError() }}</div>
            </div>

            <div class="divider"><span>or sign in with phone or email</span></div>

            <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form" novalidate>
              <div class="field">
                <label for="login-id" class="field-label">Phone Number or Email</label>
                <div class="input-icon-wrap">
                  <svg class="leading-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <rect x="2" y="4" width="20" height="16" rx="2"/>
                    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
                  </svg>
                  <input
                    id="login-id"
                    class="field-input field-input--with-icon"
                    formControlName="loginId"
                    placeholder="Enter your phone number or email"
                    autocomplete="username"
                    inputmode="email"
                    autocorrect="off"
                    autocapitalize="none"
                  />
                </div>
              </div>

              <div class="field">
                <div class="field-label-row">
                  <label for="login-password" class="field-label">Password</label>
                  <a
                    href="#forgot"
                    class="forgot-link"
                    (click)="startForgotPassword($event)"
                    role="button"
                    aria-haspopup="dialog"
                  >Forgot password?</a>
                </div>
                <div class="input-icon-wrap">
                  <svg class="leading-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                  </svg>
                  <input
                    id="login-password"
                    class="field-input field-input--with-icon field-input--with-trailing"
                    [type]="showPassword() ? 'text' : 'password'"
                    formControlName="password"
                    placeholder="Enter your password"
                    autocomplete="current-password"
                  />
                  <button
                    type="button"
                    class="toggle-pwd-btn"
                    (click)="showPassword.set(!showPassword())"
                    [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
                    [attr.aria-pressed]="showPassword()"
                  >
                    <svg *ngIf="showPassword()" class="pwd-icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8"><path d="M3 3l18 18"/><path d="M10.6 10.6a2 2 0 0 0 2.8 2.8"/><path d="M9.9 5.1A10.8 10.8 0 0 1 12 4.8c5.2 0 8.8 7.2 8.8 7.2a14.5 14.5 0 0 1-3.1 3.7"/><path d="M6.6 6.6C3.8 8.2 2 12 2 12s3.6 7.2 10 7.2a10.6 10.6 0 0 0 3.3-.5"/></svg>
                    <svg *ngIf="!showPassword()" class="pwd-icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
                  </button>
                </div>
              </div>

              <div *ngIf="error()" class="alert-box error" role="alert">{{ error() }}</div>

              <button class="login-button" type="submit" [disabled]="form.invalid || loading()">
                <span *ngIf="!loading()">Sign in</span>
                <span *ngIf="loading()" class="btn-spinner-row">
                  <svg class="spinning" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                  Signing in…
                </span>
              </button>
            </form>

            <div class="auth-card-footer">
              <p class="footer-links">
                New to KhanaBook?
                <a href="https://khanabook.in/get-started" class="signup-link" target="_blank" rel="noopener">Get started</a>
              </p>
              <nav class="login-footer-links" aria-label="Legal and support">
                <a href="https://khanabook.in/privacy-policy" target="_blank" rel="noopener noreferrer">Privacy Policy</a>
                <a href="https://khanabook.in/terms-and-conditions" target="_blank" rel="noopener noreferrer">Terms &amp; Conditions</a>
                <a href="https://khanabook.in/contact-us" target="_blank" rel="noopener noreferrer">Contact Support</a>
              </nav>
              <div class="mobile-foot-row">
                <span>© {{ year }} KhanaBook.</span>
                <a href="https://khanabook.in" target="_blank" rel="noopener" class="mobile-foot-link">khanabook.in ↗</a>
              </div>
            </div>
          </ng-container>

          <!-- Forgot Password: Step 1 (Phone) -->
          <ng-container *ngIf="forgotStep() === 'phone'">
            <header class="auth-head">
              <h2 class="login-heading">Forgot password</h2>
              <p class="login-sub">Enter your registered mobile number to receive an OTP.</p>
            </header>
            <form [formGroup]="phoneForm" (ngSubmit)="submitPhone()" class="auth-form" novalidate>
              <div class="field">
                <label for="forgot-phone" class="field-label">Phone number</label>
                <div class="input-icon-wrap">
                  <svg class="leading-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                  </svg>
                  <input
                    id="forgot-phone"
                    class="field-input field-input--with-icon"
                    formControlName="phone"
                    placeholder="10-digit mobile number"
                    maxlength="10"
                    autocomplete="tel"
                    inputmode="tel"
                  />
                </div>
              </div>
              <div *ngIf="forgotError()" class="alert-box error" role="alert">{{ forgotError() }}</div>
              <button class="login-button" type="submit" [disabled]="phoneForm.invalid || forgotLoading()">
                <span *ngIf="!forgotLoading()">Send OTP</span>
                <span *ngIf="forgotLoading()" class="btn-spinner-row">
                  <svg class="spinning" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                  Sending OTP…
                </span>
              </button>
            </form>
            <a href="#login" class="back-link" (click)="backToLogin($event)" role="button">← Back to sign in</a>
          </ng-container>

          <!-- Forgot Password: Step 2 (OTP) -->
          <ng-container *ngIf="forgotStep() === 'otp'">
            <header class="auth-head">
              <h2 class="login-heading">Enter OTP</h2>
              <p class="login-sub">If this number is registered, you'll receive a 6-digit code shortly.</p>
            </header>
            <form [formGroup]="otpForm" (ngSubmit)="submitOtp()" class="auth-form" novalidate>
              <div class="field">
                <label for="forgot-otp" class="field-label">OTP code</label>
                <div class="input-icon-wrap">
                  <svg class="leading-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                  </svg>
                  <input
                    id="forgot-otp"
                    class="field-input field-input--with-icon"
                    formControlName="otp"
                    placeholder="6-digit OTP"
                    maxlength="6"
                    autocomplete="one-time-code"
                    inputmode="numeric"
                  />
                </div>
              </div>
              <div *ngIf="forgotError()" class="alert-box error" role="alert">{{ forgotError() }}</div>
              <button class="login-button" type="submit" [disabled]="otpForm.invalid || forgotLoading()">
                <span *ngIf="!forgotLoading()">Verify OTP &rarr;</span>
                <span *ngIf="forgotLoading()" class="btn-spinner-row">
                  <svg class="spinning" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                  Verifying…
                </span>
              </button>
            </form>
            <a href="#login" class="back-link" (click)="backToLogin($event)" role="button">← Back to sign in</a>
          </ng-container>

          <!-- Forgot Password: Step 3 (New Password) -->
          <ng-container *ngIf="forgotStep() === 'password'">
            <header class="auth-head">
              <h2 class="login-heading">Reset password</h2>
              <p class="login-sub">Enter a new password (minimum 6 characters).</p>
            </header>
            <form [formGroup]="passwordForm" (ngSubmit)="submitNewPassword()" class="auth-form" novalidate>
              <div class="field">
                <label for="reset-new-password" class="field-label">New password</label>
                <div class="input-icon-wrap">
                  <svg class="leading-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                  </svg>
                  <input
                    id="reset-new-password"
                    class="field-input field-input--with-icon"
                    type="password"
                    formControlName="newPassword"
                    placeholder="Min 6 characters"
                    autocomplete="new-password"
                  />
                </div>
              </div>
              <div class="field">
                <label for="reset-confirm-password" class="field-label">Confirm password</label>
                <div class="input-icon-wrap">
                  <svg class="leading-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                  </svg>
                  <input
                    id="reset-confirm-password"
                    class="field-input field-input--with-icon"
                    type="password"
                    formControlName="confirmPassword"
                    placeholder="Re-enter password"
                    autocomplete="new-password"
                    [attr.aria-invalid]="passwordMismatch && passwordForm.get('confirmPassword')?.touched"
                    [attr.aria-describedby]="passwordMismatch && passwordForm.get('confirmPassword')?.touched ? 'reset-confirm-password-error' : null"
                  />
                </div>
              </div>
              <div id="reset-confirm-password-error" *ngIf="passwordMismatch && passwordForm.get('confirmPassword')?.touched" class="alert-box error" role="alert">
                Passwords do not match.
              </div>
              <div *ngIf="forgotError()" class="alert-box error" role="alert">{{ forgotError() }}</div>
              <button class="login-button" type="submit" [disabled]="passwordForm.invalid || passwordMismatch || forgotLoading()">
                <span *ngIf="!forgotLoading()">Reset password &rarr;</span>
                <span *ngIf="forgotLoading()" class="btn-spinner-row">
                  <svg class="spinning" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                  Resetting…
                </span>
              </button>
            </form>
            <a href="#login" class="back-link" (click)="backToLogin($event)" role="button">← Back to sign in</a>
          </ng-container>

          <!-- Forgot Password: Step 4 (Success) -->
          <ng-container *ngIf="forgotStep() === 'success'">
            <header class="auth-head">
              <h2 class="login-heading">Password updated!</h2>
              <p class="login-sub">Your password has been changed successfully. You can now sign in with your new credentials.</p>
            </header>
            <a href="#login" class="login-button login-button--link" (click)="backToLogin($event)" role="button">
              Sign in now &rarr;
            </a>
          </ng-container>
        </div>
      </main>
    </section>
  `,
  styles: [`
    :host {
      display: block;
      --login-orange: var(--kb-color-primary);
      --login-orange-action: var(--kb-color-primary);
      --login-orange-hover: var(--kb-color-primary-hover);
      --kb-color-foreground: #111827;
      --kb-color-muted-foreground: #64748B;
      --kb-color-border: #E2E8F0;
      --kb-color-surface: #FFFFFF;
      --kb-color-surface-2: #F8FAFC;
    }

    .auth-shell {
      min-height: 100vh;
      min-height: 100dvh;
      display: grid;
      grid-template-columns: 1.15fr 1fr;
      background: var(--kb-color-surface);
      overflow-x: hidden;
    }

    /* LEFT BRAND PANEL */
    .auth-brand {
      background:
        radial-gradient(120% 120% at 50% 0%, rgba(99, 102, 241, 0.18) 0%, rgba(32, 25, 95, 0) 60%),
        radial-gradient(90% 90% at 80% 80%, rgba(255, 90, 10, 0.10) 0%, rgba(32, 25, 95, 0) 50%),
        linear-gradient(135deg, #20195F 0%, #222B8A 56%, #3325B8 100%);
      border-right: 1px solid rgba(255, 255, 255, 0.08);
      color: var(--kb-color-foreground-contrast);
      padding: clamp(32px, 3.5vw, 48px) clamp(32px, 3.5vw, 56px);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      position: relative;
      overflow: hidden;
    }

    .brand-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: relative;
      z-index: 1;
    }
    .brand-mark {
      display: inline-flex;
      align-items: center;
      gap: var(--kb-space-3);
      text-decoration: none;
    }
    .brand-logo {
      width: 44px;
      height: 44px;
      border-radius: var(--kb-radius-lg);
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.12);
      display: grid;
      place-items: center;
      padding: var(--kb-space-1);
    }
    .brand-logo img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
    .brand-logo--sm {
      width: 36px;
      height: 36px;
      border-radius: var(--kb-radius-md);
      padding: var(--kb-space-1);
    }
    .brand-title {
      font-family: var(--font-display);
      font-weight: 700;
      font-size: var(--kb-text-2xl);
      letter-spacing: -0.02em;
      color: var(--kb-color-foreground-contrast);
    }
    .brand-title--dark {
      color: var(--kb-color-foreground);
      font-size: var(--kb-text-xl);
    }

    .brand-context-pill {
      display: inline-flex;
      align-items: center;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.12);
      padding: var(--kb-space-2) var(--kb-space-3);
      border-radius: var(--kb-radius-full);
      font-size: var(--kb-text-xs);
      font-weight: 500;
      color: rgba(255, 255, 255, 0.85);
      gap: var(--kb-space-2);
    }

    .brand-body {
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: var(--kb-space-8);
      margin: auto 0;
      position: relative;
      z-index: 1;
    }

    .brand-copy {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-3);
      max-width: 480px;
    }
    .brand-hero-title {
      font-family: var(--font-display);
      font-size: clamp(32px, 2.5vw, 44px);
      font-weight: 700;
      line-height: 1.15;
      letter-spacing: -0.025em;
      color: var(--kb-color-foreground-contrast);
      margin: 0;
    }
    .brand-hero-accent {
      color: var(--login-orange);
    }
    .brand-hero-sub {
      font-size: var(--kb-text-md);
      font-weight: 400;
      line-height: 1.5;
      color: rgba(255, 255, 255, 0.72);
      margin: 0;
      max-width: 440px;
    }

    /* Brand Showcase: Vector Live Components */
    .brand-showcase {
      display: grid;
      grid-template-columns: 1.4fr 1fr;
      gap: var(--kb-space-3);
      max-width: 520px;
    }

    .pos-preview-card {
      background: rgba(255, 255, 255, 0.05);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: var(--kb-radius-xl);
      padding: var(--kb-space-3);
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-2);
      box-shadow: 0 16px 32px -8px rgba(0, 0, 0, 0.4);
    }
    .pos-preview-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      padding-bottom: var(--kb-space-1);
    }
    .pos-preview-badge {
      display: inline-flex;
      align-items: center;
      gap: var(--kb-space-2);
      font-size: var(--kb-text-xs);
      font-weight: 600;
      color: var(--pos-sidebar-success);
    }
    .live-dot {
      width: 8px;
      height: 8px;
      border-radius: var(--kb-radius-full);
      background: var(--pos-sidebar-success);
      box-shadow: 0 0 8px var(--pos-sidebar-success);
      animation: pulse-dot 2s ease-in-out infinite;
    }
    @keyframes pulse-dot {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.5; transform: scale(0.85); }
    }
    .pos-order-id {
      font-size: var(--kb-text-xs);
      color: rgba(255, 255, 255, 0.5);
      font-family: var(--kb-font-mono);
    }
    .pos-preview-items {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-1);
    }
    .pos-item-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: var(--kb-text-xs);
      color: rgba(255, 255, 255, 0.9);
    }
    .pos-item-qty {
      font-weight: 600;
      color: var(--kb-color-primary);
      margin-right: var(--kb-space-1);
    }
    .pos-item-price {
      font-variant-numeric: tabular-nums;
      color: rgba(255, 255, 255, 0.7);
    }
    .pos-preview-total {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: var(--kb-space-2);
      border-top: 1px dashed rgba(255, 255, 255, 0.12);
      font-size: var(--kb-text-sm);
      font-weight: 600;
      color: var(--kb-color-foreground-contrast);
    }
    .pos-total-val {
      font-size: var(--kb-text-md);
      color: var(--kb-color-primary);
      font-variant-numeric: tabular-nums;
    }
    .pos-preview-footer {
      display: flex;
      flex-wrap: wrap;
      gap: var(--kb-space-1);
      padding-top: var(--kb-space-1);
    }
    .pos-chip {
      font-size: var(--kb-text-2xs);
      padding: var(--kb-space-1) var(--kb-space-2);
      border-radius: var(--kb-radius-sm);
      background: rgba(255, 255, 255, 0.06);
      color: rgba(255, 255, 255, 0.75);
      border: 1px solid rgba(255, 255, 255, 0.08);
    }
    .pos-chip--success {
      background: rgba(22, 101, 52, 0.25);
      color: var(--pos-sidebar-success);
      border-color: rgba(52, 211, 153, 0.3);
    }
    .pos-chip--sync {
      background: rgba(79, 70, 229, 0.2);
      color: var(--kb-color-primary-soft);
      border-color: rgba(99, 102, 241, 0.3);
    }

    .metric-preview-card {
      background: rgba(255, 255, 255, 0.04);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: var(--kb-radius-xl);
      padding: var(--kb-space-3);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      box-shadow: 0 16px 32px -8px rgba(0, 0, 0, 0.35);
    }
    .metric-preview-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .metric-label {
      font-size: var(--kb-text-xs);
      color: rgba(255, 255, 255, 0.6);
      font-weight: 500;
    }
    .metric-trend {
      font-size: var(--kb-text-2xs);
      font-weight: 600;
      color: var(--pos-sidebar-success);
      background: rgba(22, 101, 52, 0.25);
      padding: var(--kb-space-1);
      border-radius: var(--kb-radius-sm);
    }
    .metric-val {
      font-family: var(--font-display);
      font-size: var(--kb-text-2xl);
      font-weight: 700;
      color: var(--kb-color-foreground-contrast);
      font-variant-numeric: tabular-nums;
      margin-top: var(--kb-space-1);
    }
    .metric-sub {
      font-size: var(--kb-text-sm);
      color: rgba(255, 255, 255, 0.5);
    }
    .sparkline-svg {
      width: 100%;
      height: 36px;
      overflow: visible;
      margin: var(--kb-space-1) 0;
    }
    .metric-footnote {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: var(--kb-text-2xs);
      color: rgba(255, 255, 255, 0.45);
      border-top: 1px solid rgba(255, 255, 255, 0.06);
      padding-top: var(--kb-space-1);
    }

    /* Product Benefit Chips */
    .brand-feature-stack {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: var(--kb-space-3);
      max-width: 520px;
    }
    .brand-feature-card {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      padding: var(--kb-space-3) var(--kb-space-4);
      border-radius: var(--kb-radius-lg);
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.08);
    }
    .feature-icon-badge {
      width: 36px;
      height: 36px;
      border-radius: var(--kb-radius-md);
      background: rgba(255, 90, 10, 0.12);
      color: var(--kb-color-primary);
      display: grid;
      place-items: center;
      flex-shrink: 0;
      border: 1px solid rgba(255, 90, 10, 0.2);
    }
    .feature-text {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-1);
    }
    .feature-heading {
      font-size: var(--kb-text-sm);
      font-weight: 600;
      color: var(--kb-color-foreground-contrast);
      line-height: 1.2;
    }
    .feature-sub {
      font-size: var(--kb-text-xs);
      color: rgba(255, 255, 255, 0.6);
      line-height: 1.2;
    }

    .brand-foot-row {
      font-size: var(--kb-text-xs);
      color: rgba(255, 255, 255, 0.4);
    }

    /* RIGHT MAIN FORM PANEL */
    .auth-main {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: clamp(24px, 3.5vw, 48px) clamp(24px, 4vw, 56px);
      background: var(--kb-color-bg-app);
      overflow-y: auto;
      box-sizing: border-box;
      min-height: 100vh;
      min-height: 100dvh;
    }
    .auth-topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: min(100%, 420px);
      margin: 0 auto var(--kb-space-4);
    }
    .auth-card {
      width: min(100%, 420px);
      margin: auto;
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-6);
      box-sizing: border-box;
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl);
      padding: var(--kb-space-8);
      box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.05), 0 8px 10px -6px rgba(15, 23, 42, 0.03);
    }

    .mobile-brand-container {
      display: none;
    }
    .mobile-brand {
      display: inline-flex;
      align-items: center;
      gap: var(--kb-space-3);
      text-decoration: none;
    }
    .mobile-brand-meta {
      display: flex;
      flex-direction: column;
    }
    .brand-subtitle {
      font-size: var(--kb-text-2xs);
      color: var(--login-orange-action);
      font-weight: 600;
    }

    .auth-head {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-1);
      text-align: left;
    }
    .login-heading {
      margin: 0;
      font-family: var(--font-display);
      font-size: var(--kb-text-3xl);
      font-weight: 700;
      line-height: 1.25;
      letter-spacing: -0.025em;
      color: var(--kb-color-foreground);
    }
    .login-sub {
      margin: 0;
      font-size: var(--kb-text-sm);
      color: var(--kb-color-muted-foreground);
      line-height: 1.45;
    }

    .google-wrap {
      display: grid;
      gap: var(--kb-space-2);
      width: 100%;
      min-width: 0;
      min-height: 48px;
    }
    .google-button-host {
      width: 100%;
      min-width: 0;
      display: flex;
      justify-content: center;
      align-items: flex-start;
      overflow: hidden;
      border-radius: var(--kb-radius-lg);
    }
    .google-loading {
      text-align: center;
      font-size: var(--kb-text-sm);
      color: var(--kb-color-muted-foreground);
      padding: var(--kb-space-1) 0;
    }

    .divider {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-xs);
      font-weight: 500;
    }
    .divider::before, .divider::after {
      content: '';
      flex: 1;
      height: 1px;
      background: var(--kb-color-border);
    }

    .auth-form {
      display: grid;
      gap: var(--kb-space-4);
    }
    .field {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-1);
    }
    .field-label {
      font-size: var(--kb-text-sm);
      font-weight: 600;
      color: var(--kb-color-foreground);
      letter-spacing: -0.01em;
      text-transform: none;
    }
    .field-label-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--kb-space-4);
    }
    .input-icon-wrap {
      position: relative;
      display: flex;
      align-items: center;
      width: 100%;
    }
    .leading-icon {
      position: absolute;
      left: 14px;
      color: var(--kb-color-muted-foreground);
      pointer-events: none;
      z-index: 1;
      flex-shrink: 0;
    }
    .field-input {
      box-sizing: border-box;
      width: 100%;
      min-height: 48px;
      padding: var(--kb-space-3) var(--kb-space-4);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
      background: var(--kb-color-surface);
      font: inherit;
      font-size: var(--kb-text-md);
      color: var(--kb-color-foreground);
      transition: border-color 150ms ease, box-shadow 150ms ease;
    }
    .field-input--with-icon {
      padding-left: 44px;
    }
    .field-input--with-trailing {
      padding-right: 44px;
    }
    .field-input::placeholder {
      color: var(--kb-color-muted-foreground);
      opacity: 0.7;
    }
    .field-input:focus {
      outline: none;
      border-color: var(--kb-color-primary);
      box-shadow: 0 0 0 3px rgba(255, 90, 10, 0.15);
    }

    .toggle-pwd-btn {
      position: absolute;
      right: 6px;
      background: none;
      border: none;
      cursor: pointer;
      min-width: 36px;
      min-height: 36px;
      padding: var(--kb-space-1);
      color: var(--kb-color-muted-foreground);
      display: grid;
      place-items: center;
      z-index: 1;
      border-radius: var(--kb-radius-md);
      transition: color 150ms ease;
    }
    .toggle-pwd-btn:hover {
      color: var(--kb-color-foreground);
    }
    .toggle-pwd-btn:focus-visible {
      outline: 2px solid var(--kb-color-primary);
    }
    .pwd-icon {
      display: block;
    }

    .forgot-link {
      font-size: var(--kb-text-xs);
      font-weight: 500;
      color: var(--kb-color-link);
      text-decoration: none;
      transition: color 150ms ease;
    }
    .forgot-link:hover {
      color: var(--kb-color-link-hover);
      text-decoration: underline;
    }

    .login-button {
      width: 100%;
      min-height: 48px;
      border: 0;
      border-radius: var(--kb-radius-lg);
      background: var(--kb-color-primary);
      color: var(--kb-color-surface);
      font: inherit;
      font-size: var(--kb-text-md);
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(255, 90, 10, 0.25);
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--kb-space-2);
      transition: background 150ms ease,
                  transform 150ms ease,
                  box-shadow 150ms ease;
    }
    .login-button:hover:not(:disabled) {
      background: var(--kb-color-primary-hover);
      box-shadow: 0 4px 14px rgba(255, 90, 10, 0.35);
      transform: translateY(-1px);
    }
    .login-button:active:not(:disabled) {
      transform: scale(0.98);
    }
    .login-button:disabled {
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      border: 1px solid var(--kb-color-border);
      box-shadow: none;
      cursor: not-allowed;
      transform: none;
      opacity: 1;
    }
    .login-button:focus-visible {
      outline: 2px solid var(--kb-color-primary);
      outline-offset: 2px;
    }

    .btn-spinner-row {
      display: inline-flex;
      align-items: center;
      gap: var(--kb-space-2);
      justify-content: center;
    }
    .spinning {
      animation: spin 1s linear infinite;
    }
    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    .auth-card-footer {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--kb-space-4);
      text-align: center;
    }
    .footer-links {
      margin: 0;
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-sm);
    }
    .signup-link {
      color: var(--kb-color-primary);
      font-weight: 600;
      text-decoration: none;
      transition: color 150ms ease;
    }
    .signup-link:hover {
      text-decoration: underline;
    }
    .login-footer-links {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: var(--kb-space-2) var(--kb-space-4);
      font-size: var(--kb-text-xs);
      line-height: 1.5;
    }
    .login-footer-links a {
      color: var(--kb-color-muted-foreground);
      text-decoration: none;
      transition: color 150ms ease;
    }
    .login-footer-links a:hover {
      color: var(--kb-color-foreground);
      text-decoration: underline;
    }
    .login-footer-links a:focus-visible,
    .forgot-link:focus-visible,
    .signup-link:focus-visible {
      outline: 2px solid var(--kb-color-primary);
      outline-offset: 2px;
      border-radius: var(--kb-radius-sm);
    }
    .mobile-foot-row {
      display: none;
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
      gap: var(--kb-space-2);
    }
    .mobile-foot-link {
      color: var(--kb-color-primary);
      font-weight: 600;
      text-decoration: none;
    }
    .mobile-foot-link:hover {
      text-decoration: underline;
    }

    .back-link {
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-sm);
      font-weight: 500;
      cursor: pointer;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      gap: var(--kb-space-1);
      margin-top: var(--kb-space-1);
      transition: color 150ms ease;
    }
    .back-link:hover {
      color: var(--kb-color-primary);
      text-decoration: underline;
    }

    .alert-box {
      border-radius: var(--kb-radius-lg);
      padding: var(--kb-space-3) var(--kb-space-4);
      font-size: var(--kb-text-sm);
      line-height: 1.4;
    }
    .alert-box.error {
      color: var(--kb-color-danger);
      background: var(--kb-color-danger-soft);
      border: 1px solid var(--kb-color-danger-soft);
    }
    .alert-box.success {
      color: var(--kb-color-success);
      background: var(--kb-color-success-soft);
      border: 1px solid var(--kb-color-success-soft);
    }

    @media (max-width: 1100px) {
      .auth-shell {
        grid-template-columns: 1fr;
        background: var(--kb-color-surface);
      }
      .auth-brand {
        display: none !important;
      }
      .mobile-brand-container {
        display: block !important;
      }
      .mobile-foot-row {
        display: flex !important;
      }
      .auth-main {
        background: var(--kb-color-surface);
        padding: var(--kb-space-6) var(--kb-space-4);
      }
      .auth-card {
        border: none;
        padding: 0;
        box-shadow: none;
        gap: var(--kb-space-6);
      }
      .auth-form {
        gap: var(--kb-space-4);
      }
      .google-wrap {
        min-height: 48px;
      }
      .auth-card-footer {
        gap: var(--kb-space-3);
      }
    }

    @media (prefers-contrast: more) {
      .field-input { border-color: var(--kb-color-danger); border-width: 2px; }
      .field-label { color: var(--kb-color-foreground); font-weight: 700; }
      .login-button { background: var(--kb-color-primary); color: var(--kb-color-surface); border: 2px solid var(--kb-color-danger); }
      .divider { color: var(--kb-color-danger); font-weight: 700; }
      .divider::before, .divider::after { background: var(--kb-color-danger); height: 2px; }
    }
    .login-button--link { text-decoration: none; }
  `]
})
export class LoginPageComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly ngZone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly year = new Date().getFullYear();
  readonly showPassword = signal(false);

  readonly form = this.fb.nonNullable.group({
    loginId: ['', Validators.required],
    password: ['', Validators.required],
    rememberMe: [false]
  });

  readonly phoneForm = this.fb.nonNullable.group({
    phone: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]]
  });

  readonly otpForm = this.fb.nonNullable.group({
    otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
  });

  readonly passwordForm = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', [Validators.required, Validators.minLength(6)]]
  });

  readonly loading = signal(false);
  readonly error = signal('');
  readonly googleError = signal('');
  readonly googleLoading = signal(false);
  readonly loginSuccessMessage = signal('');

  readonly forgotStep = signal<ForgotStep>('none');
  readonly forgotLoading = signal(false);
  readonly forgotError = signal('');
  private forgotPhone = '';
  private tempToken = '';
  private googleTimer: ReturnType<typeof setTimeout> | null = null;
  private googleInitialized = false;
  private googleButtonHost: HTMLElement | null = null;
  private googleButtonObserver: ResizeObserver | null = null;
  private renderedGoogleButtonHost: HTMLElement | null = null;
  private renderedGoogleButtonWidth = 0;

  @ViewChild('googleButtonHost')
  set googleButtonHostElement(hostRef: ElementRef<HTMLDivElement> | undefined) {
    this.googleButtonObserver?.disconnect();
    this.googleButtonObserver = null;
    this.googleButtonHost = hostRef?.nativeElement ?? null;
    this.renderedGoogleButtonHost = null;
    this.renderedGoogleButtonWidth = 0;

    if (this.googleButtonHost && typeof ResizeObserver !== 'undefined') {
      this.googleButtonObserver = new ResizeObserver(() => this.renderGoogleButton());
      this.googleButtonObserver.observe(this.googleButtonHost);
    }

    this.renderGoogleButton();
  }

  get passwordMismatch(): boolean {
    const { newPassword, confirmPassword } = this.passwordForm.getRawValue();
    return confirmPassword.length > 0 && newPassword !== confirmPassword;
  }

  ngOnInit(): void {
    const lastLoginId = sessionStorage.getItem(LAST_LOGIN_ID_STORAGE_KEY);
    if (lastLoginId) {
      this.form.patchValue({ loginId: lastLoginId });
    }
    if (window.location.hash === '#forgot') {
      this.forgotStep.set('phone');
    }
    this.initGoogle();
  }

  private initGoogle(): void {
    let attempts = 0;
    const maxAttempts = 40; // ~12s at 300ms
    const check = () => {
      attempts++;
      const gsiReady = typeof google !== 'undefined' && google?.accounts?.id;

      if (gsiReady) {
        google.accounts.id.initialize({
          client_id: environment.googleClientId,
          callback: (response: any) => {
            this.ngZone.run(() => this.handleGoogleCredential(response.credential));
          }
        });
        this.googleInitialized = true;
        this.renderGoogleButton();
        return;
      }
      if (attempts < maxAttempts) {
        this.googleTimer = setTimeout(check, 300);
      }
    };
    check();
  }

  private renderGoogleButton(): void {
    const host = this.googleButtonHost;
    if (!this.googleInitialized || !host || typeof google === 'undefined' || !google?.accounts?.id) return;

    const width = Math.min(400, Math.floor(host.getBoundingClientRect().width));
    if (width < 120 || (this.renderedGoogleButtonHost === host && this.renderedGoogleButtonWidth === width)) return;

    host.replaceChildren();
    google.accounts.id.renderButton(host, {
      type: 'standard',
      theme: 'outline',
      size: 'large',
      text: 'continue_with',
      shape: 'rectangular',
      logo_alignment: 'left',
      width
    });
    const gsiWrapper = host.firstElementChild as HTMLElement | null;
    const gsiButton = host.querySelector<HTMLElement>('[role="button"]');
    gsiWrapper?.style.setProperty('width', '100%', 'important');
    gsiButton?.parentElement?.style.setProperty('width', '100%', 'important');
    gsiButton?.style.setProperty('width', '100%', 'important');
    gsiButton?.style.setProperty('max-width', 'none', 'important');
    gsiButton?.style.setProperty('height', '48px', 'important');
    this.renderedGoogleButtonHost = host;
    this.renderedGoogleButtonWidth = width;
  }

  ngOnDestroy(): void {
    this.googleButtonObserver?.disconnect();
    this.googleButtonObserver = null;
    if (this.googleTimer !== null) {
      clearTimeout(this.googleTimer);
      this.googleTimer = null;
    }
  }

  private handleGoogleCredential(idToken: string): void {
    this.googleError.set('');
    this.googleLoading.set(true);
    this.authService.googleLogin(idToken).subscribe({
      next: () => {
        this.googleLoading.set(false);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.googleLoading.set(false);
        this.googleError.set(err?.error?.error ?? err?.error?.message ?? 'Google sign-in failed.');
        this.cdr.markForCheck();
      }
    });
  }

  submit(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.loginSuccessMessage.set('');
    const { loginId, password } = this.form.getRawValue();
    this.authService.login({ loginId, password }).subscribe({
      next: () => {
        this.loading.set(false);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.error || err?.error?.message || 'Invalid login ID or password.');
        this.cdr.markForCheck();
      }
    });
  }

  startForgotPassword(event?: Event): void {
    if (event) event.preventDefault();
    this.forgotStep.set('phone');
    this.forgotError.set('');
    this.forgotLoading.set(false);
    this.loginSuccessMessage.set('');
    this.phoneForm.reset();
    this.otpForm.reset();
    this.passwordForm.reset();
    this.cdr.markForCheck();
  }

  backToLogin(event?: Event): void {
    if (event) event.preventDefault();
    this.forgotStep.set('none');
    this.forgotError.set('');
    this.forgotLoading.set(false);
    this.forgotPhone = '';
    this.tempToken = '';
    this.cdr.markForCheck();
  }

  submitPhone(): void {
    if (this.phoneForm.invalid || this.forgotLoading()) return;
    this.forgotLoading.set(true);
    this.forgotError.set('');
    this.forgotPhone = this.phoneForm.getRawValue().phone;

    this.authService.requestPasswordOtp(this.forgotPhone).subscribe({
      next: () => {
        this.forgotLoading.set(false);
        this.forgotStep.set('otp');
        this.forgotError.set('');
        this.cdr.markForCheck();
      },
      error: () => {
        this.forgotLoading.set(false);
        this.forgotStep.set('otp');
        this.forgotError.set('');
        this.cdr.markForCheck();
      }
    });
  }

  submitOtp(): void {
    if (this.otpForm.invalid || this.forgotLoading()) return;
    this.forgotLoading.set(true);
    this.forgotError.set('');
    const otp = this.otpForm.getRawValue().otp;

    this.authService.verifyPasswordOtp(this.forgotPhone, otp).subscribe({
      next: (res) => {
        this.forgotLoading.set(false);
        this.tempToken = res.tempToken;
        this.forgotStep.set('password');
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.forgotLoading.set(false);
        this.forgotError.set(err?.error?.error || err?.error?.message || 'Invalid or expired OTP. Please try again.');
        this.cdr.markForCheck();
      }
    });
  }

  submitNewPassword(): void {
    const { newPassword, confirmPassword } = this.passwordForm.getRawValue();
    if (this.passwordForm.invalid
        || !isPasswordResetSubmissionValid(newPassword, confirmPassword)
        || this.forgotLoading()) return;
    this.forgotLoading.set(true);
    this.forgotError.set('');

    this.authService.resetPassword(this.tempToken, newPassword).subscribe({
      next: () => {
        this.forgotLoading.set(false);
        this.forgotStep.set('success');
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.forgotLoading.set(false);
        this.forgotError.set(err?.error?.error || err?.error?.message || 'Failed to reset password. Please try again.');
        this.cdr.markForCheck();
      }
    });
  }
}
