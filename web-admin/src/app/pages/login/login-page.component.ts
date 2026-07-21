import { CommonModule } from '@angular/common';
import { Component, OnInit, NgZone, inject } from '@angular/core';
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
      <aside class="auth-brand">
        <div class="brand-mark">
          <span class="brand-dot"></span>
          <span>KhanaBook</span>
        </div>
        <div class="brand-copy">
          <h1>Run your kitchen<br/>with confidence.</h1>
          <p>One workspace for menu, orders, staff, and payments — built for busy owners and managers.</p>
        </div>
        <ul class="brand-points">
          <li><span>01</span> Live orders across POS &amp; marketplaces</li>
          <li><span>02</span> Menu &amp; stock control in seconds</li>
          <li><span>03</span> Daily revenue &amp; refund insights</li>
        </ul>
        <p class="brand-foot">© {{ year }} KhanaBook. All rights reserved.</p>
      </aside>

      <main class="auth-main">
        <div class="auth-card">
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
                <span class="field-label">Password</span>
                <input class="field-input" type="password" formControlName="password" placeholder="Enter password" autocomplete="current-password">
              </label>

              <a class="link-right" (click)="startForgotPassword()">Forgot password?</a>

              <div *ngIf="error" class="alert-box error">{{ error }}</div>

              <button class="primary-btn primary-btn--block" [disabled]="form.invalid || loading">
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
              <button class="primary-btn primary-btn--block" [disabled]="phoneForm.invalid || forgotLoading">
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
              <button class="primary-btn primary-btn--block" [disabled]="otpForm.invalid || forgotLoading">
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
              <button class="primary-btn primary-btn--block" [disabled]="passwordForm.invalid || passwordMismatch || forgotLoading">
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
      grid-template-columns: 1.05fr 1fr;
      background: var(--surface-alt, #f7f7f5);
    }
    @media (max-width: 960px) {
      .auth-shell { grid-template-columns: 1fr; }
      .auth-brand { display: none; }
    }
    .auth-brand {
      position: relative;
      padding: 3rem 3.25rem;
      color: #fff;
      background:
        radial-gradient(1200px 500px at 10% 0%, rgba(255,255,255,0.12), transparent 60%),
        radial-gradient(700px 400px at 90% 100%, rgba(255,255,255,0.08), transparent 60%),
        linear-gradient(160deg, #7c2d12 0%, #b45309 55%, #d97706 100%);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      overflow: hidden;
    }
    .auth-brand::after {
      content: '';
      position: absolute;
      inset: auto -20% -30% auto;
      width: 460px;
      height: 460px;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(255,255,255,0.18), transparent 60%);
      pointer-events: none;
    }
    .brand-mark {
      display: inline-flex;
      align-items: center;
      gap: 0.6rem;
      font-weight: 700;
      letter-spacing: 0.02em;
      font-size: 1.05rem;
    }
    .brand-dot {
      width: 12px; height: 12px; border-radius: 4px;
      background: #fff;
      box-shadow: 0 0 0 3px rgba(255,255,255,0.25);
    }
    .brand-copy h1 {
      font-size: clamp(1.9rem, 3vw, 2.6rem);
      line-height: 1.1;
      margin: 0 0 0.9rem;
      letter-spacing: -0.01em;
    }
    .brand-copy p {
      max-width: 42ch;
      color: rgba(255,255,255,0.88);
      line-height: 1.55;
      margin: 0;
      font-size: 1.02rem;
    }
    .brand-points {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.75rem;
    }
    .brand-points li {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      padding: 0.85rem 1rem;
      border-radius: 12px;
      background: rgba(255,255,255,0.09);
      backdrop-filter: blur(6px);
      border: 1px solid rgba(255,255,255,0.12);
      font-size: 0.95rem;
    }
    .brand-points span {
      font-variant-numeric: tabular-nums;
      font-weight: 700;
      color: rgba(255,255,255,0.7);
      font-size: 0.8rem;
      letter-spacing: 0.06em;
    }
    .brand-foot { font-size: 0.78rem; color: rgba(255,255,255,0.65); margin: 0; }

    .auth-main {
      display: grid;
      place-items: center;
      padding: 2rem 1.25rem;
    }
    .auth-card {
      width: min(440px, 100%);
      background: #fff;
      border: 1px solid var(--line, #e6e4df);
      border-radius: 18px;
      padding: 2.25rem;
      display: grid;
      gap: 1.25rem;
      box-shadow: 0 24px 60px -32px rgba(17, 24, 39, 0.18);
    }
    .auth-head h2 { margin: 0 0 0.35rem; font-size: 1.55rem; letter-spacing: -0.01em; }
    .auth-head p { margin: 0; }
    .auth-form { display: grid; gap: 0.9rem; }
    .field { display: grid; gap: 0.4rem; }
    .field-label { font-weight: 600; font-size: 0.88rem; color: var(--ink, #1f2937); }
    .field-input {
      border: 1px solid var(--line, #e6e4df);
      border-radius: 10px;
      padding: 0.85rem 1rem;
      background: #fff;
      font-size: 0.98rem;
      transition: border-color .18s ease, box-shadow .18s ease;
    }
    .field-input:focus {
      outline: none;
      border-color: var(--brand, #d97706);
      box-shadow: 0 0 0 4px rgba(217,119,6,0.14);
    }
    .divider {
      display: flex; align-items: center; gap: 0.75rem;
      color: var(--muted, #6b7280); font-size: 0.82rem;
    }
    .divider::before, .divider::after {
      content: ''; flex: 1; height: 1px; background: var(--line, #e6e4df);
    }
    .google-wrap { display: grid; gap: 0.5rem; }
    #google-btn { display: flex; justify-content: center; }
    .link-right {
      justify-self: end;
      color: var(--brand, #d97706);
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
    }
    .link-right:hover { text-decoration: underline; }
    .back-link {
      color: var(--muted, #6b7280);
      font-size: 0.88rem;
      cursor: pointer;
      text-decoration: none;
      justify-self: start;
    }
    .back-link:hover { color: var(--brand, #d97706); text-decoration: underline; }
    .primary-btn--block { width: 100%; justify-content: center; }
    .alert-box { border-radius: 10px; padding: 0.75rem 0.95rem; font-size: 0.9rem; }
    .alert-box.error { color: #991b1b; background: #fef2f2; border: 1px solid #fecaca; }
    .alert-box.success { color: #065f46; background: #ecfdf5; border: 1px solid #a7f3d0; }
  `]
})
export class LoginPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly ngZone = inject(NgZone);

  readonly year = new Date().getFullYear();

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
