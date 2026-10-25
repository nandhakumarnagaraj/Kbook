import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessStaffItem } from '../../core/models/api.models';
import { FocusTrapDirective } from '../../core/components/focus-trap.directive';

@Component({
  selector: 'app-staff-permissions-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, FocusTrapDirective],
  styles: [`
    .modal-content { width: 100%; max-width: 640px; }
    .role-disabled-note {
      font-size: var(--kb-text-xs); color: var(--kb-color-muted-foreground);
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

    /* ── Permissions dialog layout ──
       These declarations used to be inline style attributes. Values are
       unchanged; the carrier moved into the component so the stylesheet
       checks can see them. The .row/.row--between utilities supply
       arrangement only; each class below owns its own rhythm. */
    .permissions-heading { margin: 0 0 var(--kb-space-2); }
    .permissions-role { margin: 0 0 var(--kb-space-5); font-size: var(--kb-text-md); }
    .permissions-fixed { margin-bottom: var(--kb-space-4); }
    .permissions-fixed-note { margin: 0 0 var(--kb-space-3); }
    .permissions-category-group { margin-bottom: 0.6rem; }
    .permissions-chip-row {
      display: flex;
      flex-wrap: wrap;
      gap: var(--kb-space-1) var(--kb-space-2);
      align-items: baseline;
    }
    .permissions-category-name {
      font-size: var(--kb-text-xs);
      color: var(--kb-color-primary);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-right: var(--kb-space-1);
    }
    .permissions-denied { text-decoration: line-through; }
    .permissions-template-actions {
      display: flex;
      gap: var(--kb-space-2);
      flex-wrap: wrap;
      margin-bottom: var(--kb-space-4);
    }
    .permissions-template-btn { font-size: var(--kb-text-sm); }
    .permissions-template-btn--dashed { border-style: dashed; }
    .permissions-templates { margin-bottom: var(--kb-space-4); }
    .permissions-section-label {
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .permissions-row { padding: 0.4rem 0; border-bottom: 1px solid var(--line); }
    .permissions-template-name { font-size: var(--kb-text-md); font-weight: 500; }
    .permissions-template-meta {
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
      margin-left: var(--kb-space-2);
    }
    .permissions-template-apply { font-size: var(--kb-text-xs); }
    .permissions-category { margin-bottom: var(--kb-space-4); }
    .permissions-category-head {
      padding: 0.3rem 0;
      border-bottom: 2px solid var(--kb-color-primary);
      margin-bottom: var(--kb-space-1);
    }
    .permissions-category-toggle {
      font-size: var(--kb-text-sm);
      color: var(--kb-color-primary);
      cursor: pointer;
    }
    .permissions-perm-name { font-size: var(--kb-text-md); }
    .permissions-create-modal { max-width: 400px; }
    .permissions-create-heading { margin: 0 0 var(--kb-space-4); }
    .permissions-create-note { font-size: var(--kb-text-sm); margin-bottom: var(--kb-space-4); }
  `],
  template: `
    <div class="modal-backdrop" *ngIf="open()" appFocusTrap (escape)="close.emit()" (click)="close.emit()">
      <div class="modal-box modal-content" role="dialog" aria-modal="true" (click)="$event.stopPropagation()">
        <h3 class="permissions-heading">Permissions — {{ staff()?.name }}</h3>
        <p class="muted permissions-role">
          Role: <span class="chip">{{ staff()?.role }}</span>
        </p>

        <div *ngIf="permissionsLoading()" class="loading">Loading permissions...</div>

        <div class="permissions-fixed" *ngIf="!permissionsLoading() && isRoleFixed()">
          <p class="role-disabled-note permissions-fixed-note">
            Staff permissions are fixed by the <strong>Sales Staff</strong> role and cannot be changed per member.
          </p>
          <div class="permissions-category-group" *ngFor="let cat of permissionCategories; trackBy: trackByIndex">
            <div class="permissions-chip-row">
              <strong class="permissions-category-name">{{ cat.name }}:</strong>
              <span *ngFor="let perm of cat.items; trackBy: trackByIndex">
                <span class="chip" *ngIf="permissionsSet().has(perm.key)">{{ perm.displayName }}</span>
                <span class="muted permissions-denied" *ngIf="!permissionsSet().has(perm.key)">{{ perm.displayName }}</span>
              </span>
            </div>
          </div>
        </div>

        <div *ngIf="!permissionsLoading() && !isRoleFixed()">
          <div class="permissions-template-actions">
            <button class="ghost-btn permissions-template-btn" (click)="applyCounterStaffTemplate()">Counter Staff</button>
            <button class="ghost-btn permissions-template-btn" (click)="applyManagerTemplate()">Manager</button>
            <button class="ghost-btn permissions-template-btn permissions-template-btn--dashed" (click)="showCreateTemplate.set(true)">+ Save as Template</button>
          </div>

          <div class="permissions-templates" *ngIf="roleTemplates.length > 0">
            <strong class="permissions-section-label">Saved Templates</strong>
            <div class="row row--between permissions-row" *ngFor="let tpl of roleTemplates; trackBy: trackByIndex">
              <div>
                <span class="permissions-template-name">{{ tpl.name }}</span>
                <span class="permissions-template-meta">{{ tpl.permissions?.length || 0 }} permissions</span>
              </div>
              <button class="ghost-btn permissions-template-apply" (click)="applyServerTemplate(tpl)">Apply</button>
            </div>
          </div>

          <div class="permissions-category" *ngFor="let cat of permissionCategories; trackBy: trackByIndex">
            <div class="row row--between permissions-category-head">
              <strong class="permissions-category-toggle" (click)="toggleCategory(cat.name, cat.items)">
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
            <div class="row row--between permissions-row" *ngFor="let perm of cat.items; trackBy: trackByIndex">
              <span class="permissions-perm-name">{{ perm.displayName }}</span>
              <label class="toggle-switch">
                <input type="checkbox" [checked]="permissionsSet().has(perm.key)" (change)="togglePermission(perm.key)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
        </div>

        <div class="modal-actions" *ngIf="!permissionsLoading()">
          <button class="ghost-btn" (click)="close.emit()">{{ isRoleFixed() ? 'Close' : 'Cancel' }}</button>
          <button class="primary-btn" *ngIf="!isRoleFixed()" (click)="savePermissions()" [disabled]="permissionsSaving()">
            {{ permissionsSaving() ? 'Saving...' : 'Save Permissions' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Create Template Dialog -->
    <div class="modal-backdrop" *ngIf="showCreateTemplate()" appFocusTrap (escape)="showCreateTemplate.set(false)" (click)="showCreateTemplate.set(false)">
      <div class="modal-box modal-content permissions-create-modal" role="dialog" (click)="$event.stopPropagation()">
        <h3 class="permissions-create-heading">Save as Role Template</h3>
        <div class="form-group">
          <label>Template Name *</label>
            <!-- The template ref is what gives this field a touched state: the
                 app's other forms are reactive and get theirs from the control,
                 this one only has ngModel. It flags on the rule the Save button
                 actually applies — a name that is only whitespace is not a name. -->
          <input
            class="field-control"
            type="text"
            #templateNameRef="ngModel"
            [(ngModel)]="templateName"
            [attr.aria-invalid]="templateNameRef.touched && !templateName.trim()"
            [attr.aria-describedby]="templateNameRef.touched && !templateName.trim() ? 'template-name-error' : null"
            placeholder="e.g. Senior Cashier" />
          <div class="field-error" id="template-name-error" *ngIf="templateNameRef.touched && !templateName.trim()">Template name is required.</div>
        </div>
        <div class="form-group">
          <label>Description</label>
          <input class="field-control" type="text" [(ngModel)]="templateDescription" placeholder="Optional description" />
        </div>
        <p class="muted permissions-create-note">
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

  /**
   * SHOP_STAFF access is fixed by role (mirrors server PermissionService).
   * The owner cannot grant or revoke individual permissions for staff members,
   * so the editor is read-only for them.
   */
  isRoleFixed(): boolean {
    return this.staff()?.role === 'SHOP_STAFF';
  }

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
      'reports.day_summary', 'orders.view'
    ]));
  }

  applyManagerTemplate(): void {
    this.permissionsSet.set(new Set([
      'billing.create', 'billing.edit', 'billing.void', 'billing.discount',
      'billing.refund', 'billing.settle', 'menu.view', 'menu.toggle_availability',
      'menu.edit_price', 'menu.add_item', 'reports.day_summary', 'reports.full',
      'orders.view', 'staff.view'
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
