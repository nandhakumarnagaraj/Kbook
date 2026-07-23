import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { TrendingUp, TrendingDown, Building2, Pause, Clock } from "lucide-react";
import { AppShell } from "../proto/AppShell";
import { Skeleton, StateSwitcher, ErrorState, EmptyState, INR, Badge } from "../proto/primitives";
import { PLATFORM_KPIS, SAMPLE_BUSINESSES } from "../proto/data";

export const Route = createFileRoute("/proto/admin/dashboard")({
  head: () => ({
    meta: [
      { title: "Platform — KhanaBook Admin" },
      { name: "description", content: "KBOOK_ADMIN platform dashboard prototype." },
      { property: "og:title", content: "KhanaBook Platform Dashboard" },
      { property: "og:description", content: "Executive dashboard for KhanaBook operators." },
    ],
  }),
  component: AdminDash,
});

type S = "ready" | "loading" | "empty" | "error" | "restricted";

function AdminDash() {
  const [s, setS] = useState<S>("ready");

  return (
    <>
      <StateSwitcher
        value={s}
        onChange={setS}
        options={["ready", "loading", "empty", "error", "restricted"] as const}
        position="top"
        label="State"
      />
      <AppShell
        role="KBOOK_ADMIN"
        title="Platform overview"
        subtitle="184 businesses · 1,428 staff · ₹1.42 Cr revenue in the last 30 days"
      >
        {s === "restricted" && (
          <div className="card-surface p-6 text-center">
            <div className="mx-auto grid h-10 w-10 place-items-center rounded-full bg-warning-soft text-warning">
              <Pause className="h-5 w-5" />
            </div>
            <h3 className="mt-3 text-sm font-semibold">Access restricted</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Your account doesn't have permission to view the platform dashboard.
            </p>
          </div>
        )}
        {s === "error" && <ErrorState onRetry={() => setS("ready")} />}
        {s === "empty" && (
          <EmptyState
            icon={Building2}
            title="No businesses onboarded yet"
            description="Once operators onboard restaurants, platform KPIs will appear here."
          />
        )}
        {s === "loading" && (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="card-surface p-5">
                <Skeleton className="h-3 w-24" />
                <Skeleton className="mt-4 h-7 w-28" />
                <Skeleton className="mt-3 h-3 w-40" />
              </div>
            ))}
          </div>
        )}
        {s === "ready" && (
          <>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {PLATFORM_KPIS.map((k) => {
                const up = k.delta >= 0;
                return (
                  <div key={k.label} className="card-surface p-5">
                    <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                      {k.label}
                    </div>
                    <div className="mt-2 text-3xl font-semibold tabular tracking-tight text-foreground">
                      {k.value}
                    </div>
                    <div
                      className={`mt-3 flex items-center gap-1 text-xs ${up ? "text-success" : "text-danger"}`}
                    >
                      {up ? (
                        <TrendingUp className="h-3 w-3" />
                      ) : (
                        <TrendingDown className="h-3 w-3" />
                      )}
                      <span className="tabular font-medium">{Math.abs(k.delta)}%</span>
                      <span className="text-muted-foreground">30d</span>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="mt-6 grid gap-4 lg:grid-cols-3">
              <div className="lg:col-span-2 card-surface overflow-hidden">
                <div className="border-b border-border px-5 py-4">
                  <h2 className="text-lg font-semibold tracking-tight">
                    Top businesses (30d revenue)
                  </h2>
                </div>
                <table className="w-full text-sm">
                  <thead className="bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
                    <tr>
                      <th className="px-5 py-3 font-medium">Business</th>
                      <th className="px-5 py-3 font-medium">City</th>
                      <th className="px-5 py-3 font-medium">Plan</th>
                      <th className="px-5 py-3 font-medium text-right">Revenue (30d)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {SAMPLE_BUSINESSES.filter((b) => b.status === "active")
                      .sort((a, b) => b.revenue30d - a.revenue30d)
                      .slice(0, 4)
                      .map((b) => (
                        <tr key={b.id} className="border-t border-border">
                          <td className="px-5 py-3 font-medium">{b.name}</td>
                          <td className="px-5 py-3 text-muted-foreground">{b.city}</td>
                          <td className="px-5 py-3">
                            <Badge tone="info" size="sm">
                              {b.plan}
                            </Badge>
                          </td>
                          <td className="px-5 py-3 text-right tabular font-medium">
                            <INR value={b.revenue30d} />
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>

              <div className="card-surface p-5">
                <h2 className="text-lg font-semibold tracking-tight">Status breakdown</h2>
                <div className="mt-4 space-y-3">
                  <StatusRow label="Active" value={162} tone="success" />
                  <StatusRow
                    label="Pending onboarding"
                    value={14}
                    tone="warning"
                    icon={<Clock className="h-3 w-3" />}
                  />
                  <StatusRow
                    label="Suspended"
                    value={8}
                    tone="danger"
                    icon={<Pause className="h-3 w-3" />}
                  />
                </div>
              </div>
            </div>
          </>
        )}
      </AppShell>
    </>
  );
}

function StatusRow({
  label,
  value,
  tone,
  icon,
}: {
  label: string;
  value: number;
  tone: "success" | "warning" | "danger";
  icon?: React.ReactNode;
}) {
  const bg = { success: "bg-success", warning: "bg-warning", danger: "bg-danger" }[tone];
  const total = 184;
  const pct = (value / total) * 100;
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-xs">
        <span className="flex items-center gap-1.5 text-foreground">
          {icon}
          {label}
        </span>
        <span className="tabular font-medium">{value}</span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
        <div className={`h-full ${bg}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
