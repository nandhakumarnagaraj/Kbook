import { useEffect, useRef, type ReactNode } from "react";
import {
  CheckCircle2,
  Clock,
  XCircle,
  RotateCcw,
  AlertTriangle,
  Ban,
  Wifi,
  WifiOff,
  ShieldAlert,
  Plug,
  PlugZap,
  CircleSlash,
} from "lucide-react";

/* Escape-to-close + focus-restore for modals/drawers */
function useModalA11y(open: boolean, onClose: () => void) {
  const returnFocusTo = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (!open) return;
    returnFocusTo.current = (document.activeElement as HTMLElement) ?? null;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
      returnFocusTo.current?.focus?.();
    };
  }, [open, onClose]);
}

/* ------------------------------------------------------------------ */
/* Status badge system — text + icon, never color alone                */
/* ------------------------------------------------------------------ */

type Tone = "success" | "warning" | "danger" | "info" | "muted";

const toneClasses: Record<Tone, string> = {
  success: "bg-success-soft text-success border-success/20",
  warning: "bg-warning-soft text-warning border-warning/25",
  danger: "bg-danger-soft text-danger border-danger/25",
  info: "bg-info-soft text-info border-info/20",
  muted: "bg-muted text-muted-foreground border-border",
};

export function Badge({
  tone = "muted",
  icon: Icon,
  children,
  size = "md",
}: {
  tone?: Tone;
  icon?: React.ComponentType<{ className?: string }>;
  children: ReactNode;
  size?: "sm" | "md";
}) {
  const sz = size === "sm" ? "text-[11px] px-1.5 py-0.5 gap-1" : "text-xs px-2 py-1 gap-1.5";
  return (
    <span
      className={`inline-flex items-center rounded-md border font-medium ${sz} ${toneClasses[tone]}`}
    >
      {Icon && <Icon className={size === "sm" ? "h-3 w-3" : "h-3.5 w-3.5"} />}
      {children}
    </span>
  );
}

export function OrderStatusBadge({ status }: { status: string }) {
  switch (status) {
    case "paid":
      return (
        <Badge tone="success" icon={CheckCircle2}>
          Paid
        </Badge>
      );
    case "pending":
      return (
        <Badge tone="warning" icon={Clock}>
          Pending
        </Badge>
      );
    case "refunded":
      return (
        <Badge tone="danger" icon={RotateCcw}>
          Refunded
        </Badge>
      );
    case "partial_refund":
      return (
        <Badge tone="warning" icon={RotateCcw}>
          Partial refund
        </Badge>
      );
    case "cancelled":
      return (
        <Badge tone="muted" icon={Ban}>
          Cancelled
        </Badge>
      );
    default:
      return <Badge>{status}</Badge>;
  }
}

export function DeviceStatusBadge({ status }: { status: string }) {
  switch (status) {
    case "active":
      return (
        <Badge tone="success" icon={Wifi}>
          Active
        </Badge>
      );
    case "inactive":
      return (
        <Badge tone="muted" icon={WifiOff}>
          Inactive
        </Badge>
      );
    case "pending":
      return (
        <Badge tone="warning" icon={Clock}>
          Pending approval
        </Badge>
      );
    case "recovery":
      return (
        <Badge tone="danger" icon={ShieldAlert}>
          Recovery required
        </Badge>
      );
    case "deactivated":
      return (
        <Badge tone="muted" icon={CircleSlash}>
          Deactivated
        </Badge>
      );
    default:
      return <Badge>{status}</Badge>;
  }
}

export function MarketplaceStatusBadge({ status }: { status: string }) {
  switch (status) {
    case "connected":
      return (
        <Badge tone="success" icon={PlugZap}>
          Connected
        </Badge>
      );
    case "incomplete":
      return (
        <Badge tone="warning" icon={AlertTriangle}>
          Incomplete
        </Badge>
      );
    case "disabled":
      return (
        <Badge tone="muted" icon={Plug}>
          Disabled
        </Badge>
      );
    case "error":
      return (
        <Badge tone="danger" icon={XCircle}>
          Error
        </Badge>
      );
    default:
      return <Badge>{status}</Badge>;
  }
}

/* ------------------------------------------------------------------ */
/* KPI card                                                            */
/* ------------------------------------------------------------------ */

export function KpiCard({
  label,
  value,
  delta,
  spark,
  hero,
  danger,
}: {
  label: string;
  value: string;
  delta: number;
  spark: number[];
  hero?: boolean;
  danger?: boolean;
}) {
  const up = delta >= 0;
  const goodDirection = danger ? !up : up;
  const deltaTone = goodDirection ? "text-success" : "text-danger";
  const arrow = up ? "▲" : "▼";

  // sparkline points
  const max = Math.max(...spark);
  const min = Math.min(...spark);
  const range = max - min || 1;
  const w = 88;
  const h = 28;
  const points = spark
    .map((v, i) => {
      const x = (i / (spark.length - 1)) * w;
      const y = h - ((v - min) / range) * h;
      return `${x},${y}`;
    })
    .join(" ");

  const wrap = hero
    ? "hero-gradient rounded-2xl p-5 shadow-[var(--shadow-warm)]"
    : "card-surface p-5";
  const labelCls = hero ? "text-white/80" : "text-muted-foreground";
  const valueCls = hero ? "text-white" : "text-foreground";
  const strokeCls = hero ? "stroke-white/80" : goodDirection ? "stroke-success" : "stroke-danger";

  return (
    <div className={wrap}>
      <div className={`text-xs font-medium uppercase tracking-wide ${labelCls}`}>{label}</div>
      <div className="mt-2 flex items-end justify-between gap-3">
        <div
          className={`${hero ? "font-display text-[38px]" : "text-3xl font-semibold"} leading-none tabular tracking-tight ${valueCls}`}
        >
          {value}
        </div>
        <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} className="shrink-0">
          <polyline fill="none" strokeWidth="1.5" className={strokeCls} points={points} />
        </svg>
      </div>
      <div className={`mt-3 flex items-center gap-2 text-xs ${hero ? "text-white/85" : deltaTone}`}>
        <span className="tabular font-medium">
          {arrow} {Math.abs(delta)}%
        </span>
        <span className={hero ? "text-white/70" : "text-muted-foreground"}>vs previous 7 days</span>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Bar chart (revenue trend) — pure SVG                                */
/* ------------------------------------------------------------------ */

export function TrendChart({
  data,
  height = 200,
}: {
  data: { day: string; value: number }[];
  height?: number;
}) {
  const max = Math.max(...data.map((d) => d.value));
  return (
    <div className="w-full">
      <div className="flex items-end gap-3" style={{ height }}>
        {data.map((d) => {
          const pct = (d.value / max) * 100;
          return (
            <div key={d.day} className="flex flex-1 flex-col items-center gap-2">
              <div className="w-full flex-1 flex items-end">
                <div
                  className="w-full rounded-md bg-primary/85 hover:bg-primary transition-colors"
                  style={{ height: `${pct}%`, minHeight: 4 }}
                  title={`₹${d.value.toLocaleString("en-IN")}`}
                />
              </div>
              <div className="text-[11px] text-muted-foreground">{d.day}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* States: empty, error, loading skeleton                              */
/* ------------------------------------------------------------------ */

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="card-surface flex flex-col items-center justify-center gap-3 py-12 px-6 text-center">
      <div className="grid h-12 w-12 place-items-center rounded-full bg-muted">
        <Icon className="h-6 w-6 text-muted-foreground" />
      </div>
      <div>
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  );
}

export function ErrorState({
  title = "Something went wrong",
  description = "We couldn't load this section. Please try again.",
  onRetry,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
}) {
  return (
    <div className="card-surface flex flex-col items-center justify-center gap-3 py-12 px-6 text-center">
      <div className="grid h-12 w-12 place-items-center rounded-full bg-danger-soft">
        <AlertTriangle className="h-6 w-6 text-danger" />
      </div>
      <div>
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      </div>
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-1 rounded-md border border-input bg-surface px-3 py-1.5 text-sm font-medium hover:bg-muted"
        >
          Retry
        </button>
      )}
    </div>
  );
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-muted ${className}`} />;
}

/* ------------------------------------------------------------------ */
/* Currency                                                            */
/* ------------------------------------------------------------------ */

export function INR({ value }: { value: number }) {
  return <span className="tabular">₹{value.toLocaleString("en-IN")}</span>;
}

/* ------------------------------------------------------------------ */
/* State switcher pill (top or bottom of a prototype)                  */
/* ------------------------------------------------------------------ */

export function StateSwitcher<T extends string>({
  value,
  onChange,
  options,
  position = "bottom",
  label,
}: {
  value: T;
  onChange: (v: T) => void;
  options: readonly T[];
  position?: "top" | "bottom";
  label?: string;
}) {
  const pos = position === "top" ? "top-4" : "bottom-4";
  return (
    <div
      className={`fixed left-1/2 z-50 -translate-x-1/2 ${pos} flex items-center gap-1 rounded-full border border-border bg-surface p-1 shadow-[var(--shadow-md)]`}
    >
      {label && (
        <span className="ml-2 pr-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
          {label}
        </span>
      )}
      {options.map((o) => (
        <button
          key={o}
          onClick={() => onChange(o)}
          className={`rounded-full px-3 py-1 text-xs font-medium capitalize transition-colors ${
            value === o
              ? "bg-espresso text-espresso-foreground"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          {o.replace(/_/g, " ")}
        </button>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Drawer (right-hand slide-in)                                        */
/* ------------------------------------------------------------------ */

export function Drawer({
  open,
  onClose,
  title,
  subtitle,
  children,
  footer,
  width = "md",
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  subtitle?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  width?: "sm" | "md" | "lg";
}) {
  useModalA11y(open, onClose);
  if (!open) return null;
  const w = { sm: "sm:w-[420px]", md: "sm:w-[560px]", lg: "sm:w-[720px]" }[width];
  return (
    <>
      <div
        className="fixed inset-0 z-40 bg-espresso/50 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden
      />
      <aside
        className={`fixed inset-y-0 right-0 z-50 flex w-full flex-col bg-surface shadow-[var(--shadow-lg)] ${w}`}
        role="dialog"
        aria-modal="true"
      >
        <div className="flex items-start justify-between gap-4 border-b border-border px-6 py-4">
          <div className="min-w-0">
            <div className="text-base font-semibold text-foreground">{title}</div>
            {subtitle && <div className="mt-0.5 text-xs text-muted-foreground">{subtitle}</div>}
          </div>
          <button
            onClick={onClose}
            className="grid h-8 w-8 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-muted"
            aria-label="Close"
          >
            <XCircle className="h-4 w-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && <div className="border-t border-border bg-surface-2 px-6 py-3">{footer}</div>}
      </aside>
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Dialog (centered modal)                                             */
/* ------------------------------------------------------------------ */

export function Dialog({
  open,
  onClose,
  title,
  children,
  footer,
  destructive,
  width = "md",
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  destructive?: boolean;
  width?: "sm" | "md" | "lg";
}) {
  useModalA11y(open, onClose);
  if (!open) return null;
  const w = { sm: "max-w-sm", md: "max-w-md", lg: "max-w-lg" }[width];
  return (
    <>
      <div
        className="fixed inset-0 z-40 bg-espresso/60 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden
      />
      <div
        className={`fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-surface shadow-[var(--shadow-lg)] ${w}`}
        role="dialog"
        aria-modal="true"
      >
        {destructive && (
          <div className="rounded-t-xl border-b border-danger/20 bg-danger-soft px-5 py-2 text-[11px] font-semibold uppercase tracking-wider text-danger">
            Sensitive action
          </div>
        )}
        <div className="px-5 pt-4 pb-2">
          <div className="text-base font-semibold text-foreground">{title}</div>
        </div>
        <div className="px-5 pb-4 text-sm text-muted-foreground">{children}</div>
        {footer && (
          <div className="flex items-center justify-end gap-2 border-t border-border bg-surface-2 px-5 py-3">
            {footer}
          </div>
        )}
      </div>
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Buttons                                                             */
/* ------------------------------------------------------------------ */

type BtnVariant = "primary" | "secondary" | "ghost" | "danger" | "outline";

export function Btn({
  variant = "secondary",
  size = "md",
  className = "",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: BtnVariant;
  size?: "sm" | "md";
}) {
  const v = {
    primary: "bg-primary text-primary-foreground hover:bg-primary-hover",
    secondary: "bg-surface border border-input hover:bg-muted",
    ghost: "hover:bg-muted",
    danger: "bg-danger text-danger-foreground hover:opacity-90",
    outline: "border border-danger text-danger hover:bg-danger-soft",
  }[variant];
  const s = size === "sm" ? "h-8 px-2.5 text-xs" : "h-9 px-3 text-sm";
  return (
    <button
      className={`inline-flex items-center justify-center gap-1.5 rounded-md font-medium transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 ${v} ${s} ${className}`}
      {...props}
    />
  );
}
