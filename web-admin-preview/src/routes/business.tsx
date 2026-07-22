import { createFileRoute, Link, Outlet, useRouterState, useNavigate } from "@tanstack/react-router";

export const Route = createFileRoute("/business")({
  component: BusinessLayout,
});

const navItems = [
  { to: "/business/dashboard", label: "Business Dashboard", icon: "◉" },
  { to: "/business/reports", label: "Reports", icon: "◔" },
  { to: "/business/orders", label: "Orders", icon: "▤" },
  { to: "/business/menu", label: "Menu", icon: "◈" },
  { to: "/business/staff", label: "Staff", icon: "◍" },
  { to: "/business/marketplace", label: "Integrations", icon: "◇" },
  { to: "/business/terminals", label: "Devices", icon: "▣" },
] as const;

function BusinessLayout() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-background text-foreground">
      <div className="flex min-h-screen">
        <aside className="hidden md:flex w-64 flex-col border-r border-border bg-surface">
          <div className="flex items-center gap-3 px-5 py-5 border-b border-border">
            <div
              className="h-10 w-10 rounded-lg grid place-items-center text-primary-foreground font-display font-bold shadow-[var(--shadow-elevated)]"
              style={{ backgroundImage: "var(--gradient-primary)" }}
            >
              K
            </div>
            <div>
              <div className="font-display font-bold text-sm leading-tight">KhanaBook</div>
              <div className="text-[11px] text-muted-foreground">Web Admin</div>
            </div>
          </div>

          <div className="px-4 py-4 border-b border-border">
            <div className="flex items-center gap-3 rounded-lg bg-card p-3 shadow-[var(--shadow-card)]">
              <div className="h-9 w-9 rounded-full bg-accent grid place-items-center font-semibold text-accent-foreground text-sm">
                N
              </div>
              <div className="min-w-0">
                <div className="text-sm font-semibold truncate">NandhaKumar N</div>
                <div className="text-[10px] font-bold tracking-wide text-primary">OWNER</div>
              </div>
            </div>
          </div>

          <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
            {navItems.map((item) => {
              const isActive = pathname === item.to;
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={`w-full relative flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all ${
                    isActive
                      ? "bg-primary/10 text-primary"
                      : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                  }`}
                >
                  {isActive && (
                    <span className="absolute left-0 top-1.5 bottom-1.5 w-1 rounded-r-full bg-primary" />
                  )}
                  <span className="text-base">{item.icon}</span>
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </nav>

          <div className="p-3 border-t border-border">
            <button
              onClick={() => navigate({ to: "/login" })}
              className="w-full flex items-center gap-2 px-3 py-2.5 rounded-lg text-sm font-medium text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
            >
              <span>↩</span>
              <span>Sign out</span>
            </button>
          </div>
        </aside>

        <main className="flex-1 min-w-0">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
