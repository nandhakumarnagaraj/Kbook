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
    .operational-staff-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: var(--kb-space-3);
      margin-bottom: var(--kb-space-4);
      padding-bottom: var(--kb-space-3);
      border-bottom: 1px solid var(--kb-color-border);
      flex-wrap: wrap;
    }
    .header-title-row {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
      flex-wrap: wrap;
    }
    .header-title-row h2 {
      margin: 0;
      font-size: 1.35rem;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    .staff-count-badge {
      font-size: 0.75rem;
      font-weight: 600;
      padding: 3px 10px;
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }
    .header-sub {
      margin: 4px 0 0 0;
      font-size: 0.85rem;
      color: var(--kb-color-muted-foreground);
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
      flex-wrap: wrap;
    }
    .primary-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--kb-color-primary);
      color: var(--kb-color-primary-foreground, #ffffff);
      border: none;
      border-radius: var(--kb-radius-md);
      font-size: 0.82rem;
      font-weight: 600;
      padding: 8px 16px;
      cursor: pointer;
      transition: transform 120ms ease, opacity 120ms ease;
    }
    .primary-btn-tactile:active { transform: scale(0.97); }
    .ghost-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--kb-color-surface);
      color: var(--kb-color-foreground);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-md);
      font-size: 0.82rem;
      font-weight: 500;
      padding: 8px 14px;
      cursor: pointer;
      transition: transform 120ms ease, background-color 150ms ease;
    }
    .ghost-btn-tactile:active { transform: scale(0.97); }
    .danger-btn {
      color: #dc2626;
      border-color: rgba(239, 68, 68, 0.25);
    }
    .danger-btn:hover { background: rgba(239, 68, 68, 0.06); }
    .role-filter-strip {
      display: flex;
      gap: var(--kb-space-2);
      margin-bottom: var(--kb-space-4);
      overflow-x: auto;
      padding-bottom: 2px;
    }
    .role-tab-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border-radius: var(--kb-radius-full);
      font-size: 0.8rem;
      font-weight: 500;
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      color: var(--kb-color-muted-foreground);
      cursor: pointer;
      font-variant-numeric: tabular-nums;
      transition: transform 120ms ease, border-color 150ms ease, color 150ms ease;
      white-space: nowrap;
    }
    .role-tab-pill:active { transform: scale(0.97); }
    .role-tab-pill:hover {
      border-color: var(--kb-color-primary);
      color: var(--kb-color-foreground);
    }
    .role-tab-pill--active {
      border-color: var(--kb-color-primary);
      background: var(--kb-color-surface-2);
      color: var(--kb-color-primary);
      font-weight: 600;
    }
    .staff-user-cell {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
    }
    .user-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--kb-color-surface-2);
      color: var(--kb-color-primary);
      font-weight: 700;
      font-size: 0.76rem;
      display: flex;
      align-items: center;
      justify-content: center;
      border: 1px solid var(--kb-color-border);
      flex-shrink: 0;
    }
    .user-meta {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .self-pill {
      display: inline-block;
      padding: 1px 6px;
      border-radius: var(--kb-radius-full);
      font-size: 0.68rem;
      font-weight: 700;
      background: rgba(var(--kb-color-primary-rgb, 37, 99, 235), 0.12);
      color: var(--kb-color-primary);
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }
    .role-pill {
      display: inline-block;
      padding: 2px 8px;
      border-radius: var(--kb-radius-sm);
      font-size: 0.72rem;
      font-weight: 600;
      letter-spacing: 0.04em;
    }
    .role-pill--owner {
      background: rgba(var(--kb-color-primary-rgb, 37, 99, 235), 0.1);
      color: var(--kb-color-primary);
      border: 1px solid rgba(var(--kb-color-primary-rgb, 37, 99, 235), 0.3);
    }
    .role-pill--staff {
      background: var(--kb-color-surface-2);
      color: var(--kb-color-foreground);
      border: 1px solid var(--kb-color-border);
    }
    .status-indicator-pill {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 2px 8px;
      border-radius: var(--kb-radius-full);
      font-size: 0.72rem;
      font-weight: 600;
    }
    .indicator-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: currentColor;
    }
    .status-indicator-pill--active {
      color: #16a34a;
      background: rgba(34, 197, 94, 0.08);
      border: 1px solid rgba(34, 197, 94, 0.25);
    }
    .status-indicator-pill--inactive {
      color: var(--kb-color-muted-foreground);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
    }
    .action-cell {
      display: flex;
      gap: 0.35rem;
      align-items: center;
      flex-wrap: wrap;
    }
    .small-action-btn {
      padding: 4px 10px;
      border-radius: var(--kb-radius-md);
      font-size: 0.76rem;
      font-weight: 500;
      cursor: pointer;
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      color: var(--kb-color-foreground);
      transition: transform 120ms ease, background-color 150ms ease;
    }
    .small-action-btn:hover:not(:disabled) { background: var(--kb-color-surface-2); }
    .small-action-btn:active:not(:disabled) { transform: scale(0.97); }
    .small-action-btn--danger { color: #dc2626; border-color: rgba(239, 68, 68, 0.25); }
    .small-action-btn--danger:hover:not(:disabled) { background: rgba(239, 68, 68, 0.06); }
    .small-action-btn--success { color: #16a34a; border-color: rgba(34, 197, 94, 0.25); }
    .small-action-btn--success:hover:not(:disabled) { background: rgba(34, 197, 94, 0.06); }
    .small-action-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .tabular-num { font-variant-numeric: tabular-nums; }
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
      <!-- Operational Header (navbar.gallery standard) -->
      <header class="operational-staff-header">
        <div class="header-left">
          <div class="header-title-row">
            <h2>Staff &amp; Team Directory</h2>
            <span class="staff-count-badge" *ngIf="loaded()">
              {{ activeCount }} / {{ staff().length }} Active Accounts
            </span>
          </div>
          <p class="header-sub">Manage staff accounts, assign granular role permissions, and control multi-terminal sign-in security.</p>
        </div>
        <div class="header-right">
          <button type="button" class="primary-btn-tactile" *ngIf="isOwner" (click)="openCreateModal()">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Add Staff
          </button>
          <button
            type="button"
            class="ghost-btn-tactile danger-btn"
            *ngIf="isOwner"
            (click)="requestRevokeAll()"
            title="Emergency sign out of all devices"
          >
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18.36 6.64a9 9 0 1 1-12.73 0"/>
              <line x1="12" y1="2" x2="12" y2="12"/>
            </svg>
            Sign Out All Devices
          </button>
          <button type="button" class="ghost-btn-tactile" (click)="loadStaff()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            Refresh
          </button>
        </div>
      </header>

      <!-- Role Quick Filter Strip (bentogrids.com standard) -->
      <nav class="role-filter-strip" *ngIf="loaded() && staff().length" aria-label="Staff role filters">
        <button
          type="button"
          class="role-tab-pill"
          [class.role-tab-pill--active]="roleFilter === 'ALL'"
          (click)="setRoleFilter('ALL')">
          All Roles ({{ staff().length }})
        </button>
        <button
          *ngFor="let role of roleOptions; trackBy: trackByIndex"
          type="button"
          class="role-tab-pill"
          [class.role-tab-pill--active]="roleFilter === role"
          (click)="setRoleFilter(role)">
          {{ role }}
        </button>
      </nav>

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

      <!-- Revoke sessions for one staff member -->
      <app-confirm-dialog
        *ngIf="staffToRevoke"
        title="Sign out of all devices"
        [message]="'Sign ' + staffToRevoke.name + ' out of every device? Their account stays active — they can sign in again straight away. Use this if a device was lost.'"
        confirmLabel="Sign out"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="confirmRevoke()"
        (cancelled)="cancelRevoke()"
      ></app-confirm-dialog>

      <!-- Revoke sessions for everyone, including the current user -->
      <app-confirm-dialog
        *ngIf="showRevokeAll"
        title="Sign out all devices"
        message="Every account in this restaurant will be signed out of every device, including you. Accounts stay active and everyone can sign in again. Use this if a terminal was lost or stolen."
        confirmLabel="Sign out everyone"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="confirmRevokeAll()"
        (cancelled)="cancelRevokeAll()"
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
          <button class="ghost-btn-tactile" (click)="clearFilters()">Clear filters</button>
        </div>
      </section>

      <div class="panel table-wrap" *ngIf="loaded() && pagedStaff.length; else loading">
        <table class="data-table">
          <thead>
            <tr>
              <th>Staff Member</th>
              <th>Login ID</th>
              <th>Assigned Role</th>
              <th>Contact</th>
              <th>Status</th>
              <th>Updated</th>
              <th *ngIf="isOwner">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let item of pagedStaff; trackBy: trackByUserId">
              <td>
                <div class="staff-user-cell">
                  <div class="user-avatar">{{ getInitials(item.name) }}</div>
                  <div class="user-meta">
                    <strong>{{ item.name }}</strong>
                    <span class="self-pill" *ngIf="isSelf(item)">You</span>
                  </div>
                </div>
              </td>
              <td><code>{{ item.loginId }}</code></td>
              <td>
                <span class="role-pill" [class.role-pill--owner]="item.role === 'OWNER'" [class.role-pill--staff]="item.role !== 'OWNER'">
                  {{ item.role }}
                </span>
              </td>
              <td>{{ item.whatsappNumber || item.email || '-' }}</td>
              <td>
                <span class="status-indicator-pill" [class.status-indicator-pill--active]="item.active" [class.status-indicator-pill--inactive]="!item.active">
                  <span class="indicator-dot"></span>
                  {{ item.active ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td class="tabular-num muted">{{ formatDateValue(item.updatedAt) }}</td>
              <td *ngIf="isOwner">
                <div class="action-cell">
                  <button class="small-action-btn" (click)="openEditModal(item)">Edit</button>
                  <button class="small-action-btn" (click)="openPermissionsModal(item)" *ngIf="!isSelf(item) && item.role !== 'OWNER'">Permissions</button>
                  <button
                    class="small-action-btn"
                    *ngIf="item.active"
                    (click)="requestRevoke(item)"
                    title="Sign this person out of every device without disabling their account"
                  >Sign out</button>
                  <button
                    class="small-action-btn small-action-btn--success"
                    *ngIf="!item.active && !isSelf(item)"
                    (click)="activateStaff(item)"
                  >Activate</button>
                  <span class="tooltip-wrapper" *ngIf="isSelf(item); else deactivateEnabled">
                    <button class="small-action-btn small-action-btn--danger" disabled>Deactivate</button>
                    <span class="tooltip-text">Cannot deactivate yourself</span>
                  </span>
                  <ng-template #deactivateEnabled>
                    <button
                      class="small-action-btn small-action-btn--danger"
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
            <div class="mobile-data-card__head">
              <div class="staff-user-cell">
                <div class="user-avatar">{{ getInitials(item.name) }}</div>
                <strong>{{ item.name }}</strong>
                <span class="self-pill" *ngIf="isSelf(item)">You</span>
              </div>
              <span class="status-indicator-pill" [class.status-indicator-pill--active]="item.active" [class.status-indicator-pill--inactive]="!item.active">
                <span class="indicator-dot"></span>
                {{ item.active ? 'Active' : 'Inactive' }}
              </span>
            </div>
            <p><code>{{ item.loginId }}</code> · {{ item.whatsappNumber || item.email || 'No contact' }}</p>
            <dl><div><dt>Role</dt><dd>{{ item.role }}</dd></div><div><dt>Updated</dt><dd class="tabular-num">{{ formatDateValue(item.updatedAt) }}</dd></div></dl>
            <div class="mobile-data-card__actions" *ngIf="isOwner">
              <button class="ghost-btn-tactile" (click)="openEditModal(item)">Edit</button>
              <button class="ghost-btn-tactile danger-btn" [disabled]="isSelf(item) || !item.active" (click)="requestDeactivate(item)">Deactivate</button>
            </div>
          </article>
        </div>

        <div class="pagination-bar" *ngIf="filteredStaff.length > pageSize">
          <p class="muted">Page {{ currentPage }} of {{ totalPages }}</p>
          <div class="pagination-controls">
            <button class="ghost-btn-tactile" [disabled]="currentPage === 1" (click)="goToPage(currentPage - 1)">Previous</button>
            <button class="ghost-btn-tactile" [disabled]="currentPage === totalPages" (click)="goToPage(currentPage + 1)">Next</button>
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

  get activeCount(): number {
    return this.staff().filter((s) => s.active).length;
  }

  setRoleFilter(role: string): void {
    this.roleFilter = role;
    this.resetPage();
  }

  getInitials(name: string | null | undefined): string {
    if (!name) return 'ST';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }

  showFormModal = signal(false);
  formMode = signal<'create' | 'edit'>('create');
  editingStaff = signal<BusinessStaffItem | null>(null);
  editingSelf = signal(false);

  staffToDeactivate: BusinessStaffItem | null = null;
  staffToRevoke: BusinessStaffItem | null = null;
  showRevokeAll = false;

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

  requestRevoke(item: BusinessStaffItem): void {
    this.staffToRevoke = item;
  }

  confirmRevoke(): void {
    if (!this.staffToRevoke) return;

    const name = this.staffToRevoke.name;
    const userId = this.staffToRevoke.userId;
    this.staffToRevoke = null;

    this.api.revokeStaffSessions(userId).subscribe({
      next: () => {
        this.toast.show(`${name} has been signed out of all devices`, 'success');
        this.loadStaff();
      },
      error: () => {
        this.toast.show('Failed to sign the staff member out. Please try again.', 'error');
      }
    });
  }

  cancelRevoke(): void {
    this.staffToRevoke = null;
  }

  requestRevokeAll(): void {
    this.showRevokeAll = true;
  }

  confirmRevokeAll(): void {
    this.showRevokeAll = false;

    this.api.revokeAllSessions().subscribe({
      next: (result) => {
        // The caller's own session is revoked too, so the next request 401s and the
        // auth interceptor redirects to login. Show the count while we still can.
        this.toast.show(
          `${result.revoked} account(s) signed out of all devices. You will need to sign in again.`,
          'success'
        );
      },
      error: () => {
        this.toast.show('Failed to sign all devices out. Please try again.', 'error');
      }
    });
  }

  cancelRevokeAll(): void {
    this.showRevokeAll = false;
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
