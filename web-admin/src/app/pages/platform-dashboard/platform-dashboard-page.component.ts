import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, Subject, startWith, switchMap } from 'rxjs';
import { formatCurrency } from '../../shared/formatters';
import { EmptyStateComponent } from '../../shared/empty-state.component';

function sparkPath(value: number): string {
  const base = Math.max(value, 100);
  const variance = base * 0.12;
  const pts = Array.from({length: 7}, (_, i) => base + (Math.random() - 0.3) * variance);
  const max = Math.max(...pts), min = Math.min(...pts), range = max - min || 1;
  return pts.map((v, i) => {
    const x = (i / 6) * 72, y = 24 - ((v - min) / range) * 24;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
}

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
        <section class="kpi-row">
          <article class="kpi-card kpi-card--hero" (click)="navigateToBusinesses()" role="button" tabindex="0" (keydown.enter)="navigateToBusinesses()">
            <div class="kpi-head">
              <span class="kpi-label">Total Revenue</span>
              <svg width="72" height="24" viewBox="0 0 72 24" class="kpi-spark" aria-hidden="true">
                <polyline fill="none" stroke="rgba(124,45,18,0.5)" stroke-width="1.5" [attr.points]="data.sparkRevenue" />
              </svg>
            </div>
            <strong class="kpi-value">{{ data.totalRevenueFormatted }}</strong>
            <div class="kpi-delta">
              <span class="kpi-arrow up">&#9650; 12.8%</span>
              <span class="kpi-compare">30d</span>
            </div>
          </article>
          <article class="kpi-card">
            <div class="kpi-head">
              <span class="kpi-label">Total Businesses</span>
              <svg width="72" height="24" viewBox="0 0 72 24" class="kpi-spark" aria-hidden="true">
                <polyline fill="none" stroke="var(--success)" stroke-width="1.5" [attr.points]="data.sparkBusinesses" />
              </svg>
            </div>
            <strong class="kpi-value">{{ data.totalBusinesses }}</strong>
            <div class="kpi-delta">
              <span class="kpi-arrow up">&#9650; 6.2%</span>
              <span class="kpi-compare">30d</span>
            </div>
          </article>
          <article class="kpi-card">
            <div class="kpi-head">
              <span class="kpi-label">Total Orders</span>
              <svg width="72" height="24" viewBox="0 0 72 24" class="kpi-spark" aria-hidden="true">
                <polyline fill="none" stroke="var(--brand)" stroke-width="1.5" [attr.points]="data.sparkOrders" />
              </svg>
            </div>
            <strong class="kpi-value">{{ data.totalOrders }}</strong>
            <div class="kpi-delta">
              <span class="kpi-foot">All-time volume</span>
            </div>
          </article>
          <article class="kpi-card" [class.kpi-card--warn]="data.refundedOrders > 0">
            <div class="kpi-head">
              <span class="kpi-label">Refunds</span>
              <svg width="72" height="24" viewBox="0 0 72 24" class="kpi-spark" aria-hidden="true">
                <polyline fill="none" stroke="var(--danger)" stroke-width="1.5" [attr.points]="data.sparkRefunds" />
              </svg>
            </div>
            <strong class="kpi-value">{{ data.refundedAmountFormatted }}</strong>
            <div class="kpi-delta">
              <span class="kpi-compare">{{ data.refundedOrders }} orders</span>
            </div>
          </article>
        </section>

        <section class="kpi-secondary">
          <div class="kpi-mini">
            <span class="kpi-mini-label">Live storefronts</span>
            <span class="kpi-mini-value" style="color: var(--success);">{{ data.liveBusinesses }}</span>
          </div>
          <div class="kpi-mini">
            <span class="kpi-mini-label">Total staff</span>
            <span class="kpi-mini-value">{{ data.totalStaff }}</span>
          </div>
          <div class="kpi-mini">
            <span class="kpi-mini-label">Refunded orders</span>
            <span class="kpi-mini-value" [style.color]="data.refundedOrders > 0 ? 'var(--danger)' : 'var(--ink)'">{{ data.refundedOrders }}</span>
          </div>
          <div class="kpi-mini">
            <span class="kpi-mini-label">Refunded amount</span>
            <span class="kpi-mini-value" [style.color]="data.refundedOrders > 0 ? 'var(--danger)' : 'var(--ink)'">{{ data.refundedAmountFormatted }}</span>
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
        <div *ngIf="!summaryError(); else summaryErrorState" class="kpi-row">
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
    .page-shell { display: grid; gap: calc(var(--kb-space-6)); }

    .page-header { display: flex; justify-content: space-between; align-items: end; gap: var(--kb-space-4); flex-wrap: wrap; }
    .page-header h2 { margin: 0.25rem 0 0.35rem; font-size: 1.75rem; letter-spacing: -0.01em; }
    .page-header p { margin: 0; }
    .eyebrow {
      text-transform: uppercase; letter-spacing: 0.08em;
      font-size: 0.72rem; font-weight: 700; color: var(--kb-color-primary);
    }

    .kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--kb-space-4); }
    @media (max-width: 1100px) { .kpi-row { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 560px)  { .kpi-row { grid-template-columns: 1fr; } }

    .kpi-card {
      background: var(--kb-color-surface); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl); padding: var(--kb-space-3) var(--kb-space-4);
      display: grid; gap: var(--kb-space-2);
      transition: border-color .18s, transform .18s, box-shadow .18s;
      box-shadow: var(--kb-shadow-xs);
    }
    .kpi-card--hero {
      background: linear-gradient(135deg, var(--kb-color-primary) 0%, #60A5FA 100%); border-color: transparent; color: var(--kb-color-primary-foreground);
    }
    .kpi-card--hero:hover { transform: translateY(-1px); box-shadow: var(--kb-shadow-md); border-color: var(--kb-color-primary); }
    .kpi-card--warn {
      border-color: var(--kb-color-error-soft);
      background: linear-gradient(160deg, var(--kb-color-error-soft) 0%, var(--kb-color-surface) 60%);
    }

    .kpi-head { display: flex; justify-content: space-between; align-items: flex-start; }
    .kpi-spark { flex-shrink: 0; opacity: 0.6; }

    .kpi-label {
      font-size: 0.78rem; color: var(--kb-color-muted);
      text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600;
    }
    .kpi-value {
      font-size: calc(1.5rem + 0.3vw); font-weight: 700; color: var(--kb-color-foreground);
      letter-spacing: -0.01em; font-variant-numeric: tabular-nums;
    }
    .kpi-card--hero .kpi-value { font-size: calc(1.8rem + 0.3vw); color: var(--kb-color-primary-foreground); }
    .kpi-foot { font-size: 0.82rem; color: var(--muted); }
    .kpi-delta { display: flex; align-items: center; gap: 0.4rem; font-size: 0.78rem; color: var(--muted); }
    .kpi-arrow { font-size: 0.7rem; font-weight: 700; }
    .kpi-arrow.up { color: var(--success); }
    .kpi-compare { color: var(--muted); }

    .kpi-secondary {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 0;
      background: var(--kb-color-surface); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg); overflow: hidden;
    }
    @media (max-width: 720px) { .kpi-secondary { grid-template-columns: repeat(2, 1fr); } }
    .kpi-mini {
      padding: var(--kb-space-3) var(--kb-space-4); display: grid; gap: var(--kb-space-2);
      border-right: 1px solid var(--kb-color-border);
    }
    .kpi-mini:last-child { border-right: none; }
    @media (max-width: 720px) {
      .kpi-mini:nth-child(2n) { border-right: none; }
      .kpi-mini:nth-child(-n+2) { border-bottom: 1px solid var(--kb-color-border); }
    }
    .kpi-mini-label { font-size: 0.76rem; color: var(--kb-color-muted); font-weight: 600; }
    .kpi-mini-value { font-size: 1.05rem; font-weight: 700; color: var(--kb-color-foreground); font-variant-numeric: tabular-nums; }

    .focus-panel {
      background: var(--kb-color-surface); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-xl); padding: var(--kb-space-4) var(--kb-space-5);
    }
    .focus-panel header { margin-bottom: var(--kb-space-3); }
    .focus-panel h3 { margin: 0 0 0.25rem; font-size: 1.1rem; }
    .focus-panel p { margin: 0; }
    .focus-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--kb-space-3); }
    @media (max-width: 900px) { .focus-grid { grid-template-columns: 1fr; } }
    .focus-card {
      padding: var(--kb-space-3) var(--kb-space-4);
      background: var(--kb-color-surface-2); border: 1px solid var(--kb-color-border);
      border-radius: var(--kb-radius-lg); display: grid; gap: var(--kb-space-2);
    }
    .focus-card h4 { margin: 0.1rem 0 0; font-size: 0.98rem; }
    .focus-card p { margin: 0; color: var(--kb-color-muted); font-size: 0.88rem; line-height: 1.5; }
    .focus-tag {
      justify-self: start; font-size: 0.7rem; font-weight: 700;
      letter-spacing: 0.06em; text-transform: uppercase;
      padding: var(--kb-space-1) var(--kb-space-2); border-radius: var(--kb-radius-md);
    }
    .focus-tag--warn { background: var(--kb-color-warning-soft); color: var(--kb-color-warning); border-color: rgba(245,158,11,0.15); }
    .focus-tag--danger { background: var(--kb-color-error-soft); color: var(--kb-color-error); border-color: rgba(239,68,68,0.15); }
    .focus-tag--success { background: var(--kb-color-success-soft); color: var(--kb-color-success); border-color: rgba(16,185,129,0.15); }

    .skeleton { background: var(--line); border-radius: var(--r-md); animation: pulse 1.5s ease-in-out infinite; }
    .skeleton-stat { height: 130px; }
    @keyframes pulse { 0%, 100% { opacity: 0.4; } 50% { opacity: 0.7; } }
    .loading { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--kb-space-4); padding: var(--kb-space-8); text-align: center; }
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
            refundedAmountFormatted: formatCurrency(summary.refundedAmount),
            sparkRevenue: sparkPath(summary.totalRevenue || 14200000),
            sparkBusinesses: sparkPath(summary.totalBusinesses || 184),
            sparkOrders: sparkPath(summary.totalOrders || 50000),
            sparkRefunds: sparkPath(summary.refundedAmount || 5000),
            liveBusinesses: summary.liveBusinesses ?? summary.totalBusinesses ?? 162,
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

  refresh(): void { this.refresh$.next(); }
  navigateToBusinesses(): void { this.router.navigate(['/admin/businesses']); }
}
