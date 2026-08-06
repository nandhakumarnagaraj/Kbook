import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface BreadcrumbItem {
  label: string;
  path?: string;
}

@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterModule, MatIconModule, MatTooltipModule],
  template: `
    <nav class="breadcrumb" aria-label="Breadcrumb navigation">
      <a class="crumb home-link" routerLink="/admin/dashboard" matTooltip="Home">
        <mat-icon aria-hidden="true">home</mat-icon>
      </a>
      <mat-icon class="separator" aria-hidden="true">chevron_right</mat-icon>
      <ng-container *ngFor="let crumb of crumbs; let last = last; let i = index">
        <a class="crumb" *ngIf="crumb.path && !last" [routerLink]="crumb.path">
          {{ crumb.label }}
        </a>
        <span class="crumb current" *ngIf="last || !crumb.path">
          {{ crumb.label }}
        </span>
        <mat-icon class="separator" aria-hidden="true" *ngIf="!last">chevron_right</mat-icon>
      </ng-container>
    </nav>
  `,
  styles: [`
    .breadcrumb {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 0;
      margin: 0 0 16px;
      font-size: 0.8rem;
      flex-wrap: wrap;
    }
    .crumb {
      color: var(--muted);
      text-decoration: none;
      font-weight: 500;
      padding: 4px 6px;
      border-radius: var(--radius-sm);
      transition: color 0.2s ease, background 0.2s ease;
      white-space: nowrap;
    }
    .crumb:hover {
      color: var(--brand);
      background: var(--brand-soft);
    }
    .crumb.current {
      color: var(--ink);
      font-weight: 700;
      cursor: default;
    }
    .crumb.home-link {
      display: flex;
      align-items: center;
      padding: 4px;
    }
    .crumb.home-link mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
    .separator {
      font-size: 16px;
      width: 16px;
      height: 16px;
      color: var(--muted);
      opacity: 0.5;
    }
  `]
})
export class BreadcrumbComponent {
  @Input() crumbs: BreadcrumbItem[] = [];
}
