import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChangeDetectionStrategy, Component, inject, signal, OnDestroy } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder, OrderDetailResponse, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';

@Component({
  selector: 'app-active-orders-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, OrderDetailModalComponent],
  template: `
    <div class="page-shell">
      <!-- Operational Header (navbar.gallery standard) -->
      <header class="operational-header">
        <div class="header-left">
          <div class="header-title-row">
            <h2>Active Orders &amp; Live Tables</h2>
            <span class="live-pulse-badge">● Live Kitchen Stream</span>
          </div>
          <p class="header-sub">Unsettled tables, cooking KOTs, and draft bills. Auto-refreshes every 30 seconds.</p>
        </div>
        <div class="header-right">
          <button
            type="button"
            class="refresh-btn-tactile"
            [disabled]="loading()"
            (click)="load()">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" [class.spinning]="loading()">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            {{ loading() ? 'Updating...' : 'Refresh Feed' }}
          </button>
        </div>
      </header>

      <!-- Loading and Error States -->
      <div class="panel loading" *ngIf="loading() && !activeOrders().length">
        <div class="skeleton-stack">
          <div class="skeleton-row" *ngFor="let i of [1,2,3]"></div>
        </div>
      </div>
      <div class="alert error" *ngIf="error()">{{ error() }}</div>

      <!-- Bento Cards Grid (bentogrids.com standard) -->
      <div class="active-orders-grid" *ngIf="activeOrders().length">
        <article
          class="pos-card-tactile"
          *ngFor="let order of activeOrders(); trackBy: trackByOrderId"
          [class.pos-card--urgent]="isUrgent(order)"
          tabindex="0"
          role="button"
          [attr.aria-label]="'View order #' + order.orderCode"
          (click)="openOrderDetail(order.orderId)"
          (keydown.enter)="openOrderDetail(order.orderId)">
          
          <div class="card-top-row">
            <div class="channel-avatar" [ngClass]="'channel-avatar--' + (order.orderStatus || 'draft').toLowerCase()">
              <svg *ngIf="order.sourceType === 'TAKEAWAY'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/>
                <path d="M3 6h18"/>
                <path d="M16 10a4 4 0 0 1-8 0"/>
              </svg>
              <svg *ngIf="order.sourceType === 'DELIVERY'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="1" y="3" width="15" height="13"/>
                <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/>
                <circle cx="5.5" cy="18.5" r="2.5"/>
                <circle cx="18.5" cy="18.5" r="2.5"/>
              </svg>
              <svg *ngIf="order.sourceType !== 'TAKEAWAY' && order.sourceType !== 'DELIVERY'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 2v6a3 3 0 0 1-3 3 3 3 0 0 1-3-3V2"/>
                <path d="M15 2v19"/>
                <path d="M5 2c1.5 2 1.5 5 0 7v12"/>
              </svg>
            </div>

            <div class="order-id-meta">
              <span class="order-code">#{{ order.orderCode }}</span>
              <span class="channel-tag">{{ order.sourceType || 'DINE_IN' }}</span>
            </div>

            <span class="status-pill" [class.status-pill--warn]="order.orderStatus === 'draft'" [class.status-pill--pending]="order.paymentStatus === 'pending'">
              {{ order.orderStatus === 'draft' ? 'Open Bill' : order.paymentStatus }}
            </span>
          </div>

          <div class="card-customer-row">
            <span class="customer-name">{{ order.customerName || 'Walk-in Guest' }}</span>
            <span class="table-identifier" *ngIf="order.tableNumber">Table {{ order.tableNumber }}</span>
          </div>

          <div class="card-bottom-row">
            <div class="amount-col">
              <span class="amount-label">Payable</span>
              <strong class="amount-val">{{ fmt(order.totalAmount) }}</strong>
            </div>
            <div class="elapsed-col">
              <span class="elapsed-pill" [class.elapsed-pill--urgent]="isUrgent(order)">
                ⏱ {{ getElapsed(order) }}
              </span>
            </div>
          </div>

          <div class="card-actions-row">
            <button
              type="button"
              class="card-action-btn card-action-btn--primary"
              (click)="openOrderDetail(order.orderId); $event.stopPropagation()"
              title="View full order details and KOTs"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
              </svg>
              Details
            </button>
            <button
              type="button"
              class="card-action-btn"
              (click)="copyInvoiceLink(order, $event)"
              title="Copy invoice/order reference"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
              </svg>
              Copy
            </button>
            <button
              type="button"
              class="card-action-btn card-action-btn--danger"
              (click)="openCancelModal(order, $event)"
              title="Void or cancel this order"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              Void
            </button>
          </div>
        </article>
      </div>

      <!-- Dignified Zero State (404s.design / Anti-Slop standard) -->
      <div class="all-clear-card" *ngIf="!loading() && !error() && !activeOrders().length">
        <div class="all-clear-halo" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
            <rect x="9" y="3" width="6" height="4" rx="1"/>
            <path d="m9 14 2 2 4-4"/>
          </svg>
        </div>
        <h3>All Tables &amp; Orders Settled</h3>
        <p class="all-clear-sub">
          There are no pending KOTs or open tables right now. New orders taken from counter POS or floor tablets will stream here in real time.
        </p>
        <button type="button" class="refresh-btn-tactile" (click)="load()">
          Check for New Orders
        </button>
      </div>

      <!-- Order Detail Modal -->
      <app-order-detail-modal
        [order]="selectedOrderDetail()"
        (closed)="closeOrderDetail()">
      </app-order-detail-modal>

      <!-- Order Cancellation Modal (Accessible Design-System Dialog) -->
      <div class="modal-backdrop" *ngIf="orderToCancel()" (click)="closeCancelModal()" role="dialog" aria-modal="true" aria-labelledby="cancel-modal-title">
        <section class="modal-box" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <div>
              <span class="chip danger">Void / Cancel Order</span>
              <h3 id="cancel-modal-title" style="margin: 0.35rem 0 0;">Cancel Order #{{ orderToCancel()?.orderCode }}</h3>
              <p class="muted" style="margin: 0.25rem 0 0; font-size: 0.85rem;">
                Amount: {{ fmt(orderToCancel()?.totalAmount || 0) }} · {{ orderToCancel()?.customerName || 'Walk-in customer' }}
              </p>
            </div>
            <button type="button" class="close-btn" (click)="closeCancelModal()" [disabled]="cancelSubmitting()" aria-label="Close dialog">✕</button>
          </div>

          <div class="modal-body" style="display: grid; gap: 1rem; padding: 1rem 0;">
            <div class="field">
              <label for="cancel-preset">Cancellation Reason</label>
              <select id="cancel-preset" class="field-select" [(ngModel)]="cancelReasonPreset" [disabled]="cancelSubmitting()">
                <option value="Customer requested cancellation">Customer requested cancellation</option>
                <option value="Kitchen unable to fulfill / Item out of stock">Kitchen unable to fulfill / Item out of stock</option>
                <option value="Duplicate order entry">Duplicate order entry</option>
                <option value="Entered by mistake / Billing error">Entered by mistake / Billing error</option>
                <option value="Other reason">Other reason</option>
              </select>
            </div>

            <div class="field">
              <label for="cancel-custom-note">Additional Note (Optional)</label>
              <input
                id="cancel-custom-note"
                type="text"
                class="field-control"
                [(ngModel)]="cancelReasonCustom"
                [disabled]="cancelSubmitting()"
                placeholder="Optional explanation or reference note..."
              />
            </div>
          </div>

          <div class="modal-actions">
            <button type="button" class="ghost-btn" (click)="closeCancelModal()" [disabled]="cancelSubmitting()">Keep Order</button>
            <button type="button" class="primary-btn-tactile danger-btn" (click)="confirmCancelOrder()" [disabled]="cancelSubmitting()">
              {{ cancelSubmitting() ? 'Cancelling...' : 'Confirm Cancellation' }}
            </button>
          </div>
        </section>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      background: var(--pos-bg-body, #F6F8FD);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', sans-serif;
    }

    .page-shell {
      padding: 1.5rem 2rem;
      max-width: 1440px;
      margin: 0 auto;
    }

    /* ── Operational Topbar (navbar.gallery standard) ── */
    .operational-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
      margin-bottom: 1.75rem;
      padding-bottom: 1.25rem;
      border-bottom: 1px solid #EEF0F7;
    }
    .header-title-row {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      margin-bottom: 0.25rem;
    }
    .header-title-row h2 {
      margin: 0;
      font-size: 1.45rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
    }
    .live-pulse-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.2rem 0.65rem;
      border-radius: 999px;
      font-size: 0.74rem;
      font-weight: 700;
      background: #F0FDF4;
      color: #10B981;
      border: 1px solid #BBF7D0;
    }
    .header-sub {
      margin: 0;
      font-size: 0.88rem;
      color: #64748B;
    }

    .refresh-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.55rem 1.15rem;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      font-size: 0.85rem;
      font-weight: 600;
      color: #334155;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .refresh-btn-tactile:hover:not(:disabled) {
      background: #F8FAFC;
      border-color: #CBD5E1;
      color: #0F172A;
    }
    .refresh-btn-tactile:active:not(:disabled) {
      transform: scale(0.97);
    }
    .spinning {
      animation: spin 800ms linear infinite;
    }
    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    /* ── Bento Active Cards Grid (bentogrids.com standard) ── */
    .active-orders-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
      gap: 1.15rem;
    }

    .pos-card-tactile {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.25rem 1.35rem;
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      cursor: pointer;
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .pos-card-tactile:hover {
      transform: translateY(-2px);
      border-color: #DDD6FE;
      box-shadow: 0 8px 24px rgba(93, 69, 253, 0.08);
    }
    .pos-card-tactile:active {
      transform: scale(0.98);
    }
    .pos-card--urgent {
      border-color: #FDE68A;
      background: #FFFDF7;
    }

    .card-top-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .channel-avatar {
      width: 40px;
      height: 40px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #F1F5F9;
      color: #475569;
      border: 1px solid #E2E8F0;
      flex-shrink: 0;
    }
    .channel-avatar--draft {
      background: #FFFBEB;
      color: #D97706;
      border-color: #FDE68A;
    }

    .order-id-meta {
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .order-code {
      font-size: 1.05rem;
      font-weight: 800;
      color: #0F172A;
    }
    .channel-tag {
      font-size: 0.72rem;
      font-weight: 600;
      color: #94A3B8;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .status-pill {
      font-size: 0.72rem;
      font-weight: 700;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      background: #F1F5F9;
      color: #475569;
    }
    .status-pill--warn {
      background: #FFFBEB;
      color: #D97706;
    }
    .status-pill--pending {
      background: #ECFDF5;
      color: #10B981;
    }

    .card-customer-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .customer-name {
      font-size: 0.88rem;
      font-weight: 600;
      color: #334155;
    }
    .table-identifier {
      font-size: 0.78rem;
      font-weight: 700;
      color: #5D45FD;
      background: #F3F0FF;
      padding: 0.15rem 0.55rem;
      border-radius: 6px;
    }

    .card-bottom-row {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      border-top: 1px solid #F8FAFC;
      padding-top: 0.75rem;
    }
    .amount-col {
      display: flex;
      flex-direction: column;
    }
    .amount-label {
      font-size: 0.7rem;
      color: #94A3B8;
      font-weight: 600;
      text-transform: uppercase;
    }
    .amount-val {
      font-size: 1.2rem;
      font-weight: 800;
      color: #0F172A;
      font-variant-numeric: tabular-nums;
    }

    .elapsed-pill {
      font-size: 0.74rem;
      font-weight: 600;
      color: #64748B;
      font-variant-numeric: tabular-nums;
    }
    .elapsed-pill--urgent {
      color: #D97706;
      font-weight: 700;
    }

    /* ── All Clear Dignified Empty State (404s.design standard) ── */
    .all-clear-card {
      background: #FFFFFF;
      border: 1.5px dashed #CBD5E1;
      border-radius: 20px;
      padding: 3.5rem 1.5rem;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      margin-top: 1.5rem;
    }
    .all-clear-halo {
      width: 60px;
      height: 60px;
      border-radius: 18px;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      color: #10B981;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
    }
    .all-clear-card h3 {
      margin: 0;
      font-size: 1.15rem;
      font-weight: 700;
      color: #0F172A;
    }
    .all-clear-sub {
      margin: 0;
      font-size: 0.88rem;
      color: #64748B;
      max-width: 420px;
      line-height: 1.5;
    }

    /* ── Card Actions Row ── */
    .card-actions-row {
      display: flex;
      gap: 0.5rem;
      border-top: 1px solid #F1F5F9;
      padding-top: 0.75rem;
      margin-top: 0.25rem;
    }
    .card-action-btn {
      flex: 1;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.35rem;
      padding: 0.45rem 0.5rem;
      border-radius: 8px;
      font-size: 0.76rem;
      font-weight: 600;
      border: 1px solid #E2E8F0;
      background: #FFFFFF;
      color: #334155;
      cursor: pointer;
      min-height: 36px;
      transition: all 120ms ease;
    }
    .card-action-btn:hover {
      background: #F8FAFC;
      border-color: #CBD5E1;
      color: #0F172A;
    }
    .card-action-btn:active {
      transform: scale(0.97);
    }
    .card-action-btn--primary {
      background: #EEF2FF;
      border-color: #C7D2FE;
      color: #4F46E5;
    }
    .card-action-btn--primary:hover {
      background: #E0E7FF;
      border-color: #A5B4FC;
    }
    .card-action-btn--danger {
      background: #FEF2F2;
      border-color: #FECACA;
      color: #DC2626;
    }
    .card-action-btn--danger:hover {
      background: #FEE2E2;
      border-color: #FCA5A5;
    }

    /* ── Modal Layout ── */
    .modal-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 0.75rem;
    }
    .close-btn {
      background: transparent;
      border: none;
      font-size: 1.15rem;
      color: #64748B;
      cursor: pointer;
      padding: 0.25rem;
    }
    .chip {
      display: inline-block;
      padding: 0.2rem 0.55rem;
      font-size: 0.72rem;
      font-weight: 700;
      border-radius: 999px;
      background: #F1F5F9;
      color: #475569;
    }
    .chip.danger {
      background: #FEE2E2;
      color: #DC2626;
    }
    .field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .field label {
      font-size: 0.82rem;
      font-weight: 600;
      color: #334155;
    }
    .field-select, .field-control {
      padding: 0.5rem 0.75rem;
      border: 1px solid #CBD5E1;
      border-radius: 8px;
      font-size: 0.88rem;
      min-height: 40px;
      background: #FFFFFF;
      color: #0F172A;
      outline: none;
    }
    .field-select:focus, .field-control:focus {
      border-color: #5D45FD;
    }
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 1.25rem;
    }
    .ghost-btn {
      padding: 0.5rem 1rem;
      border-radius: 8px;
      border: 1px solid #CBD5E1;
      background: #FFFFFF;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      color: #334155;
      min-height: 40px;
    }
    .ghost-btn:hover:not(:disabled) {
      background: #F8FAFC;
    }
    .primary-btn-tactile {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0.5rem 1rem;
      background: #5D45FD;
      color: #FFFFFF;
      border: none;
      border-radius: 8px;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      min-height: 40px;
    }
    .primary-btn-tactile:hover:not(:disabled) {
      background: #4D37E6;
    }
    .danger-btn {
      background: #DC2626 !important;
      color: #FFFFFF !important;
      border: none !important;
    }
    .danger-btn:hover:not(:disabled) {
      background: #B91C1C !important;
    }
  `]
})
export class ActiveOrdersPageComponent implements OnDestroy {
  private readonly api = inject(BusinessApiService);
  private readonly toast = inject(ToastService);

  loading = signal(false);
  error = signal('');
  activeOrders = signal<BusinessOrder[]>([]);
  selectedOrderDetail = signal<OrderDetailResponse | null>(null);

  // Cancel order modal state
  orderToCancel = signal<BusinessOrder | null>(null);
  cancelReasonPreset = 'Customer requested cancellation';
  cancelReasonCustom = '';
  cancelSubmitting = signal(false);

  private pollInterval: ReturnType<typeof setInterval> | null = null;

  readonly fmt = formatCurrency;

  constructor() {
    this.load();
    this.pollInterval = setInterval(() => this.load(), 30_000);
  }

  ngOnDestroy(): void {
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
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

  openOrderDetail(orderId: number): void {
    this.api.getOrderDetail(orderId).subscribe({
      next: (res: OrderDetailResponse) => {
        this.selectedOrderDetail.set(res);
      },
      error: () => {
        // Fallback
      }
    });
  }

  closeOrderDetail(): void {
    this.selectedOrderDetail.set(null);
  }

  getElapsed(order: BusinessOrder): string {
    if (!order.createdAt) return '';
    const mins = Math.floor((Date.now() - order.createdAt) / 60000);
    if (mins < 1) return 'Just now';
    if (mins < 60) return `${mins} min ago`;
    const hrs = Math.floor(mins / 60);
    return `${hrs}h ${mins % 60}m ago`;
  }

  isUrgent(order: BusinessOrder): boolean {
    if (!order.createdAt) return false;
    return (Date.now() - order.createdAt) > 25 * 60 * 1000;
  }

  trackByOrderId = (_: number, order: BusinessOrder) => order.orderId;

  openCancelModal(order: BusinessOrder, event?: MouseEvent): void {
    if (event) event.stopPropagation();
    this.orderToCancel.set(order);
    this.cancelReasonPreset = 'Customer requested cancellation';
    this.cancelReasonCustom = '';
    this.cancelSubmitting.set(false);
  }

  closeCancelModal(): void {
    if (this.cancelSubmitting()) return;
    this.orderToCancel.set(null);
  }

  confirmCancelOrder(): void {
    const target = this.orderToCancel();
    if (!target) return;
    const base = this.cancelReasonPreset === 'Other reason' ? '' : this.cancelReasonPreset;
    const custom = this.cancelReasonCustom.trim();
    let reason = 'Cancelled from KDS/Active Orders';
    if (base && custom) {
      reason = `${base}: ${custom}`;
    } else if (base) {
      reason = base;
    } else if (custom) {
      reason = custom;
    }

    this.cancelSubmitting.set(true);
    this.api.voidBill(target.orderId, reason).subscribe({
      next: () => {
        this.cancelSubmitting.set(false);
        this.toast.show('Order cancelled and voided successfully.', 'success');
        this.closeCancelModal();
        this.load();
      },
      error: (err: any) => {
        this.cancelSubmitting.set(false);
        const msg = err?.error?.message || err?.error?.error || 'Failed to cancel order.';
        this.toast.show(msg, 'error');
      }
    });
  }

  copyInvoiceLink(order: BusinessOrder, event?: MouseEvent): void {
    if (event) event.stopPropagation();
    const textToCopy = `Order #${order.orderCode} - ${order.customerName || 'Walk-in'} (Total: ${this.fmt(order.totalAmount)})`;
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(textToCopy).then(
        () => this.toast.show(`Order #${order.orderCode} details copied to clipboard!`, 'success'),
        () => this.toast.show(`Order #${order.orderCode}`, 'info')
      );
    } else {
      this.toast.show(`Order #${order.orderCode}`, 'info');
    }
  }
}
