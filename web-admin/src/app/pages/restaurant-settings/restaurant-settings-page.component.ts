import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';

@Component({
  selector: 'app-restaurant-settings-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Restaurant Settings</h2>
        <p class="muted">GST and FSSAI lookup. Fetch business details directly from government portals.</p>
        <div class="hero-meta">
          <span class="chip">GST Lookup</span>
          <span class="chip success">FSSAI Lookup</span>
        </div>
      </section>

      <section class="panel soft-section">
        <div class="section-head">
          <h3>GST Lookup</h3>
          <p class="muted">Fetch business name and address from GSTIN.</p>
        </div>

        <div class="lookup-form">
          <div class="field-row">
            <div class="field">
              <label for="gstin">GSTIN</label>
              <input id="gstin" class="field-control" type="text" [(ngModel)]="gstin" placeholder="Enter 15-character GSTIN" maxlength="15" />
            </div>
            <button class="primary-btn" (click)="fetchGst()" [disabled]="gstin.length < 15 || gstLoading">
              {{ gstLoading ? 'Fetching...' : 'Fetch' }}
            </button>
          </div>
          <p class="hint-text" *ngIf="gstin.length > 0 && gstin.length < 15">GSTIN must be 15 characters.</p>
        </div>

        <div class="result-card" *ngIf="gstResult">
          <div class="result-row"><strong>Business Name:</strong> {{ gstResult.businessName || '-' }}</div>
          <div class="result-row"><strong>Address:</strong> {{ gstResult.address || '-' }}</div>
          <div class="result-row" *ngIf="gstResult.tradeName"><strong>Trade Name:</strong> {{ gstResult.tradeName }}</div>
        </div>

        <p class="error-text" *ngIf="gstError">{{ gstError }}</p>
      </section>

      <section class="panel soft-section">
        <div class="section-head">
          <h3>FSSAI Lookup</h3>
          <p class="muted">Fetch business name and address from FSSAI license number.</p>
        </div>

        <div class="lookup-form">
          <div class="field-row">
            <div class="field">
              <label for="fssai">FSSAI Number</label>
              <input id="fssai" class="field-control" type="text" [(ngModel)]="fssaiNo" placeholder="Enter FSSAI license number" />
            </div>
            <button class="primary-btn" (click)="fetchFssai()" [disabled]="!fssaiNo.trim() || fssaiLoading">
              {{ fssaiLoading ? 'Fetching...' : 'Fetch' }}
            </button>
          </div>
        </div>

        <div class="result-card" *ngIf="fssaiResult">
          <div class="result-row"><strong>Business Name:</strong> {{ fssaiResult.businessName || '-' }}</div>
          <div class="result-row"><strong>Address:</strong> {{ fssaiResult.address || '-' }}</div>
          <div class="result-row" *ngIf="fssaiResult.licenseType"><strong>License Type:</strong> {{ fssaiResult.licenseType }}</div>
        </div>

        <p class="error-text" *ngIf="fssaiError">{{ fssaiError }}</p>
      </section>
    </div>
  `,
  styles: [`
    .soft-section {
      padding: 1.5rem;
    }

    .lookup-form {
      margin: 1rem 0;
    }

    .field-row {
      display: flex;
      align-items: end;
      gap: 0.85rem;
    }

    .field-row .field {
      flex: 1;
    }

    .field-row .primary-btn {
      white-space: nowrap;
      height: 44px;
      padding: 0.75rem 1.5rem;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .field label {
      font-size: 0.82rem;
      font-weight: 700;
      color: var(--muted);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .hint-text {
      color: var(--muted);
      font-size: 0.8rem;
      margin: 0.35rem 0 0;
    }

    .result-card {
      margin-top: 1rem;
      padding: 1rem 1.25rem;
      background: var(--accent-soft);
      border: 1px solid rgba(29, 123, 95, 0.2);
      border-radius: 12px;
      display: grid;
      gap: 0.5rem;
    }

    .result-row {
      font-size: 0.95rem;
    }

    .error-text {
      color: var(--danger);
      font-size: 0.85rem;
      margin: 0.5rem 0 0;
    }
  `]
})
export class RestaurantSettingsPageComponent {
  private readonly api = inject(BusinessApiService);

  gstin = '';
  gstLoading = false;
  gstResult: any = null;
  gstError: string | null = null;

  fssaiNo = '';
  fssaiLoading = false;
  fssaiResult: any = null;
  fssaiError: string | null = null;

  fetchGst(): void {
    if (this.gstin.length < 15) return;
    this.gstLoading = true;
    this.gstError = null;
    this.gstResult = null;

    this.api.lookupGst(this.gstin).subscribe({
      next: (data) => {
        this.gstResult = data;
        this.gstLoading = false;
      },
      error: (err) => {
        this.gstError = err?.error?.error || 'GST lookup failed. Please check the GSTIN and try again.';
        this.gstLoading = false;
      }
    });
  }

  fetchFssai(): void {
    const val = this.fssaiNo.trim();
    if (!val) return;
    this.fssaiLoading = true;
    this.fssaiError = null;
    this.fssaiResult = null;

    this.api.lookupFssai(val).subscribe({
      next: (data) => {
        this.fssaiResult = data;
        this.fssaiLoading = false;
      },
      error: (err) => {
        this.fssaiError = err?.error?.error || 'FSSAI lookup failed. Please check the number and try again.';
        this.fssaiLoading = false;
      }
    });
  }
}
