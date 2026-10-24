import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { authGuard, roleGuard } from './core/auth/role.guard';
import { unsavedChangesGuard } from './core/guards/unsaved-changes.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login-page.component').then(m => m.LoginPageComponent)
  },
  {
    path: 'limited-access',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/limited-access/limited-access-page.component').then(m => m.LimitedAccessPageComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/sidebar-layout/sidebar-layout.component').then(m => m.SidebarLayoutComponent),
    children: [
      {
        path: 'admin/dashboard',
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'] },
        loadComponent: () => import('./pages/platform-dashboard/platform-dashboard-page.component').then(m => m.PlatformDashboardPageComponent)
      },
      {
        path: 'admin/businesses',
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'] },
        loadComponent: () => import('./pages/businesses/businesses-page.component').then(m => m.BusinessesPageComponent)
      },
      {
        path: 'admin/feature-flags',
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'] },
        loadComponent: () => import('./pages/feature-flags/feature-flags-page.component').then(m => m.FeatureFlagsPageComponent)
      },
      {
        path: 'business/dashboard',
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./pages/business-dashboard/business-dashboard-page.component').then(m => m.BusinessDashboardPageComponent)
      },
      {
        path: 'business/reports',
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./pages/reports/reports-page.component').then(m => m.ReportsPageComponent)
      },
      {
        path: 'business/daily-closing',
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./pages/daily-closing/daily-closing-page.component').then(m => m.DailyClosingPageComponent)
      },
      {
        path: 'business/orders',
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./pages/orders/orders-page.component').then(m => m.OrdersPageComponent)
      },
      {
        // Active Orders is now the "Live Floor" tab of /business/orders.
        // Kept as a redirect so sidebar bookmarks, dashboard deep links and
        // notification links keep working.
        path: 'business/active-orders',
        redirectTo: 'business/orders',
        pathMatch: 'full'
      },
      {
        path: 'business/menu',
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./pages/menu/menu-page.component').then(m => m.MenuPageComponent)
      },
      {
        path: 'business/inventory',
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./pages/inventory/inventory-page.component').then(m => m.InventoryPageComponent)
      },
      {
        path: 'business/staff',
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./pages/staff/staff-page.component').then(m => m.StaffPageComponent)
      },
      {
        path: 'business/terminals',
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./pages/terminals/terminals-page.component').then(m => m.TerminalsPageComponent)
      },
      {
        path: 'business/settings',
        canActivate: [roleGuard],
        canDeactivate: [unsavedChangesGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./pages/business-settings/business-settings-page.component').then(m => m.BusinessSettingsPageComponent)
      },
      {
        path: 'demo/spectrum',
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
        loadComponent: () => import('./demo/spectrum-demo.component').then(m => m.SpectrumDemoComponent)
      },
      {
        path: '',
        pathMatch: 'full',
        redirectTo: () => inject(Router).parseUrl(inject(AuthService).getLandingPath())
      }
    ]
  },
  {
    path: '404',
    loadComponent: () => import('./pages/not-found/not-found-page.component').then(m => m.NotFoundPageComponent)
  },
  {
    path: '**',
    loadComponent: () => import('./pages/not-found/not-found-page.component').then(m => m.NotFoundPageComponent)
  }
];
