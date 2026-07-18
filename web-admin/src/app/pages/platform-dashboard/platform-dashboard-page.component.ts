import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, Subject, startWith, switchMap } from 'rxjs';
import { formatCurrency } from '../../shared/formatters';
import { EmptyStateComponent } from '../../shared/empty-state.component';

@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  imports: [CommonModule, EmptyStateComponent],
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
        <article class="panel stat-card stat-card--clickable" (click)="navigateToBusinesses()" role="button" tabindex="0" (keydown.enter)="navigateToBusinesses()">
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
              <p>Review stores that are configured but not active.</p>
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
        <div class="stats-grid" *ngIf="!summaryError(); else summaryErrorState">
          <div class="skeleton skeleton-stat" *ngFor="let i of [1,2,3,4,5,6,7]"></div>
        </div>
        <ng-template #summaryErrorState>
          <div class="panel loading">
            <p>{{ summaryError() }}</p>
            <button class="primary-btn" (click)="refresh()">Retry</button>
          </div>
        </ng-template>
      </ng-template>
    </div>
  `
})
export class PlatformDashboardPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly router = inject(Router);

  readonly summaryError = signal('');
  private readonly refresh$ = new Subject<void>();

  readonly summary = toSignal(
    this.refresh$.pipe(
      startWith(undefined),
      switchMap(() => {
        this.summaryError.set('');
        return this.api.getDashboardSummary().pipe(
          map((summary) => ({
            ...summary,
            totalRevenueFormatted: formatCurrency(summary.totalRevenue),
            refundedAmountFormatted: formatCurrency(summary.refundedAmount)
          })),
          catchError((error: unknown) => {
            const response = error as { error?: { message?: string; error?: string } };
            this.summaryError.set(
              response.error?.message || response.error?.error || 'Unable to load the platform dashboard.'
            );
            return of(null);
          })
        );
      })
    )
  );

  refresh(): void {
    this.refresh$.next();
  }

  navigateToBusinesses(): void {
    this.router.navigate(['/admin/businesses']);
  }
}
