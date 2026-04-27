import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * FIX #10 — Shared loading / error state wrapper.
 *
 * Usage in any page template:
 *
 *   <app-api-state
 *     [loading]="isLoading"
 *     [error]="errorMessage"
 *     (retry)="loadData()">
 *     <!-- page content goes here — only shown when not loading and no error -->
 *     <div>...</div>
 *   </app-api-state>
 *
 * This ensures every page that uses this component gets a consistent
 * loading spinner and error banner instead of blank pages.
 */
@Component({
  selector: 'app-api-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <!-- Loading spinner -->
    <div *ngIf="loading" class="api-state-loading">
      <div class="spinner"></div>
      <p class="loading-text">{{ loadingText }}</p>
    </div>

    <!-- Error banner -->
    <div *ngIf="!loading && error" class="api-state-error">
      <div class="error-icon">⚠️</div>
      <p class="error-message">{{ error }}</p>
      <button *ngIf="showRetry" class="retry-btn" (click)="retry.emit()">
        Try Again
      </button>
    </div>

    <!-- Content slot — rendered only when idle and no error -->
    <ng-container *ngIf="!loading && !error">
      <ng-content></ng-content>
    </ng-container>
  `,
  styles: [`
    .api-state-loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 3rem 1rem;
      gap: 1rem;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid rgba(196, 160, 90, 0.2);
      border-top-color: #c4a05a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .loading-text {
      color: #9a8060;
      font-size: 0.9rem;
      margin: 0;
    }

    .api-state-error {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 2.5rem 1.5rem;
      gap: 0.75rem;
      background: rgba(220, 53, 69, 0.06);
      border: 1px solid rgba(220, 53, 69, 0.25);
      border-radius: 12px;
      margin: 1rem;
    }

    .error-icon {
      font-size: 2rem;
    }

    .error-message {
      color: #c0392b;
      font-size: 0.95rem;
      text-align: center;
      margin: 0;
      max-width: 400px;
    }

    .retry-btn {
      margin-top: 0.5rem;
      padding: 0.5rem 1.5rem;
      border: none;
      border-radius: 8px;
      background: #c4a05a;
      color: #1a1008;
      font-weight: 600;
      font-size: 0.9rem;
      cursor: pointer;
      transition: opacity 0.2s;
    }

    .retry-btn:hover {
      opacity: 0.85;
    }
  `]
})
export class ApiStateComponent {
  @Input() loading = false;
  @Input() error: string | null = null;
  @Input() loadingText = 'Loading…';
  @Input() showRetry = true;
  @Output() retry = new EventEmitter<void>();
}
