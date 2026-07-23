import { useState } from "react";
import {
  Check,
  X,
  Copy,
  ShieldAlert,
  MonitorSmartphone,
  AlertTriangle,
  RefreshCw,
  CheckCircle2,
} from "lucide-react";
import { AppShell } from "./AppShell";
import { DeviceStatusBadge, StateSwitcher, Dialog, Btn, Badge } from "./primitives";
import { SAMPLE_TERMINALS, SAMPLE_ACTIVATION_REQUESTS, type Terminal } from "./data";

type Tab = "fleet" | "requests" | "recovery";
type Sheet =
  | "none"
  | "rename"
  | "deactivate"
  | "reactivate"
  | "reject"
  | "approve_limit"
  | "recovery_input"
  | "recovery_token"
  | "recovery_copied";

export function TerminalsScreen({ role }: { role: "OWNER" | "SHOP_ADMIN" }) {
  const [tab, setTab] = useState<Tab>("fleet");
  const [sheet, setSheet] = useState<Sheet>("none");
  const [active, setActive] = useState<Terminal | null>(null);

  const activeCount = SAMPLE_TERMINALS.filter((t) => t.status === "active").length;
  const limit = 5;

  return (
    <>
      <StateSwitcher
        value={sheet}
        onChange={setSheet}
        options={
          [
            "none",
            "rename",
            "deactivate",
            "reactivate",
            "reject",
            "approve_limit",
            "recovery_input",
            "recovery_token",
            "recovery_copied",
          ] as const
        }
        label="Sheet"
      />

      <AppShell
        role={role}
        title="Devices"
        subtitle={`${activeCount} of ${limit} terminals active · ${SAMPLE_ACTIVATION_REQUESTS.length} pending requests`}
        actions={
          <Btn variant="primary" onClick={() => setSheet("recovery_input")}>
            <ShieldAlert className="h-4 w-4" /> Recover terminal
          </Btn>
        }
      >
        <div className="mb-4 inline-flex rounded-md border border-input bg-surface p-0.5">
          {(["fleet", "requests", "recovery"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`rounded px-3 py-1 text-xs font-medium capitalize ${tab === t ? "bg-espresso text-espresso-foreground" : "text-muted-foreground"}`}
            >
              {t === "fleet"
                ? "Fleet"
                : t === "requests"
                  ? `Requests · ${SAMPLE_ACTIVATION_REQUESTS.length}`
                  : "Recovery"}
            </button>
          ))}
        </div>

        {activeCount >= limit && tab === "requests" && (
          <div className="mb-4 flex items-start gap-2 rounded-md border border-warning/25 bg-warning-soft p-3 text-sm text-warning">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <div className="font-medium">Terminal limit reached</div>
              <div className="mt-0.5 text-xs">
                You have {activeCount}/{limit} active terminals. Deactivate one before approving a
                new request, or contact KhanaBook to raise your limit.
              </div>
            </div>
          </div>
        )}

        {tab === "fleet" && (
          <>
            {/* Desktop */}
            <div className="hidden md:block card-surface overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3 font-medium">Terminal</th>
                    <th className="px-4 py-3 font-medium">Device ID</th>
                    <th className="px-4 py-3 font-medium">Model</th>
                    <th className="px-4 py-3 font-medium">Last seen</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium text-right w-52">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {SAMPLE_TERMINALS.map((t) => (
                    <tr
                      key={t.id}
                      className={`border-t border-border hover:bg-surface-2 ${t.status === "recovery" ? "border-l-2 border-l-danger" : ""}`}
                    >
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <MonitorSmartphone className="h-4 w-4 text-muted-foreground" />
                          <span className="font-medium">{t.name}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                        {t.deviceId}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground text-xs">{t.model}</td>
                      <td className="px-4 py-3 text-muted-foreground text-xs tabular">
                        {t.lastSeen}
                      </td>
                      <td className="px-4 py-3">
                        <DeviceStatusBadge status={t.status} />
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="inline-flex gap-1">
                          <button
                            onClick={() => {
                              setActive(t);
                              setSheet("rename");
                            }}
                            className="rounded px-2 py-1 text-xs hover:bg-muted"
                          >
                            Rename
                          </button>
                          {t.status === "active" ? (
                            <button
                              onClick={() => {
                                setActive(t);
                                setSheet("deactivate");
                              }}
                              className="rounded px-2 py-1 text-xs text-danger hover:bg-danger-soft"
                            >
                              Deactivate
                            </button>
                          ) : t.status === "inactive" || t.status === "deactivated" ? (
                            <button
                              onClick={() => {
                                setActive(t);
                                setSheet("reactivate");
                              }}
                              className="rounded px-2 py-1 text-xs hover:bg-muted"
                            >
                              Reactivate
                            </button>
                          ) : t.status === "recovery" ? (
                            <button
                              onClick={() => {
                                setActive(t);
                                setSheet("recovery_input");
                              }}
                              className="rounded px-2 py-1 text-xs text-danger hover:bg-danger-soft"
                            >
                              Recover
                            </button>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {/* Mobile cards */}
            <div className="md:hidden card-surface divide-y divide-border">
              {SAMPLE_TERMINALS.map((t) => (
                <div key={t.id} className="p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <MonitorSmartphone className="h-4 w-4 text-muted-foreground" />
                        <span className="truncate font-medium">{t.name}</span>
                      </div>
                      <div className="mt-1 font-mono text-[11px] text-muted-foreground">
                        {t.deviceId}
                      </div>
                      <div className="text-[11px] text-muted-foreground">
                        {t.model} · seen {t.lastSeen}
                      </div>
                    </div>
                    <DeviceStatusBadge status={t.status} />
                  </div>
                </div>
              ))}
            </div>
          </>
        )}

        {tab === "requests" && (
          <div className="card-surface overflow-hidden">
            {SAMPLE_ACTIVATION_REQUESTS.length === 0 ? (
              <div className="p-8 text-center text-sm text-muted-foreground">
                No pending activation requests.
              </div>
            ) : (
              <div className="divide-y divide-border">
                {SAMPLE_ACTIVATION_REQUESTS.map((r) => (
                  <div key={r.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <MonitorSmartphone className="h-4 w-4 text-muted-foreground" />
                        <span className="font-medium">{r.requestedName}</span>
                        <Badge tone="warning" size="sm">
                          Pending approval
                        </Badge>
                      </div>
                      <div className="mt-1 font-mono text-[11px] text-muted-foreground">
                        {r.deviceId} · {r.model}
                      </div>
                      <div className="text-[11px] text-muted-foreground">
                        Requested {r.requestedAt}
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <Btn onClick={() => setSheet("reject")}>
                        <X className="h-4 w-4" /> Reject
                      </Btn>
                      <Btn
                        variant="primary"
                        onClick={() => setSheet(activeCount >= limit ? "approve_limit" : "none")}
                      >
                        <Check className="h-4 w-4" /> Approve
                      </Btn>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {tab === "recovery" && (
          <div className="card-surface p-6">
            <div className="flex items-start gap-3">
              <div className="grid h-10 w-10 shrink-0 place-items-center rounded-md bg-danger-soft text-danger">
                <ShieldAlert className="h-5 w-5" />
              </div>
              <div>
                <h3 className="text-base font-semibold">Recover a lost or wiped terminal</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  Enter the device ID printed on the receipt sticker under the terminal. KhanaBook
                  will issue a one-time recovery token you enter on the device to re-associate it
                  with your business.
                </p>
                <Btn variant="primary" className="mt-4" onClick={() => setSheet("recovery_input")}>
                  <ShieldAlert className="h-4 w-4" /> Start recovery
                </Btn>
              </div>
            </div>
          </div>
        )}
      </AppShell>

      {/* Rename dialog */}
      <Dialog
        open={sheet === "rename"}
        onClose={() => setSheet("none")}
        title="Rename terminal"
        footer={
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="primary" onClick={() => setSheet("none")}>
              Save name
            </Btn>
          </>
        }
      >
        <label className="mb-1.5 block text-xs font-medium text-foreground">Display name</label>
        <input
          defaultValue={active?.name ?? "Counter POS"}
          className="h-10 w-full rounded-md border border-input bg-surface px-3 text-sm"
        />
        <div className="mt-2 text-xs text-muted-foreground">
          Shown on receipts, order tickets and the device switcher.
        </div>
      </Dialog>

      {/* Deactivate confirmation */}
      <Dialog
        open={sheet === "deactivate"}
        onClose={() => setSheet("none")}
        destructive
        title={`Deactivate "${active?.name ?? "terminal"}"?`}
        footer={
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="danger" onClick={() => setSheet("none")}>
              Deactivate
            </Btn>
          </>
        }
      >
        The terminal will be signed out immediately and can no longer accept orders until you
        reactivate it. Any open bills on the device will remain and can be closed from another
        terminal.
      </Dialog>

      {/* Reactivate confirm */}
      <Dialog
        open={sheet === "reactivate"}
        onClose={() => setSheet("none")}
        title={`Reactivate "${active?.name ?? "terminal"}"?`}
        footer={
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="primary" onClick={() => setSheet("none")}>
              Reactivate
            </Btn>
          </>
        }
      >
        This will count against your {activeCount + 1}/{limit} terminal limit. The device will need
        to sign in again after reactivation.
      </Dialog>

      {/* Reject dialog */}
      <Dialog
        open={sheet === "reject"}
        onClose={() => setSheet("none")}
        destructive
        title="Reject activation request?"
        footer={
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="danger" onClick={() => setSheet("none")}>
              Reject request
            </Btn>
          </>
        }
      >
        <label className="mb-1.5 block text-xs font-medium text-foreground">
          Reason (visible to whoever requested activation)
        </label>
        <textarea
          rows={3}
          defaultValue="Unrecognised device — please contact the restaurant owner."
          className="w-full rounded-md border border-input bg-surface p-2 text-sm"
        />
      </Dialog>

      {/* Approve blocked by limit */}
      <Dialog
        open={sheet === "approve_limit"}
        onClose={() => setSheet("none")}
        title="Can't approve — terminal limit reached"
        footer={
          <Btn variant="primary" onClick={() => setSheet("none")}>
            Got it
          </Btn>
        }
      >
        You have {activeCount}/{limit} active terminals. Deactivate one from the Fleet tab first,
        then re-approve this request.
      </Dialog>

      {/* Recovery input */}
      <Dialog
        open={sheet === "recovery_input"}
        onClose={() => setSheet("none")}
        title="Recover terminal"
        footer={
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="primary" onClick={() => setSheet("recovery_token")}>
              Generate recovery token
            </Btn>
          </>
        }
      >
        <label className="mb-1.5 block text-xs font-medium text-foreground">Device ID</label>
        <input
          defaultValue="KB-DEV-88F3A24"
          className="h-10 w-full rounded-md border border-input bg-surface px-3 font-mono text-sm"
        />
        <div className="mt-2 text-xs text-muted-foreground">
          Look for the sticker under the terminal or on its packaging.
        </div>
      </Dialog>

      {/* Recovery token */}
      <Dialog
        open={sheet === "recovery_token" || sheet === "recovery_copied"}
        onClose={() => setSheet("none")}
        destructive
        title="Recovery token"
        width="md"
        footer={
          <Btn variant="primary" onClick={() => setSheet("none")}>
            I've entered it on the device
          </Btn>
        }
      >
        <div className="flex items-start gap-2 rounded-md border border-danger/25 bg-danger-soft p-3 text-xs text-danger">
          <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
          <div>
            <div className="font-medium">Anyone with this token can re-associate this device.</div>
            Enter it on the terminal within 10 minutes. Do not share it over email or WhatsApp.
          </div>
        </div>
        <div className="mt-4">
          <div className="mb-1 text-xs font-medium text-foreground">Device ID</div>
          <div className="rounded-md border border-border bg-surface-2 px-3 py-2 font-mono text-sm">
            KB-DEV-88F3A24
          </div>
        </div>
        <div className="mt-3">
          <div className="mb-1 text-xs font-medium text-foreground">One-time recovery token</div>
          <div className="flex items-stretch gap-2">
            <div className="flex-1 rounded-md border border-border bg-surface-2 px-3 py-2 font-mono text-lg tabular tracking-wider">
              R3C-88F3-A24-9K2X
            </div>
            <Btn variant="primary" onClick={() => setSheet("recovery_copied")}>
              {sheet === "recovery_copied" ? (
                <>
                  <CheckCircle2 className="h-4 w-4" /> Copied
                </>
              ) : (
                <>
                  <Copy className="h-4 w-4" /> Copy
                </>
              )}
            </Btn>
          </div>
          <div className="mt-2 flex items-center gap-1 text-xs text-muted-foreground">
            <RefreshCw className="h-3 w-3" /> Expires in 10:00
          </div>
        </div>
      </Dialog>
    </>
  );
}
