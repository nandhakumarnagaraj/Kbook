import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AnimatedCounterComponent } from './animated-counter.component';
import { formatCurrency } from './formatters';

/**
 * The counter exists to make a refresh legible: the dashboard polls every 60s and
 * the values used to swap instantly, so a 12% move looked like no move at all.
 * These specs pin the two properties that matter — it rolls, and it finishes on
 * exactly the value it was given — plus the one that keeps it honest for anyone
 * who asked their OS not to animate things.
 */
describe('AnimatedCounterComponent', () => {
  let fixture: ComponentFixture<AnimatedCounterComponent>;

  function text(): string {
    return (fixture.nativeElement.textContent as string).trim();
  }

  /** Strips ₹ and grouping so a mid-roll frame can be compared numerically. */
  function amount(): number {
    return Number(text().replace(/[^0-9.]/g, '')) || 0;
  }

  function withMotionPreference(reduce: boolean): void {
    spyOn(window, 'matchMedia').and.returnValue({ matches: reduce } as MediaQueryList);
  }

  /** The roll writes a signal, so the DOM only shows it on the next change
   *  detection pass — in the app zone.js triggers that, in a spec we flush it. */
  function advance(ms: number): void {
    tick(ms);
    fixture.detectChanges();
  }

  afterEach(() => {
    if (fixture) {
      fixture.destroy();
    }
  });

  it('rolls to the new value instead of snapping to it', fakeAsync(() => {
    withMotionPreference(false);
    fixture = TestBed.createComponent(AnimatedCounterComponent);
    fixture.componentRef.setInput('value', 0);
    fixture.componentRef.setInput('format', 'currency');
    fixture.detectChanges();
    expect(text()).toBe(formatCurrency(0));

    fixture.componentRef.setInput('value', 250);
    fixture.detectChanges();

    // Part-way through, the number is visibly in transit — that is the feature.
    advance(280);
    expect(amount()).toBeGreaterThan(0);
    expect(amount()).toBeLessThan(250);

    advance(400);
    expect(text()).toBe(formatCurrency(250));
  }));

  it('lands on exactly the value it was given, not a rounded-off approximation', fakeAsync(() => {
    withMotionPreference(false);
    fixture = TestBed.createComponent(AnimatedCounterComponent);
    fixture.componentRef.setInput('value', 0);
    fixture.componentRef.setInput('format', 'currency');
    fixture.detectChanges();

    fixture.componentRef.setInput('value', 12345.67);
    fixture.detectChanges();
    advance(700);

    expect(text()).toBe(formatCurrency(12345.67));
  }));

  it('renders plain counts with en-IN grouping', fakeAsync(() => {
    withMotionPreference(false);
    fixture = TestBed.createComponent(AnimatedCounterComponent);
    fixture.componentRef.setInput('value', 0);
    fixture.detectChanges();

    fixture.componentRef.setInput('value', 1234);
    fixture.detectChanges();
    advance(700);

    expect(text()).toBe('1,234');
  }));

  it('snaps straight to the value when the user asked for reduced motion', fakeAsync(() => {
    // The global `prefers-reduced-motion` block in styles.css cannot cover this:
    // it caps CSS animation durations, and a requestAnimationFrame loop ignores it.
    withMotionPreference(true);
    fixture = TestBed.createComponent(AnimatedCounterComponent);
    fixture.componentRef.setInput('value', 0);
    fixture.componentRef.setInput('format', 'currency');
    fixture.detectChanges();

    fixture.componentRef.setInput('value', 250);
    fixture.detectChanges();
    fixture.detectChanges();

    // No timer advanced: it must already be final.
    expect(text()).toBe(formatCurrency(250));
  }));

  it('stops rolling once destroyed, so a poll cannot write into a dead view', fakeAsync(() => {
    withMotionPreference(false);
    fixture = TestBed.createComponent(AnimatedCounterComponent);
    fixture.componentRef.setInput('value', 0);
    fixture.detectChanges();

    fixture.componentRef.setInput('value', 500);
    fixture.detectChanges();
    tick(100);

    fixture.destroy();
    const afterDestroy = fixture.nativeElement.textContent;

    tick(1000);
    expect(fixture.nativeElement.textContent).toBe(afterDestroy);
  }));
});
