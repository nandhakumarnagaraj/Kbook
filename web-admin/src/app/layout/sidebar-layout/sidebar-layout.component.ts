import { CommonModule } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessProfile } from '../../core/models/api.models';

type NavLink = { icon: string; label: string; path: string };

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="layout-shell">
      <button
        class="hamburger"
        (click)="drawerOpen.set(true)"
        aria-label="Open navigation menu"
        *ngIf="!drawerOpen()"
      >
        <span class="hamburger-line"></span>
        <span class="hamburger-line"></span>
        <span class="hamburger-line"></span>
      </button>

      <div
        class="drawer-backdrop"
        [class.visible]="drawerOpen()"
        (click)="drawerOpen.set(false)"
      ></div>

      <aside class="sidebar" [class.drawer-open]="drawerOpen()">
        <div class="sidebar-header">
          <div class="brand">
            <img [src]="brandLogo()" [alt]="brandName()" class="brand-logo" (error)="onLogoError()" />
            <div class="brand-copy">
              <strong>{{ brandName() }}</strong>
              <span>{{ session()?.role === 'KBOOK_ADMIN' ? 'Platform Console' : 'Business Console' }}</span>
            </div>
          </div>
          <button
            class="drawer-close"
            (click)="drawerOpen.set(false)"
            aria-label="Close navigation menu"
          >
            &times;
          </button>
        </div>

        <div class="user-card">
          <div class="user-avatar">{{ userInitial() }}</div>
          <div class="user-copy">
            <strong>{{ session()?.userName || 'Operator' }}</strong>
            <span>{{ session()?.loginId || 'Signed in' }}</span>
          </div>
        </div>

        <nav class="nav-links">
          <a
            *ngFor="let link of links()"
            [routerLink]="link.path"
            routerLinkActive="nav-active"
            class="nav-link"
            (click)="closeDrawerOnMobile()"
          >
            <span class="nav-icon">{{ link.icon }}</span>
            <span class="nav-text">{{ link.label }}</span>
          </a>
        </nav>

        <button class="logout-btn" (click)="logout()">
          <span class="nav-icon">🚪</span>
          <span>Logout</span>
        </button>
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
      grid-template-columns: 240px 1fr;
      background: var(--bg);
    }

    .hamburger {
      display: none;
      position: fixed;
      top: 0.75rem;
      left: 0.75rem;
      z-index: 1010;
      width: 42px;
      height: 42px;
      border-radius: 12px;
      border: 1px solid var(--line);
      background: var(--panel);
      box-shadow: var(--shadow-sm);
      cursor: pointer;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
      padding: 0;
      transition: background 0.18s ease;
    }

    .hamburger:hover {
      background: rgba(181,106,45,0.08);
    }

    .hamburger-line {
      display: block;
      width: 18px;
      height: 2px;
      border-radius: 2px;
      background: var(--ink);
    }

    .drawer-backdrop {
      display: none;
      position: fixed;
      inset: 0;
      z-index: 1000;
      background: rgba(36,23,15,0.4);
      backdrop-filter: blur(2px);
      opacity: 0;
      transition: opacity 0.25s ease;
      pointer-events: none;
    }

    .drawer-backdrop.visible {
      opacity: 1;
      pointer-events: auto;
    }

    .sidebar {
      position: sticky;
      top: 0;
      height: 100vh;
      height: 100dvh;
      padding: 1rem 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.8rem;
      background: linear-gradient(180deg, #fffdf8, #f7efe4);
      border-right: 1px solid var(--line);
      overflow-y: auto;
      box-shadow: 2px 0 12px rgba(36,23,15,0.04);
    }

    .sidebar-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 0.5rem;
    }

    .drawer-close {
      display: none;
      width: 32px;
      height: 32px;
      border-radius: 8px;
      border: 1px solid var(--line);
      background: transparent;
      font-size: 1.25rem;
      line-height: 1;
      cursor: pointer;
      color: var(--muted);
      flex-shrink: 0;
      transition: background 0.18s ease;
    }

    .drawer-close:hover {
      background: rgba(166,55,47,0.08);
      color: var(--danger);
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 0.7rem;
      padding: 0.4rem 0.35rem 0.75rem;
      border-bottom: 1px solid var(--line);
    }

    .brand-logo {
      height: 32px;
      max-width: 64px;
      object-fit: contain;
      border-radius: 6px;
      display: block;
      flex-shrink: 0;
    }

    .brand-copy {
      display: grid;
      gap: 0.12rem;
      min-width: 0;
    }

    .brand-copy strong {
      font-size: 1rem;
      line-height: 1.1;
      color: var(--brand-deep);
    }

    .brand-copy span {
      font-size: 0.7rem;
      color: var(--muted);
    }

    .nav-links {
      display: flex;
      flex-direction: column;
      gap: 0.22rem;
      flex: 1;
    }

    .user-card {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      padding: 0.65rem 0.7rem;
      border-radius: 14px;
      border: 1px solid var(--line);
      background: rgba(255,255,255,0.85);
      box-shadow: 0 1px 4px rgba(36,23,15,0.04);
    }

    .user-avatar {
      width: 32px;
      height: 32px;
      border-radius: 10px;
      display: grid;
      place-items: center;
      background: linear-gradient(135deg, rgba(181,106,45,0.18), rgba(181,106,45,0.08));
      color: var(--brand-deep);
      font-size: 0.88rem;
      font-weight: 800;
      flex-shrink: 0;
    }

    .user-copy {
      min-width: 0;
      display: grid;
      gap: 0.08rem;
    }

    .user-copy strong {
      font-size: 0.84rem;
      line-height: 1.15;
      color: var(--ink);
    }

    .user-copy span {
      font-size: 0.68rem;
      color: var(--muted);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .nav-link {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      padding: 0.65rem 0.75rem;
      border-radius: 12px;
      color: var(--ink);
      text-decoration: none;
      border: 1px solid transparent;
      font-weight: 600;
      font-size: 0.88rem;
      transition: background 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
    }

    .nav-link:hover {
      background: rgba(181,106,45,0.08);
      border-color: rgba(181,106,45,0.14);
    }

    .nav-link.nav-active {
      background: linear-gradient(135deg, rgba(181,106,45,0.14), rgba(181,106,45,0.06));
      border-color: rgba(181,106,45,0.22);
      color: var(--brand-deep);
      box-shadow: inset 3px 0 0 var(--brand);
      font-weight: 700;
    }

    .nav-icon {
      width: 30px;
      height: 30px;
      border-radius: 9px;
      display: grid;
      place-items: center;
      background: linear-gradient(135deg, rgba(181,106,45,0.12), rgba(181,106,45,0.06));
      flex-shrink: 0;
      font-size: 0.95rem;
    }

    .nav-text {
      line-height: 1.2;
      font-size: 0.84rem;
    }

    .logout-btn {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      padding: 0.62rem 0.68rem;
      border-radius: 12px;
      border: 1px solid rgba(166,55,47,0.2);
      background: rgba(166,55,47,0.08);
      color: var(--danger);
      font-weight: 700;
      cursor: pointer;
      transition: all 0.18s ease;
    }

    .logout-btn:hover {
      background: var(--danger);
      color: #fff;
      border-color: var(--danger);
    }

    .content-shell {
      min-width: 0;
      background: var(--bg);
    }

    @media (max-width: 960px) {
      .layout-shell {
        grid-template-columns: 1fr;
      }

      .hamburger {
        display: flex;
      }

      .drawer-backdrop {
        display: block;
      }

      .sidebar {
        position: fixed;
        top: 0;
        left: 0;
        bottom: 0;
        width: 280px;
        z-index: 1005;
        transform: translateX(-100%);
        transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
        box-shadow: none;
        border-right: 1px solid var(--line);
      }

      .sidebar.drawer-open {
        transform: translateX(0);
        box-shadow: 8px 0 24px rgba(36,23,15,0.12);
      }

      .sidebar-header {
        align-items: center;
      }

      .drawer-close {
        display: grid;
        place-items: center;
      }

      .nav-links {
        flex-direction: column;
        gap: 0.22rem;
      }

      .nav-link {
        white-space: normal;
      }

      .content-shell {
        padding: 0;
      }
    }
  `]
})
export class SidebarLayoutComponent {
  private readonly authService = inject(AuthService);
  private readonly businessApi = inject(BusinessApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly drawerOpen = signal(false);
  readonly session = this.authService.session;
  readonly shopProfile = signal<BusinessProfile | null>(null);
  readonly logoFailed = signal(false);

  constructor() {
    if (this.session()?.role === 'OWNER') {
      this.businessApi.getProfile().pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => of(null))
      ).subscribe(profile => {
        if (profile) this.shopProfile.set(profile);
      });
    }
  }

  readonly brandLogo = computed(() => {
    const profile = this.shopProfile();
    if (profile?.logoUrl && !this.logoFailed()) return profile.logoUrl;
    return '/khanabook_logo.png';
  });

  readonly brandName = computed(() => {
    const profile = this.shopProfile();
    if (profile?.shopName) return profile.shopName;
    return 'KhanaBook';
  });

  onLogoError(): void {
    this.logoFailed.set(true);
  }

  readonly links = computed<NavLink[]>(() => {
    const role = this.session()?.role;
    if (role === 'KBOOK_ADMIN') {
      return [
        { icon: '📊', label: 'Platform Dashboard', path: '/admin/dashboard' },
        { icon: '🏪', label: 'Businesses', path: '/admin/businesses' },
        { icon: '👥', label: 'Sub-Merchants', path: '/admin/sub-merchants' },
        { icon: '💳', label: 'Payment Dashboard', path: '/admin/payment-dashboard' },
        { icon: '🧾', label: 'Transactions', path: '/admin/transactions' },
        { icon: '💸', label: 'Settlements', path: '/admin/settlements' },
        { icon: '📈', label: 'Commission', path: '/admin/commission' }
      ];
    }

    return [
      { icon: '📊', label: 'Dashboard', path: '/business/dashboard' },
      { icon: '📋', label: 'Orders', path: '/business/orders' },
      { icon: '🔗', label: 'Marketplace Setup', path: '/business/marketplace-setup' },
      { icon: '🍽️', label: 'Menu', path: '/business/menu' },
      { icon: '👤', label: 'Staff', path: '/business/staff' },
      { icon: '⚙️', label: 'Settings', path: '/business/settings' }
    ];
  });

  userInitial(): string {
    const name = this.session()?.userName?.trim();
    return name ? name.charAt(0).toUpperCase() : 'K';
  }

  closeDrawerOnMobile(): void {
    if (window.innerWidth <= 960) {
      this.drawerOpen.set(false);
    }
  }

  logout(): void {
    this.drawerOpen.set(false);
    this.authService.logout();
  }
}
