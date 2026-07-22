import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, Subject, startWith, switchMap } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';
import { formatCurrency } from '../../shared/formatters';

@Component({
  selector: 'app-reports-page',
  standalone: true,
  imports: [CommonModule, DateRangeSelectorComponent],
  template: `
    <div class="page-shell">
      <section class="page-header">
        <div>
          <span class="eyebrow">Owner view</span>
          <h2>Business Reports</h2>
          <p class="muted">Revenue, billing activity, pending payments, and refunds for the selected period.</p>
        </div>
      </section>

      <section class="toolbar">
        <app-date-range-selector [initialRange]="selectedRange()" (rangeChanged)="setRange($event)"/>
        <button type="button" class="ghost-btn" [disabled]="refreshing()" (click)="refresh()">
          {{ refreshing() ? 'Refreshing…' : 'Refresh' }}
        </button>
      </section>

      <ng-container *ngIf="report() as data; else reportState">
        <section class="kpi-primary" aria-label="Report summary">
          <article class="kpi-card kpi-card--hero">
            <span class="kpi-label">Recognized Revenue</span>
            <strong class="kpi-value">{{ data.revenue }}</strong>
            <span class="kpi-foot">Completed &amp; paid bills</span>
          </article>
          <article class="kpi-card">
            <span class="kpi-label">Bill Records</span>
            <strong class="kpi-value">{{ data.billCount }}</strong>
            <span class="kpi-foot">All bill states in period</span>
          </article>
          <article class="kpi-card" [class.kpi-card--warn]="data.pendingPayments > 0">
            <span class="kpi-label">Pending Payments</span>
            <strong class="kpi-value">{{ data.pendingPayments }}</strong>
            <span class="kpi-foot">Draft POS payments</span>
          </article>
          <article class="kpi-card">
            <span class="kpi-label">Net After Refunds</span>
            <strong class="kpi-value">{{ data.netRevenue }}</strong>
            <span class="kpi-foot">Revenue less refunds</span>
          </article>
        </section>

        <section class="kpi-secondary">
          <div class="kpi-mini">
            <span class="kpi-mini-label">Refunded orders</span>
            <span class="kpi-mini-value">{{ data.refundedOrders }}</span>
          </div>
          <div class="kpi-mini">
            <span class="kpi-mini-label">Refunded amount</span>
            <span class="kpi-mini-value">{{ data.refundedAmount }}</span>
          </div>
          <div class="kpi-mini">
            <span class="kpi-mini-label">Refund rate</span>
            <span class="kpi-mini-value">{{ data.refundRate }}</span>
          </div>
        </section>

        <section class="note-panel">
          <h3>How to read this report</h3>
          <p class="muted">Bill Records includes draft and cancelled records. Recognized Revenue only includes completed or paid bills with a successful payment status.</p>
        </section>
      </ng-container>

      <ng-template #reportState>
        <div class="panel loading" *ngIf="error(); else loadingState" role="alert">
          <p>{{ error() }}</p>
          <button class="primary-btn" (click)="refresh()">Try again</button>
        </div>
        <ng-template #loadingState>
          <div class="kpi-primary" role="status" aria-label="Loading reports">
            <div class="skeleton skeleton-stat" *ngFor="let item of [1,2,3,4]"></div>
          </div>
        </ng-template>
      </ng-template>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .page-shell { display: grid; gap: 1.5rem; }
    .page-header h2 { margin: 0.25rem 0 0.35rem; font-size: 1.75rem; letter-spacing: -0.01em; }
    .page-header p { margin: 0; }
    .eyebrow { text-transform: uppercase; letter-spacing: 0.08em; font-size: 0.72rem; font-weight: 700; color: var(--brand, #d97706); }

    .toolbar {
      display: flex; align-items: center; justify-content: space-between;
      gap: 1rem; flex-wrap: wrap;
      background: #fff; border: 1px solid var(--line, #e6e4df);
      border-radius: 12px; padding: 0.9rem 1rem;
    }
    @media (max-width: 560px) { .toolbar { align-items: stretch; } .toolbar .ghost-btn { width: 100%; } }

    .kpi-primary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; }
    @media (max-width: 1100px) { .kpi-primary { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 560px)  { .kpi-primary { grid-template-columns: 1fr; } }

    .kpi-card {
      background: #fff; border: 1px solid var(--line, #e6e4df);
      border-radius: 14px; padding: 1.15rem 1.25rem;
      display: grid; gap: 0.35rem;
    }
    .kpi-card--hero {
      background: var(--gradient-hero);
      border-color: transparent;
      color: #ffffff;
    }
    .kpi-card--hero .kpi-label { color: rgba(255, 255, 255, 0.85); }
    .kpi-card--hero .kpi-value { font-size: 2.1rem; color: #ffffff; }
    .kpi-card--hero .kpi-foot { color: rgba(255, 255, 255, 0.85); }
    .kpi-card--warn { border-color: #fecaca; background: linear-gradient(160deg, #fef2f2 0%, #ffffff 60%); }

    .kpi-label { font-size: 0.78rem; color: var(--muted, #6b7280); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600; }
    .kpi-value { font-size: 1.85rem; font-weight: 700; color: var(--ink, #111827); letter-spacing: -0.01em; font-variant-numeric: tabular-nums; }
    .kpi-foot { font-size: 0.82rem; color: var(--muted, #6b7280); }

    .kpi-secondary {
      display: grid; grid-template-columns: repeat(3, 1fr); gap: 0;
      background: #fff; border: 1px solid var(--line, #e6e4df);
      border-radius: 12px; overflow: hidden;
    }
    @media (max-width: 720px) { .kpi-secondary { grid-template-columns: 1fr; } }
    .kpi-mini { padding: 0.9rem 1.1rem; display: grid; gap: 0.25rem; border-right: 1px solid var(--line, #e6e4df); }
    .kpi-mini:last-child { border-right: none; }
    @media (max-width: 720px) {
      .kpi-mini { border-right: none; border-bottom: 1px solid var(--line, #e6e4df); }
      .kpi-mini:last-child { border-bottom: none; }
    }
    .kpi-mini-label { font-size: 0.76rem; color: var(--muted, #6b7280); font-weight: 600; }
    .kpi-mini-value { font-size: 1.05rem; font-weight: 700; color: var(--ink, #111827); font-variant-numeric: tabular-nums; }

    .note-panel {
      background: #fff; border: 1px solid var(--line, #e6e4df);
      border-radius: 12px; padding: 1.15rem 1.35rem;
    }
    .note-panel h3 { margin: 0 0 0.3rem; font-size: 1rem; }
    .note-panel p { margin: 0; line-height: 1.55; }
  `]
})
export class ReportsPageComponent {
  private static readonly RANGE_KEY = 'business-reports-date-range';
  private readonly api = inject(BusinessApiService);
  private readonly trigger$ = new Subject<void>();
  readonly selectedRange = signal<{ from: string; to: string } | null>(this.readRange());
  readonly refreshing = signal(false);
  readonly error = signal('');
  readonly report = toSignal(this.trigger$.pipe(startWith(undefined), switchMap(() => {
    this.refreshing.set(true); this.error.set('');
    const range = this.selectedRange();
    return this.api.getDashboard(range?.from, range?.to).pipe(
      map(data => {
        this.refreshing.set(false);
        const net = Math.max(0, data.totalRevenue - data.refundedAmount);
        return {
          revenue: formatCurrency(data.totalRevenue),
          billCount: data.posOrderCount,
          pendingPayments: data.pendingPosPayments,
          refundedOrders: data.refundedOrders,
          refundedAmount: formatCurrency(data.refundedAmount),
          netRevenue: formatCurrency(net),
          refundRate: data.posOrderCount ? `${((data.refundedOrders / data.posOrderCount) * 100).toFixed(1)}%` : '0%'
        };
      }),
      catchError((err: unknown) => {
        this.refreshing.set(false);
        const response = err as { error?: { message?: string; error?: string } };
        this.error.set(response.error?.message || response.error?.error || 'Unable to load reports.');
        return of(null);
      })
    );
  })));
  setRange(range: { from: string; to: string }): void {
    this.selectedRange.set(range);
    sessionStorage.setItem(ReportsPageComponent.RANGE_KEY, JSON.stringify(range));
    this.trigger$.next();
  }
  refresh(): void { this.trigger$.next(); }
  private readRange(): { from: string; to: string } | null {
    try {
      const raw = sessionStorage.getItem(ReportsPageComponent.RANGE_KEY);
      if (!raw) return null;
      const range = JSON.parse(raw) as { from?: string; to?: string };
      return /^\d{4}-\d{2}-\d{2}$/.test(range.from ?? '') && /^\d{4}-\d{2}-\d{2}$/.test(range.to ?? '')
        ? { from: range.from!, to: range.to! } : null;
    } catch { return null; }
  }
}
