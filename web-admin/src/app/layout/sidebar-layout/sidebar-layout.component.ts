import { CommonModule } from '@angular/common';
import { Component, computed, ElementRef, HostListener, inject, signal, ViewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

type NavLink = { label: string; path: string; icon: string };

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
            <span class="nav-link__icon" aria-hidden="true">{{ link.icon }}</span>
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
      background: #140d07;
      border-right: 1px solid rgba(255, 255, 255, 0.08);
      color: #f3f4f6;
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
      color: #ffffff;
    }

    .brand-logo {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: linear-gradient(135deg, var(--brand) 0%, #b45309 100%);
      box-shadow: 0 4px 14px -3px rgba(217, 119, 6, 0.6);
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
      color: #fbbf24;
      font-size: 0.65rem;
      font-weight: 700;
    }

    .user-card {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      padding: 0.65rem 0.75rem;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      backdrop-filter: blur(8px);
    }

    .user-avatar {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      background: var(--gradient-primary);
      color: #ffffff;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 0.85rem;
      flex-shrink: 0;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
    }

    .user-meta {
      display: grid;
      min-width: 0;
    }

    .user-name {
      font-weight: 600;
      font-size: 0.85rem;
      color: #ffffff;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .user-role {
      font-size: 0.68rem;
      color: #fbbf24;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      font-weight: 700;
    }

    .nav-links {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .nav-link {
      position: relative;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.65rem 0.85rem;
      border-radius: 10px;
      color: rgba(255, 255, 255, 0.65);
      text-decoration: none;
      font-weight: 500;
      font-size: 0.88rem;
      transition: all 0.15s ease;
    }

    .nav-link__icon {
      font-size: 1rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
    }

    .nav-link:hover {
      background: rgba(255, 255, 255, 0.08);
      color: #ffffff;
    }

    .nav-link.active-link {
      background: linear-gradient(90deg, rgba(217, 119, 6, 0.28) 0%, rgba(217, 119, 6, 0.06) 100%);
      color: #fbbf24;
      font-weight: 700;
      border: 1px solid rgba(245, 158, 11, 0.25);
    }

    .nav-link.active-link::before {
      content: "";
      position: absolute;
      left: 0;
      top: 0.35rem;
      bottom: 0.35rem;
      width: 4px;
      border-radius: 0 4px 4px 0;
      background: #f59e0b;
      box-shadow: 0 0 8px #f59e0b;
    }

    .logout-btn {
      margin-top: auto;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.65rem 0.85rem;
      background: rgba(255, 255, 255, 0.04);
      color: rgba(255, 255, 255, 0.65);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.85rem;
      transition: all 0.15s ease;
    }

    .logout-btn:hover {
      background: rgba(239, 68, 68, 0.2);
      color: #fca5a5;
      border-color: rgba(239, 68, 68, 0.4);
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
        { label: 'Platform Dashboard', path: '/admin/dashboard', icon: '◉' },
        { label: 'Businesses', path: '/admin/businesses', icon: '🏢' }
      ];
    }

    if (role === 'SHOP_ADMIN') {
      return [{ label: 'Devices', path: '/business/terminals', icon: '▣' }];
    }

    const links: NavLink[] = [
      { label: 'Business Dashboard', path: '/business/dashboard', icon: '◉' },
      { label: 'Reports', path: '/business/reports', icon: '◔' },
      { label: 'Orders', path: '/business/orders', icon: '▤' },
      { label: 'Menu', path: '/business/menu', icon: '◈' },
      { label: 'Staff', path: '/business/staff', icon: '◍' },
      { label: 'Integrations', path: '/business/marketplace', icon: '◇' },
      { label: 'Devices', path: '/business/terminals', icon: '▣' }
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
