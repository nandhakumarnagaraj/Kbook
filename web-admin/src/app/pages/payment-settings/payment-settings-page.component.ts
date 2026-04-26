import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { PaymentConfig, PaymentEnvironment } from '../../core/models/api.models';

@Component({
  selector: 'app-payment-settings-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="page-shell">
      <div class="section-head">
        <div>
          <h2>Payment Settings</h2>
          <p class="muted">Configure your Easebuzz payment gateway credentials.</p>
        </div>
      </div>

      <div class="panel loading" *ngIf="configState() === 'loading'">
        Loading payment configuration...
      </div>

      <!-- Current config display + toggle -->
      <div class="panel" style="margin-bottom: 1.5rem;"
           *ngIf="configState() === 'loaded' && config() as cfg">
        <div class="section-head" style="margin-bottom: 1rem;">
          <div>
            <h3>Easebuzz</h3>
            <p class="muted">Gateway: {{ cfg.gateway }} &nbsp;·&nbsp; Merchant Key: <span style="font-family:monospace;">{{ cfg.merchantKeyMasked }}</span> &nbsp;·&nbsp; Env:
              <span class="chip" [class.warn]="cfg.environment === 'TEST'" [class.success]="cfg.environment === 'PROD'">{{ cfg.environment }}</span>
            </p>
          </div>

          <!-- Toggle switch -->
          <label class="toggle-wrap" [class.disabled]="toggling()">
            <span class="toggle-label">{{ cfg.active ? 'Enabled' : 'Disabled' }}</span>
            <button
              class="toggle-btn"
              [class.on]="cfg.active"
              [disabled]="toggling()"
              (click)="toggleActive(cfg.active)"
              type="button">
              <span class="toggle-knob"></span>
            </button>
          </label>
        </div>

        <div *ngIf="toggleError()"
             style="margin-top: 0.75rem; color: #b03030; background: #fdf0f0; border-radius: 8px; padding: 0.75rem 1rem;">
          {{ toggleError() }}
        </div>
      </div>

      <!-- Not configured notice -->
      <div class="panel loading" style="margin-bottom: 1.5rem;"
           *ngIf="configState() === 'not-found'">
        No Easebuzz configuration yet. Use the form below to add one.
      </div>

      <!-- Save / Update credentials form -->
      <div class="panel" *ngIf="configState() !== 'loading'">
        <h3>{{ configState() === 'not-found' ? 'Set Up Easebuzz' : 'Update Credentials' }}</h3>
        <p class="muted" style="margin-bottom: 1rem;">
          The salt is never shown after saving — always enter it when updating credentials.
        </p>

        <form [formGroup]="form" (ngSubmit)="submit()" style="display: grid; gap: 1rem;">
          <label style="display: grid; gap: 0.4rem; font-weight: 600;">
            Merchant Key
            <input formControlName="merchantKey" placeholder="Easebuzz merchant key"
                   style="border: 1px solid var(--line); border-radius: 12px; padding: 0.9rem 1rem;">
          </label>

          <label style="display: grid; gap: 0.4rem; font-weight: 600;">
            Salt
            <input formControlName="salt" placeholder="Easebuzz salt (required every save)"
                   style="border: 1px solid var(--line); border-radius: 12px; padding: 0.9rem 1rem;">
          </label>

          <label style="display: grid; gap: 0.4rem; font-weight: 600;">
            Environment
            <select formControlName="environment"
                    style="border: 1px solid var(--line); border-radius: 12px; padding: 0.9rem 1rem; background: #fff;">
              <option value="TEST">TEST</option>
              <option value="PROD">PROD</option>
            </select>
          </label>

          <div *ngIf="saveState() === 'saved'"
               style="color: #2d7a3a; background: #eafaf0; border-radius: 8px; padding: 0.75rem 1rem;">
            Saved — Merchant Key: <strong>{{ savedMaskedKey() }}</strong>
          </div>
          <div *ngIf="saveState() === 'error'"
               style="color: #b03030; background: #fdf0f0; border-radius: 8px; padding: 0.75rem 1rem;">
            {{ saveError() }}
          </div>

          <button class="primary-btn" [disabled]="form.invalid || saveState() === 'saving'">
            {{ saveState() === 'saving' ? 'Saving...' : (configState() === 'not-found' ? 'Save Configuration' : 'Update Configuration') }}
          </button>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .toggle-wrap {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      cursor: pointer;
      user-select: none;
    }
    .toggle-wrap.disabled { opacity: 0.6; cursor: not-allowed; }
    .toggle-label { font-weight: 600; font-size: 0.95rem; min-width: 4rem; text-align: right; }
    .toggle-btn {
      position: relative;
      width: 52px;
      height: 28px;
      border-radius: 999px;
      border: none;
      background: #ccc;
      cursor: pointer;
      transition: background 0.2s;
      padding: 0;
    }
    .toggle-btn.on { background: #b56a2d; }
    .toggle-btn:disabled { cursor: not-allowed; }
    .toggle-knob {
      position: absolute;
      top: 3px;
      left: 3px;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: #fff;
      transition: transform 0.2s;
      display: block;
    }
    .toggle-btn.on .toggle-knob { transform: translateX(24px); }
  `]
})
export class PaymentSettingsPageComponent implements OnInit {
  private readonly api = inject(BusinessApiService);
  private readonly fb = inject(FormBuilder);

  readonly configState = signal<'loading' | 'not-found' | 'loaded'>('loading');
  readonly config = signal<PaymentConfig | null>(null);
  readonly saveState = signal<'idle' | 'saving' | 'saved' | 'error'>('idle');
  readonly saveError = signal('');
  readonly savedMaskedKey = signal('');
  readonly toggling = signal(false);
  readonly toggleError = signal('');

  readonly form = this.fb.nonNullable.group({
    merchantKey: ['', Validators.required],
    salt: ['', Validators.required],
    environment: ['TEST' as PaymentEnvironment, Validators.required]
  });

  ngOnInit(): void {
    this.api.getPaymentConfig().subscribe({
      next: (cfg) => {
        this.config.set(cfg);
        this.form.patchValue({ environment: cfg.environment });
        this.configState.set('loaded');
      },
      error: () => { this.configState.set('not-found'); }
    });
  }

  toggleActive(currentActive: boolean): void {
    this.toggling.set(true);
    this.toggleError.set('');
    this.api.togglePaymentConfigActive(!currentActive).subscribe({
      next: (cfg) => { this.config.set(cfg); this.toggling.set(false); },
      error: (err) => {
        this.toggleError.set(err?.error?.error ?? err?.error?.message ?? 'Failed to update status. Please try again.');
        this.toggling.set(false);
      }
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.saveState.set('saving');
    this.saveError.set('');
    this.api.savePaymentConfig(this.form.getRawValue()).subscribe({
      next: (cfg) => {
        this.config.set(cfg);
        this.savedMaskedKey.set(cfg.merchantKeyMasked);
        this.configState.set('loaded');
        this.saveState.set('saved');
        this.form.patchValue({ merchantKey: '', salt: '', environment: cfg.environment });
      },
      error: (err) => {
        this.saveState.set('error');
        this.saveError.set(err?.error?.error ?? err?.error?.message ?? 'Save failed. Please try again.');
      }
    });
  }
}
