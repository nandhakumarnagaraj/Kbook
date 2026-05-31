import { Routes } from '@angular/router';
import { SidebarLayoutComponent } from './layout/sidebar-layout/sidebar-layout.component';
import { authGuard, roleGuard } from './core/auth/role.guard';

// All page components are lazy-loaded to keep the initial bundle small.
// Only the shell (SidebarLayoutComponent) and auth pages are eagerly loaded
// since they are needed immediately on first paint.

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./pages/login/login-page.component')
        .then(m => m.LoginPageComponent)
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./pages/forgot-password/forgot-password-page.component')
        .then(m => m.ForgotPasswordPageComponent)
  },
  {
    path: 'limited-access',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/limited-access/limited-access-page.component')
        .then(m => m.LimitedAccessPageComponent)
  },
  {
    path: '',
    component: SidebarLayoutComponent,
    canActivate: [authGuard],
    children: [

      // ── Platform Admin routes ──────────────────────────────────
      {
        path: 'admin/dashboard',
        loadComponent: () =>
          import('./pages/platform-dashboard/platform-dashboard-page.component')
            .then(m => m.PlatformDashboardPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminDashboard' }
      },
      {
        path: 'admin/businesses',
        loadComponent: () =>
          import('./pages/businesses/businesses-page.component')
            .then(m => m.BusinessesPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminBusinesses' }
      },
      {
        path: 'admin/sub-merchants',
        loadComponent: () =>
          import('./pages/sub-merchants/sub-merchants-page.component')
            .then(m => m.SubMerchantsPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminSubMerchants' }
      },
      {
        path: 'admin/payment-dashboard',
        loadComponent: () =>
          import('./pages/payment-dashboard/payment-dashboard-page.component')
            .then(m => m.PaymentDashboardPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminPaymentDashboard' }
      },
      {
        path: 'admin/commission-report',
        loadComponent: () =>
          import('./pages/commission-report/commission-report-page.component')
            .then(m => m.CommissionReportPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminCommissionReport' }
      },
      {
        path: 'admin/transactions',
        loadComponent: () =>
          import('./pages/transaction-monitor/transaction-monitor-page.component')
            .then(m => m.TransactionMonitorPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminTransactions' }
      },
      {
        path: 'admin/settlements',
        loadComponent: () =>
          import('./pages/settlement-reports/settlement-reports-page.component')
            .then(m => m.SettlementReportsPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminSettlements' }
      },
      {
        path: 'admin/commission',
        loadComponent: () =>
          import('./pages/commission-config/commission-config-page.component')
            .then(m => m.CommissionConfigPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminCommission' }
      },

      {
        path: 'admin/webhook-health',
        loadComponent: () =>
          import('./pages/webhook-health/webhook-health-page.component')
            .then(m => m.WebhookHealthPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminWebhookHealth' }
      },
      {
        path: 'admin/payment-routing',
        loadComponent: () =>
          import('./pages/payment-routing/payment-routing-page.component')
            .then(m => m.PaymentRoutingPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminPaymentRouting' }
      },
      {
        path: 'admin/developer-portal',
        loadComponent: () =>
          import('./pages/developer-portal/developer-portal-page.component')
            .then(m => m.DeveloperPortalPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['KBOOK_ADMIN'], animation: 'adminDeveloperPortal' }
      },

      // ── Restaurant Owner routes ────────────────────────────────
      {
        path: 'business/dashboard',
        loadComponent: () =>
          import('./pages/business-dashboard/business-dashboard-page.component')
            .then(m => m.BusinessDashboardPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessDashboard' }
      },
      {
        path: 'business/orders',
        loadComponent: () =>
          import('./pages/orders/orders-page.component')
            .then(m => m.OrdersPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessOrders' }
      },
      {
        path: 'business/marketplace-setup',
        loadComponent: () =>
          import('./pages/marketplace-setup/marketplace-setup-page.component')
            .then(m => m.MarketplaceSetupPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessMarketplace' }
      },
      {
        path: 'business/menu',
        loadComponent: () =>
          import('./pages/menu/menu-page.component')
            .then(m => m.MenuPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessMenu' }
      },
      {
        path: 'business/staff',
        loadComponent: () =>
          import('./pages/staff/staff-page.component')
            .then(m => m.StaffPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessStaff' }
      },
      {
        path: 'business/settings',
        loadComponent: () =>
          import('./pages/restaurant-settings/restaurant-settings-page.component')
            .then(m => m.RestaurantSettingsPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessSettings' }
      },

      {
        path: 'business/refunds',
        loadComponent: () =>
          import('./pages/refund-automation/refund-automation-page.component')
            .then(m => m.RefundAutomationPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessRefunds' }
      },
      {
        path: 'business/onboarding',
        loadComponent: () =>
          import('./pages/onboarding-tracker/onboarding-tracker-page.component')
            .then(m => m.OnboardingTrackerPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessOnboarding' }
      },
      {
        path: 'business/settlements',
        loadComponent: () =>
          import('./pages/instant-settlements/instant-settlements-page.component')
            .then(m => m.InstantSettlementsPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessSettlements' }
      },
      {
        path: 'business/tax',
        loadComponent: () =>
          import('./pages/tax-compliance/tax-compliance-page.component')
            .then(m => m.TaxCompliancePageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessTax' }
      },
      {
        path: 'business/chargebacks',
        loadComponent: () =>
          import('./pages/chargebacks/chargebacks-page.component')
            .then(m => m.ChargebacksPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessChargebacks' }
      },
      {
        path: 'business/financing',
        loadComponent: () =>
          import('./pages/financing/financing-page.component')
            .then(m => m.FinancingPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessFinancing' }
      },
      {
        path: 'business/commerce',
        loadComponent: () =>
          import('./pages/unified-commerce/unified-commerce-page.component')
            .then(m => m.UnifiedCommercePageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessCommerce' }
      },
      {
        path: 'business/customers',
        loadComponent: () =>
          import('./pages/customer-cdp/customer-cdp-page.component')
            .then(m => m.CustomerCdpPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'], animation: 'businessCustomers' }
      },

      // Default redirect
      { path: '', pathMatch: 'full', redirectTo: 'business/dashboard' }
    ]
  },

  // Catch-all
  { path: '**', redirectTo: 'login' }
];
