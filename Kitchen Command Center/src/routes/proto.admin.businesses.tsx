import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { Search, Pause, Play, Building2 } from "lucide-react";
import { AppShell } from "../proto/AppShell";
import { Badge, StateSwitcher, Drawer, Dialog, Btn, INR } from "../proto/primitives";
import { SAMPLE_BUSINESSES, type Business } from "../proto/data";

export const Route = createFileRoute("/proto/admin/businesses")({
  head: () => ({
    meta: [
      { title: "Businesses — KhanaBook Platform" },
      { name: "description", content: "KBOOK_ADMIN business directory prototype." },
      { property: "og:title", content: "KhanaBook Businesses" },
      { property: "og:description", content: "Design reference for cross-business admin." },
    ],
  }),
  component: BusinessesProto,
});

type Sheet = "none" | "detail" | "activate" | "suspend";

const statusTone: Record<Business["status"], "success" | "warning" | "danger"> = {
  active: "success",
  pending: "warning",
  suspended: "danger",
};

function BusinessesProto() {
  const [sheet, setSheet] = useState<Sheet>("none");
  const [active, setActive] = useState<Business | null>(null);

  const open = (b: Business) => {
    setActive(b);
    setSheet("detail");
  };

  return (
    <>
      <StateSwitcher
        value={sheet}
        onChange={setSheet}
        options={["none", "detail", "activate", "suspend"] as const}
        label="Sheet"
      />

      <AppShell
        role="KBOOK_ADMIN"
        title="Businesses"
        subtitle="184 total · 162 active · 14 pending · 8 suspended"
      >
        <div className="card-surface mb-4 flex flex-wrap items-center gap-2 p-3">
          <div className="relative flex-1 min-w-[220px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              placeholder="Search business, owner, city…"
              className="h-9 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
            />
          </div>
          <Btn size="sm">Status: All</Btn>
          <Btn size="sm">Plan: All</Btn>
          <Btn size="sm">City: All</Btn>
        </div>

        <div className="card-surface overflow-hidden">
          {/* Desktop */}
          <div className="hidden md:block overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Business</th>
                  <th className="px-4 py-3 font-medium">Owner</th>
                  <th className="px-4 py-3 font-medium">City</th>
                  <th className="px-4 py-3 font-medium">Plan</th>
                  <th className="px-4 py-3 font-medium text-right">30d revenue</th>
                  <th className="px-4 py-3 font-medium text-right">Terminals</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium w-10"></th>
                </tr>
              </thead>
              <tbody>
                {SAMPLE_BUSINESSES.map((b) => (
                  <tr
                    key={b.id}
                    onClick={() => open(b)}
                    className="cursor-pointer border-t border-border hover:bg-surface-2"
                  >
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <div className="grid h-8 w-8 shrink-0 place-items-center rounded-md bg-muted text-xs font-semibold text-muted-foreground">
                          {b.name[0]}
                        </div>
                        <div className="min-w-0">
                          <div className="truncate font-medium">{b.name}</div>
                          <div className="font-mono text-[11px] text-muted-foreground">{b.id}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{b.owner}</td>
                    <td className="px-4 py-3 text-muted-foreground">{b.city}</td>
                    <td className="px-4 py-3">
                      <Badge tone="info" size="sm">
                        {b.plan}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-right tabular">
                      {b.revenue30d ? (
                        <INR value={b.revenue30d} />
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right tabular">{b.terminals}</td>
                    <td className="px-4 py-3">
                      <Badge tone={statusTone[b.status]} size="sm">
                        {b.status[0].toUpperCase() + b.status.slice(1)}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">→</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {/* Mobile */}
          <div className="md:hidden divide-y divide-border">
            {SAMPLE_BUSINESSES.map((b) => (
              <button
                key={b.id}
                onClick={() => open(b)}
                className="block w-full p-4 text-left hover:bg-surface-2"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="truncate font-medium">{b.name}</div>
                    <div className="text-xs text-muted-foreground">
                      {b.owner} · {b.city}
                    </div>
                  </div>
                  <Badge tone={statusTone[b.status]} size="sm">
                    {b.status}
                  </Badge>
                </div>
                <div className="mt-2 flex items-center gap-2 text-xs">
                  <Badge tone="info" size="sm">
                    {b.plan}
                  </Badge>
                  <span className="text-muted-foreground">·</span>
                  <span className="tabular">
                    {b.revenue30d ? <INR value={b.revenue30d} /> : "—"}
                  </span>
                  <span className="text-muted-foreground">30d</span>
                </div>
              </button>
            ))}
          </div>
        </div>
      </AppShell>

      {/* Detail drawer */}
      <Drawer
        open={sheet === "detail"}
        onClose={() => setSheet("none")}
        title={active?.name ?? "Business"}
        subtitle={<span className="font-mono">{active?.id}</span>}
        width="lg"
        footer={
          active?.status === "suspended" ? (
            <div className="flex justify-end gap-2">
              <Btn onClick={() => setSheet("none")}>Close</Btn>
              <Btn variant="primary" onClick={() => setSheet("activate")}>
                <Play className="h-4 w-4" /> Reactivate
              </Btn>
            </div>
          ) : active?.status === "pending" ? (
            <div className="flex justify-end gap-2">
              <Btn onClick={() => setSheet("none")}>Close</Btn>
              <Btn variant="primary" onClick={() => setSheet("activate")}>
                <Play className="h-4 w-4" /> Activate business
              </Btn>
            </div>
          ) : (
            <div className="flex justify-end gap-2">
              <Btn onClick={() => setSheet("none")}>Close</Btn>
              <Btn variant="outline" onClick={() => setSheet("suspend")}>
                <Pause className="h-4 w-4" /> Suspend
              </Btn>
            </div>
          )
        }
      >
        {active && <BusinessDetail b={active} />}
      </Drawer>

      {/* Activate */}
      <Dialog
        open={sheet === "activate"}
        onClose={() => setSheet("none")}
        title={`Activate ${active?.name ?? "business"}?`}
        footer={
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="primary" onClick={() => setSheet("none")}>
              Activate
            </Btn>
          </>
        }
      >
        The owner will regain access to their dashboard and terminals will resume accepting orders.
      </Dialog>

      {/* Suspend */}
      <Dialog
        open={sheet === "suspend"}
        onClose={() => setSheet("none")}
        destructive
        title={`Suspend ${active?.name ?? "business"}?`}
        footer={
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="danger" onClick={() => setSheet("none")}>
              <Pause className="h-4 w-4" /> Suspend
            </Btn>
          </>
        }
      >
        <div className="space-y-2">
          <p>
            All owner, shop-admin and staff accounts will lose access immediately. Terminals will
            stop accepting orders. Historical data and settled payouts are preserved.
          </p>
          <label className="mt-3 block text-xs font-medium">
            Reason (visible on the business's suspension notice)
          </label>
          <textarea
            rows={3}
            defaultValue="Awaiting KYC re-verification."
            className="w-full rounded-md border border-input bg-surface p-2 text-sm"
          />
        </div>
      </Dialog>
    </>
  );
}

function BusinessDetail({ b }: { b: Business }) {
  return (
    <div className="space-y-6 text-sm">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Meta label="Status">
          <Badge tone={statusTone[b.status]} size="sm">
            {b.status}
          </Badge>
        </Meta>
        <Meta label="Plan">
          <Badge tone="info" size="sm">
            {b.plan}
          </Badge>
        </Meta>
        <Meta label="Owner">{b.owner}</Meta>
        <Meta label="City">{b.city}</Meta>
      </div>

      <div>
        <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          30-day performance
        </div>
        <div className="grid grid-cols-3 gap-3">
          <StatCard
            label="Revenue"
            value={b.revenue30d ? `₹${b.revenue30d.toLocaleString("en-IN")}` : "—"}
          />
          <StatCard label="Terminals" value={String(b.terminals)} />
          <StatCard label="Since" value={b.created} />
        </div>
      </div>

      <div>
        <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Contact
        </div>
        <div className="card-surface p-4">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Email</span>
            <span>
              {b.owner.split(" ")[0].toLowerCase()}@{b.name.split(" ")[0].toLowerCase()}.in
            </span>
          </div>
          <div className="mt-2 flex justify-between">
            <span className="text-muted-foreground">Phone</span>
            <span className="tabular">+91 98450 12345</span>
          </div>
          <div className="mt-2 flex justify-between">
            <span className="text-muted-foreground">Address</span>
            <span className="max-w-xs text-right">14, MG Road, {b.city} 560001</span>
          </div>
        </div>
      </div>

      <div>
        <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Recent audit
        </div>
        <div className="card-surface divide-y divide-border">
          {[
            "Owner updated marketplace credentials · 2h ago",
            "New terminal approved · yesterday",
            "Plan changed Starter → Growth · 4d ago",
          ].map((l, i) => (
            <div
              key={i}
              className="flex items-center gap-2 px-4 py-2 text-xs text-muted-foreground"
            >
              <Building2 className="h-3 w-3" /> {l}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="card-surface p-3">
      <div className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </div>
      <div className="mt-1 text-lg font-semibold tabular">{value}</div>
    </div>
  );
}

function Meta({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </div>
      <div className="mt-1 text-foreground">{children}</div>
    </div>
  );
}
