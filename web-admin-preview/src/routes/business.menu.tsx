import { createFileRoute } from "@tanstack/react-router";
import { PageHeader } from "./business.dashboard";
import { Field, Select } from "./business.orders";

export const Route = createFileRoute("/business/menu")({
  head: () => ({
    meta: [
      { title: "Menu — KhanaBook Admin" },
      { name: "description", content: "Current business menu with pricing and availability." },
    ],
  }),
  component: MenuPage,
});

const items = [
  { name: "Bismi Spl Fish Biriyani", cat: "Biriyani", type: "non-veg", price: "₹290.00", variants: 0, stock: "OUT_OF_STOCK", updated: "21 Jul 2026, 11:31 pm" },
  { name: "Bismi Spl Mutton Biriyani", cat: "Biriyani", type: "non-veg", price: "₹270.00", variants: 0, stock: "OUT_OF_STOCK", updated: "18 Jul 2026, 3:44 pm" },
  { name: "Bismi Spl Prawn Biriyani", cat: "Biriyani", type: "non-veg", price: "₹280.00", variants: 0, stock: "OUT_OF_STOCK", updated: "18 Jul 2026, 3:44 pm" },
  { name: "Fish Biriyani", cat: "Biriyani", type: "non-veg", price: "₹230.00", variants: 0, stock: "OUT_OF_STOCK", updated: "17 Jul 2026, 2:03 pm" },
  { name: "Bismi Spl Chicken Biriyani", cat: "Biriyani", type: "non-veg", price: "₹230.00", variants: 0, stock: "OUT_OF_STOCK", updated: "17 Jul 2026, 2:03 pm" },
  { name: "Chicken Biriyani", cat: "Biriyani", type: "non-veg", price: "₹170.00", variants: 0, stock: "OUT_OF_STOCK", updated: "17 Jul 2026, 12:04 pm" },
  { name: "Mutton Biriyani", cat: "Biriyani", type: "non-veg", price: "₹200.00", variants: 0, stock: "OUT_OF_STOCK", updated: "16 Jul 2026, 5:06 pm" },
  { name: "Bismi Fish Tikka", cat: "Starter", type: "non-veg", price: "₹320.00", variants: 0, stock: "OUT_OF_STOCK", updated: "13 Jul 2026, 6:11 pm" },
  { name: "Prawn Biriyani", cat: "Biriyani", type: "non-veg", price: "₹260.00", variants: 0, stock: "OUT_OF_STOCK", updated: "9 Jul 2026, 5:49 pm" },
  { name: "Bismi Tandoori Chicken", cat: "Starter", type: "non-veg", price: "₹400.00", variants: 2, stock: "OUT_OF_STOCK", updated: "9 Jul 2026, 5:49 pm" },
];

function MenuPage() {
  return (
    <>
      <PageHeader
        eyebrow="Catalog Review"
        title="Menu"
        subtitle="Current business menu with cleaner alignment for descriptions, pricing, and availability status."
        tabs={["Stock Visibility", "OCR Import", "Menu Snapshot"]}
      />

      <div className="px-4 md:px-8 py-6 space-y-4">
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">Use this list to spot missing descriptions, low stock, and stale updates.</p>
          <div className="flex gap-2">
            <button className="px-3 py-2 rounded-lg text-xs font-semibold text-primary-foreground shadow-[var(--shadow-elevated)]" style={{ backgroundImage: "var(--gradient-primary)" }}>+ Add Item</button>
            <button className="px-3 py-2 rounded-lg text-xs font-semibold border border-border bg-card hover:bg-accent">Refresh</button>
          </div>
        </div>

        <div className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] p-4">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
            <Field label="Search"><input placeholder="Search by item, category, or description" className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-primary" /></Field>
            <Field label="Stock"><Select options={["All stock states"]} /></Field>
            <Field label="Availability"><Select options={["All items"]} /></Field>
            <Field label="Rows"><Select options={["10"]} /></Field>
          </div>
          <div className="flex items-center justify-between mt-3 pt-3 border-t border-border">
            <span className="text-xs text-muted-foreground">11 of 11 menu items</span>
            <button className="text-xs font-semibold text-primary hover:underline">Clear filters</button>
          </div>
        </div>

        <div className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-muted/60 text-muted-foreground text-xs uppercase tracking-wide">
                <tr>
                  {["Item", "Category", "Type", "Price", "Variants", "Availability", "Updated", "Actions"].map((h) => (
                    <th key={h} className="text-left font-semibold px-4 py-3">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {items.map((it, i) => (
                  <tr key={i} className={`border-t border-border ${i % 2 ? "bg-surface/40" : ""} hover:bg-accent/40`}>
                    <td className="px-4 py-3">
                      <div className="font-semibold">{it.name}</div>
                      <div className="text-[11px] text-muted-foreground italic">No description added yet.</div>
                    </td>
                    <td className="px-4 py-3 text-xs">{it.cat}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-destructive">
                        <span className="h-2 w-2 rounded-full bg-destructive" />{it.type}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-semibold">{it.price}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{it.variants}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex px-2 py-0.5 rounded-full border text-[11px] font-semibold bg-destructive/10 text-destructive border-destructive/20">{it.stock}</span>
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{it.updated}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button className="relative inline-flex h-5 w-9 items-center rounded-full bg-success">
                          <span className="inline-block h-4 w-4 translate-x-4 rounded-full bg-white transition" />
                        </button>
                        <button className="text-xs font-semibold text-primary hover:underline">Edit</button>
                        <button className="text-xs font-semibold text-destructive hover:underline">Delete</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between px-4 py-3 border-t border-border bg-surface/40">
            <span className="text-xs text-muted-foreground">Page 1 of 2</span>
            <div className="flex gap-2">
              <button className="px-3 py-1.5 rounded-md text-xs font-semibold border border-border bg-card text-muted-foreground">Previous</button>
              <button className="px-3 py-1.5 rounded-md text-xs font-semibold border border-border bg-card hover:bg-accent">Next</button>
            </div>
          </div>
        </div>

        <section className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] p-5">
          <h3 className="font-display font-semibold">Import Menu from File</h3>
          <p className="text-xs text-muted-foreground mt-1">Upload a menu PDF or image (JPG/PNG, max 10 MB). We extract the item table and show a preview.</p>
          <div className="mt-4 rounded-lg border-2 border-dashed border-border p-8 text-center bg-surface/40">
            <button className="px-4 py-2 rounded-lg text-sm font-semibold text-primary-foreground" style={{ backgroundImage: "var(--gradient-primary)" }}>Choose file</button>
            <p className="text-xs text-muted-foreground mt-2">or drag & drop here</p>
          </div>
        </section>
      </div>
    </>
  );
}
