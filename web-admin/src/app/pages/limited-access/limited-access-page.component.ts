import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-limited-access-page',
  standalone: true,
  template: `
    <main class="access-shell">
      <section class="panel access-card" role="main" aria-labelledby="access-title">
        <div class="access-mark" aria-hidden="true">K</div>
        <span class="eyebrow">KhanaBook Web Admin</span>
        <h1 id="access-title">This workspace is not available for your account</h1>
        <p class="muted">
          You are signed in as <strong>{{ roleLabel }}</strong>. Your account does not have access to the requested administration area.
        </p>
        <div class="access-note">
          <strong>What you can do</strong>
          <p>{{ guidance }}</p>
        </div>
        <button type="button" class="primary-btn" (click)="signOut()">Sign out</button>
      </section>
    </main>
  `,
  styles: [`
    .access-shell {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 1rem;
    }
    .access-card {
      width: min(560px, 100%);
      padding: clamp(1.5rem, 5vw, 2.5rem);
      text-align: center;
    }
    .access-mark {
      display: grid;
      place-items: center;
      width: 3.25rem;
      height: 3.25rem;
      margin: 0 auto 1rem;
      color: #fff;
      background: linear-gradient(135deg, var(--brand), var(--brand-deep));
      border-radius: 16px;
      box-shadow: var(--shadow-soft);
      font-size: 1.35rem;
      font-weight: 800;
    }
    .eyebrow {
      color: var(--brand-deep);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      font-size: 0.76rem;
      font-weight: 800;
    }
    h1 { margin: 0.55rem 0 0.75rem; font-size: clamp(1.5rem, 4vw, 2rem); }
    .access-note {
      margin: 1.5rem 0;
      padding: 1rem;
      color: var(--brand-deep);
      background: var(--brand-soft);
      border: 1px solid var(--line);
      border-radius: 14px;
    }
    .access-note p { margin: 0.35rem 0 0; color: var(--ink); line-height: 1.5; }
    .primary-btn { width: 100%; }
  `]
})
export class LimitedAccessPageComponent {
  private readonly authService = inject(AuthService);
  readonly session = this.authService.session;

  get roleLabel(): string {
    const role = this.session()?.role;
    if (role === 'KBOOK_ADMIN') return 'Platform Administrator';
    if (role === 'OWNER') return 'Restaurant Owner';
    if (role === 'SHOP_ADMIN') return 'Terminal Administrator';
    return 'Limited-access user';
  }

  get guidance(): string {
    return this.session()?.role === 'SHOP_ADMIN'
      ? 'Use the Devices area to manage terminals and device requests.'
      : 'Ask your KhanaBook administrator to confirm your account role and business access.';
  }

  signOut(): void { this.authService.logout(); }
}
