import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="confirm-dialog__overlay" (click)="cancelled.emit()">
      <div class="confirm-dialog__card" (click)="$event.stopPropagation()">
        <h3 class="confirm-dialog__title">{{ title }}</h3>
        <p class="confirm-dialog__message">{{ message }}</p>
        <div class="confirm-dialog__actions">
          <button
            class="confirm-dialog__btn confirm-dialog__btn--cancel"
            (click)="cancelled.emit()">
            {{ cancelLabel }}
          </button>
          <button
            class="confirm-dialog__btn confirm-dialog__btn--confirm"
            [class.confirm-dialog__btn--danger]="confirmDanger"
            (click)="confirmed.emit()">
            {{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .confirm-dialog__overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.45);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
    }

    .confirm-dialog__card {
      background: var(--panel, #fffdf8);
      border: 1px solid var(--line, #e9dcc9);
      border-radius: 16px;
      padding: 1.5rem;
      width: 100%;
      max-width: 460px;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
    }

    .confirm-dialog__title {
      margin: 0 0 0.5rem;
      font-size: 1.15rem;
      font-weight: 600;
      color: var(--ink, #24170f);
    }

    .confirm-dialog__message {
      margin: 0 0 1.5rem;
      font-size: 0.95rem;
      color: var(--muted, #7d6b5f);
      line-height: 1.5;
    }

    .confirm-dialog__actions {
      display: flex;
      gap: 0.75rem;
      justify-content: flex-end;
    }

    .confirm-dialog__btn {
      padding: 0.6rem 1.25rem;
      border-radius: 10px;
      font-size: 0.9rem;
      font-weight: 600;
      cursor: pointer;
      border: none;
      transition: opacity 0.2s;
    }

    .confirm-dialog__btn:hover {
      opacity: 0.85;
    }

    .confirm-dialog__btn--cancel {
      background: transparent;
      border: 1px solid var(--line, #e9dcc9);
      color: var(--muted, #7d6b5f);
    }

    .confirm-dialog__btn--confirm {
      background: var(--brand, #b56a2d);
      color: #fff;
    }

    .confirm-dialog__btn--danger {
      background: var(--danger, #a6372f);
    }

    @media (max-width: 480px) {
      .confirm-dialog__actions {
        flex-direction: column-reverse;
      }

      .confirm-dialog__btn {
        width: 100%;
        text-align: center;
      }
    }
  `]
})
export class ConfirmDialogComponent {
  @Input() title = 'Confirm';
  @Input() message = 'Are you sure?';
  @Input() confirmLabel = 'Confirm';
  @Input() cancelLabel = 'Cancel';
  @Input() confirmDanger = false;

  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
}
