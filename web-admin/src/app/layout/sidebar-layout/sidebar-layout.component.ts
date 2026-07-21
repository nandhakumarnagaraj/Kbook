import { CommonModule } from '@angular/common';
import { Component, computed, ElementRef, HostListener, inject, signal, ViewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

type NavLink = { label: string; path: string };

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="layout-shell">
      <header class="topbar">
        <button
          #menuButton
          type="button"
          class="hamburger"
          aria-label="Toggle navigation menu"
          [attr.aria-expanded]="menuOpen()"
          aria-controls="sidebar-nav"
          (click)="toggleMenu()">
          <span class="hamburger__bar"></span>
          <span class="hamburger__bar"></span>
          <span class="hamburger__bar"></span>
        </button>
        <span class="topbar__title">KhanaBook Web Admin</span>
      </header>

      <div
        class="sidebar-backdrop"
        *ngIf="menuOpen()"
        (click)="closeMenu()"
        aria-hidden="true"></div>

      <aside
        #sidebar
        class="sidebar panel"
        [class.sidebar--open]="menuOpen()"
        id="sidebar-nav"
        aria-label="Primary navigation"
        tabindex="-1"
      >
        <div class="brand-block">
          <div class="brand-logo" aria-hidden="true">
            <span class="brand-logo__mark">K</span>
          </div>
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
            (click)="closeMenu()">
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
      gap: 1.25rem;
      padding: 1.25rem;
      align-items: start;
    }

    .sidebar {
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
      position: sticky;
      top: 1.25rem;
      height: calc(100vh - 2.5rem);
      background: var(--panel);
    }

    .brand-block h1 {
      margin: 0.35rem 0;
      font-size: 1.7rem;
    }

    .brand-logo {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 48px;
      height: 48px;
      border-radius: 8px;
      background: var(--brand);
      box-shadow: 0 1px 3px rgba(0,0,0,0.06);
      margin-bottom: 0.35rem;
    }

    .brand-logo__mark {
      color: #fff;
      font-weight: 800;
      font-size: 1.5rem;
      line-height: 1;
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
      border-radius: 8px;
      color: var(--ink);
      text-decoration: none;
      font-weight: 600;
      border: 1px solid transparent;
      transition: background 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
    }

    .nav-link:hover {
      background: rgba(249, 115, 22, 0.08);
      border-color: rgba(249, 115, 22, 0.12);
      transform: translateX(2px);
    }

    .nav-link.active-link {
      background: rgba(249, 115, 22, 0.16);
      color: var(--brand-deep);
      border-color: rgba(249, 115, 22, 0.18);
    }

    .logout-btn {
      margin-top: auto;
    }

    .content-shell {
      min-width: 0;
    }

    .topbar {
      display: none;
    }

    .hamburger {
      display: inline-flex;
      flex-direction: column;
      justify-content: center;
      gap: 4px;
      width: 44px;
      height: 44px;
      padding: 0 10px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: var(--panel);
      cursor: pointer;
    }

    .hamburger__bar {
      display: block;
      height: 2px;
      border-radius: 2px;
      background: var(--ink);
    }

    .sidebar-backdrop {
      display: none;
    }

    @media (max-width: 1024px) {
      .layout-shell {
        grid-template-columns: 1fr;
        padding: 0;
        gap: 0;
      }

      .topbar {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        position: sticky;
        top: 0;
        z-index: 30;
        padding: 0.75rem 1rem;
        background: var(--panel);
        border-bottom: 1px solid var(--line);
      }

      .topbar__title {
        font-weight: 700;
        color: var(--brand-deep);
      }

      .sidebar {
        position: fixed;
        top: 0;
        left: 0;
        z-index: 40;
        width: 280px;
        max-width: 85vw;
        height: 100vh;
        border-radius: 0;
        transform: translateX(-100%);
        transition: transform 0.25s ease;
        box-shadow: var(--shadow);
      }

      .sidebar--open {
        transform: translateX(0);
      }

      .sidebar-backdrop {
        display: block;
        position: fixed;
        inset: 0;
        z-index: 35;
        background: rgba(30, 41, 59, 0.45);
      }

      .content-shell {
        padding: 1rem;
      }
    }

    @media (min-width: 1025px) {
      .sidebar-backdrop {
        display: none !important;
      }
    }
  `]
})
export class SidebarLayoutComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  @ViewChild('menuButton') private menuButton?: ElementRef<HTMLButtonElement>;
  @ViewChild('sidebar') private sidebar?: ElementRef<HTMLElement>;

  readonly menuOpen = signal(false);

  readonly session = this.authService.session;
  readonly links = computed<NavLink[]>(() => {
    const role = this.session()?.role;
    if (role === 'KBOOK_ADMIN') {
      return [
        { label: 'Platform Dashboard', path: '/admin/dashboard' },
        { label: 'Businesses', path: '/admin/businesses' }
      ];
    }

    if (role === 'SHOP_ADMIN') {
      return [{ label: 'Devices', path: '/business/terminals' }];
    }

    const links: NavLink[] = [
      { label: 'Business Dashboard', path: '/business/dashboard' },
      { label: 'Reports', path: '/business/reports' },
      { label: 'Orders', path: '/business/orders' },
      { label: 'Menu', path: '/business/menu' },
      { label: 'Staff', path: '/business/staff' },
      { label: 'Payments & Integrations', path: '/business/marketplace' },
      { label: 'Devices', path: '/business/terminals' }
    ];
    return links;
  });

  toggleMenu(): void {
    if (this.menuOpen()) {
      this.closeMenu();
      return;
    }
    this.menuOpen.set(true);
    setTimeout(() => {
      const firstLink = this.sidebar?.nativeElement.querySelector<HTMLElement>('.nav-link');
      (firstLink ?? this.sidebar?.nativeElement)?.focus();
    });
  }

  closeMenu(restoreFocus = true): void {
    if (!this.menuOpen()) return;
    this.menuOpen.set(false);
    if (restoreFocus) setTimeout(() => this.menuButton?.nativeElement.focus());
  }

  @HostListener('document:keydown', ['$event'])
  handleMenuKeydown(event: KeyboardEvent): void {
    if (!this.menuOpen()) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeMenu();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusable = this.sidebar?.nativeElement.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])'
    );
    if (!focusable?.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  logout(): void {
    this.authService.logout();
  }
}
