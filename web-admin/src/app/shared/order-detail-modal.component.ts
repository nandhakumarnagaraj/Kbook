import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderDetailResponse } from '../core/models/api.models';
import { formatCurrency, formatDate } from './formatters';

@Component({
  selector: 'app-order-detail-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="order-modal__overlay" *ngIf="order" (click)="closed.emit()">
      <div class="order-modal__card" (click)="$event.stopPropagation()">
        <div class="order-modal__header">
          <h3 class="order-modal__title">Order #{{ order.order.orderCode }}</h3>
          <button class="order-modal__close" (click)="closed.emit()" aria-label="Close">✕</button>
        </div>

        <div class="order-modal__details">
          <div class="order-modal__row">
            <span class="order-modal__label">Customer</span>
            <span class="order-modal__value">{{ order.order.customerName || '—' }}</span>
          </div>
          <div class="order-modal__row">
            <span class="order-modal__label">Contact</span>
            <span class="order-modal__value">{{ order.order.customerContact || '—' }}</span>
          </div>
          <div class="order-modal__row">
            <span class="order-modal__label">Status</span>
            <span class="order-modal__value">
              <span class="chip" [class.chip--success]="order.order.orderStatus === 'COMPLETED'"
                                  [class.chip--warn]="order.order.orderStatus === 'PENDING'"
                                  [class.chip--danger]="order.order.orderStatus === 'CANCELLED'">
                {{ order.order.orderStatus }}
              </span>
            </span>
          </div>
          <div class="order-modal__row">
            <span class="order-modal__label">Payment</span>
            <span class="order-modal__value">{{ order.order.paymentMethod }}</span>
          </div>
          <div class="order-modal__row">
            <span class="order-modal__label">Payment Status</span>
            <span class="order-modal__value">{{ order.order.paymentStatus }}</span>
          </div>
          <div class="order-modal__row">
            <span class="order-modal__label">Date</span>
            <span class="order-modal__value">{{ fmtDate(order.order.createdAt) }}</span>
          </div>
          <div class="order-modal__row order-modal__row--total">
            <span class="order-modal__label">Total</span>
            <span class="order-modal__value order-modal__value--total">{{ fmtCurrency(order.order.totalAmount) }}</span>
          </div>
        </div>

        <div class="order-modal__items">
          <h4 class="order-modal__subtitle">Line Items</h4>
          <div class="order-modal__table-wrap">
            <table class="order-modal__table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Qty</th>
                  <th>Price</th>
                  <th>Total</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let item of order.lineItems">
                  <td>
                    {{ item.itemName }}
                    <span class="order-modal__variant" *ngIf="item.variantName">
                      ({{ item.variantName }})
                    </span>
                  </td>
                  <td>{{ item.quantity }}</td>
                  <td>{{ fmtCurrency(item.price) }}</td>
                  <td>{{ fmtCurrency(item.itemTotal) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="order-modal__empty" *ngIf="order.lineItems.length === 0">
            No line items found.
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .order-modal__overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.45);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
      overflow-y: auto;
    }

    .order-modal__card {
      background: var(--panel, #fffdf8);
      border: 1px solid var(--line, #e9dcc9);
      border-radius: 16px;
      width: 100%;
      max-width: 460px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
    }

    .order-modal__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1.25rem 1.5rem;
      border-bottom: 1px solid var(--line, #e9dcc9);
    }

    .order-modal__title {
      margin: 0;
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--ink, #24170f);
    }

    .order-modal__close {
      background: none;
      border: none;
      font-size: 1.2rem;
      color: var(--muted, #7d6b5f);
      cursor: pointer;
      padding: 0.25rem 0.5rem;
      border-radius: 6px;
      transition: background 0.2s;
    }

    .order-modal__close:hover {
      background: var(--bg, #f6f1e8);
    }

    .order-modal__details {
      padding: 1rem 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
    }

    .order-modal__row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .order-modal__row--total {
      margin-top: 0.5rem;
      padding-top: 0.75rem;
      border-top: 1px solid var(--line, #e9dcc9);
    }

    .order-modal__label {
      font-size: 0.85rem;
      color: var(--muted, #7d6b5f);
    }

    .order-modal__value {
      font-size: 0.9rem;
      color: var(--ink, #24170f);
      font-weight: 500;
    }

    .order-modal__value--total {
      font-size: 1.05rem;
      font-weight: 700;
      color: var(--brand, #b56a2d);
    }

    .chip {
      display: inline-block;
      padding: 0.15rem 0.55rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      background: var(--bg, #f6f1e8);
      color: var(--ink, #24170f);
    }

    .chip--success {
      background: rgba(29, 123, 95, 0.12);
      color: var(--accent, #1d7b5f);
    }

    .chip--warn {
      background: rgba(181, 106, 45, 0.12);
      color: var(--brand, #b56a2d);
    }

    .chip--danger {
      background: rgba(166, 55, 47, 0.12);
      color: var(--danger, #a6372f);
    }

    .order-modal__items {
      padding: 1rem 1.5rem 1.5rem;
      border-top: 1px solid var(--line, #e9dcc9);
    }

    .order-modal__subtitle {
      margin: 0 0 0.75rem;
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--ink, #24170f);
    }

    .order-modal__table-wrap {
      overflow-x: auto;
    }

    .order-modal__table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.85rem;
    }

    .order-modal__table th {
      text-align: left;
      padding: 0.5rem 0.6rem;
      border-bottom: 1px solid var(--line, #e9dcc9);
      color: var(--muted, #7d6b5f);
      font-weight: 600;
      font-size: 0.8rem;
      white-space: nowrap;
    }

    .order-modal__table td {
      padding: 0.5rem 0.6rem;
      border-bottom: 1px solid var(--line, #e9dcc9);
      color: var(--ink, #24170f);
    }

    .order-modal__table tr:last-child td {
      border-bottom: none;
    }

    .order-modal__variant {
      font-size: 0.8rem;
      color: var(--muted, #7d6b5f);
    }

    .order-modal__empty {
      text-align: center;
      padding: 1rem;
      color: var(--muted, #7d6b5f);
      font-size: 0.9rem;
    }

    @media (max-width: 480px) {
      .order-modal__card {
        max-height: 95vh;
        border-radius: 12px;
      }

      .order-modal__header,
      .order-modal__details,
      .order-modal__items {
        padding-left: 1rem;
        padding-right: 1rem;
      }
    }
  `]
})
export class OrderDetailModalComponent {
  @Input() order: OrderDetailResponse | null = null;
  @Output() closed = new EventEmitter<void>();

  fmtCurrency = formatCurrency;
  fmtDate = formatDate;
}
