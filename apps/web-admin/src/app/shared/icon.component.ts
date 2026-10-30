import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges } from '@angular/core';
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
 * payments, reports, settings, profile, business, flag, help
 */
@Component({
  selector: 'kb-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <svg
      *ngIf="safeHtml"
      [attr.viewBox]="iconTemplate?.viewBox"
      [attr.width]="size"
      [attr.height]="size"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="iconTemplate?.strokeWidth ?? 2"
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
export class IconComponent implements OnChanges {
  @Input() name!: string;
  @Input() size = 18;
  @Input() color?: string;
  @Input() ariaHidden = true;

  safeHtml: SafeHtml | null = null;

  constructor(private readonly sanitizer: DomSanitizer) {}

  /* Must be a getter: field initializers run before Angular assigns the
     `name` input, so evaluating ICONS[this.name] in the constructor always
     yields undefined and no icon is ever rendered. */
  get iconTemplate(): { viewBox: string; strokeWidth?: number; paths: string[] } | undefined {
    return ICONS[this.name];
  }

  ngOnInit(): void {
    this.updateSafeHtml();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['name']) {
      this.updateSafeHtml();
    }
  }

  private updateSafeHtml(): void {
    const iconTemplate = this.iconTemplate;
    if (iconTemplate) {
      this.safeHtml = this.sanitizer.bypassSecurityTrustHtml(iconTemplate.paths.join(''));
    } else {
      this.safeHtml = null;
    }
  }
}

const ICONS: Record<string, { viewBox: string; strokeWidth?: number; paths: string[] }> = {
  // Platform-admin console extensions (prototype). Same stroke style as the
  // original set so the rail reads as one family.
  manage_accounts: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>',
      '<circle cx="9" cy="7" r="4"/>',
      '<path d="M19 8v6"/>',
      '<path d="M22 11h-6"/>',
    ],
  },
  monitoring: {
    viewBox: '0 0 24 24',
    paths: [
      '<line x1="3" y1="21" x2="21" y2="21"/>',
      '<line x1="6" y1="17" x2="6" y2="13"/>',
      '<line x1="11" y1="17" x2="11" y2="8"/>',
      '<line x1="16" y1="17" x2="16" y2="10"/>',
      '<polyline points="3 8 8 4 13 7 21 3"/>',
    ],
  },
  health: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>',
    ],
  },
  history: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M3 3v6h6"/>',
      '<path d="M3.05 13a9 9 0 1 0 1.27-4.73L3 9"/>',
      '<polyline points="12 7 12 12 15 14"/>',
    ],
  },
  notifications: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/>',
      '<path d="M13.73 21a2 2 0 0 1-3.46 0"/>',
    ],
  },
  download: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>',
      '<polyline points="7 10 12 15 17 10"/>',
      '<line x1="12" y1="3" x2="12" y2="15"/>',
    ],
  },
  backup: {
    viewBox: '0 0 24 24',
    paths: [
      '<rect x="2" y="4" width="20" height="16" rx="2"/>',
      '<line x1="7" y1="10" x2="17" y2="10"/>',
      '<line x1="7" y1="14" x2="13" y2="14"/>',
    ],
  },
  extension: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M20.5 11H19V7a2 2 0 0 0-2-2h-4V3.5a2.5 2.5 0 0 0-5 0V5H4a2 2 0 0 0-2 2v3.8h1.5a2.7 2.7 0 0 1 0 5.4H2V20a2 2 0 0 0 2 2h3.8v-1.5a2.7 2.7 0 0 1 5.4 0V22H17a2 2 0 0 0 2-2v-4h1.5a2.5 2.5 0 0 0 0-5z"/>',
    ],
  },
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
  profile: {
    viewBox: '0 0 24 24',
    paths: [
      '<circle cx="12" cy="8" r="4"/>',
      '<path d="M4 21a8 8 0 0 1 16 0"/>',
    ],
  },
  business: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18Z"/>',
      '<path d="M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2"/>',
      '<path d="M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2"/>',
      '<path d="M10 6h4"/><path d="M10 10h4"/><path d="M10 14h4"/><path d="M10 18h4"/>',
    ],
  },
  help: {
    viewBox: '0 0 24 24',
    paths: [
      '<circle cx="12" cy="12" r="10"/>',
      '<path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>',
      '<line x1="12" y1="17" x2="12.01" y2="17"/>',
    ],
  },
  flag: {
    viewBox: '0 0 24 24',
    paths: [
      '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/>',
      '<line x1="4" x2="4" y1="22" y2="15"/>',
    ],
  },
};
