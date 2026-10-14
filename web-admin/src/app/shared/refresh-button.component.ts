import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Canonical header refresh button shared by every web-admin page.
 *
 * Before this existed, refresh was a plain text button on some pages
 * (Businesses, Inventory, Platform Dashboard), an icon + text button on
 * others (Menu, Staff), and a solid violet CTA elsewhere (Feature Flags,
 * Business Dashboard) — same action, three designs. This component is now
 * the single standard: neutral `header-action-btn` treatment, refresh icon
 * with an optional loading spin, text label.
 *
 * Usage:
 *   <app-refresh-button (refresh)="loadStaff()" [loading]="!loaded()">
 *   </app-refresh-button>
 */
@Component({
  selector: 'app-refresh-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="header-action-btn"
      [disabled]="disabled || loading"
      (click)="refresh.emit()"
      [attr.aria-label]="ariaLabel"
      [attr.title]="ariaLabel"
    >
      <svg
        viewBox="0 0 24 24"
        width="15"
        height="15"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
        [class.spinning]="loading"
      >
        <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
        <path d="M3 3v5h5" />
        <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
        <path d="M16 21h5v-5" />
      </svg>
      <span>{{ label }}</span>
    </button>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      .header-action-btn {
        display: inline-flex;
        align-items: center;
        gap: 0.45rem;
      }
      .spinning {
        animation: kb-refresh-spin 800ms linear infinite;
      }
      @keyframes kb-refresh-spin {
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
})
export class RefreshButtonComponent {
  @Input() disabled = false;
  /** Spins the icon and disables the button while the reload is in flight. */
  @Input() loading = false;
  @Input() label = 'Refresh';
  @Input() ariaLabel = 'Refresh';
  @Output() refresh = new EventEmitter<void>();
}
