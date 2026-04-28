import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessStaffItem } from '../../core/models/api.models';
import { formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-staff-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
        <button class="ghost-btn" (click)="loadStaff()">Refresh</button>
      </div>

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
        <div class="panel loading">{{ loaded ? 'No staff match the current filters.' : 'Loading staff...' }}</div>
      </ng-template>
    </div>
  `
})
export class StaffPageComponent {
  private readonly api = inject(BusinessApiService);

  staff: BusinessStaffItem[] = [];
  loaded = false;

  searchTerm = '';
  roleFilter = 'ALL';
  statusFilter: 'ALL' | 'ACTIVE' | 'INACTIVE' = 'ALL';
  pageSize = 10;
  currentPage = 1;

  constructor() {
    this.loadStaff();
  }

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
