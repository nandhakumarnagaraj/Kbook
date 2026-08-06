import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { BreadcrumbComponent } from './breadcrumb.component';
import { ErrorStateComponent } from './error-state.component';
import { EmptyStateComponent } from './empty-state.component';
import { SkeletonComponent } from './skeleton.component';

export interface BreadcrumbItem {
  label: string;
  path?: string;
}

@Component({
  selector: 'app-dashboard-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    BreadcrumbComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    SkeletonComponent
  ],
  template: `
    <div class="shell-container">
      <div class="shell-header">
        <div class="shell-header-left">
          <app-breadcrumb *ngIf="crumbs.length > 0" [crumbs]="crumbs" />
          <h1 class="shell-title">{{ title }}</h1>
          <p class="shell-subtitle" *ngIf="subtitle">{{ subtitle }}</p>
        </div>
        <div class="shell-header-right">
          <ng-content select="[header-actions]"></ng-content>
        </div>
      </div>

      @if (loading) {
        <div class="shell-loading">
          <app-skeleton variant="card" height="200px" />
          <app-skeleton variant="card" height="120px" />
          <app-skeleton variant="card" height="120px" />
        </div>
      } @else if (error) {
        <app-error-state
          [title]="errorTitle"
          [description]="error"
          [retryable]="retryable"
          (retry)="retry.emit()"
        />
      } @else if (empty) {
        <app-empty-state
          [icon]="emptyIcon"
          [title]="emptyTitle"
          [description]="emptyDescription"
        >
          <ng-content select="[empty-actions]"></ng-content>
        </app-empty-state>
      } @else {
        <ng-content></ng-content>
      }
    </div>
  `,
  styles: [`
    .shell-container {
      display: flex;
      flex-direction: column;
      gap: 24px;
      padding: 24px;
      min-height: 100%;
    }

    .shell-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }

    .shell-header-left {
      flex: 1;
      min-width: 0;
    }

    .shell-title {
      font-size: 1.5rem;
      font-weight: 800;
      color: var(--ink);
      margin: 4px 0 0;
      letter-spacing: -0.3px;
    }

    .shell-subtitle {
      font-size: 0.85rem;
      color: var(--muted);
      margin: 4px 0 0;
    }

    .shell-loading {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    @media (max-width: 600px) {
      .shell-container { padding: 16px; gap: 16px; }
      .shell-title { font-size: 1.25rem; }
    }

    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after { animation-duration: 0.01ms !important; animation-iteration-count: 1 !important; transition-duration: 0.01ms !important; }
    }
  `]
})
export class DashboardShellComponent {
  @Input() title = '';
  @Input() subtitle = '';
  @Input() crumbs: BreadcrumbItem[] = [];
  @Input() loading = false;
  @Input() error = '';
  @Input() errorTitle = 'Something went wrong';
  @Input() retryable = true;
  @Input() empty = false;
  @Input() emptyIcon = 'dashboard';
  @Input() emptyTitle = 'No data yet';
  @Input() emptyDescription = '';
  @Output() retry = new EventEmitter<void>();
}
