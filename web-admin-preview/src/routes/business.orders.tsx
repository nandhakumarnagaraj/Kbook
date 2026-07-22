import { createFileRoute } from "@tanstack/react-router";
import { PageHeader } from "./business.dashboard";

export const Route = createFileRoute("/business/orders")({
  head: () => ({
    meta: [
      { title: "Orders — KhanaBook Admin" },
      { name: "description", content: "POS order management with refund actions and status visibility." },
    ],
  }),
  component: OrdersPage,
});

const orders = [
  { src: "POS", id: "A-03", cust: "-", phone: "6745236988", status: "Cancelled", pay: "Upi / Pending", total: "₹882.00", refund: "Not refunded", time: "21 Jul 2026, 6:08 pm", action: "-" },
  { src: "POS", id: "A-02", cust: "Table 1", phone: "9150677849", status: "Draft", pay: "Cash / Pending", total: "₹546.00", refund: "Not refunded", time: "21 Jul 2026, 6:07 pm", action: "-" },
  { src: "POS", id: "A-01", cust: "Table 1", phone: "9150677849", status: "Completed", pay: "Upi / Success", total: "₹588.00", refund: "Not refunded", time: "21 Jul 2026, 12:12 pm", action: "Manual Refund" },
  { src: "POS", id: "A-03", cust: "-", phone: "6745236988", status: "Completed", pay: "Upi / Success", total: "₹546.00", refund: "Not refunded", time: "20 Jul 2026, 7:14 pm", action: "Manual Refund" },
  { src: "POS", id: "E-02", cust: "Table", phone: "9632877899", status: "Completed", pay: "Cash / Success", total: "₹714.00", refund: "Not refunded", time: "20 Jul 2026, 1:57 pm", action: "Manual Refund" },
  { src: "POS", id: "E-01", cust: "Table", phone: "9632877899", status: "Completed", pay: "Cash / Success", total: "₹829.50", refund: "Not refunded", time: "20 Jul 2026, 1:56 pm", action: "Manual Refund" },
  { src: "POS", id: "A-02", cust: "Table 1", phone: "9150677849", status: "Cancelled", pay: "Upi / Success", total: "₹882.00", refund: "Not refunded", time: "20 Jul 2026, 1:45 pm", action: "Manual Refund" },
  { src: "POS", id: "A-01", cust: "Table 1", phone: "9150677849", status: "Completed", pay: "Cash / Success", total: "₹546.00", refund: "Not refunded", time: "20 Jul 2026, 11:54 am", action: "Manual Refund" },
  { src: "POS", id: "A-01", cust: "Table 1", phone: "9150677849", status: "Completed", pay: "Cash / Success", total: "₹546.00", refund: "Not refunded", time: "19 Jul 2026, 12:02 pm", action: "Manual Refund" },
  { src: "POS", id: "E-02", cust: "-", phone: "9632587899", status: "Cancelled", pay: "Upi / Pending", total: "₹829.50", refund: "Not refunded", time: "18 Jul 2026, 6:38 pm", action: "-" },
];

function statusStyle(s: string) {
  if (s === "Completed") return "bg-success/10 text-success border-success/20";
  if (s === "Draft") return "bg-primary/10 text-primary border-primary/20";
  if (s === "Cancelled") return "bg-destructive/10 text-destructive border-destructive/20";
  return "bg-muted text-muted-foreground border-border";
}

function OrdersPage() {
  return (
    <>
      <PageHeader
        eyebrow="Refund Tools"
        title="Orders"
        subtitle="POS order management with refund actions and status visibility."
        tabs={["Unified Order View", "POS and Business Orders"]}
      />

      <div className="px-4 md:px-8 py-6 space-y-4">
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">POS order list.</p>
          <div className="flex gap-2">
            <button className="px-3 py-2 rounded-lg text-xs font-semibold border border-border bg-card hover:bg-accent">Export CSV</button>
            <button className="px-3 py-2 rounded-lg text-xs font-semibold border border-border bg-card hover:bg-accent">Refresh</button>
          </div>
        </div>

        <div className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] p-4">
          <div className="grid grid-cols-1 md:grid-cols-5 gap-3">
            <Field label="Search">
              <input placeholder="Search by order, customer, contact, or payment" className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-primary" />
            </Field>
            <Field label="Status"><Select options={["All statuses"]} /></Field>
            <Field label="Source"><Select options={["All sources"]} /></Field>
            <Field label="Rows"><Select options={["10"]} /></Field>
            <Field label="Date Range">
              <div className="flex flex-wrap gap-1">
                {["Today", "This Week", "This Month", "Custom"].map((r, i) => (
                  <button key={r} className={`px-2 py-1 rounded text-[11px] font-semibold ${i === 0 ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"}`}>{r}</button>
                ))}
              </div>
            </Field>
          </div>
          <div className="flex items-center justify-between mt-3 pt-3 border-t border-border">
            <span className="text-xs text-muted-foreground">155 of 155 orders</span>
            <button className="text-xs font-semibold text-primary hover:underline">Clear filters</button>
          </div>
        </div>

        <div className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-muted/60 text-muted-foreground text-xs uppercase tracking-wide">
                <tr>
                  {["Source", "Order", "Customer", "Status", "Payment", "Total", "Refund", "Created", "Action"].map((h) => (
                    <th key={h} className="text-left font-semibold px-4 py-3">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {orders.map((o, i) => (
                  <tr key={i} className={`border-t border-border ${i % 2 ? "bg-surface/40" : ""} hover:bg-accent/40`}>
                    <td className="px-4 py-3 text-xs font-bold text-muted-foreground">{o.src}</td>
                    <td className="px-4 py-3 font-semibold">{o.id}</td>
                    <td className="px-4 py-3">
                      <div className="text-sm">{o.cust}</div>
                      <div className="text-[11px] text-muted-foreground">{o.phone}</div>
                    </td>
                    <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full border text-[11px] font-semibold ${statusStyle(o.status)}`}>{o.status}</span></td>
                    <td className="px-4 py-3 text-xs">{o.pay}</td>
                    <td className="px-4 py-3 font-semibold">{o.total}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{o.refund}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{o.time}</td>
                    <td className="px-4 py-3">
                      {o.action === "-" ? <span className="text-muted-foreground text-xs">-</span> : (
                        <button className="text-xs font-semibold text-primary hover:underline">{o.action}</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between px-4 py-3 border-t border-border bg-surface/40">
            <span className="text-xs text-muted-foreground">Page 1 of 16</span>
            <div className="flex gap-2">
              <button className="px-3 py-1.5 rounded-md text-xs font-semibold border border-border bg-card text-muted-foreground">Previous</button>
              <button className="px-3 py-1.5 rounded-md text-xs font-semibold border border-border bg-card hover:bg-accent">Next</button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">{label}</label>
      <div className="mt-1">{children}</div>
    </div>
  );
}

export function Select({ options }: { options: string[] }) {
  return (
    <select className="w-full h-9 rounded-md border border-input bg-background px-2 text-sm outline-none focus:border-primary">
      {options.map((o) => <option key={o}>{o}</option>)}
    </select>
  );
}
