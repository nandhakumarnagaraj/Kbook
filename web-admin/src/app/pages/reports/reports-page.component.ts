import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, Subject, startWith, switchMap } from 'rxjs';
import { BusinessApiService } from '../../core/services/business-api.service';
import { DateRangeSelectorComponent } from '../../shared/date-range-selector.component';
import { formatCurrency } from '../../shared/formatters';

@Component({
  selector: 'app-reports-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, DateRangeSelectorComponent],
  template: `
    <div class="page-shell">
      <!-- Operational Header (navbar.gallery standard) -->
      <header class="reports-header">
        <div class="header-left">
          <div class="header-title-row">
            <h2>Financial &amp; Sales Reports</h2>
            <span class="owner-pill">● Executive View</span>
          </div>
          <p class="header-sub">Recognized revenues, billing volume, refund impact, and gateway reconciliation.</p>
        </div>
        <div class="header-right">
          <app-date-range-selector [initialRange]="selectedRange()" (rangeChanged)="setRange($event)"/>
          <button type="button" class="ghost-btn-tactile" [disabled]="refreshing()" (click)="refresh()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
            {{ refreshing() ? 'Calculating...' : 'Refresh' }}
          </button>
          <button type="button" class="ghost-btn-tactile" (click)="exportCsv()" *ngIf="report()">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            Export CSV
          </button>
        </div>
      </header>

      <ng-container *ngIf="report() as data; else reportState">
        <!-- Bento Analytics Row (bentogrids.com standard) -->
        <section class="reports-bento-grid" aria-label="Report summary">
          <!-- Hero Tile: Net Realized -->
          <article class="bento-card bento-card--hero">
            <div class="bento-head">
              <span class="bento-label">Net Realized Revenue</span>
              <svg width="68" height="22" viewBox="0 0 64 20" class="bento-spark" aria-hidden="true">
                <polyline fill="none" stroke="rgba(255,255,255,0.85)" stroke-width="2" [attr.points]="data.sparkNet"/>
              </svg>
            </div>
            <strong class="bento-value">{{ data.netRevenue }}</strong>
            <div class="bento-foot">
              <span class="delta-tag delta-tag--up">&#9650; Active Realized</span>
              <span class="delta-sub">After all refunds</span>
            </div>
          </article>

          <!-- Gross Billed -->
          <article class="bento-card">
            <div class="bento-head">
              <span class="bento-label">Gross Billed</span>
              <svg width="64" height="20" viewBox="0 0 64 20" class="bento-spark" aria-hidden="true">
                <polyline fill="none" stroke="#5D45FD" stroke-width="1.75" [attr.points]="data.sparkRevenue"/>
              </svg>
            </div>
            <strong class="bento-value">{{ data.revenue }}</strong>
            <div class="bento-foot">
              <span class="delta-sub">Total sales before deductions</span>
            </div>
          </article>

          <!-- Bill Records -->
          <article class="bento-card">
            <div class="bento-head">
              <span class="bento-label">Bills Minted</span>
              <svg width="64" height="20" viewBox="0 0 64 20" class="bento-spark" aria-hidden="true">
                <polyline fill="none" stroke="#10B981" stroke-width="1.75" [attr.points]="data.sparkBills"/>
              </svg>
            </div>
            <strong class="bento-value">{{ data.billCount }}</strong>
            <div class="bento-foot">
              <span class="delta-sub">Completed &amp; draft orders</span>
            </div>
          </article>

          <!-- Pending Payments -->
          <article class="bento-card" [class.bento-card--warn]="data.pendingPayments > 0">
            <div class="bento-head">
              <span class="bento-label">Unsettled / Pending</span>
              <svg width="64" height="20" viewBox="0 0 64 20" class="bento-spark" aria-hidden="true">
                <polyline fill="none" stroke="#D97706" stroke-width="1.75" [attr.points]="data.sparkPending"/>
              </svg>
            </div>
            <strong class="bento-value">{{ data.pendingPayments }}</strong>
            <div class="bento-foot">
              <span class="delta-sub">Awaiting customer payment</span>
            </div>
          </article>
        </section>

        <!-- Secondary Financial Metrics Strip -->
        <section class="financial-strip-card">
          <div class="strip-item">
            <span class="strip-label">Refunded Tickets</span>
            <strong class="strip-val" [class.text-danger]="data.refundedOrders > 0">{{ data.refundedOrders }}</strong>
            <span class="strip-hint">Orders reversed or refunded</span>
          </div>
          <div class="strip-item">
            <span class="strip-label">Total Refund Deductions</span>
            <strong class="strip-val" [class.text-danger]="data.refundedOrders > 0">{{ data.refundedAmount }}</strong>
            <span class="strip-hint">Returned to customers</span>
          </div>
          <div class="strip-item">
            <span class="strip-label">Refund Rate</span>
            <strong class="strip-val" [class.text-warn]="data.refundedOrders > 0">{{ data.refundRate }}</strong>
            <span class="strip-hint">Percentage of overall order volume</span>
          </div>
        </section>

        <!-- Financial Audit Note (unsection.com aesthetic) -->
        <section class="audit-note-card">
          <div class="note-icon-halo">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="16" x2="12" y2="12"/>
              <line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>
          </div>
          <div class="note-copy">
            <h4>Financial Integrity &amp; Settlement Standard</h4>
            <p>
              Recognized Revenue includes all orders settled through Cash, Dynamic UPI QR, and paired card terminals. Unsettled draft tickets and cancelled orders are excluded from net revenue to prevent double-counting.
            </p>
          </div>
        </section>
      </ng-container>

      <ng-template #reportState>
        <div class="panel loading" *ngIf="error(); else loadingState" role="alert">
          <p>{{ error() }}</p>
          <button type="button" class="primary-btn-tactile" (click)="refresh()">Try again</button>
        </div>
        <ng-template #loadingState>
          <div class="reports-bento-grid" role="status" aria-label="Loading reports">
            <div class="skeleton skeleton-stat" *ngFor="let item of [1,2,3,4]; trackBy: trackByIndex"></div>
          </div>
        </ng-template>
      </ng-template>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      background: var(--pos-bg-body, #F6F8FD);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', sans-serif;
    }

    .page-shell {
      padding: 1.5rem 2rem;
      max-width: 1440px;
      margin: 0 auto;
    }

    /* ── Header (navbar.gallery standard) ── */
    .reports-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
      margin-bottom: 1.75rem;
      padding-bottom: 1.25rem;
      border-bottom: 1px solid #EEF0F7;
    }
    .header-title-row {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      margin-bottom: 0.25rem;
    }
    .header-title-row h2 {
      margin: 0;
      font-size: 1.45rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
    }
    .owner-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.2rem 0.65rem;
      border-radius: 999px;
      font-size: 0.74rem;
      font-weight: 700;
      background: #F3F0FF;
      color: #5D45FD;
      border: 1px solid #DDD6FE;
    }
    .header-sub {
      margin: 0;
      font-size: 0.88rem;
      color: #64748B;
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .ghost-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.55rem 1rem;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      font-size: 0.84rem;
      font-weight: 600;
      color: #334155;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .ghost-btn-tactile:hover:not(:disabled) {
      background: #F8FAFC;
      border-color: #CBD5E1;
      color: #0F172A;
    }
    .ghost-btn-tactile:active:not(:disabled) {
      transform: scale(0.97);
    }

    .primary-btn-tactile {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.55rem 1.15rem;
      background: #5D45FD;
      color: #FFFFFF;
      border: none;
      border-radius: 10px;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(93, 69, 253, 0.25);
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .primary-btn-tactile:active {
      transform: scale(0.97);
    }

    /* ── Bento Analytics Grid (bentogrids.com standard) ── */
    .reports-bento-grid {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr 1fr;
      gap: 1rem;
      margin-bottom: 1.5rem;
    }
    @media (max-width: 1080px) {
      .reports-bento-grid { grid-template-columns: 1fr 1fr; }
    }
    @media (max-width: 640px) {
      .reports-bento-grid { grid-template-columns: 1fr; }
    }

    .bento-card {
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.25rem 1.4rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .bento-card--hero {
      background: linear-gradient(135deg, #1E1B4B 0%, #312E81 100%);
      color: #FFFFFF;
      border: none;
      box-shadow: 0 8px 24px rgba(49, 46, 129, 0.25);
    }
    .bento-card--hero .bento-label { color: rgba(255, 255, 255, 0.75); }
    .bento-card--hero .bento-value { color: #FFFFFF; }
    .bento-card--hero .delta-sub { color: rgba(255, 255, 255, 0.7); }
    .bento-card--warn {
      border-color: #FDE68A;
      background: #FFFDF7;
    }
    .bento-card--warn .bento-value { color: #D97706; }

    .bento-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .bento-label {
      font-size: 0.78rem;
      font-weight: 600;
      color: #8F95B2;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .bento-spark {
      opacity: 0.8;
    }
    .bento-value {
      font-size: 1.65rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
    }
    .bento-foot {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.78rem;
    }
    .delta-tag {
      font-size: 0.72rem;
      font-weight: 700;
      padding: 0.15rem 0.45rem;
      border-radius: 999px;
    }
    .delta-tag--up {
      background: rgba(16, 185, 129, 0.2);
      color: #34D399;
    }
    .delta-sub {
      color: #64748B;
      font-size: 0.76rem;
    }

    /* ── Secondary Financial Strip ── */
    .financial-strip-card {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      overflow: hidden;
      margin-bottom: 1.5rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    @media (max-width: 768px) {
      .financial-strip-card { grid-template-columns: 1fr; }
    }
    .strip-item {
      padding: 1.25rem 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      border-right: 1px solid #EEF0F7;
    }
    .strip-item:last-child {
      border-right: none;
    }
    .strip-label {
      font-size: 0.78rem;
      font-weight: 600;
      color: #64748B;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .strip-val {
      font-size: 1.3rem;
      font-weight: 800;
      color: #0F172A;
      font-variant-numeric: tabular-nums;
    }
    .strip-hint {
      font-size: 0.76rem;
      color: #94A3B8;
    }
    .text-danger { color: #DC2626 !important; }
    .text-warn { color: #D97706 !important; }

    /* ── Audit Note Card (unsection.com aesthetic) ── */
    .audit-note-card {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      background: #FFFFFF;
      border: 1px solid #EEF0F7;
      border-radius: 18px;
      padding: 1.25rem 1.5rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }
    .note-icon-halo {
      width: 38px;
      height: 38px;
      border-radius: 10px;
      background: #F5F3FF;
      color: #5D45FD;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .note-copy h4 {
      margin: 0 0 0.25rem;
      font-size: 0.95rem;
      font-weight: 700;
      color: #0F172A;
    }
    .note-copy p {
      margin: 0;
      font-size: 0.84rem;
      color: #64748B;
      line-height: 1.5;
    }

    .skeleton { background: #E2E8F0; border-radius: 14px; animation: pulse 1.5s ease-in-out infinite; }
    .skeleton-stat { height: 120px; }
    @keyframes pulse { 0%, 100% { opacity: 0.4; } 50% { opacity: 0.7; } }
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
        const totalRev = Number(data.totalRevenue) || 0;
        const refunded = Number(data.refundedAmount) || 0;
        const net = Math.max(0, totalRev - refunded);
        const billCount = Number(data.posOrderCount) || 0;
        const pending = Number(data.pendingPosPayments) || 0;

        function sp(v: number): string {
          if (v <= 0) return '0,20 64,20';
          const pts = Array.from({length:6}, (_,i) => v * (0.85 + (i * 0.05)));
          const mx=Math.max(...pts), mn=Math.min(...pts), rn=mx-mn||1;
          return pts.map((p,i)=>{const x=(i/5)*64,y=20-((p-mn)/rn)*20;return `${x.toFixed(1)},${y.toFixed(1)}`;}).join(' ');
        }
        return {
          sparkRevenue: sp(totalRev),
          sparkBills: sp(billCount),
          sparkPending: sp(pending),
          sparkNet: sp(net),
          revenue: formatCurrency(totalRev),
          billCount: billCount,
          pendingPayments: pending,
          refundedOrders: data.refundedOrders || 0,
          refundedAmount: formatCurrency(refunded),
          netRevenue: formatCurrency(net),
          refundRate: billCount ? `${(((data.refundedOrders || 0) / billCount) * 100).toFixed(1)}%` : '0%'
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

  trackByIndex = (_: number, __: unknown) => _;

  exportCsv(): void {
    const data = this.report();
    if (!data) return;
    const range = this.selectedRange();
    const rows = [
      ['Metric', 'Value'],
      ['Revenue', data.revenue],
      ['Bill Count', String(data.billCount)],
      ['Pending Payments', String(data.pendingPayments)],
      ['Refunded Orders', String(data.refundedOrders)],
      ['Refunded Amount', data.refundedAmount],
      ['Net Revenue', data.netRevenue],
      ['Refund Rate', data.refundRate],
      ['Period', range ? `${range.from} to ${range.to}` : 'All time']
    ];
    const csv = rows.map(r => r.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `khanabook-report-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

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
