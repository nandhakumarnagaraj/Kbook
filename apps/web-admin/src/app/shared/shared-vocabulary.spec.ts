import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

/**
 * The design freeze (docs/design/DESIGN_SYSTEM_FREEZE.md, rule 3) says the shared
 * vocabulary in `styles/bento.css` is the single definition of these classes: a class
 * name must mean exactly one thing across the app, and pages use the shared class
 * "instead of page-local equivalents".
 *
 * bento.css is loaded last, which is *not* enough to enforce that on its own: a component
 * that redeclares the class in its own `styles: []` wins, because Angular scopes those
 * selectors with an `[_ngcontent-*]` attribute and that out-specifies the plain global
 * rule. That is how `.kpi-tag`, `.bento-tile`, `.status-chip` and `.btn-micro` each ended
 * up with two meanings and the shared file silently lost.
 *
 * These specs run in the real browser with the real stylesheets in the real order
 * (see the `styles` list in angular.json), so they measure what a page actually gets.
 */
@Component({
  standalone: true,
  template: `
    <span class="kpi-tag">12</span>
    <span class="kpi-tag kpi-tag--green">3 Online</span>
    <span class="kpi-tag kpi-tag--warn">2 Reversals</span>
    <span class="kpi-tag kpi-tag--neutral">-</span>
    <span class="status-chip badge-completed">Completed</span>
    <a class="btn-micro" href="#invoice">Invoice</a>
    <button type="button" class="btn-micro btn-micro--primary">Details</button>
    <button type="button" class="btn-micro btn-micro--danger">Refund</button>
  `,
})
class VocabularyHostComponent {}

function styleOf(host: HTMLElement, selector: string): CSSStyleDeclaration {
  const el = host.querySelector(selector);
  if (!el) {
    throw new Error(`expected ${selector} to be rendered`);
  }
  return getComputedStyle(el);
}

/** `rem` values, resolved against whatever the root font size actually is. */
function rem(value: number): string {
  const root = parseFloat(getComputedStyle(document.documentElement).fontSize);
  return `${Math.round(value * root * 100) / 100}px`;
}

describe('shared vocabulary (bento.css)', () => {
  let host: HTMLElement;

  beforeEach(() => {
    const fixture = TestBed.configureTestingModule({ imports: [VocabularyHostComponent] })
      .createComponent(VocabularyHostComponent);
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;
  });

  // The host is left in the Karma DOM between specs otherwise, and querySelector would
  // find the previous fixture's element.
  afterEach(() => host.remove());

  it('renders a .kpi-tag at the shared size, not a page-local one', () => {
    const tag = styleOf(host, '.kpi-tag');

    expect(tag.paddingTop).toBe(rem(0.15));
    expect(tag.borderRadius).toBe('999px');
    expect(tag.fontSize).toBe(rem(0.72));
    expect(tag.fontWeight).toBe('700');
    expect(tag.fontVariantNumeric).toBe('tabular-nums');
    expect(tag.backgroundColor).toBe('rgb(248, 245, 239)'); // --kb-color-surface-2
  });

  it('resolves the .kpi-tag modifiers to semantic tokens', () => {
    // POS Devices and Daily Closing read live state off these two.
    expect(styleOf(host, '.kpi-tag--green').color).toBe('rgb(47, 133, 90)'); // --kb-color-success
    expect(styleOf(host, '.kpi-tag--green').backgroundColor).toBe('rgb(231, 244, 236)'); // success-soft
    expect(styleOf(host, '.kpi-tag--warn').color).toBe('rgb(192, 57, 43)'); // --kb-color-danger
    expect(styleOf(host, '.kpi-tag--warn').backgroundColor).toBe('rgb(249, 231, 227)'); // danger-soft
  });

  it('renders .status-chip from the single shared rule', () => {
    const chip = styleOf(host, '.status-chip');

    expect(chip.display).toBe('inline-block');
    expect(chip.paddingTop).toBe(rem(0.2));
    expect(chip.paddingLeft).toBe(rem(0.6));
    expect(chip.borderRadius).toBe('999px');
  });

  it('renders .btn-micro (on an anchor) without losing the link reset', () => {
    const btn = styleOf(host, 'a.btn-micro');

    expect(btn.backgroundColor).toBe('rgb(255, 255, 255)'); // --kb-color-surface
    expect(btn.color).toBe('rgb(28, 26, 23)'); // --kb-color-foreground, beats a { color: var(--brand) }
    expect(btn.textDecorationLine).toBe('none');
    expect(btn.display).toBe('inline-flex');
  });

  it('renders both .btn-micro modifiers, including the danger variant', () => {
    const primary = styleOf(host, '.btn-micro--primary');
    expect(primary.backgroundColor).toBe('rgb(0, 102, 204)'); // --kb-color-primary
    expect(primary.color).toBe('rgb(255, 255, 255)');

    // Added to bento.css when the order-history copy was removed; two templates use it.
    const danger = styleOf(host, '.btn-micro--danger');
    expect(danger.color).toBe('rgb(192, 57, 43)'); // --kb-color-danger
    expect(danger.backgroundColor).toBe('rgb(249, 231, 227)'); // --kb-color-danger-soft
  });
});
