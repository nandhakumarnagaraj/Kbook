import { CommonModule } from '@angular/common';
import { Component, computed, ElementRef, ViewChild, HostListener, inject, signal, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BottomActionBarComponent } from '../bottom-action-bar/bottom-action-bar.component';
import { AuthService } from '../../core/auth/auth.service';
import { BottomActionBarModule } from '../bottom-action-bar/bottom-action-bar.module';
import { environment } from '../../../environments/environment';

const API = environment.apiBaseUrl;

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
              <h1>{{ contextTitle() }}</h1>
              <span class="tenant-chip" *ngIf="session()?.restaurantId as restaurantId">Restaurant #{{ restaurantId }}</span>
            </div>
          </div>

          <button *ngIf="session()?.role === 'OWNER'" type="button" class="sidebar-search" (click)="openOrders()" aria-label="Search orders">
            <span class="sidebar-search__icon" aria-hidden="true">⌕</span>
            <span class="sidebar-search__label">Search orders</span>
            <kbd class="sidebar-search__kbd">Ctrl K</kbd>
          </button>
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

        <div class="sidebar-footer">
          <div class="user-card">
            <div class="user-avatar" aria-hidden="true">
              {{ (session()?.userName || 'O').charAt(0).toUpperCase() }}
            </div>
            <div class="user-meta">
              <span class="user-name">{{ session()?.userName || 'Operator' }}</span>
              <span class="user-role">{{ session()?.role }}</span>
            </div>
          </div>

          <button class="logout-btn" (click)="logout()" type="button" aria-label="Sign out">
            <span aria-hidden="true">↩</span>
            <span>Sign out</span>
          </button>
        </div>
      </aside>

      <div class="workspace">
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
      padding: var(--kb-space-4);
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-3);
      position: sticky;
      top: 0;
      height: 100vh;
      overflow-y: auto;
      background: var(--kb-color-foreground);
      border-right: 1px solid var(--kb-color-border);
      color: var(--kb-color-foreground-contrast);
    }

    .brand-block { display: grid; gap: var(--kb-space-3); padding: 0 var(--kb-space-1); }
    .brand-row { display: flex; align-items: center; gap: var(--kb-space-3); }
    .brand-copy { display: grid; min-width: 0; }
    .brand-copy h1 { margin: 0; font-size: clamp(0.95rem, 1.5vw, 1.15rem); font-weight: 700; letter-spacing: -0.02em; color: var(--kb-color-foreground-contrast); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .tenant-chip { display: inline-block; font-size: 0.7rem; font-weight: 600; color: var(--kb-color-primary); letter-spacing: 0.02em; }

    .brand-logo {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: var(--kb-radius-md);
      background: var(--kb-gradient-hero);
      box-shadow: var(--kb-shadow-sm);
      flex-shrink: 0;
    }
    .brand-logo--sm { width: 32px; height: 32px; border-radius: var(--kb-radius-sm); }
    .brand-logo__mark { color: var(--kb-color-primary-foreground); font-weight: 800; font-size: 1.2rem; line-height: 1; letter-spacing: -0.02em; }
    .brand-logo--sm .brand-logo__mark { font-size: 0.95rem; }

    .eyebrow { text-transform: uppercase; letter-spacing: 0.1em; color: var(--kb-color-muted); font-size: 0.65rem; font-weight: 700; }

    /* Integrated Search */
    .sidebar-search {
      width: 100%;
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
      padding: var(--kb-space-2) var(--kb-space-3);
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: var(--kb-radius-md);
      color: var(--kb-color-muted-foreground);
      font-size: 0.8rem;
      cursor: pointer;
      text-align: left;
      transition: background-color 150ms var(--ease-out, ease-out), border-color 150ms var(--ease-out, ease-out), color 150ms var(--ease-out, ease-out), transform 120ms var(--ease-out, ease-out);
    }
    .sidebar-search:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: var(--kb-color-primary);
      color: var(--kb-color-foreground-contrast);
    }
    .sidebar-search:focus-visible { outline: 2px solid var(--kb-color-primary); outline-offset: 2px; }
    .sidebar-search:active { transform: scale(0.98); }
    .sidebar-search__icon { font-size: 1rem; color: var(--kb-color-muted-foreground); }
    .sidebar-search__label { flex: 1; }
    .sidebar-search__kbd {
      padding: 2px 6px;
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: var(--kb-radius-sm);
      font-size: 0.65rem;
      font-family: inherit;
      color: var(--kb-color-foreground-contrast);
    }

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
      transition: background-color 150ms var(--ease-out, ease-out), color 150ms var(--ease-out, ease-out), transform 120ms var(--ease-out, ease-out);
    }
    .nav-link__icon { font-size: 1rem; display: inline-flex; align-items: center; justify-content: center; width: 20px; }
    .nav-link:hover { background: var(--kb-color-surface-2); color: var(--kb-color-foreground); }
    .nav-link:active { transform: scale(0.98); }
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

    /* Sidebar Footer */
    .sidebar-footer {
      margin-top: auto;
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-2);
      padding-top: var(--kb-space-3);
      border-top: 1px solid rgba(255, 255, 255, 0.08);
    }

    /* User card */
    .user-card {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      padding: var(--kb-space-2) var(--kb-space-3);
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: var(--kb-radius-card);
    }
    .user-avatar {
      width: 32px;
      height: 32px;
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-primary);
      color: var(--kb-color-primary-foreground);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 0.82rem;
      flex-shrink: 0;
      box-shadow: var(--kb-shadow-xs);
    }
    .user-meta { display: grid; min-width: 0; }
    .user-name { font-weight: 600; font-size: 0.82rem; color: var(--kb-color-foreground-contrast); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .user-role { font-size: 0.65rem; color: var(--kb-color-muted); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; }

    /* Logout */
    .logout-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--kb-space-2);
      padding: var(--kb-space-2) var(--kb-space-3);
      background: transparent;
      color: var(--kb-color-muted-foreground);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: var(--kb-radius-card);
      cursor: pointer;
      font-weight: 600;
      font-size: 0.82rem;
      transition: background-color 150ms var(--ease-out, ease-out), color 150ms var(--ease-out, ease-out), border-color 150ms var(--ease-out, ease-out), transform 120ms var(--ease-out, ease-out);
    }
    .logout-btn:hover { background: rgba(255, 255, 255, 0.08); color: var(--kb-color-foreground-contrast); border-color: rgba(255, 255, 255, 0.2); }
    .logout-btn:active { transform: scale(0.97); }

    /* Content */
    .content-shell { min-width: 0; width: 100%; flex: 1; }
    .content-shell:focus { outline: none; }

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
export class SidebarLayoutComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  @ViewChild('menuButton') private menuButton?: ElementRef<HTMLButtonElement>;
  @ViewChild('sidebar') private sidebar?: ElementRef<HTMLElement>;

  readonly menuOpen = signal(false);
  readonly orderPaymentFlowMode = signal<string>('pay_before_food');

  readonly session = this.authService.session;
  readonly contextTitle = computed(() => this.session()?.role === 'KBOOK_ADMIN' ? 'KhanaBook Platform' : 'Restaurant operations');
  readonly links = computed<NavLink[]>(() => {
    const role = this.session()?.role;
    if (role === 'KBOOK_ADMIN') {
      return [
        { label: 'Platform Dashboard', path: '/admin/dashboard', icon: '◉' },
        { label: 'Businesses', path: '/admin/businesses', icon: '🏢' },
        { label: 'Feature Flags', path: '/admin/feature-flags', icon: '⚑' }
      ];
    }
    if (role !== 'OWNER') {
      // SHOP_STAFF has no business web access (server /business/** is OWNER-only).
      return [];
    }
    const items: NavLink[] = [
      { label: 'Business Dashboard', path: '/business/dashboard', icon: '◉' },
    ];
    if (this.orderPaymentFlowMode() === 'pay_after_food') {
      items.push({ label: 'Active Orders', path: '/business/active-orders', icon: '🔴' });
    }
    items.push(
      { label: 'Daily Closing', path: '/business/daily-closing', icon: '💰' },
      { label: 'Reports', path: '/business/reports', icon: '◔' },
      { label: 'Orders', path: '/business/orders', icon: '▤' },
      { label: 'Menu', path: '/business/menu', icon: '◈' },
      { label: 'Inventory', path: '/business/inventory', icon: '📦' },
      { label: 'Staff', path: '/business/staff', icon: '◍' },
      { label: 'Settings', path: '/business/settings', icon: '⚙' },
      { label: 'Devices', path: '/business/terminals', icon: '▣' }
    );
    return items;
  });

  readonly bottomActionItems = computed<BottomActionBarItem[]>(() => {
    const items: BottomActionBarItem[] = [
      { label: 'Orders', icon: 'shop', route: '/business/orders', badge: '24' },
      { label: 'Reports', icon: 'chart', route: '/business/reports' },
      { label: 'Settings', icon: 'settings', route: '/business/settings' }
    ];
    if (this.orderPaymentFlowMode() === 'pay_after_food') {
      items.push({ label: 'KDS', icon: 'kitchen', route: '/business/active-orders' });
    }
    return items;
  });

  readonly selectedBottomTab = signal(0);

  readonly isMobileView = computed(() => window.innerWidth < 1024);

  ngOnInit(): void {
    const role = this.session()?.role;
    if (role === 'OWNER') {
      this.http.get<any>(`${API}/business/profile`).subscribe({
        next: (p) => {
          if (p?.orderPaymentFlowMode) {
            this.orderPaymentFlowMode.set(p.orderPaymentFlowMode);
          }
        },
        error: () => {}
      });
    }
  }

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
