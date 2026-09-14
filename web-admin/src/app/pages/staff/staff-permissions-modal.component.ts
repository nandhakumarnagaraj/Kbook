import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessStaffItem } from '../../core/models/api.models';

@Component({
  selector: 'app-staff-permissions-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule],
  styles: [`
    .modal-content { width: 100%; max-width: 560px; }
    .role-disabled-note {
      font-size: 0.75rem; color: var(--kb-color-muted-foreground);
      font-style: italic; margin-top: var(--kb-space-1);
    }
    .toggle-switch {
      position: relative; display: inline-block; width: 40px; height: 22px;
    }
    .toggle-switch input { opacity: 0; width: 0; height: 0; }
    .toggle-slider {
      position: absolute; cursor: pointer; inset: 0;
      background: var(--kb-color-border); border-radius: 22px; transition: 0.2s;
    }
    .toggle-slider::before {
      content: ""; position: absolute; width: 16px; height: 16px;
      left: 3px; bottom: 3px; background: var(--kb-color-foreground);
      border-radius: 50%; transition: 0.2s;
    }
    .toggle-switch input:checked + .toggle-slider { background: var(--kb-color-primary); }
    .toggle-switch input:checked + .toggle-slider::before { transform: translateX(18px); }
  `],
  template: `
    <div class="modal-backdrop" *ngIf="open()" (click)="close.emit()">
      <div class="modal-box modal-content" role="dialog" aria-modal="true" (click)="$event.stopPropagation()">
        <h3 style="margin:0 0 0.5rem">Permissions — {{ staff()?.name }}</h3>
        <p class="muted" style="margin:0 0 1.25rem;font-size:0.85rem">
          Role: <span class="chip">{{ staff()?.role }}</span>
        </p>

        <div *ngIf="permissionsLoading()" class="loading">Loading permissions...</div>

        <div *ngIf="!permissionsLoading()">
          <div style="margin-bottom:1rem;display:flex;gap:0.5rem;flex-wrap:wrap">
            <button class="ghost-btn" (click)="applyCounterStaffTemplate()" style="font-size:0.8rem">Counter Staff</button>
            <button class="ghost-btn" (click)="applyManagerTemplate()" style="font-size:0.8rem">Manager</button>
            <button class="ghost-btn" (click)="showCreateTemplate.set(true)" style="font-size:0.8rem;border-style:dashed">+ Save as Template</button>
          </div>

          <div *ngIf="roleTemplates.length > 0" style="margin-bottom:1rem">
            <strong style="font-size:0.75rem;color:var(--kb-color-muted-foreground);text-transform:uppercase;letter-spacing:0.05em">Saved Templates</strong>
            <div *ngFor="let tpl of roleTemplates; trackBy: trackByIndex" style="display:flex;justify-content:space-between;align-items:center;padding:0.4rem 0;border-bottom:1px solid var(--line)">
              <div>
                <span style="font-size:0.875rem;font-weight:500">{{ tpl.name }}</span>
                <span style="font-size:0.75rem;color:var(--kb-color-muted-foreground);margin-left:0.5rem">{{ tpl.permissions?.length || 0 }} permissions</span>
              </div>
              <button class="ghost-btn" style="font-size:0.75rem" (click)="applyServerTemplate(tpl)">Apply</button>
            </div>
          </div>

          <div *ngFor="let cat of permissionCategories; trackBy: trackByIndex" style="margin-bottom:1rem">
            <div style="display:flex;justify-content:space-between;align-items:center;padding:0.3rem 0;border-bottom:2px solid var(--kb-color-primary);margin-bottom:0.25rem">
              <strong style="font-size:0.8rem;color:var(--kb-color-primary);cursor:pointer" (click)="toggleCategory(cat.name, cat.items)">
                {{ cat.name }}
              </strong>
              <label class="toggle-switch">
                <input type="checkbox"
                  [checked]="isCategoryFullySelected(cat.items)"
                  [indeterminate]="isCategoryPartiallySelected(cat.items)"
                  (change)="toggleCategory(cat.name, cat.items)">
                <span class="toggle-slider"></span>
              </label>
            </div>
            <div *ngFor="let perm of cat.items; trackBy: trackByIndex" style="display:flex;justify-content:space-between;align-items:center;padding:0.4rem 0;border-bottom:1px solid var(--line)">
              <span style="font-size:0.875rem">{{ perm.displayName }}</span>
              <label class="toggle-switch">
                <input type="checkbox" [checked]="permissionsSet().has(perm.key)" (change)="togglePermission(perm.key)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
        </div>

        <div class="modal-actions" *ngIf="!permissionsLoading()">
          <button class="ghost-btn" (click)="close.emit()">Cancel</button>
          <button class="primary-btn" (click)="savePermissions()" [disabled]="permissionsSaving()">
            {{ permissionsSaving() ? 'Saving...' : 'Save Permissions' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Create Template Dialog -->
    <div class="modal-backdrop" *ngIf="showCreateTemplate()" (click)="showCreateTemplate.set(false)">
      <div class="modal-box modal-content" role="dialog" style="max-width:400px" (click)="$event.stopPropagation()">
        <h3 style="margin:0 0 1rem">Save as Role Template</h3>
        <div class="form-group">
          <label>Template Name *</label>
          <input class="field-control" type="text" [(ngModel)]="templateName" placeholder="e.g. Senior Cashier" />
        </div>
        <div class="form-group">
          <label>Description</label>
          <input class="field-control" type="text" [(ngModel)]="templateDescription" placeholder="Optional description" />
        </div>
        <p class="muted" style="font-size:0.8rem;margin-bottom:1rem">
          Will save {{ permissionsSet().size }} selected permissions as a reusable template.
        </p>
        <div class="modal-actions">
          <button class="ghost-btn" (click)="showCreateTemplate.set(false)">Cancel</button>
          <button class="primary-btn" (click)="saveTemplate()" [disabled]="!templateName.trim()">Save Template</button>
        </div>
      </div>
    </div>
  `
})
export class StaffPermissionsModalComponent {
  private readonly api = inject(BusinessApiService);
  private readonly toast = inject(ToastService);

  open = input(false);
  staff = input<BusinessStaffItem | null>(null);

  close = output<void>();
  saved = output<void>();

  permissionsLoading = signal(false);
  permissionsSaving = signal(false);
  permissionsSet = signal(new Set<string>());

  roleTemplates: any[] = [];
  showCreateTemplate = signal(false);
  templateName = '';
  templateDescription = '';

  readonly permissionCategories = [
    {
      name: 'Billing',
      items: [
        { key: 'billing.create', displayName: 'Create Bills' },
        { key: 'billing.edit', displayName: 'Edit Open Bills' },
        { key: 'billing.void', displayName: 'Cancel/Void Bills' },
        { key: 'billing.discount', displayName: 'Apply Discounts' },
        { key: 'billing.refund', displayName: 'Process Refunds' },
        { key: 'billing.settle', displayName: 'Mark Payment Received' },
      ]
    },
    {
      name: 'Menu',
      items: [
        { key: 'menu.view', displayName: 'View Menu Items' },
        { key: 'menu.toggle_availability', displayName: 'Toggle Item Availability' },
        { key: 'menu.edit_price', displayName: 'Change Prices' },
        { key: 'menu.add_item', displayName: 'Add New Items' },
        { key: 'menu.delete_item', displayName: 'Remove Items' },
      ]
    },
    {
      name: 'Orders',
      items: [
        { key: 'orders.view', displayName: 'View Order List' },
        { key: 'orders.kot_view', displayName: 'See Kitchen Queue' },
        { key: 'orders.kot_ready', displayName: 'Mark Items Ready' },
        { key: 'orders.kot_void', displayName: 'Void KOT Items' },
      ]
    },
    {
      name: 'Reports',
      items: [
        { key: 'reports.day_summary', displayName: "Today's Sales Summary" },
        { key: 'reports.full', displayName: 'Full Revenue Reports' },
        { key: 'reports.gst', displayName: 'GST/Tax Reports' },
        { key: 'reports.export', displayName: 'Export/Download Data' },
      ]
    },
    {
      name: 'Staff',
      items: [
        { key: 'staff.view', displayName: 'View Staff List' },
        { key: 'staff.add', displayName: 'Add New Staff' },
        { key: 'staff.edit', displayName: 'Edit Staff Details' },
        { key: 'staff.remove', displayName: 'Deactivate Staff' },
        { key: 'staff.permissions', displayName: 'Manage Permissions' },
      ]
    },
    {
      name: 'Settings',
      items: [
        { key: 'settings.shop_profile', displayName: 'Edit Shop Profile' },
        { key: 'settings.payment', displayName: 'Bank/UPI Settings' },
        { key: 'settings.printer', displayName: 'Printer Configuration' },
        { key: 'settings.terminal', displayName: 'Manage Devices' },
        { key: 'settings.gst', displayName: 'GST/FSSAI Settings' },
      ]
    }
  ];

  constructor() {
    // allowSignalWrites: this effect reacts to staff/open input signals and triggers
    // loadPermissions(), which synchronously sets permissionsLoading/permissionsSet
    // before the async fetch resolves. Those writes are intentional; without this flag
    // Angular throws NG0600 when the modal is instantiated.
    effect(() => {
      const s = this.staff();
      if (s && this.open()) {
        this.loadPermissions(s.userId);
        this.loadTemplates();
      }
    }, { allowSignalWrites: true });
  }

  private loadPermissions(userId: number): void {
    this.permissionsLoading.set(true);
    this.permissionsSet.set(new Set());
    this.api.getUserPermissions(userId).subscribe({
      next: (res: any) => {
        this.permissionsSet.set(new Set(res.grantedPermissions));
        this.permissionsLoading.set(false);
      },
      error: () => {
        this.permissionsLoading.set(false);
      }
    });
  }

  private loadTemplates(): void {
    this.api.getRoleTemplates().subscribe({
      next: (templates) => { this.roleTemplates = templates; },
      error: () => { this.roleTemplates = []; }
    });
  }

  togglePermission(key: string): void {
    const current = new Set(this.permissionsSet());
    if (current.has(key)) {
      current.delete(key);
    } else {
      current.add(key);
    }
    this.permissionsSet.set(current);
  }

  toggleCategory(_categoryName: string, items: { key: string }[]): void {
    const current = new Set(this.permissionsSet());
    const allSelected = items.every(i => current.has(i.key));
    items.forEach(i => {
      if (allSelected) {
        current.delete(i.key);
      } else {
        current.add(i.key);
      }
    });
    this.permissionsSet.set(current);
  }

  isCategoryFullySelected(items: { key: string }[]): boolean {
    return items.every(i => this.permissionsSet().has(i.key));
  }

  isCategoryPartiallySelected(items: { key: string }[]): boolean {
    const selected = items.filter(i => this.permissionsSet().has(i.key)).length;
    return selected > 0 && selected < items.length;
  }

  applyCounterStaffTemplate(): void {
    this.permissionsSet.set(new Set([
      'billing.create', 'billing.settle', 'menu.view', 'menu.toggle_availability',
      'reports.day_summary', 'orders.view', 'orders.kot_view'
    ]));
  }

  applyManagerTemplate(): void {
    this.permissionsSet.set(new Set([
      'billing.create', 'billing.edit', 'billing.void', 'billing.discount',
      'billing.refund', 'billing.settle', 'menu.view', 'menu.toggle_availability',
      'menu.edit_price', 'menu.add_item', 'reports.day_summary', 'reports.full',
      'orders.view', 'orders.kot_view', 'orders.kot_ready', 'staff.view'
    ]));
  }

  applyServerTemplate(template: any): void {
    if (template?.permissions) {
      this.permissionsSet.set(new Set(template.permissions));
    }
  }

  saveTemplate(): void {
    if (!this.templateName.trim()) return;
    this.api.createRoleTemplate({
      name: this.templateName.trim(),
      description: this.templateDescription.trim() || null,
      permissions: [...this.permissionsSet()]
    }).subscribe({
      next: (created) => {
        this.roleTemplates = [...this.roleTemplates, created];
        this.showCreateTemplate.set(false);
        this.templateName = '';
        this.templateDescription = '';
        this.toast.show('Template created', 'success');
      },
      error: () => {
        this.toast.show('Failed to create template', 'error');
      }
    });
  }

  savePermissions(): void {
    const s = this.staff();
    if (!s) return;
    this.permissionsSaving.set(true);
    this.api.updateUserPermissions(s.userId, [...this.permissionsSet()]).subscribe({
      next: () => {
        this.permissionsSaving.set(false);
        this.saved.emit();
        this.close.emit();
        this.toast.show('Permissions updated', 'success');
      },
      error: () => {
        this.permissionsSaving.set(false);
        this.toast.show('Failed to save permissions', 'error');
      }
    });
  }

  trackByIndex = (_: number, __: unknown) => _;
}
