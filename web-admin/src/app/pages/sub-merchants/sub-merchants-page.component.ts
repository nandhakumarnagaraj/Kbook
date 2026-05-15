import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { EasebuzzSubMerchant, EasebuzzSubMerchantRequest } from '../../core/models/api.models';
import { formatDate } from '../../shared/formatters';

const STATUS_OPTIONS = ['ALL', 'DRAFT', 'PENDING_KYC', 'KYC_SUBMITTED', 'ACTIVE', 'SUSPENDED', 'REJECTED', 'FAILED'] as const;
const KYC_OPTIONS = ['ALL', 'PENDING', 'SUBMITTED', 'ACTIVATED', 'FAILED'] as const;
const BUSINESS_TYPES = ['SOLE_PROPRIETORSHIP', 'PARTNERSHIP', 'PRIVATE_LIMITED', 'PUBLIC_LIMITED', 'OTHERS'] as const;

function getSubMerchantStatusChip(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'chip success';
    case 'PENDING_KYC': return 'chip warn';
    case 'KYC_SUBMITTED': return 'chip info';
    case 'SUSPENDED': case 'REJECTED': case 'FAILED': return 'chip danger';
    default: return 'chip';
  }
}

function getKycChipClass(kycStatus: string | null): string {
  if (!kycStatus) return 'chip';
  switch (kycStatus) {
    case 'ACTIVATED': return 'chip success';
    case 'SUBMITTED': return 'chip info';
    case 'FAILED': return 'chip danger';
    default: return 'chip warn';
  }
}

function formatStatus(status: string): string {
  return status.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

@Component({
  selector: 'app-sub-merchants-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Sub-Merchants</h2>
        <p class="muted">Manage Easebuzz settlement sub-merchant onboarding, KYC, and lifecycle.</p>
        <div class="hero-meta">
          <span class="chip">Admin Access</span>
          <span class="chip success">Easebuzz Settlement</span>
          <span class="chip warn">KYC Workflow</span>
        </div>
      </section>

      <div class="dash-mini">
        <div class="mini-card total"><strong>{{ stats.total }}</strong><span>Total</span></div>
        <div class="mini-card active"><strong>{{ stats.active }}</strong><span>Active</span></div>
        <div class="mini-card pending"><strong>{{ stats.pending }}</strong><span>Pending KYC</span></div>
        <div class="mini-card rejected"><strong>{{ stats.rejected }}</strong><span>Rejected</span></div>
      </div>

      <div class="toolbar">
        <div>
          <h3>Sub-Merchant Directory</h3>
          <p class="muted">{{ subMerchants().length }} sub-merchants onboarded</p>
        </div>
        <div style="display: flex; gap: 0.5rem; flex-wrap:wrap;">
          <button class="ghost-btn" (click)="exportCsv()">📄 CSV</button>
          <a href="https://dashboard.easebuzz.in" target="_blank" rel="noopener noreferrer" class="ghost-btn" style="text-decoration:none;">🔗 Easebuzz</a>
          <button class="ghost-btn" (click)="loadSubMerchants()">Refresh</button>
          <button class="primary-btn" (click)="openCreate()">+ New Sub-Merchant</button>
        </div>
      </div>

      <section class="panel filter-panel" *ngIf="loaded() && subMerchants().length">
        <div class="filter-grid">
          <div class="filter-group">
            <label for="search">Search</label>
            <input id="search" class="field-control" type="text" [(ngModel)]="searchTerm" (ngModelChange)="resetPage()" placeholder="Search by business name, email, or contact" />
          </div>
          <div class="filter-group">
            <label for="status-filter">Status</label>
            <select id="status-filter" class="field-select" [(ngModel)]="statusFilter" (ngModelChange)="resetPage()">
              <option *ngFor="let s of statusOptions" [value]="s">{{ s === 'ALL' ? 'All statuses' : formatStatusValue(s) }}</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="kyc-filter">KYC</label>
            <select id="kyc-filter" class="field-select" [(ngModel)]="kycFilter" (ngModelChange)="resetPage()">
              <option *ngFor="let k of kycOptions" [value]="k">{{ k === 'ALL' ? 'All KYC' : k }}</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="page-size">Rows</label>
            <select id="page-size" class="field-select" [(ngModel)]="pageSize" (ngModelChange)="resetPage()">
              <option [ngValue]="5">5</option>
              <option [ngValue]="10">10</option>
              <option [ngValue]="20">20</option>
            </select>
          </div>
        </div>
        <div class="filter-summary">
          <p class="muted">{{ filteredSubMerchants.length }} of {{ subMerchants().length }} sub-merchants</p>
          <button class="ghost-btn" (click)="clearFilters()">Clear filters</button>
        </div>
      </section>

      <ng-template #loading>
        <div class="panel loading">{{ loaded() ? 'No sub-merchants match the current filters.' : (loadError() || 'Loading sub-merchants...') }}</div>
      </ng-template>

      <div class="panel table-wrap" *ngIf="loaded() && pagedSubMerchants.length; else loading">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Business Name</th>
              <th>Status</th>
              <th>KYC Status</th>
              <th>Contact</th>
              <th>Commission</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let sm of pagedSubMerchants">
              <td>{{ sm.id }}</td>
              <td>
                <div class="stacked-meta">
                  <strong>{{ sm.businessName }}</strong>
                  <span class="muted">#{{ sm.restaurantId }}</span>
                </div>
              </td>
              <td><span [class]="getStatusChip(sm.status)">{{ formatStatusValue(sm.status) }}</span></td>
              <td><span [class]="getKycChip(sm.kycStatus)">{{ sm.kycStatus || '-' }}</span></td>
              <td>
                <div class="stacked-meta">
                  <span>{{ sm.contactEmail || '-' }}</span>
                  <span class="muted">{{ sm.contactPhone || '' }}</span>
                </div>
              </td>
              <td>{{ sm.commissionRate != null ? sm.commissionRate + '%' : '-' }}</td>
              <td>{{ formatDateValue(sm.createdAt) }}</td>
              <td>
                <div class="action-cell">
                  <button class="ghost-btn" (click)="viewDetail(sm)" title="View">👁️</button>
                  <button class="ghost-btn" (click)="openEdit(sm)" title="Edit">✏️</button>
                  <button class="ghost-btn" *ngIf="sm.status === 'DRAFT'" (click)="assignSubMerchantId(sm)" title="Assign Easebuzz ID">
                    <span class="chip warn" style="padding:0.2rem 0.5rem; font-size:0.7rem;">Assign ID</span>
                  </button>
                  <select class="status-select" (change)="quickStatusAction(sm, $event)" *ngIf="sm.status !== 'DRAFT'">
                    <option value="" disabled selected>Status...</option>
                    <option value="ACTIVE" *ngIf="sm.status === 'PENDING_KYC' || sm.status === 'KYC_SUBMITTED'">✅ Mark Active</option>
                    <option value="SUSPENDED" *ngIf="sm.status === 'ACTIVE'">⏸️ Suspend</option>
                    <option value="REJECTED" *ngIf="sm.status === 'PENDING_KYC' || sm.status === 'KYC_SUBMITTED'">❌ Reject</option>
                    <option value="PENDING_KYC" *ngIf="sm.status === 'ACTIVE' || sm.status === 'SUSPENDED'">🔄 Reset to Pending KYC</option>
                  </select>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="pagination-bar" *ngIf="filteredSubMerchants.length > pageSize">
          <p class="muted">Page {{ currentPage }} of {{ totalPages }}</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="currentPage === 1" (click)="goToPage(currentPage - 1)">Previous</button>
            <button class="ghost-btn" [disabled]="currentPage === totalPages" (click)="goToPage(currentPage + 1)">Next</button>
          </div>
        </div>
      </div>

      <div class="panel soft-section" *ngIf="selectedSubMerchant() as sm">
        <div class="section-head">
          <div>
            <h3>{{ sm.businessName }}</h3>
            <p class="muted">Sub-Merchant ID: {{ sm.id }} | Restaurant: #{{ sm.restaurantId }}</p>
          </div>
          <div style="display:flex; gap:0.5rem; align-items:center; flex-wrap:wrap;">
            <button class="ghost-btn" (click)="closeDetail()">Close</button>
          </div>
        </div>

        <div class="stats-grid">
          <article class="panel stat-card">
            <h3>Status</h3>
            <strong><span [class]="getStatusChip(sm.status)">{{ formatStatusValue(sm.status) }}</span></strong>
          </article>
          <article class="panel stat-card">
            <h3>Easebuzz ID</h3>
            <strong style="font-size:1rem;"><code>{{ sm.subMerchantId || '-' }}</code></strong>
          </article>
          <article class="panel stat-card">
            <h3>Commission</h3>
            <strong>{{ sm.commissionRate != null ? sm.commissionRate + '%' : '-' }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Business Type</h3>
            <strong>{{ sm.businessType || '-' }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Contact</h3>
            <strong style="font-size:1rem;">{{ sm.contactEmail || '-' }}<br *ngIf="sm.contactEmail" /><span class="muted" style="font-size:0.85rem;">{{ sm.contactPhone || '' }}</span></strong>
          </article>
          <article class="panel stat-card">
            <h3>PAN / GST</h3>
            <strong style="font-size:0.95rem;">{{ sm.pan || '-' }}<br /><span class="muted" style="font-size:0.85rem;">{{ sm.gst || '' }}</span></strong>
          </article>
          <article class="panel stat-card" style="grid-column: span 2;">
            <h3>Beneficiary</h3>
            <strong style="font-size:1rem;">{{ sm.beneficiaryName || '-' }}</strong>
            <p class="muted" style="margin:0.25rem 0 0;">{{ sm.bankAccountNo ? 'A/C: ' + sm.bankAccountNo : '' }} {{ sm.ifsc ? 'IFSC: ' + sm.ifsc : '' }}</p>
          </article>
          <article class="panel stat-card" style="grid-column: span 2;">
            <h3>Business Address</h3>
            <p style="margin:0; white-space:pre-wrap;">{{ sm.businessAddress || '-' }}</p>
          </article>
        </div>

        <div class="detail-section">
          <h3>Status Timeline</h3>
          <div class="timeline">
            <div class="timeline-step" [class.completed]="true">
              <div class="step-dot"></div>
              <div class="step-info">
                <strong>Draft Created</strong>
                <span class="muted">{{ formatDateValue(sm.createdAt) }}</span>
              </div>
            </div>
            <div class="timeline-step" [class.completed]="sm.status !== 'DRAFT'">
              <div class="step-dot"></div>
              <div class="step-info">
                <strong>Registered with Easebuzz</strong>
                <span class="muted">{{ sm.subMerchantId ? 'Sub-Merchant ID: ' + sm.subMerchantId : 'Pending' }}</span>
              </div>
            </div>
            <div class="timeline-step" [class.completed]="!!sm.kycPortalUrl">
              <div class="step-dot"></div>
              <div class="step-info">
                <strong>KYC Generated</strong>
                <span class="muted">{{ sm.kycPortalUrl ? 'Portal URL available' : 'Pending' }}</span>
              </div>
            </div>
            <div class="timeline-step" [class.completed]="sm.kycStatus === 'SUBMITTED' || sm.kycStatus === 'ACTIVATED'">
              <div class="step-dot"></div>
              <div class="step-info">
                <strong>KYC Submitted</strong>
                <span class="muted">{{ sm.kycSubmittedAt ? formatDateValue(sm.kycSubmittedAt) : 'Pending' }}</span>
              </div>
            </div>
            <div class="timeline-step" [class.completed]="sm.kycStatus === 'ACTIVATED'">
              <div class="step-dot"></div>
              <div class="step-info">
                <strong>Activated</strong>
                <span class="muted">{{ sm.kycActivatedAt ? formatDateValue(sm.kycActivatedAt) : 'Pending' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="detail-section" *ngIf="sm.kycPortalUrl">
          <h3>KYC Portal</h3>
          <div class="kyc-section">
            <div class="kyc-info">
              <p><strong>Status:</strong> <span [class]="getKycChip(sm.kycStatus)">{{ sm.kycStatus || 'PENDING' }}</span></p>
              <p><strong>Portal URL:</strong> <a [href]="sm.kycPortalUrl" target="_blank" rel="noopener noreferrer">{{ sm.kycPortalUrl }}</a></p>
              <p *ngIf="sm.kycSubmittedAt"><strong>Submitted:</strong> {{ formatDateValue(sm.kycSubmittedAt) }}</p>
              <p *ngIf="sm.kycActivatedAt"><strong>Activated:</strong> {{ formatDateValue(sm.kycActivatedAt) }}</p>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h3>Transaction Summary</h3>
          <div class="panel loading" style="margin:0;">Transaction history will be available once the sub-merchant is active.</div>
        </div>
      </div>
    </div>

    <div class="modal-overlay" *ngIf="showFormModal()" (click)="closeForm()">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <h3>{{ editingSubMerchant() ? 'Edit' : 'Create' }} Sub-Merchant</h3>
        <form [formGroup]="subMerchantForm" (ngSubmit)="submitForm()">
          <div class="form-grid">
            <label class="field-label">
              Business Name *
              <input class="field-control" formControlName="businessName" placeholder="Enter business name" />
              <span class="field-error" *ngIf="subMerchantForm.get('businessName')?.invalid && subMerchantForm.get('businessName')?.touched">Required</span>
            </label>
            <label class="field-label">
              Business Type *
              <select class="field-select" formControlName="businessType">
                <option value="" disabled>Select type</option>
                <option *ngFor="let bt of businessTypes" [value]="bt">{{ bt }}</option>
              </select>
              <span class="field-error" *ngIf="subMerchantForm.get('businessType')?.invalid && subMerchantForm.get('businessType')?.touched">Required</span>
            </label>
            <label class="field-label">
              PAN *
              <input class="field-control" formControlName="pan" placeholder="AAAAA1234A" maxlength="10" style="text-transform:uppercase;" />
              <span class="field-error" *ngIf="subMerchantForm.get('pan')?.invalid && subMerchantForm.get('pan')?.touched">Invalid PAN format (e.g. AAAAA1234A)</span>
            </label>
            <label class="field-label">
              GST
              <input class="field-control" formControlName="gst" placeholder="Optional" maxlength="15" />
            </label>
            <label class="field-label">
              Bank Account No *
              <input class="field-control" formControlName="bankAccountNo" placeholder="Enter account number" />
              <span class="field-error" *ngIf="subMerchantForm.get('bankAccountNo')?.invalid && subMerchantForm.get('bankAccountNo')?.touched">Required</span>
            </label>
            <label class="field-label">
              IFSC Code *
              <input class="field-control" formControlName="ifsc" placeholder="ABCD0123456" maxlength="11" style="text-transform:uppercase;" />
              <span class="field-error" *ngIf="subMerchantForm.get('ifsc')?.invalid && subMerchantForm.get('ifsc')?.touched">Invalid IFSC format (e.g. ABCD0123456)</span>
            </label>
            <label class="field-label">
              Beneficiary Name *
              <input class="field-control" formControlName="beneficiaryName" placeholder="Enter beneficiary name" />
              <span class="field-error" *ngIf="subMerchantForm.get('beneficiaryName')?.invalid && subMerchantForm.get('beneficiaryName')?.touched">Required</span>
            </label>
            <label class="field-label">
              Contact Email *
              <input class="field-control" formControlName="contactEmail" type="email" placeholder="email@example.com" />
              <span class="field-error" *ngIf="subMerchantForm.get('contactEmail')?.invalid && subMerchantForm.get('contactEmail')?.touched">Invalid email</span>
            </label>
            <label class="field-label">
              Contact Phone *
              <input class="field-control" formControlName="contactPhone" placeholder="9876543210" maxlength="10" />
              <span class="field-error" *ngIf="subMerchantForm.get('contactPhone')?.invalid && subMerchantForm.get('contactPhone')?.touched">Invalid phone (10 digits starting with 6-9)</span>
            </label>
            <label class="field-label">
              Commission Rate (%) *
              <input class="field-control" formControlName="commissionRate" type="number" step="0.01" min="0" max="100" placeholder="e.g. 2.5" />
              <span class="field-error" *ngIf="subMerchantForm.get('commissionRate')?.invalid && subMerchantForm.get('commissionRate')?.touched">Required (0-100)</span>
            </label>
            <label class="field-label" style="grid-column: 1 / -1;">
              Business Address
              <textarea class="field-control" formControlName="businessAddress" rows="3" placeholder="Enter business address"></textarea>
            </label>
          </div>

          <div class="save-error" *ngIf="formError()">{{ formError() }}</div>

          <div class="modal-footer">
            <button type="button" class="ghost-btn" (click)="closeForm()">Cancel</button>
            <button type="submit" class="primary-btn" [disabled]="formSaving()">
              <span *ngIf="formSaving()" class="btn-spinner"></span>
              {{ editingSubMerchant() ? 'Update' : 'Create' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <div class="toast-success" *ngIf="actionFeedback() as fb">
      {{ fb.message }}
    </div>
  `,
  styles: [`
    .dash-mini { display:grid; grid-template-columns:repeat(auto-fit,minmax(120px,1fr)); gap:.75rem; }
    .mini-card { background:var(--panel); border:1px solid var(--line); border-radius:12px; padding:.75rem 1rem; text-align:center; box-shadow:var(--shadow-soft); }
    .mini-card strong { display:block; font-size:1.5rem; font-weight:800; }
    .mini-card span { font-size:.78rem; color:var(--muted); text-transform:uppercase; letter-spacing:.04em; }
    .mini-card.total strong { color:var(--brand); }
    .mini-card.active strong { color:var(--accent); }
    .mini-card.pending strong { color:#e67e22; }
    .mini-card.rejected strong { color:var(--danger); }
    .status-select { padding:.3rem .5rem; border-radius:8px; border:1px solid var(--line); background:var(--bg); font-size:.78rem; cursor:pointer; min-width:110px; }
    .action-cell {
      display: flex;
      gap: 0.35rem;
      align-items: center;
      flex-wrap: nowrap;
    }

    .action-cell .ghost-btn {
      padding: 0.4rem 0.5rem;
      font-size: 0.8rem;
    }

    .chip.info {
      background: rgba(52, 152, 219, 0.14);
      color: #2980b9;
    }

    .detail-section {
      margin-top: 1.5rem;
    }

    .detail-section h3 {
      margin-bottom: 0.75rem;
      font-size: 1.05rem;
    }

    .timeline {
      display: flex;
      flex-direction: column;
      gap: 0;
      padding: 0.5rem 0;
    }

    .timeline-step {
      display: flex;
      align-items: flex-start;
      gap: 0.85rem;
      padding: 0.75rem 0;
      position: relative;
      opacity: 0.5;
    }

    .timeline-step.completed {
      opacity: 1;
    }

    .timeline-step:not(:last-child)::before {
      content: '';
      position: absolute;
      left: 8px;
      top: 32px;
      bottom: -4px;
      width: 2px;
      background: var(--line);
    }

    .timeline-step.completed:not(:last-child)::before {
      background: var(--accent);
    }

    .step-dot {
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: var(--line);
      border: 3px solid var(--panel);
      flex-shrink: 0;
      margin-top: 2px;
    }

    .timeline-step.completed .step-dot {
      background: var(--accent);
    }

    .step-info {
      display: grid;
      gap: 0.15rem;
    }

    .kyc-section {
      padding: 1rem;
      border: 1px solid var(--line);
      border-radius: 12px;
      background: rgba(255,255,255,0.6);
    }

    .kyc-info {
      display: grid;
      gap: 0.5rem;
    }

    .kyc-info p {
      margin: 0;
    }

    .kyc-info a {
      color: var(--brand);
      word-break: break-all;
    }

    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.35);
      display: flex;
      align-items: flex-start;
      justify-content: center;
      padding: 2rem;
      z-index: 1000;
      overflow-y: auto;
    }

    .modal-dialog {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 18px;
      box-shadow: var(--shadow);
      padding: 1.5rem;
      max-width: 720px;
      width: 100%;
      margin-top: 2rem;
      animation: fadeSlideIn 0.2s ease;
    }

    .modal-dialog h3 {
      margin: 0 0 1.25rem;
      font-size: 1.2rem;
    }

    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    .field-label {
      display: grid;
      gap: 0.35rem;
      font-weight: 600;
      font-size: 0.88rem;
    }

    .field-error {
      color: var(--danger);
      font-size: 0.78rem;
      font-weight: 400;
    }

    .save-error {
      color: var(--danger);
      background: var(--danger-soft);
      border-radius: 8px;
      padding: 0.7rem 1rem;
      font-size: 0.88rem;
      margin-top: 1rem;
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      padding: 1.25rem 0 0;
    }

    .btn-spinner {
      display: inline-block;
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255,255,255,0.4);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    @keyframes fadeSlideIn {
      from { opacity: 0; transform: translateY(-12px); }
      to { opacity: 1; transform: none; }
    }

    .toast-success {
      position: fixed;
      bottom: 1.5rem;
      right: 1.5rem;
      color: #2d7a3a;
      background: #eafaf0;
      border: 1px solid #a8dbb8;
      border-radius: 10px;
      padding: 0.85rem 1.25rem;
      font-size: 0.9rem;
      box-shadow: var(--shadow);
      z-index: 1100;
      animation: fadeIn 0.3s ease;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(8px); }
      to { opacity: 1; transform: none; }
    }

    .primary-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      transform: none;
    }
  `]
})
export class SubMerchantsPageComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);

  readonly subMerchants = signal<EasebuzzSubMerchant[]>([]);
  readonly loaded = signal(false);
  readonly loadError = signal('');
  readonly selectedSubMerchant = signal<EasebuzzSubMerchant | null>(null);
  readonly showFormModal = signal(false);
  readonly editingSubMerchant = signal<EasebuzzSubMerchant | null>(null);
  readonly formSaving = signal(false);
  readonly formError = signal('');
  readonly actionFeedback = signal<{ message: string } | null>(null);

  searchTerm = '';
  statusFilter: string = 'ALL';
  kycFilter: string = 'ALL';
  pageSize = 10;
  currentPage = 1;

  readonly statusOptions = STATUS_OPTIONS;
  readonly kycOptions = KYC_OPTIONS;
  readonly businessTypes = BUSINESS_TYPES;

  readonly subMerchantForm = this.fb.nonNullable.group({
    businessName: ['', Validators.required],
    businessType: ['', Validators.required],
    pan: ['', [Validators.required, Validators.pattern(/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/)]],
    gst: [''],
    bankAccountNo: ['', Validators.required],
    ifsc: ['', [Validators.required, Validators.pattern(/^[A-Z]{4}0[A-Z0-9]{6}$/)]],
    beneficiaryName: ['', Validators.required],
    businessAddress: [''],
    contactEmail: ['', [Validators.required, Validators.email]],
    contactPhone: ['', [Validators.required, Validators.pattern(/^[6-9]\d{9}$/)]],
    commissionRate: [0, [Validators.required, Validators.min(0), Validators.max(100)]]
  });

  ngOnInit(): void {
    this.loadSubMerchants();
  }

  get filteredSubMerchants(): EasebuzzSubMerchant[] {
    const search = this.searchTerm.trim().toLowerCase();
    return this.subMerchants().filter((sm) => {
      const matchesSearch = !search || [
        sm.businessName,
        sm.contactEmail ?? '',
        sm.contactPhone ?? '',
        String(sm.id),
        String(sm.restaurantId)
      ].some((v) => v.toLowerCase().includes(search));
      const matchesStatus = this.statusFilter === 'ALL' || sm.status === this.statusFilter;
      const matchesKyc = this.kycFilter === 'ALL' || (sm.kycStatus ?? 'PENDING') === this.kycFilter;
      return matchesSearch && matchesStatus && matchesKyc;
    });
  }

  get pagedSubMerchants(): EasebuzzSubMerchant[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredSubMerchants.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredSubMerchants.length / this.pageSize));
  }

  loadSubMerchants(): void {
    this.loaded.set(false);
    this.loadError.set('');
    this.api.getSubMerchants().subscribe({
      next: (data) => {
        this.subMerchants.set(data);
        this.loaded.set(true);
        this.currentPage = 1;
      },
      error: () => {
        this.subMerchants.set([]);
        this.loadError.set('Unable to load sub-merchants.');
        this.loaded.set(true);
      }
    });
  }

  resetPage(): void {
    this.currentPage = 1;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.statusFilter = 'ALL';
    this.kycFilter = 'ALL';
    this.pageSize = 10;
    this.currentPage = 1;
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(1, page), this.totalPages);
  }

  openCreate(): void {
    this.editingSubMerchant.set(null);
    this.subMerchantForm.reset({
      businessName: '',
      businessType: '',
      pan: '',
      gst: '',
      bankAccountNo: '',
      ifsc: '',
      beneficiaryName: '',
      businessAddress: '',
      contactEmail: '',
      contactPhone: '',
      commissionRate: 0
    });
    this.formError.set('');
    this.showFormModal.set(true);
  }

  openEdit(merchant: EasebuzzSubMerchant): void {
    this.editingSubMerchant.set(merchant);
    this.subMerchantForm.patchValue({
      businessName: merchant.businessName,
      businessType: merchant.businessType ?? '',
      pan: merchant.pan ?? '',
      gst: merchant.gst ?? '',
      bankAccountNo: merchant.bankAccountNo ?? '',
      ifsc: merchant.ifsc ?? '',
      beneficiaryName: merchant.beneficiaryName ?? '',
      businessAddress: merchant.businessAddress ?? '',
      contactEmail: merchant.contactEmail ?? '',
      contactPhone: merchant.contactPhone ?? '',
      commissionRate: merchant.commissionRate ?? 0
    });
    this.formError.set('');
    this.showFormModal.set(true);
  }

  closeForm(): void {
    this.showFormModal.set(false);
    this.editingSubMerchant.set(null);
  }

  submitForm(): void {
    if (this.subMerchantForm.invalid) {
      this.subMerchantForm.markAllAsTouched();
      return;
    }

    this.formSaving.set(true);
    this.formError.set('');
    const raw = this.subMerchantForm.getRawValue();

    const payload: EasebuzzSubMerchantRequest = {
      businessName: raw.businessName,
      businessType: raw.businessType,
      pan: raw.pan,
      gst: raw.gst || undefined,
      bankAccountNo: raw.bankAccountNo,
      ifsc: raw.ifsc,
      beneficiaryName: raw.beneficiaryName,
      businessAddress: raw.businessAddress,
      contactEmail: raw.contactEmail,
      contactPhone: raw.contactPhone,
      commissionRate: raw.commissionRate
    };

    const edit = this.editingSubMerchant();
    const request = edit
      ? this.api.updateSubMerchant(edit.id, payload)
      : this.api.createSubMerchant(payload);

    request.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.closeForm();
        this.loadSubMerchants();
        this.showFeedback(edit ? 'Sub-merchant updated successfully.' : 'Sub-merchant created successfully.');
      },
      error: (err) => {
        this.formSaving.set(false);
        this.formError.set(err?.error?.error ?? err?.error?.message ?? 'Operation failed. Please try again.');
      }
    });
  }

  viewDetail(merchant: EasebuzzSubMerchant): void {
    this.selectedSubMerchant.set(merchant);
    window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
  }

  closeDetail(): void {
    this.selectedSubMerchant.set(null);
  }

  assignSubMerchantId(merchant: EasebuzzSubMerchant): void {
    const subMerchantId = prompt(`Enter Easebuzz Sub-Merchant ID for "${merchant.businessName}":\n\n(After creating this merchant manually in Easebuzz Dashboard https://dashboard.easebuzz.in)`, '');
    if (!subMerchantId || subMerchantId.trim() === '') return;
    this.api.assignSubMerchantId(merchant.id, subMerchantId.trim()).subscribe({
      next: () => {
        this.showFeedback('Sub-merchant ID assigned successfully. Status set to PENDING_KYC.');
        this.loadSubMerchants();
      },
      error: (err) => {
        this.showFeedback(err?.error?.error ?? 'Failed to assign sub-merchant ID.', true);
      }
    });
  }

  generateKyc(merchant: EasebuzzSubMerchant): void {
    if (!confirm(`Generate KYC access for "${merchant.businessName}"?`)) return;
    this.api.generateKyc(merchant.id).subscribe({
      next: () => {
        this.loadSubMerchants();
        this.showFeedback('KYC access key generated successfully.');
      },
      error: () => {
        this.showFeedback('KYC generation failed. Please try again.');
      }
    });
  }

  get stats() {
    const all = this.subMerchants();
    return {
      total: all.length,
      active: all.filter(s => s.status === 'ACTIVE').length,
      pending: all.filter(s => s.status === 'PENDING_KYC' || s.status === 'KYC_SUBMITTED').length,
      rejected: all.filter(s => s.status === 'REJECTED' || s.status === 'FAILED').length
    };
  }

  quickStatusAction(merchant: EasebuzzSubMerchant, event: Event): void {
    const newStatus = (event.target as HTMLSelectElement).value;
    if (!newStatus) return;
    if (!confirm(`Change "${merchant.businessName}" status to ${newStatus}?`)) return;
    this.api.updateSubMerchantStatus(merchant.id, newStatus).subscribe({
      next: () => {
        this.showFeedback(`Status updated to ${newStatus}`);
        this.loadSubMerchants();
      },
      error: () => this.showFeedback('Status update failed.', true)
    });
  }

  exportCsv(): void {
    const headers = ['ID,Business Name,Status,KYC,Email,Phone,PAN,GST,Commission,Sub-Merchant ID,Created'];
    const rows = this.subMerchants().map(s =>
      `${s.id},"${s.businessName}",${s.status},${s.kycStatus || ''},${s.contactEmail || ''},${s.contactPhone || ''},${s.pan || ''},${s.gst || ''},${s.commissionRate ?? ''},${s.subMerchantId || ''},${s.createdAt || ''}`
    );
    const csv = [...headers, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'sub-merchants.csv'; a.click();
    URL.revokeObjectURL(url);
  }

  private showFeedback(message: string, _isError?: boolean): void {
    this.actionFeedback.set({ message });
    setTimeout(() => this.actionFeedback.set(null), 4000);
  }

  getStatusChip(status: string): string {
    return getSubMerchantStatusChip(status);
  }

  getKycChip(kycStatus: string | null): string {
    return getKycChipClass(kycStatus);
  }

  formatStatusValue(status: string): string {
    return formatStatus(status);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }
}
