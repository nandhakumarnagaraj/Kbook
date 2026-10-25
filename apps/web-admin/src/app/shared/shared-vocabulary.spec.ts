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

/**
 * Resolves a design token in the browser.
 *
 * These assertions used to compare against literal rgb() values — the palette as it
 * stood on the day the spec was written — so every deliberate palette change turned
 * them red and the class they were guarding was fine. Pinning the *token* keeps the
 * guarantee (this class paints itself from the shared semantic colour) without
 * freezing the hex it happens to hold today.
 */
function tokenColor(name: string): string {
  const probe = document.createElement('div');
  probe.style.color = `var(${name})`;
  document.body.appendChild(probe);
  const value = getComputedStyle(probe).color;
  probe.remove();
  return value;
}

/**
 * Resolves a type-scale token the same way: the assertion says "this class paints
 * itself at the shared step", not "this class is 11.84px". Pinning the literal is
 * how the tag kept a page-local size through a refactor that was supposed to remove
 * it — the spec would have gone red for the right change.
 */
function tokenSize(name: string): string {
  const probe = document.createElement('div');
  probe.style.fontSize = `var(${name})`;
  document.body.appendChild(probe);
  const value = getComputedStyle(probe).fontSize;
  probe.remove();
  return value;
}

/**
 * Resolves a length token - radii today. Same reasoning as tokenSize: the assertion says
 * "this class paints from the shared pill", not "this class is 9999px". The old `999px`
 * literal had already drifted from --kb-radius-full, which is 9999px.
 */
function tokenLength(name: string): string {
  const probe = document.createElement('div');
  probe.style.borderRadius = `var(${name})`;
  document.body.appendChild(probe);
  const value = getComputedStyle(probe).borderRadius;
  probe.remove();
  return value;
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

    expect(tag.paddingTop).toBe(rem(0.2));
    expect(tag.borderRadius).toBe(tokenLength('--kb-radius-full'));
    expect(tag.fontSize).toBe(tokenSize('--kb-text-xs'));
    expect(tag.fontWeight).toBe('700');
    expect(tag.fontVariantNumeric).toBe('tabular-nums');
    expect(tag.backgroundColor).toBe(tokenColor('--kb-color-surface-2'));
  });

  it('resolves the .kpi-tag modifiers to semantic tokens', () => {
    // POS Devices and Daily Closing read live state off these two.
    expect(styleOf(host, '.kpi-tag--green').color).toBe(tokenColor('--kb-color-success'));
    expect(styleOf(host, '.kpi-tag--green').backgroundColor).toBe(tokenColor('--kb-color-success-soft'));
    expect(styleOf(host, '.kpi-tag--warn').color).toBe(tokenColor('--kb-color-danger'));
    expect(styleOf(host, '.kpi-tag--warn').backgroundColor).toBe(tokenColor('--kb-color-danger-soft'));
  });

  it('renders .status-chip from the single shared rule', () => {
    const chip = styleOf(host, '.status-chip');

    expect(chip.display).toBe('inline-block');
    expect(chip.paddingTop).toBe(rem(0.2));
    expect(chip.paddingLeft).toBe(rem(0.6));
    expect(chip.borderRadius).toBe(tokenLength('--kb-radius-full'));
  });

  it('renders .btn-micro (on an anchor) without losing the link reset', () => {
    const btn = styleOf(host, 'a.btn-micro');

    expect(btn.backgroundColor).toBe(tokenColor('--kb-color-surface-2'));
    // --kb-color-foreground: the shared rule beats `a { color: var(--brand) }`.
    expect(btn.color).toBe(tokenColor('--kb-color-foreground'));
    expect(btn.textDecorationLine).toBe('none');
    expect(btn.display).toBe('inline-flex');
  });

  it('renders both .btn-micro modifiers, including the danger variant', () => {
    const primary = styleOf(host, '.btn-micro--primary');
    expect(primary.backgroundColor).toBe(tokenColor('--kb-color-primary'));
    expect(primary.color).toBe('rgb(255, 255, 255)');

    // Added to bento.css when the order-history copy was removed; two templates use it.
    const danger = styleOf(host, '.btn-micro--danger');
    expect(danger.color).toBe(tokenColor('--kb-color-danger'));
    expect(danger.backgroundColor).toBe(tokenColor('--kb-color-danger-soft'));
  });
});
