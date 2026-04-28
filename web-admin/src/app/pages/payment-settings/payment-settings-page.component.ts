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
      <section class="panel page-hero">
        <h2>Payment Settings</h2>
        <p class="muted">Configure Easebuzz credentials with a cleaner layout for status, environment, and update actions.</p>
        <div class="hero-meta">
          <span class="chip">Gateway Setup</span>
          <span class="chip success">Payment Ready</span>
        </div>
      </section>

      <div class="panel loading-panel" *ngIf="configState() === 'loading'">
        <span class="spinner"></span> Loading payment configuration...
      </div>

      <div class="panel config-card soft-section" *ngIf="configState() === 'loaded' && config() as cfg">
        <div class="card-header">
          <div class="card-info">
            <h3>Easebuzz</h3>
            <p class="muted meta-row">
              <span>Gateway: <strong>{{ cfg.gateway }}</strong></span>
              <span class="dot">.</span>
              <span>Merchant Key: <code>{{ cfg.merchantKeyMasked }}</code></span>
              <span class="dot">.</span>
              <span>
                Environment:
                <span class="chip" [class.warn]="cfg.environment === 'TEST'" [class.success]="cfg.environment === 'PROD'">
                  {{ cfg.environment }}
                </span>
              </span>
            </p>
          </div>

          <div class="card-actions">
            <button class="outline-btn" type="button" (click)="openModal()">
              Update Credentials
            </button>

            <label class="toggle-wrap" [class.disabled]="toggling()">
              <span class="toggle-label">{{ cfg.active ? 'Enabled' : 'Disabled' }}</span>
              <button
                class="toggle-btn"
                [class.on]="cfg.active"
                [disabled]="toggling()"
                (click)="toggleActive(cfg.active)"
                type="button"
              >
                <span class="toggle-knob"></span>
              </button>
            </label>
          </div>
        </div>

        <div class="toast-success" *ngIf="saveState() === 'saved'">
          Configuration updated. Merchant Key: <strong>{{ savedMaskedKey() }}</strong>
        </div>

        <div class="toggle-error" *ngIf="toggleError()">
          {{ toggleError() }}
        </div>
      </div>

      <div class="panel empty-card" *ngIf="configState() === 'not-found'">
        <div class="empty-icon">Rs</div>
        <p class="empty-text">No payment gateway configured yet.</p>
        <p class="muted">Connect Easebuzz to accept UPI and card payments from your customers.</p>
        <button class="primary-btn setup-btn" type="button" (click)="openModal()">
          Set Up Easebuzz
        </button>
      </div>
    </div>

    <div class="modal-overlay" *ngIf="modalOpen()" (click)="onOverlayClick($event)">
      <div class="modal-box" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <div class="modal-header">
          <div>
            <h3 id="modal-title">{{ configState() === 'not-found' ? 'Set Up Easebuzz' : 'Update Credentials' }}</h3>
            <p class="muted modal-sub">
              The salt is <span class="never">never</span> shown after saving, so enter it on every update.
            </p>
          </div>
          <button class="close-btn" type="button" (click)="closeModal()" aria-label="Close">x</button>
        </div>

        <form [formGroup]="form" (ngSubmit)="submit()" class="modal-form">
          <label class="field-label">
            Merchant Key
            <input
              class="field-input"
              formControlName="merchantKey"
              placeholder="Easebuzz merchant key"
              autocomplete="off"
            />
          </label>

          <label class="field-label">
            Salt
            <input
              class="field-input"
              formControlName="salt"
              type="password"
              placeholder="Easebuzz salt (required every save)"
              autocomplete="new-password"
            />
          </label>

          <label class="field-label">
            Environment
            <select class="field-input field-select" formControlName="environment">
              <option value="TEST">TEST - Sandbox</option>
              <option value="PROD">PROD - Live</option>
            </select>
          </label>

          <div class="save-error" *ngIf="saveState() === 'error'">
            {{ saveError() }}
          </div>

          <div class="modal-footer">
            <button class="ghost-btn" type="button" (click)="closeModal()" [disabled]="saveState() === 'saving'">
              Cancel
            </button>
            <button class="primary-btn" type="submit" [disabled]="form.invalid || saveState() === 'saving'">
              <span class="btn-spinner" *ngIf="saveState() === 'saving'"></span>
              {{ saveState() === 'saving' ? 'Saving...' : (configState() === 'not-found' ? 'Save Configuration' : 'Update Configuration') }}
            </button>
          </div>
        </form>
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

    .card-actions {
      display: flex;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
    }

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

    .outline-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.55rem 1.1rem;
      border: 1.5px solid #c4a05a;
      border-radius: 10px;
      background: transparent;
      color: #b56a2d;
      font-weight: 600;
      font-size: 0.88rem;
      cursor: pointer;
      transition: background 0.18s, color 0.18s;
      white-space: nowrap;
    }

    .outline-btn:hover { background: rgba(196, 160, 90, 0.1); }

    .toggle-wrap {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      cursor: pointer;
      user-select: none;
    }

    .toggle-wrap.disabled { opacity: 0.6; cursor: not-allowed; }
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
    .toggle-btn:disabled { cursor: not-allowed; }

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

    .toggle-error {
      margin-top: 0.75rem;
      color: #b03030;
      background: #fdf0f0;
      border-radius: 8px;
      padding: 0.75rem 1rem;
      font-size: 0.9rem;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(-4px); }
      to { opacity: 1; transform: none; }
    }

    .empty-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 0.5rem;
      padding: 2.5rem 2rem;
    }

    .empty-icon {
      width: 4rem;
      height: 4rem;
      border-radius: 50%;
      display: grid;
      place-items: center;
      background: var(--brand-soft);
      color: var(--brand-deep);
      font-weight: 800;
      letter-spacing: 0.04em;
    }

    .empty-text { font-weight: 600; font-size: 1rem; margin: 0.25rem 0 0; }
    .setup-btn { margin-top: 1rem; padding: 0.75rem 2rem; }

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

    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(20, 12, 4, 0.45);
      backdrop-filter: blur(4px);
      -webkit-backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
      animation: overlayIn 0.2s ease;
    }

    @keyframes overlayIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    .modal-box {
      background: #fff;
      border-radius: 18px;
      width: 100%;
      max-width: 480px;
      box-shadow: 0 24px 60px rgba(0, 0, 0, 0.2), 0 8px 20px rgba(0, 0, 0, 0.1);
      animation: slideUp 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
      overflow: hidden;
    }

    @keyframes slideUp {
      from { opacity: 0; transform: translateY(20px) scale(0.97); }
      to { opacity: 1; transform: none; }
    }

    .modal-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      padding: 1.5rem 1.5rem 0;
    }

    .modal-header h3 { margin: 0 0 0.3rem; font-size: 1.1rem; }
    .modal-sub { margin: 0; font-size: 0.85rem; }
    .never { color: #c0392b; font-style: italic; font-weight: 600; }

    .close-btn {
      flex-shrink: 0;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      border: none;
      background: rgba(0, 0, 0, 0.06);
      color: #666;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.18s, color 0.18s;
      margin-top: -0.1rem;
      text-transform: uppercase;
    }

    .close-btn:hover { background: rgba(0, 0, 0, 0.12); color: #333; }

    .modal-form {
      display: grid;
      gap: 1rem;
      padding: 1.25rem 1.5rem 0;
    }

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

    .field-select { appearance: auto; cursor: pointer; }

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
      padding: 1.25rem 0 1.5rem;
    }

    .ghost-btn {
      padding: 0.65rem 1.25rem;
      border: 1.5px solid var(--line, #ddd);
      border-radius: 10px;
      background: transparent;
      color: #666;
      font-weight: 600;
      font-size: 0.9rem;
      cursor: pointer;
      transition: border-color 0.18s, color 0.18s;
    }

    .ghost-btn:hover { border-color: #aaa; color: #333; }
    .ghost-btn:disabled { opacity: 0.5; cursor: not-allowed; }

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
  readonly modalOpen = signal(false);

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

  openModal(): void {
    this.saveState.set('idle');
    this.saveError.set('');
    this.form.reset({
      merchantKey: '',
      salt: '',
      environment: this.config()?.environment ?? 'TEST'
    });
    this.modalOpen.set(true);
  }

  closeModal(): void {
    if (this.saveState() === 'saving') return;
    this.modalOpen.set(false);
    this.saveState.set('idle');
  }

  onOverlayClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-overlay')) {
      this.closeModal();
    }
  }

  toggleActive(currentActive: boolean): void {
    this.toggling.set(true);
    this.toggleError.set('');
    this.api.togglePaymentConfigActive(!currentActive).subscribe({
      next: (cfg) => {
        this.config.set(cfg);
        this.toggling.set(false);
      },
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
        setTimeout(() => this.modalOpen.set(false), 800);
      },
      error: (err) => {
        this.saveState.set('error');
        this.saveError.set(err?.error?.error ?? err?.error?.message ?? 'Save failed. Please try again.');
      }
    });
  }
}
