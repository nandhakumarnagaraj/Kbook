import { createFileRoute, Link } from "@tanstack/react-router";
import {
  Badge,
  OrderStatusBadge,
  DeviceStatusBadge,
  MarketplaceStatusBadge,
  Btn,
} from "../proto/primitives";

export const Route = createFileRoute("/proto/system")({
  head: () => ({
    meta: [
      { title: "Design system & handoff — KhanaBook" },
      {
        name: "description",
        content: "Tokens, components, states and Angular mapping for the KhanaBook redesign.",
      },
      { property: "og:title", content: "KhanaBook Design System" },
      {
        property: "og:description",
        content: "Implementation-ready reference for the Angular team.",
      },
    ],
  }),
  component: SystemDoc,
});

function SystemDoc() {
  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-espresso text-espresso-foreground">
        <div className="mx-auto max-w-5xl px-6 py-8">
          <div className="text-xs uppercase tracking-widest text-cream/70">Handoff reference</div>
          <h1 className="mt-1 font-display text-3xl">KhanaBook Design System</h1>
          <p className="mt-2 max-w-2xl text-sm text-cream/70">
            Design-only deliverable — tokens, components, states and an Angular mapping for the
            existing Angular implementation. Do not rewrite the Angular app in React.
          </p>
          <div className="mt-4">
            <Link to="/" className="text-xs text-cream/80 hover:text-white">
              ← Prototype index
            </Link>
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-5xl space-y-16 px-6 py-10">
        {/* Tokens */}
        <Section
          title="1. Colour tokens"
          id="colors"
          note="Defined in src/styles.css as OKLCH CSS custom properties. Angular should mirror these in src/styles/tokens.scss with the same variable names."
        >
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <Swatch
              name="--background"
              hex="#FAF7F2"
              role="Application background (warm off-white)"
            />
            <Swatch name="--surface" hex="#FFFFFF" role="Cards, popovers, sheets" />
            <Swatch name="--surface-2" hex="#F7F3EB" role="Table header, subtle raised" />
            <Swatch name="--foreground" hex="#1C1A17" role="Primary text" />
            <Swatch name="--muted-foreground" hex="#6B655C" role="Secondary text, labels" />
            <Swatch name="--border" hex="#EFE9DE" role="Divider / input border" />
            <Swatch name="--espresso" hex="#2A1F17" role="Sidebar, strong headings" dark />
            <Swatch
              name="--saffron / --primary"
              hex="#E87A1E"
              role="Primary action, focus, links"
              dark
            />
            <Swatch name="--turmeric" hex="#E4B233" role="Secondary warm accent" />
            <Swatch
              name="--burnt"
              hex="#D2643A"
              role="Hospitality accent, hero gradient end"
              dark
            />
            <Swatch name="--success" hex="#2F855A" role="Paid, Active, Connected" dark />
            <Swatch name="--warning" hex="#B7791F" role="Pending, Incomplete" dark />
            <Swatch name="--danger" hex="#C0392B" role="Destructive, Refunded, Recovery" dark />
            <Swatch name="--info" hex="#2B6CB0" role="Informational, Card badges" dark />
          </div>
          <Sub>
            Each semantic colour has a `-soft` variant for badge/alert backgrounds (`--success-soft`
            etc.). Never use raw hex in components — always reference the token.
          </Sub>
        </Section>

        <Section title="2. Typography" id="typography">
          <Table
            headers={["Token", "Family", "Usage"]}
            rows={[
              [
                "--font-sans",
                "Inter, system-ui",
                "EVERYTHING — nav, forms, tables, dialogs, KPIs (except overrides below)",
              ],
              [
                "--font-display",
                "Instrument Serif",
                "SELECTIVE: OWNER dashboard welcome heading · OWNER dashboard hero KPI value · one optional hospitality heading (Setup progress)",
              ],
              ["--font-mono", "JetBrains Mono", "Order IDs, device IDs, API keys, secrets"],
            ]}
          />
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <TypeSample
              size="34"
              weight="700"
              text="Recognized revenue"
              sub="Inter 34 / 700 tracking -0.02em — KPI values"
            />
            <TypeSample size="24" weight="600" text="Orders" sub="Inter 24 / 600 — Page titles" />
            <TypeSample
              size="18"
              weight="600"
              text="Revenue trend"
              sub="Inter 18 / 600 — Section headers"
            />
            <TypeSample
              size="14"
              weight="500"
              text="Ravi Menon · Manager"
              sub="Inter 14 / 500 — Body medium"
            />
            <TypeSample
              size="13"
              weight="400"
              text="₹48,320 · UPI · 13:24"
              sub="Inter 13 / 400 tabular-nums — Table cells"
            />
            <TypeSample
              size="11"
              weight="600"
              text="STATUS"
              sub="Inter 11 / 600 uppercase 0.05em — Meta labels"
            />
            <TypeSample
              size="32"
              weight="400"
              font="serif"
              text="Good afternoon, Ravi"
              sub="Instrument Serif 32 — OWNER dashboard only"
            />
          </div>
        </Section>

        <Section title="3. Spacing & radius" id="spacing">
          <div className="grid gap-6 sm:grid-cols-2">
            <div>
              <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Spacing scale (4px base)
              </div>
              <Table
                headers={["Token", "px", "Use"]}
                rows={[
                  ["space-1", "4", "Tight inline"],
                  ["space-2", "8", "Icon ↔ label"],
                  ["space-3", "12", "Compact stack"],
                  ["space-4", "16", "Card padding min"],
                  ["space-5", "20", "Card padding default"],
                  ["space-6", "24", "Section separator"],
                  ["space-8", "32", "Page vertical"],
                  ["space-10", "40", "Section separator"],
                ]}
              />
            </div>
            <div>
              <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Radius scale
              </div>
              <Table
                headers={["Token", "px", "Use"]}
                rows={[
                  ["--radius-sm", "4", "Chips, tight badges"],
                  ["--radius-md", "6", "Inputs (denser)"],
                  ["--radius-lg", "8", "Inputs default, buttons"],
                  ["--radius-xl", "12", "Cards, tables"],
                  ["--radius-2xl", "16", "Hero KPI, drawer"],
                ]}
              />
            </div>
          </div>
        </Section>

        <Section title="4. Shadows" id="shadows">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <ShadowSample name="--shadow-xs" desc="Cards at rest" />
            <ShadowSample name="--shadow-sm" desc="Card hover, dropdown" />
            <ShadowSample name="--shadow-md" desc="Sticky pill, toast" />
            <ShadowSample name="--shadow-lg" desc="Dialog, drawer" />
            <ShadowSample name="--shadow-warm" desc="OWNER hero-KPI only" warm />
          </div>
        </Section>

        <Section title="5. Breakpoints" id="breakpoints">
          <Table
            headers={["Name", "Min width", "Behaviour"]}
            rows={[
              ["xs", "320", "Base mobile — stacked cards, mobile drawer nav"],
              ["sm", "640", "2-column KPI, still card-style tables"],
              ["md", "768", "Tables switch to full <table> rows, side-by-side forms"],
              ["lg", "1024", "Sidebar visible, 3-col chart+aside"],
              ["xl", "1280", "4-col KPI row"],
              ["2xl", "1536", "Content max-width 1400 stays centred"],
            ]}
          />
          <Sub>
            Verified at 320 · 375 · 430 · 768 · 1024 · 1366 · 1440 · 1920 with no page-level
            horizontal overflow. Long restaurant names use <code>truncate</code> inside{" "}
            <code>min-w-0</code> flex parents. INR values use <code>tabular-nums</code> and
            right-align in tables.
          </Sub>
        </Section>

        <Section title="6. Layout structure" id="layout">
          <Table
            headers={["Region", "Desktop", "Mobile"]}
            rows={[
              [
                "Sidebar",
                "w-60 (240px), fixed, espresso background",
                "Off-canvas drawer, w-64, opens via top-left menu button",
              ],
              [
                "Topbar",
                "h-14 (56px) sticky, restaurant switcher + search + date + bell + avatar",
                "h-14, menu + restaurant name + avatar; search/date collapse into overflow",
              ],
              [
                "Page header",
                "padding y-5 x-6/8, breadcrumb + h1 + actions",
                "Same padding; actions wrap below title with grid-cols-[1fr_auto]",
              ],
              [
                "Content",
                "max-w-1400 centered, padding x-6/8 y-6",
                "Padding x-4 y-6, single column",
              ],
            ]}
          />
        </Section>

        <Section title="7. Component reference" id="components">
          <ComponentDoc
            name="Sidebar"
            angular="kb-sidebar / KbSidebarComponent"
            inputs={[
              "role: 'OWNER' | 'SHOP_ADMIN' | 'KBOOK_ADMIN'",
              "collapsed?: boolean",
              "currentUrl: string",
            ]}
            outputs={["(collapseToggle)"]}
            states={["Default", "Collapsed (icon rail)", "Mobile drawer open"]}
            responsive="Hidden below lg; replaced by top menu button + drawer."
          />
          <ComponentDoc
            name="Topbar"
            angular="kb-topbar / KbTopbarComponent"
            inputs={[
              "restaurant: { name, branch }",
              "user: { name, role, initials }",
              "notificationCount?: number",
            ]}
            outputs={["(searchOpen)", "(dateRangeChange)", "(logoutClick)"]}
            states={["Default", "With unread badge", "Compact (mobile)"]}
            responsive="Search + date collapse under md; only menu + brand + avatar remain."
          />
          <ComponentDoc
            name="Page header"
            angular="kb-page-header"
            inputs={[
              "title: string | TemplateRef",
              "subtitle?: string",
              "actions?: TemplateRef",
              "breadcrumbs?: {label, link?}[]",
            ]}
            outputs={[]}
            states={["Default", "With breadcrumbs", "Loading (skeleton subtitle)"]}
            responsive="Actions wrap below title on <sm."
          />
          <ComponentDoc
            name="KPI card"
            angular="kb-kpi-card"
            inputs={[
              "label",
              "value",
              "delta: number",
              "spark: number[]",
              "hero?: boolean",
              "danger?: boolean",
            ]}
            outputs={[]}
            states={[
              "Default (positive)",
              "Negative delta",
              "Hero (OWNER only, gradient)",
              "Loading skeleton",
            ]}
            responsive="Full-width on <sm, 2-col sm, 4-col xl."
          />
          <ComponentDoc
            name="Status badge"
            angular="kb-badge"
            inputs={[
              "tone: 'success'|'warning'|'danger'|'info'|'muted'",
              "icon?: IconName",
              "size?: 'sm'|'md'",
            ]}
            outputs={[]}
            states={["All tones with icon + text — never colour alone"]}
            responsive="No change; badges wrap in mobile card headers."
          />
          <ComponentDoc
            name="Data table + mobile card"
            angular="kb-data-table"
            inputs={[
              "columns: KbColumnDef[]",
              "rows: T[]",
              "empty: TemplateRef",
              "loading: boolean",
              "(rowClick): EventEmitter<T>",
            ]}
            outputs={["(rowClick)", "(sortChange)", "(pageChange)"]}
            states={["Ready", "Loading (row skeletons)", "Empty", "Error", "Row hover"]}
            responsive="Under md: repeat body as stacked <kb-data-card> using column templates."
          />
          <ComponentDoc
            name="Filter toolbar"
            angular="kb-filter-toolbar"
            inputs={["searchPlaceholder", "filters: KbFilterDef[]", "activeFilters"]}
            outputs={["(searchChange)", "(filterChange)"]}
            states={["Default", "With active filter chips", "Loading (disabled)"]}
            responsive="Wraps; search takes full row on <sm."
          />
          <ComponentDoc
            name="Drawer"
            angular="kb-drawer"
            inputs={["open", "title", "subtitle?", "width: 'sm'|'md'|'lg'", "footer: TemplateRef"]}
            outputs={["(close)"]}
            states={["Closed", "Open", "Loading content"]}
            responsive="Full-width on <sm; slide-in from right on ≥sm."
          />
          <ComponentDoc
            name="Dialog / confirmation"
            angular="kb-dialog"
            inputs={["open", "title", "destructive?: boolean", "width: 'sm'|'md'|'lg'"]}
            outputs={["(close)", "(confirm)"]}
            states={[
              "Default",
              "Destructive (danger header strip)",
              "Confirming (spinner in confirm button)",
              "Disabled during submit",
            ]}
            responsive="Full-width with 16px inset on <sm; centred on ≥sm."
          />
          <ComponentDoc
            name="Form field"
            angular="kb-field"
            inputs={["label", "htmlFor", "error?", "hint?", "trailing?: TemplateRef"]}
            outputs={[]}
            states={[
              "Default",
              "Focus (saffron ring)",
              "Error (border-danger + message)",
              "Disabled",
              "Read-only",
            ]}
            responsive="Full width; inputs h-11 mobile, h-10 desktop."
          />
          <ComponentDoc
            name="Secret input"
            angular="kb-secret-input"
            inputs={["value: 'masked' | string", "label"]}
            outputs={["(reveal)", "(replace)", "(save: string)"]}
            states={[
              "Stored (masked + Replace)",
              "Replacing (input + Cancel)",
              "Revealed (eye toggle)",
              "Validation error",
            ]}
            responsive="Same across breakpoints."
          />
          <ComponentDoc
            name="Copy button / feedback"
            angular="kb-copy-button"
            inputs={["value: string", "label?"]}
            outputs={["(copied)"]}
            states={[
              "Idle (Copy icon)",
              "Copied (Check icon + 'Copied' + role=status live region)",
            ]}
            responsive="Same."
          />
          <ComponentDoc
            name="File upload (OCR)"
            angular="kb-file-drop"
            inputs={["accept: string[]", "maxSizeMb: number"]}
            outputs={["(files)", "(validationError)"]}
            states={[
              "Idle (dashed border)",
              "Dragover",
              "Validation error",
              "Uploading (progress)",
              "Processing (indeterminate)",
              "Success",
              "Partial",
              "Failed",
            ]}
            responsive="Dashed area shrinks; buttons stack under sm."
          />
          <ComponentDoc
            name="Toast"
            angular="kb-toast (backed by CdkOverlay + ToastService)"
            inputs={["tone", "title", "description?", "action?: {label, handler}"]}
            outputs={["(dismiss)"]}
            states={["Info", "Success", "Warning", "Danger"]}
            responsive="Bottom-centre on mobile, top-right on ≥md; auto-dismiss 5s (destructive: manual)."
          />
        </Section>

        <Section title="8. Status system" id="statuses">
          <div className="grid gap-4 sm:grid-cols-2">
            <Group title="Order">
              <div className="flex flex-wrap gap-2">
                <OrderStatusBadge status="paid" />
                <OrderStatusBadge status="pending" />
                <OrderStatusBadge status="refunded" />
                <OrderStatusBadge status="partial_refund" />
                <OrderStatusBadge status="cancelled" />
              </div>
            </Group>
            <Group title="Device">
              <div className="flex flex-wrap gap-2">
                <DeviceStatusBadge status="active" />
                <DeviceStatusBadge status="pending" />
                <DeviceStatusBadge status="recovery" />
                <DeviceStatusBadge status="inactive" />
                <DeviceStatusBadge status="deactivated" />
              </div>
            </Group>
            <Group title="Marketplace">
              <div className="flex flex-wrap gap-2">
                <MarketplaceStatusBadge status="connected" />
                <MarketplaceStatusBadge status="incomplete" />
                <MarketplaceStatusBadge status="disabled" />
                <MarketplaceStatusBadge status="error" />
              </div>
            </Group>
            <Group title="General">
              <div className="flex flex-wrap gap-2">
                <Badge tone="success">Active</Badge>
                <Badge tone="warning">Pending</Badge>
                <Badge tone="danger">Suspended</Badge>
                <Badge tone="info">Growth</Badge>
                <Badge tone="muted">Inactive</Badge>
              </div>
            </Group>
          </div>
        </Section>

        <Section title="9. Interaction states" id="interaction">
          <Table
            headers={["Element", "Default", "Hover", "Focus", "Active", "Disabled"]}
            rows={[
              [
                "Primary button",
                "bg-primary text-primary-foreground",
                "bg-primary-hover",
                "ring-4 ring-ring (saffron 40%)",
                "scale 0.98",
                "opacity-50 cursor-not-allowed",
              ],
              [
                "Secondary button",
                "bg-surface border-input",
                "bg-muted",
                "ring-4 ring-ring",
                "translate-y-0",
                "opacity-50",
              ],
              [
                "Danger button",
                "bg-danger",
                "opacity-90",
                "ring-4 ring-danger/40",
                "scale 0.98",
                "opacity-50",
              ],
              ["Table row", "bg-surface", "bg-surface-2", "outline visible", "—", "opacity-60"],
              ["Input", "border-input", "—", "border-saffron + ring-4 ring-ring", "—", "bg-muted"],
              [
                "Sidebar item",
                "text-sidebar-foreground",
                "bg-sidebar-active/60",
                "outline visible",
                "bg-sidebar-active",
                "opacity-40",
              ],
            ]}
          />
          <Sub>
            All destructive actions require: (a) sensitive-action strip on dialog, (b) explicit
            confirm button labelled with the verb (Refund, Suspend, Delete), (c) disabled state
            during submit to prevent duplicate submission, (d) success or error feedback in the same
            surface.
          </Sub>
        </Section>

        <Section title="10. Keyboard & accessibility" id="a11y">
          <Table
            headers={["Key", "Behaviour"]}
            rows={[
              ["Tab / Shift-Tab", "Move through focusable elements in DOM order"],
              ["Enter / Space", "Activate button, toggle checkbox"],
              ["Escape", "Close top-most dialog or drawer; restore focus to invoker"],
              ["Cmd/Ctrl-K", "Open global search (from topbar)"],
              ["Arrow keys", "Navigate menus, radio groups, tabs"],
            ]}
          />
          <Sub>
            Every focusable element shows a 4px saffron ring (44% opacity) on{" "}
            <code>:focus-visible</code>. Dialog/drawer restore focus to the invoker on close. Icons
            in buttons carry <code>aria-hidden</code>; the visible text label carries meaning.
            Minimum touch target 40×40. Reduced-motion honoured via{" "}
            <code>@media (prefers-reduced-motion: reduce)</code> — animations are removed,
            transitions kept under 100ms.
          </Sub>
        </Section>

        <Section title="11. Sensitive-action patterns" id="destructive">
          <ol className="list-decimal space-y-2 pl-6 text-sm">
            <li>
              Trigger button uses <code>outline</code> danger variant, never same color as Save.
            </li>
            <li>Opens a dialog with the danger strip ("Sensitive action") at the top.</li>
            <li>Includes explicit summary of what will change (order id, amount, recipient).</li>
            <li>Optional Review → Confirm two-step for financial actions (refund).</li>
            <li>Confirm button carries the verb (Refund, Suspend, Deactivate, Delete).</li>
            <li>
              Confirm button disables and shows spinner during submit — duplicate submission
              blocked.
            </li>
            <li>
              On success: dialog transitions to success view with reference id; ledger note visible.
            </li>
            <li>
              On failure: inline banner with error code, no charge occurred message, retry allowed.
            </li>
          </ol>
        </Section>

        <Section title="12. Angular class-name & CSS-variable mapping" id="angular-map">
          <Table
            headers={["Prototype", "Suggested Angular selector", "Root CSS custom prop"]}
            rows={[
              ["AppShell", "kb-app-shell", "--kb-shell-*"],
              ["Sidebar", "kb-sidebar", "--kb-sidebar-*"],
              ["Topbar", "kb-topbar", "--kb-topbar-*"],
              ["Page header", "kb-page-header", "—"],
              ["KpiCard", "kb-kpi-card", "--kb-kpi-*"],
              ["TrendChart", "kb-trend-chart", "—"],
              ["Data table", "kb-data-table + kb-data-card", "--kb-table-*"],
              ["Filter toolbar", "kb-filter-toolbar", "—"],
              ["Badge", "kb-badge", "--kb-badge-*"],
              ["Drawer", "kb-drawer (CdkOverlay)", "--kb-drawer-*"],
              ["Dialog", "kb-dialog (CdkDialog)", "--kb-dialog-*"],
              ["Field", "kb-field", "--kb-field-*"],
              ["Secret input", "kb-secret-input", "—"],
              ["File drop", "kb-file-drop", "—"],
              ["Toast", "kb-toast (ToastService)", "--kb-toast-*"],
              ["Skeleton", "kb-skeleton", "—"],
              ["Button", "kbButton directive", "--kb-btn-*"],
              ["Copy button", "kb-copy-button", "—"],
            ]}
          />
          <Sub>
            Use these class names or Angular directive selectors 1:1. Keep all runtime
            colour/typography values in <code>src/styles/tokens.scss</code> with the same
            CSS-variable names used here; component SCSS should only reference variables.
          </Sub>
        </Section>

        <Section title="13. Functional preservation" id="preservation">
          <ul className="list-disc space-y-2 pl-6 text-sm text-muted-foreground">
            <li>
              API base URL <code>https://kbook.iadv.cloud/api/v1</code> unchanged.
            </li>
            <li>
              Existing services (<code>AuthService</code>, <code>BusinessApiService</code>,{" "}
              <code>AdminApiService</code>) are the only data sources for the Angular
              implementation.
            </li>
            <li>
              Request / response interfaces, payload field names, JWT interceptor, storage, role
              guards, and route paths remain as-is.
            </li>
            <li>
              No new backend endpoints or fictional metrics are proposed by this design — every KPI,
              table, and action maps to an existing API.
            </li>
            <li>
              Sensitive credentials (marketplace API keys, webhook secrets, staff passwords,
              terminal recovery tokens) are never rendered in plain text after their intended reveal
              moment.
            </li>
          </ul>
        </Section>

        <div className="pt-6">
          <Link to="/">
            <Btn variant="primary">← Back to prototype index</Btn>
          </Link>
        </div>
      </div>
    </div>
  );
}

/* ---- helpers ---- */

function Section({
  title,
  id,
  note,
  children,
}: {
  title: string;
  id: string;
  note?: string;
  children: React.ReactNode;
}) {
  return (
    <section id={id}>
      <h2 className="text-xl font-semibold tracking-tight text-foreground">{title}</h2>
      {note && <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{note}</p>}
      <div className="mt-4">{children}</div>
    </section>
  );
}

function Sub({ children }: { children: React.ReactNode }) {
  return <p className="mt-3 text-xs text-muted-foreground">{children}</p>;
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="card-surface p-4">
      <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        {title}
      </div>
      {children}
    </div>
  );
}

function Swatch({
  name,
  hex,
  role,
  dark,
}: {
  name: string;
  hex: string;
  role: string;
  dark?: boolean;
}) {
  return (
    <div className="card-surface overflow-hidden">
      <div className={`h-14 ${dark ? "text-white" : "text-espresso"}`} style={{ background: hex }}>
        <div className="p-2 text-xs font-mono">{hex}</div>
      </div>
      <div className="p-3">
        <div className="font-mono text-xs text-foreground">{name}</div>
        <div className="mt-0.5 text-xs text-muted-foreground">{role}</div>
      </div>
    </div>
  );
}

function TypeSample({
  size,
  weight,
  text,
  sub,
  font,
}: {
  size: string;
  weight: string;
  text: string;
  sub: string;
  font?: "serif";
}) {
  return (
    <div className="card-surface p-4">
      <div
        className={font === "serif" ? "font-display" : ""}
        style={{
          fontSize: `${size}px`,
          fontWeight: font === "serif" ? 400 : Number(weight),
          letterSpacing: Number(size) >= 24 ? "-0.02em" : undefined,
        }}
      >
        {text}
      </div>
      <div className="mt-2 text-xs text-muted-foreground">{sub}</div>
    </div>
  );
}

function ShadowSample({ name, desc, warm }: { name: string; desc: string; warm?: boolean }) {
  return (
    <div
      className="rounded-xl bg-surface p-6 text-center"
      style={{ boxShadow: `var(${warm ? "--shadow-warm" : name})` }}
    >
      <div className="font-mono text-xs">{name}</div>
      <div className="mt-1 text-xs text-muted-foreground">{desc}</div>
    </div>
  );
}

function Table({ headers, rows }: { headers: string[]; rows: (string | React.ReactNode)[][] }) {
  return (
    <div className="card-surface overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            {headers.map((h) => (
              <th key={h} className="px-4 py-2.5 font-medium">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-t border-border">
              {r.map((c, j) => (
                <td key={j} className="px-4 py-2.5 align-top">
                  {c}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ComponentDoc({
  name,
  angular,
  inputs,
  outputs,
  states,
  responsive,
}: {
  name: string;
  angular: string;
  inputs: string[];
  outputs: string[];
  states: string[];
  responsive: string;
}) {
  return (
    <div className="card-surface mb-3 p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div className="text-sm font-semibold text-foreground">{name}</div>
        <div className="font-mono text-xs text-muted-foreground">{angular}</div>
      </div>
      <div className="mt-3 grid gap-3 text-xs sm:grid-cols-2">
        <Kv label="Inputs" items={inputs.length ? inputs : ["—"]} />
        <Kv label="Outputs" items={outputs.length ? outputs : ["—"]} />
        <Kv label="Visual states" items={states} />
        <Kv label="Responsive" items={[responsive]} />
      </div>
    </div>
  );
}

function Kv({ label, items }: { label: string; items: string[] }) {
  return (
    <div>
      <div className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </div>
      <ul className="mt-1 space-y-0.5">
        {items.map((x, i) => (
          <li key={i} className="font-mono text-[11px] text-foreground">
            {x}
          </li>
        ))}
      </ul>
    </div>
  );
}
