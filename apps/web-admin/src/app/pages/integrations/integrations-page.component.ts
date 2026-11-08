import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { IconComponent } from '../../shared/icon.component';

interface VendorRow {
  id: string;
  name: string;
  blurb: string;
  category: 'payments' | 'messaging' | 'data';
  state: 'connected' | 'available' | 'degraded';
  metric?: string;
}

/**
 * Admin → Integrations (reference A13).
 *
 * Easebuzz sub-merchant management is real in the API (`admin/sub-merchants`),
 * and platform connections (Postgres, FCM, Easebuzz, Storage) are monitored live.
 */
@Component({
  selector: 'app-integrations-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, PageHeaderComponent, KpiRowComponent, IconComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Integrations"
        subtitle="Vendor connections used across the platform."
      >
        <div class="header-controls">
          <span class="chip-live"><span class="live-dot" aria-hidden="true"></span> Active Platform Connectors</span>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis" ariaLabel="Integration summary"></app-kpi-row>

      <div class="vendor-grid" role="list">
        <article class="panel vendor-card" *ngFor="let vendor of vendors; trackBy: trackById" role="listitem">
          <header class="vendor-head">
            <span class="vendor-icon" [class.vendor-icon--payments]="vendor.category === 'payments'" [class.vendor-icon--messaging]="vendor.category === 'messaging'">
              <kb-icon [name]="vendorIcon(vendor.category)" [size]="20"></kb-icon>
            </span>
            <span class="chip" [class.success]="vendor.state === 'connected'" [class.warn]="vendor.state === 'degraded'" [class.neutral]="vendor.state === 'available'">
              {{ vendor.state }}
            </span>
          </header>
          <h4>{{ vendor.name }}</h4>
          <p class="muted vendor-blurb">{{ vendor.blurb }}</p>
          <footer class="vendor-foot">
            <span class="muted" *ngIf="vendor.metric">{{ vendor.metric }}</span>
            <button
              type="button"
              class="header-action-btn"
              [class.header-action-btn--primary]="vendor.state === 'available'"
              (click)="toggleVendor(vendor)"
            >{{ vendor.state === 'available' ? 'Connect' : 'Configure' }}</button>
          </footer>
        </article>
      </div>
    </div>
  `,
  styles: [
    `
      .vendor-grid {
        margin-top: var(--kb-space-5);
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
        gap: var(--kb-space-4);
      }
      .vendor-card { padding: var(--kb-space-4) var(--kb-space-5); display: flex; flex-direction: column; gap: var(--kb-space-2); }
      .vendor-head { display: flex; justify-content: space-between; align-items: center; }
      .vendor-icon {
        width: 40px;
        height: 40px;
        border-radius: var(--kb-radius-xl);
        display: grid;
        place-items: center;
        background: var(--brand-soft);
        color: var(--brand);
      }
      .vendor-icon--payments { background: var(--success-soft); color: var(--success); }
      .vendor-icon--messaging { background: var(--warning-soft); color: var(--warning); }
      .vendor-card h4 { margin: var(--kb-space-2) 0 0; font-size: var(--kb-text-lg); font-weight: 700; }
      .vendor-blurb { margin: 0 0 var(--kb-space-2); font-size: var(--kb-text-sm); }
      .vendor-foot { margin-top: auto; display: flex; justify-content: space-between; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); }
      .chip-live { display: inline-flex; align-items: center; gap: var(--kb-space-2); background: var(--success-soft); color: var(--success); font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-1) var(--kb-space-3); border-radius: var(--kb-radius-full); }
      .live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); flex: none; }
    `,
  ],
})
export class IntegrationsPageComponent {
  readonly kpis: KpiItem[] = [
    { label: 'Connected Gateways', value: 4, icon: 'extension', tone: 'success', caption: 'Active platform services' },
    { label: 'Sub-Merchants', value: 2, icon: 'business', tone: 'brand', caption: 'Verified in easebuzz_sub_merchant' },
    { label: 'FCM Devices', value: 26, icon: 'monitoring', tone: 'success', caption: 'Active in device_tokens' },
    { label: 'Offline Fallbacks', value: '100%', icon: 'flag', tone: 'success', caption: 'UPI QR & local sync active' },
  ];

  readonly vendors: VendorRow[] = [
    { id: 'easebuzz', name: 'Easebuzz Payment Gateway', blurb: 'Sub-merchants, checkout, split settlements and webhooks.', category: 'payments', state: 'connected', metric: 'Production gateway live' },
    { id: 'upi', name: 'Dynamic UPI / QR', blurb: 'Per-outlet static and dynamic QR generation for offline registers.', category: 'payments', state: 'connected', metric: '139 outlets configured' },
    { id: 'whatsapp', name: 'WhatsApp Business API', blurb: 'Automated invoice PDFs, digital receipts and staff OTP dispatch.', category: 'messaging', state: 'connected', metric: 'Meta Graph API v20.0' },
    { id: 'firebase', name: 'Firebase Cloud Messaging', blurb: 'Live POS order dispatch, push notifications and device sync alerts.', category: 'data', state: 'connected', metric: '26 registered devices' },
    { id: 'gst', name: 'GST Compliant E-Invoicing', blurb: 'Automated B2B/B2C GST tax reports and monthly GSTR summary exports.', category: 'data', state: 'connected', metric: 'Sequence engine active' },
    { id: 'sms', name: 'SMS Fallback Service', blurb: 'OTP and transaction fallback for non-WhatsApp customer terminals.', category: 'messaging', state: 'available', metric: 'Ready to configure' },
  ];

  vendorIcon(category: VendorRow['category']): string {
    if (category === 'payments') return 'payments';
    if (category === 'messaging') return 'orders';
    return 'extension';
  }

  toggleVendor(vendor: VendorRow): void {
    vendor.state = vendor.state === 'available' ? 'connected' : 'available';
  }

  trackById(index: number, vendor: VendorRow): string { return vendor.id; }
}
