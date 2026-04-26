import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { PaymentConfig } from '../../core/models/api.models';

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

      <!-- Current config display -->
      <div class="panel" style="margin-bottom: 1.5rem;" *ngIf="configState() === 'loaded' && config() as cfg">
        <div class="section-head">
          <div>
            <h3>Current Configuration</h3>
            <p class="muted">Gateway: {{ cfg.gateway }}</p>
          </div>
          <span class="chip" [class.success]="cfg.active" [class.danger]="!cfg.active">
            {{ cfg.active ? 'Active' : 'Inactive' }}
          </span>
        </div>
        <div class="stats-grid" style="margin-top: 1rem;">
          <article class="panel stat-card">
            <h3>Merchant Key</h3>
            <strong style="font-family: monospace;">{{ cfg.merchantKeyMasked }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Environment</h3>
            <strong>
              <span class="chip" [class.warn]="cfg.environment === 'TEST'" [class.success]="cfg.environment === 'PROD'">
                {{ cfg.environment }}
              </span>
            </strong>
          </article>
        </div>
      </div>

      <div class="panel loading" *ngIf="configState() === 'not-found'">
        No Easebuzz configuration set up yet. Use the form below to add one.
      </div>

      <div class="panel loading" *ngIf="configState() === 'loading'">
        Loading payment configuration...
      </div>

      <!-- Save / Update form -->
      <div class="panel" *ngIf="configState() !== 'loading'">
        <h3>{{ configState() === 'not-found' ? 'Set Up Easebuzz' : 'Update Credentials' }}</h3>
        <p class="muted" style="margin-bottom: 1rem;">
          The salt is never shown after saving — always enter it when updating credentials.
        </p>

        <form [formGroup]="form" (ngSubmit)="submit()" style="display: grid; gap: 1rem;">

          <label style="display: grid; gap: 0.4rem; font-weight: 600;">
            Merchant Key
            <input formControlName="merchantKey" placeholder="Easebuzz merchant key" style="border: 1px solid var(--line); border-radius: 12px; padding: 0.9rem 1rem;">
          </label>

          <label style="display: grid; gap: 0.4rem; font-weight: 600;">
            Salt
            <input formControlName="salt" placeholder="Easebuzz salt (required every save)" style="border: 1px solid var(--line); border-radius: 12px; padding: 0.9rem 1rem;">
          </label>

          <label style="display: grid; gap: 0.4rem; font-weight: 600;">
            Environment
            <select formControlName="environment" style="border: 1px solid var(--line); border-radius: 12px; padding: 0.9rem 1rem; background: #fff;">
              <option value="TEST">TEST</option>
              <option value="PROD">PROD</option>
            </select>
          </label>

          <div *ngIf="saveState() === 'saved'" class="alert" style="color: #2d7a3a; background: #eafaf0; border-radius: 8px; padding: 0.75rem 1rem;">
            Saved successfully — Merchant Key: <strong>{{ savedMaskedKey() }}</strong>
          </div>

          <div *ngIf="saveState() === 'error'" class="alert" style="color: #b03030; background: #fdf0f0; border-radius: 8px; padding: 0.75rem 1rem;">
            {{ saveError() }}
          </div>

          <button class="primary-btn" [disabled]="form.invalid || saveState() === 'saving'">
            {{ saveState() === 'saving' ? 'Saving...' : (configState() === 'not-found' ? 'Save Configuration' : 'Update Configuration') }}
          </button>

        </form>
      </div>

    </div>
  `
})
export class PaymentSettingsPageComponent implements OnInit {
  private readonly api = inject(BusinessApiService);
  private readonly fb = inject(FormBuilder);

  readonly configState = signal<'loading' | 'not-found' | 'loaded'>('loading');
  readonly config = signal<PaymentConfig | null>(null);
  readonly saveState = signal<'idle' | 'saving' | 'saved' | 'error'>('idle');
  readonly saveError = signal('');
  readonly savedMaskedKey = signal('');

  readonly form = this.fb.nonNullable.group({
    merchantKey: ['', Validators.required],
    salt: ['', Validators.required],
    environment: ['TEST' as 'TEST' | 'PROD', Validators.required]
  });

  ngOnInit(): void {
    this.api.getPaymentConfig().subscribe({
      next: (cfg) => {
        this.config.set(cfg);
        this.form.patchValue({ environment: cfg.environment as 'TEST' | 'PROD' });
        this.configState.set('loaded');
      },
      error: () => {
        this.configState.set('not-found');
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
        this.form.patchValue({ merchantKey: '', salt: '', environment: cfg.environment as 'TEST' | 'PROD' });
      },
      error: (err) => {
        this.saveState.set('error');
        this.saveError.set(err?.error?.error ?? err?.error?.message ?? 'Save failed. Please try again.');
      }
    });
  }
}
