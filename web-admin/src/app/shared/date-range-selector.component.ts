import { ChangeDetectionStrategy, Component, Input, Output, EventEmitter, signal, computed } from '@angular/core';
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
    <div class="date-range">
      <div class="date-range__presets">
        <button
          *ngFor="let preset of presets; trackBy: trackByIndex"
          type="button"
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
      gap: 0.5rem;
    }

    .date-range__presets {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }

    .date-range__preset-btn {
      min-height: 36px;
      padding: 0.45rem 0.85rem;
      border-radius: 9px;
      border: 1px solid #E2E8F0;
      background: #FFFFFF;
      color: #334155;
      font-size: 0.82rem;
      font-weight: 600;
      white-space: nowrap;
      cursor: pointer;
      transition: all 120ms cubic-bezier(0.16, 1, 0.3, 1);
    }

    .date-range__preset-btn:hover {
      border-color: #6C27D9;
      color: #6C27D9;
      background: #FAF8FF;
    }

    .date-range__preset-btn--active {
      background: #6C27D9;
      color: #FFFFFF;
      border-color: #6C27D9;
      box-shadow: 0 2px 8px rgba(108, 39, 217, 0.25);
    }

    .date-range__custom {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      background: #FFFFFF;
      padding: 0.2rem 0.5rem;
      border-radius: 9px;
      border: 1px solid #E2E8F0;
    }

    .date-range__input {
      padding: 0.35rem 0.5rem;
      border: 1px solid #CBD5E1;
      border-radius: 6px;
      background: #FFFFFF;
      color: #1E293B;
      font-size: 0.82rem;
      min-height: 32px;
    }

    .date-range__input:focus {
      outline: none;
      border-color: #6C27D9;
      box-shadow: 0 0 0 2px rgba(108, 39, 217, 0.15);
    }

    .date-range__separator {
      color: #64748B;
      font-size: 0.8rem;
      font-weight: 500;
    }

    .date-range__label {
      font-size: 0.78rem;
      font-weight: 600;
      color: #64748B;
      background: #F1F5F9;
      padding: 0.3rem 0.65rem;
      border-radius: 6px;
    }

    @media (max-width: 480px) {
      .date-range {
        flex-direction: column;
        align-items: stretch;
      }

      .date-range__presets {
        display: flex;
        flex-wrap: wrap;
      }

      .date-range__preset-btn {
        flex: 1 1 calc(50% - 0.35rem);
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
    // Check if matches known presets
    const today = new Date();
    const todayRange = getPresetDateRange('today', today);
    const yestRange = getPresetDateRange('yesterday', today);
    const weekRange = getPresetDateRange('last-7-days', today);
    const monthRange = getPresetDateRange('this-month', today);

    if (value.from === todayRange.from && value.to === todayRange.to) {
      this.activePreset.set('today');
    } else if (value.from === yestRange.from && value.to === yestRange.to) {
      this.activePreset.set('yesterday');
    } else {
      this.activePreset.set('custom');
    }
  }

  presets = [
    { label: 'Today', value: 'today' as DatePreset },
    { label: 'Yesterday', value: 'yesterday' as DatePreset },
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

  trackByIndex = (_: number, __: unknown) => _;

  selectPreset(preset: DatePreset): void {
    this.activePreset.set(preset);
    if (preset !== 'custom') {
      const range = getPresetDateRange(preset);
      this.rangeChanged.emit(range);
    } else {
      const today = formatLocalDate(new Date());
      if (!this.customFrom()) this.customFrom.set(today);
      if (!this.customTo()) this.customTo.set(today);
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
