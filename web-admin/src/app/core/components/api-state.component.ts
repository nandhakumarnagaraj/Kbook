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
    <div *ngIf="loading" class="api-state-loading" role="status" aria-live="polite" aria-busy="true">
      <div class="spinner" aria-hidden="true"></div>
      <p class="loading-text">{{ loadingText }}</p>
    </div>

    <section *ngIf="!loading && error" class="api-state-error" role="alert" aria-live="assertive">
      <div class="error-icon" aria-hidden="true">!</div>
      <div>
        <h3>We couldn't load this content</h3>
        <p class="error-message">{{ error }}</p>
      </div>
      <button *ngIf="showRetry" type="button" class="retry-btn" (click)="retry.emit()">
        Try again
      </button>
    </section>

    <ng-container *ngIf="!loading && !error">
      <ng-content></ng-content>
    </ng-container>
  `,
  styles: [`
    .api-state-loading {
      display: grid;
      justify-items: center;
      gap: 0.8rem;
      padding: 3rem 1rem;
    }
    .spinner {
      width: 36px;
      height: 36px;
      border: 3px solid var(--brand-soft);
      border-top-color: var(--brand);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .loading-text { margin: 0; color: var(--muted); }
    .api-state-error {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 1rem;
      margin: 1rem;
      padding: 1rem 1.1rem;
      color: var(--danger);
      background: var(--danger-soft);
      border: 1px solid rgba(166, 55, 47, 0.22);
      border-radius: 14px;
    }
    .error-icon {
      display: grid;
      place-items: center;
      width: 2.25rem;
      height: 2.25rem;
      color: #fff;
      background: var(--danger);
      border-radius: 999px;
      font-weight: 800;
    }
    h3 { margin: 0 0 0.2rem; color: var(--ink); font-size: 0.95rem; }
    .error-message { margin: 0; color: var(--danger); line-height: 1.4; }
    .retry-btn {
      min-height: 42px;
      padding: 0.55rem 1rem;
      color: #fff;
      background: var(--danger);
      border: 0;
      border-radius: 10px;
      cursor: pointer;
      font-weight: 700;
    }
    .retry-btn:focus-visible { outline: 3px solid rgba(166, 55, 47, 0.24); outline-offset: 2px; }
    @media (max-width: 640px) {
      .api-state-error { grid-template-columns: auto minmax(0, 1fr); }
      .retry-btn { grid-column: 1 / -1; width: 100%; }
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
