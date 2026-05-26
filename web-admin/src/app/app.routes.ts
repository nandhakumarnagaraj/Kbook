import { Routes } from '@angular/router';
import { LoginPageComponent } from './pages/login/login-page.component';
import { ForgotPasswordPageComponent } from './pages/forgot-password/forgot-password-page.component';
import { SidebarLayoutComponent } from './layout/sidebar-layout/sidebar-layout.component';
import { PlatformDashboardPageComponent } from './pages/platform-dashboard/platform-dashboard-page.component';
import { BusinessesPageComponent } from './pages/businesses/businesses-page.component';
import { SubMerchantsPageComponent } from './pages/sub-merchants/sub-merchants-page.component';
import { PaymentDashboardPageComponent } from './pages/payment-dashboard/payment-dashboard-page.component';
import { CommissionReportPageComponent } from './pages/commission-report/commission-report-page.component';
import { TransactionMonitorPageComponent } from './pages/transaction-monitor/transaction-monitor-page.component';
import { SettlementReportsPageComponent } from './pages/settlement-reports/settlement-reports-page.component';
import { CommissionConfigPageComponent } from './pages/commission-config/commission-config-page.component';
import { BusinessDashboardPageComponent } from './pages/business-dashboard/business-dashboard-page.component';
import { OrdersPageComponent } from './pages/orders/orders-page.component';
import { MenuPageComponent } from './pages/menu/menu-page.component';
import { StaffPageComponent } from './pages/staff/staff-page.component';
import { LimitedAccessPageComponent } from './pages/limited-access/limited-access-page.component';
import { MarketplaceSetupPageComponent } from './pages/marketplace-setup/marketplace-setup-page.component';
import { RestaurantSettingsPageComponent } from './pages/restaurant-settings/restaurant-settings-page.component';
import { authGuard, roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'forgot-password', component: ForgotPasswordPageComponent },
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
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminDashboard' }
      },
      {
        path: 'admin/businesses',
        component: BusinessesPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminBusinesses' }
      },
      {
        path: 'admin/sub-merchants',
        component: SubMerchantsPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminSubMerchants' }
      },
      {
        path: 'admin/payment-dashboard',
        component: PaymentDashboardPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminPaymentDashboard' }
      },
      {
        path: 'admin/commission-report',
        component: CommissionReportPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminCommissionReport' }
      },
      {
        path: 'admin/transactions',
        component: TransactionMonitorPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminTransactions' }
      },
      {
        path: 'admin/settlements',
        component: SettlementReportsPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminSettlements' }
      },
      {
        path: 'admin/commission',
        component: CommissionConfigPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminCommission' }
      },
      {
        path: 'business/dashboard',
        component: BusinessDashboardPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessDashboard' }
      },
      {
        path: 'business/orders',
        component: OrdersPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessOrders' }
      },
      {
        path: 'business/marketplace-setup',
        component: MarketplaceSetupPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessMarketplace' }
      },
      {
        path: 'business/menu',
        component: MenuPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessMenu' }
      },
      {
        path: 'business/staff',
        component: StaffPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessStaff' }
      },
      {
        path: 'business/settings',
        component: RestaurantSettingsPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessSettings' }
      },
      { path: '', pathMatch: 'full', redirectTo: 'business/dashboard' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
