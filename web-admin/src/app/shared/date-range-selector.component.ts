import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export type DatePreset = 'today' | 'yesterday' | 'last-7-days' | 'this-month' | 'custom';

export function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function getPresetDateRange(
  preset: Exclude<DatePreset, 'custom'>,
  today = new Date()
): { from: string; to: string } {
  switch (preset) {
    case 'yesterday': {
      const y = new Date(today);
      y.setDate(today.getDate() - 1);
      return { from: formatLocalDate(y), to: formatLocalDate(y) };
    }
    case 'last-7-days': {
      const d7 = new Date(today);
      d7.setDate(today.getDate() - 6);
      return { from: formatLocalDate(d7), to: formatLocalDate(today) };
    }
    case 'this-month': {
      const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
      return { from: formatLocalDate(firstDay), to: formatLocalDate(today) };
    }
    default:
      return { from: formatLocalDate(today), to: formatLocalDate(today) };
  }
}

@Component({
  selector: 'app-date-range-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="date-range" [class.date-range--single]="mode === 'single'">
      <label class="date-range__field">
        <span class="visually-hidden">{{ mode === 'single' ? singleDateLabel : 'Start date' }}</span>
        <input
          type="date"
          class="date-range__input"
          [ngModel]="customFrom()"
          (ngModelChange)="onFromChange($event)"
          [max]="mode === 'single' ? (maxDate || undefined) : (customTo() || undefined)"
          [attr.aria-label]="mode === 'single' ? singleDateLabel : 'Start date'" />
      </label>

      <ng-container *ngIf="mode !== 'single'">
        <span class="date-range__separator" aria-hidden="true">to</span>
        <label class="date-range__field">
          <span class="visually-hidden">End date</span>
          <input
            type="date"
            class="date-range__input"
            [ngModel]="customTo()"
            (ngModelChange)="onToChange($event)"
            [min]="customFrom() || undefined"
            aria-label="End date" />
        </label>
      </ng-container>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .date-range {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      white-space: nowrap;
    }
    .date-range__field { display: block; margin: 0; }
    .date-range__input {
      box-sizing: border-box;
      width: 142px;
      min-height: 36px;
      padding: 0.4rem 0.55rem;
      border: 1px solid #cfd3da;
      border-radius: 5px;
      background: #f7f8fa;
      color: #343b46;
      font: 500 0.78rem/1.2 var(--kb-font-sans, Arial, sans-serif);
      font-variant-numeric: tabular-nums;
      cursor: text;
    }
    .date-range__input:hover { border-color: #aeb6c2; background: #fff; }
    .date-range__input:focus-visible {
      outline: 2px solid rgba(24, 126, 245, 0.28);
      outline-offset: 1px;
      border-color: #1686f5;
      background: #fff;
    }
    .date-range__input::-webkit-calendar-picker-indicator {
      opacity: 0.72;
      cursor: pointer;
    }
    .date-range__separator { color: #737b87; font-size: 0.78rem; }
    @media (max-width: 680px) {
      .date-range { flex-wrap: wrap; white-space: normal; }
      .date-range__input { width: 138px; }
      .date-range--single .date-range__input { width: 100%; }
    }
  `]
})
export class DateRangeSelectorComponent {
  @Output() rangeChanged = new EventEmitter<{ from: string; to: string }>();

  @Input() mode: 'range' | 'single' = 'range';
  @Input() maxDate?: string;
  @Input() singleDateLabel = 'Select date';

  @Input()
  set initialRange(value: { from: string; to: string } | null) {
    if (!value?.from || !value.to) return;
    if (this.lastEmitted && value.from === this.lastEmitted.from && value.to === this.lastEmitted.to) return;
    this.customFrom.set(value.from);
    this.customTo.set(value.to);
  }

  customFrom = signal<string>('');
  customTo = signal<string>('');
  private lastEmitted: { from: string; to: string } | null = null;

  onFromChange(value: string): void {
    this.customFrom.set(value);
    if (this.mode === 'single') {
      this.customTo.set(value);
      if (value) this.emit({ from: value, to: value });
      return;
    }
    this.emitWhenComplete();
  }

  onToChange(value: string): void {
    this.customTo.set(value);
    this.emitWhenComplete();
  }

  private emitWhenComplete(): void {
    const from = this.customFrom();
    const to = this.customTo();
    if (from && to && from <= to) this.emit({ from, to });
  }

  private emit(range: { from: string; to: string }): void {
    this.lastEmitted = { ...range };
    this.rangeChanged.emit(range);
  }
}
