import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from './icon.component';

/** A pill in a tile's head, e.g. "▲ 12.8% vs yest." or "3 Cooking". */
export interface KpiBadge {
  text: string;
  tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'brand';
}

/** One slice of a tile's tender split (cash vs UPI). `percent` is 0–100. */
export interface KpiSegment {
  label: string;
  value: string;
  percent: number;
  tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'brand';
}

/**
 * One KPI tile, as every page should read it.
 *
 * Before this existed, four pages each shipped their own summary strip —
 * `.fleet-tile` on Terminals (icon + label + metric), `.stat-card` on the
 * business detail (label + value), `.bento-kpi` on Daily Closing
 * (label + value + sub-line) and `.kpi-mini` on the platform dashboard
 * (label + value inline). Same information, four anatomies: different label
 * casing, different value weight, units sometimes inside the value and
 * sometimes beside it. Moving between pages meant re-learning where to look.
 *
 * The anatomy here is fixed, in this order, for every tile and every page:
 *
 *   [optional icon]
 *   LABEL                      uppercase, secondary ink   [badge]
 *   VALUE  unit                 value is the largest thing in the tile
 *   [tender split bar + legend]  optional, two-segment only
 *   caption                     one muted line of context
 *   [foot action]               optional, pinned to the tile's bottom
 *
 * A unit never goes inside the value string — callers pass it as `unit` so it
 * renders in the secondary ink and a ₹ number and a count scan the same way.
 * A tile that needs attention sets `alert`, which tints the tile border and
 * background; the tone colours only the value.
 *
 * `emphasis` marks the one figure the page is really about (today's sales,
 * net realized revenue). It raises the value's size and tints the tile, but it
 * keeps the same grid cell, so the row still reads left to right as one line.
 */
export interface KpiItem {
  /** Short uppercase label, e.g. "Registered terminals". */
  label: string;
  /** The number (or short status) the tile exists to show. */
  value: string | number;
  /** Secondary suffix after the value, e.g. "/ 5 max" or "pending". */
  unit?: string;
  /** One muted line under the value. */
  caption?: string;
  /** Colours the value only. Defaults to the ordinary foreground ink. */
  tone?: 'default' | 'success' | 'warning' | 'danger' | 'brand';
  /** A name from the shared `kb-icon` set, rendered above the label. */
  icon?: string;
  /** Tints the whole tile, for a metric that is over a limit or has work waiting. */
  alert?: boolean;
  /** Marks the page's primary figure. Bigger value, tinted tile. */
  emphasis?: boolean;
  /** A short state pill in the head, e.g. a trend or a work-in-progress count. */
  badge?: KpiBadge;
  /** The tender split, rendered as one proportional bar plus a label/value legend. */
  segments?: KpiSegment[];
  /** A foot action; clicking it emits `action` with this whole item. */
  action?: { label: string };
}

@Component({
  selector: 'app-kpi-row',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, IconComponent],
  template: `
    <section class="kb-kpi-row" role="group" [attr.aria-label]="ariaLabel">
      <article
        class="kb-kpi"
        *ngFor="let item of items; trackBy: trackByLabel"
        [class.kb-kpi--alert]="item.alert"
        [class.kb-kpi--hero]="item.emphasis"
        [class.kb-kpi--success]="item.tone === 'success'"
        [class.kb-kpi--warning]="item.tone === 'warning'"
        [class.kb-kpi--danger]="item.tone === 'danger'"
        [class.kb-kpi--brand]="item.tone === 'brand'"
      >
        <span class="kb-kpi__icon" *ngIf="item.icon" aria-hidden="true">
          <kb-icon [name]="item.icon" [size]="16"></kb-icon>
        </span>

        <div class="kb-kpi__head">
          <span class="kb-kpi__label">{{ item.label }}</span>
          <span
            class="kb-kpi__badge"
            *ngIf="item.badge"
            [ngClass]="'kb-kpi__badge--' + (item.badge.tone || 'neutral')"
            >{{ item.badge.text }}</span
          >
        </div>

        <span class="kb-kpi__value">
          {{ item.value }}<small *ngIf="item.unit">{{ item.unit }}</small>
        </span>

        <!-- The bar is a picture of the split; the legend under it carries the
             same numbers as text, so nothing is only conveyed by colour. -->
        <div class="kb-kpi__segments" *ngIf="item.segments?.length">
          <div class="kb-kpi__segment-bar" aria-hidden="true">
            <span
              *ngFor="let s of item.segments"
              class="kb-kpi__segment-fill"
              [ngClass]="'kb-kpi__segment-fill--' + (s.tone || 'neutral')"
              [style.flexGrow]="s.percent"
            ></span>
          </div>
          <dl class="kb-kpi__segment-legend">
            <div class="kb-kpi__segment" *ngFor="let s of item.segments">
              <dt class="kb-kpi__segment-label">
                <span
                  class="kb-kpi__segment-dot"
                  [ngClass]="'kb-kpi__segment-fill--' + (s.tone || 'neutral')"
                  aria-hidden="true"
                ></span>
                {{ s.label }}
              </dt>
              <dd class="kb-kpi__segment-value">
                {{ s.value }}
                <small *ngIf="s.percent > 0">{{ segmentPercent(s.percent) }}</small>
              </dd>
            </div>
          </dl>
        </div>

        <span class="kb-kpi__caption" *ngIf="item.caption">{{ item.caption }}</span>

        <button
          type="button"
          class="kb-kpi__action"
          *ngIf="item.action"
          [attr.aria-label]="item.action.label + ' — ' + item.label"
          (click)="emitAction(item)"
        >
          {{ item.action.label }}
          <span class="kb-kpi__action-arrow" aria-hidden="true">→</span>
        </button>
      </article>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }

      .kb-kpi-row {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(215px, 1fr));
        gap: 0.85rem;
      }

      .kb-kpi {
        display: flex;
        flex-direction: column;
        align-content: start;
        gap: 0.3rem;
        padding: 0.95rem 1.1rem;
        background: var(--kb-color-surface);
        border: 1px solid var(--kb-color-border);
        border-radius: var(--kb-radius-xl, 12px);
        min-width: 0;
      }

      /* A tile carrying an alert keeps the same anatomy and only tints itself, so
         the row still scans left to right at one rhythm. */
      .kb-kpi--alert {
        border-color: rgba(var(--kb-color-warning-rgb), 0.45);
        background: rgba(var(--kb-color-warning-rgb), 0.05);
      }

      /* The page's primary figure: same cell, same anatomy, more weight. */
      .kb-kpi--hero {
        border-color: rgba(var(--kb-color-primary-rgb), 0.28);
        background: linear-gradient(
          180deg,
          rgba(var(--kb-color-primary-rgb), 0.06),
          var(--kb-color-surface) 60%
        );
      }

      .kb-kpi__icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 26px;
        height: 26px;
        margin-bottom: 0.15rem;
        border-radius: var(--kb-radius-md, 6px);
        background: var(--kb-color-surface-2);
        color: var(--kb-color-muted-foreground);
      }

      /* The badge drops to its own line rather than squeezing the label out:
         a truncated "TODAY'S GROSS…" costs more than a slightly taller tile. */
      .kb-kpi__head {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.25rem 0.5rem;
        min-width: 0;
      }

      .kb-kpi__label {
        font-size: 0.72rem;
        font-weight: 700;
        line-height: 1.2;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--kb-color-muted-foreground);
        overflow-wrap: anywhere;
      }

      .kb-kpi__badge {
        flex: none;
        margin-left: auto;
        padding: 0.12rem 0.45rem;
        border: 1px solid transparent;
        border-radius: 999px;
        font-size: 0.68rem;
        font-weight: 600;
        line-height: 1.35;
        letter-spacing: 0.01em;
        white-space: nowrap;
      }

      .kb-kpi__badge--neutral {
        background: var(--kb-color-surface-2);
        border-color: var(--kb-color-border);
        color: var(--kb-color-muted-foreground);
      }

      .kb-kpi__badge--success {
        background: rgba(var(--kb-color-success-rgb), 0.1);
        color: var(--kb-color-success);
      }

      .kb-kpi__badge--warning {
        background: rgba(var(--kb-color-warning-rgb), 0.12);
        color: var(--kb-color-warning);
      }

      .kb-kpi__badge--danger {
        background: rgba(var(--kb-color-danger-rgb), 0.1);
        color: var(--kb-color-danger);
      }

      .kb-kpi__badge--brand {
        background: rgba(var(--kb-color-primary-rgb), 0.1);
        color: var(--kb-color-link);
      }

      .kb-kpi__value {
        font-size: 1.45rem;
        font-weight: 700;
        line-height: 1.15;
        letter-spacing: -0.02em;
        color: var(--kb-color-foreground);
        font-variant-numeric: tabular-nums;
        /* Money values can be long; wrap rather than push the tile wider. */
        overflow-wrap: anywhere;
      }

      .kb-kpi--hero .kb-kpi__value {
        font-size: 1.8rem;
      }

      .kb-kpi__value small {
        margin-left: 0.3rem;
        font-size: 0.8rem;
        font-weight: 500;
        letter-spacing: 0;
        color: var(--kb-color-muted-foreground);
      }

      .kb-kpi__segments {
        display: grid;
        gap: 0.3rem;
        margin-top: 0.15rem;
      }

      .kb-kpi__segment-bar {
        display: flex;
        height: 6px;
        border-radius: 999px;
        overflow: hidden;
        background: var(--kb-color-surface-2);
      }

      .kb-kpi__segment-fill {
        flex-basis: 0;
        min-width: 0;
        background: var(--kb-color-muted-foreground);
      }

      .kb-kpi__segment-fill--neutral {
        background: var(--kb-color-muted-foreground);
      }

      .kb-kpi__segment-fill--success {
        background: var(--kb-color-success);
      }

      .kb-kpi__segment-fill--warning {
        background: var(--kb-color-warning);
      }

      .kb-kpi__segment-fill--danger {
        background: var(--kb-color-danger);
      }

      .kb-kpi__segment-fill--brand {
        background: var(--kb-color-link);
      }

      .kb-kpi__segment-legend {
        display: grid;
        gap: 0.15rem;
        margin: 0;
      }

      .kb-kpi__segment {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 0.5rem;
        font-size: 0.72rem;
      }

      .kb-kpi__segment-label {
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
        color: var(--kb-color-muted-foreground);
      }

      .kb-kpi__segment-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
      }

      .kb-kpi__segment-value {
        margin: 0;
        color: var(--kb-color-foreground);
        font-variant-numeric: tabular-nums;
      }

      .kb-kpi__segment-value small {
        margin-left: 0.25rem;
        color: var(--kb-color-muted-foreground);
      }

      .kb-kpi__caption {
        font-size: 0.76rem;
        line-height: 1.35;
        color: var(--kb-color-muted-foreground);
      }

      .kb-kpi__action {
        align-self: flex-start;
        margin-top: auto;
        padding: 0.35rem 0 0;
        border: 0;
        background: none;
        color: var(--kb-color-link);
        font: inherit;
        font-size: 0.76rem;
        font-weight: 600;
        cursor: pointer;
      }

      .kb-kpi__action:hover {
        text-decoration: underline;
      }

      .kb-kpi__action:focus-visible {
        outline: 2px solid var(--kb-color-primary);
        outline-offset: 2px;
        border-radius: var(--kb-radius-sm, 4px);
      }

      .kb-kpi__action-arrow {
        margin-left: 0.15rem;
      }

      .kb-kpi--success .kb-kpi__value { color: var(--kb-color-success); }
      .kb-kpi--warning .kb-kpi__value { color: var(--kb-color-warning); }
      .kb-kpi--danger .kb-kpi__value { color: var(--kb-color-danger); }
      .kb-kpi--brand .kb-kpi__value { color: var(--kb-color-link); }

      @media (max-width: 640px) {
        .kb-kpi-row {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class KpiRowComponent {
  @Input({ required: true }) items: KpiItem[] = [];
  /** Announced for the group; every page passes its own ("Terminal fleet overview"). */
  @Input() ariaLabel = 'Summary metrics';

  /** Fired by a tile's foot action, carrying the item whose action was clicked. */
  @Output() action = new EventEmitter<KpiItem>();

  trackByLabel = (_: number, item: KpiItem) => item.label;

  /** `percent` is a 0–100 share; a rounded label is all a legend needs. */
  segmentPercent(percent: number): string {
    return `${Math.round(Number(percent) || 0)}%`;
  }

  emitAction(item: KpiItem): void {
    this.action.emit(item);
  }
}
