import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

const API_BASE_URL = environment.apiBaseUrl;

@Component({
  selector: 'app-forgot-password-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
    <section class="login-shell">
      <div class="login-card">
        <div class="login-header">
          <img src="/khanabook_logo.png" alt="KhanaBook" class="login-logo" />
          <h1>Reset password</h1>
          <p class="muted">Enter your phone number and we'll send an OTP to reset your password.</p>
        </div>

        <div class="login-body">
          <!-- Step 1: Request OTP -->
          <ng-container *ngIf="step() === 'request'">
            <form [formGroup]="requestForm" (ngSubmit)="requestOtp()">
              <label class="field">
                Phone Number
                <input class="field-input" formControlName="phoneNumber" placeholder="10-digit phone number" maxlength="10" />
              </label>

              <div *ngIf="error()" class="msg msg-error">{{ error() }}</div>
              <div *ngIf="success()" class="msg msg-success">{{ success() }}</div>

              <button class="submit-btn" [disabled]="requestForm.invalid || loading()">
                {{ loading() ? 'Sending...' : 'Send OTP' }}
              </button>
            </form>
          </ng-container>

          <!-- Step 2: Reset password -->
          <ng-container *ngIf="step() === 'reset'">
            <form [formGroup]="resetForm" (ngSubmit)="resetPassword()">
              <label class="field">
                OTP
                <input class="field-input" formControlName="otp" placeholder="6-digit OTP" maxlength="6" />
              </label>

              <label class="field">
                New Password
                <input class="field-input" type="password" formControlName="newPassword" placeholder="Min 6 characters" />
              </label>

              <label class="field">
                Confirm Password
                <input class="field-input" type="password" formControlName="confirmPassword" placeholder="Re-enter password" />
              </label>

              <div *ngIf="error()" class="msg msg-error">{{ error() }}</div>
              <div *ngIf="success()" class="msg msg-success">{{ success() }}</div>

              <button class="submit-btn" [disabled]="resetForm.invalid || loading()">
                {{ loading() ? 'Resetting...' : 'Reset Password' }}
              </button>
            </form>
          </ng-container>

          <div class="back-link">
            <a routerLink="/login">&larr; Back to sign in</a>
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .login-shell {
      min-height: 100vh;
      min-height: 100dvh;
      display: grid;
      place-items: center;
      padding: 1.5rem;
      background:
        radial-gradient(circle at 30% 40%, rgba(181, 106, 45, 0.12), transparent 50%),
        radial-gradient(circle at 70% 60%, rgba(29, 123, 95, 0.06), transparent 40%),
        var(--bg);
    }

    .login-card {
      width: min(420px, 100%);
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 24px;
      box-shadow: var(--shadow-xl);
      overflow: hidden;
      animation: slideUp 0.35s ease;
    }

    .login-header {
      padding: 2rem 2rem 1.5rem;
      text-align: center;
      border-bottom: 1px solid var(--line);
      background: linear-gradient(180deg, rgba(181, 106, 45, 0.04), transparent);
    }
    .login-logo { height: 44px; width: fit-content; margin: 0 auto 1rem; display: block; }
    .login-header h1 { margin: 0 0 0.35rem; font-size: 1.35rem; font-weight: 800; }
    .login-header p { margin: 0; font-size: 0.88rem; }

    .login-body {
      padding: 1.5rem 2rem 2rem;
      display: grid;
      gap: 1.25rem;
    }

    form { display: grid; gap: 1rem; }
    .field { display: grid; gap: 0.4rem; font-weight: 600; font-size: 0.85rem; }
    .field-input {
      border: 1px solid var(--line);
      border-radius: 12px;
      padding: 0.85rem 1rem;
      background: var(--panel);
      font-size: 0.95rem;
      transition: border-color 0.18s ease, box-shadow 0.18s ease;
    }
    .field-input:focus {
      outline: none;
      border-color: var(--brand);
      box-shadow: 0 0 0 3px rgba(181, 106, 45, 0.14);
    }
    .field-input:hover { border-color: var(--line-strong); }

    .submit-btn {
      width: 100%;
      padding: 0.85rem;
      border: none;
      border-radius: 12px;
      background: linear-gradient(135deg, var(--brand) 0%, var(--brand-deep) 100%);
      color: #fff;
      font-weight: 700;
      font-size: 1rem;
      cursor: pointer;
      box-shadow: 0 12px 24px rgba(126, 68, 23, 0.2);
      transition: all 0.18s ease;
    }
    .submit-btn:hover { transform: translateY(-1px); box-shadow: 0 16px 28px rgba(126, 68, 23, 0.28); }
    .submit-btn:active { transform: translateY(0); }
    .submit-btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none; box-shadow: none; }

    .msg {
      border-radius: 10px;
      padding: 0.7rem 1rem;
      font-size: 0.85rem;
      text-align: center;
    }
    .msg-error { color: var(--danger); background: var(--danger-soft); border: 1px solid rgba(166,55,47,0.2); }
    .msg-success { color: var(--success); background: rgba(29,123,95,0.08); border: 1px solid rgba(29,123,95,0.25); }

    .back-link {
      text-align: center;
      font-size: 0.88rem;
    }
    .back-link a { color: var(--brand); text-decoration: none; font-weight: 600; }
    .back-link a:hover { text-decoration: underline; }

    @media (max-width: 520px) {
      .login-shell { padding: 1rem; }
      .login-header { padding: 1.5rem 1.25rem 1rem; }
      .login-body { padding: 1.25rem; }
    }
  `]
})
export class ForgotPasswordPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly step = signal<'request' | 'reset'>('request');
  readonly loading = signal(false);
  readonly error = signal('');
  readonly success = signal('');

  readonly requestForm = this.fb.nonNullable.group({
    phoneNumber: ['', [Validators.required, Validators.pattern('^\\d{10}$')]]
  });

  readonly resetForm = this.fb.nonNullable.group({
    otp: ['', [Validators.required, Validators.pattern('^\\d{6}$')]],
    newPassword: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', [Validators.required]]
  });

  requestOtp(): void {
    if (this.requestForm.invalid || this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.success.set('');

    const phone = this.requestForm.getRawValue().phoneNumber;
    this.http.post(`${API_BASE_URL}/auth/reset-password/request`, { phoneNumber: phone }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set('OTP sent to your WhatsApp. Please check and enter it below.');
        this.step.set('reset');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.error || err?.error?.message || 'Failed to send OTP. Please try again.');
      }
    });
  }

  resetPassword(): void {
    if (this.resetForm.invalid || this.loading()) return;

    const { otp, newPassword, confirmPassword } = this.resetForm.getRawValue();
    if (newPassword !== confirmPassword) {
      this.error.set('Passwords do not match.');
      return;
    }

    this.loading.set(true);
    this.error.set('');
    this.success.set('');

    const phone = this.requestForm.getRawValue().phoneNumber;
    this.http.post(`${API_BASE_URL}/auth/reset-password`, {
      phoneNumber: phone,
      otp,
      newPassword
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set('Password reset successful! Redirecting to login...');
        setTimeout(() => this.router.navigate(['/login']), 1500);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.error || err?.error?.message || 'Failed to reset password. Please try again.');
      }
    });
  }
}
