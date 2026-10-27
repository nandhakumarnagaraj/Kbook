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
      <!-- Left Brand Panel (64% desktop) -->
      <aside class="auth-brand" aria-label="KhanaBook Restaurant Platform Overview">
        <div class="brand-glow-1" aria-hidden="true"></div>
        <div class="brand-glow-2" aria-hidden="true"></div>

        <div class="brand-top">
          <a href="https://khanabook.in" class="brand-mark" title="Back to KhanaBook website" aria-label="KhanaBook - Back to Homepage">
            <div class="brand-logo">
              <img src="/khanabook_logo.png" alt="KhanaBook" />
            </div>
            <span class="brand-title">KhanaBook</span>
          </a>

          <span class="brand-context-pill">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="var(--kb-color-primary)" aria-hidden="true"><path d="m12 2.5 2.85 5.78 6.38.93-4.62 4.5 1.09 6.36L12 17.06l-5.7 3 1.09-6.36-4.62-4.5 6.38-.93L12 2.5Z"/></svg>
            Trusted by 250+ Restaurants
          </span>
        </div>

        <div class="brand-body">
          <div class="brand-copy">
            <h1 class="brand-hero-title">
              Run your restaurant<br/>with <span class="brand-hero-accent">confidence.</span>
            </h1>
            <p class="brand-hero-sub">
              One powerful workspace for menu, orders,<br />staff, payments and more.
            </p>
          </div>

          <img
            src="/login-approved-dashboard.png"
            alt="KhanaBook restaurant dashboard, mobile order screen, and receipt printer"
            class="brand-approved-dashboard-img"
            fetchpriority="high"
          />

          <!-- Product benefits -->
          <div class="brand-feature-stack">
            <div class="brand-feature-card">
              <div class="feature-icon-badge feature-icon-badge--sales">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="10" cy="7" r="4"/>
                  <path d="M20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
                </svg>
              </div>
              <div class="feature-text">
              <strong class="feature-heading">Customer Management</strong>
                <span class="feature-sub">Build lasting relationships, bring<br />customers back.</span>
              </div>
            </div>

            <div class="brand-feature-card">
              <div class="feature-icon-badge feature-icon-badge--menu">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M3 3v18h18"/>
                  <path d="m7 14 4-4 4 3 6-7"/>
                  <path d="M16 6h5v5"/>
                </svg>
              </div>
              <div class="feature-text">
              <strong class="feature-heading">Real-time Analytics</strong>
              <span class="feature-sub">Track sales, inventory and growth<br />with ease.</span>
              </div>
            </div>

            <div class="brand-feature-card">
              <div class="feature-icon-badge feature-icon-badge--reports">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/>
                  <path d="m9 12 2 2 4-4"/>
                </svg>
              </div>
              <div class="feature-text">
                <strong class="feature-heading">Secure &amp; Reliable</strong>
                <span class="feature-sub">Your data, payments and operations<br />are protected.</span>
              </div>
            </div>
          </div>
        </div>

      </aside>

      <!-- Right Form Panel (37.5% desktop) -->
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
                    <svg *ngIf="showPassword()" class="pwd-icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8"><path d="M3 3l18 18"/><path d="M10.6 10.6a2 2 0 0 0 2.8 2.8"/><path d="M9.9 5.1A10.8 10.8 0 0 1 12 4.8c5.2 0 8.8 7.2 8.8 7.2a14.5 14.5 0 0 1-3.1 3.7"/><path d="M6.6 6.6C3.8 8.2 2 12 2 12s3.6 7.2 10 7.2a10.6 10.6 0 0 0 3.3-.5"/></svg>
                    <svg *ngIf="!showPassword()" class="pwd-icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
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
              <br />
            </form>

            <div class="auth-card-footer">
              <p class="footer-links">
                New to KhanaBook?
                <a href="https://khanabook.in/get-started" class="signup-link" target="_blank" rel="noopener">Get started</a>
              </p>
              <br />
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
      --kb-color-foreground: #172347;
      --kb-color-muted-foreground: #7180A2;
      --kb-color-border: #D8E0F1;
      --kb-color-surface: #FFFFFF;
      --kb-color-surface-2: #FFFFFF;
      --kb-purple-950: var(--kb-color-primary);
      --kb-purple-800: var(--kb-color-primary-hover);
      --kb-purple-600: var(--kb-color-primary);
      --kb-lavender: var(--kb-color-primary);
      --kb-text: var(--kb-color-foreground);
      --kb-muted: var(--kb-color-muted-foreground);
      --kb-border: var(--kb-color-border);
      --kb-surface: var(--kb-color-surface);
      --kb-soft: var(--kb-color-primary-soft);
    }

    .auth-shell {
      min-height: 100vh;
      min-height: 100dvh;
      display: grid;
      grid-template-columns: 7fr 4fr;
      background: var(--kb-color-surface);
      overflow-x: hidden;
    }

    /* LEFT BRAND PANEL */
    .auth-brand {
      background:
        radial-gradient(ellipse at 88% 28%, rgba(69, 77, 226, 0.68) 0%, rgba(69, 77, 226, 0) 40%),
        radial-gradient(ellipse at 88% 64%, rgba(226, 82, 49, 0.28) 0%, rgba(226, 82, 49, 0) 34%),
        linear-gradient(135deg, #20195F 0%, #222B8A 56%, #3325B8 100%);
      color: var(--kb-color-surface);
      padding: clamp(32px, 3.5vw, 42px) clamp(32px, 3.5vw, 65px);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      position: relative;
      overflow: hidden;
    }
    .auth-brand::before,
    .auth-brand::after {
      content: '';
      position: absolute;
      bottom: -245px;
      width: 430px;
      height: 430px;
      border-radius: 50%;
      pointer-events: none;
      z-index: 0;
    }
    .auth-brand::before {
      left: -245px;
      background: linear-gradient(145deg, rgba(232, 122, 30, 0.36), rgba(232, 122, 30, 0.04));
    }
    .auth-brand::after {
      right: -245px;
      background: linear-gradient(220deg, rgba(196, 181, 253, 0.10), rgba(147, 51, 234, 0.24));
    }

    /* Ambient soft decorative glows */
    .brand-glow-1 {
      position: absolute;
      top: -100px;
      left: -80px;
      width: 440px;
      height: 440px;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(167, 139, 250, 0.18) 0%, rgba(91, 33, 182, 0) 70%);
      pointer-events: none;
    }
    .brand-glow-2 {
      position: absolute;
      bottom: -60px;
      right: -80px;
      width: 480px;
      height: 480px;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(232, 122, 30, 0.16) 0%, rgba(232, 122, 30, 0) 75%);
      pointer-events: none;
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
      gap: 1.125rem;
      text-decoration: none;
    }
    .brand-logo {
      width: 62px;
      height: 62px;
      border-radius: 15px;
      background: var(--kb-color-surface);
      display: grid;
      place-items: center;
      padding: var(--kb-space-1);
      box-shadow: 0 4px 14px rgba(0, 0, 0, 0.25);
    }
    .brand-logo img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
    .brand-mark .brand-logo {
      background: var(--login-orange);
    }
    .brand-mark .brand-logo img {
      mix-blend-mode: multiply;
    }
    .brand-logo--sm {
      width: 36px;
      height: 36px;
      border-radius: 9px;
      padding: 3px;
    }
    .brand-title {
      font-weight: 800;
      font-size: var(--kb-text-4xl);
      letter-spacing: -0.025em;
      color: var(--kb-color-surface);
    }
    .brand-title--dark {
      color: var(--kb-color-foreground);
      font-size: 1.2rem;
    }

    .brand-context-pill {
      display: inline-flex;
      align-self: flex-start;
      align-items: center;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.18);
      padding: 10px 23px;
      margin-top: 4px;
      border-radius: var(--kb-radius-full);
      font-size: var(--kb-text-md);
      font-weight: 650;
      letter-spacing: normal;
      text-transform: none;
      color: rgba(255, 255, 255, 0.9);
      gap: var(--kb-space-2);
    }

    .brand-body {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      grid-template-rows: auto minmax(0, 1fr);
      column-gap: clamp(0.5rem, 1.4vw, 1.25rem);
      row-gap: clamp(0.75rem, 1.5vh, 1.25rem);
      min-height: clamp(500px, 66vh, 620px);
      margin: auto 0;
      position: relative;
      z-index: 1;
    }

    .brand-copy {
      display: grid;
      gap: 0.65rem;
      grid-column: 1;
      grid-row: 1;
      align-self: start;
      position: relative;
      z-index: 2;
      max-width: 490px;
    }
    .brand-hero-title {
      font-size: clamp(34px, 2.8vw, 52px);
      font-weight: 800;
      line-height: 1.1;
      letter-spacing: -0.03em;
      color: var(--kb-color-surface);
      margin: 0;
    }
    .brand-hero-accent {
      color: var(--login-orange);
    }
    .brand-hero-sub {
      font-size: clamp(15px, 1.05vw, 17px);
      font-weight: 400;
      line-height: 1.5;
      color: rgba(255, 255, 255, 0.88);
      margin: 0;
      max-width: 410px;
    }

    .brand-approved-dashboard-img {
      grid-column: 2;
      grid-row: 1 / span 2;
      align-self: center;
      justify-self: center;
      width: 125%;
      height: auto;
      max-width: 700px;
      object-fit: contain;
      filter: drop-shadow(0 18px 28px rgba(18, 5, 43, 0.34));
      position: relative;
      z-index: 1;
    }

    /* Translucent Feature Cards */
    .brand-feature-stack {
      display: grid;
      gap: 14px;
      grid-column: 1;
      grid-row: 2;
      align-self: center;
      width: 100%;
      max-width: none;
      position: relative;
      z-index: 2;
    }
    .brand-feature-card {
      display: flex;
      align-items: center;
      gap: 18px;
      padding: var(--kb-space-2) 0;
    }
    .feature-icon-badge {
      width: 60px;
      height: 60px;
      border-radius: var(--kb-radius-xl);
      background: rgba(255, 255, 255, 0.18);
      color: var(--kb-color-surface);
      display: grid;
      place-items: center;
      flex-shrink: 0;
      border: 1px solid rgba(255, 255, 255, 0.25);
      box-shadow: none;
    }
    .feature-icon-badge--sales {
      background: var(--login-orange);
      border-color: rgba(255, 255, 255, 0.38);
    }
    .feature-icon-badge--menu {
      background: var(--kb-color-primary);
      border-color: rgba(255, 255, 255, 0.32);
    }
    .feature-icon-badge--reports {
      background: var(--kb-color-success);
      border-color: rgba(255, 255, 255, 0.32);
    }
    .feature-text {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-1);
      max-width: 340px;
    }
    .feature-heading {
      font-size: 17px;
      font-weight: 650;
      color: var(--kb-color-surface);
      line-height: 1.35;
      letter-spacing: -0.01em;
    }
    .feature-sub {
      font-size: 15px;
      color: rgba(255, 255, 255, 0.84);
      line-height: 1.3;
    }

    /* RIGHT MAIN FORM PANEL */
    .auth-main {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: clamp(24px, 3.5vw, 42px) clamp(24px, 4vw, 56px);
      background: var(--kb-color-surface);
      overflow-y: auto;
      box-sizing: border-box;
      min-height: 100vh;
      min-height: 100dvh;
    }
    .auth-topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: min(100%, 506px);
      margin: 0 auto var(--kb-space-6);
    }
    .auth-card {
      width: min(100%, 506px);
      margin: auto;
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-10);
      box-sizing: border-box;
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
      gap: var(--kb-space-2);
    }
    .login-heading {
      margin: 0;
      font-size: clamp(30px, 2.2vw, 38px);
      font-weight: 750;
      line-height: 1.2;
      letter-spacing: -0.035em;
      color: var(--kb-color-foreground);
    }
    .login-sub {
      margin: 0;
      font-size: 15px;
      color: var(--kb-color-muted-foreground);
      line-height: 1.45;
    }

    .google-wrap {
      display: grid;
      gap: var(--kb-space-2);
      width: 100%;
      min-width: 0;
      min-height: 58px;
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
      gap: 14px;
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-sm);
      font-weight: 500;
      margin: -1px 0;
    }
    .divider::before, .divider::after {
      content: '';
      flex: 1;
      height: 1px;
      background: var(--kb-color-border);
    }

    .auth-form {
      display: grid;
      gap: var(--kb-space-6);
    }
    .field {
      display: grid;
      gap: 6px;
    }
    .field-label {
      font-size: var(--kb-text-md);
      font-weight: 600;
      color: var(--kb-color-foreground);
      letter-spacing: -0.01em;
      text-transform: none;
    }
    .field-label-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 18px;
    }
    .input-icon-wrap {
      position: relative;
      display: flex;
      align-items: center;
      width: 100%;
    }
    .leading-icon {
      position: absolute;
      left: 16px;
      color: var(--kb-color-muted-foreground);
      pointer-events: none;
      z-index: 1;
      flex-shrink: 0;
    }
    .field-input {
      box-sizing: border-box;
      width: 100%;
      min-height: 52px;
      padding: 14px var(--kb-space-4);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl);
      background: var(--kb-color-surface);
      font: inherit;
      font-size: 15px;
      color: var(--kb-color-foreground);
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }
    .field-input--with-icon {
      padding-left: 46px;
    }
    .field-input--with-trailing {
      padding-right: 48px;
    }
    .field-input::placeholder {
      color: var(--kb-color-muted-foreground);
    }
    .field-input:focus {
      outline: 3px solid rgba(189, 77, 0, 0.24);
      outline-offset: 2px;
      border-color: var(--login-orange-action);
    }

    .toggle-pwd-btn {
      position: absolute;
      right: 6px;
      background: none;
      border: none;
      cursor: pointer;
      min-width: 40px;
      min-height: 40px;
      padding: var(--kb-space-2);
      color: var(--kb-color-muted-foreground);
      display: grid;
      place-items: center;
      z-index: 1;
      border-radius: var(--kb-radius-lg);
      transition: color 0.15s ease;
    }
    .toggle-pwd-btn:hover {
      color: var(--kb-color-foreground);
    }
    .toggle-pwd-btn:focus-visible {
      outline: 2px solid var(--login-orange-action);
    }
    .pwd-icon {
      display: block;
    }

    .field-actions-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .remember-label {
      display: inline-flex;
      align-items: center;
      gap: var(--kb-space-2);
      font-size: var(--kb-text-md);
      color: var(--kb-color-muted-foreground);
      cursor: pointer;
      user-select: none;
    }
    .remember-checkbox {
      width: 17px;
      height: 17px;
      border-radius: var(--kb-radius-sm);
      border: 1.5px solid var(--kb-color-border);
      accent-color: var(--kb-color-primary);
      cursor: pointer;
    }
    .forgot-link {
      font-size: var(--kb-text-md);
      font-weight: 600;
      color: var(--kb-color-primary);
      text-decoration: underline;
      text-underline-offset: 3px;
      transition: color 0.15s ease;
    }
    .forgot-link:hover {
      color: var(--login-orange-hover);
      text-decoration: underline;
      text-underline-offset: 3px;
    }

    .login-button {
      width: 100%;
      min-height: 52px;
      border: 0;
      border-radius: var(--kb-radius-xl);
      background: var(--login-orange-action);
      color: var(--kb-color-surface);
      font: inherit;
      font-size: var(--kb-text-lg);
      font-weight: 650;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(125, 52, 0, 0.2);
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--kb-space-2);
      transition: background 160ms ease,
                  transform 160ms ease,
                  box-shadow 160ms ease;
    }
    .login-button:hover:not(:disabled) {
      background: var(--login-orange-hover);
      box-shadow: 0 6px 20px rgba(125, 52, 0, 0.28);
      transform: translateY(-1px);
    }
    .login-button:active:not(:disabled) {
      transform: scale(0.98);
    }
    .login-button:disabled {
      background: var(--login-orange-action);
      opacity: 0.6;
      color: var(--kb-color-surface);
      box-shadow: none;
      cursor: not-allowed;
      transform: none;
    }
    .login-button:focus-visible {
      outline: 3px solid var(--login-orange);
      outline-offset: 3px;
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
      gap: 22px;
      text-align: center;
    }
    .footer-links {
      margin: 0;
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-md);
    }
    .signup-link {
      color: var(--login-orange-action);
      font-weight: 600;
      text-decoration: none;
      transition: color 0.15s ease;
    }
    .signup-link:hover {
      color: var(--login-orange-hover);
      text-decoration: underline;
      text-underline-offset: 3px;
    }
    .login-footer-links {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: var(--kb-space-2) var(--kb-space-4);
      font-size: var(--kb-text-sm);
      line-height: 1.5;
    }
    .login-footer-links a {
      color: var(--login-orange-action);
      text-decoration: none;
      text-underline-offset: 3px;
    }
    .login-footer-links a:hover {
      color: var(--login-orange-hover);
      text-decoration: underline;
    }
    .login-footer-links a:focus-visible,
    .forgot-link:focus-visible,
    .signup-link:focus-visible {
      outline: 2px solid var(--login-orange-action);
      outline-offset: 3px;
      border-radius: 2px;
    }
    .mobile-foot-row {
      display: none;
      font-size: var(--kb-text-sm);
      color: var(--kb-color-muted-foreground);
      gap: var(--kb-space-2);
    }
    .mobile-foot-link {
      color: var(--login-orange-action);
      font-weight: 600;
      text-decoration: none;
    }
    .mobile-foot-link:hover {
      color: var(--login-orange-hover);
      text-decoration: underline;
      text-underline-offset: 3px;
    }

    .back-link {
      color: var(--kb-color-muted-foreground);
      font-size: var(--kb-text-md);
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      margin-top: 4px;
      transition: color 0.15s ease;
    }
    .back-link:hover {
      color: var(--kb-color-link);
      text-decoration: underline;
    }

    .alert-box {
      border-radius: 10px;
      padding: var(--kb-space-3) var(--kb-space-4);
      font-size: var(--kb-text-md);
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
        padding: var(--kb-space-6) var(--kb-space-5);
      }
      .auth-card {
        gap: var(--kb-space-6);
      }
      .auth-form {
        gap: 18px;
      }
      .google-wrap {
        min-height: 50px;
      }
      .login-button {
        margin-top: 0;
      }
      .auth-card-footer {
        gap: var(--kb-space-3);
        margin-top: 8px;
      }
      .login-footer-links {
        margin-top: 0;
      }
    }

    @media (min-width: 1100px) and (max-height: 860px) {
      .auth-main {
        padding-top: var(--kb-space-4);
        padding-bottom: var(--kb-space-4);
      }
      .auth-topbar {
        margin-bottom: var(--kb-space-3);
      }
      .auth-card {
        gap: var(--kb-space-5);
      }
      .auth-form {
        gap: var(--kb-space-4);
      }
      .auth-card-footer {
        gap: var(--kb-space-2);
      }
    }

    @media (min-width: 1100px) and (max-height: 800px) {
      .auth-main {
        padding-top: var(--kb-space-3);
        padding-bottom: var(--kb-space-3);
      }
      .auth-card {
        gap: var(--kb-space-4);
      }
      .auth-form {
        gap: var(--kb-space-3);
      }
      .field-input {
        min-height: 46px;
      }
      .google-wrap {
        min-height: 48px;
      }
      .auth-card-footer {
        margin-top: 0;
      }
    }

    @media (prefers-contrast: more) {
      .field-input { border-color: var(--kb-color-error); border-width: 2px; }
      .field-label { color: var(--kb-color-foreground); font-weight: 700; }
      .login-button { background: var(--kb-color-primary); color: var(--kb-color-surface); border: 2px solid var(--kb-color-error); }
      .divider { color: var(--kb-color-error); font-weight: 700; }
      .divider::before, .divider::after { background: var(--kb-color-error); height: 2px; }
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

    const width = Math.min(506, Math.floor(host.getBoundingClientRect().width));
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
    gsiButton?.style.setProperty('height', width >= 480 ? '58px' : '40px', 'important');
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
