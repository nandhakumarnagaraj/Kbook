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
      <section class="page-header">
        <div>
          <span class="eyebrow">Admin overview</span>
          <h2>Platform Dashboard</h2>
          <p class="muted">Cross-business snapshot of revenue, activity, and refund health.</p>
        </div>
      </section>

      <ng-container *ngIf="summary() as data; else loading">
        <section class="kpi-primary">
          <article class="kpi-card kpi-card--hero" (click)="navigateToBusinesses()" role="button" tabindex="0" (keydown.enter)="navigateToBusinesses()">
            <span class="kpi-label">Total Revenue</span>
            <strong class="kpi-value">{{ data.totalRevenueFormatted }}</strong>
            <span class="kpi-foot">Across all businesses</span>
          </article>
          <article class="kpi-card">
            <span class="kpi-label">Total Businesses</span>
            <strong class="kpi-value">{{ data.totalBusinesses }}</strong>
            <span class="kpi-foot">
              <span class="dot dot--success"></span> {{ data.liveBusinesses }} live
            </span>
          </article>
          <article class="kpi-card">
            <span class="kpi-label">Total Orders</span>
            <strong class="kpi-value">{{ data.totalOrders }}</strong>
            <span class="kpi-foot">All-time volume</span>
          </article>
          <article class="kpi-card kpi-card--warn" *ngIf="data.refundedOrders > 0">
            <span class="kpi-label">Refunds</span>
            <strong class="kpi-value">{{ data.refundedAmountFormatted }}</strong>
            <span class="kpi-foot">{{ data.refundedOrders }} orders</span>
          </article>
          <article class="kpi-card" *ngIf="data.refundedOrders === 0">
            <span class="kpi-label">Refunds</span>
            <strong class="kpi-value">{{ data.refundedAmountFormatted }}</strong>
            <span class="kpi-foot">{{ data.refundedOrders }} orders</span>
          </article>
        </section>

        <section class="kpi-secondary">
          <div class="kpi-mini">
            <span class="kpi-mini-label">Live storefronts</span>
            <span class="kpi-mini-value">{{ data.liveBusinesses }}</span>
          </div>
          <div class="kpi-mini">
            <span class="kpi-mini-label">Total staff</span>
            <span class="kpi-mini-value">{{ data.totalStaff }}</span>
          </div>
          <div class="kpi-mini">
            <span class="kpi-mini-label">Refunded orders</span>
            <span class="kpi-mini-value">{{ data.refundedOrders }}</span>
          </div>
          <div class="kpi-mini">
            <span class="kpi-mini-label">Refunded amount</span>
            <span class="kpi-mini-value">{{ data.refundedAmountFormatted }}</span>
          </div>
        </section>

        <section class="focus-panel">
          <header>
            <h3>Suggested focus</h3>
            <p class="muted">Weekly checks to keep the platform view actionable.</p>
          </header>
          <div class="focus-grid">
            <article class="focus-card">
              <span class="focus-tag focus-tag--warn">Weekly</span>
              <h4>Audit low-activity stores</h4>
              <p>Review businesses that are configured but not actively transacting.</p>
            </article>
            <article class="focus-card">
              <span class="focus-tag focus-tag--danger">Risk</span>
              <h4>Watch refund drift</h4>
              <p>Track refunded orders beside revenue to catch payment issues early.</p>
            </article>
            <article class="focus-card">
              <span class="focus-tag focus-tag--success">Ops</span>
              <h4>Review staffing spread</h4>
              <p>Business growth with flat staff counts signals onboarding gaps.</p>
            </article>
          </div>
        </section>
      </ng-container>

      <ng-template #loading>
        <div *ngIf="!summaryError(); else summaryErrorState" class="kpi-primary">
          <div class="skeleton skeleton-stat" *ngFor="let i of [1,2,3,4]"></div>
        </div>
        <ng-template #summaryErrorState>
          <div class="panel loading">
            <p>{{ summaryError() }}</p>
            <button class="primary-btn" (click)="refresh()">Retry</button>
          </div>
        </ng-template>
      </ng-template>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .page-shell { display: grid; gap: 1.5rem; }

    .page-header { display: flex; justify-content: space-between; align-items: end; gap: 1rem; }
    .page-header h2 { margin: 0.25rem 0 0.35rem; font-size: 1.75rem; letter-spacing: -0.01em; }
    .page-header p { margin: 0; }
    .eyebrow {
      text-transform: uppercase;
      letter-spacing: 0.08em;
      font-size: 0.72rem;
      font-weight: 700;
      color: var(--brand, #d97706);
    }

    .kpi-primary {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 1rem;
    }
    @media (max-width: 1100px) { .kpi-primary { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 560px)  { .kpi-primary { grid-template-columns: 1fr; } }

    .kpi-card {
      position: relative;
      background: #fff;
      border: 1px solid var(--line, #e6e4df);
      border-radius: 14px;
      padding: 1.15rem 1.25rem;
      display: grid;
      gap: 0.35rem;
      transition: border-color .18s, transform .18s, box-shadow .18s;
    }
    .kpi-card--hero {
      background: linear-gradient(160deg, #fff7ed 0%, #ffffff 60%);
      border-color: #fed7aa;
      cursor: pointer;
    }
    .kpi-card--hero:hover { transform: translateY(-1px); box-shadow: 0 12px 28px -20px rgba(217,119,6,0.5); }
    .kpi-card--warn { border-color: #fecaca; background: linear-gradient(160deg, #fef2f2 0%, #ffffff 60%); }

    .kpi-label {
      font-size: 0.78rem;
      color: var(--muted, #6b7280);
      text-transform: uppercase;
      letter-spacing: 0.06em;
      font-weight: 600;
    }
    .kpi-value {
      font-size: 1.85rem;
      font-weight: 700;
      color: var(--ink, #111827);
      letter-spacing: -0.01em;
      font-variant-numeric: tabular-nums;
    }
    .kpi-card--hero .kpi-value { font-size: 2.1rem; color: #7c2d12; }
    .kpi-foot { font-size: 0.82rem; color: var(--muted, #6b7280); display: inline-flex; align-items: center; gap: 0.4rem; }
    .dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
    .dot--success { background: #10b981; box-shadow: 0 0 0 3px rgba(16,185,129,0.15); }

    .kpi-secondary {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 0;
      background: #fff;
      border: 1px solid var(--line, #e6e4df);
      border-radius: 12px;
      overflow: hidden;
    }
    @media (max-width: 720px) { .kpi-secondary { grid-template-columns: repeat(2, 1fr); } }
    .kpi-mini {
      padding: 0.9rem 1.1rem;
      display: grid;
      gap: 0.25rem;
      border-right: 1px solid var(--line, #e6e4df);
    }
    .kpi-mini:last-child { border-right: none; }
    @media (max-width: 720px) {
      .kpi-mini:nth-child(2n) { border-right: none; }
      .kpi-mini:nth-child(-n+2) { border-bottom: 1px solid var(--line, #e6e4df); }
    }
    .kpi-mini-label { font-size: 0.76rem; color: var(--muted, #6b7280); font-weight: 600; }
    .kpi-mini-value { font-size: 1.05rem; font-weight: 700; color: var(--ink, #111827); font-variant-numeric: tabular-nums; }

    .focus-panel {
      background: #fff;
      border: 1px solid var(--line, #e6e4df);
      border-radius: 14px;
      padding: 1.35rem 1.5rem;
    }
    .focus-panel header { margin-bottom: 1rem; }
    .focus-panel h3 { margin: 0 0 0.25rem; font-size: 1.1rem; }
    .focus-panel p { margin: 0; }
    .focus-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.85rem;
    }
    @media (max-width: 900px) { .focus-grid { grid-template-columns: 1fr; } }
    .focus-card {
      padding: 1rem 1.1rem;
      background: var(--surface-alt, #fafaf9);
      border: 1px solid var(--line, #e6e4df);
      border-radius: 12px;
      display: grid;
      gap: 0.4rem;
    }
    .focus-card h4 { margin: 0.1rem 0 0; font-size: 0.98rem; }
    .focus-card p { margin: 0; color: var(--muted, #6b7280); font-size: 0.88rem; line-height: 1.5; }
    .focus-tag {
      justify-self: start;
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      padding: 0.2rem 0.55rem;
      border-radius: 6px;
    }
    .focus-tag--warn { background: #fef3c7; color: #92400e; }
    .focus-tag--danger { background: #fee2e2; color: #991b1b; }
    .focus-tag--success { background: #d1fae5; color: #065f46; }
  `]
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
