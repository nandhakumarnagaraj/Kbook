import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  signal,
} from '@angular/core';
import { formatCurrency } from './formatters';

/**
 * A number that rolls to its new value instead of snapping to it.
 *
 * The dashboard re-reads ~9 endpoints every 60s, and until now every KPI simply
 * replaced its text: revenue, settled orders, terminals. A value that changes by
 * 12% looked identical to one that changed by nothing. Rolling the number makes
 * the size and direction of the change legible at a glance, which is the whole
 * point of a live cockpit.
 *
 * Adapted for Angular from the `animated-counter` component in Rare UI —
 * https://github.com/swamimalode07/rare-ui · https://rareui.com
 * Rare UI is MIT + Commons Clause + Attribution; the notice is kept here and the
 * project credit lives in README.md.
 *
 * Two things this deliberately does NOT do:
 *   - It never restyles the value. It renders bare text so the surrounding
 *     `.bento-tile__main-metric` keeps owning size, weight and `tabular-nums`
 *     (no digit jitter while rolling). No colour, radius or spacing is added,
 *     per docs/design/DESIGN_SYSTEM_FREEZE.md.
 *   - It does not rely on the global `prefers-reduced-motion` block in
 *     styles.css: that rule caps CSS animation durations, and a
 *     `requestAnimationFrame` loop is immune to it, so the check is repeated
 *     here in JS.
 */
@Component({
  selector: 'app-animated-counter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `{{ display() }}`,
})
export class AnimatedCounterComponent implements OnChanges, OnDestroy {
  /** The number to roll to. */
  @Input({ required: true }) value = 0;
  /** `currency` renders through the shared `formatCurrency` (₹, en-IN). */
  @Input() format: 'plain' | 'currency' = 'plain';
  /** Roll length. Short enough to feel like a refresh, not a loading state. */
  @Input() durationMs = 560;

  readonly display = signal('0');

  private current = 0;
  private from = 0;
  private target = 0;
  private startedAt = -1;
  private frame: number | null = null;
  private readonly numberFormat = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['value'] && !changes['format']) {
      return;
    }

    const next = Number(this.value);
    this.target = Number.isFinite(next) ? next : 0;

    this.cancel();

    // A format-only change needs no motion; re-render the current value.
    if (!changes['value']) {
      this.display.set(this.render(this.current));
      return;
    }

    if (this.durationMs <= 0 || this.prefersReducedMotion()) {
      this.current = this.target;
      this.display.set(this.render(this.target));
      return;
    }

    this.from = this.current;
    // Paint the starting value in the target format straight away: waiting for the
    // first frame would flash the raw initial '0' instead of '₹0.00'.
    this.display.set(this.render(this.from));
    this.startedAt = -1;
    this.frame = requestAnimationFrame(this.step);
  }

  ngOnDestroy(): void {
    this.cancel();
  }

  private readonly step = (timestamp: number): void => {
    this.frame = null;

    // Anchor to the browser's own frame clock so the first frame is not
    // measured against a different time source.
    if (this.startedAt < 0) {
      this.startedAt = timestamp;
    }

    const progress = Math.min(1, (timestamp - this.startedAt) / this.durationMs);
    const eased = 1 - Math.pow(1 - progress, 3); // easeOutCubic
    const rolled = this.from + (this.target - this.from) * eased;
    this.current = progress >= 1 ? this.target : rolled;

    const next = this.render(this.current);
    // Only touch the signal when the rendered text actually changes, so a
    // 560ms roll costs a handful of change-detection passes, not 34.
    if (next !== this.display()) {
      this.display.set(next);
    }

    if (progress < 1) {
      this.frame = requestAnimationFrame(this.step);
    }
  };

  private render(value: number): string {
    return this.format === 'currency' ? formatCurrency(value) : this.numberFormat.format(Math.round(value));
  }

  private cancel(): void {
    if (this.frame !== null) {
      cancelAnimationFrame(this.frame);
      this.frame = null;
    }
  }

  /** Read at roll time, not construction time, so it can be stubbed in specs. */
  private prefersReducedMotion(): boolean {
    return (
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    );
  }
}
