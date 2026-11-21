import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  NgZone,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

/**
 * A canvas that shows the frame matching the scroll position.
 *
 * WHY: a short animation played on scroll reads as "video the visitor controls",
 * and frame-by-frame scrubbing (unlike `video.currentTime` seeking) is instant
 * and works backward without re-buffering. The technique is plain image
 * sequences: the section is a tall track, the canvas is sticky inside it, and
 * scroll position selects a frame.
 *
 * WHAT: renders `frameCount` images from `frameBase` on a canvas fixed to the
 * viewport while the track scrolls past, then leaves the canvas behind.
 *
 * HOW: progress is `-rect.top / (rect.height - viewportHeight)` on the track,
 * clamped to [0, 1] (see `scrollProgress`); the frame is
 * `round(progress * (frameCount - 1))` (see `frameIndexFor`). The painted frame
 * follows the target through an exponential smoother so a wheel notch glides
 * instead of jumping, and only the chosen frame is redrawn.
 *
 * The scrub position is also published to CSS as `--kb-sequence-progress` (0–1)
 * on the stage, so projected copy can change with the animation without the host
 * subscribing to an event that would run change detection on every rAF tick.
 *
 * LIMITATIONS: not a video element — no audio, no playback rate, and the frame
 * budget is the page weight (120–200 WebP frames is the practical range). Frames
 * are assumed to share one aspect ratio; `cover` cropping uses the first loaded
 * frame's dimensions.
 */
@Component({
  selector: 'kb-scroll-sequence',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      #track
      class="sequence"
      [class.sequence--band]="variant() === 'band'"
      [style.--kb-sequence-length]="scrollLength()">
      <div #stage class="sequence-stage">
        <canvas #canvas class="sequence-canvas" role="img" [attr.aria-label]="label()"></canvas>
        <div class="sequence-overlay">
          <ng-content />
        </div>
        @if (percent() < 100) {
          <div
            class="sequence-loader"
            role="progressbar"
            aria-valuemin="0"
            aria-valuemax="100"
            [attr.aria-valuenow]="percent()"
            [attr.aria-label]="'Loading animation, ' + percent() + ' percent'"
          >
            <div class="sequence-loader-bar" [style.width.%]="percent()"></div>
          </div>
        }
      </div>
    </section>
  `,
  styles: `
    :host {
      display: block;
      /* Scroll distance, in viewport heights. 4 means the track is 400svh and the
         canvas scrubs for 300svh, i.e. three screens of scroll for the sequence. */
      --kb-sequence-length: 4;
      /* Painted under the canvas before the first frame arrives. Hosts on a dark
         plane override this to avoid a light flash. */
      --kb-sequence-surface: var(--kb-color-surface-2);
      /* Default before the first scroll tick; the stage overwrites it with the
         live scrub position (see publishProgress). */
      --kb-sequence-progress: 0;
      /* Height of the stage in band mode only. */
      --kb-sequence-stage-height: 12rem;
    }

    .sequence {
      position: relative;
      height: calc(var(--kb-sequence-length) * 100svh);
    }

    /* The stage carries --kb-sequence-progress (0 at the top of the track, 1 at the
       end of the pinned travel). Projected copy inherits it, so an overlay can ramp
       an opacity with calc() and keep the choreography in CSS. */
    .sequence-stage {
      position: sticky;
      top: 0;
      height: 100svh;
      overflow: hidden;
      background: var(--kb-sequence-surface);
    }

    /* Band mode: a short strip that scrubs while it crosses the viewport instead
       of a tall track that pins. The section is only as tall as the stage, so it
       adds no empty scroll to the page it sits on. */
    .sequence--band {
      height: auto;
    }

    .sequence--band .sequence-stage {
      position: relative;
      height: var(--kb-sequence-stage-height);
    }

    .sequence-canvas {
      display: block;
      width: 100%;
      height: 100%;
    }

    .sequence-overlay {
      position: absolute;
      inset: 0;
      display: grid;
    }

    /* A hairline across the top edge of the stage: projected hero copy owns the
       bottom of the canvas, so the loader must not sit there. */
    .sequence-loader {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: var(--kb-space-1);
      background: var(--kb-color-border);
      overflow: hidden;
    }

    .sequence-loader-bar {
      height: 100%;
      background: var(--kb-color-primary);
    }

  `,
})
export class ScrollSequenceComponent {
  /** Folder that holds the frames, without a trailing slash (e.g. `/sequence/plate`). */
  readonly frameBase = input.required<string>();
  /** Number of frames in that folder. */
  readonly frameCount = input.required<number>();
  /** File name is `${framePrefix}${index + 1, zero-padded to framePadding}.${frameExtension}`. */
  readonly framePrefix = input('frame-');
  readonly frameExtension = input('webp');
  readonly framePadding = input(3);
  /**
   * Load every nth frame. The band on a data page uses 3, so it pays for a third
   * of the page weight; the lookup falls back to the nearest loaded frame, so a
   * stride only costs smoothness, never a blank canvas.
   */
  readonly frameStride = input(1);
  /** Track height in viewport heights. Higher = the same frames play over more scroll. */
  readonly scrollLength = input(4);
  /**
   * `scrub` pins the stage for `scrollLength` screens; `band` keeps the stage at
   * `--kb-sequence-stage-height` and scrubs while the band crosses the viewport,
   * so it can sit inside a normal page without adding empty scroll.
   */
  readonly variant = input<'scrub' | 'band'>('scrub');
  /** Describes the animation for screen readers; the frames carry the story. */
  readonly label = input('Scroll-driven animation');

  /** Frames that settled (loaded or failed); drives the loader percentage. */
  readonly resolved = signal(0);
  /** Frames actually fetched: one per `frameStride` step through the sequence. */
  readonly slotCount = computed(() => frameSlotCount(this.frameCount(), this.frameStride()));
  readonly percent = computed(() => {
    const slots = this.slotCount();
    if (slots <= 0) {
      return 100;
    }
    return Math.min(100, Math.round((this.resolved() / slots) * 100));
  });

  private readonly track = viewChild.required<ElementRef<HTMLElement>>('track');
  private readonly stage = viewChild.required<ElementRef<HTMLElement>>('stage');
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);

  /** Identifies the current frame set so a stale load cannot write into a new one. */
  private loadToken: object | null = null;
  private frames: (HTMLImageElement | undefined)[] = [];
  private target = 0;
  private current = 0;
  private raf: number | null = null;
  private lastTimestamp = -1;
  private drawnIndex = -1;
  /** Last value written to `--kb-sequence-progress`, so a still page writes nothing. */
  private publishedProgress = -1;
  private destroyed = false;
  private resizeObserver: ResizeObserver | null = null;
  private readonly reducedMotion = () =>
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  constructor() {
    effect(() => {
      const base = this.frameBase().replace(/\/$/, '');
      const count = this.frameCount();
      const stride = this.frameStride();
      // Loading writes `resolved`, and an effect may not write signals by default
      // (NG0600), so the load starts once this effect has finished.
      queueMicrotask(() => {
        if (!this.destroyed) {
          void this.loadFrames(base, count, stride);
        }
      });
    });

    afterNextRender(() => {
      if (this.destroyed) {
        return;
      }
      this.bind();
    });

    this.destroyRef.onDestroy(() => this.teardown());
  }

  private bind(): void {
    this.zone.runOutsideAngular(() => {
      window.addEventListener('scroll', this.onScroll, { passive: true });
      window.addEventListener('resize', this.onResize, { passive: true });

      if (typeof ResizeObserver !== 'undefined') {
        this.resizeObserver = new ResizeObserver(() => this.onResize());
        this.resizeObserver.observe(this.track().nativeElement);
      }

      this.onResize();
      this.onScroll();
    });
  }

  private teardown(): void {
    this.destroyed = true;
    this.loadToken = null;

    // `bind` never ran during server rendering, so there is nothing to detach.
    if (typeof window === 'undefined') {
      return;
    }

    window.removeEventListener('scroll', this.onScroll);
    window.removeEventListener('resize', this.onResize);
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;

    if (this.raf !== null) {
      cancelAnimationFrame(this.raf);
      this.raf = null;
    }
  }

  private readonly onScroll = (): void => {
    const track = this.track().nativeElement;
    const rect = track.getBoundingClientRect();

    this.target =
      this.variant() === 'band'
        ? bandProgress(rect.top, rect.height, window.innerHeight)
        : scrollProgress(rect.top, rect.height, window.innerHeight);

    if (this.reducedMotion()) {
      // Scrubbing still works, it just snaps: the visitor drives the motion, and
      // an eased catch-up would add motion on top of it.
      this.current = this.target;
      this.publishProgress(this.current);
      this.drawFrame(this.frameIndex());
      return;
    }

    if (this.raf === null) {
      this.lastTimestamp = -1;
      this.raf = requestAnimationFrame(this.step);
    }
  };

  private readonly onResize = (): void => {
    this.sizeCanvas();
    this.drawFrame(this.frameIndex(), true);
  };

  private readonly step = (timestamp: number): void => {
    this.raf = null;

    // Frame-rate independent smoothing: the exponential time constant makes the
    // catch-up identical at 60 and 120 Hz. dt is clamped so a background tab that
    // resumes after a long gap does not jump the visible frame to the target.
    const dt = this.lastTimestamp < 0 ? 16.7 : Math.min(64, timestamp - this.lastTimestamp);
    this.lastTimestamp = timestamp;
    const k = 1 - Math.exp(-dt / 90);

    this.current += (this.target - this.current) * k;
    if (Math.abs(this.target - this.current) < 0.0004) {
      this.current = this.target;
    }

    this.drawFrame(this.frameIndex());
    this.publishProgress(this.current);

    if (this.current !== this.target) {
      this.raf = requestAnimationFrame(this.step);
    }
  };

  private frameIndex(): number {
    return frameIndexFor(this.current, this.frameCount());
  }

  /**
   * Publishes the scrub position as `--kb-sequence-progress` on the stage.
   *
   * WHY: the frames carry the motion, but the copy projected over them usually
   * has to change with it (a line per act, a hint that disappears once the
   * visitor is scrolling). A custom property lets the host choreograph that in
   * CSS; an output would emit on every rAF tick and run change detection.
   *
   * WHAT: a number in [0, 1], rounded to three decimals — the value has to be
   * smooth, not exact — written only when it changed since the last frame.
   */
  private publishProgress(progress: number): void {
    const rounded = Math.round(clamp01(progress) * 1000) / 1000;
    if (rounded === this.publishedProgress) {
      return;
    }
    this.publishedProgress = rounded;
    this.stage().nativeElement.style.setProperty('--kb-sequence-progress', `${rounded}`);
  }

  private sizeCanvas(): void {
    const canvas = this.canvas().nativeElement;
    const rect = canvas.getBoundingClientRect();
    // 2x is the ceiling: a 3x phone would quadruple the pixels of every redraw
    // for no visible gain once the source is 960–1280 px wide.
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const width = Math.max(1, Math.round(rect.width * dpr));
    const height = Math.max(1, Math.round(rect.height * dpr));

    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
      this.drawnIndex = -1;
    }
  }

  /**
   * Paints `index`, or the nearest earlier frame that has arrived, so a fast
   * scroll never blanks the canvas back to the placeholder surface.
   */
  private drawFrame(index: number, force = false): void {
    if (force) {
      this.drawnIndex = -1;
    }
    if (index === this.drawnIndex) {
      return;
    }

    const image = this.nearestLoaded(index);
    if (!image) {
      return;
    }

    const canvas = this.canvas().nativeElement;
    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }

    const rect = coverRect(
      image.naturalWidth,
      image.naturalHeight,
      canvas.width,
      canvas.height
    );

    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = 'high';
    if (rect) {
      context.drawImage(image, rect.x, rect.y, rect.width, rect.height);
    } else {
      context.drawImage(image, 0, 0, canvas.width, canvas.height);
    }

    this.drawnIndex = index;
  }

  private slotFor(index: number): number {
    return frameSlotFor(index, this.frameStride(), this.slotCount());
  }

  private nearestLoaded(index: number): HTMLImageElement | undefined {
    for (let i = this.slotFor(index); i >= 0; i--) {
      const frame = this.frames[i];
      if (frame && frame.complete && frame.naturalWidth > 0) {
        return frame;
      }
    }
    return this.frames.find((frame) => frame?.complete && frame.naturalWidth > 0);
  }

  private async loadFrames(base: string, count: number, stride: number): Promise<void> {
    const token = {};
    this.loadToken = token;

    const slots = frameSlotCount(count, stride);
    this.frames = new Array(slots);
    this.resolved.set(0);
    this.drawnIndex = -1;

    // Nothing to fetch during server rendering: there is no Image constructor, and
    // the first client render loads the set anyway.
    if (!slots || !base || typeof Image === 'undefined') {
      return;
    }

    // The first frame is awaited on its own so the opening paint is not queued
    // behind the rest of the sequence.
    await this.loadFrame(0, base, token, stride);
    if (token !== this.loadToken) {
      return;
    }
    this.drawFrame(this.frameIndex(), true);

    const pending = Array.from({ length: slots - 1 }, (_, i) => i + 1);
    const workers = Array.from({ length: Math.min(6, pending.length) }, async () => {
      while (pending.length && token === this.loadToken) {
        await this.loadFrame(pending.shift() as number, base, token, stride);
      }
    });

    await Promise.all(workers);
  }

  /** `slot` is the position in the fetched set; the file is `slot * stride` in the sequence. */
  private loadFrame(slot: number, base: string, token: object, stride: number): Promise<void> {
    return new Promise((resolve) => {
      const settle = () => {
        if (token === this.loadToken) {
          this.resolved.update((value) => value + 1);
          if (slot === this.slotFor(this.frameIndex())) {
            this.drawFrame(this.frameIndex(), true);
          }
        }
        resolve();
      };

      const sequenceIndex = slot * Math.max(1, Math.floor(stride) || 1);
      const image = new Image();
      image.decoding = 'async';
      image.onload = () => {
        if (token === this.loadToken) {
          this.frames[slot] = image;
        }
        settle();
      };
      // A missing frame leaves a gap rather than rejecting: the loader must not
      // hang on one bad file, and `nearestLoaded` covers the hole at paint time.
      image.onerror = settle;
      image.src = `${base}/${this.framePrefix()}${String(sequenceIndex + 1).padStart(this.framePadding(), '0')}.${this.frameExtension()}`;
    });
  }
}

/**
 * How many images a sequence with `frameCount` frames fetches at `stride`.
 *
 * Boundaries: 120 frames at stride 1 is 120 slots, at stride 3 is 40. A stride
 * below 1 (or not a number) is treated as 1, so the set can never be empty for a
 * non-empty sequence; a count of 0 or less has nothing to fetch and returns 0.
 */
export function frameSlotCount(frameCount: number, stride: number): number {
  if (!Number.isFinite(frameCount) || frameCount <= 0) {
    return 0;
  }
  return Math.ceil(frameCount / normaliseStride(stride));
}

/**
 * Slot in the fetched set for a frame index.
 *
 * Slot `n` holds frame `n * stride`, so frame 0-2 at stride 3 are slot 0 and
 * frame 3 is slot 1. The result is clamped to the last slot, because the final
 * partial group (119 at stride 3 is slot 39 of 0-39) must not ask for a frame
 * that was never fetched.
 */
export function frameSlotFor(frameIndex: number, stride: number, slotCount: number): number {
  if (!(slotCount > 0)) {
    return 0;
  }
  const slot = Math.floor(Math.max(0, frameIndex) / normaliseStride(stride));
  return Math.min(slotCount - 1, slot);
}

/** A stride is a positive whole number of frames; anything else means "every frame". */
function normaliseStride(stride: number): number {
  const step = Math.floor(stride);
  return Number.isFinite(step) && step >= 1 ? step : 1;
}

/**
 * Clamps a scroll fraction to the [0, 1] range the frame lookup expects.
 * `NaN` (an unmeasurable element) reports 0; infinities clamp like any other
 * out-of-range number.
 */
export function clamp01(value: number): number {
  if (Number.isNaN(value)) {
    return 0;
  }
  return value <= 0 ? 0 : value >= 1 ? 1 : value;
}

/**
 * Frame for a scroll progress.
 *
 * Boundaries: progress 0 → frame 0, progress 1 → frame `frameCount - 1`
 * (so the last frame is reachable and only at the end), progress in between is
 * rounded — 0.5 of 120 frames is `round(0.5 × 119) = 60`, i.e. frame 60 of 0–119.
 * A count below 2 has no sequence to scrub, so it always returns frame 0.
 */
export function frameIndexFor(progress: number, frameCount: number): number {
  if (!Number.isFinite(frameCount) || frameCount <= 1) {
    return 0;
  }
  return Math.min(frameCount - 1, Math.round(clamp01(progress) * (frameCount - 1)));
}

/**
 * Scrub progress of a sticky track.
 *
 * `travel = sectionHeight - viewportHeight` is the distance the track moves while
 * its sticky child is pinned. Progress is 0 when the track's top is at the
 * viewport top (`sectionTop = 0`) and 1 when its bottom reaches the viewport
 * bottom (`sectionTop = -travel`). A track no taller than the viewport has no
 * pinned travel, so it reports 0 rather than dividing by zero.
 */
export function scrollProgress(
  sectionTop: number,
  sectionHeight: number,
  viewportHeight: number
): number {
  const travel = sectionHeight - viewportHeight;
  if (!(travel > 0)) {
    return 0;
  }
  return clamp01(-sectionTop / travel);
}

/**
 * Scrub progress of a band that scrolls with the page.
 *
 * Boundaries: 0 when the band's top first reaches the viewport bottom (nothing
 * visible yet), 1 when its bottom leaves the viewport top. The denominator is the
 * distance the band travels through the viewport — viewport + band height — so a
 * taller band plays the same frames over a longer scroll. A degenerate geometry
 * (nothing to travel) reports 0 instead of dividing by zero.
 */
export function bandProgress(top: number, height: number, viewportHeight: number): number {
  const travel = viewportHeight + height;
  if (!(travel > 0)) {
    return 0;
  }
  return clamp01((viewportHeight - top) / travel);
}

/**
 * `cover` fit for a frame drawn on a canvas of another aspect ratio.
 *
 * Scales by the larger ratio so the frame fills the canvas, and centres the
 * overflow. Returns `null` when either side is unknown (an image that failed to
 * decode), which the caller handles by stretching the draw.
 */
export function coverRect(
  sourceWidth: number,
  sourceHeight: number,
  targetWidth: number,
  targetHeight: number
): { x: number; y: number; width: number; height: number } | null {
  if (!(sourceWidth > 0) || !(sourceHeight > 0) || !(targetWidth > 0) || !(targetHeight > 0)) {
    return null;
  }

  const scale = Math.max(targetWidth / sourceWidth, targetHeight / sourceHeight);
  const width = sourceWidth * scale;
  const height = sourceHeight * scale;

  return {
    x: (targetWidth - width) / 2,
    y: (targetHeight - height) / 2,
    width,
    height,
  };
}
