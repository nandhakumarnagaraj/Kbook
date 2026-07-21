import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, Subject, startWith, switchMap } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';
import { formatCurrency } from '../../shared/formatters';

@Component({
  selector: 'app-reports-page', standalone: true, imports: [CommonModule, DateRangeSelectorComponent],
  template: `<div class="page-shell">
    <section class="panel page-hero"><h2>Business Reports</h2><p class="muted">Review revenue, billing activity, pending payments, and refunds for a selected period.</p><div class="hero-meta"><span class="chip">Date-based</span><span class="chip success">Owner view</span></div></section>
    <section class="panel report-toolbar"><app-date-range-selector [initialRange]="selectedRange()" (rangeChanged)="setRange($event)"/><button type="button" class="ghost-btn" [disabled]="refreshing()" (click)="refresh()">{{ refreshing() ? 'Refreshing...' : 'Refresh' }}</button></section>
    <ng-container *ngIf="report() as data; else reportState">
      <div class="stats-grid" aria-label="Report summary">
        <article class="panel stat-card primary-metric"><h3>Recognized Revenue</h3><strong>{{ data.revenue }}</strong><p class="muted">Completed and paid bills</p></article>
        <article class="panel stat-card"><h3>Bill Records</h3><strong>{{ data.billCount }}</strong><p class="muted">All bill states in period</p></article>
        <article class="panel stat-card"><h3>Pending Payments</h3><strong>{{ data.pendingPayments }}</strong><p class="muted">Draft POS payments needing action</p></article>
        <article class="panel stat-card"><h3>Refunded Orders</h3><strong>{{ data.refundedOrders }}</strong><p class="muted">{{ data.refundRate }} of bill records</p></article>
        <article class="panel stat-card"><h3>Refunded Amount</h3><strong>{{ data.refundedAmount }}</strong><p class="muted">Recorded refunds in period</p></article>
        <article class="panel stat-card"><h3>Net After Refunds</h3><strong>{{ data.netRevenue }}</strong><p class="muted">Revenue less recorded refunds</p></article>
      </div>
      <section class="panel report-note"><h3>How to read this report</h3><p class="muted">Bill Records includes draft and cancelled records. Recognized Revenue only includes completed or paid bills with successful payment status.</p></section>
    </ng-container>
    <ng-template #reportState><div class="panel loading" *ngIf="error(); else loadingState" role="alert"><p>{{ error() }}</p><button class="primary-btn" (click)="refresh()">Try again</button></div><ng-template #loadingState><div class="stats-grid" role="status" aria-label="Loading reports"><div class="skeleton skeleton-stat" *ngFor="let item of [1,2,3,4,5,6]"></div></div></ng-template></ng-template>
  </div>`,
  styles: [`.report-toolbar{display:flex;align-items:center;justify-content:space-between;gap:1rem;flex-wrap:wrap}.primary-metric{border-color:rgba(181,106,45,.35);background:linear-gradient(135deg,var(--panel),rgba(181,106,45,.1))}.stat-card p{margin:.45rem 0 0}.report-note{padding:1.2rem 1.35rem}.report-note h3{margin:0 0 .35rem}@media(max-width:480px){.report-toolbar{align-items:stretch}.report-toolbar button{width:100%}}`]
})
export class ReportsPageComponent {
  private static readonly RANGE_KEY = 'business-reports-date-range'; private readonly api = inject(BusinessApiService); private readonly trigger$ = new Subject<void>();
  readonly selectedRange = signal<{ from: string; to: string } | null>(this.readRange()); readonly refreshing = signal(false); readonly error = signal('');
  readonly report = toSignal(this.trigger$.pipe(startWith(undefined), switchMap(() => { this.refreshing.set(true); this.error.set(''); const range = this.selectedRange(); return this.api.getDashboard(range?.from, range?.to).pipe(map(data => { this.refreshing.set(false); const net = Math.max(0, data.totalRevenue - data.refundedAmount); return { revenue: formatCurrency(data.totalRevenue), billCount: data.posOrderCount, pendingPayments: data.pendingPosPayments, refundedOrders: data.refundedOrders, refundedAmount: formatCurrency(data.refundedAmount), netRevenue: formatCurrency(net), refundRate: data.posOrderCount ? `${((data.refundedOrders / data.posOrderCount) * 100).toFixed(1)}%` : '0%' }; }), catchError((err: unknown) => { this.refreshing.set(false); const response = err as { error?: { message?: string; error?: string } }; this.error.set(response.error?.message || response.error?.error || 'Unable to load reports.'); return of(null); })); })));
  setRange(range: { from: string; to: string }): void { this.selectedRange.set(range); sessionStorage.setItem(ReportsPageComponent.RANGE_KEY, JSON.stringify(range)); this.trigger$.next(); }
  refresh(): void { this.trigger$.next(); }
  private readRange(): { from: string; to: string } | null { try { const raw = sessionStorage.getItem(ReportsPageComponent.RANGE_KEY); if (!raw) return null; const range = JSON.parse(raw) as { from?: string; to?: string }; return /^\d{4}-\d{2}-\d{2}$/.test(range.from ?? '') && /^\d{4}-\d{2}-\d{2}$/.test(range.to ?? '') ? { from: range.from!, to: range.to! } : null; } catch { return null; } }
}
