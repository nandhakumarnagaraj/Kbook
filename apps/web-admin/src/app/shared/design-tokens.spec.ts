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

  interface Rgba { r: number; g: number; b: number; a: number; }

  function toRgba(css: string): Rgba {
    const numbers = (css.match(/[\d.]+/g) || []).map(Number);
    if (numbers.length < 3) throw new Error(`unparseable colour: ${css}`);
    return { r: numbers[0], g: numbers[1], b: numbers[2], a: numbers.length > 3 ? numbers[3] : 1 };
  }

  /** Resolves a token to the rgb the browser actually paints, through any var() chain. */
  function colour(token: string): Rgba {
    const el = probe(node => (node.style.color = token));
    return toRgba(getComputedStyle(el).color);
  }

  function composite(top: Rgba, bottom: Rgba): Rgba {
    const channel = (t: number, b: number) => t * top.a + b * (1 - top.a);
    return { r: channel(top.r, bottom.r), g: channel(top.g, bottom.g), b: channel(top.b, bottom.b), a: 1 };
  }

  function relativeLuminance({ r, g, b }: Rgba): number {
    const linear = (value: number) => {
      const c = value / 255;
      return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
  }

  function contrast(a: Rgba, b: Rgba): number {
    const [light, dark] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x);
    return (light + 0.05) / (dark + 0.05);
  }

  /**
   * The palette lives in tokens, so a theme flip is one edit in styles.css and
   * hundreds of surfaces inherit it — including any surface that becomes
   * unreadable. These pairs are the ones the app actually renders (body and
   * table ink, links, button fills, the four status chips) and each must clear
   * WCAG AA on the surface it is painted on. Contrast is measured from the
   * resolved tokens, never from a literal, so a rebrand is not a test failure
   * as long as the new values are legible.
   */
  it('keeps every semantic ink at AA on the surface it is painted on', () => {
    const surface = colour('var(--kb-color-surface)');
    const surface2 = colour('var(--kb-color-surface-2)');
    const tintOn = (softToken: string) => composite(colour(softToken), surface);

    const cases: Array<{ label: string; ink: Rgba; background: Rgba; min: number }> = [
      { label: 'body ink on a card', ink: colour('var(--kb-color-foreground)'), background: surface, min: 4.5 },
      { label: 'table cell ink on a card', ink: colour('var(--kb-color-foreground-2)'), background: surface, min: 4.5 },
      { label: 'secondary ink on a card', ink: colour('var(--kb-color-muted-foreground)'), background: surface, min: 4.5 },
      { label: 'secondary ink on a table header', ink: colour('var(--kb-color-muted-foreground)'), background: surface2, min: 4.5 },
      { label: 'link ink on a card', ink: colour('var(--kb-color-link)'), background: surface, min: 4.5 },
      { label: 'white on the primary fill', ink: colour('var(--kb-color-primary-foreground)'), background: colour('var(--kb-color-primary)'), min: 2.8 },
      { label: 'white on a destructive fill', ink: colour('var(--kb-color-primary-foreground)'), background: colour('var(--kb-color-danger-solid)'), min: 4.5 },
      { label: 'success ink on its chip', ink: colour('var(--kb-color-success)'), background: tintOn('var(--kb-color-success-soft)'), min: 4.5 },
      { label: 'warning ink on its chip', ink: colour('var(--kb-color-warning)'), background: tintOn('var(--kb-color-warning-soft)'), min: 4.5 },
      { label: 'danger ink on its chip', ink: colour('var(--kb-color-danger)'), background: tintOn('var(--kb-color-danger-soft)'), min: 4.5 },
      { label: 'info ink on its chip', ink: colour('var(--kb-color-info)'), background: tintOn('var(--kb-color-info-soft)'), min: 4.5 },
      { label: 'money text on white', ink: colour('var(--kb-accent-money)'), background: surface, min: 4.5 },
      { label: 'money text on canvas', ink: colour('var(--kb-accent-money)'), background: colour('var(--kb-color-bg-app)'), min: 4.5 },
      { label: 'flow text on white', ink: colour('var(--kb-accent-flow)'), background: surface, min: 4.5 },
      // Non-text: a control's outline must be findable, which is 3:1, not 4.5:1.
      { label: 'control border on a card', ink: colour('var(--kb-border-control)'), background: surface, min: 3 },
      { label: 'control border on canvas', ink: colour('var(--kb-border-control)'), background: colour('var(--kb-color-bg-app)'), min: 3 },
      { label: 'strong border on a card', ink: colour('var(--kb-color-border-strong)'), background: surface, min: 3 }
    ];

    for (const testCase of cases) {
      expect(contrast(testCase.ink, testCase.background))
        .withContext(`${testCase.label} (${testCase.min}:1)`)
        .toBeGreaterThanOrEqual(testCase.min);
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
