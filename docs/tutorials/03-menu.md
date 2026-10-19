# 3. Menu

Create categories, add items with prices and photos, manage variants, and use
the AI scanner.

**Who can edit:** owner only. Staff see the menu in read-only mode —
*"Viewing menu in read-only mode. Only the restaurant owner or an admin can edit
the menu."*

**Where:** **Settings** tab → **Menu Configuration**.

---

## Choose How to Add Items

On first open you're asked: **"How would you like to add items?"**

- **Manual Entry** — *"Add, view & Edit items one by one"* (Add / View / Edit).
- **Smart AI** — *"Extract from camera gallery, pdf."* (Camera / Gallery / PDF).
  Footer warning: *"AI can make mistakes. Please review before saving."*

The screen also shows stats: **Categories** and **Total Items**.

---

## Add a Category

1. Tap the green **Add Category** chip (first chip in the category row).
2. In the **Add Category** dialog, type the **Category Name** (e.g. "Starters").
3. Tap **Save** → toast **"Category added"**.

### Rename, reorder, or pin categories

1. Tap the ⋮ menu on the selected category → **Manage Categories**.
2. In the **Manage Categories** dialog:
   - **Reorder** — *"Press and hold, then drag a category to reorder it."*
   - **Rename** — tap the pencil icon next to a name.
   - **Star (pin)** — tap the star icon to pin the category to the top of the
     billing strip; tap a filled star (**"Unpin …"**) to unpin. Owner-only.
3. Tap **Save** — the button reads **"No Changes"** until you actually move a
   category.

---

## Add an Item (Manual Entry)

1. Select the category chip the item belongs to (items are added to the
   **currently selected** category).
2. Tap the gold **Add New Item** button at the bottom.
3. In the **Add New Item** dialog:
   - *(Optional)* tap the photo box **"Tap to add a dish photo"** and pick an
     image from your gallery.
   - **Item Name** — e.g. `Paneer Butter Masala`.
   - **Base Price (₹)** — shown only if the item has no variants.
   - Pick **Veg** or **Non-Veg**.
   - *(Optional)* tap **Add Variant** to add sizes: **Variant Name**
     (e.g. Small / Medium / Large) and **Price (₹)**, then **Add**.
4. Tap **Add Item** → toast **"Item added successfully"**.

> **Duplicate name?** If an item with the same name exists in this category you
> get *"An item with this name already exists in this category. Do you want to
> overwrite/update the existing item?"* with **Cancel** / **Overwrite**.

---

## Edit Items

- **Tap an item row** (or ⋮ → **Edit**) to open the **Edit Item** dialog — change
  name, price, veg/non-veg, photo, variants. Tap **Save**.
- **Availability** — flip the switch on the item row to mark it
  **Available** / **Unavailable** (it disappears from billing when off).
  Read-only devices see a pill instead of a switch.
- **Move to Category** — ⋮ → **Move to Category** (only if other categories
  exist).
- **Favourites** — tap the **star** on an item row to add it to *this till's*
  favourites. Favourites appear as a chip on the billing screen for quick
  re-ordering. (Category pinning is separate and owner-only.)

---

## Smart AI Import

Skip typing — let AI read your printed menu.

1. In Menu Configuration, choose **Smart AI**, then **Camera**, **Gallery**, or
   **PDF**.
2. For Camera: frame the menu (hint: *"Ensure menu is fully in frame, well-lit,
   text in English."*) → **Capture Photo** → **Use Photo** (or **Retake**).
3. Wait for **"Processing..."**.
4. In **"Review Detected Items"**, check each row (**Type**, **Item Name**,
   **Price**), fix anything wrong, then tap **Add N Items**.
   - Duplicate items trigger **"Conflicts Found"** with **Overwrite All** /
     **Merge & Skip** / **Cancel**.
   - Leaving the screen asks **"Discard Items?"** (**Discard** / **Keep Editing**).

---

## Tips

- There is no barcode field for items — barcode scanning is used in billing to
  find items, not to create them.
- The item's category is whatever chip is selected — there is no category
  dropdown inside the item dialog.
- Menu edits work offline and sync in the background.

Next: [4. Tax (GST & FSSAI)](04-tax.md)
