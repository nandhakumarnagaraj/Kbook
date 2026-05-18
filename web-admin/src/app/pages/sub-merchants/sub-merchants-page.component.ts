import { CommonModule } from '@angular/common';
import { Component, OnInit, HostListener, inject, signal } from '@angular/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { EasebuzzSubMerchant, EasebuzzSubMerchantRequest } from '../../core/models/api.models';
import { formatDate, formatAge } from '../../shared/formatters';

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
        <div class="toolbar-actions">
          <button class="ghost-btn" (click)="exportCsv()">📄 CSV</button>
          <a href="https://dashboard.easebuzz.in" target="_blank" rel="noopener noreferrer" class="ghost-btn easebuzz-link">🔗 Easebuzz</a>
          <button class="ghost-btn" (click)="loadSubMerchants()">Refresh</button>
          <button class="ghost-btn" (click)="openSettlementRetrieve()">📅 Settlements</button>
          <button class="ghost-btn" (click)="openOnDemandSettlement()">⚡ Settle</button>
          <button class="ghost-btn" (click)="openPayout()">💸 Payout</button>            <div class="wire-dropdown">
            <button class="ghost-btn" (click)="showWireMenu.set(!showWireMenu())" title="WIRE Platform Actions">
              🌐 WIRE ▾
            </button>
            <div *ngIf="showWireMenu()" class="wire-menu">
              <button class="ghost-btn wire-menu-btn" (click)="wireLookupByEmail(); showWireMenu.set(false)">📧 Lookup by Email</button>
              <button class="ghost-btn wire-menu-btn" (click)="wireLookupById(); showWireMenu.set(false)">🔍 Lookup by ID</button>
              <button class="ghost-btn wire-menu-btn" (click)="wireLookupByKey(); showWireMenu.set(false)">🔑 Lookup by Key</button>
              <button class="ghost-btn wire-menu-btn" (click)="wireConfigureInstaCollect(); showWireMenu.set(false)">📲 InstaCollect Webhook</button>
              <button class="ghost-btn wire-menu-btn" (click)="wireConfigurePayout(); showWireMenu.set(false)">💳 Payout Webhook</button>
            </div>
          </div>
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
              <th>KYC Age</th>
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
              <td>{{ formatKycAge(sm) }}</td>
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
                  <ng-container *ngIf="sm.status === 'DRAFT'">
                    <button class="ghost-btn" (click)="submitToEasebuzz(sm)" title="Submit to Easebuzz API">
                      <span class="chip chip-sm info">🚀 Submit</span>
                    </button>
                    <button class="ghost-btn" (click)="assignSubMerchantId(sm)" title="Manually assign Easebuzz ID">
                      <span class="chip chip-sm warn">Assign ID</span>
                    </button>
                  </ng-container>
                  <ng-container *ngIf="sm.status === 'PENDING_KYC' || sm.status === 'KYC_SUBMITTED'">
                    <button class="ghost-btn" (click)="generateKyc(sm)" title="Generate KYC portal URL">
                      <span class="chip chip-sm info">🔑 KYC</span>
                    </button>
                    <button class="ghost-btn" (click)="openVerifyOtp(sm)" title="Verify OTP">
                      <span class="chip chip-sm warn">🔢 OTP</span>
                    </button>
                    <button class="ghost-btn" (click)="updateOnEasebuzz(sm)" title="Sync changes to Easebuzz">
                      <span class="chip chip-sm">🔄 Sync</span>
                    </button>
                  </ng-container>
                  <ng-container *ngIf="sm.status === 'ACTIVE'">
                    <button class="ghost-btn" (click)="createSplitLabel(sm)" title="Create settlement split label" *ngIf="!sm.splitLabel">
                      <span class="chip chip-sm info">🏷️ Split</span>
                    </button>
                    <button class="ghost-btn" (click)="updateOnEasebuzz(sm)" title="Sync changes to Easebuzz">
                      <span class="chip chip-sm">🔄 Sync</span>
                    </button>
                  </ng-container>
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
          <div class="detail-close-row">
            <button class="ghost-btn" (click)="closeDetail()">Close</button>
          </div>
        </div>

        <div class="status-banner" *ngIf="sm.status === 'ACTIVE'" style="background: linear-gradient(135deg, rgba(29,123,95,0.1), rgba(29,123,95,0.05)); border: 1px solid rgba(29,123,95,0.2); border-radius: 12px; padding: 1rem; margin-bottom: 1rem; display: flex; align-items: center; gap: 0.75rem;">
          <span style="font-size: 1.5rem;">✅</span>
          <div>
            <strong style="color: var(--success);">Fully Active</strong>
            <p class="muted" style="margin: 0;">This sub-merchant is ready to receive settlements. KYC approved on {{ formatDateValue(sm.kycActivatedAt) }}.</p>
          </div>
        </div>

        <div class="stats-grid">
          <article class="panel stat-card">
            <h3>Status</h3>
            <strong><span [class]="getStatusChip(sm.status)">{{ formatStatusValue(sm.status) }}</span></strong>
          </article>
          <article class="panel stat-card">
            <h3>Easebuzz ID</h3>
            <strong class="stat-value">{{ sm.subMerchantId || '-' }}</strong>
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
            <strong class="stat-value">{{ sm.contactEmail || '-' }}<br *ngIf="sm.contactEmail" /><span class="muted stat-sub">{{ sm.contactPhone || '' }}</span></strong>
          </article>
          <article class="panel stat-card">
            <h3>PAN / GST</h3>
            <strong class="stat-value-sm">{{ sm.pan || '-' }}<br /><span class="muted stat-sub">{{ sm.gst || '' }}</span></strong>
          </article>
          <article class="panel stat-card span-2">
            <h3>Beneficiary</h3>
            <strong class="stat-value">{{ sm.beneficiaryName || '-' }}</strong>
            <p class="muted stat-note">
              {{ sm.bankName ? sm.bankName : '' }}{{ sm.branchName ? ' - ' + sm.branchName : '' }}
            </p>
            <p class="muted stat-note">
              {{ sm.bankAccountNo ? 'A/C: ' + sm.bankAccountNo : '' }} {{ sm.ifsc ? 'IFSC: ' + sm.ifsc : '' }}
            </p>
          </article>
          <article class="panel stat-card span-2">
            <h3>Business Address</h3>
            <p class="address-text">{{ sm.businessAddress || '-' }}</p>
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
            <div class="timeline-step" [class.completed]="sm.kycStatus === 'SUBMITTED' || sm.kycStatus === 'ACTIVATED' || sm.kycStatus === 'True'">
              <div class="step-dot"></div>
              <div class="step-info">
                <strong>KYC Submitted</strong>
                <span class="muted">{{ sm.kycSubmittedAt ? formatDateValue(sm.kycSubmittedAt) : 'Pending' }}</span>
              </div>
            </div>
            <div class="timeline-step" [class.completed]="sm.kycStatus === 'ACTIVATED' || sm.kycStatus === 'True'">
              <div class="step-dot"></div>
              <div class="step-info">
                <strong>Activated</strong>
                <span class="muted">{{ sm.kycActivatedAt ? formatDateValue(sm.kycActivatedAt) : 'Pending' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="detail-section" *ngIf="sm.kycPortalUrl || sm.subMerchantId">
          <h3>KYC Portal</h3>
          <div class="kyc-section">
            <div class="kyc-info" *ngIf="sm.kycPortalUrl">
              <p><strong>Status:</strong> <span [class]="getKycChip(sm.kycStatus)">{{ sm.kycStatus || 'PENDING' }}</span></p>
              <p><strong>Portal URL:</strong> <a [href]="sm.kycPortalUrl" target="_blank" rel="noopener noreferrer">{{ sm.kycPortalUrl }}</a></p>
              <p *ngIf="sm.kycSubmittedAt"><strong>Submitted:</strong> {{ formatDateValue(sm.kycSubmittedAt) }}</p>
              <p *ngIf="sm.kycActivatedAt"><strong>Activated:</strong> {{ formatDateValue(sm.kycActivatedAt) }}</p>
            </div>
            <div class="kyc-actions" style="margin-top:0.75rem;display:flex;gap:0.5rem;flex-wrap:wrap;">
              <button class="ghost-btn" (click)="wireGetKycProfileUrl(sm)" *ngIf="sm.subMerchantId" title="Retrieve existing KYC profile URL from WIRE">
                <span class="chip chip-sm info">🆔 KYC Profile URL</span>
              </button>
            </div>
          </div>
        </div>

        <div class="detail-section" *ngIf="sm.splitLabel">
          <h3>Split Label</h3>
          <div class="kyc-section">
            <div class="kyc-info">
              <p><strong>Label:</strong> <code>{{ sm.splitLabel }}</code></p>
              <p class="muted">Settlement split label for routing sub-merchant payouts.</p>
              <button class="ghost-btn" style="margin-top:0.5rem;" (click)="retrieveSplitStatus(sm)">🔍 Retrieve Split Status</button>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h3>Transaction Summary</h3>
          <div class="panel" *ngIf="sm.status === 'ACTIVE'; else notActiveSummary">
            <div class="stats-grid" style="margin: 0;">
              <article class="panel stat-card">
                <h3>Settlement Status</h3>
                <strong class="stat-value" style="color: var(--success);">✅ Active</strong>
                <p class="muted">Ready for split settlements</p>
              </article>
              <article class="panel stat-card">
                <h3>Split Label</h3>
                <strong class="stat-value">{{ sm.splitLabel || 'Not configured' }}</strong>
                <p class="muted">For commission routing</p>
              </article>
              <article class="panel stat-card">
                <h3>Commission Rate</h3>
                <strong class="stat-value">{{ sm.commissionRate }}%</strong>
                <p class="muted">Per transaction</p>
              </article>
            </div>
            <div class="wire-detail-actions" style="margin-top:1rem;display:flex;gap:0.5rem;flex-wrap:wrap;">
              <button class="ghost-btn" (click)="wireConfigureInstaCollect()" title="Configure InstaCollect QR webhook on WIRE">
                <span class="chip chip-sm info">📲 InstaCollect Webhook</span>
              </button>
              <button class="ghost-btn" (click)="wireConfigurePayout()" title="Configure payout webhook on WIRE">
                <span class="chip chip-sm info">💳 Payout Webhook</span>
              </button>
            </div>
          </div>
          <ng-template #notActiveSummary>
            <div class="panel loading no-margin">Transaction history will be available once the sub-merchant is active.</div>
          </ng-template>
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
              Restaurant ID *
              <input class="field-control" formControlName="restaurantId" type="number" placeholder="e.g. 4853539561918673034" />
              <span class="field-error" *ngIf="subMerchantForm.get('restaurantId')?.invalid && subMerchantForm.get('restaurantId')?.touched">Required (must be a valid restaurant ID)</span>
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
              <input class="field-control uppercase" formControlName="pan" placeholder="AAAAA1234A" maxlength="10" />
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
              <input class="field-control uppercase" formControlName="ifsc" placeholder="ABCD0123456" maxlength="11" />
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
              Bank Name *
              <input class="field-control" formControlName="bankName" placeholder="e.g. HDFC" />
              <span class="field-error" *ngIf="subMerchantForm.get('bankName')?.invalid && subMerchantForm.get('bankName')?.touched">Required</span>
            </label>
            <label class="field-label">
              Branch Name
              <input class="field-control" formControlName="branchName" placeholder="e.g. Dhanori Branch" />
            </label>
            <label class="field-label">
              Commission Rate (%) *
              <input class="field-control" formControlName="commissionRate" type="number" step="0.01" min="0" max="100" placeholder="e.g. 2.5" />
              <span class="field-error" *ngIf="subMerchantForm.get('commissionRate')?.invalid && subMerchantForm.get('commissionRate')?.touched">Required (0-100)</span>
            </label>
            <label class="field-label full-width">
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

    <div class="dialog-overlay" *ngIf="confirmDialog() as dialog" (click)="dismissConfirm()">
      <div class="dialog-panel" (click)="$event.stopPropagation()">
        <h3>{{ dialog.title }}</h3>
        <p>{{ dialog.message }}</p>
        <div class="dialog-actions">
          <button class="ghost-btn" (click)="dismissConfirm()">Cancel</button>
          <button class="primary-btn" (click)="dialog.onConfirm(); dismissConfirm()">Confirm</button>
        </div>
      </div>
    </div>

    <div class="dialog-overlay" *ngIf="promptDialog() as dialog" (click)="dismissPrompt()">
      <div class="dialog-panel" (click)="$event.stopPropagation()">
        <h3>{{ dialog.title }}</h3>
        <p>{{ dialog.message }}</p>
        <input
          class="field-control"
          [(ngModel)]="promptValue"
          [placeholder]="dialog.placeholder"
          (keydown.enter)="dialog.onConfirm(promptValue.trim()); dismissPrompt()"
        />
        <div class="dialog-actions">
          <button class="ghost-btn" (click)="dismissPrompt()">Cancel</button>
          <button class="primary-btn" (click)="dialog.onConfirm(promptValue.trim()); dismissPrompt()" [disabled]="!promptValue.trim()">Submit</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dash-mini { display:grid; grid-template-columns:repeat(auto-fit,minmax(120px,1fr)); gap:.75rem; }
    .mini-card { background:var(--panel); border:1px solid var(--line); border-radius:12px; padding:.75rem 1rem; text-align:center; box-shadow:var(--shadow-soft); }
    .mini-card strong { display:block; font-size:1.5rem; font-weight:800; }
    .mini-card span { font-size:.78rem; color:var(--muted); text-transform:uppercase; letter-spacing:.04em; }
    .mini-card.total strong { color:var(--brand); }
    .mini-card.active strong { color:var(--accent); }
    .mini-card.pending strong { color: var(--warn); }
    .mini-card.rejected strong { color:var(--danger); }
    .status-select { padding:.3rem .5rem; border-radius:8px; border:1px solid var(--line); background:var(--bg); font-size:.78rem; cursor:pointer; min-width:110px; }
    .action-cell {
      display: flex;
      gap: 0.35rem;
      align-items: center;
      flex-wrap: wrap;
    }

    .action-cell .ghost-btn {
      padding: 0.4rem 0.5rem;
      font-size: 0.8rem;
    }

    .toolbar-actions {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .easebuzz-link {
      text-decoration: none;
    }

    .chip-sm {
      padding: 0.2rem 0.5rem;
      font-size: 0.7rem;
    }

    .detail-close-row {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      flex-wrap: wrap;
    }

    .stat-value {
      font-size: 1rem;
    }

    .stat-value-sm {
      font-size: 0.95rem;
    }

    .stat-sub {
      font-size: 0.85rem;
    }

    .stat-note {
      margin: 0.25rem 0 0;
    }

    .span-2 {
      grid-column: span 2;
    }

    .address-text {
      margin: 0;
      white-space: pre-wrap;
    }

    .no-margin {
      margin: 0;
    }

    .uppercase {
      text-transform: uppercase;
    }

    .full-width {
      grid-column: 1 / -1;
    }

    .chip.info {
      background: rgba(52, 152, 219, 0.14);
      color: var(--info);
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

    .dialog-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.35);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2rem;
      z-index: 1050;
      animation: fadeIn 0.15s ease;
    }

    .dialog-panel {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 18px;
      box-shadow: var(--shadow-lg);
      padding: 1.5rem;
      max-width: 440px;
      width: 100%;
      animation: fadeSlideIn 0.2s ease;
    }

    .dialog-panel h3 {
      margin: 0 0 0.5rem;
      font-size: 1.1rem;
    }

    .dialog-panel p {
      margin: 0 0 1rem;
      color: var(--muted);
      font-size: 0.9rem;
      line-height: 1.5;
    }

    .dialog-panel .field-control {
      width: 100%;
      margin-bottom: 1rem;
    }

    .dialog-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
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

    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 0.75rem;
      flex-wrap: wrap;
    }

    .hero-meta {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    @media (max-width: 720px) {
      .toolbar { flex-direction: column; }
      .form-grid { grid-template-columns: 1fr; }
      .modal-dialog { padding: 1rem; max-width: 100%; }
      .stats-grid { grid-template-columns: 1fr !important; }
      .stats-grid .stat-card[style*="span 2"] { grid-column: 1 !important; }
    }

    .wire-dropdown { position:relative; display:inline-block; }
    .wire-menu {
      position:absolute; top:100%; left:0; z-index:100;
      background:var(--panel); border:1px solid var(--line); border-radius:10px;
      padding:.5rem; box-shadow:var(--shadow-lg); min-width:220px;
    }
    .wire-menu-btn { width:100%; justify-content:flex-start; }

    @media (max-width: 520px) {
      .action-cell { flex-direction: column; align-items: stretch; }
      .action-cell .ghost-btn { width: 100%; text-align: center; }
      .status-select { min-width: unset; width: 100%; }
      .dash-mini { grid-template-columns: repeat(2, 1fr); }
      .wire-menu { min-width:unset; width:100%; }
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
  readonly confirmDialog = signal<{ title: string; message: string; onConfirm: () => void } | null>(null);
  readonly promptDialog = signal<{ title: string; message: string; placeholder: string; onConfirm: (value: string) => void } | null>(null);
  promptValue = '';
  readonly showWireMenu = signal(false);

  searchTerm = '';
  statusFilter: string = 'ALL';
  kycFilter: string = 'ALL';
  pageSize = 10;
  currentPage = 1;

  readonly statusOptions = STATUS_OPTIONS;
  readonly kycOptions = KYC_OPTIONS;
  readonly businessTypes = BUSINESS_TYPES;

  readonly subMerchantForm = this.fb.nonNullable.group({
    restaurantId: [0, [Validators.required, Validators.min(1)]],
    businessName: ['', Validators.required],
    businessType: ['', Validators.required],
    pan: ['', [Validators.required, Validators.pattern(/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/)]],
    gst: [''],
    bankAccountNo: ['', Validators.required],
    ifsc: ['', [Validators.required, Validators.pattern(/^[A-Z]{4}0[A-Z0-9]{6}$/)]],
    beneficiaryName: ['', Validators.required],
    bankName: ['', Validators.required],
    branchName: [''],
    businessAddress: [''],
    contactEmail: ['', [Validators.required, Validators.email]],
    contactPhone: ['', [Validators.required, Validators.pattern(/^[6-9]\d{9}$/)]],
    commissionRate: [0, [Validators.required, Validators.min(0), Validators.max(100)]]
  });

  ngOnInit(): void {
    this.loadSubMerchants();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.showWireMenu()) {
      const target = event.target as HTMLElement;
      if (!target.closest('.wire-dropdown')) {
        this.showWireMenu.set(false);
      }
    }
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
      restaurantId: 0,
      businessName: '',
      businessType: '',
      pan: '',
      gst: '',
      bankAccountNo: '',
      ifsc: '',
      beneficiaryName: '',
      bankName: '',
      branchName: '',
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
      restaurantId: merchant.restaurantId ?? 0,
      businessName: merchant.businessName,
      businessType: merchant.businessType ?? '',
      pan: merchant.pan ?? '',
      gst: merchant.gst ?? '',
      bankAccountNo: merchant.bankAccountNo ?? '',
      ifsc: merchant.ifsc ?? '',
      beneficiaryName: merchant.beneficiaryName ?? '',
      bankName: merchant.bankName ?? '',
      branchName: merchant.branchName ?? '',
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
      restaurantId: Number(raw.restaurantId),
      businessName: raw.businessName,
      businessType: raw.businessType,
      pan: raw.pan,
      gst: raw.gst || undefined,
      bankAccountNo: raw.bankAccountNo,
      ifsc: raw.ifsc,
      beneficiaryName: raw.beneficiaryName,
      bankName: raw.bankName,
      branchName: raw.branchName || undefined,
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

  submitToEasebuzz(merchant: EasebuzzSubMerchant): void {
    this.showConfirm(
      'Submit to Easebuzz',
      `Submit "${merchant.businessName}" to Easebuzz for onboarding?`,
      () => {
        this.api.submitToEasebuzz(merchant.id).subscribe({
          next: () => {
            this.loadSubMerchants();
            this.showFeedback('Sub-merchant submitted to Easebuzz successfully.');
          },
          error: (err) => {
            this.showFeedback(err?.error?.error ?? 'Submission failed.', true);
          }
        });
      }
    );
  }

  assignSubMerchantId(merchant: EasebuzzSubMerchant): void {
    this.showPrompt(
      'Assign Sub-Merchant ID',
      `Enter Easebuzz Sub-Merchant ID for "${merchant.businessName}".\n\n(After creating this merchant manually in Easebuzz Dashboard)`,
      'Enter Sub-Merchant ID',
      (subMerchantId) => {
        if (!subMerchantId) return;
        this.api.assignSubMerchantId(merchant.id, subMerchantId).subscribe({
          next: () => {
            this.showFeedback('Sub-merchant ID assigned successfully. Status set to PENDING_KYC.');
            this.loadSubMerchants();
          },
          error: (err) => {
            this.showFeedback(err?.error?.error ?? 'Failed to assign sub-merchant ID.', true);
          }
        });
      }
    );
  }

  generateKyc(merchant: EasebuzzSubMerchant): void {
    this.showConfirm(
      'Generate KYC Access',
      `Generate KYC access for "${merchant.businessName}"?`,
      () => {
        this.api.generateKyc(merchant.id).subscribe({
          next: (res) => {
            this.loadSubMerchants();
            const url = res.kyc_url;
            if (url) {
              this.showFeedback(`KYC portal URL generated. Opening in new tab...`);
              setTimeout(() => window.open(url, '_blank'), 500);
            } else {
              this.showFeedback('KYC access key generated successfully.');
            }
          },
          error: () => {
            this.showFeedback('KYC generation failed.', true);
          }
        });
      }
    );
  }

  updateOnEasebuzz(merchant: EasebuzzSubMerchant): void {
    this.showConfirm(
      'Sync to Easebuzz',
      `Sync "${merchant.businessName}" changes to Easebuzz?`,
      () => {
        this.api.updateOnEasebuzz(merchant.id).subscribe({
          next: () => {
            this.loadSubMerchants();
            this.showFeedback('Sub-merchant synced to Easebuzz successfully.');
          },
          error: (err) => {
            this.showFeedback(err?.error?.error ?? 'Sync failed.', true);
          }
        });
      }
    );
  }

  retrieveSplitStatus(merchant: EasebuzzSubMerchant): void {
    this.showPrompt(
      'Retrieve Split Status',
      `Enter the merchant request ID to retrieve split configuration for "${merchant.businessName}".`,
      'Merchant Request ID',
      (merchantRequestId) => {
        if (!merchantRequestId) return;
        this.api.retrieveSplitStatus(merchant.id, merchantRequestId).subscribe({
          next: (res) => {
            const config = res.split_configuration;
            if (config && config.length) {
              this.showFeedback(`Split: ${JSON.stringify(config)}`);
            } else {
              this.showFeedback('No split configuration found.');
            }
          },
          error: (err) => {
            this.showFeedback(err?.error?.error ?? 'Split retrieve failed.', true);
          }
        });
      }
    );
  }

  createSplitLabel(merchant: EasebuzzSubMerchant): void {
    this.showConfirm(
      'Create Split Label',
      `Create settlement split label for "${merchant.businessName}"?`,
      () => {
        this.api.createSplitLabel(merchant.id).subscribe({
          next: (res) => {
            this.loadSubMerchants();
            this.showFeedback(res.msg || 'Split label created successfully.');
          },
          error: (err) => {
            this.showFeedback(err?.error?.error ?? 'Split label creation failed.', true);
          }
        });
      }
    );
  }

  openVerifyOtp(merchant: EasebuzzSubMerchant): void {
    this.showPrompt(
      'Verify OTP',
      `Enter OTP sent to ${merchant.contactPhone} for "${merchant.businessName}".`,
      '6-digit OTP',
      (otp) => {
        if (!otp) return;
        this.api.verifyOtp(merchant.id, otp).subscribe({
          next: () => {
            this.showFeedback('OTP verified successfully.');
            this.loadSubMerchants();
          },
          error: (err) => {
            this.showFeedback(err?.error?.error ?? 'OTP verification failed.', true);
          }
        });
      }
    );
  }

  openSettlementRetrieve(): void {
    const today = new Date().toISOString().split('T')[0];
    this.showPrompt(
      'Retrieve Settlements',
      'Enter date (YYYY-MM-DD) to fetch settlements from Easebuzz.',
      today,
      (date) => {
        if (!date) return;
        this.api.retrieveSettlementsByDate(date).subscribe({
          next: (res) => {
            this.showConfirm('Settlement Data', JSON.stringify(res, null, 2), () => {});
          },
          error: (err) => {
            this.showFeedback(err?.error?.error ?? 'Failed to retrieve settlements.', true);
          }
        });
      }
    );
  }

  openOnDemandSettlement(): void {
    this.showPrompt(
      'On-Demand Settlement',
      'Enter amount to settle immediately to your bank account.',
      'Amount (e.g. 1000.00)',
      (amount) => {
        if (!amount) return;
        this.api.onDemandSettlement(amount).subscribe({
          next: (res) => {
            this.showFeedback(`Settlement initiated: ${res.msg || 'Success'}`);
          },
          error: (err) => {
            this.showFeedback(err?.error?.error ?? 'Settlement initiation failed.', true);
          }
        });
      }
    );
  }

  openPayout(): void {
    this.showPrompt(
      'Direct Payout',
      'Enter amount for manual payout.',
      'Amount (e.g. 500.00)',
      (amount) => {
        if (!amount) return;
        // Simple payout test with fixed beneficiary for now, or could show another prompt
        const beneficiary = {
          beneficiary_name: 'Test Beneficiary',
          beneficiary_account_number: '1234567890',
          beneficiary_ifsc: 'HDFC0000123'
        };
        this.api.initiatePayout(amount, beneficiary).subscribe({
          next: (res) => {
            this.showFeedback(`Payout initiated: ${res.msg || 'Success'}`);
          },
          error: (err) => {
            this.showFeedback(err?.error?.error ?? 'Payout failed.', true);
          }
        });
      }
    );
  }

  // ============================================================
  // WIRE Platform Action Handlers
  // ============================================================

  wireLookupByEmail(): void {
    this.showPrompt(
      'WIRE Lookup by Email',
      'Enter the sub-merchant email to look up on the Easebuzz WIRE platform.',
      'email@example.com',
      (email) => {
        if (!email) return;
        this.api.wireLookupByEmail(email).subscribe({
          next: (res) => this.showConfirm('WIRE Lookup Result', JSON.stringify(res, null, 2), () => {}),
          error: (err) => this.showFeedback(err?.error?.error ?? 'Lookup failed.', true)
        });
      }
    );
  }

  wireLookupById(): void {
    this.showPrompt(
      'WIRE Lookup by ID',
      'Enter the Easebuzz sub-merchant ID (e.g., S360DILA).',
      'Sub-Merchant ID',
      (subMerchantId) => {
        if (!subMerchantId) return;
        this.api.wireLookupById(subMerchantId).subscribe({
          next: (res) => this.showConfirm('WIRE Lookup Result', JSON.stringify(res, null, 2), () => {}),
          error: (err) => this.showFeedback(err?.error?.error ?? 'Lookup failed.', true)
        });
      }
    );
  }

  wireLookupByKey(): void {
    this.showPrompt(
      'WIRE Lookup by Key',
      'Enter the sub-merchant key from Easebuzz.',
      'Sub-Merchant Key',
      (key) => {
        if (!key) return;
        this.api.wireLookupByKey(key).subscribe({
          next: (res) => this.showConfirm('WIRE Lookup Result', JSON.stringify(res, null, 2), () => {}),
          error: (err) => this.showFeedback(err?.error?.error ?? 'Lookup failed.', true)
        });
      }
    );
  }

  wireGetKycProfileUrl(merchant: EasebuzzSubMerchant): void {
    this.api.wireGetKycProfileUrl(merchant.id).subscribe({
      next: (res) => {
        const url = res?.data?.kyc_url;
        if (url) {
          this.showFeedback(`KYC profile URL retrieved. Opening...`);
          setTimeout(() => window.open(url, '_blank'), 500);
        } else {
          this.showFeedback('KYC profile URL: ' + JSON.stringify(res), false);
        }
      },
      error: (err) => this.showFeedback(err?.error?.error ?? 'KYC profile URL retrieval failed.', true)
    });
  }

  wireConfigureInstaCollect(): void {
    this.showPrompt(
      'InstaCollect QR Webhook',
      'Enter: subMerchantId | webhookUrl | eventType\n\nSupported event types: ORDER_STATUS_UPDATE, TRANSACTION_CREDIT, INSTA_COLLECT_VIRTUAL_ACCOUNT_KYC_APPROVAL\n\nExample: S360DILA | https://api.example.com/webhook | ORDER_STATUS_UPDATE',
      'subMerchantId | url | eventType',
      (input) => {
        if (!input) return;
        const parts = input.split('|').map(p => p.trim());
        if (parts.length < 3 || parts.some(p => !p.trim())) {
          this.showFeedback('Please provide all 3 non-empty values separated by |', true);
          return;
        }
        this.api.wireConfigureInstaCollectWebhook({
          subMerchantId: parts[0],
          url: parts[1],
          eventType: parts[2],
          intervalUnit: 'hours',
          intervalValue: 24,
          maxAttempts: 3
        }).subscribe({
          next: (res) => this.showFeedback('InstaCollect webhook configured: ' + JSON.stringify(res)),
          error: (err) => this.showFeedback(err?.error?.error ?? 'Webhook config failed.', true)
        });
      }
    );
  }

  wireConfigurePayout(): void {
    this.showPrompt(
      'Payout Webhook',
      'Enter: subMerchantId | webhookUrl | eventType\n\nSupported event types: TRANSFER_INITIATED, TRANSFER_STATUS_UPDATE, LOW_BALANCE_ALERT\n\nExample: S360DILA | https://api.example.com/payout-webhook | TRANSFER_STATUS_UPDATE',
      'subMerchantId | url | eventType',
      (input) => {
        if (!input) return;
        const parts = input.split('|').map(p => p.trim());
        if (parts.length < 3 || parts.some(p => !p.trim())) {
          this.showFeedback('Please provide all 3 non-empty values separated by |', true);
          return;
        }
        this.api.wireConfigurePayoutWebhook({
          subMerchantId: parts[0],
          url: parts[1],
          eventType: parts[2],
          intervalUnit: 'minutes',
          intervalValue: 5,
          maxAttempts: 3
        }).subscribe({
          next: (res) => this.showFeedback('Payout webhook configured: ' + JSON.stringify(res)),
          error: (err) => this.showFeedback(err?.error?.error ?? 'Webhook config failed.', true)
        });
      }
    );
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
    this.showConfirm(
      'Change Status',
      `Change "${merchant.businessName}" status to ${newStatus}?`,
      () => {
        this.api.updateSubMerchantStatus(merchant.id, newStatus).subscribe({
          next: () => {
            this.showFeedback(`Status updated to ${newStatus}`);
            this.loadSubMerchants();
          },
          error: () => this.showFeedback('Status update failed.', true)
        });
      }
    );
    (event.target as HTMLSelectElement).value = '';
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

  formatKycAge(sm: EasebuzzSubMerchant): string {
    if (sm.kycStatus === 'ACTIVATED' || sm.kycStatus === 'ACTIVE') {
      return 'Done';
    }
    const ts = sm.kycSubmittedAt ?? sm.createdAt;
    return formatAge(ts);
  }

  showConfirm(title: string, message: string, onConfirm: () => void): void {
    this.confirmDialog.set({ title, message, onConfirm });
  }

  dismissConfirm(): void {
    this.confirmDialog.set(null);
  }

  showPrompt(title: string, message: string, placeholder: string, onConfirm: (value: string) => void): void {
    this.promptValue = '';
    this.promptDialog.set({ title, message, placeholder, onConfirm });
  }

  dismissPrompt(): void {
    this.promptDialog.set(null);
  }
}
