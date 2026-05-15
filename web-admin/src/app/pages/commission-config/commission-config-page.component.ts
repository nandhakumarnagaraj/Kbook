import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatDate } from '../../shared/formatters';

interface CommissionRecord {
  id: number;
  subMerchantId: string;
  businessName: string;
  status: string;
  commissionRate: number;
  updatedAt: number;
}

function getStatusChip(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'chip success';
    case 'PENDING_KYC': case 'KYC_SUBMITTED': return 'chip warn';
    case 'SUSPENDED': case 'REJECTED': case 'FAILED': case 'INACTIVE': return 'chip danger';
    default: return 'chip';
  }
}

@Component({
  selector: 'app-commission-config-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Commission Configuration</h2>
        <p class="muted">Manage commission rates for all sub-merchants across the platform.</p>
        <div class="hero-meta">
          <span class="chip">Admin Access</span>
          <span class="chip success">Commission</span>
        </div>
      </section>

      <ng-template #loading>
        <div class="panel loading">Loading commission config...</div>
      </ng-template>

      <div class="panel table-wrap" *ngIf="records().length; else loading">
        <table class="data-table">
          <thead>
            <tr>
              <th>Business Name</th>
              <th>Sub-Merchant ID</th>
              <th>Status</th>
              <th>Current Commission %</th>
              <th>Last Updated</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let rec of records()">
              <td><strong>{{ rec.businessName }}</strong></td>
              <td><code>{{ rec.subMerchantId || '-' }}</code></td>
              <td><span [class]="getChipClass(rec.status)">{{ rec.status }}</span></td>
              <td>
                <span *ngIf="editingId() !== rec.id">
                  <strong>{{ rec.commissionRate }}%</strong>
                </span>
                <div class="edit-inline" *ngIf="editingId() === rec.id">
                  <input class="field-control" type="number" step="0.01" min="0" max="100" [(ngModel)]="editRate" style="width:80px;" />
                  <span class="inline-suffix">%</span>
                  <button class="primary-btn" (click)="saveCommission(rec.id)" [disabled]="saving()">Save</button>
                  <button class="ghost-btn" (click)="cancelEdit()">Cancel</button>
                </div>
              </td>
              <td class="muted">{{ formatDateVal(rec.updatedAt) }}</td>
              <td>
                <button class="ghost-btn" (click)="startEdit(rec)" *ngIf="editingId() !== rec.id">Edit</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="panel loading" *ngIf="!records().length && loaded()">
        <span class="empty-icon">📭</span>
        <p>No commission records found.</p>
      </div>
    </div>
  `,
  styles: [`
    code {
      font-size: 0.82rem;
      background: rgba(0,0,0,0.04);
      padding: 0.15rem 0.35rem;
      border-radius: 4px;
    }
    .edit-inline {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .edit-inline .field-control {
      width: 80px;
      min-height: 36px;
      padding: 0.4rem 0.6rem;
    }
    .edit-inline .primary-btn,
    .edit-inline .ghost-btn {
      padding: 0.4rem 0.7rem;
      font-size: 0.82rem;
    }
    .inline-suffix {
      font-weight: 700;
      font-size: 0.95rem;
    }
    .empty-icon {
      font-size: 2.5rem;
      display: block;
      margin-bottom: 0.5rem;
    }
  `]
})
export class CommissionConfigPageComponent implements OnInit {
  private readonly api = inject(AdminApiService);

  readonly records = signal<CommissionRecord[]>([]);
  readonly loaded = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly saving = signal(false);
  editRate = 0;

  ngOnInit(): void {
    this.loadCommissions();
  }

  loadCommissions(): void {
    this.loaded.set(false);
    this.api.getCommissions().subscribe({
      next: (data) => {
        this.records.set(data);
        this.loaded.set(true);
      },
      error: () => {
        this.records.set([]);
        this.loaded.set(true);
      }
    });
  }

  startEdit(rec: CommissionRecord): void {
    this.editingId.set(rec.id);
    this.editRate = rec.commissionRate;
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.editRate = 0;
  }

  saveCommission(id: number): void {
    this.saving.set(true);
    this.api.updateCommission(id, this.editRate).subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelEdit();
        this.loadCommissions();
        this.showToast('Commission rate updated successfully.');
      },
      error: () => {
        this.saving.set(false);
        this.showToast('Failed to update commission rate.');
      }
    });
  }

  private showToast(message: string): void {
    const toast = document.createElement('div');
    toast.className = 'toast success';
    toast.textContent = message;
    const bar = document.querySelector('.toast-bar') || (() => {
      const b = document.createElement('div');
      b.className = 'toast-bar';
      document.body.appendChild(b);
      return b;
    })();
    bar.appendChild(toast);
    setTimeout(() => toast.remove(), 3500);
  }

  getChipClass(status: string): string {
    return getStatusChip(status);
  }

  formatDateVal(value: number): string {
    return formatDate(value);
  }
}
