import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { Copy, Eye, EyeOff, RefreshCw, CheckCircle2, AlertCircle, Plug, Store } from "lucide-react";
import { AppShell } from "../proto/AppShell";
import { MarketplaceStatusBadge, StateSwitcher, Btn } from "../proto/primitives";
import { SAMPLE_MARKETPLACES, type MarketplaceProvider } from "../proto/data";

export const Route = createFileRoute("/proto/owner/marketplace")({
  head: () => ({
    meta: [
      { title: "Marketplace — Spice Garden · KhanaBook" },
      { name: "description", content: "Zomato and Swiggy integration configuration prototype." },
      { property: "og:title", content: "KhanaBook Marketplace" },
      {
        property: "og:description",
        content: "Design reference for marketplace credential + webhook setup.",
      },
    ],
  }),
  component: MarketplaceProto,
});

type Save = "idle" | "saving" | "saved" | "validation_error";

function MarketplaceProto() {
  const [save, setSave] = useState<Save>("idle");
  return (
    <>
      <StateSwitcher
        value={save}
        onChange={setSave}
        options={["idle", "saving", "saved", "validation_error"] as const}
        label="Save"
      />

      <AppShell
        role="OWNER"
        title="Marketplace"
        subtitle="Sync orders from Zomato and Swiggy directly into your KhanaBook POS."
      >
        <div className="grid gap-4 lg:grid-cols-2">
          {SAMPLE_MARKETPLACES.map((p) => (
            <ProviderCard key={p.key} p={p} save={save} setSave={setSave} />
          ))}
        </div>

        <div className="mt-6 card-surface p-5 text-sm">
          <div className="mb-2 flex items-center gap-2 text-foreground">
            <Store className="h-4 w-4 text-muted-foreground" />
            <span className="font-medium">How marketplace sync works</span>
          </div>
          <p className="text-muted-foreground">
            Once connected, each marketplace posts new-order events to your dedicated webhook URL.
            KhanaBook validates the signature, creates the order, prints the ticket to the assigned
            kitchen terminal, and syncs status back to the marketplace.
          </p>
        </div>
      </AppShell>
    </>
  );
}

function ProviderCard({
  p,
  save,
  setSave,
}: {
  p: MarketplaceProvider;
  save: Save;
  setSave: (s: Save) => void;
}) {
  const [showSecret, setShowSecret] = useState(false);
  const [replacing, setReplacing] = useState(false);
  const [copied, setCopied] = useState(false);

  const brand = p.key === "zomato" ? "text-[#E23744]" : "text-[#FC8019]";

  return (
    <div className="card-surface p-5">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div
            className={`grid h-10 w-10 place-items-center rounded-md bg-muted font-display text-lg ${brand}`}
          >
            {p.name[0]}
          </div>
          <div>
            <div className="font-semibold text-foreground">{p.name}</div>
            <div className="text-xs text-muted-foreground">Marketplace integration</div>
          </div>
        </div>
        <MarketplaceStatusBadge status={p.status} />
      </div>

      {save === "saved" && (
        <div className="mb-3 flex items-start gap-2 rounded-md border border-success/20 bg-success-soft p-3 text-xs text-success">
          <CheckCircle2 className="mt-0.5 h-3.5 w-3.5" /> Credentials saved. Test order posted
          successfully.
        </div>
      )}
      {save === "validation_error" && (
        <div className="mb-3 flex items-start gap-2 rounded-md border border-danger/25 bg-danger-soft p-3 text-xs text-danger">
          <AlertCircle className="mt-0.5 h-3.5 w-3.5" /> {p.name} rejected these credentials. Check
          the API key and try again.
        </div>
      )}

      <div className="space-y-3 text-sm">
        <div>
          <label className="mb-1.5 block text-xs font-medium">Restaurant ID</label>
          <input
            defaultValue={p.restaurantId ?? ""}
            placeholder={p.key === "zomato" ? "ZOM-XXXXXX" : "SWG-XXXXXX"}
            className="h-10 w-full rounded-md border border-input bg-surface px-3 font-mono outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
          />
        </div>

        <div>
          <label className="mb-1.5 block text-xs font-medium">API key</label>
          {p.status === "connected" && !replacing ? (
            <div className="flex items-center gap-2 rounded-md border border-border bg-surface-2 px-3 py-2">
              <span className="flex-1 font-mono text-sm">••••••••••••••7f2a</span>
              <Btn size="sm" onClick={() => setReplacing(true)}>
                <RefreshCw className="h-3.5 w-3.5" /> Replace
              </Btn>
            </div>
          ) : (
            <div className="relative">
              <input
                type={showSecret ? "text" : "password"}
                defaultValue={p.status === "connected" ? "" : ""}
                placeholder="Paste new API key"
                className="h-10 w-full rounded-md border border-input bg-surface pr-10 px-3 font-mono outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
              />
              <button
                type="button"
                onClick={() => setShowSecret((s) => !s)}
                className="absolute right-2 top-1/2 grid h-7 w-7 -translate-y-1/2 place-items-center rounded text-muted-foreground hover:bg-muted"
              >
                {showSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          )}
          <div className="mt-1 text-xs text-muted-foreground">
            Stored encrypted. Never displayed after save — you'll always need to paste a new key to
            replace it.
          </div>
        </div>

        <div>
          <label className="mb-1.5 block text-xs font-medium">Webhook secret</label>
          {p.status === "connected" && !replacing ? (
            <div className="flex items-center gap-2 rounded-md border border-border bg-surface-2 px-3 py-2">
              <span className="flex-1 font-mono text-sm">••••••••••••••c19d</span>
              <Btn size="sm" onClick={() => setReplacing(true)}>
                <RefreshCw className="h-3.5 w-3.5" /> Replace
              </Btn>
            </div>
          ) : (
            <input
              type="password"
              placeholder="Paste webhook signing secret"
              className="h-10 w-full rounded-md border border-input bg-surface px-3 font-mono"
            />
          )}
        </div>

        <div>
          <label className="mb-1.5 block text-xs font-medium">
            Webhook URL{" "}
            <span className="text-muted-foreground">(paste this in {p.name}'s dashboard)</span>
          </label>
          <div className="flex items-stretch gap-2">
            <input
              readOnly
              value={p.webhookUrl}
              className="h-10 flex-1 rounded-md border border-border bg-surface-2 px-3 font-mono text-xs text-muted-foreground"
            />
            <Btn
              size="sm"
              onClick={() => {
                setCopied(true);
                setTimeout(() => setCopied(false), 1500);
              }}
            >
              {copied ? (
                <>
                  <CheckCircle2 className="h-3.5 w-3.5" /> Copied
                </>
              ) : (
                <>
                  <Copy className="h-3.5 w-3.5" /> Copy
                </>
              )}
            </Btn>
          </div>
        </div>

        <div className="flex items-center justify-between border-t border-border pt-4">
          <label className="flex items-center gap-2 text-xs">
            <span className="relative inline-flex h-4 w-7 items-center">
              <input
                type="checkbox"
                defaultChecked={p.status === "connected"}
                className="peer sr-only"
              />
              <span className="h-4 w-7 rounded-full bg-muted transition-colors peer-checked:bg-success" />
              <span className="absolute left-0.5 h-3 w-3 rounded-full bg-white transition-transform peer-checked:translate-x-3" />
            </span>
            <span>Enabled — accept incoming orders</span>
          </label>
          <div className="flex gap-2">
            <Btn
              onClick={() => {
                setReplacing(false);
                setSave("idle");
              }}
            >
              Cancel
            </Btn>
            <Btn
              variant="primary"
              disabled={save === "saving"}
              onClick={() => {
                setSave("saving");
                setTimeout(() => setSave("saved"), 700);
              }}
            >
              {save === "saving" ? "Saving…" : "Save changes"}
            </Btn>
          </div>
        </div>
      </div>

      {p.status === "disabled" && (
        <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
          <Plug className="h-3 w-3" /> Integration disabled. Orders from {p.name} will not sync.
        </div>
      )}
    </div>
  );
}
