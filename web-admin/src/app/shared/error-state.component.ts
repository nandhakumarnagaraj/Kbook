import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-error-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <div class="error-state">
      <span class="error-icon">⚠️</span>
      <h3 class="error-title">{{ title }}</h3>
      <p class="error-description" *ngIf="description">{{ description }}</p>
      <button class="primary-btn" *ngIf="retryable" (click)="retry.emit()">Retry</button>
    </div>
  `,
  styles: [`
    .error-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 24px; text-align: center; gap: 12px; }
    .error-icon { font-size: 2.5rem; }
    .error-title { font-size: 1.1rem; font-weight: 700; color: var(--ink); margin: 0; }
    .error-description { font-size: 0.85rem; color: var(--muted); margin: 0; max-width: 400px; }
    .primary-btn { background: var(--brand); color: #fff; border: none; border-radius: 8px; padding: 10px 20px; font-weight: 600; cursor: pointer; }
    .primary-btn:hover { opacity: 0.9; }
  `]
})
export class ErrorStateComponent {
  @Input() title = 'Something went wrong';
  @Input() description = '';
  @Input() retryable = true;
  @Output() retry = new EventEmitter<void>();
}
