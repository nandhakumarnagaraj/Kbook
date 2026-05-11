import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMarketplaceSetup, MarketplaceConfig, MarketplaceConfigRequest } from '../../core/models/api.models';

@Component({
  selector: 'app-marketplace-setup-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Marketplace Setup</h2>
        <p class="muted">Manage your own Zomato and Swiggy credentials here. Easebuzz settlement onboarding is handled only by the KBook admin team.</p>
        <div class="hero-meta">
          <span class="chip">Owner Access</span>
          <span class="chip success">Marketplace Credentials</span>
        </div>
      </section>

      <div class="panel loading-panel" *ngIf="setupState() === 'loading'">
        <span class="spinner"></span> Loading payment onboarding...
      </div>

      <div class="panel config-card soft-section" *ngIf="setupState() === 'loaded' && marketplaceSetup() as setup">
        <div class="card-header">
          <div class="card-info">
            <h3>Easebuzz Settlement Onboarding</h3>
            <p class="muted meta-row">
              <span>Managed by: <strong>KBook Admin</strong></span>
              <span class="dot">.</span>
              <span>Restaurant: <strong>{{ setup.shopName || ('#' + setup.restaurantId) }}</strong></span>
              <span class="dot">.</span>
              <span>
                Status:
                <span class="chip"
                  [class.warn]="setup.subMerchantStatus === 'PENDING' || setup.subMerchantStatus === 'KYC_SUBMITTED' || !setup.subMerchantStatus"
                  [class.success]="setup.subMerchantStatus === 'ACTIVE'"
                  [class.danger]="setup.subMerchantStatus === 'REJECTED' || setup.subMerchantStatus === 'SUSPENDED'">
                  {{ setup.subMerchantStatus || 'NOT_STARTED' }}
                </span>
              </span>
            </p>
          </div>
        </div>

        <div class="stats-grid">
          <article class="panel stat-card">
            <h3>Sub-Merchant ID</h3>
            <strong><code>{{ setup.subMerchantId || '-' }}</code></strong>
          </article>
          <article class="panel stat-card">
            <h3>KYC Portal</h3>
            <strong>{{ setup.kycPortalUrl ? 'Generated' : 'Pending' }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Settlement Path</h3>
            <strong>{{ setup.subMerchantStatus === 'ACTIVE' ? 'Master + Sub-Merchant' : 'Awaiting admin onboarding' }}</strong>
          </article>
        </div>

        <div class="toast-success">
          Easebuzz credentials are not editable here. Contact KBook Admin if settlement onboarding needs to be created or changed.
        </div>
      </div>

      <div class="panel config-card soft-section" *ngIf="marketplaceState() === 'loaded' && marketplaceConfig() as mp">
        <h3>Marketplace Integration</h3>
        <p class="muted meta-row">
          <span class="chip">Marketplace</span>
          <span>Configure Zomato and Swiggy integration for online orders.</span>
        </p>

        <div class="section-header">
          <h4>Zomato</h4>
          <label class="toggle-wrap">
            <span class="toggle-label">{{ mp.zomatoEnabled ? 'Enabled' : 'Disabled' }}</span>
            <button class="toggle-btn" [class.on]="marketplaceForm.get('zomatoEnabled')?.value"
              (click)="marketplaceForm.patchValue({ zomatoEnabled: !marketplaceForm.get('zomatoEnabled')?.value })"
              type="button">
              <span class="toggle-knob"></span>
            </button>
          </label>
        </div>

        <div class="form-group" *ngIf="marketplaceForm.get('zomatoEnabled')?.value">
          <label class="field-label">
            API Key
            <input class="field-input" formControlName="zomatoApiKey" placeholder="Enter Zomato API key" [type]="marketplaceSaveState() === 'saved' ? 'password' : 'text'">
          </label>
          <label class="field-label">
            Webhook Secret
            <input class="field-input" formControlName="zomatoWebhookSecret" placeholder="Enter webhook secret" type="password">
          </label>
          <p class="muted small" *ngIf="mp.zomatoWebhookUrl">Webhook URL: <code>{{ mp.zomatoWebhookUrl }}</code></p>
          <p class="muted small" *ngIf="mp.zomatoApiKeyMasked">Stored API key: <code>{{ mp.zomatoApiKeyMasked }}</code></p>
        </div>

        <div class="section-header">
          <h4>Swiggy</h4>
          <label class="toggle-wrap">
            <span class="toggle-label">{{ mp.swiggyEnabled ? 'Enabled' : 'Disabled' }}</span>
            <button class="toggle-btn" [class.on]="marketplaceForm.get('swiggyEnabled')?.value"
              (click)="marketplaceForm.patchValue({ swiggyEnabled: !marketplaceForm.get('swiggyEnabled')?.value })"
              type="button">
              <span class="toggle-knob"></span>
            </button>
          </label>
        </div>

        <div class="form-group" *ngIf="marketplaceForm.get('swiggyEnabled')?.value">
          <label class="field-label">
            API Key
            <input class="field-input" formControlName="swiggyApiKey" placeholder="Enter Swiggy API key" [type]="marketplaceSaveState() === 'saved' ? 'password' : 'text'">
          </label>
          <label class="field-label">
            Webhook Secret
            <input class="field-input" formControlName="swiggyWebhookSecret" placeholder="Enter webhook secret" type="password">
          </label>
          <p class="muted small" *ngIf="mp.swiggyWebhookUrl">Webhook URL: <code>{{ mp.swiggyWebhookUrl }}</code></p>
          <p class="muted small" *ngIf="mp.swiggyApiKeyMasked">Stored API key: <code>{{ mp.swiggyApiKeyMasked }}</code></p>
        </div>

        <div class="modal-footer">
          <button class="primary-btn" type="button" (click)="saveMarketplaceConfig()"
            [disabled]="marketplaceSaveState() === 'saving'">
            <span *ngIf="marketplaceSaveState() === 'saving'">
              <span class="btn-spinner"></span>
            </span>
            Save Marketplace Config
          </button>
        </div>

        <div class="toast-success" *ngIf="marketplaceSaveState() === 'saved'">
          Marketplace configuration updated successfully.
        </div>
        <div class="save-error" *ngIf="marketplaceSaveState() === 'error'">
          {{ marketplaceSaveError() }}
        </div>
      </div>

      <div class="panel loading-panel" *ngIf="marketplaceState() === 'loading'">
        <span class="spinner"></span> Loading marketplace configuration...
      </div>
    </div>
  `,
  styles: [`
    .config-card { margin-bottom: 1.5rem; }

    .card-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1.5rem;
      flex-wrap: wrap;
    }

    .card-info { flex: 1 1 0; min-width: 0; }
    .card-info h3 { margin: 0 0 0.35rem; font-size: 1.05rem; }

    .meta-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.35rem 0.5rem;
      margin: 0;
    }

    .meta-row code {
      font-family: "Courier New", monospace;
      font-size: 0.88rem;
      background: rgba(0, 0, 0, 0.05);
      padding: 1px 6px;
      border-radius: 4px;
    }

    .dot { color: #c4b09a; }

    .toggle-wrap {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      cursor: pointer;
      user-select: none;
    }

    .toggle-label { font-weight: 600; font-size: 0.9rem; white-space: nowrap; }

    .toggle-btn {
      position: relative;
      width: 52px;
      height: 28px;
      border-radius: 999px;
      border: none;
      background: #ccc;
      cursor: pointer;
      transition: background 0.22s;
      padding: 0;
    }

    .toggle-btn.on { background: #b56a2d; }

    .toggle-knob {
      position: absolute;
      top: 3px;
      left: 3px;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: #fff;
      transition: transform 0.22s;
      display: block;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.18);
    }

    .toggle-btn.on .toggle-knob { transform: translateX(24px); }

    .toast-success {
      margin-top: 0.85rem;
      color: #2d7a3a;
      background: #eafaf0;
      border: 1px solid #a8dbb8;
      border-radius: 8px;
      padding: 0.75rem 1rem;
      font-size: 0.9rem;
      animation: fadeIn 0.3s ease;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(-4px); }
      to { opacity: 1; transform: none; }
    }

    .loading-panel {
      margin-bottom: 1.5rem;
      display: flex;
      align-items: center;
      gap: 0.65rem;
      color: #9a8060;
      font-size: 0.95rem;
    }

    .spinner {
      display: inline-block;
      width: 18px;
      height: 18px;
      border: 2px solid rgba(196, 160, 90, 0.25);
      border-top-color: #c4a05a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      flex-shrink: 0;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .field-label {
      display: grid;
      gap: 0.4rem;
      font-weight: 600;
      font-size: 0.88rem;
    }

    .field-input {
      border: 1px solid var(--line, #ddd);
      border-radius: 10px;
      padding: 0.8rem 1rem;
      font-size: 0.95rem;
      background: #fff;
      color: inherit;
      outline: none;
      width: 100%;
      box-sizing: border-box;
      transition: border-color 0.18s, box-shadow 0.18s;
    }

    .field-input:focus {
      border-color: #c4a05a;
      box-shadow: 0 0 0 3px rgba(196, 160, 90, 0.15);
    }

    .save-error {
      color: #b03030;
      background: #fdf0f0;
      border-radius: 8px;
      padding: 0.7rem 1rem;
      font-size: 0.88rem;
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      padding: 1.25rem 0 0;
    }

    .primary-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
    }

    .btn-spinner {
      display: inline-block;
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255, 255, 255, 0.4);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 1rem;
      margin-top: 1rem;
    }

    .stat-card h3 {
      margin: 0 0 0.5rem;
      font-size: 0.9rem;
      color: var(--muted);
    }
  `]
})
export class MarketplaceSetupPageComponent implements OnInit {
  private readonly api = inject(BusinessApiService);
  private readonly fb = inject(FormBuilder);

  readonly marketplaceSetup = signal<BusinessMarketplaceSetup | null>(null);
  readonly setupState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly marketplaceConfig = signal<MarketplaceConfig | null>(null);
  readonly marketplaceState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly marketplaceSaveState = signal<'idle' | 'saving' | 'saved' | 'error'>('idle');
  readonly marketplaceSaveError = signal('');

  readonly marketplaceForm = this.fb.nonNullable.group({
    zomatoApiKey: ['' as string | null, []],
    zomatoWebhookSecret: ['' as string | null, []],
    zomatoEnabled: [false],
    swiggyApiKey: ['' as string | null, []],
    swiggyWebhookSecret: ['' as string | null, []],
    swiggyEnabled: [false]
  });

  ngOnInit(): void {
    this.api.getMarketplaceSetup().subscribe({
      next: (setup) => {
        this.marketplaceSetup.set(setup);
        this.setupState.set('loaded');
      },
      error: () => { this.setupState.set('error'); }
    });

    this.loadMarketplaceConfig();
  }

  private loadMarketplaceConfig(): void {
    this.marketplaceState.set('loading');
    this.api.getMarketplaceConfig().subscribe({
      next: (cfg) => {
        this.marketplaceConfig.set(cfg);
        this.marketplaceForm.patchValue({
          zomatoEnabled: cfg.zomatoEnabled,
          zomatoApiKey: '',
          zomatoWebhookSecret: '',
          swiggyEnabled: cfg.swiggyEnabled,
          swiggyApiKey: '',
          swiggyWebhookSecret: ''
        });
        this.marketplaceState.set('loaded');
      },
      error: () => { this.marketplaceState.set('error'); }
    });
  }

  saveMarketplaceConfig(): void {
    this.marketplaceSaveState.set('saving');
    this.marketplaceSaveError.set('');

    const rawValue = this.marketplaceForm.getRawValue();
    const payload: MarketplaceConfigRequest = {
      zomatoApiKey: rawValue.zomatoApiKey || undefined,
      zomatoWebhookSecret: rawValue.zomatoWebhookSecret || undefined,
      zomatoEnabled: rawValue.zomatoEnabled,
      swiggyApiKey: rawValue.swiggyApiKey || undefined,
      swiggyWebhookSecret: rawValue.swiggyWebhookSecret || undefined,
      swiggyEnabled: rawValue.swiggyEnabled
    };

    this.api.saveMarketplaceConfig(payload).subscribe({
      next: (cfg) => {
        this.marketplaceConfig.set(cfg);
        this.marketplaceState.set('loaded');
        this.marketplaceSaveState.set('saved');
        setTimeout(() => this.marketplaceSaveState.set('idle'), 2000);
      },
      error: (err) => {
        this.marketplaceSaveState.set('error');
        this.marketplaceSaveError.set(err?.error?.error ?? err?.error?.message ?? 'Save failed. Please try again.');
      }
    });
  }
}
