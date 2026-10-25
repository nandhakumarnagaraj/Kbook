import { Directive, ElementRef, EventEmitter, HostListener, inject, OnDestroy, OnInit, Output } from '@angular/core';

/**
 * FocusTrapDirective — keeps keyboard focus inside a modal while it is open.
 *
 * Usage:
 *   <div class="modal-overlay" *ngIf="open" appFocusTrap (escape)="close()">
 *     <section class="modal-box" tabindex="-1">
 *       <button #close type="button">✕</button>
 *     </section>
 *   </div>
 *
 * On open: saves the previously focused element, traps Tab/Escape inside the
 * element, focuses the first focusable child (or the host itself).
 * On close/destroy: restores focus and body overflow.
 */
@Directive({
  selector: '[appFocusTrap]',
  standalone: true,
})
export class FocusTrapDirective implements OnInit, OnDestroy {
  private readonly el = inject(ElementRef);
  private previouslyFocused: HTMLElement | null = null;
  private previousBodyOverflow = '';
  private trapActive = false;

  @Output() escape = new EventEmitter<void>();

  ngOnInit(): void {
    this.trapActive = true;
    this.previouslyFocused = document.activeElement as HTMLElement | null;
    this.previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    setTimeout(() => this.focusFirstFocusable(), 0);
  }

  ngOnDestroy(): void {
    this.restorePage();
  }

  @HostListener('document:keydown', ['$event'])
  handleKeydown(event: KeyboardEvent): void {
    if (!this.trapActive) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.escape.emit();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusable = this.el.nativeElement.querySelectorAll(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    ) as NodeListOf<HTMLElement>;
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

  private focusFirstFocusable(): void {
    const focusable = this.el.nativeElement.querySelectorAll(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    ) as NodeListOf<HTMLElement>;
    (focusable?.[0] ?? this.el.nativeElement as HTMLElement).focus();
  }

  restorePage(): void {
    this.trapActive = false;
    document.body.style.overflow = this.previousBodyOverflow;
    if (this.previouslyFocused) {
      this.previouslyFocused.focus();
      this.previouslyFocused = null;
    }
  }
}
