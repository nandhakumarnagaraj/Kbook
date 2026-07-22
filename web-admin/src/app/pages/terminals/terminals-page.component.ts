import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessTerminal, RecoverTerminalResponse, TerminalRequest } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-terminals-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent],
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

      <div class="panel table-wrap" *ngIf="!terminalsLoading(); else loading">
        <div class="alert error load-error" role="alert" *ngIf="terminalsError()">
          <span>{{ terminalsError() }}</span>
          <button type="button" class="ghost-btn" (click)="loadTerminals()">Try again</button>
        </div>
        <table class="data-table" *ngIf="!terminalsError() && terminals().length; else noTerminals">
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
                  class="chip-pill"
                  [class.chip-pill--ok]="terminal.status.toLowerCase() === 'active'"
                  [class.chip-pill--pending]="terminal.status.toLowerCase() === 'inactive'"
                >
                  {{ terminal.status }}
                </span>
              </td>
              <td>{{ terminal.isActive ? 'Yes' : 'No' }}</td>
              <td>{{ formatDateValue(terminal.updatedAt) }}</td>
              <td>
                <div class="action-stack">
                  <button class="ghost-btn" [disabled]="saving()" (click)="startEdit(terminal)">Rename</button>
                  <button class="ghost-btn" [disabled]="saving()" (click)="startRecovery(terminal)">Recover</button>
                  <button
                    *ngIf="terminal.status.toLowerCase() !== 'inactive'"
                    class="ghost-btn danger-btn"
                    [disabled]="saving()"
                    (click)="requestDeactivate(terminal)"
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

        <div class="mobile-data-list" *ngIf="!terminalsError() && terminals().length" aria-label="Registered terminals">
          <article class="mobile-data-card" *ngFor="let terminal of terminals()">
            <div class="mobile-data-card__head"><strong>{{ terminal.terminalName || 'Unnamed terminal' }}</strong><span class="chip" [class.success]="terminal.status.toLowerCase() === 'active'" [class.warn]="terminal.status.toLowerCase() === 'inactive'">{{ terminal.status }}</span></div>
            <p>{{ terminal.terminalSeries || 'No series' }} · {{ terminal.deviceId || 'No device assigned' }}</p>
            <dl><div><dt>Active</dt><dd>{{ terminal.isActive ? 'Yes' : 'No' }}</dd></div><div><dt>Updated</dt><dd>{{ formatDateValue(terminal.updatedAt) }}</dd></div></dl>
            <div class="mobile-data-card__actions">
              <button class="ghost-btn" [disabled]="saving()" (click)="startEdit(terminal)">Rename</button>
              <button class="ghost-btn" [disabled]="saving()" (click)="startRecovery(terminal)">Recover</button>
              <button *ngIf="terminal.status.toLowerCase() !== 'inactive'" class="ghost-btn danger-btn" [disabled]="saving()" (click)="requestDeactivate(terminal)">Deactivate</button>
              <button *ngIf="terminal.status.toLowerCase() === 'inactive' && canManageTerminals()" class="ghost-btn success-btn" [disabled]="saving()" (click)="confirmReactivate(terminal)">Reactivate</button>
            </div>
          </article>
        </div>

        <ng-template #noTerminals>
          <app-empty-state
            *ngIf="!terminalsError()"
            icon="📟"
            title="No terminals registered yet"
            text="New devices will appear here once they request access to this shop."
          ></app-empty-state>
        </ng-template>
      </div>

      <ng-template #loading>
        <div class="panel loading">
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4]"></div>
          </div>
        </div>
      </ng-template>

      <section class="panel filter-panel">
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

        <div class="loading compact-loading" role="status" *ngIf="requestsLoading()">Loading device requests...</div>
        <div class="alert error load-error" role="alert" *ngIf="requestsError()">
          <span>{{ requestsError() }}</span>
          <button type="button" class="ghost-btn" (click)="loadRequests()">Try again</button>
        </div>

        <div class="panel table-wrap" *ngIf="!requestsLoading() && !requestsError() && pendingOrAllRequests().length">
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
                  <button class="ghost-btn danger-btn" [disabled]="saving()" (click)="requestReject(req)">Reject</button>
                  </div>
                  <ng-template #reqDone><span class="muted">{{ req.rejectionReason || '-' }}</span></ng-template>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="mobile-data-list" aria-label="Terminal requests">
            <article class="mobile-data-card" *ngFor="let req of pendingOrAllRequests()">
              <div class="mobile-data-card__head"><strong>{{ req.deviceName || 'Unnamed device' }}</strong><span class="chip" [class.success]="req.status.toLowerCase() === 'approved'" [class.danger]="req.status.toLowerCase() === 'rejected'" [class.warn]="req.status.toLowerCase() === 'pending'">{{ req.status }}</span></div>
              <p>{{ req.deviceModel || 'Unknown model' }} · {{ req.deviceId || 'No device ID' }}</p>
              <dl><div><dt>Type</dt><dd>{{ req.requestType || '-' }}</dd></div><div><dt>Requested</dt><dd>{{ formatDateValue(req.requestedAt) }}</dd></div></dl>
              <div class="mobile-data-card__actions" *ngIf="req.status.toLowerCase() === 'pending'">
                <button class="ghost-btn success-btn" [disabled]="saving()" (click)="approve(req)">Approve</button>
                <button class="ghost-btn danger-btn" [disabled]="saving()" (click)="requestReject(req)">Reject</button>
              </div>
            </article>
          </div>
        </div>
        <app-empty-state
          *ngIf="!requestsLoading() && !requestsError() && !pendingOrAllRequests().length"
          icon="🔔"
          title="No device requests"
          text="Pending and processed device access requests will appear here."
        ></app-empty-state>
      </section>

      <div class="modal-backdrop" *ngIf="recoveringTerminal() as terminal" (click)="closeRecovery()">
        <section
          class="modal-box recovery-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="recovery-title"
          (click)="$event.stopPropagation()"
        >
          <ng-container *ngIf="recoveryResult() as result; else recoveryForm">
            <h3 id="recovery-title">Terminal recovery ready</h3>
            <p class="muted">{{ result.terminalName || result.terminalSeries || 'Terminal' }} is now assigned to the new device.</p>
            <div class="credential-box">
              <span class="credential-label">One-time terminal token</span>
              <code>{{ result.terminalToken }}</code>
            </div>
            <p class="recovery-warning">Copy this token now and enter it on the Android device. It will not be shown again after closing.</p>
            <div class="modal-actions">
              <button type="button" class="ghost-btn" (click)="copyRecoveryToken()">Copy token</button>
              <button type="button" class="primary-btn" (click)="closeRecovery()">Done</button>
            </div>
          </ng-container>

          <ng-template #recoveryForm>
            <h3 id="recovery-title">Recover {{ terminal.terminalName || terminal.terminalSeries || 'terminal' }}</h3>
            <p class="muted">Rebind this terminal to a replacement Android device. Existing terminal credentials will stop working.</p>
            <div class="filter-group">
              <label for="recovery-device-id">New device ID</label>
              <input
                id="recovery-device-id"
                class="field-control"
                [(ngModel)]="recoveryDeviceId"
                placeholder="Enter the ID shown on the new device"
                autocomplete="off"
                autofocus
              />
            </div>
            <p class="error-text" role="alert" *ngIf="recoveryError()">{{ recoveryError() }}</p>
            <div class="modal-actions">
              <button type="button" class="ghost-btn" [disabled]="saving()" (click)="closeRecovery()">Cancel</button>
              <button
                type="button"
                class="primary-btn"
                [disabled]="saving() || !recoveryDeviceId.trim()"
                (click)="recoverTerminal()"
              >
                {{ saving() ? 'Recovering...' : 'Recover terminal' }}
              </button>
            </div>
          </ng-template>
        </section>
      </div>

      <app-confirm-dialog
        *ngIf="reactivatingTerminal()"
        title="Reactivate Terminal"
        [message]="getReactivateMessage()"
        confirmLabel="Reactivate"
        cancelLabel="Cancel"
        (confirmed)="doReactivate()"
        (cancelled)="reactivatingTerminal.set(null)"
      ></app-confirm-dialog>

      <app-confirm-dialog
        *ngIf="deactivatingTerminal()"
        title="Deactivate Terminal"
        [message]="getDeactivateMessage()"
        confirmLabel="Deactivate"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="doDeactivate()"
        (cancelled)="deactivatingTerminal.set(null)"
      ></app-confirm-dialog>

      <app-confirm-dialog
        *ngIf="rejectingRequest()"
        title="Reject Device Request"
        [message]="getRejectMessage()"
        confirmLabel="Reject"
        cancelLabel="Cancel"
        [confirmDanger]="true"
        (confirmed)="doReject()"
        (cancelled)="rejectingRequest.set(null)"
      ></app-confirm-dialog>

    </div>
  `,
  styles: [`
    .ghost-btn.active { background: rgba(249, 115, 22, 0.16); color: var(--brand-deep); }
    .action-stack { display: flex; flex-direction: column; align-items: flex-start; gap: 0.35rem; }
    .row-actions { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-top: 0.3rem; }
    .stacked-meta { display: flex; flex-direction: column; gap: 0.15rem; }
    .recovery-dialog { width: min(100%, 520px); }
    .credential-box {
      display: grid;
      gap: 0.45rem;
      margin: 1.1rem 0;
      padding: 1rem;
      background: var(--bg);
      border: 1px solid var(--line);
      border-radius: 12px;
    }
    .credential-box code {
      display: block;
      overflow-wrap: anywhere;
      color: var(--ink);
      font-size: 0.9rem;
      line-height: 1.55;
      user-select: all;
    }
    .credential-label {
      color: var(--brand-deep);
      font-size: 0.78rem;
      font-weight: 800;
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }
    .recovery-warning {
      margin: 0;
      padding: 0.85rem 1rem;
      color: #6f4e00;
      background: #fff4cf;
      border: 1px solid #ead58e;
      border-radius: 12px;
      line-height: 1.5;
    }
    .error-text { margin: 0.75rem 0 0; color: var(--danger); font-weight: 650; }
    .modal-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 0.65rem; margin-top: 1.25rem; }
    @media (max-width: 480px) {
      .modal-actions button { width: 100%; }
      .credential-box { padding: 0.85rem; }
    }
  `]
})
export class TerminalsPageComponent {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private recoveryTrigger: HTMLElement | null = null;
  private readonly recoveryKeydownHandler = (event: KeyboardEvent) => this.handleRecoveryKeydown(event);

  readonly terminals = signal<BusinessTerminal[]>([]);
  readonly requests = signal<TerminalRequest[]>([]);
  readonly terminalsLoading = signal(true);
  readonly requestsLoading = signal(true);
  readonly terminalsError = signal('');
  readonly requestsError = signal('');
  readonly saving = signal(false);
  readonly reactivatingTerminal = signal<BusinessTerminal | null>(null);
  readonly deactivatingTerminal = signal<BusinessTerminal | null>(null);
  readonly rejectingRequest = signal<TerminalRequest | null>(null);
  readonly recoveringTerminal = signal<BusinessTerminal | null>(null);
  readonly recoveryResult = signal<RecoverTerminalResponse | null>(null);
  readonly recoveryError = signal('');

  readonly requestFilter = signal<'PENDING' | 'ALL'>('PENDING');
  readonly editingId = signal<number | null>(null);
  editName = '';
  recoveryDeviceId = '';

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
    this.loadTerminals();
    this.loadRequests();
  }

  loadTerminals(): void {
    this.terminalsLoading.set(true);
    this.terminalsError.set('');
    this.api.getTerminals().subscribe({
      next: (data) => {
        this.terminals.set(data);
        this.terminalsLoading.set(false);
      },
      error: (err) => {
        this.terminalsLoading.set(false);
        this.terminalsError.set(this.errMsg(err, 'Failed to load registered terminals.'));
      }
    });
  }

  loadRequests(): void {
    this.requestsLoading.set(true);
    this.requestsError.set('');
    this.api.getTerminalRequests('ALL').subscribe({
      next: (data) => {
        this.requests.set(data);
        this.requestsLoading.set(false);
      },
      error: (err) => {
        this.requestsLoading.set(false);
        this.requestsError.set(this.errMsg(err, 'Failed to load device requests.'));
      }
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

  startRecovery(terminal: BusinessTerminal): void {
    this.recoveryTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    this.recoveringTerminal.set(terminal);
    this.recoveryResult.set(null);
    this.recoveryError.set('');
    this.recoveryDeviceId = '';
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', this.recoveryKeydownHandler);
    setTimeout(() => document.getElementById('recovery-device-id')?.focus());
  }

  closeRecovery(): void {
    if (this.saving()) return;
    document.removeEventListener('keydown', this.recoveryKeydownHandler);
    document.body.style.overflow = '';
    this.recoveringTerminal.set(null);
    this.recoveryResult.set(null);
    this.recoveryError.set('');
    this.recoveryDeviceId = '';
    const trigger = this.recoveryTrigger;
    this.recoveryTrigger = null;
    setTimeout(() => trigger?.focus());
  }

  recoverTerminal(): void {
    const terminal = this.recoveringTerminal();
    const deviceId = this.recoveryDeviceId.trim();
    if (!terminal || !deviceId || this.saving()) return;

    this.saving.set(true);
    this.recoveryError.set('');
    this.api.recoverTerminal(terminal.id, { deviceId }).subscribe({
      next: (result) => {
        this.terminals.update((list) => list.map((item) =>
          item.id === terminal.id
            ? {
                ...item,
                deviceId,
                status: 'ACTIVE',
                isActive: true,
                credentialVersion: (item.credentialVersion ?? 0) + 1,
                updatedAt: Date.now()
              }
            : item
        ));
        this.recoveryDeviceId = '';
        this.recoveryResult.set(result);
        this.saving.set(false);
        this.notify('Terminal recovered. Copy the new token now.');
        setTimeout(() => {
          document.querySelector<HTMLElement>('.recovery-dialog .ghost-btn')?.focus();
        });
      },
      error: (err) => {
        this.saving.set(false);
        this.recoveryError.set(this.errMsg(err, 'Terminal recovery failed. Check the device ID and try again.'));
      }
    });
  }

  async copyRecoveryToken(): Promise<void> {
    const token = this.recoveryResult()?.terminalToken;
    if (!token) return;
    try {
      await navigator.clipboard.writeText(token);
      this.notify('Terminal token copied');
    } catch {
      this.toast.show('Could not copy automatically. Select and copy the token manually.', 'error');
    }
  }

  deactivate(terminal: BusinessTerminal): void {
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

  requestDeactivate(terminal: BusinessTerminal): void {
    this.deactivatingTerminal.set(terminal);
  }

  getDeactivateMessage(): string {
    const t = this.deactivatingTerminal();
    const name = t?.terminalName || t?.terminalSeries || 'Unnamed';
    return `Deactivate terminal "${name}"? It will be marked inactive immediately.`;
  }

  doDeactivate(): void {
    const terminal = this.deactivatingTerminal();
    this.deactivatingTerminal.set(null);
    if (terminal) this.deactivate(terminal);
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

  requestReject(req: TerminalRequest): void {
    this.rejectingRequest.set(req);
  }

  getRejectMessage(): string {
    const r = this.rejectingRequest();
    const name = r?.deviceName || r?.deviceId || 'this device';
    return `Reject the device request for "${name}"?`;
  }

  doReject(): void {
    const req = this.rejectingRequest();
    this.rejectingRequest.set(null);
    if (req) this.reject(req);
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

  private handleRecoveryKeydown(event: KeyboardEvent): void {
    if (!this.recoveringTerminal()) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeRecovery();
      return;
    }
    if (event.key !== 'Tab') return;

    const dialog = document.querySelector<HTMLElement>('.recovery-dialog');
    const focusable = dialog?.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'
    );
    if (!focusable?.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private notify(message: string): void {
    this.toast.show(message, 'success');
  }

  private fail(message: string): void {
    this.toast.show(message, 'error');
  }

  private errMsg(err: unknown, fallback: string): string {
    const e = err as { error?: { error?: string; message?: string } } | null;
    return e?.error?.message || e?.error?.error || fallback;
  }

  formatDateValue(value: number | null): string { return formatDate(value); }
}


