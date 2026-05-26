import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="skeleton-container" [style.height]="height" [style.width]="width" [class.card]="variant === 'card'" [class.circle]="variant === 'circle'">
      <div class="skeleton-inner" [class.rounded]="rounded && variant !== 'circle'"></div>
    </div>
  `,
  styles: [`
    .skeleton-container {
      display: block;
      overflow: hidden;
    }
    .skeleton-container.card {
      border-radius: var(--radius-xl);
      border: 1px solid var(--line);
    }
    .skeleton-container.circle {
      border-radius: 50%;
    }
    .skeleton-inner {
      width: 100%;
      height: 100%;
      background: linear-gradient(90deg, var(--line) 25%, var(--line-strong) 37%, var(--line) 63%);
      background-size: 200% 100%;
      animation: shimmer 1.4s ease infinite;
    }
    .skeleton-inner.rounded {
      border-radius: var(--radius-sm);
    }
    @keyframes shimmer {
      0% { background-position: -200% 0; }
      100% { background-position: 200% 0; }
    }
  `]
})
export class SkeletonComponent {
  @Input() width = '100%';
  @Input() height = '16px';
  @Input() variant: 'text' | 'card' | 'circle' = 'text';
  @Input() rounded = true;
}
