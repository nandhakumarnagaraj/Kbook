import { CommonModule } from '@angular/common';
import { Component, OnInit, NgZone, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { environment } from '../../../environments/environment';

declare const google: any;

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
          <p class="muted">Sign in to manage storefront operations, menu data, team access, and payments from one place.</p>
          <div class="hero-meta">
            <span class="chip">Fast Access</span>
            <span class="chip success">Business Control</span>
          </div>
        </div>

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

          <div *ngIf="error" class="alert-box error">{{ error }}</div>

          <button class="primary-btn" [disabled]="form.invalid || loading">
            {{ loading ? 'Signing in...' : 'Sign in' }}
          </button>
        </form>
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

    #google-btn { display: flex; justify-content: center; }
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
    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => { this.loading = false; },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.error || err?.error?.message || 'Login failed.';
      }
    });
  }
}
