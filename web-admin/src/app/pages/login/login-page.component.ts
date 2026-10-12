import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, OnDestroy, NgZone, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
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
      <aside class="auth-brand" aria-label="KhanaBook Restaurant Platform Overview">
        <div class="brand-top">
          <a href="https://khanabook.in" class="brand-mark" title="Back to KhanaBook website" aria-label="KhanaBook Homepage">
            <div class="brand-logo">
              <img src="/khanabook_logo.png" alt="KhanaBook" style="width:100%;height:100%;object-fit:contain;" />
            </div>
            <span class="brand-title">KhanaBook</span>
          </a>
        </div>

        <div class="brand-copy">
          <h1 class="brand-hero-title">
            Run your restaurant<br/>with <span class="brand-accent-text">confidence.</span>
          </h1>
          <p class="brand-hero-sub">One workspace for menu, orders, staff, and payments — built for restaurant owners.</p>

          <div class="brand-feature-stack">
            <div class="brand-feature-card">
              <div class="feature-icon-badge">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M18 20V10"/>
                  <path d="M12 20V4"/>
                  <path d="M6 20v-6"/>
                </svg>
              </div>
              <div class="feature-text">
                <strong class="feature-heading">Consolidated live sales across all POS terminals</strong>
                <span class="feature-sub">Real-time insights for better decisions.</span>
              </div>
            </div>

            <div class="brand-feature-card">
              <div class="feature-icon-badge">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <line x1="10" y1="9" x2="8" y2="9"/>
                </svg>
              </div>
              <div class="feature-text">
                <strong class="feature-heading">Menu &amp; recipe stock control in seconds</strong>
                <span class="feature-sub">Keep your kitchen and inventory in sync.</span>
              </div>
            </div>

            <div class="brand-feature-card">
              <div class="feature-icon-badge">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10 9 9 9 8 9"/>
                </svg>
              </div>
              <div class="feature-text">
                <strong class="feature-heading">Daily revenue, settlements &amp; CA-ready tax reports</strong>
                <span class="feature-sub">Save time. Stay compliant.</span>
              </div>
            </div>
          </div>
        </div>

        <div class="brand-foot-row">
          <p class="brand-foot">© {{ year }} KhanaBook. All rights reserved.</p>
          <a href="https://khanabook.in" class="brand-link" target="_blank" rel="noopener">khanabook.in ↗</a>
        </div>
      </aside>

      <main class="auth-main">
        <div class="auth-card">
          <div class="mobile-brand-container">
            <a href="https://khanabook.in" class="mobile-brand" title="Back to KhanaBook website" aria-label="KhanaBook Homepage">
              <div class="brand-logo brand-logo--sm">
                <img src="/khanabook_logo.png" alt="KhanaBook" style="width:100%;height:100%;object-fit:contain;" />
              </div>
              <div class="mobile-brand-meta">
                <span class="brand-title brand-title--dark">KhanaBook</span>
                <span class="brand-subtitle">Restaurant Owner Workspace</span>
              </div>
            </a>
          </div>

          <div *ngIf="loginSuccessMessage" class="alert-box success" role="status">{{ loginSuccessMessage }}</div>

          <ng-container *ngIf="forgotStep === 'none'">
            <header class="auth-head">
              <h2>Welcome back</h2>
              <p class="muted">Sign in to your restaurant owner workspace.</p>
            </header>

            <div class="google-wrap">
              <div id="google-btn"></div>
              <div *ngIf="googleLoading" class="google-loading">Signing in with Google...</div>
              <div *ngIf="googleError" class="alert-box error" role="alert">{{ googleError }}</div>
            </div>

            <div class="divider"><span>or with password</span></div>

            <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form" novalidate>
              <div class="field">
                <label for="login-id" class="field-label">PHONE NUMBER OR EMAIL</label>
                <div class="input-icon-wrap">
                  <svg class="leading-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <rect x="2" y="4" width="20" height="16" rx="2"/>
                    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
                  </svg>
                  <input
                    id="login-id"
                    class="field-input field-input--with-icon"
                    formControlName="loginId"
                    placeholder="e.g. 98765 43210 or owner@restaurant.com"
                    autocomplete="username"
                    inputmode="text"
                    autocorrect="off"
                    autocapitalize="none"
                  />
                </div>
              </div>

              <div class="field">
                <div class="field-row">
                  <label for="login-password" class="field-label">PASSWORD</label>
                  <a
                    href="#forgot"
                    class="link-right"
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
                    placeholder="Enter password"
                    autocomplete="current-password"
                  />
                  <button
                    type="button"
                    class="toggle-pwd-btn"
                    (click)="showPassword.set(!showPassword())"
                    [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
                    [attr.aria-pressed]="showPassword()"
                  >
                    <svg *ngIf="showPassword()" class="pwd-icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="19" height="19"><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575a1 1 0 0 1 0 .696a10.8 10.8 0 0 1-1.444 2.49m-6.41-.679a3 3 0 0 1-4.242-4.242"/><path fill="currentColor" d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151a1 1 0 0 1 0-.696a10.75 10.75 0 0 1 4.446-5.143M2 2l20 20"/></g></svg>
                    <svg *ngIf="!showPassword()" class="pwd-icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="19" height="19"><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M2.062 12.348a1 1 0 0 1 0-.696a10.75 10.75 0 0 1 19.876 0a1 1 0 0 1 0 .696a10.75 10.75 0 0 1-19.876 0"/><circle fill="currentColor" cx="12" cy="12" r="3"/></g></svg>
                  </button>
                </div>
              </div>

              <div *ngIf="error" class="alert-box error" role="alert">{{ error }}</div>

              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="form.invalid || loading">
                <span *ngIf="!loading">Sign in &rarr;</span>
                <span *ngIf="loading" class="btn-spinner-row">
                  <svg class="spinning" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                  Signing in…
                </span>
              </button>
            </form>

            <div class="auth-card-footer">
              <p class="footer-links muted">
                New to KhanaBook?
                <a href="https://khanabook.in/get-started" class="link-right" target="_blank" rel="noopener">Get started</a>
                or
                <a href="https://khanabook.in" class="link-right" target="_blank" rel="noopener">learn more</a>
              </p>
              <div class="trust-badge-row">
                <span class="trust-icon" aria-hidden="true">🔒</span>
                <span>256-bit SSL encrypted • Restaurant Cloud</span>
              </div>
              <div class="mobile-foot-row">
                <span>© {{ year }} KhanaBook.</span>
                <a href="https://khanabook.in" target="_blank" rel="noopener" class="mobile-foot-link">khanabook.in ↗</a>
              </div>
            </div>
          </ng-container>

          <ng-container *ngIf="forgotStep === 'phone'">
            <header class="auth-head">
              <h2>Forgot password</h2>
              <p class="muted">Enter your registered phone number to receive an OTP.</p>
            </header>
            <form [formGroup]="phoneForm" (ngSubmit)="submitPhone()" class="auth-form" novalidate>
              <div class="field">
                <label for="forgot-phone" class="field-label">PHONE NUMBER</label>
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
              <div *ngIf="forgotError" class="alert-box error" role="alert">{{ forgotError }}</div>
              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="phoneForm.invalid || forgotLoading">
                <span *ngIf="!forgotLoading">Send OTP &rarr;</span>
                <span *ngIf="forgotLoading" class="btn-spinner-row">
                  <svg class="spinning" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                  Sending OTP…
                </span>
              </button>
            </form>
            <a href="#login" class="back-link" (click)="backToLogin($event)" role="button">← Back to sign in</a>
          </ng-container>

          <ng-container *ngIf="forgotStep === 'otp'">
            <header class="auth-head">
              <h2>Enter OTP</h2>
              <p class="muted">If this number is registered, you'll receive a 6-digit code shortly.</p>
            </header>
            <form [formGroup]="otpForm" (ngSubmit)="submitOtp()" class="auth-form" novalidate>
              <div class="field">
                <label for="forgot-otp" class="field-label">OTP CODE</label>
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
              <div *ngIf="forgotError" class="alert-box error" role="alert">{{ forgotError }}</div>
              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="otpForm.invalid || forgotLoading">
                <span *ngIf="!forgotLoading">Verify OTP &rarr;</span>
                <span *ngIf="forgotLoading" class="btn-spinner-row">
                  <svg class="spinning" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                  Verifying…
                </span>
              </button>
            </form>
            <a href="#login" class="back-link" (click)="backToLogin($event)" role="button">← Back to sign in</a>
          </ng-container>

          <ng-container *ngIf="forgotStep === 'password'">
            <header class="auth-head">
              <h2>Reset password</h2>
              <p class="muted">Enter a new password (minimum 6 characters).</p>
            </header>
            <form [formGroup]="passwordForm" (ngSubmit)="submitNewPassword()" class="auth-form" novalidate>
              <div class="field">
                <label for="reset-new-password" class="field-label">NEW PASSWORD</label>
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
                <label for="reset-confirm-password" class="field-label">CONFIRM PASSWORD</label>
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
                  />
                </div>
              </div>
              <div *ngIf="passwordMismatch && passwordForm.get('confirmPassword')?.touched" class="alert-box error" role="alert">
                Passwords do not match.
              </div>
              <div *ngIf="forgotError" class="alert-box error" role="alert">{{ forgotError }}</div>
              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="passwordForm.invalid || passwordMismatch || forgotLoading">
                <span *ngIf="!forgotLoading">Reset password &rarr;</span>
                <span *ngIf="forgotLoading" class="btn-spinner-row">
                  <svg class="spinning" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                  Resetting…
                </span>
              </button>
            </form>
            <a href="#login" class="back-link" (click)="backToLogin($event)" role="button">← Back to sign in</a>
          </ng-container>

          <ng-container *ngIf="forgotStep === 'success'">
            <header class="auth-head">
              <h2>Password reset</h2>
              <p class="muted">Your password has been changed. You can now sign in with your new password.</p>
            </header>
            <a href="#login" class="back-link" (click)="backToLogin($event)" role="button">← Back to sign in</a>
          </ng-container>
        </div>
      </main>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .auth-shell {
      min-height: 100vh;
      min-height: 100dvh;
      display: grid;
      grid-template-columns: 1.1fr 1fr;
      background: #FFFFFF;
      overflow-x: hidden;
    }
    @media (max-width: 960px) {
      .auth-shell { grid-template-columns: 1fr; }
      .auth-brand { display: none !important; }
      .mobile-brand-container { display: block !important; }
      .mobile-foot-row { display: flex !important; }
    }
    .auth-brand {
      padding: clamp(2rem, 4vw, 3.5rem) clamp(2rem, 3.8vw, 3.2rem);
      background: linear-gradient(145deg, var(--kb-color-espresso) 0%, var(--kb-color-primary-hover) 40%, var(--kb-color-primary-hover) 75%, var(--kb-color-primary-hover) 100%);
      color: #FFFFFF;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      position: relative;
    }
    .brand-top { display: flex; align-items: center; justify-content: space-between; }
    .brand-mark { display: flex; align-items: center; gap: 0.75rem; text-decoration: none; }
    .brand-mark:hover .brand-title { opacity: 0.95; }
    .brand-logo {
      width: 42px; height: 42px; border-radius: 10px;
      background: #FFFFFF;
      display: grid; place-items: center;
      padding: 4px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
    }
    .brand-logo--sm {
      width: 38px; height: 38px; border-radius: 8px;
      padding: 3px;
    }
    .brand-title {
      font-family: var(--kb-font-display);
      font-weight: 800; font-size: 1.3rem; letter-spacing: -0.02em; color: #FFFFFF;
    }
    .brand-title--dark { color: var(--kb-color-foreground); font-size: 1.2rem; }
    .brand-copy { max-width: none; display: grid; gap: 1rem; margin: clamp(1.5rem, 3vh, 3rem) 0; }
    .brand-hero-title {
      font-family: var(--kb-font-display);
      font-size: clamp(2rem, 3.2vw, 2.75rem);
      line-height: 1.15;
      font-weight: 800;
      color: #FFFFFF;
      margin: 0;
      letter-spacing: -0.025em;
      text-shadow: 0 2px 10px rgba(0, 0, 0, 0.25);
    }
    .brand-accent-text {
      font-style: italic;
      background: linear-gradient(135deg, var(--kb-color-primary-soft) 0%, var(--kb-color-primary) 50%, var(--kb-color-primary) 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .brand-hero-sub {
      color: rgba(255, 255, 255, 0.88);
      line-height: 1.55;
      margin: 0;
      font-size: 1rem;
      text-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
    }
    .brand-feature-stack {
      display: grid; gap: 0.85rem; margin-top: 1rem;
    }
    .brand-feature-card {
      display: flex; align-items: flex-start; gap: 14px;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.14);
      padding: 12px 16px;
      border-radius: 12px;
      backdrop-filter: blur(10px);
      -webkit-backdrop-filter: blur(10px);
      transition: background 0.15s ease, transform 0.15s ease;
    }
    .brand-feature-card:hover {
      background: rgba(255, 255, 255, 0.12);
      transform: translateY(-1px);
    }
    .feature-icon-badge {
      width: 36px; height: 36px; border-radius: 10px;
      background: #FFFFFF; color: var(--kb-color-primary);
      display: grid; place-items: center; flex-shrink: 0;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
    }
    .feature-text {
      display: flex; flex-direction: column; gap: 3px;
    }
    .feature-heading {
      font-size: 0.88rem; font-weight: 700; color: #FFFFFF; line-height: 1.35;
    }
    .feature-sub {
      font-size: 0.78rem; color: rgba(255, 255, 255, 0.82); line-height: 1.3;
    }
    .brand-foot { font-size: 0.82rem; color: rgba(255, 255, 255, 0.85); margin: 0; }
    .brand-foot-row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-top: 1rem; }
    .brand-link { color: rgba(255, 255, 255, 0.92); font-size: 0.82rem; font-weight: 600; text-decoration: none; }
    .brand-link:hover { color: #FFFFFF; text-decoration: underline; }

    .auth-main {
      display: grid; place-items: center; padding: clamp(1.5rem, 3.5vw, 3rem);
      overflow-x: hidden;
      width: 100%;
      box-sizing: border-box;
      background: #FFFFFF;
    }
    .auth-card {
      width: min(420px, 100%);
      display: grid; gap: 1.25rem;
      box-sizing: border-box;
    }
    .mobile-brand-container {
      display: none;
      margin-bottom: 0.5rem;
    }
    .mobile-brand {
      display: inline-flex; align-items: center; gap: 0.75rem; text-decoration: none;
    }
    .mobile-brand-meta { display: flex; flex-direction: column; }
    .brand-subtitle { font-size: 0.72rem; color: var(--kb-color-primary); font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; }

    .auth-head h2 {
      margin: 0 0 0.35rem;
      font-family: var(--kb-font-display);
      font-size: clamp(1.6rem, 2.2vw, 1.85rem);
      font-weight: 800;
      color: var(--kb-color-foreground);
      letter-spacing: -0.025em;
    }
    .auth-head p { margin: 0; color: var(--kb-color-muted-foreground); font-size: 0.92rem; }
    .auth-form { display: grid; gap: 1.15rem; }

    .auth-card-footer {
      margin-top: 0.5rem;
      padding-top: 1rem;
      border-top: 1px solid var(--kb-color-surface-2);
      text-align: center;
      font-size: 0.85rem;
    }
    .footer-links { margin: 0; color: var(--kb-color-muted-foreground); font-size: 0.86rem; }
    .trust-badge-row {
      display: flex; align-items: center; justify-content: center; gap: 6px;
      font-size: 0.76rem; color: var(--kb-color-muted-foreground); margin-top: 0.75rem;
    }
    .trust-icon { font-size: 0.85rem; }
    .mobile-foot-row {
      display: none;
      font-size: 0.76rem; color: var(--kb-color-muted-foreground); margin-top: 0.5rem;
      justify-content: center; gap: 6px;
    }
    .mobile-foot-link { color: var(--kb-color-primary); font-weight: 600; text-decoration: none; }
    .mobile-foot-link:hover { text-decoration: underline; color: var(--kb-color-primary-hover); }

    .field { display: grid; gap: 0.35rem; }
    .field-row { display: flex; justify-content: space-between; align-items: center; }
    .field-label {
      font-size: 0.72rem; font-weight: 700; letter-spacing: 0.05em;
      text-transform: uppercase; color: var(--kb-color-muted-foreground);
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
      border: 1.5px solid var(--kb-color-border);
      border-radius: 10px;
      padding: 0.65rem 1rem;
      background: #FFFFFF;
      font-size: 0.93rem;
      color: var(--kb-color-foreground);
      height: 46px;
      box-sizing: border-box;
      width: 100%;
      transition: border-color .15s ease, box-shadow .15s ease;
    }
    .field-input--with-icon {
      padding-left: 42px;
    }
    .field-input--with-trailing {
      padding-right: 48px;
    }
    .field-input:focus {
      outline: none;
      border-color: var(--kb-color-primary);
      box-shadow: 0 0 0 3.5px rgba(108, 39, 217, 0.14);
    }
    .field-input::placeholder {
      color: var(--kb-color-muted-foreground);
    }

    .divider {
      display: flex; align-items: center; gap: 0.75rem;
      color: var(--kb-color-muted-foreground); font-size: 0.78rem; font-weight: 500; margin: 0.25rem 0;
    }
    .divider::before, .divider::after {
      content: ''; flex: 1; height: 1px; background: var(--kb-color-border);
    }

    .google-wrap { display: grid; gap: 0.5rem; width: 100%; }
    #google-btn {
      display: flex; justify-content: center; width: 100%; max-width: 100%;
      min-height: 44px; overflow: hidden;
    }
    #google-btn > div, #google-btn iframe { max-width: 100% !important; }
    .google-loading {
      text-align: center; font-size: 0.82rem; color: var(--kb-color-muted-foreground);
      padding: 0.25rem 0;
    }

    .link-right {
      color: var(--kb-color-primary);
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      background: none;
      border: none;
      padding: 0;
      margin: 0;
      font-family: inherit;
      display: inline-flex;
      align-items: center;
      transition: color 0.15s ease;
    }
    .link-right:hover { color: var(--kb-color-primary-hover); text-decoration: underline; }
    .link-right:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: 2px; border-radius: 4px; }

    .back-link {
      color: var(--kb-color-muted-foreground);
      font-size: 0.84rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      background: none;
      border: none;
      padding: 0;
      margin: 0;
      font-family: inherit;
      min-height: 44px;
      display: inline-flex;
      align-items: center;
    }
    .back-link:hover { color: var(--kb-color-primary); text-decoration: underline; }
    .back-link:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: 2px; border-radius: 4px; }

    .primary-btn--block { width: 100%; justify-content: center; height: 48px; font-size: 0.95rem; }
    .primary-btn--hero {
      background: linear-gradient(135deg, var(--kb-color-primary) 0%, var(--kb-color-primary) 100%);
      color: #FFFFFF;
      font-weight: 700;
      letter-spacing: 0.01em;
      border: none;
      box-shadow: 0 4px 14px rgba(93, 69, 253, 0.35);
      border-radius: 10px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      transition: all 0.15s ease;
    }
    .primary-btn--hero:hover:not(:disabled) {
      background: linear-gradient(135deg, var(--kb-color-primary-hover) 0%, var(--kb-color-primary-hover) 100%);
      transform: translateY(-1px);
      box-shadow: 0 6px 20px rgba(93, 69, 253, 0.45);
    }
    .primary-btn--hero:active:not(:disabled) {
      transform: translateY(0);
      box-shadow: 0 2px 8px rgba(93, 69, 253, 0.25);
    }
    .primary-btn--hero:disabled {
      background: var(--kb-color-primary-soft);
      color: var(--kb-color-primary);
      border: 1px solid var(--kb-color-primary-soft);
      box-shadow: none;
      cursor: not-allowed;
      transform: none;
    }

    .btn-spinner-row {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      justify-content: center;
    }
    .spinning {
      animation: spin 1s linear infinite;
    }
    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    .toggle-pwd-btn {
      position: absolute;
      right: 4px;
      background: none;
      border: none;
      cursor: pointer;
      font-size: 1rem;
      line-height: 1;
      min-width: 40px;
      min-height: 40px;
      padding: 8px;
      color: var(--kb-color-muted-foreground);
      transition: color 0.15s ease;
      display: grid;
      place-items: center;
      z-index: 1;
    }
    .toggle-pwd-btn:hover { color: var(--kb-color-muted-foreground); }
    .pwd-icon { display: block; }
    .toggle-pwd-btn:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: -2px; border-radius: 4px; color: var(--kb-color-muted-foreground); }
    .alert-box { border-radius: 8px; padding: 0.75rem 1rem; font-size: 0.85rem; }
    .alert-box.error { color: var(--kb-color-danger); background: var(--kb-color-danger-soft); border: 1px solid var(--kb-color-danger-soft); }
    .alert-box.success { color: var(--kb-color-success); background: var(--kb-color-success-soft); border: 1px solid var(--kb-color-success-soft); }

    @media (prefers-contrast: more) {
      .field-input { border-color: var(--kb-color-foreground); border-width: 2px; }
      .field-label { color: #000000; font-weight: 700; }
      .link-right, .back-link, .mobile-foot-link { color: var(--kb-color-primary-hover); text-decoration: underline; font-weight: 700; }
      .primary-btn--hero { background: var(--kb-color-primary-hover); color: #FFFFFF; border: 2px solid #000000; }
      .divider { color: #000000; font-weight: 700; }
      .divider::before, .divider::after { background: var(--kb-color-foreground); height: 2px; }
    }
  `]
})
export class LoginPageComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly ngZone = inject(NgZone);

  readonly year = new Date().getFullYear();
  readonly showPassword = signal(false);

  readonly form = this.fb.nonNullable.group({
    loginId: ['', Validators.required],
    password: ['', Validators.required]
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

  loading = false;
  error = '';
  googleError = '';
  googleLoading = false;
  loginSuccessMessage = '';

  forgotStep: ForgotStep = 'none';
  forgotLoading = false;
  forgotError = '';
  private forgotPhone = '';
  private tempToken = '';
  private googleTimer: ReturnType<typeof setTimeout> | null = null;

  get passwordMismatch(): boolean {
    const { newPassword, confirmPassword } = this.passwordForm.getRawValue();
    return confirmPassword.length > 0 && newPassword !== confirmPassword;
  }

  ngOnInit(): void {
    const lastLoginId = sessionStorage.getItem('last_login_id');
    if (lastLoginId) {
      this.form.patchValue({ loginId: lastLoginId });
    }
    this.initGoogle();
  }

  private initGoogle(): void {
    let attempts = 0;
    const maxAttempts = 40; // ~12s at 300ms
    const check = () => {
      attempts++;
      const target = document.getElementById('google-btn');
      const gsiReady = typeof google !== 'undefined' && google?.accounts?.id;
      // Render only once BOTH the GIS script is ready AND the target element
      // exists in the DOM. The #google-btn div lives inside *ngIf="forgotStep
      // === 'none'", so it may not be present on the first tick — calling
      // renderButton(null, ...) makes GIS log "no parent" and draw nothing.
      if (gsiReady && target) {
        google.accounts.id.initialize({
          client_id: environment.googleClientId,
          callback: (response: any) => {
            this.ngZone.run(() => this.handleGoogleCredential(response.credential));
          }
        });
        // GIS renders an iframe. Clamping width to actual container width prevents
        // horizontal overflow on small mobile screens (<400px down to 320px).
        const containerWidth = Math.round(
          target.clientWidth || target.parentElement?.clientWidth || (window.innerWidth - 48)
        );
        const targetWidth = Math.max(200, Math.min(380, containerWidth));
        google.accounts.id.renderButton(
          target,
          { theme: 'outline', size: 'large', width: targetWidth, text: 'signin_with' }
        );
        return;
      }
      if (attempts < maxAttempts) {
        this.googleTimer = setTimeout(check, 300);
      }
    };
    check();
  }

  ngOnDestroy(): void {
    if (this.googleTimer !== null) {
      clearTimeout(this.googleTimer);
      this.googleTimer = null;
    }
  }

  private handleGoogleCredential(idToken: string): void {
    this.googleError = '';
    this.googleLoading = true;
    this.authService.googleLogin(idToken).subscribe({
      next: () => { this.googleLoading = false; },
      error: (err) => {
        this.googleLoading = false;
        this.googleError = err?.error?.error ?? err?.error?.message ?? 'Google sign-in failed.';
      }
    });
  }

  submit(): void {
    if (this.form.invalid || this.loading) return;
    this.loading = true;
    this.error = '';
    this.loginSuccessMessage = '';
    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => { this.loading = false; },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.error || err?.error?.message || 'Login failed.';
      }
    });
  }

  startForgotPassword(event?: Event): void {
    if (event) event.preventDefault();
    this.forgotStep = 'phone';
    this.forgotError = '';
    this.forgotLoading = false;
    this.loginSuccessMessage = '';
    this.phoneForm.reset();
    this.otpForm.reset();
    this.passwordForm.reset();
  }

  backToLogin(event?: Event): void {
    if (event) event.preventDefault();
    this.forgotStep = 'none';
    this.forgotError = '';
    this.forgotLoading = false;
    this.forgotPhone = '';
    this.tempToken = '';
  }

  submitPhone(): void {
    if (this.phoneForm.invalid || this.forgotLoading) return;
    this.forgotLoading = true;
    this.forgotError = '';
    this.forgotPhone = this.phoneForm.getRawValue().phone;

    this.authService.requestPasswordOtp(this.forgotPhone).subscribe({
      next: () => {
        this.forgotLoading = false;
        this.forgotStep = 'otp';
        this.forgotError = '';
      },
      error: () => {
        // Server always returns 200 to prevent user enumeration.
        // Move to OTP step regardless — if the number isn't registered, no OTP arrives.
        this.forgotLoading = false;
        this.forgotStep = 'otp';
        this.forgotError = '';
      }
    });
  }

  submitOtp(): void {
    if (this.otpForm.invalid || this.forgotLoading) return;
    this.forgotLoading = true;
    this.forgotError = '';
    const otp = this.otpForm.getRawValue().otp;

    this.authService.verifyPasswordOtp(this.forgotPhone, otp).subscribe({
      next: (res) => {
        this.forgotLoading = false;
        this.tempToken = res.tempToken;
        this.forgotStep = 'password';
      },
      error: (err) => {
        this.forgotLoading = false;
        this.forgotError = err?.error?.error || err?.error?.message || 'Invalid or expired OTP. Please try again.';
      }
    });
  }

  submitNewPassword(): void {
    const { newPassword, confirmPassword } = this.passwordForm.getRawValue();
    if (this.passwordForm.invalid
        || !isPasswordResetSubmissionValid(newPassword, confirmPassword)
        || this.forgotLoading) return;
    this.forgotLoading = true;
    this.forgotError = '';

    this.authService.resetPassword(this.tempToken, newPassword).subscribe({
      next: () => {
        this.forgotLoading = false;
        this.forgotStep = 'success';
      },
      error: (err) => {
        this.forgotLoading = false;
        this.forgotError = err?.error?.error || err?.error?.message || 'Failed to reset password. Please try again.';
      }
    });
  }
}
