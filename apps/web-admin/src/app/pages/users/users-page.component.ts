import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../core/services/toast.service';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';

interface UserRow {
  id: number;
  name: string;
  email: string;
  role: 'Admin' | 'Owner' | 'Shop Staff';
  business?: string;
  location?: string;
  status: 'Active' | 'Inactive' | 'Pending';
  lastLogin: string;
}

/**
 * Admin → Users & Access (reference A05, fused chrome pass).
 * No user-administration endpoint exists yet (admin-api.service.ts), so the
 * roster is prototype sample data, labelled on screen. The reference's page
 * language — four KPI cards, a select-style filter bar, initial-avatar rows,
 * kebab row actions and footer pagination with rows-per-page — is reproduced
 * with real local filtering/pagination behaviour.
 */
@Component({
  selector: 'app-users-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, PageHeaderComponent, KpiRowComponent, PaginationBarComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Users & Access"
        subtitle="Manage platform users, roles and permissions across all restaurants."
      >
        <div class="header-controls">
          <span class="chip warn">Prototype sample — no API yet</span>
          <button class="header-action-btn header-action-btn--primary" type="button" (click)="inviteUser()">
            + Invite User
          </button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis" ariaLabel="User summary"></app-kpi-row>

      <div class="panel list-panel">
        <!-- Reference filter bar: search + select filters + clear -->
        <div class="filter-bar">
          <label class="filter-search">
            <input
              type="search"
              name="search"
              placeholder="Search by name, email or business..."
              [(ngModel)]="search"
              (ngModelChange)="page.set(1)"
              aria-label="Search users"
            />
          </label>
          <label class="filter-select" aria-label="Role filter">
            <select name="roleFilter" [(ngModel)]="roleFilter" (ngModelChange)="page.set(1)">
              <option value="">All Roles</option>
              @for (role of roleOptions; track role) { <option [value]="role">{{ role }}</option> }
            </select>
          </label>
          <label class="filter-select" aria-label="Status filter">
            <select name="statusFilter" [(ngModel)]="statusFilter" (ngModelChange)="page.set(1)">
              <option value="">All Status</option>
              @for (status of statusOptions; track status) { <option [value]="status">{{ status }}</option> }
            </select>
          </label>
          <label class="filter-select" aria-label="Business filter">
            <select name="businessFilter" [(ngModel)]="businessFilter" (ngModelChange)="page.set(1)">
              <option value="">All Businesses</option>
              @for (business of businessOptions; track business) { <option [value]="business">{{ business }}</option> }
            </select>
          </label>
          <button type="button" class="clear-filters" (click)="clearFilters()">
            <span aria-hidden="true">≡</span> Clear Filters
          </button>
        </div>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th class="idx-col">#</th>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Business</th>
                <th>Status</th>
                <th>Last Login</th>
                <th class="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (user of pagedRows(); track user.id; let i = $index) {
                <tr>
                  <td class="idx-col">{{ (page() - 1) * pageSize() + i + 1 }}</td>
                  <td>
                    <div class="stacked">
                      <span class="avatar" aria-hidden="true">{{ user.name.charAt(0) }}</span>
                      <strong>{{ user.name }}</strong>
                    </div>
                  </td>
                  <td class="email">{{ user.email }}</td>
                  <td><span class="role-name">{{ user.role }}</span></td>
                  <td>
                    @if (user.business) {
                      <div class="stacked">
                        <strong class="biz">{{ user.business }}</strong>
                        <span class="muted place">{{ user.location }}</span>
                      </div>
                    } @else {
                      <span class="muted">Platform workspace</span>
                    }
                  </td>
                  <td>
                    <span class="chip" [class.success]="user.status === 'Active'" [class.danger]="user.status === 'Inactive'" [class.warn]="user.status === 'Pending'">
                      {{ user.status }}
                    </span>
                  </td>
                  <td>{{ user.lastLogin }}</td>
                  <td class="actions-col">
                    <button type="button" class="kebab-btn" aria-label="Row actions" (click)="rowMenu(user)">⋮</button>
                  </td>
                </tr>
              }
              @if (!filteredRows().length) {
                <tr><td colspan="8" class="muted empty-cell">No users match these filters.</td></tr>
              }
            </tbody>
          </table>
        </div>

        <footer class="list-foot">
          <span class="muted count-note">
            {{ filteredRows().length }} of {{ users.length }} sample users
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
        min-width: 220px;
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
        display: inline-flex;
        align-items: center;
        gap: var(--kb-space-1);
        border: none;
        background: transparent;
        color: var(--brand);
        font-size: var(--kb-text-sm);
        cursor: pointer;
      }
      .filter-search:focus-within, .filter-select select:focus, .filter-search input:focus, .rpp select:focus { outline: 2px solid var(--brand); outline-offset: 1px; }
      .idx-col { width: 48px; text-align: center; color: var(--muted); }
      .stacked { display: flex; align-items: center; gap: var(--kb-space-2); }
      .avatar {
        flex: none;
        width: 28px;
        height: 28px;
        border-radius: var(--kb-radius-full);
        background: var(--brand-soft);
        color: var(--brand);
        display: grid;
        place-items: center;
        font-size: var(--kb-text-sm);
        font-weight: 700;
      }
      .role-name { color: var(--brand); font-weight: 650; }
      .biz { font-size: var(--kb-text-sm); }
      .place { font-size: var(--kb-text-xs); }
      .email { font-size: var(--kb-text-sm); color: var(--ink-2); }
      .actions-col { width: 56px; text-align: center; }
      .kebab-btn {
        border: none;
        background: transparent;
        color: var(--muted);
        font-size: var(--kb-text-lg);
        cursor: pointer;
        line-height: 1;
      }
      .empty-cell { text-align: center; padding: var(--kb-space-6) 0; font-size: var(--kb-text-sm); }
      .list-foot { display: flex; align-items: center; gap: var(--kb-space-4); padding-top: var(--kb-space-4); }
      .count-note { font-size: var(--kb-text-sm); }
      .rpp { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); color: var(--muted); margin-left: auto; }
    `,
  ],
})
export class UsersPageComponent {
  private readonly toast = inject(ToastService);

  readonly page = signal(1);
  readonly pageSize = signal(10);
  readonly rpp = 10;

  readonly kpis: KpiItem[] = [
    { label: 'Total Users', value: 156, icon: 'staff', tone: 'brand', caption: '+12 this month' },
    { label: 'Admins', value: 12, icon: 'flag', tone: 'warning', caption: '7.7% of accounts' },
    { label: 'Business Owners', value: 98, icon: 'business', tone: 'success', caption: '62.8% of accounts' },
    { label: 'Staff Users', value: 46, icon: 'manage_accounts', tone: 'brand', caption: '29.5% of accounts' },
  ];

  readonly roleOptions = ['Admin', 'Owner', 'Shop Staff'] as const;
  readonly statusOptions = ['Active', 'Inactive', 'Pending'] as const;
  readonly businessOptions = ['Spice Villa', 'Dine Hub', 'Tasty Bites', 'Food Junction', 'Royal Kitchen', 'Curry Corner'];

  search = '';
  roleFilter = '';
  statusFilter = '';
  businessFilter = '';

  readonly users: UserRow[] = [
    { id: 1, name: 'Bharat Mehta', email: 'bharat@khanabook.in', role: 'Admin', status: 'Active', lastLogin: '28 Oct 2026, 09:12 am' },
    { id: 2, name: 'NandhaKumar N', email: 'nandhakumar@spicevilla.in', role: 'Owner', business: 'Spice Villa', location: 'Chennai, TN', status: 'Active', lastLogin: '28 Oct 2026, 07:45 am' },
    { id: 3, name: 'Ravi Kumar', email: 'ravi@spicevilla.in', role: 'Shop Staff', business: 'Spice Villa', location: 'Chennai, TN', status: 'Active', lastLogin: '28 Oct 2026, 07:20 am' },
    { id: 4, name: 'Suresh M', email: 'suresh@dinehub.in', role: 'Owner', business: 'Dine Hub', location: 'Bengaluru, KA', status: 'Active', lastLogin: '27 Oct 2026, 11:05 am' },
    { id: 5, name: 'Priya S', email: 'priya@tastybites.in', role: 'Owner', business: 'Tasty Bites', location: 'Coimbatore, TN', status: 'Active', lastLogin: '26 Oct 2026, 09:12 am' },
    { id: 6, name: 'Deepa R', email: 'deepa@royalkitchen.in', role: 'Owner', business: 'Royal Kitchen', location: 'Trichy, TN', status: 'Inactive', lastLogin: '12 Oct 2026, 11:30 am' },
    { id: 7, name: 'Mohammed A', email: 'mohammed@flavorspoint.in', role: 'Owner', business: 'Flavors Point', location: 'Hyderabad, TS', status: 'Pending', lastLogin: '—' },
    { id: 8, name: 'Vikram N', email: 'vikram@biryanihouse.in', role: 'Owner', business: 'Biryani House', location: 'Kochi, KL', status: 'Active', lastLogin: '22 Oct 2026, 10:50 am' },
    { id: 9, name: 'Anjali P', email: 'anjali@tasteofindia.in', role: 'Owner', business: 'Taste of India', location: 'Mysuru, KA', status: 'Active', lastLogin: '21 Oct 2026, 03:22 pm' },
    { id: 10, name: 'Neha S', email: 'neha@urbandine.in', role: 'Owner', business: 'Urban Dine', location: 'Pune, MH', status: 'Active', lastLogin: '20 Oct 2026, 11:08 am' },
  ];

  readonly filteredRows = computed(() => {
    const searchLower = this.search.trim().toLowerCase();
    return this.users.filter((user) => {
      const matchesRole = !this.roleFilter || user.role === this.roleFilter;
      const matchesStatus = !this.statusFilter || user.status === this.statusFilter;
      const matchesBusiness = !this.businessFilter || user.business === this.businessFilter;
      const haystack = `${user.name} ${user.email} ${user.business ?? ''} ${user.location ?? ''}`.toLowerCase();
      return matchesRole && matchesStatus && matchesBusiness && (!searchLower || haystack.includes(searchLower));
    });
  });

  pagedRows(): UserRow[] {
    const start = (this.page() - 1) * this.pageSize();
    return this.filteredRows().slice(start, start + this.pageSize());
  }

  clearFilters(): void {
    this.search = '';
    this.roleFilter = '';
    this.statusFilter = '';
    this.businessFilter = '';
    this.page.set(1);
  }

  onRppChange(): void {
    this.pageSize.set(Number(this.rpp) || 10);
    this.page.set(1);
  }

  inviteUser(): void {
    this.toast.show('User invites will be wired once the users API exists — prototype sample data shown.', 'info');
  }

  rowMenu(user: UserRow): void {
    this.toast.show(`${user.name} — user actions pending the users API (prototype).`, 'info');
  }
}
