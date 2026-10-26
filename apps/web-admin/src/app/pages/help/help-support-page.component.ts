import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../shared/page-header.component';

/**
 * Help & Support.
 *
 * Deliberately static: it answers the questions the app can answer from its own
 * navigation (where a setting lives, why a category will not delete, what an
 * offline terminal means) and hands everything else to support. It calls no API,
 * so it cannot break when the backend is down — which is exactly when someone
 * opens the help page.
 *
 * A ticket list is NOT here. There is no ticketing backend, and a page that
 * showed an empty "your tickets" table would be worse than no page.
 */
@Component({
  selector: 'app-help-support-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageHeaderComponent, RouterLink],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Help &amp; Support"
        subtitle="Where things live, what to do when a terminal misbehaves, and how to reach us."
      ></app-page-header>

      <section class="panel help-section" aria-labelledby="help-start">
        <h2 class="help-section__title" id="help-start">Start here</h2>
        <p class="muted help-section__lead">
          The three jobs that set a new counter running. Each one opens the page that does it.
        </p>

        <div class="help-cards">
          <a class="help-card" routerLink="/business/menu">
            <strong>Build your menu</strong>
            <span>Dishes, categories and portion prices. Star the ones that lead their section.</span>
          </a>
          <a class="help-card" routerLink="/business/terminals">
            <strong>Connect a terminal</strong>
            <span>Register a billing device, see which are online, and recover one that stopped syncing.</span>
          </a>
          <a class="help-card" routerLink="/business/daily-closing">
            <strong>Close the day</strong>
            <span>Count the cash, see the variance against expected, and lock the day's figures.</span>
          </a>
        </div>
      </section>

      <section class="panel help-section" aria-labelledby="help-faq">
        <h2 class="help-section__title" id="help-faq">Common questions</h2>

        <details class="help-faq">
          <summary>A terminal shows offline, or keeps saying it has a pending sync.</summary>
          <p>
            Check the device's connection first. If it is online but still shows pending work, open
            <a routerLink="/business/terminals">Terminals</a>, find the device in the fleet list and use
            its recovery action. If it stays stuck, contact support with the terminal name and the time
            it stopped — those two facts are what we need to trace the queued bills.
          </p>
        </details>

        <details class="help-faq">
          <summary>An order is not showing in today's sales.</summary>
          <p>
            An order that is still open on a table is a draft until it is settled, so it appears on the
            live board but not in the day's totals. Settle it from
            <a routerLink="/business/orders">Orders</a>, then check the order history for the day.
          </p>
        </details>

        <details class="help-faq">
          <summary>Where do I turn on UPI, card, or GST?</summary>
          <p>
            Both live in <a routerLink="/business/settings">Business Settings</a>: payment channels and the
            UPI handle under Payment Channels, and the tax numbers under the tax section. They are settings,
            not separate pages, so this is the only place to change them.
          </p>
        </details>

        <details class="help-faq">
          <summary>Why can't I delete a category?</summary>
          <p>
            A category that still holds dishes cannot be deleted, so a stray click cannot orphan the menu.
            In <a routerLink="/business/menu">Menu</a> open Categories, use <em>Move dishes</em> to empty it
            into another category, then delete it.
          </p>
        </details>

        <details class="help-faq">
          <summary>A dish keeps reverting to available, or to out of stock.</summary>
          <p>
            Availability is shared by every terminal. If the state keeps changing under you, another device
            is changing it — check who else is signed in before assuming the setting is broken.
          </p>
        </details>
      </section>

      <section class="panel help-section" aria-labelledby="help-contact">
        <h2 class="help-section__title" id="help-contact">Contact support</h2>
        <p class="help-section__lead muted">
          Reach us from the KhanaBook site. Include your restaurant name, the terminal involved and roughly
          when the problem started — with those three we can usually answer on the first reply.
        </p>
        <a
          class="header-action-btn header-action-btn--primary help-contact-link"
          href="https://khanabook.in/contact-us"
          target="_blank"
          rel="noopener noreferrer"
        >
          Contact support
        </a>
      </section>
    </div>
  `,
  styles: [
    `
      .help-section + .help-section {
        margin-top: var(--kb-space-4);
      }

      .help-section__title {
        margin: 0;
        font-size: var(--kb-text-xl);
        font-weight: 700;
        color: var(--kb-color-foreground);
        letter-spacing: -0.01em;
      }

      .help-section__lead {
        margin: var(--kb-space-1) 0 var(--kb-space-4);
        font-size: var(--kb-text-md);
      }

      /* Cards, not a link list: each one is a destination with a reason. */
      .help-cards {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: var(--kb-space-3);
      }

      .help-card {
        display: flex;
        flex-direction: column;
        gap: var(--kb-space-1);
        padding: var(--kb-space-4);
        border: 1px solid var(--kb-color-border);
        border-radius: var(--kb-radius-lg);
        background: var(--kb-color-surface-2);
        color: inherit;
        text-decoration: none;
        transition: border-color 150ms ease, background 150ms ease;
      }

      .help-card:hover {
        border-color: var(--kb-color-primary);
        background: var(--kb-color-surface);
      }

      .help-card:focus-visible {
        outline: 2px solid var(--kb-color-primary);
        outline-offset: 2px;
      }

      .help-card strong {
        font-size: var(--kb-text-md);
        font-weight: 700;
        color: var(--kb-color-foreground);
      }

      .help-card span {
        font-size: var(--kb-text-sm);
        color: var(--kb-color-muted-foreground);
        line-height: 1.45;
      }

      /* Native disclosure: keyboard support and find-in-page reveal come free,
         which a hand-rolled accordion would have to re-earn. */
      .help-faq {
        border-bottom: 1px solid var(--kb-color-border);
      }

      .help-faq:last-of-type {
        border-bottom: 0;
      }

      .help-faq summary {
        padding: var(--kb-space-3) 0;
        font-size: var(--kb-text-md);
        font-weight: 600;
        color: var(--kb-color-foreground);
        cursor: pointer;
        list-style-position: inside;
      }

      .help-faq summary:hover {
        color: var(--kb-color-link);
      }

      .help-faq summary:focus-visible {
        outline: 2px solid var(--kb-color-primary);
        outline-offset: 2px;
        border-radius: var(--kb-radius-sm);
      }

      .help-faq p {
        margin: 0 0 var(--kb-space-3);
        font-size: var(--kb-text-md);
        line-height: 1.55;
        color: var(--kb-color-foreground-2);
        max-width: 78ch;
      }

      .help-faq a {
        color: var(--kb-color-link);
        font-weight: 600;
      }

      .help-contact-link {
        text-decoration: none;
      }
    `,
  ],
})
export class HelpSupportPageComponent {}
