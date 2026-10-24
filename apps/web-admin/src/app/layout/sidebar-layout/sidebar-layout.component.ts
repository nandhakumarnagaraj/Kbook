import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, ElementRef, ViewChild, HostListener, inject, signal, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BottomActionBarComponent } from '../bottom-action-bar/bottom-action-bar.component';
import { IconComponent } from '../../shared/icon.component';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessApiService } from '../../core/services/business-api.service';
import { environment } from '../../../environments/environment';

const API = environment.apiBaseUrl;

type NavLink = { label: string; path: string; icon: string; iconKey: string; badge?: string };

type BottomActionBarItem = { label: string; iconKey: string; route: string; badge?: string };

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, BottomActionBarComponent, IconComponent],
  template: `
    <div class="layout-shell">
      <a class="skip-link" href="#main-content">Skip to main content</a>
      <header class="topbar topbar--mobile" [attr.aria-hidden]="isMobileView() && menuOpen() ? 'true' : null" [attr.inert]="isMobileView() && menuOpen() ? '' : null">
        <button
          #menuButton
          type="button"
          class="hamburger"
          [class.hamburger--open]="menuOpen()"
          [attr.aria-label]="menuOpen() ? 'Close navigation menu' : 'Open navigation menu'"
          [attr.aria-expanded]="menuOpen()"
          aria-controls="sidebar-nav"
          (click)="toggleMenu()">
          <span class="hamburger__bar"></span>
          <span class="hamburger__bar"></span>
          <span class="hamburger__bar"></span>
        </button>
        <div class="topbar__brand">
          <div class="brand-logo-circle brand-logo-circle--sm" aria-hidden="true">
            <img src="/khanabook_logo.png" alt="KhanaBook" class="brand-logo-img" />
          </div>
          <div class="topbar__brand-copy">
            <span class="topbar__title">KhanaBook</span>
            <span class="topbar__context" aria-live="polite">{{ currentPageLabel() }}</span>
          </div>
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
        [attr.aria-hidden]="isMobileView() && !menuOpen() ? 'true' : null"
        [attr.inert]="isMobileView() && !menuOpen() ? '' : null"
        tabindex="-1"
      >
        <div class="brand-block">
          <div class="brand-row">
            <div class="brand-logo-circle" aria-hidden="true">
              <img src="/khanabook_logo.png" alt="KhanaBook" class="brand-logo-img" />
            </div>
            <div class="brand-copy">
              <span class="brand-title">KhanaBook</span>
            </div>
            <button type="button" class="sidebar-close-btn" aria-label="Close navigation menu" (click)="closeMenu()">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                <path d="m18 6-12 12M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        <nav class="nav-links" aria-label="Main">
          <a
            *ngFor="let link of links(); trackBy: trackByNavLink"
            [routerLink]="link.path"
            routerLinkActive="active-link"
            ariaCurrentWhenActive="page"
            class="nav-link"
            (click)="closeMenu(false)">
            <span class="nav-link__icon" aria-hidden="true">
              <kb-icon *ngIf="link.iconKey" [name]="link.iconKey" [size]="18"></kb-icon>
              <span *ngIf="!link.iconKey">{{ link.icon }}</span>
            </span>
            <span class="nav-link__label">{{ link.label }}</span>
            <span *ngIf="link.badge" class="nav-link__badge">{{ link.badge }}</span>
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
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            <span>Sign out</span>
          </button>
        </div>
      </aside>

      <div class="workspace" [attr.aria-hidden]="isMobileView() && menuOpen() ? 'true' : null" [attr.inert]="isMobileView() && menuOpen() ? '' : null">
        <main id="main-content" class="content-shell" tabindex="-1">
          <router-outlet />
        </main>
      </div>

      <!-- Mobile bottom action bar -->
      <kb-bottom-action-bar
        *ngIf="showBottomBar()"
        [items]="bottomActionItems()"
      />
    </div>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; background: var(--kb-color-bg-app); }

    .layout-shell {
      min-height: 100vh;
      display: grid;
      grid-template-columns: 240px 1fr;
      align-items: start;
    }
    .workspace { min-width: 0; min-height: 100vh; display: flex; flex-direction: column; background: var(--pos-bg-body, #F6F8FD); }
    .skip-link {
      position: fixed; left: 1rem; top: 0; z-index: 110;
      padding: 0.65rem 1rem; color: #ffffff; background: var(--kb-color-primary, #0066cc);
      font-weight: 600; text-decoration: none;
      border-radius: var(--kb-radius-sm); transform: translateY(-110%);
      transition: transform 0.15s ease;
    }
    .skip-link:focus { transform: translateY(0); outline: 2px solid #ffffff; outline-offset: 2px; }

    /* ── Sidebar (POS Command Center Navy) ── */
    .sidebar {
      padding: 1.5rem 1rem;
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-3);
      position: sticky;
      top: 0;
      height: 100vh;
      overflow-y: auto;
      background: var(--pos-sidebar-bg, #181B34);
      border-right: 1px solid rgba(255, 255, 255, 0.05);
      color: var(--pos-sidebar-text, #8E95A9);
    }

    .brand-block { padding: 0 0.5rem 0.5rem; }
    .brand-row { display: flex; align-items: center; gap: 0.75rem; }
    .brand-logo-circle {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: #FFFFFF;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      overflow: hidden;
      padding: 3px;
    }
    .brand-logo-circle--sm {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      padding: 2px;
    }
    .brand-logo-img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
    .brand-title {
      font-size: 1.25rem;
      font-weight: 700;
      color: #FFFFFF;
      letter-spacing: -0.02em;
    }
    .sidebar-close-btn { display: none; }

    /* Navigation */
    .nav-links { display: flex; flex-direction: column; gap: 6px; }
    .nav-link {
      position: relative;
      display: flex;
      align-items: center;
      gap: 0.85rem;
      padding: 0.75rem 1rem;
      border-radius: 12px;
      color: var(--pos-sidebar-text, #8E95A9);
      text-decoration: none;
      font-weight: 500;
      font-size: 0.92rem;
      transition: all 150ms var(--ease-out, ease-out);
    }
    .nav-link__icon { font-size: 1rem; display: inline-flex; align-items: center; justify-content: center; width: 22px; color: inherit; }
    .nav-link:hover { background: var(--pos-sidebar-hover, #22274A); color: #FFFFFF; }
    .nav-link:active { transform: scale(0.97); }
    .nav-link.active-link {
      background: var(--pos-purple, #0066CC);
      color: #FFFFFF;
      font-weight: 600;
      border: none;
    }
    .nav-link.active-link .nav-link__icon {
      color: #FFFFFF;
    }
    .nav-link__badge {
      margin-left: auto;
      padding: 0.15rem 0.45rem;
      font-size: 0.65rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.12);
      color: rgba(255, 255, 255, 0.72);
      border: 1px solid rgba(255, 255, 255, 0.22);
    }
    .nav-link.active-link .nav-link__badge {
      background: rgba(255, 255, 255, 0.25);
      color: #FFFFFF;
      border-color: rgba(255, 255, 255, 0.4);
    }

    /* Sidebar Footer (footer.design standard) */
    .sidebar-footer {
      margin-top: auto;
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
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

    /* Sign out stays visibly destructive so the action is clear at a glance. */
    .logout-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.65rem 1rem;
      background: var(--kb-color-danger);
      color: var(--kb-color-primary-foreground);
      border: 1px solid var(--kb-color-danger);
      border-radius: 12px;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.92rem;
    }
    .logout-btn:hover {
      background: var(--kb-color-danger);
      color: var(--kb-color-primary-foreground);
      border-color: var(--kb-color-danger);
    }
    .logout-btn:focus-visible {
      outline: 2px solid var(--kb-color-foreground-contrast);
      outline-offset: 2px;
    }

    /* Content */
    .content-shell { min-width: 0; width: 100%; flex: 1; }
    .content-shell:focus { outline: none; }

    /* ── Topbar (mobile only) ── */
    .topbar { display: none; }
    .topbar__brand { display: flex; align-items: center; gap: var(--kb-space-3); min-width: 0; }
    .topbar__brand-copy { display: grid; min-width: 0; gap: 0.1rem; }

    .hamburger {
      display: inline-flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      gap: 5px;
      width: 44px;
      height: 44px;
      padding: 0;
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-card);
      background: var(--kb-color-surface);
      cursor: pointer;
      flex: 0 0 44px;
    }
    .hamburger__bar {
      display: block;
      width: 22px;
      height: 2px;
      border-radius: var(--kb-radius-sm);
      background: var(--kb-color-foreground);
      transition: transform 160ms ease, opacity 120ms ease;
    }
    .hamburger--open .hamburger__bar:first-child { transform: translateY(7px) rotate(45deg); }
    .hamburger--open .hamburger__bar:nth-child(2) { opacity: 0; }
    .hamburger--open .hamburger__bar:last-child { transform: translateY(-7px) rotate(-45deg); }
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
        z-index: 101;
        padding: var(--kb-space-3) var(--kb-space-4);
        background: var(--kb-color-surface);
        border-bottom: 1px solid var(--kb-color-border);
      }
      .topbar__title { font-size: 0.9rem; line-height: 1.15; font-weight: 700; color: var(--kb-color-foreground); letter-spacing: -0.01em; }
      .topbar__context { overflow: hidden; color: var(--kb-color-muted-foreground); font-size: 0.72rem; line-height: 1.15; text-overflow: ellipsis; white-space: nowrap; }
      .sidebar {
        position: fixed;
        top: 0;
        left: 0;
        z-index: 100;
        width: 280px;
        max-width: 85vw;
        height: 100vh;
        transform: translateX(-100%);
        transition: transform 0.25s ease;
        box-shadow: var(--kb-shadow-lg);
      }
      .sidebar--open { transform: translateX(0); }
      .sidebar-close-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
        margin-left: auto;
        border: 1px solid rgba(255, 255, 255, 0.18);
        border-radius: 10px;
        background: transparent;
        color: var(--kb-color-foreground-contrast);
        cursor: pointer;
      }
      .sidebar-close-btn:focus-visible { outline: 2px solid var(--kb-color-foreground-contrast); outline-offset: 2px; }
      .sidebar-backdrop {
        display: block;
        position: fixed;
        inset: 0;
        z-index: 99;
        background: rgba(26, 26, 26, 0.5);
        backdrop-filter: blur(2px);
      }
      /* Clear space for the fixed mobile bottom action bar */
      .workspace { padding-bottom: 76px; }
    }
    @media (min-width: 1025px) {
      .sidebar-backdrop { display: none !important; }
    }
    @media (prefers-reduced-motion: reduce) {
      .hamburger__bar { transition: none; }
    }
  `]
})
export class SidebarLayoutComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly businessApi = inject(BusinessApiService);
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
        { label: 'Platform Dashboard', path: '/admin/dashboard', icon: '📊', iconKey: '' },
        { label: 'Businesses', path: '/admin/businesses', icon: '🏢', iconKey: '' },
        { label: 'Feature Flags', path: '/admin/feature-flags', icon: '🚩', iconKey: '' }
      ];
    }
    if (role !== 'OWNER') {
      // SHOP_STAFF has no business web access (server /business/** is OWNER-only).
      return [];
    }
    const items: NavLink[] = [
      { label: 'Dashboard', path: '/business/dashboard', icon: '📊', iconKey: '' },
      { label: 'Orders', path: '/business/orders', icon: '🧾', iconKey: '' },
      { label: 'Menu', path: '/business/menu', icon: '🍽️', iconKey: '' },
      { label: 'Staff', path: '/business/staff', icon: '👥', iconKey: '' },
      { label: 'Inventory', path: '/business/inventory', icon: '📦', iconKey: '' },
      { label: 'Terminals', path: '/business/terminals', icon: '🖥️', iconKey: '' },
      { label: 'Daily Closing', path: '/business/daily-closing', icon: '💰', iconKey: '' },
      { label: 'Reports', path: '/business/reports', icon: '📈', iconKey: '' },
      { label: 'Settings', path: '/business/settings', icon: '⚙️', iconKey: '' }
    ];
    return items;
  });

  readonly bottomActionItems = computed<BottomActionBarItem[]>(() => {
    return [
      { label: 'Dashboard', iconKey: 'dashboard', route: '/business/dashboard' },
      // Orders opens on the Live Floor tab, so the mobile bar no longer needs
      // a separate "Active" destination for the live board.
      { label: 'Orders', iconKey: 'kitchen', route: '/business/orders' },
      { label: 'Closing', iconKey: 'payments', route: '/business/daily-closing' },
      { label: 'Settings', iconKey: 'settings', route: '/business/settings' }
    ];
  });

  readonly isMobileView = signal(typeof window !== 'undefined' && window.innerWidth <= 1024);

  readonly showBottomBar = computed(() => this.session()?.role === 'OWNER' && this.isMobileView());

  currentPageLabel(): string {
    const path = this.router.url.split(/[?#]/, 1)[0];
    return this.links().find((link) => path === link.path || path.startsWith(`${link.path}/`))?.label
      ?? (this.session()?.role === 'KBOOK_ADMIN' ? 'Platform' : 'Workspace');
  }

  ngOnInit(): void {
    this.isMobileView.set(window.innerWidth <= 1024);
    const role = this.session()?.role;
    if (role === 'OWNER') {
      this.businessApi.getProfile().subscribe({
        next: (profile) => {
          const mode = profile?.orderPaymentFlowMode;
          this.orderPaymentFlowMode.set(mode === 'pay_after_food' ? 'pay_after_food' : 'pay_before_food');
        },
        error: () => {
          // Keep the default; KDS tab simply won't show for pay-after-food
          // until settings load succeeds elsewhere.
        }
      });
    }
  }

  toggleMenu(): void {
    if (this.menuOpen()) { this.closeMenu(); return; }
    this.menuOpen.set(true);
    setTimeout(() => this.sidebar?.nativeElement?.focus());
  }

  closeMenu(restoreFocus = true): void {
    this.menuOpen.set(false);
    setTimeout(() => {
      if (restoreFocus) {
        this.menuButton?.nativeElement?.focus();
      } else {
        document.getElementById('main-content')?.focus();
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }

  openOrders(): void {
    void this.router.navigate(['/business/orders']);
  }

  @HostListener('window:resize')
  onResize(): void {
    const isMobile = window.innerWidth <= 1024;
    this.isMobileView.set(isMobile);
    if (!isMobile && this.menuOpen()) this.menuOpen.set(false);
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

  @HostListener('document:keydown.tab', ['$event'])
  trapMenuFocus(event: KeyboardEvent): void {
    if (!this.isMobileView() || !this.menuOpen()) return;
    const focusable = this.sidebar?.nativeElement.querySelectorAll<HTMLElement>('a[href], button:not([disabled])');
    if (!focusable?.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || active === this.sidebar?.nativeElement)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (active === last || active === this.sidebar?.nativeElement)) {
      event.preventDefault();
      first.focus();
    }
  }

  trackByNavLink = (_: number, link: NavLink) => link.path;
}
