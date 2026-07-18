import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../core/services/toast.service';

@Component({
  selector: 'app-global-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="global-toast"
      *ngIf="toast.message() as msg"
      [class.global-toast--error]="msg.type === 'error'"
      [class.global-toast--success]="msg.type === 'success'"
      (click)="toast.dismiss()"
    >
      {{ msg.text }}
    </div>
  `,
  styles: [`
    .global-toast {
      position: fixed;
      bottom: 1.5rem;
      right: 1.5rem;
      background: #24170f;
      color: #fff;
      padding: 0.85rem 1.25rem;
      border-radius: 12px;
      box-shadow: 0 12px 30px rgba(0, 0, 0, 0.25);
      z-index: 9999;
      max-width: 360px;
      cursor: pointer;
      font-size: 0.9rem;
    }
    .global-toast--error {
      background: #5c1a1a;
      border: 1px solid #a6372f;
    }
    .global-toast--success {
      background: #1a3d2a;
      border: 1px solid #1d7b5f;
    }
  `]
})
export class GlobalToastComponent {
  readonly toast = inject(ToastService);
}
