import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-limited-access-page',
  standalone: true,
  template: `
    <div class="page-shell">
      <section class="panel" style="padding: 2rem; max-width: 720px;">
        <h2>Limited Access</h2>
        <p class="muted">
          Web admin access is limited to <strong>KBOOK_ADMIN</strong> and <strong>OWNER</strong>.
          Any legacy user role should be migrated before signing in here.
        </p>
        <p class="muted" style="margin-top: 1rem;">
          Your account role is: <span class="chip">{{ session()?.role || 'unknown' }}</span>
        </p>
      </section>
    </div>
  `
})
export class LimitedAccessPageComponent {
  private readonly authService = inject(AuthService);
  readonly session = this.authService.session;
}
