import { createFileRoute } from "@tanstack/react-router";
import { PageHeader } from "./business.dashboard";

export const Route = createFileRoute("/business/terminals")({
  head: () => ({
    meta: [
      { title: "Devices — KhanaBook Admin" },
      { name: "description", content: "Manage POS devices, approve new device requests, and recover terminals." },
    ],
  }),
  component: TerminalsPage,
});

const terminals = [
  { name: "Terminal 1", series: "A1", device: "d86f60ba-24db-47b8-81ed-0037aadff757", status: "INACTIVE", active: "No", updated: "18 Jul 2026, 6:21 pm" },
  { name: "Terminal 2", series: "A2", device: "30110b37-39fe-44fa-9625-aef9875dec58", status: "INACTIVE", active: "No", updated: "18 Jul 2026, 3:14 pm" },
  { name: "Terminal 3", series: "A3", device: "88289af0-aae7-4d33-a7e1-a5ab9c88f150", status: "INACTIVE", active: "No", updated: "18 Jul 2026, 3:14 pm" },
  { name: "Terminal 4", series: "A4", device: "4852c0f7-42d6-4c78-b9d9-e0020ee8c350", status: "INACTIVE", active: "No", updated: "18 Jul 2026, 3:14 pm" },
  { name: "Terminal 5", series: "A5", device: "63ee4ede-39dd-4c55-bdcf-4b26049b484c", status: "INACTIVE", active: "No", updated: "18 Jul 2026, 3:14 pm" },
  { name: "Terminal 6", series: "A6", device: "6fea34a6-9d63-4294-aa65-9464acce6e5f", status: "INACTIVE", active: "No", updated: "18 Jul 2026, 3:14 pm" },
  { name: "Terminal 7", series: "A7", device: "8c33611d-8413-4338-9396-bc6319072147", status: "INACTIVE", active: "No", updated: "18 Jul 2026, 3:14 pm" },
  { name: "Terminal B", series: "B", device: "7ecb0338-99e2-4fb1-91c8-0356b67dc08f", status: "ACTIVE", active: "Yes", updated: "14 Jul 2026, 2:52 pm" },
  { name: "Terminal C", series: "C", device: "550f95a3-bade-4446-bd1b-8897b9ad8fc7", status: "ACTIVE", active: "Yes", updated: "14 Jul 2026, 2:54 pm" },
  { name: "Terminal D", series: "D", device: "e7b7f008-c269-4e96-8f3b-93ee58ae933e", status: "ACTIVE", active: "Yes", updated: "16 Jul 2026, 4:54 pm" },
  { name: "Terminal A", series: "A", device: "a4de7463-487e-4d34-9b86-7817674dc0c6", status: "ACTIVE", active: "Yes", updated: "18 Jul 2026, 3:15 pm" },
  { name: "Terminal E", series: "E", device: "423967d8-a268-4030-8350-a73bed30016d", status: "ACTIVE", active: "Yes", updated: "18 Jul 2026, 6:37 pm" },
];

function TerminalsPage() {
  return (
    <>
      <PageHeader
        eyebrow="Fleet"
        title="Devices & Terminals"
        subtitle="Manage POS devices, approve new device requests, and recover terminals."
      />
      <div className="px-4 md:px-8 py-6 space-y-5">
        <div className="flex flex-wrap gap-3">
          <div className="rounded-lg bg-card border border-border px-4 py-3 flex items-center gap-3">
            <span className="h-2 w-2 rounded-full bg-success" />
            <span className="text-sm"><span className="font-bold">12</span> <span className="text-muted-foreground">Registered</span></span>
          </div>
          <div className="rounded-lg bg-card border border-border px-4 py-3 flex items-center gap-3">
            <span className="h-2 w-2 rounded-full bg-warning" />
            <span className="text-sm"><span className="font-bold">0</span> <span className="text-muted-foreground">Pending</span></span>
          </div>
        </div>

        <div className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] overflow-hidden">
          <div className="flex items-center justify-between px-5 py-4 border-b border-border">
            <div>
              <h2 className="font-display font-semibold">Registered Terminals</h2>
              <p className="text-xs text-muted-foreground">Active and deactivated POS devices for this shop.</p>
            </div>
            <button className="px-3 py-2 rounded-lg text-xs font-semibold border border-border bg-card hover:bg-accent">Refresh</button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-muted/60 text-muted-foreground text-xs uppercase tracking-wide">
                <tr>
                  {["Name", "Series", "Device", "Status", "Active", "Updated", "Action"].map((h) => (
                    <th key={h} className="text-left font-semibold px-4 py-3">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {terminals.map((t, i) => (
                  <tr key={i} className={`border-t border-border ${i % 2 ? "bg-surface/40" : ""} hover:bg-accent/40`}>
                    <td className="px-4 py-3 font-semibold">{t.name}</td>
                    <td className="px-4 py-3 text-xs font-mono">{t.series}</td>
                    <td className="px-4 py-3 text-[11px] font-mono text-muted-foreground">{t.device}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex px-2 py-0.5 rounded-full border text-[11px] font-bold tracking-wide ${
                        t.status === "ACTIVE" ? "bg-success/10 text-success border-success/20" : "bg-muted text-muted-foreground border-border"
                      }`}>{t.status}</span>
                    </td>
                    <td className="px-4 py-3 text-xs">{t.active}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{t.updated}</td>
                    <td className="px-4 py-3">
                      <div className="flex gap-3">
                        <button className="text-xs font-semibold text-primary hover:underline">Rename</button>
                        <button className="text-xs font-semibold text-primary hover:underline">Recover</button>
                        <button className={`text-xs font-semibold hover:underline ${t.status === "ACTIVE" ? "text-destructive" : "text-success"}`}>
                          {t.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)]">
          <div className="flex items-center justify-between px-5 py-4 border-b border-border">
            <div>
              <h2 className="font-display font-semibold">Device Requests</h2>
              <p className="text-xs text-muted-foreground">New devices requesting access to this shop.</p>
            </div>
            <div className="flex rounded-lg border border-border bg-surface p-0.5 text-xs">
              {["Pending", "All"].map((t, i) => (
                <button key={t} className={`px-3 py-1.5 rounded-md font-medium ${i === 0 ? "bg-primary text-primary-foreground" : "text-muted-foreground"}`}>{t}</button>
              ))}
            </div>
          </div>
          <div className="p-10 text-center">
            <div className="text-4xl">🔔</div>
            <div className="mt-3 font-semibold">No device requests</div>
            <div className="text-xs text-muted-foreground mt-1">Pending and processed device access requests will appear here.</div>
          </div>
        </div>
      </div>
    </>
  );
}
