import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  SubscriptionPlan,
  SubscriptionSummary,
  BillingCycle
} from '../../core/models/subscription.model';
import { SubscriptionService } from '../../core/services/subscription.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-subscription-section',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="panel settings-section subscription-panel" aria-labelledby="subscription-section-title">
      <div class="section-title-wrap">
        <div class="section-icon">
          <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="1" y="4" width="22" height="16" rx="2" ry="2"/>
            <line x1="1" y1="10" x2="23" y2="10"/>
          </svg>
        </div>
        <div class="title-details">
          <div class="title-row">
            <h3 id="subscription-section-title">Plan &amp; License Entitlements</h3>
            <span
              *ngIf="summary() as sub"
              class="status-badge"
              [ngClass]="'status-badge--' + sub.status.toLowerCase()">
              {{ statusLabel(sub.status) }}
            </span>
          </div>
          <p class="muted">Manage your SaaS tier, active terminal licenses, billing cycle, and feature entitlements.</p>
        </div>
      </div>

      <!-- Loading State -->
      <div *ngIf="loading()" class="sub-loading" role="status" aria-live="polite">
        <div class="sub-spinner" aria-hidden="true"></div>
        <span>Loading subscription details...</span>
      </div>

      <!-- Error State -->
      <div *ngIf="!loading() && errorMessage()" class="sub-alert sub-alert--error" role="alert">
        <span class="sub-alert__icon" aria-hidden="true">⚠</span>
        <div class="sub-alert__content">
          <strong>Subscription Error</strong>
          <p>{{ errorMessage() }}</p>
        </div>
        <button type="button" class="btn btn-secondary btn-sm" (click)="loadSubscription()">Retry</button>
      </div>

      <!-- Main Subscription Body -->
      <div *ngIf="!loading() && summary() as sub" class="subscription-content">
        <!-- Status Specific Alert Banners -->
        <div *ngIf="sub.status === 'TRIAL'" class="sub-alert sub-alert--trial" role="status">
          <span class="sub-alert__icon" aria-hidden="true">⏳</span>
          <div class="sub-alert__content">
            <strong>14-Day Free Trial Active</strong>
            <p>You have {{ sub.daysRemaining }} day(s) remaining. Select a plan below anytime to keep all multi-terminal and inventory features active.</p>
          </div>
        </div>

        <div *ngIf="sub.isInGracePeriod" class="sub-alert sub-alert--grace" role="alert">
          <span class="sub-alert__icon" aria-hidden="true">⚡</span>
          <div class="sub-alert__content">
            <strong>Grace Period Active</strong>
            <p>Your subscription period has ended. Your POS terminals remain operational during the grace period. Please select a plan to renew and avoid terminal service lockout.</p>
          </div>
        </div>

        <div *ngIf="sub.isExpired && !sub.isInGracePeriod" class="sub-alert sub-alert--expired" role="alert">
          <span class="sub-alert__icon" aria-hidden="true">⛔</span>
          <div class="sub-alert__content">
            <strong>Subscription Expired</strong>
            <p>Your subscription is inactive. Secondary terminals and advanced reports are locked. Renew now to restore full operations.</p>
          </div>
        </div>

        <!-- KPI Grid: Plan, Terminals, Staff -->
        <div class="sub-kpi-grid">
          <div class="sub-kpi-card">
            <span class="sub-kpi-card__label">Current Plan</span>
            <span class="sub-kpi-card__value">{{ sub.planName }}</span>
            <span class="sub-kpi-card__subtext">
              {{ sub.billingCycle === 'YEARLY' ? 'Billed annually' : 'Billed monthly' }} • Valid until {{ sub.currentPeriodEnd | date:'mediumDate' }}
            </span>
          </div>

          <div class="sub-kpi-card">
            <div class="kpi-card-header">
              <span class="sub-kpi-card__label">Active Terminals</span>
              <span class="quota-pill">{{ sub.activeTerminalsCount }} / {{ sub.maxTerminals }} Used</span>
            </div>
            <div class="quota-bar" role="progressbar" [attr.aria-valuenow]="sub.activeTerminalsCount" [attr.aria-valuemin]="0" [attr.aria-valuemax]="sub.maxTerminals">
              <div
                class="quota-bar__fill"
                [style.width.%]="Math.min(100, (sub.activeTerminalsCount / sub.maxTerminals) * 100)"
                [ngClass]="{'quota-bar__fill--warn': sub.activeTerminalsCount >= sub.maxTerminals}"></div>
            </div>
            <span class="sub-kpi-card__subtext">
              {{ sub.maxTerminals - sub.activeTerminalsCount > 0 ? (sub.maxTerminals - sub.activeTerminalsCount) + ' terminal slot(s) available' : 'Terminal quota fully reached' }}
            </span>
          </div>

          <div class="sub-kpi-card">
            <div class="kpi-card-header">
              <span class="sub-kpi-card__label">Staff Members</span>
              <span class="quota-pill">{{ sub.activeStaffCount }} / {{ sub.maxStaffMembers }} Active</span>
            </div>
            <div class="quota-bar" role="progressbar" [attr.aria-valuenow]="sub.activeStaffCount" [attr.aria-valuemin]="0" [attr.aria-valuemax]="sub.maxStaffMembers">
              <div
                class="quota-bar__fill"
                [style.width.%]="Math.min(100, (sub.activeStaffCount / sub.maxStaffMembers) * 100)"></div>
            </div>
            <span class="sub-kpi-card__subtext">Up to {{ sub.maxStaffMembers }} cashiers and managers</span>
          </div>
        </div>

        <!-- Feature Entitlements Checklist -->
        <div class="entitlements-box">
          <h4 class="entitlements-heading">Included in Your Plan</h4>
          <div class="entitlements-grid">
            <div class="entitlement-item" [ngClass]="{'entitlement-item--locked': !sub.features['multi_terminal']}">
              <span class="entitlement-icon" aria-hidden="true">{{ sub.features['multi_terminal'] ? '✓' : '🔒' }}</span>
              <div class="entitlement-text">
                <strong>Multi-Counter POS Sync</strong>
                <p>Connect up to {{ sub.maxTerminals }} device(s) with live offline-sync.</p>
              </div>
            </div>

            <div class="entitlement-item" [ngClass]="{'entitlement-item--locked': !sub.features['inventory']}">
              <span class="entitlement-icon" aria-hidden="true">{{ sub.features['inventory'] ? '✓' : '🔒' }}</span>
              <div class="entitlement-text">
                <strong>Inventory &amp; Recipe Tracking</strong>
                <p>Raw materials, stock decrements, and low-inventory alerts.</p>
              </div>
            </div>

            <div class="entitlement-item" [ngClass]="{'entitlement-item--locked': !sub.features['online_payments']}">
              <span class="entitlement-icon" aria-hidden="true">{{ sub.features['online_payments'] ? '✓' : '🔒' }}</span>
              <div class="entitlement-text">
                <strong>Online &amp; UPI Payments</strong>
                <p>Instant Easebuzz settlements and dynamic counter QR.</p>
              </div>
            </div>

            <div class="entitlement-item" [ngClass]="{'entitlement-item--locked': !sub.features['advanced_reports']}">
              <span class="entitlement-icon" aria-hidden="true">{{ sub.features['advanced_reports'] ? '✓' : '🔒' }}</span>
              <div class="entitlement-text">
                <strong>Financial Analytics &amp; EOD Digest</strong>
                <p>Sales breakdown, cashier shifts, and automated closing emails.</p>
              </div>
            </div>

            <div class="entitlement-item" [ngClass]="{'entitlement-item--locked': !sub.features['ai_menu']}">
              <span class="entitlement-icon" aria-hidden="true">{{ sub.features['ai_menu'] ? '✓' : '🔒' }}</span>
              <div class="entitlement-text">
                <strong>AI Smart Menu Digitizer</strong>
                <p>Photo-to-menu OCR extraction and automated catalog setup.</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Available Plans & Upgrades Section -->
        <div class="plans-section">
          <div class="plans-header">
            <div>
              <h4 class="plans-title">Available Subscription Plans</h4>
              <p class="plans-subtitle">Upgrade or switch tiers anytime. Changes take effect immediately.</p>
            </div>

            <!-- Billing Cycle Switcher -->
            <div class="cycle-switcher" role="radiogroup" aria-label="Billing cycle selector">
              <button
                type="button"
                role="radio"
                class="cycle-btn"
                [class.cycle-btn--active]="billingCycle() === 'MONTHLY'"
                [attr.aria-checked]="billingCycle() === 'MONTHLY'"
                (click)="setBillingCycle('MONTHLY')">
                Monthly
              </button>
              <button
                type="button"
                role="radio"
                class="cycle-btn"
                [class.cycle-btn--active]="billingCycle() === 'YEARLY'"
                [attr.aria-checked]="billingCycle() === 'YEARLY'"
                (click)="setBillingCycle('YEARLY')">
                Yearly <span class="save-tag">Save ~17%</span>
              </button>
            </div>
          </div>

          <!-- Plans Grid -->
          <div class="plans-grid">
            <div
              *ngFor="let plan of plans()"
              class="plan-card"
              [ngClass]="{
                'plan-card--current': plan.code === sub.planCode,
                'plan-card--featured': plan.code === 'PRO'
              }">
              <div *ngIf="plan.code === 'PRO'" class="featured-badge">MOST POPULAR</div>
              <div *ngIf="plan.code === sub.planCode" class="current-badge">CURRENT PLAN</div>

              <div class="plan-card__header">
                <h5 class="plan-card__name">{{ plan.name }}</h5>
                <p class="plan-card__desc">{{ plan.description }}</p>
                <div class="plan-card__price-box">
                  <span class="plan-card__currency">₹</span>
                  <span class="plan-card__amount">
                    {{ (billingCycle() === 'YEARLY' ? plan.priceYearly : plan.priceMonthly) | number:'1.0-0' }}
                  </span>
                  <span class="plan-card__period">
                    / {{ billingCycle() === 'YEARLY' ? 'year' : 'month' }}
                  </span>
                </div>
              </div>

              <div class="plan-card__features">
                <div class="plan-feat-row">
                  <span class="plan-feat-check">✓</span>
                  <span><strong>{{ plan.maxTerminals }}</strong> Terminal license(s)</span>
                </div>
                <div class="plan-feat-row">
                  <span class="plan-feat-check">✓</span>
                  <span><strong>{{ plan.maxStaffMembers }}</strong> Staff accounts</span>
                </div>
                <div class="plan-feat-row" [class.plan-feat-row--disabled]="!plan.inventoryEnabled">
                  <span class="plan-feat-check">{{ plan.inventoryEnabled ? '✓' : '—' }}</span>
                  <span>Inventory &amp; Recipe tracking</span>
                </div>
                <div class="plan-feat-row" [class.plan-feat-row--disabled]="!plan.advancedReportsEnabled">
                  <span class="plan-feat-check">{{ plan.advancedReportsEnabled ? '✓' : '—' }}</span>
                  <span>Advanced Analytics &amp; EOD emails</span>
                </div>
                <div class="plan-feat-row" [class.plan-feat-row--disabled]="!plan.aiMenuEnabled">
                  <span class="plan-feat-check">{{ plan.aiMenuEnabled ? '✓' : '—' }}</span>
                  <span>AI Smart Menu Digitizer</span>
                </div>
              </div>

              <div class="plan-card__action">
                <button
                  type="button"
                  class="btn plan-action-btn"
                  [disabled]="plan.code === sub.planCode || changingPlan()"
                  [ngClass]="{
                    'btn-secondary': plan.code === sub.planCode,
                    'btn-primary': plan.code !== sub.planCode
                  }"
                  (click)="confirmChangePlan(plan)">
                  <span *ngIf="plan.code === sub.planCode">Current Active Plan</span>
                  <span *ngIf="plan.code !== sub.planCode && !changingPlan()">
                    {{ getActionLabel(plan, sub) }}
                  </span>
                  <span *ngIf="changingPlan() && selectedPlan()?.code === plan.code">Switching...</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .subscription-panel {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .title-details {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .title-row {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }

    .title-row h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--text-primary, #0f172a);
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      padding: 3px 10px;
      border-radius: 9999px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .status-badge--active {
      background: #ecfdf5;
      color: #065f46;
      border: 1px solid #a7f3d0;
    }

    .status-badge--trial {
      background: #eff6ff;
      color: #1e40af;
      border: 1px solid #bfdbfe;
    }

    .status-badge--grace_period {
      background: #fffbeb;
      color: #92400e;
      border: 1px solid #fde68a;
    }

    .status-badge--expired,
    .status-badge--cancelled {
      background: #fef2f2;
      color: #991b1b;
      border: 1px solid #fecaca;
    }

    .sub-loading {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 32px 16px;
      color: var(--text-secondary, #64748b);
      justify-content: center;
      font-size: 14px;
    }

    .sub-spinner {
      width: 20px;
      height: 20px;
      border: 2px solid #e2e8f0;
      border-top-color: var(--primary-color, #2563eb);
      border-radius: 50%;
      animation: subSpin 0.8s linear infinite;
    }

    @keyframes subSpin {
      to { transform: rotate(360deg); }
    }

    .sub-alert {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 14px 16px;
      border-radius: 8px;
      font-size: 13px;
      line-height: 1.5;
    }

    .sub-alert__icon {
      font-size: 18px;
      line-height: 1;
      flex-shrink: 0;
    }

    .sub-alert__content strong {
      display: block;
      margin-bottom: 2px;
    }

    .sub-alert__content p {
      margin: 0;
      color: inherit;
      opacity: 0.95;
    }

    .sub-alert--trial {
      background: #eff6ff;
      color: #1e3a8a;
      border: 1px solid #bfdbfe;
    }

    .sub-alert--grace {
      background: #fffbeb;
      color: #78350f;
      border: 1px solid #fcd34d;
    }

    .sub-alert--expired,
    .sub-alert--error {
      background: #fef2f2;
      color: #7f1d1d;
      border: 1px solid #fca5a5;
    }

    .sub-kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-top: 8px;
    }

    .sub-kpi-card {
      background: var(--bg-primary, #f8fafc);
      border: 1px solid var(--border-color, #e2e8f0);
      border-radius: 8px;
      padding: 14px 16px;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .kpi-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }

    .sub-kpi-card__label {
      font-size: 12px;
      font-weight: 500;
      color: var(--text-secondary, #64748b);
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .sub-kpi-card__value {
      font-size: 18px;
      font-weight: 700;
      color: var(--text-primary, #0f172a);
    }

    .sub-kpi-card__subtext {
      font-size: 12px;
      color: var(--text-secondary, #64748b);
      line-height: 1.4;
    }

    .quota-pill {
      font-size: 11px;
      font-weight: 600;
      background: var(--surface-color, #ffffff);
      border: 1px solid var(--border-color, #e2e8f0);
      padding: 2px 8px;
      border-radius: 9999px;
      color: var(--text-primary, #0f172a);
    }

    .quota-bar {
      height: 6px;
      background: #e2e8f0;
      border-radius: 9999px;
      overflow: hidden;
      margin: 4px 0 2px 0;
    }

    .quota-bar__fill {
      height: 100%;
      background: var(--primary-color, #2563eb);
      border-radius: 9999px;
      transition: width 0.3s ease;
    }

    .quota-bar__fill--warn {
      background: #d97706;
    }

    .entitlements-box {
      border: 1px solid var(--border-color, #e2e8f0);
      border-radius: 8px;
      padding: 16px;
      background: var(--surface-color, #ffffff);
    }

    .entitlements-heading {
      margin: 0 0 12px 0;
      font-size: 13px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: var(--text-secondary, #64748b);
    }

    .entitlements-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 12px;
    }

    .entitlement-item {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 10px 12px;
      background: var(--bg-primary, #f8fafc);
      border: 1px solid var(--border-color, #e2e8f0);
      border-radius: 6px;
      transition: background 0.15s ease;
    }

    .entitlement-item--locked {
      opacity: 0.6;
      background: #f1f5f9;
    }

    .entitlement-icon {
      font-weight: bold;
      color: #059669;
      font-size: 14px;
      line-height: 1.4;
    }

    .entitlement-item--locked .entitlement-icon {
      color: #94a3b8;
    }

    .entitlement-text strong {
      display: block;
      font-size: 13px;
      font-weight: 600;
      color: var(--text-primary, #0f172a);
    }

    .entitlement-text p {
      margin: 2px 0 0 0;
      font-size: 11px;
      color: var(--text-secondary, #64748b);
      line-height: 1.3;
    }

    .plans-section {
      margin-top: 8px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .plans-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }

    .plans-title {
      margin: 0;
      font-size: 15px;
      font-weight: 600;
      color: var(--text-primary, #0f172a);
    }

    .plans-subtitle {
      margin: 2px 0 0 0;
      font-size: 12px;
      color: var(--text-secondary, #64748b);
    }

    .cycle-switcher {
      display: inline-flex;
      background: var(--bg-primary, #f1f5f9);
      padding: 3px;
      border-radius: 8px;
      border: 1px solid var(--border-color, #e2e8f0);
    }

    .cycle-btn {
      background: transparent;
      border: none;
      padding: 6px 14px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 500;
      color: var(--text-secondary, #64748b);
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: all 0.15s ease;
    }

    .cycle-btn--active {
      background: var(--surface-color, #ffffff);
      color: var(--text-primary, #0f172a);
      font-weight: 600;
      box-shadow: 0 1px 2px rgba(0,0,0,0.06);
    }

    .save-tag {
      background: #ecfdf5;
      color: #065f46;
      border: 1px solid #a7f3d0;
      font-size: 10px;
      padding: 1px 6px;
      border-radius: 9999px;
      font-weight: 600;
    }

    .plans-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 16px;
    }

    .plan-card {
      position: relative;
      background: var(--surface-color, #ffffff);
      border: 1px solid var(--border-color, #e2e8f0);
      border-radius: 10px;
      padding: 20px;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      gap: 16px;
      transition: transform 0.15s ease, box-shadow 0.15s ease;
    }

    .plan-card:hover {
      box-shadow: 0 4px 12px rgba(0,0,0,0.05);
    }

    .plan-card--featured {
      border-color: #3b82f6;
      box-shadow: 0 0 0 1px #3b82f6;
    }

    .plan-card--current {
      background: #fafaf9;
      border-color: #059669;
    }

    .featured-badge,
    .current-badge {
      position: absolute;
      top: -10px;
      right: 16px;
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.05em;
      padding: 3px 8px;
      border-radius: 9999px;
    }

    .featured-badge {
      background: #2563eb;
      color: #ffffff;
    }

    .current-badge {
      background: #059669;
      color: #ffffff;
    }

    .plan-card__name {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--text-primary, #0f172a);
    }

    .plan-card__desc {
      margin: 6px 0 12px 0;
      font-size: 12px;
      color: var(--text-secondary, #64748b);
      line-height: 1.4;
      min-height: 48px;
    }

    .plan-card__price-box {
      display: flex;
      align-items: baseline;
      gap: 2px;
      margin-bottom: 4px;
    }

    .plan-card__currency {
      font-size: 16px;
      font-weight: 600;
      color: var(--text-primary, #0f172a);
    }

    .plan-card__amount {
      font-size: 26px;
      font-weight: 700;
      color: var(--text-primary, #0f172a);
      line-height: 1;
    }

    .plan-card__period {
      font-size: 12px;
      color: var(--text-secondary, #64748b);
      margin-left: 2px;
    }

    .plan-card__features {
      display: flex;
      flex-direction: column;
      gap: 10px;
      border-top: 1px solid var(--border-color, #e2e8f0);
      padding-top: 14px;
    }

    .plan-feat-row {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      color: var(--text-primary, #0f172a);
    }

    .plan-feat-row--disabled {
      color: #94a3b8;
    }

    .plan-feat-check {
      font-weight: bold;
      color: #059669;
      font-size: 13px;
    }

    .plan-feat-row--disabled .plan-feat-check {
      color: #cbd5e1;
    }

    .plan-action-btn {
      width: 100%;
      padding: 9px 14px;
      font-size: 13px;
      font-weight: 600;
      border-radius: 6px;
      cursor: pointer;
    }

    @media (max-width: 640px) {
      .plans-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class SubscriptionSectionComponent implements OnInit {
  private readonly subscriptionService = inject(SubscriptionService);
  private readonly toast = inject(ToastService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly Math = Math;

  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);
  readonly summary = signal<SubscriptionSummary | null>(null);
  readonly plans = signal<SubscriptionPlan[]>([]);
  readonly billingCycle = signal<BillingCycle>('MONTHLY');
  readonly changingPlan = signal<boolean>(false);
  readonly selectedPlan = signal<SubscriptionPlan | null>(null);

  ngOnInit(): void {
    this.loadSubscription();
  }

  loadSubscription(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.subscriptionService.getSubscriptionSummary().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        if (summary.billingCycle) {
          this.billingCycle.set(summary.billingCycle);
        }
        this.loadPlans();
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.error?.message || 'Failed to load subscription details.');
        this.cdr.markForCheck();
      }
    });
  }

  private loadPlans(): void {
    this.subscriptionService.getAvailablePlans().subscribe({
      next: (plans) => {
        this.plans.set(plans);
        this.loading.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading.set(false);
        this.cdr.markForCheck();
      }
    });
  }

  setBillingCycle(cycle: BillingCycle): void {
    this.billingCycle.set(cycle);
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'Active';
      case 'TRIAL': return 'Free Trial';
      case 'GRACE_PERIOD': return 'Grace Period';
      case 'EXPIRED': return 'Expired';
      case 'CANCELLED': return 'Cancelled';
      default: return status;
    }
  }

  getActionLabel(plan: SubscriptionPlan, sub: SubscriptionSummary): string {
    if (plan.maxTerminals > sub.maxTerminals) {
      return `Upgrade to ${plan.name}`;
    }
    return `Switch to ${plan.name}`;
  }

  confirmChangePlan(plan: SubscriptionPlan): void {
    const sub = this.summary();
    if (!sub) return;

    if (sub.activeTerminalsCount > plan.maxTerminals) {
      this.toast.show(
        `Cannot switch to ${plan.name}: You have ${sub.activeTerminalsCount} active terminals, but this plan allows at most ${plan.maxTerminals}. Please deactivate excess terminals first.`,
        'error'
      );
      return;
    }

    this.changingPlan.set(true);
    this.selectedPlan.set(plan);

    this.subscriptionService.changePlan({
      planCode: plan.code,
      billingCycle: this.billingCycle()
    }).subscribe({
      next: (updated) => {
        this.summary.set(updated);
        this.changingPlan.set(false);
        this.selectedPlan.set(null);
        this.toast.show(`Successfully upgraded to ${updated.planName}!`, 'success');
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.changingPlan.set(false);
        this.selectedPlan.set(null);
        const msg = err?.error?.message || 'Failed to update plan. Please try again.';
        this.toast.show(msg, 'error');
        this.cdr.markForCheck();
      }
    });
  }
}
