import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Canonical page header used by every web-admin page.
 *
 * Before this existed, all ten pages hand-rolled their own header markup
 * (`.header-left`/`.header-title-row`/`.header-sub`/`.header-right`,
 * `.dash-header*`, `.page-header`) each with slightly different sizes,
 * gaps and heading levels — which is why pages looked like different designs.
 *
 * Usage:
 *   <app-page-header title="Inventory" subtitle="Track raw materials">
 *     <button class="header-action-btn" ...>Refresh</button>
 *   </app-page-header>
 *
 * Optional projected badge next to the title. There is exactly one badge vocabulary,
 * the shared `.kpi-tag` in styles/bento.css:
 *
 *   kpi-tag            a count, or a state that is simply off
 *   kpi-tag--green     healthy / live / settled
 *   kpi-tag--amber     needs attention
 *   kpi-tag--warn      fault or danger
 *   kpi-tag--neutral   muted, on the recessed surface
 *
 * Put `<span class="pulse-indicator" aria-hidden="true"></span>` first when the thing
 * is live right now. Do not invent a page-local badge class: `.active-badge`,
 * `.staff-count-badge`, `.settle-badge` and `.sync-state-badge` all existed once and
 * meant slightly different things, which is the drift
 * docs/design/DESIGN_SYSTEM_FREEZE.md rule 3 exists to stop.
 *
 * Usage:
 *   <span kbHeaderBadge class="kpi-tag kpi-tag--green">Live</span>
 *   <span kbHeaderBadge class="kpi-tag">3/3 Active Accounts</span>
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <header class="kb-page-header">
      <div class="kb-page-header__left">
        <div class="kb-page-header__eyebrow" *ngIf="eyebrow">
          <span class="pulse-indicator" *ngIf="live" aria-hidden="true"></span>
          {{ eyebrow }}
        </div>
        <div class="kb-page-header__title-row">
          <!-- One <h1> per page: this is the page's own title, and the shared
               header is the only heading-level owner in the layout. -->
          <h1 class="kb-page-header__title">{{ title }}</h1>
          <ng-content select="[kbHeaderBadge]"></ng-content>
        </div>
        <p class="kb-page-header__sub" *ngIf="subtitle">{{ subtitle }}</p>
      </div>

      <div class="kb-page-header__right">
        <ng-content></ng-content>
      </div>
    </header>
  `,
  styles: [
    `
      :host {
        display: block;
      }

      .kb-page-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1.5rem;
        flex-wrap: wrap;
        margin-bottom: 0.25rem;
      }

      .kb-page-header__left {
        flex: 1 1 20rem;
        min-width: 0;
      }

      .kb-page-header__eyebrow {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.76rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.06em;
        color: var(--kb-color-muted-foreground);
        margin-bottom: 0.25rem;
      }

      .kb-page-header__title-row {
        display: flex;
        align-items: center;
        gap: 0.75rem; /* 12px clean separation between heading and status badge */
        flex-wrap: wrap;
      }

      .kb-page-header__title {
        margin: 0;
        font-size: clamp(1.4rem, 2vw, 1.65rem);
        font-weight: 800;
        color: var(--kb-color-foreground);
        letter-spacing: -0.03em;
        line-height: 1.25;
      }

      .kb-page-header__sub {
        margin: 0.25rem 0 0;
        font-size: 0.88rem;
        line-height: 1.45;
        color: var(--kb-color-muted-foreground);
        max-width: 75ch; /* Fits introduction cleanly on single line on desktop/tablet viewports */
      }

      .kb-page-header__right {
        display: flex;
        align-items: center;
        gap: 0.65rem;
        flex-wrap: wrap;
        justify-content: flex-end;
        min-width: 0;
      }

      @media (max-width: 640px) {
        .kb-page-header {
          align-items: flex-start;
          flex-direction: column;
          gap: 0.85rem;
        }

        .kb-page-header__left,
        .kb-page-header__right {
          width: 100%;
        }

        /* The desktop flex-basis is horizontal; reset it when the header stacks. */
        .kb-page-header__left { flex: 0 1 auto; }

        .kb-page-header__right {
          justify-content: flex-start;
          gap: 0.5rem;
        }
      }
    `,
  ],
})
export class PageHeaderComponent {
  @Input({ required: true }) title!: string;
  @Input() subtitle?: string;
  /** Small uppercase label rendered above the title. */
  @Input() eyebrow?: string;
  /** Shows the green live pulse dot after the eyebrow. */
  @Input() live = false;
}
