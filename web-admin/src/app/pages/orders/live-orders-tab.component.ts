import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChangeDetectionStrategy, Component, inject, signal, computed, OnDestroy } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder, OrderDetailResponse, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { buildInvoiceLink } from '../../core/utils/invoice-link.util';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';
import { ApiStateComponent } from '../../core/components/api-state.component';

@Component({
  selector: 'app-live-orders-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, OrderDetailModalComponent, ApiStateComponent],
  template: `
    <div class="tab-panel">
      <!-- Loading and Error States -->
      <div class="stale-banner" *ngIf="error() && activeOrders().length" role="status" aria-live="polite">
        <span>Couldn't update live orders. {{ error() }}</span>
        <button type="button" class="btn-retry" (click)="load()">Retry</button>
      </div>

      <div class="panel loading" *ngIf="loading() && !activeOrders().length" role="status" aria-busy="true">
        <span class="sr-only">Loading live orders...</span>
        <div class="skeleton-stack">
          <div class="skeleton-row" *ngFor="let i of [1,2,3]"></div>
        </div>
      </div>

      <app-api-state
        *ngIf="error() && !activeOrders().length"
        [message]="error()"
        (retry)="load()"
      ></app-api-state>

      <!-- In-Flight Floor Telemetry Strip for Restaurant Owner -->
      <section class="floor-telemetry-grid" *ngIf="activeOrders().length" aria-label="Floor load overview">
        <article class="telemetry-card telemetry-card--hero">
          <div class="telemetry-head">
            <span class="telemetry-label">Running Floor Value</span>
          </div>
          <strong class="telemetry-val">{{ fmt(totalFloorValue()) }}</strong>
          <span class="telemetry-sub">{{ activeOrders().length }} active bills awaiting final settlement</span>
        </article>

        <article class="telemetry-card">
          <div class="telemetry-head">
            <span class="telemetry-label">Occupied Tables &amp; Orders</span>
            <span class="telemetry-mini-pill">{{ dineInCount() }} Dine-in</span>
          </div>
          <strong class="telemetry-val">{{ activeTableCount() }} Tables Occupied</strong>
          <span class="telemetry-sub">{{ takeawayCount() }} Takeaway · {{ dineInCount() }} Dine-in</span>
        </article>

        <article class="telemetry-card" [class.telemetry-card--alert]="urgentCount() > 0">
          <div class="telemetry-head">
            <span class="telemetry-label">Oldest Waiting Ticket</span>
            <span class="telemetry-mini-pill" [class.telemetry-mini-pill--red]="urgentCount() > 0">
              {{ urgentCount() > 0 ? (urgentCount() + ' Overdue (>25m)') : 'On Pace' }}
            </span>
          </div>
          <strong class="telemetry-val" [class.text-alert]="urgentCount() > 0">{{ oldestWaitFormatted() }}</strong>
          <span class="telemetry-sub">{{ urgentCount() > 0 ? 'Orders exceeding 25m turnaround' : 'All floor orders progressing smoothly' }}</span>
        </article>
      </section>

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
              <svg *ngIf="isTakeaway(order)" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/>
                <path d="M3 6h18"/>
                <path d="M16 10a4 4 0 0 1-8 0"/>
              </svg>
              <svg *ngIf="isDelivery(order)" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="1" y="3" width="15" height="13"/>
                <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/>
                <circle cx="5.5" cy="18.5" r="2.5"/>
                <circle cx="18.5" cy="18.5" r="2.5"/>
              </svg>
              <svg *ngIf="!isTakeaway(order) && !isDelivery(order)" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 2v6a3 3 0 0 1-3 3 3 3 0 0 1-3-3V2"/>
                <path d="M15 2v19"/>
                <path d="M5 2c1.5 2 1.5 5 0 7v12"/>
              </svg>
            </div>

            <div class="order-id-meta">
              <span class="order-code">#{{ order.orderCode }}</span>
              <span class="channel-tag" [class.channel-tag--takeaway]="isTakeaway(order)">{{ formatOrderType(order) }}</span>
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
              title="View full order details"
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
              Invoice
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
          There are no active unsettled orders right now. New orders taken from counter POS will stream here in real time.
        </p>
      </div>

      <!-- Order Detail Modal -->
      <app-order-detail-modal
        [order]="selectedOrderDetail()"
        (billUpdated)="load()"
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
            <button type="button" class="primary-btn danger-btn" (click)="confirmCancelOrder()" [disabled]="cancelSubmitting()">
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
      font-family: var(--kb-font-sans);
    }

    /* Tab body — the routed shell owns the page header and outer geometry. */
    .tab-panel {
      display: grid;
      gap: 1.5rem;
    }
    .tab-toolbar {
      display: flex;
      justify-content: flex-end;
      gap: 0.65rem;
      flex-wrap: wrap;
    }
    .tab-toolbar__actions {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      flex-wrap: wrap;
    }

    
    
    
    .spinning {
      animation: spin 800ms linear infinite;
    }
    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    /* ── Floor Telemetry Grid ── */
    .floor-telemetry-grid {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr;
      gap: 1rem;
      margin-bottom: 1.5rem;
    }
    @media (max-width: 900px) {
      .floor-telemetry-grid { grid-template-columns: 1fr; }
    }
    .telemetry-card {
      background: #FFFFFF;
      border: 1px solid var(--kb-color-surface-2);
      border-radius: 18px;
      padding: 1.25rem 1.4rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      transition: transform 140ms ease, box-shadow 140ms ease;
    }
    .telemetry-card:hover {
      box-shadow: 0 6px 18px rgba(0, 0, 0, 0.05);
    }
    .telemetry-card--hero {
      background: linear-gradient(135deg, var(--kb-color-espresso) 0%, var(--kb-color-primary-hover) 100%);
      color: #FFFFFF;
      border: none;
      box-shadow: 0 8px 24px rgba(49, 46, 129, 0.25);
    }
    .telemetry-card--hero .telemetry-label {
      color: rgba(255, 255, 255, 0.75);
    }
    .telemetry-card--hero .telemetry-val {
      color: #FFFFFF;
    }
    .telemetry-card--hero .telemetry-sub {
      color: rgba(255, 255, 255, 0.75);
    }
    .telemetry-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .telemetry-label {
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .telemetry-val {
      font-size: 1.65rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
    }
    .telemetry-sub {
      font-size: 0.78rem;
      color: var(--kb-color-muted-foreground);
      font-weight: 500;
    }
    .telemetry-mini-pill {
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.15rem 0.5rem;
      border-radius: 999px;
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }
    .telemetry-mini-pill--red {
      background: var(--kb-color-danger-soft);
      color: var(--kb-color-danger);
    }
    .telemetry-card--alert {
      border-color: var(--kb-color-danger-soft);
      background: var(--kb-color-surface);
    }
    .text-alert {
      color: var(--kb-color-danger) !important;
    }

    /* ── Bento Active Cards Grid (bentogrids.com standard) ── */
    .active-orders-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
      gap: 1.15rem;
    }

    .pos-card-tactile {
      background: #FFFFFF;
      border: 1px solid var(--kb-color-surface-2);
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
      border-color: var(--kb-color-primary-soft);
      box-shadow: 0 8px 24px rgba(93, 69, 253, 0.08);
    }
    .pos-card-tactile:active {
      transform: scale(0.98);
    }
    .pos-card--urgent {
      border-color: var(--kb-color-warning-soft);
      background: var(--kb-color-surface);
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
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      border: 1px solid var(--kb-color-border);
      flex-shrink: 0;
    }
    .channel-avatar--draft {
      background: var(--kb-color-warning-soft);
      color: var(--kb-color-warning);
      border-color: var(--kb-color-warning-soft);
    }

    .order-id-meta {
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .order-code {
      font-size: 1.05rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
    }
    .channel-tag {
      font-size: 0.72rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .status-pill {
      font-size: 0.72rem;
      font-weight: 700;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
    }
    .status-pill--warn {
      background: var(--kb-color-warning-soft);
      color: var(--kb-color-warning);
    }
    .status-pill--pending {
      background: var(--kb-color-success-soft);
      color: var(--kb-color-success);
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
      color: var(--kb-color-foreground);
    }
    .table-identifier {
      font-size: 0.78rem;
      font-weight: 700;
      color: var(--kb-color-primary);
      background: var(--kb-color-surface);
      padding: 0.15rem 0.55rem;
      border-radius: 6px;
    }

    .card-bottom-row {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      border-top: 1px solid var(--kb-color-bg-app);
      padding-top: 0.75rem;
    }
    .amount-col {
      display: flex;
      flex-direction: column;
    }
    .amount-label {
      font-size: 0.7rem;
      color: var(--kb-color-muted-foreground);
      font-weight: 600;
      text-transform: uppercase;
    }
    .amount-val {
      font-size: 1.2rem;
      font-weight: 800;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }

    .elapsed-pill {
      font-size: 0.74rem;
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
    }
    .elapsed-pill--urgent {
      color: var(--kb-color-warning);
      font-weight: 700;
    }

    /* ── All Clear Dignified Empty State (404s.design standard) ── */
    .all-clear-card {
      background: #FFFFFF;
      border: 1.5px dashed var(--kb-color-border-strong);
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
      background: var(--kb-color-bg-app);
      border: 1px solid var(--kb-color-border);
      color: var(--kb-color-success);
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
    }
    .all-clear-card h3 {
      margin: 0;
      font-size: 1.15rem;
      font-weight: 700;
      color: var(--kb-color-foreground);
    }
    .all-clear-sub {
      margin: 0;
      font-size: 0.88rem;
      color: var(--kb-color-muted-foreground);
      max-width: 420px;
      line-height: 1.5;
    }

    /* ── Card Actions Row ── */
    .card-actions-row {
      display: flex;
      gap: 0.5rem;
      border-top: 1px solid var(--kb-color-surface-2);
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
      border: 1px solid var(--kb-color-border);
      background: #FFFFFF;
      color: var(--kb-color-foreground);
      cursor: pointer;
      min-height: 36px;
      transition: all 120ms ease;
    }
    .card-action-btn:hover {
      background: var(--kb-color-bg-app);
      border-color: var(--kb-color-border-strong);
      color: var(--kb-color-foreground);
    }
    .card-action-btn:active {
      transform: scale(0.97);
    }
    .card-action-btn--primary {
      background: var(--kb-color-primary-soft);
      border-color: var(--kb-color-primary-soft);
      color: var(--kb-color-primary);
    }
    .card-action-btn--primary:hover {
      background: var(--kb-color-primary-soft);
      border-color: var(--kb-color-primary-soft);
    }
    .card-action-btn--danger {
      background: var(--kb-color-danger-soft);
      border-color: var(--kb-color-danger-soft);
      color: var(--kb-color-danger);
    }
    .card-action-btn--danger:hover {
      background: var(--kb-color-danger-soft);
      border-color: var(--kb-color-danger-soft);
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
      color: var(--kb-color-muted-foreground);
      cursor: pointer;
      padding: 0.25rem;
    }
    .chip {
      display: inline-block;
      padding: 0.2rem 0.55rem;
      font-size: 0.72rem;
      font-weight: 700;
      border-radius: 999px;
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
    }
    .chip.danger {
      background: var(--kb-color-danger-soft);
      color: var(--kb-color-danger);
    }
    .field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .field label {
      font-size: 0.82rem;
      font-weight: 600;
      color: var(--kb-color-foreground);
    }
    .field-select, .field-control {
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--kb-color-border-strong);
      border-radius: 8px;
      font-size: 0.88rem;
      min-height: 40px;
      background: #FFFFFF;
      color: var(--kb-color-foreground);
      outline: none;
    }
    .field-select:focus, .field-control:focus {
      border-color: var(--kb-color-primary);
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
      border: 1px solid var(--kb-color-border-strong);
      background: #FFFFFF;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      color: var(--kb-color-foreground);
      min-height: 40px;
    }
    .ghost-btn:hover:not(:disabled) {
      background: var(--kb-color-bg-app);
    }
    
    
    .danger-btn {
      background: var(--kb-color-danger) !important;
      color: #FFFFFF !important;
      border: none !important;
    }
    .danger-btn:hover:not(:disabled) {
      background: var(--kb-color-danger) !important;
    }
  `]
})
export class LiveOrdersTabComponent implements OnDestroy {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
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

  readonly totalFloorValue = computed(() => {
    return this.activeOrders().reduce((sum, o) => sum + (o.totalAmount || 0), 0);
  });

  readonly dineInCount = computed(() => {
    return this.activeOrders().filter(o => this.isDineIn(o)).length;
  });

  readonly takeawayCount = computed(() => {
    return this.activeOrders().filter(o => this.isTakeaway(o)).length;
  });

  readonly deliveryCount = computed(() => {
    return this.activeOrders().filter(o => this.isDelivery(o)).length;
  });

  readonly activeTableCount = computed(() => {
    const tableOrders = this.activeOrders().filter(o => o.tableNumber != null && o.tableNumber !== '');
    const uniqueTables = new Set(tableOrders.map(o => String(o.tableNumber)));
    return uniqueTables.size || this.dineInCount();
  });

  readonly urgentCount = computed(() => {
    return this.activeOrders().filter(o => this.isUrgent(o)).length;
  });

  readonly oldestWaitFormatted = computed(() => {
    const orders = this.activeOrders();
    if (!orders.length) return '—';
    const now = Date.now();
    let maxElapsed = 0;
    for (const o of orders) {
      if (o.createdAt) {
        const elapsed = now - o.createdAt;
        if (elapsed > maxElapsed) maxElapsed = elapsed;
      }
    }
    const mins = Math.floor(maxElapsed / 60000);
    if (mins < 1) return 'Just started';
    if (mins < 60) return `${mins}m max wait`;
    return `${Math.floor(mins / 60)}h ${mins % 60}m`;
  });

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

  isDineIn(order: BusinessOrder): boolean {
    const t = (order.orderType || order.sourceType || '').toLowerCase();
    return t.includes('dine') || (!t.includes('takeaway') && !t.includes('delivery') && !t.includes('parcel'));
  }

  isTakeaway(order: BusinessOrder): boolean {
    const t = (order.orderType || order.sourceType || '').toLowerCase();
    return t.includes('takeaway') || t.includes('parcel');
  }

  isDelivery(order: BusinessOrder): boolean {
    const t = (order.orderType || order.sourceType || '').toLowerCase();
    return t.includes('delivery');
  }

  formatOrderType(order: BusinessOrder): string {
    if (this.isTakeaway(order)) return 'Takeaway';
    if (this.isDelivery(order)) return 'Delivery';
    return 'Dine-In';
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
    const session = this.auth.session();
    const link = buildInvoiceLink(session?.restaurantId, order.orderId, order.publicToken);
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(link).then(
        () => this.toast.show(`Invoice link for Order #${order.orderCode} copied!`, 'success'),
        () => this.toast.show(`Order #${order.orderCode} link ready`, 'info')
      );
    } else {
      this.toast.show(`Order #${order.orderCode} link ready`, 'info');
    }
  }
}
