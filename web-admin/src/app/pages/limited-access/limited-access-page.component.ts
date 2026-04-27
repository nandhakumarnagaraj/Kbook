import { Component } from '@angular/core';

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
      </section>
    </div>
  `
})
export class LimitedAccessPageComponent {}
