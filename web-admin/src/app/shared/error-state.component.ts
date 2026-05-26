import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-error-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  template: `
    <div class="error-state" [class.compact]="compact">
      <mat-icon class="error-icon" aria-hidden="true">{{ icon }}</mat-icon>
      <h3 class="error-title">{{ title }}</h3>
      <p class="error-description" *ngIf="description">{{ description }}</p>
      <p class="error-detail" *ngIf="detail">{{ detail }}</p>
      <ng-content></ng-content>
      <button mat-flat-button color="primary" *ngIf="retryable" (click)="retry.emit()">
        <mat-icon>refresh</mat-icon>
        {{ retryLabel }}
      </button>
    </div>
  `,
  styles: [`
    .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 64px 24px;
      text-align: center;
    }
    .error-state.compact {
      padding: 32px 16px;
    }
    .error-icon {
      font-size: 56px;
      width: 56px;
      height: 56px;
      margin-bottom: 16px;
      opacity: 0.5;
      color: var(--danger);
    }
    .error-title {
      margin: 0 0 8px;
      font-size: 1.15rem;
      font-weight: 700;
      color: var(--ink);
    }
    .error-description {
      margin: 0;
      font-size: 0.85rem;
      color: var(--ink-secondary);
      max-width: 400px;
      line-height: 1.5;
    }
    .error-detail {
      margin: 8px 0 0;
      font-size: 0.75rem;
      color: var(--muted);
      max-width: 480px;
      line-height: 1.4;
      font-family: monospace;
      padding: 8px 12px;
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: var(--radius-sm);
      word-break: break-all;
    }
    button {
      margin-top: 20px;
    }
  `]
})
export class ErrorStateComponent {
  @Input() icon = 'error_outline';
  @Input() title = 'Something went wrong';
  @Input() description = 'An unexpected error occurred. Please try again.';
  @Input() detail = '';
  @Input() compact = false;
  @Input() retryable = true;
  @Input() retryLabel = 'Try Again';
  @Output() retry = new EventEmitter<void>();
}
