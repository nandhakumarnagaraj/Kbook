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
          <p class="page-subtitle">Configure Zomato & Swiggy integrations. Direct settlement routing is managed via Easebuzz onboarding.</p>
        </div>
      </div>

      <div class="loading-state" *ngIf="setupState() === 'loading' || marketplaceState() === 'loading'">
        <mat-spinner diameter="40" color="primary"></mat-spinner>
        <p>Loading integration data...</p>
      </div>

      <div class="content-grid" *ngIf="setupState() === 'loaded' && marketplaceState() === 'loaded'">
        <!-- Easebuzz Onboarding Progress -->
        <div class="config-card onboarding-card" *ngIf="marketplaceSetup() as setup">
          <div class="onboarding-header">
            <div class="onboarding-title-group">
              <mat-icon class="onboarding-icon">account_balance</mat-icon>
              <div>
                <h3>Easebuzz Settlement Onboarding</h3>
                <p>Settlement routing status for {{ setup.shopName || ('Shop #' + setup.restaurantId) }}</p>
              </div>
            </div>
            <span class="status-badge" [class]="getOnboardingStatusClass(setup.subMerchantStatus)">
              {{ setup.subMerchantStatus || 'NOT STARTED' }}
            </span>
          </div>

          <!-- Timeline Steps -->
          <div class="timeline-container">
            <div class="timeline-track">
              <!-- Step 1 -->
              <div class="timeline-step" [class.completed]="isStepCompleted(1, setup.subMerchantStatus)" [class.active]="isStepActive(1, setup.subMerchantStatus)">
                <div class="step-icon-wrapper">
                  <mat-icon>{{ isStepCompleted(1, setup.subMerchantStatus) ? 'check' : 'edit_note' }}</mat-icon>
                </div>
                <div class="step-content">
                  <div class="step-title">Draft Setup</div>
                  <div class="step-desc">Account created in system</div>
                </div>
              </div>
              
              <div class="timeline-connector" [class.completed]="isStepCompleted(2, setup.subMerchantStatus)"></div>

              <!-- Step 2 -->
              <div class="timeline-step" [class.completed]="isStepCompleted(2, setup.subMerchantStatus)" [class.active]="isStepActive(2, setup.subMerchantStatus)">
                <div class="step-icon-wrapper">
                  <mat-icon>{{ isStepCompleted(2, setup.subMerchantStatus) ? 'check' : 'cloud_upload' }}</mat-icon>
                </div>
                <div class="step-content">
                  <div class="step-title">Submitted</div>
                  <div class="step-desc">Sent to Easebuzz portal</div>
                </div>
              </div>

              <div class="timeline-connector" [class.completed]="isStepCompleted(3, setup.subMerchantStatus)"></div>

              <!-- Step 3 -->
              <div class="timeline-step" [class.completed]="isStepCompleted(3, setup.subMerchantStatus)" [class.active]="isStepActive(3, setup.subMerchantStatus)">
                <div class="step-icon-wrapper">
                  <mat-icon>{{ isStepCompleted(3, setup.subMerchantStatus) ? 'check' : 'fingerprint' }}</mat-icon>
                </div>
                <div class="step-content">
                  <div class="step-title">KYC Check</div>
                  <div class="step-desc">Verification link ready</div>
                </div>
              </div>

              <div class="timeline-connector" [class.completed]="isStepCompleted(4, setup.subMerchantStatus)"></div>

              <!-- Step 4 -->
              <div class="timeline-step" [class.completed]="isStepCompleted(4, setup.subMerchantStatus)" [class.active]="isStepActive(4, setup.subMerchantStatus)">
                <div class="step-icon-wrapper">
                  <mat-icon>{{ isStepCompleted(4, setup.subMerchantStatus) ? 'check' : 'rocket_launch' }}</mat-icon>
                </div>
                <div class="step-content">
                  <div class="step-title">Active</div>
                  <div class="step-desc">Settlements processing</div>
                </div>
              </div>
            </div>
          </div>

          <!-- Onboarding details -->
          <div class="onboarding-details">
            <div class="detail-tile">
              <span class="tile-label">Sub-Merchant ID</span>
              <span class="tile-val highlight">
                <code>{{ setup.subMerchantId || 'Pending registration' }}</code>
                <button mat-icon-button type="button" class="copy-btn" *ngIf="setup.subMerchantId" (click)="copyToClipboard(setup.subMerchantId)" matTooltip="Copy ID">
                  <mat-icon>content_copy</mat-icon>
                </button>
              </span>
            </div>
            
            <div class="detail-tile">
              <span class="tile-label">KYC Portal Status</span>
              <span class="tile-val">
                <span class="val-text">{{ setup.kycPortalUrl ? 'Verification Link Ready' : 'Pending Link Generation' }}</span>
                <a mat-stroked-button color="primary" class="portal-link-btn" *ngIf="setup.kycPortalUrl" [href]="setup.kycPortalUrl" target="_blank">
                  <mat-icon>open_in_new</mat-icon> Open Portal
                </a>
              </span>
            </div>

            <div class="detail-tile">
              <span class="tile-label">Settlement Path</span>
              <span class="tile-val">
                {{ setup.subMerchantStatus === 'ACTIVE' ? 'Easebuzz Sub-merchant Settlement (Direct)' : 'KhanaBook Aggregated / Pending' }}
              </span>
            </div>
          </div>

          <div class="admin-notice">
            <mat-icon>info</mat-icon>
            <span>Easebuzz onboarding and merchant registration is managed by KhanaBook Admins. Contact support to update bank or business registration info.</span>
          </div>
        </div>

        <!-- Marketplace Integration Cards -->
        <form [formGroup]="marketplaceForm" class="config-form" (ngSubmit)="saveMarketplaceConfig()">
          <div class="integration-grid">
            
            <!-- ZOMATO -->
            <div class="platform-card zomato" [class.enabled]="marketplaceForm.get('zomatoEnabled')?.value">
              <div class="platform-card-header">
                <div class="platform-logo zomato">Z</div>
                <div class="header-text">
                  <h3>Zomato Integration</h3>
                  <p>Sync store status, catalog and accept real-time orders</p>
                </div>
                <div class="header-toggle">
                  <mat-slide-toggle formControlName="zomatoEnabled">
                    <span class="toggle-status" [class.active]="marketplaceForm.get('zomatoEnabled')?.value">
                      {{ marketplaceForm.get('zomatoEnabled')?.value ? 'Active' : 'Disabled' }}
                    </span>
                  </mat-slide-toggle>
                </div>
              </div>

              <div class="platform-card-body">
                <!-- Overlay message when disabled -->
                <div class="disabled-overlay" *ngIf="!marketplaceForm.get('zomatoEnabled')?.value">
                  <mat-icon class="lock-icon">lock_open</mat-icon>
                  <p>Integration is disabled. Toggle to active to configure credentials and store details.</p>
                </div>

                <div class="form-fields-container" [class.hidden]="!marketplaceForm.get('zomatoEnabled')?.value">
                  <div class="input-row">
                    <mat-form-field appearance="outline" class="w-100">
                      <mat-label>API Key</mat-label>
                      <mat-icon matSuffix>vpn_key</mat-icon>
                      <input matInput formControlName="zomatoApiKey" [type]="marketplaceSaveState() === 'saved' ? 'password' : 'text'" placeholder="Enter Zomato API Key">
                      <mat-hint *ngIf="marketplaceConfig()?.zomatoApiKeyMasked">Stored: {{ marketplaceConfig()?.zomatoApiKeyMasked }}</mat-hint>
                    </mat-form-field>
                  </div>

                  <div class="input-row-grid">
                    <mat-form-field appearance="outline">
                      <mat-label>Webhook Secret</mat-label>
                      <mat-icon matSuffix>security</mat-icon>
                      <input matInput formControlName="zomatoWebhookSecret" type="password" placeholder="Enter Secret">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Outlet ID</mat-label>
                      <mat-icon matSuffix>storefront</mat-icon>
                      <input matInput formControlName="zomatoOutletId" placeholder="Zomato Outlet ID">
                    </mat-form-field>
                  </div>

                  <div class="webhook-block" *ngIf="marketplaceConfig()?.zomatoWebhookUrl">
                    <div class="webhook-header">
                      <span>ZOMATO WEBHOOK ENDPOINT</span>
                      <button mat-icon-button type="button" (click)="copyToClipboard(marketplaceConfig()?.zomatoWebhookUrl || '')" matTooltip="Copy Link">
                        <mat-icon>content_copy</mat-icon>
                      </button>
                    </div>
                    <code class="webhook-code">{{ marketplaceConfig()?.zomatoWebhookUrl }}</code>
                  </div>

                  <div class="diagnostics-panel">
                    <button mat-flat-button class="check-btn" type="button" (click)="runHealthCheck('ZOMATO')" [disabled]="healthLoading() === 'ZOMATO'">
                      <mat-icon *ngIf="healthLoading() !== 'ZOMATO'">shield_heart</mat-icon>
                      <mat-spinner diameter="18" *ngIf="healthLoading() === 'ZOMATO'"></mat-spinner>
                      <span>Check Connection</span>
                    </button>

                    <div class="health-result-indicator" *ngIf="healthResult('ZOMATO') as h" [class.success]="h.healthy" [class.error]="!h.healthy">
                      <mat-icon>{{ h.healthy ? 'verified_user' : 'warning_amber' }}</mat-icon>
                      <div class="result-text">
                        <div class="status-title">{{ h.healthy ? 'Active' : 'Issues detected' }}</div>
                        <div class="status-subtitle" *ngIf="!h.healthy">
                          {{ !h.apiKeyConfigured ? 'API Key is missing' : (!h.outletIdConfigured ? 'Outlet ID is missing' : 'Connection failed') }}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- SWIGGY -->
            <div class="platform-card swiggy" [class.enabled]="marketplaceForm.get('swiggyEnabled')?.value">
              <div class="platform-card-header">
                <div class="platform-logo swiggy">S</div>
                <div class="header-text">
                  <h3>Swiggy Integration</h3>
                  <p>Sync store status, catalog and accept real-time orders</p>
                </div>
                <div class="header-toggle">
                  <mat-slide-toggle formControlName="swiggyEnabled">
                    <span class="toggle-status" [class.active]="marketplaceForm.get('swiggyEnabled')?.value">
                      {{ marketplaceForm.get('swiggyEnabled')?.value ? 'Active' : 'Disabled' }}
                    </span>
                  </mat-slide-toggle>
                </div>
              </div>

              <div class="platform-card-body">
                <!-- Overlay message when disabled -->
                <div class="disabled-overlay" *ngIf="!marketplaceForm.get('swiggyEnabled')?.value">
                  <mat-icon class="lock-icon">lock_open</mat-icon>
                  <p>Integration is disabled. Toggle to active to configure credentials and store details.</p>
                </div>

                <div class="form-fields-container" [class.hidden]="!marketplaceForm.get('swiggyEnabled')?.value">
                  <div class="input-row">
                    <mat-form-field appearance="outline" class="w-100">
                      <mat-label>API Key</mat-label>
                      <mat-icon matSuffix>vpn_key</mat-icon>
                      <input matInput formControlName="swiggyApiKey" [type]="marketplaceSaveState() === 'saved' ? 'password' : 'text'" placeholder="Enter Swiggy API Key">
                      <mat-hint *ngIf="marketplaceConfig()?.swiggyApiKeyMasked">Stored: {{ marketplaceConfig()?.swiggyApiKeyMasked }}</mat-hint>
                    </mat-form-field>
                  </div>

                  <div class="input-row-grid">
                    <mat-form-field appearance="outline">
                      <mat-label>Webhook Secret</mat-label>
                      <mat-icon matSuffix>security</mat-icon>
                      <input matInput formControlName="swiggyWebhookSecret" type="password" placeholder="Enter Secret">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Store ID</mat-label>
                      <mat-icon matSuffix>storefront</mat-icon>
                      <input matInput formControlName="swiggyStoreId" placeholder="Swiggy Store ID">
                    </mat-form-field>
                  </div>

                  <div class="webhook-block" *ngIf="marketplaceConfig()?.swiggyWebhookUrl">
                    <div class="webhook-header">
                      <span>SWIGGY WEBHOOK ENDPOINT</span>
                      <button mat-icon-button type="button" (click)="copyToClipboard(marketplaceConfig()?.swiggyWebhookUrl || '')" matTooltip="Copy Link">
                        <mat-icon>content_copy</mat-icon>
                      </button>
                    </div>
                    <code class="webhook-code">{{ marketplaceConfig()?.swiggyWebhookUrl }}</code>
                  </div>

                  <div class="diagnostics-panel">
                    <button mat-flat-button class="check-btn" type="button" (click)="runHealthCheck('SWIGGY')" [disabled]="healthLoading() === 'SWIGGY'">
                      <mat-icon *ngIf="healthLoading() !== 'SWIGGY'">shield_heart</mat-icon>
                      <mat-spinner diameter="18" *ngIf="healthLoading() === 'SWIGGY'"></mat-spinner>
                      <span>Check Connection</span>
                    </button>

                    <div class="health-result-indicator" *ngIf="healthResult('SWIGGY') as h" [class.success]="h.healthy" [class.error]="!h.healthy">
                      <mat-icon>{{ h.healthy ? 'verified_user' : 'warning_amber' }}</mat-icon>
                      <div class="result-text">
                        <div class="status-title">{{ h.healthy ? 'Active' : 'Issues detected' }}</div>
                        <div class="status-subtitle" *ngIf="!h.healthy">
                          {{ !h.apiKeyConfigured ? 'API Key is missing' : (!h.storeIdConfigured ? 'Store ID is missing' : 'Connection failed') }}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

          </div>

          <div class="form-actions-bar">
            <button mat-flat-button color="primary" class="save-config-btn" type="submit" [disabled]="marketplaceSaveState() === 'saving'">
              <mat-icon *ngIf="marketplaceSaveState() !== 'saving'">save</mat-icon>
              <mat-spinner diameter="20" color="accent" *ngIf="marketplaceSaveState() === 'saving'"></mat-spinner>
              <span>Save Configuration</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      padding: 32px 24px;
      max-width: 1100px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 32px;
    }
    .content-grid {
      display: flex;
      flex-direction: column;
      gap: 32px;
    }
    .header-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid var(--line);
      padding-bottom: 20px;
    }
    .page-title {
      margin: 0;
      font-size: 2.25rem;
      font-weight: 800;
      color: var(--ink);
      letter-spacing: -0.8px;
    }
    .page-subtitle {
      margin: 6px 0 0;
      color: var(--muted);
      font-size: 1rem;
    }

    .config-card.onboarding-card {
      border-radius: var(--radius-xl);
      border: 1px solid var(--line);
      background: var(--panel);
      box-shadow: var(--shadow-md);
      overflow: hidden;
      padding: 24px;
    }
    .onboarding-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 28px;
    }
    .onboarding-title-group {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .onboarding-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
      color: var(--brand);
    }
    .onboarding-title-group h3 {
      margin: 0;
      font-size: 1.3rem;
      font-weight: 700;
      color: var(--ink);
    }
    .onboarding-title-group p {
      margin: 2px 0 0;
      color: var(--muted);
      font-size: 0.85rem;
    }
    .status-badge {
      padding: 6px 14px;
      border-radius: 99px;
      font-size: 0.75rem;
      font-weight: 800;
      letter-spacing: 0.8px;
      text-transform: uppercase;
      box-shadow: var(--shadow-sm);
    }
    .status-badge.success {
      background: rgba(22, 163, 74, 0.1);
      color: var(--accent);
      border: 1px solid rgba(22, 163, 74, 0.2);
    }
    .status-badge.warn {
      background: rgba(217, 119, 6, 0.1);
      color: var(--warn);
      border: 1px solid rgba(217, 119, 6, 0.2);
    }
    .status-badge.danger {
      background: rgba(220, 38, 38, 0.1);
      color: var(--danger);
      border: 1px solid rgba(220, 38, 38, 0.2);
    }

    /* Stepper Timeline */
    .timeline-container {
      margin: 32px 0;
      padding: 0 10px;
    }
    .timeline-track {
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: relative;
    }
    .timeline-step {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      position: relative;
      z-index: 2;
      flex: 1;
      text-align: center;
    }
    .step-icon-wrapper {
      width: 42px;
      height: 42px;
      border-radius: 50%;
      background: var(--surface);
      border: 2px solid var(--line-strong);
      color: var(--muted);
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.3s ease;
    }
    .step-icon-wrapper mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
    .timeline-step.completed .step-icon-wrapper {
      background: var(--accent);
      border-color: var(--accent);
      color: #fff;
      box-shadow: 0 0 12px rgba(22, 163, 74, 0.3);
    }
    .timeline-step.active .step-icon-wrapper {
      background: var(--brand);
      border-color: var(--brand);
      color: #fff;
      box-shadow: 0 0 12px rgba(200, 90, 0, 0.3);
      animation: pulse-step 2s infinite;
    }
    @keyframes pulse-step {
      0% { transform: scale(1); }
      50% { transform: scale(1.08); }
      100% { transform: scale(1); }
    }
    .step-content {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .step-title {
      font-size: 0.9rem;
      font-weight: 700;
      color: var(--ink);
    }
    .step-desc {
      font-size: 0.75rem;
      color: var(--muted);
    }
    .timeline-connector {
      height: 3px;
      background: var(--line-strong);
      flex: 1.5;
      margin: 0 -20px;
      margin-top: -24px;
      transition: all 0.3s ease;
      z-index: 1;
    }
    .timeline-connector.completed {
      background: var(--accent);
    }

    /* Details Grid */
    .onboarding-details {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 20px;
      margin-bottom: 24px;
      padding-top: 24px;
      border-top: 1px solid var(--line);
    }
    .detail-tile {
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: var(--radius-lg);
      padding: 18px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .tile-label {
      font-size: 0.75rem;
      font-weight: 800;
      color: var(--muted);
      text-transform: uppercase;
      letter-spacing: 0.8px;
    }
    .tile-val {
      font-size: 1rem;
      font-weight: 700;
      color: var(--ink);
      display: flex;
      align-items: center;
      gap: 12px;
      justify-content: space-between;
    }
    .tile-val.highlight {
      font-family: monospace;
      color: var(--brand);
    }
    .tile-val.highlight code {
      font-size: 0.95rem;
      color: var(--brand);
      background: transparent;
      border: none;
      padding: 0;
    }
    .copy-btn {
      color: var(--muted);
      width: 32px;
      height: 32px;
      line-height: 32px;
      transition: color 0.2s ease;
    }
    .copy-btn:hover {
      color: var(--brand);
    }
    .portal-link-btn {
      height: 32px;
      font-size: 0.8rem;
      font-weight: 700;
      border-radius: var(--radius-md);
    }

    .admin-notice {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 18px;
      background: var(--info-soft);
      border-radius: var(--radius-md);
      color: var(--info);
      border: 1px solid rgba(2, 132, 199, 0.15);
      font-size: 0.85rem;
      font-weight: 600;
      margin-top: 8px;
    }
    .admin-notice mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
      color: var(--info);
    }

    /* Integration Grid */
    .integration-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(460px, 1fr));
      gap: 28px;
    }
    @media (max-width: 1024px) {
      .integration-grid {
        grid-template-columns: 1fr;
      }
      .timeline-track {
        flex-direction: column;
        align-items: flex-start;
        gap: 20px;
        padding-left: 20px;
      }
      .timeline-track::before {
        content: '';
        position: absolute;
        left: 31px;
        top: 10px;
        bottom: 10px;
        width: 3px;
        background: var(--line-strong);
        z-index: 1;
      }
      .timeline-step {
        flex-direction: row;
        text-align: left;
        gap: 16px;
      }
      .timeline-connector {
        display: none;
      }
    }

    .platform-card {
      border-radius: var(--radius-xl);
      border: 1px solid var(--line);
      background: var(--panel);
      box-shadow: var(--shadow-md);
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      display: flex;
      flex-direction: column;
      overflow: hidden;
      position: relative;
    }
    .platform-card:hover {
      transform: translateY(-4px);
      box-shadow: var(--shadow-lg);
    }
    
    .platform-card.zomato {
      border-color: rgba(203, 32, 45, 0.08);
      background: linear-gradient(180deg, rgba(203, 32, 45, 0.015) 0%, var(--panel) 100%);
    }
    .platform-card.zomato.enabled {
      border-color: rgba(203, 32, 45, 0.25);
    }
    .platform-card.zomato.enabled:hover {
      border-color: rgba(203, 32, 45, 0.4);
      box-shadow: 0 12px 30px -8px rgba(203, 32, 45, 0.12);
    }

    .platform-card.swiggy {
      border-color: rgba(252, 128, 25, 0.08);
      background: linear-gradient(180deg, rgba(252, 128, 25, 0.015) 0%, var(--panel) 100%);
    }
    .platform-card.swiggy.enabled {
      border-color: rgba(252, 128, 25, 0.25);
    }
    .platform-card.swiggy.enabled:hover {
      border-color: rgba(252, 128, 25, 0.4);
      box-shadow: 0 12px 30px -8px rgba(252, 128, 25, 0.12);
    }

    .platform-card-header {
      padding: 24px;
      display: flex;
      align-items: center;
      border-bottom: 1px solid var(--line);
    }
    
    .platform-logo {
      width: 48px;
      height: 48px;
      border-radius: var(--radius-md);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 900;
      font-size: 1.5rem;
      color: #fff;
      margin-right: 18px;
      box-shadow: var(--shadow-md);
      transition: all 0.3s ease;
    }
    .platform-card:hover .platform-logo {
      transform: scale(1.06);
    }
    .platform-logo.zomato { background: #cb202d; }
    .platform-logo.swiggy { background: #fc8019; }

    .header-text {
      flex: 1;
    }
    .header-text h3 {
      margin: 0;
      font-size: 1.15rem;
      font-weight: 700;
      color: var(--ink);
    }
    .header-text p {
      margin: 3px 0 0;
      font-size: 0.8rem;
      color: var(--muted);
    }
    .header-toggle {
      display: flex;
      align-items: center;
    }
    
    .toggle-status {
      font-size: 0.8rem;
      font-weight: 700;
      color: var(--muted);
      margin-left: 8px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .toggle-status.active {
      color: var(--brand-light);
      font-weight: 800;
    }

    .platform-card-body {
      padding: 24px;
      flex: 1;
      display: flex;
      flex-direction: column;
      position: relative;
    }

    .disabled-overlay {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: 40px 20px;
      color: var(--muted);
      gap: 12px;
      flex: 1;
    }
    .disabled-overlay .lock-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: var(--muted);
      opacity: 0.5;
    }
    .disabled-overlay p {
      margin: 0;
      font-size: 0.9rem;
      max-width: 280px;
      line-height: 1.4;
    }

    .form-fields-container {
      display: flex;
      flex-direction: column;
      gap: 18px;
      animation: fadeIn 0.4s ease;
    }
    .form-fields-container.hidden {
      display: none;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(10px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .input-row-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    @media (max-width: 480px) {
      .input-row-grid {
        grid-template-columns: 1fr;
      }
    }

    ::ng-deep .form-fields-container .mat-mdc-text-field-wrapper {
      border-radius: var(--radius-md) !important;
      background: var(--bg) !important;
    }

    /* Webhook Info Block */
    .webhook-block {
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: var(--radius-lg);
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .webhook-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.7rem;
      font-weight: 800;
      color: var(--muted);
      letter-spacing: 1px;
    }
    .webhook-header button {
      width: 28px;
      height: 28px;
      line-height: 28px;
    }
    .webhook-code {
      font-family: monospace;
      font-size: 0.8rem;
      color: var(--ink-secondary);
      word-break: break-all;
      background: transparent !important;
      border: none !important;
      padding: 0 !important;
    }

    /* Diagnostics / Health Panel */
    .diagnostics-panel {
      display: flex;
      align-items: center;
      gap: 16px;
      padding-top: 18px;
      border-top: 1px dashed var(--line);
      margin-top: 6px;
    }
    .check-btn {
      height: 40px;
      border-radius: var(--radius-md);
      font-size: 0.85rem;
      font-weight: 700;
      background: var(--surface) !important;
      color: var(--ink) !important;
      border: 1px solid var(--line-strong);
      display: flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s ease;
    }
    .check-btn:hover {
      background: var(--surface-hover) !important;
      border-color: var(--brand);
    }
    .check-btn mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: var(--brand-light);
    }

    .health-result-indicator {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 14px;
      border-radius: var(--radius-md);
      flex: 1;
      border: 1px solid transparent;
      animation: slideIn 0.3s ease;
    }
    @keyframes slideIn {
      from { opacity: 0; transform: translateX(-10px); }
      to { opacity: 1; transform: translateX(0); }
    }
    
    .health-result-indicator.success {
      background: rgba(22, 163, 74, 0.08);
      color: var(--accent);
      border-color: rgba(22, 163, 74, 0.15);
    }
    .health-result-indicator.error {
      background: rgba(220, 38, 38, 0.08);
      color: var(--danger);
      border-color: rgba(220, 38, 38, 0.15);
    }
    .health-result-indicator mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
    .result-text {
      display: flex;
      flex-direction: column;
    }
    .status-title {
      font-size: 0.85rem;
      font-weight: 700;
    }
    .status-subtitle {
      font-size: 0.75rem;
      opacity: 0.8;
    }

    /* Save Config Action Bar */
    .form-actions-bar {
      display: flex;
      justify-content: flex-end;
      padding-top: 16px;
    }
    .save-config-btn {
      padding: 0 28px;
      height: 48px;
      border-radius: var(--radius-lg);
      font-weight: 700;
      font-size: 0.95rem;
      box-shadow: var(--shadow-md);
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      background: linear-gradient(135deg, var(--brand) 0%, var(--brand-light) 100%) !important;
      color: #fff !important;
    }
    .save-config-btn:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-lg);
    }
    .save-config-btn:disabled {
      background: var(--surface) !important;
      color: var(--muted) !important;
      box-shadow: none;
      transform: none;
    }
    .save-config-btn mat-spinner {
      margin-right: 8px;
    }

    .loading-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 80px 20px;
      color: var(--muted);
      gap: 16px;
    }
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

  isStepCompleted(step: number, status?: string | null): boolean {
    if (!status) return false;
    if (status === 'ACTIVE') return true;
    if (step === 1) return true; // Draft is always complete
    if (step === 2) return status !== 'NOT_STARTED'; // Submitted
    if (step === 3) return status !== 'NOT_STARTED' && status !== 'PENDING_KYC'; // KYC portal link generated
    return false;
  }

  isStepActive(step: number, status?: string | null): boolean {
    if (status === 'ACTIVE') return step === 4;
    if (!status || status === 'NOT_STARTED') return step === 1;
    if (status === 'PENDING_KYC') return step === 3;
    return false;
  }

  copyToClipboard(text: string): void {
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => {
      this.snackBar.open('Copied to clipboard!', 'Close', { duration: 2000 });
    }).catch(() => {
      this.snackBar.open('Failed to copy text', 'Close', { duration: 2000 });
    });
  }
}
