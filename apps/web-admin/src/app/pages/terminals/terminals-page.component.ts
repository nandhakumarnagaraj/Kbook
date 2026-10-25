import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessTerminal, RecoverTerminalResponse, TerminalRequest } from '../../core/models/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { KpiItem, KpiRowComponent } from '../../shared/kpi-row.component';
import { PageHeaderComponent } from '../../shared/page-header.component';
import { RefreshButtonComponent } from '../../shared/refresh-button.component';
import { ApiStateComponent } from '../../core/components/api-state.component';
import { FocusTrapDirective } from '../../core/components/focus-trap.directive';
import { formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-terminals-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ConfirmDialogComponent, EmptyStateComponent, PageHeaderComponent, ApiStateComponent, RefreshButtonComponent, KpiRowComponent, FocusTrapDirective],
  template: `
    <div class="page-shell">
      <!-- Operational Header (unified PageHeaderComponent) -->
      <app-page-header
        title="POS Devices &amp; Terminals"
        subtitle="Manage authorized Android billing registers, approve counter pairing requests, and handle terminal recovery tokens."
      >
        <span
          kbHeaderBadge
          class="kpi-tag"
          [class.kpi-tag--green]="activeTerminalsCount > 0"
          [class.kpi-tag--neutral]="activeTerminalsCount === 0"
          *ngIf="!terminalsLoading()">
          <span class="pulse-indicator" aria-hidden="true" *ngIf="activeTerminalsCount > 0"></span>
          {{ activeTerminalsCount }} Active
        </span>

        <app-refresh-button (refresh)="reload()" [disabled]="terminalsLoading() || requestsLoading()" [loading]="terminalsLoading() || requestsLoading()" ariaLabel="Refresh terminals" />
      </app-page-header>

      <!-- Shared KPI row: same anatomy as every other page's summary strip. -->
      <app-kpi-row [items]="fleetKpis()" ariaLabel="Terminal fleet overview" />

      <div *ngIf="fleetOverLimit()" class="fleet-limit-banner" role="status">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3M12 9v4m0 4h.01"/></svg>
        <span>You have {{ activeTerminalsCount }} active terminals; your plan allows {{ maxActiveTerminals }}. Deactivate a terminal before activating another.</span>
      </div>

      <!-- Registered Terminals Section -->
      <section class="panel terminals-panel">
        <div class="section-topbar">
          <div class="topbar-heading">
            <h3>Registered terminals</h3>
            <p class="muted">Active and deactivated Android POS devices registered to this shop.</p>
          </div>
        </div>

        <div class="table-wrap" *ngIf="!terminalsLoading(); else loading">
          <app-api-state
            *ngIf="terminalsError()"
            [message]="terminalsError()"
            (retry)="loadTerminals()"
          ></app-api-state>

          <table class="data-table" *ngIf="!terminalsError() && terminals().length; else noTerminals">
            <thead>
              <tr>
                <th>Hardware / Terminal</th>
                <th>Role</th>
                <th>Hardware ID</th>
                <th>Status</th>
                <th>Updated</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let terminal of terminals(); trackBy: trackByTerminalId">
                <td>
                  <ng-container *ngIf="editingId() === terminal.id; else nameCell">
                    <!-- Flags on the rule the save actually applies — a name that
                         is only whitespace is refused by saveRename() even though
                         the required validator would call it non-empty. -->
                    <input
                      class="field-control"
                      #editNameRef="ngModel"
                      [(ngModel)]="editName"
                      (keyup.enter)="saveRename(terminal)"
                      [attr.aria-invalid]="editNameRef.touched && !editName.trim()"
                      [attr.aria-describedby]="editNameRef.touched && !editName.trim() ? 'terminal-name-error' : null"
                      placeholder="Terminal name"
                    />
                    <!-- A name of spaces is refused by saveRename(), so the message says what
                         the rule is rather than leaving the red border to explain itself. -->
                    <div class="field-error" id="terminal-name-error" *ngIf="editNameRef.touched && !editName.trim()">Terminal name is required.</div>
                    <div class="row-actions">
                      <button class="primary-btn small-btn" [disabled]="saving()" (click)="saveRename(terminal)">Save</button>
                      <button class="ghost-btn small-btn" (click)="cancelEdit()">Cancel</button>
                    </div>
                  </ng-container>
                  <ng-template #nameCell>
                    <div class="terminal-name-cell">
                      <div class="terminal-icon" [class.terminal-icon--active]="terminal.isActive">
                        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <rect x="4" y="2" width="16" height="20" rx="2" ry="2"/>
                          <line x1="12" y1="18" x2="12.01" y2="18"/>
                        </svg>
                      </div>
                      <div>
                        <strong>{{ terminal.terminalName || 'Unnamed Register' }}</strong>
                        <span class="primary-badge" *ngIf="terminal.isPrimary">Primary</span>
                      </div>
                    </div>
                  </ng-template>
                </td>
                <td>
                  <span class="role-pill" [class.role-pill--billing]="terminal.terminalType === 'BILLING'" [class.role-pill--kot]="terminal.terminalType === 'KOT'">
                    {{ terminal.terminalType || 'BILLING' }}
                  </span>
                </td>
                <td>
                  <span class="device-pill" *ngIf="terminal.deviceId; else noDevice" [title]="terminal.deviceId">
                    <code>{{ formatDeviceId(terminal.deviceId) }}</code>
                    <button type="button" class="device-copy-btn" title="Copy full Device ID" [attr.aria-label]="'Copy full Device ID ' + terminal.deviceId" (click)="copyDeviceId(terminal.deviceId, $event)">
                      <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                      </svg>
                    </button>
                  </span>
                  <ng-template #noDevice><span class="muted">-</span></ng-template>
                </td>
                <td>
                  <span
                    class="status-indicator-pill"
                    [class.status-indicator-pill--active]="terminal.status.toLowerCase() === 'active'"
                    [class.status-indicator-pill--inactive]="terminal.status.toLowerCase() === 'inactive'"
                  >
                    <span class="indicator-dot"></span>
                    {{ terminal.status }}
                  </span>
                </td>
                <td class="tabular-num muted">{{ formatDateValue(terminal.updatedAt) }}</td>
                <td>
                  <div class="action-stack-row">
                    <button class="ghost-btn small-btn" [disabled]="saving()" (click)="startEdit(terminal)">Rename</button>
                    <button class="ghost-btn small-btn" [disabled]="saving()" (click)="startRecovery(terminal)" title="Recover access using the new device ID">Recover access</button>
                    <button
                      *ngIf="terminal.status.toLowerCase() !== 'inactive'"
                      class="ghost-btn danger-btn small-btn"
                      [disabled]="saving()"
                      (click)="requestDeactivate(terminal)"
                    >
                      Deactivate
                    </button>
                    <button
                      *ngIf="terminal.status.toLowerCase() === 'inactive' && canManageTerminals()"
                      class="ghost-btn success-btn small-btn"
                      [disabled]="saving()"
                      (click)="confirmReactivate(terminal)"
                    >
                      Reactivate
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>

          <div class="mobile-data-list" *ngIf="!terminalsError() && terminals().length" aria-label="Registered terminals">
            <article class="mobile-data-card" *ngFor="let terminal of terminals(); trackBy: trackByTerminalId">
              <div class="mobile-data-card__head">
                <div class="terminal-name-cell">
                  <div class="terminal-icon" [class.terminal-icon--active]="terminal.isActive">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="4" y="2" width="16" height="20" rx="2" ry="2"/>
                      <line x1="12" y1="18" x2="12.01" y2="18"/>
                    </svg>
                  </div>
                  <strong>{{ terminal.terminalName || 'Unnamed terminal' }}</strong>
                </div>
                <span class="status-indicator-pill" [class.status-indicator-pill--active]="terminal.status.toLowerCase() === 'active'" [class.status-indicator-pill--inactive]="terminal.status.toLowerCase() === 'inactive'">
                  <span class="indicator-dot"></span>
                  {{ terminal.status }}
                </span>
              </div>
              <p>
                <span class="device-pill" *ngIf="terminal.deviceId; else noCardDev" [title]="terminal.deviceId">
                  <code>{{ formatDeviceId(terminal.deviceId) }}</code>
                  <button type="button" class="device-copy-btn" title="Copy full Device ID" [attr.aria-label]="'Copy full Device ID ' + terminal.deviceId" (click)="copyDeviceId(terminal.deviceId, $event)"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg></button>
                </span>
                <ng-template #noCardDev>No device assigned</ng-template>
              </p>
              <dl><div><dt>Type</dt><dd>{{ terminal.terminalType || 'BILLING' }}</dd></div><div><dt>Updated</dt><dd class="tabular-num">{{ formatDateValue(terminal.updatedAt) }}</dd></div></dl>
              <div class="mobile-data-card__actions">
                <button class="ghost-btn small-btn" [disabled]="saving()" (click)="startEdit(terminal)">Rename</button>
                  <button class="ghost-btn small-btn" [disabled]="saving()" (click)="startRecovery(terminal)" title="Recover access using the new device ID">Recover access</button>
                <button *ngIf="terminal.status.toLowerCase() !== 'inactive'" class="ghost-btn danger-btn small-btn" [disabled]="saving()" (click)="requestDeactivate(terminal)">Deactivate</button>
                <button *ngIf="terminal.status.toLowerCase() === 'inactive' && canManageTerminals()" class="ghost-btn success-btn small-btn" [disabled]="saving()" (click)="confirmReactivate(terminal)">Reactivate</button>
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
      </section>

      <ng-template #loading>
        <div class="panel loading" role="status" aria-busy="true">
          <span class="visually-hidden">Loading terminals...</span>
          <div class="skeleton-stack">
            <div class="skeleton skeleton-row" *ngFor="let i of [1,2,3,4]; trackBy: trackByIndex"></div>
          </div>
        </div>
      </ng-template>

      <!-- Device Pairing Requests Section -->
      <section class="panel requests-panel">
        <div class="section-topbar">
          <div class="topbar-heading">
            <div class="heading-row">
              <h3>Device Pairing Requests</h3>
              <span class="counter-badge" *ngIf="pendingRequests().length">{{ pendingRequests().length }} Pending</span>
            </div>
            <p class="muted">Authorize new Android tablets and thermal billing registers seeking shop access.</p>
          </div>
          <div class="filter-pills-row">
            <button
              class="filter-pill-tactile"
              [class.filter-pill-tactile--active]="requestFilter() === 'PENDING'"
              (click)="setRequestFilter('PENDING')"
            >
              Pending ({{ pendingRequests().length }})
            </button>
            <button
              class="filter-pill-tactile"
              [class.filter-pill-tactile--active]="requestFilter() === 'ALL'"
              (click)="setRequestFilter('ALL')"
            >
              All ({{ requests().length }})
            </button>
          </div>
        </div>

        <div class="loading compact-loading" role="status" aria-busy="true" *ngIf="requestsLoading()">Loading device requests...</div>
        <app-api-state
          *ngIf="requestsError()"
          [message]="requestsError()"
          (retry)="loadRequests()"
        ></app-api-state>

        <div class="table-wrap" *ngIf="!requestsLoading() && !requestsError() && pendingOrAllRequests().length">
          <table class="data-table">
            <thead>
              <tr>
                <th>Device Model / Hardware</th>
                <th>Type</th>
                <th>Status</th>
                <th>Requested</th>
                <th>Authorization</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let req of pendingOrAllRequests(); trackBy: trackByRequestId">
                <td>
                  <div class="device-meta-cell">
                    <strong>{{ req.deviceName || req.deviceModel || 'Android Device' }}</strong>
                    <span class="device-pill" *ngIf="req.deviceId; else noReqDev" [title]="req.deviceId">
                      <code>{{ formatDeviceId(req.deviceId) }}</code>
                      <button type="button" class="device-copy-btn" title="Copy full Device ID" [attr.aria-label]="'Copy full Device ID ' + req.deviceId" (click)="copyDeviceId(req.deviceId, $event)">
                        <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                        </svg>
                      </button>
                    </span>
                    <ng-template #noReqDev><span class="muted">No device id</span></ng-template>
                  </div>
                </td>
                <td>
                  <span class="role-pill role-pill--req">{{ req.requestType || 'PAIRING' }}</span>
                </td>
                <td>
                  <span
                    class="status-indicator-pill"
                    [class.status-indicator-pill--active]="req.status.toLowerCase() === 'approved'"
                    [class.status-indicator-pill--inactive]="req.status.toLowerCase() === 'rejected'"
                    [class.status-indicator-pill--pending]="req.status.toLowerCase() === 'pending'"
                  >
                    <span class="indicator-dot"></span>
                    {{ req.status }}
                  </span>
                </td>
                <td class="tabular-num muted">{{ formatDateValue(req.requestedAt) }}</td>
                <td>
                  <div class="action-stack-row" *ngIf="req.status.toLowerCase() === 'pending'; else reqDone">
                    <button class="primary-btn small-btn" [disabled]="saving()" (click)="approve(req)">
                      <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                      Approve
                    </button>
                    <button class="ghost-btn danger-btn small-btn" [disabled]="saving()" (click)="requestReject(req)">Reject</button>
                  </div>
                  <ng-template #reqDone><span class="muted">{{ req.rejectionReason || '-' }}</span></ng-template>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="mobile-data-list" aria-label="Terminal requests">
            <article class="mobile-data-card" *ngFor="let req of pendingOrAllRequests(); trackBy: trackByRequestId">
              <div class="mobile-data-card__head">
                <strong>{{ req.deviceName || 'Unnamed device' }}</strong>
                <span class="status-indicator-pill" [class.status-indicator-pill--active]="req.status.toLowerCase() === 'approved'" [class.status-indicator-pill--inactive]="req.status.toLowerCase() === 'rejected'" [class.status-indicator-pill--pending]="req.status.toLowerCase() === 'pending'">
                  <span class="indicator-dot"></span>
                  {{ req.status }}
                </span>
              </div>
              <p>
                {{ req.deviceModel || 'Unknown model' }} ·
                <span class="device-pill" *ngIf="req.deviceId; else noReqCardDev" [title]="req.deviceId">
                  <code>{{ formatDeviceId(req.deviceId) }}</code>
                  <button type="button" class="device-copy-btn" title="Copy full Device ID" [attr.aria-label]="'Copy full Device ID ' + req.deviceId" (click)="copyDeviceId(req.deviceId, $event)"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg></button>
                </span>
                <ng-template #noReqCardDev>No device ID</ng-template>
              </p>
              <dl><div><dt>Type</dt><dd>{{ req.requestType || '-' }}</dd></div><div><dt>Requested</dt><dd class="tabular-num">{{ formatDateValue(req.requestedAt) }}</dd></div></dl>
              <div class="mobile-data-card__actions" *ngIf="req.status.toLowerCase() === 'pending'">
                <button class="primary-btn small-btn" [disabled]="saving()" (click)="approve(req)">Approve</button>
                <button class="ghost-btn danger-btn small-btn" [disabled]="saving()" (click)="requestReject(req)">Reject</button>
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

      <div class="modal-backdrop" *ngIf="recoveringTerminal() as terminal" appFocusTrap (escape)="closeRecovery()" (click)="closeRecovery()">
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
                #recoveryDeviceRef="ngModel"
                [(ngModel)]="recoveryDeviceId"
                [attr.aria-invalid]="(recoveryDeviceRef.touched && !recoveryDeviceId.trim()) || !!recoveryError()"
                [attr.aria-describedby]="recoveryError() ? 'recovery-error' : (recoveryDeviceRef.touched && !recoveryDeviceId.trim() ? 'recovery-device-id-error' : null)"
                placeholder="Enter the ID shown on the new device"
                autocomplete="off"
                autofocus
              />
            </div>
            <!-- A refusal from the server outranks the empty-field hint, so it is named first;
                 both can be on screen at once if a rejected value is cleared. -->
            <div class="field-error" id="recovery-device-id-error" *ngIf="recoveryDeviceRef.touched && !recoveryDeviceId.trim()">Enter the device ID shown on the new device.</div>
            <p class="error-text" id="recovery-error" role="alert" *ngIf="recoveryError()">{{ recoveryError() }}</p>
            <div class="modal-actions">
              <button type="button" class="ghost-btn" [disabled]="saving()" (click)="closeRecovery()">Cancel</button>
              <button
                type="button"
                class="primary-btn"
                [disabled]="saving() || !recoveryDeviceId.trim()"
                (click)="recoverTerminal()"
              >
                {{ saving() ? 'Recovering access…' : 'Recover terminal access' }}
              </button>
            </div>
          </ng-template>
        </section>
      </div>

      <div class="modal-backdrop" *ngIf="approvingRequest() as req" appFocusTrap (escape)="closeApprove()" (click)="closeApprove()">
        <section
          class="modal-box"
          role="dialog"
          aria-modal="true"
          aria-labelledby="approve-title"
          (click)="$event.stopPropagation()"
        >
          <h3 id="approve-title">Confirm device number</h3>
          <p class="muted">
            Enter the number shown on
            "{{ req.deviceName || req.deviceId || 'the device' }}" to approve it.
          </p>
          <div class="filter-group">
            <label for="approve-challenge">Number on device</label>
            <input
              id="approve-challenge"
              class="field-control"
              #challengeRef="ngModel"
              [(ngModel)]="challengeCode"
              [attr.aria-invalid]="(challengeRef.touched && !challengeCode.trim()) || !!challengeError()"
              [attr.aria-describedby]="challengeError() ? 'challenge-error' : (challengeRef.touched && !challengeCode.trim() ? 'challenge-code-error' : null)"
              inputmode="numeric"
              maxlength="2"
              placeholder="e.g. 52"
              autocomplete="off"
              autofocus
            />
            <div class="field-error" id="challenge-code-error" *ngIf="challengeRef.touched && !challengeCode.trim()">Enter the number shown on the device.</div>
          </div>
          <p class="muted" *ngIf="challengeSecondsLeft() as _left">Expires in {{ challengeCountdown() }}</p>
          <p class="error-text" id="challenge-error" role="alert" *ngIf="challengeError()">{{ challengeError() }}</p>
          <div class="modal-actions">
            <button type="button" class="ghost-btn" [disabled]="saving()" (click)="closeApprove()">Cancel</button>
            <button
              type="button"
              class="primary-btn"
              [disabled]="saving() || !challengeCode.trim()"
              (click)="submitApprove()"
            >
              {{ saving() ? 'Approving...' : 'Approve device' }}
            </button>
          </div>
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
    .small-btn {
      padding: var(--kb-space-1) 10px;
      font-size: var(--kb-text-xs);
    }
    .danger-btn {
      color: var(--kb-color-danger);
      border-color: rgba(var(--kb-color-danger-rgb), 0.25);
    }
    .danger-btn:hover {
      background: rgba(var(--kb-color-danger-rgb), 0.06);
    }
    .success-btn {
      color: var(--kb-color-success);
      border-color: rgba(var(--kb-color-success-rgb), 0.25);
    }
    .success-btn:hover {
      background: rgba(var(--kb-color-success-rgb), 0.06);
    }
    /* The fleet strip itself is the shared <app-kpi-row>; only the over-limit
       banner (which no other page has) lives here. */
    .fleet-limit-banner {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
      padding: var(--kb-space-2) var(--kb-space-3);
      margin-top: var(--kb-space-2);
      border-radius: var(--kb-radius-md);
      border: 1px solid rgba(var(--kb-color-warning-rgb), 0.4);
      background: rgba(var(--kb-color-warning-rgb), 0.08);
      color: var(--kb-color-warning);
      font-size: var(--kb-text-sm);
      font-weight: 500;
      line-height: 1.4;
    }
    .tabular-num { font-variant-numeric: tabular-nums; }

    /* Section Topbars */
    .section-topbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--kb-space-3);
      padding-bottom: var(--kb-space-3);
      margin-bottom: var(--kb-space-3);
      border-bottom: 1px solid var(--kb-color-border);
      flex-wrap: wrap;
    }
    .topbar-heading h3 {
      margin: 0;
      font-size: var(--kb-text-xl);
      font-weight: 600;
    }
    .heading-row {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
    }
    .counter-badge {
      font-size: var(--kb-text-2xs);
      font-weight: 600;
      padding: 2px var(--kb-space-2);
      border-radius: var(--kb-radius-full);
      background: rgba(var(--kb-color-warning-rgb), 0.12);
      color: var(--kb-color-warning);
      border: 1px solid rgba(var(--kb-color-warning-rgb), 0.3);
    }
    .filter-pills-row {
      display: flex;
      gap: 6px;
    }
    .filter-pill-tactile {
      border: 1px solid var(--kb-color-border);
      background: var(--kb-color-surface);
      border-radius: var(--kb-radius-full);
      padding: var(--kb-space-1) var(--kb-space-3);
      font-size: var(--kb-text-xs);
      font-weight: 500;
      color: var(--kb-color-muted-foreground);
      cursor: pointer;
      transition: all 150ms ease;
    }
    .filter-pill-tactile:active { transform: scale(0.97); }
    .filter-pill-tactile--active {
      background: var(--kb-color-surface-2);
      border-color: var(--kb-color-primary);
      color: var(--kb-color-link);
      font-weight: 600;
    }

    /* Terminal Row Elements */
    .terminal-name-cell {
      display: flex;
      align-items: center;
      gap: var(--kb-space-2);
    }
    .terminal-icon {
      width: 28px;
      height: 28px;
      border-radius: var(--kb-radius-sm);
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--kb-color-surface-2);
      color: var(--kb-color-muted-foreground);
      flex-shrink: 0;
    }
    .terminal-icon--active {
      color: var(--kb-color-link);
      background: rgba(var(--kb-color-primary-rgb), 0.08);
    }
    .role-pill {
      display: inline-block;
      padding: 2px var(--kb-space-2);
      border-radius: var(--kb-radius-sm);
      font-size: var(--kb-text-2xs);
      font-weight: 600;
      letter-spacing: 0.04em;
    }
    .role-pill--billing {
      background: rgba(var(--kb-color-primary-rgb), 0.08);
      color: var(--kb-color-link);
      border: 1px solid rgba(var(--kb-color-primary-rgb), 0.2);
    }
    .role-pill--kot {
      background: rgba(var(--kb-color-primary-rgb), 0.08);
      color: var(--kb-color-link);
      border: 1px solid rgba(var(--kb-color-primary-rgb), 0.2);
    }
    .role-pill--req {
      background: var(--kb-color-surface-2);
      color: var(--kb-color-foreground);
      border: 1px solid var(--kb-color-border);
    }
    .status-indicator-pill {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 2px var(--kb-space-2);
      border-radius: var(--kb-radius-full);
      font-size: var(--kb-text-2xs);
      font-weight: 600;
    }
    .indicator-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: currentColor;
    }
    .status-indicator-pill--active {
      color: var(--kb-color-success);
      background: rgba(var(--kb-color-success-rgb), 0.08);
      border: 1px solid rgba(var(--kb-color-success-rgb), 0.25);
    }
    .status-indicator-pill--inactive {
      color: var(--kb-color-muted-foreground);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
    }
    .status-indicator-pill--pending {
      color: var(--kb-color-warning);
      background: rgba(var(--kb-color-warning-rgb), 0.08);
      border: 1px solid rgba(var(--kb-color-warning-rgb), 0.25);
    }
    .action-stack-row {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }
    .device-meta-cell {
      display: flex;
      flex-direction: column;
      gap: var(--kb-space-1);
    }
    .primary-badge {
      display: inline-block;
      margin-left: 0.35rem;
      padding: 0.08rem 0.4rem;
      background: rgba(var(--kb-color-primary-rgb), 0.1);
      color: var(--kb-color-link);
      border: 1px solid rgba(var(--kb-color-primary-rgb), 0.3);
      border-radius: var(--kb-radius-full);
      font-size: var(--kb-text-2xs);
      font-weight: 700;
      letter-spacing: 0.03em;
      text-transform: uppercase;
    }
    .row-actions { display: flex; flex-wrap: wrap; gap: var(--kb-space-2); margin-top: var(--kb-space-2); }
    .recovery-dialog { width: min(100%, 520px); }
    .credential-box {
      display: grid;
      gap: var(--kb-space-2);
      margin: var(--kb-space-4) 0;
      padding: var(--kb-space-4);
      background: var(--kb-color-surface);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
    }
    .credential-box code {
      display: block;
      overflow-wrap: anywhere;
      color: var(--kb-color-foreground);
      font-size: 0.95rem;
      line-height: 1.55;
      font-family: var(--kb-font-mono);
      user-select: all;
      font-variant-numeric: tabular-nums;
    }
    .credential-label {
      color: var(--kb-color-link);
      font-size: var(--kb-text-xs);
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }
    .recovery-warning {
      margin: 0;
      padding: var(--kb-space-3) var(--kb-space-4);
      color: var(--kb-color-error);
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg);
      line-height: 1.5;
      font-size: var(--kb-text-md);
    }
    .error-text { margin: var(--kb-space-3) 0 0; color: var(--kb-color-error); font-weight: 600; font-size: var(--kb-text-md); }
    .modal-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: var(--kb-space-2); margin-top: var(--kb-space-4); }
    .device-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.15rem 0.45rem;
      background: var(--kb-color-surface-2);
      border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-sm);
      font-family: var(--kb-font-mono);
      font-size: var(--kb-text-sm);
    }
    .device-copy-btn {
      background: none;
      border: none;
      cursor: pointer;
      padding: 0;
      color: var(--kb-color-muted-foreground);
      display: inline-flex;
      align-items: center;
      transition: color 0.15s ease, transform 0.15s ease;
    }
    .device-copy-btn:hover { color: var(--kb-color-foreground); }
    .device-copy-btn:active { transform: scale(0.9); }
    .terminals-panel, .requests-panel {
      margin-bottom: var(--kb-space-4);
    }
    @media (max-width: 480px) {
      .modal-actions button { width: 100%; }
      .credential-box { padding: 0.85rem; }
    }
  `]
})
export class TerminalsPageComponent implements OnDestroy {
  private readonly api = inject(BusinessApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private recoveryTrigger: HTMLElement | null = null;
  private readonly recoveryKeydownHandler = (event: KeyboardEvent) => this.handleRecoveryKeydown(event);

  readonly terminals = signal<BusinessTerminal[]>([]);
  readonly requests = signal<TerminalRequest[]>([]);
  readonly maxActiveTerminals = 5;
  readonly fleetOverLimit = computed(() => this.activeTerminalsCount > this.maxActiveTerminals);
  readonly terminalsLoading = signal(true);
  readonly requestsLoading = signal(true);
  readonly terminalsError = signal('');
  readonly requestsError = signal('');
  readonly saving = signal(false);
  readonly reactivatingTerminal = signal<BusinessTerminal | null>(null);
  readonly deactivatingTerminal = signal<BusinessTerminal | null>(null);
  readonly rejectingRequest = signal<TerminalRequest | null>(null);
  readonly approvingRequest = signal<TerminalRequest | null>(null);
  readonly challengeError = signal<string | null>(null);
  readonly challengeNow = signal(Date.now());
  challengeCode = '';
  private challengeTimer: ReturnType<typeof setInterval> | null = null;
  readonly recoveringTerminal = signal<BusinessTerminal | null>(null);
  readonly recoveryResult = signal<RecoverTerminalResponse | null>(null);
  readonly recoveryError = signal('');

  readonly requestFilter = signal<'PENDING' | 'ALL'>('PENDING');
  readonly editingId = signal<number | null>(null);
  editName = '';
  recoveryDeviceId = '';

  get activeTerminalsCount(): number {
    return this.terminals().filter((t) => t.isActive && t.status?.toLowerCase() === 'active').length;
  }

  readonly pendingRequests = computed(() =>
    this.requests().filter((r) => r.status?.toLowerCase() === 'pending')
  );

  /** The fleet strip, expressed once in the shared KPI anatomy. */
  readonly fleetKpis = computed<KpiItem[]>(() => {
    const pending = this.pendingRequests().length;
    const overLimit = this.fleetOverLimit();
    return [
      {
        label: 'Registered terminals',
        value: this.terminals().length,
        caption: 'Android registers on this shop',
      },
      {
        label: 'Active counter terminals',
        value: this.activeTerminalsCount,
        unit: `/ ${this.maxActiveTerminals} max`,
        tone: overLimit ? 'warning' : 'success',
        alert: overLimit,
        caption: overLimit ? 'Plan limit reached' : 'Currently billing at the counter',
      },
      {
        label: 'Pairing requests',
        value: pending,
        unit: pending === 1 ? 'request' : 'requests',
        tone: pending > 0 ? 'warning' : 'default',
        alert: pending > 0,
        caption: pending > 0 ? 'Waiting for your approval' : 'Nothing waiting',
      },
    ];
  });

  readonly pendingOrAllRequests = computed(() => {
    if (this.requestFilter() === 'ALL') return this.requests();
    return this.pendingRequests();
  });

  constructor() {
    this.reload();
  }

  ngOnDestroy(): void {
    if (this.challengeTimer) {
      clearInterval(this.challengeTimer);
      this.challengeTimer = null;
    }
    // Guarantee the recovery dialog's scroll-lock + key handler never leak when
    // navigating away without clicking Close.
    document.removeEventListener('keydown', this.recoveryKeydownHandler);
    document.body.style.overflow = '';
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

  setPrimary(terminal: BusinessTerminal): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.api.setPrimaryTerminal(terminal.id).subscribe({
      next: () => {
        this.terminals.update((list) =>
          list.map((t) => ({ ...t, isPrimary: t.id === terminal.id }))
        );
        this.saving.set(false);
        this.notify('Primary device updated');
      },
      error: (err) => {
        this.saving.set(false);
        const msg = this.errMsg(err, 'Could not set primary device');
        if (msg.includes('TERMINAL_NOT_ACTIVE')) {
          this.fail('Only active terminals can be primary. Reload and try again.');
        } else {
          this.fail(msg);
        }
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
    // Number-matching: if the device is showing a code, collect it first.
    if (req.challengeRequired) {
      this.challengeCode = '';
      this.challengeError.set(null);
      this.approvingRequest.set(req);
      this.challengeNow.set(Date.now());
      this.challengeTimer = setInterval(() => this.challengeNow.set(Date.now()), 1000);
      return;
    }
    this.sendApprove(req, undefined);
  }

  closeApprove(): void {
    if (this.challengeTimer) {
      clearInterval(this.challengeTimer);
      this.challengeTimer = null;
    }
    this.approvingRequest.set(null);
    this.challengeCode = '';
    this.challengeError.set(null);
  }

  submitApprove(): void {
    const req = this.approvingRequest();
    if (!req) return;
    const code = this.challengeCode.trim();
    if (!/^\d{1,2}$/.test(code)) {
      this.challengeError.set('Enter the 1–2 digit number shown on the device.');
      return;
    }
    this.sendApprove(req, code.padStart(2, '0'));
  }

  /** Remaining seconds on the challenge, or null when no live challenge. */
  challengeSecondsLeft(): number | null {
    const req = this.approvingRequest();
    if (!req?.challengeExpiresAt) return null;
    return Math.max(0, Math.round((req.challengeExpiresAt - this.challengeNow()) / 1000));
  }

  challengeCountdown(): string {
    const s = this.challengeSecondsLeft();
    if (s === null) return '';
    const m = Math.floor(s / 60);
    return `${m}:${(s % 60).toString().padStart(2, '0')}`;
  }

  private sendApprove(req: TerminalRequest, challengeCode: string | undefined): void {
    this.saving.set(true);
    this.api.approveTerminalRequest(req.id, challengeCode).subscribe({
      next: () => {
        this.requests.update((list) =>
          list.map((r) => (r.id === req.id ? { ...r, status: 'APPROVED' } : r))
        );
        this.saving.set(false);
        this.closeApprove();
        this.notify('Device request approved');
      },
      error: (err) => {
        this.saving.set(false);
        const code = (err as { headers?: { get(name: string): string | null } })?.headers?.get('X-Error-Code');
        const message = this.challengeErrorMessage(code) ?? this.errMsg(err, 'Approval failed');
        if (this.approvingRequest()) {
          this.challengeError.set(message);
        } else {
          this.fail(message);
        }
      }
    });
  }

  private challengeErrorMessage(code: string | null | undefined): string | null {
    switch (code) {
      case 'RECOVERY_CHALLENGE_MISMATCH':
        return 'That number does not match the one on the device. Check and try again.';
      case 'RECOVERY_CHALLENGE_EXPIRED':
        return 'The number has expired. Ask the device to generate a new one.';
      case 'RECOVERY_CHALLENGE_LOCKED':
        return 'Too many wrong attempts. Ask the device to generate a new number.';
      default:
        return null;
    }
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
    return role === 'OWNER';
  }

  trackByIndex = (_: number, __: unknown) => _;
  trackByTerminalId = (_: number, terminal: BusinessTerminal) => terminal.id;
  trackByRequestId = (_: number, req: TerminalRequest) => req.id;

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

  formatDeviceId(deviceId: string | null | undefined): string {
    if (!deviceId) return '-';
    const trimmed = deviceId.trim();
    if (trimmed.length > 14) {
      return trimmed.substring(0, 8) + '...' + trimmed.substring(trimmed.length - 4);
    }
    return trimmed;
  }

  async copyDeviceId(deviceId: string | null | undefined, event?: Event): Promise<void> {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    if (!deviceId) return;
    try {
      await navigator.clipboard.writeText(deviceId);
      this.notify('Device ID copied to clipboard');
    } catch {
      this.toast.show('Could not copy automatically.', 'error');
    }
  }
}


