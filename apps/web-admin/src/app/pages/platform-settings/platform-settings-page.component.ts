import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { IconComponent } from '../../shared/icon.component';

interface SettingsSection { id: string; label: string; icon: string; blurb: string; }

/**
 * Admin → Platform Settings (reference A11).
 *
 * Platform configuration has no backend endpoints yet; every panel keeps its
 * form state locally and reports unsaved changes in-page, never to a server.
 * The section switcher below is the same interaction model the owner
 * settings page ships with.
 */
@Component({
  selector: 'app-platform-settings-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, PageHeaderComponent, KpiRowComponent, IconComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Platform Settings"
        subtitle="Global configuration for the KhanaBook deployment."
      >
        <div class="header-controls">
          <span class="chip warn">Prototype sample — local state only</span>
        </div>
      </app-page-header>

      <div class="settings-layout">
        <nav class="panel section-nav" aria-label="Settings sections">
          <button
            *ngFor="let section of sections; trackBy: trackById"
            type="button"
            class="section-link"
            [class.section-link--active]="activeId() === section.id"
            (click)="activeId.set(section.id)"
          >
            <kb-icon [name]="section.icon" [size]="18"></kb-icon>
            <span>{{ section.label }}</span>
          </button>
        </nav>

        <div class="panel section-body">
          <div class="section-head">
            <h3>{{ active()?.label }}</h3>
            <span class="muted">{{ active()?.blurb }}</span>
          </div>

          <ng-container [ngSwitch]="activeId()">
            <div *ngSwitchCase="'general'" class="form-grid">
              <label><span>Platform Name</span><input type="text" name="platformName" [(ngModel)]="model.name" /></label>
              <label><span>Support Email</span><input type="email" name="supportEmail" [(ngModel)]="model.supportEmail" /></label>
              <label><span>Default Currency</span><input type="text" name="currency" [(ngModel)]="model.currency" readonly /></label>
              <label><span>Timezone</span><input type="text" name="timezone" [(ngModel)]="model.timezone" /></label>
            </div>

            <div *ngSwitchCase="'security'" class="form-grid">
              <label><span>Session Timeout (minutes)</span><input type="number" name="sessionTimeout" [(ngModel)]="model.sessionTimeout" /></label>
              <label class="toggle-row">
                <input type="checkbox" name="force2fa" [(ngModel)]="model.force2fa" />
                <span>Force 2FA for all admins</span>
              </label>
              <label><span>Password Min Length</span><input type="number" name="pwdMin" [(ngModel)]="model.pwdMin" /></label>
            </div>

            <div *ngSwitchCase="'billing'" class="form-grid">
              <label><span>Trial Length (days)</span><input type="number" name="trialDays" [(ngModel)]="model.trialDays" /></label>
              <label><span>Invoice Footer</span><input type="text" name="invoiceFooter" [(ngModel)]="model.invoiceFooter" /></label>
              <label class="toggle-row">
                <input type="checkbox" name="autoSuspend" [(ngModel)]="model.autoSuspend" />
                <span>Auto-suspend businesses after 3 failed renewals</span>
              </label>
            </div>

            <div *ngSwitchCase="'integrations'" class="plain-list">
              <p class="muted">Vendor connections are managed on the <strong>Integrations</strong> screen. Payment provider deep-links are read-only here.</p>
            </div>

            <div *ngSwitchCase="'roles'" class="plain-list">
              <p class="muted">Role matrix: <strong>Admin</strong> — full platform access · <strong>Owner</strong> — own restaurant only · <strong>Shop Staff</strong> — terminal screens only. Editing the matrix is not part of this prototype.</p>
            </div>
          </ng-container>

          <footer class="section-foot">
            <span class="chip warn" *ngIf="dirty()">Unsaved local changes</span>
            <button type="button" class="header-action-btn header-action-btn--primary" [disabled]="!dirty()" (click)="save()">Save Changes</button>
          </footer>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .settings-layout {
        margin-top: var(--kb-space-5);
        display: grid;
        grid-template-columns: 240px minmax(0, 1fr);
        gap: var(--kb-space-4);
        align-items: start;
      }
      .section-nav { padding: var(--kb-space-2); display: flex; flex-direction: column; gap: var(--kb-space-1); position: sticky; top: var(--kb-space-4); }
      .section-link {
        display: flex;
        align-items: center;
        gap: var(--kb-space-3);
        border: none;
        background: transparent;
        border-radius: var(--kb-radius-lg);
        padding: var(--kb-space-2) var(--kb-space-3);
        font-size: var(--kb-text-md);
        color: var(--ink-2);
        cursor: pointer;
        text-align: left;
      }
      .section-link:hover { background: var(--panel-2); }
      .section-link--active { background: var(--brand-soft); color: var(--brand); font-weight: 650; }
      .section-body { padding: var(--kb-space-5) var(--kb-space-6); min-height: 320px; display: flex; flex-direction: column; gap: var(--kb-space-4); }
      .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: var(--kb-space-4); }
      .form-grid label { display: flex; flex-direction: column; gap: var(--kb-space-1); font-size: var(--kb-text-sm); color: var(--ink-2); }
      .form-grid input {
        border: 1px solid var(--line);
        border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3);
        font-size: var(--kb-text-md);
        background: var(--kb-color-input);
        color: var(--ink);
      }
      .form-grid input:focus { outline: 2px solid var(--brand); outline-offset: 1px; }
      .toggle-row { flex-direction: row !important; align-items: center; gap: var(--kb-space-2) !important; }
      .toggle-row input { width: auto; }
      .plain-list p { margin: 0; font-size: var(--kb-text-md); line-height: 1.5; }
      .section-foot { margin-top: auto; display: flex; justify-content: flex-end; align-items: center; gap: var(--kb-space-3); }
    `,
  ],
})
export class PlatformSettingsPageComponent {
  readonly sections: SettingsSection[] = [
    { id: 'general', label: 'General', icon: 'settings', blurb: 'Platform identity and regional defaults' },
    { id: 'security', label: 'Security', icon: 'manage_accounts', blurb: 'Sessions, passwords and administrator auth' },
    { id: 'billing', label: 'Billing Rules', icon: 'payments', blurb: 'Trial windows, invoices and suspensions' },
    { id: 'integrations', label: 'Integrations', icon: 'extension', blurb: 'Vendor configuration across the platform' },
    { id: 'roles', label: 'Roles & Access', icon: 'staff', blurb: 'What each role can reach' },
  ];

  readonly activeId = signal('general');
  readonly active = computed(() => this.sections.find((section) => section.id === this.activeId()));

  readonly model = {
    name: 'KhanaBook Platform',
    supportEmail: 'support@khanabook.in',
    currency: 'INR ₹',
    timezone: 'Asia/Kolkata',
    sessionTimeout: 30,
    force2fa: false,
    pwdMin: 8,
    trialDays: 14,
    invoiceFooter: 'Thank you for dining with us!',
    autoSuspend: true,
  };

  snapshot = JSON.stringify(this.model);
  dirty(): boolean { return JSON.stringify(this.model) !== this.snapshot; }

  save(): void {
    // Local-only persistence: no platform-settings endpoint exists yet.
    this.snapshot = JSON.stringify(this.model);
  }

  trackById(index: number, section: SettingsSection): string { return section.id; }
}
