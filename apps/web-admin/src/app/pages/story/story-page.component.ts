import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ScrollSequenceComponent } from '../../shared/scroll-sequence.component';

/**
 * The scroll-sequence demo, as a landing page.
 *
 * WHY: a component is only judged on a real page, and the sequence needs a tall
 * track the admin shell cannot give it. So this page is both the reference use
 * of `kb-scroll-sequence` and the brand's pitch: a sticky canvas scrubs 120
 * frames, the hero copy changes with the scrub, and the story, features and
 * call to action follow underneath.
 *
 * WHAT: the hero copy is three acts in one grid; each act sets its own `--act`
 * opacity from `--kb-sequence-progress` (published by the sequence, 0–1), so the
 * text changes at fixed moments of the animation and reverses on the way back
 * up. Only CSS reads the progress — no event, no change detection per frame.
 *
 * HOW the act ramps work: a `clamp(0, calc((edge - p) * slope), 1)` is a linear
 * ramp that reaches 1 at the edge and 0 a fixed distance away; act 2 multiplies
 * a rising ramp by a falling one, which is a trapezoid. All the numbers are
 * fractions of the pinned scroll travel, not pixels.
 *
 * Sections below the track reveal on scroll with a view timeline, guarded by
 * `@supports` (unsupported browsers show them normally) and switched off for
 * reduced motion (see the media query at the bottom).
 *
 * Not in the sidebar and not guarded: it is a preview, not a workspace.
 */
@Component({
  selector: 'app-story-page',
  standalone: true,
  imports: [ScrollSequenceComponent, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="story" id="top">
      <kb-scroll-sequence
        class="story-sequence"
        frameBase="/sequence/plate"
        [frameCount]="120"
        [scrollLength]="4"
        label="A gold thali of seven dishes rotating slowly on a deep violet table"
      >
        <div class="story-hero">
          <header class="story-bar">
            <a class="story-brand" routerLink="/login" aria-label="KhanaBook sign in">
              <img class="story-brand-logo" src="/khanabook_logo-removebg.png" alt="" />
              <span class="story-brand-name">KhanaBook</span>
            </a>
            <span class="story-chip">Scroll demo</span>
          </header>

          <div class="story-copy">
            <div class="story-acts">
              <div class="story-act story-act-1">
                <p class="story-eyebrow">Offline-first restaurant billing</p>
                <h1 class="story-title">Every bill, the moment it happens.</h1>
                <p class="story-lede">
                  A 120-frame sequence tied to the scroll bar. Down plays it forward, up plays
                  it back — no video element, no animation library.
                </p>
              </div>
              <div class="story-act story-act-2">
                <p class="story-eyebrow">No signal, no queue</p>
                <p class="story-title">The wifi drops. The bill still prints.</p>
                <p class="story-lede">
                  The order, the KOT and the payment land in the till's own database first.
                  The cloud catches up when the connection comes back.
                </p>
              </div>
              <div class="story-act story-act-3">
                <p class="story-eyebrow">One shop, every screen</p>
                <p class="story-title">By the time you look, the day is already there.</p>
                <p class="story-lede">
                  The owner's console reads what the terminals pushed: today's sales, the GST
                  split, the staff on shift. In batches, never in a queue.
                </p>
              </div>
            </div>
            <p class="story-hint" aria-hidden="true">Scroll ↓</p>
          </div>
        </div>
      </kb-scroll-sequence>

      <section class="story-note reveal" aria-labelledby="story-note-title">
        <div class="story-note-inner">
          <p class="story-eyebrow">Why it exists</p>
          <h2 class="story-statement" id="story-note-title">
            A busy kitchen should never wait for a connection.
          </h2>
          <p class="story-note-text">
            KhanaBook runs a restaurant on the terminal itself: menu, orders, payments and
            the day's totals live in a local database, so a bill is written and printed
            whether the line is up or not. The web console is the long view — it reads what
            the till pushed and shows it back as today's numbers.
          </p>
          <p class="story-note-text">
            No spinner between the customer asking for the bill and the paper coming out.
            That is the whole design.
          </p>
        </div>
      </section>

      <section class="story-features reveal" aria-labelledby="story-features-title">
        <div class="story-inner">
          <h2 class="story-section-title" id="story-features-title">
            Built for the 9:47 pm rush
          </h2>
          <p class="story-section-lede">
            Three things the till does before the network is even part of the question.
          </p>
          <div class="story-grid">
            <article class="story-card">
              <p class="story-card-figure">01</p>
              <h3 class="story-card-title">Bills that don't ask the internet</h3>
              <p class="story-card-text">
                Order, KOT, part cash/UPI, refund — all written to the device first, then
                queued for sync.
              </p>
            </article>
            <article class="story-card">
              <p class="story-card-figure">02</p>
              <h3 class="story-card-title">GST invoices, on paper</h3>
              <p class="story-card-text">
                GSTIN, CGST/SGST and a thermal receipt on a 58 mm or 80 mm roll — the same
                arithmetic the console reports.
              </p>
            </article>
            <article class="story-card">
              <p class="story-card-figure">03</p>
              <h3 class="story-card-title">The owner's view, live</h3>
              <p class="story-card-text">
                Day summary, staff and reports in the web console. Terminals sync in batches
                and conflicts resolve to one bill.
              </p>
            </article>
          </div>
        </div>
      </section>

      <section class="story-details reveal" id="how" aria-labelledby="story-details-title">
        <div class="story-inner">
          <h2 class="story-section-title" id="story-details-title">How this page works</h2>
          <p class="story-section-lede">
            The animation above is not a video. It is a folder of stills and a scroll
            position.
          </p>
          <div class="story-grid">
            <article class="story-card">
              <p class="story-card-figure">120</p>
              <h3 class="story-card-title">frames, not a video</h3>
              <p class="story-card-text">
                A 5-second animation exported as WebP images. The scroll position picks the
                frame, so scrubbing backwards is instant.
              </p>
            </article>
            <article class="story-card">
              <p class="story-card-figure">1.2 MB</p>
              <h3 class="story-card-title">for the whole sequence</h3>
              <p class="story-card-text">
                Frame one paints immediately; the other 119 load in the background while the
                loader reports progress.
              </p>
            </article>
            <article class="story-card">
              <p class="story-card-figure">0</p>
              <h3 class="story-card-title">extra dependencies</h3>
              <p class="story-card-text">
                One Angular component, a canvas and requestAnimationFrame. Reduced-motion
                users get the same frames without the eased catch-up.
              </p>
            </article>
          </div>
        </div>
      </section>

      <section class="story-cta reveal" aria-labelledby="story-cta-title">
        <div class="story-cta-inner">
          <p class="story-eyebrow">KhanaBook console</p>
          <h2 class="story-cta-title" id="story-cta-title">Open the console.</h2>
          <p class="story-cta-text">
            Sign in with the owner account and see today as the till wrote it.
          </p>
          <div class="story-cta-actions">
            <a class="story-button" routerLink="/login">Sign in to KhanaBook</a>
            <a class="story-button-quiet" href="#how">How this page works</a>
          </div>
        </div>
      </section>

      <footer class="story-footer">
        <div class="story-footer-inner">
          <a class="story-brand" routerLink="/login" aria-label="KhanaBook sign in">
            <img class="story-brand-logo" src="/khanabook_logo-removebg.png" alt="" />
            <span class="story-brand-name">KhanaBook</span>
          </a>
          <p class="story-footer-text">Offline-first billing for Indian kitchens.</p>
          <nav class="story-footer-nav" aria-label="Footer">
            <a class="story-link" routerLink="/login">Sign in</a>
            <a class="story-link" href="#top">Back to top</a>
          </nav>
        </div>
      </footer>
    </main>
  `,
  styles: `
    :host {
      display: block;
    }

    .story {
      background: var(--kb-color-bg-app);
    }

    /* The sticky canvas sits on the brand's dark plane, so the frames and the
       placeholder surface are the same violet and the first paint cannot flash.
       Set on the component itself: the component declares its own default on
       :host, which shadows anything inherited from an ancestor. */
    .story-sequence {
      --kb-sequence-surface: var(--pos-sidebar-peek-bg);
    }

    .story-hero {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      height: 100%;
      padding: var(--kb-space-6);
      box-sizing: border-box;
    }

    .story-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--kb-space-4);
    }

    .story-brand {
      display: inline-flex;
      align-items: center;
      gap: var(--kb-space-2);
      text-decoration: none;
      border-radius: var(--kb-radius-lg);
    }

    .story-brand-logo {
      width: var(--kb-space-8);
      height: var(--kb-space-8);
      border-radius: var(--kb-radius-lg);
      background: var(--kb-color-surface);
      padding: var(--kb-space-1);
    }

    .story-brand-name {
      font-family: var(--kb-font-display);
      font-size: var(--kb-text-lg);
      font-weight: 700;
      color: var(--pos-sidebar-ink);
      letter-spacing: -0.01em;
    }

    .story-chip {
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--kb-color-primary-foreground);
      background: var(--kb-color-primary-strong);
      padding: var(--kb-space-1) var(--kb-space-3);
      border-radius: var(--kb-radius-full);
      white-space: nowrap;
    }

    .story-copy {
      /* Scrub position, inherited from the sequence stage (which declares 0 as the
         pre-scroll default). */
      --p: var(--kb-sequence-progress);
      max-width: 640px;
      /* A scrim derived from the same violet, so the copy stays legible wherever
         the plate happens to be behind it. */
      background: linear-gradient(
        to top,
        color-mix(in srgb, var(--pos-sidebar-peek-bg) 92%, transparent) 0%,
        color-mix(in srgb, var(--pos-sidebar-peek-bg) 78%, transparent) 62%,
        transparent 100%
      );
      border-radius: var(--kb-radius-2xl);
      padding: var(--kb-space-6);
      margin: 0 calc(var(--kb-space-6) * -1) calc(var(--kb-space-6) * -1);
    }

    /* The three acts share one grid cell, so the block does not jump between
       them: its height is the tallest act, not the visible one. */
    .story-acts {
      display: grid;
    }

    .story-act {
      grid-area: 1 / 1;
      align-self: end;
      /* Declared here and overwritten per act below; 0 means "not in this part of
         the scrub", which is also the state before the first tick. */
      --act: 0;
      opacity: var(--act);
      transform: translateY(calc((1 - var(--act)) * var(--kb-space-3)));
    }

    /* Linear ramps over the pinned travel. Act 1 is on at the top and gone by
       0.26; act 2 is a trapezoid between 0.18 and 0.68; act 3 arrives at 0.64
       and stays through the end of the sequence. */
    .story-act-1 {
      --act: clamp(0, calc((0.26 - var(--p)) * 8), 1);
    }

    .story-act-2 {
      --act: calc(
        clamp(0, calc((var(--p) - 0.18) * 8), 1) *
          clamp(0, calc((0.68 - var(--p)) * 8), 1)
      );
    }

    .story-act-3 {
      --act: clamp(0, calc((var(--p) - 0.64) * 8), 1);
    }

    .story-eyebrow {
      margin: 0 0 var(--kb-space-3);
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--pos-sidebar-accent);
    }

    .story-title {
      /* Fluid on purpose: the hero is the one element sized by the viewport, so it
         is a clamp() rather than a scale step. */
      font-family: var(--kb-font-display);
      font-size: clamp(2.25rem, 5.2vw, 4rem);
      line-height: 1.05;
      letter-spacing: -0.025em;
      font-weight: 700;
      color: var(--pos-sidebar-ink);
      margin: 0 0 var(--kb-space-4);
      max-width: 18ch;
    }

    .story-lede {
      margin: 0;
      max-width: 52ch;
      font-size: var(--kb-text-lg);
      line-height: 1.5;
      color: var(--pos-sidebar-text);
    }

    .story-hint {
      margin: var(--kb-space-6) 0 0;
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      color: var(--pos-sidebar-ink-muted);
      /* Gone after the first tenth of the track: once the visitor is scrolling,
         the instruction is noise. */
      opacity: clamp(0, calc((0.1 - var(--p)) * 12), 1);
      /* --p is declared on .story-copy above, so the hint and the acts share it. */
    }

    .story-inner,
    .story-note-inner,
    .story-cta-inner,
    .story-footer-inner {
      max-width: var(--kb-content-max-width);
      margin: 0 auto;
    }

    .story-note {
      background: var(--kb-color-surface);
      padding: var(--kb-space-16) var(--kb-space-6);
      border-top: 1px solid var(--kb-color-border);
    }

    .story-note-inner {
      display: grid;
      grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
      gap: var(--kb-space-4) var(--kb-space-12);
      align-items: start;
    }

    .story-note .story-eyebrow {
      grid-column: 1 / -1;
      color: var(--kb-color-primary-strong);
    }

    .story-statement {
      margin: 0;
      font-family: var(--kb-font-display);
      font-size: clamp(1.75rem, 3.4vw, var(--kb-text-4xl));
      line-height: 1.1;
      letter-spacing: -0.02em;
      font-weight: 700;
      color: var(--kb-color-foreground);
    }

    .story-note-text {
      margin: 0 0 var(--kb-space-4);
      font-size: var(--kb-text-lg);
      line-height: 1.6;
      color: var(--kb-color-muted-foreground);
    }

    .story-note-text:last-child {
      margin-bottom: 0;
    }

    .story-features,
    .story-details {
      padding: var(--kb-space-16) var(--kb-space-6);
    }

    .story-details {
      background: var(--kb-color-surface);
      border-top: 1px solid var(--kb-color-border);
      border-bottom: 1px solid var(--kb-color-border);
    }

    .story-section-title {
      font-family: var(--kb-font-display);
      font-size: var(--kb-text-2xl);
      font-weight: 700;
      letter-spacing: -0.01em;
      color: var(--kb-color-foreground);
      margin: 0 0 var(--kb-space-3);
    }

    .story-section-lede {
      margin: 0 0 var(--kb-space-8);
      max-width: 60ch;
      font-size: var(--kb-text-lg);
      line-height: 1.5;
      color: var(--kb-color-muted-foreground);
    }

    .story-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: var(--kb-space-4);
    }

    .story-card {
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl);
      padding: var(--kb-space-6);
    }

    .story-details .story-card {
      background: var(--kb-color-surface-2);
    }

    .story-card-figure {
      margin: 0;
      font-family: var(--kb-font-mono);
      font-size: var(--kb-text-3xl);
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: var(--kb-color-primary-strong);
    }

    .story-card-title {
      margin: var(--kb-space-2) 0 var(--kb-space-3);
      font-size: var(--kb-text-lg);
      font-weight: 600;
      color: var(--kb-color-foreground);
    }

    .story-card-text {
      margin: 0;
      font-size: var(--kb-text-md);
      line-height: 1.5;
      color: var(--kb-color-muted-foreground);
    }

    .story-cta {
      /* One dark band on a light page: the same violet plane the animation runs
         on, so the call to action and the hero read as the same surface. */
      background: var(--pos-sidebar-peek-bg);
      padding: var(--kb-space-16) var(--kb-space-6);
    }

    .story-cta .story-eyebrow {
      color: var(--pos-sidebar-accent);
    }

    .story-cta-title {
      margin: 0 0 var(--kb-space-4);
      font-family: var(--kb-font-display);
      font-size: clamp(2rem, 4vw, 3rem);
      line-height: 1.05;
      letter-spacing: -0.025em;
      font-weight: 700;
      color: var(--pos-sidebar-ink);
      max-width: 20ch;
    }

    .story-cta-text {
      margin: 0 0 var(--kb-space-8);
      max-width: 46ch;
      font-size: var(--kb-text-lg);
      line-height: 1.5;
      color: var(--pos-sidebar-text);
    }

    .story-cta-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--kb-space-4);
    }

    .story-button {
      display: inline-flex;
      align-items: center;
      min-height: var(--kb-space-10);
      padding: 0 var(--kb-space-6);
      border-radius: var(--kb-radius-lg);
      background: var(--kb-color-primary-strong);
      color: var(--kb-color-primary-foreground);
      font-size: var(--kb-text-md);
      font-weight: 600;
      text-decoration: none;
      border: 1px solid var(--kb-color-primary-strong);
      transition: background-color var(--kb-duration-fast) ease;
    }

    .story-button:hover {
      background: var(--kb-color-primary-strong-hover);
      border-color: var(--kb-color-primary-strong-hover);
    }

    .story-button-quiet {
      color: var(--pos-sidebar-ink);
      font-size: var(--kb-text-md);
      font-weight: 600;
      text-decoration: none;
      border-radius: var(--kb-radius-sm);
      transition: color var(--kb-duration-fast) ease;
    }

    .story-button-quiet:hover {
      color: var(--pos-sidebar-accent);
      text-decoration: underline;
    }

    .story-footer {
      padding: var(--kb-space-8) var(--kb-space-6);
      background: var(--kb-color-surface);
      border-top: 1px solid var(--kb-color-border);
    }

    .story-footer-inner {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--kb-space-4) var(--kb-space-8);
    }

    .story-footer .story-brand-name {
      color: var(--kb-color-foreground);
    }

    .story-footer .story-brand-logo {
      background: var(--kb-color-surface-2);
    }

    .story-footer-text {
      margin: 0;
      font-size: var(--kb-text-md);
      color: var(--kb-color-muted-foreground);
    }

    .story-footer-nav {
      display: flex;
      gap: var(--kb-space-6);
      margin-left: auto;
    }

    .story-link {
      color: var(--kb-color-link);
      font-size: var(--kb-text-md);
      font-weight: 600;
      text-decoration: none;
      border-radius: var(--kb-radius-sm);
      transition: color var(--kb-duration-fast) ease;
    }

    .story-link:hover {
      color: var(--kb-color-link-hover);
      text-decoration: underline;
    }

    .story-link:focus-visible,
    .story-brand:focus-visible,
    .story-button:focus-visible,
    .story-button-quiet:focus-visible {
      outline: 2px solid var(--kb-color-primary);
      outline-offset: 2px;
    }

    /* Progressive enhancement: where view timelines exist, the sections below the
       track rise in as they enter. Without support the rules never apply and the
       content is simply visible — the page must not depend on the motion. */
    @supports (animation-timeline: view()) {
      .reveal {
        animation-name: reveal-up;
        animation-timing-function: linear;
        animation-fill-mode: both;
        animation-timeline: view();
        animation-range: entry 8% cover 32%;
      }
    }

    @keyframes reveal-up {
      from {
        opacity: 0;
        transform: translateY(var(--kb-space-4));
      }

      to {
        opacity: 1;
        transform: none;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .reveal {
        animation: none;
      }
    }

    @media (max-width: 1024px) {
      .story-grid,
      .story-note-inner {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 768px) {
      .story-hero {
        padding: var(--kb-space-4);
      }

      .story-copy {
        margin: 0 calc(var(--kb-space-4) * -1) calc(var(--kb-space-4) * -1);
        padding: var(--kb-space-4);
      }

      .story-note,
      .story-features,
      .story-details,
      .story-cta {
        padding: var(--kb-space-12) var(--kb-space-4);
      }

      .story-footer-nav {
        margin-left: 0;
      }
    }
  `,
})
export class StoryPageComponent {}
