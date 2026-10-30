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
 * but a vendor-registry listing does not exist yet, so the card grid is
 * prototype sample data, labelled on screen. Connection toggles local-only.
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
          <span class="chip warn">Prototype sample — no API yet</span>
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
    `,
  ],
})
export class IntegrationsPageComponent {
  readonly kpis: KpiItem[] = [
    { label: 'Connected Vendors', value: 4, icon: 'extension', tone: 'success', caption: 'Of 6 supported (sample)' },
    { label: 'Merchants Onboarded', value: 118, icon: 'business', tone: 'brand', caption: 'Via Easebuzz sub-merchants' },
    { label: 'Webhook Success', value: '99.2%', icon: 'monitoring', tone: 'success', caption: 'Rolled 7 days (sample)' },
    { label: 'Degraded', value: 1, icon: 'flag', tone: 'warning', caption: 'RM gateway retry' },
  ];

  readonly vendors: VendorRow[] = [
    { id: 'easebuzz', name: 'Easebuzz', blurb: 'Sub-merchants, checkout, split settlements, webhooks.', category: 'payments', state: 'connected', metric: '118 sub-merchants live' },
    { id: 'upi', name: 'UPI / QR', blurb: 'Standalone UPI intent + static QR fallback for offline terminals.', category: 'payments', state: 'connected', metric: '28,412 intent calls / week (sample)' },
    { id: 'sms', name: 'SMS Gateway', blurb: 'Order and OTP messages to customers.', category: 'messaging', state: 'connected', metric: 'Delivery 97.4% (sample)' },
    { id: 'whatsapp', name: 'WhatsApp Business', blurb: 'Invoice sharing and review requests.', category: 'messaging', state: 'available' },
    { id: 'firebase', name: 'Firebase Cloud Messaging', blurb: 'Terminal + owner push notifications.', category: 'data', state: 'connected', metric: '316 registered devices' },
    { id: 'gst', name: 'GST Filing Export', blurb: 'GSTR-friendly exports per restaurant.', category: 'data', state: 'degraded', metric: 'Retry queued' },
  ];

  vendorIcon(category: VendorRow['category']): string {
    if (category === 'payments') return 'payments';
    if (category === 'messaging') return 'orders';
    return 'extension';
  }

  toggleVendor(vendor: VendorRow): void {
    // Local-only in the prototype: no vendor-registry endpoint exists.
    vendor.state = vendor.state === 'available' ? 'connected' : 'available';
  }

  trackById(index: number, vendor: VendorRow): string { return vendor.id; }
}
