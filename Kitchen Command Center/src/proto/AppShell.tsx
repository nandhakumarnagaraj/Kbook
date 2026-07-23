import { Link, useRouterState } from "@tanstack/react-router";
import type { ReactNode } from "react";
import {
  LayoutDashboard,
  BarChart3,
  Receipt,
  UtensilsCrossed,
  Users,
  MonitorSmartphone,
  Store,
  ShieldCheck,
  Building2,
  Search,
  Bell,
  ChevronDown,
  Calendar,
  Menu,
} from "lucide-react";
import { useState } from "react";

type Role = "OWNER" | "SHOP_ADMIN" | "KBOOK_ADMIN";

const NAV: Record<
  Role,
  { to: string; label: string; icon: React.ComponentType<{ className?: string }> }[]
> = {
  OWNER: [
    { to: "/proto/owner/dashboard", label: "Dashboard", icon: LayoutDashboard },
    { to: "/proto/owner/reports", label: "Reports", icon: BarChart3 },
    { to: "/proto/owner/orders", label: "Orders", icon: Receipt },
    { to: "/proto/owner/menu", label: "Menu", icon: UtensilsCrossed },
    { to: "/proto/owner/staff", label: "Staff", icon: Users },
    { to: "/proto/owner/terminals", label: "Devices", icon: MonitorSmartphone },
    { to: "/proto/owner/marketplace", label: "Marketplace", icon: Store },
  ],
  SHOP_ADMIN: [{ to: "/proto/shop/terminals", label: "Devices", icon: MonitorSmartphone }],
  KBOOK_ADMIN: [
    { to: "/proto/admin/dashboard", label: "Platform", icon: ShieldCheck },
    { to: "/proto/admin/businesses", label: "Businesses", icon: Building2 },
  ],
};

export function AppShell({
  role,
  children,
  title,
  subtitle,
  actions,
  breadcrumbs,
}: {
  role: Role;
  children: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  breadcrumbs?: { label: string; to?: string }[];
}) {
  const pathname = useRouterState({ select: (r) => r.location.pathname });
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="flex min-h-screen bg-background">
      {/* Sidebar — desktop */}
      <aside className="hidden lg:flex w-60 flex-col bg-sidebar text-sidebar-foreground">
        <SidebarInner role={role} pathname={pathname} />
      </aside>

      {/* Sidebar — mobile drawer */}
      {mobileOpen && (
        <>
          <div
            className="fixed inset-0 z-40 bg-espresso/60 lg:hidden"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-sidebar text-sidebar-foreground lg:hidden">
            <SidebarInner role={role} pathname={pathname} onNavigate={() => setMobileOpen(false)} />
          </aside>
        </>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Topbar */}
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-background/95 px-4 backdrop-blur">
          <button
            onClick={() => setMobileOpen(true)}
            className="grid h-9 w-9 place-items-center rounded-md hover:bg-muted lg:hidden"
            aria-label="Open navigation"
          >
            <Menu className="h-5 w-5" />
          </button>

          <div className="hidden md:flex items-center gap-2 min-w-0">
            <div className="grid h-8 w-8 shrink-0 place-items-center rounded-md bg-espresso text-espresso-foreground font-display text-sm">
              S
            </div>
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold leading-tight">Spice Garden</div>
              <div className="truncate text-[11px] text-muted-foreground leading-tight">
                Koramangala
              </div>
            </div>
            <ChevronDown className="h-4 w-4 text-muted-foreground shrink-0" />
          </div>

          <div className="ml-auto flex items-center gap-2">
            <div className="hidden md:flex h-9 items-center gap-2 rounded-md border border-input bg-surface px-3 text-sm text-muted-foreground w-64">
              <Search className="h-4 w-4" />
              <span className="text-xs">Search orders, items…</span>
              <kbd className="ml-auto rounded border border-border bg-muted px-1.5 py-0.5 text-[10px]">
                ⌘K
              </kbd>
            </div>
            <button className="hidden sm:inline-flex h-9 items-center gap-2 rounded-md border border-input bg-surface px-3 text-sm">
              <Calendar className="h-4 w-4" />
              <span>Last 7 days</span>
            </button>
            <button className="relative grid h-9 w-9 place-items-center rounded-md hover:bg-muted">
              <Bell className="h-4 w-4" />
              <span className="absolute right-2 top-2 h-1.5 w-1.5 rounded-full bg-danger" />
            </button>
            <div className="grid h-9 w-9 place-items-center rounded-full bg-primary-soft text-espresso text-sm font-semibold">
              RM
            </div>
          </div>
        </header>

        {/* Page header */}
        <div className="border-b border-border bg-background">
          <div className="mx-auto max-w-[1400px] px-4 py-5 sm:px-6 lg:px-8">
            {breadcrumbs && (
              <nav className="mb-2 flex items-center gap-1 text-xs text-muted-foreground">
                {breadcrumbs.map((b, i) => (
                  <span key={i} className="flex items-center gap-1">
                    {i > 0 && <span>/</span>}
                    {b.to ? (
                      <Link to={b.to} className="hover:text-foreground">
                        {b.label}
                      </Link>
                    ) : (
                      <span>{b.label}</span>
                    )}
                  </span>
                ))}
              </nav>
            )}
            <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-4">
              <div className="min-w-0">
                <h1 className="truncate text-2xl font-semibold leading-tight tracking-tight text-foreground">
                  {title}
                </h1>
                {subtitle && <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>}
              </div>
              {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
            </div>
          </div>
        </div>

        <main className="mx-auto w-full max-w-[1400px] flex-1 px-4 py-6 sm:px-6 lg:px-8">
          {children}
        </main>
      </div>
    </div>
  );
}

function SidebarInner({
  role,
  pathname,
  onNavigate,
}: {
  role: Role;
  pathname: string;
  onNavigate?: () => void;
}) {
  const items = NAV[role];
  return (
    <>
      <div className="flex h-14 items-center gap-2 border-b border-sidebar-border px-4">
        <div className="grid h-8 w-8 place-items-center rounded-md bg-saffron text-white font-display">
          K
        </div>
        <div className="font-display text-lg text-sidebar-active-foreground">KhanaBook</div>
      </div>
      <div className="px-3 py-4">
        <div className="mb-2 px-2 text-[10px] font-semibold uppercase tracking-wider text-sidebar-muted">
          {role === "OWNER" ? "Restaurant" : role === "SHOP_ADMIN" ? "Devices" : "Platform"}
        </div>
        <nav className="flex flex-col gap-0.5">
          {items.map((item) => {
            const active = pathname === item.to;
            return (
              <Link
                key={item.to}
                to={item.to}
                onClick={onNavigate}
                className={`group relative flex items-center gap-3 rounded-md px-2.5 py-2 text-sm transition-colors ${
                  active
                    ? "bg-sidebar-active text-sidebar-active-foreground"
                    : "text-sidebar-foreground hover:bg-sidebar-active/60 hover:text-sidebar-active-foreground"
                }`}
              >
                {active && (
                  <span className="absolute left-0 top-1/2 h-6 w-[3px] -translate-y-1/2 rounded-r bg-saffron" />
                )}
                <item.icon className="h-4 w-4 shrink-0" />
                <span className="truncate">{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </div>
      <div className="mt-auto border-t border-sidebar-border p-3">
        <div className="flex items-center gap-2 rounded-md px-2 py-1.5">
          <div className="grid h-8 w-8 place-items-center rounded-full bg-saffron/20 text-saffron text-xs font-semibold">
            RM
          </div>
          <div className="min-w-0 text-xs">
            <div className="truncate font-medium text-sidebar-active-foreground">Ravi Menon</div>
            <div className="truncate text-sidebar-muted">{role}</div>
          </div>
        </div>
      </div>
    </>
  );
}
