import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminApiService } from '../../core/services/admin-api.service';
import { ToastService } from '../../core/services/toast.service';
import { FeatureFlagAdminItem, FeatureFlagAuditItem } from '../../core/models/api.models';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-feature-flags-page',
  standalone: true,
  imports: [CommonModule, FormsModule, EmptyStateComponent],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Feature Flags</h2>
        <p class="muted">Control rollout of ported v2 features. The kill switch dominates every override (first rollback step).</p>
        <div class="hero-meta">
          <span class="chip">{{ flags().length }} Flags</span>
          <span class="chip success">{{ enabledCount() }} Globally Effective</span>
        </div>
      </section>

      <div class="toolbar">
        <div>
          <h3>Flag State</h3>
          <p class="muted">Effective state shown is what a restaurant <strong>without an override</strong> resolves to.</p>
        </div>
        <button class="ghost-btn" (click)="reload()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="!loading(); else loadingBlock">
        <div class="alert error load-error" role="alert" *ngIf="error()">
          <span>{{ error() }}</span>
          <button type="button" class="ghost-btn" (click)="reload()">Try again</button>
        </div>
        <table class="data-table" *ngIf="!error() && flags().length; else emptyBlock">
          <thead>
            <tr>
              <th>Flag</th>
              <th>Kill Switch</th>
              <th>Default</th>
              <th>Effective</th>
              <th>Updated</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let flag of flags()">
              <td>
                <strong>{{ flag.flagKey }}</strong>
                <div class="muted">{{ flag.description }}</div>
              </td>
              <td>
                <label class="switch-label">
                  <input type="checkbox" [checked]="flag.killSwitched" (change)="toggleKillSwitch(flag, $event)" [disabled]="saving()" />
                  <span class="switch-track"></span>
                </label>
                <span class="muted" [class.danger-text]="flag.killSwitched">On</span>
              </td>
              <td>
                <label class="switch-label">
                  <input type="checkbox" [checked]="flag.defaultEnabled" (change)="toggleDefault(flag, $event)" [disabled]="saving()" />
                  <span class="switch-track"></span>
                </label>
                <span class="muted">{{ flag.defaultEnabled ? 'On' : 'Off' }}</span>
              </td>
              <td>
                <span class="chip" [class.success]="flag.effectiveState" [class.danger]="!flag.effectiveState">
                  {{ flag.effectiveState ? 'ENABLED' : 'DISABLED' }}
                </span>
              </td>
              <td>{{ formatDateValue(flag.updatedAt) }}</td>
              <td>
                <button class="ghost-btn" [disabled]="saving()" (click)="loadAudit(flag)">History</button>
              </td>
            </tr>
          </tbody>
        </table>
        <ng-template #emptyBlock>
          <div class="empty-state">
            <p>No feature flags configured.</p>
          </div>
        </ng-template>
      </div>

      <ng-template #loadingBlock>
        <div class="panel loading">Loading feature flags...</div>
      </ng-template>

      <div class="panel table-wrap audit-panel" *ngIf="selectedFlag(); else noAudit">
        <div class="toolbar">
          <div>
            <h3>Change History — {{ selectedFlag() }}</h3>
            <p class="muted">Every mutation writes an audit row (kill switch, default, override).</p>
          </div>
          <button class="ghost-btn" (click)="closeAudit()">Close</button>
        </div>
        <table class="data-table" *ngIf="audit().length; else noAuditRows">
          <thead>
            <tr>
              <th>When</th>
              <th>Scope</th>
              <th>Restaurant</th>
              <th>From</th>
              <th>To</th>
              <th>Actor</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of audit()">
              <td>{{ formatDateValue(row.changedAt) }}</td>
              <td>
                <span class="chip">{{ row.scope }}</span>
              </td>
              <td>{{ row.restaurantId ?? '-' }}</td>
              <td class="muted">{{ row.previousState ?? '-' }}</td>
              <td>
                <span class="chip" [class.success]="row.newState === 'ENABLED'" [class.danger]="row.newState === 'DISABLED'">
                  {{ row.newState }}
                </span>
              </td>
              <td>{{ row.actorUsername || (row.actorUserId !== null ? 'user#' + row.actorUserId : '-') }}</td>
            </tr>
          </tbody>
        </table>
        <ng-template #noAuditRows>
          <div class="empty-state"><p>No changes recorded for this flag yet.</p></div>
        </ng-template>
      </div>

      <ng-template #noAudit>
        <div class="panel hint-panel">
          <p class="muted">Select <strong>History</strong> on a flag to see its change log.</p>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .switch-label { display: inline-flex; align-items: center; gap: 0.5rem; cursor: pointer; }
    .switch-label input { display: none; }
    .switch-track {
      width: 40px; height: 22px; border-radius: 12px; background: #d9cdb6;
      position: relative; transition: background 0.2s ease; display: inline-block;
    }
    .switch-track::after {
      content: ''; position: absolute; top: 2px; left: 2px; width: 18px; height: 18px;
      border-radius: 50%; background: #fff; transition: transform 0.2s ease;
    }
    .switch-label input:checked + .switch-track { background: #1d7b5f; }
    .switch-label input:checked + .switch-track::after { transform: translateX(18px); }
    .switch-label input:disabled + .switch-track { opacity: 0.5; cursor: not-allowed; }
    .danger-text { color: #a6372f; }
    .audit-panel { margin-top: 1.25rem; }
    .hint-panel { padding: 1rem 1.25rem; }
  `]
})
export class FeatureFlagsPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly toast = inject(ToastService);

  readonly flags = signal<FeatureFlagAdminItem[]>([]);
  readonly audit = signal<FeatureFlagAuditItem[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly saving = signal(false);
  readonly selectedFlag = signal<string | null>(null);

  readonly enabledCount = () => this.flags().filter((f) => f.effectiveState).length;

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.getFeatureFlags().subscribe({
      next: (data) => {
        this.flags.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.errMsg(err, 'Failed to load feature flags.'));
      }
    });
  }

  toggleKillSwitch(flag: FeatureFlagAdminItem, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.saving.set(true);
    this.api.setFeatureFlagKillSwitch(flag.flagKey, checked).subscribe({
      next: () => {
        this.saving.set(false);
        this.notify(`Kill switch ${checked ? 'enabled' : 'disabled'} for ${flag.flagKey}`);
        this.patchFlag(flag.flagKey, (f) => ({ ...f, killSwitched: checked, effectiveState: checked ? false : f.effectiveState }));
      },
      error: (err) => this.fail(this.errMsg(err, 'Failed to update kill switch.'))
    });
  }

  toggleDefault(flag: FeatureFlagAdminItem, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.saving.set(true);
    this.api.setFeatureFlagDefault(flag.flagKey, checked).subscribe({
      next: () => {
        this.saving.set(false);
        this.notify(`Default ${checked ? 'enabled' : 'disabled'} for ${flag.flagKey}`);
        this.patchFlag(flag.flagKey, (f) => ({ ...f, defaultEnabled: checked }));
      },
      error: (err) => this.fail(this.errMsg(err, 'Failed to update default state.'))
    });
  }

  loadAudit(flag: FeatureFlagAdminItem): void {
    this.selectedFlag.set(flag.flagKey);
    this.audit.set([]);
    this.api.getFeatureFlagAudit(flag.flagKey).subscribe({
      next: (rows) => this.audit.set(rows),
      error: (err) => {
        this.audit.set([]);
        this.fail(this.errMsg(err, 'Failed to load change history.'));
      }
    });
  }

  closeAudit(): void {
    this.selectedFlag.set(null);
    this.audit.set([]);
  }

  private patchFlag(flagKey: string, transform: (f: FeatureFlagAdminItem) => FeatureFlagAdminItem): void {
    this.flags.update((list) => list.map((f) => (f.flagKey === flagKey ? transform(f) : f)));
  }

  private notify(message: string): void {
    this.toast.show(message, 'success');
  }

  private fail(message: string): void {
    this.saving.set(false);
    this.toast.show(message, 'error');
  }

  private errMsg(err: unknown, fallback: string): string {
    const e = err as { error?: { error?: string; message?: string } } | null;
    return e?.error?.message || e?.error?.error || fallback;
  }

  formatDateValue(value: number | null): string { return formatDate(value); }
}