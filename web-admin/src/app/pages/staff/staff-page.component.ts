import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessStaffItem } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { ToastService } from '../../core/services/toast.service';
import { formatDate } from '../../shared/formatters';
import { StaffFormModalComponent } from './staff-form-modal.component';
import { StaffPermissionsModalComponent } from './staff-permissions-modal.component';

@Component({
  selector: 'app-staff-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent, ApiStateComponent, StaffFormModalComponent, StaffPermissionsModalComponent],
  styles: [`
    .action-cell {
      display: flex; gap: 0.5rem; align-items: center;
    }
    .action-btn {
      padding: var(--kb-space-2) var(--kb-space-3); border-radius: var(--kb-radius-lg);
      font-size: 0.78rem; font-weight: 500; cursor: pointer;
      border: 1px solid var(--kb-color-border); background: transparent;
      color: var(--kb-color-foreground); transition: background 0.15s ease, transform 0.12s var(--ease-out, ease-out);
    }
    .action-btn:hover:not(:disabled) { background: var(--kb-color-surface-2); }
    .action-btn:active:not(:disabled) { transform: scale(0.96); }
    .action-btn--danger { color: var(--kb-color-error); border-color: var(--danger); }
    .action-btn--danger:hover:not(:disabled) { background: rgba(239, 68, 68, 0.06); }
    .action-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .tooltip-wrapper { position: relative; display: inline-block; }
    .tooltip-wrapper .tooltip-text {
      visibility: hidden; position: absolute; bottom: 100%; left: 50%;
      transform: translateX(-50%); background: var(--kb-color-foreground); color: #fff;
      font-size: 0.7rem; padding: var(--kb-space-1) var(--kb-space-2);
      border-radius: var(--kb-radius-md); white-space: nowrap; z-index: 10;
      margin-bottom: var(--kb-space-1);
    }
    .tooltip-wrapper:hover .tooltip-text { visibility: visible; }
  `],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Staff</h2>
        <p class="muted">Team directory with better spacing for roles, status, and contact details.</p>
        <div class="hero-meta">
          <span class="chip">Access Review</span>
          <span class="chip success">Team Health</span>
        </div>
      </section>

      <div class="toolbar">
        <div>
          <h3>Staff Directory</h3>
          <p class="muted">Check role coverage and inactive accounts without scanning cramped rows.</p>
        </div>
        <div style="display: flex; gap: 0.5rem; align-items: center;">
          <button class="primary-btn" *ngIf="isOwner" (click)="openCreateModal()">Add Staff</button>
          <button class="ghost-btn" (click)="loadStaff()">Refresh</button>
        </div>
      </div>

      <app-api-state
        *ngIf="loadError()"
        [loading]="false"
        [error]="loadError()"
        (retry)="loadStaff()"
      ></app-api-state>

      <!-- Staff Form Modal (Create/Edit) -->
      <app-staff-form-modal
        [open]="showFormModal()"
        [isEdit]="formMode() === 'edit'"
        [editItem]="editingStaff()"
        [disableRoleForSelf]="editingSelf()"
        (close)="closeFormModal()"
        (saved)="loadStaff()"
      />

      <!-- Deactivate Confirmation Dialog -->
      <app-confirm-dialog
        *ngIf="staffToDeactivate"
        title="Deactivate Staff Member"
        [message]="'Are you sure you want to deactivate ' + staffToDeactivate.name + '? They will lose access immediately.'"
        confirmLabel="Deactivate"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="confirmDeactivate()"
        (cancelled)="cancelDeactivate()"
      ></app-confirm-dialog>

      <section class="panel filter-panel" *ngIf="loaded() && staff().length">
        <div class="filter-grid">
          <div class="filter-group">
            <label for="staff-search">Search</label>
            <input
              id="staff-search"
              class="field-control"
              type="text"
              [(ngModel)]="searchTerm"
              (ngModelChange)="resetPage()"
              placeholder="Search by name, login, email, or phone"
            />
          </div>
          <div class="filter-group">
            <label for="staff-role">Role</label>
            <select id="staff-role" class="field-select" [(ngModel)]="roleFilter" (ngModelChange)="resetPage()">
              <option value="ALL">All roles</option>
              <option *ngFor="let role of roleOptions; trackBy: trackByIndex" [value]="role">{{ role }}</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="staff-status">Status</label>
            <select id="staff-status" class="field-select" [(ngModel)]="statusFilter" (ngModelChange)="resetPage()">
              <option value="ALL">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="staff-size">Rows</label>
            <select id="staff-size" class="field-select" [(ngModel)]="pageSize" (ngModelChange)="resetPage()">
              <option [ngValue]="5">5</option>
              <option [ngValue]="10">10</option>
              <option [ngValue]="20">20</option>
            </select>
          </div>
        </div>

        <div class="filter-summary">
          <p class="muted">{{ filteredStaff.length }} of {{ staff().length }} staff members</p>
          <button class="ghost-btn" (click)="clearFilters()">Clear filters</button>
        </div>
      </section>

      <div class="panel table-wrap" *ngIf="loaded() && pagedStaff.length; else loading">
        <table class="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Login ID</th>
              <th>Role</th>
              <th>Contact</th>
              <th>Status</th>
              <th>Updated</th>
              <th *ngIf="isOwner">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let item of pagedStaff; trackBy: trackByUserId">
              <td>{{ item.name }}</td>
              <td>{{ item.loginId }}</td>
              <td><span class="chip-pill" [class.chip-pill--ok]="item.role === 'OWNER'">{{ item.role }}</span></td>
              <td>{{ item.whatsappNumber || item.email || '-' }}</td>
              <td>
                <span class="chip-pill" [class.chip-pill--ok]="item.active" [class.chip-pill--pending]="!item.active">
                  {{ item.active ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td>{{ formatDateValue(item.updatedAt) }}</td>
              <td *ngIf="isOwner">
                <div class="action-cell">
                  <button class="action-btn" (click)="openEditModal(item)">Edit</button>
                  <button class="action-btn" (click)="openPermissionsModal(item)" *ngIf="!isSelf(item) && item.role !== 'OWNER'">Permissions</button>
                  <button
                    class="action-btn action-btn--success"
                    *ngIf="!item.active && !isSelf(item)"
                    (click)="activateStaff(item)"
                  >Activate</button>
                  <span class="tooltip-wrapper" *ngIf="isSelf(item); else deactivateEnabled">
                    <button class="action-btn action-btn--danger" disabled>Deactivate</button>
                    <span class="tooltip-text">Cannot deactivate yourself</span>
                  </span>
                  <ng-template #deactivateEnabled>
                    <button
                      class="action-btn action-btn--danger"
                      [disabled]="!item.active"
                      (click)="requestDeactivate(item)"
                    >Deactivate</button>
                  </ng-template>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="mobile-data-list" aria-label="Staff members">
          <article class="mobile-data-card" *ngFor="let item of pagedStaff; trackBy: trackByUserId">
            <div class="mobile-data-card__head"><strong>{{ item.name }}</strong><span class="chip" [class.success]="item.active" [class.warn]="!item.active">{{ item.active ? 'Active' : 'Inactive' }}</span></div>
            <p>{{ item.loginId }} · {{ item.whatsappNumber || item.email || 'No contact' }}</p>
            <dl><div><dt>Role</dt><dd>{{ item.role }}</dd></div><div><dt>Updated</dt><dd>{{ formatDateValue(item.updatedAt) }}</dd></div></dl>
            <div class="mobile-data-card__actions" *ngIf="isOwner">
              <button class="ghost-btn" (click)="openEditModal(item)">Edit</button>
              <button class="ghost-btn danger-btn" [disabled]="isSelf(item) || !item.active" (click)="requestDeactivate(item)">Deactivate</button>
            </div>
          </article>
        </div>

        <div class="pagination-bar" *ngIf="filteredStaff.length > pageSize">
          <p class="muted">Page {{ currentPage }} of {{ totalPages }}</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="currentPage === 1" (click)="goToPage(currentPage - 1)">Previous</button>
            <button class="ghost-btn" [disabled]="currentPage === totalPages" (click)="goToPage(currentPage + 1)">Next</button>
          </div>
        </div>
      </div>

      <ng-template #loading>
        <div class="panel loading" *ngIf="!loaded(); else staffEmpty">
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4,5]; trackBy: trackByIndex"></div>
          </div>
        </div>
        <ng-template #staffEmpty>
          <app-empty-state
            *ngIf="!loadError()"
            icon="👥"
            title="No staff match the current filters"
            text="Try a different search, role, or status filter. Owners can add a new team member."
            [actionLabel]="isOwner ? 'Add Staff' : ''"
            (action)="openCreateModal()"
          ></app-empty-state>
        </ng-template>
      </ng-template>

      <!-- Permissions Modal -->
      <app-staff-permissions-modal
        [open]="showPermissionsModal()"
        [staff]="permissionsStaff()"
        (close)="closePermissionsModal()"
        (saved)="toast.show('Permissions updated', 'success')"
      />
    </div>
  `
})
export class StaffPageComponent {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  readonly toast = inject(ToastService);

  staff = signal<BusinessStaffItem[]>([]);
  loaded = signal(false);
  loadError = signal('');

  searchTerm = '';
  roleFilter = 'ALL';
  statusFilter: 'ALL' | 'ACTIVE' | 'INACTIVE' = 'ALL';
  pageSize = 10;
  currentPage = 1;

  showFormModal = signal(false);
  formMode = signal<'create' | 'edit'>('create');
  editingStaff = signal<BusinessStaffItem | null>(null);
  editingSelf = signal(false);

  staffToDeactivate: BusinessStaffItem | null = null;

  showPermissionsModal = signal(false);
  permissionsStaff = signal<BusinessStaffItem | null>(null);

  get isOwner(): boolean {
    return this.auth.session()?.role === 'OWNER';
  }

  constructor() {
    this.loadStaff();
  }

  isSelf(item: BusinessStaffItem): boolean {
    const session = this.auth.session();
    if (!session) return false;
    return item.loginId === session.loginId;
  }

  openCreateModal(): void {
    this.formMode.set('create');
    this.editingStaff.set(null);
    this.editingSelf.set(false);
    this.showFormModal.set(true);
  }

  openEditModal(item: BusinessStaffItem): void {
    this.formMode.set('edit');
    this.editingStaff.set(item);
    this.editingSelf.set(this.isSelf(item));
    this.showFormModal.set(true);
  }

  closeFormModal(): void {
    this.showFormModal.set(false);
    this.editingStaff.set(null);
  }

  openPermissionsModal(item: BusinessStaffItem): void {
    this.permissionsStaff.set(item);
    this.showPermissionsModal.set(true);
  }

  closePermissionsModal(): void {
    this.showPermissionsModal.set(false);
    this.permissionsStaff.set(null);
  }

  requestDeactivate(item: BusinessStaffItem): void {
    if (this.isSelf(item)) return;
    this.staffToDeactivate = item;
  }

  activateStaff(item: BusinessStaffItem): void {
    this.api.activateStaff(item.userId).subscribe({
      next: () => {
        this.toast.show(`${item.name} has been reactivated`, 'success');
        this.loadStaff();
      },
      error: () => {
        this.toast.show('Failed to activate staff. Please try again.', 'error');
      }
    });
  }

  confirmDeactivate(): void {
    if (!this.staffToDeactivate) return;

    const userId = this.staffToDeactivate.userId;
    this.staffToDeactivate = null;

    this.api.deactivateStaff(userId).subscribe({
      next: () => {
        this.loadStaff();
      },
      error: () => {
        this.toast.show('Failed to deactivate the staff account. Please try again.', 'error');
      }
    });
  }

  cancelDeactivate(): void {
    this.staffToDeactivate = null;
  }

  get roleOptions(): string[] {
    return [...new Set(this.staff().map((item) => item.role))].sort();
  }

  get filteredStaff(): BusinessStaffItem[] {
    const search = this.searchTerm.trim().toLowerCase();

    return this.staff().filter((item) => {
      const matchesSearch = !search || [
        item.name,
        item.loginId,
        item.email ?? '',
        item.whatsappNumber ?? '',
        item.role
      ].some((value) => value.toLowerCase().includes(search));

      const matchesRole = this.roleFilter === 'ALL' || item.role === this.roleFilter;
      const matchesStatus =
        this.statusFilter === 'ALL' ||
        (this.statusFilter === 'ACTIVE' && item.active) ||
        (this.statusFilter === 'INACTIVE' && !item.active);

      return matchesSearch && matchesRole && matchesStatus;
    });
  }

  get pagedStaff(): BusinessStaffItem[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredStaff.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredStaff.length / this.pageSize));
  }

  loadStaff(): void {
    this.loaded.set(false);
    this.loadError.set('');
    this.api.getStaff().subscribe({
      next: (data) => {
        this.staff.set(data);
        this.loaded.set(true);
        this.currentPage = 1;
      },
      error: () => {
        this.staff.set([]);
        this.loadError.set('Unable to load staff accounts. Check your connection and try again.');
        this.loaded.set(true);
      }
    });
  }

  resetPage(): void {
    this.currentPage = 1;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.roleFilter = 'ALL';
    this.statusFilter = 'ALL';
    this.pageSize = 10;
    this.currentPage = 1;
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(1, page), this.totalPages);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }

  trackByIndex = (_: number, __: unknown) => _;
  trackByUserId = (_: number, item: BusinessStaffItem) => item.userId;
}
