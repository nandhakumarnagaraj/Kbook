import { CommonModule } from '@angular/common';
import { Component, OnInit, NgZone, inject, signal } from '@angular/core';
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
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="auth-shell">
      <aside class="auth-brand" style="background-image: var(--gradient-hero);">
        <div class="brand-top">
          <div class="brand-mark">
            <div class="brand-logo">K</div>
            <span class="brand-title">KhanaBook</span>
          </div>
        </div>

        <div class="brand-copy">
          <h1>Run your kitchen<br/>with confidence.</h1>
          <p>One workspace for menu, orders, staff, and payments — built for busy owners and managers.</p>

          <ul class="brand-points">
            <li>
              <span class="point-num">01</span>
              <span>Live orders across POS &amp; marketplaces</span>
            </li>
            <li>
              <span class="point-num">02</span>
              <span>Menu &amp; stock control in seconds</span>
            </li>
            <li>
              <span class="point-num">03</span>
              <span>Daily revenue &amp; refund insights</span>
            </li>
          </ul>
        </div>

        <p class="brand-foot">© {{ year }} KhanaBook. All rights reserved.</p>
      </aside>

      <main class="auth-main">
        <div class="auth-card">
          <div class="mobile-brand">
            <div class="brand-logo brand-logo--sm">K</div>
            <span class="brand-title">KhanaBook</span>
          </div>

          <div *ngIf="loginSuccessMessage" class="alert-box success">{{ loginSuccessMessage }}</div>

          <ng-container *ngIf="forgotStep === 'none'">
            <header class="auth-head">
              <h2>Welcome back</h2>
              <p class="muted">Sign in to your admin workspace.</p>
            </header>

            <div class="google-wrap">
              <div id="google-btn"></div>
              <div *ngIf="googleError" class="alert-box error">{{ googleError }}</div>
            </div>

            <div class="divider"><span>or with password</span></div>

            <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form">
              <label class="field">
                <span class="field-label">Login ID</span>
                <input class="field-input" formControlName="loginId" placeholder="Phone number or email" autocomplete="username">
              </label>

              <label class="field">
                <div class="field-row">
                  <span class="field-label">Password</span>
                  <a class="link-right" (click)="startForgotPassword()">Forgot password?</a>
                </div>
                <div class="input-with-action">
                  <input
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
                  >
                    {{ showPassword() ? '🙈' : '👁️' }}
                  </button>
                </div>
              </label>

              <div *ngIf="error" class="alert-box error">{{ error }}</div>

              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="form.invalid || loading">
                {{ loading ? 'Signing in…' : 'Sign in' }}
              </button>
            </form>
          </ng-container>

          <ng-container *ngIf="forgotStep === 'phone'">
            <header class="auth-head">
              <h2>Forgot password</h2>
              <p class="muted">Enter your registered phone number to receive an OTP.</p>
            </header>
            <form [formGroup]="phoneForm" (ngSubmit)="submitPhone()" class="auth-form">
              <label class="field">
                <span class="field-label">Phone number</span>
                <input class="field-input" formControlName="phone" placeholder="10-digit phone number" maxlength="10" inputmode="numeric">
              </label>
              <div *ngIf="forgotError" class="alert-box error">{{ forgotError }}</div>
              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="phoneForm.invalid || forgotLoading">
                {{ forgotLoading ? 'Sending OTP…' : 'Send OTP' }}
              </button>
            </form>
            <a class="back-link" (click)="backToLogin()">← Back to sign in</a>
          </ng-container>

          <ng-container *ngIf="forgotStep === 'otp'">
            <header class="auth-head">
              <h2>Enter OTP</h2>
              <p class="muted">If this number is registered, you'll receive a 6-digit code shortly.</p>
            </header>
            <form [formGroup]="otpForm" (ngSubmit)="submitOtp()" class="auth-form">
              <label class="field">
                <span class="field-label">OTP code</span>
                <input class="field-input" formControlName="otp" placeholder="6-digit OTP" maxlength="6" inputmode="numeric">
              </label>
              <div *ngIf="forgotError" class="alert-box error">{{ forgotError }}</div>
              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="otpForm.invalid || forgotLoading">
                {{ forgotLoading ? 'Verifying…' : 'Verify OTP' }}
              </button>
            </form>
            <a class="back-link" (click)="backToLogin()">← Back to sign in</a>
          </ng-container>

          <ng-container *ngIf="forgotStep === 'password'">
            <header class="auth-head">
              <h2>Reset password</h2>
              <p class="muted">Enter a new password (minimum 6 characters).</p>
            </header>
            <form [formGroup]="passwordForm" (ngSubmit)="submitNewPassword()" class="auth-form">
              <label class="field">
                <span class="field-label">New password</span>
                <input class="field-input" type="password" formControlName="newPassword" placeholder="Min 6 characters" autocomplete="new-password">
              </label>
              <label class="field">
                <span class="field-label">Confirm password</span>
                <input class="field-input" type="password" formControlName="confirmPassword" placeholder="Re-enter password" autocomplete="new-password">
              </label>
              <div *ngIf="passwordMismatch && passwordForm.get('confirmPassword')?.touched" class="alert-box error">
                Passwords do not match.
              </div>
              <div *ngIf="forgotError" class="alert-box error">{{ forgotError }}</div>
              <button class="primary-btn primary-btn--block primary-btn--hero" [disabled]="passwordForm.invalid || passwordMismatch || forgotLoading">
                {{ forgotLoading ? 'Resetting…' : 'Reset password' }}
              </button>
            </form>
            <a class="back-link" (click)="backToLogin()">← Back to sign in</a>
          </ng-container>

          <ng-container *ngIf="forgotStep === 'success'">
            <header class="auth-head">
              <h2>Password reset</h2>
              <p class="muted">Your password has been changed. You can now sign in with your new password.</p>
            </header>
            <a class="back-link" (click)="backToLogin()">← Back to sign in</a>
          </ng-container>
        </div>
      </main>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .auth-shell {
      min-height: 100vh;
      display: grid;
      grid-template-columns: 1fr 1fr;
      background: var(--kb-color-surface);
    }
    @media (max-width: 960px) {
      .auth-shell { grid-template-columns: 1fr; }
      .auth-brand { display: none; }
    }
    .auth-brand {
      padding: var(--kb-space-6) var(--kb-space-5);
      color: var(--kb-color-foreground);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      position: relative;
    }
    .brand-top { display: flex; align-items: center; justify-content: space-between; }
    .brand-mark { display: flex; align-items: center; gap: var(--kb-space-3); }
    .brand-logo {
      width: 44px; height: 44px; border-radius: var(--kb-radius-md);
      background: rgba(59, 130, 246, 0.12); backdrop-filter: blur(8px);
      display: grid; place-items: center;
      font-family: var(--font-display); font-weight: 800; font-size: 1.15rem; color: var(--kb-color-primary);
    }
    .brand-logo--sm {
      width: 38px; height: 38px; border-radius: var(--kb-radius-sm);
      background: var(--kb-color-primary); font-size: 1rem;
    }
    .brand-title { font-family: var(--font-display); font-weight: 800; font-size: 1.15rem; letter-spacing: -0.01em; color: var(--kb-color-primary-foreground); }
    .brand-copy { max-width: none; display: grid; gap: var(--kb-space-4); }
    .brand-copy h1 {
      font-size: clamp(2rem, 3.2vw, 2.75rem);
      line-height: 1.15;
      font-weight: 800;
      color: var(--kb-color-primary-foreground);
      margin: 0;
      letter-spacing: -0.02em;
    }
    .brand-copy p {
      color: rgba(26, 26, 26, 0.88);
      line-height: 1.55;
      margin: 0;
      font-size: 0.98rem;
    }
    .brand-points {
      list-style: none; padding: 0; margin: var(--kb-space-3) 0 0; display: grid; gap: var(--kb-space-2);
    }
    .brand-points li {
      display: flex; align-items: center; gap: var(--kb-space-2);
      font-size: 0.9rem; color: var(--kb-color-foreground);
    }
    .point-num {
      width: 28px; height: 28px; border-radius: var(--kb-radius-sm);
      background: rgba(59, 130, 246, 0.12); display: grid; place-items: center;
      font-size: 0.75rem; font-weight: 700; color: var(--kb-color-primary-foreground); flex-shrink: 0;
    }
    .brand-foot { font-size: 0.78rem; color: rgba(26, 26, 26, 0.7); margin: 0; }

    .auth-main {
      display: grid; place-items: center; padding: var(--kb-space-6) var(--kb-space-5);
    }
    .auth-card {
      width: min(420px, 100%);
      display: grid; gap: var(--kb-space-4);
    }
    .mobile-brand {
      display: none; align-items: center; gap: var(--kb-space-2); margin-bottom: var(--kb-space-2);
    }
    @media (max-width: 960px) { .mobile-brand { display: flex; } }
    .auth-head h2 { margin: 0 0 var(--kb-space-2); font-size: calc(1.4rem + 0.3vw); font-weight: 700; color: var(--kb-color-foreground); }
    .auth-head p { margin: 0; color: var(--kb-color-muted); }
    .auth-form { display: grid; gap: var(--kb-space-4); }
    .field { display: grid; gap: var(--kb-space-2); }
    .field-row { display: flex; justify-content: space-between; align-items: center; }
    .field-label { font-weight: 600; font-size: 0.82rem; color: var(--kb-color-foreground); }
    .field-input {
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
      padding: var(--kb-space-2) var(--kb-space-3);
      background: var(--kb-color-surface);
      font-size: 0.92rem;
      color: var(--kb-color-foreground);
      height: 44px;
      transition: border-color .15s ease, box-shadow .15s ease;
    }
    .field-input:focus {
      outline: none;
      border-color: var(--kb-color-primary);
      box-shadow: 0 0 0 3px var(--kb-color-primary-soft);
    }
    .divider {
      display: flex; align-items: center; gap: var(--kb-space-3);
      color: var(--kb-color-muted); font-size: 0.78rem; margin: var(--kb-space-2) 0;
    }
    .divider::before, .divider::after {
      content: ''; flex: 1; height: 1px; background: var(--kb-color-border);
    }
    .google-wrap { display: grid; gap: var(--kb-space-3); }
    #google-btn { display: flex; justify-content: center; }
    .link-right {
      color: var(--kb-color-primary);
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
    }
    .link-right:hover { text-decoration: underline; }
    .back-link {
      color: var(--kb-color-muted);
      font-size: 0.84rem;
      cursor: pointer;
      text-decoration: none;
      justify-self: start;
    }
    .back-link:hover { color: var(--kb-color-primary); text-decoration: underline; }
    .primary-btn--block { width: 100%; justify-content: center; padding: var(--kb-space-2) var(--kb-space-3); height: 44px; font-size: 0.88rem; }
    .primary-btn--hero {
      background: linear-gradient(135deg, var(--kb-color-primary) 0%, #60A5FA 100%);
      border: none;
      box-shadow: var(--kb-shadow-lg);
    }
    .primary-btn--hero:hover {
      transform: translateY(-1px);
    }
    .input-with-action {
      position: relative;
      display: flex;
      align-items: center;
    }
    .input-with-action .field-input {
      padding-right: var(--kb-space-4);
    }
    .toggle-pwd-btn {
      position: absolute;
      right: var(--kb-space-2);
      background: none;
      border: none;
      cursor: pointer;
      font-size: 1rem;
      padding: var(--kb-space-1);
      opacity: 0.7;
      transition: opacity 0.15s ease;
    }
    .toggle-pwd-btn:hover { opacity: 1; }
    .alert-box { border-radius: var(--kb-radius-md); padding: var(--kb-space-3) var(--kb-space-4); font-size: 0.85rem; }
    .alert-box.error { color: var(--kb-color-error); background: var(--kb-color-surface-2); border: 1px solid rgba(239, 68, 68, 0.15); }
    .alert-box.success { color: var(--kb-color-success); background: var(--kb-color-surface-2); border: 1px solid rgba(16, 185, 129, 0.15); }
  `]
})
export class LoginPageComponent implements OnInit {
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
  loginSuccessMessage = '';

  forgotStep: ForgotStep = 'none';
  forgotLoading = false;
  forgotError = '';
  private forgotPhone = '';
  private tempToken = '';

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
        google.accounts.id.renderButton(
          target,
          { theme: 'outline', size: 'large', width: 380, text: 'signin_with' }
        );
        return;
      }
      if (attempts < maxAttempts) {
        setTimeout(check, 300);
      }
    };
    check();
  }

  private handleGoogleCredential(idToken: string): void {
    this.googleError = '';
    this.authService.googleLogin(idToken).subscribe({
      error: (err) => {
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

  startForgotPassword(): void {
    this.forgotStep = 'phone';
    this.forgotError = '';
    this.forgotLoading = false;
    this.loginSuccessMessage = '';
    this.phoneForm.reset();
    this.otpForm.reset();
    this.passwordForm.reset();
  }

  backToLogin(): void {
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
