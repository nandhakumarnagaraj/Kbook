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
      <section class="panel page-hero">
        <h2>Platform Dashboard</h2>
        <p class="muted">Cross-business operational summary with a cleaner scan path for revenue, businesses, and refund performance.</p>
        <div class="hero-meta">
          <span class="chip">Admin Overview</span>
          <span class="chip">Operational Snapshot</span>
          <span class="chip success">Revenue and Refunds</span>
        </div>
      </section>

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
        <article class="panel stat-card">
          <h3>Refunded Orders</h3>
          <strong>{{ data.refundedOrders }}</strong>
        </article>
        <article class="panel stat-card">
          <h3>Refunded Amount</h3>
          <strong>{{ data.refundedAmountFormatted }}</strong>
        </article>
        <section class="panel soft-section" style="grid-column: 1 / -1;">
          <div class="section-head">
            <div>
              <h3>Suggested Focus</h3>
              <p class="muted">Use these checks to keep the platform view actionable.</p>
            </div>
          </div>
          <div class="suggestion-grid">
            <article class="suggestion-card">
              <h3>Audit Low Activity Stores</h3>
              <p>Compare live storefronts against total businesses and review stores that are configured but not active.</p>
              <span class="chip warn">Good weekly check</span>
            </article>
            <article class="suggestion-card">
              <h3>Watch Refund Drift</h3>
              <p>Track refunded orders beside total revenue so payment issues surface before they become support load.</p>
              <span class="chip danger">Risk signal</span>
            </article>
            <article class="suggestion-card">
              <h3>Review Staffing Spread</h3>
              <p>Large business growth with flat staff counts is a quick cue to verify onboarding and access setup.</p>
              <span class="chip success">Ops hygiene</span>
            </article>
          </div>
        </section>
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
        totalRevenueFormatted: formatCurrency(summary.totalRevenue),
        refundedAmountFormatted: formatCurrency(summary.refundedAmount)
      }))
    )
  );
}
