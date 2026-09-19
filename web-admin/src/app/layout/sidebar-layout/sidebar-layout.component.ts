import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, ElementRef, ViewChild, HostListener, inject, signal, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BottomActionBarComponent } from '../bottom-action-bar/bottom-action-bar.component';
import { AuthService } from '../../core/auth/auth.service';
import { BottomActionBarModule } from '../bottom-action-bar/bottom-action-bar.module';
import { environment } from '../../../environments/environment';

const API = environment.apiBaseUrl;

type NavLink = { label: string; path: string; icon: string; iconKey: string; badge?: string };

type BottomActionBarItem = { label: string; icon: string; route: string; badge?: string };

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
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
          <div class="brand-logo-circle brand-logo-circle--sm" aria-hidden="true">
            <img src="/khanabook_logo.png" alt="KhanaBook" class="brand-logo-img" />
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
            <div class="brand-logo-circle" aria-hidden="true">
              <img src="/khanabook_logo.png" alt="KhanaBook" class="brand-logo-img" />
            </div>
            <div class="brand-copy">
              <span class="brand-title">KhanaBook</span>
            </div>
          </div>
        </div>

        <nav class="nav-links" aria-label="Main">
          <a
            *ngFor="let link of links(); trackBy: trackByNavLink"
            [routerLink]="link.path"
            routerLinkActive="active-link"
            class="nav-link"
            (click)="closeMenu()">
            <span class="nav-link__icon" aria-hidden="true" [ngSwitch]="link.iconKey">
              <!-- Dashboard -->
              <svg *ngSwitchCase="'dashboard'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/>
              </svg>
              <!-- Orders -->
              <svg *ngSwitchCase="'orders'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
              <!-- Menu -->
              <svg *ngSwitchCase="'menu'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 2v6a3 3 0 0 1-3 3 3 3 0 0 1-3-3V2"/><path d="M15 2v19"/><path d="M5 2c1.5 2 1.5 5 0 7v12"/>
              </svg>
              <!-- Staff -->
              <svg *ngSwitchCase="'staff'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
              <!-- Inventory -->
              <svg *ngSwitchCase="'inventory'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                <line x1="12" y1="22.08" x2="12" y2="12"/>
              </svg>
              <!-- Terminals -->
              <svg *ngSwitchCase="'terminals'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="4" y="2" width="16" height="20" rx="2" ry="2"/>
                <line x1="12" y1="18" x2="12.01" y2="18"/>
              </svg>
              <!-- Payments -->
              <svg *ngSwitchCase="'payments'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="10" x2="23" y2="10"/>
              </svg>
              <!-- Reports -->
              <svg *ngSwitchCase="'reports'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>
              </svg>
              <!-- Settings -->
              <svg *ngSwitchCase="'settings'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
              </svg>
              <span *ngSwitchDefault>{{ link.icon }}</span>
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
      grid-template-columns: 240px 1fr;
      align-items: start;
    }
    .workspace { min-width: 0; min-height: 100vh; display: flex; flex-direction: column; background: var(--pos-bg-body, #F6F8FD); }
    .skip-link {
      position: fixed; left: 1rem; top: 0; z-index: 100;
      padding: 0.65rem 1rem; color: var(--kb-color-foreground); background: var(--kb-color-primary);
      border-radius: var(--kb-radius-sm); transform: translateY(-110%);
    }
    .skip-link:focus { transform: translateY(0); }

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
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: #FFFFFF;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
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
    .nav-link:active { transform: scale(0.98); }
    .nav-link.active-link {
      background: var(--pos-purple, #5D45FD);
      color: #FFFFFF;
      font-weight: 600;
      border: none;
      box-shadow: 0 4px 14px rgba(93, 69, 253, 0.35);
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
      background: rgba(245, 158, 11, 0.15);
      color: #f59e0b;
      border: 1px solid rgba(245, 158, 11, 0.3);
    }
    .nav-link.active-link .nav-link__badge {
      background: rgba(255, 255, 255, 0.25);
      color: #FFFFFF;
      border-color: rgba(255, 255, 255, 0.4);
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
        { label: 'Platform Dashboard', path: '/admin/dashboard', icon: '◉', iconKey: 'dashboard' },
        { label: 'Businesses', path: '/admin/businesses', icon: '🏢', iconKey: 'tables' },
        { label: 'Feature Flags', path: '/admin/feature-flags', icon: '⚑', iconKey: 'settings' }
      ];
    }
    if (role !== 'OWNER') {
      // SHOP_STAFF has no business web access (server /business/** is OWNER-only).
      return [];
    }
    const items: NavLink[] = [
      { label: 'Dashboard', path: '/business/dashboard', icon: '◉', iconKey: 'dashboard' },
      { label: 'Orders', path: '/business/orders', icon: '▤', iconKey: 'orders' },
      { label: 'Menu', path: '/business/menu', icon: '◈', iconKey: 'menu' },
      { label: 'Staff', path: '/business/staff', icon: '👥', iconKey: 'staff' },
      { label: 'Inventory', path: '/business/inventory', icon: '📦', iconKey: 'inventory', badge: 'Soon' },
      { label: 'Terminals', path: '/business/terminals', icon: '📱', iconKey: 'terminals' },
      { label: 'Payments', path: '/business/daily-closing', icon: '💳', iconKey: 'payments' },
      { label: 'Reports', path: '/business/reports', icon: '◔', iconKey: 'reports' },
      { label: 'Settings', path: '/business/settings', icon: '⚙', iconKey: 'settings' }
    ];
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
      // API audit 2026-09-16: GET /business/profile does not exist on the server
      // (404). The profile (incl. orderPaymentFlowMode) is served by the
      // restaurant-profile sync pull endpoint — the same one the settings page
      // uses. Response is an array; take the first profile.
      this.http
        .get<any[]>(`${API}/sync/restaurantprofile/pull?lastSyncTimestamp=0&deviceId=web-admin&ignoreDeviceId=true`)
        .subscribe({
          next: (profiles) => {
            const mode = profiles?.[0]?.orderPaymentFlowMode;
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

  trackByNavLink = (_: number, link: NavLink) => link.path;
}
