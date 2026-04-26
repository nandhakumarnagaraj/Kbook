import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { formatCurrency } from '../../shared/formatters';

@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <div class="section-head">
        <div>
          <h2>Platform Dashboard</h2>
          <p class="muted">Cross-business Phase 1 operational summary.</p>
        </div>
      </div>

      <div class="stats-grid" *ngIf="summary() as data; else loading">
        <article class="panel stat-card">
          <h3>Total Businesses</h3>
          <strong>{{ data.totalBusinesses }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Live Storefronts</h3>
          <strong>{{ data.liveBusinesses }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Total Staff</h3>
          <strong>{{ data.totalStaff }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Total Orders</h3>
          <strong>{{ data.totalOrders }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Total Revenue</h3>
          <strong>{{ data.totalRevenueFormatted }}</strong>
        </article>
      </div>

      <ng-template #loading>
        <div class="panel loading">Loading platform dashboard...</div>
      </ng-template>
    </div>
  `
})
export class PlatformDashboardPageComponent {
  private readonly api = inject(AdminApiService);

  readonly summary = toSignal(
    this.api.getDashboardSummary().pipe(
      map((summary) => ({
        ...summary,
        totalRevenueFormatted: formatCurrency(summary.totalRevenue)
      }))
    )
  );
}
