import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../core/services/toast.service';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';

interface TicketRow {
  id: string;
  subject: string;
  restaurant: string;
  category: 'Billing' | 'Technical' | 'Onboarding' | 'Feature Request';
  priority: 'Low' | 'Medium' | 'High';
  status: 'Open' | 'In Progress' | 'Resolved' | 'Closed';
  createdOn: string;
}

export const DEFAULT_TICKETS: TicketRow[] = [
  { id: 'TK-1041', subject: 'Printer not working', restaurant: 'Spice Villa', category: 'Technical', priority: 'High', status: 'Open', createdOn: '28 Oct 2026, 09:12 am' },
  { id: 'TK-1040', subject: 'GST configuration', restaurant: 'Dosa Plaza', category: 'Billing', priority: 'Medium', status: 'In Progress', createdOn: '27 Oct 2026, 05:15 pm' },
  { id: 'TK-1039', subject: 'App pairing issue', restaurant: 'The Grill House', category: 'Onboarding', priority: 'High', status: 'Open', createdOn: '27 Oct 2026, 11:30 am' },
  { id: 'TK-1038', subject: 'Payment issue', restaurant: 'Tasty Bites', category: 'Billing', priority: 'Medium', status: 'In Progress', createdOn: '27 Oct 2026, 07:00 am' },
  { id: 'TK-1037', subject: 'Add new staff', restaurant: 'Cafe Corner', category: 'Onboarding', priority: 'Low', status: 'Resolved', createdOn: '26 Oct 2026, 04:38 pm' },
  { id: 'TK-1036', subject: 'Refund not reflecting', restaurant: 'Royal Kitchen', category: 'Billing', priority: 'High', status: 'Open', createdOn: '26 Oct 2026, 10:00 am' },
  { id: 'TK-1035', subject: 'Menu categories', restaurant: 'Food Junction', category: 'Feature Request', priority: 'Low', status: 'Resolved', createdOn: '25 Oct 2026, 08:45 am' },
  { id: 'TK-1034', subject: 'Slow dashboard load', restaurant: 'Dine Hub', category: 'Technical', priority: 'Medium', status: 'Resolved', createdOn: '24 Oct 2026, 06:20 pm' },
  { id: 'TK-1033', subject: 'SMS notifications', restaurant: 'Madras Meals', category: 'Feature Request', priority: 'Low', status: 'Closed', createdOn: '23 Oct 2026, 02:12 pm' },
  { id: 'TK-1032', subject: 'Terminal offline', restaurant: 'Spice Villa', category: 'Technical', priority: 'High', status: 'In Progress', createdOn: '22 Oct 2026, 09:05 am' },
  { id: 'TK-1031', subject: 'Invoice download', restaurant: 'Biryani House', category: 'Billing', priority: 'Low', status: 'Closed', createdOn: '21 Oct 2026, 12:30 pm' },
  { id: 'TK-1030', subject: 'Inventory export', restaurant: 'Urban Dine', category: 'Feature Request', priority: 'Medium', status: 'Resolved', createdOn: '20 Oct 2026, 03:00 pm' },
];

/**
 * Admin → Support Tickets: Tracks operational requests raised by platform outlets.
 */
@Component({
  selector: 'app-support-tickets-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, PageHeaderComponent, KpiRowComponent, PaginationBarComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Support Tickets"
        subtitle="Track and resolve restaurant support requests raised on the platform."
      >
        <div class="header-controls">
          <span class="chip-live"><span class="live-dot" aria-hidden="true"></span> Support Desk</span>
          <button class="header-action-btn header-action-btn--primary" type="button" (click)="newTicket()">
            + New Ticket
          </button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis()" ariaLabel="Ticket summary"></app-kpi-row>

      <div class="panel list-panel">
        <div class="status-tabs" role="tablist" aria-label="Ticket status">
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
          <label class="filter-search">
            <input
              type="search"
              name="search"
              placeholder="Search by subject, #id or restaurant..."
              [(ngModel)]="search"
              (ngModelChange)="page.set(1)"
              aria-label="Search tickets"
            />
          </label>
          <label class="filter-select" aria-label="Category filter">
            <select name="categoryFilter" [(ngModel)]="categoryFilter" (ngModelChange)="page.set(1)">
              <option value="">All Categories</option>
              @for (category of categoryOptions; track category) { <option [value]="category">{{ category }}</option> }
            </select>
          </label>
          <label class="filter-select" aria-label="Priority filter">
            <select name="priorityFilter" [(ngModel)]="priorityFilter" (ngModelChange)="page.set(1)">
              <option value="">All Priorities</option>
              @for (priority of priorityOptions; track priority) { <option [value]="priority">{{ priority }}</option> }
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
                <th>Subject</th>
                <th>Restaurant</th>
                <th>Category</th>
                <th>Priority</th>
                <th>Status</th>
                <th>Created On</th>
                <th class="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (ticket of pagedRows(); track ticket.id; let i = $index) {
                <tr>
                  <td class="idx-col">{{ (page() - 1) * pageSize() + i + 1 }}</td>
                  <td>
                    <div class="stacked">
                      <strong>{{ ticket.subject }}</strong>
                      <span class="muted ticket-id">#{{ ticket.id }}</span>
                    </div>
                  </td>
                  <td>{{ ticket.restaurant }}</td>
                  <td><span class="role-name">{{ ticket.category }}</span></td>
                  <td>
                    <span class="chip" [class.warn]="ticket.priority === 'Medium'" [class.danger]="ticket.priority === 'High'" [class.success]="ticket.priority === 'Low'">
                      {{ ticket.priority }}
                    </span>
                  </td>
                  <td>
                    <span class="chip" [class.danger]="ticket.status === 'Open'" [class.warn]="ticket.status === 'In Progress'" [class.success]="ticket.status === 'Resolved' || ticket.status === 'Closed'">
                      {{ ticket.status }}
                    </span>
                  </td>
                  <td>{{ ticket.createdOn }}</td>
                  <td class="actions-col">
                    <button type="button" class="kebab-btn" aria-label="Row actions" (click)="rowMenu(ticket)">⋮</button>
                  </td>
                </tr>
              }
              @if (!filteredRows().length) {
                <tr>
                  <td colspan="8" class="muted empty-cell">
                    <strong>All 139 Outlets Operating Normally</strong>
                    <div class="empty-sub">No unresolved support tickets on the platform. Inquiries from WhatsApp or Email (+91 80000 00000 / support&#64;khanabook.com) will appear here.</div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <footer class="list-foot">
          <span class="muted count-note">{{ filteredRows().length }} of {{ tickets().length }} logged tickets</span>
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
      .status-tabs { display: flex; flex-wrap: wrap; gap: var(--kb-space-2); margin-bottom: var(--kb-space-4); }
      .status-tab {
        border: none;
        background: transparent;
        color: var(--muted);
        font-size: var(--kb-text-sm);
        font-weight: 650;
        padding: var(--kb-space-2) var(--kb-space-3);
        border-radius: var(--kb-radius-md);
        cursor: pointer;
      }
      .status-tab.active { background: var(--brand-soft); color: var(--brand); }
      .tab-count { color: var(--muted); font-weight: 500; }
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
      .filter-search input { flex: 1; border: none; outline: none; background: transparent; font-size: var(--kb-text-sm); color: var(--ink); }
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
      .ticket-id { font-size: var(--kb-text-xs); }
      .role-name { color: var(--brand); font-weight: 650; }
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
      .empty-sub { margin-top: var(--kb-space-1); font-size: var(--kb-text-xs); color: var(--muted); }
      .list-foot { display: flex; align-items: center; gap: var(--kb-space-4); padding-top: var(--kb-space-4); }
      .count-note { font-size: var(--kb-text-sm); }
      .rpp { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); color: var(--muted); margin-left: auto; }
    `,
  ],
})
export class SupportTicketsPageComponent {
  private readonly toast = inject(ToastService);

  readonly tickets = signal<TicketRow[]>(this.loadStoredTickets());
  readonly page = signal(1);
  readonly pageSize = signal(10);
  readonly rpp = 10;
  readonly statusTab = signal<'All' | 'Open' | 'In Progress' | 'Resolved' | 'Closed'>('All');
  readonly tabs = ['All', 'Open', 'In Progress', 'Resolved', 'Closed'] as const;

  readonly kpis = computed<KpiItem[]>(() => {
    const list = this.tickets();
    const total = list.length;
    const open = list.filter((t) => t.status === 'Open').length;
    const inProgress = list.filter((t) => t.status === 'In Progress').length;

    return [
      { label: 'Total Tickets', value: total, icon: 'help', tone: 'brand', caption: total > 0 ? 'Logged support requests' : 'Zero open inquiries' },
      { label: 'Open', value: open, icon: 'notifications', tone: open > 0 ? 'danger' : 'success', caption: open > 0 ? 'Needs response' : 'Inbox clear' },
      { label: 'In Progress', value: inProgress, icon: 'history', tone: inProgress > 0 ? 'warning' : 'success', caption: inProgress > 0 ? 'Being resolved' : 'None active' },
      { label: 'Platform Outlets', value: '139', icon: 'health', tone: 'success', caption: '100% outlets operational' },
    ];
  });

  readonly categoryOptions = ['Billing', 'Technical', 'Onboarding', 'Feature Request'] as const;
  readonly priorityOptions = ['Low', 'Medium', 'High'] as const;

  search = '';
  categoryFilter = '';
  priorityFilter = '';

  private loadStoredTickets(): TicketRow[] {
    try {
      const raw = localStorage.getItem('kbook_admin_support_tickets');
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      }
    } catch {}
    return [...DEFAULT_TICKETS];
  }

  private saveStoredTickets(list: TicketRow[]): void {
    try {
      localStorage.setItem('kbook_admin_support_tickets', JSON.stringify(list));
    } catch {}
  }

  tabCount(tab: string): number {
    if (tab === 'All') return this.tickets().length;
    return this.tickets().filter((t) => t.status === tab).length;
  }

  selectTab(tab: this['tabs'][number]): void {
    this.statusTab.set(tab);
    this.page.set(1);
  }

  filteredRows(): TicketRow[] {
    const searchLower = this.search.trim().toLowerCase();
    return this.tickets().filter((ticket) => {
      const matchesTab = this.statusTab() === 'All' || ticket.status === this.statusTab();
      const matchesCategory = !this.categoryFilter || ticket.category === this.categoryFilter;
      const matchesPriority = !this.priorityFilter || ticket.priority === this.priorityFilter;
      const haystack = `${ticket.subject} ${ticket.id} ${ticket.restaurant}`.toLowerCase();
      return matchesTab && matchesCategory && matchesPriority && (!searchLower || haystack.includes(searchLower));
    });
  }

  pagedRows(): TicketRow[] {
    const start = (this.page() - 1) * this.pageSize();
    return this.filteredRows().slice(start, start + this.pageSize());
  }

  clearFilters(): void {
    this.search = '';
    this.categoryFilter = '';
    this.priorityFilter = '';
    this.statusTab.set('All');
    this.page.set(1);
  }

  onRppChange(): void {
    this.pageSize.set(Number(this.rpp) || 10);
    this.page.set(1);
  }

  newTicket(): void {
    const subj = window.prompt('Enter support ticket subject / issue:');
    if (!subj) return;
    const rest = window.prompt('Enter restaurant outlet name:') || 'General Platform Outlets';
    const newTk: TicketRow = {
      id: `TK-${Date.now().toString().slice(-4)}`,
      subject: subj,
      restaurant: rest,
      category: 'Technical',
      priority: 'Medium',
      status: 'Open',
      createdOn: new Date().toLocaleDateString('en-IN', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      }),
    };
    const updated = [newTk, ...this.tickets()];
    this.tickets.set(updated);
    this.saveStoredTickets(updated);
    this.toast.show(`Support ticket ${newTk.id} created successfully.`, 'success');
  }

  rowMenu(ticket: TicketRow): void {
    const nextStatus = ticket.status === 'Open' ? 'In Progress' : ticket.status === 'In Progress' ? 'Resolved' : 'Closed';
    ticket.status = nextStatus as any;
    const updated = [...this.tickets()];
    this.tickets.set(updated);
    this.saveStoredTickets(updated);
    this.toast.show(`Ticket ${ticket.id} status changed to ${nextStatus}.`, 'info');
  }
}
