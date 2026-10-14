import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, forkJoin } from 'rxjs';
import { OrderDetailResponse } from '../core/models/api.models';
import { formatCurrency, formatDate } from './formatters';
import { AuthService } from '../core/auth/auth.service';
import { buildInvoiceLink } from '../core/utils/invoice-link.util';
import { ToastService } from '../core/services/toast.service';
import { BusinessApiService } from '../core/services/business-api.service';

@Component({
  selector: 'app-order-detail-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="order-modal__overlay" *ngIf="order" (click)="close()">
      <section
        #dialogCard
        class="order-modal__card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="order-detail-title"
        tabindex="-1"
        (click)="$event.stopPropagation()"
      >
        <div class="order-modal__header">
          <h3 id="order-detail-title" class="order-modal__title">Order #{{ order.order.orderCode }}</h3>
          <button #closeButton type="button" class="order-modal__close" (click)="close()" aria-label="Close order details">✕</button>
        </div>

        <div class="order-modal__details">
          <div class="order-modal__row" *ngIf="order.order.orderType">
            <span class="order-modal__label">Order Type</span>
            <span class="order-modal__value">
              <span class="chip" [class.chip--dine-in]="(order.order.orderType || '').toLowerCase().includes('dine')" [class.chip--takeaway]="(order.order.orderType || '').toLowerCase().includes('take')">
                {{ formatOrderType(order.order.orderType) }}
              </span>
            </span>
          </div>
          <div class="order-modal__row" *ngIf="order.order.invoiceNumber">
            <span class="order-modal__label">Invoice #</span>
            <span class="order-modal__value font-mono" style="font-weight: 700;">{{ order.order.invoiceNumber }}</span>
          </div>
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
              <span class="chip" [class.chip--success]="isStatus(order.order.orderStatus, 'completed')"
                                  [class.chip--warn]="isStatus(order.order.orderStatus, 'draft')"
                                  [class.chip--danger]="isStatus(order.order.orderStatus, 'cancelled')">
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

        <!-- Owner-only correction. Deliberately last-word-on-the-server: the form submits
             what changed and shows the server's refusal message verbatim. -->
        <div class="order-modal__correction" *ngIf="isOwner()">
          <button
            type="button"
            class="correction__toggle"
            [attr.aria-expanded]="editOpen()"
            (click)="toggleCorrection()">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>
            </svg>
            {{ editOpen() ? 'Close correction' : 'Correct this bill' }}
          </button>

          <div class="correction__form" *ngIf="editOpen()">
            <div class="correction__grid">
              <label class="correction__field">
                <span>Order status</span>
                <select id="correction-status" [ngModel]="statusDraft()" (ngModelChange)="statusDraft.set($event)">
                  <option *ngFor="let option of statusOptions" [value]="option.value">{{ option.label }}</option>
                </select>
              </label>

              <label class="correction__field">
                <span>Payment mode</span>
                <select id="correction-mode" [ngModel]="modeDraft()" (ngModelChange)="modeDraft.set($event)">
                  <option *ngFor="let option of modeOptions" [value]="option.value">{{ option.label }}</option>
                </select>
              </label>

              <label class="correction__field" *ngIf="isPartMode(modeDraft())">
                <span>First split amount</span>
                <input id="correction-part-1" type="number" min="0" step="0.01"
                       [ngModel]="part1()" (ngModelChange)="part1.set($event)" />
              </label>

              <label class="correction__field" *ngIf="isPartMode(modeDraft())">
                <span>Second split amount</span>
                <input id="correction-part-2" type="number" min="0" step="0.01"
                       [ngModel]="part2()" (ngModelChange)="part2.set($event)" />
              </label>
            </div>

            <label class="correction__field correction__field--wide">
              <span>Reason (kept in the audit log)</span>
              <input id="correction-reason" type="text" maxlength="500"
                     placeholder="For example: cashier selected UPI by mistake"
                     [ngModel]="reason()" (ngModelChange)="reason.set($event)" />
            </label>

            <p class="correction__note">
              Saving this changes the order's recognised revenue and the day's closing figures.
              A completed bill cannot be reopened because the stock it consumed cannot be returned.
            </p>

            <p class="correction__error" role="alert" *ngIf="editError()">{{ editError() }}</p>

            <div class="correction__actions">
              <button type="button" class="ghost-btn" (click)="toggleCorrection()" [disabled]="editSubmitting()">
                Cancel
              </button>
              <button type="button" class="action-btn-link primary-action" (click)="saveCorrection()" [disabled]="editSubmitting()">
                {{ editSubmitting() ? 'Saving…' : 'Save correction' }}
              </button>
            </div>
          </div>
        </div>

        <div class="order-modal__items">
          <h4 class="order-modal__subtitle">Line Items</h4>
          <div class="order-modal__table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Qty</th>
                  <th>Price</th>
                  <th>Total</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let item of order.lineItems; trackBy: trackByIndex">
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

        <div class="order-modal__footer">
          <a
            *ngIf="getInvoiceUrl()"
            [href]="getInvoiceUrl()"
            target="_blank"
            rel="noopener noreferrer"
            class="action-btn-link primary-action"
            title="Open printable tax invoice in a new tab"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
            View Invoice
          </a>
          <button type="button" class="ghost-btn" (click)="copyInvoiceLink()">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
            Copy Link
          </button>
          <button
            *ngIf="order.order.customerContact"
            type="button"
            class="wa-btn"
            (click)="shareOnWhatsApp()"
            title="Share invoice link on WhatsApp"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M.057 24l1.687-6.163c-1.041-1.804-1.588-3.849-1.587-5.946.003-6.556 5.338-11.891 11.893-11.891 3.181.001 6.167 1.24 8.413 3.488 2.245 2.248 3.481 5.236 3.48 8.414-.003 6.557-5.338 11.892-11.893 11.892-1.99-.001-3.951-.5-5.688-1.448l-6.305 1.654zm6.597-3.807c1.676.995 3.276 1.591 5.392 1.592 5.448 0 9.886-4.434 9.889-9.885.002-5.462-4.415-9.89-9.881-9.892-5.452 0-9.887 4.434-9.889 9.884-.001 2.225.651 3.891 1.746 5.634l-.999 3.648 3.742-.981z"/></svg>
            WhatsApp
          </button>
          <button type="button" class="ghost-btn" (click)="close()">Close</button>
        </div>
      </section>
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
      background: var(--panel);
      border: 1px solid var(--line);
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
      border-bottom: 1px solid var(--line);
    }

    .order-modal__title {
      margin: 0;
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--ink);
    }

    .order-modal__close {
      background: none;
      border: none;
      font-size: 1.2rem;
      color: var(--muted);
      cursor: pointer;
      padding: 0.25rem 0.5rem;
      border-radius: 6px;
      transition: background 0.2s;
    }

    .order-modal__close:hover {
      background: var(--bg);
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
      border-top: 1px solid var(--line);
    }

    .order-modal__label {
      font-size: 0.85rem;
      color: var(--muted);
    }

    .order-modal__value {
      font-size: 0.9rem;
      color: var(--ink);
      font-weight: 500;
    }

    .order-modal__value--total {
      font-size: 1.05rem;
      font-weight: 700;
      color: var(--brand);
    }

    .chip {
      display: inline-block;
      padding: 0.15rem 0.55rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      background: var(--bg);
      color: var(--ink);
    }

    .chip--success {
      background: rgba(15, 118, 110, 0.12);
      color: var(--accent);
    }

    .chip--warn {
      background: rgba(232, 122, 30, 0.12);
      color: var(--brand);
    }

    .chip--danger {
      background: rgba(192, 57, 43, 0.12);
      color: var(--danger);
    }

    .order-modal__correction {
      padding: 1rem 1.5rem;
      border-top: 1px solid var(--line);
      background: var(--kb-color-surface-2, #f8f5ef);
    }

    .correction__toggle {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      border: 1px solid var(--line);
      background: var(--panel);
      color: var(--kb-color-foreground);
      border-radius: 999px;
      padding: 0.4rem 0.85rem;
      font-size: 0.8rem;
      font-weight: 700;
      cursor: pointer;
    }

    .correction__toggle:hover { border-color: var(--brand); color: var(--brand); }

    .correction__form { margin-top: 0.85rem; display: grid; gap: 0.7rem; }

    .correction__grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 160px), 1fr));
      gap: 0.6rem;
    }

    .correction__field { display: grid; gap: 0.25rem; }
    .correction__field > span { font-size: 0.74rem; font-weight: 700; color: var(--kb-color-muted-foreground); }

    .correction__field select,
    .correction__field input {
      min-height: 36px;
      padding: 0.35rem 0.55rem;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      color: var(--kb-color-foreground);
      font-family: inherit;
      font-size: 0.84rem;
    }

    .correction__note { margin: 0; font-size: 0.74rem; color: var(--kb-color-muted-foreground); line-height: 1.4; }

    .correction__error {
      margin: 0;
      font-size: 0.78rem;
      font-weight: 600;
      color: var(--kb-color-danger);
    }

    .correction__actions { display: flex; justify-content: flex-end; gap: 0.5rem; }

    .order-modal__items {
      padding: 1rem 1.5rem 1.5rem;
      border-top: 1px solid var(--line);
    }

    .order-modal__subtitle {
      margin: 0 0 0.75rem;
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--ink);
    }

    .order-modal__table-wrap {
      overflow-x: auto;
    }

    /* The table design is defined once in the global stylesheet (see the
       "Tables" section of styles.css). Only the modal's own scroll box and its
       variant line stay here. */

    .order-modal__variant {
      font-size: 0.8rem;
      color: var(--muted);
    }

    .order-modal__empty {
      text-align: center;
      padding: 1rem;
      color: var(--muted);
      font-size: 0.9rem;
    }

    .order-modal__footer {
      display: flex;
      justify-content: flex-end;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem 1.5rem 1.25rem;
      border-top: 1px solid var(--line);
      flex-wrap: wrap;
    }

    .action-btn-link {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.45rem 0.85rem;
      border-radius: 8px;
      font-size: 0.82rem;
      font-weight: 600;
      text-decoration: none;
      transition: background 0.15s, transform 0.15s;
    }

    .action-btn-link.primary-action {
      background: var(--brand);
      color: var(--kb-color-on-primary);
    }

    .action-btn-link.primary-action:hover {
      opacity: 0.92;
    }

    .wa-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.45rem 0.85rem;
      border-radius: 8px;
      font-size: 0.82rem;
      font-weight: 600;
      background: rgba(37, 211, 102, 0.12);
      color: var(--kb-color-success);
      border: 1px solid rgba(37, 211, 102, 0.3);
      cursor: pointer;
      transition: background 0.15s;
    }

    .wa-btn:hover {
      background: rgba(37, 211, 102, 0.2);
    }

    .chip--dine-in {
      background: rgba(59, 130, 246, 0.12);
      color: var(--kb-color-info);
    }

    .chip--takeaway {
      background: rgba(245, 158, 11, 0.12);
      color: var(--kb-color-warning);
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
export class OrderDetailModalComponent implements OnChanges, OnDestroy {
  @Input() order: OrderDetailResponse | null = null;
  @Output() closed = new EventEmitter<void>();
  /** Emitted after a successful correction so the list behind the modal can refetch. */
  @Output() billUpdated = new EventEmitter<void>();
  @ViewChild('dialogCard') private dialogCard?: ElementRef<HTMLElement>;
  @ViewChild('closeButton') private closeButton?: ElementRef<HTMLButtonElement>;

  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly api = inject(BusinessApiService);

  /** Only the owner may correct a recorded bill; the server enforces this too. */
  readonly isOwner = computed(() => this.auth.session()?.role === 'OWNER');

  readonly editOpen = signal(false);
  readonly editSubmitting = signal(false);
  readonly editError = signal('');
  readonly statusDraft = signal('draft');
  readonly modeDraft = signal('');
  readonly part1 = signal<string>('');
  readonly part2 = signal<string>('');
  readonly reason = signal('');

  readonly statusOptions = [
    { value: 'draft', label: 'Pending (open bill)' },
    { value: 'completed', label: 'Completed / paid' },
    { value: 'cancelled', label: 'Cancelled' },
  ];

  readonly modeOptions = [
    { value: 'cash', label: 'Cash' },
    { value: 'upi', label: 'UPI' },
    { value: 'pos', label: 'Card / POS' },
    { value: 'part_payment_upi_cash', label: 'Split — UPI + Cash' },
    { value: 'part_payment_cash_pos', label: 'Split — Cash + Card' },
    { value: 'part_payment_upi_pos', label: 'Split — UPI + Card' },
  ];

  /** Status labels arrive title-cased from the API ("Completed"), never uppercased. */
  isStatus(value: string | null | undefined, expected: string): boolean {
    return (value || '').trim().toLowerCase() === expected;
  }

  isPartMode(mode: string): boolean {
    return (mode || '').startsWith('part');
  }

  toggleCorrection(): void {
    if (this.editOpen()) {
      this.editOpen.set(false);
      this.editError.set('');
      return;
    }
    const current = this.order?.order;
    if (!current) return;
    this.statusDraft.set(this.normalizeStatus(current.orderStatus));
    this.modeDraft.set(this.normalizeMode(current.paymentMethod));
    this.part1.set('');
    this.part2.set('');
    this.reason.set('');
    this.editError.set('');
    this.editOpen.set(true);
  }

  saveCorrection(): void {
    const current = this.order?.order;
    if (!current || this.editSubmitting()) return;

    const reason = this.reason().trim();
    const statusChanged = this.statusDraft() !== this.normalizeStatus(current.orderStatus);
    const modeChanged = this.modeDraft() !== '' && this.modeDraft() !== this.normalizeMode(current.paymentMethod);

    this.editError.set('');
    if (!statusChanged && !modeChanged) {
      this.editError.set('Change the order status or the payment mode before saving.');
      return;
    }
    if (reason.length < 5) {
      this.editError.set('Give a short reason (at least 5 characters) — it is recorded in the audit log.');
      return;
    }

    const calls: Observable<unknown>[] = [];

    if (statusChanged) {
      calls.push(this.api.updateBillStatus(current.orderId, {
        orderStatus: this.statusDraft() as 'draft' | 'completed' | 'cancelled',
        reason,
      }));
    }

    if (modeChanged) {
      const partMode = this.isPartMode(this.modeDraft());
      const first = Number(this.part1());
      const second = Number(this.part2());
      if (partMode && !(first > 0) ) {
        this.editError.set('Enter the first split amount.');
        return;
      }
      if (partMode && !(second > 0)) {
        this.editError.set('Enter the second split amount.');
        return;
      }
      calls.push(this.api.updateBillPaymentMode(current.orderId, {
        paymentMode: this.modeDraft(),
        partAmount1: partMode ? first : null,
        partAmount2: partMode ? second : null,
        reason,
      }));
    }

    this.editSubmitting.set(true);
    forkJoin(calls).subscribe({
      next: () => {
        this.editSubmitting.set(false);
        this.editOpen.set(false);
        this.toast.show(`Order #${current.orderCode} corrected`, 'success');
        this.billUpdated.emit();
      },
      error: (err: { error?: { error?: string } }) => {
        this.editSubmitting.set(false);
        // The server's refusals are written for the user; show them as-is.
        this.editError.set(err?.error?.error || 'The correction could not be saved.');
      },
    });
  }

  private normalizeStatus(value: string | null | undefined): string {
    const normalized = (value || '').trim().toLowerCase();
    if (normalized === 'completed' || normalized === 'paid') return 'completed';
    if (normalized === 'cancelled') return 'cancelled';
    return 'draft';
  }

  private normalizeMode(value: string | null | undefined): string {
    const normalized = (value || '').trim().toLowerCase().replace(/\s+/g, '_');
    return this.modeOptions.some((option) => option.value === normalized) ? normalized : '';
  }

  readonly fmtCurrency = formatCurrency;
  readonly fmtDate = formatDate;
  private previouslyFocused: HTMLElement | null = null;
  private previousBodyOverflow = '';

  getInvoiceUrl(): string | null {
    if (!this.order?.order) return null;
    const session = this.auth.session();
    return buildInvoiceLink(session?.restaurantId, this.order.order.orderId, this.order.order.publicToken);
  }

  copyInvoiceLink(): void {
    const url = this.getInvoiceUrl();
    if (!url) return;
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(url).then(() => {
        this.toast.show(`Invoice link copied for Order #${this.order?.order.orderCode}`, 'success');
      }).catch(() => {
        prompt('Copy invoice link:', url);
      });
    } else {
      prompt('Copy invoice link:', url);
    }
  }

  shareOnWhatsApp(): void {
    const url = this.getInvoiceUrl();
    if (!url || !this.order) return;
    const phone = (this.order.order.customerContact || '').replace(/\D/g, '');
    const text = encodeURIComponent(`Invoice for Order #${this.order.order.orderCode}: ${url}`);
    const waUrl = phone ? `https://wa.me/${phone}?text=${text}` : `https://wa.me/?text=${text}`;
    window.open(waUrl, '_blank', 'noopener,noreferrer');
  }

  formatOrderType(type?: string): string {
    if (!type) return 'Dine-In';
    const lower = type.toLowerCase();
    if (lower.includes('dine')) return 'Dine-In';
    if (lower.includes('take') || lower.includes('parcel')) return 'Takeaway';
    if (lower.includes('deliv')) return 'Delivery';
    return type.charAt(0).toUpperCase() + type.slice(1);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['order']) return;
    if (this.order) {
      this.previouslyFocused = document.activeElement as HTMLElement | null;
      this.previousBodyOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      setTimeout(() => this.closeButton?.nativeElement.focus());
    } else {
      this.restorePage();
    }
  }

  ngOnDestroy(): void {
    this.restorePage();
  }

  @HostListener('document:keydown', ['$event'])
  handleKeydown(event: KeyboardEvent): void {
    if (!this.order) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusable = this.dialogCard?.nativeElement.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    );
    if (!focusable?.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  close(): void {
    this.restorePage();
    this.closed.emit();
  }

  private restorePage(): void {
    document.body.style.overflow = this.previousBodyOverflow;
    if (this.previouslyFocused) {
      this.previouslyFocused.focus();
      this.previouslyFocused = null;
    }
  }

  trackByIndex = (_: number, __: unknown) => _;
}
