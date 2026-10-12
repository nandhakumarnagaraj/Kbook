import { TestBed, ComponentFixture } from '@angular/core/testing';

import { DateRangeSelectorComponent, formatLocalDate } from './date-range-selector.component';

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return formatLocalDate(date);
}

describe('DateRangeSelectorComponent', () => {
  let fixture: ComponentFixture<DateRangeSelectorComponent>;

  function create(mode: 'range' | 'single', initialRange: { from: string; to: string } | null, maxDate?: string): void {
    fixture = TestBed.createComponent(DateRangeSelectorComponent);
    fixture.componentInstance.mode = mode;
    fixture.componentInstance.maxDate = maxDate;
    fixture.componentInstance.initialRange = initialRange;
    fixture.detectChanges();
  }

  function pills(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.date-range__preset-btn'));
  }

  function inputs(): HTMLInputElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.date-range__input'));
  }

  function label(): string {
    return fixture.nativeElement.querySelector('.date-range__label')?.textContent?.trim() ?? '';
  }

  it('offers the same three pills in both modes', () => {
    create('range', null);
    const rangeLabels = pills().map((p) => p.textContent?.trim());
    create('single', null);
    const singleLabels = pills().map((p) => p.textContent?.trim());

    expect(rangeLabels).toEqual(['Today', 'Yesterday', 'Custom']);
    expect(singleLabels).toEqual(rangeLabels);
  });

  it('marks Today and Yesterday active from the incoming range', () => {
    create('range', { from: daysAgo(0), to: daysAgo(0) });
    expect(pills()[0].classList).toContain('date-range__preset-btn--active');

    create('range', { from: daysAgo(1), to: daysAgo(1) });
    expect(pills()[1].classList).toContain('date-range__preset-btn--active');
    expect(pills()[0].classList).not.toContain('date-range__preset-btn--active');
  });

  it('range mode opens two inputs for a window', () => {
    create('range', { from: daysAgo(5), to: daysAgo(3) });

    expect(pills()[2].classList).toContain('date-range__preset-btn--active');
    expect(inputs().length).toBe(2);
    expect(label()).toBe(`${daysAgo(5)} → ${daysAgo(3)}`);
  });

  it('single mode opens exactly one input and emits a one-day range', () => {
    const emitted: { from: string; to: string }[] = [];
    create('single', { from: daysAgo(4), to: daysAgo(4) });
    fixture.componentInstance.rangeChanged.subscribe((range) => emitted.push(range));

    expect(inputs().length).toBe(1);

    const chosen = daysAgo(9);
    inputs()[0].value = chosen;
    inputs()[0].dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(emitted).toEqual([{ from: chosen, to: chosen }]);
    // One day must not read back as "from → to".
    expect(label()).toBe(chosen);
  });

  it('single mode respects maxDate so a future closing day cannot be picked', () => {
    const today = daysAgo(0);
    // A past day is custom, so the input is rendered (Today hides it and needs no bound).
    create('single', { from: daysAgo(4), to: daysAgo(4) }, today);

    expect(inputs().length).toBe(1);
    expect(inputs()[0].getAttribute('max')).toBe(today);
  });

  it('opening Custom in single mode reveals one input already seeded and bounded', async () => {
    const today = daysAgo(0);
    create('single', { from: today, to: today }, today);
    // Today is a preset, so its input stays hidden until Custom is chosen.
    expect(inputs().length).toBe(0);

    pills()[2].click();
    fixture.detectChanges();
    // NgModel writes the model into the DOM one microtask after the view updates.
    await fixture.whenStable();

    const el = inputs()[0];
    expect(inputs().length).toBe(1);
    expect(el.value).toBe(today);
    expect(el.getAttribute('max')).toBe(today);
  });

  it('selecting a preset emits that preset range without touching the inputs', () => {
    const emitted: { from: string; to: string }[] = [];
    create('single', { from: daysAgo(4), to: daysAgo(4) });
    fixture.componentInstance.rangeChanged.subscribe((range) => emitted.push(range));

    pills()[1].click();
    fixture.detectChanges();

    expect(emitted).toEqual([{ from: daysAgo(1), to: daysAgo(1) }]);
  });

  it('follows the parent when the parent moves the range itself', () => {
    create('range', { from: daysAgo(0), to: daysAgo(0) });
    expect(pills()[0].classList).toContain('date-range__preset-btn--active');

    // A page that resets its own filter (Clear Filters, a restored range) must take the
    // control with it, otherwise the pills keep naming a day the table is not showing.
    fixture.componentInstance.initialRange = { from: daysAgo(6), to: daysAgo(2) };
    fixture.detectChanges();

    expect(pills()[2].classList).toContain('date-range__preset-btn--active');
    expect(pills()[0].classList).not.toContain('date-range__preset-btn--active');
    expect(label()).toBe(`${daysAgo(6)} → ${daysAgo(2)}`);
  });

  it('ignores the echo of its own emission so an in-progress input is never reset', () => {
    const to = daysAgo(3);
    create('range', { from: daysAgo(9), to });
    // A page bound to a getter hands the same values straight back on the next cycle.
    fixture.componentInstance.rangeChanged.subscribe((range) => {
      fixture.componentInstance.initialRange = { ...range };
    });

    const typed = daysAgo(5);
    inputs()[0].value = typed;
    inputs()[0].dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.componentInstance.customFrom()).toBe(typed);
    expect(inputs()[0].value).toBe(typed);
    expect(label()).toBe(`${typed} → ${to}`);
  });
});
