import { Routes } from '@angular/router';
import { LoginPageComponent } from './pages/login/login-page.component';
import { SidebarLayoutComponent } from './layout/sidebar-layout/sidebar-layout.component';
import { PlatformDashboardPageComponent } from './pages/platform-dashboard/platform-dashboard-page.component';
import { BusinessesPageComponent } from './pages/businesses/businesses-page.component';
import { BusinessDashboardPageComponent } from './pages/business-dashboard/business-dashboard-page.component';
import { OrdersPageComponent } from './pages/orders/orders-page.component';
import { MenuPageComponent } from './pages/menu/menu-page.component';
import { StaffPageComponent } from './pages/staff/staff-page.component';
import { LimitedAccessPageComponent } from './pages/limited-access/limited-access-page.component';
import { authGuard, roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'limited-access', canActivate: [authGuard], component: LimitedAccessPageComponent },
  {
    path: '',
    component: SidebarLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'admin/dashboard',
        component: PlatformDashboardPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'] }
      },
      {
        path: 'admin/businesses',
        component: BusinessesPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'] }
      },
      {
        path: 'business/dashboard',
        component: BusinessDashboardPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER', 'MANAGER'] }
      },
      {
        path: 'business/orders',
        component: OrdersPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER', 'MANAGER'] }
      },
      {
        path: 'business/menu',
        component: MenuPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER', 'MANAGER'] }
      },
      {
        path: 'business/staff',
        component: StaffPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER', 'MANAGER'] }
      },
      { path: '', pathMatch: 'full', redirectTo: 'business/dashboard' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
