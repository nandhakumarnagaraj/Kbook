import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../core/services/toast.service';

@Component({
  selector: 'app-global-toast',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <aside
      class="global-toast"
      *ngIf="toast.message() as msg"
      [class.global-toast--error]="msg.type === 'error'"
      [class.global-toast--success]="msg.type === 'success'"
      [attr.role]="msg.type === 'error' ? 'alert' : 'status'"
      [attr.aria-live]="msg.type === 'error' ? 'assertive' : 'polite'"
      aria-atomic="true"
    >
      <span class="global-toast__mark" aria-hidden="true">
        {{ msg.type === 'error' ? '!' : (msg.type === 'success' ? '✓' : 'i') }}
      </span>
      <span class="global-toast__message">{{ msg.text }}</span>
      <button
        type="button"
        class="global-toast__close"
        aria-label="Dismiss notification"
        (click)="toast.dismiss()"
      >×</button>
    </aside>
  `,
  styles: [`
    .global-toast {
      position: fixed;
      right: 1.5rem;
      bottom: 1.5rem;
      z-index: var(--kb-z-toast);
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: var(--kb-space-3);
      width: min(380px, calc(100vw - 2rem));
      padding: 0.8rem 0.9rem;
      /* This used --ink as its background: a near-white fill with white text,
         i.e. 1.05:1. A toast is the topmost plane, so it takes surface-3 and
         signals its type with the glyph + a matching mark tint, never colour
         alone. */
      color: var(--kb-color-foreground);
      background: var(--kb-color-surface-3);
      border: 1px solid var(--kb-color-border-strong);
      border-radius: 14px;
      box-shadow: var(--shadow-lg);
    }
    .global-toast--error { border-color: rgba(var(--kb-color-danger-rgb), 0.55); }
    .global-toast--success { border-color: rgba(var(--kb-color-success-rgb), 0.55); }
    .global-toast--error .global-toast__mark {
      background: var(--kb-color-danger-soft);
      color: var(--kb-color-danger);
    }
    .global-toast--success .global-toast__mark {
      background: var(--kb-color-success-soft);
      color: var(--kb-color-success);
    }
    .global-toast__mark {
      display: grid;
      place-items: center;
      width: 1.75rem;
      height: 1.75rem;
      border-radius: var(--kb-radius-full);
      background: var(--kb-color-surface-2);
      color: var(--kb-color-foreground-2);
      font-weight: 800;
    }
    .global-toast__message { line-height: 1.4; }
    .global-toast__close {
      display: grid;
      place-items: center;
      width: 2rem;
      height: 2rem;
      padding: 0;
      color: inherit;
      background: transparent;
      border: 0;
      border-radius: var(--kb-radius-lg);
      cursor: pointer;
      font-size: var(--kb-text-xl);
    }
    /* The hover wash sits on the toast surface, not on a dark overlay: a white
       12% tint on a white toast is invisible. */
    .global-toast__close:hover,
    .global-toast__close:focus-visible { background: var(--kb-color-surface-2); }
    .global-toast__close:focus-visible { outline: 2px solid var(--kb-color-on-primary); outline-offset: 2px; }
    @media (max-width: 480px) {
      .global-toast { right: 1rem; bottom: 1rem; }
    }
  `]
})
export class GlobalToastComponent {
  readonly toast = inject(ToastService);
}
