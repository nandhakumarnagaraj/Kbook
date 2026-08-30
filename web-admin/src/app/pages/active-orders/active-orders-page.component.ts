import { CommonModule } from '@angular/common';
import { Component, inject, signal, OnDestroy } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessOrder, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-active-orders-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Active Orders</h2>
        <p class="muted">Live view of open tables and unsettled orders. Auto-refreshes every 30 seconds.</p>
        <div class="hero-meta">
          <span class="chip warn">{{ activeOrders().length }} open</span>
          <span class="chip">Live</span>
        </div>
      </section>

      <div class="toolbar">
        <div>
          <h3>Open Tables & Drafts</h3>
          <p class="muted">Orders with status "draft" or payment pending.</p>
        </div>
        <button class="ghost-btn" [disabled]="loading()" (click)="load()">
          {{ loading() ? 'Refreshing...' : 'Refresh Now' }}
        </button>
      </div>

      <div class="panel loading" *ngIf="loading() && !activeOrders().length">Loading active orders...</div>
      <div class="panel loading" *ngIf="error()">{{ error() }}</div>

      <div class="orders-grid" *ngIf="activeOrders().length">
        <article class="order-card" *ngFor="let order of activeOrders()" [class.order-card--old]="isOld(order)">
          <div class="order-card__header">
            <strong class="order-card__code">{{ order.orderCode }}</strong>
            <span class="chip" [class.warn]="order.orderStatus === 'draft'" [class.success]="order.paymentStatus === 'pending'">
              {{ order.orderStatus === 'draft' ? 'Open' : order.paymentStatus }}
            </span>
          </div>
          <div class="order-card__customer">
            {{ order.customerName || 'Walk-in' }}
          </div>
          <div class="order-card__meta">
            <span>{{ fmt(order.totalAmount) }}</span>
            <span class="muted">{{ order.paymentMethod || '-' }}</span>
          </div>
          <div class="order-card__time">
            <span class="muted">{{ getElapsed(order) }}</span>
          </div>
        </article>
      </div>

      <div class="panel" style="text-align:center;padding:2rem;" *ngIf="!loading() && !error() && !activeOrders().length">
        <p style="font-size:1.5rem;">✅</p>
        <h3>All clear!</h3>
        <p class="muted">No open orders right now. All tables are settled.</p>
      </div>
    </div>
  `,
  styles: [`
    .orders-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr));
      gap: var(--kb-space-3);
    }
    .order-card {
      background: var(--kb-color-surface); border: 1px solid var(--kb-color-border); border-radius: var(--kb-radius-md);
      padding: var(--kb-space-3); display: flex; flex-direction: column; gap: var(--kb-space-2);
      transition: border-color 0.2s;
    }
    .order-card--old { border-color: var(--kb-color-error); border-width: 2px; }
    .order-card__header { display: flex; justify-content: space-between; align-items: center; }
    .order-card__code { font-size: 1rem; color: var(--kb-color-foreground); }
    .order-card__customer { font-size: 0.88rem; color: var(--kb-color-foreground); font-weight: 500; }
    .order-card__meta { display: flex; justify-content: space-between; font-size: 0.85rem; }
    .order-card__meta span:first-child { font-weight: 700; color: var(--kb-color-foreground); }
    .order-card__time { font-size: 0.8rem; color: var(--kb-color-muted); }
    .chip.warn { background: rgba(59, 130, 246, 0.12); color: var(--kb-color-primary); }
    .chip.success { background: rgba(16, 185, 129, 0.12); color: var(--kb-color-success); }
  `]
})
export class ActiveOrdersPageComponent implements OnDestroy {
  private readonly api = inject(BusinessApiService);

  loading = signal(false);
  error = signal('');
  activeOrders = signal<BusinessOrder[]>([]);
  private pollInterval: ReturnType<typeof setInterval> | null = null;

  readonly fmt = formatCurrency;

  constructor() {
    this.load();
    // Auto-refresh every 30 seconds
    this.pollInterval = setInterval(() => this.load(), 30_000);
  }

  ngOnDestroy(): void {
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    // Fetch draft/pending orders (today, status=draft)
    this.api.getOrdersPaginated(0, 100, 'draft').subscribe({
      next: (res: PaginatedOrdersResponse) => {
        this.activeOrders.set(res.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to load active orders.');
      }
    });
  }

  getElapsed(order: BusinessOrder): string {
    if (!order.createdAt) return '';
    const mins = Math.floor((Date.now() - order.createdAt) / 60000);
    if (mins < 1) return 'Just now';
    if (mins < 60) return `${mins} min ago`;
    const hrs = Math.floor(mins / 60);
    return `${hrs}h ${mins % 60}m ago`;
  }

  isOld(order: BusinessOrder): boolean {
    if (!order.createdAt) return false;
    return (Date.now() - order.createdAt) > 90 * 60 * 1000; // > 90 min = old
  }
}
