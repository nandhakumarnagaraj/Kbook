import { createFileRoute } from "@tanstack/react-router";
import { PageHeader } from "./business.dashboard";
import { Field, Select } from "./business.orders";

export const Route = createFileRoute("/business/staff")({
  head: () => ({
    meta: [
      { title: "Staff — KhanaBook Admin" },
      { name: "description", content: "Team directory with roles, status, and contact details." },
    ],
  }),
  component: StaffPage,
});

const staff = [
  { name: "Shop Admin", login: "admin-9150677849@kbook.iadv.cloud", role: "OWNER", contact: "-", status: "Inactive", updated: "21 Jul 2026, 5:49 pm" },
  { name: "NandhaKumar N", login: "nandhakumar2536@gmail.com", role: "OWNER", contact: "9150677849", status: "Active", updated: "20 Jul 2026, 1:45 pm" },
  { name: "Shop Staff", login: "shop-9150677849@kbook.iadv.cloud", role: "SHOP_ADMIN", contact: "-", status: "Active", updated: "17 Jul 2026, 6:34 pm" },
];

function StaffPage() {
  return (
    <>
      <PageHeader
        eyebrow="Access Review"
        title="Staff"
        subtitle="Team directory with better spacing for roles, status, and contact details."
        tabs={["Team Health", "Staff Directory"]}
      />
      <div className="px-4 md:px-8 py-6 space-y-4">
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">Check role coverage and inactive accounts without scanning cramped rows.</p>
          <div className="flex gap-2">
            <button className="px-3 py-2 rounded-lg text-xs font-semibold text-primary-foreground shadow-[var(--shadow-elevated)]" style={{ backgroundImage: "var(--gradient-primary)" }}>Add Staff</button>
            <button className="px-3 py-2 rounded-lg text-xs font-semibold border border-border bg-card hover:bg-accent">Refresh</button>
          </div>
        </div>

        <div className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] p-4">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
            <Field label="Search"><input placeholder="Search by name, login, email, or phone" className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-primary" /></Field>
            <Field label="Role"><Select options={["All roles"]} /></Field>
            <Field label="Status"><Select options={["All statuses"]} /></Field>
            <Field label="Rows"><Select options={["10"]} /></Field>
          </div>
          <div className="flex items-center justify-between mt-3 pt-3 border-t border-border">
            <span className="text-xs text-muted-foreground">3 of 3 staff members</span>
            <button className="text-xs font-semibold text-primary hover:underline">Clear filters</button>
          </div>
        </div>

        <div className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted/60 text-muted-foreground text-xs uppercase tracking-wide">
              <tr>
                {["Name", "Login ID", "Role", "Contact", "Status", "Updated", "Actions"].map((h) => (
                  <th key={h} className="text-left font-semibold px-4 py-3">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {staff.map((s, i) => (
                <tr key={i} className={`border-t border-border ${i % 2 ? "bg-surface/40" : ""} hover:bg-accent/40`}>
                  <td className="px-4 py-3 font-semibold">{s.name}</td>
                  <td className="px-4 py-3 text-xs text-muted-foreground">{s.login}</td>
                  <td className="px-4 py-3">
                    <span className="inline-flex px-2 py-0.5 rounded-full border text-[11px] font-bold tracking-wide bg-primary/10 text-primary border-primary/20">{s.role}</span>
                  </td>
                  <td className="px-4 py-3 text-xs">{s.contact}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex px-2 py-0.5 rounded-full border text-[11px] font-semibold ${
                      s.status === "Active" ? "bg-success/10 text-success border-success/20" : "bg-muted text-muted-foreground border-border"
                    }`}>{s.status}</span>
                  </td>
                  <td className="px-4 py-3 text-xs text-muted-foreground">{s.updated}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-3">
                      <button className="text-xs font-semibold text-primary hover:underline">Edit</button>
                      <button className="text-xs font-semibold text-destructive hover:underline">Deactivate</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}
