import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { DateRangeSelectorComponent } from './date-range-selector.component';
import { RefreshButtonComponent } from './refresh-button.component';

/** Shared date and page-action strip used by date-filtered business pages. */
@Component({
  selector: 'app-date-range-toolbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, DateRangeSelectorComponent, RefreshButtonComponent],
  template: `
    <div class="date-toolbar">
      <app-date-range-selector
        *ngIf="showDateSelector"
        [mode]="mode"
        [singleDateLabel]="singleDateLabel"
        [maxDate]="maxDate"
        [initialRange]="initialRange"
        (rangeChanged)="rangeChanged.emit($event)">
      </app-date-range-selector>
      <button
        *ngIf="showDownload"
        type="button"
        class="date-toolbar__button date-toolbar__button--download"
        [disabled]="downloadDisabled"
        (click)="download.emit()"
        [attr.aria-label]="downloadLabel"
        [title]="downloadLabel">
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <path d="m7 10 5 5 5-5" />
          <path d="M12 15V3" />
        </svg>
      </button>
      <app-refresh-button
        [disabled]="refreshDisabled"
        [loading]="refreshing"
        (refresh)="refresh.emit()"
        ariaLabel="Refresh"
      />
      <button
        *ngIf="showPrint"
        type="button"
        class="date-toolbar__button date-toolbar__button--secondary"
        [disabled]="printDisabled"
        (click)="print.emit()"
        aria-label="Print closing slip"
        title="Print Slip">
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <polyline points="6 9 6 2 18 2 18 9" />
          <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2" />
          <rect x="6" y="14" width="12" height="8" />
        </svg>
      </button>
    </div>
  `,
  styles: [`
    .date-toolbar {
      display: flex;
      align-items: center;
      justify-content: flex-start;
      gap: 8px;
      flex-wrap: nowrap;
    }
    app-date-range-selector { flex: 0 0 auto; width: auto; }
    .date-toolbar__button {
      display: grid;
      place-items: center;
      flex: 0 0 38px;
      width: 38px;
      height: 38px;
      padding: 0;
      border: 1px solid #1686f5;
      border-radius: 5px;
      cursor: pointer;
      transition: background 150ms ease, border-color 150ms ease, color 150ms ease;
    }
    .date-toolbar__button--download { color: #fff; background: #1686f5; }
    .date-toolbar__button--download:hover:not(:disabled) { background: #0872dc; border-color: #0872dc; }
    .date-toolbar__button--refresh,
    .date-toolbar__button--secondary { color: #1686f5; background: #fff; }
    .date-toolbar__button--refresh:hover:not(:disabled),
    .date-toolbar__button--secondary:hover:not(:disabled) { background: #edf6ff; }
    /* Angular scopes this rule with its own attribute, which outranks the shared
       layer's generic disabled rule in styles.css — so a component that paints
       its own pointer cursor also states the disabled counterpart. Without this
       line the button kept a pointer cursor while disabled. */
    .date-toolbar__button:disabled { opacity: 0.55; cursor: not-allowed; }
    .date-toolbar__button:focus-visible { outline: 2px solid #1686f5; outline-offset: 2px; }
    @media (max-width: 680px) {
      .date-toolbar { justify-content: flex-start; flex-wrap: wrap; }
    }
  `]
})
export class DateRangeToolbarComponent {
  @Input() initialRange: { from: string; to: string } | null = null;
  @Input() mode: 'single' | 'range' = 'range';
  @Input() singleDateLabel = 'Date';
  @Input() maxDate?: string;
  @Input() showDateSelector = true;
  @Input() showDownload = true;
  @Input() downloadDisabled = false;
  @Input() downloadLabel = 'Download CSV';
  @Input() refreshDisabled = false;
  @Input() refreshing = false;
  @Input() showPrint = false;
  @Input() printDisabled = false;

  @Output() rangeChanged = new EventEmitter<{ from: string; to: string }>();
  @Output() download = new EventEmitter<void>();
  @Output() refresh = new EventEmitter<void>();
  @Output() print = new EventEmitter<void>();
}
