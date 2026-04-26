import { CommonModule } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

type NavLink = { label: string; path: string };

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="layout-shell">
      <aside class="sidebar panel">
        <div class="brand-block">
          <span class="eyebrow">KhanaBook</span>
          <h1>Web Admin</h1>
          <p class="muted">{{ session()?.userName || 'Operator' }}</p>
          <span class="chip">{{ session()?.role }}</span>
        </div>

        <nav class="nav-links">
          <a
            *ngFor="let link of links()"
            [routerLink]="link.path"
            routerLinkActive="active-link"
            class="nav-link"
          >
            {{ link.label }}
          </a>
        </nav>

        <button class="ghost-btn logout-btn" (click)="logout()">Logout</button>
      </aside>

      <main class="content-shell">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .layout-shell {
      min-height: 100vh;
      display: grid;
      grid-template-columns: 280px 1fr;
      gap: 1rem;
      padding: 1rem;
    }

    .sidebar {
      padding: 1.4rem;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
      position: sticky;
      top: 1rem;
      height: calc(100vh - 2rem);
      background:
        linear-gradient(180deg, rgba(181, 106, 45, 0.08), transparent 28%),
        var(--panel);
    }

    .brand-block h1 {
      margin: 0.35rem 0;
      font-size: 1.7rem;
    }

    .eyebrow {
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--brand-deep);
      font-size: 0.78rem;
      font-weight: 700;
    }

    .nav-links {
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
    }

    .nav-link {
      display: block;
      padding: 0.85rem 1rem;
      border-radius: 12px;
      color: var(--ink);
      text-decoration: none;
      font-weight: 600;
    }

    .nav-link.active-link {
      background: linear-gradient(135deg, rgba(181, 106, 45, 0.16), rgba(126, 68, 23, 0.12));
      color: var(--brand-deep);
    }

    .logout-btn {
      margin-top: auto;
    }

    .content-shell {
      min-width: 0;
    }

    @media (max-width: 960px) {
      .layout-shell {
        grid-template-columns: 1fr;
      }

      .sidebar {
        position: static;
        height: auto;
      }
    }
  `]
})
export class SidebarLayoutComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly session = this.authService.session;
  readonly links = computed<NavLink[]>(() => {
    const role = this.session()?.role;
    if (role === 'KBOOK_ADMIN') {
      return [
        { label: 'Platform Dashboard', path: '/admin/dashboard' },
        { label: 'Businesses', path: '/admin/businesses' }
      ];
    }

    const links: NavLink[] = [
      { label: 'Business Dashboard', path: '/business/dashboard' },
      { label: 'Orders', path: '/business/orders' },
      { label: 'Menu', path: '/business/menu' },
      { label: 'Staff', path: '/business/staff' }
    ];
    if (role === 'OWNER') {
      links.push({ label: 'Payment Settings', path: '/business/payment-settings' });
    }
    return links;
  });

  logout(): void {
    this.authService.logout();
  }
}
