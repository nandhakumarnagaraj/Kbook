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
    <div class="login-shell">
      <div class="login-container">
        
        <!-- Left Side: Interactive Brand Showcase -->
        <div class="showcase-section">
          <div class="showcase-bg">
            <div class="glow-orb glow-1"></div>
            <div class="glow-orb glow-2"></div>
          </div>
          <div class="showcase-content">
            <div class="brand-header">
              <img src="khanabook_logo_header.png" alt="KhanaBook" class="showcase-logo" />
              <h1>KhanaBook</h1>
            </div>
            
            <div class="showcase-hero">
              <h1 class="text-balance">Modern Restaurant Billing & POS</h1>
              <p class="hero-sub">The offline-first operating system built for high-growth food businesses, cafes, and modern restaurants.</p>
            </div>

            <div class="features-list">
              <div class="feature-item">
                <div class="feature-icon"><mat-icon aria-hidden="true">bolt</mat-icon></div>
                <div class="feature-details">
                  <h3>Offline-First Billing</h3>
                  <p>Generate bills instantly, even when offline. Seamless auto-sync once internet is back.</p>
                </div>
              </div>
              <div class="feature-item">
                <div class="feature-icon"><mat-icon aria-hidden="true">analytics</mat-icon></div>
                <div class="feature-details">
                  <h3>Real-Time Analytics</h3>
                  <p>Track sales, inventory, and staff performance from a centralized dashboard.</p>
                </div>
              </div>
              <div class="feature-item">
                <div class="feature-icon"><mat-icon aria-hidden="true">cable</mat-icon></div>
                <div class="feature-details">
                  <h3>Multi-Terminal Sync</h3>
                  <p>Connect and share orders across waitstaff devices and kitchen terminals instantly.</p>
                </div>
              </div>
            </div>

            <div class="showcase-footer-placeholder"></div>
          </div>
        </div>

        <!-- Right Side: Reset Password Panel -->
        <div class="form-section">
          <div class="form-bg-orbs">
            <div class="form-orb form-orb-1"></div>
            <div class="form-orb form-orb-2"></div>
          </div>

          <mat-card class="login-card">
            <div class="login-header">
              <div class="logo-wrap">
                <img src="khanabook_logo.png" alt="KhanaBook" class="logo" />
              </div>
              <h2 class="text-balance">{{ step() === 'request' ? 'Reset Password' : 'Verify & Reset' }}</h2>
              <p class="subtitle">
                {{ step() === 'request' 
                   ? 'Enter your registered phone number to receive a verification code.' 
                   : 'Enter the code sent to your WhatsApp and set a new password.' }}
              </p>
            </div>

            <div class="login-body">
              <!-- Step 1: Request OTP -->
              <form *ngIf="step() === 'request'" [formGroup]="requestForm" (ngSubmit)="requestOtp()" class="login-form">
                
                <div class="custom-input-group">
                  <label class="custom-label">Phone Number</label>
                  <div class="custom-input-wrapper">
                    <span class="country-prefix">+91</span>
                    <input type="text" formControlName="phoneNumber" placeholder="10-digit mobile number" maxlength="10" autocomplete="tel" class="custom-input with-prefix">
                    <mat-icon class="input-icon">smartphone</mat-icon>
                  </div>
                  @if (requestForm.get('phoneNumber')?.touched && requestForm.get('phoneNumber')?.invalid) {
                    <div class="custom-error">Please enter a valid 10-digit phone number</div>
                  }
                </div>

                <div class="error-box" *ngIf="error()">
                  <mat-icon>error_outline</mat-icon>
                  <span>{{ error() }}</span>
                </div>
                <div class="success-box" *ngIf="success()">
                  <mat-icon>check_circle_outline</mat-icon>
                  <span>{{ success() }}</span>
                </div>

                <button mat-flat-button color="primary" class="submit-btn" [disabled]="requestForm.invalid || loading()">
                  @if (loading()) {
                    <mat-spinner diameter="20" aria-label="Sending code"></mat-spinner>
                  } @else {
                    <span>Send Reset Code</span>
                  }
                </button>
              </form>

              <!-- Step 2: Reset Password -->
              <form *ngIf="step() === 'reset'" [formGroup]="resetForm" (ngSubmit)="resetPassword()" class="login-form">
                
                <div class="custom-input-group">
                  <label class="custom-label">Verification Code (OTP)</label>
                  <div class="custom-input-wrapper">
                    <input type="text" formControlName="otp" placeholder="6-digit code" maxlength="6" class="custom-input">
                    <mat-icon class="input-icon">lock_open</mat-icon>
                  </div>
                  @if (resetForm.get('otp')?.touched && resetForm.get('otp')?.invalid) {
                    <div class="custom-error">6-digit code is required</div>
                  }
                </div>

                <div class="custom-input-group">
                  <label class="custom-label">New Password</label>
                  <div class="custom-input-wrapper">
                    <input [type]="showNewPassword ? 'text' : 'password'" formControlName="newPassword" (input)="updatePasswordStrength($any($event.target).value)" autocomplete="new-password" class="custom-input" placeholder="Enter new password">
                    <mat-icon class="input-icon">lock</mat-icon>
                    <button class="input-action-btn" (click)="showNewPassword = !showNewPassword" type="button" [attr.aria-label]="showNewPassword ? 'Hide new password' : 'Show new password'">
                      <mat-icon>{{ showNewPassword ? 'visibility_off' : 'visibility' }}</mat-icon>
                    </button>
                  </div>
                  @if (resetForm.get('newPassword')?.touched && resetForm.get('newPassword')?.invalid) {
                    <div class="custom-error">Password is required (min 6 characters)</div>
                  }
                </div>

                <div class="strength-section" *ngIf="passwordStrength">
                  <div class="strength-label">
                    Security Level: <span [class]="passwordStrength">{{ passwordStrength | uppercase }}</span>
                  </div>
                  <mat-progress-bar mode="determinate" [value]="strengthValue()" [color]="strengthColor()"></mat-progress-bar>
                </div>

                <div class="custom-input-group">
                  <label class="custom-label">Confirm New Password</label>
                  <div class="custom-input-wrapper">
                    <input [type]="showConfirmPassword ? 'text' : 'password'" formControlName="confirmPassword" class="custom-input" placeholder="Repeat new password">
                    <mat-icon class="input-icon">lock</mat-icon>
                    <button class="input-action-btn" (click)="showConfirmPassword = !showConfirmPassword" type="button" [attr.aria-label]="showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'">
                      <mat-icon>{{ showConfirmPassword ? 'visibility_off' : 'visibility' }}</mat-icon>
                    </button>
                  </div>
                  @if (resetForm.get('confirmPassword')?.touched && resetForm.get('confirmPassword')?.invalid) {
                    <div class="custom-error">Please confirm your new password</div>
                  }
                </div>

                <div class="error-box" *ngIf="error()">
                  <mat-icon>error_outline</mat-icon>
                  <span>{{ error() }}</span>
                </div>
                <div class="success-box" *ngIf="success()">
                  <mat-icon>check_circle_outline</mat-icon>
                  <span>{{ success() }}</span>
                </div>

                <button mat-flat-button color="primary" class="submit-btn" [disabled]="resetForm.invalid || loading()">
                  @if (loading()) {
                    <mat-spinner diameter="20" aria-label="Updating password"></mat-spinner>
                  } @else {
                    <span>Change Password</span>
                  }
                </button>
              </form>

              <div class="footer-link">
                <a mat-button color="primary" routerLink="/login" class="back-link">
                  <mat-icon>arrow_back</mat-icon>
                  Back to Sign In
                </a>
              </div>
            </div>
          </mat-card>
        </div>

      </div>
    </div>
  `,
  styles: [`
    /* Shell & Container */
    .login-shell {
      height: 100dvh;
      width: 100vw;
      display: flex;
      background: var(--bg);
      overflow: hidden;
      position: relative;
    }

    .login-container {
      display: flex;
      width: 100%;
      height: 100%;
    }

    /* Left Showcase Section */
    .showcase-section {
      flex: 1.2;
      background: radial-gradient(circle at 0% 0%, #151a22 0%, #0b0e13 100%);
      position: relative;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 48px;
      color: var(--panel);
    }

    @media (max-width: 960px) {
      .showcase-section {
        display: none;
      }
    }

    .showcase-bg {
      position: absolute;
      inset: 0;
      z-index: 0;
    }

    .glow-orb {
      position: absolute;
      border-radius: 50%;
      filter: blur(120px);
      opacity: 0.15;
      animation: ambientFloat 20s infinite ease-in-out;
    }

    .glow-1 {
      width: 600px;
      height: 600px;
      background: radial-gradient(circle, rgba(199, 115, 47, 0.25) 0%, transparent 70%);
      top: -150px;
      left: -150px;
    }

    .glow-2 {
      width: 500px;
      height: 500px;
      background: radial-gradient(circle, rgba(59, 130, 246, 0.2) 0%, transparent 70%);
      bottom: -100px;
      right: -100px;
      animation-delay: -5s;
      animation-direction: reverse;
    }

    @keyframes ambientFloat {
      0%, 100% { transform: translate(0, 0) scale(1); }
      50% { transform: translate(40px, 30px) scale(1.1); }
    }
    @media (prefers-reduced-motion: reduce) {
      .glow-orb { animation: none; }
    }

    .showcase-content {
      position: relative;
      z-index: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }

    .brand-header {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .showcase-logo {
      height: 40px;
      object-fit: contain;
    }

    .brand-header h1 {
      font-size: 2rem;
      font-weight: 800;
      letter-spacing: -0.5px;
      color: var(--panel);
      margin: 0;
      line-height: 1;
    }

    .showcase-hero {
      margin: 40px 0;
      max-width: 540px;
    }

    .showcase-hero h1 {
      font-size: clamp(2rem, 4vw, 2.75rem);
      font-weight: 800;
      line-height: 1.15;
      margin: 0 0 16px;
      letter-spacing: -1px;
      color: var(--bg);
    }

    .hero-sub {
      font-size: 1.1rem;
      line-height: 1.6;
      color: var(--muted);
      margin: 0;
    }

    .features-list {
      display: flex;
      flex-direction: column;
      gap: 20px;
      margin-bottom: 40px;
    }

    .feature-item {
      display: flex;
      gap: 16px;
      padding: 16px 20px;
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.03) 0%, rgba(255, 255, 255, 0.01) 100%);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 14px;
      transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), border-color 0.3s cubic-bezier(0.4, 0, 0.2, 1), background 0.3s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .feature-item:hover {
      transform: translateY(-2px);
      border-color: rgba(199, 115, 47, 0.25);
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.05) 0%, rgba(255, 255, 255, 0.02) 100%);
      box-shadow: 0 10px 20px rgba(0, 0, 0, 0.2);
    }

    .feature-icon {
      font-size: 1.5rem;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      background: rgba(199, 115, 47, 0.12);
      border-radius: 10px;
      color: var(--brand);
    }

    .feature-details h3 {
      font-size: 0.95rem;
      font-weight: 700;
      margin: 0 0 4px;
      color: var(--bg);
    }

    .feature-details p {
      font-size: 0.85rem;
      color: var(--muted);
      margin: 0;
      line-height: 1.4;
    }

    /* Right Form Section */
    .form-section {
      flex: 0.8;
      background: var(--bg);
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      overflow: hidden;
      padding: 24px;
    }

    @media (max-width: 960px) {
      .form-section {
        flex: 1;
        width: 100%;
        height: 100%;
      }
    }

    .form-bg-orbs {
      position: absolute;
      inset: 0;
      z-index: 0;
    }

    .form-orb {
      position: absolute;
      border-radius: 50%;
      filter: blur(80px);
      opacity: 0.15;
    }

    .form-orb-1 {
      width: 400px;
      height: 400px;
      background: var(--brand);
      top: -100px;
      right: -100px;
    }

    .form-orb-2 {
      width: 450px;
      height: 450px;
      background: var(--success);
      bottom: -150px;
      left: -150px;
    }

    .login-card {
      width: 100%;
      max-width: 420px;
      border-radius: 20px;
      border: 1px solid rgba(0, 0, 0, 0.06);
      z-index: 1;
      overflow: hidden;
      background: rgba(255, 255, 255, 0.85);
      backdrop-filter: blur(20px);
      box-shadow: 0 20px 40px rgba(15, 23, 42, 0.04), 0 1px 3px rgba(15, 23, 42, 0.02);
    }

    .login-header {
      padding: 32px 32px 16px;
      text-align: center;
    }

    .logo-wrap {
      margin-bottom: 16px;
      display: flex;
      justify-content: center;
    }

    .logo {
      height: 120px;
      width: 120px;
      object-fit: contain;
      background: var(--panel);
      padding: 4px;
      border-radius: 20px;
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06), 0 1px 3px rgba(0, 0, 0, 0.04);
      transition: transform 0.3s ease;
    }

    .logo:hover {
      transform: scale(1.05);
    }

    .login-header h2 {
      margin: 0;
      font-size: 1.6rem;
      font-weight: 800;
      color: var(--ink);
      letter-spacing: -0.5px;
    }

    .subtitle {
      margin: 6px 0 0;
      color: var(--muted);
      font-size: 0.9rem;
      line-height: 1.4;
    }

    .login-body {
      padding: 0 32px 32px;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    /* Custom Inputs Styling */
    .custom-input-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
      width: 100%;
      text-align: left;
    }

    .custom-label {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--ink-secondary);
      letter-spacing: 0.5px;
      text-transform: uppercase;
    }

    .custom-input-wrapper {
      position: relative;
      width: 100%;
      display: flex;
      align-items: center;
    }

    .custom-input {
      height: 48px;
      width: 100%;
      border: 1px solid rgba(0, 0, 0, 0.12);
      border-radius: 8px;
      padding: 0 12px 0 38px;
      font-size: 0.9rem;
      font-family: inherit;
      color: var(--ink);
      background: var(--panel);
      transition: border-color 0.2s ease, box-shadow 0.2s ease;
      box-sizing: border-box;
    }

    .custom-input.with-prefix {
      padding-left: 70px;
    }

    .country-prefix {
      position: absolute;
      left: 36px;
      font-size: 0.9rem;
      font-weight: 700;
      color: var(--ink-secondary);
    }

    .custom-input::placeholder {
      color: var(--muted);
      opacity: 0.6;
    }

    .custom-input:focus {
      border-color: var(--brand);
      box-shadow: 0 0 0 3px var(--brand-soft);
      outline: none;
    }

    .input-icon {
      position: absolute;
      left: 12px;
      color: var(--muted);
      font-size: 18px !important;
      width: 18px !important;
      height: 18px !important;
      line-height: 18px !important;
      pointer-events: none;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .input-action-btn {
      position: absolute;
      right: 8px;
      background: transparent;
      border: none;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border-radius: 50%;
      color: var(--muted);
      transition: background 0.2s ease, color 0.2s ease;
      padding: 0;
    }

    .input-action-btn:hover {
      background: rgba(0, 0, 0, 0.05);
      color: var(--ink);
    }

    .input-action-btn mat-icon {
      font-size: 18px !important;
      width: 18px !important;
      height: 18px !important;
      line-height: 18px !important;
    }

    .custom-error {
      font-size: 0.75rem;
      color: var(--danger);
      font-weight: 600;
      margin-top: 2px;
    }

    .strength-section {
      margin-top: 4px;
      margin-bottom: 12px;
      text-align: left;
    }

    .strength-label {
      font-size: 0.75rem;
      font-weight: 700;
      margin-bottom: 8px;
      color: var(--muted);
    }

    .strength-label span.weak { color: var(--danger); }
    .strength-label span.medium { color: var(--warn); }
    .strength-label span.strong { color: var(--success); }

    .error-box {
      background: var(--danger-bg);
      color: var(--danger);
      padding: 10px 14px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.8rem;
      font-weight: 600;
      border: 1px solid var(--danger-border);
      text-align: left;
    }

    .success-box {
      background: var(--success-bg);
      color: var(--success);
      padding: 10px 14px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.8rem;
      font-weight: 600;
      border: 1px solid var(--success-border);
      text-align: left;
    }

    .submit-btn {
      height: 48px;
      border-radius: 8px;
      font-size: 0.9rem;
      font-weight: 700;
      margin-top: 4px;
      background: linear-gradient(135deg, var(--brand) 0%, var(--brand-dark) 100%) !important;
      color: white !important;
      box-shadow: 0 4px 12px rgba(199, 115, 47, 0.2);
      transition: transform 0.2s ease, box-shadow 0.2s ease !important;
    }

    .submit-btn:hover:not([disabled]) {
      transform: translateY(-1px);
      box-shadow: 0 6px 16px rgba(199, 115, 47, 0.3);
    }

    .footer-link {
      margin-top: 24px;
      text-align: center;
      display: flex;
      justify-content: center;
    }

    .back-link {
      font-size: 0.85rem;
      font-weight: 600;
      color: var(--brand) !important;
      display: flex;
      align-items: center;
      gap: 4px;
    }

    @media (max-width: 960px) {
      .login-shell {
        background: var(--bg);
      }
      .login-card {
        border-radius: 16px;
        background: var(--panel);
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
      }
    }

    @media (max-width: 480px) {
      .login-shell {
        background: var(--panel);
      }
      .login-card {
        border: none;
        box-shadow: none;
        background: transparent;
        backdrop-filter: none;
      }
      .login-header {
        padding: 16px 16px 12px;
      }
      .login-body {
        padding: 0 16px 12px;
      }
      .logo {
        height: 80px;
        width: 80px;
      }
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
          .onAction().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.router.navigate(['/login']));
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.error || err?.error?.message || 'Reset failed. Please check the code.');
      }
    });
  }
}
