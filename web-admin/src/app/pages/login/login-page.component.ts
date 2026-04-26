import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="login-shell">
      <div class="login-card panel">
        <div>
          <span class="eyebrow">Phase 1</span>
          <h1>KhanaBook Web Admin</h1>
          <p class="muted">Use your existing KhanaBook login. The JWT token is stored locally after login.</p>
        </div>

        <form [formGroup]="form" (ngSubmit)="submit()">
          <label>
            Login ID
            <input formControlName="loginId" placeholder="Phone number or email">
          </label>

          <label>
            Password
            <input type="password" formControlName="password" placeholder="Enter password">
          </label>

          <div *ngIf="error" class="alert error">{{ error }}</div>

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

    h1 {
      margin: 0.25rem 0 0.5rem;
      font-size: 2rem;
    }

    form {
      display: grid;
      gap: 1rem;
    }

    label {
      display: grid;
      gap: 0.45rem;
      font-weight: 600;
    }

    input {
      border: 1px solid var(--line);
      border-radius: 12px;
      padding: 0.9rem 1rem;
      background: #fff;
    }

    .eyebrow {
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--brand-deep);
      font-size: 0.78rem;
      font-weight: 700;
    }
  `]
})
export class LoginPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  readonly form = this.fb.nonNullable.group({
    loginId: ['', Validators.required],
    password: ['', Validators.required]
  });

  loading = false;
  error = '';

  submit(): void {
    if (this.form.invalid || this.loading) {
      return;
    }

    this.loading = true;
    this.error = '';
    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.error || err?.error?.message || 'Login failed.';
      }
    });
  }
}
