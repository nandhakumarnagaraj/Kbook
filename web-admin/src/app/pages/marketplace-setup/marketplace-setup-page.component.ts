import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMarketplaceSetup, MarketplaceConfig, MarketplaceConfigRequest } from '../../core/models/api.models';

@Component({
  selector: 'app-marketplace-setup-page',
  standalone: true,
  imports: [
    CommonModule, 
    ReactiveFormsModule, 
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatChipsModule,
    MatSnackBarModule,
    MatTooltipModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">Marketplace Setup</h1>
          <p class="page-subtitle">Manage Zomato and Swiggy credentials. Settlement onboarding is managed by KBook admin.</p>
        </div>
      </div>

      <div class="loading-state" *ngIf="setupState() === 'loading' || marketplaceState() === 'loading'">
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading configuration...</p>
      </div>

      <div class="content-grid" *ngIf="setupState() === 'loaded' && marketplaceState() === 'loaded'">
        <!-- Settlement Info -->
        <mat-card class="config-card" *ngIf="marketplaceSetup() as setup">
          <mat-card-header>
            <mat-icon mat-card-avatar color="primary">account_balance</mat-icon>
            <mat-card-title>Easebuzz Settlement Onboarding</mat-card-title>
            <mat-card-subtitle>
              Restaurant: {{ setup.shopName || ('#' + setup.restaurantId) }} | Status: 
              <span class="status-chip" [class]="getOnboardingStatusClass(setup.subMerchantStatus)">
                {{ setup.subMerchantStatus || 'NOT STARTED' }}
              </span>
            </mat-card-subtitle>
          </mat-card-header>
          
          <mat-card-content>
            <div class="onboarding-grid">
              <div class="info-item">
                <span class="label">Sub-Merchant ID</span>
                <span class="value"><code>{{ setup.subMerchantId || '-' }}</code></span>
              </div>
              <div class="info-item">
                <span class="label">KYC Portal</span>
                <span class="value">{{ setup.kycPortalUrl ? 'Activated' : 'Pending' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Settlement Path</span>
                <span class="value">{{ setup.subMerchantStatus === 'ACTIVE' ? 'Sub-Merchant Ready' : 'Pending Onboarding' }}</span>
              </div>
            </div>
            
            <div class="admin-notice">
              <mat-icon>info</mat-icon>
              <span>Easebuzz settlement details are managed by KBook Admin. Contact support for changes.</span>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Marketplace Config -->
        <form [formGroup]="marketplaceForm" class="config-form" (ngSubmit)="saveMarketplaceConfig()">
          <div class="integration-cards">
            <!-- ZOMATO -->
            <mat-card class="platform-card zomato">
              <mat-card-header>
                <div class="platform-logo zomato">Z</div>
                <mat-card-title>Zomato Integration</mat-card-title>
                <mat-card-subtitle>Manage API access and webhooks for Zomato orders.</mat-card-subtitle>
                <span class="spacer"></span>
                <mat-slide-toggle formControlName="zomatoEnabled">
                  {{ marketplaceForm.get('zomatoEnabled')?.value ? 'Enabled' : 'Disabled' }}
                </mat-slide-toggle>
              </mat-card-header>
              
              <mat-divider></mat-divider>
              
              <mat-card-content *ngIf="marketplaceForm.get('zomatoEnabled')?.value">
                <div class="form-grid">
                  <mat-form-field appearance="outline">
                    <mat-label>API Key</mat-label>
                    <input matInput formControlName="zomatoApiKey" [type]="marketplaceSaveState() === 'saved' ? 'password' : 'text'" placeholder="Enter API Key">
                    <mat-hint *ngIf="marketplaceConfig()?.zomatoApiKeyMasked">Stored: {{ marketplaceConfig()?.zomatoApiKeyMasked }}</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Webhook Secret</mat-label>
                    <input matInput formControlName="zomatoWebhookSecret" type="password" placeholder="Enter Webhook Secret">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Outlet ID</mat-label>
                    <input matInput formControlName="zomatoOutletId" placeholder="Enter Zomato Outlet ID">
                  </mat-form-field>
                </div>

                <div class="webhook-info" *ngIf="marketplaceConfig()?.zomatoWebhookUrl">
                   <strong>Webhook URL:</strong> <code>{{ marketplaceConfig()?.zomatoWebhookUrl }}</code>
                </div>

                <div class="health-check">
                  <button mat-stroked-button type="button" (click)="runHealthCheck('ZOMATO')" [disabled]="healthLoading() === 'ZOMATO'">
                    <mat-icon *ngIf="healthLoading() !== 'ZOMATO'">diagnostics</mat-icon>
                    <mat-spinner diameter="18" *ngIf="healthLoading() === 'ZOMATO'"></mat-spinner>
                    Health Check
                  </button>
                  <div class="health-result" *ngIf="healthResult('ZOMATO') as h">
                    <mat-icon [color]="h.healthy ? 'primary' : 'warn'">
                      {{ h.healthy ? 'check_circle' : 'error' }}
                    </mat-icon>
                    <span [class.success]="h.healthy" [class.error]="!h.healthy">
                      {{ h.healthy ? 'Connected' : 'Issues detected' }}
                    </span>
                    <span class="detail" *ngIf="!h.healthy">
                      {{ !h.apiKeyConfigured ? '(Missing API Key)' : (!h.outletIdConfigured ? '(Missing Outlet ID)' : '') }}
                    </span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>

            <!-- SWIGGY -->
            <mat-card class="platform-card swiggy">
              <mat-card-header>
                <div class="platform-logo swiggy">S</div>
                <mat-card-title>Swiggy Integration</mat-card-title>
                <mat-card-subtitle>Manage API access and webhooks for Swiggy orders.</mat-card-subtitle>
                <span class="spacer"></span>
                <mat-slide-toggle formControlName="swiggyEnabled">
                  {{ marketplaceForm.get('swiggyEnabled')?.value ? 'Enabled' : 'Disabled' }}
                </mat-slide-toggle>
              </mat-card-header>
              
              <mat-divider></mat-divider>
              
              <mat-card-content *ngIf="marketplaceForm.get('swiggyEnabled')?.value">
                <div class="form-grid">
                  <mat-form-field appearance="outline">
                    <mat-label>API Key</mat-label>
                    <input matInput formControlName="swiggyApiKey" [type]="marketplaceSaveState() === 'saved' ? 'password' : 'text'" placeholder="Enter API Key">
                    <mat-hint *ngIf="marketplaceConfig()?.swiggyApiKeyMasked">Stored: {{ marketplaceConfig()?.swiggyApiKeyMasked }}</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Webhook Secret</mat-label>
                    <input matInput formControlName="swiggyWebhookSecret" type="password" placeholder="Enter Webhook Secret">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Store ID</mat-label>
                    <input matInput formControlName="swiggyStoreId" placeholder="Enter Swiggy Store ID">
                  </mat-form-field>
                </div>

                <div class="webhook-info" *ngIf="marketplaceConfig()?.swiggyWebhookUrl">
                   <strong>Webhook URL:</strong> <code>{{ marketplaceConfig()?.swiggyWebhookUrl }}</code>
                </div>

                <div class="health-check">
                  <button mat-stroked-button type="button" (click)="runHealthCheck('SWIGGY')" [disabled]="healthLoading() === 'SWIGGY'">
                    <mat-icon *ngIf="healthLoading() !== 'SWIGGY'">diagnostics</mat-icon>
                    <mat-spinner diameter="18" *ngIf="healthLoading() === 'SWIGGY'"></mat-spinner>
                    Health Check
                  </button>
                  <div class="health-result" *ngIf="healthResult('SWIGGY') as h">
                    <mat-icon [color]="h.healthy ? 'primary' : 'warn'">
                      {{ h.healthy ? 'check_circle' : 'error' }}
                    </mat-icon>
                    <span [class.success]="h.healthy" [class.error]="!h.healthy">
                      {{ h.healthy ? 'Connected' : 'Issues detected' }}
                    </span>
                    <span class="detail" *ngIf="!h.healthy">
                      {{ !h.apiKeyConfigured ? '(Missing API Key)' : (!h.storeIdConfigured ? '(Missing Store ID)' : '') }}
                    </span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>
          </div>

          <div class="form-actions">
            <button mat-raised-button color="primary" type="submit" [disabled]="marketplaceSaveState() === 'saving'">
              <mat-icon *ngIf="marketplaceSaveState() !== 'saving'">save</mat-icon>
              <mat-spinner diameter="18" *ngIf="marketplaceSaveState() === 'saving'"></mat-spinner>
              Save Marketplace Configuration
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1000px; margin: 0 auto; }
    .header-row { margin-bottom: 28px; }
    .page-title { margin: 0; font-size: 2rem; font-weight: 800; color: var(--ink); letter-spacing: -0.5px; }
    .page-subtitle { margin: 4px 0 0; color: var(--muted); font-size: 0.95rem; }

    .content-grid { display: flex; flex-direction: column; gap: 32px; }
    .config-card { border-radius: var(--radius-xl); border: 1px solid var(--line); box-shadow: var(--shadow-md); background: var(--panel); }
    
    .onboarding-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 24px; padding: 24px 0; }
    .info-item { display: flex; flex-direction: column; gap: 6px; }
    .info-item .label { font-size: 0.75rem; text-transform: uppercase; color: var(--muted); font-weight: 700; letter-spacing: 1px; }
    .info-item .value { font-weight: 700; color: var(--ink); font-size: 1.05rem; }
    
    .status-chip { 
      padding: 4px 12px; 
      border-radius: 999px; 
      font-size: 0.75rem; 
      font-weight: 700; 
      text-transform: uppercase; 
      letter-spacing: 0.5px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.05);
    }
    .status-chip.success { background: rgba(16, 185, 129, 0.12); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.2); }
    .status-chip.warn { background: rgba(245, 158, 11, 0.12); color: #d97706; border: 1px solid rgba(245, 158, 11, 0.2); }
    .status-chip.danger { background: rgba(239, 68, 68, 0.12); color: #dc2626; border: 1px solid rgba(239, 68, 68, 0.2); }

    .admin-notice { 
      display: flex; 
      align-items: center; 
      gap: 12px; 
      padding: 14px 18px; 
      background: var(--info-soft); 
      border-radius: var(--radius-md); 
      color: var(--info); 
      border: 1px solid rgba(59, 130, 246, 0.15);
      font-size: 0.85rem; 
      font-weight: 600;
    }
    .admin-notice mat-icon { font-size: 20px; width: 20px; height: 20px; color: var(--info); }

    .integration-cards { display: flex; flex-direction: column; gap: 32px; }
    
    .platform-card { 
      border-radius: var(--radius-xl); 
      border: 1px solid var(--line); 
      background: var(--panel); 
      box-shadow: var(--shadow-md); 
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      overflow: hidden;
    }
    .platform-card:hover {
      transform: translateY(-4px);
      box-shadow: var(--shadow-lg);
    }
    .platform-card.zomato {
      border-color: rgba(203, 32, 45, 0.12) !important;
      background: linear-gradient(180deg, rgba(203, 32, 45, 0.02) 0%, var(--panel) 100%);
    }
    .platform-card.zomato:hover {
      border-color: rgba(203, 32, 45, 0.3) !important;
      box-shadow: 0 12px 28px -8px rgba(203, 32, 45, 0.15) !important;
    }
    
    .platform-card.swiggy {
      border-color: rgba(252, 128, 25, 0.12) !important;
      background: linear-gradient(180deg, rgba(252, 128, 25, 0.02) 0%, var(--panel) 100%);
    }
    .platform-card.swiggy:hover {
      border-color: rgba(252, 128, 25, 0.3) !important;
      box-shadow: 0 12px 28px -8px rgba(252, 128, 25, 0.15) !important;
    }

    .platform-logo { 
      width: 44px; 
      height: 44px; 
      border-radius: var(--radius-md); 
      display: flex; 
      align-items: center; 
      justify-content: center; 
      font-weight: 900; 
      font-size: 1.25rem; 
      color: #fff; 
      margin-right: 16px; 
      box-shadow: 0 4px 10px rgba(0,0,0,0.15);
    }
    .platform-logo.zomato { background: #cb202d; }
    .platform-logo.swiggy { background: #fc8019; }
    
    .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px; padding: 24px 0; }
    ::ng-deep .form-grid .mat-mdc-text-field-wrapper { border-radius: var(--radius-md) !important; }

    .webhook-info { 
      background: var(--bg); 
      border: 1px solid var(--line);
      padding: 14px 16px; 
      border-radius: var(--radius-md); 
      font-size: 0.85rem; 
      margin-bottom: 20px; 
    }
    .webhook-info code { color: var(--ink-secondary); background: transparent; padding: 0; }

    .health-check { display: flex; align-items: center; gap: 16px; padding-top: 18px; border-top: 1px dashed var(--line); }
    .health-result { 
      display: flex; 
      align-items: center; 
      gap: 8px; 
      font-weight: 700; 
      font-size: 0.9rem; 
      padding: 6px 14px;
      border-radius: var(--radius-md);
      background: var(--bg);
      border: 1px solid var(--line);
    }
    .health-result mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .health-result .detail { font-weight: 500; color: var(--muted); font-size: 0.8rem; }
    
    .success { color: #10b981; }
    .error { color: #ef4444; }

    .form-actions { display: flex; justify-content: flex-end; margin-top: 24px; }
    .spacer { flex: 1; }
    .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 100px; color: var(--muted); }

    code { background: var(--bg); padding: 4px 8px; border-radius: var(--radius-sm); border: 1px solid var(--line); font-family: monospace; font-size: 0.85rem; }
  `]
})
export class MarketplaceSetupPageComponent implements OnInit {
  private readonly api = inject(BusinessApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snackBar = inject(MatSnackBar);

  readonly marketplaceSetup = signal<BusinessMarketplaceSetup | null>(null);
  readonly setupState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly marketplaceConfig = signal<MarketplaceConfig | null>(null);
  readonly marketplaceState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly marketplaceSaveState = signal<'idle' | 'saving' | 'saved' | 'error'>('idle');
  readonly marketplaceSaveError = signal('');
  readonly healthLoading = signal<'SWIGGY' | 'ZOMATO' | null>(null);
  readonly healthResults = signal<Record<string, {
    healthy: boolean;
    apiKeyConfigured: boolean;
    storeIdConfigured?: boolean;
    outletIdConfigured?: boolean;
  } | null>>({});

  readonly marketplaceForm = this.fb.nonNullable.group({
    zomatoApiKey: ['' as string | null, []],
    zomatoWebhookSecret: ['' as string | null, []],
    zomatoOutletId: ['' as string | null, []],
    zomatoEnabled: [false],
    swiggyApiKey: ['' as string | null, []],
    swiggyWebhookSecret: ['' as string | null, []],
    swiggyStoreId: ['' as string | null, []],
    swiggyEnabled: [false]
  });

  ngOnInit(): void {
    this.api.getMarketplaceSetup().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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
    this.api.getMarketplaceConfig().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (cfg) => {
        this.marketplaceConfig.set(cfg);
        this.marketplaceForm.patchValue({
          zomatoEnabled: cfg.zomatoEnabled,
          zomatoApiKey: '',
          zomatoWebhookSecret: '',
          zomatoOutletId: cfg.zomatoOutletId || '',
          swiggyEnabled: cfg.swiggyEnabled,
          swiggyApiKey: '',
          swiggyWebhookSecret: '',
          swiggyStoreId: cfg.swiggyStoreId || ''
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
      zomatoOutletId: rawValue.zomatoOutletId || undefined,
      zomatoEnabled: rawValue.zomatoEnabled,
      swiggyApiKey: rawValue.swiggyApiKey || undefined,
      swiggyWebhookSecret: rawValue.swiggyWebhookSecret || undefined,
      swiggyStoreId: rawValue.swiggyStoreId || undefined,
      swiggyEnabled: rawValue.swiggyEnabled
    };

    this.api.saveMarketplaceConfig(payload).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (cfg) => {
        this.marketplaceConfig.set(cfg);
        this.marketplaceSaveState.set('saved');
        this.snackBar.open('Configuration saved successfully', 'Close', { duration: 3000 });
        setTimeout(() => this.marketplaceSaveState.set('idle'), 2000);
      },
      error: (err) => {
        this.marketplaceSaveState.set('error');
        this.marketplaceSaveError.set(err?.error?.error ?? err?.error?.message ?? 'Save failed. Please try again.');
        this.snackBar.open('Failed to save configuration', 'Close', { duration: 5000 });
      }
    });
  }

  healthResult(platform: 'SWIGGY' | 'ZOMATO') {
    return this.healthResults()[platform];
  }

  runHealthCheck(platform: 'SWIGGY' | 'ZOMATO'): void {
    if (this.healthLoading()) return;
    this.healthLoading.set(platform);
    this.api.marketplaceHealthCheck(platform).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.healthResults.update(r => ({ ...r, [platform]: result }));
        this.healthLoading.set(null);
      },
      error: () => {
        this.healthResults.update(r => ({ ...r, [platform]: null }));
        this.healthLoading.set(null);
      }
    });
  }

  getOnboardingStatusClass(status?: string | null) {
    if (status === 'ACTIVE') return 'success';
    if (status === 'REJECTED' || status === 'SUSPENDED') return 'danger';
    return 'warn';
  }
}
