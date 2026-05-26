import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  template: `
    <div class="empty-state" [class.compact]="compact">
      <mat-icon class="empty-icon" aria-hidden="true">{{ icon }}</mat-icon>
      <h3 class="empty-title">{{ title }}</h3>
      <p class="empty-description" *ngIf="description">{{ description }}</p>
      <ng-content></ng-content>
    </div>
  `,
  styles: [`
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 64px 24px;
      text-align: center;
    }
    .empty-state.compact {
      padding: 32px 16px;
    }
    .empty-icon {
      font-size: 56px;
      width: 56px;
      height: 56px;
      margin-bottom: 16px;
      opacity: 0.25;
      color: var(--muted);
    }
    .empty-title {
      margin: 0 0 8px;
      font-size: 1.15rem;
      font-weight: 700;
      color: var(--ink-secondary);
    }
    .empty-description {
      margin: 0;
      font-size: 0.85rem;
      color: var(--muted);
      max-width: 360px;
      line-height: 1.5;
    }
  `]
})
export class EmptyStateComponent {
  @Input() icon = 'inbox';
  @Input() title = 'No data found';
  @Input() description = '';
  @Input() compact = false;
}
