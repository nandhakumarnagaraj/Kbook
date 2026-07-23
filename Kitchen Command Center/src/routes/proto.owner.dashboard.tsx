import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import {
  Plus,
  Download,
  Inbox,
  RotateCcw,
  UtensilsCrossed,
  MonitorSmartphone,
  ChevronRight,
  Calendar,
  Sparkles,
} from "lucide-react";
import { AppShell } from "../proto/AppShell";
import {
  KpiCard,
  TrendChart,
  OrderStatusBadge,
  INR,
  EmptyState,
  ErrorState,
  Skeleton,
  Badge,
} from "../proto/primitives";
import { KPIS, SAMPLE_ORDERS, REVENUE_TREND } from "../proto/data";

export const Route = createFileRoute("/proto/owner/dashboard")({
  head: () => ({
    meta: [
      { title: "Dashboard — Spice Garden · KhanaBook" },
      { name: "description", content: "OWNER business dashboard prototype." },
      { property: "og:title", content: "KhanaBook OWNER Dashboard" },
      { property: "og:description", content: "Design reference — Concept 1 + Warm Kitchen blend." },
    ],
  }),
  component: DashboardProto,
});

type State = "ready" | "loading" | "empty" | "error";

function DashboardProto() {
  const [state, setState] = useState<State>("ready");

  return (
    <>
      <StateSwitcher state={state} setState={setState} />
      <AppShell
        role="OWNER"
        title={<span className="font-display text-[32px] leading-tight">Good afternoon, Ravi</span>}
        subtitle="Here's what's happening at Spice Garden today."
        actions={
          <>
            <button className="hidden md:inline-flex h-9 items-center gap-2 rounded-md border border-input bg-surface px-3 text-sm font-medium hover:bg-muted">
              <Calendar className="h-4 w-4" /> Last 7 days
            </button>
            <button className="hidden sm:inline-flex h-9 items-center gap-2 rounded-md border border-input bg-surface px-3 text-sm font-medium hover:bg-muted">
              <Download className="h-4 w-4" /> Export
            </button>
            <button className="inline-flex h-9 items-center gap-2 rounded-md bg-primary px-3 text-sm font-semibold text-primary-foreground hover:bg-primary-hover">
              <Plus className="h-4 w-4" /> New menu item
            </button>
          </>
        }
      >
        {state === "loading" && <LoadingView />}
        {state === "empty" && <EmptyView />}
        {state === "error" && <ErrorView onRetry={() => setState("ready")} />}
        {state === "ready" && <ReadyView />}
      </AppShell>
    </>
  );
}

/* -------------- Ready -------------- */

function ReadyView() {
  return (
    <div className="space-y-6">
      {/* KPI row */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {KPIS.map((k) => (
          <KpiCard key={k.label} {...k} />
        ))}
      </div>

      {/* Chart + Quick actions */}
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2 card-surface p-5">
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold tracking-tight text-foreground">
                Revenue trend
              </h2>
              <p className="text-xs text-muted-foreground">Last 7 days · ₹2,84,220 total</p>
            </div>
            <div className="flex items-center gap-2">
              <TabPill active>Revenue</TabPill>
              <TabPill>Orders</TabPill>
              <TabPill>AOV</TabPill>
            </div>
          </div>
          <TrendChart data={REVENUE_TREND} />
        </div>

        <div className="card-warm p-5">
          <div className="mb-3 flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-saffron" />
            <h2 className="font-display text-xl text-foreground">Setup progress</h2>
          </div>
          <div className="text-xs text-muted-foreground">4 of 6 complete</div>
          <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-cream">
            <div className="h-full w-2/3 bg-gradient-to-r from-saffron to-burnt" />
          </div>
          <ul className="mt-4 space-y-2 text-sm">
            <SetupItem done>Restaurant profile</SetupItem>
            <SetupItem done>Menu uploaded (48 items)</SetupItem>
            <SetupItem done>2 terminals active</SetupItem>
            <SetupItem done>3 staff added</SetupItem>
            <SetupItem>Connect Zomato</SetupItem>
            <SetupItem>Connect Swiggy</SetupItem>
          </ul>
        </div>
      </div>

      {/* Quick actions strip */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <QuickAction
          icon={Inbox}
          label="Terminal requests"
          count={2}
          to="/proto/owner/terminals/requests"
          tone="warning"
        />
        <QuickAction
          icon={RotateCcw}
          label="Refunds this week"
          count={3}
          to="/proto/owner/orders"
          tone="danger"
        />
        <QuickAction
          icon={UtensilsCrossed}
          label="Menu items unavailable"
          count={4}
          to="/proto/owner/menu"
          tone="muted"
        />
        <QuickAction
          icon={MonitorSmartphone}
          label="Devices online"
          count="2 / 2"
          to="/proto/owner/terminals"
          tone="success"
        />
      </div>

      {/* Recent orders */}
      <div className="card-surface overflow-hidden">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div>
            <h2 className="text-lg font-semibold tracking-tight text-foreground">Recent orders</h2>
            <p className="text-xs text-muted-foreground">Live · updated seconds ago</p>
          </div>
          <Link
            to="/proto/owner/orders"
            className="inline-flex items-center gap-1 text-sm font-medium text-saffron hover:underline"
          >
            View all <ChevronRight className="h-4 w-4" />
          </Link>
        </div>

        {/* Desktop table */}
        <div className="hidden md:block">
          <table className="w-full text-sm">
            <thead className="bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <Th>Order</Th>
                <Th>Time</Th>
                <Th>Items</Th>
                <Th>Payment</Th>
                <Th>Status</Th>
                <Th className="text-right">Amount</Th>
              </tr>
            </thead>
            <tbody>
              {SAMPLE_ORDERS.map((o) => (
                <tr
                  key={o.id}
                  className={`border-t border-border transition-colors hover:bg-surface-2 ${
                    o.status === "refunded" ? "border-l-2 border-l-danger" : ""
                  }`}
                >
                  <Td className="font-mono text-xs font-medium text-foreground">{o.id}</Td>
                  <Td className="text-muted-foreground tabular">{o.time}</Td>
                  <Td className="max-w-sm truncate">{o.items}</Td>
                  <Td>
                    <Badge tone={o.mode === "Cash" ? "muted" : "info"} size="sm">
                      {o.mode}
                    </Badge>
                  </Td>
                  <Td>
                    <OrderStatusBadge status={o.status} />
                  </Td>
                  <Td className="text-right font-medium">
                    <INR value={o.amount} />
                    {o.refunded && (
                      <div className="text-[11px] text-danger">
                        −<INR value={o.refunded} /> refunded
                      </div>
                    )}
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Mobile cards */}
        <div className="md:hidden divide-y divide-border">
          {SAMPLE_ORDERS.map((o) => (
            <div key={o.id} className="p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="font-mono text-xs text-muted-foreground">
                    {o.id} · {o.time}
                  </div>
                  <div className="mt-1 truncate text-sm font-medium">{o.items}</div>
                </div>
                <div className="shrink-0 text-right">
                  <div className="font-medium">
                    <INR value={o.amount} />
                  </div>
                  {o.refunded && (
                    <div className="text-[11px] text-danger">
                      −<INR value={o.refunded} />
                    </div>
                  )}
                </div>
              </div>
              <div className="mt-2 flex items-center gap-2">
                <OrderStatusBadge status={o.status} />
                <Badge tone={o.mode === "Cash" ? "muted" : "info"} size="sm">
                  {o.mode}
                </Badge>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* -------------- Sub-components -------------- */

function TabPill({ children, active }: { children: React.ReactNode; active?: boolean }) {
  return (
    <button
      className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
        active ? "bg-espresso text-espresso-foreground" : "text-muted-foreground hover:bg-muted"
      }`}
    >
      {children}
    </button>
  );
}

function SetupItem({ children, done }: { children: React.ReactNode; done?: boolean }) {
  return (
    <li className="flex items-center gap-2">
      <span
        className={`grid h-4 w-4 shrink-0 place-items-center rounded-full text-[10px] ${
          done ? "bg-success text-white" : "border border-border bg-surface text-muted-foreground"
        }`}
      >
        {done ? "✓" : ""}
      </span>
      <span className={done ? "text-muted-foreground line-through" : "text-foreground"}>
        {children}
      </span>
    </li>
  );
}

function QuickAction({
  icon: Icon,
  label,
  count,
  to,
  tone,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  count: number | string;
  to: string;
  tone: "success" | "warning" | "danger" | "muted";
}) {
  const toneBg = {
    success: "bg-success-soft text-success",
    warning: "bg-warning-soft text-warning",
    danger: "bg-danger-soft text-danger",
    muted: "bg-muted text-muted-foreground",
  }[tone];
  return (
    <Link
      to={to}
      className="card-surface flex items-center gap-3 p-4 transition-colors hover:border-saffron hover:shadow-[var(--shadow-sm)]"
    >
      <div className={`grid h-10 w-10 shrink-0 place-items-center rounded-lg ${toneBg}`}>
        <Icon className="h-5 w-5" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate text-xs text-muted-foreground">{label}</div>
        <div className="text-xl font-semibold tabular text-foreground">{count}</div>
      </div>
      <ChevronRight className="h-4 w-4 text-muted-foreground" />
    </Link>
  );
}

function Th({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <th className={`px-5 py-3 font-medium ${className}`}>{children}</th>;
}
function Td({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <td className={`px-5 py-3 ${className}`}>{children}</td>;
}

/* -------------- Alternate states -------------- */

function LoadingView() {
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="card-surface p-5">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="mt-4 h-8 w-32" />
            <Skeleton className="mt-3 h-3 w-40" />
          </div>
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2 card-surface p-5">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="mt-6 h-48 w-full" />
        </div>
        <div className="card-surface p-5">
          <Skeleton className="h-4 w-32" />
          <div className="mt-4 space-y-2">
            {[0, 1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-4 w-full" />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function EmptyView() {
  return (
    <div className="space-y-6">
      <EmptyState
        icon={UtensilsCrossed}
        title="No activity yet"
        description="Once you start taking orders, your dashboard will light up here."
        action={
          <button className="rounded-md bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground hover:bg-primary-hover">
            Add your first menu item
          </button>
        }
      />
    </div>
  );
}

function ErrorView({ onRetry }: { onRetry: () => void }) {
  return (
    <ErrorState
      title="Couldn't load your dashboard"
      description="Check your connection and try again. Your data is safe."
      onRetry={onRetry}
    />
  );
}

/* -------------- Debug switcher -------------- */

function StateSwitcher({ state, setState }: { state: State; setState: (s: State) => void }) {
  const opts: State[] = ["ready", "loading", "empty", "error"];
  return (
    <div className="fixed bottom-4 left-1/2 z-50 -translate-x-1/2 rounded-full border border-border bg-surface p-1 shadow-[var(--shadow-md)]">
      {opts.map((o) => (
        <button
          key={o}
          onClick={() => setState(o)}
          className={`rounded-full px-3 py-1 text-xs font-medium capitalize transition-colors ${
            state === o
              ? "bg-espresso text-espresso-foreground"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          {o}
        </button>
      ))}
    </div>
  );
}
