import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, ElementRef, ViewChild, HostListener, inject, signal, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BottomActionBarComponent } from '../bottom-action-bar/bottom-action-bar.component';
import { IconComponent } from '../../shared/icon.component';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessApiService } from '../../core/services/business-api.service';
import { PresenceService } from '../../core/services/presence.service';
import { environment } from '../../../environments/environment';

const API = environment.apiBaseUrl;

type NavLink = { label: string; path: string; icon: string; iconKey: string; badge?: string };
/** A headed cluster of destinations. `heading` is empty for the flat admin rail. */
type NavGroup = { heading: string; links: NavLink[] };

type BottomActionBarItem = { label: string; iconKey: string; route: string; badge?: string };

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, BottomActionBarComponent, IconComponent],
  template: `
    <div class="layout-shell" [class.layout-shell--collapsed]="collapsed() && !isMobileView()">
      <a class="skip-link" href="#main-content" (click)="skipToMain($event)">Skip to main content</a>
      <!-- Screen-reader feedback for the collapse toggle (role=status = aria-live polite) -->
      <div class="sr-only" role="status" aria-live="polite">
        {{ isMobileView() ? (menuOpen() ? 'Menu drawer open' : 'Menu drawer closed') : (collapsed() ? 'Sidebar collapsed' : 'Sidebar expanded') }}
      </div>
      <header class="topbar topbar--mobile" [attr.aria-hidden]="isMobileView() && menuOpen() ? 'true' : null" [attr.inert]="isMobileView() && menuOpen() ? '' : null">
        <button
          #menuButton
          type="button"
          class="hamburger"
          [class.hamburger--open]="menuOpen()"
          [attr.aria-label]="menuOpen() ? 'Close navigation menu' : 'Open navigation menu'"
          [attr.aria-expanded]="menuOpen()"
          aria-controls="sidebar-nav"
          (click)="toggleMenu()">
          <span class="hamburger__bar"></span>
          <span class="hamburger__bar"></span>
          <span class="hamburger__bar"></span>
        </button>
        <div class="topbar__brand">
          <div class="brand-logo-circle brand-logo-circle--sm" aria-hidden="true">
            <img src="/khanabook_logo.png" alt="KhanaBook" class="brand-logo-img" />
          </div>
          <div class="topbar__brand-copy">
            <span class="topbar__title">KhanaBook</span>
            <span class="topbar__context" aria-live="polite">{{ currentPageLabel() }}</span>
          </div>
        </div>
      </header>

      <!-- Mobile backdrop -->
      <div
        class="sidebar-backdrop"
        *ngIf="menuOpen()"
        (click)="closeMenu()"
        aria-hidden="true"></div>

      <!-- Sidebar -->
      <aside
        #sidebar
        class="sidebar"
        [class.sidebar--open]="menuOpen()"
        [class.sidebar--collapsed]="collapsed() && !isMobileView()"
        id="sidebar-nav"
        aria-label="Primary navigation"
        [attr.aria-hidden]="isMobileView() && !menuOpen() ? 'true' : null"
        [attr.inert]="isMobileView() && !menuOpen() ? '' : null"
        tabindex="-1"
      >
        <div class="brand-block">
          <div class="brand-row">
            <div class="brand-logo-circle" aria-hidden="true">
              <img src="/khanabook_logo.png" alt="KhanaBook" class="brand-logo-img" />
              <span class="brand-mark-kb" aria-hidden="true">Kb</span>
            </div>
            <div class="brand-copy">
              <span class="brand-title">KhanaBook</span>
              <span class="brand-subtitle">{{ brandSubtitle() }}</span>
            </div>
            <button type="button" class="sidebar-close-btn" aria-label="Close navigation menu" (click)="closeMenu()">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                <path d="m18 6-12 12M6 6l12 12" />
              </svg>
            </button>
            <button
              type="button"
              class="sidebar-toggle"
              (click)="toggleCollapsed()"
              [attr.aria-label]="collapsed() ? 'Expand sidebar' : 'Collapse sidebar'"
              [attr.aria-expanded]="!collapsed()"
              aria-controls="sidebar-nav">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path [attr.d]="collapsed() ? 'm9 18 6-6-6-6' : 'm15 18-6-6 6-6'" />
              </svg>
            </button>
          </div>
        </div>

        <nav class="nav-links" aria-label="Main">
          <div class="nav-group" *ngFor="let group of navGroups(); trackBy: trackByNavGroup">
            <!-- The heading is decoration for the eye: each link already carries
                 its own accessible name, so announcing "Operations" before the
                 row adds a stop without adding information. -->
            <p class="nav-group__heading" *ngIf="group.heading" aria-hidden="true">{{ group.heading }}</p>
            <a
              *ngFor="let link of group.links; trackBy: trackByNavLink"
              [routerLink]="link.path"
              routerLinkActive="active-link"
              ariaCurrentWhenActive="page"
              class="nav-link"
              [attr.aria-label]="link.label"
              (mouseenter)="peek($event, link)"
              (mouseleave)="hidePeek()"
              (focus)="peek($event, link)"
              (blur)="hidePeek()"
              (click)="closeMenu(false)">
              <span class="nav-link__icon" aria-hidden="true">
                <kb-icon *ngIf="link.iconKey" [name]="link.iconKey" [size]="18"></kb-icon>
                <span *ngIf="!link.iconKey">{{ link.icon }}</span>
              </span>
              <span class="nav-link__label">{{ link.label }}</span>
              <span *ngIf="link.badge" class="nav-link__badge">{{ link.badge }}</span>
            </a>
          </div>
        </nav>

        <div class="sidebar-footer">
          <div class="user-card">
            <div class="user-avatar">
              <span aria-hidden="true">{{ (session()?.userName || 'O').charAt(0).toUpperCase() }}</span>
              <!-- Presence comes from the server's own heartbeat, so the dot
                   states a measurement. No dot until the first ack resolves. -->
              <span
                *ngIf="presence.connected() !== null"
                class="presence-dot"
                [class.presence-dot--offline]="presence.connected() === false"
                aria-hidden="true"></span>
              <!-- Colour is never the only signal; the state is also text. The class
                   keeps this distinct from the collapse announcement's live region. -->
              <span class="sr-only presence-status" role="status">{{ presenceLabel() }}</span>
            </div>
            <div class="user-meta">
              <span class="user-name">{{ session()?.userName || 'Operator' }}</span>
              <span class="user-role">{{ roleLabel() }}</span>
            </div>
          </div>

          <button class="logout-btn" (click)="logout()" type="button" aria-label="Sign out">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            <span>Sign out</span>
          </button>
        </div>
      </aside>

      <div class="workspace" [attr.aria-hidden]="isMobileView() && menuOpen() ? 'true' : null" [attr.inert]="isMobileView() && menuOpen() ? '' : null">
        <main id="main-content" class="content-shell" tabindex="-1">
          <router-outlet />
        </main>
      </div>

      <!-- Mobile bottom action bar -->
      <kb-bottom-action-bar
        *ngIf="showBottomBar()"
        [items]="bottomActionItems()"
      />

      <!-- Hover peek label, shown while a nav icon is hovered/focused in collapsed mode.
           Rendered position:fixed and measured from the link rect so it can escape
           the sidebar's overflow clipping. maxWidth is clamped to the viewport, so a
           long label wraps onto a second line instead of leaving the screen. -->
      <div
        *ngIf="peekTip() as tip"
        class="nav-peek"
        role="tooltip"
        [style.left.px]="tip.x"
        [style.top.px]="tip.y"
        [style.maxWidth.px]="tip.maxWidth">{{ tip.label }}</div>
    </div>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; background: var(--kb-color-bg-app); }

    .layout-shell {
      /* The collapsed rail's width, declared once and read by both the grid track
         and the rail itself; before this it was a literal in two places, which is
         how a rail and its grid track drift apart. It sits on the layout rather
         than :host because this is the element that uses it, and an unresolved
         custom property would silently drop the whole grid-template-columns
         declaration. The rail's own 1px edge and 10px padding leave a 43px
         column for the targets. */
      --rail-width: 64px;
      min-height: 100vh;
      display: grid;
      grid-template-columns: var(--kb-sidebar-width) 1fr;
      align-items: start;
    }
    .workspace { min-width: 0; min-height: 100vh; display: flex; flex-direction: column; background: var(--kb-color-bg-app); }
    .skip-link {
      position: fixed; left: 1rem; top: 0; z-index: var(--kb-z-skip);
      padding: 0.65rem var(--kb-space-4); color: var(--kb-color-on-primary); background: var(--kb-color-primary);
      font-weight: 600; text-decoration: none;
      border-radius: var(--kb-radius-sm); transform: translateY(-110%);
      transition: transform 0.15s ease;
    }
    .skip-link:focus { transform: translateY(0); outline: 2px solid var(--kb-color-foreground-contrast); outline-offset: 2px; }

    /* ── Sidebar (logo-violet command rail) ── */
    .sidebar {
      padding: var(--kb-space-5) var(--kb-space-3);
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-2);
      position: sticky;
      top: 0;
      height: 100vh;
      overflow-y: auto;
      background: var(--pos-sidebar-bg);
      /* The rail's edge belongs to the rail: borrowing the workspace border token
         would paint a light seam down the side of a dark panel the moment the
         workspace theme changed. */
      border-right: 1px solid var(--pos-sidebar-border);
      color: var(--pos-sidebar-text);
    }

    /* The brand and the footer must never be compressed by a short window: the
       nav is the only part of the rail that gives (see the desktop block). */
    .brand-block { padding: 0 var(--kb-space-2) var(--kb-space-5); flex-shrink: 0; }
    .brand-row { display: flex; align-items: center; gap: var(--kb-space-2); }
    .brand-logo-circle {
      width: 34px;
      height: 34px;
      border-radius: var(--kb-radius-md);
      background: var(--kb-color-surface);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      overflow: hidden;
      padding: 3px;
    }
    .brand-logo-circle--sm {
      width: 32px;
      height: 32px;
      border-radius: var(--kb-radius-lg);
      padding: 2px;
    }
    .brand-logo-img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
    .brand-copy { display: grid; min-width: 0; line-height: 1.15; }
    .brand-title {
      font-size: var(--kb-text-lg);
      font-weight: 700;
      color: var(--kb-color-foreground-contrast);
      letter-spacing: -0.01em;
    }
    /* The wordmark alone does not say which product this console belongs to;
       one 11px line names the surface without competing with the nav. */
    .brand-subtitle {
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--pos-sidebar-ink-muted);
    }
    .sidebar-close-btn { display: none; }

    /* Navigation. Rows are targets sized for a mouse, not cushions: 34px tall
       with a 2px inter-row gap groups the destinations while keeping nine of
       them above the fold on a 768px-tall laptop. */
    .nav-links { display: flex; flex-direction: column; gap: var(--kb-space-3); }

    /* Section headings. 11px uppercase is the one place on the rail where type
       steps below --kb-text-sm: the label is a signpost, not a target, and at
       13px it would compete with the destinations it names. */
    .nav-group { display: flex; flex-direction: column; gap: var(--kb-space-1); }
    .nav-group + .nav-group { margin-top: var(--kb-space-2); }
    .nav-group__heading {
      margin: 0 0 var(--kb-space-1) var(--kb-space-3);
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--pos-sidebar-ink-muted);
      white-space: nowrap;
    }

    .nav-link {
      position: relative;
      display: flex;
      /* A nav row is a target, not a cushion: when the column runs out of height
         the list must scroll, not squeeze nine 44px rows into whatever is left. */
      flex-shrink: 0;
      align-items: center;
      gap: var(--kb-space-3);
      padding: var(--kb-space-2) var(--kb-space-3);
      border-radius: var(--kb-radius-md);
      color: var(--pos-sidebar-text);
      text-decoration: none;
      font-weight: 500;
      font-size: var(--kb-text-sm);
      transition: background 150ms var(--ease-out), color 150ms var(--ease-out);
    }
    .nav-link__icon { font-size: var(--kb-text-md); display: inline-flex; align-items: center; justify-content: center; width: 24px; color: inherit; }
    .nav-link:hover { background: var(--pos-sidebar-hover); color: var(--pos-sidebar-ink); }
    .nav-link:active { transform: scale(0.97); }
    /* The current page is stated twice: a violet field for the eye, and the
       logo's saffron in the rail's left gutter for anyone scanning down it. No
       glow — a blurred shadow on a dark plane reads as a render artefact. */
    .nav-link.active-link {
      background: var(--pos-sidebar-active);
      color: var(--kb-color-foreground-contrast);
      font-weight: 600;
      border: none;
      box-shadow: none;
    }
    .nav-link.active-link::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      width: 3px;
      height: 18px;
      border-radius: var(--kb-radius-full);
      background: var(--pos-sidebar-accent);
      transform: translateY(-50%);
    }
    /* The app draws one violet keyboard ring, but on this dark plane it all but
       disappears. The rail states focus in white, the way every other control
       on it does (toggle, sign out, drawer close, skip link). */
    .nav-link:focus-visible {
      outline: 2px solid var(--kb-color-foreground-contrast);
      outline-offset: 2px;
    }
    .nav-link.active-link .nav-link__icon {
      color: var(--kb-color-foreground-contrast);
    }
    .nav-link__badge {
      margin-left: auto;
      padding: 2px var(--kb-space-2);
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      border-radius: var(--kb-radius-full);
      /* A white chip would glare on the violet plane; the rail uses its own tint. */
      background: rgba(255, 255, 255, 0.14);
      color: var(--kb-color-foreground-contrast);
      border: 1px solid transparent;
    }
    .nav-link.active-link .nav-link__badge {
      background: var(--pos-sidebar-accent);
      color: var(--pos-sidebar-bg);
      border-color: transparent;
    }

    /* Sidebar Footer (footer.design standard) */
    .sidebar-footer {
      margin-top: auto;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-2);
      padding-top: var(--kb-space-3);
      border-top: 1px solid var(--pos-sidebar-border);
    }


    /* User card */
    .user-card {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
      padding: var(--kb-space-1) var(--kb-space-2);
      background: transparent;
      border: 1px solid transparent;
      border-radius: var(--kb-radius-md);
    }
    .user-avatar {
      position: relative;
      width: 32px;
      height: 32px;
      border-radius: var(--kb-radius-full);
      background: var(--pos-sidebar-active);
      color: var(--kb-color-foreground-contrast);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: var(--kb-text-sm);
      flex-shrink: 0;
      box-shadow: var(--kb-shadow-xs);
    }
    /* Presence dot: an 8:1 green on the rail's violet, ringed in the rail colour so
       it reads where it overlaps the avatar's edge. Offline is the muted ink, not
       a second red — a dropped heartbeat is not an error state. */
    .presence-dot {
      position: absolute;
      right: -1px;
      bottom: -1px;
      width: 9px;
      height: 9px;
      border-radius: var(--kb-radius-full);
      background: var(--pos-sidebar-success);
      box-shadow: 0 0 0 2px var(--pos-sidebar-bg);
    }
    .presence-dot--offline { background: var(--pos-sidebar-ink-muted); }

    .user-meta { display: grid; min-width: 0; }
    /* These two used the ink for text that sits ON a foreground fill
       (--kb-color-foreground-contrast, #0B0E1A) and the recessed-input surface
       (--kb-color-muted), which rendered the signed-in name at 1.2:1 and the
       role at 1.2:1 — present in the DOM, invisible on screen. */
    .user-name { font-weight: 600; font-size: var(--kb-text-sm); color: var(--pos-sidebar-ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .user-role { font-size: var(--kb-text-2xs); color: var(--pos-sidebar-ink-muted); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; }

    /* Sign out: subtle restrained danger styling with tactile feedback */
    .logout-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--kb-space-2);
      padding: var(--kb-space-2) var(--kb-space-3);
      /* Signing out is not the rail's job to advertise: it stays a quiet outline
         and only turns into the danger fill once the pointer is on it. */
      background: transparent;
      color: var(--pos-sidebar-text);
      border: 1px solid var(--pos-sidebar-border);
      border-radius: var(--kb-radius-md);
      cursor: pointer;
      font-weight: 600;
      font-size: var(--kb-text-sm);
      transition: background 160ms ease, color 160ms ease, border-color 160ms ease;
    }
    .logout-btn:hover {
      background: var(--pos-sidebar-danger);
      color: var(--kb-color-foreground-contrast);
      border-color: var(--pos-sidebar-danger);
    }
    .logout-btn:active {
      transform: scale(0.97);
    }
    .logout-btn:focus-visible {
      outline: 2px solid var(--kb-color-foreground-contrast);
      outline-offset: 2px;
    }

    /* Content */
    .content-shell { min-width: 0; width: 100%; flex: 1; }
    .content-shell:focus { outline: none; }

    /* ── Topbar (mobile only) ── */
    .topbar { display: none; }
    .topbar__brand { display: flex; align-items: center; gap: var(--kb-space-3); min-width: 0; }
    .topbar__brand-copy { display: grid; min-width: 0; gap: 0.1rem; }

    .hamburger {
      display: inline-flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      gap: 5px;
      width: 44px;
      height: 44px;
      padding: 0;
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-card);
      background: var(--kb-color-surface);
      cursor: pointer;
      flex: 0 0 44px;
    }
    .hamburger__bar {
      display: block;
      width: 22px;
      height: 2px;
      border-radius: var(--kb-radius-sm);
      background: var(--kb-color-foreground);
      transition: transform 160ms ease, opacity 150ms ease;
    }
    .hamburger--open .hamburger__bar:first-child { transform: translateY(7px) rotate(45deg); }
    .hamburger--open .hamburger__bar:nth-child(2) { opacity: 0; }
    .hamburger--open .hamburger__bar:last-child { transform: translateY(-7px) rotate(-45deg); }
    .sidebar-backdrop { display: none; }

    /* ── Desktop collapsible sidebar (icon rail) ── */
    /* Hidden on mobile/tablet: the drawer there is opened with the hamburger
       and its collapsed counterpart would make no sense. */
    .sidebar-toggle { display: none; }

    @media (min-width: 1025px) {
      /* The rail no longer scrolls as one block. Nine items and a footer are taller
         than a short laptop window, and scrolling the whole rail pushed the profile
         and sign out below the fold; now the nav is the scroller and the footer
         stays put. Mobile keeps its whole-drawer scroll (this is desktop only). */
      .sidebar {
        width: var(--kb-sidebar-width);
        overflow: hidden;
        transition: width 200ms var(--ease-out);
      }
      .nav-links {
        flex: 1 1 auto;
        min-height: 0;
        overflow-y: auto;
        overflow-x: hidden;
        /* The 2px ring offset needs room, or the scroller clips it. */
        padding-block: 2px;
        /* A thin rail-coloured scrollbar rather than the OS one: a 15px native
           bar inside a 64px rail would eat a third of every target. */
        scrollbar-width: thin;
        scrollbar-color: var(--pos-sidebar-border) transparent;
      }
      .nav-links::-webkit-scrollbar { width: 6px; }
      .nav-links::-webkit-scrollbar-track { background: transparent; }
      .nav-links::-webkit-scrollbar-thumb {
        background: var(--pos-sidebar-border);
        border-radius: var(--kb-radius-full);
      }

      /* 44px hit area, 32px circle drawn inside it: the toggle stays visually
         secondary while its target meets --kb-touch-min. */
      .sidebar-toggle {
        position: relative;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        width: 44px;
        height: 44px;
        margin-left: auto;
        padding: 0;
        border: 0;
        background: transparent;
        color: var(--pos-sidebar-ink);
        cursor: pointer;
      }
      .sidebar-toggle::before {
        content: '';
        position: absolute;
        inset: 6px;
        border: 1px solid rgba(255, 255, 255, 0.18);
        border-radius: var(--kb-radius-full);
        background: rgba(255, 255, 255, 0.06);
        transition: background 150ms var(--ease-out);
      }
      .sidebar-toggle svg { position: relative; }
      .sidebar-toggle:hover { color: var(--kb-color-foreground-contrast); }
      .sidebar-toggle:hover::before { background: var(--pos-sidebar-hover); }
      .sidebar-toggle:focus-visible {
        outline: 2px solid var(--kb-color-foreground-contrast);
        outline-offset: 2px;
      }

      .layout-shell--collapsed { grid-template-columns: var(--rail-width) 1fr; }
      .sidebar--collapsed {
        width: var(--rail-width);
        padding-inline: 10px;
      }

      /* Brand block reduces to a compact Kb mark, with the expand chevron under it */
      .sidebar--collapsed .brand-row {
        flex-direction: column;
        gap: var(--kb-space-2);
      }
      .sidebar--collapsed .brand-block { padding: 0; }
      .sidebar--collapsed .brand-copy { display: none; }
      /* A 36px square sits in the 44px column: the rail is 24px narrower than a
         sidebar, so the mark scales with it rather than filling it edge to edge. */
      .sidebar--collapsed .brand-logo-circle {
        width: 36px;
        height: 36px;
        border-radius: 10px;
        padding: 0;
      }
      .sidebar--collapsed .brand-logo-img { display: none; }
      .sidebar--collapsed .brand-mark-kb {
        display: inline-block;
        font-size: 0.95rem;
        font-weight: 800;
        letter-spacing: -0.03em;
        color: var(--kb-color-foreground);
      }
      .sidebar--collapsed .sidebar-toggle { margin-left: 0; }

      /* Nav links become 44px-tall targets filling the rail's column — the
         --kb-touch-min height, instead of a 48px row squeezed into a 46px track. */
      .sidebar--collapsed .nav-links { gap: var(--kb-space-2); }
      .sidebar--collapsed .nav-link {
        justify-content: center;
        gap: 0;
        padding: 0;
        height: 44px;
        border-radius: 10px;
      }
      .sidebar--collapsed .nav-link__icon { width: 24px; }
      .sidebar--collapsed .nav-link__label { display: none; }
      /* A 64px rail has no room for a heading, and there is nothing to group
         against once the labels are gone. */
      .sidebar--collapsed .nav-group__heading { display: none; }
      .sidebar--collapsed .nav-link__badge {
        position: absolute;
        top: 5px;
        right: 5px;
        width: 8px;
        height: 8px;
        padding: 0;
        font-size: 0;
        border: none;
        color: transparent;
        background: var(--kb-color-danger);
        overflow: hidden;
      }

      /* Footer: avatar only, 44px square sign out */
      .sidebar--collapsed .user-card {
        justify-content: center;
        padding: 0;
        background: transparent;
        border-color: transparent;
      }
      .sidebar--collapsed .user-meta { display: none; }
      .sidebar--collapsed .user-avatar { width: 34px; height: 34px; }
      .sidebar--collapsed .logout-btn {
        width: 44px;
        height: 44px;
        padding: 0;
        margin-inline: auto;
        border-radius: 10px;
      }
      .sidebar--collapsed .logout-btn span { display: none; }

      /* Hover peek chip: anchored to the fixed coords measured off the link rect.
         It follows the app's one tooltip pattern (staff's .tooltip-text): a plain
         rounded chip, no arrow. It wraps inside the viewport-clamped width instead
         of running off the right edge. */
      .nav-peek {
        position: fixed;
        transform: translateY(-50%);
        z-index: var(--kb-z-peek);
        padding: 0.45rem 0.7rem;
        border-radius: 10px;
        background: var(--pos-sidebar-peek-bg);
        color: var(--kb-color-foreground-contrast);
        font-size: var(--kb-text-sm);
        font-weight: 600;
        line-height: 1.3;
        white-space: normal;
        overflow-wrap: anywhere;
        pointer-events: none;
        box-shadow: var(--kb-shadow-md);
      }
      /* Pointer toward the icon, so the chip reads as belonging to its item. */
      .nav-peek::before {
        content: '';
        position: absolute;
        left: -5px;
        top: 50%;
        width: 10px;
        height: 10px;
        background: var(--pos-sidebar-peek-bg);
        transform: translateY(-50%) rotate(45deg);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .hamburger__bar,
      .sidebar,
      .nav-link,
      .sidebar-toggle::before { transition: none; }
    }

    /* Increased-contrast mode turns every focus ring near-black (#0F172A), which
       is invisible on the violet plane. The rail keeps its own white ring there. */
    @media (prefers-contrast: more) {
      .sidebar a:focus-visible,
      .sidebar button:focus-visible {
        outline-color: var(--kb-color-foreground-contrast) !important;
      }
    }

    /* Screen-reader only helper for the collapse announcement */
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }

    /* ── Responsive ── */
    @media (max-width: 1024px) {
      .layout-shell { grid-template-columns: 1fr; }
      .topbar {
        display: flex;
        align-items: center;
        gap: var(--kb-space-4);
        position: sticky;
        top: 0;
        z-index: var(--kb-z-topbar);
        padding: var(--kb-space-3) var(--kb-space-4);
        background: var(--kb-color-surface);
        border-bottom: 1px solid var(--kb-color-border);
        box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
      }
      .topbar__title { font-size: var(--kb-text-md); line-height: 1.15; font-weight: 700; color: var(--kb-color-foreground); letter-spacing: -0.01em; }
      .topbar__context { overflow: hidden; color: var(--kb-color-muted-foreground); font-size: var(--kb-text-2xs); line-height: 1.15; text-overflow: ellipsis; white-space: nowrap; }
      /* The drawer is fixed and full height, so it owns the top of the screen:
         it must paint above the sticky topbar (which is inert while open) and
         above its own scrim. Both are rungs of the z ladder in styles.css. */
      .sidebar {
        position: fixed;
        top: 0;
        left: 0;
        z-index: var(--kb-z-sidebar);
        width: 280px;
        max-width: 85vw;
        height: 100vh;
        transform: translateX(-100%);
        transition: transform 0.25s ease;
        box-shadow: var(--kb-shadow-lg);
      }
      .sidebar--open { transform: translateX(0); }
      .sidebar-close-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
        margin-left: auto;
        border: 1px solid rgba(255, 255, 255, 0.18);
        border-radius: 10px;
        background: transparent;
        color: var(--pos-sidebar-ink);
        cursor: pointer;
      }
      .sidebar-close-btn:focus-visible { outline: 2px solid var(--kb-color-foreground-contrast); outline-offset: 2px; }
      .sidebar-backdrop {
        display: block;
        position: fixed;
        inset: 0;
        z-index: var(--kb-z-backdrop);
        background: rgba(26, 26, 26, 0.5);
        backdrop-filter: blur(2px);
      }
      /* Clear space for the fixed mobile bottom action bar */
      .workspace { padding-bottom: 76px; }
    }
    @media (min-width: 1025px) {
      .sidebar-backdrop { display: none !important; }
    }
  `]
})
export class SidebarLayoutComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private  readonly businessApi = inject(BusinessApiService);
  readonly presence = inject(PresenceService);
  @ViewChild('menuButton') private menuButton?: ElementRef<HTMLButtonElement>;
  @ViewChild('sidebar') private sidebar?: ElementRef<HTMLElement>;

  readonly menuOpen = signal(false);

  /* Desktop icon-rail collapse. Persisted so operators can keep their layout. */
  readonly collapsed = signal<boolean>(this.readCollapsedPreference());

  private readCollapsedPreference(): boolean {
    try {
      return typeof localStorage !== 'undefined' && localStorage.getItem('kb.sidebar.collapsed') === '1';
    } catch {
      // Storage unavailable (private mode, etc.) — default to expanded.
      return false;
    }
  }

  toggleCollapsed(): void {
    const next = !this.collapsed();
    this.collapsed.set(next);
    this.hidePeek();
    try {
      localStorage.setItem('kb.sidebar.collapsed', next ? '1' : '0');
    } catch {
      // Ignore persistence failures; the toggle still works this session.
    }
  }

  /* Hover peek: while the rail is collapsed, hovering or focusing a nav link
     shows its label in a styled chip beside the icon. */
  readonly peekTip = signal<{ label: string; x: number; y: number; maxWidth: number } | null>(null);

  /** Pointer dwell before the chip appears. Keyboard focus skips it: a keyboard
      user has already committed to the item, so making them wait reads as lag. */
  private static readonly PEEK_DELAY_MS = 280;
  private peekTimer?: ReturnType<typeof setTimeout>;

  peek(event: MouseEvent | FocusEvent, link: NavLink): void {
    if (!this.collapsed() || this.isMobileView()) { this.hidePeek(); return; }
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const x = rect.right + 10;
    // Clamped to the viewport, so a wide label wraps instead of running off screen.
    // 120px is the floor that keeps a label from collapsing into a word-per-line
    // column; if even that would overflow, the chip slides left instead of clipping.
    const maxWidth = Math.max(120, window.innerWidth - x - 12);
    const tip = {
      label: link.label,
      x: Math.min(x, Math.max(8, window.innerWidth - 12 - maxWidth)),
      y: rect.top + rect.height / 2,
      maxWidth
    };
    this.clearPeekTimer();
    if (event.type === 'focus') { this.peekTip.set(tip); return; }
    this.peekTimer = setTimeout(() => this.peekTip.set(tip), SidebarLayoutComponent.PEEK_DELAY_MS);
  }

  hidePeek(): void {
    this.clearPeekTimer();
    this.peekTip.set(null);
  }

  private clearPeekTimer(): void {
    if (this.peekTimer !== undefined) {
      clearTimeout(this.peekTimer);
      this.peekTimer = undefined;
    }
  }
  readonly orderPaymentFlowMode = signal<string>('pay_before_food');

  readonly session = this.authService.session;
  readonly contextTitle = computed(() => this.session()?.role === 'KBOOK_ADMIN' ? 'KhanaBook Platform' : 'Restaurant operations');
  /** Console chrome follows the reference: ADMIN CONSOLE for platform staff, POS Console for restaurant operators. */
  readonly brandSubtitle = computed(() => this.session()?.role === 'KBOOK_ADMIN' ? 'ADMIN CONSOLE' : 'POS Console');
  /** Human-readable role for the sidebar footer chip. */
  readonly roleLabel = computed(() => {
    switch (this.session()?.role) {
      case 'KBOOK_ADMIN': return 'Super Admin';
      case 'OWNER': return 'Owner';
      default: return this.session()?.role ?? '';
    }
  });
  /**
   * Owner navigation, grouped by the job the screen is for. Nine flat rows gave
   * a counter, a catalogue, stock, payroll and the books the same weight; the
   * headings let someone find "the money screens" without reading every label.
   *
   * Only destinations that exist are listed. Payments and GST live inside
   * Business Settings, so they are not separate rows that would deep-link into
   * a page the owner then has to scroll.
   */
  readonly navGroups = computed<NavGroup[]>(() => {
    const role = this.session()?.role;
    // Platform staff see the reference's four console sections. Support
    // Tickets lives under MANAGE — it is a queue for platform staff, not a
    // settings concern.
    if (role === 'KBOOK_ADMIN') {
      return [
        {
          heading: 'Overview',
          links: [
            { label: 'Dashboard', path: '/admin/dashboard', icon: '', iconKey: 'dashboard' }
          ]
        },
        {
          heading: 'Manage',
          links: [
            { label: 'Businesses', path: '/admin/businesses', icon: '', iconKey: 'business' },
            { label: 'Feature Flags', path: '/admin/feature-flags', icon: '', iconKey: 'flag' },
            { label: 'Subscriptions', path: '/admin/subscriptions', icon: '', iconKey: 'payments' },
            { label: 'Users & Access', path: '/admin/users', icon: '', iconKey: 'manage_accounts' },
            { label: 'Support Tickets', path: '/admin/support-tickets', icon: '', iconKey: 'help' }
          ]
        },
        {
          heading: 'Operations',
          links: [
            { label: 'Sales Overview', path: '/admin/sales', icon: '', iconKey: 'monitoring' },
            { label: 'Reports', path: '/admin/reports', icon: '', iconKey: 'reports' },
            { label: 'System Health', path: '/admin/system-health', icon: '', iconKey: 'health' },
            { label: 'Activity Logs', path: '/admin/activity-logs', icon: '', iconKey: 'history' }
          ]
        },
        {
          heading: 'Settings',
          links: [
            { label: 'Platform Settings', path: '/admin/settings', icon: '', iconKey: 'settings' },
            { label: 'Notifications', path: '/admin/notifications', icon: '', iconKey: 'notifications' },
            { label: 'Backups', path: '/admin/backups', icon: '', iconKey: 'backup' },
            { label: 'Integrations', path: '/admin/integrations', icon: '', iconKey: 'extension' }
          ]
        }
      ];
    }
    if (role !== 'OWNER') {
      // SHOP_STAFF has no business web access (server /business/** is OWNER-only).
      return [];
    }
    return [
      {
        heading: 'Overview',
        links: [
          { label: 'Dashboard', path: '/business/dashboard', icon: '', iconKey: 'dashboard' }
        ]
      },
      {
        heading: 'Operations',
        links: [
          { label: 'Orders', path: '/business/orders', icon: '', iconKey: 'orders' },
          { label: 'Menu', path: '/business/menu', icon: '', iconKey: 'menu' },
          { label: 'Inventory', path: '/business/inventory', icon: '', iconKey: 'inventory' },
          { label: 'Staff', path: '/business/staff', icon: '', iconKey: 'staff' },
          { label: 'Terminals', path: '/business/terminals', icon: '', iconKey: 'terminals' }
        ]
      },
      {
        heading: 'Finance',
        links: [
          { label: 'Daily Closing', path: '/business/daily-closing', icon: '', iconKey: 'payments' },
          { label: 'Reports', path: '/business/reports', icon: '', iconKey: 'reports' }
        ]
      },
      {
        heading: 'Business',
        // The route is the settings screen and holds the profile, taxes, receipt
        // and payment configuration. Calling the row "Profile" promised a page
        // that only edited the owner's name.
        links: [
          { label: 'Business Settings', path: '/business/settings', icon: '', iconKey: 'profile' }
        ]
      },
      {
        // Support sits last on purpose: it is where someone goes when the app is
        // not doing what it should, not part of the daily round.
        heading: 'Help',
        links: [
          { label: 'Help & Support', path: '/business/help', icon: '', iconKey: 'help' }
        ]
      }
    ];
  });

  /** Flat view of `navGroups` — the current-page label and the specs read this. */
  readonly links = computed<NavLink[]>(() => this.navGroups().flatMap((group) => group.links));

  readonly bottomActionItems = computed<BottomActionBarItem[]>(() => {
    return [
      { label: 'Dashboard', iconKey: 'dashboard', route: '/business/dashboard' },
      // Orders opens on the Live Floor tab, so the mobile bar no longer needs
      // a separate "Active" destination for the live board.
      { label: 'Orders', iconKey: 'kitchen', route: '/business/orders' },
      { label: 'Closing', iconKey: 'payments', route: '/business/daily-closing' },
      { label: 'Settings', iconKey: 'settings', route: '/business/settings' }
    ];
  });

  readonly isMobileView = signal(typeof window !== 'undefined' && window.innerWidth <= 1024);

  readonly showBottomBar = computed(() => this.session()?.role === 'OWNER' && this.isMobileView());

  currentPageLabel(): string {
    const path = this.router.url.split(/[?#]/, 1)[0];
    return this.links().find((link) => path === link.path || path.startsWith(`${link.path}/`))?.label
      ?? (this.session()?.role === 'KBOOK_ADMIN' ? 'Platform' : 'Workspace');
  }

  ngOnInit(): void {
    this.isMobileView.set(window.innerWidth <= 1024);
    // The shell only renders behind the auth guard, but a null session must not
    // start a heartbeat that would 401 on every tick.
    if (this.session()) this.presence.start();
    const role = this.session()?.role;
    if (role === 'OWNER') {
      this.businessApi.getProfile().subscribe({
        next: (profile) => {
          const mode = profile?.orderPaymentFlowMode;
          this.orderPaymentFlowMode.set(mode === 'pay_after_food' ? 'pay_after_food' : 'pay_before_food');
        },
        error: () => {
          // Keep the default; KDS tab simply won't show for pay-after-food
          // until settings load succeeds elsewhere.
        }
      });
    }
  }

  skipToMain(event: Event): void {
    event.preventDefault();
    const main = document.getElementById('main-content');
    if (main) {
      main.focus();
      main.scrollIntoView();
    }
  }

  toggleMenu(): void {
    if (this.menuOpen()) { this.closeMenu(); return; }
    this.menuOpen.set(true);
    setTimeout(() => this.sidebar?.nativeElement?.focus());
  }

  closeMenu(restoreFocus = true): void {
    this.menuOpen.set(false);
    setTimeout(() => {
      if (restoreFocus) {
        this.menuButton?.nativeElement?.focus();
      } else {
        document.getElementById('main-content')?.focus();
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }

  openOrders(): void {
    void this.router.navigate(['/business/orders']);
  }

  @HostListener('window:resize')
  onResize(): void {
    const isMobile = window.innerWidth <= 1024;
    this.isMobileView.set(isMobile);
    this.hidePeek();
    if (!isMobile && this.menuOpen()) this.menuOpen.set(false);
  }

  @HostListener('document:keydown.control.k', ['$event'])
  openOrderSearch(event: KeyboardEvent): void {
    if (this.session()?.role !== 'OWNER') return;
    event.preventDefault();
    this.openOrders();
  }

  /* Ctrl+Shift+B toggles the desktop sidebar rail (mirrors Chrome / VS Code). */
  @HostListener('document:keydown.control.shift.b', ['$event'])
  onToggleCollapseShortcut(event: KeyboardEvent): void {
    if (this.isMobileView()) return;
    event.preventDefault();
    this.toggleCollapsed();
  }

  /* Close menu on Escape key */
  @HostListener('document:keydown.escape', ['$event'])
  onEscape(event: KeyboardEvent): void {
    this.hidePeek();
    if (this.menuOpen()) this.closeMenu();
  }

  /* A fixed-position chip would hang in place while the page scrolls under it. */
  @HostListener('window:scroll')
  onScroll(): void {
    this.hidePeek();
  }

  /** Text alternative for the presence dot — colour alone is not a signal. */
  presenceLabel(): string {
    const connected = this.presence.connected();
    if (connected === null) return '';
    return connected ? 'Connected to KhanaBook' : 'Not connected to KhanaBook';
  }

  ngOnDestroy(): void {
    this.presence.stop();
    // A pending dwell must not fire into a torn-down component.
    this.clearPeekTimer();
  }

  @HostListener('document:keydown.tab', ['$event'])
  trapMenuFocus(event: KeyboardEvent): void {
    if (!this.isMobileView() || !this.menuOpen()) return;
    const focusable = this.sidebar?.nativeElement.querySelectorAll<HTMLElement>('a[href], button:not([disabled])');
    if (!focusable?.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || active === this.sidebar?.nativeElement)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (active === last || active === this.sidebar?.nativeElement)) {
      event.preventDefault();
      first.focus();
    }
  }

  trackByNavLink = (_: number, link: NavLink) => link.path;
  trackByNavGroup = (_: number, group: NavGroup) => group.heading;
}
