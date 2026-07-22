import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { PageHeader } from "./business.dashboard";

export const Route = createFileRoute("/business/marketplace")({
  head: () => ({
    meta: [
      { title: "Integrations — KhanaBook Admin" },
      { name: "description", content: "Connect supported delivery providers for online orders." },
    ],
  }),
  component: MarketplacePage,
});

function MarketplacePage() {
  const [zomato, setZomato] = useState(false);
  const [swiggy, setSwiggy] = useState(false);

  return (
    <>
      <PageHeader
        eyebrow="Owner Access"
        title="Online-order Integrations"
        subtitle="Connect supported delivery providers for online orders from this restaurant."
        tabs={["Zomato & Swiggy", "Marketplace Integration"]}
      />
      <div className="px-4 md:px-8 py-6 space-y-5">
        <div>
          <h2 className="font-display font-semibold">Marketplace</h2>
          <p className="text-xs text-muted-foreground">Configure Zomato and Swiggy integration for online orders.</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <ProviderCard name="Zomato" color="oklch(0.6 0.22 25)" enabled={zomato} onToggle={setZomato} />
          <ProviderCard name="Swiggy" color="oklch(0.72 0.17 55)" enabled={swiggy} onToggle={setSwiggy} />
        </div>

        <div className="flex justify-end">
          <button className="px-4 py-2.5 rounded-lg text-sm font-semibold text-primary-foreground shadow-[var(--shadow-elevated)]" style={{ backgroundImage: "var(--gradient-primary)" }}>
            Save Marketplace Config
          </button>
        </div>
      </div>
    </>
  );
}

function ProviderCard({ name, color, enabled, onToggle }: { name: string; color: string; enabled: boolean; onToggle: (v: boolean) => void }) {
  return (
    <div className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] p-5">
      <div className="flex items-center gap-4">
        <div className="h-12 w-12 rounded-xl grid place-items-center text-primary-foreground font-display font-bold text-lg" style={{ backgroundColor: color }}>
          {name[0]}
        </div>
        <div className="flex-1">
          <div className="font-semibold">{name}</div>
          <div className="text-xs text-muted-foreground">Sync menu & receive online orders</div>
        </div>
        <button
          onClick={() => onToggle(!enabled)}
          className={`relative inline-flex h-6 w-11 items-center rounded-full transition ${enabled ? "bg-success" : "bg-muted"}`}
        >
          <span className={`inline-block h-5 w-5 transform rounded-full bg-white transition ${enabled ? "translate-x-5" : "translate-x-0.5"}`} />
        </button>
      </div>

      {enabled && (
        <div className="mt-4 pt-4 border-t border-border space-y-3">
          <div>
            <label className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Restaurant ID</label>
            <input className="mt-1 w-full h-9 rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-primary" placeholder="Enter restaurant ID" />
          </div>
          <div>
            <label className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">API Key</label>
            <input type="password" className="mt-1 w-full h-9 rounded-md border border-input bg-background px-3 font-mono text-sm outline-none focus:border-primary" placeholder="••••••••••••" />
          </div>
        </div>
      )}
    </div>
  );
}
