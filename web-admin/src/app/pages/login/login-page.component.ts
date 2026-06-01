import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, NgZone, OnDestroy, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
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
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatFormFieldModule,
    MatInputModule
  ],
  template: `
    <div class="login-page">
      <!-- Desktop: left brand panel -->
      <div class="login-brand-panel">
        <div class="brand-content">
          <img src="khanabook_logo.png" alt="KhanaBook" class="brand-logo" width="56" height="56" />
          <h1 class="brand-name">KhanaBook</h1>
          <p class="brand-tagline">Restaurant Operating System</p>

          <div class="brand-features">
            <div class="brand-feature">
              <mat-icon aria-hidden="true">bolt</mat-icon>
              <span>Offline-First Billing</span>
            </div>
            <div class="brand-feature">
              <mat-icon aria-hidden="true">analytics</mat-icon>
              <span>Real-Time Analytics</span>
            </div>
            <div class="brand-feature">
              <mat-icon aria-hidden="true">cable</mat-icon>
              <span>Multi-Terminal Sync</span>
            </div>
          </div>

          <p class="brand-support">
            24/7 Support &middot;
            <a href="mailto:support&#64;khanabook.com">support&#64;khanabook.com</a>
          </p>
        </div>
      </div>

      <!-- Right: login form -->
      <div class="login-form-panel">
        <mat-card class="login-card" appearance="outlined">
          <div class="login-card-header">
            <img src="khanabook_logo.png" alt="KhanaBook" class="mobile-logo" width="48" height="48" />
            <h2 class="login-title">Sign in</h2>
            <p class="login-subtitle">Access your business dashboard</p>
          </div>

          <form [formGroup]="form" (ngSubmit)="submit()" class="login-form" role="form" aria-label="Login form">
            <mat-form-field appearance="outline">
              <mat-label>Login ID</mat-label>
              <input matInput formControlName="loginId" placeholder="Phone or email"
                     autocomplete="username" #loginIdInput>
              <mat-icon matIconPrefix>person</mat-icon>
              @if (form.get('loginId')?.touched && form.get('loginId')?.invalid) {
                <mat-error>Login ID is required</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Password</mat-label>
              <input matInput [type]="showPassword() ? 'text' : 'password'"
                     formControlName="password" autocomplete="current-password"
                     placeholder="Enter password">
              <mat-icon matIconPrefix>lock</mat-icon>
              <button mat-icon-button matSuffix type="button"
                      (click)="togglePassword()"
                      [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'">
                <mat-icon>{{ showPassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              @if (form.get('password')?.touched && form.get('password')?.invalid) {
                <mat-error>Password is required</mat-error>
              }
            </mat-form-field>

            <div class="login-actions">
              <a mat-button color="primary" routerLink="/forgot-password" class="forgot-link">
                Forgot password?
              </a>
            </div>

            @if (error()) {
              <div class="error-message" role="alert" aria-live="assertive">
                <mat-icon aria-hidden="true">error_outline</mat-icon>
                <span>{{ error() }}</span>
              </div>
            }

            <button mat-flat-button color="primary" class="submit-btn"
                    [disabled]="form.invalid || loading()" type="submit">
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

          <p class="login-footer-text">
            Need help?
            <a href="mailto:support&#64;khanabook.com">Contact support</a>
          </p>
        </mat-card>
      </div>
    </div>
  `,
  styles: [`
    .login-page {
      display: flex;
      min-height: 100dvh;
      background: var(--bg);
    }

    /* ── Brand Panel ── */
    .login-brand-panel {
      display: none;
      flex: 1;
      background: var(--bg-elevated);
      border-right: 1px solid var(--line);
      padding: 48px;
      align-items: center;
      justify-content: center;
    }

    @media (min-width: 960px) {
      .login-brand-panel { display: flex; }
    }

    .brand-content {
      max-width: 400px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .brand-logo {
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow-sm);
    }

    .brand-name {
      font-size: 1.75rem;
      font-weight: 800;
      color: var(--ink);
      margin: 0;
      letter-spacing: -0.5px;
    }

    .brand-tagline {
      font-size: 0.95rem;
      color: var(--muted);
      margin: -16px 0 0;
    }

    .brand-features {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .brand-feature {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 0.9rem;
      color: var(--ink-secondary);
    }

    .brand-feature mat-icon {
      color: var(--brand);
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .brand-support {
      font-size: 0.8rem;
      color: var(--muted);
      margin-top: auto;
    }

    .brand-support a {
      color: var(--brand);
      text-decoration: none;
      font-weight: 600;
    }

    .brand-support a:hover {
      text-decoration: underline;
    }

    /* ── Form Panel ── */
    .login-form-panel {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }

    .login-card {
      width: 100%;
      max-width: 400px;
      padding: 32px;
      border-radius: var(--radius-xl);
      border: 1px solid var(--line);
      box-shadow: var(--shadow-md);
    }

    .login-card-header {
      text-align: center;
      margin-bottom: 24px;
    }

    .mobile-logo {
      display: block;
      margin: 0 auto 12px;
      border-radius: var(--radius-md);
      box-shadow: var(--shadow-sm);
    }

    @media (min-width: 960px) {
      .mobile-logo { display: none; }
    }

    .login-title {
      font-size: 1.5rem;
      font-weight: 700;
      color: var(--ink);
      margin: 0;
    }

    .login-subtitle {
      font-size: 0.85rem;
      color: var(--muted);
      margin: 4px 0 0;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .login-actions {
      display: flex;
      justify-content: flex-end;
      margin-top: -8px;
    }

    .forgot-link {
      font-size: 0.8rem;
      font-weight: 600;
      height: 28px;
      line-height: 28px;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      background: var(--danger-soft);
      color: var(--danger);
      border: 1px solid color-mix(in srgb, var(--danger) 20%, transparent);
      border-radius: var(--radius-md);
      font-size: 0.8rem;
      font-weight: 600;
    }

    .error-message mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
      flex-shrink: 0;
    }

    .submit-btn {
      width: 100%;
      height: 48px;
      font-size: 0.9rem;
      font-weight: 700;
    }

    .submit-btn mat-spinner {
      margin: 0 auto;
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

    .social-login {
      display: flex;
      justify-content: center;
    }

    #google-btn {
      width: 100%;
      display: flex;
      justify-content: center;
    }

    .login-footer-text {
      text-align: center;
      font-size: 0.8rem;
      color: var(--muted);
      margin: 16px 0 0;
    }

    .login-footer-text a {
      color: var(--brand);
      text-decoration: none;
      font-weight: 600;
    }

    .login-footer-text a:hover {
      text-decoration: underline;
    }

    /* ── Responsive ── */
    @media (max-width: 480px) {
      .login-form-panel { padding: 16px; }
      .login-card {
        padding: 24px 16px;
        box-shadow: none;
        border: none;
      }
      .login-title { font-size: 1.35rem; }
      .login-form { gap: 12px; }
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
