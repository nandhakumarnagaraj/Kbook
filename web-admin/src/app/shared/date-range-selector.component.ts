import { ChangeDetectionStrategy, Component, EventEmitter, HostListener, Input, Output, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

export type DatePreset = 'today' | 'yesterday' | 'last-7-days' | 'this-month' | 'custom';
type CalendarCell = { date: string; day: number } | null;

export function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseLocalDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
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
  imports: [CommonModule],
  template: `
    <div class="date-range">
      <div class="date-range__presets" aria-label="Date range presets">
        <button
          *ngFor="let preset of presets; trackBy: trackByIndex"
          type="button"
          class="date-range__preset-btn"
          [class.date-range__preset-btn--active]="activePreset() === preset.value"
          (click)="selectPreset(preset.value)">
          {{ preset.label }}
        </button>
      </div>

      <div class="date-range__label" *ngIf="rangeLabel()">
        {{ rangeLabel() }}
      </div>
    </div>

    <div
      class="calendar-backdrop"
      *ngIf="pickerOpen()"
      (click)="closePicker()"
      (keydown.escape)="closePicker()">
      <section
        class="calendar-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="date-picker-title"
        (click)="$event.stopPropagation()">
        <header class="calendar-header">
          <button type="button" class="calendar-nav" aria-label="Previous month" (click)="changeMonth(-1)">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
          </button>
          <h2 id="date-picker-title">{{ monthLabel() }}</h2>
          <button
            type="button"
            class="calendar-nav"
            aria-label="Next month"
            [disabled]="!canGoForward()"
            (click)="changeMonth(1)">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6" /></svg>
          </button>
        </header>

        <div class="calendar-summary" aria-live="polite">
        <span>{{ selectionSummary() }}</span>
          <span class="calendar-count" *ngIf="rangeDayCount() as dayCount">{{ dayCount }} {{ dayCount === 1 ? 'day' : 'days' }}</span>
        </div>

        <div class="calendar-divider"></div>

        <div class="calendar-grid" role="grid" [attr.aria-label]="monthLabel()">
          <span class="calendar-weekday" role="columnheader" *ngFor="let weekday of weekdayLabels">{{ weekday }}</span>
          <ng-container *ngFor="let cell of calendarCells(); trackBy: trackCalendarCell">
            <span class="calendar-empty" role="gridcell" *ngIf="!cell" aria-hidden="true"></span>
            <button
              *ngIf="cell"
              type="button"
              role="gridcell"
              class="calendar-day"
              [class.calendar-day--start]="draftFrom() === cell.date"
              [class.calendar-day--end]="mode === 'range' && draftTo() === cell.date && draftTo() !== draftFrom()"
              [class.calendar-day--in-range]="isInRange(cell.date)"
              [class.calendar-day--today]="today === cell.date && !isSelected(cell.date)"
              [class.calendar-day--disabled]="isDisabled(cell.date)"
              [disabled]="isDisabled(cell.date)"
              [attr.aria-selected]="isSelected(cell.date)"
              [attr.aria-label]="dayLabel(cell.date)"
              (click)="selectDay(cell.date)">
              <span>{{ cell.day }}</span>
            </button>
          </ng-container>
        </div>

        <div class="calendar-divider calendar-divider--bottom"></div>

        <footer class="calendar-actions">
          <button type="button" class="calendar-action calendar-action--secondary" (click)="closePicker()">Cancel</button>
          <button type="button" class="calendar-action calendar-action--primary" [disabled]="!canApply()" (click)="applyPicker()">Apply</button>
        </footer>
      </section>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .date-range { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; }
    .date-range__presets { display: flex; flex-wrap: wrap; gap: 0.35rem; }
    .date-range__preset-btn {
      min-height: 36px; padding: 0.45rem 0.85rem; border-radius: 9px;
      border: 1px solid var(--kb-color-border); background: #fff;
      color: var(--kb-color-foreground); font-size: 0.82rem; font-weight: 600;
      white-space: nowrap; cursor: pointer; transition: border-color 120ms ease, background 120ms ease, color 120ms ease;
    }
    .date-range__preset-btn:hover { border-color: var(--kb-color-primary); color: var(--kb-color-primary); background: var(--kb-color-surface); }
    .date-range__preset-btn--active { background: var(--kb-color-primary); color: #fff; border-color: var(--kb-color-primary); box-shadow: 0 2px 8px rgba(108, 39, 217, 0.25); }
    .date-range__label {
      font-size: 0.78rem; font-weight: 600; color: var(--kb-color-muted-foreground);
      background: var(--kb-color-surface-2); padding: 0.3rem 0.65rem; border-radius: 6px;
    }

    .calendar-backdrop {
      position: fixed; inset: 0; z-index: 1600; display: grid; place-items: center;
      padding: 1rem; background: rgba(15, 10, 20, 0.48); backdrop-filter: blur(3px);
    }
    .calendar-dialog {
      width: min(100%, 400px); max-height: min(92vh, 680px); overflow: auto;
      padding: 1.1rem 0 1rem; border: 1px solid rgba(200, 150, 60, 0.38); border-radius: 20px;
      color: #f5e6c8; background: linear-gradient(160deg, #1a0a0a 0%, #2d1010 54%, #1a0828 100%);
      box-shadow: 0 24px 70px rgba(14, 8, 24, 0.42); outline: none;
    }
    .calendar-header { display: flex; align-items: center; gap: 0.75rem; padding: 0 1rem; }
    .calendar-header h2 { flex: 1; margin: 0; color: #c8960c; font-size: 1rem; font-weight: 750; text-align: center; }
    .calendar-nav {
      display: grid; place-items: center; flex: 0 0 40px; width: 40px; height: 40px;
      border: 1px solid rgba(200, 150, 60, 0.35); border-radius: 50%; background: rgba(26, 10, 10, 0.72);
      color: #c8960c; cursor: pointer;
    }
    .calendar-nav:hover:not(:disabled) { background: rgba(200, 150, 12, 0.16); }
    .calendar-nav:disabled { opacity: 0.3; cursor: default; }
    .calendar-nav svg { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
    .calendar-summary { display: flex; align-items: center; gap: 0.5rem; min-height: 42px; padding: 0.4rem 1rem 0; font-size: 0.84rem; color: #f5e6c8; }
    .calendar-summary > span:first-child { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .calendar-count { padding: 0.2rem 0.5rem; border-radius: 999px; color: #e6b943; background: rgba(200, 150, 12, 0.16); font-size: 0.72rem; font-weight: 700; white-space: nowrap; }
    .calendar-divider { height: 1px; margin: 0.85rem 1rem; background: rgba(200, 150, 60, 0.25); }
    .calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); padding: 0 0.75rem; }
    .calendar-weekday { display: grid; place-items: center; height: 30px; color: #d4a843; font-size: 0.68rem; font-weight: 700; }
    .calendar-empty, .calendar-day { aspect-ratio: 1; min-width: 0; }
    .calendar-day {
      position: relative; display: grid; place-items: center; padding: 0; border: 0; border-radius: 0;
      color: #f5e6c8; background: transparent; font: inherit; font-size: 0.84rem; cursor: pointer;
    }
    .calendar-day::before { position: absolute; inset: 5px 0; z-index: 0; background: transparent; content: ''; }
    .calendar-day > span { position: relative; z-index: 1; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; }
    .calendar-day--in-range::before { background: rgba(200, 150, 12, 0.16); }
    .calendar-day--start::before { border-radius: 999px 0 0 999px; }
    .calendar-day--end::before { border-radius: 0 999px 999px 0; }
    .calendar-day--start.calendar-day--end::before { border-radius: 999px; }
    .calendar-day--start > span, .calendar-day--end > span { color: #1a0a0a; background: #c8960c; font-weight: 800; }
    .calendar-day--today > span { color: #d4a843; box-shadow: inset 0 0 0 1px #c8960c; }
    .calendar-day:hover:not(:disabled):not(.calendar-day--start):not(.calendar-day--end) > span { background: rgba(200, 150, 12, 0.2); }
    .calendar-day--disabled { opacity: 0.32; cursor: default; }
    .calendar-day:focus-visible, .calendar-nav:focus-visible, .calendar-action:focus-visible { outline: 2px solid #f2c14e; outline-offset: 2px; }
    .calendar-actions { display: flex; gap: 0.65rem; padding: 0 1rem; }
    .calendar-action { flex: 1; min-height: 42px; border-radius: 10px; font: inherit; font-size: 0.84rem; font-weight: 700; cursor: pointer; }
    .calendar-action--secondary { border: 1px solid rgba(200, 150, 60, 0.35); color: #f5e6c8; background: rgba(26, 10, 10, 0.55); }
    .calendar-action--secondary:hover { background: rgba(200, 150, 12, 0.12); }
    .calendar-action--primary { border: 1px solid #c8960c; color: #1a0a0a; background: #c8960c; }
    .calendar-action--primary:hover:not(:disabled) { background: #ddb02f; }
    .calendar-action--primary:disabled { opacity: 0.4; cursor: default; }
    @media (max-width: 480px) {
      .calendar-backdrop { padding: 0.75rem; }
      .calendar-dialog { width: 100%; padding-top: 0.9rem; border-radius: 18px; }
      .calendar-grid { padding: 0 0.5rem; }
      .calendar-day > span { width: 32px; height: 32px; }
    }
    @media (prefers-reduced-motion: reduce) { .date-range__preset-btn { transition: none; } }
  `]
})
export class DateRangeSelectorComponent {
  @Output() rangeChanged = new EventEmitter<{ from: string; to: string }>();

  @Input() mode: 'range' | 'single' = 'range';
  @Input() maxDate?: string;
  @Input() singleDateLabel = 'Select date';

  @Input()
  set initialRange(value: { from: string; to: string } | null) {
    if (!value || !value.from || !value.to) return;
    if (this.lastEmitted && value.from === this.lastEmitted.from && value.to === this.lastEmitted.to) return;
    this.applyRange(value);
  }

  private lastEmitted: { from: string; to: string } | null = null;
  readonly today = formatLocalDate(new Date());
  readonly weekdayLabels = Array.from({ length: 7 }, (_, index) =>
    new Intl.DateTimeFormat(undefined, { weekday: 'short' }).format(new Date(2024, 0, 7 + index))
  );

  presets = [
    { label: 'Today', value: 'today' as DatePreset },
    { label: 'Yesterday', value: 'yesterday' as DatePreset },
    { label: 'Custom', value: 'custom' as DatePreset }
  ];

  activePreset = signal<DatePreset | null>(null);
  customFrom = signal<string>('');
  customTo = signal<string>('');
  pickerOpen = signal(false);
  draftFrom = signal<string | null>(null);
  draftTo = signal<string | null>(null);
  visibleMonth = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));

  monthLabel = computed(() => new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(this.visibleMonth()));
  calendarCells = computed<CalendarCell[]>(() => {
    const month = this.visibleMonth();
    const year = month.getFullYear();
    const monthIndex = month.getMonth();
    const leadingBlanks = new Date(year, monthIndex, 1).getDay();
    const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
    return [
      ...Array<null>(leadingBlanks).fill(null),
      ...Array.from({ length: daysInMonth }, (_, index) => ({
        date: formatLocalDate(new Date(year, monthIndex, index + 1)),
        day: index + 1
      }))
    ];
  });
  rangeLabel = computed(() => {
    const preset = this.activePreset();
    const range = preset === 'custom' ? { from: this.customFrom(), to: this.customTo() }
      : preset ? getPresetDateRange(preset) : null;
    if (!range?.from || !range.to) return '';
    if (this.mode === 'single' || range.from === range.to) return range.from;
    return `${range.from} → ${range.to}`;
  });
  selectionSummary = computed(() => {
    const from = this.draftFrom();
    const to = this.mode === 'single' ? from : this.draftTo();
    if (!from) return this.mode === 'single' ? this.singleDateLabel : 'Select a start date';
    if (!to) return `${this.formatDay(from)}  –  pick end date`;
    return `${this.formatDay(from)}  –  ${this.formatDay(to)}`;
  });
  rangeDayCount = computed(() => {
    const from = this.draftFrom();
    const to = this.mode === 'single' ? from : this.draftTo();
    if (!from || !to) return null;
    return Math.round((parseLocalDate(to).getTime() - parseLocalDate(from).getTime()) / 86400000) + 1;
  });
  canApply = computed(() => !!this.draftFrom() && (this.mode === 'single' || !!this.draftTo()));
  canGoForward = computed(() => {
    if (this.mode !== 'single' || !this.maxDate) return true;
    const month = this.visibleMonth();
    const next = new Date(month.getFullYear(), month.getMonth() + 1, 1);
    return formatLocalDate(new Date(next.getFullYear(), next.getMonth(), 1)) <= this.maxDate;
  });

  trackByIndex = (index: number) => index;
  trackCalendarCell = (index: number, cell: CalendarCell) => cell?.date ?? `blank-${index}`;

  selectPreset(preset: DatePreset): void {
    this.activePreset.set(preset);
    if (preset !== 'custom') {
      const range = getPresetDateRange(preset);
      this.customFrom.set(range.from);
      this.customTo.set(range.to);
      this.emit(range);
      return;
    }

    const today = formatLocalDate(new Date());
    const from = this.customFrom() || today;
    const to = this.customTo() || from;
    this.draftFrom.set(from);
    this.draftTo.set(this.mode === 'single' ? from : to);
    this.visibleMonth.set(new Date(parseLocalDate(from).getFullYear(), parseLocalDate(from).getMonth(), 1));
    this.pickerOpen.set(true);
  }

  changeMonth(delta: number): void {
    const month = this.visibleMonth();
    this.visibleMonth.set(new Date(month.getFullYear(), month.getMonth() + delta, 1));
  }

  selectDay(day: string): void {
    if (this.isDisabled(day)) return;
    if (this.mode === 'single') {
      this.draftFrom.set(day);
      this.draftTo.set(day);
      return;
    }
    const from = this.draftFrom();
    const to = this.draftTo();
    if (!from || to) {
      this.draftFrom.set(day);
      this.draftTo.set(null);
    } else if (day < from) {
      this.draftFrom.set(day);
      this.draftTo.set(from);
    } else {
      this.draftTo.set(day);
    }
  }

  isSelected(day: string): boolean { return this.draftFrom() === day || this.draftTo() === day; }
  isInRange(day: string): boolean {
    const from = this.draftFrom();
    const to = this.draftTo();
    return this.mode === 'range' && !!from && !!to && from !== to && day >= from && day <= to;
  }
  isDisabled(day: string): boolean { return this.mode === 'single' && !!this.maxDate && day > this.maxDate; }
  dayLabel(day: string): string { return new Intl.DateTimeFormat(undefined, { dateStyle: 'full' }).format(parseLocalDate(day)); }

  applyPicker(): void {
    const from = this.draftFrom();
    const to = this.mode === 'single' ? from : this.draftTo();
    if (!from || !to) return;
    this.customFrom.set(from);
    this.customTo.set(to);
    this.emit({ from, to });
    this.closePicker();
  }

  closePicker(): void { this.pickerOpen.set(false); }

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.pickerOpen()) this.closePicker(); }

  private formatDay(value: string): string {
    return new Intl.DateTimeFormat(undefined, { day: '2-digit', month: 'short', year: 'numeric' }).format(parseLocalDate(value));
  }

  private emit(range: { from: string; to: string }): void {
    this.lastEmitted = { ...range };
    this.rangeChanged.emit(range);
  }

  /** Keep the active pill and editable range in sync with the page's actual filter. */
  private applyRange(value: { from: string; to: string }): void {
    this.customFrom.set(value.from);
    this.customTo.set(value.to);
    const todayRange = getPresetDateRange('today');
    const yesterdayRange = getPresetDateRange('yesterday');
    if (value.from === todayRange.from && value.to === todayRange.to) this.activePreset.set('today');
    else if (value.from === yesterdayRange.from && value.to === yesterdayRange.to) this.activePreset.set('yesterday');
    else this.activePreset.set('custom');
  }
}
