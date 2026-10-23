import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { IconComponent } from '../../shared/icon.component';

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
  imports: [CommonModule, RouterLink, RouterLinkActive, IconComponent],
  host: { class: 'kb-bottom-action-bar' },
  template: `
    <a
      *ngFor="let item of items; trackBy: trackByItem"
      class="action-item"
      [routerLink]="item.route"
      routerLinkActive="action-item--active"
      [attr.aria-label]="item.label"
    >
      <span class="icon">
        <kb-icon *ngIf="item.iconKey" [name]="item.iconKey" [size]="20"></kb-icon>
        <span *ngIf="!item.iconKey">{{ item.icon }}</span>
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
      background: var(--kb-color-surface);
      border-top: 1px solid var(--kb-color-border);
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
      color: var(--kb-color-muted-foreground);
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
      color: var(--kb-color-primary);
      background: rgba(var(--kb-color-primary-rgb), 0.1);
    }
    .kb-badge {
      position: absolute;
      top: 2px;
      right: calc(50% - 18px);
      background: var(--kb-color-danger);
      color: var(--kb-color-surface);
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