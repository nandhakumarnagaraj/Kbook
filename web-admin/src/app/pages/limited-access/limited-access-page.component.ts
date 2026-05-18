import { Component } from '@angular/core';

@Component({
  selector: 'app-limited-access-page',
  standalone: true,
  template: `
    <div class="page-shell">
      <section class="panel limited-card">
        <h2>Limited Access</h2>
        <p class="muted">
          Web admin access is limited to <strong>KBOOK_ADMIN</strong> and <strong>OWNER</strong>.
          Any legacy user role should be migrated before signing in here.
        </p>
        <div class="hero-meta">
          <span class="chip warn">Role restricted</span>
          <span class="chip">Migration required</span>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .limited-card {
      max-width: 720px;
      margin: 0 auto;
      padding: 2rem;
    }
  `]
})
export class LimitedAccessPageComponent {}
