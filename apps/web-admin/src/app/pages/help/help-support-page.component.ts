import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../shared/page-header.component';

/**
 * Help & Support.
 *
 * Deliberately static and offline-safe: it answers operational questions
 * from the app's own navigation (where a setting lives, why a category will not
 * delete, what an offline terminal means) and routes everything else directly to
 * human support.
 *
 * It calls no API, so it functions seamlessly even when the backend or internet
 * connectivity drops — which is precisely when counter staff visit the help desk.
 */
@Component({
  selector: 'app-help-support-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, PageHeaderComponent, RouterLink],
  template: `
    <div class="page-shell help-shell">
      <app-page-header
        title="Help &amp; Support"
        subtitle="Where things live, what to do when a terminal misbehaves, and how to reach us."
      >
        <span kbHeaderBadge class="kpi-tag kpi-tag--green">
          <span class="pulse-indicator" aria-hidden="true"></span>Live Desk
        </span>
        <a
          class="header-action-btn header-action-btn--primary help-contact-link"
          href="https://khanabook.in/contact-us"
          target="_blank"
          rel="noopener noreferrer"
        >
          <svg
            class="header-action-icon"
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
          Contact support
        </a>
      </app-page-header>

      <!-- Section 1: Start here (Core operations) -->
      <section class="panel help-section" aria-labelledby="help-start">
        <div class="help-section__header">
          <div class="help-section__eyebrow">ESSENTIAL WORKFLOWS</div>
          <h2 class="help-section__title" id="help-start">Start here</h2>
          <p class="muted help-section__lead">
            The three jobs that set a new counter running. Each one opens the page that does it.
          </p>
        </div>

        <div class="help-cards">
          <!-- Step 1: Menu -->
          <a class="help-card card-lift" routerLink="/business/menu">
            <div class="help-card__top">
              <div class="help-card__icon-badge help-card__icon-badge--menu">
                <svg
                  viewBox="0 0 24 24"
                  width="20"
                  height="20"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <path d="M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2" />
                  <path d="M7 2v20" />
                  <path d="M21 15V2v0a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7" />
                </svg>
              </div>
              <span class="help-card__step">Step 1 • Menu</span>
            </div>
            <div class="help-card__body">
              <strong>Build your menu</strong>
              <span>Dishes, categories and portion prices. Star the ones that lead their section.</span>
            </div>
            <div class="help-card__action">
              <span>Configure dishes &amp; categories</span>
              <svg
                class="help-card__arrow"
                viewBox="0 0 24 24"
                width="16"
                height="16"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </div>
          </a>

          <!-- Step 2: Terminals -->
          <a class="help-card card-lift" routerLink="/business/terminals">
            <div class="help-card__top">
              <div class="help-card__icon-badge help-card__icon-badge--terminals">
                <svg
                  viewBox="0 0 24 24"
                  width="20"
                  height="20"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
                  <line x1="8" y1="21" x2="16" y2="21" />
                  <line x1="12" y1="17" x2="12" y2="21" />
                </svg>
              </div>
              <span class="help-card__step">Step 2 • Hardware</span>
            </div>
            <div class="help-card__body">
              <strong>Connect a terminal</strong>
              <span>Register a billing device, see which are online, and recover one that stopped syncing.</span>
            </div>
            <div class="help-card__action">
              <span>Manage POS fleet</span>
              <svg
                class="help-card__arrow"
                viewBox="0 0 24 24"
                width="16"
                height="16"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </div>
          </a>

          <!-- Step 3: Daily Closing -->
          <a class="help-card card-lift" routerLink="/business/daily-closing">
            <div class="help-card__top">
              <div class="help-card__icon-badge help-card__icon-badge--closing">
                <svg
                  viewBox="0 0 24 24"
                  width="20"
                  height="20"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <rect x="2" y="4" width="20" height="16" rx="2" />
                  <line x1="2" y1="10" x2="22" y2="10" />
                  <line x1="12" y1="14" x2="12" y2="17" />
                  <line x1="9" y1="17" x2="15" y2="17" />
                </svg>
              </div>
              <span class="help-card__step">Step 3 • Closing</span>
            </div>
            <div class="help-card__body">
              <strong>Close the day</strong>
              <span>Count the cash, see the variance against expected, and lock the day's figures.</span>
            </div>
            <div class="help-card__action">
              <span>Reconcile daily totals</span>
              <svg
                class="help-card__arrow"
                viewBox="0 0 24 24"
                width="16"
                height="16"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </div>
          </a>
        </div>
      </section>

      <!-- Section 2: Common questions (FAQ) -->
      <section class="panel help-section" aria-labelledby="help-faq">
        <div class="help-section__header">
          <div class="help-section__eyebrow">TROUBLESHOOTING &amp; ANSWERS</div>
          <h2 class="help-section__title" id="help-faq">Common questions</h2>
          <p class="muted help-section__lead">
            Instant guidance for device syncing, order settling, GST configuration, and menu management.
          </p>
        </div>

        <!-- Interactive Search & Topic Filter -->
        <div class="faq-controls">
          <div class="faq-search">
            <svg
              class="faq-search__icon"
              viewBox="0 0 24 24"
              width="16"
              height="16"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              class="faq-search__input"
              placeholder="Filter topics by keyword (e.g. offline, sync, orders, gst, dishes)..."
              [value]="searchQuery()"
              (input)="onSearchInput($event)"
              aria-label="Filter questions by keyword"
            />
            <button
              type="button"
              *ngIf="searchQuery()"
              class="faq-search__clear"
              (click)="clearSearch()"
              aria-label="Clear filter query"
            >
              <svg
                viewBox="0 0 24 24"
                width="14"
                height="14"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>

          <div class="faq-filter-chips" role="tablist" aria-label="Filter questions by category">
            <button
              type="button"
              class="faq-filter-chip"
              [class.faq-filter-chip--active]="selectedCategory() === 'all'"
              (click)="selectCategory('all')"
            >
              All topics
            </button>
            <button
              type="button"
              class="faq-filter-chip"
              [class.faq-filter-chip--active]="selectedCategory() === 'hardware'"
              (click)="selectCategory('hardware')"
            >
              Hardware &amp; Sync
            </button>
            <button
              type="button"
              class="faq-filter-chip"
              [class.faq-filter-chip--active]="selectedCategory() === 'orders'"
              (click)="selectCategory('orders')"
            >
              Orders &amp; Sales
            </button>
            <button
              type="button"
              class="faq-filter-chip"
              [class.faq-filter-chip--active]="selectedCategory() === 'settings'"
              (click)="selectCategory('settings')"
            >
              Taxes &amp; UPI
            </button>
            <button
              type="button"
              class="faq-filter-chip"
              [class.faq-filter-chip--active]="selectedCategory() === 'menu'"
              (click)="selectCategory('menu')"
            >
              Menu &amp; Catalog
            </button>
            <button
              type="button"
              class="faq-filter-chip"
              [class.faq-filter-chip--active]="selectedCategory() === 'closing'"
              (click)="selectCategory('closing')"
            >
              Daily Closing
            </button>
          </div>
        </div>

        <div class="faq-list">
          <!-- Question 1: Terminals & Sync -->
          <details
            class="help-faq"
            [hidden]="!matchesFilter('hardware', 'terminal offline pending sync recovery stuck fleet queued bills device')"
          >
            <summary>
              <div class="help-faq__summary-row">
                <span class="help-faq__badge">Hardware</span>
                <span class="help-faq__question-text">
                  A terminal shows offline, or keeps saying it has a pending sync.
                </span>
              </div>
              <span class="help-faq__chevron" aria-hidden="true">
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </span>
            </summary>
            <div class="help-faq__answer">
              <p>
                Check the device's connection first. If it is online but still shows pending work, open
                <a routerLink="/business/terminals">Terminals</a>, find the device in the fleet list and use
                its recovery action. If it stays stuck, contact support with the terminal name and the time
                it stopped — those two facts are what we need to trace the queued bills.
              </p>
              <div class="help-faq__tip">
                <svg
                  viewBox="0 0 24 24"
                  width="16"
                  height="16"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="16" x2="12" y2="12" />
                  <line x1="12" y1="8" x2="12.01" y2="8" />
                </svg>
                <span>
                  Counter terminals keep bills safely queued in local device storage. Bills sync in order once connection restores.
                </span>
              </div>
            </div>
          </details>

          <!-- Question 2: Orders & Today's Sales -->
          <details
            class="help-faq"
            [hidden]="!matchesFilter('orders', 'order not showing today sales draft table live board settle order history')"
          >
            <summary>
              <div class="help-faq__summary-row">
                <span class="help-faq__badge">Orders</span>
                <span class="help-faq__question-text">
                  An order is not showing in today's sales.
                </span>
              </div>
              <span class="help-faq__chevron" aria-hidden="true">
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </span>
            </summary>
            <div class="help-faq__answer">
              <p>
                An order that is still open on a table is a draft until it is settled, so it appears on the
                live board but not in the day's totals. Settle it from
                <a routerLink="/business/orders">Orders</a>, then check the order history for the day.
              </p>
            </div>
          </details>

          <!-- Question 3: Settings (UPI, Card, GST) -->
          <details
            class="help-faq"
            [hidden]="!matchesFilter('settings', 'upi card gst turn on business settings payment channels tax numbers qr')"
          >
            <summary>
              <div class="help-faq__summary-row">
                <span class="help-faq__badge">Settings</span>
                <span class="help-faq__question-text">
                  Where do I turn on UPI, card, or GST?
                </span>
              </div>
              <span class="help-faq__chevron" aria-hidden="true">
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </span>
            </summary>
            <div class="help-faq__answer">
              <p>
                Both live in <a routerLink="/business/settings">Business Settings</a>: payment channels and the
                UPI handle under Payment Channels, and the tax numbers under the tax section. They are settings,
                not separate pages, so this is the only place to change them.
              </p>
            </div>
          </details>

          <!-- Question 4: Menu Categories -->
          <details
            class="help-faq"
            [hidden]="!matchesFilter('menu', 'why cant delete category dishes move empty menu orphan')"
          >
            <summary>
              <div class="help-faq__summary-row">
                <span class="help-faq__badge">Menu</span>
                <span class="help-faq__question-text">
                  Why can't I delete a category?
                </span>
              </div>
              <span class="help-faq__chevron" aria-hidden="true">
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </span>
            </summary>
            <div class="help-faq__answer">
              <p>
                A category that still holds dishes cannot be deleted, so a stray click cannot orphan the menu.
                In <a routerLink="/business/menu">Menu</a> open Categories, use <em>Move dishes</em> to empty it
                into another category, then delete it.
              </p>
            </div>
          </details>

          <!-- Question 5: Dish Availability -->
          <details
            class="help-faq"
            [hidden]="!matchesFilter('menu', 'dish keeps reverting available out of stock shared terminal device')"
          >
            <summary>
              <div class="help-faq__summary-row">
                <span class="help-faq__badge">Menu</span>
                <span class="help-faq__question-text">
                  A dish keeps reverting to available, or to out of stock.
                </span>
              </div>
              <span class="help-faq__chevron" aria-hidden="true">
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </span>
            </summary>
            <div class="help-faq__answer">
              <p>
                Availability is shared by every terminal. If the state keeps changing under you, another device
                is changing it — check who else is signed in before assuming the setting is broken.
              </p>
            </div>
          </details>

          <!-- Question 6: Daily Closing Variance -->
          <details
            class="help-faq"
            [hidden]="!matchesFilter('closing', 'cash drawer daily totals variance physical count closing figures daily closing register')"
          >
            <summary>
              <div class="help-faq__summary-row">
                <span class="help-faq__badge">Closing</span>
                <span class="help-faq__question-text">
                  What should I do if the cash drawer or daily totals don't match?
                </span>
              </div>
              <span class="help-faq__chevron" aria-hidden="true">
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </span>
            </summary>
            <div class="help-faq__answer">
              <p>
                Count physical cash and enter the counted total in <a routerLink="/business/daily-closing">Daily Closing</a>.
                The register calculates the variance against recorded bills. Add an explanatory note in the closing remarks
                before finalizing the day so your accounting trail stays transparent.
              </p>
            </div>
          </details>
        </div>
      </section>

      <!-- Section 3: Contact support (Structured Concierge Desk) -->
      <section class="panel help-section help-section--contact" aria-labelledby="help-contact">
        <div class="help-contact-grid">
          <div class="help-contact-info">
            <div class="help-section__eyebrow">DEDICATED OPERATIONS DESK</div>
            <h2 class="help-section__title" id="help-contact">Contact support</h2>
            <p class="muted help-section__lead">
              Reach us from the KhanaBook site. Include your restaurant name, the terminal involved and roughly
              when the problem started — with those three we can usually answer on the first reply.
            </p>

            <div class="help-contact-action-row">
              <a
                class="header-action-btn header-action-btn--primary help-contact-link"
                href="https://khanabook.in/contact-us"
                target="_blank"
                rel="noopener noreferrer"
              >
                <svg
                  class="header-action-icon"
                  viewBox="0 0 24 24"
                  width="16"
                  height="16"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
                Contact support
                <svg
                  class="external-arrow"
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6M15 3h6v6M10 14L21 3" />
                </svg>
              </a>

              <div class="help-desk-timing">
                <span class="help-desk-dot" aria-hidden="true"></span>
                <span>Support desk active 7 days a week • 9 AM – 11 PM IST</span>
              </div>
            </div>
          </div>

          <!-- Structured Checklist Card -->
          <div class="help-checklist-card">
            <div class="help-checklist-card__header">
              <svg
                viewBox="0 0 24 24"
                width="18"
                height="18"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M9 11l3 3L22 4" />
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
              </svg>
              <h3 class="help-checklist-card__title">Fast-Resolution Checklist</h3>
            </div>
            <p class="help-checklist-card__sub">
              Have these 3 details ready when contacting our team for immediate resolution:
            </p>
            <ul class="help-checklist-card__list">
              <li>
                <span class="help-checklist-num">1</span>
                <div>
                  <strong>Restaurant Name &amp; Outlet</strong>
                  <span>Your registered branch identifier shown in the navigation sidebar.</span>
                </div>
              </li>
              <li>
                <span class="help-checklist-num">2</span>
                <div>
                  <strong>Terminal Device Name</strong>
                  <span>The device name listed under <a routerLink="/business/terminals">Terminals</a>.</span>
                </div>
              </li>
              <li>
                <span class="help-checklist-num">3</span>
                <div>
                  <strong>Timestamp or Bill Number</strong>
                  <span>Approximate time the issue occurred or the specific check number.</span>
                </div>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <!-- Offline Readiness Status Strip -->
      <footer class="help-status-strip">
        <div class="help-status-strip__content">
          <span class="kpi-tag kpi-tag--green">
            <span class="pulse-indicator" aria-hidden="true"></span>Offline-Safe
          </span>
          <span class="help-status-strip__text">
            This guide runs entirely in your local browser cache. All instructions and troubleshooting steps remain accessible even if counter network connectivity is interrupted.
          </span>
        </div>
      </footer>
    </div>
  `,
  styles: [
    `
      .help-shell {
        display: flex;
        flex-direction: column;
        gap: var(--kb-space-6);
      }

      .help-section {
        padding: var(--kb-space-6);
        background: var(--kb-color-surface);
        border: 1px solid var(--kb-color-border);
        border-radius: var(--kb-radius-xl);
        box-shadow: var(--shadow-xs);
      }

      .help-section__header {
        margin-bottom: var(--kb-space-5);
      }

      .help-section__eyebrow {
        font-size: var(--kb-text-xs);
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--kb-color-primary);
        margin-bottom: var(--kb-space-1);
      }

      .help-section__title {
        margin: 0;
        font-size: var(--kb-text-xl);
        font-weight: 700;
        color: var(--kb-color-foreground);
        letter-spacing: -0.01em;
      }

      .help-section__lead {
        margin: var(--kb-space-1) 0 0;
        font-size: var(--kb-text-md);
        line-height: 1.5;
        max-width: 68ch;
      }

      /* ── Start Here Cards ────────────────────────────────────────── */
      .help-cards {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: var(--kb-space-4);
      }

      .help-card {
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        gap: var(--kb-space-4);
        padding: var(--kb-space-5);
        border: 1px solid var(--kb-color-border);
        border-radius: var(--kb-radius-xl);
        background: var(--kb-color-surface);
        color: inherit;
        text-decoration: none;
        transition: transform 200ms ease, border-color 200ms ease, box-shadow 200ms ease;
      }

      .help-card:hover {
        border-color: var(--kb-color-primary);
        box-shadow: var(--shadow-md);
        transform: translateY(-2px);
      }

      .help-card:focus-visible {
        outline: 2px solid var(--kb-color-primary);
        outline-offset: 2px;
      }

      .help-card__top {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      .help-card__icon-badge {
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--kb-space-10);
        height: var(--kb-space-10);
        border-radius: var(--kb-radius-lg);
      }

      .help-card__icon-badge--menu {
        background: var(--kb-color-primary-soft);
        color: var(--kb-color-primary);
      }

      .help-card__icon-badge--terminals {
        background: var(--kb-color-primary-soft);
        color: var(--kb-accent-flow);
      }

      .help-card__icon-badge--closing {
        background: var(--kb-color-warning-soft);
        color: var(--kb-accent-money);
      }

      .help-card__step {
        font-size: var(--kb-text-xs);
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--kb-color-muted-foreground);
        background: var(--kb-color-surface-2);
        padding: var(--kb-space-1) var(--kb-space-2);
        border-radius: var(--kb-radius-md);
      }

      .help-card__body {
        display: flex;
        flex-direction: column;
        gap: var(--kb-space-1);
      }

      .help-card strong {
        font-size: var(--kb-text-lg);
        font-weight: 700;
        color: var(--kb-color-foreground);
      }

      .help-card span {
        font-size: var(--kb-text-sm);
        color: var(--kb-color-muted-foreground);
        line-height: 1.5;
      }

      .help-card__action {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding-top: var(--kb-space-3);
        border-top: 1px solid var(--kb-color-surface-2);
        font-size: var(--kb-text-xs);
        font-weight: 600;
        color: var(--kb-color-link);
      }

      .help-card__arrow {
        transition: transform 200ms ease;
      }

      .help-card:hover .help-card__arrow {
        transform: translateX(var(--kb-space-1));
      }

      /* ── FAQ Interactive Controls ────────────────────────────────── */
      .faq-controls {
        display: flex;
        flex-direction: column;
        gap: var(--kb-space-3);
        margin-bottom: var(--kb-space-4);
      }

      .faq-search {
        position: relative;
        display: flex;
        align-items: center;
      }

      .faq-search__icon {
        position: absolute;
        left: var(--kb-space-3);
        color: var(--kb-border-control);
        pointer-events: none;
      }

      .faq-search__input {
        width: 100%;
        height: var(--kb-space-10);
        padding: var(--kb-space-2) var(--kb-space-8) var(--kb-space-2) var(--kb-space-8);
        font-size: var(--kb-text-sm);
        color: var(--kb-color-foreground);
        background: var(--kb-color-surface);
        border: 1px solid var(--kb-border-control);
        border-radius: var(--kb-radius-lg);
        outline: none;
        transition: border-color 150ms ease, box-shadow 150ms ease;
      }

      .faq-search__input:focus {
        border-color: var(--kb-color-primary);
        box-shadow: 0 0 0 3px var(--kb-color-primary-soft);
      }

      .faq-search__clear {
        position: absolute;
        right: var(--kb-space-2);
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--kb-space-6);
        height: var(--kb-space-6);
        border: 0;
        border-radius: var(--kb-radius-md);
        background: var(--kb-color-surface-2);
        color: var(--kb-color-muted-foreground);
        cursor: pointer;
        transition: background 150ms ease, color 150ms ease;
      }

      .faq-search__clear:hover {
        background: var(--kb-color-border);
        color: var(--kb-color-foreground);
      }

      .faq-filter-chips {
        display: flex;
        flex-wrap: wrap;
        gap: var(--kb-space-2);
      }

      .faq-filter-chip {
        display: inline-flex;
        align-items: center;
        padding: var(--kb-space-1) var(--kb-space-3);
        border: 1px solid var(--kb-color-border);
        border-radius: var(--kb-radius-full);
        background: var(--kb-color-surface-2);
        color: var(--kb-color-foreground-2);
        font-size: var(--kb-text-xs);
        font-weight: 600;
        cursor: pointer;
        transition: background 150ms ease, border-color 150ms ease, color 150ms ease;
      }

      .faq-filter-chip:hover {
        border-color: var(--kb-border-control);
        color: var(--kb-color-foreground);
      }

      .faq-filter-chip--active {
        background: var(--kb-color-primary);
        border-color: var(--kb-color-primary);
        color: var(--kb-color-on-primary);
      }

      .faq-filter-chip--active:hover {
        background: var(--kb-color-primary-hover);
        border-color: var(--kb-color-primary-hover);
        color: var(--kb-color-on-primary);
      }

      /* ── FAQ Details List ────────────────────────────────────────── */
      .faq-list {
        display: flex;
        flex-direction: column;
        gap: var(--kb-space-3);
      }

      .help-faq {
        border: 1px solid var(--kb-color-border);
        border-radius: var(--kb-radius-lg);
        background: var(--kb-color-surface);
        transition: border-color 200ms ease, box-shadow 200ms ease;
        overflow: hidden;
      }

      .help-faq[open] {
        border-color: var(--kb-border-control);
        box-shadow: var(--shadow-xs);
      }

      .help-faq summary {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--kb-space-3);
        padding: var(--kb-space-4);
        cursor: pointer;
        user-select: none;
        list-style: none;
        transition: background 150ms ease;
      }

      .help-faq summary::-webkit-details-marker {
        display: none;
      }

      .help-faq summary:hover {
        background: var(--kb-color-surface-2);
      }

      .help-faq summary:focus-visible {
        outline: 2px solid var(--kb-color-primary);
        outline-offset: -2px;
      }

      .help-faq__summary-row {
        display: flex;
        align-items: center;
        gap: var(--kb-space-3);
        flex: 1;
        min-width: 0;
      }

      .help-faq__badge {
        display: inline-flex;
        align-items: center;
        padding: 0 var(--kb-space-2);
        height: var(--kb-space-5);
        border-radius: var(--kb-radius-md);
        background: var(--kb-color-surface-2);
        color: var(--kb-color-foreground-2);
        font-size: var(--kb-text-xs);
        font-weight: 700;
        white-space: nowrap;
      }

      .help-faq__question-text {
        font-size: var(--kb-text-md);
        font-weight: 600;
        color: var(--kb-color-foreground);
        line-height: 1.4;
      }

      .help-faq summary:hover .help-faq__question-text {
        color: var(--kb-color-link);
      }

      .help-faq__chevron {
        display: flex;
        align-items: center;
        justify-content: center;
        color: var(--kb-color-muted-foreground);
        transition: transform 200ms ease, color 150ms ease;
      }

      .help-faq[open] .help-faq__chevron {
        transform: rotate(180deg);
        color: var(--kb-color-primary);
      }

      .help-faq__answer {
        padding: var(--kb-space-4);
        border-top: 1px solid var(--kb-color-surface-2);
        background: var(--kb-color-surface);
      }

      .help-faq__answer p {
        margin: 0;
        font-size: var(--kb-text-md);
        line-height: 1.6;
        color: var(--kb-color-foreground-2);
        max-width: 78ch;
      }

      .help-faq__answer a {
        color: var(--kb-color-link);
        font-weight: 600;
        text-decoration: underline;
        text-underline-offset: 2px;
      }

      .help-faq__tip {
        display: flex;
        align-items: flex-start;
        gap: var(--kb-space-2);
        margin-top: var(--kb-space-3);
        padding: var(--kb-space-3);
        border-radius: var(--kb-radius-md);
        background: var(--kb-color-surface-2);
        color: var(--kb-color-foreground-2);
        font-size: var(--kb-text-xs);
        line-height: 1.45;
      }

      .help-faq__tip svg {
        flex-shrink: 0;
        margin-top: var(--kb-space-1);
        color: var(--kb-color-primary);
      }

      /* ── Contact Support Section ─────────────────────────────────── */
      .help-contact-grid {
        display: grid;
        grid-template-columns: 1.15fr 1fr;
        gap: var(--kb-space-6);
        align-items: start;
      }

      .help-contact-info {
        display: flex;
        flex-direction: column;
      }

      .help-contact-action-row {
        display: flex;
        flex-direction: column;
        gap: var(--kb-space-3);
        align-items: flex-start;
        margin-top: var(--kb-space-4);
      }

      .help-contact-link {
        text-decoration: none;
      }

      .header-action-icon {
        margin-right: var(--kb-space-1);
      }

      .external-arrow {
        margin-left: var(--kb-space-1);
        opacity: 0.85;
      }

      .help-desk-timing {
        display: flex;
        align-items: center;
        gap: var(--kb-space-2);
        font-size: var(--kb-text-xs);
        color: var(--kb-color-muted-foreground);
      }

      .help-desk-dot {
        width: var(--kb-space-2);
        height: var(--kb-space-2);
        border-radius: var(--kb-radius-full);
        background: var(--kb-color-success);
      }

      .help-checklist-card {
        padding: var(--kb-space-5);
        border: 1px solid var(--kb-color-border);
        border-radius: var(--kb-radius-xl);
        background: var(--kb-color-surface-2);
      }

      .help-checklist-card__header {
        display: flex;
        align-items: center;
        gap: var(--kb-space-2);
        color: var(--kb-color-primary);
        margin-bottom: var(--kb-space-1);
      }

      .help-checklist-card__title {
        margin: 0;
        font-size: var(--kb-text-sm);
        font-weight: 700;
        color: var(--kb-color-foreground);
      }

      .help-checklist-card__sub {
        margin: 0 0 var(--kb-space-4);
        font-size: var(--kb-text-xs);
        color: var(--kb-color-muted-foreground);
        line-height: 1.45;
      }

      .help-checklist-card__list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--kb-space-3);
      }

      .help-checklist-card__list li {
        display: flex;
        align-items: flex-start;
        gap: var(--kb-space-3);
      }

      .help-checklist-num {
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--kb-space-5);
        height: var(--kb-space-5);
        border-radius: var(--kb-radius-full);
        background: var(--kb-color-primary);
        color: var(--kb-color-on-primary);
        font-size: var(--kb-text-xs);
        font-weight: 700;
        flex-shrink: 0;
      }

      .help-checklist-card__list li div {
        display: flex;
        flex-direction: column;
        gap: var(--kb-space-1);
      }

      .help-checklist-card__list strong {
        font-size: var(--kb-text-xs);
        font-weight: 700;
        color: var(--kb-color-foreground);
      }

      .help-checklist-card__list span {
        font-size: var(--kb-text-xs);
        color: var(--kb-color-muted-foreground);
        line-height: 1.4;
      }

      .help-checklist-card__list a {
        color: var(--kb-color-link);
        font-weight: 600;
      }

      /* ── Offline Status Strip ────────────────────────────────────── */
      .help-status-strip {
        padding: var(--kb-space-3) var(--kb-space-4);
        border: 1px solid var(--kb-color-border);
        border-radius: var(--kb-radius-lg);
        background: var(--kb-color-surface-2);
      }

      .help-status-strip__content {
        display: flex;
        align-items: center;
        gap: var(--kb-space-3);
      }

      .help-status-strip__text {
        font-size: var(--kb-text-xs);
        color: var(--kb-color-muted-foreground);
        line-height: 1.45;
      }

      /* ── Responsive Ladder ───────────────────────────────────────── */
      @media (max-width: 1024px) {
        .help-cards {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }

        .help-contact-grid {
          grid-template-columns: 1fr;
          gap: var(--kb-space-4);
        }
      }

      @media (max-width: 640px) {
        .help-section {
          padding: var(--kb-space-4);
        }

        .help-cards {
          grid-template-columns: 1fr;
        }

        .help-faq summary {
          padding: var(--kb-space-3);
        }

        .help-status-strip__content {
          flex-direction: column;
          align-items: flex-start;
          gap: var(--kb-space-2);
        }
      }
    `,
  ],
})
export class HelpSupportPageComponent {
  readonly searchQuery = signal<string>('');
  readonly selectedCategory = signal<string>('all');

  onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value || '';
    this.searchQuery.set(value);
  }

  clearSearch(): void {
    this.searchQuery.set('');
  }

  selectCategory(category: string): void {
    this.selectedCategory.set(category);
  }

  matchesFilter(category: string, searchKeywords: string): boolean {
    const activeCat = this.selectedCategory();
    if (activeCat !== 'all' && activeCat !== category) {
      return false;
    }

    const query = this.searchQuery().trim().toLowerCase();
    if (!query) {
      return true;
    }

    return searchKeywords.toLowerCase().includes(query);
  }
}
