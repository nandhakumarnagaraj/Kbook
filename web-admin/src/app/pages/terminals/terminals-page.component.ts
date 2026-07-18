import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessTerminal, TerminalRequest } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-terminals-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Devices &amp; Terminals</h2>
        <p class="muted">Manage POS devices, approve new device requests, and recover terminals.</p>
        <div class="hero-meta">
          <span class="chip">{{ terminals().length }} Registered</span>
          <span class="chip warn">{{ pendingRequests().length }} Pending</span>
        </div>
      </section>

      <div class="toolbar">
        <div>
          <h3>Registered Terminals</h3>
          <p class="muted">Active and deactivated POS devices for this shop.</p>
        </div>
        <button class="ghost-btn" (click)="reload()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="loaded(); else loading">
        <table class="data-table" *ngIf="terminals().length; else noTerminals">
          <thead>
            <tr>
              <th>Name</th>
              <th>Series</th>
              <th>Device</th>
              <th>Status</th>
              <th>Active</th>
              <th>Updated</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let terminal of terminals()">
              <td>
                <ng-container *ngIf="editingId() === terminal.id; else nameCell">
                  <input
                    class="field-control"
                    [(ngModel)]="editName"
                    (keyup.enter)="saveRename(terminal)"
                    placeholder="Terminal name"
                  />
                  <div class="row-actions">
                    <button class="ghost-btn" [disabled]="saving()" (click)="saveRename(terminal)">Save</button>
                    <button class="ghost-btn" (click)="cancelEdit()">Cancel</button>
                  </div>
                </ng-container>
                <ng-template #nameCell>
                  <strong>{{ terminal.terminalName || 'Unnamed' }}</strong>
                </ng-template>
              </td>
              <td>{{ terminal.terminalSeries || '-' }}</td>
              <td>
                <span class="muted">{{ terminal.deviceId || '-' }}</span>
              </td>
              <td>
                <span
                  class="chip"
                  [class.success]="terminal.status.toLowerCase() === 'active'"
                  [class.danger]="terminal.status.toLowerCase() === 'inactive'"
                >
                  {{ terminal.status }}
                </span>
              </td>
              <td>{{ terminal.isActive ? 'Yes' : 'No' }}</td>
              <td>{{ formatDateValue(terminal.updatedAt) }}</td>
              <td>
                <div class="action-stack">
                  <button class="ghost-btn" [disabled]="saving()" (click)="startEdit(terminal)">Rename</button>
                  <button
                    *ngIf="terminal.status.toLowerCase() !== 'inactive'"
                    class="ghost-btn danger-btn"
                    [disabled]="saving()"
                    (click)="deactivate(terminal)"
                  >
                    Deactivate
                  </button>
                  <button
                    *ngIf="terminal.status.toLowerCase() === 'inactive' && canManageTerminals()"
                    class="ghost-btn success-btn"
                    [disabled]="saving()"
                    (click)="confirmReactivate(terminal)"
                  >
                    Reactivate
                  </button>
                  <span *ngIf="terminal.status.toLowerCase() === 'inactive' && !canManageTerminals()" class="muted">Deactivated</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <ng-template #noTerminals>
          <div class="loading">No terminals registered yet.</div>
        </ng-template>
      </div>

      <ng-template #loading>
        <div class="panel loading">Loading devices...</div>
      </ng-template>

      <section class="panel filter-panel" *ngIf="loaded()">
        <div class="toolbar">
          <div>
            <h3>Device Requests</h3>
            <p class="muted">New devices requesting access to this shop.</p>
          </div>
          <button
            class="ghost-btn"
            [class.active]="requestFilter() === 'PENDING'"
            (click)="setRequestFilter('PENDING')"
          >
            Pending
          </button>
          <button
            class="ghost-btn"
            [class.active]="requestFilter() === 'ALL'"
            (click)="setRequestFilter('ALL')"
          >
            All
          </button>
        </div>

        <div class="panel table-wrap" *ngIf="pendingOrAllRequests().length; else noRequests">
          <table class="data-table">
            <thead>
              <tr>
                <th>Device</th>
                <th>Model</th>
                <th>Type</th>
                <th>Status</th>
                <th>Requested</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let req of pendingOrAllRequests()">
                <td>
                  <div class="stacked-meta">
                    <strong>{{ req.deviceName || '-' }}</strong>
                    <span class="muted">{{ req.deviceId || 'No device id' }}</span>
                  </div>
                </td>
                <td>{{ req.deviceModel || '-' }}</td>
                <td>{{ req.requestType || '-' }}</td>
                <td>
                  <span
                    class="chip"
                    [class.success]="req.status.toLowerCase() === 'approved'"
                    [class.danger]="req.status.toLowerCase() === 'rejected'"
                    [class.warn]="req.status.toLowerCase() === 'pending'"
                  >
                    {{ req.status }}
                  </span>
                </td>
                <td>{{ formatDateValue(req.requestedAt) }}</td>
                <td>
                  <div class="action-stack" *ngIf="req.status.toLowerCase() === 'pending'; else reqDone">
                    <button class="ghost-btn success-btn" [disabled]="saving()" (click)="approve(req)">Approve</button>
                    <button class="ghost-btn danger-btn" [disabled]="saving()" (click)="reject(req)">Reject</button>
                  </div>
                  <ng-template #reqDone><span class="muted">{{ req.rejectionReason || '-' }}</span></ng-template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <ng-template #noRequests>
          <div class="loading">No device requests.</div>
        </ng-template>
      </section>

      <app-confirm-dialog
        *ngIf="reactivatingTerminal()"
        title="Reactivate Terminal"
        [message]="getReactivateMessage()"
        confirmLabel="Reactivate"
        cancelLabel="Cancel"
        (confirmed)="doReactivate()"
        (cancelled)="reactivatingTerminal.set(null)"
      ></app-confirm-dialog>

      <div class="toast" *ngIf="toast(); else nostate">{{ toast() }}</div>
      <ng-template #nostate></ng-template>
    </div>
  `,
  styles: [`
    .danger-btn { color: #b03030; border-color: #b03030; }
    .success-btn { color: #2d7a3a; border-color: #2d7a3a; }
    .ghost-btn.active { background: rgba(181, 106, 45, 0.16); color: var(--brand-deep); }
    .action-stack { display: flex; flex-direction: column; align-items: flex-start; gap: 0.35rem; }
    .row-actions { display: flex; gap: 0.4rem; margin-top: 0.3rem; }
    .stacked-meta { display: flex; flex-direction: column; }
    .chip.success { background: #e6f4ea; color: #2d7a3a; }
    .chip.danger { background: #fdecea; color: #b03030; }
    .chip.warn { background: #fff8e1; color: #7a5c00; }
    .toast {
      position: fixed;
      bottom: 1.5rem;
      right: 1.5rem;
      background: #24170f;
      color: #fff;
      padding: 0.85rem 1.25rem;
      border-radius: 12px;
      box-shadow: 0 12px 30px rgba(0, 0, 0, 0.25);
      z-index: 1100;
      max-width: 320px;
    }
  `]
})
export class TerminalsPageComponent {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);

  readonly terminals = signal<BusinessTerminal[]>([]);
  readonly requests = signal<TerminalRequest[]>([]);
  readonly loaded = signal(false);
  readonly saving = signal(false);
  readonly toast = signal<string | null>(null);
  readonly reactivatingTerminal = signal<BusinessTerminal | null>(null);

  readonly requestFilter = signal<'PENDING' | 'ALL'>('PENDING');
  readonly editingId = signal<number | null>(null);
  editName = '';

  readonly pendingRequests = computed(() =>
    this.requests().filter((r) => r.status?.toLowerCase() === 'pending')
  );

  readonly pendingOrAllRequests = computed(() => {
    if (this.requestFilter() === 'ALL') return this.requests();
    return this.pendingRequests();
  });

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loaded.set(false);
    this.api.getTerminals().subscribe({
      next: (data) => {
        this.terminals.set(data);
        this.loaded.set(true);
      },
      error: () => this.fail('Failed to load terminals')
    });
    this.api.getTerminalRequests('ALL').subscribe({
      next: (data) => this.requests.set(data),
      error: () => this.fail('Failed to load device requests')
    });
  }

  setRequestFilter(filter: 'PENDING' | 'ALL'): void {
    this.requestFilter.set(filter);
  }

  startEdit(terminal: BusinessTerminal): void {
    this.editingId.set(terminal.id);
    this.editName = terminal.terminalName ?? '';
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.editName = '';
  }

  saveRename(terminal: BusinessTerminal): void {
    const name = this.editName.trim();
    if (!name) return;
    this.saving.set(true);
    this.api.renameTerminal(terminal.id, { name }).subscribe({
      next: (updated) => {
        this.terminals.update((list) =>
          list.map((t) => (t.id === updated.id ? updated : t))
        );
        this.saving.set(false);
        this.cancelEdit();
        this.notify('Terminal renamed');
      },
      error: () => {
        this.saving.set(false);
        this.fail('Rename failed');
      }
    });
  }

  deactivate(terminal: BusinessTerminal): void {
    if (!confirm(`Deactivate terminal "${terminal.terminalName || terminal.terminalSeries}"?`)) return;
    this.saving.set(true);
    this.api.deactivateTerminal(terminal.id).subscribe({
      next: () => {
        this.terminals.update((list) =>
          list.map((t) => (t.id === terminal.id ? { ...t, status: 'INACTIVE', isActive: false } : t))
        );
        this.saving.set(false);
        this.notify('Terminal deactivated');
      },
      error: () => {
        this.saving.set(false);
        this.fail('Deactivate failed');
      }
    });
  }

  approve(req: TerminalRequest): void {
    this.saving.set(true);
    this.api.approveTerminalRequest(req.id).subscribe({
      next: () => {
        this.requests.update((list) =>
          list.map((r) => (r.id === req.id ? { ...r, status: 'APPROVED' } : r))
        );
        this.saving.set(false);
        this.notify('Device request approved');
      },
      error: (err) => {
        this.saving.set(false);
        this.fail(this.errMsg(err, 'Approval failed'));
      }
    });
  }

  reject(req: TerminalRequest): void {
    if (!confirm('Reject this device request?')) return;
    this.saving.set(true);
    this.api.rejectTerminalRequest(req.id).subscribe({
      next: () => {
        this.requests.update((list) =>
          list.map((r) => (r.id === req.id ? { ...r, status: 'REJECTED' } : r))
        );
        this.saving.set(false);
        this.notify('Device request rejected');
      },
      error: () => {
        this.saving.set(false);
        this.fail('Reject failed');
      }
    });
  }

  canManageTerminals(): boolean {
    const role = this.auth.session()?.role;
    return role === 'OWNER' || role === 'SHOP_ADMIN';
  }

  confirmReactivate(terminal: BusinessTerminal): void {
    this.reactivatingTerminal.set(terminal);
  }

  getReactivateMessage(): string {
    const t = this.reactivatingTerminal();
    const name = t?.terminalName || t?.terminalSeries || 'Unnamed';
    return `Reactivate terminal "${name}"? It will become active and count toward the 5 active terminal limit.`;
  }

  doReactivate(): void {
    const terminal = this.reactivatingTerminal();
    if (!terminal) return;
    this.reactivatingTerminal.set(null);
    this.saving.set(true);
    this.api.reactivateTerminal(terminal.id).subscribe({
      next: () => {
        this.terminals.update((list) =>
          list.map((t) => (t.id === terminal.id ? { ...t, status: 'ACTIVE', isActive: true } : t))
        );
        this.saving.set(false);
        this.notify('Terminal reactivated');
      },
      error: (err) => {
        this.saving.set(false);
        const msg = this.errMsg(err, 'Reactivation failed');
        if (msg === 'MAX_ACTIVE_TERMINALS_REACHED'
            || msg.toLowerCase().includes('limit')
            || msg.toLowerCase().includes('maximum')) {
          this.fail('Cannot reactivate: maximum 5 active terminals reached');
        } else {
          this.fail(msg);
        }
      }
    });
  }

  private notify(message: string): void {
    this.toast.set(message);
    setTimeout(() => this.toast.set(null), 2600);
  }

  private fail(message: string): void {
    this.loaded.set(true);
    this.notify(message);
  }

  private errMsg(err: unknown, fallback: string): string {
    const e = err as { error?: { error?: string } } | null;
    return e?.error?.error || fallback;
  }

  formatDateValue(value: number | null): string { return formatDate(value); }
}


