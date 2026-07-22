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
              <p class="muted">Enter the 4-digit code sent to your phone.</p>
            </header>
            <form [formGroup]="otpForm" (ngSubmit)="submitOtp()" class="auth-form">
              <label class="field">
                <span class="field-label">OTP code</span>
                <input class="field-input" formControlName="otp" placeholder="4-digit OTP" maxlength="4" inputmode="numeric">
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
      background: var(--bg);
    }
    @media (max-width: 960px) {
      .auth-shell { grid-template-columns: 1fr; }
      .auth-brand { display: none; }
    }
    .auth-brand {
      padding: 3.5rem 3rem;
      color: #fff;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      position: relative;
    }
    .brand-top { display: flex; align-items: center; justify-content: space-between; }
    .brand-mark { display: flex; align-items: center; gap: 0.75rem; }
    .brand-logo {
      width: 44px; height: 44px; border-radius: 12px;
      background: rgba(255, 255, 255, 0.18);
      backdrop-filter: blur(8px);
      display: grid; place-items: center;
      font-family: var(--font-display); font-weight: 800; font-size: 1.15rem; color: #fff;
    }
    .brand-logo--sm {
      width: 38px; height: 38px; border-radius: 10px;
      background: var(--gradient-primary); font-size: 1rem;
    }
    .brand-title { font-family: var(--font-display); font-weight: 800; font-size: 1.15rem; letter-spacing: -0.01em; }
    .brand-copy { max-width: 420px; display: grid; gap: 1.25rem; }
    .brand-copy h1 {
      font-size: clamp(2rem, 3.2vw, 2.75rem);
      line-height: 1.15;
      font-weight: 800;
      color: #fff;
      margin: 0;
      letter-spacing: -0.02em;
    }
    .brand-copy p {
      color: rgba(255, 255, 255, 0.88);
      line-height: 1.55;
      margin: 0;
      font-size: 0.98rem;
    }
    .brand-points {
      list-style: none; padding: 0; margin: 0.5rem 0 0; display: grid; gap: 0.65rem;
    }
    .brand-points li {
      display: flex; align-items: center; gap: 0.75rem;
      font-size: 0.9rem; color: rgba(255, 255, 255, 0.92);
    }
    .point-num {
      width: 28px; height: 28px; border-radius: 6px;
      background: rgba(255, 255, 255, 0.16);
      display: grid; place-items: center;
      font-size: 0.75rem; font-weight: 700; color: #fff; flex-shrink: 0;
    }
    .brand-foot { font-size: 0.78rem; color: rgba(255, 255, 255, 0.7); margin: 0; }

    .auth-main {
      display: grid; place-items: center; padding: 2.5rem 1.5rem;
    }
    .auth-card {
      width: min(420px, 100%);
      display: grid; gap: 1.25rem;
    }
    .mobile-brand {
      display: none; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem;
    }
    @media (max-width: 960px) { .mobile-brand { display: flex; } }
    .auth-head h2 { margin: 0 0 0.35rem; font-size: 1.6rem; font-weight: 700; }
    .auth-head p { margin: 0; }
    .auth-form { display: grid; gap: 1rem; }
    .field { display: grid; gap: 0.35rem; }
    .field-row { display: flex; justify-content: space-between; align-items: center; }
    .field-label { font-weight: 600; font-size: 0.82rem; color: var(--ink); }
    .field-input {
      border: 1px solid var(--line-strong);
      border-radius: var(--r-md);
      padding: 0.7rem 0.9rem;
      background: #fff;
      font-size: 0.92rem;
      height: 44px;
      transition: border-color .15s ease, box-shadow .15s ease;
    }
    .field-input:focus {
      outline: none;
      border-color: var(--brand);
      box-shadow: 0 0 0 3px var(--brand-ring);
    }
    .divider {
      display: flex; align-items: center; gap: 0.75rem;
      color: var(--muted); font-size: 0.78rem; margin: 0.25rem 0;
    }
    .divider::before, .divider::after {
      content: ''; flex: 1; height: 1px; background: var(--line);
    }
    .google-wrap { display: grid; gap: 0.5rem; }
    #google-btn { display: flex; justify-content: center; }
    .link-right {
      color: var(--brand);
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
    }
    .link-right:hover { text-decoration: underline; }
    .back-link {
      color: var(--muted);
      font-size: 0.84rem;
      cursor: pointer;
      text-decoration: none;
      justify-self: start;
    }
    .back-link:hover { color: var(--brand); text-decoration: underline; }
    .primary-btn--block { width: 100%; justify-content: center; height: 44px; font-size: 0.9rem; }
    .primary-btn--hero {
      background: var(--gradient-primary);
      border: none;
      box-shadow: var(--shadow-elevated);
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
      width: 100%;
      padding-right: 2.5rem;
    }
    .toggle-pwd-btn {
      position: absolute;
      right: 0.5rem;
      background: none;
      border: none;
      cursor: pointer;
      font-size: 1rem;
      padding: 0.35rem;
      opacity: 0.7;
      transition: opacity 0.15s ease;
    }
    .toggle-pwd-btn:hover { opacity: 1; }
    .alert-box { border-radius: var(--r-md); padding: 0.75rem 0.95rem; font-size: 0.86rem; }
    .alert-box.error { color: var(--danger); background: var(--danger-soft); border: 1px solid rgba(185,28,28,0.2); }
    .alert-box.success { color: var(--success); background: var(--success-soft); border: 1px solid rgba(4,120,87,0.2); }
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
    otp: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]]
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
    this.initGoogle();
  }

  private initGoogle(): void {
    const check = () => {
      if (typeof google !== 'undefined' && google?.accounts?.id) {
        google.accounts.id.initialize({
          client_id: environment.googleClientId,
          callback: (response: any) => {
            this.ngZone.run(() => this.handleGoogleCredential(response.credential));
          }
        });
        google.accounts.id.renderButton(
          document.getElementById('google-btn'),
          { theme: 'outline', size: 'large', width: 380, text: 'signin_with' }
        );
      } else {
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
      },
      error: (err) => {
        this.forgotLoading = false;
        this.forgotError = err?.error?.error || err?.error?.message || 'Failed to send OTP. Please try again.';
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
