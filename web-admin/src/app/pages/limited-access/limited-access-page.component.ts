import { Component } from '@angular/core';

@Component({
  selector: 'app-limited-access-page',
  standalone: true,
  template: `
    <div class="page-shell">
      <section class="panel" style="padding: 2rem; max-width: 720px;">
        <h2>Limited Access</h2>
        <p class="muted">
          Phase 1 web admin is enabled for <strong>KBOOK_ADMIN</strong>, <strong>OWNER</strong>, and <strong>MANAGER</strong>.
          <strong>CASHIER</strong> and <strong>KITCHEN</strong> roles are reserved as placeholders for a later phase.
        </p>
      </section>
    </div>
  `
})
export class LimitedAccessPageComponent {}
