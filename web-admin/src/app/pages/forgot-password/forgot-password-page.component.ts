import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../environments/environment';

const API_BASE_URL = environment.apiBaseUrl;

@Component({
  selector: 'app-forgot-password-page',
  standalone: true,
  imports: [
    CommonModule, 
    ReactiveFormsModule, 
    FormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  template: `
    <div class="reset-shell">
      <div class="login-bg">
        <div class="orb orb-1"></div>
        <div class="orb orb-2"></div>
      </div>

      <mat-card class="reset-card mat-elevation-z12">
        <div class="reset-header">
          <div class="logo-wrap">
            <img src="/khanabook_logo.png" alt="KhanaBook" class="logo" />
          </div>
          <h1>{{ step() === 'request' ? 'Reset Password' : 'Verify & Reset' }}</h1>
          <p class="subtitle">
            {{ step() === 'request' 
               ? 'Enter your registered phone number to receive a verification code.' 
               : 'Enter the code sent to your WhatsApp and set a new password.' }}
          </p>
        </div>

        <mat-card-content class="reset-body">
          <!-- Step 1: Request OTP -->
          <form *ngIf="step() === 'request'" [formGroup]="requestForm" (ngSubmit)="requestOtp()" class="reset-form">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Phone Number</mat-label>
              <span matPrefix>+91&nbsp;</span>
              <input matInput formControlName="phoneNumber" placeholder="10-digit mobile number" maxlength="10" autocomplete="tel">
              <mat-icon matSuffix>smartphone</mat-icon>
            </mat-form-field>

            <div class="error-msg" *ngIf="error()">{{ error() }}</div>
            <div class="success-msg" *ngIf="success()">{{ success() }}</div>

            <button mat-flat-button color="primary" class="submit-btn" [disabled]="requestForm.invalid || loading()">
              <mat-spinner diameter="20" *ngIf="loading()"></mat-spinner>
              <span *ngIf="!loading()">Send Reset Code</span>
            </button>
          </form>

          <!-- Step 2: Reset Password -->
          <form *ngIf="step() === 'reset'" [formGroup]="resetForm" (ngSubmit)="resetPassword()" class="reset-form">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Verification Code (OTP)</mat-label>
              <input matInput formControlName="otp" placeholder="6-digit code" maxlength="6">
              <mat-icon matSuffix>lock_open</mat-icon>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New Password</mat-label>
              <input matInput [type]="showNewPassword ? 'text' : 'password'" 
                     formControlName="newPassword" 
                     (input)="updatePasswordStrength($any($event.target).value)"
                     autocomplete="new-password">
              <button mat-icon-button matSuffix (click)="showNewPassword = !showNewPassword" type="button">
                <mat-icon>{{ showNewPassword ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
            </mat-form-field>

            <div class="strength-section" *ngIf="passwordStrength">
              <div class="strength-label">
                Security Level: <span [class]="passwordStrength">{{ passwordStrength | uppercase }}</span>
              </div>
              <mat-progress-bar mode="determinate" 
                               [value]="strengthValue()" 
                               [color]="strengthColor()"></mat-progress-bar>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirm New Password</mat-label>
              <input matInput [type]="showConfirmPassword ? 'text' : 'password'" formControlName="confirmPassword">
              <button mat-icon-button matSuffix (click)="showConfirmPassword = !showConfirmPassword" type="button">
                <mat-icon>{{ showConfirmPassword ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
            </mat-form-field>

            <div class="error-msg" *ngIf="error()">{{ error() }}</div>
            <div class="success-msg" *ngIf="success()">{{ success() }}</div>

            <button mat-flat-button color="primary" class="submit-btn" [disabled]="resetForm.invalid || loading()">
              <mat-spinner diameter="20" *ngIf="loading()"></mat-spinner>
              <span *ngIf="!loading()">Change Password</span>
            </button>
          </form>

          <div class="footer-link">
            <a mat-button color="primary" routerLink="/login">
              <mat-icon>arrow_back</mat-icon>
              Back to Sign In
            </a>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .reset-shell {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: #f8fafc;
      position: relative;
      overflow: hidden;
    }

    .login-bg { position: absolute; inset: 0; z-index: 0; }
    .orb { position: absolute; border-radius: 50%; filter: blur(80px); opacity: 0.15; }
    .orb-1 { width: 500px; height: 500px; background: var(--brand); top: -100px; left: -100px; animation: float 15s infinite ease-in-out; }
    .orb-2 { width: 400px; height: 400px; background: #10b981; bottom: -50px; right: -50px; animation: float 12s infinite ease-in-out reverse; }
    @keyframes float { 0%, 100% { transform: translate(0, 0); } 50% { transform: translate(30px, 30px); } }

    .reset-card {
      width: 100%;
      max-width: 440px;
      border-radius: 24px;
      border: none;
      z-index: 1;
      overflow: hidden;
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(10px);
    }

    .reset-header { padding: 40px 40px 24px; text-align: center; }
    .logo { height: 56px; margin-bottom: 24px; }
    h1 { margin: 0; font-size: 1.75rem; font-weight: 800; color: var(--ink); letter-spacing: -0.5px; }
    .subtitle { margin: 8px 0 0; color: var(--muted); font-size: 0.95rem; line-height: 1.4; }

    .reset-body { padding: 0 40px 40px !important; }
    .reset-form { display: flex; flex-direction: column; gap: 8px; }
    .full-width { width: 100%; }

    .strength-section { margin-bottom: 16px; padding: 0 4px; }
    .strength-label { font-size: 0.75rem; font-weight: 700; margin-bottom: 8px; color: var(--muted); }
    .strength-label span.weak { color: #ef4444; }
    .strength-label span.medium { color: #f59e0b; }
    .strength-label span.strong { color: #10b981; }

    .error-msg { background: #fee2e2; color: #b91c1c; padding: 12px; border-radius: 12px; font-size: 0.85rem; font-weight: 600; margin-bottom: 16px; text-align: center; }
    .success-msg { background: #dcfce7; color: #15803d; padding: 12px; border-radius: 12px; font-size: 0.85rem; font-weight: 600; margin-bottom: 16px; text-align: center; }

    .submit-btn { height: 52px; border-radius: 26px; font-size: 1.05rem; font-weight: 800; margin-top: 8px; }
    .footer-link { margin-top: 24px; text-align: center; }

    @media (max-width: 480px) {
      .reset-shell { padding: 16px; }
      .reset-header { padding: 32px 24px 20px; }
      .reset-body { padding: 0 24px 32px !important; }
    }
  `]
})
export class ForgotPasswordPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);

  readonly step = signal<'request' | 'reset'>('request');
  readonly loading = signal(false);
  readonly error = signal('');
  readonly success = signal('');

  showNewPassword = false;
  showConfirmPassword = false;
  passwordStrength: 'weak' | 'medium' | 'strong' | null = null;

  updatePasswordStrength(value: string): void {
    if (!value || value.length < 6) {
      this.passwordStrength = null;
      return;
    }
    const hasUpper = /[A-Z]/.test(value);
    const hasLower = /[a-z]/.test(value);
    const hasDigit = /\d/.test(value);
    const hasSpecial = /[^A-Za-z0-9]/.test(value);
    const score = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
    if (value.length >= 10 && score >= 3) {
      this.passwordStrength = 'strong';
    } else if (value.length >= 8 && score >= 2) {
      this.passwordStrength = 'medium';
    } else {
      this.passwordStrength = 'weak';
    }
  }

  strengthValue(): number {
    if (this.passwordStrength === 'strong') return 100;
    if (this.passwordStrength === 'medium') return 66;
    if (this.passwordStrength === 'weak') return 33;
    return 0;
  }

  strengthColor(): 'primary' | 'accent' | 'warn' {
    if (this.passwordStrength === 'strong') return 'primary'; // Will look green-ish with palette
    if (this.passwordStrength === 'medium') return 'accent';
    return 'warn';
  }

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
        this.success.set('Security code sent to your WhatsApp.');
        this.step.set('reset');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.error || err?.error?.message || 'Verification failed. Please try again.');
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
        this.snackBar.open('Password reset successful!', 'Sign In', { duration: 5000 })
          .onAction().subscribe(() => this.router.navigate(['/login']));
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.error || err?.error?.message || 'Reset failed. Please check the code.');
      }
    });
  }
}
