import { CommonModule } from '@angular/common';
import { Component, OnInit, NgZone, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

declare const google: any;

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
    <section class="login-shell">
      <div class="login-card">
        <div class="login-header">
          <img src="/khanabook_logo.png" alt="KhanaBook" class="login-logo" />
          <h1>Welcome back</h1>
          <p class="muted">Sign in to manage your restaurant — menu, orders, payments, and team.</p>
        </div>

        <div class="login-body">
          <div id="google-btn"></div>
          <div *ngIf="googleError" class="msg msg-error">{{ googleError }}</div>

          <div class="divider"><span>or sign in with password</span></div>

          <form [formGroup]="form" (ngSubmit)="submit()">
            <label class="field">
              Login ID
              <input class="field-input" formControlName="loginId" placeholder="Phone or email" />
            </label>

            <label class="field">
              Password
              <input class="field-input" type="password" formControlName="password" placeholder="Enter password" />
            </label>

            <div class="forgot-link">
              <a routerLink="/forgot-password">Forgot password?</a>
            </div>

            <div *ngIf="error" class="msg msg-error">{{ error }}</div>

            <button class="submit-btn" [disabled]="form.invalid || loading">
              {{ loading ? 'Signing in...' : 'Sign in' }}
            </button>
          </form>
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

    .divider {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      color: var(--muted);
      font-size: 0.82rem;
    }
    .divider::before, .divider::after {
      content: '';
      flex: 1;
      height: 1px;
      background: var(--line);
    }

    .forgot-link {
      text-align: right;
      font-size: 0.82rem;
      margin-top: -0.25rem;
    }
    .forgot-link a {
      color: var(--brand);
      text-decoration: none;
      font-weight: 600;
    }
    .forgot-link a:hover { text-decoration: underline; }

    .msg {
      border-radius: 10px;
      padding: 0.7rem 1rem;
      font-size: 0.85rem;
      text-align: center;
    }
    .msg-error { color: var(--danger); background: var(--danger-soft); border: 1px solid rgba(166,55,47,0.2); }

    #google-btn {
      display: flex;
      justify-content: center;
      overflow: hidden;
      max-width: 100%;
    }
    #google-btn > div { max-width: 100%; }
    #google-btn iframe { max-width: 100%; }

    @keyframes slideUp {
      from { opacity: 0; transform: translateY(16px); }
      to { opacity: 1; transform: translateY(0); }
    }

    @media (max-width: 520px) {
      .login-shell { padding: 1rem; }
      .login-header { padding: 1.5rem 1.25rem 1rem; }
      .login-body { padding: 1.25rem; }
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

  loading = false;
  error = '';
  googleError = '';

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
          { theme: 'outline', size: 'large', text: 'signin_with' }
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
    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => { this.loading = false; },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.error || err?.error?.message || 'Login failed.';
      }
    });
  }
}
