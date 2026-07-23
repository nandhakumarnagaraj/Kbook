import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import {
  Calendar,
  Download,
  TrendingUp,
  TrendingDown,
  RefreshCw,
  FileBarChart,
} from "lucide-react";
import { AppShell } from "../proto/AppShell";
import {
  INR,
  Skeleton,
  StateSwitcher,
  ErrorState,
  EmptyState,
  Btn,
  TrendChart,
} from "../proto/primitives";
import { REVENUE_TREND } from "../proto/data";

export const Route = createFileRoute("/proto/owner/reports")({
  head: () => ({
    meta: [
      { title: "Reports — Spice Garden · KhanaBook" },
      {
        name: "description",
        content:
          "Financial reports prototype — recognized revenue, net revenue, pending payments, bill count, revenue trend, payment-mode breakdown, and daily records.",
      },
      { property: "og:title", content: "KhanaBook Reports" },
      {
        property: "og:description",
        content:
          "Design reference for restaurant financial reports (Service Pass, operational density).",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: ReportsProto,
});

type ReportState = "ready" | "loading" | "refreshing" | "empty" | "error";

const PAYMENT_MODES = [
  { label: "UPI", amount: 168420, count: 742, tone: "bg-primary" },
  { label: "Card", amount: 62310, count: 261, tone: "bg-info" },
  { label: "Cash", amount: 38040, count: 218, tone: "bg-turmeric" },
  { label: "Marketplace", amount: 15450, count: 63, tone: "bg-burnt" },
];

const CARDS = [
  { key: "recognized", label: "Recognized revenue", value: 284220, delta: +12.4, currency: true },
  { key: "bills", label: "Bills recorded", value: 1284, delta: +8.1, currency: false },
  {
    key: "pending",
    label: "Pending payments",
    value: 18420,
    delta: -4.2,
    currency: true,
    warn: true,
  },
  { key: "net", label: "Net revenue", value: 265800, delta: +11.8, currency: true },
];

function ReportsProto() {
  const [s, setS] = useState<ReportState>("ready");
  const refreshing = s === "refreshing";
  const busy = s === "loading" || refreshing;

  const paymentTotal = PAYMENT_MODES.reduce((sum, m) => sum + m.amount, 0);

  return (
    <>
      <StateSwitcher
        value={s}
        onChange={setS}
        options={["ready", "loading", "refreshing", "empty", "error"] as const}
        position="top"
        label="State"
      />
      <AppShell
        role="OWNER"
        title="Reports"
        subtitle="Last 7 days · Mon 15 Jul — Sun 21 Jul"
        actions={
          <>
            <Btn disabled={busy}>
              <Calendar className="h-4 w-4" /> Last 7 days
            </Btn>
            <Btn
              disabled={busy}
              onClick={() => {
                if (s === "ready") {
                  setS("refreshing");
                  window.setTimeout(() => setS("ready"), 900);
                }
              }}
            >
              <RefreshCw className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`} />
              {refreshing ? "Refreshing" : "Refresh"}
            </Btn>
            <Btn variant="primary" disabled={busy}>
              <Download className="h-4 w-4" /> Download PDF
            </Btn>
          </>
        }
      >
        {s === "error" && <ErrorState onRetry={() => setS("ready")} />}

        {s === "empty" && (
          <EmptyState
            icon={FileBarChart}
            title="No reports for this range"
            description="There are no recorded bills for the selected date range. Try widening the range or choose a different period."
            action={<Btn variant="primary">Reset to last 7 days</Btn>}
          />
        )}

        {s === "loading" && (
          <>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="card-surface p-5">
                  <Skeleton className="h-3 w-24" />
                  <Skeleton className="mt-4 h-7 w-32" />
                  <Skeleton className="mt-3 h-3 w-40" />
                </div>
              ))}
            </div>
            <div className="mt-6 card-surface p-5">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="mt-4 h-[200px] w-full" />
            </div>
          </>
        )}

        {(s === "ready" || s === "refreshing") && (
          <div
            aria-busy={refreshing || undefined}
            className={refreshing ? "opacity-70 transition-opacity" : ""}
          >
            {/* KPI summary cards */}
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {CARDS.map((c) => {
                const up = c.delta >= 0;
                const goodDirection = c.warn ? !up : up;
                const tone = goodDirection ? "text-success" : "text-danger";
                return (
                  <div key={c.key} className="card-surface p-5">
                    <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                      {c.label}
                    </div>
                    <div className="mt-2 text-3xl font-semibold tabular tracking-tight">
                      {c.currency ? <INR value={c.value} /> : c.value.toLocaleString("en-IN")}
                    </div>
                    <div className={`mt-3 flex items-center gap-1 text-xs ${tone}`}>
                      {up ? (
                        <TrendingUp className="h-3 w-3" aria-hidden />
                      ) : (
                        <TrendingDown className="h-3 w-3" aria-hidden />
                      )}
                      <span className="tabular font-medium">
                        {up ? "+" : "−"}
                        {Math.abs(c.delta)}%
                      </span>
                      <span className="text-muted-foreground">vs previous period</span>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Trend + payment-mode breakdown */}
            <div className="mt-6 grid gap-4 lg:grid-cols-3">
              <div className="card-surface p-5 lg:col-span-2">
                <div className="flex items-start justify-between">
                  <div>
                    <h2 className="text-base font-semibold tracking-tight">Revenue trend</h2>
                    <p className="text-xs text-muted-foreground">
                      Daily gross revenue · last 7 days
                    </p>
                  </div>
                  <div className="text-right">
                    <div className="text-xs uppercase tracking-wide text-muted-foreground">
                      Peak day
                    </div>
                    <div className="text-sm font-semibold tabular">
                      <INR value={Math.max(...REVENUE_TREND.map((d) => d.value))} />
                    </div>
                  </div>
                </div>
                <div className="mt-5">
                  <TrendChart data={REVENUE_TREND} height={200} />
                </div>
              </div>

              <div className="card-surface p-5">
                <h2 className="text-base font-semibold tracking-tight">Payment modes</h2>
                <p className="text-xs text-muted-foreground">Share of recognized revenue</p>
                <ul className="mt-4 space-y-3">
                  {PAYMENT_MODES.map((m) => {
                    const pct = Math.round((m.amount / paymentTotal) * 100);
                    return (
                      <li key={m.label}>
                        <div className="flex items-center justify-between text-sm">
                          <div className="flex items-center gap-2">
                            <span className={`h-2.5 w-2.5 rounded-sm ${m.tone}`} aria-hidden />
                            <span className="font-medium">{m.label}</span>
                          </div>
                          <div className="tabular text-muted-foreground">{pct}%</div>
                        </div>
                        <div className="mt-1 flex items-center justify-between text-xs text-muted-foreground">
                          <span className="tabular">
                            <INR value={m.amount} />
                          </span>
                          <span className="tabular">{m.count} bills</span>
                        </div>
                        <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                          <div className={`h-full ${m.tone}`} style={{ width: `${pct}%` }} />
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </div>
            </div>

            {/* Records — desktop table */}
            <div className="mt-6 card-surface overflow-hidden">
              <div className="border-b border-border px-5 py-4">
                <h2 className="text-base font-semibold tracking-tight">Daily breakdown</h2>
                <p className="text-xs text-muted-foreground">
                  Revenue, bills and pending settlement
                </p>
              </div>
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full text-sm">
                  <caption className="sr-only">
                    Daily revenue breakdown for the selected date range
                  </caption>
                  <thead className="bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
                    <tr>
                      <th scope="col" className="px-5 py-3 font-medium">
                        Day
                      </th>
                      <th scope="col" className="px-5 py-3 font-medium text-right">
                        Bills
                      </th>
                      <th scope="col" className="px-5 py-3 font-medium text-right">
                        Gross
                      </th>
                      <th scope="col" className="px-5 py-3 font-medium text-right">
                        Refunds
                      </th>
                      <th scope="col" className="px-5 py-3 font-medium text-right">
                        Pending
                      </th>
                      <th scope="col" className="px-5 py-3 font-medium text-right">
                        Net
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {REVENUE_TREND.map((d) => {
                      const bills = Math.round(d.value / 260);
                      const refunds = Math.round(d.value * 0.02);
                      const pending = Math.round(d.value * 0.05);
                      const net = d.value - refunds;
                      return (
                        <tr key={d.day} className="border-t border-border">
                          <td className="px-5 py-3 font-medium">{d.day}</td>
                          <td className="px-5 py-3 text-right tabular">{bills}</td>
                          <td className="px-5 py-3 text-right tabular">
                            <INR value={d.value} />
                          </td>
                          <td className="px-5 py-3 text-right tabular text-danger">
                            −<INR value={refunds} />
                          </td>
                          <td className="px-5 py-3 text-right tabular text-warning">
                            <INR value={pending} />
                          </td>
                          <td className="px-5 py-3 text-right tabular font-semibold">
                            <INR value={net} />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Mobile cards */}
              <ul className="divide-y divide-border md:hidden">
                {REVENUE_TREND.map((d) => {
                  const bills = Math.round(d.value / 260);
                  const refunds = Math.round(d.value * 0.02);
                  const pending = Math.round(d.value * 0.05);
                  const net = d.value - refunds;
                  return (
                    <li key={d.day} className="px-5 py-4">
                      <div className="flex items-center justify-between">
                        <div className="text-sm font-semibold">{d.day}</div>
                        <div className="tabular text-sm font-semibold">
                          <INR value={net} />
                        </div>
                      </div>
                      <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-muted-foreground">
                        <div className="flex justify-between">
                          <dt>Bills</dt>
                          <dd className="tabular text-foreground">{bills}</dd>
                        </div>
                        <div className="flex justify-between">
                          <dt>Gross</dt>
                          <dd className="tabular text-foreground">
                            <INR value={d.value} />
                          </dd>
                        </div>
                        <div className="flex justify-between">
                          <dt>Refunds</dt>
                          <dd className="tabular text-danger">
                            −<INR value={refunds} />
                          </dd>
                        </div>
                        <div className="flex justify-between">
                          <dt>Pending</dt>
                          <dd className="tabular text-warning">
                            <INR value={pending} />
                          </dd>
                        </div>
                      </dl>
                    </li>
                  );
                })}
              </ul>
            </div>
          </div>
        )}
      </AppShell>
    </>
  );
}
