import { createFileRoute, Link } from "@tanstack/react-router";
import {
  LogIn,
  KeyRound,
  LayoutDashboard,
  BarChart3,
  Receipt,
  UtensilsCrossed,
  Users,
  MonitorSmartphone,
  Store,
  ShieldCheck,
  Building2,
  FileText,
  Upload,
} from "lucide-react";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "KhanaBook Web Admin — Design System & Prototypes" },
      {
        name: "description",
        content: "High-fidelity design reference for the KhanaBook Web Admin redesign.",
      },
      { property: "og:title", content: "KhanaBook Web Admin — Design Reference" },
      {
        property: "og:description",
        content: "Concept 1 (Service Pass) with Warm Kitchen accents on the OWNER dashboard.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: ProtoIndex,
});

type Kind = "Screen" | "Drawer" | "Dialog" | "States" | "Reference";

interface Entry {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  kind: Kind;
  note?: string;
}

const SECTIONS: { title: string; subtitle: string; items: Entry[] }[] = [
  {
    title: "Authentication",
    subtitle: "Public routes",
    items: [
      {
        label: "Login",
        to: "/proto/login",
        icon: LogIn,
        kind: "Screen",
        note: "5 states — default, loading, error, success, disabled",
      },
      {
        label: "Forgot password + OTP",
        to: "/proto/forgot",
        icon: KeyRound,
        kind: "States",
        note: "4 steps — request, otp, reset, done",
      },
    ],
  },
  {
    title: "OWNER — Restaurant",
    subtitle: "Full operational console",
    items: [
      {
        label: "Business Dashboard",
        to: "/proto/owner/dashboard",
        icon: LayoutDashboard,
        kind: "Screen",
        note: "Ready · loading · empty · error",
      },
      {
        label: "Reports",
        to: "/proto/owner/reports",
        icon: BarChart3,
        kind: "Screen",
        note: "Ready · loading · refreshing · empty · error · trend + payment modes + daily table",
      },
      {
        label: "Orders + Details + Refund",
        to: "/proto/owner/orders",
        icon: Receipt,
        kind: "Screen",
        note: "List, drawer, refund dialog with 6 states",
      },
      {
        label: "Menu + Add/Edit + Delete + OCR",
        to: "/proto/owner/menu",
        icon: UtensilsCrossed,
        kind: "Screen",
        note: "Grid/table, drawers, delete confirm, 6 OCR states",
      },
      {
        label: "Menu OCR (state)",
        to: "/proto/owner/menu",
        icon: Upload,
        kind: "States",
        note: "Switch 'Sheet' to ocr_*",
      },
      {
        label: "Staff Directory + Add/Edit + Temp password",
        to: "/proto/owner/staff",
        icon: Users,
        kind: "Screen",
        note: "Drawer, temp password dialog, deactivate confirm",
      },
      {
        label: "Terminal Fleet + Requests + Recovery",
        to: "/proto/owner/terminals",
        icon: MonitorSmartphone,
        kind: "Screen",
        note: "3 tabs, rename/deactivate/reject/limit/recovery",
      },
      {
        label: "Marketplace (Zomato + Swiggy)",
        to: "/proto/owner/marketplace",
        icon: Store,
        kind: "Screen",
        note: "Idle · saving · saved · validation_error",
      },
    ],
  },
  {
    title: "SHOP_ADMIN",
    subtitle: "Devices only",
    items: [
      {
        label: "Devices (SHOP_ADMIN view)",
        to: "/proto/shop/terminals",
        icon: MonitorSmartphone,
        kind: "Screen",
        note: "Same fleet screen, sidebar shows only Devices",
      },
    ],
  },
  {
    title: "KBOOK_ADMIN — Platform",
    subtitle: "Cross-business operator",
    items: [
      {
        label: "Platform Dashboard",
        to: "/proto/admin/dashboard",
        icon: ShieldCheck,
        kind: "Screen",
        note: "Ready · loading · empty · error · restricted",
      },
      {
        label: "Businesses + Details + Activate/Suspend",
        to: "/proto/admin/businesses",
        icon: Building2,
        kind: "Screen",
        note: "Table, drawer, activate/suspend dialogs",
      },
    ],
  },
  {
    title: "Handoff",
    subtitle: "Design system & Angular reference",
    items: [
      {
        label: "Design System & Handoff",
        to: "/proto/system",
        icon: FileText,
        kind: "Reference",
        note: "Tokens · typography · components · Angular mapping",
      },
    ],
  },
];

const kindColor: Record<Kind, string> = {
  Screen: "bg-info-soft text-info",
  Drawer: "bg-warning-soft text-warning",
  Dialog: "bg-warning-soft text-warning",
  States: "bg-primary-soft text-saffron",
  Reference: "bg-muted text-muted-foreground",
};

function ProtoIndex() {
  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-espresso text-espresso-foreground">
        <div className="mx-auto max-w-6xl px-6 py-10">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-lg bg-saffron text-white font-display text-lg">
              K
            </div>
            <div>
              <div className="font-display text-2xl">KhanaBook Web Admin</div>
              <div className="text-xs uppercase tracking-widest text-cream/70">
                Design reference · Service Pass + selective Warm Kitchen
              </div>
            </div>
          </div>
          <p className="mt-6 max-w-2xl text-sm leading-relaxed text-cream/80">
            High-fidelity design reference for the Angular admin redesign. All screens are rendered
            in React for reference only — production remains Angular with unchanged API contracts,
            routes, guards, and services.
          </p>
          <div className="mt-4 flex flex-wrap gap-2 text-xs">
            <a
              href="#owner"
              className="rounded-full border border-cream/20 px-3 py-1 hover:bg-cream/10"
            >
              OWNER
            </a>
            <a
              href="#shop_admin"
              className="rounded-full border border-cream/20 px-3 py-1 hover:bg-cream/10"
            >
              SHOP_ADMIN
            </a>
            <a
              href="#kbook_admin"
              className="rounded-full border border-cream/20 px-3 py-1 hover:bg-cream/10"
            >
              KBOOK_ADMIN
            </a>
            <Link
              to="/proto/system"
              className="rounded-full border border-cream/20 px-3 py-1 hover:bg-cream/10"
            >
              Design system →
            </Link>
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-6xl px-6 py-10">
        <div className="space-y-10">
          {SECTIONS.map((section) => (
            <section
              key={section.title}
              id={section.title.toLowerCase().replace(/\s.*/, "").replace("—", "")}
            >
              <div className="mb-4">
                <h2 className="text-xl font-semibold tracking-tight text-foreground">
                  {section.title}
                </h2>
                <p className="text-sm text-muted-foreground">{section.subtitle}</p>
              </div>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {section.items.map((item) => (
                  <Link
                    key={item.to + item.label}
                    to={item.to}
                    className="card-surface group flex items-start gap-3 p-4 transition-colors hover:border-saffron hover:shadow-[var(--shadow-sm)]"
                  >
                    <div className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-primary-soft text-saffron">
                      <item.icon className="h-5 w-5" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="truncate text-sm font-medium text-foreground">
                          {item.label}
                        </span>
                      </div>
                      <div className="mt-1 flex flex-wrap items-center gap-2">
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider ${kindColor[item.kind]}`}
                        >
                          {item.kind}
                        </span>
                        {item.note && (
                          <span className="text-[11px] text-muted-foreground">{item.note}</span>
                        )}
                      </div>
                    </div>
                  </Link>
                ))}
              </div>
            </section>
          ))}
        </div>

        <footer className="mt-16 border-t border-border pt-6 text-xs text-muted-foreground">
          All batches delivered: design system, app shell, authentication, OWNER (dashboard ·
          reports · orders · menu + OCR · staff · terminals · marketplace), SHOP_ADMIN devices,
          KBOOK_ADMIN (platform dashboard · businesses), and the full handoff reference at{" "}
          <Link to="/proto/system" className="underline">
            /proto/system
          </Link>
          . State variants are exposed via top/bottom pill switchers on each screen — no duplicated
          per-state pages.
        </footer>
      </div>
    </div>
  );
}
