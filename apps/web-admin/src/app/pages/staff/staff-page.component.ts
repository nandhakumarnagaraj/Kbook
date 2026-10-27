import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessStaffItem } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { HeaderSearchComponent } from '../../shared/header-search.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { ToastService } from '../../core/services/toast.service';
import { formatDate } from '../../shared/formatters';
import { StaffFormModalComponent } from './staff-form-modal.component';
import { StaffPermissionsModalComponent } from './staff-permissions-modal.component';

@Component({
  selector: 'app-staff-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent, PageHeaderComponent, ApiStateComponent, StaffFormModalComponent, StaffPermissionsModalComponent, HeaderSearchComponent, RefreshButtonComponent, PaginationBarComponent],
  styles: [`
    .danger-btn {
      color: var(--kb-color-danger);
      border-color: rgba(192, 57, 43, 0.25);
    }
    .danger-btn:hover { background: rgba(192, 57, 43, 0.06); }
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
      color: var(--kb-color-link);
      font-weight: 700;
      font-size: var(--kb-text-xs);
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
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      background: rgba(var(--kb-color-primary-rgb), 0.12);
      color: var(--kb-color-link);
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }
    .role-pill {
      display: inline-block;
      padding: 2px var(--kb-space-2);
      border-radius: var(--kb-radius-sm);
      font-size: var(--kb-text-2xs);
      font-weight: 600;
      letter-spacing: 0.04em;
    }
    .role-pill--owner {
      background: rgba(var(--kb-color-primary-rgb), 0.1);
      color: var(--kb-color-link);
      border: 1px solid rgba(var(--kb-color-primary-rgb), 0.3);
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
      padding: 2px var(--kb-space-2);
      border-radius: var(--kb-radius-full);
      font-size: var(--kb-text-2xs);
      font-weight: 600;
    }
    .indicator-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: currentColor;
    }
    .status-indicator-pill--active {
      color: var(--kb-color-success);
      background: rgba(47, 133, 90, 0.08);
      border: 1px solid rgba(47, 133, 90, 0.25);
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
      flex-wrap: nowrap;
      white-space: nowrap;
    }
    .small-action-btn {
      padding: var(--kb-space-1) 10px;
      border-radius: var(--kb-radius-md);
      font-size: var(--kb-text-xs);
      font-weight: 500;
      cursor: pointer;
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      color: var(--kb-color-foreground);
      transition: transform 150ms ease, background-color 150ms ease;
    }
    .small-action-btn:hover:not(:disabled) { background: var(--kb-color-surface-2); }
    .small-action-btn:active:not(:disabled) { transform: scale(0.97); }
    .small-action-btn--danger { color: var(--kb-color-danger); border-color: rgba(192, 57, 43, 0.25); }
    .small-action-btn--danger:hover:not(:disabled) { background: rgba(192, 57, 43, 0.06); }
    .small-action-btn--success { color: var(--kb-color-success); border-color: rgba(47, 133, 90, 0.25); }
    .small-action-btn--success:hover:not(:disabled) { background: rgba(47, 133, 90, 0.06); }
    .small-action-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .tabular-num { font-variant-numeric: tabular-nums; }
    .tooltip-wrapper { position: relative; display: inline-block; }
    .tooltip-wrapper .tooltip-text {
      visibility: hidden; position: absolute; bottom: 100%; left: 50%;
      transform: translateX(-50%); background: var(--kb-color-foreground); color: var(--kb-color-on-primary);
      font-size: var(--kb-text-2xs); padding: var(--kb-space-1) var(--kb-space-2);
      border-radius: var(--kb-radius-md); white-space: nowrap; z-index: var(--kb-z-tooltip);
      margin-bottom: var(--kb-space-1);
    }
    .tooltip-wrapper:hover .tooltip-text { visibility: visible; }
  `],
  template: `
    <div class="page-shell">
      <!-- Canonical page header (shared design system) -->
      <app-page-header
        title="Staff &amp; Team Directory"
        subtitle="Manage staff accounts and review each role’s access. Shop staff permissions are fixed by role and can’t be customized here.">
        <span kbHeaderBadge class="kpi-tag" *ngIf="loaded()">
          {{ activeCount }}/{{ staff().length }} Active Accounts
        </span>
          <app-header-search
            [(value)]="searchTerm"
            (valueChange)="resetPage()"
            placeholder="Search staff"
            ariaLabel="Search staff members"
            inputId="staff-search"
          />
          <button type="button" class="header-action-btn header-action-btn--primary" *ngIf="isOwner" (click)="openCreateModal()">
            Add Staff
          </button>
          <app-refresh-button (refresh)="loadStaff()" [loading]="refreshing()" ariaLabel="Refresh staff" />
      </app-page-header>

      <!-- Stale refresh banner (prototype pattern) -->
      <div class="stale-banner" *ngIf="loadError() && staff().length" role="status" aria-live="polite">
        <span>Couldn't update staff accounts. {{ loadError() }}</span>
        <button type="button" class="btn-retry" (click)="loadStaff()">Retry</button>
      </div>

      <app-api-state
        *ngIf="loadError() && !staff().length"
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

      <!-- Reset password for one staff member -->
      <app-confirm-dialog
        *ngIf="staffToResetPassword"
        title="Reset Staff Password"
        [message]="'Generate a new password for ' + staffToResetPassword.name + ' (' + (staffToResetPassword.whatsappNumber || staffToResetPassword.loginId) + ')? A new password will be sent to their WhatsApp number, and they will be signed out of all devices immediately.'"
        confirmLabel="Reset Password"
        cancelLabel="Cancel"
        [confirmDanger]="false"
        (confirmed)="confirmResetPassword()"
        (cancelled)="cancelResetPassword()"
      ></app-confirm-dialog>

      <!-- Revoke sessions for one staff member is the only sign-out action on
           this page: the bulk "sign out all devices" button was removed from the
           header when staff was aligned with the other page headers. -->

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
                  {{ roleLabel(item.role) }}
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
                <div class="action-cell" *ngIf="item.role !== 'OWNER'">
                  <button
                    class="small-action-btn"
                    *ngIf="item.active"
                    (click)="requestResetPassword(item)"
                    [disabled]="resettingStaffId() === item.userId"
                    title="Generate a new password and send it to staff via WhatsApp"
                    [attr.aria-label]="'Reset password for ' + item.name"
                  >
                    {{ resettingStaffId() === item.userId ? 'Resetting...' : 'Reset Password' }}
                  </button>
                  <button
                    class="small-action-btn"
                    *ngIf="item.active"
                    (click)="requestRevoke(item)"
                    title="Sign out of all devices without deactivating this account"
                    [attr.aria-label]="'Sign out ' + item.name + ' on all devices'"
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
                <span class="muted" *ngIf="item.role === 'OWNER'">—</span>
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
            <dl><div><dt>Role</dt><dd>{{ roleLabel(item.role) }}</dd></div><div><dt>Updated</dt><dd class="tabular-num">{{ formatDateValue(item.updatedAt) }}</dd></div></dl>
            <div class="mobile-data-card__actions" *ngIf="isOwner && item.role !== 'OWNER'">
              <button class="ghost-btn" *ngIf="item.active" [disabled]="resettingStaffId() === item.userId" (click)="requestResetPassword(item)">Reset Password</button>
              <button class="ghost-btn" *ngIf="item.active" (click)="requestRevoke(item)">Sign out</button>
              <button class="ghost-btn danger-btn" [disabled]="isSelf(item) || !item.active" (click)="requestDeactivate(item)">Deactivate</button>
            </div>
          </article>
        </div>

        <app-pagination-bar
          *ngIf="filteredStaff.length > 0"
          [currentPage]="currentPage"
          [pageSize]="pageSize"
          [totalItems]="filteredStaff.length"
          (pageChange)="goToPage($event)"
        />
      </div>

      <ng-template #loading>
        <div class="panel loading" *ngIf="!loaded() && !loadError(); else staffEmpty" role="status" aria-busy="true">
          <span class="visually-hidden">Loading staff members...</span>
          <div class="skeleton-stack">
            <!-- One shape per thing a staff row actually shows: avatar, name,
                 login, role, contact, status, action. The initials, the name
                 and the login ID stay invisible until the record loads. -->
            <div class="skeleton-row skeleton-row--detail" *ngFor="let i of [1,2,3,4,5]; trackBy: trackByIndex">
              <span class="skeleton skeleton-avatar"></span>
              <span class="skeleton skeleton-line skeleton-line--name"></span>
              <span class="skeleton skeleton-line skeleton-line--meta"></span>
              <span class="skeleton skeleton-line skeleton-line--chip"></span>
              <span class="skeleton skeleton-line skeleton-line--grow"></span>
              <span class="skeleton skeleton-line skeleton-line--action"></span>
            </div>
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
          <app-pagination-bar
            *ngIf="!loadError()"
            [currentPage]="1"
            [pageSize]="pageSize"
            [totalItems]="0"
          />
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
  refreshing = signal(false);
  loadError = signal('');

  searchTerm = '';
  pageSize = 10;
  currentPage = 1;

  get activeCount(): number {
    return this.staff().filter((s) => s.active).length;
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
  staffToResetPassword: BusinessStaffItem | null = null;
  resettingStaffId = signal<number | null>(null);

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

  requestResetPassword(item: BusinessStaffItem): void {
    if (this.isSelf(item) || item.role === 'OWNER' || !item.active) return;
    this.staffToResetPassword = item;
  }

  cancelResetPassword(): void {
    this.staffToResetPassword = null;
  }

  confirmResetPassword(): void {
    if (!this.staffToResetPassword) return;

    const item = this.staffToResetPassword;
    const userId = item.userId;
    const name = item.name;
    const phone = item.whatsappNumber || item.loginId;
    this.staffToResetPassword = null;
    this.resettingStaffId.set(userId);

    this.api.resendStaffCredentials(userId).subscribe({
      next: () => {
        this.resettingStaffId.set(null);
        this.toast.show(`A new password was generated and sent to ${name} (${phone}) on WhatsApp.`, 'success');
        this.loadStaff();
      },
      error: (err: unknown) => {
        this.resettingStaffId.set(null);
        const errObj = err as { error?: { message?: string } };
        const msg = errObj?.error?.message || 'Failed to reset staff password. Please try again.';
        this.toast.show(msg, 'error');
      }
    });
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

      return matchesSearch;
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
    this.refreshing.set(true);
    // Clear the table on every load, including a manual refresh, so the
    // skeletons take over and the reload is visible (as on Terminals).
    this.loaded.set(false);
    this.loadError.set('');
    this.api.getStaff().subscribe({
      next: (data) => {
        this.staff.set(data);
        this.loaded.set(true);
        this.refreshing.set(false);
        this.currentPage = 1;
      },
      error: () => {
        if (!this.staff().length) {
          this.staff.set([]);
        }
        this.loadError.set('Unable to load staff accounts. Check your connection and try again.');
        this.loaded.set(true);
        this.refreshing.set(false);
      }
    });
  }

  resetPage(): void {
    this.currentPage = 1;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.pageSize = 10;
    this.currentPage = 1;
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(1, page), this.totalPages);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }

  /**
   * Human label for a staff role. The server stores OWNER / SHOP_STAFF; those
   * are storage values, not something an owner should have to decode.
   */
  roleLabel(role: string | null | undefined): string {
    switch ((role || '').toUpperCase()) {
      case 'OWNER': return 'Owner';
      case 'SHOP_STAFF': return 'Sales Staff';
      case 'KBOOK_ADMIN': return 'Platform Admin';
      default: return role ? role.charAt(0) + role.slice(1).toLowerCase().replace(/_/g, ' ') : 'Unknown';
    }
  }

  trackByIndex = (_: number, __: unknown) => _;
  trackByUserId = (_: number, item: BusinessStaffItem) => item.userId;
}
