import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="empty-state">
      <span class="empty-icon" *ngIf="icon" aria-hidden="true">{{ icon }}</span>
      <p class="empty-title">{{ title }}</p>
      <p class="empty-text" *ngIf="text">{{ text }}</p>
      <div class="empty-action" *ngIf="actionLabel">
        <button class="primary-btn" (click)="action.emit()">{{ actionLabel }}</button>
      </div>
    </div>
  `
})
export class EmptyStateComponent {
  @Input() icon = '';
  @Input() title = '';
  @Input() text = '';
  @Input() actionLabel = '';
  @Output() action = new EventEmitter<void>();
}
