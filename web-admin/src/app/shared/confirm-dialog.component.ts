import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  Output,
  ViewChild
} from '@angular/core';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="confirm-dialog__overlay" (click)="cancel()">
      <section
        #dialogCard
        class="confirm-dialog__card"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-message"
        tabindex="-1"
        (click)="$event.stopPropagation()"
      >
        <div class="confirm-dialog__icon" [class.confirm-dialog__icon--danger]="confirmDanger" aria-hidden="true">
          {{ confirmDanger ? '!' : '?' }}
        </div>
        <div class="confirm-dialog__content">
          <h3 id="confirm-dialog-title" class="confirm-dialog__title">{{ title }}</h3>
          <p id="confirm-dialog-message" class="confirm-dialog__message">{{ message }}</p>
        </div>
        <div class="confirm-dialog__actions">
          <button #cancelButton type="button" class="confirm-dialog__btn confirm-dialog__btn--cancel" (click)="cancel()">
            {{ cancelLabel }}
          </button>
          <button
            type="button"
            class="confirm-dialog__btn confirm-dialog__btn--confirm"
            [class.confirm-dialog__btn--danger]="confirmDanger"
            (click)="confirm()"
          >
            {{ confirmLabel }}
          </button>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .confirm-dialog__overlay {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: grid;
      place-items: center;
      padding: 1rem;
      background: rgba(36, 23, 15, 0.56);
      backdrop-filter: blur(3px);
    }
    .confirm-dialog__card {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      gap: 1rem;
      width: min(460px, 100%);
      padding: 1.5rem;
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 18px;
      box-shadow: 0 24px 64px rgba(36, 23, 15, 0.24);
    }
    .confirm-dialog__card:focus { outline: none; }
    .confirm-dialog__icon {
      display: grid;
      place-items: center;
      width: 2.5rem;
      height: 2.5rem;
      color: var(--brand-deep);
      background: var(--brand-soft);
      border-radius: 12px;
      font-size: 1.1rem;
      font-weight: 800;
    }
    .confirm-dialog__icon--danger { color: var(--danger); background: var(--danger-soft); }
    .confirm-dialog__title { margin: 0 0 0.45rem; font-size: 1.15rem; color: var(--ink); }
    .confirm-dialog__message { margin: 0; color: var(--muted); line-height: 1.55; }
    .confirm-dialog__actions {
      grid-column: 1 / -1;
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      margin-top: 0.5rem;
    }
    .confirm-dialog__btn {
      min-height: 44px;
      padding: 0.65rem 1.15rem;
      border: 0;
      border-radius: 11px;
      cursor: pointer;
      font-weight: 700;
    }
    .confirm-dialog__btn:focus-visible { outline: 3px solid rgba(181, 106, 45, 0.28); outline-offset: 2px; }
    .confirm-dialog__btn--cancel { color: var(--ink); background: #f4ece1; border: 1px solid var(--line); }
    .confirm-dialog__btn--confirm { color: #fff; background: var(--brand); }
    .confirm-dialog__btn--danger { background: var(--danger); }
    @media (max-width: 480px) {
      .confirm-dialog__card { padding: 1.25rem; }
      .confirm-dialog__actions { flex-direction: column-reverse; }
      .confirm-dialog__btn { width: 100%; }
    }
  `]
})
export class ConfirmDialogComponent implements AfterViewInit, OnDestroy {
  @Input() title = 'Confirm';
  @Input() message = 'Are you sure?';
  @Input() confirmLabel = 'Confirm';
  @Input() cancelLabel = 'Cancel';
  @Input() confirmDanger = false;
  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialogCard') private dialogCard?: ElementRef<HTMLElement>;
  @ViewChild('cancelButton') private cancelButton?: ElementRef<HTMLButtonElement>;

  private previouslyFocused: HTMLElement | null = null;
  private previousBodyOverflow = '';

  ngAfterViewInit(): void {
    this.previouslyFocused = document.activeElement as HTMLElement | null;
    this.previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    this.cancelButton?.nativeElement.focus();
  }

  ngOnDestroy(): void {
    document.body.style.overflow = this.previousBodyOverflow;
    this.previouslyFocused?.focus();
  }

  @HostListener('document:keydown', ['$event'])
  handleKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.cancel();
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

  cancel(): void { this.cancelled.emit(); }
  confirm(): void { this.confirmed.emit(); }
}
