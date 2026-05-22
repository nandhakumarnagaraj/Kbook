import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal, DestroyRef, ViewChild, AfterViewInit, computed } from '@angular/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormControl, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatDividerModule } from '@angular/material/divider';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBusinessListItem, EasebuzzSubMerchant, EasebuzzSubMerchantRequest } from '../../core/models/api.models';
import { formatDate, formatAge } from '../../shared/formatters';

const STATUS_OPTIONS = ['ALL', 'DRAFT', 'PENDING_KYC', 'KYC_SUBMITTED', 'ACTIVE', 'SUSPENDED', 'REJECTED', 'FAILED'] as const;
const KYC_OPTIONS = ['ALL', 'PENDING', 'SUBMITTED', 'ACTIVATED', 'FAILED'] as const;
const BUSINESS_TYPES = ['SOLE_PROPRIETORSHIP', 'PARTNERSHIP', 'PRIVATE_LIMITED', 'PUBLIC_LIMITED', 'OTHERS'] as const;

@Component({
  selector: 'app-sub-merchants-page',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule, 
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatAutocompleteModule,
    MatDividerModule,
    MatDialogModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    MatProgressBarModule,
    MatChipsModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">Sub-Merchants</h1>
          <p class="page-subtitle">Manage Easebuzz settlement sub-merchant onboarding and KYC lifecycle.</p>
        </div>
        <div class="header-actions">
          <button mat-button [matMenuTriggerFor]="moreMenu">
            <mat-icon>more_vert</mat-icon>
            Advanced Actions
          </button>
          <mat-menu #moreMenu="matMenu">
            <button mat-menu-item (click)="openSettlementRetrieve()">
              <mat-icon>calendar_today</mat-icon>
              <span>Retrieve Settlements</span>
            </button>
            <button mat-menu-item (click)="openOnDemandSettlement()">
              <mat-icon>bolt</mat-icon>
              <span>On-Demand Settle</span>
            </button>
            <button mat-menu-item (click)="openPayout()">
              <mat-icon>payments</mat-icon>
              <span>Initiate Payout</span>
            </button>
            <mat-divider></mat-divider>
            <button mat-menu-item (click)="exportCsv()">
              <mat-icon>download</mat-icon>
              <span>Export CSV</span>
            </button>
          </mat-menu>

          <a mat-button href="https://dashboard.easebuzz.in" target="_blank" rel="noopener noreferrer">
            <mat-icon>open_in_new</mat-icon>
            Easebuzz
          </a>
          <button mat-flat-button color="primary" (click)="openCreate()">
            <mat-icon>add</mat-icon>
            New Sub-Merchant
          </button>
        </div>
      </div>

      <div class="stats-grid">
        <mat-card class="stat-card total">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon">people</mat-icon>
            <mat-card-title>{{ stats().total }}</mat-card-title>
            <mat-card-subtitle>Total Sub-Merchants</mat-card-subtitle>
          </mat-card-header>
        </mat-card>

        <mat-card class="stat-card active">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon">check_circle</mat-icon>
            <mat-card-title>{{ stats().active }}</mat-card-title>
            <mat-card-subtitle>Active</mat-card-subtitle>
          </mat-card-header>
        </mat-card>

        <mat-card class="stat-card pending">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon">pending_actions</mat-icon>
            <mat-card-title>{{ stats().pending }}</mat-card-title>
            <mat-card-subtitle>Pending KYC</mat-card-subtitle>
          </mat-card-header>
        </mat-card>

        <mat-card class="stat-card rejected">
          <mat-card-header>
            <mat-icon mat-card-avatar class="stat-icon">error</mat-icon>
            <mat-card-title>{{ stats().rejected }}</mat-card-title>
            <mat-card-subtitle>Rejected / Failed</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
      </div>

      <mat-card class="filter-card">
        <mat-card-content class="filter-row">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search Sub-Merchants</mat-label>
            <mat-icon matPrefix>search</mat-icon>
            <input matInput (keyup)="applyFilter($event)" placeholder="Search by name, email, phone..." #input>
          </mat-form-field>
          
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Status</mat-label>
            <mat-select [(ngModel)]="statusFilter" (selectionChange)="applyFilters()">
              <mat-option *ngFor="let s of statusOptions" [value]="s">
                {{ s === 'ALL' ? 'All Statuses' : formatStatusValue(s) }}
              </mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>KYC</mat-label>
            <mat-select [(ngModel)]="kycFilter" (selectionChange)="applyFilters()">
              <mat-option *ngFor="let k of kycOptions" [value]="k">
                {{ k === 'ALL' ? 'All KYC' : k }}
              </mat-option>
            </mat-select>
          </mat-form-field>

          <div class="spacer"></div>
          
          <button mat-icon-button (click)="loadSubMerchants()" matTooltip="Refresh Data">
            <mat-icon>refresh</mat-icon>
          </button>
        </mat-card-content>
      </mat-card>

      <div class="content-layout">
        <div class="table-container mat-elevation-z2">
          <div class="loading-shade" *ngIf="!loaded()">
            <mat-spinner diameter="40" *ngIf="!loadError()"></mat-spinner>
            <div class="error-msg" *ngIf="loadError()">{{ loadError() }}</div>
          </div>

          <table mat-table [dataSource]="dataSource" matSort>
            <ng-container matColumnDef="business">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Business </th>
              <td mat-cell *matCellDef="let sm"> 
                <div class="stacked-meta">
                  <span class="main-text">{{ sm.businessName }}</span>
                  <span class="sub-text">#{{ sm.restaurantId }}</span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Status </th>
              <td mat-cell *matCellDef="let sm">
                <span class="status-chip" [class]="getStatusChipClass(sm.status)">
                  {{ formatStatusValue(sm.status) }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="kyc">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> KYC </th>
              <td mat-cell *matCellDef="let sm">
                <span class="status-chip" [class]="getKycChipClass(sm.kycStatus)">
                  {{ sm.kycStatus || 'PENDING' }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="kycAge">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> KYC Age </th>
              <td mat-cell *matCellDef="let sm"> {{ formatKycAge(sm) }} </td>
            </ng-container>

            <ng-container matColumnDef="contact">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Contact </th>
              <td mat-cell *matCellDef="let sm">
                <div class="stacked-meta">
                  <span>{{ sm.contactEmail || '-' }}</span>
                  <span class="sub-text">{{ sm.contactPhone || '' }}</span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="commission">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Com. </th>
              <td mat-cell *matCellDef="let sm"> {{ sm.commissionRate != null ? sm.commissionRate + '%' : '-' }} </td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef> Actions </th>
              <td mat-cell *matCellDef="let sm">
                <button mat-icon-button [matMenuTriggerFor]="actionMenu" (click)="$event.stopPropagation()">
                  <mat-icon>more_vert</mat-icon>
                </button>
                <mat-menu #actionMenu="matMenu">
                  <button mat-menu-item (click)="viewDetail(sm)">
                    <mat-icon>visibility</mat-icon>
                    <span>View Details</span>
                  </button>
                  <button mat-menu-item (click)="openEdit(sm)">
                    <mat-icon>edit</mat-icon>
                    <span>Edit</span>
                  </button>
                  <mat-divider></mat-divider>
                  
                  <ng-container *ngIf="sm.status === 'DRAFT' || sm.status === 'FAILED'">
                    <button mat-menu-item (click)="submitToEasebuzz(sm)">
                      <mat-icon color="primary">rocket_launch</mat-icon>
                      <span>Submit to Easebuzz</span>
                    </button>
                    <button mat-menu-item (click)="assignSubMerchantId(sm)">
                      <mat-icon>fingerprint</mat-icon>
                      <span>Assign Easebuzz ID</span>
                    </button>
                  </ng-container>

                  <ng-container *ngIf="sm.status === 'PENDING_KYC' || sm.status === 'KYC_SUBMITTED'">
                    <button mat-menu-item (click)="generateKyc(sm)">
                      <mat-icon color="primary">vpn_key</mat-icon>
                      <span>Generate KYC URL</span>
                    </button>
                    <button mat-menu-item (click)="openVerifyOtp(sm)">
                      <mat-icon>sms</mat-icon>
                      <span>Verify OTP</span>
                    </button>
                    <button mat-menu-item (click)="updateOnEasebuzz(sm)">
                      <mat-icon>sync</mat-icon>
                      <span>Sync to Easebuzz</span>
                    </button>
                  </ng-container>

                  <ng-container *ngIf="sm.status === 'ACTIVE'">
                     <button mat-menu-item (click)="createSplitLabel(sm)" *ngIf="!sm.splitLabel">
                      <mat-icon>label</mat-icon>
                      <span>Create Split Label</span>
                    </button>
                    <button mat-menu-item (click)="updateOnEasebuzz(sm)">
                      <mat-icon>sync</mat-icon>
                      <span>Sync to Easebuzz</span>
                    </button>
                  </ng-container>

                  <mat-divider *ngIf="sm.status !== 'DRAFT'"></mat-divider>
                  <button mat-menu-item *ngIf="sm.status === 'PENDING_KYC' || sm.status === 'KYC_SUBMITTED'" (click)="quickStatusAction(sm, 'ACTIVE')">
                     <mat-icon color="primary">check_circle</mat-icon>
                     <span>Mark Active</span>
                  </button>
                  <button mat-menu-item *ngIf="sm.status === 'ACTIVE'" (click)="quickStatusAction(sm, 'SUSPENDED')">
                     <mat-icon color="warn">pause_circle</mat-icon>
                     <span>Suspend</span>
                  </button>
                  <button mat-menu-item *ngIf="sm.status === 'PENDING_KYC' || sm.status === 'KYC_SUBMITTED'" (click)="quickStatusAction(sm, 'REJECTED')">
                     <mat-icon color="warn">cancel</mat-icon>
                     <span>Reject</span>
                  </button>
                </mat-menu>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;" 
                class="clickable-row"
                [class.selected-row]="selectedSubMerchant()?.id === row.id"
                (click)="viewDetail(row)"></tr>
          </table>

          <mat-paginator [pageSizeOptions]="[10, 25, 50, 100]" aria-label="Select page of sub-merchants"></mat-paginator>
        </div>

        <!-- Detail Panel -->
        <div class="detail-panel" *ngIf="selectedSubMerchant() as sm">
          <mat-card class="detail-card">
            <mat-card-header>
              <mat-icon mat-card-avatar color="primary">person</mat-icon>
              <mat-card-title>{{ sm.businessName }}</mat-card-title>
              <mat-card-subtitle>Sub-Merchant ID: {{ sm.id }} | Restaurant: #{{ sm.restaurantId }}</mat-card-subtitle>
              <span class="spacer"></span>
              <button mat-icon-button (click)="closeDetail()">
                <mat-icon>close</mat-icon>
              </button>
            </mat-card-header>
            
            <mat-divider></mat-divider>
            
            <mat-card-content>
              <div class="status-banner" *ngIf="sm.status === 'ACTIVE'">
                <mat-icon>check_circle</mat-icon>
                <div class="banner-text">
                  <strong>Fully Active</strong>
                  <span>Ready for settlements. KYC approved on {{ formatDateValue(sm.kycActivatedAt) }}.</span>
                </div>
              </div>

              <div class="detail-grid">
                 <div class="detail-item">
                    <span class="label">Status</span>
                    <span class="status-chip" [class]="getStatusChipClass(sm.status)">{{ formatStatusValue(sm.status) }}</span>
                 </div>
                 <div class="detail-item">
                    <span class="label">Easebuzz ID</span>
                    <span class="value">{{ sm.subMerchantId || '-' }}</span>
                 </div>
                 <div class="detail-item">
                    <span class="label">Commission</span>
                    <span class="value">{{ sm.commissionRate != null ? sm.commissionRate + '%' : '-' }}</span>
                 </div>
                 <div class="detail-item">
                    <span class="label">Business Type</span>
                    <span class="value">{{ sm.businessType || '-' }}</span>
                 </div>
                 <div class="detail-item">
                    <span class="label">Contact Email</span>
                    <span class="value">{{ sm.contactEmail || '-' }}</span>
                 </div>
                 <div class="detail-item">
                    <span class="label">Contact Phone</span>
                    <span class="value">{{ sm.contactPhone || '-' }}</span>
                 </div>
                 <div class="detail-item">
                    <span class="label">PAN</span>
                    <span class="value">{{ sm.pan || '-' }}</span>
                 </div>
                 <div class="detail-item">
                    <span class="label">GST</span>
                    <span class="value">{{ sm.gst || '-' }}</span>
                 </div>
                 <div class="detail-item span-2">
                    <span class="label">Beneficiary</span>
                    <span class="value">{{ sm.beneficiaryName || '-' }}</span>
                    <div class="sub-text">
                      {{ sm.bankName }}{{ sm.branchName ? ' - ' + sm.branchName : '' }}<br/>
                      {{ sm.bankAccountNo ? 'A/C: ' + sm.bankAccountNo : '' }} {{ sm.ifsc ? ' | IFSC: ' + sm.ifsc : '' }}
                    </div>
                 </div>
                 <div class="detail-item span-2">
                    <span class="label">Address</span>
                    <span class="value address">{{ sm.businessAddress || '-' }}</span>
                 </div>
              </div>

              <mat-divider></mat-divider>

              <div class="section-title">KYC & Settlement</div>
              <div class="kyc-row">
                 <div class="kyc-step" [class.done]="sm.status !== 'DRAFT'">
                    <mat-icon>{{ sm.status !== 'DRAFT' ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon>
                    <span>Registered</span>
                 </div>
                 <div class="kyc-step" [class.done]="!!sm.kycPortalUrl">
                    <mat-icon>{{ sm.kycPortalUrl ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon>
                    <span>KYC Link</span>
                 </div>
                 <div class="kyc-step" [class.done]="sm.kycStatus === 'SUBMITTED' || sm.kycStatus === 'ACTIVATED' || sm.kycStatus === 'True'">
                    <mat-icon>{{ (sm.kycStatus === 'SUBMITTED' || sm.kycStatus === 'ACTIVATED' || sm.kycStatus === 'True') ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon>
                    <span>Submitted</span>
                 </div>
                 <div class="kyc-step" [class.done]="sm.kycStatus === 'ACTIVATED' || sm.kycStatus === 'True'">
                    <mat-icon>{{ (sm.kycStatus === 'ACTIVATED' || sm.kycStatus === 'True') ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon>
                    <span>Activated</span>
                 </div>
              </div>

              <div class="info-box" *ngIf="sm.kycPortalUrl">
                 <strong>KYC Portal URL:</strong>
                 <a [href]="sm.kycPortalUrl" target="_blank" class="link">{{ sm.kycPortalUrl }}</a>
              </div>

               <div class="info-box" *ngIf="sm.splitLabel">
                 <strong>Split Label:</strong> <code>{{ sm.splitLabel }}</code>
                 <button mat-button color="primary" (click)="retrieveSplitStatus(sm)">Check Split Status</button>
              </div>
            </mat-card-content>
            
            <mat-card-actions align="end">
              <button mat-button (click)="closeDetail()">Dismiss</button>
              <button mat-raised-button color="primary" (click)="openEdit(sm)">Edit Sub-Merchant</button>
            </mat-card-actions>
          </mat-card>
        </div>
      </div>
    </div>

    <!-- Create/Edit Dialog Template -->
    <ng-template #formDialog>
      <h2 mat-dialog-title>{{ editingSubMerchant() ? 'Edit' : 'Create' }} Sub-Merchant</h2>
      <mat-dialog-content>
        <form [formGroup]="subMerchantForm" class="dialog-form">
          <div class="form-grid">
            <div class="fetch-row">
              <mat-form-field appearance="outline" class="flex-grow">
                <mat-label>Select Restaurant / Shop</mat-label>
                <input matInput
                  [formControl]="businessSearchCtrl"
                  [matAutocomplete]="bizAuto"
                  placeholder="Type to search by name or ID…">
                <mat-autocomplete #bizAuto="matAutocomplete"
                  [displayWith]="displayBusiness"
                  (optionSelected)="onBusinessSelected($event)">
                  <mat-option *ngFor="let b of filteredBusinesses()" [value]="b">
                    <span style="font-weight:700">{{ b.shopName || 'Unnamed' }}</span>
                    <span style="color:var(--muted);font-size:0.8rem;margin-left:8px">#{{ b.restaurantId }}</span>
                  </mat-option>
                </mat-autocomplete>
                <mat-hint>Start typing shop name or paste the restaurant ID</mat-hint>
              </mat-form-field>
              <button mat-stroked-button color="primary" type="button"
                (click)="fetchShopData()"
                [disabled]="fetchingShop()"
                class="fetch-btn">
                <mat-icon *ngIf="!fetchingShop()">cloud_download</mat-icon>
                <mat-spinner *ngIf="fetchingShop()" diameter="18"></mat-spinner>
                {{ fetchingShop() ? 'Fetching...' : 'Fetch' }}
              </button>
            </div>
            <div class="fetch-hint" [class]="fetchNotice()!.type" *ngIf="fetchNotice()">
              <mat-icon>{{ fetchNotice()!.type === 'success' ? 'check_circle' : 'error' }}</mat-icon>
              {{ fetchNotice()!.msg }}
            </div>

            <mat-form-field appearance="outline">
              <mat-label>Business Name</mat-label>
              <input matInput formControlName="businessName">
              <mat-error>Required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Business Type</mat-label>
              <mat-select formControlName="businessType">
                <mat-option *ngFor="let bt of businessTypes" [value]="bt">{{ bt }}</mat-option>
              </mat-select>
              <mat-error>Required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>PAN</mat-label>
              <input matInput formControlName="pan" placeholder="AAAAA1234A" class="uppercase">
              <mat-error>Invalid PAN format</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>GST (Optional)</mat-label>
              <input matInput formControlName="gst">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Commission Rate (%)</mat-label>
              <input matInput formControlName="commissionRate" type="number" step="0.01">
              <mat-error>0-100 required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Bank Name</mat-label>
              <input matInput formControlName="bankName">
              <mat-error>Required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Branch Name</mat-label>
              <input matInput formControlName="branchName">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Bank Account No</mat-label>
              <input matInput formControlName="bankAccountNo">
              <mat-error>Required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>IFSC Code</mat-label>
              <input matInput formControlName="ifsc" placeholder="ABCD0123456" class="uppercase">
              <mat-error>Invalid IFSC format</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Beneficiary Name</mat-label>
              <input matInput formControlName="beneficiaryName">
              <mat-error>Required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Contact Email</mat-label>
              <input matInput formControlName="contactEmail" type="email">
              <mat-error>Valid email required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Contact Phone</mat-label>
              <input matInput formControlName="contactPhone" placeholder="9876543210">
              <mat-error>10 digits starting with 6-9</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="span-2">
              <mat-label>Business Address</mat-label>
              <textarea matInput formControlName="businessAddress" rows="3"></textarea>
            </mat-form-field>
          </div>
        </form>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" (click)="submitForm()" [disabled]="formSaving()">
          {{ formSaving() ? 'Saving...' : (editingSubMerchant() ? 'Update' : 'Create') }}
        </button>
      </mat-dialog-actions>
    </ng-template>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 32px; }
    .page-title { margin: 0; font-size: 2rem; font-weight: 800; color: var(--ink); letter-spacing: -0.5px; }
    .page-subtitle { margin: 4px 0 0; color: var(--muted); font-size: 0.95rem; }
    .header-actions { display: flex; gap: 12px; align-items: center; }

    /* Premium Stats Grid & Cards */
    .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 24px; margin-bottom: 32px; }
    .stat-card { 
      position: relative;
      border: 1px solid var(--line) !important; 
      border-radius: var(--radius-xl) !important;
      background: var(--panel) !important;
      backdrop-filter: blur(12px);
      box-shadow: var(--shadow-sm) !important; 
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1) !important;
      overflow: hidden;
    }
    .stat-card:hover {
      transform: translateY(-6px);
      box-shadow: 0 12px 28px -8px var(--glow-color) !important;
      border-color: var(--border-hover) !important;
    }
    .stat-card mat-card-header { padding: 16px; }
    .stat-icon { 
      width: 48px; 
      height: 48px; 
      line-height: 48px; 
      text-align: center; 
      border-radius: var(--radius-lg); 
      font-size: 24px; 
      transition: all 0.3s ease;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .stat-card:hover .stat-icon { transform: scale(1.1) rotate(8deg); }
    .stat-card mat-card-title { font-size: 1.8rem !important; font-weight: 800 !important; color: var(--ink) !important; margin: 0 !important; }
    .stat-card mat-card-subtitle { font-size: 0.85rem !important; font-weight: 600 !important; color: var(--muted) !important; margin-top: 4px !important; }

    .stat-card.total { 
      --glow-color: rgba(2, 132, 199, 0.15); 
      --border-hover: rgba(2, 132, 199, 0.3);
      background: linear-gradient(135deg, rgba(2, 132, 199, 0.03) 0%, var(--panel) 100%) !important;
    }
    .stat-card.total .stat-icon { background: rgba(2, 132, 199, 0.1); color: #0284c7; }
    
    .stat-card.active { 
      --glow-color: rgba(22, 163, 74, 0.15); 
      --border-hover: rgba(22, 163, 74, 0.3);
      background: linear-gradient(135deg, rgba(22, 163, 74, 0.03) 0%, var(--panel) 100%) !important;
    }
    .stat-card.active .stat-icon { background: rgba(22, 163, 74, 0.1); color: #16a34a; }

    .stat-card.pending { 
      --glow-color: rgba(217, 119, 6, 0.15); 
      --border-hover: rgba(217, 119, 6, 0.3);
      background: linear-gradient(135deg, rgba(217, 119, 6, 0.03) 0%, var(--panel) 100%) !important;
    }
    .stat-card.pending .stat-icon { background: rgba(217, 119, 6, 0.1); color: #d97706; }

    .stat-card.rejected { 
      --glow-color: rgba(239, 68, 68, 0.15); 
      --border-hover: rgba(239, 68, 68, 0.3);
      background: linear-gradient(135deg, rgba(239, 68, 68, 0.03) 0%, var(--panel) 100%) !important;
    }
    .stat-card.rejected .stat-icon { background: rgba(239, 68, 68, 0.1); color: #dc2626; }

    /* Filters Layout */
    .filter-card { 
      margin-bottom: 32px; 
      border: 1px solid var(--line) !important; 
      border-radius: var(--radius-xl) !important;
      background: var(--panel) !important;
      box-shadow: var(--shadow-sm) !important;
      overflow: hidden;
    }
    .filter-row { display: flex; align-items: center; gap: 16px; padding: 16px 20px !important; }
    .search-field { flex: 1.5; max-width: 450px; }
    .filter-field { width: 180px; }
    .spacer { flex: 1; }
    ::ng-deep .filter-card .mat-mdc-form-field-subscript-wrapper { display: none !important; }
    ::ng-deep .filter-card .mat-mdc-text-field-wrapper { 
      height: 44px !important; 
      padding: 0 16px !important; 
      border-radius: var(--radius-lg) !important; 
      background: var(--bg) !important;
    }
    ::ng-deep .filter-card .mat-mdc-form-field-flex { align-items: center !important; }
    ::ng-deep .filter-card .mat-mdc-form-field-infix { padding-top: 10px !important; padding-bottom: 10px !important; }

    /* Split Content Layout */
    .content-layout { display: flex; gap: 28px; align-items: flex-start; margin-top: 8px; }
    
    .table-container { 
      flex: 1; 
      min-width: 0;
      position: relative; 
      background: var(--panel) !important; 
      border-radius: var(--radius-xl) !important; 
      border: 1px solid var(--line) !important; 
      box-shadow: var(--shadow-md) !important;
      overflow: hidden; 
    }
    .loading-shade { 
      position: absolute; 
      top: 0; 
      left: 0; 
      bottom: 56px; 
      right: 0; 
      background: rgba(255, 255, 255, 0.7); 
      z-index: 1; 
      display: flex; 
      align-items: center; 
      justify-content: center; 
    }
    .dark-theme .loading-shade { background: rgba(15, 23, 42, 0.7); }
    table { width: 100%; background: transparent !important; }
    
    th.mat-mdc-header-cell {
      background: var(--bg) !important;
      color: var(--ink-secondary) !important;
      font-size: 0.75rem !important;
      font-weight: 700 !important;
      text-transform: uppercase !important;
      letter-spacing: 1px !important;
      padding: 16px !important;
      border-bottom: 1px solid var(--line) !important;
    }
    td.mat-mdc-cell {
      padding: 16px !important;
      border-bottom: 1px solid var(--line) !important;
      font-size: 0.9rem !important;
      color: var(--ink) !important;
    }
    .clickable-row { cursor: pointer; transition: all 0.2s ease; }
    .clickable-row:hover { background: var(--panel-hover) !important; }
    .selected-row { background: var(--brand-soft) !important; border-left: 4px solid var(--brand) !important; }
    .selected-row td { font-weight: 500 !important; }

    .stacked-meta { display: flex; flex-direction: column; gap: 3px; }
    .main-text { font-weight: 700; color: var(--ink); }
    .sub-text { font-size: 0.75rem; color: var(--muted); }

    /* Chips */
    .status-chip { 
      display: inline-block;
      padding: 5px 12px; 
      border-radius: 999px; 
      font-size: 0.7rem; 
      font-weight: 800; 
      text-transform: uppercase; 
      letter-spacing: 0.5px; 
      text-align: center;
      box-shadow: 0 1px 2px rgba(0,0,0,0.05);
    }
    .status-chip.success { background: rgba(22, 163, 74, 0.08); color: #16a34a; border: 1px solid rgba(22, 163, 74, 0.2); }
    .status-chip.warn { background: rgba(217, 119, 6, 0.08); color: #d97706; border: 1px solid rgba(217, 119, 6, 0.2); }
    .status-chip.info { background: rgba(2, 132, 199, 0.08); color: #0284c7; border: 1px solid rgba(2, 132, 199, 0.2); }
    .status-chip.danger { background: rgba(239, 68, 68, 0.08); color: #dc2626; border: 1px solid rgba(239, 68, 68, 0.2); }

    /* Detail Drawer Panel */
    .detail-panel { width: 460px; flex-shrink: 0; animation: slideIn 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
    @keyframes slideIn {
      from { transform: translateX(30px); opacity: 0; }
      to { transform: translateX(0); opacity: 1; }
    }
    .detail-card {
      border: 1px solid var(--line) !important;
      border-radius: var(--radius-xl) !important;
      background: var(--panel) !important;
      box-shadow: var(--shadow-lg) !important;
      overflow: hidden;
    }
    .detail-card mat-card-header { padding: 20px 24px !important; align-items: center; }
    .detail-card mat-card-title { font-size: 1.2rem !important; font-weight: 800 !important; color: var(--ink) !important; margin: 0 !important; }
    .detail-card mat-card-subtitle { font-size: 0.8rem !important; color: var(--muted) !important; margin-top: 4px !important; }
    .detail-card mat-card-content { padding: 0 24px 24px !important; }
    .detail-card mat-card-actions { padding: 16px 24px 20px !important; border-top: 1px solid var(--line); }

    .status-banner { 
      display: flex; 
      align-items: center; 
      gap: 16px; 
      padding: 16px; 
      background: rgba(34, 197, 94, 0.06); 
      border: 1px solid rgba(34, 197, 94, 0.2); 
      border-radius: var(--radius-lg); 
      margin: 16px 0; 
    }
    .status-banner mat-icon { color: #16a34a; font-size: 32px; width: 32px; height: 32px; }
    .banner-text { display: flex; flex-direction: column; gap: 2px; }
    .banner-text strong { font-weight: 700; color: #16a34a; font-size: 0.95rem; }
    .banner-text span { font-size: 0.8rem; color: var(--ink-secondary); }

    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 16px 0; }
    .detail-item { 
      display: flex; 
      flex-direction: column; 
      gap: 4px; 
      padding: 12px 14px; 
      border-radius: var(--radius-md); 
      background: var(--bg); 
      border: 1px solid var(--line); 
      transition: all 0.2s ease;
    }
    .detail-item:hover { border-color: var(--brand-soft); background: var(--panel-hover); }
    .detail-item .label { font-size: 0.65rem; text-transform: uppercase; letter-spacing: 0.8px; color: var(--muted); font-weight: 700; }
    .detail-item .value { font-size: 0.9rem; font-weight: 700; color: var(--ink); word-break: break-all; }
    .detail-item .sub-text { font-size: 0.75rem; color: var(--muted); margin-top: 4px; line-height: 1.4; }
    .span-2 { grid-column: span 2; }

    /* KYC Visual Pipeline */
    .section-title { font-weight: 800; margin: 28px 0 16px; font-size: 0.8rem; text-transform: uppercase; color: var(--ink-secondary); letter-spacing: 1.2px; }
    .kyc-row { display: flex; justify-content: space-between; padding: 16px 0; position: relative; }
    .kyc-row::before {
      content: '';
      position: absolute;
      top: 28px;
      left: 10%;
      right: 10%;
      height: 2px;
      background: var(--line);
      z-index: 1;
    }
    .kyc-step { 
      display: flex; 
      flex-direction: column; 
      align-items: center; 
      gap: 8px; 
      color: var(--muted); 
      font-size: 0.7rem; 
      font-weight: 700; 
      position: relative;
      z-index: 2;
      background: var(--panel);
      padding: 0 12px;
    }
    .kyc-step mat-icon { 
      font-size: 24px; 
      width: 24px;
      height: 24px;
      color: var(--muted);
      background: var(--panel);
      border-radius: 50%;
    }
    .kyc-step.done { color: #16a34a; }
    .kyc-step.done mat-icon { color: #16a34a; }

    .info-box { 
      background: var(--bg); 
      border: 1px solid var(--line);
      padding: 14px 16px; 
      border-radius: var(--radius-lg); 
      margin-top: 16px; 
      display: flex; 
      align-items: center; 
      justify-content: space-between;
      gap: 12px; 
      font-size: 0.85rem; 
    }
    .info-box strong { color: var(--ink); font-weight: 700; }
    .info-box code {
      background: var(--panel);
      padding: 3px 8px;
      border-radius: 4px;
      border: 1px solid var(--line);
      font-family: monospace;
      font-size: 0.8rem;
    }
    .link { color: var(--brand); text-decoration: none; font-weight: 600; word-break: break-all; }
    .link:hover { text-decoration: underline; }

    /* Dialog styling */
    .dialog-form { padding-top: 16px; }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .uppercase { text-transform: uppercase; }
    ::ng-deep .dialog-form .mat-mdc-text-field-wrapper { border-radius: var(--radius-md) !important; }

    .fetch-row { display: flex; align-items: flex-start; gap: 12px; grid-column: span 2; }
    .fetch-row .flex-grow { flex: 1; }
    .fetch-btn { height: 56px; white-space: nowrap; display: flex; align-items: center; gap: 6px; padding: 0 20px; border-radius: var(--radius-md) !important; }
    .fetch-hint {
      grid-column: span 2;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      border-radius: var(--radius-md);
      font-size: 0.82rem;
      font-weight: 600;
      margin-top: -8px;
    }
    .fetch-hint mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .fetch-hint.success { background: rgba(22,163,74,0.08); color: #16a34a; border: 1px solid rgba(22,163,74,0.2); }
    .fetch-hint.error   { background: rgba(239,68,68,0.08);  color: #dc2626; border: 1px solid rgba(239,68,68,0.2); }

    @media (max-width: 1100px) {
      .content-layout { flex-direction: column; }
      .detail-panel { width: 100%; }
    }
    @media (max-width: 768px) {
      .filter-row { flex-direction: column; align-items: stretch; }
      .search-field { max-width: none; }
      .form-grid { grid-template-columns: 1fr; }
      .fetch-row { flex-direction: column; }
      .fetch-btn { width: 100%; justify-content: center; }
    }
  `]
})
export class SubMerchantsPageComponent implements OnInit, AfterViewInit {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild('formDialog') formDialogTemplate!: any;

  readonly dataSource = new MatTableDataSource<EasebuzzSubMerchant>([]);
  readonly displayedColumns = ['business', 'status', 'kyc', 'kycAge', 'contact', 'commission', 'actions'];
  
  readonly subMerchants = signal<EasebuzzSubMerchant[]>([]);
  readonly loaded = signal(false);
  readonly loadError = signal('');
  readonly selectedSubMerchant = signal<EasebuzzSubMerchant | null>(null);
  readonly editingSubMerchant = signal<EasebuzzSubMerchant | null>(null);
  readonly formSaving = signal(false);
  readonly fetchingShop = signal(false);
  readonly fetchNotice = signal<{ type: 'success' | 'error'; msg: string } | null>(null);

  // Business autocomplete
  readonly businesses = signal<AdminBusinessListItem[]>([]);
  readonly businessSearchCtrl = new FormControl('');
  readonly businessSearchQuery = signal('');
  readonly filteredBusinesses = computed(() => {
    const raw = this.businessSearchQuery();
    const q = (typeof raw === 'string' ? raw : '').toLowerCase().trim();
    const all = this.businesses();
    if (!q) return all.slice(0, 50);
    return all.filter(b =>
      (b.shopName ?? '').toLowerCase().includes(q) ||
      String(b.restaurantId).includes(q)
    ).slice(0, 50);
  });

  displayBusiness = (b: AdminBusinessListItem | null): string =>
    b ? `${b.shopName ?? 'Unnamed'} (#${b.restaurantId})` : '';

  onBusinessSelected(event: any): void {
    const b: AdminBusinessListItem = event.option.value;
    // Patch basic fields immediately from the list data (no extra API call needed)
    const phone = (b.whatsappNumber ?? '').replace(/^\+91/, '').replace(/\D/g, '').slice(-10);
    this.subMerchantForm.patchValue({
      restaurantId: b.restaurantId,
      businessName: b.shopName ?? '',
      contactEmail:  b.email ?? '',
      contactPhone:  phone,
    });
    this.fetchNotice.set({ type: 'success', msg: `✓ Loaded: ${b.shopName ?? b.restaurantId} — fill in bank/PAN details below.` });
    // Also try detail fetch for address + gstin (best-effort, ignore failure)
    this.fetchingShop.set(true);
    this.api.getBusinessDetail(b.restaurantId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (biz) => {
        const phone2 = (biz.whatsappNumber || biz.ownerWhatsappNumber || phone)
          .replace(/^\+91/, '').replace(/\D/g, '').slice(-10);
        this.subMerchantForm.patchValue({
          gst:             biz.gstin            || '',
          businessAddress: biz.shopAddress      || '',
          contactPhone:    phone2               || phone,
          contactEmail:    biz.email            || b.email || '',
        });
        this.fetchingShop.set(false);
        this.fetchNotice.set({ type: 'success', msg: `✓ Loaded: ${biz.shopName || b.shopName}` });
      },
      error: () => {
        // Detail fetch failed — basic data from list is already filled, that's fine
        this.fetchingShop.set(false);
      }
    });
  }

  statusFilter = 'ALL';
  kycFilter = 'ALL';

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

  stats = computed(() => {
    const data = this.subMerchants();
    return {
      total: data.length,
      active: data.filter(s => s.status === 'ACTIVE').length,
      pending: data.filter(s => s.status === 'PENDING_KYC' || s.status === 'KYC_SUBMITTED').length,
      rejected: data.filter(s => s.status === 'REJECTED' || s.status === 'FAILED').length
    };
  });

  ngOnInit(): void {
    this.loadSubMerchants();
    this.api.getBusinesses().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (list) => this.businesses.set(list),
      error: () => {}
    });
    this.businessSearchCtrl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(v => this.businessSearchQuery.set(typeof v === 'string' ? v : ''));
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.dataSource.filterPredicate = (data, filter) => {
      const searchStr = filter.toLowerCase();
      const matchesSearch = !searchStr || [
        data.businessName,
        data.contactEmail ?? '',
        data.contactPhone ?? '',
        String(data.id),
        String(data.restaurantId)
      ].some(v => v.toLowerCase().includes(searchStr));
      
      const matchesStatus = this.statusFilter === 'ALL' || data.status === this.statusFilter;
      const matchesKyc = this.kycFilter === 'ALL' || (data.kycStatus ?? 'PENDING') === this.kycFilter;
      
      return matchesSearch && matchesStatus && matchesKyc;
    };
  }

  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }

  applyFilters() {
    const currentFilter = this.dataSource.filter;
    this.dataSource.filter = '';
    this.dataSource.filter = currentFilter;
  }

  loadSubMerchants(): void {
    this.loaded.set(false);
    this.loadError.set('');
    this.api.getSubMerchants().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.subMerchants.set(data);
        this.dataSource.data = data;
        this.loaded.set(true);
      },
      error: () => {
        this.subMerchants.set([]);
        this.dataSource.data = [];
        this.loadError.set('Unable to load sub-merchants.');
        this.loaded.set(true);
      }
    });
  }

  openCreate(prefilledRestaurantId?: number): void {
    this.editingSubMerchant.set(null);
    this.fetchNotice.set(null);
    this.businessSearchCtrl.setValue('');
    this.businessSearchQuery.set('');
    this.subMerchantForm.reset({
      restaurantId: prefilledRestaurantId ?? 0,
      businessName: '', businessType: '', pan: '', gst: '',
      bankAccountNo: '', ifsc: '', beneficiaryName: '', bankName: '', branchName: '',
      businessAddress: '', contactEmail: '', contactPhone: '', commissionRate: 0
    });
    this.dialog.open(this.formDialogTemplate, { width: '840px', maxHeight: '90vh' });
  }

  fetchShopData(): void {
    const rid = Number(this.subMerchantForm.get('restaurantId')?.value);
    if (!rid || rid <= 0) {
      this.fetchNotice.set({ type: 'error', msg: 'Enter a valid Restaurant ID first.' });
      return;
    }
    this.fetchingShop.set(true);
    this.fetchNotice.set(null);
    this.api.getBusinessDetail(rid).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (biz) => {
        const phone = (biz.whatsappNumber || biz.ownerWhatsappNumber || biz.whatsappNumber || '')
          .replace(/^\+91/, '').replace(/\D/g, '').slice(-10);
        this.subMerchantForm.patchValue({
          businessName:    biz.shopName         || '',
          contactEmail:    biz.email            || '',
          contactPhone:    phone,
          gst:             biz.gstin            || '',
          businessAddress: biz.shopAddress      || '',
        });
        this.fetchingShop.set(false);
        this.fetchNotice.set({ type: 'success', msg: `✓ Loaded: ${biz.shopName || rid}` });
      },
      error: (err) => {
        this.fetchingShop.set(false);
        const msg = err?.error?.error || err?.error?.message || 'Not found or server error.';
        this.fetchNotice.set({ type: 'error', msg: `Restaurant #${rid}: ${msg}` });
      }
    });
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
    this.dialog.open(this.formDialogTemplate, { width: '800px' });
  }

  submitForm(): void {
    if (this.subMerchantForm.invalid) {
      this.subMerchantForm.markAllAsTouched();
      return;
    }

    this.formSaving.set(true);
    const raw = this.subMerchantForm.getRawValue();
    const payload: EasebuzzSubMerchantRequest = { ...raw, restaurantId: Number(raw.restaurantId) };

    const edit = this.editingSubMerchant();
    const request = edit
      ? this.api.updateSubMerchant(edit.id, payload)
      : this.api.createSubMerchant(payload);

    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.formSaving.set(false);
        this.dialog.closeAll();
        this.loadSubMerchants();
        this.snackBar.open(edit ? 'Updated successfully' : 'Created successfully', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.formSaving.set(false);
        this.snackBar.open(err?.error?.error || 'Operation failed', 'Close', { duration: 5000 });
      }
    });
  }

  viewDetail(merchant: EasebuzzSubMerchant): void {
    this.selectedSubMerchant.set(merchant);
  }

  closeDetail(): void {
    this.selectedSubMerchant.set(null);
  }

  submitToEasebuzz(merchant: EasebuzzSubMerchant): void {
    if(!confirm(`Submit ${merchant.businessName} to Easebuzz?`)) return;
    this.api.submitToEasebuzz(merchant.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.loadSubMerchants(); this.snackBar.open('Submitted to Easebuzz', 'OK', { duration: 3000 }); },
      error: (err) => this.snackBar.open(err?.error?.error || 'Submission failed', 'OK')
    });
  }

  assignSubMerchantId(merchant: EasebuzzSubMerchant): void {
    const id = prompt('Enter Easebuzz Sub-Merchant ID:');
    if (!id) return;
    this.api.assignSubMerchantId(merchant.id, id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.loadSubMerchants(); this.snackBar.open('ID Assigned', 'OK', { duration: 3000 }); },
      error: (err) => this.snackBar.open(err?.error?.error || 'Assignment failed', 'OK')
    });
  }

  generateKyc(merchant: EasebuzzSubMerchant): void {
    this.api.generateKyc(merchant.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.loadSubMerchants();
        if (res.kyc_url) window.open(res.kyc_url, '_blank');
        this.snackBar.open('KYC Access Generated', 'OK', { duration: 3000 });
      },
      error: () => this.snackBar.open('KYC Generation failed', 'OK')
    });
  }

  updateOnEasebuzz(merchant: EasebuzzSubMerchant): void {
    this.api.updateOnEasebuzz(merchant.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.loadSubMerchants(); this.snackBar.open('Synced to Easebuzz', 'OK', { duration: 3000 }); },
      error: (err) => this.snackBar.open(err?.error?.error || 'Sync failed', 'OK')
    });
  }

  retrieveSplitStatus(merchant: EasebuzzSubMerchant): void {
    const reqId = prompt('Enter Merchant Request ID:');
    if (!reqId) return;
    this.api.retrieveSplitStatus(merchant.id, reqId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => alert(`Split Status: ${JSON.stringify(res.split_configuration)}`),
      error: (err) => this.snackBar.open(err?.error?.error || 'Retrieve failed', 'OK')
    });
  }

  createSplitLabel(merchant: EasebuzzSubMerchant): void {
    this.api.createSplitLabel(merchant.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.loadSubMerchants(); this.snackBar.open('Split Label Created', 'OK', { duration: 3000 }); },
      error: (err) => this.snackBar.open(err?.error?.error || 'Creation failed', 'OK')
    });
  }

  openVerifyOtp(merchant: EasebuzzSubMerchant): void {
    const otp = prompt('Enter 6-digit OTP:');
    if (!otp) return;
    this.api.verifyOtp(merchant.id, otp).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.loadSubMerchants(); this.snackBar.open('OTP Verified', 'OK', { duration: 3000 }); },
      error: (err) => this.snackBar.open(err?.error?.error || 'Verification failed', 'OK')
    });
  }

  openSettlementRetrieve(): void {
    const today = new Date().toISOString().split('T')[0];
    const date = prompt('Enter date (YYYY-MM-DD):', today);
    if (!date) return;
    this.api.retrieveSettlementsByDate(date).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => alert(`Settlements: ${JSON.stringify(res, null, 2)}`),
      error: (err) => this.snackBar.open(err?.error?.error || 'Failed to retrieve', 'OK')
    });
  }

  openOnDemandSettlement(): void {
    const amount = prompt('Enter amount to settle:');
    if (!amount) return;
    this.api.onDemandSettlement(amount).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => this.snackBar.open(`Initiated: ${res.msg}`, 'OK'),
      error: (err) => this.snackBar.open(err?.error?.error || 'Failed', 'OK')
    });
  }

  openPayout(): void {
    const amount = prompt('Enter amount for payout:');
    if (!amount) return;
    const beneficiary = {
      beneficiary_name: 'Manual Payout',
      beneficiary_account_number: '1234567890',
      beneficiary_ifsc: 'HDFC0000123'
    };
    this.api.initiatePayout(amount, beneficiary).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => this.snackBar.open(`Initiated: ${res.msg}`, 'OK'),
      error: (err) => this.snackBar.open(err?.error?.error || 'Failed', 'OK')
    });
  }

  quickStatusAction(merchant: EasebuzzSubMerchant, newStatus: string): void {
    if(!confirm(`Change status to ${newStatus}?`)) return;
    this.api.updateSubMerchantStatus(merchant.id, newStatus).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.loadSubMerchants(); this.snackBar.open(`Status updated to ${newStatus}`, 'OK', { duration: 3000 }); },
      error: () => this.snackBar.open('Status update failed', 'OK')
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

  getStatusChipClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'PENDING_KYC': return 'warn';
      case 'KYC_SUBMITTED': return 'info';
      case 'SUSPENDED': case 'REJECTED': case 'FAILED': return 'danger';
      default: return '';
    }
  }

  getKycChipClass(kycStatus: string | null): string {
    if (!kycStatus) return 'warn';
    switch (kycStatus) {
      case 'ACTIVATED': case 'True': return 'success';
      case 'SUBMITTED': return 'info';
      case 'FAILED': return 'danger';
      default: return 'warn';
    }
  }

  formatStatusValue(status: string): string {
    return status.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
  }

  formatDateValue(value: number | null): string { return formatDate(value); }
  formatKycAge(sm: EasebuzzSubMerchant): string {
    if (sm.kycStatus === 'ACTIVATED' || sm.kycStatus === 'ACTIVE' || sm.kycStatus === 'True') return 'Done';
    return formatAge(sm.kycSubmittedAt ?? sm.createdAt);
  }
}
