import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Canonical icon component for the web-admin design system.
 *
 * Renders one of the named SVG icons used across the sidebar, bottom action bar,
 * and page components. Keeps a single source of truth for each icon so the same
 * dashboard/orders/kitchen/etc. symbol is not redefined inline in every layout.
 *
 * Usage:
 *   <kb-icon name="dashboard" size="18"></kb-icon>
 *   <kb-icon name="settings" size="20" color="currentColor"></kb-icon>
 *
 * Icon names: dashboard, orders, kitchen, menu, staff, inventory, terminals,
 * payments, reports, settings
 */
@Component({
  selector: 'kb-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <svg
      *ngIf="safeHtml"
      [attr.viewBox]="iconTemplate.viewBox"
      [attr.width]="size"
      [attr.height]="size"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="iconTemplate.strokeWidth ?? 2"
      stroke-linecap="round"
      stroke-linejoin="round"
      [attr.aria-hidden]="ariaHidden ? 'true' : null"
      [style.color]="color ?? null"
      [innerHTML]="safeHtml"
    ></svg>
  `,
  styles: [
    `
      :host { display: inline-block; }
    `,
  ],
})
export class IconComponent {
  @Input() name!: string;
  @Input() size = 18;
  @Input() color?: string;
  @Input() ariaHidden = true;

  readonly iconTemplate = ICONS[this.name];
  safeHtml: SafeHtml | null = null;

  constructor(private readonly sanitizer: DomSanitizer) {
    // safeHtml is set in ngOnInit after iconTemplate is resolved
  }

  ngOnInit(): void {
    if (this.iconTemplate) {
      const html = this.iconTemplate.paths.join('');
      this.safeHtml = this.sanitizer.bypassSecurityTrustHtml(html);
    }
  }
}

const ICONS: Record<string, { viewBox: string; strokeWidth?: number; paths: string[] }> = {
  dashboard: {
    viewBox: '0 0 24 24',
    paths: [
      '<rect x="3" y="3" width="7" height="7" rx="1.5"/>',
      '<rect x="14" y="3" width="7" height="7" rx="1.5"/>',
      '<rect x="14" y="14" width="7" height="7" rx="1.5"/>',
      '<rect x="3" y="14" width="7" height="7" rx="1.5"/>',
    ],
  },
  orders: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>',
      '<polyline points="14 2 14 8 20 8"/>',
      '<line x1="16" y1="13" x2="8" y2="13"/>',
      '<line x1="16" y1="17" x2="8" y2="17"/>',
    ],
  },
  kitchen: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M17 21a1 1 0 0 0 1-1v-5.35c0-.457.316-.844.727-1.041a4 4 0 0 0-2.134-7.589a5 5 0 0 0-9.186 0a4 4 0 0 0-2.134 7.588c.411.198.727.585.727 1.041V20a1 1 0 0 0 1 1ZM6 17h12"/>',
    ],
  },
  menu: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M18 2v6a3 3 0 0 1-3 3 3 3 0 0 1-3-3V2"/>',
      '<path d="M15 2v19"/>',
      '<path d="M5 2c1.5 2 1.5 5 0 7v12"/>',
    ],
  },
  staff: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>',
      '<circle cx="9" cy="7" r="4"/>',
      '<path d="M23 21v-2a4 4 0 0 0-3-3.87"/>',
      '<path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    ],
  },
  inventory: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>',
      '<polyline points="3.27 6.96 12 12.01 20.73 6.96"/>',
      '<line x1="12" y1="22.08" x2="12" y2="12"/>',
    ],
  },
  terminals: {
    viewBox: '0 0 24 24',
    paths: [
      '<rect x="4" y="2" width="16" height="20" rx="2" ry="2"/>',
      '<line x1="12" y1="18" x2="12.01" y2="18"/>',
    ],
  },
  payments: {
    viewBox: '0 0 24 24',
    paths: [
      '<rect x="1" y="4" width="22" height="16" rx="2" ry="2"/>',
      '<line x1="1" y1="10" x2="23" y2="10"/>',
    ],
  },
  reports: {
    viewBox: '0 0 24 24',
    paths: [
      '<line x1="18" y1="20" x2="18" y2="10"/>',
      '<line x1="12" y1="20" x2="12" y2="4"/>',
      '<line x1="6" y1="20" x2="6" y2="14"/>',
    ],
  },
  settings: {
    viewBox: '0 0 24 24',
    paths: [
      '<circle cx="12" cy="12" r="3"/>',
      '<path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 .33-1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
    ],
  },
};
