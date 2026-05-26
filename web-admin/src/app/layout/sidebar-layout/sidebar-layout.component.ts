import { CommonModule } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, of } from 'rxjs';
import { MatSidenavModule, MatSidenav } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { map, shareReplay } from 'rxjs/operators';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessProfile } from '../../core/models/api.models';
import { ThemeService } from '../../core/services/theme.service';

type NavLink = { icon: string; label: string; path: string };

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  imports: [
    CommonModule, 
    RouterOutlet, 
    RouterLink, 
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatTooltipModule,
    MatDividerModule
  ],
  template: `
    <mat-sidenav-container class="layout-container">
      <mat-sidenav #drawer class="sidebar"
          [mode]="(isHandset$ | async) ? 'over' : 'side'"
          [opened]="(isHandset$ | async) === false">
        
        <div class="sidebar-header">
          <img [src]="brandLogo()" [alt]="brandName()" class="brand-logo" (error)="onLogoError()" />
          <div class="brand-info">
            <div class="brand-name">{{ brandName() }}</div>
            <div class="brand-sub">{{ session()?.role === 'KBOOK_ADMIN' ? 'Platform Console' : 'Business Console' }}</div>
          </div>
        </div>

        <mat-divider></mat-divider>

        <mat-nav-list class="nav-links">
          <div class="nav-section-label">Main Menu</div>
          <a mat-list-item *ngFor="let link of links().slice(0, 3)" 
             [routerLink]="link.path" 
             routerLinkActive="active-link"
             (click)="closeOnMobile()">
            <mat-icon matListItemIcon>{{ link.icon }}</mat-icon>
            <span matListItemTitle>{{ link.label }}</span>
          </a>

          <div class="nav-section-label">Operations</div>
          <a mat-list-item *ngFor="let link of links().slice(3)" 
             [routerLink]="link.path" 
             routerLinkActive="active-link"
             (click)="closeOnMobile()">
            <mat-icon matListItemIcon>{{ link.icon }}</mat-icon>
            <span matListItemTitle>{{ link.label }}</span>
          </a>
        </mat-nav-list>

        <div class="sidebar-footer">
          <div class="version">v3.0</div>
        </div>
      </mat-sidenav>

      <mat-sidenav-content class="content-area">
        <mat-toolbar color="primary" class="header-toolbar">
          <button
            type="button"
            aria-label="Toggle sidenav"
            mat-icon-button
            (click)="drawer.toggle()"
            *ngIf="isHandset$ | async">
            <mat-icon aria-label="Side nav toggle icon">menu</mat-icon>
          </button>
          
          <span class="page-title">{{ activePageTitle() }}</span>
          <span class="spacer"></span>

          <div class="toolbar-actions">
            <button mat-icon-button (click)="themeService.toggleTheme()" matTooltip="Toggle Light/Dark Mode">
              <mat-icon>{{ themeService.isDarkMode() ? 'light_mode' : 'dark_mode' }}</mat-icon>
            </button>

            <button [matMenuTriggerFor]="profileMenu" class="profile-trigger">
              <div class="header-avatar">{{ userInitial() }}</div>
            </button>
          </div>
        </mat-toolbar>

        <mat-menu #profileMenu="matMenu" class="profile-dropdown">
          <div class="menu-user-info">
            <div class="menu-avatar">{{ userInitial() }}</div>
            <div class="menu-details">
              <div class="menu-name">{{ session()?.userName || 'Operator' }}</div>
              <div class="menu-email">{{ session()?.loginId }}</div>
            </div>
          </div>
          <mat-divider></mat-divider>
          <button mat-menu-item routerLink="/business/settings">
            <mat-icon>person</mat-icon>
            <span>My Profile</span>
          </button>
          <button mat-menu-item (click)="logout()">
            <mat-icon color="warn">logout</mat-icon>
            <span class="text-warn">Logout</span>
          </button>
        </mat-menu>

        <main class="page-content">
          <router-outlet></router-outlet>
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .layout-container {
      height: 100vh;
    }

    .sidebar {
      width: 280px;
      border-right: none;
      box-shadow: 2px 0 10px rgba(0,0,0,0.05);
      background: var(--bg-elevated);
    }

    .sidebar-header {
      padding: 24px;
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .brand-logo {
      width: 56px;
      height: 56px;
      border-radius: 10px;
      object-fit: contain;
      background: #fff;
      padding: 4px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.1);
      image-rendering: auto;
    }

    .brand-info {
      display: flex;
      flex-direction: column;
    }

    .brand-name {
      font-weight: 700;
      font-size: 1.1rem;
      color: var(--ink);
    }

    .brand-sub {
      font-size: 0.7rem;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--muted);
    }

    .profile-trigger {
      background: none;
      border: none;
      padding: 0;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      outline: none;
      flex-shrink: 0;
    }

    .header-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--brand);
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 0.8rem;
      cursor: pointer;
      transition: all 0.2s ease;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
      flex-shrink: 0;
    }


    .header-avatar:hover {
      transform: scale(1.05);
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    }


    ::ng-deep .profile-dropdown .mat-mdc-menu-content {
      padding: 0 !important;
    }

    .menu-user-info {
      padding: 20px;
      display: flex;
      align-items: center;
      gap: 16px;
      min-width: 240px;
    }

    .menu-avatar {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      background: var(--brand-soft);
      color: var(--brand);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 1.2rem;
    }

    .menu-details {
      display: flex;
      flex-direction: column;
    }

    .menu-name {
      font-weight: 700;
      font-size: 1rem;
      color: var(--ink);
    }

    .menu-email {
      font-size: 0.8rem;
      color: var(--muted);
    }

    .text-warn {
      color: #dc2626;
    }

    .nav-section-label {
      padding: 16px 24px 8px;
      font-size: 0.65rem;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 1.5px;
      color: var(--muted);
      opacity: 0.7;
    }

    .nav-links {
      padding-top: 0;
    }

    ::ng-deep .nav-links .mat-mdc-list-item {
      margin: 2px 0;
      border-radius: 0;
      height: 44px !important;
      transition: all 0.2s ease !important;
    }

    ::ng-deep .nav-links .mat-mdc-list-item-icon {
      margin-right: 12px !important;
      margin-left: 16px !important;
    }

    .active-link {
      background: var(--brand) !important;
      color: white !important;
    }

    .active-link mat-icon {
      color: white !important;
    }

    .sidebar-footer {
      position: absolute;
      bottom: 0;
      width: 100%;
      padding: 16px;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .version {
      text-align: center;
      font-size: 0.65rem;
      color: var(--muted);
      opacity: 0.7;
    }

    .content-area {
      background: var(--bg);
    }

    .header-toolbar {
      background: rgba(255, 255, 255, 0.8) !important;
      backdrop-filter: blur(12px);
      color: var(--ink) !important;
      border-bottom: 1px solid var(--line);
      position: sticky;
      top: 0;
      z-index: 1000;
      display: flex !important;
      align-items: center !important;
      justify-content: flex-start !important;
      height: 64px !important;
    }

    .toolbar-actions {
      display: flex !important;
      align-items: center !important;
      gap: 12px;
      height: 64px;
    }


    /* Dark Mode Override for Toolbar */
    :host-context(.dark-theme) .header-toolbar {
      background: rgba(11, 14, 19, 0.8) !important;
      border-bottom: 1px solid rgba(255,255,255,0.05);
    }

    .spacer {
      flex: 1 1 auto;
    }

    .page-title {
      margin-left: 16px;
      font-weight: 600;
      font-size: 1.1rem;
    }

    .page-content {
      padding: 0;
      min-height: calc(100vh - 64px);
    }

    @media (max-width: 599px) {
      .sidebar {
        width: 100%;
      }
      .page-title {
        font-size: 0.95rem;
        margin-left: 8px;
      }
    }
  `]
})
export class SidebarLayoutComponent {
  @ViewChild('drawer') drawer!: MatSidenav;
  
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly businessApi = inject(BusinessApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly breakpointObserver = inject(BreakpointObserver);
  public readonly themeService = inject(ThemeService);

  readonly isHandset$ = this.breakpointObserver.observe(Breakpoints.Handset)
    .pipe(
      map(result => result.matches),
      shareReplay()
    );

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
    return 'khanabook_logo.png';
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
        { icon: 'dashboard', label: 'Platform Dashboard', path: '/admin/dashboard' },
        { icon: 'business', label: 'Businesses', path: '/admin/businesses' },
        { icon: 'people', label: 'Sub-Merchants', path: '/admin/sub-merchants' },
        { icon: 'payments', label: 'Payment Dashboard', path: '/admin/payment-dashboard' },
        { icon: 'receipt_long', label: 'Transactions', path: '/admin/transactions' },
        { icon: 'account_balance_wallet', label: 'Settlements', path: '/admin/settlements' },
        { icon: 'percent', label: 'Commission', path: '/admin/commission' }
      ];
    }

    return [
      { icon: 'dashboard', label: 'Dashboard', path: '/business/dashboard' },
      { icon: 'shopping_bag', label: 'Orders', path: '/business/orders' },
      { icon: 'store', label: 'Marketplace Setup', path: '/business/marketplace-setup' },
      { icon: 'restaurant_menu', label: 'Menu', path: '/business/menu' },
      { icon: 'people', label: 'Staff', path: '/business/staff' },
      { icon: 'settings', label: 'Settings', path: '/business/settings' }
    ];
  });

  activePageTitle(): string {
    const currentPath = this.router.url;
    const activeLink = this.links().find(link => currentPath.startsWith(link.path));
    return activeLink ? activeLink.label : 'Dashboard';
  }

  userInitial(): string {
    const name = this.session()?.userName?.trim();
    return name ? name.charAt(0).toUpperCase() : 'K';
  }

  closeOnMobile(): void {
    this.breakpointObserver.observe(Breakpoints.Handset).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => {
      if (result.matches) {
        this.drawer.close();
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
