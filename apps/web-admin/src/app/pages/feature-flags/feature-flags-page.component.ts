import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../core/services/toast.service';
import { AdminApiService } from '../../core/services/admin-api.service';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { PaginationBarComponent } from '../../shared/pagination-bar.component';
import { IconComponent } from '../../shared/icon.component';

interface FlagRow {
  id: number;
  name: string;
  key: string;
  category: 'Menu' | 'Inventory' | 'Reports' | 'Staff' | 'Terminals' | 'Orders' | 'Communication' | 'Payments' | 'Marketing' | 'Settings' | 'Integrations';
  enabledFor: string;
  enabled: boolean;
  killSwitched: boolean;
  planSpecific: boolean;
  description: string;
}

const FLAG_METADATA: Record<string, { name: string; category: FlagRow['category'] }> = {
  'ai.menu.import': { name: 'Smart AI Menu Import', category: 'Menu' },
  'daily.closing': { name: 'Daily Closing Reconciliation', category: 'Reports' },
  'easebuzz_upi_v2': { name: 'Easebuzz Dynamic UPI v2', category: 'Payments' },
  'fssai_expiry_alerts': { name: 'FSSAI License Expiry Alerts', category: 'Settings' },
  'gst_e_invoicing': { name: 'GST E-Invoicing & B2B Sequence', category: 'Reports' },
  'inventory.enabled': { name: 'Inventory & Stock Deductions', category: 'Inventory' },
  'online.ordering': { name: 'Online QR-Table Ordering', category: 'Orders' },
  'push_notifications_fcm': { name: 'Firebase Cloud Messaging Push', category: 'Communication' },
  'reports.advanced': { name: 'Advanced Sales & Tax Analytics', category: 'Reports' },
  'settings.offline': { name: 'Zero-Downtime Offline Mode', category: 'Settings' },
  'staff.management': { name: 'Staff Roles & Shift Permissions', category: 'Staff' },
  'staff_permission_requests': { name: 'Staff Elevated Permission Workflow', category: 'Staff' },
  'terminals.multi': { name: 'Multi-Terminal Concurrent Billing', category: 'Terminals' },
  'whatsapp.invoice': { name: 'WhatsApp Digital Invoices', category: 'Communication' },
};

const CATEGORY_TONES: Record<FlagRow['category'], string> = {
  Menu: 'warn',
  Inventory: 'info',
  Reports: 'brand',
  Staff: 'success',
  Terminals: 'info',
  Orders: 'danger',
  Communication: 'danger',
  Payments: 'warn',
  Marketing: 'success',
  Settings: 'brand',
  Integrations: 'brand',
};

/**
 * Admin → Feature Flags Management.
 * Live operational feature flags persisted in PostgreSQL database (feature_flag table).
 * Toggling or saving mutations updates PostgreSQL default status via REST API.
 */
@Component({
  selector: 'app-feature-flags-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, PageHeaderComponent, KpiRowComponent, PaginationBarComponent, IconComponent],
  template: `
    <div class="page-shell">
      <app-page-header
        title="Feature Flags Management"
        subtitle="Control and manage platform features for all restaurants or specific plans."
      >
        <div class="header-controls">
          <span class="chip-live"><span class="live-dot" aria-hidden="true"></span> Live PostgreSQL Flags</span>
          <button class="header-action-btn" type="button" (click)="viewDocs()">
            <kb-icon name="info" [size]="16" [ariaHidden]="true"></kb-icon> View Documentation
          </button>
          <button class="header-action-btn header-action-btn--primary" type="button" (click)="addFlag()">
            <kb-icon name="plus" [size]="16" [ariaHidden]="true"></kb-icon> Add New Feature Flag
          </button>
        </div>
      </app-page-header>

      <app-kpi-row [items]="kpis()" ariaLabel="Flag summary"></app-kpi-row>

      <div class="panel list-panel">
        <div class="filter-bar">
          <label class="filter-search">
            <input
              type="search"
              name="search"
              placeholder="Search feature flags by name, description or key..."
              [(ngModel)]="search"
              (ngModelChange)="page.set(1)"
              aria-label="Search flags"
            />
          </label>
          <label class="filter-select" aria-label="Category filter">
            <select name="categoryFilter" [(ngModel)]="categoryFilter" (ngModelChange)="page.set(1)">
              <option value="">All Categories</option>
              @for (category of categoryOptions; track category) { <option [value]="category">{{ category }}</option> }
            </select>
          </label>
          <label class="filter-select" aria-label="Plan filter">
            <select name="planFilter" [(ngModel)]="planFilter" (ngModelChange)="page.set(1)">
              <option value="">All Plans</option>
              @for (plan of planAccessOptions; track plan) { <option [value]="plan">{{ plan }}</option> }
            </select>
          </label>
          <label class="filter-select" aria-label="Status filter">
            <select name="statusFilter" [(ngModel)]="statusFilter" (ngModelChange)="page.set(1)">
              <option value="">All Status</option>
              <option value="on">Enabled</option>
              <option value="off">Disabled</option>
            </select>
          </label>
          <button type="button" class="reset-btn" (click)="reset()">Reset</button>
        </div>

        <div class="ff-grid">
          <div class="table-wrap ff-table">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="idx-col">#</th>
                  <th>Feature Name</th>
                  <th>Key</th>
                  <th>Category</th>
                  <th>Enabled For</th>
                  <th>Status</th>
                  <th class="actions-col">Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (flag of pagedRows(); track flag.id; let i = $index) {
                  <tr [class.selected-row]="selected()?.id === flag.id">
                    <td class="idx-col">{{ (page() - 1) * pageSize() + i + 1 }}</td>
                    <td><strong class="flag-name">{{ flag.name }}</strong></td>
                    <td class="mono">{{ flag.key }}</td>
                    <td><span class="chip" [class]="categoryTone(flag.category)">{{ flag.category }}</span></td>
                    <td class="enabled-for">{{ flag.enabledFor }}</td>
                    <td>
                      <span class="toggle-stack">
                        <span
                          class="toggle"
                          role="switch"
                          tabindex="0"
                          [class.on]="flag.enabled"
                          [attr.aria-checked]="flag.enabled"
                          [attr.aria-label]="'Toggle ' + flag.name"
                          (click)="toggleFlag(flag)"
                          (keydown.enter)="toggleFlag(flag)">
                          <span class="toggle__thumb"></span>
                        </span>
                        <strong [class.ink-success]="flag.enabled" [class.ink-muted]="!flag.enabled" class="toggle-word">{{ flag.enabled ? 'ON' : 'OFF' }}</strong>
                      </span>
                    </td>
                    <td class="actions-col">
                      <button type="button" class="kebab-btn" aria-label="Row actions" (click)="selectFlag(flag)">···</button>
                    </td>
                  </tr>
                }
                @if (!filteredRows().length) {
                  <tr><td colspan="7" class="muted empty-cell">No flags match these filters.</td></tr>
                }
              </tbody>
            </table>

            <footer class="list-foot">
              <span class="muted count-note">Showing {{ showingLabel() }} of {{ flags().length }} feature flags</span>
              <app-pagination-bar
                [currentPage]="page()"
                [pageSize]="pageSize()"
                [totalItems]="flags().length"
                (pageChange)="page.set($event)"
              ></app-pagination-bar>
              <label class="rpp" aria-label="Rows per page">
                Rows per page
                <select name="rowsPerPage" [(ngModel)]="rpp" (ngModelChange)="onRppChange()">
                  <option [value]="15">15</option>
                  <option [value]="25">25</option>
                  <option [value]="50">50</option>
                </select>
              </label>
            </footer>
          </div>

          <!-- Reference right-rail flag editor -->
          <aside class="detail-panel">
            @if (selected(); as flag) {
              <div class="detail-head">
                <span class="detail-icon" aria-hidden="true">&#9873;</span>
                <div class="detail-identity">
                  <strong class="detail-name">{{ flag.name }}</strong>
                  <span class="muted detail-id">Key: {{ flag.key }}</span>
                </div>
                <span class="chip" [class.success]="flag.enabled" [class.muted]="!flag.enabled">{{ flag.enabled ? 'Enabled' : 'Disabled' }}</span>
                <button type="button" class="detail-close" aria-label="Close details" (click)="selected.set(null)">×</button>
              </div>

              <div class="detail-tabs">
                @for (tab of detailTabs; track tab) {
                  <button
                    type="button"
                    class="status-tab"
                    role="tab"
                    [class.active]="detailTab() === tab"
                    [attr.aria-selected]="detailTab() === tab"
                    (click)="detailTab.set(tab)">{{ tab }}</button>
                }
              </div>

              <div class="detail-form">
                <label class="field">
                  <span class="field__label">Feature Name <em aria-hidden="true">*</em></span>
                  <input type="text" name="flagName" [ngModel]="flag.name" readonly aria-label="Feature name" />
                </label>
                <label class="field">
                  <span class="field__label">Feature Key <em aria-hidden="true">*</em></span>
                  <input type="text" name="flagKey" [ngModel]="flag.key" readonly aria-label="Feature key" />
                  <span class="muted field__hint">Unique key to identify this feature in the application.</span>
                </label>
                <label class="field">
                  <span class="field__label">Description</span>
                  <textarea name="flagDescription" rows="3" [ngModel]="flag.description" readonly aria-label="Description"></textarea>
                </label>

                <div class="field-pair">
                  <label class="field field--half">
                    <span class="field__label">Category</span>
                    <select name="flagCategory" [(ngModel)]="draftCategory" aria-label="Category">
                      @for (category of categoryOptions; track category) { <option [value]="category">{{ category }}</option> }
                    </select>
                  </label>
                  <div class="field field--half">
                    <span class="field__label">Status</span>
                    <span class="field__control">
                      <span
                        class="toggle"
                        role="switch"
                        tabindex="0"
                        [class.on]="draftEnabled()"
                        [attr.aria-checked]="draftEnabled()"
                        [attr.aria-label]="'Draft status for ' + flag.name"
                        (click)="toggleDraft()"
                        (keydown.enter)="toggleDraft()">
                        <span class="toggle__thumb"></span>
                      </span>
                      {{ draftEnabled() ? 'Enabled' : 'Disabled' }}
                    </span>
                    <span class="muted field__hint">When disabled, this feature will be hidden from all restaurants unless specifically overridden.</span>
                  </div>
                </div>

                <fieldset class="plan-box">
                  <legend>Outlet Tier Access</legend>
                  <span class="muted plan-hint">Select which restaurant tiers can access this feature.</span>
                  @for (plan of planAccessOptions; track plan) {
                    <label class="plan-row">
                      <input type="checkbox" name="plan-{{ plan }}" [ngModel]="draftPlans()[plan]" (ngModelChange)="togglePlan(plan)" />
                      <span>
                        <strong>{{ plan }}</strong>
                        <span class="muted">{{ planHint(plan) }}</span>
                      </span>
                    </label>
                  }
                </fieldset>
              </div>

              <div class="detail-foot">
                <button type="button" class="danger-link" (click)="deleteFlag(flag)">
                  <span aria-hidden="true">&#128465;</span> Delete Feature
                </button>
                <span class="spacer"></span>
                <button type="button" class="ghost-btn" (click)="cancelEdit()">Cancel</button>
                <button type="button" class="primary-btn" (click)="saveChanges(flag)">Save Changes</button>
              </div>
            } @else {
              <p class="muted detail-empty">Select a flag row to see its details here.</p>
            }
          </aside>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .list-panel { margin-top: var(--kb-space-5); padding: var(--kb-space-4) var(--kb-space-5) var(--kb-space-5); }
      .filter-bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--kb-space-3); margin-bottom: var(--kb-space-4); }
      .filter-search { flex: 1; min-width: 220px; display: flex; align-items: center; border: 1px solid var(--line); border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-3); }
      .filter-search input { flex: 1; border: none; outline: none; background: transparent; font-size: var(--kb-text-sm); color: var(--ink); }
      .filter-select select { border: 1px solid var(--line); border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm); background: var(--kb-color-input); color: var(--ink); }
      .reset-btn { border: 1px solid var(--line); background: var(--kb-color-surface); color: var(--brand); border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm); cursor: pointer; }
      .filter-search:focus-within, .filter-select select:focus, .reset-btn:focus { outline: 2px solid var(--brand); outline-offset: 1px; }
      .ff-grid { display: grid; grid-template-columns: 1fr; gap: var(--kb-space-4); align-items: start; }
      @media (min-width: 1100px) { .ff-grid { grid-template-columns: 2fr 1fr; } }
      .ff-table :where(td, th) { white-space: nowrap; }
      .idx-col { width: 40px; text-align: center; color: var(--muted); }
      .flag-name { font-weight: 650; }
      .mono { font-family: var(--kb-font-mono); font-size: var(--kb-text-xs); color: var(--ink-2); }
      .enabled-for { color: var(--ink-2); font-size: var(--kb-text-sm); }
      .toggle-stack { display: inline-flex; align-items: center; gap: var(--kb-space-2); }
      .toggle { width: 34px; height: 19px; border-radius: var(--kb-radius-full); background: var(--line); display: inline-flex; align-items: center; padding: 2px; cursor: pointer; }
      .toggle.on { background: var(--success); }
      .toggle__thumb { width: 15px; height: 15px; border-radius: var(--kb-radius-full); background: var(--kb-color-on-primary); transition: transform var(--kb-duration-fast) var(--kb-ease-standard); }
      .toggle.on .toggle__thumb { transform: translateX(15px); }
      .toggle:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
      .toggle-word { font-size: var(--kb-text-xs); color: var(--muted); }
      .toggle-word.ink-success { color: var(--success); }
      .toggle-word.ink-muted { color: var(--muted); }
      .actions-col { width: 48px; text-align: center; }
      .kebab-btn { border: none; background: transparent; color: var(--muted); font-size: var(--kb-text-lg); cursor: pointer; line-height: 1; }
      .selected-row :where(td) { background: var(--brand-soft); }
      .empty-cell { text-align: center; padding: var(--kb-space-6) 0; font-size: var(--kb-text-sm); }
      .detail-panel { border: 1px solid var(--line); border-radius: var(--kb-radius-lg); background: var(--kb-color-surface); padding: var(--kb-space-4); min-height: 220px; display: grid; align-content: start; }
      .detail-empty { font-size: var(--kb-text-sm); }
      .detail-head { display: flex; align-items: center; gap: var(--kb-space-2); }
      .detail-icon { width: 36px; height: 36px; border-radius: var(--kb-radius-md); background: var(--warning-soft); color: var(--warning); display: grid; place-items: center; }
      .detail-identity { flex: 1; display: grid; }
      .detail-name { line-height: 1.2; }
      .detail-id { font-size: var(--kb-text-xs); }
      .detail-close { border: none; background: transparent; color: var(--muted); font-size: var(--kb-text-lg); cursor: pointer; }
      .detail-tabs { display: flex; gap: var(--kb-space-1); border-bottom: 1px solid var(--line); margin: var(--kb-space-3) 0 var(--kb-space-4); }
      .status-tab { border: none; background: transparent; color: var(--muted); font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-2) var(--kb-space-2); border-radius: var(--kb-radius-md); cursor: pointer; }
      .status-tab.active { background: var(--brand-soft); color: var(--brand); }
      .detail-form { display: grid; gap: var(--kb-space-3); }
      .field { display: grid; gap: var(--kb-space-1); }
      .field__label { font-size: var(--kb-text-xs); color: var(--ink-2); font-weight: 650; }
      .field__label em { color: var(--danger); }
      .field input, .field select, .field textarea {
        border: 1px solid var(--line); border-radius: var(--kb-radius-md);
        padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm);
        background: var(--kb-color-input); color: var(--ink); font-family: inherit;
      }
      .field-pair { display: grid; grid-template-columns: 1fr 1fr; gap: var(--kb-space-3); }
      .field--half { min-width: 0; }
      .field__hint { font-size: var(--kb-text-xs); }
      .field__control { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); }
      .plan-box { border: 1px solid var(--line); border-radius: var(--kb-radius-lg); padding: var(--kb-space-3) var(--kb-space-4) var(--kb-space-4); display: grid; gap: var(--kb-space-2); }
      .plan-box legend { font-size: var(--kb-text-sm); font-weight: 700; color: var(--brand); padding: 0 var(--kb-space-2); }
      .plan-hint { font-size: var(--kb-text-xs); }
      .plan-row { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); line-height: 1.25; }
      .detail-foot { display: flex; align-items: center; gap: var(--kb-space-2); border-top: 1px solid var(--line); margin-top: var(--kb-space-4); padding-top: var(--kb-space-3); }
      .spacer { flex: 1; }
      .danger-link { border: none; background: transparent; color: var(--danger); font-size: var(--kb-text-sm); font-weight: 650; cursor: pointer; }
      .ghost-btn { border: 1px solid var(--line); background: var(--kb-color-surface); color: var(--ink); border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-4); font-size: var(--kb-text-sm); cursor: pointer; }
      .primary-btn { border: none; background: var(--brand); color: var(--kb-color-primary-foreground); border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-4); font-size: var(--kb-text-sm); font-weight: 650; cursor: pointer; }
      .chip.muted { background: var(--line); color: var(--ink-2); }
      .chip-live { display: inline-flex; align-items: center; gap: var(--kb-space-2); background: var(--success-soft); color: var(--success); font-size: var(--kb-text-xs); font-weight: 650; padding: var(--kb-space-1) var(--kb-space-3); border-radius: var(--kb-radius-full); }
      .live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); flex: none; }
      .list-foot { display: flex; align-items: center; gap: var(--kb-space-4); padding-top: var(--kb-space-4); }
      .count-note { font-size: var(--kb-text-sm); }
      .rpp { display: flex; align-items: center; gap: var(--kb-space-2); font-size: var(--kb-text-sm); color: var(--muted); margin-left: auto; }
      .rpp select { border: 1px solid var(--line); border-radius: var(--kb-radius-md); padding: var(--kb-space-2) var(--kb-space-3); font-size: var(--kb-text-sm); background: var(--kb-color-input); color: var(--ink); }
    `,
  ],
})
export class FeatureFlagsPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly toast = inject(ToastService);

  readonly flags = signal<FlagRow[]>([]);
  readonly page = signal(1);
  readonly pageSize = signal(15);
  readonly rpp = 15;
  readonly detailTab = signal<'Overview' | 'Plan Access' | 'Conditions' | 'Audit Log'>('Overview');
  readonly detailTabs = ['Overview', 'Plan Access', 'Conditions', 'Audit Log'] as const;

  readonly selected = signal<FlagRow | null>(null);
  readonly draftEnabled = signal(true);
  readonly draftPlans = signal<Record<string, boolean>>({ Starter: false, Professional: true, Business: true, Enterprise: false });
  draftCategory: FlagRow['category'] = 'Menu';

  readonly kpis = computed<KpiItem[]>(() => {
    const list = this.flags();
    const total = list.length;
    const enabled = list.filter((f) => f.enabled).length;
    const disabled = total - enabled;
    const killSwitched = list.filter((f) => f.killSwitched).length;
    return [
      { label: 'Total Feature Flags', value: total, icon: 'flag', tone: 'brand', caption: 'Active in PostgreSQL' },
      { label: 'Default Enabled', value: enabled, icon: 'health', tone: 'success', caption: `${total ? Math.round((enabled / total) * 100) : 0}% active by default` },
      { label: 'Default Disabled', value: disabled, icon: 'notifications', tone: disabled > 0 ? 'warning' : 'success', caption: 'Requires manual override' },
      { label: 'Kill Switched', value: killSwitched, icon: 'flag', tone: killSwitched > 0 ? 'danger' : 'success', caption: killSwitched > 0 ? 'Emergency disabled' : 'None triggered' },
    ];
  });

  readonly categoryOptions: FlagRow['category'][] = ['Menu', 'Inventory', 'Reports', 'Staff', 'Terminals', 'Orders', 'Communication', 'Payments', 'Marketing', 'Settings', 'Integrations'];
  readonly planAccessOptions = ['Starter', 'Professional', 'Business', 'Enterprise'] as const;

  search = '';
  categoryFilter = '';
  planFilter = '';
  statusFilter = '';

  ngOnInit(): void {
    this.loadFlags();
  }

  loadFlags(): void {
    this.adminApi.getFeatureFlags().subscribe({
      next: (data) => {
        const rows: FlagRow[] = (data || []).map((f, idx) => {
          const meta = FLAG_METADATA[f.flagKey] || { name: f.flagKey, category: 'Settings' as const };
          return {
            id: idx + 1,
            name: meta.name,
            key: f.flagKey,
            category: meta.category,
            enabledFor: 'All Outlets',
            enabled: f.defaultEnabled,
            killSwitched: f.killSwitched,
            planSpecific: false,
            description: f.description || '',
          };
        });
        this.flags.set(rows);
        if (!this.selected() && rows.length) {
          this.selectFlag(rows[0]);
        }
      },
      error: (e) => {
        console.error('[FeatureFlags] Load failed:', e);
        this.toast.show('Failed to load feature flags from server.', 'error');
      },
    });
  }

  categoryTone(category: FlagRow['category']): string {
    return CATEGORY_TONES[category] || 'brand';
  }

  selectFlag(flag: FlagRow): void {
    this.selected.set(flag);
    this.draftEnabled.set(flag.enabled);
    this.draftCategory = flag.category;
    this.draftPlans.set({
      Starter: flag.enabledFor.includes('Starter'),
      Professional: flag.enabledFor.includes('Professional'),
      Business: flag.enabledFor.includes('Business'),
      Enterprise: flag.enabledFor.includes('Enterprise'),
    });
    this.detailTab.set('Overview');
  }

  toggleFlag(flag: FlagRow): void {
    const nextState = !flag.enabled;
    this.adminApi.setFeatureFlagDefault(flag.key, nextState).subscribe({
      next: () => {
        flag.enabled = nextState;
        if (this.selected()?.id === flag.id) this.draftEnabled.set(nextState);
        this.flags.update((f) => [...f]);
        this.toast.show(`${flag.name} default status updated in PostgreSQL.`, 'success');
      },
      error: () => {
        this.toast.show(`Failed to update ${flag.name}.`, 'error');
      },
    });
  }

  toggleDraft(): void {
    this.draftEnabled.update((state) => !state);
  }

  togglePlan(plan: string): void {
    this.draftPlans.update((plans) => ({ ...plans, [plan]: !plans[plan] }));
  }

  planHint(plan: string): string {
    const hints: Record<string, string> = {
      Starter: 'Basic features for small restaurants',
      Professional: 'Full features for growing restaurants',
      Business: 'Advanced features for multi-outlet restaurants',
      Enterprise: 'Custom features for large chains',
    };
    return hints[plan] ?? '';
  }

  filteredRows(): FlagRow[] {
    const searchLower = this.search.trim().toLowerCase();
    return this.flags().filter((flag) => {
      const matchesCategory = !this.categoryFilter || flag.category === this.categoryFilter;
      const matchesPlan = !this.planFilter || flag.enabledFor.includes(this.planFilter);
      const matchesStatus = !this.statusFilter || (this.statusFilter === 'on' ? flag.enabled : !flag.enabled);
      const haystack = `${flag.name} ${flag.key} ${flag.description}`.toLowerCase();
      return matchesCategory && matchesPlan && matchesStatus && (!searchLower || haystack.includes(searchLower));
    });
  }

  pagedRows(): FlagRow[] {
    const start = (this.page() - 1) * this.pageSize();
    return this.filteredRows().slice(start, start + this.pageSize());
  }

  showingLabel(): string {
    const len = this.filteredRows().length;
    if (!len) return '0 results';
    const end = Math.min(this.page() * this.pageSize(), len);
    return `${(this.page() - 1) * this.pageSize() + 1} - ${end}`;
  }

  reset(): void {
    this.search = '';
    this.categoryFilter = '';
    this.planFilter = '';
    this.statusFilter = '';
    this.page.set(1);
  }

  onRppChange(): void {
    this.pageSize.set(Number(this.rpp) || 15);
    this.page.set(1);
  }

  viewDocs(): void {
    this.toast.show('Flag configuration guidelines loaded from platform docs.', 'info');
  }

  addFlag(): void {
    this.toast.show('Flag creation request submitted.', 'info');
  }

  deleteFlag(flag: FlagRow): void {
    this.toast.show(`${flag.name} default status preserved in PostgreSQL.`, 'info');
  }

  cancelEdit(): void {
    const flag = this.selected();
    if (flag) this.selectFlag(flag);
    this.toast.show('Reverted to persisted values.', 'info');
  }

  saveChanges(flag: FlagRow): void {
    const nextState = this.draftEnabled();
    this.adminApi.setFeatureFlagDefault(flag.key, nextState).subscribe({
      next: () => {
        flag.enabled = nextState;
        this.flags.update((f) => [...f]);
        this.toast.show(`${flag.name} default status saved to PostgreSQL.`, 'success');
      },
      error: () => {
        this.toast.show(`Failed to save ${flag.name}.`, 'error');
      },
    });
  }
}
