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
  imports: [CommonModule],
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
        *ngIf="open()">
        <div class="notif-flyout__head">
          <h4>System Notifications</h4>
          <button
            type="button"
            class="link-btn-text"
            (click)="markAllRead.emit()"
            *ngIf="notifications.length > 0">
            Mark all read
          </button>
        </div>
        <div class="notif-flyout__body" *ngIf="notifications.length > 0; else noNotifs">
          <div
            class="notif-item"
            *ngFor="let n of notifications; trackBy: trackById"
            [class.notif-item--unread]="!n.isRead"
            (click)="markRead.emit(n)">
            <div class="notif-unread-dot" *ngIf="!n.isRead"></div>
            <div class="notif-copy">
              <strong class="notif-title">{{ n.title || 'POS Alert' }}</strong>
              <p class="notif-msg">{{ n.message }}</p>
              <span class="notif-time">{{ formatTime(n.createdAt) }}</span>
            </div>
          </div>
        </div>
        <ng-template #noNotifs>
          <div class="notif-empty">
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
        border-radius: 999px;
        background: var(--kb-color-danger);
        color: #FFFFFF;
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
        background: #FFFFFF;
        border: 1px solid var(--kb-color-border);
        border-radius: 16px;
        box-shadow: 0 10px 30px rgba(15, 23, 42, 0.12);
        z-index: 60;
        display: flex;
        flex-direction: column;
        overflow: hidden;
        animation: notifFlyoutIn 150ms cubic-bezier(0.16, 1, 0.3, 1);
      }

      @keyframes notifFlyoutIn {
        from { opacity: 0; transform: translateY(-4px); }
        to { opacity: 1; transform: translateY(0); }
      }

      .notif-flyout__head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.85rem 1rem;
        border-bottom: 1px solid var(--kb-color-surface-2);
        background: var(--kb-color-bg-app);
      }

      .notif-flyout__head h4 {
        margin: 0;
        font-size: 0.88rem;
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
        padding: 0.75rem 1rem;
        border-bottom: 1px solid var(--kb-color-bg-app);
        display: flex;
        align-items: flex-start;
        gap: 0.65rem;
        cursor: pointer;
        transition: background 150ms ease;
      }

      .notif-item:hover { background: var(--kb-color-bg-app); }
      .notif-item--unread { background: var(--kb-color-primary-soft); }

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
        font-size: 0.8rem;
        color: var(--kb-color-foreground);
        display: block;
        margin-bottom: 0.15rem;
      }

      .notif-msg {
        margin: 0 0 0.25rem;
        font-size: 0.74rem;
        color: var(--kb-color-muted-foreground);
        line-height: 1.4;
      }

      .notif-time { font-size: 0.68rem; color: var(--kb-color-muted-foreground); }

      .notif-empty {
        padding: 2rem 1rem;
        text-align: center;
        color: var(--kb-color-muted-foreground);
        font-size: 0.82rem;
      }
    `,
  ],
})
export class NotificationBellComponent implements OnChanges, OnDestroy {
  @Input() notifications: NotificationItem[] = [];
  @Input() unreadCount = 0;

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
