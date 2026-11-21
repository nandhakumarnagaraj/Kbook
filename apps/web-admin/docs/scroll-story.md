# Scroll-driven frame sequence

`kb-scroll-sequence` plays an image sequence under the scroll bar: a tall section
holds a sticky canvas, and the scroll position picks the frame. Scrolling down
runs it forward, scrolling up runs it backward, and there is no video element to
buffer or seek.

Preview it at **`/story`** (`npm start`, then `http://localhost:4200/story`).

- Component: `src/app/shared/scroll-sequence.component.ts`
- Frames: `public/sequence/plate/frame-001.webp … frame-120.webp` (shared by the
  story page and the business-dashboard band)
- Demo page: `src/app/pages/story/story-page.component.ts`
- Frame renderer: `scripts/render-story-frames.mjs`
- Scrub check: `scripts/verify-story.mjs` (compares canvas pixels at three scroll positions)

## Usage

```html
<kb-scroll-sequence
  frameBase="/sequence/plate"
  [frameCount]="120"
  [scrollLength]="4"
  label="A gold thali rotating on a violet table"
>
  <!-- projected over the canvas, position it yourself -->
  <div class="my-hero-copy">…</div>
</kb-scroll-sequence>
```

| Input | Default | Meaning |
|---|---|---|
| `frameBase` (required) | — | Folder holding the frames, no trailing slash. |
| `frameCount` (required) | — | Number of frames in that folder. |
| `framePrefix` | `frame-` | File name before the number. |
| `frameExtension` | `webp` | `webp`, `jpg` … |
| `framePadding` | `3` | `frame-001` is padding 3. |
| `scrollLength` | `4` | Track height in viewport heights. 4 = 400svh, 3 screens of scrubbing. |
| `variant` | `scrub` | `scrub` pins the stage for `scrollLength` screens. `band` keeps the stage at `--kb-sequence-stage-height` and scrubs while the band crosses the viewport, so it can sit inside a normal page without adding empty scroll. |
| `label` | `Scroll-driven animation` | `aria-label` on the canvas; the frames carry the story. |

The host can also set two CSS custom properties:

- `--kb-sequence-length` — same value as `scrollLength`, if you prefer CSS.
- `--kb-sequence-surface` — painted under the canvas before frame 1 arrives.
  Defaults to `--kb-color-surface-2`; set it to your dark plane to avoid a light flash.

Both properties must be set **on the `kb-scroll-sequence` element**, not on an
ancestor: the component declares its own defaults on `:host`, and a specified
value there shadows anything inherited. The same is true of
`--kb-sequence-stage-height` in band mode.

And it reads one property back, so projected copy can change with the scrub
without the host subscribing to an event (which would run change detection on
every animation frame):

- **`--kb-sequence-progress`** — published on the stage, `0` at the top of the
track and `1` at the end of the pinned travel, in steps of 0.001. It inherits
into the projected content, so an overlay can ramp an opacity with `calc()`:

  ```css
  /* visible at the top, gone by 26% of the scrub */
  .copy-act { opacity: clamp(0, calc((0.26 - var(--kb-sequence-progress)) * 8), 1); }
  ```

  `story-page.component.ts` uses three such acts, with act 2 as a trapezoid
  (a rising ramp multiplied by a falling one). Only CSS reads the value; nothing
  in Angular re-renders per frame.

## Making the frames

Frames must share one aspect ratio and be numbered contiguously. The demo uses
**120 frames at 960×540, WebP quality 0.72 — 1.24 MB for the whole sequence**.

From a real video (5–8 seconds, one subject, smooth motion):

```bash
# 15 fps from a 8s clip = 120 frames, scaled to 960 wide
ffmpeg -i clip.mp4 -vf "fps=15,scale=960:-1" -q:v 3 frames/frame-%03d.jpg
```

ezgif's *Video to JPG* works the same way if you would rather not install
ffmpeg. Keep the count inside **120–200**: fewer is choppy, more is a slow first
paint. Then point `frameBase` at the folder and set `frameCount` to the real
number.

The demo sequence is generated, not filmed:

```bash
npm run story:frames                # 120 × 960×540 WebP into public/sequence/plate
npm run story:frames -- --count 150 --quality 0.65
```

## How it behaves

- **First frame first.** Frame 1 is awaited and painted before the rest start;
  the other frames load six at a time and a hairline loader across the top of the
  stage reports the percentage (then disappears).
- **Only the chosen frame is drawn.** A `requestAnimationFrame` loop follows the
  scroll target through an exponential smoother (90 ms time constant), so a wheel
  notch glides instead of jumping, and the loop stops when it catches up.
- **Nearest loaded frame wins.** A fast scroll never blanks the canvas.
- **Reduced motion.** The scrub still works, but it snaps to the target frame
  instead of easing — the visitor is driving the motion, so nothing is added on top.
- **Device pixel ratio is capped at 2.** The canvas is sized to the CSS box times
  `min(dpr, 2)`, and `cover`-cropped so the frame fills any viewport.

## Band mode

`variant="band"` is the version for a page that must stay usable — the owner
dashboard, a settings page. The stage is a fixed-height strip (`10rem` on the
dashboard, set with `--kb-sequence-stage-height`) that scrolls with the page and
scrubs from the moment it enters the viewport to the moment it leaves:

- progress 0 when the band's top reaches the viewport bottom,
- 0.5 when the band is centred,
- 1 when its bottom leaves the viewport top.

The math is `bandProgress` in the component (unit-tested in
`scroll-sequence.component.spec.ts`), and the travel is
`viewportHeight + bandHeight`, so a taller band plays the same frames over a
longer scroll. Because the section is only as tall as the stage, the page gains
no empty scroll and nothing is pushed below the fold.

## Limits

- Crop, not art direction: a portrait phone `cover`s the landscape frame, so the
  sides are cut. Export a second, portrait frame set and swap `frameBase` by
  breakpoint if that framing matters.
- The section is `100svh` tall and the stage is `position: sticky`, so the page
  scrolls normally around it; there is no scroll hijacking.
- This is a scrub, not playback: no audio, no loop, no autoplay.
