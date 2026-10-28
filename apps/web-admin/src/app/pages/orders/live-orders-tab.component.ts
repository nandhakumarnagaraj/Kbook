import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChangeDetectionStrategy, Component, inject, signal, computed, OnDestroy } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder, OrderDetailResponse, PaginatedOrdersResponse } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';
import { KpiItem } from '../../shared/kpi-row.component';
import { buildInvoiceLink } from '../../core/utils/invoice-link.util';
import { OrderDetailModalComponent } from '../../shared/order-detail-modal.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { FocusTrapDirective } from '../../core/components/focus-trap.directive';

@Component({
  selector: 'app-live-orders-tab',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, OrderDetailModalComponent, ApiStateComponent, FocusTrapDirective],
  template: `
    <div class="tab-panel">
      <!-- Loading and Error States -->
      <div class="stale-banner" *ngIf="error() && activeOrders().length" role="status" aria-live="polite">
        <span>Couldn't update live orders. {{ error() }}</span>
        <button type="button" class="btn-retry" (click)="load(true)">Retry</button>
      </div>

      <div class="panel loading" *ngIf="showReloadSkeleton()" role="status" aria-busy="true">
        <span class="visually-hidden">Loading live orders...</span>
        <!-- Card-shaped, not row-shaped: the live board loads the same grid it
             ends up showing, so nothing reflows when the orders land. The
             shapes carry the fill themselves (a bare skeleton-row has none,
             which is why this panel used to load as a blank box), and none of
             them prints an order code, a guest name or an amount. -->
        <div class="skeleton-card-grid">
          <div class="bento-tile skeleton-card" *ngFor="let i of [1,2,3,4]" aria-hidden="true">
            <div class="skeleton-card__row">
              <span class="skeleton skeleton-avatar"></span>
              <span class="skeleton skeleton-line skeleton-line--name"></span>
              <span class="skeleton skeleton-line skeleton-line--chip"></span>
            </div>
            <div class="skeleton-card__row">
              <span class="skeleton skeleton-line skeleton-line--grow"></span>
            </div>
            <div class="skeleton-card__row">
              <span class="skeleton skeleton-line skeleton-line--name"></span>
              <span class="skeleton skeleton-line skeleton-line--action"></span>
            </div>
          </div>
        </div>
      </div>

      <app-api-state
        *ngIf="error() && !activeOrders().length"
        [message]="error()"
        (retry)="load(true)"
      ></app-api-state>

      <!-- KPI Cards Row + Filter Chips -->
      <div class="kpi-row-and-filters"
        *ngIf="activeOrders().length && !showReloadSkeleton()"
        aria-label="KPI and filter controls"
      >
        <!-- KPI Cards: four compact cards in a single row at desktop -->
        <div class="kpi-cards"
          *ngIf="!showReloadSkeleton()"
          aria-label="Key order metrics"
        >
          <div class="kpi-card"
            *ngFor="let item of liveKpiItems()"
            [class.kpi-card--active]="item.accent === 'positive'"
            [class.kpi-card--flow]="item.accent === 'flow'"
            [class.kpi-card--activity]="item.accent === 'activity'"
            [attr.aria-label]="item.label + ': ' + item.value"
          >
            <div class="kpi-card__label" [class.kpi-card__label--secondary]="true">{{ item.label }}</div>
            <div class="kpi-card__value" [class.kpi-card__value--tone]="item.tone"
              [class.kpi-card__value--accent]="item.accent"
            >{{ item.value }}</div>
            <div class="kpi-card__badge" *ngIf="item.badge"
              [class.kpi-card__badge--tone]="item.badge.tone"
            >{{ item.badge.text }}</div>
            <div class="kpi-card__accent"
              [class.kpi-card__accent--flow]="item.accent === 'flow'"
              [class.kpi-card__accent--positive]="item.accent === 'positive'"
              [class.kpi-card__accent--activity]="item.accent === 'activity'"
            ></div>
          </div>
        </div>

        <!-- Order-type filter chips directly below the KPI cards -->
        <div class="filter-chips"
          *ngIf="!showReloadSkeleton()"
          aria-label="Order type filters"
        >
          <button
            type="button"
            class="filter-chip"
            *ngFor="let channel of ['All', 'Dine-in', 'Takeaway', 'Delivery']"
            [class.filter-chip--active]="channel !== 'All' && isTakeawayFilter || channel === 'All'"
            (click)="filterByChannel(channel)"
          >{{ channel }}</button>
        </div>
      </div>

      <!-- Floor summary: the five numbers a busy counter checks before reading
           individual cards. It reuses the computeds the board already carries
           and adds no request of its own. -->
      <section
        class="live-summary"
        *ngIf="activeOrders().length && !showReloadSkeleton()"
        aria-label="Live floor summary">
        <div class="live-summary__item">
          <span class="live-summary__label">Active orders</span>
          <strong class="live-summary__value">{{ activeOrders().length }}</strong>
        </div>
        <div class="live-summary__item">
          <span class="live-summary__label">Tables in use</span>
          <strong class="live-summary__value">{{ activeTableCount() }}</strong>
        </div>
        <div class="live-summary__item">
          <span class="live-summary__label">Oldest wait</span>
          <strong class="live-summary__value">{{ oldestWaitFormatted() }}</strong>
        </div>
        <div class="live-summary__item live-summary__item--money">
          <span class="live-summary__label">On the floor</span>
          <strong class="live-summary__value">{{ fmt(totalFloorValue()) }}</strong>
        </div>
        <div class="live-summary__item live-summary__item--attention" *ngIf="urgentCount() > 0">
          <span class="live-summary__label">Waiting 25m+</span>
          <strong class="live-summary__value">{{ urgentCount() }}</strong>
        </div>
      </section>

      <!-- Bento Cards Grid (bentogrids.com standard) -->
      <div class="active-orders-grid" *ngIf="activeOrders().length && !showReloadSkeleton()">
        <article
          class="bento-tile pos-card-tactile"
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

            <span class="status-pill" [class.status-pill--warn]="isOpenBill(order)" [class.status-pill--pending]="isPendingPayment(order)">
              {{ isOpenBill(order) ? 'Preparing' : order.paymentStatus }}
            </span>
          </div>

          <div class="card-customer-row">
            <span class="customer-name">{{ order.customerName || 'Walk-in guest' }}</span>
            <span class="table-identifier" *ngIf="order.tableNumber">Table {{ order.tableNumber }}</span>
          </div>

          <!-- Payment is stated in words as well as by the dot: an operator must
               never have to decode a colour to know whether the bill is settled. -->
          <div class="card-payment-row">
            <span class="payment-status" [class.payment-status--pending]="isPendingPayment(order)">
              <span class="payment-dot" aria-hidden="true"></span>
              {{ order.paymentMethod || 'Cash' }} · {{ order.paymentStatus || 'Pending' }}
            </span>
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
              title="Cancel this order"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              Cancel
            </button>
          </div>
        </article>
      </div>

      <!-- Dignified Zero State (404s.design / Anti-Slop standard) -->
      <div class="empty-orders-view" *ngIf="!loading() && !error() && !activeOrders().length">
        <div class="empty-icon-circle" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
            <rect x="9" y="3" width="6" height="4" rx="1"/>
            <path d="m9 14 2 2 4-4"/>
          </svg>
        </div>
        <h4>No active orders</h4>
        <p>
          New POS orders will appear here as they’re placed.
        </p>
      </div>

      <!-- Order Detail Modal -->
      <app-order-detail-modal
        [order]="selectedOrderDetail()"
        (billUpdated)="load()"
        (closed)="closeOrderDetail()">
      </app-order-detail-modal>

      <!-- Order Cancellation Modal (Accessible Design-System Dialog) -->
      <div class="modal-backdrop" *ngIf="orderToCancel()" appFocusTrap (escape)="closeCancelModal()" (click)="closeCancelModal()" role="dialog" aria-modal="true" aria-labelledby="cancel-modal-title">
        <section class="modal-box" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <div>
              <span class="chip danger">Void / Cancel Order</span>
              <h3 class="cancel-modal-title" id="cancel-modal-title">Cancel Order #{{ orderToCancel()?.orderCode }}</h3>
              <p class="muted cancel-modal-note">
                Amount: {{ fmt(orderToCancel()?.totalAmount || 0) }} · {{ orderToCancel()?.customerName || 'Walk-in customer' }}
              </p>
            </div>
            <button type="button" class="icon-btn" (click)="closeCancelModal()" [disabled]="cancelSubmitting()" aria-label="Close dialog">✕</button>
          </div>

          <div class="modal-body cancel-modal-body">
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
      gap: var(--kb-space-5);
    }

    /* ── Floor summary strip ── */
    .live-summary {
      display: flex;
      align-items: stretch;
      flex-wrap: wrap;
      gap: var(--kb-space-3) 0;
      padding: var(--kb-space-3) var(--kb-space-4);
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-card);
    }

    .live-summary__item {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-1);
      flex: 1 1 0;
      min-width: 110px;
      padding: 0 var(--kb-space-4);
      border-left: 1px solid var(--kb-color-border);
    }

    .live-summary__item:first-child { border-left: 0; padding-left: 0; }
    .live-summary__item:last-child { padding-right: 0; }

    .live-summary__label {
      font-size: var(--kb-text-xs);
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--kb-color-muted-foreground);
      white-space: nowrap;
    }

    .live-summary__value {
      font-size: var(--kb-text-lg);
      font-weight: 700;
      line-height: 1.2;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }

    .live-summary__item--money .live-summary__value { color: var(--kb-accent-money); }
    .live-summary__item--attention .live-summary__value,
    .live-summary__item--attention .live-summary__label { color: var(--kb-color-warning); }

    @media (max-width: 640px) {
      .live-summary {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: var(--kb-space-3);
      }
      .live-summary__item { padding: 0; border-left: 0; }
      .live-summary__item--money,
      .live-summary__item--attention { grid-column: span 2; }
    }

    /* Refresh lives in the page header's shared date-range toolbar, which owns
       its own spinner, so the tab no longer draws a second one. */

    /* ── Bento Active Cards Grid (bentogrids.com standard) ── */
    .active-orders-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
      gap: var(--kb-space-4);
    }

    /* A 310px minimum reads as a card, but on a phone it is wider than the
       column that holds it — which is what pushed the board sideways. The
       ladder's 640 step is where the app drops every two-up grid to one. */
    @media (max-width: 640px) {
      .active-orders-grid,
      .skeleton-card-grid {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    /* The loading state borrows .bento-tile for its box, and repeats the card
       track of .active-orders-grid so the board does not jump when the orders
       land. The class is separate on purpose: "is there a grid" is also what a
       test asks to tell loaded cards from placeholders. */
    .skeleton-card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
      gap: var(--kb-space-4);
    }
    /* A placeholder cannot be hovered or pressed like a card. */
    .skeleton-card {
      pointer-events: none;
    }
    .skeleton-card__row {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
    }

    /* Surface, radius, padding and the idle hover all come from the shared
       .bento-tile. This adds only what is local to an order card: the wider
       inner rhythm and the brand-tinted hover lift. */
    .pos-card-tactile {
      gap: var(--kb-space-3);
      cursor: pointer;
      /* The shared .bento-tile carries a drop shadow; on a board of six cards
         that reads as six floating planes. The order card sits on its border
         and earns elevation only under the pointer. */
      box-shadow: var(--kb-shadow-xs);
    }
    .pos-card-tactile:hover {
      transform: translateY(-2px);
      border-color: var(--kb-color-primary);
      box-shadow: var(--kb-shadow-sm);
    }
    .pos-card-tactile:active {
      transform: scale(0.98);
    }
    /* An urgent card is marked in its gutter and its border. The inset bar does
       not change the card's box, so the grid does not reflow when the clock
       crosses 25 minutes. */
    .pos-card--urgent {
      border-color: rgba(var(--kb-color-warning-rgb), 0.45);
      box-shadow: inset 3px 0 0 var(--kb-color-warning), var(--kb-shadow-xs);
    }
    .pos-card--urgent:hover {
      border-color: var(--kb-color-warning);
      box-shadow: inset 3px 0 0 var(--kb-color-warning), var(--kb-shadow-sm);
    }

    .card-top-row {
      display: flex;
      align-items: center;
      gap: var(--kb-space-3);
    }
    .channel-avatar {
      width: 40px;
      height: 40px;
      border-radius: var(--kb-radius-xl);
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
      font-size: var(--kb-text-lg);
      font-weight: 800;
      color: var(--kb-color-foreground);
    }
    .channel-tag {
      font-size: var(--kb-text-xs);
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .status-pill {
      flex: none;
      font-size: var(--kb-text-xs);
      font-weight: 700;
      line-height: 1.3;
      padding: 0.2rem 0.6rem;
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
    }
    /* The pill states the ORDER's state, so the warning tone must win over the
       payment modifier when both classes are present. Payment already has its
       own dot and word on the line below, which is why this does not try to say
       both things in one chip. */
    .status-pill--pending {
      background: var(--kb-color-success-soft);
      color: var(--kb-color-success);
    }
    .status-pill--warn {
      background: var(--kb-color-warning-soft);
      color: var(--kb-color-warning);
    }

    .card-customer-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--kb-space-2);
    }
    .customer-name {
      font-size: var(--kb-text-md);
      font-weight: 600;
      color: var(--kb-color-foreground);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .table-identifier {
      flex: none;
      font-size: var(--kb-text-sm);
      font-weight: 700;
      color: var(--kb-color-link);
      background: var(--kb-color-primary-soft);
      padding: 0.15rem 0.55rem;
      border-radius: var(--kb-radius-md);
    }

    .card-payment-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--kb-space-2);
    }
    .payment-status {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      font-size: var(--kb-text-xs);
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
    }
    .payment-dot {
      width: 6px;
      height: 6px;
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-success);
      flex: none;
    }
    .payment-status--pending { color: var(--kb-color-warning); }
    .payment-status--pending .payment-dot { background: var(--kb-color-warning); }

    .card-bottom-row {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--kb-space-3);
      border-top: 1px solid var(--kb-color-border);
      padding-top: 0.75rem;
    }
    .amount-col {
      display: flex;
      flex-direction: column;
    }
    .amount-label {
      font-size: var(--kb-text-xs);
      color: var(--kb-color-muted-foreground);
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }
    .amount-val {
      font-size: var(--kb-text-xl);
      font-weight: 800;
      line-height: 1.15;
      color: var(--kb-color-foreground);
      font-variant-numeric: tabular-nums;
    }

    .elapsed-pill {
      font-size: var(--kb-text-xs);
      font-weight: 600;
      color: var(--kb-color-muted-foreground);
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .elapsed-pill--urgent {
      color: var(--kb-color-warning);
      font-weight: 700;
    }

    /* The all-clear state is the shared .empty-orders-view (bento.css), so it
       matches every other empty state in the app rather than drawing a second
       dashed card of its own. */

    /* ── Card Actions Row ── */
    .card-actions-row {
      display: flex;
      flex-wrap: wrap;
      gap: var(--kb-space-2);
      border-top: 1px solid var(--kb-color-border);
      padding-top: var(--kb-space-3);
      margin-top: var(--kb-space-1);
    }
    .card-action-btn {
      flex: 1 1 auto;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.35rem;
      padding: 0.45rem var(--kb-space-2);
      border-radius: var(--kb-radius-lg);
      font-size: var(--kb-text-sm);
      font-weight: 600;
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      color: var(--kb-color-foreground);
      cursor: pointer;
      min-height: 38px;
      transition: background 150ms ease, border-color 150ms ease, color 150ms ease;
    }
    .card-action-btn:hover {
      background: var(--kb-color-surface-2);
      border-color: var(--kb-color-border-strong);
      color: var(--kb-color-foreground);
    }
    .card-action-btn:active {
      transform: scale(0.97);
    }
    /* Details is the card's primary action: the one filled brand control. */
    .card-action-btn--primary {
      background: var(--kb-color-primary);
      border-color: var(--kb-color-primary);
      color: var(--kb-color-primary-foreground);
    }
    .card-action-btn--primary:hover {
      background: var(--kb-color-primary-hover);
      border-color: var(--kb-color-primary-hover);
      color: var(--kb-color-primary-foreground);
    }
    @media (pointer: coarse) {
      .card-action-btn { min-height: var(--kb-touch-min); }
    }
    /* Solid danger fill, matching the sidebar's Sign out button: the cancel action is
       destructive, so it reads as a filled red button with white content rather than a
       tinted chip that blends into the Details / Invoice pair beside it. */
    .card-action-btn--danger {
      background: var(--kb-color-danger-solid);
      border-color: var(--kb-color-danger-solid);
      color: var(--kb-color-on-primary);
    }
    .card-action-btn--danger:hover {
      background: var(--kb-color-danger-solid-hover);
      border-color: var(--kb-color-danger-solid-hover);
      color: var(--kb-color-on-primary);
    }

    /* ── Modal Layout ── */
    .modal-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--kb-space-4);
      margin-bottom: 0.75rem;
    }
    /* The close cross is the shared global icon-btn — see the
       "Micro-interactions & Tactile Feedback" section of styles.css. */
    .modal-header .icon-btn { font-size: 1.15rem; }
    .chip {
      display: inline-block;
      padding: 0.2rem 0.55rem;
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      border-radius: var(--kb-radius-full);
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
      font-size: var(--kb-text-sm);
      font-weight: 600;
      color: var(--kb-color-foreground);
    }
    .field-select, .field-control {
      padding: var(--kb-space-2) var(--kb-space-3);
      border: 1px solid var(--kb-color-border-strong);
      border-radius: var(--kb-radius-lg);
      font-size: var(--kb-text-md);
      min-height: 40px;
      background: var(--kb-color-input);
      color: var(--kb-color-foreground);
      outline: none;
    }
    .field-select:focus, .field-control:focus {
      border-color: var(--kb-color-primary);
    }
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--kb-space-2);
      margin-top: 1.25rem;
    }
    .ghost-btn {
      padding: var(--kb-space-2) var(--kb-space-4);
      border-radius: var(--kb-radius-lg);
      border: 1px solid var(--kb-color-border-strong);
      background: var(--kb-color-surface);
      font-size: var(--kb-text-md);
      font-weight: 600;
      cursor: pointer;
      color: var(--kb-color-foreground);
      min-height: 40px;
    }
    .ghost-btn:hover:not(:disabled) {
      background: var(--kb-color-bg-app);
    }
    
    
    .danger-btn {
      background: var(--kb-color-danger-solid) !important;
      color: var(--kb-color-foreground-contrast) !important;
      border: none !important;
    }
    .danger-btn:hover:not(:disabled) {
      background: var(--kb-color-danger) !important;
    }

    /* Cancel dialog: the same three carriers exist in the history tab. */
    .cancel-modal-title { margin: 0.35rem 0 0; }
    .cancel-modal-note { margin: var(--kb-space-1) 0 0; font-size: var(--kb-text-md); }
    .cancel-modal-body { display: grid; gap: var(--kb-space-4); padding: var(--kb-space-4) 0; }
  `]
})
export class LiveOrdersTabComponent implements OnDestroy {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  loading = signal(false);
  error = signal('');
  activeOrders = signal<BusinessOrder[]>([]);

  /** Set only by a user-requested reload — see `showReloadSkeleton`. */
  private readonly manualLoad = signal(false);

  /**
   * Skeleton on the first load and on any user-requested reload, so the reload
   * is visible (as on Terminals). Deliberately false for the 30s poll: flashing
   * skeletons over orders someone is reading would be worse than no feedback.
   */
  readonly showReloadSkeleton = computed(
    () => this.manualLoad() || (this.loading() && !this.activeOrders().length),
  );
  selectedOrderDetail = signal<OrderDetailResponse | null>(null);

  // Cancel order modal state
  orderToCancel = signal<BusinessOrder | null>(null);
  cancelReasonPreset = 'Customer requested cancellation';
  cancelReasonCustom = '';
  cancelSubmitting = signal(false);

  private pollInterval: ReturnType<typeof setInterval> | null = null;

  readonly fmt = formatCurrency;

  readonly liveKpiItems = computed(() => {
    const orders = this.activeOrders();
    return [
      {
        label: 'Active Orders',
        value: orders.length.toString(),
        tone: 'success' as const,
        accent: 'positive',
        badge: { text: '+new', tone: 'success' },
      } as KpiItem,
      {
        label: 'Preparing',
        value: orders.filter(o => (o.orderStatus || '').trim().toLowerCase() === 'draft').length.toString(),
        tone: 'warning' as const,
        accent: 'flow',
      } as KpiItem,
      {
        label: 'Completed (Today)',
        value: orders.filter(o => {
          const created = o.createdAt ? new Date(o.createdAt) : null;
          const today = new Date();
          const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
          return created && created.toISOString().startsWith(todayStr) && (o.orderStatus || '').trim().toLowerCase() === 'completed';
        }).length.toString(),
        tone: 'success' as const,
      } as KpiItem,
      {
        label: 'Payment Due',
        value: orders.filter(o => (o.paymentStatus || '').trim().toLowerCase() === 'pending').length.toString(),
        tone: 'danger' as const,
        accent: 'activity',
      } as KpiItem,
    ] as KpiItem[];
  });

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

  /** `manual` = a user asked for this (header refresh, Retry). */
  load(manual = false): void {
    this.manualLoad.set(manual);
    this.loading.set(true);
    this.error.set('');
    this.api.getOrdersPaginated(0, 100, 'draft').subscribe({
      next: (res: PaginatedOrdersResponse) => {
        this.activeOrders.set(res.content);
        this.loading.set(false);
        this.manualLoad.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.manualLoad.set(false);
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

  /**
   * The API sends title-cased status labels ("Draft", "Pending" — see
   * BusinessReadService.normalizeLabel) while these tests used to compare against the
   * lowercase stored value, so both were always false: an open bill's pill fell through to
   * printing the payment status, which is why the live board read "Pending" (the money's
   * word) instead of the order's own state. Every status test here is case-insensitive.
   */
  isOpenBill(order: BusinessOrder): boolean {
    return (order.orderStatus || '').trim().toLowerCase() === 'draft';
  }

  isPendingPayment(order: BusinessOrder): boolean {
    return (order.paymentStatus || '').trim().toLowerCase() === 'pending';
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

  readonly isTakeawayFilter = signal('All');

  filterByChannel(channel: string): void {
    this.isTakeawayFilter.set(channel);
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
