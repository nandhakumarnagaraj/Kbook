import { createFileRoute, Link } from "@tanstack/react-router";

export const Route = createFileRoute("/business/dashboard")({
  head: () => ({
    meta: [
      { title: "Business Dashboard — KhanaBook Admin" },
      { name: "description", content: "Revenue, order health, and operational readiness in one view." },
    ],
  }),
  component: DashboardPage,
});

const ranges = ["Today", "This Week", "This Month", "Custom"];

const primary = [
  { label: "Today Revenue", value: "₹0.00", hint: "Recognized today", hero: true },
  { label: "Total Revenue", value: "₹98,235.50", hint: "For the selected period" },
  { label: "POS Orders", value: "155", hint: "View orders →", link: "/business/orders" },
  { label: "Pending Payments", value: "6", hint: "Review pending →", link: "/business/orders" },
];

const secondary = [
  { label: "Refunds", value: "₹0.00", hint: "0 orders" },
  { label: "Staff", value: "3", hint: "Manage →", link: "/business/staff" },
  { label: "Menu", value: "11", hint: "Manage →", link: "/business/menu" },
  { label: "Setup", value: "1/5", hint: "Finish setup →" },
];

const checklist = [
  { title: "Website Checkout", status: "Pending", desc: "Enable own website checkout before expecting direct online orders." },
  { title: "Customer Printer", status: "Pending", desc: "Configure the customer printer to avoid manual receipt handling." },
  { title: "Kitchen KDS Printer", status: "Pending", desc: "Configure the kitchen printer so accepted online orders can print instantly." },
  { title: "Marketplace Intake", status: "Pending", desc: "No Zomato or Swiggy marketplace channel is enabled yet.", action: { label: "Open integrations →", to: "/business/marketplace" } },
  { title: "Operating Baseline", status: "Ready", desc: "The business has staff access and menu data in place." },
];

const orders = [
  { source: "POS", id: "A-03", customer: "—", status: "Cancelled", total: "₹882.00", time: "21 Jul 2026, 6:08 pm" },
  { source: "POS", id: "A-02", customer: "Table 1", status: "Draft", total: "₹546.00", time: "21 Jul 2026, 6:07 pm" },
  { source: "POS", id: "A-01", customer: "Table 1", status: "Completed", total: "₹588.00", time: "21 Jul 2026, 12:12 pm" },
  { source: "POS", id: "A-03", customer: "—", status: "Completed", total: "₹546.00", time: "20 Jul 2026, 7:14 pm" },
  { source: "POS", id: "E-02", customer: "Table", status: "Completed", total: "₹714.00", time: "20 Jul 2026, 1:57 pm" },
  { source: "POS", id: "E-01", customer: "Table", status: "Completed", total: "₹829.50", time: "20 Jul 2026, 1:56 pm" },
  { source: "POS", id: "A-02", customer: "Table 1", status: "Cancelled", total: "₹882.00", time: "20 Jul 2026, 1:45 pm" },
  { source: "POS", id: "A-01", customer: "Table 1", status: "Completed", total: "₹546.00", time: "20 Jul 2026, 11:54 am" },
];

function statusStyle(status: string) {
  switch (status) {
    case "Completed":
    case "Ready":
      return "bg-success/10 text-success border-success/20";
    case "Draft":
      return "bg-primary/10 text-primary border-primary/20";
    case "Pending":
      return "bg-warning/15 text-warning-foreground border-warning/30";
    case "Cancelled":
      return "bg-destructive/10 text-destructive border-destructive/20";
    default:
      return "bg-muted text-muted-foreground border-border";
  }
}

function DashboardPage() {
  return (
    <>
      <PageHeader
        eyebrow="Owner overview"
        title="NandhaKumar N's Restaurant"
        subtitle="Revenue, order health, and operational readiness in one view."
        ranges
      />

      <div className="px-4 md:px-8 py-6 space-y-6">
        <section className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {primary.map((k) => (
            <div
              key={k.label}
              className={`rounded-xl p-5 border shadow-[var(--shadow-card)] transition-transform hover:-translate-y-0.5 ${
                k.hero ? "text-primary-foreground border-transparent" : "bg-card border-border"
              }`}
              style={k.hero ? { backgroundImage: "var(--gradient-hero)" } : undefined}
            >
              <div className={`text-xs font-medium ${k.hero ? "text-primary-foreground/80" : "text-muted-foreground"}`}>{k.label}</div>
              <div className="mt-2 text-2xl md:text-3xl font-display font-bold tracking-tight">{k.value}</div>
              {k.link ? (
                <Link to={k.link} className={`mt-2 inline-block text-xs font-semibold ${k.hero ? "text-primary-foreground/90" : "text-primary hover:underline"}`}>
                  {k.hint}
                </Link>
              ) : (
                <div className={`mt-2 text-xs ${k.hero ? "text-primary-foreground/80" : "text-muted-foreground"}`}>{k.hint}</div>
              )}
            </div>
          ))}
        </section>

        <section className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {secondary.map((k) => (
            <div key={k.label} className="rounded-lg bg-card border border-border px-4 py-3">
              <div className="text-[11px] uppercase tracking-wide text-muted-foreground">{k.label}</div>
              <div className="mt-1 flex items-baseline justify-between">
                <span className="text-lg font-bold">{k.value}</span>
                {k.link ? (
                  <Link to={k.link} className="text-[11px] font-semibold text-primary hover:underline">{k.hint}</Link>
                ) : (
                  <span className="text-[11px] text-muted-foreground">{k.hint}</span>
                )}
              </div>
            </div>
          ))}
        </section>

        <section className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)]">
          <div className="flex items-center justify-between px-5 py-4 border-b border-border">
            <div>
              <div className="text-[11px] uppercase tracking-wide text-muted-foreground">Readiness</div>
              <h2 className="font-display font-semibold">Setup Checklist</h2>
              <p className="text-xs text-muted-foreground">Complete the remaining items needed for daily operations.</p>
            </div>
            <span className="text-xs font-bold text-primary">1 of 5 ready</span>
          </div>
          <ul className="divide-y divide-border">
            {checklist.map((t) => (
              <li key={t.title} className="px-5 py-4 flex items-start gap-4">
                <span className={`mt-0.5 inline-flex items-center px-2 py-0.5 rounded-full border text-[11px] font-semibold ${statusStyle(t.status)}`}>{t.status}</span>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold">{t.title}</div>
                  <div className="text-xs text-muted-foreground mt-0.5">{t.desc}</div>
                </div>
                {t.action && (
                  <Link to={t.action.to} className="text-xs font-semibold text-primary hover:underline whitespace-nowrap">{t.action.label}</Link>
                )}
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] overflow-hidden">
          <div className="px-5 py-4 border-b border-border">
            <h2 className="font-display font-semibold">Recent Orders</h2>
            <p className="text-xs text-muted-foreground">Latest POS activity.</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-muted/60 text-muted-foreground text-xs uppercase tracking-wide">
                <tr>
                  {["Source", "Order", "Customer", "Status", "Total", "Created"].map((h) => (
                    <th key={h} className="text-left font-semibold px-5 py-3">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {orders.map((o, i) => (
                  <tr key={i} className={`border-t border-border ${i % 2 ? "bg-surface/40" : ""} hover:bg-accent/40`}>
                    <td className="px-5 py-3 text-xs font-bold text-muted-foreground">{o.source}</td>
                    <td className="px-5 py-3 font-semibold">{o.id}</td>
                    <td className="px-5 py-3 text-muted-foreground">{o.customer}</td>
                    <td className="px-5 py-3">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full border text-[11px] font-semibold ${statusStyle(o.status)}`}>{o.status}</span>
                    </td>
                    <td className="px-5 py-3 font-semibold">{o.total}</td>
                    <td className="px-5 py-3 text-xs text-muted-foreground">{o.time}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </>
  );
}

export function PageHeader({
  eyebrow,
  title,
  subtitle,
  ranges: showRanges,
  tabs,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
  ranges?: boolean;
  tabs?: string[];
}) {
  return (
    <header className="sticky top-0 z-10 backdrop-blur bg-background/85 border-b border-border">
      <div className="px-4 md:px-8 py-5 flex flex-wrap items-end gap-4">
        <div className="flex-1 min-w-0">
          <div className="text-[11px] uppercase tracking-wide font-semibold text-primary">{eyebrow}</div>
          <h1 className="text-xl md:text-2xl font-display font-bold truncate">{title}</h1>
          <p className="text-xs md:text-sm text-muted-foreground">{subtitle}</p>
          {tabs && (
            <div className="mt-3 flex gap-1 border-b border-border -mb-5">
              {tabs.map((t, i) => (
                <button key={t} className={`px-3 py-2 text-xs font-semibold border-b-2 -mb-px ${i === 0 ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}>{t}</button>
              ))}
            </div>
          )}
        </div>
        {showRanges && (
          <div className="flex items-center gap-2">
            <div className="flex rounded-lg border border-border bg-card p-0.5 text-xs">
              {ranges.map((r, i) => (
                <button key={r} className={`px-3 py-1.5 rounded-md font-medium ${i === 0 ? "bg-primary text-primary-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}>{r}</button>
              ))}
            </div>
            <button className="px-3 py-2 rounded-lg text-xs font-semibold border border-border bg-card hover:bg-accent">Refresh</button>
          </div>
        )}
      </div>
    </header>
  );
}
