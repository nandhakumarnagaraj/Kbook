import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { tap, switchMap } from 'rxjs/operators';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { MarketplaceOrder, MarketplaceOrderCounts } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

export type MarketplaceFilterStatus = 'ALL' | 'pending' | 'accepted' | 'ready' | 'completed' | 'rejected';

const ALL_STATUSES: MarketplaceFilterStatus[] = ['ALL', 'pending', 'accepted', 'ready', 'completed', 'rejected'];

@Component({
  selector: 'app-marketplace-orders-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ApiStateComponent, EmptyStateComponent],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Marketplace Orders</h2>
        <p class="muted">Incoming Zomato and Swiggy orders. Accept, reject, mark ready, or complete each order.</p>
        <div class="hero-meta">
          <span class="chip">Zomato</span>
          <span class="chip">Swiggy</span>
        </div>
      </section>

      <div class="panel stats-grid">
        <article class="stat-card">
          <h3>Pending</h3>
          <strong>{{ counts().pending }}</strong>
        </article>
        <article class="stat-card">
          <h3>Accepted</h3>
          <strong>{{ counts().accepted }}</strong>
        </article>
        <article class="stat-card">
          <h3>Ready</h3>
          <strong>{{ counts().ready }}</strong>
        </article>
        <article class="stat-card">
          <h3>Completed</h3>
          <strong>{{ counts().completed }}</strong>
        </article>
        <article class="stat-card">
          <h3>Rejected</h3>
          <strong>{{ counts().rejected }}</strong>
        </article>
      </div>

      <app-api-state
        *ngIf="viewState() === 'error'"
        [loading]="false"
        [error]="errorMessage()"
        (retry)="loadOrders()"
      ></app-api-state>

      <div class="panel table-wrap" *ngIf="viewState() === 'loaded' && filteredOrders().length; else emptyState">
        <table class="data-table">
          <thead>
            <tr>
              <th>Provider</th>
              <th>Order #</th>
              <th>Customer</th>
              <th>Status</th>
              <th>Total</th>
              <th>Created</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let order of pagedOrders()">
              <td><span class="chip">{{ order.platform === 'SWIGGY' ? 'Swiggy' : 'Zomato' }}</span></td>
              <td>{{ order.platformOrderId }}</td>
              <td>
                <div class="stacked-meta">
                  <strong>{{ order.customerName || '-' }}</strong>
                  <span class="muted">{{ order.customerPhone || 'No contact' }}</span>
                </div>
              </td>
              <td>
                <span class="chip"
                  [class.warn]="order.orderStatus === 'pending' || order.orderStatus === 'accepted'"
                  [class.success]="order.orderStatus === 'ready'"
                  [class.danger]="order.orderStatus === 'rejected'">
                  {{ order.orderStatus }}
                </span>
              </td>
              <td>{{ formatCurrencyValue(order.totalAmount) }}</td>
              <td>{{ formatDateValue(order.createdAt) }}</td>
              <td>
                <div class="action-stack">
                  <button
                    class="ghost-btn"
                    *ngIf="order.orderStatus === 'pending'"
                    [disabled]="performingAction()[order.id]"
                    (click)="acceptOrder(order)"
                  >Accept</button>
                  <button
                    class="ghost-btn danger-btn"
                    *ngIf="order.orderStatus === 'pending'"
                    [disabled]="performingAction()[order.id]"
                    (click)="rejectOrder(order)"
                  >Reject</button>
                  <button
                    class="ghost-btn"
                    *ngIf="order.orderStatus === 'accepted' || order.orderStatus === 'preparing'"
                    [disabled]="performingAction()[order.id]"
                    (click)="markReady(order)"
                  >Mark Ready</button>
                  <button
                    class="ghost-btn"
                    *ngIf="order.orderStatus === 'ready'"
                    [disabled]="performingAction()[order.id]"
                    (click)="completeOrder(order)"
                  >Complete</button>
                  <span class="muted" *ngIf="!canAct(order)">—</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="pagination-bar">
          <span class="muted">{{ filteredOrders().length }} order(s)</span>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="page() <= 1" (click)="prevPage()">Previous</button>
            <span class="muted">Page {{ page() }} of {{ totalPages() }}</span>
            <button class="ghost-btn" [disabled]="page() >= totalPages()" (click)="nextPage()">Next</button>
          </div>
        </div>
      </div>

      <ng-template #emptyState>
        <div class="panel loading" *ngIf="viewState() === 'loading'" role="status" aria-live="polite">
          Loading marketplace orders...
        </div>
        <app-empty-state *ngIf="viewState() === 'loaded' && !filteredOrders().length">
          <p>No marketplace orders match the current filters.</p>
        </app-empty-state>
      </ng-template>
    </div>
  `,
  styles: []
})
export class MarketplaceOrdersPageComponent implements OnInit {
  private readonly api = inject(BusinessApiService);

  readonly viewState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly errorMessage = signal('');
  readonly orders = signal<MarketplaceOrder[]>([]);
  readonly counts = signal<MarketplaceOrderCounts>({ pending: 0, accepted: 0, ready: 0, completed: 0, rejected: 0 });
  readonly filter = signal<MarketplaceFilterStatus>('ALL');
  readonly pageSize = signal(10);
  readonly page = signal(1);
  readonly performingAction = signal<Record<number, boolean>>({});

  ngOnInit(): void { this.loadOrders(); }

  loadOrders(): void {
    this.viewState.set('loading');
    this.errorMessage.set('');
    this.api.getMarketplaceOrderCounts().pipe(
      tap((rawCounts) => this.counts.set(rawCounts as unknown as MarketplaceOrderCounts)),
      switchMap(() => this.api.getMarketplaceOrders())
    ).subscribe({
      next: (list) => {
        this.orders.set(list);
        this.viewState.set('loaded');
      },
      error: (err) => {
        this.errorMessage.set(err.message ?? 'Failed to load marketplace orders');
        this.viewState.set('error');
      }
    });
  }

  filteredOrders(): MarketplaceOrder[] {
    const f = this.filter();
    if (f === 'ALL') return this.orders();
    return this.orders().filter(o => o.orderStatus === f);
  }

  pagedOrders(): MarketplaceOrder[] {
    const start = (this.page() - 1) * this.pageSize();
    return this.filteredOrders().slice(start, start + this.pageSize());
  }

  totalPages(): number {
    const total = this.filteredOrders().length;
    return total === 0 ? 1 : Math.ceil(total / this.pageSize());
  }

  prevPage(): void { if (this.page() > 1) this.page.update(p => p - 1); }
  nextPage(): void { if (this.page() < this.totalPages()) this.page.update(p => p + 1); }

  canAct(order: MarketplaceOrder): boolean {
    return order.orderStatus === 'pending' || order.orderStatus === 'accepted' || order.orderStatus === 'preparing' || order.orderStatus === 'ready';
  }

  private act(order: MarketplaceOrder, call: () => void): void {
    this.performingAction.update(m => ({ ...m, [order.id]: true }));
    call();
  }

  acceptOrder(order: MarketplaceOrder): void {
    this.act(order, () => this.api.acceptMarketplaceOrder(order.id).subscribe({
      next: (updated) => this.replaceOrder(updated),
      error: (err) => this.errorMessage.set(err.message ?? 'Action failed')
    }));
  }

  rejectOrder(order: MarketplaceOrder): void {
    this.act(order, () => this.api.rejectMarketplaceOrder(order.id, null).subscribe({
      next: (updated) => this.replaceOrder(updated),
      error: (err) => this.errorMessage.set(err.message ?? 'Action failed')
    }));
  }

  markReady(order: MarketplaceOrder): void {
    this.act(order, () => this.api.markReadyMarketplaceOrder(order.id).subscribe({
      next: (updated) => this.replaceOrder(updated),
      error: (err) => this.errorMessage.set(err.message ?? 'Action failed')
    }));
  }

  completeOrder(order: MarketplaceOrder): void {
    this.act(order, () => this.api.completeMarketplaceOrder(order.id).subscribe({
      next: (updated) => this.replaceOrder(updated),
      error: (err) => this.errorMessage.set(err.message ?? 'Action failed')
    }));
  }

  private replaceOrder(updated: MarketplaceOrder): void {
    this.orders.update(list => list.map(o => o.id === updated.id ? { ...o, ...updated } : o));
    this.performingAction.update(m => { const clone = { ...m }; delete clone[updated.id]; return clone; });
    this.loadCountsOnly();
  }

  private loadCountsOnly(): void {
    this.api.getMarketplaceOrderCounts().subscribe({
      next: (raw) => this.counts.set(raw as unknown as MarketplaceOrderCounts),
      error: () => {}
    });
  }

  formatCurrencyValue(value: number | null | undefined): string { return formatCurrency(value ?? 0); }
  formatDateValue(value: number | null | undefined): string { return formatDate(value); }
}
