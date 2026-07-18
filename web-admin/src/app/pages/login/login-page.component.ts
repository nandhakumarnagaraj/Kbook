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
    <section class="login-shell">
      <div class="login-card panel">
        <div class="login-copy">
          <span class="eyebrow">KhanaBook</span>
          <h1>Web Admin</h1>
          <p class="muted">Sign in to manage menu data, team access, and orders from one place.</p>
          <div class="hero-meta">
            <span class="chip">Fast Access</span>
            <span class="chip success">Business Control</span>
          </div>
        </div>

        <!-- Login success message (after password reset) -->
        <div *ngIf="loginSuccessMessage" class="alert-box success">{{ loginSuccessMessage }}</div>

        <!-- Main Login Form -->
        <ng-container *ngIf="forgotStep === 'none'">
          <!-- Google Sign-In button -->
          <div>
            <div id="google-btn"></div>
            <div *ngIf="googleError" class="alert-box error" style="margin-top: 0.75rem;">{{ googleError }}</div>
          </div>

          <div class="divider"><span>or sign in with password</span></div>

          <form [formGroup]="form" (ngSubmit)="submit()">
            <label>
              Login ID
              <input formControlName="loginId" placeholder="Phone number or email">
            </label>

            <label>
              Password
              <input type="password" formControlName="password" placeholder="Enter password">
            </label>

            <a class="forgot-link" (click)="startForgotPassword()">Forgot Password?</a>

            <div *ngIf="error" class="alert-box error">{{ error }}</div>

            <button class="primary-btn" [disabled]="form.invalid || loading">
              {{ loading ? 'Signing in...' : 'Sign in' }}
            </button>
          </form>
        </ng-container>

        <!-- Forgot Password Step 1: Phone Number -->
        <ng-container *ngIf="forgotStep === 'phone'">
          <div class="forgot-header">
            <h2>Forgot Password</h2>
            <p class="muted">Enter your registered phone number to receive an OTP.</p>
          </div>

          <form [formGroup]="phoneForm" (ngSubmit)="submitPhone()">
            <label>
              Phone Number
              <input formControlName="phone" placeholder="10-digit phone number" maxlength="10">
            </label>

            <div *ngIf="forgotError" class="alert-box error">{{ forgotError }}</div>

            <button class="primary-btn" [disabled]="phoneForm.invalid || forgotLoading">
              {{ forgotLoading ? 'Sending OTP...' : 'Send OTP' }}
            </button>
          </form>

          <a class="back-link" (click)="backToLogin()">← Back to Login</a>
        </ng-container>

        <!-- Forgot Password Step 2: OTP Entry -->
        <ng-container *ngIf="forgotStep === 'otp'">
          <div class="forgot-header">
            <h2>Enter OTP</h2>
            <p class="muted">Enter the 4-digit code sent to your phone.</p>
          </div>

          <form [formGroup]="otpForm" (ngSubmit)="submitOtp()">
            <label>
              OTP Code
              <input formControlName="otp" placeholder="4-digit OTP" maxlength="4">
            </label>

            <div *ngIf="forgotError" class="alert-box error">{{ forgotError }}</div>

            <button class="primary-btn" [disabled]="otpForm.invalid || forgotLoading">
              {{ forgotLoading ? 'Verifying...' : 'Verify OTP' }}
            </button>
          </form>

          <a class="back-link" (click)="backToLogin()">← Back to Login</a>
        </ng-container>

        <!-- Forgot Password Step 3: New Password -->
        <ng-container *ngIf="forgotStep === 'password'">
          <div class="forgot-header">
            <h2>Reset Password</h2>
            <p class="muted">Enter your new password (minimum 6 characters).</p>
          </div>

          <form [formGroup]="passwordForm" (ngSubmit)="submitNewPassword()">
            <label>
              New Password
              <input type="password" formControlName="newPassword" placeholder="Min 6 characters">
            </label>

            <label>
              Confirm Password
              <input type="password" formControlName="confirmPassword" placeholder="Re-enter password">
            </label>

            <div *ngIf="passwordMismatch && passwordForm.get('confirmPassword')?.touched" class="alert-box error">
              Passwords do not match.
            </div>

            <div *ngIf="forgotError" class="alert-box error">{{ forgotError }}</div>

            <button class="primary-btn" [disabled]="passwordForm.invalid || passwordMismatch || forgotLoading">
              {{ forgotLoading ? 'Resetting...' : 'Reset Password' }}
            </button>
          </form>

          <a class="back-link" (click)="backToLogin()">← Back to Login</a>
        </ng-container>

        <!-- Forgot Password Success -->
        <ng-container *ngIf="forgotStep === 'success'">
          <div class="forgot-header">
            <h2>Password Reset Successful</h2>
            <p class="muted">Your password has been changed. You can now sign in with your new password.</p>
          </div>

          <a class="back-link" (click)="backToLogin()">← Back to Login</a>
        </ng-container>
      </div>
    </section>
  `,
  styles: [`
    .login-shell {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 1rem;
    }

    .login-card {
      width: min(460px, 100%);
      padding: 2rem;
      display: grid;
      gap: 1.5rem;
    }

    .login-copy {
      display: grid;
      gap: 0.75rem;
    }

    h1 { margin: 0.25rem 0 0.5rem; font-size: 2rem; }
    h2 { margin: 0; font-size: 1.4rem; }

    .eyebrow {
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--brand-deep);
      font-size: 0.78rem;
      font-weight: 700;
    }

    form { display: grid; gap: 1rem; }

    label { display: grid; gap: 0.45rem; font-weight: 600; }

    input {
      border: 1px solid var(--line);
      border-radius: 12px;
      padding: 0.9rem 1rem;
      background: #fff;
      transition: border-color 0.18s ease, box-shadow 0.18s ease;
    }

    input:focus {
      outline: none;
      border-color: var(--brand);
      box-shadow: 0 0 0 3px rgba(181, 106, 45, 0.14);
    }

    .divider {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      color: var(--muted);
      font-size: 0.85rem;
    }
    .divider::before, .divider::after {
      content: '';
      flex: 1;
      height: 1px;
      background: var(--line);
    }

    .alert-box {
      border-radius: 8px;
      padding: 0.75rem 1rem;
      font-size: 0.9rem;
    }
    .alert-box.error { color: #b03030; background: #fdf0f0; }
    .alert-box.success { color: #1d6b4f; background: #edf8f4; }

    #google-btn { display: flex; justify-content: center; }

    .forgot-link {
      color: var(--brand);
      font-size: 0.85rem;
      cursor: pointer;
      text-align: right;
      text-decoration: none;
      font-weight: 500;
    }
    .forgot-link:hover { text-decoration: underline; }

    .back-link {
      color: var(--muted);
      font-size: 0.88rem;
      cursor: pointer;
      text-decoration: none;
    }
    .back-link:hover { color: var(--brand); text-decoration: underline; }

    .forgot-header {
      display: grid;
      gap: 0.5rem;
    }
  `]
})
export class LoginPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly ngZone = inject(NgZone);

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
          { theme: 'outline', size: 'large', width: 400, text: 'signin_with' }
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
