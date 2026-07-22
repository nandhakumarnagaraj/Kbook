import { Component, Input, Output, EventEmitter, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export type DatePreset = 'today' | 'this-week' | 'this-month' | 'custom';

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
    case 'this-week': {
      const monday = new Date(today);
      monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));
      return { from: formatLocalDate(monday), to: formatLocalDate(today) };
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
  imports: [CommonModule, FormsModule],
  template: `
    <div class="date-range">
      <div class="date-range__presets">
        <button
          *ngFor="let preset of presets"
          class="date-range__preset-btn"
          [class.date-range__preset-btn--active]="activePreset() === preset.value"
          (click)="selectPreset(preset.value)">
          {{ preset.label }}
        </button>
      </div>

      <div class="date-range__custom" *ngIf="activePreset() === 'custom'">
        <input
          type="date"
          class="date-range__input"
          [ngModel]="customFrom()"
          (ngModelChange)="onCustomFromChange($event)"
          [max]="customTo() || undefined"
          aria-label="Start date" />
        <span class="date-range__separator">to</span>
        <input
          type="date"
          class="date-range__input"
          [ngModel]="customTo()"
          (ngModelChange)="onCustomToChange($event)"
          [min]="customFrom() || undefined"
          aria-label="End date" />
      </div>

      <div class="date-range__label" *ngIf="rangeLabel()">
        {{ rangeLabel() }}
      </div>
    </div>
  `,
  styles: [`
    .date-range {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem;
    }

    .date-range__presets {
      display: grid;
      grid-template-columns: repeat(4, minmax(max-content, 1fr));
      gap: 0.5rem;
    }

    .date-range__preset-btn {
      min-height: 42px;
      min-width: 5.5rem;
      padding: 0.55rem 0.9rem;
      border-radius: 8px;
      border: 1px solid var(--line);
      background: var(--panel);
      color: var(--ink);
      font-size: 0.85rem;
      font-weight: 600;
      white-space: nowrap;
      cursor: pointer;
      transition: background 0.2s, border-color 0.2s, box-shadow 0.2s;
    }

    .date-range__preset-btn:hover {
      border-color: var(--brand);
    }

    .date-range__preset-btn--active {
      background: var(--brand);
      color: #fff;
      border-color: var(--brand);
    }

    .date-range__custom {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .date-range__input {
      padding: 0.4rem 0.6rem;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      color: var(--ink);
      font-size: 0.85rem;
      min-height: 36px;
    }

    .date-range__input:focus {
      outline: none;
      border-color: var(--brand);
      box-shadow: 0 0 0 2px rgba(232, 122, 30, 0.15);
    }

    .date-range__separator {
      color: var(--muted);
      font-size: 0.85rem;
    }

    .date-range__label {
      font-size: 0.8rem;
      color: var(--muted);
      background: var(--bg);
      padding: 0.25rem 0.6rem;
      border-radius: 6px;
    }

    @media (max-width: 480px) {
      .date-range {
        flex-direction: column;
        align-items: stretch;
      }

      .date-range__presets {
        display: grid;
        grid-template-columns: 1fr 1fr;
      }

      .date-range__preset-btn {
        width: 100%;
        text-align: center;
      }

      .date-range__custom {
        flex-direction: column;
        align-items: stretch;
      }

      .date-range__input {
        width: 100%;
      }
    }
  `]
})
export class DateRangeSelectorComponent {
  @Output() rangeChanged = new EventEmitter<{ from: string; to: string }>();

  @Input()
  set initialRange(value: { from: string; to: string } | null) {
    if (!value) return;
    this.customFrom.set(value.from);
    this.customTo.set(value.to);
    this.activePreset.set('custom');
  }

  presets = [
    { label: 'Today', value: 'today' as DatePreset },
    { label: 'This Week', value: 'this-week' as DatePreset },
    { label: 'This Month', value: 'this-month' as DatePreset },
    { label: 'Custom', value: 'custom' as DatePreset }
  ];

  activePreset = signal<DatePreset | null>(null);
  customFrom = signal<string>('');
  customTo = signal<string>('');

  rangeLabel = computed(() => {
    const preset = this.activePreset();
    if (!preset) return '';
    if (preset === 'custom') {
      const from = this.customFrom();
      const to = this.customTo();
      if (from && to) return `${from} → ${to}`;
      return '';
    }
    const { from, to } = getPresetDateRange(preset);
    return `${from} → ${to}`;
  });

  selectPreset(preset: DatePreset): void {
    this.activePreset.set(preset);
    if (preset !== 'custom') {
      const range = getPresetDateRange(preset);
      this.rangeChanged.emit(range);
    } else {
      const from = this.customFrom();
      const to = this.customTo();
      if (from && to) {
        this.rangeChanged.emit({ from, to });
      }
    }
  }

  onCustomFromChange(value: string): void {
    this.customFrom.set(value);
    this.emitCustomRange();
  }

  onCustomToChange(value: string): void {
    this.customTo.set(value);
    this.emitCustomRange();
  }

  private emitCustomRange(): void {
    const from = this.customFrom();
    const to = this.customTo();
    if (from && to) {
      this.rangeChanged.emit({ from, to });
    }
  }

}
