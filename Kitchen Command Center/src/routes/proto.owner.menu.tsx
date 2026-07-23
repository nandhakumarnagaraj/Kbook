import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import {
  Plus,
  Search,
  LayoutGrid,
  List,
  Upload,
  FileText,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  Trash2,
  UtensilsCrossed,
  Circle,
} from "lucide-react";
import { AppShell } from "../proto/AppShell";
import { Badge, EmptyState, INR, StateSwitcher, Drawer, Dialog, Btn } from "../proto/primitives";
import { SAMPLE_MENU, MENU_CATEGORIES, type MenuItem, type FoodType } from "../proto/data";

export const Route = createFileRoute("/proto/owner/menu")({
  head: () => ({
    meta: [
      { title: "Menu — Spice Garden · KhanaBook" },
      { name: "description", content: "Menu management, add/edit and OCR upload prototype." },
      { property: "og:title", content: "KhanaBook Menu" },
      { property: "og:description", content: "Design reference for menu management + OCR." },
    ],
  }),
  component: MenuProto,
});

type Sheet =
  | "none"
  | "add"
  | "edit"
  | "delete"
  | "ocr_idle"
  | "ocr_validation_error"
  | "ocr_uploading"
  | "ocr_processing"
  | "ocr_success"
  | "ocr_partial"
  | "ocr_failed";
type View = "grid" | "table";

function FoodDot({ type }: { type: FoodType }) {
  const c = {
    veg: "text-success border-success",
    non_veg: "text-danger border-danger",
    egg: "text-warning border-warning",
  }[type];
  return (
    <span
      className={`inline-grid h-4 w-4 shrink-0 place-items-center rounded-sm border ${c}`}
      aria-label={type}
    >
      <Circle className="h-1.5 w-1.5 fill-current" />
    </span>
  );
}

function MenuProto() {
  const [sheet, setSheet] = useState<Sheet>("none");
  const [view, setView] = useState<View>("grid");
  const [items, setItems] = useState(SAMPLE_MENU);
  const [cat, setCat] = useState("All");
  const [active, setActive] = useState<MenuItem | null>(null);

  const filtered = cat === "All" ? items : items.filter((i) => i.category === cat);
  const toggleAvail = (id: string) =>
    setItems(items.map((i) => (i.id === id ? { ...i, available: !i.available } : i)));

  return (
    <>
      <StateSwitcher
        value={sheet}
        onChange={setSheet}
        options={
          [
            "none",
            "add",
            "edit",
            "delete",
            "ocr_idle",
            "ocr_validation_error",
            "ocr_uploading",
            "ocr_processing",
            "ocr_success",
            "ocr_partial",
            "ocr_failed",
          ] as const
        }
        label="Sheet"
      />

      <AppShell
        role="OWNER"
        title="Menu"
        subtitle={`${items.length} items · ${items.filter((i) => !i.available).length} unavailable`}
        actions={
          <>
            <Btn onClick={() => setSheet("ocr_idle")}>
              <Upload className="h-4 w-4" /> Upload menu (OCR)
            </Btn>
            <Btn variant="primary" onClick={() => setSheet("add")}>
              <Plus className="h-4 w-4" /> Add item
            </Btn>
          </>
        }
      >
        {/* Toolbar */}
        <div className="card-surface mb-4 flex flex-wrap items-center gap-2 p-3">
          <div className="relative flex-1 min-w-[220px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              placeholder="Search menu items…"
              className="h-9 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
            />
          </div>
          <div className="inline-flex rounded-md border border-input bg-surface p-0.5">
            <button
              onClick={() => setView("grid")}
              className={`grid h-7 w-7 place-items-center rounded ${view === "grid" ? "bg-muted" : ""}`}
            >
              <LayoutGrid className="h-3.5 w-3.5" />
            </button>
            <button
              onClick={() => setView("table")}
              className={`grid h-7 w-7 place-items-center rounded ${view === "table" ? "bg-muted" : ""}`}
            >
              <List className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>

        {/* Category chips */}
        <div className="mb-4 flex gap-2 overflow-x-auto pb-1">
          {["All", ...MENU_CATEGORIES].map((c) => (
            <button
              key={c}
              onClick={() => setCat(c)}
              className={`shrink-0 rounded-full border px-3 py-1 text-xs font-medium transition-colors ${cat === c ? "border-saffron bg-primary-soft text-saffron" : "border-border bg-surface text-muted-foreground hover:text-foreground"}`}
            >
              {c}
            </button>
          ))}
        </div>

        {filtered.length === 0 ? (
          <EmptyState
            icon={UtensilsCrossed}
            title="No items in this category"
            description="Add an item or upload a menu photo to get started."
          />
        ) : view === "grid" ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {filtered.map((m) => (
              <div key={m.id} className={`card-surface p-4 ${!m.available ? "opacity-70" : ""}`}>
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <FoodDot type={m.type} />
                      <div className="truncate text-sm font-medium">{m.name}</div>
                    </div>
                    <div className="mt-0.5 text-[11px] text-muted-foreground">{m.category}</div>
                  </div>
                  <div className="text-right text-sm font-semibold tabular">
                    <INR value={m.price} />
                  </div>
                </div>
                {m.description && (
                  <div className="mt-2 line-clamp-2 text-xs text-muted-foreground">
                    {m.description}
                  </div>
                )}
                <div className="mt-3 flex items-center justify-between border-t border-border pt-3">
                  <label className="flex items-center gap-2 text-xs">
                    <span className="relative inline-flex h-4 w-7 items-center">
                      <input
                        type="checkbox"
                        checked={m.available}
                        onChange={() => toggleAvail(m.id)}
                        className="peer sr-only"
                      />
                      <span className="h-4 w-7 rounded-full bg-muted transition-colors peer-checked:bg-success" />
                      <span className="absolute left-0.5 h-3 w-3 rounded-full bg-white transition-transform peer-checked:translate-x-3" />
                    </span>
                    <span className="text-muted-foreground">
                      {m.available ? "Available" : "Unavailable"}
                    </span>
                  </label>
                  <div className="flex gap-1">
                    <button
                      onClick={() => {
                        setActive(m);
                        setSheet("edit");
                      }}
                      className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => {
                        setActive(m);
                        setSheet("delete");
                      }}
                      className="rounded p-1 text-muted-foreground hover:bg-danger-soft hover:text-danger"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="card-surface overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Item</th>
                  <th className="px-4 py-3 font-medium">Category</th>
                  <th className="px-4 py-3 font-medium text-right">Price</th>
                  <th className="px-4 py-3 font-medium">Available</th>
                  <th className="px-4 py-3 font-medium w-10"></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((m) => (
                  <tr key={m.id} className="border-t border-border hover:bg-surface-2">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <FoodDot type={m.type} />
                        <span className="font-medium">{m.name}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{m.category}</td>
                    <td className="px-4 py-3 text-right tabular">
                      <INR value={m.price} />
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={m.available ? "success" : "muted"} size="sm">
                        {m.available ? "Available" : "Unavailable"}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-1">
                        <button
                          onClick={() => {
                            setActive(m);
                            setSheet("edit");
                          }}
                          className="rounded px-2 py-1 text-xs hover:bg-muted"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => {
                            setActive(m);
                            setSheet("delete");
                          }}
                          className="rounded p-1 text-muted-foreground hover:bg-danger-soft hover:text-danger"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </AppShell>

      {/* Add/Edit drawer */}
      <Drawer
        open={sheet === "add" || sheet === "edit"}
        onClose={() => setSheet("none")}
        title={sheet === "add" ? "Add menu item" : "Edit menu item"}
        subtitle={sheet === "edit" ? active?.name : "Fill in the item details"}
        footer={
          <div className="flex justify-end gap-2">
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="primary" onClick={() => setSheet("none")}>
              {sheet === "add" ? "Add item" : "Save changes"}
            </Btn>
          </div>
        }
      >
        <MenuForm active={active} isEdit={sheet === "edit"} />
      </Drawer>

      {/* Delete confirmation */}
      <Dialog
        open={sheet === "delete"}
        onClose={() => setSheet("none")}
        destructive
        title={`Delete "${active?.name ?? "item"}"?`}
        footer={
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="danger" onClick={() => setSheet("none")}>
              <Trash2 className="h-4 w-4" /> Delete item
            </Btn>
          </>
        }
      >
        This item will be removed from your menu immediately. Historical orders that referenced it
        are preserved. This cannot be undone.
      </Dialog>

      {/* OCR modal */}
      {sheet.startsWith("ocr_") && <OcrDialog sheet={sheet} setSheet={setSheet} />}
    </>
  );
}

function MenuForm({ active, isEdit }: { active: MenuItem | null; isEdit: boolean }) {
  const item =
    isEdit && active
      ? active
      : {
          name: "",
          category: "Mains",
          type: "veg" as FoodType,
          price: 0,
          available: true,
          description: "",
        };
  return (
    <div className="space-y-4 text-sm">
      <div>
        <label className="mb-1.5 block text-xs font-medium">Item name</label>
        <input
          defaultValue={item.name}
          placeholder="e.g. Paneer Butter Masala"
          className="h-10 w-full rounded-md border border-input bg-surface px-3 outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
        />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="mb-1.5 block text-xs font-medium">Category</label>
          <select
            defaultValue={item.category}
            className="h-10 w-full rounded-md border border-input bg-surface px-2"
          >
            {MENU_CATEGORIES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="mb-1.5 block text-xs font-medium">Food type</label>
          <div className="flex gap-2">
            {(["veg", "egg", "non_veg"] as const).map((t) => (
              <label
                key={t}
                className={`flex flex-1 items-center gap-2 rounded-md border px-3 h-10 cursor-pointer ${item.type === t ? "border-saffron bg-primary-soft" : "border-input bg-surface"}`}
              >
                <input
                  type="radio"
                  name="ft"
                  defaultChecked={item.type === t}
                  className="sr-only"
                />
                <FoodDot type={t} />
                <span className="text-xs capitalize">{t.replace("_", " ")}</span>
              </label>
            ))}
          </div>
        </div>
      </div>
      <div>
        <label className="mb-1.5 block text-xs font-medium">Base price (INR)</label>
        <div className="relative w-40">
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
            ₹
          </span>
          <input
            defaultValue={item.price || ""}
            placeholder="0"
            className="h-10 w-full rounded-md border border-input bg-surface pl-7 pr-3 tabular outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
          />
        </div>
      </div>
      <div>
        <label className="mb-1.5 block text-xs font-medium">
          Description <span className="text-muted-foreground">(optional)</span>
        </label>
        <textarea
          defaultValue={item.description ?? ""}
          rows={3}
          className="w-full rounded-md border border-input bg-surface p-3 outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
        />
      </div>
      <label className="flex items-center gap-2 rounded-md border border-border bg-surface-2 p-3">
        <input
          type="checkbox"
          defaultChecked={item.available}
          className="h-4 w-4 rounded border-input"
        />
        <span className="text-sm">Available for ordering</span>
      </label>
    </div>
  );
}

function OcrDialog({ sheet, setSheet }: { sheet: Sheet; setSheet: (s: Sheet) => void }) {
  return (
    <Dialog
      open
      onClose={() => setSheet("none")}
      title="Upload menu (OCR)"
      width="lg"
      footer={
        sheet === "ocr_success" || sheet === "ocr_partial" ? (
          <>
            <Btn onClick={() => setSheet("none")}>Review later</Btn>
            <Btn variant="primary" onClick={() => setSheet("none")}>
              Import 24 items
            </Btn>
          </>
        ) : sheet === "ocr_failed" ? (
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="primary" onClick={() => setSheet("ocr_uploading")}>
              Retry upload
            </Btn>
          </>
        ) : sheet === "ocr_uploading" || sheet === "ocr_processing" ? (
          <Btn disabled>
            <Loader2 className="h-4 w-4 animate-spin" />{" "}
            {sheet === "ocr_uploading" ? "Uploading…" : "Extracting…"}
          </Btn>
        ) : sheet === "ocr_validation_error" ? (
          <Btn onClick={() => setSheet("ocr_idle")}>Choose another file</Btn>
        ) : (
          <>
            <Btn onClick={() => setSheet("none")}>Cancel</Btn>
            <Btn variant="primary" onClick={() => setSheet("ocr_uploading")}>
              Start upload
            </Btn>
          </>
        )
      }
    >
      {sheet === "ocr_idle" && (
        <div className="rounded-lg border-2 border-dashed border-border p-8 text-center">
          <Upload className="mx-auto h-8 w-8 text-muted-foreground" />
          <div className="mt-3 text-sm font-medium text-foreground">Drop a menu image or PDF</div>
          <div className="text-xs text-muted-foreground">
            JPG, PNG or PDF · up to 10 MB · clear, well-lit photos work best
          </div>
          <Btn className="mt-4" variant="primary">
            Browse files
          </Btn>
        </div>
      )}
      {sheet === "ocr_validation_error" && (
        <div className="rounded-lg border border-danger/25 bg-danger-soft p-4 text-sm text-danger">
          <div className="flex items-center gap-2 font-medium">
            <AlertTriangle className="h-4 w-4" /> File not accepted
          </div>
          <div className="mt-1 text-xs">
            menu-scan.tiff · 14.2 MB · Supported: JPG, PNG, PDF up to 10 MB.
          </div>
        </div>
      )}
      {(sheet === "ocr_uploading" || sheet === "ocr_processing") && (
        <div>
          <div className="flex items-center gap-3 rounded-md border border-border bg-surface-2 p-3 text-sm">
            <FileText className="h-4 w-4 text-muted-foreground" />
            <div className="flex-1 min-w-0">
              <div className="truncate font-medium">spice-garden-menu.pdf</div>
              <div className="text-xs text-muted-foreground">
                3.2 MB · {sheet === "ocr_uploading" ? "Uploading" : "Extracting items"}
              </div>
            </div>
            <div className="tabular text-xs text-muted-foreground">
              {sheet === "ocr_uploading" ? "62%" : "—"}
            </div>
          </div>
          <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-muted">
            <div
              className={`h-full ${sheet === "ocr_uploading" ? "w-[62%] bg-saffron" : "w-full animate-pulse bg-gradient-to-r from-saffron to-burnt"}`}
            />
          </div>
          <div className="mt-3 text-xs text-muted-foreground">
            {sheet === "ocr_processing"
              ? "Extraction usually takes 15–40 seconds for a 2-page menu."
              : "Do not close this window."}
          </div>
        </div>
      )}
      {(sheet === "ocr_success" || sheet === "ocr_partial") && (
        <div>
          <div
            className={`flex items-start gap-3 rounded-md p-3 text-sm ${sheet === "ocr_success" ? "border border-success/20 bg-success-soft text-success" : "border border-warning/25 bg-warning-soft text-warning"}`}
          >
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <div className="font-medium">
                {sheet === "ocr_success" ? "24 menu items extracted" : "18 of 24 items extracted"}
              </div>
              <div className="mt-0.5 text-xs">
                {sheet === "ocr_success"
                  ? "Review and import them into your menu."
                  : "6 rows had unreadable prices. You can fix them before import."}
              </div>
            </div>
          </div>
          <div className="mt-4 card-surface overflow-hidden max-h-64 overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-surface-2 text-left text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-3 py-2 font-medium">Name</th>
                  <th className="px-3 py-2 font-medium">Category</th>
                  <th className="px-3 py-2 font-medium text-right">Price</th>
                </tr>
              </thead>
              <tbody>
                {[
                  { n: "Paneer Butter Masala", c: "Mains", p: 320 },
                  { n: "Dal Makhani", c: "Mains", p: 260 },
                  { n: "Chicken Tikka", c: "Starters", p: 340 },
                  { n: "Butter Naan", c: "Breads", p: 60 },
                  { n: "Jeera Rice", c: "Rice & Biryani", p: 180 },
                  { n: "Malai Kofta", c: "Mains", p: 280 },
                ].map((r, i) => (
                  <tr key={i} className="border-t border-border">
                    <td className="px-3 py-2">{r.n}</td>
                    <td className="px-3 py-2 text-muted-foreground">{r.c}</td>
                    <td className="px-3 py-2 text-right tabular">
                      <INR value={r.p} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {sheet === "ocr_failed" && (
        <div className="rounded-md border border-danger/25 bg-danger-soft p-4 text-sm text-danger">
          <div className="flex items-center gap-2 font-medium">
            <AlertTriangle className="h-4 w-4" /> Extraction failed
          </div>
          <div className="mt-1 text-xs">
            We couldn't read the menu. Photos taken at an angle or with heavy shadows often fail.
            Try a straight-on, well-lit photo, or split the menu into 2 clearer images.
          </div>
        </div>
      )}
    </Dialog>
  );
}
