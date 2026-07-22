import { createFileRoute } from "@tanstack/react-router";
import { PageHeader } from "./business.dashboard";

export const Route = createFileRoute("/business/reports")({
  head: () => ({
    meta: [
      { title: "Business Reports — KhanaBook Admin" },
      { name: "description", content: "Revenue, billing activity, pending payments, and refunds." },
    ],
  }),
  component: ReportsPage,
});

const primary = [
  { label: "Recognized Revenue", value: "₹98,235.50", hint: "Completed & paid bills", hero: true },
  { label: "Bill Records", value: "155", hint: "All bill states in period" },
  { label: "Pending Payments", value: "6", hint: "Draft POS payments" },
  { label: "Net After Refunds", value: "₹98,235.50", hint: "Revenue less refunds" },
];

const secondary = [
  { label: "Refunded orders", value: "0" },
  { label: "Refunded amount", value: "₹0.00" },
  { label: "Refund rate", value: "0.0%" },
];

function ReportsPage() {
  return (
    <>
      <PageHeader
        eyebrow="Owner view"
        title="Business Reports"
        subtitle="Revenue, billing activity, pending payments, and refunds for the selected period."
        ranges
      />

      <div className="px-4 md:px-8 py-6 space-y-6">
        <section className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {primary.map((k) => (
            <div
              key={k.label}
              className={`rounded-xl p-5 border shadow-[var(--shadow-card)] ${
                k.hero ? "text-primary-foreground border-transparent" : "bg-card border-border"
              }`}
              style={k.hero ? { backgroundImage: "var(--gradient-hero)" } : undefined}
            >
              <div className={`text-xs font-medium ${k.hero ? "text-primary-foreground/80" : "text-muted-foreground"}`}>{k.label}</div>
              <div className="mt-2 text-2xl md:text-3xl font-display font-bold">{k.value}</div>
              <div className={`mt-2 text-xs ${k.hero ? "text-primary-foreground/80" : "text-muted-foreground"}`}>{k.hint}</div>
            </div>
          ))}
        </section>

        <section className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {secondary.map((k) => (
            <div key={k.label} className="rounded-lg bg-card border border-border px-5 py-4">
              <div className="text-xs text-muted-foreground">{k.label}</div>
              <div className="mt-1 text-lg font-bold">{k.value}</div>
            </div>
          ))}
        </section>

        <section className="rounded-xl bg-accent/40 border border-border p-5">
          <h3 className="font-display font-semibold text-sm">How to read this report</h3>
          <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
            Bill Records includes draft and cancelled records. Recognized Revenue only includes completed or paid bills with a successful payment status.
          </p>
        </section>
      </div>
    </>
  );
}
