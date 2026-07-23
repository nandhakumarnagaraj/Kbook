import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import {
  Search,
  Filter,
  Calendar,
  Download,
  MoreHorizontal,
  RotateCcw,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Receipt,
} from "lucide-react";
import { AppShell } from "../proto/AppShell";
import {
  OrderStatusBadge,
  Badge,
  INR,
  EmptyState,
  ErrorState,
  Skeleton,
  StateSwitcher,
  Drawer,
  Dialog,
  Btn,
} from "../proto/primitives";
import { SAMPLE_ORDERS, type Order } from "../proto/data";

export const Route = createFileRoute("/proto/owner/orders")({
  head: () => ({
    meta: [
      { title: "Orders — Spice Garden · KhanaBook" },
      { name: "description", content: "Orders list, details drawer and refund dialog prototype." },
      { property: "og:title", content: "KhanaBook Orders" },
      { property: "og:description", content: "Design reference for order management + refunds." },
    ],
  }),
  component: OrdersProto,
});

type ListState = "ready" | "loading" | "empty" | "error";
type Sheet =
  | "none"
  | "drawer"
  | "refund_default"
  | "refund_confirm"
  | "refund_submitting"
  | "refund_success"
  | "refund_error"
  | "refund_duplicate";

function OrdersProto() {
  const [state, setState] = useState<ListState>("ready");
  const [sheet, setSheet] = useState<Sheet>("none");
  const [active, setActive] = useState<Order | null>(null);

  const openDrawer = (o: Order) => {
    setActive(o);
    setSheet("drawer");
  };

  return (
    <>
      <StateSwitcher
        value={state}
        onChange={setState}
        options={["ready", "loading", "empty", "error"] as const}
        position="top"
        label="List"
      />
      <StateSwitcher
        value={sheet}
        onChange={setSheet}
        options={
          [
            "none",
            "drawer",
            "refund_default",
            "refund_confirm",
            "refund_submitting",
            "refund_success",
            "refund_error",
            "refund_duplicate",
          ] as const
        }
        label="Sheet"
      />

      <AppShell
        role="OWNER"
        title="Orders"
        subtitle="184 orders today · ₹48,320 revenue"
        actions={
          <>
            <Btn>
              <Calendar className="h-4 w-4" /> Last 7 days
            </Btn>
            <Btn>
              <Download className="h-4 w-4" /> Export CSV
            </Btn>
          </>
        }
      >
        {/* Toolbar */}
        <div className="card-surface mb-4 flex flex-wrap items-center gap-2 p-3">
          <div className="relative flex-1 min-w-[220px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              placeholder="Search order #, item, phone…"
              className="h-9 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
            />
          </div>
          <Btn size="sm">
            <Filter className="h-3.5 w-3.5" /> Status: All
          </Btn>
          <Btn size="sm">Payment: All</Btn>
          <Btn size="sm">Sort: Newest</Btn>
        </div>

        {state === "ready" && <OrdersTable rows={SAMPLE_ORDERS} onOpen={openDrawer} />}
        {state === "loading" && <TableSkeleton />}
        {state === "empty" && (
          <EmptyState
            icon={Receipt}
            title="No orders match your filters"
            description="Try clearing filters or expanding the date range."
          />
        )}
        {state === "error" && (
          <ErrorState
            title="Couldn't load orders"
            description="Check your connection and retry."
            onRetry={() => setState("ready")}
          />
        )}
      </AppShell>

      {/* Drawer: order details */}
      <Drawer
        open={sheet === "drawer"}
        onClose={() => setSheet("none")}
        title={<span className="font-mono">{active?.id ?? "KB-2416"}</span>}
        subtitle={<>{active?.time ?? "13:24"} · UPI · Table 4</>}
        width="lg"
        footer={
          <div className="flex items-center justify-between">
            <div className="text-xs text-muted-foreground">
              Refunds are recorded on your ledger and reported to the payment gateway.
            </div>
            <div className="flex gap-2">
              <Btn onClick={() => setSheet("none")}>Close</Btn>
              <Btn variant="outline" onClick={() => setSheet("refund_default")}>
                <RotateCcw className="h-4 w-4" /> Issue refund
              </Btn>
            </div>
          </div>
        }
      >
        <OrderDetail order={active ?? SAMPLE_ORDERS[2]} />
      </Drawer>

      {/* Refund dialogs */}
      {sheet.startsWith("refund_") && (
        <RefundDialog sheet={sheet} setSheet={setSheet} order={active ?? SAMPLE_ORDERS[2]} />
      )}
    </>
  );
}

function OrdersTable({ rows, onOpen }: { rows: Order[]; onOpen: (o: Order) => void }) {
  return (
    <div className="card-surface overflow-hidden">
      {/* Desktop */}
      <div className="hidden md:block">
        <table className="w-full text-sm">
          <thead className="bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th className="px-4 py-3 font-medium">Order</th>
              <th className="px-4 py-3 font-medium">Time</th>
              <th className="px-4 py-3 font-medium">Items</th>
              <th className="px-4 py-3 font-medium">Payment</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium text-right">Amount</th>
              <th className="px-4 py-3 font-medium w-10"></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((o) => (
              <tr
                key={o.id}
                className={`cursor-pointer border-t border-border transition-colors hover:bg-surface-2 ${o.status === "refunded" ? "border-l-2 border-l-danger" : ""}`}
                onClick={() => onOpen(o)}
              >
                <td className="px-4 py-3 font-mono text-xs font-medium">{o.id}</td>
                <td className="px-4 py-3 text-muted-foreground tabular">{o.time}</td>
                <td className="px-4 py-3 max-w-sm truncate">{o.items}</td>
                <td className="px-4 py-3">
                  <Badge tone={o.mode === "Cash" ? "muted" : "info"} size="sm">
                    {o.mode}
                  </Badge>
                </td>
                <td className="px-4 py-3">
                  <OrderStatusBadge status={o.status} />
                </td>
                <td className="px-4 py-3 text-right font-medium">
                  <INR value={o.amount} />
                  {o.refunded && (
                    <div className="text-[11px] text-danger">
                      −<INR value={o.refunded} /> refunded
                    </div>
                  )}
                </td>
                <td className="px-4 py-3 text-muted-foreground">
                  <MoreHorizontal className="h-4 w-4" />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {/* Mobile cards */}
      <div className="md:hidden divide-y divide-border">
        {rows.map((o) => (
          <button
            key={o.id}
            onClick={() => onOpen(o)}
            className="block w-full p-4 text-left hover:bg-surface-2"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="font-mono text-xs text-muted-foreground">
                  {o.id} · {o.time}
                </div>
                <div className="mt-1 truncate text-sm font-medium">{o.items}</div>
              </div>
              <div className="shrink-0 text-right">
                <div className="font-medium">
                  <INR value={o.amount} />
                </div>
                {o.refunded && (
                  <div className="text-[11px] text-danger">
                    −<INR value={o.refunded} />
                  </div>
                )}
              </div>
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <OrderStatusBadge status={o.status} />
              <Badge tone={o.mode === "Cash" ? "muted" : "info"} size="sm">
                {o.mode}
              </Badge>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

function TableSkeleton() {
  return (
    <div className="card-surface p-4 space-y-3">
      {Array.from({ length: 6 }).map((_, i) => (
        <Skeleton key={i} className="h-10 w-full" />
      ))}
    </div>
  );
}

function OrderDetail({ order }: { order: Order }) {
  const lines = [
    { name: "Chicken Biryani", qty: 1, price: 380 },
    { name: "Raita", qty: 1, price: 60 },
    { name: "Gulab Jamun (2 pc)", qty: 1, price: 100 },
  ];
  const subtotal = lines.reduce((s, l) => s + l.qty * l.price, 0);
  const tax = Math.round(subtotal * 0.05);
  const total = order.amount;
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Meta label="Status">
          <OrderStatusBadge status={order.status} />
        </Meta>
        <Meta label="Payment">
          <Badge tone="info" size="sm">
            {order.mode}
          </Badge>
        </Meta>
        <Meta label="Table">4</Meta>
        <Meta label="Server">Priya N.</Meta>
      </div>

      <div>
        <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Bill items
        </div>
        <div className="card-surface overflow-hidden">
          <table className="w-full text-sm">
            <tbody>
              {lines.map((l, i) => (
                <tr key={i} className="border-t border-border first:border-0">
                  <td className="px-4 py-2.5">
                    <span className="text-muted-foreground">{l.qty}×</span> {l.name}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular">
                    <INR value={l.qty * l.price} />
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-surface-2 text-sm">
              <tr className="border-t border-border">
                <td className="px-4 py-2 text-muted-foreground">Subtotal</td>
                <td className="px-4 py-2 text-right tabular">
                  <INR value={subtotal} />
                </td>
              </tr>
              <tr>
                <td className="px-4 py-2 text-muted-foreground">GST (5%)</td>
                <td className="px-4 py-2 text-right tabular">
                  <INR value={tax} />
                </td>
              </tr>
              <tr className="border-t border-border">
                <td className="px-4 py-2 font-semibold">Total</td>
                <td className="px-4 py-2 text-right font-semibold tabular">
                  <INR value={total} />
                </td>
              </tr>
              {order.refunded && (
                <tr>
                  <td className="px-4 py-2 text-danger">Refunded</td>
                  <td className="px-4 py-2 text-right font-medium text-danger tabular">
                    −<INR value={order.refunded} />
                  </td>
                </tr>
              )}
            </tfoot>
          </table>
        </div>
      </div>

      <div>
        <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Payment
        </div>
        <div className="card-surface p-4 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Method</span>
            <span className="font-medium">UPI · GPay</span>
          </div>
          <div className="mt-2 flex justify-between">
            <span className="text-muted-foreground">Reference</span>
            <span className="font-mono text-xs">UPI-882194-2416</span>
          </div>
          <div className="mt-2 flex justify-between">
            <span className="text-muted-foreground">Captured</span>
            <span>13:24:52 IST</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function Meta({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </div>
      <div className="mt-1 text-sm text-foreground">{children}</div>
    </div>
  );
}

function RefundDialog({
  sheet,
  setSheet,
  order,
}: {
  sheet: Sheet;
  setSheet: (s: Sheet) => void;
  order: Order;
}) {
  const refundable = order.amount - (order.refunded ?? 0);
  const isSuccess = sheet === "refund_success";
  const isError = sheet === "refund_error";
  const isDup = sheet === "refund_duplicate";
  const isSubmitting = sheet === "refund_submitting";
  const isConfirm = sheet === "refund_confirm";

  return (
    <Dialog
      open
      onClose={() => setSheet("none")}
      destructive={!isSuccess}
      title={isSuccess ? "Refund issued" : "Issue refund"}
      width="md"
      footer={
        isSuccess ? (
          <Btn variant="primary" onClick={() => setSheet("none")}>
            Done
          </Btn>
        ) : (
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            {isConfirm ? (
              <Btn variant="danger" onClick={() => setSheet("refund_submitting")}>
                Confirm refund
              </Btn>
            ) : isSubmitting ? (
              <Btn variant="danger" disabled>
                <Loader2 className="h-4 w-4 animate-spin" /> Processing…
              </Btn>
            ) : (
              <Btn variant="danger" disabled={isDup} onClick={() => setSheet("refund_confirm")}>
                <RotateCcw className="h-4 w-4" /> Review refund
              </Btn>
            )}
          </>
        )
      }
    >
      {isSuccess && (
        <div className="flex items-start gap-3 pt-1">
          <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-success-soft text-success">
            <CheckCircle2 className="h-5 w-5" />
          </div>
          <div className="text-sm text-foreground">
            <div className="font-medium">₹200.00 refunded to UPI · GPay</div>
            <div className="mt-1 text-muted-foreground">
              Reference RFD-{order.id}-A · Recorded on ledger 13:44 IST.
            </div>
          </div>
        </div>
      )}

      {!isSuccess && (
        <>
          {isError && (
            <div className="mb-3 flex items-start gap-2 rounded-md border border-danger/25 bg-danger-soft p-3 text-sm text-danger">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <div className="font-medium">Refund could not be processed</div>
                <div className="mt-0.5 text-xs">
                  Gateway returned <span className="font-mono">ERR_PSP_TIMEOUT</span>. No amount was
                  charged. Try again in a few moments.
                </div>
              </div>
            </div>
          )}
          {isDup && (
            <div className="mb-3 flex items-start gap-2 rounded-md border border-warning/25 bg-warning-soft p-3 text-sm text-warning">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <div className="font-medium">Refund already in progress</div>
                <div className="mt-0.5 text-xs">
                  A refund submitted 4 seconds ago is still processing. Wait for it to complete
                  before issuing another.
                </div>
              </div>
            </div>
          )}

          <div className="rounded-md border border-border bg-surface-2 p-3 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Order</span>
              <span className="font-mono">{order.id}</span>
            </div>
            <div className="mt-1 flex justify-between">
              <span className="text-muted-foreground">Original amount</span>
              <span className="tabular">
                <INR value={order.amount} />
              </span>
            </div>
            {order.refunded ? (
              <div className="mt-1 flex justify-between">
                <span className="text-muted-foreground">Already refunded</span>
                <span className="tabular text-danger">
                  −<INR value={order.refunded} />
                </span>
              </div>
            ) : null}
            <div className="mt-1 flex justify-between border-t border-border pt-2">
              <span className="font-medium">Refundable</span>
              <span className="tabular font-semibold">
                <INR value={refundable} />
              </span>
            </div>
          </div>

          <div className="mt-4 space-y-3 text-sm text-foreground">
            <div>
              <label className="mb-1.5 block text-xs font-medium">Refund amount (INR)</label>
              <div className="relative">
                <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                  ₹
                </span>
                <input
                  defaultValue="200"
                  className="h-10 w-full rounded-md border border-input bg-surface pl-7 pr-3 text-sm tabular outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
                />
              </div>
              <div className="mt-1 text-xs text-muted-foreground">
                Max <INR value={refundable} /> · Full refund available.
              </div>
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-medium">
                Reason <span className="text-muted-foreground">(required, visible on ledger)</span>
              </label>
              <textarea
                defaultValue="Customer received wrong sweet dish."
                rows={2}
                className="w-full rounded-md border border-input bg-surface p-2 text-sm outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
              />
            </div>
            {isConfirm && (
              <div className="rounded-md border border-danger/25 bg-danger-soft p-3 text-xs text-danger">
                <div className="font-semibold">Please confirm</div>
                You are about to refund <span className="tabular font-semibold">₹200</span> to UPI ·
                GPay for order {order.id}. This action cannot be undone.
              </div>
            )}
          </div>
        </>
      )}
    </Dialog>
  );
}
