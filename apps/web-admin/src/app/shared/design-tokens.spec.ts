/**
 * The palette lives in custom properties, and `--kb-color-primary-rgb` exists so
 * alpha variants can be written as `rgba(var(--kb-color-primary-rgb), 0.2)`
 * instead of re-typing 108, 39, 217 in every shadow and tint. These specs check
 * that the token actually resolves — a missing custom property inside `rgba()`
 * makes the whole declaration invalid and silently drops the shadow/tint.
 */
describe('design tokens', () => {
  const probes: HTMLElement[] = [];

  function probe(apply: (el: HTMLElement) => void): HTMLElement {
    const el = document.createElement('div');
    apply(el);
    document.body.appendChild(el);
    probes.push(el);
    return el;
  }

  afterEach(() => {
    while (probes.length) {
      probes.pop()!.remove();
    }
  });

  it('resolves an alpha variant of the primary colour to that colour', () => {
    const tint = probe((el) => (el.style.background = 'rgba(var(--kb-color-primary-rgb), 0.1)'));
    const solid = probe((el) => (el.style.background = 'var(--kb-color-primary)'));

    const tinted = getComputedStyle(tint).backgroundColor;
    expect(tinted).withContext('an unresolvable token would drop the declaration and leave it transparent').not.toBe('rgba(0, 0, 0, 0)');

    // Compared against the solid token, never a literal: the channels must be the
    // same hue and the alpha must be the 0.1 asked for. A literal here pinned the
    // palette of the day and failed on every rebrand.
    const solidChannels = (getComputedStyle(solid).backgroundColor.match(/\d+/g) || []).slice(0, 3).join(',');
    const tintNumbers = tinted.match(/[\d.]+/g) || [];
    expect(tintNumbers.slice(0, 3).join(',')).withContext('the tint must be the same hue as the solid token').toBe(solidChannels);
    expect(parseFloat(tintNumbers[3] ?? '1')).withContext('the tint must carry the 0.1 alpha it asked for').toBeCloseTo(0.1, 2);
  });
});
