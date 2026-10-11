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
          <h1>Run your restaurant<br/>with confidence.</h1>
          <p>One workspace for menu, orders, staff, and payments — built for restaurant owners.</p>

          <ul class="brand-points">
            <li>
              <span class="point-num">01</span>
              <span>Consolidated live sales across all POS terminals</span>
            </li>
            <li>
              <span class="point-num">02</span>
              <span>Menu &amp; recipe stock control in seconds</span>
            </li>
            <li>
              <span class="point-num">03</span>
              <span>Daily revenue, settlements &amp; CA-ready tax reports</span>
            </li>
          </ul>
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
                <label for="login-id" class="field-label">Phone number or email</label>
                <input
                  id="login-id"
                  class="field-input"
                  formControlName="loginId"
                  placeholder="e.g. 98765 43210 or owner@restaurant.com"
                  autocomplete="username"
                  inputmode="text"
                  autocorrect="off"
                  autocapitalize="none"
                />
              </div>

              <div class="field">
                <div class="field-row">
                  <label for="login-password" class="field-label">Password</label>
                  <a
                    href="#forgot"
                    class="link-right"
                    (click)="startForgotPassword($event)"
                    role="button"
                    aria-haspopup="dialog"
                  >Forgot password?</a>
                </div>
                <div class="input-with-action">
                  <input
                    id="login-password"
                    class="field-input"
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
                    <svg *ngIf="showPassword()" class="pwd-icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="20" height="20"><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575a1 1 0 0 1 0 .696a10.8 10.8 0 0 1-1.444 2.49m-6.41-.679a3 3 0 0 1-4.242-4.242"/><path fill="currentColor" d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151a1 1 0 0 1 0-.696a10.75 10.75 0 0 1 4.446-5.143M2 2l20 20"/></g></svg>
                    <svg *ngIf="!showPassword()" class="pwd-icon" aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="20" height="20"><g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"><path fill="currentColor" d="M2.062 12.348a1 1 0 0 1 0-.696a10.75 10.75 0 0 1 19.876 0a1 1 0 0 1 0 .696a10.75 10.75 0 0 1-19.876 0"/><circle fill="currentColor" cx="12" cy="12" r="3"/></g></svg>
                  </button>
                </div>
              </div>

              <div *ngIf="error" class="alert-box error" role="alert">{{ error }}</div>

              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="form.invalid || loading">
                {{ loading ? 'Signing in…' : 'Sign in' }}
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
                <label for="forgot-phone" class="field-label">Phone number</label>
                <input
                  id="forgot-phone"
                  class="field-input"
                  formControlName="phone"
                  placeholder="10-digit mobile number"
                  maxlength="10"
                  autocomplete="tel"
                  inputmode="tel"
                />
              </div>
              <div *ngIf="forgotError" class="alert-box error" role="alert">{{ forgotError }}</div>
              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="phoneForm.invalid || forgotLoading">
                {{ forgotLoading ? 'Sending OTP…' : 'Send OTP' }}
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
                <label for="forgot-otp" class="field-label">OTP code</label>
                <input
                  id="forgot-otp"
                  class="field-input"
                  formControlName="otp"
                  placeholder="6-digit OTP"
                  maxlength="6"
                  autocomplete="one-time-code"
                  inputmode="numeric"
                />
              </div>
              <div *ngIf="forgotError" class="alert-box error" role="alert">{{ forgotError }}</div>
              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="otpForm.invalid || forgotLoading">
                {{ forgotLoading ? 'Verifying…' : 'Verify OTP' }}
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
                <label for="reset-new-password" class="field-label">New password</label>
                <input
                  id="reset-new-password"
                  class="field-input"
                  type="password"
                  formControlName="newPassword"
                  placeholder="Min 6 characters"
                  autocomplete="new-password"
                />
              </div>
              <div class="field">
                <label for="reset-confirm-password" class="field-label">Confirm password</label>
                <input
                  id="reset-confirm-password"
                  class="field-input"
                  type="password"
                  formControlName="confirmPassword"
                  placeholder="Re-enter password"
                  autocomplete="new-password"
                />
              </div>
              <div *ngIf="passwordMismatch && passwordForm.get('confirmPassword')?.touched" class="alert-box error" role="alert">
                Passwords do not match.
              </div>
              <div *ngIf="forgotError" class="alert-box error" role="alert">{{ forgotError }}</div>
              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="passwordForm.invalid || passwordMismatch || forgotLoading">
                {{ forgotLoading ? 'Resetting…' : 'Reset password' }}
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
      grid-template-columns: 1.15fr 1fr;
      background: var(--kb-color-surface);
      overflow-x: hidden;
    }
    @media (max-width: 960px) {
      .auth-shell { grid-template-columns: 1fr; }
      .auth-brand { display: none !important; }
      .mobile-brand-container { display: block !important; }
      .mobile-foot-row { display: flex !important; }
    }
    .auth-brand {
      padding: clamp(2rem, 4vw, 3.5rem) clamp(1.5rem, 3.5vw, 3rem);
      background-image: linear-gradient(145deg, rgba(24, 27, 52, 0.94) 0%, rgba(79, 53, 245, 0.88) 100%), var(--gradient-hero);
      background-size: cover;
      background-position: center;
      color: #FFFFFF;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      position: relative;
    }
    .brand-top { display: flex; align-items: center; justify-content: space-between; }
    .brand-mark { display: flex; align-items: center; gap: var(--kb-space-3); text-decoration: none; }
    .brand-mark:hover .brand-title { opacity: 0.95; }
    .brand-logo {
      width: 44px; height: 44px; border-radius: var(--kb-radius-md);
      background: #FFFFFF;
      display: grid; place-items: center;
      padding: 4px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
    }
    .brand-logo--sm {
      width: 40px; height: 40px; border-radius: var(--kb-radius-md);
      padding: 3px;
    }
    .brand-title { font-family: var(--font-display); font-weight: 800; font-size: 1.25rem; letter-spacing: -0.01em; color: #FFFFFF; }
    .brand-title--dark { color: var(--kb-color-foreground); font-size: 1.2rem; }
    .brand-copy { max-width: none; display: grid; gap: var(--kb-space-4); margin: var(--kb-space-6) 0; }
    .brand-copy h1 {
      font-size: var(--kb-font-size-display, clamp(2rem, 3.2vw, 2.75rem));
      line-height: 1.15;
      font-weight: 800;
      color: #FFFFFF;
      margin: 0;
      letter-spacing: -0.02em;
      text-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
    }
    .brand-copy p {
      color: rgba(255, 255, 255, 0.94);
      line-height: 1.55;
      margin: 0;
      font-size: 1rem;
      text-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
    }
    .brand-points {
      list-style: none; padding: 0; margin: var(--kb-space-3) 0 0; display: grid; gap: var(--kb-space-3);
    }
    .brand-points li {
      display: flex; align-items: center; gap: var(--kb-space-3);
      font-size: 0.92rem; color: #FFFFFF;
      background: rgba(255, 255, 255, 0.10);
      border: 1px solid rgba(255, 255, 255, 0.18);
      padding: 10px 14px;
      border-radius: var(--kb-radius-lg);
      backdrop-filter: blur(8px);
      -webkit-backdrop-filter: blur(8px);
    }
    .point-num {
      width: 26px; height: 26px; border-radius: var(--kb-radius-sm);
      background: #FFFFFF; display: grid; place-items: center;
      font-size: 0.75rem; font-weight: 800; color: #5D45FD; flex-shrink: 0;
    }
    .brand-foot { font-size: 0.8rem; color: rgba(255, 255, 255, 0.9); margin: 0; }
    .brand-foot-row { display: flex; align-items: center; justify-content: space-between; gap: var(--kb-space-3); }
    .brand-link { color: rgba(255, 255, 255, 0.92); font-size: 0.82rem; font-weight: 600; text-decoration: none; }
    .brand-link:hover { color: #FFFFFF; text-decoration: underline; }

    .auth-main {
      display: grid; place-items: center; padding: clamp(1.5rem, 3vw, 2.5rem);
      overflow-x: hidden;
      width: 100%;
      box-sizing: border-box;
    }
    .auth-card {
      width: min(420px, 100%);
      display: grid; gap: var(--kb-space-4);
      box-sizing: border-box;
    }
    .mobile-brand-container {
      display: none;
      margin-bottom: var(--kb-space-2);
    }
    .mobile-brand {
      display: inline-flex; align-items: center; gap: var(--kb-space-3); text-decoration: none;
    }
    .mobile-brand-meta { display: flex; flex-direction: column; }
    .brand-subtitle { font-size: 0.72rem; color: #6C27D9; font-weight: 700; letter-spacing: 0.02em; text-transform: uppercase; }

    .auth-head h2 { margin: 0 0 var(--kb-space-1); font-size: calc(1.4rem + 0.3vw); font-weight: 700; color: var(--kb-color-foreground); }
    .auth-head p { margin: 0; color: var(--kb-color-muted-foreground); font-size: 0.92rem; }
    .auth-form { display: grid; gap: var(--kb-space-4); }

    .auth-card-footer {
      margin-top: var(--kb-space-2);
      padding-top: var(--kb-space-3);
      border-top: 1px solid var(--kb-color-border);
      text-align: center;
      font-size: 0.85rem;
    }
    .footer-links { margin: 0; color: var(--kb-color-muted-foreground); font-size: 0.86rem; }
    .trust-badge-row {
      display: flex; align-items: center; justify-content: center; gap: 6px;
      font-size: 0.76rem; color: #78716C; margin-top: var(--kb-space-3);
    }
    .trust-icon { font-size: 0.85rem; }
    .mobile-foot-row {
      display: none;
      font-size: 0.76rem; color: #78716C; margin-top: var(--kb-space-2);
      justify-content: center; gap: 6px;
    }
    .mobile-foot-link { color: #6C27D9; font-weight: 600; text-decoration: none; }
    .mobile-foot-link:hover { text-decoration: underline; color: #4E37EB; }

    .field { display: grid; gap: var(--kb-space-1); }
    .field-row { display: flex; justify-content: space-between; align-items: center; }
    .field-label { font-weight: 600; font-size: 0.84rem; color: var(--kb-color-foreground); }
    .field-input {
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
      padding: var(--kb-space-2) var(--kb-space-3);
      background: var(--kb-color-surface);
      font-size: 0.92rem;
      color: var(--kb-color-foreground);
      height: 44px;
      box-sizing: border-box;
      width: 100%;
      transition: border-color .15s ease, box-shadow .15s ease;
    }
    .field-input:focus {
      outline: none;
      border-color: #6C27D9;
      box-shadow: 0 0 0 3px rgba(108, 39, 217, 0.18);
    }

    .divider {
      display: flex; align-items: center; gap: var(--kb-space-3);
      color: var(--kb-color-muted-foreground); font-size: 0.78rem; margin: var(--kb-space-2) 0;
    }
    .divider::before, .divider::after {
      content: ''; flex: 1; height: 1px; background: var(--kb-color-border);
    }

    .google-wrap { display: grid; gap: var(--kb-space-3); width: 100%; }
    #google-btn {
      display: flex; justify-content: center; width: 100%; max-width: 100%;
      min-height: 44px; overflow: hidden;
    }
    #google-btn > div, #google-btn iframe { max-width: 100% !important; }
    .google-loading {
      text-align: center; font-size: 0.82rem; color: var(--kb-color-muted-foreground);
      padding: var(--kb-space-2) 0;
    }

    /* KhanaBook Logo Royal Violet: 7.17:1 on white (WCAG AAA compliant) */
    .link-right {
      color: #6C27D9;
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      text-underline-offset: 3px;
      background: none;
      border: none;
      padding: 0;
      margin: 0;
      font-family: inherit;
      display: inline-flex;
      align-items: center;
    }
    .link-right:hover { color: #4E37EB; text-decoration: underline; }
    .link-right:focus-visible { outline: 2px solid #6C27D9; outline-offset: 2px; border-radius: var(--kb-radius-sm); }

    .back-link {
      color: #57534E;
      font-size: 0.84rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      text-underline-offset: 3px;
      background: none;
      border: none;
      padding: 0;
      margin: 0;
      font-family: inherit;
      min-height: 44px;
      display: inline-flex;
      align-items: center;
    }
    .back-link:hover { color: #6C27D9; text-decoration: underline; }
    .back-link:focus-visible { outline: 2px solid #6C27D9; outline-offset: 2px; border-radius: var(--kb-radius-sm); }

    .primary-btn--block { width: 100%; justify-content: center; padding: var(--kb-space-2) var(--kb-space-3); height: 44px; font-size: 0.9rem; }
    /* Primary button: KhanaBook Logo Palette (POS Indigo #5D45FD to Royal Violet #6C27D9, 6.5:1 WCAG AAA) */
    .primary-btn--hero {
      background: linear-gradient(135deg, #5D45FD 0%, #6C27D9 100%);
      color: #FFFFFF;
      font-weight: 600;
      letter-spacing: 0.01em;
      border: 1px solid rgba(255, 255, 255, 0.15);
      box-shadow: 0 4px 14px rgba(93, 69, 253, 0.35);
      border-radius: var(--kb-radius-lg);
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .primary-btn--hero:hover:not(:disabled) {
      background: linear-gradient(135deg, #4E37EB 0%, #5B21B6 100%);
      transform: translateY(-1px);
      box-shadow: 0 6px 20px rgba(93, 69, 253, 0.45);
    }
    .primary-btn--hero:active:not(:disabled) {
      transform: translateY(0);
      box-shadow: 0 2px 8px rgba(93, 69, 253, 0.25);
    }
    .primary-btn--hero:disabled {
      background: #E5E0D8;
      color: #78716C;
      box-shadow: none;
      border: 1px solid #D6D0C5;
      cursor: not-allowed;
      transform: none;
    }

    .input-with-action {
      position: relative;
      display: flex;
      align-items: center;
    }
    .input-with-action .field-input {
      padding-right: 48px;
    }
    .toggle-pwd-btn {
      position: absolute;
      right: var(--kb-space-1);
      background: none;
      border: none;
      cursor: pointer;
      font-size: 1rem;
      line-height: 1;
      min-width: 40px;
      min-height: 40px;
      padding: var(--kb-space-2);
      opacity: 0.7;
      transition: opacity 0.15s ease;
      display: grid;
      place-items: center;
    }
    .toggle-pwd-btn:hover { opacity: 1; }
    .pwd-icon { display: block; }
    .toggle-pwd-btn:focus-visible { outline: 2px solid #6C27D9; outline-offset: -2px; border-radius: var(--kb-radius-sm); opacity: 1; }
    .alert-box { border-radius: var(--kb-radius-md); padding: var(--kb-space-3) var(--kb-space-4); font-size: 0.85rem; }
    .alert-box.error { color: var(--kb-color-error); background: var(--kb-color-surface-2); border: 1px solid rgba(239, 68, 68, 0.2); }
    .alert-box.success { color: var(--kb-color-success); background: var(--kb-color-surface-2); border: 1px solid rgba(16, 185, 129, 0.2); }

    @media (prefers-contrast: more) {
      .field-input { border-color: #1C1A17; border-width: 2px; }
      .field-label { color: #000000; font-weight: 700; }
      .link-right, .back-link, .mobile-foot-link { color: #4321A9; text-decoration: underline; font-weight: 700; }
      .primary-btn--hero { background: #4321A9; color: #FFFFFF; border: 2px solid #000000; }
      .divider { color: #000000; font-weight: 700; }
      .divider::before, .divider::after { background: #1C1A17; height: 2px; }
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
