import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationItem } from '../core/models/api.models';
import { formatDate } from './formatters';
import { FocusTrapDirective } from '../core/components/focus-trap.directive';

/**
 * Notification bell + flyout, extracted from the business dashboard.
 *
 * It used to live inline in business-dashboard-page.component.ts, where the only
 * way to close the flyout was to click the bell a second time: no Escape, no
 * click-outside. On a cockpit you keep open all day that reads as the panel being
 * stuck open, and it stays that way on any page that copies the markup — which is
 * why this owns its own open state and dismissal instead of taking it as inputs.
 *
 * The presentation is unchanged: same `.icon-action-btn`, same `.kpi-tag`
 * vocabulary-free badge, same flyout markup and styles, now in one place so the
 * platform dashboard can reuse it. No colour, radius or spacing token is added
 * (docs/design/DESIGN_SYSTEM_FREEZE.md).
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FocusTrapDirective],
  template: `
    <div class="notif-wrapper" #wrapper>
      <button
        #bell
        type="button"
        class="icon-action-btn"
        (click)="toggle()"
        aria-label="Notifications"
        aria-haspopup="true"
        [attr.aria-expanded]="open()"
        aria-controls="kb-notification-flyout">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
        </svg>
        <span
          class="notif-badge"
          [class.notif-badge--pop]="badgePop()"
          *ngIf="unreadCount > 0">{{ unreadCount }}</span>
      </button>

      <div
        class="notif-flyout"
        id="kb-notification-flyout"
        role="region"
        aria-label="System notifications"
        appFocusTrap
        *ngIf="open()">
        <div class="notif-flyout__head">
          <h4>System Notifications</h4>
          <button
            type="button"
            class="link-btn-text"
            (click)="markAllRead.emit()"
            [disabled]="markingAllRead || unreadCount === 0"
            *ngIf="notifications.length > 0">
            {{ markingAllRead ? 'Marking…' : 'Mark all read' }}
          </button>
        </div>
        <p class="notif-error" role="alert" *ngIf="loadError">
          {{ loadError }} <button type="button" class="link-btn-text" (click)="retry.emit()">Retry</button>
        </p>
        <div class="notif-flyout__body" *ngIf="notifications.length > 0; else noNotifs">
          <button
            type="button"
            class="notif-item"
            *ngFor="let n of notifications; trackBy: trackById"
            [class.notif-item--unread]="!n.isRead"
            [disabled]="n.isRead || markingReadId === n.id"
            (click)="markRead.emit(n)">
            <span class="notif-unread-dot" *ngIf="!n.isRead" aria-hidden="true"></span>
            <span class="notif-copy">
              <strong class="notif-title">{{ n.title || 'POS Alert' }}</strong>
              <span class="notif-msg">{{ n.message }}</span>
              <span class="notif-time">{{ markingReadId === n.id ? 'Marking as read…' : formatTime(n.createdAt) }}</span>
            </span>
          </button>
        </div>
        <ng-template #noNotifs>
          <div class="notif-empty" *ngIf="loading">
            <p>Loading notifications…</p>
          </div>
          <div class="notif-empty" *ngIf="!loading && !loadError">
            <p>All caught up! No unread notifications.</p>
          </div>
        </ng-template>
      </div>
    </div>
  `,
  styles: [
    `
      .notif-wrapper {
        position: relative;
      }

      /* Copied verbatim from the dashboard's own rule so this move is not a
         restyle. It anchors to .icon-action-btn, which is position: relative
         in styles/bento.css. */
      .notif-badge {
        position: absolute;
        top: -3px;
        right: -3px;
        padding: 0 5px;
        min-width: 16px;
        height: 16px;
        border-radius: var(--kb-radius-full);
        /* The soft-tuned danger ink is 2.7:1 behind white; the solid fill is the
           token that exists for text ON it. */
        background: var(--kb-color-danger-solid);
        color: var(--kb-color-foreground-contrast);
        font-size: 0.65rem;
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      /* A new alert arrives: the badge pops once, so the eye is drawn to the one
         thing on the page that changed. The count itself is the primary signal —
         the pop only makes it noticeable when the number is being replaced rather
         than read. Reduced motion is handled by the global block in styles.css.
         The badge stays in the DOM and the animation class is toggled off and on
         again, so the keyframes replay on every arrival. */
      .notif-badge--pop {
        animation: notifBadgePop 240ms cubic-bezier(0.16, 1, 0.3, 1);
      }

      @keyframes notifBadgePop {
        0% { transform: scale(1); }
        45% { transform: scale(1.35); }
        100% { transform: scale(1); }
      }

      .notif-flyout {
        position: absolute;
        top: calc(100% + 8px);
        right: 0;
        width: 320px;
        max-height: 400px;
        background: var(--kb-color-surface-3);
        border: 1px solid var(--kb-color-border-strong);
        border-radius: var(--kb-radius-2xl);
        box-shadow: var(--shadow-lg);
        z-index: var(--kb-z-dropdown);
        display: flex;
        flex-direction: column;
        overflow: hidden;
        animation: notifFlyoutIn 150ms cubic-bezier(0.16, 1, 0.3, 1);
      }

      @media (max-width: 640px) {
        .notif-flyout {
          position: fixed;
          top: 64px;
          left: 16px;
          right: 16px;
          width: auto;
          max-height: calc(100vh - 80px);
        }
      }

      @keyframes notifFlyoutIn {
        from { opacity: 0; transform: translateY(-4px); }
        to { opacity: 1; transform: translateY(0); }
      }

      .notif-flyout__head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.85rem var(--kb-space-4);
        border-bottom: 1px solid var(--kb-color-border);
        background: var(--kb-color-bg-app);
      }

      .notif-flyout__head h4 {
        margin: 0;
        font-size: var(--kb-text-md);
        font-weight: 700;
        color: var(--kb-color-foreground);
      }

      .notif-flyout__body {
        overflow-y: auto;
        max-height: 320px;
        display: flex;
        flex-direction: column;
      }

      .notif-item {
        position: relative;
        width: 100%;
        padding: var(--kb-space-3) var(--kb-space-4);
        border: 0;
        border-bottom: 1px solid var(--kb-color-bg-app);
        display: flex;
        align-items: flex-start;
        gap: 0.65rem;
        background: transparent;
        color: inherit;
        font: inherit;
        text-align: left;
        cursor: pointer;
        transition: background 150ms ease;
      }

      .notif-item:hover:not(:disabled) { background: var(--kb-color-bg-app); }
      .notif-item--unread { background: var(--kb-color-primary-soft); }
      .notif-item:disabled { cursor: default; }
      .notif-error { margin: 0; padding: var(--kb-space-2) var(--kb-space-4); color: var(--kb-color-danger); font-size: var(--kb-text-sm); }
      .notif-error .link-btn-text { margin-left: var(--kb-space-1); }

      .notif-unread-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: var(--kb-color-primary);
        margin-top: 5px;
        flex-shrink: 0;
      }

      .notif-copy { flex: 1; }

      .notif-title {
        font-size: var(--kb-text-sm);
        color: var(--kb-color-foreground);
        display: block;
        margin-bottom: 0.15rem;
      }

      .notif-msg {
        display: block;
        margin: 0 0 var(--kb-space-1);
        font-size: var(--kb-text-xs);
        color: var(--kb-color-muted-foreground);
        line-height: 1.4;
      }

      .notif-time { font-size: var(--kb-text-2xs); color: var(--kb-color-muted-foreground); }

      .notif-empty {
        padding: var(--kb-space-8) var(--kb-space-4);
        text-align: center;
        color: var(--kb-color-muted-foreground);
        font-size: var(--kb-text-sm);
      }
    `,
  ],
})
export class NotificationBellComponent implements OnChanges, OnDestroy {
  @Input() notifications: NotificationItem[] = [];
  @Input() unreadCount = 0;
  @Input() loadError = '';
  @Input() loading = false;
  @Input() markingReadId: number | null = null;
  @Input() markingAllRead = false;

  @Output() retry = new EventEmitter<void>();
  @Output() markRead = new EventEmitter<NotificationItem>();
  @Output() markAllRead = new EventEmitter<void>();

  readonly open = signal(false);

  /** Drives the one-off pop on the badge. */
  readonly badgePop = signal(false);
  private popTimer?: ReturnType<typeof setTimeout>;

  /**
   * Only an *increase* pops: that is a new alert arriving, which is the whole
   * point of the motion. A poll returning the same count, someone reading a
   * notification (a decrease) or the first render must not move anything — a
   * badge that jumps for every refetch is noise, not feedback.
   */
  ngOnChanges(changes: SimpleChanges): void {
    const change = changes['unreadCount'];
    if (!change || change.firstChange) return;
    if ((change.currentValue ?? 0) <= (change.previousValue ?? 0)) return;

    this.badgePop.set(true);
    if (this.popTimer) clearTimeout(this.popTimer);
    this.popTimer = setTimeout(() => this.badgePop.set(false), 280);
  }

  ngOnDestroy(): void {
    if (this.popTimer) clearTimeout(this.popTimer);
  }

  @ViewChild('wrapper') private wrapper?: ElementRef<HTMLElement>;
  @ViewChild('bell') private bell?: ElementRef<HTMLButtonElement>;

  toggle(): void {
    this.open.update((v) => !v);
  }

  close(): void {
    this.open.set(false);
  }

  /** Escape closes and hands focus back to the button, so keyboard users are
   *  not left with focus inside a panel that no longer exists. */
  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (!this.open()) return;
    this.close();
    this.bell?.nativeElement.focus();
  }

  /** Click-outside dismissal. The bell itself is inside the wrapper, so the
   *  same click that opens the panel does not immediately close it. */
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.open()) return;
    const host = this.wrapper?.nativeElement;
    if (host && !host.contains(event.target as Node)) {
      this.close();
    }
  }

  trackById = (_: number, item: NotificationItem) => item.id;

  formatTime(value: number | null | undefined): string {
    return formatDate(value ?? null);
  }
}
