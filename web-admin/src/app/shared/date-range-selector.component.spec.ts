import { TestBed, ComponentFixture } from '@angular/core/testing';

import { DateRangeSelectorComponent, formatLocalDate } from './date-range-selector.component';

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return formatLocalDate(date);
}

/**
 * The control is the picker the pages actually ship: one date input per end of the
 * period, bounded to each other (a range cannot run backwards) and to `maxDate` in
 * single mode. It used to render Today / Yesterday / Custom pills plus a range label,
 * which commit 1e94bdc5 replaced; these specs pin the picker so that a spec can no
 * longer pass while the template has moved on.
 */
describe('DateRangeSelectorComponent', () => {
  let fixture: ComponentFixture<DateRangeSelectorComponent>;

  function create(
    mode: 'range' | 'single',
    initialRange: { from: string; to: string } | null,
    options: { maxDate?: string; singleDateLabel?: string } = {}
  ): void {
    fixture = TestBed.createComponent(DateRangeSelectorComponent);
    fixture.componentInstance.mode = mode;
    fixture.componentInstance.maxDate = options.maxDate;
    fixture.componentInstance.singleDateLabel = options.singleDateLabel ?? 'Select date';
    fixture.componentInstance.initialRange = initialRange;
    fixture.detectChanges();
  }

  function inputs(): HTMLInputElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.date-range__input'));
  }

  /** Typing into an input the way a browser does, then letting Angular catch up. */
  function type(index: number, value: string): void {
    const input = inputs()[index];
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('range mode opens a start and an end input', () => {
    create('range', null);

    expect(inputs().length).toBe(2);
    expect(inputs()[0].getAttribute('aria-label')).toBe('Start date');
    expect(inputs()[1].getAttribute('aria-label')).toBe('End date');
  });

  it('single mode offers one input, named by the day it selects', async () => {
    create('single', { from: daysAgo(4), to: daysAgo(4) }, { singleDateLabel: 'Closing date' });
    await fixture.whenStable();

    expect(inputs().length).toBe(1);
    expect(inputs()[0].getAttribute('aria-label')).toBe('Closing date');
    expect(inputs()[0].value).toBe(daysAgo(4));
  });

  it('seeds both inputs from the incoming range and bounds the second by the first', async () => {
    create('range', { from: daysAgo(5), to: daysAgo(3) });
    await fixture.whenStable();

    expect(inputs()[0].value).toBe(daysAgo(5));
    expect(inputs()[1].value).toBe(daysAgo(3));
    expect(inputs()[1].getAttribute('min')).toBe(daysAgo(5));
  });

  it('single mode emits a one-day range and respects maxDate', async () => {
    const emitted: { from: string; to: string }[] = [];
    const today = daysAgo(0);
    create('single', { from: daysAgo(4), to: daysAgo(4) }, { maxDate: today });
    fixture.componentInstance.rangeChanged.subscribe((range) => emitted.push(range));
    await fixture.whenStable();

    // A future closing day cannot be picked.
    expect(inputs()[0].getAttribute('max')).toBe(today);

    const chosen = daysAgo(9);
    type(0, chosen);

    expect(emitted).toEqual([{ from: chosen, to: chosen }]);
  });

  it('range mode emits only a complete, ordered window', () => {
    const emitted: { from: string; to: string }[] = [];
    create('range', null);
    fixture.componentInstance.rangeChanged.subscribe((range) => emitted.push(range));

    // Half a window is not a filter.
    type(0, daysAgo(5));
    expect(emitted).toEqual([]);

    // ...and neither is a backwards one.
    type(1, daysAgo(7));
    expect(emitted).toEqual([]);

    type(1, daysAgo(3));
    expect(emitted).toEqual([{ from: daysAgo(5), to: daysAgo(3) }]);
  });

  it('follows the parent when the parent moves the range itself', async () => {
    create('range', { from: daysAgo(0), to: daysAgo(0) });
    await fixture.whenStable();
    expect(inputs()[0].value).toBe(daysAgo(0));

    // A page that resets its own filter (Clear Filters, a restored range) must take the
    // control with it, otherwise the inputs keep naming a window the table is not showing.
    fixture.componentInstance.initialRange = { from: daysAgo(6), to: daysAgo(2) };
    fixture.detectChanges();
    await fixture.whenStable();

    expect(inputs()[0].value).toBe(daysAgo(6));
    expect(inputs()[1].value).toBe(daysAgo(2));
  });

  it('ignores the echo of its own emission so an in-progress input is never reset', async () => {
    const to = daysAgo(3);
    create('range', { from: daysAgo(9), to });
    // A page bound to a getter hands the same values straight back on the next cycle.
    fixture.componentInstance.rangeChanged.subscribe((range) => {
      fixture.componentInstance.initialRange = { ...range };
    });
    await fixture.whenStable();

    const typed = daysAgo(5);
    type(0, typed);

    expect(fixture.componentInstance.customFrom()).toBe(typed);
    expect(inputs()[0].value).toBe(typed);
    expect(fixture.componentInstance.customTo()).toBe(to);
  });
});
