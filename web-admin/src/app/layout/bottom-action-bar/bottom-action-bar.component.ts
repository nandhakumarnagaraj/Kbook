import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

export interface BottomActionBarItem {
  label: string;
  iconKey?: string;
  icon?: string;
  route?: string;
  badge?: string;
}

@Component({
  selector: 'kb-bottom-action-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  host: { class: 'kb-bottom-action-bar' },
  template: `
    <a
      *ngFor="let item of items; trackBy: trackByItem"
      class="action-item"
      [routerLink]="item.route"
      routerLinkActive="action-item--active"
      [attr.aria-label]="item.label"
    >
      <span class="icon" [ngSwitch]="item.iconKey">
        <!-- Dashboard -->
        <svg *ngSwitchCase="'dashboard'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/></svg>
        <!-- Orders -->
        <svg *ngSwitchCase="'orders'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
        <!-- Payments -->
        <svg *ngSwitchCase="'payments'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>
        <!-- Reports -->
        <svg *ngSwitchCase="'reports'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>
        <!-- Terminals -->
        <svg *ngSwitchCase="'terminals'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="2" width="16" height="20" rx="2" ry="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>
        <!-- Settings -->
        <svg *ngSwitchCase="'settings'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
        <!-- KDS / kitchen -->
        <svg *ngSwitchCase="'kitchen'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 21a1 1 0 0 0 1-1v-5.35c0-.457.316-.844.727-1.041a4 4 0 0 0-2.134-7.589a5 5 0 0 0-9.186 0a4 4 0 0 0-2.134 7.588c.411.198.727.585.727 1.041V20a1 1 0 0 0 1 1ZM6 17h12"/></svg>
        <span *ngSwitchDefault>{{ item.icon }}</span>
      </span>
      <span class="label">{{ item.label }}</span>
      <span *ngIf="item.badge" class="kb-badge">{{ item.badge }}</span>
    </a>
  `,
  styles: [`
    :host {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      z-index: var(--kb-z-topbar, 30);
      display: flex;
      justify-content: space-around;
      align-items: stretch;
      background: var(--kb-color-surface, #FFFFFF);
      border-top: 1px solid var(--kb-color-border, #EFE9DE);
      padding: var(--kb-space-2, 8px) var(--kb-space-2, 8px) max(var(--kb-space-2, 8px), env(safe-area-inset-bottom));
      box-shadow: 0 -4px 16px rgba(42, 31, 23, 0.08);
      gap: var(--kb-space-1, 4px);
    }
    .action-item {
      position: relative;
      flex: 1 1 0;
      min-width: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 3px;
      padding: 6px 2px;
      border-radius: 12px;
      color: var(--kb-color-muted-foreground, #6B655C);
      text-decoration: none;
      font-size: 0.68rem;
      font-weight: 600;
      line-height: 1.2;
      min-height: 48px;
      cursor: pointer;
      transition: color 150ms var(--ease-out, ease-out), background-color 150ms var(--ease-out, ease-out);
    }
    .action-item .icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: inherit;
    }
    .action-item .label {
      text-align: center;
      width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .action-item:active { transform: scale(0.95); }
    .action-item--active {
      color: var(--pos-purple, #5D45FD);
      background: rgba(93, 69, 253, 0.1);
    }
    .kb-badge {
      position: absolute;
      top: 2px;
      right: calc(50% - 18px);
      background: var(--kb-color-danger, #C0392B);
      color: #FFFFFF;
      font-size: 0.6rem;
      font-weight: 700;
      line-height: 1;
      padding: 3px 5px;
      border-radius: 999px;
      min-width: 16px;
      text-align: center;
    }
  `]
})
export class BottomActionBarComponent {
  @Input() items: BottomActionBarItem[] = [];

  trackByItem = (_: number, item: BottomActionBarItem) => item.route ?? item.label;
}