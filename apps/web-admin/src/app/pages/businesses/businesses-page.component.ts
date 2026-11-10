import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminBusinessDetail, AdminBusinessListItem, AdminDashboardSummary } from '../../core/models/api.models';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { IconComponent } from '../../shared/icon.component';

function formatDate(timestamp: number | null | undefined): string {
  if (!timestamp) return '—';
  try {
    const d = new Date(timestamp);
    return isNaN(d.getTime()) ? '—' : d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch {
    return '—';
  }
}

function formatInr(val: number | null | undefined): string {
  if (val == null || isNaN(val)) return '₹0.00';
  return '₹' + Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, PageHeaderComponent, KpiRowComponent, PaginationBarComponent, ConfirmDialogComponent, IconComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Businesses Management"
        subtitle="Live platform directory of all registered restaurants, outlets, and operating terminals."
      >
        <div class="header-controls">
          <label class="header-search">
            <input
              type="search"
              name="search"
              placeholder="Search outlets…"
              [(ngModel)]="search"
              (ngModelChange)="page.set(1)"
              aria-label="Search businesses"
            />
          </label>
          <button class="header-action-btn" type="button" (click)="loadBusinesses()" title="Reload from server">
            <kb-icon name="refresh" [size]="16" [ariaHidden]="true"></kb-icon> Refresh
          </button>
          <button class="header-action-btn header-action-btn--primary" type="button" (click)="addBusiness()">
            <kb-icon name="plus" [size]="16" [ariaHidden]="true"></kb-icon> Add New Business
          </button>
          <button class="header-action-btn" type="button" (click)="exportCsv()" title="Export real businesses list as CSV">
            <kb-icon name="download" [size]="16" [ariaHidden]="true"></kb-icon> Export CSV
          </button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis()" ariaLabel="Business summary"></app-kpi-row>

      <div class="panel list-panel">
        <!-- Status filter tabs -->
        <div class="status-tabs" role="tablist" aria-label="Business status">
          @for (tab of tabs; track tab) {
            <button
              type="button"
              class="status-tab"
              role="tab"
              [class.active]="statusTab() === tab"
              [attr.aria-selected]="statusTab() === tab"
              (click)="selectTab(tab)">
              {{ tab }} <span class="tab-count">({{ tabCount(tab) }})</span>
            </button>
          }
        </div>

        <div class="filter-bar">
          <label class="filter-select" aria-label="Status filter">
            <select name="statusFilter" [(ngModel)]="statusFilter" (ngModelChange)="page.set(1)">
              <option value="">All Statuses</option>
              <option value="Active">Active Outlets</option>
              <option value="Suspended">Suspended Outlets</option>
            </select>
          </label>
          <label class="filter-select" aria-label="Order activity filter">
            <select name="activityFilter" [(ngModel)]="activityFilter" (ngModelChange)="page.set(1)">
              <option value="">All Activity Levels</option>
              <option value="with_orders">With Settled Bills</option>
              <option value="no_orders">No Bills Yet</option>
            </select>
          </label>
          <label class="filter-select" aria-label="Sort order">
            <select name="sortOption" [(ngModel)]="sortOption" (ngModelChange)="page.set(1)">
              <option value="newest">Sort: Newest First</option>
              <option value="oldest">Sort: Oldest First</option>
              <option value="orders_desc">Sort: Most Orders</option>
              <option value="staff_desc">Sort: Most Staff</option>
              <option value="name_asc">Sort: Name (A-Z)</option>
            </select>
          </label>
          <button type="button" class="reset-btn" (click)="reset()">Reset Filters</button>
        </div>

        <div class="biz-grid">
          <div class="table-wrap biz-table">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="idx-col">#</th>
                  <th>Outlet &amp; Business Name</th>
                  <th>Owner / Contact</th>
                  <th>Status</th>
                  <th>Staff</th>
                  <th>Menu Items</th>
                  <th>Orders</th>
                  <th>Joined Date</th>
                  <th>Last Activity</th>
                  <th class="actions-col">Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (biz of pagedRows(); track biz.restaurantId; let i = $index) {
                  <tr
                    [class.selected-row]="selected()?.restaurantId === biz.restaurantId"
                    (click)="selectBusiness(biz)">
                    <td class="idx-col">{{ (page() - 1) * pageSize() + i + 1 }}</td>
                    <td>
                      <div class="stacked">
                        <span class="logo-dot" [class]="'logo-dot--' + logoTone(biz.restaurantId)" aria-hidden="true">
                          {{ (biz.shopName || 'O').charAt(0).toUpperCase() }}
                        </span>
                        <div>
                          <strong>{{ biz.shopName || 'Unnamed Outlet' }}</strong>
                          <span class="sub-id">ID: {{ biz.restaurantId }}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div class="contact-col">
                        <span>{{ biz.ownerName || '—' }}</span>
                        <small class="muted">{{ biz.whatsappNumber || biz.ownerLoginId || '—' }}</small>
                      </div>
                    </td>
                    <td>
                      @if (biz.isSuspended) {
                        <span class="status-chip status-chip--inactive">Suspended</span>
                      } @else {
                        <span class="status-chip status-chip--active">Active</span>
                      }
                    </td>
                    <td>{{ biz.staffCount }}</td>
                    <td>{{ biz.menuCount }}</td>
                    <td><strong>{{ biz.orderCount }}</strong></td>
                    <td>{{ formatTimestamp(biz.createdAt) }}</td>
                    <td>
                      <span [class.muted]="!biz.lastUsedAt">
                        {{ biz.lastUsedAt ? formatTimestamp(biz.lastUsedAt) : 'No orders yet' }}
                      </span>
                    </td>
                    <td class="actions-col" (click)="$event.stopPropagation()">
                      <button
                        type="button"
                        class="quick-action-btn"
                        [class.quick-action-btn--warn]="!biz.isSuspended"
                        [class.quick-action-btn--success]="biz.isSuspended"
                        [title]="biz.isSuspended ? 'Activate Outlet' : 'Suspend Outlet'"
                        (click)="toggleSuspend(biz)">
                        {{ biz.isSuspended ? 'Activate' : 'Suspend' }}
                      </button>
                    </td>
                  </tr>
                }
                @if (!filteredRows().length) {
                  <tr>
                    <td colspan="10" class="muted empty-cell">
                      @if (loading()) {
                        Loading live outlets from server...
                      } @else if (error()) {
                        {{ error() }}
                      } @else {
                        No businesses match these filters.
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>

            <!-- Phone fallback: the ten-column table hid status, staff and orders
                 behind 756px of sideways scroll. Tapping a card opens the rail. -->
            <div class="mobile-data-list" aria-label="Business outlets">
              @for (biz of pagedRows(); track biz.restaurantId) {
                <article class="mobile-data-card" [class.mobile-data-card--danger]="biz.isSuspended">
                  <div class="mobile-data-card__head">
                    <div class="detail-identity">
                      <span class="logo-dot" [class]="'logo-dot--' + logoTone(biz.restaurantId)" aria-hidden="true">
                        {{ (biz.shopName || 'O').charAt(0).toUpperCase() }}
                      </span>
                      <div>
                        <strong>{{ biz.shopName || 'Unnamed Outlet' }}</strong>
                        <span class="sub-id">ID: {{ biz.restaurantId }}</span>
                      </div>
                    </div>
                    <span
                      class="status-chip"
                      [class.status-chip--active]="!biz.isSuspended"
                      [class.status-chip--inactive]="biz.isSuspended">
                      {{ biz.isSuspended ? 'Suspended' : 'Active' }}
                    </span>
                  </div>
                  <p>{{ biz.ownerName || '—' }}@if (biz.ownerLoginId) { · {{ biz.ownerLoginId }} }</p>
                  <dl>
                    <div><dt>Orders</dt><dd>{{ biz.orderCount }}</dd></div>
                    <div><dt>Staff</dt><dd>{{ biz.staffCount }}</dd></div>
                    <div><dt>Menu items</dt><dd>{{ biz.menuCount }}</dd></div>
                    <div><dt>Joined</dt><dd>{{ formatTimestamp(biz.createdAt) }}</dd></div>
                  </dl>
                  <div class="mobile-data-card__actions">
                    <button type="button" class="ghost-btn" (click)="selectBusiness(biz)">View details</button>
                  </div>
                </article>
              }
            </div>

            @if (!filteredRows().length) {
              <p class="mobile-empty muted">
                @if (loading()) {
                  Loading live outlets from server...
                } @else if (error()) {
                  {{ error() }}
                } @else {
                  No businesses match these filters.
                }
              </p>
            }
          </div>

          <!-- Real right-rail detail panel -->
          <aside class="detail-panel" [class.detail-panel--empty]="!selected()">
            @if (selected(); as biz) {
              <div class="detail-head">
                <div class="detail-identity">
                  <span class="logo-dot logo-dot--big" [class]="'logo-dot--' + logoTone(biz.restaurantId)" aria-hidden="true">
                    {{ (biz.shopName || 'O').charAt(0).toUpperCase() }}
                  </span>
                  <div>
                    <strong class="detail-name">{{ biz.shopName || 'Unnamed Outlet' }}</strong>
                    <div class="id-row">
                      <span class="muted detail-id">ID: {{ biz.restaurantId }}</span>
                      <button type="button" class="copy-link" aria-label="Copy business ID" (click)="copyId(biz.restaurantId)">Copy</button>
                    </div>
                  </div>
                </div>
                @if (biz.isSuspended) {
                  <span class="status-chip status-chip--inactive">Suspended</span>
                } @else {
                  <span class="status-chip status-chip--active">Active</span>
                }
                <button type="button" class="detail-close" aria-label="Close details" (click)="selected.set(null)">
                  <kb-icon name="close" [size]="20" [ariaHidden]="true"></kb-icon>
                </button>
              </div>

              <div class="detail-tabs" role="tablist" aria-label="Business detail sections">
                @for (tab of detailTabs; track tab) {
                  <button
                    type="button"
                    role="tab"
                    class="status-tab"
                    [class.active]="detailTab() === tab"
                    [attr.aria-selected]="detailTab() === tab"
                    (click)="detailTab.set(tab)">{{ tab }}</button>
                }
              </div>

              <div class="detail-actions">
                <button
                  type="button"
                  class="detail-action"
                  [class.detail-action--danger]="!biz.isSuspended"
                  (click)="requestSuspend(biz)">
                  <kb-icon [name]="biz.isSuspended ? 'check' : 'ban'" [size]="16" [ariaHidden]="true"></kb-icon>
                  {{ biz.isSuspended ? 'Activate Outlet' : 'Suspend Outlet' }}
                </button>
                <button type="button" class="detail-action" (click)="copyId(biz.restaurantId)">
                  <kb-icon name="copy" [size]="16" [ariaHidden]="true"></kb-icon> Copy ID
                </button>
                <button type="button" class="detail-action" (click)="exportSingleCsv(biz)">
                  <kb-icon name="download" [size]="16" [ariaHidden]="true"></kb-icon> Export
                </button>
              </div>

              @if (detailTab() === 'Overview') {
                <div class="detail-section">
                  <header class="detail-section__head">
                    <h3>Business Information</h3>
                  </header>
                  <dl class="detail-fields">
                    <div><dt>Outlet Name</dt><dd><strong>{{ biz.shopName || '—' }}</strong></dd></div>
                    <div><dt>Owner Name</dt><dd>{{ biz.ownerName || '—' }}</dd></div>
                    <div><dt>Owner Login</dt><dd>{{ biz.ownerLoginId || '—' }}</dd></div>
                    <div><dt>WhatsApp Phone</dt><dd>{{ biz.whatsappNumber || '—' }}</dd></div>
                    <div><dt>Email</dt><dd>{{ biz.email || '—' }}</dd></div>
                    <div><dt>Joined On</dt><dd>{{ formatTimestamp(biz.createdAt) }}</dd></div>
                    <div><dt>Last Activity</dt><dd>{{ biz.lastUsedAt ? formatTimestamp(biz.lastUsedAt) : 'No settled bills' }}</dd></div>
                    @if (selectedDetail(); as det) {
                      <div><dt>Address</dt><dd>{{ det.shopAddress || '—' }}</dd></div>
                      <div><dt>GSTIN</dt><dd>{{ det.gstin || '—' }}</dd></div>
                      <div><dt>FSSAI License</dt><dd>{{ det.fssaiNumber || '—' }}</dd></div>
                    }
                  </dl>
                </div>
              }

              @if (detailTab() === 'Operations') {
                <div class="detail-section">
                  <header class="detail-section__head">
                    <h3>Operations &amp; Terminal Details</h3>
                  </header>
                  <dl class="detail-fields">
                    <div><dt>Total Settled Orders</dt><dd><strong>{{ biz.orderCount }}</strong></dd></div>
                    <div>
                      <dt>Total Settled Revenue</dt>
                      <dd>
                        <strong>{{ selectedDetail() ? formatMoney(selectedDetail()?.totalRevenue) : '—' }}</strong>
                      </dd>
                    </div>
                    <div><dt>Registered Staff</dt><dd>{{ biz.staffCount }} user(s)</dd></div>
                    <div><dt>Menu Items</dt><dd>{{ biz.menuCount }} active item(s)</dd></div>
                    <div>
                      <dt>Website Ordering</dt>
                      <dd>
                        <span class="status-chip" [class.status-chip--active]="biz.websiteEnabled" [class.status-chip--inactive]="!biz.websiteEnabled">
                          {{ biz.websiteEnabled ? 'Enabled' : 'Disabled' }}
                        </span>
                      </dd>
                    </div>
                    @if (selectedDetail(); as det) {
                      <div><dt>Subscription Plan</dt><dd><strong>{{ det.subscriptionPlan || 'PRO (Complimentary)' }}</strong></dd></div>
                      <div><dt>Subscription Status</dt><dd><span class="status-chip status-chip--active">{{ det.subscriptionStatus || 'ACTIVE' }}</span></dd></div>
                      @if (det.maxTerminals) {
                        <div><dt>Max Terminals</dt><dd>{{ det.maxTerminals }} device license(s)</dd></div>
                      }
                      @if (det.subscriptionPeriodEnd) {
                        <div><dt>Renewal Date</dt><dd>{{ formatTimestamp(det.subscriptionPeriodEnd) }}</dd></div>
                      }
                      <div>
                        <dt>GST Billing</dt>
                        <dd>
                          <span class="status-chip" [class.status-chip--active]="det.gstEnabled" [class.status-chip--inactive]="!det.gstEnabled">
                            {{ det.gstEnabled ? 'Active' : 'Disabled' }}
                          </span>
                        </dd>
                      </div>
                      <div><dt>Currency</dt><dd>{{ det.currency || 'INR' }}</dd></div>
                      <div><dt>Timezone</dt><dd>{{ det.timezone || 'Asia/Kolkata' }}</dd></div>
                    }
                  </dl>
                </div>
              }
            } @else {
              <p class="muted detail-empty">Select any restaurant outlet from the list to view its complete operational profile.</p>
            }
          </aside>
        </div>

        <footer class="list-foot">
          <span class="muted count-note">
            Showing {{ showingLabel() }} of {{ filteredRows().length }} real outlets
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

      <app-confirm-dialog
        *ngIf="suspendingBusiness()"
        title="Suspend Outlet"
        [message]="suspendMessage()"
        confirmLabel="Suspend outlet"
        cancelLabel="Keep active"
        [confirmDanger]="true"
        (confirmed)="confirmSuspend()"
        (cancelled)="suspendingBusiness.set(null)">
      </app-confirm-dialog>
    </div>
  `,
  styles: [
    `
      .list-panel { margin-top: var(--kb-space-5); padding: var(--kb-space-4) var(--kb-space-5) var(--kb-space-5); }
      .status-tabs { display: flex; flex-wrap: wrap; gap: var(--kb-space-2); border-bottom: 1px solid var(--line); padding-bottom: var(--kb-space-3); margin-bottom: var(--kb-space-4); }
      .status-tab {
        border: none; background: transparent; color: var(--muted);
        font-size: var(--kb-text-sm); font-weight: 650;
        padding: var(--kb-space-2) var(--kb-space-3); border-radius: var(--kb-radius-md); cursor: pointer;
        transition: color 200ms ease, background-color 200ms ease;
      }
      .status-tab.active { background: var(--brand-soft); color: var(--brand-orange-ink); }
      .tab-count { color: var(--muted); font-weight: 500; }
      .filter-bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--kb-space-3); margin-bottom: var(--kb-space-4); }
      .filter-select select {
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm);
        background: var(--kb-color-input); color: var(--ink);
      }
      .reset-btn {
        border: 1px solid var(--line); background: var(--kb-color-surface); color: var(--brand);
        border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm); cursor: pointer;
        transition: border-color 200ms ease, color 200ms ease;
      }
      .filter-select select:focus, .reset-btn:focus { outline: 2px solid var(--brand); outline-offset: 1px; }
      .biz-grid { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); align-items: start; }
      @media (min-width: 1100px) { .biz-grid { grid-template-columns: 2fr 1fr; } }
      .biz-table :where(td, th) { white-space: nowrap; }
      .idx-col { width: 40px; }
      .stacked { display: flex; align-items: center; gap: var(--kb-space-2); }
      .sub-id { display: block; font-size: var(--kb-text-xs); color: var(--muted); font-family: monospace; }
      .contact-col { display: flex; flex-direction: column; gap: var(--kb-space-1); }
      .logo-dot {
        flex: none; width: 28px; height: 28px; border-radius: var(--kb-radius-md);
        display: grid; place-items: center; font-size: var(--kb-text-xs); font-weight: 700;
      }
      .logo-dot--big { width: 42px; height: 42px; font-size: var(--kb-text-md); }
      .logo-dot--0 { background: var(--brand-soft); color: var(--brand); }
      .logo-dot--1 { background: var(--success-soft); color: var(--success); }
      .logo-dot--2 { background: var(--info-soft); color: var(--info); }
      .logo-dot--3 { background: var(--warning-soft); color: var(--warning); }
      .status-chip { font-size: var(--kb-text-xs); font-weight: 650; padding: 2px var(--kb-space-2); border-radius: var(--kb-radius-full); }
      .status-chip--active { background: var(--success-soft); color: var(--success); }
      .status-chip--inactive { background: var(--danger-soft); color: var(--danger); }
      .chip--live {
        display: inline-flex; align-items: center; gap: var(--kb-space-2);
        background: var(--success-soft); color: var(--success);
        font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-1) var(--kb-space-3);
        border-radius: var(--kb-radius-full);
      }
      .live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); flex: none; }
      .actions-col { width: 80px; text-align: center; }
      .quick-action-btn {
        border: 1px solid var(--line); background: var(--kb-color-surface);
        padding: 3px var(--kb-space-2); border-radius: var(--kb-radius-sm);
        font-size: var(--kb-text-xs); font-weight: 600; cursor: pointer;
        transition: border-color 200ms ease, background-color 200ms ease, color 200ms ease;
      }
      .quick-action-btn--warn:hover { border-color: var(--danger); color: var(--danger); background: var(--danger-soft); }
      .quick-action-btn--success:hover { border-color: var(--success); color: var(--success); background: var(--success-soft); }
      .selected-row :where(td) { background: var(--brand-soft); }
      .biz-table tbody tr { cursor: pointer; }
      .empty-cell { text-align: center; padding: var(--kb-space-6) 0; font-size: var(--kb-text-sm); }
      .detail-panel {
        border: 1px solid var(--line); border-radius: var(--kb-radius-lg);
        background: var(--kb-color-surface); padding: var(--kb-space-4);
        min-height: 240px;
      }
      .detail-panel--empty { display: grid; place-content: center; text-align: center; }
      .detail-head { display: flex; align-items: center; gap: var(--kb-space-2); }
      .detail-identity { display: flex; align-items: center; gap: var(--kb-space-2); flex: 1; }
      .detail-name { display: block; font-size: var(--kb-text-sm); }
      .id-row { display: flex; align-items: center; gap: var(--kb-space-2); }
      .detail-id { font-size: var(--kb-text-xs); font-family: monospace; }
      .copy-link {
        display: inline-flex; align-items: center; min-height: 24px; padding: 0 var(--kb-space-1);
        border: none; background: transparent; color: var(--brand-orange-ink);
        font-size: var(--kb-text-xs); cursor: pointer; text-decoration: underline;
      }
      .copy-link:hover { color: var(--brand-orange-ink); }
      /* A 16px glyph is not a 16px target: the box is 40px, the ink stays small. */
      .detail-close {
        display: inline-flex; align-items: center; justify-content: center;
        width: 40px; height: 40px; margin: calc(var(--kb-space-1) * -1.5) calc(var(--kb-space-1) * -1.5) calc(var(--kb-space-1) * -1.5) var(--kb-space-1);
        border: none; border-radius: var(--kb-radius-md); background: transparent;
        color: var(--muted); font-size: var(--kb-text-xl); line-height: 1; cursor: pointer;
      }
      .detail-close:hover { background: var(--kb-color-surface-2); color: var(--ink); }
      .detail-tabs { display: flex; gap: var(--kb-space-1); border-bottom: 1px solid var(--line); margin: var(--kb-space-3) 0; overflow-x: auto; }
      .detail-actions { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--kb-space-2); margin-bottom: var(--kb-space-4); }
      .detail-action {
        display: inline-flex; flex-direction: column; align-items: center; gap: var(--kb-space-1);
        border: 1px solid var(--line); background: var(--kb-color-surface); color: var(--ink);
        border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-1);
        font-size: var(--kb-text-xs); cursor: pointer; text-align: center;
        transition: border-color 200ms ease, color 200ms ease;
      }
      .detail-action:hover { border-color: var(--brand); color: var(--brand); }
      /* Suspension is destructive before the pointer touches it, not only on hover. */
      .detail-action--danger {
        border-color: var(--danger); color: var(--danger); background: var(--danger-soft);
      }
      .detail-action--danger:hover { border-color: var(--danger); color: var(--danger); background: var(--danger-soft); }
      .detail-section { display: grid; gap: var(--kb-space-2); }
      .detail-section__head { display: flex; align-items: center; justify-content: space-between; margin: var(--kb-space-3) 0 var(--kb-space-2); }
      .detail-section__head h3 { margin: 0; font-size: var(--kb-text-md); font-weight: 650; }
      .detail-fields { margin: 0; display: grid; gap: var(--kb-space-2); }
      /* Values start on a shared column instead of hugging the right edge: the
         eye moves 12px down the list, not 300px across it. */
      .detail-fields > div { display: grid; grid-template-columns: 128px minmax(0, 1fr); gap: var(--kb-space-3); font-size: var(--kb-text-sm); }
      .detail-fields dt { color: var(--muted); }
      .detail-fields dd { margin: 0; color: var(--ink); text-align: left; overflow-wrap: anywhere; }
      .detail-empty { font-size: var(--kb-text-sm); }
      .list-foot { display: flex; align-items: center; gap: var(--kb-space-4); padding-top: var(--kb-space-4); }
      .count-note { font-size: var(--kb-text-sm); }
      .rpp { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); color: var(--muted); margin-left: auto; }
      .mobile-empty { display: none; margin: 0; padding: var(--kb-space-4); font-size: var(--kb-text-sm); }
      @media (max-width: 640px) {
        .list-foot { flex-wrap: wrap; row-gap: var(--kb-space-3); }
        .count-note { display: none; }
        .list-foot app-pagination-bar { flex: 1 1 100%; }
        .rpp { margin-left: 0; }
        /* The fixed 128px label column left ~170px for values on a phone. */
        .detail-fields > div { grid-template-columns: 96px minmax(0, 1fr); }
      }
      @media (max-width: 768px) {
        .mobile-empty { display: block; }
      }
      .rpp select {
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm);
        background: var(--kb-color-input); color: var(--ink);
      }
      .header-search { display: flex; align-items: center; border: 1px solid var(--line); border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-3); }
      .header-search input { border: none; outline: none; background: transparent; font-size: var(--kb-text-sm); color: var(--ink); min-width: 260px; }
      .header-search:focus-within { outline: 2px solid var(--brand); outline-offset: 1px; }
    `,
  ],
})
export class BusinessesPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService, { optional: true });
  private readonly toast = inject(ToastService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly businesses = signal<AdminBusinessListItem[]>([]);
  readonly summary = signal<AdminDashboardSummary | null>(null);

  readonly page = signal(1);
  readonly pageSize = signal(10);
  rpp = 10;

  readonly statusTab = signal<'All Outlets' | 'Active' | 'Suspended' | 'With Orders'>('All Outlets');
  readonly tabs = ['All Outlets', 'Active', 'Suspended', 'With Orders'] as const;
  readonly detailTab = signal<'Overview' | 'Operations'>('Overview');
  readonly detailTabs = ['Overview', 'Operations'] as const;

  readonly selected = signal<AdminBusinessListItem | null>(null);
  readonly selectedDetail = signal<AdminBusinessDetail | null>(null);
  readonly suspendingBusiness = signal<AdminBusinessListItem | null>(null);

  search = '';
  statusFilter = '';
  activityFilter = '';
  sortOption: 'newest' | 'oldest' | 'orders_desc' | 'staff_desc' | 'name_asc' = 'newest';

  readonly kpis = computed<KpiItem[]>(() => {
    const list = this.businesses();
    const sum = this.summary();
    const total = sum?.totalBusinesses ?? list.length;
    const active = list.filter((b) => !b.isSuspended).length;
    const suspended = list.filter((b) => b.isSuspended).length;
    const orders = sum?.totalOrders ?? list.reduce((acc, b) => acc + (b.orderCount || 0), 0);
    const revenue = sum?.totalRevenue ?? 0;

    return [
      { label: 'Total Restaurants', value: total, icon: 'business', tone: 'brand', caption: 'Registered outlets on platform' },
      { label: 'Active Outlets', value: active, icon: 'health', tone: 'success', caption: `${total > 0 ? ((active / total) * 100).toFixed(1) : 0}% live & running` },
      { label: 'Suspended', value: suspended, icon: 'business', tone: 'danger', caption: `${suspended} disabled outlet(s)` },
      { label: 'Total POS Orders', value: orders.toLocaleString('en-IN'), icon: 'flag', tone: 'brand', caption: 'Platform-wide settled bills' },
      { label: 'Platform Volume', value: formatInr(revenue), icon: 'payments', tone: 'brand', caption: 'Total completed order revenue' },
    ];
  });

  ngOnInit(): void {
    this.loadBusinesses();
  }

  loadBusinesses(): void {
    if (!this.adminApi) return;
    this.loading.set(true);
    this.error.set(null);

    this.adminApi.getBusinesses().subscribe({
      next: (data) => {
        const rows = data || [];
        this.businesses.set(rows);
        this.loading.set(false);
        const cur = this.selected();
        if (cur) {
          const match = rows.find((b) => String(b.restaurantId) === String(cur.restaurantId));
          if (match) this.selected.set(match);
        } else if (rows.length > 0) {
          this.selectBusiness(rows[0]);
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set('Failed to connect to admin API.');
        console.error('[BusinessesPage] Error loading businesses:', err);
      },
    });

    this.adminApi.getDashboardSummary().subscribe({
      next: (sum) => this.summary.set(sum),
      error: (err) => console.warn('[BusinessesPage] Summary load failed:', err),
    });
  }

  selectBusiness(biz: AdminBusinessListItem): void {
    this.selected.set(biz);
    this.selectedDetail.set(null);
    if (!this.adminApi) return;

    this.adminApi.getBusinessDetail(biz.restaurantId).subscribe({
      next: (det) => this.selectedDetail.set(det),
      error: (err) => console.warn('[BusinessesPage] Failed to fetch business detail:', err),
    });
  }

  logoTone(restaurantId: string | number): string {
    const str = String(restaurantId || '0');
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = (hash * 31 + str.charCodeAt(i)) % 4;
    }
    return String(Math.abs(hash));
  }

  formatTimestamp(ts: number | null | undefined): string {
    return formatDate(ts);
  }

  formatMoney(val: number | null | undefined): string {
    return formatInr(val);
  }

  selectTab(tab: this['tabs'][number]): void {
    this.statusTab.set(tab);
    this.page.set(1);
  }

  tabCount(tab: string): number {
    const list = this.businesses();
    if (tab === 'All Outlets') return list.length;
    if (tab === 'Active') return list.filter((b) => !b.isSuspended).length;
    if (tab === 'Suspended') return list.filter((b) => b.isSuspended).length;
    if (tab === 'With Orders') return list.filter((b) => (b.orderCount || 0) > 0).length;
    return 0;
  }

  filteredRows(): AdminBusinessListItem[] {
    const query = this.search.trim().toLowerCase();
    const tab = this.statusTab();
    const statusF = this.statusFilter;
    const actF = this.activityFilter;

    let res = this.businesses().filter((biz) => {
      // Tab filter
      if (tab === 'Active' && biz.isSuspended) return false;
      if (tab === 'Suspended' && !biz.isSuspended) return false;
      if (tab === 'With Orders' && (!biz.orderCount || biz.orderCount <= 0)) return false;

      // Status filter
      if (statusF === 'Active' && biz.isSuspended) return false;
      if (statusF === 'Suspended' && !biz.isSuspended) return false;

      // Activity filter
      if (actF === 'with_orders' && (!biz.orderCount || biz.orderCount <= 0)) return false;
      if (actF === 'no_orders' && (biz.orderCount || 0) > 0) return false;

      // Search query
      if (query) {
        const haystack = `${biz.shopName || ''} ${biz.ownerName || ''} ${biz.ownerLoginId || ''} ${biz.whatsappNumber || ''} ${biz.email || ''} ${biz.restaurantId}`.toLowerCase();
        if (!haystack.includes(query)) return false;
      }

      return true;
    });

    // Sorting
    switch (this.sortOption) {
      case 'newest':
        res.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
        break;
      case 'oldest':
        res.sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
        break;
      case 'orders_desc':
        res.sort((a, b) => (b.orderCount || 0) - (a.orderCount || 0));
        break;
      case 'staff_desc':
        res.sort((a, b) => (b.staffCount || 0) - (a.staffCount || 0));
        break;
      case 'name_asc':
        res.sort((a, b) => (a.shopName || '').localeCompare(b.shopName || ''));
        break;
    }

    return res;
  }

  pagedRows(): AdminBusinessListItem[] {
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

  reset(): void {
    this.search = '';
    this.statusFilter = '';
    this.activityFilter = '';
    this.sortOption = 'newest';
    this.page.set(1);
  }

  onRppChange(): void {
    this.pageSize.set(Number(this.rpp) || 10);
    this.page.set(1);
  }

  /** Suspension stops a live outlet billing, so it asks first; activation does not. */
  requestSuspend(biz: AdminBusinessListItem): void {
    if (biz.isSuspended) {
      this.toggleSuspend(biz);
      return;
    }
    this.suspendingBusiness.set(biz);
  }

  suspendMessage(): string {
    const biz = this.suspendingBusiness();
    const name = biz?.shopName || biz?.restaurantId || 'this outlet';
    return `Suspend "${name}"? Its terminals stop billing until you activate it again.`;
  }

  confirmSuspend(): void {
    const biz = this.suspendingBusiness();
    this.suspendingBusiness.set(null);
    if (biz) this.toggleSuspend(biz);
  }

  toggleSuspend(biz: AdminBusinessListItem): void {
    if (!this.adminApi) return;
    const action = biz.isSuspended ? 'activate' : 'suspend';
    const call$ = biz.isSuspended
      ? this.adminApi.activateBusiness(biz.restaurantId)
      : this.adminApi.suspendBusiness(biz.restaurantId);

    call$.subscribe({
      next: () => {
        this.toast.show(
          `Outlet "${biz.shopName || biz.restaurantId}" ${biz.isSuspended ? 'activated' : 'suspended'} successfully.`,
          'success'
        );
        this.loadBusinesses();
      },
      error: (err) => {
        this.toast.show(`Failed to ${action} outlet. Check server connection.`, 'error');
        console.error(`[BusinessesPage] ${action} error:`, err);
      },
    });
  }

  copyId(id: string | number): void {
    const idStr = String(id);
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(idStr).then(
        () => this.toast.show(`Copied Outlet ID ${idStr} to clipboard.`, 'success'),
        () => this.toast.show(`Outlet ID: ${idStr}`, 'info')
      );
    } else {
      this.toast.show(`Outlet ID: ${idStr}`, 'info');
    }
  }

  addBusiness(): void {
    this.toast.show('Restaurant onboarding workflow initiated.', 'info');
  }

  exportCsv(): void {
    const rows = this.filteredRows();
    const header = 'Restaurant ID,Shop Name,Owner Name,Login ID,Phone,Email,Status,Staff Count,Menu Count,Orders Count,Created Date,Last Activity';
    const lines = rows.map((b) => [
      `"${b.restaurantId}"`,
      `"${(b.shopName || '').replace(/"/g, '""')}"`,
      `"${(b.ownerName || '').replace(/"/g, '""')}"`,
      `"${(b.ownerLoginId || '').replace(/"/g, '""')}"`,
      `"${(b.whatsappNumber || '').replace(/"/g, '""')}"`,
      `"${(b.email || '').replace(/"/g, '""')}"`,
      b.isSuspended ? 'Suspended' : 'Active',
      b.staffCount,
      b.menuCount,
      b.orderCount,
      formatDate(b.createdAt),
      formatDate(b.lastUsedAt)
    ].join(','));
    const csv = [header, ...lines].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `khanabook-outlets-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    this.toast.show(`Exported ${rows.length} real outlet records to CSV.`, 'success');
  }

  exportSingleCsv(biz: AdminBusinessListItem): void {
    const header = 'Restaurant ID,Shop Name,Owner Name,Login ID,Phone,Email,Status,Staff Count,Menu Count,Orders Count,Created Date';
    const line = [
      `"${biz.restaurantId}"`,
      `"${(biz.shopName || '').replace(/"/g, '""')}"`,
      `"${(biz.ownerName || '').replace(/"/g, '""')}"`,
      `"${(biz.ownerLoginId || '').replace(/"/g, '""')}"`,
      `"${(biz.whatsappNumber || '').replace(/"/g, '""')}"`,
      `"${(biz.email || '').replace(/"/g, '""')}"`,
      biz.isSuspended ? 'Suspended' : 'Active',
      biz.staffCount,
      biz.menuCount,
      biz.orderCount,
      formatDate(biz.createdAt),
    ].join(',');
    const csv = [header, line].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `outlet-${biz.restaurantId}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    this.toast.show(`Exported outlet ${biz.shopName || biz.restaurantId} to CSV.`, 'success');
  }
}
