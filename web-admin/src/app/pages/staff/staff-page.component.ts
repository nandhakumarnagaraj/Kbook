import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal, ViewChild, AfterViewInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessStaffItem } from '../../core/models/api.models';
import { formatDate } from '../../shared/formatters';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { ErrorStateComponent } from '../../shared/error-state.component';

@Component({
  selector: 'app-staff-page',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatTooltipModule,
    MatDividerModule,
    EmptyStateComponent,
    ErrorStateComponent
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title text-balance">Team Management</h1>
          <p class="page-subtitle">Manage staff accounts, roles, and platform access levels.</p>
        </div>
        <div class="header-actions">
          <button mat-flat-button color="primary" (click)="loadStaff()">
            <mat-icon>refresh</mat-icon>
            Refresh Directory
          </button>
        </div>
      </div>

      <div class="stats-grid" *ngIf="loaded">
        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon total">people</mat-icon>
            <mat-card-title>{{ staff.length }}</mat-card-title>
            <mat-card-subtitle>Total Members</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon active">verified_user</mat-icon>
            <mat-card-title>{{ activeCount() }}</mat-card-title>
            <mat-card-subtitle>Active Sessions</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
        <mat-card class="stat-card">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon admin">admin_panel_settings</mat-icon>
            <mat-card-title>{{ adminCount() }}</mat-card-title>
            <mat-card-subtitle>Admin Accounts</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
      </div>

      <mat-card class="filter-card mat-elevation-z1">
        <mat-card-content class="filter-row">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Quick Search</mat-label>
            <mat-icon matPrefix>search</mat-icon>
            <input matInput (keyup)="applyFilter($event)" placeholder="Search by name, ID, contact..." #input aria-label="Search staff members">
          </mat-form-field>
          
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Role Filter</mat-label>
            <mat-select [(ngModel)]="roleFilter" (selectionChange)="applyFilters()" aria-label="Filter by role">
              <mat-option value="ALL">All Roles</mat-option>
              <mat-option *ngFor="let role of roleOptions" [value]="role">{{ role }}</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Status</mat-label>
            <mat-select [(ngModel)]="statusFilter" (selectionChange)="applyFilters()" aria-label="Filter by status">
              <mat-option value="ALL">All Status</mat-option>
              <mat-option value="ACTIVE">Active</mat-option>
              <mat-option value="INACTIVE">Inactive</mat-option>
            </mat-select>
          </mat-form-field>

          <div class="spacer"></div>
          
          <button mat-button (click)="clearFilters()" aria-label="Reset all filters">Reset Filters</button>
        </mat-card-content>
      </mat-card>

      <div class="table-container mat-elevation-z2">
        <div class="loading-overlay" *ngIf="!loaded">
          <mat-spinner diameter="40"></mat-spinner>
        </div>

        <table mat-table [dataSource]="dataSource" matSort>
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Staff Member </th>
            <td mat-cell *matCellDef="let item"> 
               <div class="user-cell">
                  <span class="user-name">{{ item.name }}</span>
                  <span class="user-id">ID: {{ item.loginId }}</span>
               </div>
            </td>
          </ng-container>

          <ng-container matColumnDef="loginId">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Username </th>
            <td mat-cell *matCellDef="let item"> <code>{{ item.loginId }}</code> </td>
          </ng-container>

          <ng-container matColumnDef="role">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Access Role </th>
            <td mat-cell *matCellDef="let item">
              <span class="role-badge">{{ item.role }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="contact">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Contact Info </th>
            <td mat-cell *matCellDef="let item">
              <div class="contact-cell">
                <span class="phone">{{ item.whatsappNumber || '-' }}</span>
                <span class="email" [matTooltip]="item.email || ''">{{ item.email || '-' }}</span>
              </div>
            </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Status </th>
            <td mat-cell *matCellDef="let item">
              <span class="status-chip" [class.active]="item.active" [class.inactive]="!item.active">
                <mat-icon>{{ item.active ? 'fiber_manual_record' : 'block' }}</mat-icon>
                {{ item.active ? 'Active' : 'Deactivated' }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="updatedAt">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Last Active </th>
            <td mat-cell *matCellDef="let item"> {{ formatDateValue(item.updatedAt) }} </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;" class="staff-row"></tr>
        </table>

        <div *ngIf="loaded && !dataSource.data.length && !loadError" class="empty-state-wrapper">
          <app-empty-state icon="person_search" title="No team members found" description="Your search filters did not match any staff records."></app-empty-state>
        </div>

        <div *ngIf="loaded && loadError && !staff.length" class="empty-state-wrapper">
          <app-error-state icon="error_outline" title="Failed to load staff" [description]="loadError" [retryable]="true" (retry)="loadStaff()"></app-error-state>
        </div>

        <mat-paginator [pageSizeOptions]="[10, 25, 50]"></mat-paginator>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 32px; max-width: 1400px; margin: 0 auto; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 32px; }
    .page-title { margin: 0; font-size: 2rem; font-weight: 800; color: var(--ink); letter-spacing: -0.02em; }
    .page-subtitle { margin: 8px 0 0; color: var(--muted); font-size: 1rem; }

    .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 24px; margin-bottom: 32px; }
    .stat-card { 
      position: relative;
      border-radius: var(--radius-xl); 
      border: 1px solid var(--line); 
      background: var(--bg-elevated);
      box-shadow: var(--shadow-md); 
      transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1), box-shadow 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      overflow: hidden;
    }
    .stat-card:hover {
      transform: translateY(-4px);
      box-shadow: var(--shadow-lg);
    }
    .stat-icon { 
      width: 52px; 
      height: 52px; 
      line-height: 52px; 
      text-align: center; 
      border-radius: var(--radius-md); 
      font-size: 26px; 
      transition: transform 0.3s ease;
    }
    .stat-card:hover .stat-icon {
      transform: scale(1.1) rotate(6deg);
    }
    
    .stat-icon.total { background: var(--info-soft); color: var(--info); }
    .stat-icon.active { background: rgba(16, 185, 129, 0.12); color: #10b981; }
    .stat-icon.admin { background: var(--purple-soft); color: var(--purple); }

    .filter-card { 
      margin-bottom: 24px; 
      border-radius: var(--radius-lg); 
      border: 1px solid var(--line); 
      background: var(--bg-elevated);
      box-shadow: var(--shadow-sm); 
    }
    .filter-row { display: flex; align-items: center; gap: 16px; padding: 16px 24px !important; }
    .search-field { flex: 1; max-width: 400px; }
    .filter-field { width: 180px; }
    ::ng-deep .filter-row .mat-mdc-form-field-subscript-wrapper { display: none; }
    .spacer { flex: 1; }

    .table-container { 
      position: relative; 
      background: var(--bg-elevated); 
      border-radius: var(--radius-lg); 
      border: 1px solid var(--line);
      box-shadow: var(--shadow-sm); 
      overflow: hidden; 
    }
    .loading-overlay { position: absolute; inset: 0; background: rgba(255,255,255,0.7); z-index: 10; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(4px); }
    table { width: 100%; background: transparent; }

    ::ng-deep table th.mat-mdc-header-cell {
      background: var(--surface) !important;
      font-weight: 700 !important;
      color: var(--ink) !important;
      text-transform: uppercase !important;
      font-size: 0.75rem !important;
      letter-spacing: 0.5px !important;
      border-bottom: 2px solid var(--line) !important;
      padding: 16px 24px !important;
    }
    ::ng-deep table td.mat-mdc-cell {
      padding: 16px 24px !important;
      border-bottom: 1px solid var(--line) !important;
      color: var(--ink-secondary) !important;
      font-size: 0.9rem !important;
    }

    .hover-row { transition: background 0.2s ease; background: transparent; }
    .hover-row:hover { background: var(--surface-hover) !important; }

    .user-cell { display: flex; flex-direction: column; }
    .user-name { font-weight: 700; color: var(--ink); }
    .user-id { font-size: 0.75rem; color: var(--muted); margin-top: 2px; }

    code { font-family: 'JetBrains Mono', 'Fira Code', monospace; background: var(--surface); border: 1px solid var(--line); padding: 4px 8px; border-radius: 6px; color: var(--ink-secondary); font-size: 0.85rem; }

    .contact-cell { display: flex; flex-direction: column; }
    .contact-cell .phone { font-weight: 600; color: var(--ink); }
    .contact-cell .email { font-size: 0.75rem; color: var(--muted); max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 2px; }

    .role-badge { padding: 4px 12px; border-radius: 6px; background: var(--brand-soft); color: var(--brand-saffron-dark); font-weight: 700; font-size: 0.75rem; border: 1px solid var(--line); text-transform: uppercase; letter-spacing: 0.5px; }

    .status-chip { display: inline-flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 999px; font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; border: 1px solid transparent; }
    .status-chip mat-icon { font-size: 10px; width: 10px; height: 10px; }
    .status-chip.active { background: rgba(16, 185, 129, 0.12); color: #10b981; border-color: rgba(16, 185, 129, 0.2); }
    .status-chip.inactive { background: rgba(239, 68, 68, 0.12); color: #ef4444; border-color: rgba(239, 68, 68, 0.2); }

    .empty-state-wrapper { padding: 48px 24px; }
    .tabular-nums { font-variant-numeric: tabular-nums; }
    .table-paginator { border-top: 1px solid var(--line); background: transparent; }

    @media (max-width: 768px) {
      .page-container { padding: 16px; }
      .header-row { flex-direction: column; gap: 16px; align-items: flex-start; }
      .filter-row { flex-direction: column; align-items: stretch; padding: 16px !important; }
      .search-field { max-width: none; }
      .filter-field { width: 100%; }
    }
  `]
})
export class StaffPageComponent implements AfterViewInit {
  private readonly api = inject(BusinessApiService);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  staff: BusinessStaffItem[] = [];
  loaded = false;
  loadError = '';
  dataSource = new MatTableDataSource<BusinessStaffItem>([]);
  displayedColumns = ['name', 'loginId', 'role', 'contact', 'status', 'updatedAt'];

  searchTerm = '';
  roleFilter = 'ALL';
  statusFilter = 'ALL';

  constructor() {
    this.loadStaff();
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.dataSource.filterPredicate = (data, filter) => {
      const search = filter.toLowerCase();
      const matchesSearch = !search || [
        data.name,
        data.loginId,
        data.email ?? '',
        data.whatsappNumber ?? '',
        data.role
      ].some(v => v.toLowerCase().includes(search));

      const matchesRole = this.roleFilter === 'ALL' || data.role === this.roleFilter;
      const matchesStatus =
        this.statusFilter === 'ALL' ||
        (this.statusFilter === 'ACTIVE' && data.active) ||
        (this.statusFilter === 'INACTIVE' && !data.active);

      return matchesSearch && matchesRole && matchesStatus;
    };
  }

  get roleOptions(): string[] {
    return [...new Set(this.staff.map((item) => item.role))].sort();
  }

  loadStaff(): void {
    this.loaded = false;
    this.loadError = '';
    this.api.getStaff().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.staff = data;
        this.dataSource.data = data;
        this.loaded = true;
      },
      error: (err) => { 
        this.loadError = err?.error?.error ?? err?.error?.message ?? 'Failed to load staff.'; 
        this.loaded = true;
        this.staff = [];
        this.dataSource.data = [];
      }
    });
  }

  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }

  applyFilters() {
    const current = this.dataSource.filter;
    this.dataSource.filter = '';
    this.dataSource.filter = current;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.roleFilter = 'ALL';
    this.statusFilter = 'ALL';
    this.dataSource.filter = '';
    this.applyFilters();
  }

  activeCount = () => this.staff.filter(s => s.active).length;
  adminCount = () => this.staff.filter(s => s.role.toLowerCase().includes('admin')).length;

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }
}
