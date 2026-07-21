import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ToastService } from '../core/services/toast.service';

@Component({
  selector: 'app-global-toast',
  standalone: true,
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
      z-index: 9999;
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.75rem;
      width: min(380px, calc(100vw - 2rem));
      padding: 0.8rem 0.9rem;
      color: #fff;
      background: var(--ink);
      border: 1px solid rgba(255, 255, 255, 0.14);
      border-radius: 14px;
      box-shadow: 0 16px 36px rgba(36, 23, 15, 0.28);
    }
    .global-toast--error { background: #5c1a1a; border-color: var(--danger); }
    .global-toast--success { background: #153f2d; border-color: var(--accent); }
    .global-toast__mark {
      display: grid;
      place-items: center;
      width: 1.75rem;
      height: 1.75rem;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.14);
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
      border-radius: 8px;
      cursor: pointer;
      font-size: 1.25rem;
    }
    .global-toast__close:hover,
    .global-toast__close:focus-visible { background: rgba(255, 255, 255, 0.12); }
    .global-toast__close:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
    @media (max-width: 480px) {
      .global-toast { right: 1rem; bottom: 1rem; }
    }
  `]
})
export class GlobalToastComponent {
  readonly toast = inject(ToastService);
}
