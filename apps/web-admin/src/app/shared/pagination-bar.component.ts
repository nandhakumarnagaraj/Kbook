import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/**
 * Shared pagination bar. Pages are one-based. The parent owns the page state
 * and listens to (pageChange) to update it.
 */
@Component({
  selector: 'app-pagination-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <nav class="pagination-bar" aria-label="Pagination">
      <p class="pagination-range tabular-num" aria-live="polite">
        <ng-container *ngIf="totalItems() > 0; else noItems">Showing {{ start() }}–{{ end() }} of {{ totalItems() }} items</ng-container>
        <ng-template #noItems>Showing 0 of 0 items</ng-template>
      </p>
      <div class="pagination-controls">
        <button
          type="button"
          class="pagination-nav"
          aria-label="Previous page"
          [disabled]="currentPage() <= 1"
          (click)="goTo(currentPage() - 1)"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M15 18l-6-6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
        <ng-container *ngFor="let item of pages()">
          <span class="pagination-ellipsis" *ngIf="item === null" aria-hidden="true">…</span>
          <button
            *ngIf="item !== null"
            type="button"
            class="pagination-page"
            [class.pagination-page--active]="item === currentPage()"
            [attr.aria-current]="item === currentPage() ? 'page' : null"
            [attr.aria-label]="'Page ' + item"
            (click)="goTo(item)"
          >{{ item }}</button>
        </ng-container>
        <button
          type="button"
          class="pagination-nav"
          aria-label="Next page"
          [disabled]="currentPage() >= totalPages()"
          (click)="goTo(currentPage() + 1)"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M9 18l6-6-6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
      </div>
    </nav>
  `,
  styles: [`
    :host { display: block; }
    .pagination-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 0.75rem 1rem;
      padding: 0.85rem 1.15rem;
      border-top: 1px solid var(--line);
      background: var(--panel);
      border-radius: 0 0 var(--r-xl) var(--r-xl);
    }
    .pagination-range {
      margin: 0;
      font-size: 0.82rem;
      color: var(--muted);
    }
    .pagination-controls {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 0.375rem;
    }
    .pagination-nav,
    .pagination-page {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 2rem;
      height: 2rem;
      padding: 0 0.5rem;
      border: 1px solid var(--line-strong, var(--line));
      border-radius: 0.375rem;
      background: #FFFFFF;
      color: var(--kb-color-foreground, #111827);
      font: inherit;
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.15s ease, border-color 0.15s ease;
    }
    .pagination-nav:hover:not(:disabled),
    .pagination-page:hover:not(.pagination-page--active) {
      background: var(--kb-color-surface-2, #F1F5F9);
    }
    .pagination-nav:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
    .pagination-page--active {
      background: #FF851B;
      border-color: #FF851B;
      color: #FFFFFF;
    }
    .pagination-nav:focus-visible,
    .pagination-page:focus-visible {
      outline: 2px solid var(--brand-focus-ring, #4F46E5);
      outline-offset: 2px;
    }
    .pagination-ellipsis {
      min-width: 1.5rem;
      text-align: center;
      color: var(--muted);
      font-size: 0.82rem;
    }
    @media (max-width: 520px) {
      .pagination-bar { justify-content: center; text-align: center; }
    }
  `],
})
export class PaginationBarComponent {
  readonly currentPage = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly totalItems = input.required<number>();

  readonly pageChange = output<number>();

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalItems() / this.pageSize())));

  readonly start = computed(() =>
    this.totalItems() === 0 ? 0 : (this.currentPage() - 1) * this.pageSize() + 1,
  );

  readonly end = computed(() =>
    Math.min(this.currentPage() * this.pageSize(), this.totalItems()),
  );

  /** Page numbers to show, with null marking an ellipsis gap. */
  readonly pages = computed<(number | null)[]>(() => {
    const total = this.totalPages();
    const current = this.currentPage();
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i + 1);
    }
    const result: (number | null)[] = [1];
    const from = Math.max(2, current - 1);
    const to = Math.min(total - 1, current + 1);
    if (from > 2) result.push(null);
    for (let p = from; p <= to; p++) result.push(p);
    if (to < total - 1) result.push(null);
    result.push(total);
    return result;
  });

  goTo(page: number): void {
    const clamped = Math.min(Math.max(1, page), this.totalPages());
    if (clamped !== this.currentPage()) {
      this.pageChange.emit(clamped);
    }
  }
}
