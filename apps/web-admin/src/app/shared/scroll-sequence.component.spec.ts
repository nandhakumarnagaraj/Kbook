import {
  bandProgress,
  clamp01,
  coverRect,
  frameIndexFor,
  scrollProgress,
} from './scroll-sequence.component';

/**
 * The scroll sequence is a lookup table between two continuous things (scroll
 * position, easing progress) and one discrete thing (a frame). These specs pin
 * the boundaries, because an off-by-one at either end is visible: frame 119 is
 * only reachable at progress 1, and progress 0 must never show frame 1.
 */
describe('scroll-sequence math', () => {
  describe('clamp01', () => {
    it('keeps an in-range fraction unchanged', () => {
      expect(clamp01(0.42)).toBe(0.42);
    });

    it('clamps both ends', () => {
      expect(clamp01(-0.2)).toBe(0);
      expect(clamp01(1.4)).toBe(1);
    });

    it('treats NaN as no progress and infinities as their bound', () => {
      expect(clamp01(NaN)).toBe(0);
      expect(clamp01(Number.POSITIVE_INFINITY)).toBe(1);
      expect(clamp01(Number.NEGATIVE_INFINITY)).toBe(0);
    });
  });

  describe('frameIndexFor', () => {
    it('starts on the first frame', () => {
      expect(frameIndexFor(0, 120)).toBe(0);
    });

    it('reaches the last frame only at progress 1', () => {
      expect(frameIndexFor(1, 120)).toBe(119);
      expect(frameIndexFor(0.999, 120)).toBe(119); // round(118.881)
      expect(frameIndexFor(0.99, 120)).toBe(118); // round(117.81)
    });

    it('rounds to the nearest frame in between', () => {
      // 0.5 x (120 - 1) = 59.5, and Math.round(59.5) = 60.
      expect(frameIndexFor(0.5, 120)).toBe(60);
      // 1 / 119 of the way is exactly the second frame.
      expect(frameIndexFor(1 / 119, 120)).toBe(1);
    });

    it('clamps out-of-range progress instead of asking for a missing file', () => {
      expect(frameIndexFor(-5, 120)).toBe(0);
      expect(frameIndexFor(5, 120)).toBe(119);
    });

    it('has no second frame when the sequence holds one frame or none', () => {
      expect(frameIndexFor(0, 1)).toBe(0);
      expect(frameIndexFor(1, 1)).toBe(0);
      expect(frameIndexFor(0.5, 0)).toBe(0);
    });
  });

  describe('scrollProgress', () => {
    it('is 0 while the track top sits at or below the viewport top', () => {
      expect(scrollProgress(0, 4000, 1000)).toBe(0);
      expect(scrollProgress(500, 4000, 1000)).toBe(0);
    });

    it('counts only the pinned travel, not the track height', () => {
      // 4000px track in a 1000px viewport = 3000px of travel.
      expect(scrollProgress(-1000, 4000, 1000)).toBeCloseTo(1 / 3, 10);
      expect(scrollProgress(-1500, 4000, 1000)).toBe(0.5);
    });

    it('is 1 exactly when the track bottom meets the viewport bottom', () => {
      expect(scrollProgress(-3000, 4000, 1000)).toBe(1);
      expect(scrollProgress(-3600, 4000, 1000)).toBe(1);
    });

    it('reports 0 for a track with no travel, rather than dividing by zero', () => {
      expect(scrollProgress(-10, 1000, 1000)).toBe(0);
      expect(scrollProgress(-10, 400, 1000)).toBe(0);
    });
  });

  describe('bandProgress', () => {
    it('starts when the band first reaches the bottom of the viewport', () => {
      // top === viewportHeight: nothing visible yet.
      expect(bandProgress(800, 200, 800)).toBe(0);
      expect(bandProgress(1200, 200, 800)).toBe(0);
    });

    it('is halfway when the band is centred in the viewport', () => {
      // Travel = 800 + 200 = 1000; top 300 means 500 of it is done.
      expect(bandProgress(300, 200, 800)).toBe(0.5);
    });

    it('reaches 1 when the band leaves the top of the viewport', () => {
      // top === -height: the band's bottom is at the viewport top.
      expect(bandProgress(-200, 200, 800)).toBe(1);
      expect(bandProgress(-400, 200, 800)).toBe(1);
    });

    it('reports 0 for a geometry with no travel, rather than dividing by zero', () => {
      expect(bandProgress(0, 0, 0)).toBe(0);
    });
  });

  describe('coverRect', () => {
    it('fills a same-ratio canvas exactly', () => {
      expect(coverRect(960, 540, 1920, 1080)).toEqual({
        x: 0,
        y: 0,
        width: 1920,
        height: 1080,
      });
    });

    it('scales by the larger ratio and centres the overflow', () => {
      // 1000 / 960 = 1.0417 and 1000 / 540 = 1.8519, so height drives the scale.
      const rect = coverRect(960, 540, 1000, 1000);
      expect(rect).not.toBeNull();
      expect(rect?.height).toBeCloseTo(1000, 10);
      expect(rect?.width).toBeCloseTo(1777.7778, 3);
      expect(rect?.x).toBeCloseTo(-388.8889, 3);
      expect(rect?.y).toBeCloseTo(0, 10);
    });

    it('returns null when a side is unknown, so the caller can fall back', () => {
      expect(coverRect(0, 540, 1000, 1000)).toBeNull();
      expect(coverRect(960, 540, 0, 1000)).toBeNull();
    });
  });
});
