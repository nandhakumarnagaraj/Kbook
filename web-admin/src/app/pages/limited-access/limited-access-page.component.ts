import { Component } from '@angular/core';

@Component({
  selector: 'app-limited-access-page',
  standalone: true,
  template: `
    <div class="page-shell">
      <section class="panel" style="padding: 2rem; max-width: 720px;">
        <h2>Limited Access</h2>
        <p class="muted">
          Web admin access is limited to <strong>KBOOK_ADMIN</strong>, <strong>OWNER</strong>,
          and <strong>SHOP_ADMIN</strong>. Owners and admins see the full dashboard; shop-admins
          get device management only. Any other legacy user role should be migrated before
          signing in here.
        </p>
      </section>
    </div>
  `
})
export class LimitedAccessPageComponent {}
