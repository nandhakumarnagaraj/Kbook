import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency, formatDate } from '../../shared/formatters';

interface Transaction {
  txnId: string;
  restaurantId: number;
  restaurantName: string;
  amount: number;
  status: string;
  paymentMode: string;
  createdAt: number;
}

function getStatusChip(status: string): string {
  switch (status) {
    case 'SUCCESS': case 'CAPTURED': return 'chip success';
    case 'FAILED': case 'FAILURE': return 'chip danger';
    case 'PENDING': case 'PENDING_VPA': case 'PENDING_NETBANKING': case 'PENDING_CARD': case 'PROCESSING': return 'chip warn';
    default: return 'chip';
  }
}

@Component({
  selector: 'app-transaction-monitor-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:1rem;flex-wrap:wrap;">
          <div>
            <h2>Transaction Monitor</h2>
            <p class="muted">View all Easebuzz payment transactions across the platform. For full reports, visit the Easebuzz dashboard.</p>
            <div class="hero-meta">
              <span class="chip">KBOOK_ADMIN</span>
              <span class="chip success">Easebuzz Gateway</span>
            </div>
          </div>
          <a href="https://dashboard.easebuzz.in" target="_blank" rel="noopener noreferrer" class="external-link">🔗 Open Easebuzz Dashboard</a>
        </div>
      </section>

      <section class="panel filter-panel">
        <div class="filter-grid">
          <div class="filter-group">
            <label for="status-filter">Status</label>
            <select id="status-filter" class="field-select" [(ngModel)]="statusFilter" (ngModelChange)="loadTransactions()">
              <option value="">All statuses</option>
              <option value="SUCCESS">Success</option>
              <option value="FAILED">Failed</option>
              <option value="PENDING">Pending</option>
              <option value="PROCESSING">Processing</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="restaurant-filter">Restaurant ID</label>
            <input id="restaurant-filter" class="field-control" type="number" [(ngModel)]="restaurantIdFilter" (ngModelChange)="loadTransactions()" placeholder="Enter restaurant ID" />
          </div>
          <div class="filter-group">
            <label for="page-size">Page Size</label>
            <select id="page-size" class="field-select" [(ngModel)]="pageSize" (ngModelChange)="loadTransactions()">
              <option [ngValue]="10">10</option>
              <option [ngValue]="25">25</option>
              <option [ngValue]="50">50</option>
              <option [ngValue]="100">100</option>
            </select>
          </div>
          <div class="filter-group" style="justify-content:end; display:grid;">
            <label>&nbsp;</label>
            <button class="ghost-btn" (click)="clearFilters()">Clear</button>
          </div>
        </div>
        <div class="filter-summary">
          <p class="muted">{{ transactions().length }} transaction{{ transactions().length !== 1 ? 's' : '' }}</p>
        </div>
      </section>

      <ng-template #loading>
        <div class="panel loading">Loading transactions...</div>
      </ng-template>

      <div class="panel table-wrap" *ngIf="transactions().length; else loading">
        <table class="data-table">
          <thead>
            <tr>
              <th>Txn ID</th>
              <th>Restaurant</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Payment Mode</th>
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let txn of transactions()">
              <td><code>{{ txn.txnId }}</code></td>
              <td>
                <div class="stacked-meta">
                  <strong>{{ txn.restaurantName }}</strong>
                  <span class="muted">#{{ txn.restaurantId }}</span>
                </div>
              </td>
              <td><strong>{{ formatAmount(txn.amount) }}</strong></td>
              <td><span [class]="getChipClass(txn.status)">{{ txn.status }}</span></td>
              <td>{{ txn.paymentMode || '-' }}</td>
              <td>{{ formatDateVal(txn.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div class="pagination-bar">
          <p class="muted">Page {{ currentPage() + 1 }}</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="currentPage() === 0" (click)="prevPage()">Previous</button>
            <button class="ghost-btn" [disabled]="transactions().length < pageSize()" (click)="nextPage()">Next</button>
          </div>
        </div>
      </div>

      <div class="panel loading" *ngIf="!transactions().length && loaded()">
        <span class="empty-icon">📭</span>
        <p>No transactions found.</p>
      </div>
    </div>
  `,
  styles: [`
    .external-link { display:inline-flex; align-items:center; gap:.4rem; padding:.5rem 1rem; border-radius:999px; background:rgba(29,123,95,.1); color:var(--accent); text-decoration:none; font-weight:700; font-size:.85rem; border:1px solid rgba(29,123,95,.2); transition:all .15s; white-space:nowrap; }
    .external-link:hover { background:rgba(29,123,95,.18); }
    code {
      font-size: 0.82rem;
      background: rgba(0,0,0,0.04);
      padding: 0.15rem 0.35rem;
      border-radius: 4px;
    }
    .empty-icon {
      font-size: 2.5rem;
      display: block;
      margin-bottom: 0.5rem;
    }
  `]
})
export class TransactionMonitorPageComponent implements OnInit {
  private readonly api = inject(AdminApiService);

  readonly transactions = signal<Transaction[]>([]);
  readonly loaded = signal(false);
  readonly currentPage = signal(0);
  readonly pageSize = signal(50);

  statusFilter = '';
  restaurantIdFilter: number | null = null;

  ngOnInit(): void {
    this.loadTransactions();
  }

  loadTransactions(): void {
    this.loaded.set(false);
    this.api.getTransactions(this.currentPage(), this.pageSize(), this.statusFilter || undefined, this.restaurantIdFilter ?? undefined).subscribe({
      next: (data) => {
        this.transactions.set(data);
        this.loaded.set(true);
      },
      error: () => {
        this.transactions.set([]);
        this.loaded.set(true);
      }
    });
  }

  clearFilters(): void {
    this.statusFilter = '';
    this.restaurantIdFilter = null;
    this.currentPage.set(0);
    this.loadTransactions();
  }

  nextPage(): void {
    this.currentPage.update(p => p + 1);
    this.loadTransactions();
  }

  prevPage(): void {
    this.currentPage.update(p => Math.max(0, p - 1));
    this.loadTransactions();
  }

  getChipClass(status: string): string {
    return getStatusChip(status);
  }

  formatAmount(value: number): string {
    return formatCurrency(value);
  }

  formatDateVal(value: number): string {
    return formatDate(value);
  }
}
