import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewChild
} from '@angular/core';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <dialog
      #dialogEl
      class="confirm-dialog"
      aria-labelledby="confirm-dialog-title"
      aria-describedby="confirm-dialog-message"
      (cancel)="onCancel($event)"
      (pointerdown)="onPointerDown($event)"
      (pointerup)="onPointerUp($event)"
    >
      <div #dialogCard class="confirm-dialog__card">
        <div class="confirm-dialog__icon" [class.confirm-dialog__icon--danger]="confirmDanger" aria-hidden="true">
          {{ confirmDanger ? '!' : '?' }}
        </div>
        <div class="confirm-dialog__content">
          <h3 id="confirm-dialog-title" class="confirm-dialog__title">{{ title }}</h3>
          <p id="confirm-dialog-message" class="confirm-dialog__message">{{ message }}</p>
        </div>
        <form method="dialog" class="confirm-dialog__actions">
          <button
            #cancelButton
            type="button"
            class="confirm-dialog__btn confirm-dialog__btn--cancel"
            autofocus
            (click)="cancel()"
          >
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
        </form>
      </div>
    </dialog>
  `,
  styles: [`
    /* Modern Web Guidance (dialog): native top-layer modal with overscroll containment */
    dialog.confirm-dialog {
      padding: 0;
      border: none;
      background: transparent;
      color: inherit;
      max-width: min(460px, calc(100vw - var(--kb-space-4) * 2));
      max-height: calc(100vh - var(--kb-space-4) * 2);
      overflow: visible;
      margin: auto;
    }
    dialog.confirm-dialog:modal {
      overscroll-behavior: contain;
    }
    dialog.confirm-dialog::backdrop {
      overflow: hidden;
      overscroll-behavior: contain;
      background: rgba(36, 23, 15, 0.56);
      backdrop-filter: blur(3px);
    }
    .confirm-dialog__card {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      gap: var(--kb-space-4);
      width: min(460px, 100%);
      padding: var(--kb-space-6);
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: var(--kb-radius-2xl);
      box-shadow: 0 24px 64px rgba(36, 23, 15, 0.24);
      box-sizing: border-box;
    }
    .confirm-dialog__card:focus { outline: none; }
    .confirm-dialog__icon {
      display: grid;
      place-items: center;
      width: 2.5rem;
      height: 2.5rem;
      color: var(--brand-deep);
      background: var(--brand-soft);
      border-radius: var(--kb-radius-xl);
      font-size: var(--kb-text-lg);
      font-weight: 800;
    }
    .confirm-dialog__icon--danger { color: var(--danger); background: var(--danger-soft); }
    .confirm-dialog__content {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .confirm-dialog__title { margin: 0 0 var(--kb-space-2); font-size: var(--kb-text-xl); color: var(--ink); }
    .confirm-dialog__message { margin: 0; color: var(--muted); line-height: 1.55; }
    .confirm-dialog__actions {
      grid-column: 1 / -1;
      display: flex;
      justify-content: flex-end;
      gap: var(--kb-space-3);
      margin-top: var(--kb-space-2);
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
    .confirm-dialog__btn--cancel { color: var(--ink); background: var(--kb-color-muted); border: 1px solid var(--line); }
    .confirm-dialog__btn--confirm { color: var(--kb-color-on-primary); background: var(--kb-color-primary-strong); }
    /* Solid fill + white content: --danger is the ink tone and only reaches
       2.7:1 behind white, so a destructive fill uses the solid token. */
    .confirm-dialog__btn--danger { background: var(--kb-color-danger-solid); }
    .confirm-dialog__btn--danger:hover { background: var(--kb-color-danger-solid-hover); }
    @media (max-width: 480px) {
      dialog.confirm-dialog {
        margin: auto auto 0 auto;
        width: 100%;
        max-width: 100%;
      }
      .confirm-dialog__card { width: 100%; padding: var(--kb-space-5); border-radius: 18px 18px 0 0; }
      .confirm-dialog__actions { flex-direction: column-reverse; }
      .confirm-dialog__btn { width: 100%; }
    }
  `]
})
export class ConfirmDialogComponent implements AfterViewInit {
  @Input() title = 'Confirm';
  @Input() message = 'Are you sure?';
  @Input() confirmLabel = 'Confirm';
  @Input() cancelLabel = 'Cancel';
  @Input() confirmDanger = false;
  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
  @ViewChild('dialogEl') private dialogEl?: ElementRef<HTMLDialogElement>;
  @ViewChild('cancelButton') private cancelButton?: ElementRef<HTMLButtonElement>;

  private pressedOutside = false;

  ngAfterViewInit(): void {
    const dialog = this.dialogEl?.nativeElement;
    if (dialog) {
      if (typeof dialog.showModal === 'function') {
        if (!dialog.open) {
          dialog.showModal();
        }
      } else {
        dialog.setAttribute('open', '');
      }
    }
    this.cancelButton?.nativeElement.focus();
  }

  onCancel(event: Event): void {
    event.preventDefault();
    this.cancel();
  }

  /**
   * Modern Web Guidance light-dismiss fallback:
   * Require pointer press and release outside the dialog rectangle to prevent
   * accidental dismiss from drag selections.
   */
  onPointerDown(e: PointerEvent): void {
    const dialog = this.dialogEl?.nativeElement;
    if (!dialog) return;
    const r = dialog.getBoundingClientRect();
    this.pressedOutside = e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
  }

  onPointerUp(e: PointerEvent): void {
    const dialog = this.dialogEl?.nativeElement;
    if (!dialog) return;
    const r = dialog.getBoundingClientRect();
    const releasedOutside = e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
    if (this.pressedOutside && releasedOutside) {
      this.cancel();
    }
    this.pressedOutside = false;
  }

  cancel(): void {
    const dialog = this.dialogEl?.nativeElement;
    if (dialog && typeof dialog.close === 'function' && dialog.open) {
      dialog.close();
    }
    this.cancelled.emit();
  }

  confirm(): void {
    const dialog = this.dialogEl?.nativeElement;
    if (dialog && typeof dialog.close === 'function' && dialog.open) {
      dialog.close();
    }
    this.confirmed.emit();
  }
}
