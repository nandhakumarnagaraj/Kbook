import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RefreshButtonComponent } from './refresh-button.component';

/**
 * The refresh button exists to make an in-flight reload legible, so the spin is
 * the feature, not decoration. It is asserted against the real cascade
 * (getComputedStyle) because Karma loads styles.css + the design layers exactly
 * as index.html does — a plain class-name check would pass even if a global
 * rule won the cascade and left the icon static.
 */
describe('RefreshButtonComponent', () => {
  let fixture: ComponentFixture<RefreshButtonComponent>;

  function mount(loading: boolean): void {
    fixture = TestBed.createComponent(RefreshButtonComponent);
    fixture.componentRef.setInput('loading', loading);
    fixture.detectChanges();
  }

  function icon(): SVGElement {
    return fixture.nativeElement.querySelector('svg') as SVGElement;
  }

  afterEach(() => {
    if (fixture) {
      fixture.destroy();
    }
  });

  it('emits refresh when clicked', () => {
    mount(false);
    let clicks = 0;
    fixture.componentInstance.refresh.subscribe(() => clicks++);

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();

    expect(clicks).toBe(1);
  });

  it('is disabled while a reload is in flight, so it cannot be fired twice', () => {
    mount(true);
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.getAttribute('aria-busy')).withContext('the icon is aria-hidden, so the state needs announcing').toBe('true');
  });

  it('spins the icon while loading', () => {
    mount(true);

    const iconElement = icon();
    expect(iconElement.classList).toContain('spinning');

    const style = getComputedStyle(iconElement);
    expect(style.animationName).withContext('a spinning icon needs a running animation').not.toBe('none');
    expect(parseFloat(style.animationDuration)).withContext('the spin must not be zero-length').toBeGreaterThan(0);
    expect(style.animationTimingFunction).toBe('linear');
    expect(style.animationIterationCount).toBe('infinite');
  });

  it('keeps spinning when the user asked for reduced motion', () => {
    // Regression: the blanket `prefers-reduced-motion` block in styles.css capped
    // every animation at 0.01ms / one iteration with !important, which froze the
    // spinner and left a reload with no visible or announced feedback at all.
    // Progress indicators are exempt there; this asserts the exemption holds.
    expect(window.matchMedia('(prefers-reduced-motion: reduce)').matches)
      .withContext('this spec is only meaningful in an environment that reports reduced motion')
      .toBe(true);

    mount(true);

    const style = getComputedStyle(icon());
    expect(parseFloat(style.animationDuration)).withContext('frozen at 0.01ms means the wait is invisible').toBeGreaterThan(0.1);
    expect(style.animationIterationCount).toBe('infinite');
    expect(style.animationName).not.toBe('none');
  });

  it('stays static when idle', () => {
    mount(false);

    const style = getComputedStyle(icon());
    expect(style.animationName).toBe('none');
  });
});
