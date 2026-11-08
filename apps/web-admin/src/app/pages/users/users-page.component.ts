import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminUserListItem } from '../../core/models/api.models';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';

function formatDate(timestamp: number | null | undefined): string {
  if (!timestamp) return '—';
  try {
    const d = new Date(timestamp);
    return isNaN(d.getTime()) ? '—' : d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch {
    return '—';
  }
}

@Component({
  selector: 'app-users-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, PageHeaderComponent, KpiRowComponent, PaginationBarComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Users &amp; Access"
        subtitle="Manage platform users, restaurant proprietors, cashiers and administrative roles."
      >
        <div class="header-controls">
          <span class="chip chip--live">
            <span class="live-dot" aria-hidden="true"></span>
            Live Platform Accounts ({{ users().length }})
          </span>
          <button class="header-action-btn" type="button" (click)="loadUsers()" title="Reload from server">
            <span aria-hidden="true">&#8635;</span> Refresh
          </button>
          <button class="header-action-btn header-action-btn--primary" type="button" (click)="inviteUser()">
            + Invite User
          </button>
          <button class="header-action-btn" type="button" (click)="exportCsv()" title="Export real users roster as CSV">
            <span aria-hidden="true">&#8595;</span> Export CSV
          </button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis()" ariaLabel="User summary"></app-kpi-row>

      <div class="panel list-panel">
        <!-- Filter bar: search + select filters + clear -->
        <div class="filter-bar">
          <label class="filter-search">
            <input
              type="search"
              name="search"
              placeholder="Search by name, login ID, email, phone or restaurant..."
              [(ngModel)]="search"
              (ngModelChange)="page.set(1)"
              aria-label="Search users"
            />
          </label>
          <label class="filter-select" aria-label="Role filter">
            <select name="roleFilter" [(ngModel)]="roleFilter" (ngModelChange)="page.set(1)">
              <option value="">All Roles</option>
              <option value="OWNER">Restaurant Owners</option>
              <option value="KBOOK_ADMIN">Platform Admins</option>
              <option value="SHOP_STAFF">Shop Staff</option>
            </select>
          </label>
          <label class="filter-select" aria-label="Status filter">
            <select name="statusFilter" [(ngModel)]="statusFilter" (ngModelChange)="page.set(1)">
              <option value="">All Statuses</option>
              <option value="active">Active Accounts</option>
              <option value="inactive">Inactive Accounts</option>
            </select>
          </label>
          <label class="filter-select" aria-label="Business filter">
            <select name="businessFilter" [(ngModel)]="businessFilter" (ngModelChange)="page.set(1)">
              <option value="">All Outlets</option>
              @for (business of businessOptions(); track business) {
                <option [value]="business">{{ business }}</option>
              }
            </select>
          </label>
          <label class="filter-select" aria-label="Sort order">
            <select name="sortOption" [(ngModel)]="sortOption" (ngModelChange)="page.set(1)">
              <option value="newest">Sort: Newest First</option>
              <option value="oldest">Sort: Oldest First</option>
              <option value="name_asc">Sort: Name (A-Z)</option>
              <option value="role">Sort: By Role</option>
            </select>
          </label>
          <button type="button" class="clear-filters" (click)="clearFilters()">
            Clear Filters
          </button>
        </div>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th class="idx-col">#</th>
                <th>User Account</th>
                <th>Login Identifier</th>
                <th>Phone / WhatsApp</th>
                <th>Role</th>
                <th>Assigned Outlet</th>
                <th>Status</th>
                <th>Joined Date</th>
                <th class="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (user of pagedRows(); track user.id; let i = $index) {
                <tr>
                  <td class="idx-col">{{ (page() - 1) * pageSize() + i + 1 }}</td>
                  <td>
                    <div class="stacked">
                      <span class="avatar" [class]="'avatar--' + avatarTone(user.id)" aria-hidden="true">
                        {{ (user.name || 'U').charAt(0).toUpperCase() }}
                      </span>
                      <div>
                        <strong>{{ user.name || 'Unnamed Account' }}</strong>
                        <span class="sub-id">UID: {{ user.id }}</span>
                      </div>
                    </div>
                  </td>
                  <td class="email">
                    <span>{{ user.email || user.loginId }}</span>
                    @if (user.email && user.loginId !== user.email) {
                      <small class="muted sub-text">{{ user.loginId }}</small>
                    }
                  </td>
                  <td>
                    <span [class.muted]="!user.phoneNumber">{{ user.phoneNumber || '—' }}</span>
                  </td>
                  <td>
                    <span class="role-badge" [class]="'role-badge--' + roleSlug(user.role)">
                      {{ roleDisplay(user.role) }}
                    </span>
                  </td>
                  <td>
                    @if (user.shopName) {
                      <div class="outlet-col">
                        <strong class="biz">{{ user.shopName }}</strong>
                        <span class="muted place">ID: {{ user.restaurantId }}</span>
                      </div>
                    } @else if (user.role === 'KBOOK_ADMIN') {
                      <span class="chip-platform">Platform Core</span>
                    } @else {
                      <span class="muted">Unassigned</span>
                    }
                  </td>
                  <td>
                    @if (user.isActive) {
                      <span class="status-chip status-chip--active">Active</span>
                    } @else {
                      <span class="status-chip status-chip--inactive">Inactive</span>
                    }
                  </td>
                  <td>{{ formatTimestamp(user.createdAt) }}</td>
                  <td class="actions-col">
                    <button
                      type="button"
                      class="quick-action-btn"
                      title="Copy Login Identifier"
                      (click)="copyLogin(user)">
                      Copy Login
                    </button>
                  </td>
                </tr>
              }
              @if (!filteredRows().length) {
                <tr>
                  <td colspan="9" class="muted empty-cell">
                    @if (loading()) {
                      Loading platform accounts from server...
                    } @else if (error()) {
                      {{ error() }}
                    } @else {
                      No users match these filters.
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <footer class="list-foot">
          <span class="muted count-note">
            Showing {{ showingLabel() }} of {{ filteredRows().length }} real user accounts
          </span>
          <app-pagination-bar
            [currentPage]="page()"
            [pageSize]="pageSize()"
            [totalItems]="filteredRows().length"
            (pageChange)="page.set($event)"
          ></app-pagination-bar>
          <label class="rpp" aria-label="Rows per page">
            Rows per page
            <select name="rowsPerPage" [(ngModel)]="rpp" (ngModelChange)="onRppChange()">
              <option [value]="10">10</option>
              <option [value]="25">25</option>
              <option [value]="50">50</option>
              <option [value]="100">100</option>
            </select>
          </label>
        </footer>
      </div>
    </div>
  `,
  styles: [
    `
      .list-panel { margin-top: var(--kb-space-5); padding: var(--kb-space-4) var(--kb-space-5) var(--kb-space-5); }
      .filter-bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--kb-space-3); margin-bottom: var(--kb-space-4); }
      .filter-search {
        flex: 1;
        min-width: 240px;
        display: flex;
        align-items: center;
        border: 1px solid var(--line);
        border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3);
      }
      .filter-search input {
        flex: 1;
        border: none;
        outline: none;
        background: transparent;
        font-size: var(--kb-text-sm);
        color: var(--ink);
      }
      .filter-select select,
      .rpp select {
        border: 1px solid var(--line);
        border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3);
        font-size: var(--kb-text-sm);
        background: var(--kb-color-input);
        color: var(--ink);
      }
      .clear-filters {
        border: 1px solid var(--line);
        background: var(--kb-color-surface);
        color: var(--brand);
        border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3);
        font-size: var(--kb-text-sm);
        cursor: pointer;
        transition: border-color 200ms ease, color 200ms ease;
      }
      .filter-search:focus-within, .filter-select select:focus, .filter-search input:focus, .rpp select:focus, .clear-filters:focus {
        outline: 2px solid var(--brand);
        outline-offset: 1px;
      }
      .idx-col { width: 44px; text-align: center; color: var(--muted); }
      .stacked { display: flex; align-items: center; gap: var(--kb-space-2); }
      .sub-id { display: block; font-size: var(--kb-text-xs); color: var(--muted); font-family: monospace; }
      .sub-text { display: block; font-size: var(--kb-text-xs); }
      .avatar {
        flex: none;
        width: 32px;
        height: 32px;
        border-radius: var(--kb-radius-full);
        display: grid;
        place-items: center;
        font-size: var(--kb-text-sm);
        font-weight: 700;
      }
      .avatar--0 { background: var(--brand-soft); color: var(--brand); }
      .avatar--1 { background: var(--success-soft); color: var(--success); }
      .avatar--2 { background: var(--info-soft); color: var(--info); }
      .avatar--3 { background: var(--warning-soft); color: var(--warning); }
      .role-badge {
        font-size: var(--kb-text-xs);
        font-weight: 700;
        padding: var(--kb-space-1) var(--kb-space-2);
        border-radius: var(--kb-radius-sm);
        display: inline-block;
      }
      .role-badge--admin { background: var(--warning-soft); color: var(--warning); }
      .role-badge--owner { background: var(--success-soft); color: var(--success); }
      .role-badge--staff { background: var(--info-soft); color: var(--info); }
      .outlet-col { display: flex; flex-direction: column; gap: var(--kb-space-1); }
      .biz { font-size: var(--kb-text-sm); }
      .place { font-size: var(--kb-text-xs); font-family: monospace; }
      .chip-platform {
        font-size: var(--kb-text-xs);
        font-weight: 600;
        color: var(--brand);
        background: var(--brand-soft);
        padding: var(--kb-space-1) var(--kb-space-2);
        border-radius: var(--kb-radius-full);
      }
      .email { font-size: var(--kb-text-sm); color: var(--ink); }
      .status-chip {
        font-size: var(--kb-text-xs);
        font-weight: 650;
        padding: var(--kb-space-1) var(--kb-space-2);
        border-radius: var(--kb-radius-full);
      }
      .status-chip--active { background: var(--success-soft); color: var(--success); }
      .status-chip--inactive { background: var(--danger-soft); color: var(--danger); }
      .chip--live {
        display: inline-flex;
        align-items: center;
        gap: var(--kb-space-2);
        background: var(--success-soft);
        color: var(--success);
        font-size: var(--kb-text-xs);
        font-weight: 650;
        padding: var(--kb-space-1) var(--kb-space-3);
        border-radius: var(--kb-radius-full);
      }
      .live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); flex: none; }
      .actions-col { width: 100px; text-align: center; }
      .quick-action-btn {
        border: 1px solid var(--line);
        background: var(--kb-color-surface);
        padding: var(--kb-space-1) var(--kb-space-2);
        border-radius: var(--kb-radius-sm);
        font-size: var(--kb-text-xs);
        font-weight: 600;
        color: var(--brand);
        cursor: pointer;
        transition: border-color 200ms ease, background-color 200ms ease, color 200ms ease;
      }
      .quick-action-btn:hover {
        border-color: var(--brand);
        background: var(--brand-soft);
      }
      .empty-cell { text-align: center; padding: var(--kb-space-6) 0; font-size: var(--kb-text-sm); }
      .list-foot { display: flex; align-items: center; gap: var(--kb-space-4); padding-top: var(--kb-space-4); }
      .count-note { font-size: var(--kb-text-sm); }
      .rpp { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); color: var(--muted); margin-left: auto; }
    `,
  ],
})
export class UsersPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService, { optional: true });
  private readonly toast = inject(ToastService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly users = signal<AdminUserListItem[]>([]);

  readonly page = signal(1);
  readonly pageSize = signal(10);
  rpp = 10;

  search = '';
  roleFilter = '';
  statusFilter = '';
  businessFilter = '';
  sortOption: 'newest' | 'oldest' | 'name_asc' | 'role' = 'newest';

  readonly kpis = computed<KpiItem[]>(() => {
    const list = this.users();
    const total = list.length;
    const owners = list.filter((u) => u.role === 'OWNER').length;
    const admins = list.filter((u) => u.role === 'KBOOK_ADMIN' || u.role === 'ADMIN').length;
    const staff = list.filter((u) => u.role === 'SHOP_STAFF' || u.role === 'STAFF').length;

    return [
      { label: 'Total Users', value: total, icon: 'staff', tone: 'brand', caption: 'Registered platform accounts' },
      { label: 'Business Owners', value: owners, icon: 'business', tone: 'success', caption: `${total > 0 ? ((owners / total) * 100).toFixed(1) : 0}% of platform accounts` },
      { label: 'Platform Admins', value: admins, icon: 'flag', tone: 'warning', caption: 'Superuser / root access' },
      { label: 'Shop Staff Users', value: staff, icon: 'manage_accounts', tone: 'brand', caption: 'Counter operators & cashiers' },
    ];
  });

  readonly businessOptions = computed<string[]>(() => {
    const set = new Set<string>();
    for (const u of this.users()) {
      if (u.shopName) set.add(u.shopName);
    }
    return Array.from(set).sort();
  });

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    if (!this.adminApi) return;
    this.loading.set(true);
    this.error.set(null);

    this.adminApi.getUsers().subscribe({
      next: (data) => {
        this.users.set(data || []);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set('Failed to load users from server.');
        console.error('[UsersPage] Error loading users:', err);
      },
    });
  }

  avatarTone(id: string | number): string {
    const str = String(id || '0');
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = (hash * 31 + str.charCodeAt(i)) % 4;
    }
    return String(Math.abs(hash));
  }

  roleSlug(role: string): string {
    const r = (role || '').toUpperCase();
    if (r.includes('ADMIN')) return 'admin';
    if (r.includes('STAFF')) return 'staff';
    return 'owner';
  }

  roleDisplay(role: string): string {
    const r = (role || '').toUpperCase();
    if (r === 'KBOOK_ADMIN') return 'Platform Admin';
    if (r === 'SHOP_STAFF') return 'Shop Staff';
    if (r === 'OWNER') return 'Business Owner';
    return role || 'User';
  }

  formatTimestamp(ts: number | null | undefined): string {
    return formatDate(ts);
  }

  filteredRows(): AdminUserListItem[] {
    const query = this.search.trim().toLowerCase();
    const roleF = this.roleFilter;
    const statusF = this.statusFilter;
    const bizF = this.businessFilter;

    let res = this.users().filter((user) => {
      // Role filter
      if (roleF && user.role !== roleF) return false;

      // Status filter
      if (statusF === 'active' && !user.isActive) return false;
      if (statusF === 'inactive' && user.isActive) return false;

      // Business filter
      if (bizF && user.shopName !== bizF) return false;

      // Search query
      if (query) {
        const haystack = `${user.name || ''} ${user.email || ''} ${user.loginId || ''} ${user.phoneNumber || ''} ${user.shopName || ''} ${user.id}`.toLowerCase();
        if (!haystack.includes(query)) return false;
      }

      return true;
    });

    switch (this.sortOption) {
      case 'newest':
        res.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
        break;
      case 'oldest':
        res.sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
        break;
      case 'name_asc':
        res.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
        break;
      case 'role':
        res.sort((a, b) => a.role.localeCompare(b.role));
        break;
    }

    return res;
  }

  pagedRows(): AdminUserListItem[] {
    const rows = this.filteredRows();
    const start = (this.page() - 1) * this.pageSize();
    return rows.slice(start, start + this.pageSize());
  }

  showingLabel(): string {
    const len = this.filteredRows().length;
    if (!len) return '0 results';
    const end = Math.min(this.page() * this.pageSize(), len);
    return `${(this.page() - 1) * this.pageSize() + 1} - ${end}`;
  }

  clearFilters(): void {
    this.search = '';
    this.roleFilter = '';
    this.statusFilter = '';
    this.businessFilter = '';
    this.sortOption = 'newest';
    this.page.set(1);
  }

  onRppChange(): void {
    this.pageSize.set(Number(this.rpp) || 10);
    this.page.set(1);
  }

  copyLogin(user: AdminUserListItem): void {
    const id = user.loginId || user.email || user.phoneNumber || '';
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(id).then(
        () => this.toast.show(`Copied "${id}" to clipboard.`, 'success'),
        () => this.toast.show(`Login Identifier: ${id}`, 'info')
      );
    } else {
      this.toast.show(`Login Identifier: ${id}`, 'info');
    }
  }

  inviteUser(): void {
    this.toast.show('User invite invitation link feature is queued for next platform rollout.', 'info');
  }

  exportCsv(): void {
    const rows = this.filteredRows();
    const header = 'User ID,Name,Login ID,Email,Phone,Role,Restaurant ID,Shop Name,Status,Joined Date';
    const lines = rows.map((u) => [
      `"${u.id}"`,
      `"${(u.name || '').replace(/"/g, '""')}"`,
      `"${(u.loginId || '').replace(/"/g, '""')}"`,
      `"${(u.email || '').replace(/"/g, '""')}"`,
      `"${(u.phoneNumber || '').replace(/"/g, '""')}"`,
      `"${u.role}"`,
      `"${u.restaurantId || ''}"`,
      `"${(u.shopName || '').replace(/"/g, '""')}"`,
      u.isActive ? 'Active' : 'Inactive',
      formatDate(u.createdAt),
    ].join(','));
    const csv = [header, ...lines].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `khanabook-users-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    this.toast.show(`Exported ${rows.length} real user accounts to CSV.`, 'success');
  }
}
