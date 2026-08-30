import { CommonModule } from '@angular/common';
import { Component, computed, ElementRef, ViewChild, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BottomActionBarComponent } from '../bottom-action-bar/bottom-action-bar.component';
import { AuthService } from '../../core/auth/auth.service';
import { BottomActionBarModule } from '../bottom-action-bar/bottom-action-bar.module';

type NavLink = { label: string; path: string; icon: string };

type BottomActionBarItem = { label: string; icon: string; route: string; badge?: string };

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, BottomActionBarComponent],
  template: `
    <div class="layout-shell">
      <a class="skip-link" href="#main-content">Skip to main content</a>
      <header class="topbar topbar--mobile">
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

      <!-- Mobile backdrop -->
      <div
        class="sidebar-backdrop"
        *ngIf="menuOpen()"
        (click)="closeMenu()"
        aria-hidden="true"></div>

      <!-- Sidebar -->
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

      <div class="workspace">
        <header class="desktop-topbar">
          <div class="business-context">
            <div class="context-mark" aria-hidden="true">{{ contextInitial() }}</div>
            <div class="context-copy">
              <strong>{{ contextTitle() }}</strong>
              <span>{{ contextSubtitle() }}</span>
            </div>
          </div>
          <div class="topbar-actions">
            <button *ngIf="session()?.role === 'OWNER'" type="button" class="quick-search" (click)="openOrders()">
              <span aria-hidden="true">⌕</span>
              <span>Search orders</span>
              <kbd>Ctrl K</kbd>
            </button>
            <span class="restaurant-chip" *ngIf="session()?.restaurantId as restaurantId">Restaurant #{{ restaurantId }}</span>
            <div class="topbar-avatar" [attr.aria-label]="'Signed in as ' + (session()?.userName || 'Operator')">
              {{ (session()?.userName || 'O').charAt(0).toUpperCase() }}
            </div>
          </div>
        </header>
        <main id="main-content" class="content-shell" tabindex="-1">
          <router-outlet />
        </main>
      </div>

      <!-- Mobile bottom action bar -->
      <kb-bottom-action-bar
        *ngIf="isMobileView"
        [items]="bottomActionItems()"
        [selectedIndex]="selectedBottomTab()"
        (indexChange)="onBottomTabChange($event)"
      />
    </div>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; background: var(--kb-color-bg-app); }

    .layout-shell {
      min-height: 100vh;
      display: grid;
      grid-template-columns: 260px 1fr;
      align-items: start;
    }
    .workspace { min-width: 0; min-height: 100vh; display: flex; flex-direction: column; }
    .skip-link {
      position: fixed; left: 1rem; top: 0; z-index: 100;
      padding: 0.65rem 1rem; color: var(--kb-color-foreground); background: var(--kb-color-primary);
      border-radius: var(--kb-radius-sm); transform: translateY(-110%);
    }
    .skip-link:focus { transform: translateY(0); }

    /* ── Sidebar (minimalism dark) ── */
    .sidebar {
      padding: var(--kb-space-3) var(--kb-space-4);
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-4);
      position: sticky;
      top: 0;
      height: 100vh;
      background: var(--kb-color-foreground);
      border-right: 1px solid var(--kb-color-border);
      color: var(--kb-color-foreground-contrast);
    }

    .brand-block { display: grid; gap: var(--kb-space-4); padding: 0 var(--kb-space-3); }
    .brand-row { display: flex; align-items: center; gap: var(--kb-space-3); }
    .brand-copy { display: grid; }
    .brand-copy h1 { margin: 0; font-size: clamp(1rem, 4vw, var(--kb-font-size-h1)); font-weight: 700; letter-spacing: -0.02em; color: var(--kb-color-foreground-contrast); }

    .brand-logo {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: var(--kb-radius-md);
      background: linear-gradient(135deg, var(--kb-color-primary) 0%, #60A5FA 100%);
      box-shadow: var(--kb-shadow-sm);
      flex-shrink: 0;
    }
    .brand-logo--sm { width: 32px; height: 32px; border-radius: var(--kb-radius-sm); }
    .brand-logo__mark { color: var(--kb-color-primary-foreground); font-weight: 800; font-size: 1.2rem; line-height: 1; letter-spacing: -0.02em; }
    .brand-logo--sm .brand-logo__mark { font-size: 0.95rem; }

    .eyebrow { text-transform: uppercase; letter-spacing: 0.1em; color: var(--kb-color-muted); font-size: 0.65rem; font-weight: 700; }

    /* User card */
    .user-card {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      padding: var(--kb-space-3) var(--kb-space-4);
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-card);
      backdrop-filter: blur(8px);
    }
    .user-avatar {
      width: 34px;
      height: 34px;
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-primary);
      color: var(--kb-color-primary-foreground);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 0.85rem;
      flex-shrink: 0;
      box-shadow: var(--kb-shadow-xs);
    }
    .user-meta { display: grid; min-width: 0; }
    .user-name { font-weight: 600; font-size: 0.85rem; color: var(--kb-color-foreground-contrast); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .user-role { font-size: 0.68rem; color: var(--kb-color-muted); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; }

    /* Navigation */
    .nav-links { display: flex; flex-direction: column; gap: var(--kb-space-2); }
    .nav-link {
      position: relative;
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      padding: var(--kb-space-3) var(--kb-space-4);
      border-radius: var(--kb-radius-card);
      color: var(--kb-color-muted-foreground);
      text-decoration: none;
      font-weight: 500;
      font-size: 0.88rem;
      transition: all 0.15s ease;
    }
    .nav-link__icon { font-size: 1rem; display: inline-flex; align-items: center; justify-content: center; width: 20px; }
    .nav-link:hover { background: var(--kb-color-surface-2); color: var(--kb-color-foreground); }
    .nav-link.active-link {
      background: var(--kb-color-primary);
      color: var(--kb-color-primary-foreground);
      font-weight: 700;
      border: 1px solid var(--kb-color-primary);
    }
    .nav-link.active-link::before {
      content: "";
      position: absolute;
      left: 0;
      top: 0.35rem;
      bottom: 0.35rem;
      width: 4px;
      border-radius: 0 4px 4px 0;
      background: var(--kb-color-primary-foreground);
      box-shadow: 0 0 8px var(--kb-color-primary);
    }

    /* Logout */
    .logout-btn {
      margin-top: auto;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--kb-space-2);
      padding: var(--kb-space-3) var(--kb-space-4);
      background: rgba(255, 255, 255, 0.04);
      color: var(--kb-color-muted-foreground);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-card);
      cursor: pointer;
      font-weight: 600;
      font-size: 0.85rem;
      transition: all 0.15s ease;
    }
    .logout-btn:hover { background: var(--kb-color-surface-2); color: var(--kb-color-foreground); border-color: var(--kb-color-border-strong); }

    /* Content */
    .content-shell { min-width: 0; width: 100%; flex: 1; }
    .content-shell:focus { outline: none; }

    .desktop-topbar {
      position: sticky; top: 0; z-index: var(--kb-z-topbar);
      height: var(--kb-topbar-height); padding: var(--kb-space-4) 1.5rem;
      display: flex; align-items: center; justify-content: space-between; gap: 1rem;
      background: var(--kb-color-surface); border-bottom: 1px solid var(--kb-color-border);
      backdrop-filter: blur(12px);
    }
    .business-context { display: flex; align-items: center; gap: var(--kb-space-3); min-width: 0; }
    .context-mark, .topbar-avatar {
      display: grid; place-items: center; flex: 0 0 auto; width: 34px; height: 34px;
      border-radius: var(--kb-radius-lg); background: var(--kb-color-primary); color: var(--kb-color-primary-foreground);
      font-weight: 700;
    }
    .topbar-avatar { border-radius: var(--kb-radius-full); background: var(--kb-color-surface-2); color: var(--kb-color-muted); }
    .context-copy { display: grid; min-width: 0; line-height: 1.2; }
    .context-copy strong { overflow: hidden; color: var(--kb-color-foreground); font-size: 0.86rem; text-overflow: ellipsis; white-space: nowrap; }
    .context-copy span { color: var(--kb-color-muted); font-size: 0.7rem; }
    .topbar-actions { display: flex; align-items: center; gap: var(--kb-space-3); }
    .quick-search, .restaurant-chip {
      min-height: var(--kb-space-5); display: inline-flex; align-items: center; gap: var(--kb-space-2);
      padding: var(--kb-space-2) var(--kb-space-3); color: var(--kb-color-muted); background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-lg); font-size: 0.78rem;
    }
    .quick-search { width: 220px; cursor: pointer; text-align: left; }
    .quick-search:hover { border-color: var(--kb-color-primary); color: var(--kb-color-foreground); }
    .quick-search:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: 2px; }
    .quick-search kbd { margin-left: auto; padding: var(--kb-space-1) var(--kb-space-2); border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-sm); background: var(--kb-color-surface-2); font-size: 0.65rem; }
    .restaurant-chip { min-height: var(--kb-space-4); background: var(--kb-color-surface-2); }

    /* ── Topbar (mobile only) ── */
    .topbar { display: none; }
    .topbar__brand { display: flex; align-items: center; gap: var(--kb-space-3); }

    .hamburger {
      display: inline-flex;
      flex-direction: column;
      justify-content: center;
      gap: var(--kb-space-2);
      width: 40px;
      height: 40px;
      padding: 0 var(--kb-space-3);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-card);
      background: var(--kb-color-surface);
      cursor: pointer;
    }
    .hamburger__bar { display: block; height: 2px; border-radius: var(--kb-radius-sm); background: var(--kb-color-foreground); }
    .sidebar-backdrop { display: none; }

    /* ── Responsive ── */
    @media (max-width: 1024px) {
      .layout-shell { grid-template-columns: 1fr; }
      .desktop-topbar { display: none; }
      .topbar {
        display: flex;
        align-items: center;
        gap: var(--kb-space-4);
        position: sticky;
        top: 0;
        z-index: 30;
        padding: var(--kb-space-3) var(--kb-space-4);
        background: var(--kb-color-surface);
        border-bottom: 1px solid var(--kb-color-border);
      }
      .topbar__title { font-weight: 700; color: var(--kb-color-foreground); letter-spacing: -0.01em; }
      .sidebar {
        position: fixed;
        top: 0;
        left: 0;
        z-index: 40;
        width: 280px;
        max-width: 85vw;
        height: 100vh;
        transform: translateX(-100%);
        transition: transform 0.25s ease;
        box-shadow: var(--kb-shadow-lg);
      }
      .sidebar--open { transform: translateX(0); }
      .sidebar-backdrop {
        display: block;
        position: fixed;
        inset: 0;
        z-index: 35;
        background: rgba(26, 26, 26, 0.5);
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
  readonly contextTitle = computed(() => this.session()?.role === 'KBOOK_ADMIN' ? 'KhanaBook Platform' : 'Restaurant operations');
  readonly contextSubtitle = computed(() => this.session()?.role === 'KBOOK_ADMIN' ? 'Administration workspace' : 'Live business workspace');
  readonly contextInitial = computed(() => this.session()?.role === 'KBOOK_ADMIN' ? 'K' : 'R');
  readonly links = computed<NavLink[]>(() => {
    const role = this.session()?.role;
    if (role === 'KBOOK_ADMIN') {
      return [
        { label: 'Platform Dashboard', path: '/admin/dashboard', icon: '◉' },
        { label: 'Businesses', path: '/admin/businesses', icon: '🏢' },
        { label: 'Feature Flags', path: '/admin/feature-flags', icon: '⚑' }
      ];
    }
    if (role === 'SHOP_ADMIN') {
      return [{ label: 'Devices', path: '/business/terminals', icon: '▣' }];
    }
    return [
      { label: 'Business Dashboard', path: '/business/dashboard', icon: '◉' },
      { label: 'Active Orders', path: '/business/active-orders', icon: '🔴' },
      { label: 'Daily Closing', path: '/business/daily-closing', icon: '💰' },
      { label: 'Reports', path: '/business/reports', icon: '◔' },
      { label: 'Orders', path: '/business/orders', icon: '▤' },
      { label: 'Marketplace Orders', path: '/business/marketplace-orders', icon: '▧' },
      { label: 'Menu', path: '/business/menu', icon: '◈' },
      { label: 'Staff', path: '/business/staff', icon: '◍' },
      { label: 'Settings', path: '/business/settings', icon: '⚙' },
      { label: 'Devices', path: '/business/terminals', icon: '▣' }
    ];
  });

  readonly bottomActionItems = signal<BottomActionBarItem[]>([
    { label: 'Orders', icon: 'shop', route: '/business/orders', badge: '24' },
    { label: 'Reports', icon: 'chart', route: '/business/reports' },
    { label: 'Settings', icon: 'settings', route: '/business/settings' },
    { label: 'KDS', icon: 'kitchen', route: '/business/active-orders' }
  ]);

  readonly selectedBottomTab = signal(0);

  readonly isMobileView = computed(() => window.innerWidth < 1024);

  toggleMenu(): void {
    if (this.menuOpen()) { this.closeMenu(); return; }
    this.menuOpen.set(true);
    setTimeout(() => this.sidebar?.nativeElement?.focus());
  }

  closeMenu(): void {
    this.menuOpen.set(false);
    setTimeout(() => this.menuButton?.nativeElement?.focus());
  }

  logout(): void {
    this.authService.logout();
  }

  openOrders(): void {
    void this.router.navigate(['/business/orders']);
  }

  onBottomTabChange(index: number): void {
    this.selectedBottomTab.set(index);
    const route = this.bottomActionItems()[index]?.route;
    if (route) {
      void this.router.navigate([route]);
    }
  }

  @HostListener('document:keydown.control.k', ['$event'])
  openOrderSearch(event: KeyboardEvent): void {
    if (this.session()?.role !== 'OWNER') return;
    event.preventDefault();
    this.openOrders();
  }

  /* Close menu on Escape key */
  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: KeyboardEvent): void {
    if (this.menuOpen()) this.closeMenu();
  }
}
