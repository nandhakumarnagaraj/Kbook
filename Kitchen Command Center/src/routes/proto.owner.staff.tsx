import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { Plus, Search, Copy, CheckCircle2, KeyRound, UserX, ShieldAlert } from "lucide-react";
import { AppShell } from "../proto/AppShell";
import { Badge, StateSwitcher, Drawer, Dialog, Btn } from "../proto/primitives";
import { SAMPLE_STAFF, type Staff, type StaffRole } from "../proto/data";

export const Route = createFileRoute("/proto/owner/staff")({
  head: () => ({
    meta: [
      { title: "Staff — Spice Garden · KhanaBook" },
      { name: "description", content: "Staff directory and add/edit prototype." },
      { property: "og:title", content: "KhanaBook Staff" },
      { property: "og:description", content: "Design reference for staff management." },
    ],
  }),
  component: StaffProto,
});

type Sheet = "none" | "add" | "edit" | "temp_password" | "temp_password_copied" | "deactivate";

const roleTone: Record<StaffRole, "info" | "success" | "warning" | "muted"> = {
  MANAGER: "info",
  CASHIER: "success",
  SERVER: "warning",
  KITCHEN: "muted",
};

function initials(name: string) {
  return name
    .split(" ")
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

function StaffProto() {
  const [sheet, setSheet] = useState<Sheet>("none");
  const [active, setActive] = useState<Staff | null>(null);
  const [tab, setTab] = useState<"active" | "inactive">("active");

  const rows = SAMPLE_STAFF.filter((s) => (tab === "active" ? s.active : !s.active));

  return (
    <>
      <StateSwitcher
        value={sheet}
        onChange={setSheet}
        options={
          ["none", "add", "edit", "temp_password", "temp_password_copied", "deactivate"] as const
        }
        label="Sheet"
      />

      <AppShell
        role="OWNER"
        title="Staff"
        subtitle={`${SAMPLE_STAFF.filter((s) => s.active).length} active · ${SAMPLE_STAFF.filter((s) => !s.active).length} inactive`}
        actions={
          <Btn variant="primary" onClick={() => setSheet("add")}>
            <Plus className="h-4 w-4" /> Add staff
          </Btn>
        }
      >
        <div className="mb-4 flex items-center gap-2">
          <div className="inline-flex rounded-md border border-input bg-surface p-0.5">
            {(["active", "inactive"] as const).map((t) => (
              <button
                key={t}
                onClick={() => setTab(t)}
                className={`rounded px-3 py-1 text-xs font-medium capitalize ${tab === t ? "bg-espresso text-espresso-foreground" : "text-muted-foreground"}`}
              >
                {t}
              </button>
            ))}
          </div>
          <div className="relative flex-1 max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              placeholder="Search name or role…"
              className="h-9 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
            />
          </div>
        </div>

        {/* Desktop */}
        <div className="hidden md:block card-surface overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Person</th>
                <th className="px-4 py-3 font-medium">Role</th>
                <th className="px-4 py-3 font-medium">Contact</th>
                <th className="px-4 py-3 font-medium">Joined</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium text-right w-40">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.id} className="border-t border-border hover:bg-surface-2">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary-soft text-espresso text-xs font-semibold">
                        {initials(s.name)}
                      </div>
                      <div>
                        <div className="font-medium">{s.name}</div>
                        <div className="text-xs text-muted-foreground">{s.email}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={roleTone[s.role]} size="sm">
                      {s.role}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground tabular text-xs">{s.phone}</td>
                  <td className="px-4 py-3 text-muted-foreground text-xs">{s.joined}</td>
                  <td className="px-4 py-3">
                    <Badge tone={s.active ? "success" : "muted"} size="sm">
                      {s.active ? "Active" : "Inactive"}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="inline-flex gap-1">
                      <button
                        onClick={() => {
                          setActive(s);
                          setSheet("edit");
                        }}
                        className="rounded px-2 py-1 text-xs hover:bg-muted"
                      >
                        Edit
                      </button>
                      {s.active && (
                        <button
                          onClick={() => {
                            setActive(s);
                            setSheet("deactivate");
                          }}
                          className="rounded px-2 py-1 text-xs text-danger hover:bg-danger-soft"
                        >
                          Deactivate
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Mobile cards */}
        <div className="md:hidden card-surface divide-y divide-border">
          {rows.map((s) => (
            <div key={s.id} className="p-4">
              <div className="flex items-start gap-3">
                <div className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-primary-soft text-espresso text-sm font-semibold">
                  {initials(s.name)}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <div className="truncate text-sm font-medium">{s.name}</div>
                    <Badge tone={s.active ? "success" : "muted"} size="sm">
                      {s.active ? "Active" : "Inactive"}
                    </Badge>
                  </div>
                  <div className="text-xs text-muted-foreground">{s.email}</div>
                  <div className="mt-1 flex items-center gap-2">
                    <Badge tone={roleTone[s.role]} size="sm">
                      {s.role}
                    </Badge>
                    <span className="text-xs text-muted-foreground tabular">{s.phone}</span>
                  </div>
                </div>
              </div>
              <div className="mt-3 flex justify-end gap-1">
                <button
                  onClick={() => {
                    setActive(s);
                    setSheet("edit");
                  }}
                  className="rounded px-2 py-1 text-xs hover:bg-muted"
                >
                  Edit
                </button>
                {s.active && (
                  <button
                    onClick={() => {
                      setActive(s);
                      setSheet("deactivate");
                    }}
                    className="rounded px-2 py-1 text-xs text-danger hover:bg-danger-soft"
                  >
                    Deactivate
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      </AppShell>

      {/* Add/Edit drawer */}
      <Drawer
        open={sheet === "add" || sheet === "edit"}
        onClose={() => setSheet("none")}
        title={sheet === "add" ? "Add staff member" : `Edit ${active?.name}`}
        subtitle={
          sheet === "add" ? "A temporary password will be generated after creation." : undefined
        }
        footer={
          <div className="flex justify-end gap-2">
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn
              variant="primary"
              onClick={() => setSheet(sheet === "add" ? "temp_password" : "none")}
            >
              {sheet === "add" ? "Create staff" : "Save"}
            </Btn>
          </div>
        }
      >
        <StaffForm active={active} isEdit={sheet === "edit"} />
      </Drawer>

      {/* Temp password dialog */}
      <Dialog
        open={sheet === "temp_password" || sheet === "temp_password_copied"}
        onClose={() => setSheet("none")}
        title="Temporary password created"
        width="md"
        footer={
          <Btn variant="primary" onClick={() => setSheet("none")}>
            I've shared it
          </Btn>
        }
      >
        <div className="space-y-3">
          <div className="flex items-start gap-2 rounded-md border border-warning/25 bg-warning-soft p-3 text-xs text-warning">
            <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
            <span>
              This password is shown once. Share it privately with the staff member. They must
              change it on first login.
            </span>
          </div>
          <div>
            <div className="mb-1 text-xs font-medium">Login ID</div>
            <div className="rounded-md border border-border bg-surface-2 px-3 py-2 font-mono text-sm">
              rahul@spicegarden.in
            </div>
          </div>
          <div>
            <div className="mb-1 text-xs font-medium">Temporary password</div>
            <div className="flex items-stretch gap-2">
              <div className="flex-1 rounded-md border border-border bg-surface-2 px-3 py-2 font-mono text-base tabular">
                Kh4-mango-42Tf
              </div>
              <Btn variant="primary" onClick={() => setSheet("temp_password_copied")}>
                {sheet === "temp_password_copied" ? (
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
          </div>
        </div>
      </Dialog>

      {/* Deactivate confirmation */}
      <Dialog
        open={sheet === "deactivate"}
        onClose={() => setSheet("none")}
        destructive
        title={`Deactivate ${active?.name ?? "staff"}?`}
        footer={
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="danger" onClick={() => setSheet("none")}>
              <UserX className="h-4 w-4" /> Deactivate
            </Btn>
          </>
        }
      >
        This person will no longer be able to sign in. Their historical activity (orders taken,
        bills, refunds) is preserved. You can reactivate them at any time.
      </Dialog>
    </>
  );
}

function StaffForm({ active, isEdit }: { active: Staff | null; isEdit: boolean }) {
  const s =
    isEdit && active ? active : { name: "", email: "", phone: "", role: "SERVER" as StaffRole };
  return (
    <div className="space-y-4 text-sm">
      <div>
        <label className="mb-1.5 block text-xs font-medium">Full name</label>
        <input
          defaultValue={s.name}
          className="h-10 w-full rounded-md border border-input bg-surface px-3"
        />
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label className="mb-1.5 block text-xs font-medium">Email</label>
          <input
            defaultValue={s.email}
            className="h-10 w-full rounded-md border border-input bg-surface px-3"
          />
        </div>
        <div>
          <label className="mb-1.5 block text-xs font-medium">Phone</label>
          <input
            defaultValue={s.phone}
            placeholder="+91 …"
            className="h-10 w-full rounded-md border border-input bg-surface px-3 tabular"
          />
        </div>
      </div>
      <div>
        <label className="mb-1.5 block text-xs font-medium">Role</label>
        <div className="flex flex-wrap gap-2">
          {(["MANAGER", "CASHIER", "SERVER", "KITCHEN"] as const).map((r) => (
            <label
              key={r}
              className={`flex items-center gap-2 rounded-md border px-3 py-2 cursor-pointer ${s.role === r ? "border-saffron bg-primary-soft" : "border-input bg-surface"}`}
            >
              <input type="radio" name="role" defaultChecked={s.role === r} className="sr-only" />
              <Badge tone={roleTone[r]} size="sm">
                {r}
              </Badge>
            </label>
          ))}
        </div>
      </div>
      {!isEdit && (
        <div className="flex items-start gap-2 rounded-md bg-muted p-3 text-xs text-muted-foreground">
          <KeyRound className="mt-0.5 h-3.5 w-3.5" />
          After creation, KhanaBook will show a one-time password. Share it privately with the staff
          member.
        </div>
      )}
    </div>
  );
}
