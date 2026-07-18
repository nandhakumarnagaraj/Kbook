import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessStaffItem, StaffCreatedResponse } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-staff-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, ConfirmDialogComponent, EmptyStateComponent],
  styles: [`
    .modal-content {
      width: 100%;
      max-width: 460px;
    }
    .modal-content h3 {
      margin: 0 0 1.5rem;
      color: var(--ink, #24170f);
    }
    .form-group {
      margin-bottom: 1rem;
    }
    .form-group label {
      display: block;
      margin-bottom: 0.25rem;
      font-size: 0.875rem;
      color: var(--ink, #24170f);
      font-weight: 500;
    }
    .form-group .field-control,
    .form-group .field-select {
      width: 100%;
      box-sizing: border-box;
    }
    .field-error {
      color: var(--danger, #a6372f);
      font-size: 0.75rem;
      margin-top: 0.25rem;
    }
    .form-error {
      background: rgba(166, 55, 47, 0.08);
      border: 1px solid var(--danger, #a6372f);
      border-radius: 8px;
      padding: 0.75rem 1rem;
      color: var(--danger, #a6372f);
      font-size: 0.875rem;
      margin-bottom: 1rem;
    }
    .success-section {
      text-align: center;
      padding: 1rem 0;
    }
    .success-section h4 {
      color: var(--accent, #1d7b5f);
      margin: 0 0 0.5rem;
    }
    .temp-password {
      background: var(--bg, #f6f1e8);
      border: 1px dashed var(--brand, #b56a2d);
      border-radius: 8px;
      padding: 1rem;
      margin: 1rem 0;
      font-family: monospace;
      font-size: 1.25rem;
      letter-spacing: 0.1em;
      color: var(--ink, #24170f);
      word-break: break-all;
    }
    .success-section p.muted {
      font-size: 0.8rem;
    }
    .action-cell {
      display: flex;
      gap: 0.5rem;
      align-items: center;
    }
    .action-btn {
      padding: 0.35rem 0.75rem;
      border-radius: 8px;
      font-size: 0.8rem;
      font-weight: 500;
      cursor: pointer;
      border: 1px solid var(--line, #e9dcc9);
      background: transparent;
      color: var(--ink, #24170f);
      transition: opacity 0.2s, background 0.2s;
    }
    .action-btn:hover:not(:disabled) {
      background: var(--bg, #f6f1e8);
    }
    .action-btn--danger {
      color: var(--danger, #a6372f);
      border-color: var(--danger, #a6372f);
    }
    .action-btn--danger:hover:not(:disabled) {
      background: rgba(166, 55, 47, 0.06);
    }
    .action-btn:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
    .tooltip-wrapper {
      position: relative;
      display: inline-block;
    }
    .tooltip-wrapper .tooltip-text {
      visibility: hidden;
      position: absolute;
      bottom: 100%;
      left: 50%;
      transform: translateX(-50%);
      background: var(--ink, #24170f);
      color: #fff;
      font-size: 0.7rem;
      padding: 0.35rem 0.6rem;
      border-radius: 6px;
      white-space: nowrap;
      z-index: 10;
      margin-bottom: 4px;
    }
    .tooltip-wrapper:hover .tooltip-text {
      visibility: visible;
    }
    .role-disabled-note {
      font-size: 0.75rem;
      color: var(--muted, #7d6b5f);
      font-style: italic;
      margin-top: 0.25rem;
    }
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

      <!-- Create Staff Modal -->
      <div class="modal-backdrop" *ngIf="showCreateModal" (click)="closeCreateModal()">
          <div class="modal-box modal-content" (click)="$event.stopPropagation()">
            <!-- Success View -->
          <ng-container *ngIf="createdStaff; else createFormView">
            <div class="success-section">
              <h4>Staff Member Created</h4>
              <p><strong>{{ createdStaff.name }}</strong> ({{ createdStaff.role }})</p>
              <p>Temporary Password:</p>
              <div class="temp-password">{{ createdStaff.temporaryPassword }}</div>
              <p class="muted">Share this password with the staff member. They will need it for their first login.</p>
            </div>
            <div class="modal-actions">
              <button class="primary-btn" (click)="closeCreateModal()">Done</button>
            </div>
          </ng-container>

          <!-- Form View -->
          <ng-template #createFormView>
            <h3>Add Staff Member</h3>

            <div class="form-error" *ngIf="createError">{{ createError }}</div>

            <form [formGroup]="staffForm" (ngSubmit)="submitCreate()">
              <div class="form-group">
                <label for="staff-name">Name *</label>
                <input id="staff-name" class="field-control" type="text" formControlName="name" placeholder="Full name" />
                <div class="field-error" *ngIf="staffForm.get('name')?.touched && staffForm.get('name')?.hasError('required')">
                  Name is required.
                </div>
              </div>

              <div class="form-group">
                <label for="staff-phone">Phone (10 digits) *</label>
                <input id="staff-phone" class="field-control" type="text" formControlName="phone" placeholder="10-digit phone number" maxlength="10" />
                <div class="field-error" *ngIf="staffForm.get('phone')?.touched && staffForm.get('phone')?.hasError('required')">
                  Phone is required.
                </div>
                <div class="field-error" *ngIf="staffForm.get('phone')?.touched && staffForm.get('phone')?.hasError('pattern') && !staffForm.get('phone')?.hasError('required')">
                  Phone must be exactly 10 digits.
                </div>
              </div>

              <div class="form-group">
                <label for="staff-role-select">Role *</label>
                <select id="staff-role-select" class="field-select" formControlName="role">
                  <option value="OWNER">Owner</option>
                  <option value="SHOP_ADMIN">Shop Admin</option>
                </select>
              </div>

              <div class="form-group">
                <label for="staff-email">Email (optional)</label>
                <input id="staff-email" class="field-control" type="email" formControlName="email" placeholder="Email address" />
                <div class="field-error" *ngIf="staffForm.get('email')?.touched && staffForm.get('email')?.hasError('email')">
                  Enter a valid email address.
                </div>
              </div>

              <div class="modal-actions">
                <button type="button" class="ghost-btn" (click)="closeCreateModal()" [disabled]="creating">Cancel</button>
                <button type="submit" class="primary-btn" [disabled]="staffForm.invalid || creating">
                  {{ creating ? 'Creating...' : 'Create Staff' }}
                </button>
              </div>
            </form>
          </ng-template>
        </div>
      </div>

      <!-- Edit Staff Modal -->
      <div class="modal-backdrop" *ngIf="showEditModal" (click)="closeEditModal()">
          <div class="modal-box modal-content" (click)="$event.stopPropagation()">
            <h3>Edit Staff Member</h3>

          <div class="form-error" *ngIf="editError">{{ editError }}</div>

          <form [formGroup]="editForm" (ngSubmit)="submitEdit()">
            <div class="form-group">
              <label for="edit-staff-name">Name *</label>
              <input id="edit-staff-name" class="field-control" type="text" formControlName="name" placeholder="Full name" />
              <div class="field-error" *ngIf="editForm.get('name')?.touched && editForm.get('name')?.hasError('required')">
                Name is required.
              </div>
            </div>

            <div class="form-group">
              <label for="edit-staff-phone">Phone (10 digits) *</label>
              <input id="edit-staff-phone" class="field-control" type="text" formControlName="phone" placeholder="10-digit phone number" maxlength="10" />
              <div class="field-error" *ngIf="editForm.get('phone')?.touched && editForm.get('phone')?.hasError('required')">
                Phone is required.
              </div>
              <div class="field-error" *ngIf="editForm.get('phone')?.touched && editForm.get('phone')?.hasError('pattern') && !editForm.get('phone')?.hasError('required')">
                Phone must be exactly 10 digits.
              </div>
            </div>

            <div class="form-group">
              <label for="edit-staff-role">Role *</label>
              <select id="edit-staff-role" class="field-select" formControlName="role">
                <option value="OWNER">Owner</option>
                <option value="SHOP_ADMIN">Shop Admin</option>
              </select>
              <div class="role-disabled-note" *ngIf="isEditingSelf">
                Cannot change your own role
              </div>
            </div>

            <div class="form-group">
              <label for="edit-staff-email">Email (optional)</label>
              <input id="edit-staff-email" class="field-control" type="email" formControlName="email" placeholder="Email address" />
              <div class="field-error" *ngIf="editForm.get('email')?.touched && editForm.get('email')?.hasError('email')">
                Enter a valid email address.
              </div>
            </div>

            <div class="modal-actions">
              <button type="button" class="ghost-btn" (click)="closeEditModal()" [disabled]="editing">Cancel</button>
              <button type="submit" class="primary-btn" [disabled]="editForm.invalid || editing">
                {{ editing ? 'Saving...' : 'Save Changes' }}
              </button>
            </div>
          </form>
        </div>
      </div>

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

      <section class="panel filter-panel" *ngIf="loaded && staff.length">
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
              <option *ngFor="let role of roleOptions" [value]="role">{{ role }}</option>
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
          <p class="muted">{{ filteredStaff.length }} of {{ staff.length }} staff members</p>
          <button class="ghost-btn" (click)="clearFilters()">Clear filters</button>
        </div>
      </section>

      <div class="panel table-wrap" *ngIf="loaded && pagedStaff.length; else loading">
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
            <tr *ngFor="let item of pagedStaff">
              <td>{{ item.name }}</td>
              <td>{{ item.loginId }}</td>
              <td><span class="chip">{{ item.role }}</span></td>
              <td>{{ item.whatsappNumber || item.email || '-' }}</td>
              <td>
                <span class="chip" [class.success]="item.active" [class.danger]="!item.active">
                  {{ item.active ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td>{{ formatDateValue(item.updatedAt) }}</td>
              <td *ngIf="isOwner">
                <div class="action-cell">
                  <button class="action-btn" (click)="openEditModal(item)">Edit</button>
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

        <div class="pagination-bar" *ngIf="filteredStaff.length > pageSize">
          <p class="muted">Page {{ currentPage }} of {{ totalPages }}</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="currentPage === 1" (click)="goToPage(currentPage - 1)">Previous</button>
            <button class="ghost-btn" [disabled]="currentPage === totalPages" (click)="goToPage(currentPage + 1)">Next</button>
          </div>
        </div>
      </div>

      <ng-template #loading>
        <div class="panel loading" *ngIf="!loaded; else staffEmpty">
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4,5]"></div>
          </div>
        </div>
        <ng-template #staffEmpty>
          <app-empty-state
            icon="👥"
            title="No staff match the current filters"
            text="Try a different search, role, or status filter. Owners can add a new team member."
            [actionLabel]="isOwner ? 'Add Staff' : ''"
            (action)="openCreateModal()"
          ></app-empty-state>
        </ng-template>
      </ng-template>
    </div>
  `
})
export class StaffPageComponent {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  staff: BusinessStaffItem[] = [];
  loaded = false;

  searchTerm = '';
  roleFilter = 'ALL';
  statusFilter: 'ALL' | 'ACTIVE' | 'INACTIVE' = 'ALL';
  pageSize = 10;
  currentPage = 1;

  // Staff creation state
  showCreateModal = false;
  creating = false;
  createError = '';
  createdStaff: StaffCreatedResponse | null = null;

  // Staff edit state
  showEditModal = false;
  editing = false;
  editError = '';
  editingStaff: BusinessStaffItem | null = null;

  // Staff deactivation state
  staffToDeactivate: BusinessStaffItem | null = null;

  staffForm = this.fb.group({
    name: ['', [Validators.required]],
    phone: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    role: ['OWNER' as 'OWNER' | 'SHOP_ADMIN', [Validators.required]],
    email: ['', [Validators.email]]
  });

  editForm = this.fb.group({
    name: ['', [Validators.required]],
    phone: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    role: ['OWNER' as 'OWNER' | 'SHOP_ADMIN', [Validators.required]],
    email: ['', [Validators.email]]
  });

  get isOwner(): boolean {
    return this.auth.session()?.role === 'OWNER';
  }

  get isEditingSelf(): boolean {
    if (!this.editingStaff) return false;
    return this.isSelf(this.editingStaff);
  }

  constructor() {
    this.loadStaff();
  }

  isSelf(item: BusinessStaffItem): boolean {
    const session = this.auth.session();
    if (!session) return false;
    return item.loginId === session.loginId;
  }

  // --- Create Modal ---

  openCreateModal(): void {
    this.showCreateModal = true;
    this.createError = '';
    this.createdStaff = null;
    this.staffForm.reset({ name: '', phone: '', role: 'OWNER', email: '' });
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
    this.createError = '';
    if (this.createdStaff) {
      this.createdStaff = null;
      this.loadStaff();
    }
  }

  submitCreate(): void {
    if (this.staffForm.invalid || this.creating) return;

    this.creating = true;
    this.createError = '';

    const formValue = this.staffForm.value;
    const payload = {
      name: formValue.name!,
      phone: formValue.phone!,
      role: formValue.role! as 'OWNER' | 'SHOP_ADMIN',
      ...(formValue.email ? { email: formValue.email } : {})
    };

    this.api.createStaff(payload).subscribe({
      next: (response) => {
        this.createdStaff = response;
        this.creating = false;
      },
      error: (err: HttpErrorResponse) => {
        this.creating = false;
        if (err.status === 409) {
          this.createError = 'This phone number is already registered. Please use a different number.';
        } else if (err.status === 400 && err.error?.fields) {
          const fields = err.error.fields;
          this.createError = Object.values(fields).join('. ');
        } else {
          this.createError = err.error?.message || 'Failed to create staff member. Please try again.';
        }
      }
    });
  }

  // --- Edit Modal ---

  openEditModal(item: BusinessStaffItem): void {
    this.editingStaff = item;
    this.showEditModal = true;
    this.editError = '';

    this.editForm.reset({
      name: item.name,
      phone: item.whatsappNumber || item.loginId,
      role: item.role as 'OWNER' | 'SHOP_ADMIN',
      email: item.email || ''
    });

    if (this.isSelf(item)) {
      this.editForm.get('role')?.disable();
    } else {
      this.editForm.get('role')?.enable();
    }
  }

  closeEditModal(): void {
    this.showEditModal = false;
    this.editError = '';
    this.editingStaff = null;
    this.editForm.get('role')?.enable();
  }

  submitEdit(): void {
    if (this.editForm.invalid || this.editing || !this.editingStaff) return;

    this.editing = true;
    this.editError = '';

    const formValue = this.editForm.getRawValue();
    const payload = {
      name: formValue.name!,
      phone: formValue.phone!,
      role: formValue.role! as 'OWNER' | 'SHOP_ADMIN',
      ...(formValue.email ? { email: formValue.email } : {})
    };

    this.api.updateStaff(this.editingStaff.userId, payload).subscribe({
      next: () => {
        this.editing = false;
        this.closeEditModal();
        this.loadStaff();
      },
      error: (err: HttpErrorResponse) => {
        this.editing = false;
        if (err.status === 409) {
          this.editError = 'This phone number is already registered. Please use a different number.';
        } else if (err.status === 400 && err.error?.fields) {
          const fields = err.error.fields;
          this.editError = Object.values(fields).join('. ');
        } else {
          this.editError = err.error?.message || 'Failed to update staff member. Please try again.';
        }
      }
    });
  }

  // --- Deactivation ---

  requestDeactivate(item: BusinessStaffItem): void {
    if (this.isSelf(item)) return;
    this.staffToDeactivate = item;
  }

  confirmDeactivate(): void {
    if (!this.staffToDeactivate) return;

    const userId = this.staffToDeactivate.userId;
    this.staffToDeactivate = null;

    this.api.deactivateStaff(userId).subscribe({
      next: () => {
        this.loadStaff();
      },
      error: (err: HttpErrorResponse) => {
        console.error('Failed to deactivate staff:', err);
      }
    });
  }

  cancelDeactivate(): void {
    this.staffToDeactivate = null;
  }

  // --- Filters and Pagination ---

  get roleOptions(): string[] {
    return [...new Set(this.staff.map((item) => item.role))].sort();
  }

  get filteredStaff(): BusinessStaffItem[] {
    const search = this.searchTerm.trim().toLowerCase();

    return this.staff.filter((item) => {
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
    this.loaded = false;
    this.api.getStaff().subscribe({
      next: (data) => {
        this.staff = data;
        this.loaded = true;
        this.currentPage = 1;
      },
      error: () => { this.loaded = true; }
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
}
