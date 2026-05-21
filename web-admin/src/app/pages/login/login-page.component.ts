import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, NgZone, OnDestroy, inject, signal, ViewEncapsulation } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

interface GoogleAccountsId {
  initialize(config: { client_id: string; callback: (resp: { credential: string }) => void }): void;
  renderButton(el: HTMLElement | null, opts: Record<string, unknown>): void;
}
interface GoogleAccounts { id: GoogleAccountsId }
interface GlobalGoogle { accounts: GoogleAccounts }
declare const google: GlobalGoogle | undefined;

@Component({
  selector: 'app-login-page',
  standalone: true,
  encapsulation: ViewEncapsulation.None,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
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
              <img src="/khanabook_logo_header.png" alt="KhanaBook" class="showcase-logo" />
              <h1>KhanaBook</h1>
            </div>
            
            <div class="showcase-hero">
              <h1>Modern Restaurant Billing & POS</h1>
              <p class="hero-sub">The offline-first operating system built for high-growth food businesses, cafes, and modern restaurants.</p>
            </div>

            <div class="features-list">
              <div class="feature-item">
                <div class="feature-icon">⚡</div>
                <div class="feature-details">
                  <h3>Offline-First Billing</h3>
                  <p>Generate bills instantly, even when offline. Seamless auto-sync once internet is back.</p>
                </div>
              </div>
              <div class="feature-item">
                <div class="feature-icon">📊</div>
                <div class="feature-details">
                  <h3>Real-Time Analytics</h3>
                  <p>Track sales, inventory, and staff performance from a centralized dashboard.</p>
                </div>
              </div>
              <div class="feature-item">
                <div class="feature-icon">🔌</div>
                <div class="feature-details">
                  <h3>Multi-Terminal Sync</h3>
                  <p>Connect and share orders across waitstaff devices and kitchen terminals instantly.</p>
                </div>
              </div>
            </div>

            <div class="showcase-footer-placeholder"></div>
          </div>
        </div>

        <!-- Right Side: Login Panel -->
        <div class="form-section">
          <div class="form-bg-orbs">
            <div class="form-orb form-orb-1"></div>
            <div class="form-orb form-orb-2"></div>
          </div>

          <mat-card class="login-card">
            <div class="login-header">
              <div class="logo-wrap">
                <img src="/khanabook_logo.png" alt="KhanaBook" class="logo" />
              </div>
              <h2>Platform Access</h2>
              <p class="subtitle">Sign in to manage your business operations.</p>
            </div>

            <div class="login-body">
              <form [formGroup]="form" (ngSubmit)="submit()" class="login-form" role="form" aria-label="Login form">
                
                <!-- Custom Login ID input -->
                <div class="custom-input-group">
                  <label class="custom-label">Login ID</label>
                  <div class="custom-input-wrapper">
                    <input type="text" formControlName="loginId" placeholder="Phone or email" autocomplete="username" class="custom-input" #loginIdInput>
                    <mat-icon class="input-icon">person</mat-icon>
                  </div>
                  @if (form.get('loginId')?.touched && form.get('loginId')?.invalid) {
                    <div class="custom-error">Login ID is required</div>
                  }
                </div>

                <!-- Custom Password input -->
                <div class="custom-input-group">
                  <label class="custom-label">Password</label>
                  <div class="custom-input-wrapper">
                    <input [type]="showPassword() ? 'text' : 'password'" formControlName="password" autocomplete="current-password" class="custom-input" placeholder="Enter password">
                    <mat-icon class="input-icon">lock</mat-icon>
                    <button class="input-action-btn" (click)="togglePassword()" type="button"
                            [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
                            [attr.aria-pressed]="showPassword()">
                      <mat-icon>{{ showPassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
                    </button>
                  </div>
                  @if (form.get('password')?.touched && form.get('password')?.invalid) {
                    <div class="custom-error">Password is required</div>
                  }
                </div>

                <div class="form-footer">
                  <a mat-button color="primary" routerLink="/forgot-password" class="forgot-link">Forgot password?</a>
                </div>

                @if (error()) {
                  <div class="error-box" aria-live="assertive">
                    <mat-icon>error_outline</mat-icon>
                    <span>{{ error() }}</span>
                  </div>
                }

                <button mat-flat-button color="primary" class="submit-btn" [disabled]="form.invalid || loading()">
                  @if (loading()) {
                    <mat-spinner diameter="20" aria-label="Signing in"></mat-spinner>
                  } @else {
                    <span>Sign In</span>
                  }
                </button>
              </form>

              <div class="divider-row">
                <mat-divider></mat-divider>
                <span class="divider-text">OR</span>
                <mat-divider></mat-divider>
              </div>

              <div class="social-login">
                <div id="google-btn"></div>
              </div>
            </div>

            <div class="login-footer">
              <p>Don't have an account? <a href="https://khanabook.com" target="_blank" rel="noopener">Contact Support</a></p>
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
      background: #f8fafc;
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
      color: #fff;
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

    .brand-name {
      font-size: 1.5rem;
      font-weight: 800;
      letter-spacing: -0.5px;
      color: #fff;
    }

    .showcase-hero {
      margin: 40px 0;
      max-width: 540px;
    }

    .showcase-hero h1 {
      font-size: 2.75rem;
      font-weight: 800;
      line-height: 1.15;
      margin: 0 0 16px;
      letter-spacing: -1px;
      background: linear-gradient(135deg, #ffffff 0%, #cbd5e1 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .hero-sub {
      font-size: 1.1rem;
      line-height: 1.6;
      color: #94a3b8;
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
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
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
      color: #f0a24c;
    }

    .feature-details h3 {
      font-size: 0.95rem;
      font-weight: 700;
      margin: 0 0 4px;
      color: #f8fafc;
    }

    .feature-details p {
      font-size: 0.85rem;
      color: #94a3b8;
      margin: 0;
      line-height: 1.4;
    }

    /* Right Form Section */
    .form-section {
      flex: 0.8;
      background: #f8fafc;
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
      background: #3b82f6;
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
      transition: all 0.3s ease;
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
      height: 96px;
      width: 96px;
      object-fit: contain;
      background: #ffffff;
      padding: 10px;
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
    }

    .login-body {
      padding: 0 32px 24px;
    }

    .social-login {
      display: flex;
      justify-content: center;
      width: 100%;
    }

    #google-btn {
      width: 100%;
      display: flex;
      justify-content: center;
    }

    .divider-row {
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 16px 0;
    }

    .divider-row mat-divider {
      flex: 1;
    }

    .divider-text {
      font-size: 0.7rem;
      font-weight: 700;
      color: var(--muted);
      white-space: nowrap;
      letter-spacing: 0.5px;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .full-width {
      width: 100%;
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
      height: 36px;
      width: 100%;
      border: 1px solid rgba(0, 0, 0, 0.12);
      border-radius: 8px;
      padding: 0 12px 0 38px;
      font-size: 0.9rem;
      font-family: inherit;
      color: var(--ink);
      background: #ffffff;
      transition: all 0.2s ease;
      box-sizing: border-box;
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
      width: 28px;
      height: 28px;
      border-radius: 50%;
      color: var(--muted);
      transition: all 0.2s ease;
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

    .form-footer {
      display: flex;
      justify-content: flex-end;
      margin: -6px 0 2px;
    }

    .forgot-link {
      font-size: 0.8rem;
      font-weight: 600;
      height: 28px;
      line-height: 28px;
      color: var(--brand) !important;
    }

    .error-box {
      background: #fee2e2;
      color: #b91c1c;
      padding: 10px 14px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.8rem;
      font-weight: 600;
      border: 1px solid #fecaca;
    }

    .submit-btn {
      height: 38px;
      border-radius: 8px;
      font-size: 0.9rem;
      font-weight: 700;
      margin-top: 4px;
      background: linear-gradient(135deg, var(--brand) 0%, var(--brand-dark) 100%) !important;
      color: white !important;
      box-shadow: 0 4px 12px rgba(199, 115, 47, 0.2);
      transition: all 0.2s ease !important;
    }

    .submit-btn:hover:not([disabled]) {
      transform: translateY(-1px);
      box-shadow: 0 6px 16px rgba(199, 115, 47, 0.3);
    }

    .submit-btn:active:not([disabled]) {
      transform: translateY(0);
    }

    .login-footer {
      padding: 16px 32px;
      text-align: center;
      background: #f8fafc;
      border-top: 1px solid rgba(0, 0, 0, 0.05);
    }

    .login-footer p {
      margin: 0;
      font-size: 0.8rem;
      color: var(--muted);
    }

    .login-footer a {
      color: var(--brand);
      font-weight: 600;
      text-decoration: none;
    }

    @media (max-width: 960px) {
      .login-shell {
        background: #f8fafc;
      }
      .login-card {
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.9);
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
      }
    }

    @media (max-width: 480px) {
      .login-shell {
        background: #ffffff;
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
      .login-footer {
        background: transparent;
        border-top: none;
        padding: 8px 16px;
      }
    }
  `]
})
export class LoginPageComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly ngZone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);

  private googlePollTimer: ReturnType<typeof setTimeout> | null = null;
  private googleInitialized = false;

  readonly form = this.fb.nonNullable.group({
    loginId: ['', Validators.required],
    password: ['', Validators.required]
  });

  showPassword = signal(false);
  loading = signal(false);
  error = signal('');

  ngOnInit(): void {
    this.initGoogle();
  }

  ngOnDestroy(): void {
    if (this.googlePollTimer) clearTimeout(this.googlePollTimer);
  }

  private initGoogle(): void {
    const check = () => {
      if (this.googleInitialized) return;
      if (typeof google !== 'undefined' && google?.accounts?.id) {
        this.googleInitialized = true;
        this.googlePollTimer = null;
        google.accounts.id.initialize({
          client_id: environment.googleClientId,
          callback: (response) => {
            this.ngZone.run(() => this.handleGoogleCredential(response.credential));
          }
        });
        google.accounts.id.renderButton(
          document.getElementById('google-btn'),
          { theme: 'outline', size: 'large', text: 'signin_with', width: 360, shape: 'pill' }
        );
      } else {
        this.googlePollTimer = setTimeout(check, 300);
      }
    };
    check();
  }

  private handleGoogleCredential(idToken: string): void {
    if (!idToken) return;
    this.error.set('');
    this.loading.set(true);
    this.authService.googleLogin(idToken).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: () => { },
      error: (err) => {
        this.error.set(err?.error?.error ?? err?.error?.message ?? 'Google sign-in failed.');
      }
    });
  }

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  submit(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.authService.login(this.form.getRawValue()).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: () => { },
      error: (err) => {
        this.error.set(err?.error?.error || err?.error?.message || 'Login failed. Please check credentials.');
      }
    });
  }
}
