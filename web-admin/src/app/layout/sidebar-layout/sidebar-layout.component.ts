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
        <div class="topbar__brand">
          <div class="brand-logo brand-logo--sm" aria-hidden="true">
            <span class="brand-logo__mark">K</span>
          </div>
          <span class="topbar__title">KhanaBook</span>
        </div>
      </header>

      <div
        class="sidebar-backdrop"
        *ngIf="menuOpen()"
        (click)="closeMenu()"
        aria-hidden="true"></div>

      <aside
        #sidebar
        class="sidebar"
        [class.sidebar--open]="menuOpen()"
        id="sidebar-nav"
        aria-label="Primary navigation"
        tabindex="-1"
      >
        <div class="brand-block">
          <div class="brand-row">
            <div class="brand-logo" aria-hidden="true">
              <span class="brand-logo__mark">K</span>
            </div>
            <div class="brand-copy">
              <span class="eyebrow">KhanaBook</span>
              <h1>Web Admin</h1>
            </div>
          </div>
          <div class="user-card">
            <div class="user-avatar" aria-hidden="true">
              {{ (session()?.userName || 'O').charAt(0).toUpperCase() }}
            </div>
            <div class="user-meta">
              <span class="user-name">{{ session()?.userName || 'Operator' }}</span>
              <span class="user-role">{{ session()?.role }}</span>
            </div>
          </div>
        </div>

        <nav class="nav-links" aria-label="Main">
          <a
            *ngFor="let link of links()"
            [routerLink]="link.path"
            routerLinkActive="active-link"
            class="nav-link"
            (click)="closeMenu()">
            <span class="nav-link__dot" aria-hidden="true"></span>
            <span class="nav-link__label">{{ link.label }}</span>
          </a>
        </nav>

        <button class="logout-btn" (click)="logout()" type="button">
          <span aria-hidden="true">↩</span>
          <span>Sign out</span>
        </button>
      </aside>

      <main class="content-shell">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      background: var(--bg);
    }

    .layout-shell {
      min-height: 100vh;
      display: grid;
      grid-template-columns: 260px 1fr;
      align-items: start;
    }

    .sidebar {
      padding: 1.25rem 0.9rem 1rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      position: sticky;
      top: 0;
      height: 100vh;
      background: var(--panel);
      border-right: 1px solid var(--line);
    }

    .brand-block {
      display: grid;
      gap: 1rem;
      padding: 0 0.35rem;
    }

    .brand-row {
      display: flex;
      align-items: center;
      gap: 0.7rem;
    }

    .brand-copy { display: grid; }

    .brand-copy h1 {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 700;
      letter-spacing: -0.02em;
    }

    .brand-logo {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: linear-gradient(135deg, var(--brand) 0%, var(--brand-deep) 100%);
      box-shadow: 0 4px 10px -3px rgba(217, 119, 6, 0.5);
      flex-shrink: 0;
    }

    .brand-logo--sm {
      width: 32px;
      height: 32px;
      border-radius: 8px;
    }

    .brand-logo__mark {
      color: #fff;
      font-weight: 800;
      font-size: 1.2rem;
      line-height: 1;
      letter-spacing: -0.02em;
    }

    .brand-logo--sm .brand-logo__mark { font-size: 0.95rem; }

    .eyebrow {
      text-transform: uppercase;
      letter-spacing: 0.1em;
      color: var(--muted);
      font-size: 0.65rem;
      font-weight: 700;
    }

    .user-card {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      padding: 0.6rem 0.7rem;
      background: var(--panel-2);
      border: 1px solid var(--line);
      border-radius: 12px;
    }

    .user-avatar {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      background: var(--brand-soft);
      color: var(--brand-deep);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 0.85rem;
      flex-shrink: 0;
    }

    .user-meta {
      display: grid;
      min-width: 0;
    }

    .user-name {
      font-weight: 600;
      font-size: 0.85rem;
      color: var(--ink);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .user-role {
      font-size: 0.68rem;
      color: var(--muted);
      text-transform: uppercase;
      letter-spacing: 0.06em;
      font-weight: 600;
    }

    .nav-links {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
    }

    .nav-link {
      position: relative;
      display: flex;
      align-items: center;
      gap: 0.65rem;
      padding: 0.6rem 0.85rem;
      border-radius: 8px;
      color: var(--ink-2);
      text-decoration: none;
      font-weight: 500;
      font-size: 0.88rem;
      transition: background 0.15s ease, color 0.15s ease;
    }

    .nav-link__dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: transparent;
      transition: background 0.15s ease, transform 0.15s ease;
    }

    .nav-link:hover {
      background: var(--panel-2);
      color: var(--ink);
    }

    .nav-link:hover .nav-link__dot {
      background: var(--muted);
    }

    .nav-link.active-link {
      background: var(--brand-soft);
      color: var(--brand-deep);
      font-weight: 650;
    }

    .nav-link.active-link::before {
      content: "";
      position: absolute;
      left: -0.9rem;
      top: 0.55rem;
      bottom: 0.55rem;
      width: 3px;
      border-radius: 0 3px 3px 0;
      background: var(--brand);
    }

    .nav-link.active-link .nav-link__dot {
      background: var(--brand);
      transform: scale(1.2);
    }

    .logout-btn {
      margin-top: auto;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.65rem 0.85rem;
      background: transparent;
      color: var(--muted);
      border: 1px solid var(--line);
      border-radius: 10px;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.85rem;
      transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
    }

    .logout-btn:hover {
      background: var(--danger-soft);
      color: var(--danger);
      border-color: rgba(185, 28, 28, 0.25);
    }

    .content-shell {
      min-width: 0;
      width: 100%;
    }

    .topbar { display: none; }

    .topbar__brand {
      display: flex;
      align-items: center;
      gap: 0.55rem;
    }

    .hamburger {
      display: inline-flex;
      flex-direction: column;
      justify-content: center;
      gap: 4px;
      width: 40px;
      height: 40px;
      padding: 0 10px;
      border: 1px solid var(--line-strong);
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

    .sidebar-backdrop { display: none; }

    @media (max-width: 1024px) {
      .layout-shell {
        grid-template-columns: 1fr;
      }

      .topbar {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        position: sticky;
        top: 0;
        z-index: 30;
        padding: 0.7rem 1rem;
        background: var(--panel);
        border-bottom: 1px solid var(--line);
      }

      .topbar__title {
        font-weight: 700;
        color: var(--ink);
        letter-spacing: -0.01em;
      }

      .sidebar {
        position: fixed;
        top: 0;
        left: 0;
        z-index: 40;
        width: 280px;
        max-width: 85vw;
        height: 100vh;
        border-right: 1px solid var(--line);
        transform: translateX(-100%);
        transition: transform 0.25s ease;
        box-shadow: var(--shadow-lg);
      }

      .sidebar--open { transform: translateX(0); }

      .sidebar-backdrop {
        display: block;
        position: fixed;
        inset: 0;
        z-index: 35;
        background: rgba(15, 17, 21, 0.5);
        backdrop-filter: blur(2px);
      }
    }

    @media (min-width: 1025px) {
      .sidebar-backdrop { display: none !important; }
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
      { label: 'Integrations', path: '/business/marketplace' },
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
