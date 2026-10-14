import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { HeaderSearchComponent } from '../../shared/header-search.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-inventory-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent, ApiStateComponent, PageHeaderComponent, RefreshButtonComponent, HeaderSearchComponent],
  styles: [`
    .card { background: var(--kb-color-card); border: 1px solid var(--kb-color-border); border-radius: 10px; padding: 1rem 1.25rem; margin-bottom: 1rem; }
    .materials-grid {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 0.75rem;
    }
    .material-card {
      background: var(--kb-color-card); border: 1px solid var(--kb-color-border); border-radius: 8px;
      padding: 0.85rem 1rem; display: flex; flex-direction: column; gap: 0.45rem;
      transition: border-color 0.15s, background 0.15s;
    }
    .material-card:hover { border-color: var(--kb-color-primary); background: var(--kb-color-surface); }
    .material-card.low-stock { border-color: var(--kb-color-warning); }
    .material-name { font-weight: 600; font-size: 0.95rem; }
    .material-stock { font-size: 0.85rem; color: var(--kb-color-muted-foreground); }
    .material-stock strong { color: var(--kb-color-foreground); font-variant-numeric: tabular-nums; }
    .low-label { color: var(--kb-color-error); font-weight: 600; font-size: 0.75rem; }
    .chip { display: inline-block; padding: 0.15rem 0.5rem; border-radius: 999px; font-size: 0.75rem; font-weight: 500; }
    .chip-green { background: rgba(47,133,90,0.12); color: var(--kb-color-success); }
    .chip-amber { background: rgba(183,121,31,0.12); color: var(--kb-color-warning); }
    .chip-red { background: rgba(192,57,43,0.12); color: var(--kb-color-danger); }
    .modal-content { width: 100%; max-width: 480px; }
    .modal-content h3 { margin: 0 0 1.25rem; }
    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; margin-bottom: 0.25rem; font-size: 0.875rem; font-weight: 500; }
    .form-group .field-control { width: 100%; box-sizing: border-box; }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
    .tabs { display: flex; gap: 0.25rem; margin-bottom: 1.25rem; border-bottom: 1px solid var(--kb-color-border); max-width: 100%; overflow-x: auto; -webkit-overflow-scrolling: touch; }
    .tab { flex: 0 0 auto; white-space: nowrap; padding: 0.5rem 1rem; font-size: 0.85rem; font-weight: 500; cursor: pointer; border: none; background: none; color: var(--kb-color-muted-foreground); border-bottom: 2px solid transparent; transition: color 150ms var(--ease-out, ease-out), border-color 150ms var(--ease-out, ease-out); }
    .tab:hover { color: var(--kb-color-foreground); }
    .tab.active { color: var(--kb-color-primary); border-bottom-color: var(--kb-color-primary); }
    .movement-row { display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0; border-bottom: 1px solid var(--kb-color-border); font-size: 0.85rem; }
    .movement-type { font-weight: 500; }
    .movement-qty { font-family: var(--kb-font-mono); }
    .movement-qty.positive { color: var(--kb-color-success); }
    .movement-qty.negative { color: var(--kb-color-danger); }
    .action-bar { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--kb-color-border); }
    .table-wrap { overflow-x: auto; }
    table.styled { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
    table.styled th, table.styled td { text-align: center; vertical-align: middle; }
    table.styled th { padding: 0.6rem 0.75rem; font-weight: 600; border-bottom: 2px solid var(--kb-color-border); white-space: nowrap; }
    table.styled td { padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--kb-color-border); font-variant-numeric: tabular-nums; }
    table.styled tr:hover td { background: rgba(0,0,0,0.02); }
    .inventory-summary, .materials-toolbar { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
    .inventory-summary { margin: 0.25rem 0 1rem; color: var(--kb-color-muted-foreground); font-size: 0.84rem; }
    .inventory-summary strong { color: var(--kb-color-foreground); font-variant-numeric: tabular-nums; }
    .materials-toolbar { justify-content: space-between; margin-bottom: 0.85rem; }
    .low-stock-filter[aria-pressed="true"] { color: var(--kb-color-warning); border-color: var(--kb-color-warning); background: rgba(183,121,31,0.08); }
    .material-card__actions { display: flex; gap: 0.45rem; margin-top: 0.35rem; }
    .material-card__actions button { min-height: 34px; padding: 0.35rem 0.65rem; }
    .vendor-form { padding: 0.8rem; margin: 0 0 1rem; background: var(--kb-color-surface-2); border-radius: 8px; }
    .vendor-form__actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
    .inventory-note { margin: 0 0 1rem; font-size: 0.8rem; color: var(--kb-color-muted-foreground); }
    .workflow-form { display: flex; gap: 0.6rem; align-items: end; flex-wrap: wrap; margin-bottom: 1rem; }
    .workflow-form .form-group { margin: 0; min-width: 150px; }
    .workflow-list { display: grid; gap: 0.6rem; }
    .workflow-row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0.75rem 0; border-bottom: 1px solid var(--kb-color-border); }
    .workflow-row:last-child { border-bottom: 0; }
    @media (max-width: 560px) {
      .form-row { grid-template-columns: 1fr; gap: 0; }
      .material-card__actions button { flex: 1; }
    }
  `],
  template: `
    <div class="page-shell">
      <app-page-header title="Inventory" subtitle="Track raw materials, stock levels and low-stock alerts.">
        <app-refresh-button (refresh)="loadMaterials()" [disabled]="loading" [loading]="loading" ariaLabel="Refresh inventory" />
        <button type="button" class="header-action-btn header-action-btn--primary" (click)="openAddMaterial()">+ Add Material</button>
      </app-page-header>

      <div class="inventory-summary" *ngIf="!loading && !error" aria-label="Inventory summary">
        <span><strong>{{ materials.length }}</strong> materials</span>
        <span aria-hidden="true">·</span>
        <span [class.low-label]="lowStockCount > 0"><strong>{{ lowStockCount }}</strong> low stock</span>
        <span aria-hidden="true">·</span>
        <span>Estimated value <strong>₹{{ totalStockValue | number:'1.2-2' }}</strong></span>
      </div>

      <!-- Tabs -->
      <div class="tabs" role="tablist" aria-label="Inventory sections">
        <button class="tab" role="tab" [attr.aria-selected]="activeTab === 'materials'" [class.active]="activeTab === 'materials'" (click)="activeTab='materials'">Materials</button>
        <button class="tab" role="tab" [attr.aria-selected]="activeTab === 'recipes'" [class.active]="activeTab === 'recipes'" (click)="openRecipesTab()">Item Recipes</button>
        <button class="tab" role="tab" [attr.aria-selected]="activeTab === 'purchaseOrders'" [class.active]="activeTab === 'purchaseOrders'" (click)="openPurchaseOrdersTab()">Purchase Orders</button>
        <button class="tab" role="tab" [attr.aria-selected]="activeTab === 'movements'" [class.active]="activeTab === 'movements'" (click)="activeTab='movements'">Stock Movements</button>
        <button class="tab" role="tab" [attr.aria-selected]="activeTab === 'variance'" [class.active]="activeTab === 'variance'" (click)="activeTab='variance'">Variance Report</button>
      </div>

      <section role="tabpanel" aria-label="Item recipes" *ngIf="activeTab === 'recipes' && !loading && !error" class="card">
        <p class="inventory-note">Set ingredient quantities for each menu item. Finalized bills deduct these amounts from stock automatically.</p>
        <div class="workflow-form">
          <div class="form-group"><label for="recipe-menu-item">Menu item</label><select id="recipe-menu-item" class="field-select" [(ngModel)]="selectedMenuItemId" (change)="loadRecipes()"><option [ngValue]="null">Choose item…</option><option *ngFor="let item of menuItems" [ngValue]="item.menuItemId">{{ item.name }}</option></select></div>
          <div class="form-group"><label for="recipe-material">Ingredient</label><select id="recipe-material" class="field-select" [(ngModel)]="recipeForm.rawMaterialId"><option [ngValue]="null">Choose material…</option><option *ngFor="let material of materials" [ngValue]="material.id">{{ material.name }} ({{ material.unit }})</option></select></div>
          <div class="form-group"><label for="recipe-quantity">Quantity per item</label><input id="recipe-quantity" class="field-control" type="number" min="0.0001" step="0.0001" [(ngModel)]="recipeForm.quantityPerItem" /></div>
          <button type="button" class="primary-btn" (click)="addRecipe()" [disabled]="!selectedMenuItemId || !recipeForm.rawMaterialId || !isPositive(recipeForm.quantityPerItem) || savingWorkflow">{{ savingWorkflow ? 'Saving…' : 'Add ingredient' }}</button>
        </div>
        <div *ngIf="!selectedMenuItemId" class="muted">Choose a menu item to view or configure its recipe.</div>
        <div *ngIf="selectedMenuItemId && recipes.length === 0" class="muted">No ingredients configured for this menu item.</div>
        <div class="workflow-list" *ngIf="recipes.length">
          <div class="workflow-row" *ngFor="let recipe of recipes; trackBy: trackByIndex"><span><strong>{{ recipe.materialName }}</strong> · {{ recipe.quantityPerItem }} {{ recipe.unit }} per item</span><button type="button" class="ghost-btn" (click)="removeRecipe(recipe)">Remove</button></div>
        </div>
      </section>

      <section role="tabpanel" aria-label="Purchase orders" *ngIf="activeTab === 'purchaseOrders' && !loading && !error" class="card">
        <p class="inventory-note">Draft or send a supplier order. Receiving it records stock-in movements for every line.</p>
        <div class="workflow-form">
          <div class="form-group"><label for="po-vendor">Supplier</label><select id="po-vendor" class="field-select" [(ngModel)]="poForm.vendorId"><option [ngValue]="null">No supplier</option><option *ngFor="let vendor of vendors" [ngValue]="vendor.id">{{ vendor.name }}</option></select></div>
          <div class="workflow-row" style="width:100%;justify-content:flex-start" *ngFor="let line of poForm.items; let i = index; trackBy: trackByIndex">
            <div class="form-group"><label [for]="'po-material-' + i">Material</label><select [id]="'po-material-' + i" class="field-select" [(ngModel)]="line.rawMaterialId"><option [ngValue]="null">Choose material…</option><option *ngFor="let material of materials" [ngValue]="material.id">{{ material.name }} ({{ material.unit }})</option></select></div>
            <div class="form-group"><label [for]="'po-quantity-' + i">Quantity</label><input [id]="'po-quantity-' + i" class="field-control" type="number" min="0.01" step="0.01" [(ngModel)]="line.quantity" /></div>
            <button *ngIf="poForm.items.length > 1" type="button" class="ghost-btn" (click)="removePoLine(i)" aria-label="Remove purchase order line">Remove</button>
          </div>
          <button type="button" class="ghost-btn" (click)="addPoLine()">+ Add another material</button>
          <div class="form-group"><label for="po-note">Note</label><input id="po-note" class="field-control" [(ngModel)]="poForm.note" placeholder="Optional note" /></div>
          <button type="button" class="ghost-btn" (click)="createPurchaseOrder(false)" [disabled]="!validPo || savingWorkflow">Save draft</button>
          <button type="button" class="primary-btn" (click)="createPurchaseOrder(true)" [disabled]="!validPo || savingWorkflow">Send order</button>
        </div>
        <div *ngIf="purchaseOrders.length === 0" class="muted">No purchase orders yet.</div>
        <div class="workflow-list" *ngIf="purchaseOrders.length">
          <div class="workflow-row" *ngFor="let po of purchaseOrders; trackBy: trackByIndex">
            <div><strong>PO #{{ po.id }}</strong> <span class="chip" [class.chip-green]="po.status === 'RECEIVED'">{{ po.status }}</span><div class="muted">{{ vendorName(po.vendorId) }} · {{ po.createdAt | date:'mediumDate' }}<span *ngIf="po.note"> · {{ po.note }}</span></div><div class="muted" *ngFor="let line of po.items">{{ line.materialName }}: {{ line.quantity }} {{ line.unit }}</div></div>
            <div class="toolbar-actions" *ngIf="po.status === 'DRAFT' || po.status === 'SENT'"><button type="button" class="primary-btn" (click)="receivePurchaseOrder(po)" [disabled]="savingWorkflow">Receive</button><button type="button" class="ghost-btn" (click)="cancelPurchaseOrder(po)" [disabled]="savingWorkflow">Cancel</button></div>
          </div>
        </div>
      </section>

      <app-api-state [loading]="loading" [error]="error" [attr.aria-busy]="loading"></app-api-state>

      <!-- Materials Tab -->
      <div role="tabpanel" aria-label="Materials" *ngIf="activeTab === 'materials' && !loading && !error">
        <div class="materials-toolbar" *ngIf="materials.length > 0">
          <app-header-search [(value)]="searchTerm" placeholder="Search materials..." ariaLabel="Search materials" inputId="materials-search" />
          <button type="button" class="ghost-btn low-stock-filter" (click)="lowStockOnly = !lowStockOnly" [attr.aria-pressed]="lowStockOnly">
            {{ lowStockOnly ? 'Showing low stock' : 'Low stock only' }}
          </button>
          <span class="muted" aria-live="polite">{{ filteredMaterials.length }} of {{ materials.length }}</span>
        </div>
        <div *ngIf="materials.length === 0">
          <app-empty-state
            title="No materials"
            text="Add raw materials to track stock levels."
            icon="📦"
            actionLabel="Add Material"
            (action)="openAddMaterial()">
          </app-empty-state>
        </div>

        <div *ngIf="materials.length > 0 && filteredMaterials.length === 0" class="card" role="status">
          <p style="margin:0;color:var(--kb-color-muted-foreground)">No materials match these filters.</p>
        </div>

        <div *ngIf="filteredMaterials.length > 0" class="materials-grid">
          <article *ngFor="let m of filteredMaterials; trackBy: trackByMaterialId"
               class="material-card"
               [class.low-stock]="isLowStock(m)"
               [attr.aria-label]="m.name + ', ' + m.stockQuantity + ' ' + m.unit">
            <div style="display:flex;justify-content:space-between;align-items:start">
              <span class="material-name">{{ m.name }}</span>
              <span *ngIf="isLowStock(m)" class="low-label">LOW</span>
            </div>
            <div class="material-stock">
              <strong>{{ m.stockQuantity }}</strong> {{ m.unit }}
            </div>
            <div class="material-stock" *ngIf="m.lowStockThreshold > 0">
              Threshold: {{ m.lowStockThreshold }} {{ m.unit }}
            </div>
            <div class="material-stock" *ngIf="m.costPerUnit">
              Cost: {{ m.costPerUnit | number:'1.2-2' }} / {{ m.unit }}
            </div>
            <div class="material-card__actions">
              <button type="button" class="primary-btn" (click)="openMaterialDetail(m)">Stock actions</button>
              <button type="button" class="ghost-btn" (click)="openEditMaterial(m)">Edit details</button>
            </div>
          </article>
        </div>
      </div>

      <!-- Movements Tab -->
      <div role="tabpanel" aria-label="Stock movements" *ngIf="activeTab === 'movements' && !loading && !error">
        <div style="margin-bottom:1rem">
          <select class="field-select" aria-label="Select material for stock movements" [(ngModel)]="selectedMaterialId" (change)="loadMovements()" style="max-width:300px">
            <option [ngValue]="null">Select a material...</option>
            <option *ngFor="let m of materials; trackBy: trackByMaterialId" [ngValue]="m.id">{{ m.name }}</option>
          </select>
        </div>

        <div *ngIf="selectedMaterialId && movements.length === 0" class="card">
          <p style="margin:0;color:var(--kb-color-muted-foreground)">No movements recorded yet.</p>
        </div>

        <div *ngIf="movements.length > 0" class="card">
          <div *ngFor="let mv of movements; trackBy: trackByIndex" class="movement-row">
            <div>
              <span class="movement-type">{{ formatMovementType(mv.kind) }}</span>
              <span style="margin-left:0.5rem;color:var(--kb-color-muted-foreground);font-size:0.8rem">
                {{ mv.createdAt | date:'short' }}
              </span>
              <span *ngIf="mv.reason" style="margin-left:0.5rem;font-size:0.8rem;color:var(--kb-color-muted-foreground)">
                — {{ mv.reason }}
              </span>
            </div>
            <span class="movement-qty" [class.positive]="mv.quantity > 0" [class.negative]="mv.quantity < 0">
              {{ mv.quantity > 0 ? '+' : '' }}{{ mv.quantity }} {{ getUnit(selectedMaterialId) }}
            </span>
          </div>
        </div>

        <div *ngIf="!selectedMaterialId" class="card">
          <p style="margin:0;color:var(--kb-color-muted-foreground)">Select a material to view its stock movement history.</p>
        </div>
      </div>

      <!-- Variance Tab -->
      <div role="tabpanel" aria-label="Variance report" *ngIf="activeTab === 'variance' && !loading && !error">
        <div class="card">
          <div style="display:flex;gap:1rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap">
            <div class="form-group" style="margin:0">
              <label for="varianceFrom">From</label>
              <input id="varianceFrom" class="field-control" type="date" [(ngModel)]="varianceFrom" />
            </div>
            <div class="form-group" style="margin:0">
              <label for="varianceTo">To</label>
              <input id="varianceTo" class="field-control" type="date" [(ngModel)]="varianceTo" />
            </div>
            <button class="primary-btn" (click)="loadVariance()" [disabled]="!varianceFrom || !varianceTo">Run Report</button>
          </div>

          <div class="table-wrap" *ngIf="varianceData.length > 0">
            <table class="styled">
              <thead>
                <tr>
                  <th scope="col">Material</th>
                  <th scope="col">System Stock</th>
                  <th scope="col">Physical Count</th>
                  <th scope="col">Variance</th>
                  <th scope="col">Variance %</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let v of varianceData; trackBy: trackByIndex">
                  <td>{{ v.materialName }}</td>
                  <td>{{ v.systemStock }}</td>
                  <td>{{ v.physicalCount }}</td>
                  <td [style.color]="v.variance < 0 ? 'var(--kb-color-danger)' : v.variance > 0 ? 'var(--kb-color-success)' : ''">
                    {{ v.variance > 0 ? '+' : '' }}{{ v.variance }}
                  </td>
                  <td>{{ v.variancePct | number:'1.1-1' }}%</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- Add/Edit Material Modal -->
      <div class="modal-backdrop" *ngIf="showMaterialModal" (click)="closeMaterialModal()">
        <div class="modal-box modal-content" role="dialog" aria-modal="true" aria-labelledby="material-modal-title" (click)="$event.stopPropagation()">
          <h3 id="material-modal-title">{{ editingMaterial ? 'Edit Material' : 'Add Material' }}</h3>

          <div class="form-group">
            <label for="material-name">Name *</label>
            <input id="material-name" class="field-control" type="text" [(ngModel)]="materialForm.name" placeholder="e.g. Basmati Rice" />
          </div>

          <div class="form-row">
            <div class="form-group">
              <label>Unit</label>
              <select class="field-select" [(ngModel)]="materialForm.unit">
                <option value="kg">kg</option>
                <option value="g">g</option>
                <option value="L">L</option>
                <option value="ml">ml</option>
                <option value="pcs">pcs</option>
                <option value="dozen">dozen</option>
              </select>
            </div>
          </div>

          <p class="inventory-note" *ngIf="editingMaterial">To change the stock quantity, use Physical Count so the adjustment appears in movement history.</p>
          <p class="inventory-note" *ngIf="!editingMaterial">New materials start at zero. Use Physical Count after saving to record the starting quantity in movement history.</p>

          <div class="form-row">
            <div class="form-group">
              <label>Low Stock Threshold</label>
              <input class="field-control" type="number" [(ngModel)]="materialForm.lowStockThreshold" min="0" step="0.01" />
            </div>
            <div class="form-group">
              <label>Cost per Unit (₹)</label>
              <input class="field-control" type="number" [(ngModel)]="materialForm.costPerUnit" min="0" step="0.01" />
            </div>
          </div>

          <div class="modal-actions">
            <button class="ghost-btn" (click)="closeMaterialModal()" [disabled]="savingMaterial">Cancel</button>
            <button class="primary-btn" (click)="saveMaterial()" [disabled]="!materialForm.name?.trim() || savingMaterial">
              {{ savingMaterial ? 'Saving…' : (editingMaterial ? 'Update material' : 'Add material') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Material Detail Modal (stock actions) -->
      <div class="modal-backdrop" *ngIf="showDetailModal" (click)="closeDetailModal()">
        <div class="modal-box modal-content" style="max-width:520px" role="dialog" aria-modal="true" aria-labelledby="material-detail-title" (click)="$event.stopPropagation()">
          <h3 id="material-detail-title">{{ selectedMaterial?.name }}</h3>
          <p style="margin:0 0 1rem;color:var(--kb-color-muted-foreground)">
            Current stock: <strong style="font-variant-numeric:tabular-nums">{{ selectedMaterial?.stockQuantity }} {{ selectedMaterial?.unit }}</strong>
            <span *ngIf="selectedMaterial?.costPerUnit" style="font-variant-numeric:tabular-nums"> · Cost: ₹{{ selectedMaterial?.costPerUnit | number:'1.2-2' }}/{{ selectedMaterial?.unit }}</span>
          </p>

          <!-- Action sub-tabs -->
          <div class="tabs" style="margin-bottom:1rem" role="tablist" aria-label="Stock actions">
            <button class="tab" role="tab" [attr.aria-selected]="detailTab === 'purchase'" [class.active]="detailTab === 'purchase'" (click)="detailTab='purchase'">Stock In</button>
            <button class="tab" role="tab" [attr.aria-selected]="detailTab === 'wastage'" [class.active]="detailTab === 'wastage'" (click)="detailTab='wastage'">Wastage</button>
            <button class="tab" role="tab" [attr.aria-selected]="detailTab === 'count'" [class.active]="detailTab === 'count'" (click)="detailTab='count'">Physical Count</button>
          </div>

          <!-- Purchase / Stock In -->
          <div role="tabpanel" aria-label="Stock in" *ngIf="detailTab === 'purchase'">
            <div class="form-group">
              <label for="purchase-vendor">Supplier (optional)</label>
              <select id="purchase-vendor" class="field-select" [(ngModel)]="purchaseForm.vendorId">
                <option [ngValue]="null">No supplier selected</option>
                <option *ngFor="let vendor of vendors; trackBy: trackByVendorId" [ngValue]="vendor.id">{{ vendor.name }}</option>
              </select>
              <button type="button" class="ghost-btn" style="margin-top:0.45rem" (click)="showVendorForm = !showVendorForm">
                {{ showVendorForm ? 'Cancel new supplier' : '+ Add supplier' }}
              </button>
            </div>
            <div class="vendor-form" *ngIf="showVendorForm">
              <div class="form-group">
                <label for="vendor-name">Supplier name *</label>
                <input id="vendor-name" class="field-control" type="text" [(ngModel)]="vendorForm.name" />
              </div>
              <div class="form-group">
                <label for="vendor-phone">Phone (optional)</label>
                <input id="vendor-phone" class="field-control" type="tel" [(ngModel)]="vendorForm.phone" />
              </div>
              <div class="vendor-form__actions">
                <button type="button" class="primary-btn" (click)="createVendor()" [disabled]="!vendorForm.name.trim() || savingVendor">
                  {{ savingVendor ? 'Saving…' : 'Save supplier' }}
                </button>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>Quantity *</label>
                <input class="field-control" type="number" [(ngModel)]="purchaseForm.quantity" min="0.01" step="0.01" />
              </div>
              <div class="form-group">
                <label>Unit Cost (₹)</label>
                <input class="field-control" type="number" [(ngModel)]="purchaseForm.unitCost" min="0" step="0.01" />
              </div>
            </div>
            <button class="primary-btn" (click)="submitPurchase()" [disabled]="!isPositive(purchaseForm.quantity) || savingStock">
              {{ savingStock ? 'Recording…' : 'Record stock-in' }}
            </button>
          </div>

          <!-- Wastage -->
          <div role="tabpanel" aria-label="Wastage" *ngIf="detailTab === 'wastage'">
            <div class="form-group">
              <label>Quantity *</label>
              <input class="field-control" type="number" [(ngModel)]="wastageForm.quantity" min="0.01" step="0.01" />
            </div>
            <div class="form-group">
              <label>Reason *</label>
              <input class="field-control" type="text" [(ngModel)]="wastageForm.reason" placeholder="e.g. Expired, spillage" />
            </div>
            <button class="primary-btn" (click)="submitWastage()" [disabled]="!isPositive(wastageForm.quantity) || !wastageForm.reason?.trim() || savingStock">
              {{ savingStock ? 'Recording…' : 'Record wastage' }}
            </button>
          </div>

          <!-- Physical Count -->
          <div role="tabpanel" aria-label="Physical count" *ngIf="detailTab === 'count'">
            <div class="form-group">
              <label>Counted Quantity *</label>
              <input class="field-control" type="number" [(ngModel)]="countForm.countedQty" min="0" step="0.01" />
            </div>
            <p style="margin:0 0 1rem;font-size:0.8rem;color:var(--kb-color-muted-foreground)">
              System shows {{ selectedMaterial?.stockQuantity }} {{ selectedMaterial?.unit }}. Any variance will create an adjustment entry.
            </p>
            <button class="primary-btn" (click)="submitPhysicalCount()" [disabled]="!isNonNegative(countForm.countedQty) || savingStock">
              {{ savingStock ? 'Saving…' : 'Submit count' }}
            </button>
          </div>

          <div class="modal-actions">
            <button class="ghost-btn" (click)="closeDetailModal()">Close</button>
            <button class="ghost-btn" (click)="openEditMaterial(selectedMaterial)">Edit details</button>
            <button class="ghost-btn" style="color:var(--kb-color-error)" (click)="deleteMaterial()">Delete</button>
          </div>
        </div>
      </div>

      <!-- Confirm Dialog -->
      <app-confirm-dialog
        *ngIf="confirmVisible"
        [title]="confirmTitle"
        [message]="confirmMessage"
        [confirmLabel]="confirmAction"
        [confirmDanger]="confirmDestructive"
        (confirmed)="onConfirm()"
        (cancelled)="confirmVisible = false">
      </app-confirm-dialog>
    </div>
  `
})
export class InventoryPageComponent implements OnInit {
  private api = inject(BusinessApiService);
  private toast = inject(ToastService);
  private cdr = inject(ChangeDetectorRef);

  loading = true;
  error: string | null = null;
  savingStock = false;
  savingVendor = false;
  savingMaterial = false;

  activeTab: 'materials' | 'recipes' | 'purchaseOrders' | 'movements' | 'variance' = 'materials';
  materials: any[] = [];
  menuItems: any[] = [];
  recipes: any[] = [];
  purchaseOrders: any[] = [];
  selectedMenuItemId: number | null = null;
  recipeForm: any = { rawMaterialId: null, quantityPerItem: null };
  poForm: any = { vendorId: null, items: [{ rawMaterialId: null, quantity: null }], note: '' };
  savingWorkflow = false;
  vendors: any[] = [];
  searchTerm = '';
  lowStockOnly = false;
  showVendorForm = false;
  vendorForm = { name: '', phone: '' };

  // Material modal
  showMaterialModal = false;
  editingMaterial: any = null;
  materialForm: any = {};

  // Detail modal
  showDetailModal = false;
  selectedMaterial: any = null;
  detailTab: 'purchase' | 'wastage' | 'count' = 'purchase';
  purchaseForm: any = { quantity: null, unitCost: null };
  wastageForm: any = { quantity: null, reason: '' };
  countForm: any = { countedQty: null };

  // Movements
  selectedMaterialId: number | null = null;
  movements: any[] = [];

  // Variance
  varianceFrom = '';
  varianceTo = '';
  varianceData: any[] = [];

  // Confirm dialog
  confirmVisible = false;
  confirmTitle = '';
  confirmMessage = '';
  confirmAction = '';
  confirmDestructive = false;
  private confirmCallback: (() => void) | null = null;

  ngOnInit(): void {
    this.loadMaterials();
    this.loadVendors();
  }

  get filteredMaterials(): any[] {
    const query = this.searchTerm.trim().toLowerCase();
    return this.materials.filter(material => {
      const matchesSearch = !query || String(material.name ?? '').toLowerCase().includes(query);
      return matchesSearch && (!this.lowStockOnly || this.isLowStock(material));
    });
  }

  get lowStockCount(): number {
    return this.materials.filter(material => this.isLowStock(material)).length;
  }

  get totalStockValue(): number {
    return this.materials.reduce((total, material) =>
      total + (Number(material.stockQuantity) || 0) * (Number(material.costPerUnit) || 0), 0);
  }

  get validPo(): boolean { return this.poForm.items.length > 0 && this.poForm.items.every((line: any) => !!line.rawMaterialId && this.isPositive(line.quantity)); }

  openRecipesTab(): void {
    this.activeTab = 'recipes';
    if (!this.menuItems.length) this.api.getMenu().subscribe({ next: items => { this.menuItems = items || []; this.cdr.markForCheck(); }, error: () => this.toast.show('Could not load menu items for recipes.', 'error') });
  }

  openPurchaseOrdersTab(): void {
    this.activeTab = 'purchaseOrders';
    this.loadPurchaseOrders();
  }

  loadRecipes(): void {
    this.recipes = [];
    if (!this.selectedMenuItemId) return;
    this.api.getRecipes(this.selectedMenuItemId).subscribe({ next: rows => { this.recipes = rows || []; this.cdr.markForCheck(); }, error: err => this.toast.show(err?.error?.error || 'Could not load recipe.', 'error') });
  }

  addRecipe(): void {
    if (!this.selectedMenuItemId || !this.recipeForm.rawMaterialId || !this.isPositive(this.recipeForm.quantityPerItem)) return;
    this.savingWorkflow = true;
    this.api.addRecipe({ menuItemId: this.selectedMenuItemId, rawMaterialId: Number(this.recipeForm.rawMaterialId), quantityPerItem: Number(this.recipeForm.quantityPerItem) }).subscribe({
      next: () => { this.savingWorkflow = false; this.recipeForm = { rawMaterialId: null, quantityPerItem: null }; this.loadRecipes(); this.toast.show('Recipe ingredient added.', 'success'); this.cdr.markForCheck(); },
      error: err => { this.savingWorkflow = false; this.toast.show(err?.error?.error || 'Could not add recipe ingredient.', 'error'); this.cdr.markForCheck(); }
    });
  }

  removeRecipe(recipe: any): void {
    this.api.deleteRecipe(recipe.id).subscribe({ next: () => { this.loadRecipes(); this.toast.show('Recipe ingredient removed.', 'success'); }, error: err => this.toast.show(err?.error?.error || 'Could not remove ingredient.', 'error') });
  }

  loadPurchaseOrders(): void {
    this.api.getPurchaseOrders().subscribe({ next: rows => { this.purchaseOrders = rows || []; this.cdr.markForCheck(); }, error: err => this.toast.show(err?.error?.error || 'Could not load purchase orders.', 'error') });
  }

  createPurchaseOrder(sendNow: boolean): void {
    if (!this.validPo || this.savingWorkflow) return;
    this.savingWorkflow = true;
    const items = this.poForm.items.map((line: any) => ({ rawMaterialId: Number(line.rawMaterialId), quantity: Number(line.quantity) }));
    this.api.createPurchaseOrder({ vendorId: this.poForm.vendorId || undefined, note: this.poForm.note.trim() || undefined, items, sendNow }).subscribe({
      next: () => { this.savingWorkflow = false; this.poForm = { vendorId: null, items: [{ rawMaterialId: null, quantity: null }], note: '' }; this.loadPurchaseOrders(); this.toast.show(sendNow ? 'Purchase order sent.' : 'Purchase order saved as draft.', 'success'); this.cdr.markForCheck(); },
      error: err => { this.savingWorkflow = false; this.toast.show(err?.error?.error || 'Could not create purchase order.', 'error'); this.cdr.markForCheck(); }
    });
  }

  addPoLine(): void { this.poForm.items = [...this.poForm.items, { rawMaterialId: null, quantity: null }]; }

  removePoLine(index: number): void { if (this.poForm.items.length > 1) this.poForm.items.splice(index, 1); }

  receivePurchaseOrder(po: any): void {
    this.api.receivePurchaseOrder(po.id).subscribe({ next: () => { this.loadPurchaseOrders(); this.loadMaterials(); this.toast.show('Purchase order received and stock updated.', 'success'); }, error: err => this.toast.show(err?.error?.error || 'Could not receive purchase order.', 'error') });
  }

  cancelPurchaseOrder(po: any): void {
    this.api.cancelPurchaseOrder(po.id).subscribe({ next: () => { this.loadPurchaseOrders(); this.toast.show('Purchase order cancelled.', 'success'); }, error: err => this.toast.show(err?.error?.error || 'Could not cancel purchase order.', 'error') });
  }

  vendorName(vendorId: number | null): string { return this.vendors.find(v => v.id === vendorId)?.name || 'No supplier'; }

  isPositive(value: unknown): boolean {
    return Number.isFinite(Number(value)) && Number(value) > 0;
  }

  isNonNegative(value: unknown): boolean {
    return value !== null && value !== undefined && value !== ''
      && Number.isFinite(Number(value)) && Number(value) >= 0;
  }

  loadMaterials(): void {
    this.loading = true;
    this.error = null;
    this.api.getInventoryMaterials().subscribe({
      next: (data) => { this.materials = data; this.loading = false; this.cdr.markForCheck(); },
      error: (err) => { this.error = err?.error?.error || 'Failed to load materials'; this.loading = false; this.cdr.markForCheck(); }
    });
  }

  loadVendors(): void {
    this.api.getVendors().subscribe({
      next: vendors => {
        this.vendors = Array.isArray(vendors) ? vendors : [];
        this.cdr.markForCheck();
      },
      error: () => {
        this.vendors = [];
        this.cdr.markForCheck();
      }
    });
  }

  isLowStock(m: any): boolean {
    return m.lowStockThreshold > 0 && m.stockQuantity <= m.lowStockThreshold;
  }

  openAddMaterial(): void {
    this.editingMaterial = null;
    this.materialForm = { name: '', unit: 'kg', lowStockThreshold: 0, costPerUnit: null };
    this.showMaterialModal = true;
  }

  openEditMaterial(material: any): void {
    if (!material) return;
    this.closeDetailModal();
    this.editingMaterial = material;
    this.materialForm = {
      name: material.name ?? '',
      unit: material.unit ?? 'kg',
      lowStockThreshold: Number(material.lowStockThreshold) || 0,
      costPerUnit: material.costPerUnit ?? null
    };
    this.showMaterialModal = true;
  }

  openMaterialDetail(m: any): void {
    this.selectedMaterial = m;
    this.detailTab = 'purchase';
    this.purchaseForm = { quantity: null, unitCost: m.costPerUnit || null };
    this.purchaseForm.vendorId = null;
    this.wastageForm = { quantity: null, reason: '' };
    this.countForm = { countedQty: null };
    this.showVendorForm = false;
    this.vendorForm = { name: '', phone: '' };
    this.showDetailModal = true;
  }

  closeMaterialModal(): void {
    this.showMaterialModal = false;
    this.editingMaterial = null;
  }

  closeDetailModal(): void {
    this.showDetailModal = false;
    this.selectedMaterial = null;
  }

  saveMaterial(): void {
    if (!this.materialForm.name?.trim()) return;
    if (!this.isNonNegative(this.materialForm.lowStockThreshold)
        || !this.isNonNegative(this.materialForm.costPerUnit ?? 0)) {
      this.toast.show('Threshold and unit cost must be zero or greater.', 'error');
      return;
    }
    this.savingMaterial = true;
    const payload = {
      ...this.materialForm,
      name: this.materialForm.name.trim(),
      ...(!this.editingMaterial ? { stockQuantity: 0 } : {})
    };

    if (this.editingMaterial) {
      this.api.updateMaterial(this.editingMaterial.id, payload).subscribe({
        next: () => { this.savingMaterial = false; this.closeMaterialModal(); this.loadMaterials(); this.toast.show('Material updated', 'success'); },
        error: (err) => { this.savingMaterial = false; this.toast.show(err?.error?.error || 'Failed to update material.', 'error'); this.cdr.markForCheck(); }
      });
    } else {
      this.api.createMaterial(payload).subscribe({
        next: () => { this.savingMaterial = false; this.closeMaterialModal(); this.loadMaterials(); this.toast.show('Material added', 'success'); },
        error: (err) => { this.savingMaterial = false; this.toast.show(err?.error?.error || 'Failed to add material.', 'error'); this.cdr.markForCheck(); }
      });
    }
  }

  createVendor(): void {
    const name = this.vendorForm.name.trim();
    if (!name || this.savingVendor) return;
    this.savingVendor = true;
    this.api.createVendor({ name, phone: this.vendorForm.phone.trim() || undefined }).subscribe({
      next: vendor => {
        this.savingVendor = false;
        this.vendors = [...this.vendors, vendor].sort((a, b) => String(a.name).localeCompare(String(b.name)));
        this.purchaseForm.vendorId = vendor.id;
        this.vendorForm = { name: '', phone: '' };
        this.showVendorForm = false;
        this.toast.show('Supplier saved.', 'success');
        this.cdr.markForCheck();
      },
      error: err => {
        this.savingVendor = false;
        this.toast.show(err?.error?.error || 'Could not save supplier.', 'error');
        this.cdr.markForCheck();
      }
    });
  }

  deleteMaterial(): void {
    if (!this.selectedMaterial) return;
    this.confirmTitle = 'Delete Material';
    this.confirmMessage = `Delete "${this.selectedMaterial.name}"? This cannot be undone.`;
    this.confirmAction = 'Delete';
    this.confirmDestructive = true;
    this.confirmCallback = () => {
      this.api.deleteMaterial(this.selectedMaterial.id).subscribe({
        next: () => { this.closeDetailModal(); this.loadMaterials(); this.toast.show('Material deleted', 'success'); },
        error: (err) => { this.toast.show(err?.error?.error || 'Failed to delete', 'error'); }
      });
    };
    this.confirmVisible = true;
  }

  submitPurchase(): void {
    if (!this.selectedMaterial || !this.isPositive(this.purchaseForm.quantity) || this.savingStock) return;
    if (this.purchaseForm.unitCost !== null && this.purchaseForm.unitCost !== '' && !this.isNonNegative(this.purchaseForm.unitCost)) {
      this.toast.show('Unit cost must be zero or greater.', 'error');
      return;
    }
    this.savingStock = true;
    this.api.purchaseStock({
      materialId: this.selectedMaterial.id,
      quantity: Number(this.purchaseForm.quantity),
      unitCost: this.purchaseForm.unitCost === null || this.purchaseForm.unitCost === '' ? undefined : Number(this.purchaseForm.unitCost),
      vendorId: this.purchaseForm.vendorId ? Number(this.purchaseForm.vendorId) : undefined
    }).subscribe({
      next: () => {
        this.savingStock = false;
        this.toast.show('Stock recorded', 'success');
        this.closeDetailModal();
        this.loadMaterials();
      },
      error: (err) => { this.savingStock = false; this.toast.show(err?.error?.error || 'Failed to record stock-in.', 'error'); this.cdr.markForCheck(); }
    });
  }

  submitWastage(): void {
    if (!this.selectedMaterial || !this.isPositive(this.wastageForm.quantity) || !this.wastageForm.reason?.trim() || this.savingStock) return;
    this.savingStock = true;
    this.api.recordWastage({
      materialId: this.selectedMaterial.id,
      quantity: Number(this.wastageForm.quantity),
      reason: this.wastageForm.reason.trim()
    }).subscribe({
      next: () => {
        this.savingStock = false;
        this.toast.show('Wastage recorded', 'success');
        this.closeDetailModal();
        this.loadMaterials();
      },
      error: (err) => { this.savingStock = false; this.toast.show(err?.error?.error || 'Failed to record wastage.', 'error'); this.cdr.markForCheck(); }
    });
  }

  submitPhysicalCount(): void {
    if (!this.selectedMaterial || !this.isNonNegative(this.countForm.countedQty) || this.savingStock) return;
    this.savingStock = true;
    this.api.physicalCount({
      materialId: this.selectedMaterial.id,
      countedQty: this.countForm.countedQty
    }).subscribe({
      next: (res: any) => {
        this.savingStock = false;
        const variance = res?.variance ?? 0;
        this.toast.show(
          variance === 0 ? 'Count matches system' : `Variance: ${variance > 0 ? '+' : ''}${variance} ${this.selectedMaterial.unit}`,
          variance === 0 ? 'success' : 'error'
        );
        this.closeDetailModal();
        this.loadMaterials();
      },
      error: (err) => { this.savingStock = false; this.toast.show(err?.error?.error || 'Failed to submit physical count.', 'error'); this.cdr.markForCheck(); }
    });
  }

  loadMovements(): void {
    if (!this.selectedMaterialId) { this.movements = []; return; }
    this.api.getStockMovements(this.selectedMaterialId).subscribe({
      next: (data) => { this.movements = data; this.cdr.markForCheck(); },
      error: () => { this.movements = []; this.cdr.markForCheck(); }
    });
  }

  loadVariance(): void {
    if (!this.varianceFrom || !this.varianceTo) return;
    this.api.getInventoryVariance(this.varianceFrom, this.varianceTo).subscribe({
      next: (data) => { this.varianceData = Array.isArray(data) ? data : []; this.cdr.markForCheck(); },
      error: () => { this.varianceData = []; this.cdr.markForCheck(); }
    });
  }

  formatMovementType(kind: string): string {
    const map: Record<string, string> = {
      PURCHASE: 'Purchase', WASTAGE: 'Wastage', SALES_DEDUCT: 'Sales', ADJUST: 'Adjustment', OPENING: 'Opening'
    };
    return map[kind] || kind;
  }

  getUnit(materialId: number | null): string {
    if (!materialId) return '';
    return this.materials.find(m => m.id === materialId)?.unit || '';
  }

  onConfirm(): void {
    this.confirmVisible = false;
    this.confirmCallback?.();
    this.confirmCallback = null;
  }

  trackByIndex = (_: number, __: unknown) => _;
  trackByMaterialId = (_: number, m: any) => m.id;
  trackByVendorId = (_: number, v: any) => v.id;
}
