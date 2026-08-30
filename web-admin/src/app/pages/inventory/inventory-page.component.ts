import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-inventory-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent, ApiStateComponent],
  styles: [`
    .page-header {
      display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem;
    }
    .page-header h2 { margin: 0; }
    .card { background: var(--kb-color-card); border: 1px solid var(--kb-color-border); border-radius: 10px; padding: 1rem 1.25rem; margin-bottom: 1rem; }
    .materials-grid {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 0.75rem;
    }
    .material-card {
      background: var(--kb-color-card); border: 1px solid var(--kb-color-border); border-radius: 8px;
      padding: 0.85rem 1rem; display: flex; flex-direction: column; gap: 0.25rem; cursor: pointer;
      transition: border-color 0.15s, box-shadow 0.15s;
    }
    .material-card:hover { border-color: var(--kb-color-primary); box-shadow: 0 0 0 1px var(--kb-color-primary); }
    .material-card.low-stock { border-left: 3px solid var(--kb-color-warning, #f59e0b); }
    .material-name { font-weight: 600; font-size: 0.95rem; }
    .material-stock { font-size: 0.85rem; color: var(--kb-color-muted); }
    .material-stock strong { color: var(--kb-color-foreground); }
    .low-label { color: var(--kb-color-error); font-weight: 600; font-size: 0.75rem; }
    .chip { display: inline-block; padding: 0.15rem 0.5rem; border-radius: 999px; font-size: 0.75rem; font-weight: 500; }
    .chip-green { background: rgba(34,197,94,0.12); color: #16a34a; }
    .chip-amber { background: rgba(245,158,11,0.12); color: #d97706; }
    .chip-red { background: rgba(239,68,68,0.12); color: #dc2626; }
    .modal-content { width: 100%; max-width: 480px; }
    .modal-content h3 { margin: 0 0 1.25rem; }
    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; margin-bottom: 0.25rem; font-size: 0.875rem; font-weight: 500; }
    .form-group .field-control { width: 100%; box-sizing: border-box; }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
    .tabs { display: flex; gap: 0.25rem; margin-bottom: 1.25rem; border-bottom: 1px solid var(--kb-color-border); }
    .tab { padding: 0.5rem 1rem; font-size: 0.85rem; font-weight: 500; cursor: pointer; border: none; background: none; color: var(--kb-color-muted); border-bottom: 2px solid transparent; transition: all 0.15s; }
    .tab:hover { color: var(--kb-color-foreground); }
    .tab.active { color: var(--kb-color-primary); border-bottom-color: var(--kb-color-primary); }
    .movement-row { display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0; border-bottom: 1px solid var(--kb-color-border); font-size: 0.85rem; }
    .movement-type { font-weight: 500; }
    .movement-qty { font-family: monospace; }
    .movement-qty.positive { color: #16a34a; }
    .movement-qty.negative { color: #dc2626; }
    .action-bar { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--kb-color-border); }
    .table-wrap { overflow-x: auto; }
    table.styled { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
    table.styled th { text-align: left; padding: 0.6rem 0.75rem; font-weight: 600; border-bottom: 2px solid var(--kb-color-border); white-space: nowrap; }
    table.styled td { padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--kb-color-border); }
    table.styled tr:hover td { background: rgba(0,0,0,0.02); }
  `],
  template: `
    <div style="padding:0">
      <div class="page-header">
        <h2>Inventory</h2>
        <div style="display:flex;gap:0.5rem">
          <button class="ghost-btn" (click)="loadMaterials()" [disabled]="loading">Refresh</button>
          <button class="primary-btn" (click)="openAddMaterial()">+ Add Material</button>
        </div>
      </div>

      <!-- Tabs -->
      <div class="tabs">
        <button class="tab" [class.active]="activeTab === 'materials'" (click)="activeTab='materials'">Materials</button>
        <button class="tab" [class.active]="activeTab === 'movements'" (click)="activeTab='movements'">Stock Movements</button>
        <button class="tab" [class.active]="activeTab === 'variance'" (click)="activeTab='variance'">Variance Report</button>
      </div>

      <app-api-state [loading]="loading" [error]="error"></app-api-state>

      <!-- Materials Tab -->
      <div *ngIf="activeTab === 'materials' && !loading && !error">
        <div *ngIf="materials.length === 0">
          <app-empty-state
            title="No materials"
            description="Add raw materials to track stock levels."
            icon="inventory_2"
            (action)="openAddMaterial()">
          </app-empty-state>
        </div>

        <div *ngIf="materials.length > 0" class="materials-grid">
          <div *ngFor="let m of materials"
               class="material-card"
               [class.low-stock]="isLowStock(m)"
               (click)="openMaterialDetail(m)">
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
          </div>
        </div>
      </div>

      <!-- Movements Tab -->
      <div *ngIf="activeTab === 'movements' && !loading && !error">
        <div style="margin-bottom:1rem">
          <select class="field-select" [(ngModel)]="selectedMaterialId" (change)="loadMovements()" style="max-width:300px">
            <option [ngValue]="null">Select a material...</option>
            <option *ngFor="let m of materials" [ngValue]="m.id">{{ m.name }}</option>
          </select>
        </div>

        <div *ngIf="selectedMaterialId && movements.length === 0" class="card">
          <p style="margin:0;color:var(--kb-color-muted)">No movements recorded yet.</p>
        </div>

        <div *ngIf="movements.length > 0" class="card">
          <div *ngFor="let mv of movements" class="movement-row">
            <div>
              <span class="movement-type">{{ formatMovementType(mv.kind) }}</span>
              <span style="margin-left:0.5rem;color:var(--kb-color-muted);font-size:0.8rem">
                {{ mv.createdAt | date:'short' }}
              </span>
              <span *ngIf="mv.reason" style="margin-left:0.5rem;font-size:0.8rem;color:var(--kb-color-muted)">
                — {{ mv.reason }}
              </span>
            </div>
            <span class="movement-qty" [class.positive]="mv.quantity > 0" [class.negative]="mv.quantity < 0">
              {{ mv.quantity > 0 ? '+' : '' }}{{ mv.quantity }} {{ getUnit(selectedMaterialId) }}
            </span>
          </div>
        </div>

        <div *ngIf="!selectedMaterialId" class="card">
          <p style="margin:0;color:var(--kb-color-muted)">Select a material to view its stock movement history.</p>
        </div>
      </div>

      <!-- Variance Tab -->
      <div *ngIf="activeTab === 'variance' && !loading && !error">
        <div class="card">
          <div style="display:flex;gap:1rem;align-items:end;margin-bottom:1rem;flex-wrap:wrap">
            <div class="form-group" style="margin:0">
              <label>From</label>
              <input class="field-control" type="date" [(ngModel)]="varianceFrom" />
            </div>
            <div class="form-group" style="margin:0">
              <label>To</label>
              <input class="field-control" type="date" [(ngModel)]="varianceTo" />
            </div>
            <button class="primary-btn" (click)="loadVariance()" [disabled]="!varianceFrom || !varianceTo">Run Report</button>
          </div>

          <div *ngIf="varianceData.length > 0">
            <table class="styled">
              <thead>
                <tr>
                  <th>Material</th>
                  <th>System Stock</th>
                  <th>Physical Count</th>
                  <th>Variance</th>
                  <th>Variance %</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let v of varianceData">
                  <td>{{ v.materialName }}</td>
                  <td>{{ v.systemStock }}</td>
                  <td>{{ v.physicalCount }}</td>
                  <td [style.color]="v.variance < 0 ? '#dc2626' : v.variance > 0 ? '#16a34a' : ''">
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
        <div class="modal-box modal-content" (click)="$event.stopPropagation()">
          <h3>{{ editingMaterial ? 'Edit Material' : 'Add Material' }}</h3>

          <div class="form-group">
            <label>Name *</label>
            <input class="field-control" type="text" [(ngModel)]="materialForm.name" placeholder="e.g. Basmati Rice" />
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
            <div class="form-group">
              <label>Stock Quantity</label>
              <input class="field-control" type="number" [(ngModel)]="materialForm.stockQuantity" min="0" step="0.01" />
            </div>
          </div>

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
            <button class="ghost-btn" (click)="closeMaterialModal()">Cancel</button>
            <button class="primary-btn" (click)="saveMaterial()" [disabled]="!materialForm.name?.trim()">
              {{ editingMaterial ? 'Update' : 'Add Material' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Material Detail Modal (stock actions) -->
      <div class="modal-backdrop" *ngIf="showDetailModal" (click)="closeDetailModal()">
        <div class="modal-box modal-content" style="max-width:520px" (click)="$event.stopPropagation()">
          <h3>{{ selectedMaterial?.name }}</h3>
          <p style="margin:0 0 1rem;color:var(--kb-color-muted)">
            Current stock: <strong>{{ selectedMaterial?.stockQuantity }} {{ selectedMaterial?.unit }}</strong>
            <span *ngIf="selectedMaterial?.costPerUnit"> · Cost: ₹{{ selectedMaterial?.costPerUnit | number:'1.2-2' }}/{{ selectedMaterial?.unit }}</span>
          </p>

          <!-- Action sub-tabs -->
          <div class="tabs" style="margin-bottom:1rem">
            <button class="tab" [class.active]="detailTab === 'purchase'" (click)="detailTab='purchase'">Stock In</button>
            <button class="tab" [class.active]="detailTab === 'wastage'" (click)="detailTab='wastage'">Wastage</button>
            <button class="tab" [class.active]="detailTab === 'count'" (click)="detailTab='count'">Physical Count</button>
          </div>

          <!-- Purchase / Stock In -->
          <div *ngIf="detailTab === 'purchase'">
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
            <button class="primary-btn" (click)="submitPurchase()" [disabled]="!purchaseForm.quantity">Record Purchase</button>
          </div>

          <!-- Wastage -->
          <div *ngIf="detailTab === 'wastage'">
            <div class="form-group">
              <label>Quantity *</label>
              <input class="field-control" type="number" [(ngModel)]="wastageForm.quantity" min="0.01" step="0.01" />
            </div>
            <div class="form-group">
              <label>Reason *</label>
              <input class="field-control" type="text" [(ngModel)]="wastageForm.reason" placeholder="e.g. Expired, spillage" />
            </div>
            <button class="primary-btn" (click)="submitWastage()" [disabled]="!wastageForm.quantity || !wastageForm.reason?.trim()">Record Wastage</button>
          </div>

          <!-- Physical Count -->
          <div *ngIf="detailTab === 'count'">
            <div class="form-group">
              <label>Counted Quantity *</label>
              <input class="field-control" type="number" [(ngModel)]="countForm.countedQty" min="0" step="0.01" />
            </div>
            <p style="margin:0 0 1rem;font-size:0.8rem;color:var(--kb-color-muted)">
              System shows {{ selectedMaterial?.stockQuantity }} {{ selectedMaterial?.unit }}. Any variance will create an adjustment entry.
            </p>
            <button class="primary-btn" (click)="submitPhysicalCount()" [disabled]="countForm.countedQty === null">Submit Count</button>
          </div>

          <div class="modal-actions">
            <button class="ghost-btn" (click)="closeDetailModal()">Close</button>
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

  loading = true;
  error: string | null = null;

  activeTab: 'materials' | 'movements' | 'variance' = 'materials';
  materials: any[] = [];

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
  }

  loadMaterials(): void {
    this.loading = true;
    this.api.getInventoryMaterials().subscribe({
      next: (data) => { this.materials = data; this.loading = false; },
      error: (err) => { this.error = err?.error?.error || 'Failed to load materials'; this.loading = false; }
    });
  }

  isLowStock(m: any): boolean {
    return m.lowStockThreshold > 0 && m.stockQuantity <= m.lowStockThreshold;
  }

  openAddMaterial(): void {
    this.editingMaterial = null;
    this.materialForm = { name: '', unit: 'kg', stockQuantity: 0, lowStockThreshold: 0, costPerUnit: null };
    this.showMaterialModal = true;
  }

  openMaterialDetail(m: any): void {
    this.selectedMaterial = m;
    this.detailTab = 'purchase';
    this.purchaseForm = { quantity: null, unitCost: m.costPerUnit || null };
    this.wastageForm = { quantity: null, reason: '' };
    this.countForm = { countedQty: null };
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
    const payload = { ...this.materialForm, name: this.materialForm.name.trim() };

    if (this.editingMaterial) {
      this.api.updateMaterial(this.editingMaterial.id, payload).subscribe({
        next: () => { this.closeMaterialModal(); this.loadMaterials(); this.toast.show('Material updated', 'success'); },
        error: (err) => { this.toast.show(err?.error?.error || 'Failed to update', 'error'); }
      });
    } else {
      this.api.createMaterial(payload).subscribe({
        next: () => { this.closeMaterialModal(); this.loadMaterials(); this.toast.show('Material added', 'success'); },
        error: (err) => { this.toast.show(err?.error?.error || 'Failed to add', 'error'); }
      });
    }
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
    if (!this.selectedMaterial || !this.purchaseForm.quantity) return;
    this.api.purchaseStock({
      materialId: this.selectedMaterial.id,
      quantity: this.purchaseForm.quantity,
      unitCost: this.purchaseForm.unitCost || undefined
    }).subscribe({
      next: () => {
        this.toast.show('Stock recorded', 'success');
        this.closeDetailModal();
        this.loadMaterials();
      },
      error: (err) => { this.toast.show(err?.error?.error || 'Failed to record', 'error'); }
    });
  }

  submitWastage(): void {
    if (!this.selectedMaterial || !this.wastageForm.quantity || !this.wastageForm.reason?.trim()) return;
    this.api.recordWastage({
      materialId: this.selectedMaterial.id,
      quantity: this.wastageForm.quantity,
      reason: this.wastageForm.reason.trim()
    }).subscribe({
      next: () => {
        this.toast.show('Wastage recorded', 'success');
        this.closeDetailModal();
        this.loadMaterials();
      },
      error: (err) => { this.toast.show(err?.error?.error || 'Failed to record', 'error'); }
    });
  }

  submitPhysicalCount(): void {
    if (!this.selectedMaterial || this.countForm.countedQty === null) return;
    this.api.physicalCount({
      materialId: this.selectedMaterial.id,
      countedQty: this.countForm.countedQty
    }).subscribe({
      next: (res: any) => {
        const variance = res?.variance ?? 0;
        this.toast.show(
          variance === 0 ? 'Count matches system' : `Variance: ${variance > 0 ? '+' : ''}${variance} ${this.selectedMaterial.unit}`,
          variance === 0 ? 'success' : 'error'
        );
        this.closeDetailModal();
        this.loadMaterials();
      },
      error: (err) => { this.toast.show(err?.error?.error || 'Failed to submit', 'error'); }
    });
  }

  loadMovements(): void {
    if (!this.selectedMaterialId) { this.movements = []; return; }
    this.api.getStockMovements(this.selectedMaterialId).subscribe({
      next: (data) => { this.movements = data; },
      error: () => { this.movements = []; }
    });
  }

  loadVariance(): void {
    if (!this.varianceFrom || !this.varianceTo) return;
    this.api.getInventoryVariance(this.varianceFrom, this.varianceTo).subscribe({
      next: (data) => { this.varianceData = Array.isArray(data) ? data : []; },
      error: () => { this.varianceData = []; }
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
}
