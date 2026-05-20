import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AdminApiService } from '../../core/services/admin-api.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { formatCurrency } from '../../shared/formatters';

@Component({
  selector: 'app-platform-dashboard-page',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="dash-shell">
      <ng-container *ngIf="summary() as data; else loading">
        <section class="hero-panel">
          <div class="hero-copy">
            <div class="hero-badge">
              <span class="hero-dot"></span>
              Platform overview
            </div>
            <h1>Platform Control Room</h1>
            <p class="hero-text">Monitor business growth, payment health, and KYC pipeline pressure from one place.</p>
            <div class="hero-facts">
              <span>{{ data.totalBusinesses }} businesses</span>
              <span>{{ data.totalOrders }} orders</span>
              <span>{{ data.liveBusinesses }} currently live</span>
            </div>
          </div>

          <div class="hero-aside">
            <div class="hero-kpi">
              <span class="hero-kpi-label">Net Platform Revenue</span>
              <strong>{{ netRevenueFormatted(data) }}</strong>
              <p>{{ data.refundedAmountFormatted }} refunded across all merchants</p>
            </div>
            <div class="hero-kpi slim">
              <span class="hero-kpi-label">KYC Queue</span>
              <strong>{{ pendingCount() }}</strong>
              <p>{{ pendingCount() > 0 ? 'Requires admin attention now' : 'No immediate actions waiting' }}</p>
            </div>
          </div>
        </section>

        <section class="metric-band">
          <article class="metric-card gold">
            <span class="metric-kicker">Businesses</span>
            <strong>{{ data.totalBusinesses }}</strong>
            <p>{{ data.liveBusinesses }} active on platform</p>
          </article>
          <article class="metric-card green">
            <span class="metric-kicker">Gross Revenue</span>
            <strong>{{ data.totalRevenueFormatted }}</strong>
            <p>All-time business revenue</p>
          </article>
          <article class="metric-card blue">
            <span class="metric-kicker">Orders</span>
            <strong>{{ data.totalOrders }}</strong>
            <p>{{ data.refundedOrders }} refunded orders</p>
          </article>
          <article class="metric-card purple">
            <span class="metric-kicker">Staff</span>
            <strong>{{ data.totalStaff }}</strong>
            <p>Team members across restaurants</p>
          </article>
        </section>

        <section class="ops-grid">
          <article class="ops-card emphasis">
            <div class="ops-head">
              <h3>Sub-merchant pipeline</h3>
              <span class="ops-count">{{ subMerchantCounts().total }}</span>
            </div>
            <div class="stack-bars">
              <div class="stack-bar active" [style.width.%]="subMerchantPct.active"></div>
              <div class="stack-bar pending" [style.width.%]="subMerchantPct.pending"></div>
              <div class="stack-bar rejected" [style.width.%]="subMerchantPct.rejected"></div>
            </div>
            <div class="legend-grid">
              <div class="legend-item">
                <span class="legend-dot active"></span>
                <div><strong>{{ subMerchantCounts().active }}</strong><span>Active</span></div>
              </div>
              <div class="legend-item">
                <span class="legend-dot pending"></span>
                <div><strong>{{ subMerchantCounts().pending }}</strong><span>Pending KYC</span></div>
              </div>
              <div class="legend-item">
                <span class="legend-dot rejected"></span>
                <div><strong>{{ subMerchantCounts().rejected }}</strong><span>Rejected</span></div>
              </div>
            </div>
          </article>

          <article class="ops-card">
            <div class="ops-head">
              <h3>Revenue quality</h3>
              <span class="ops-count">Net</span>
            </div>
            <div class="revenue-stage">
              <div class="revenue-column">
                <span class="column-label">Revenue</span>
                <div class="column-fill revenue" style="height: 100%"></div>
              </div>
              <div class="revenue-column">
                <span class="column-label">Refunds</span>
                <div class="column-fill refunds" [style.height.%]="refundPct"></div>
              </div>
            </div>
            <div class="detail-list">
              <div><span>Gross revenue</span><strong>{{ data.totalRevenueFormatted }}</strong></div>
              <div><span>Refunded</span><strong>{{ data.refundedAmountFormatted }}</strong></div>
              <div class="highlight"><span>Net revenue</span><strong>{{ netRevenueFormatted(data) }}</strong></div>
            </div>
          </article>

          <article class="ops-card">
            <div class="ops-head">
              <h3>Business health</h3>
              <span class="ops-count">{{ businessLivePct(data) }}%</span>
            </div>
            <div class="health-ring" [style.--pct]="businessLivePct(data)">
              <div class="health-ring-core">
                <strong>{{ data.liveBusinesses }}</strong>
                <span>live now</span>
              </div>
            </div>
            <div class="detail-list">
              <div><span>Total businesses</span><strong>{{ data.totalBusinesses }}</strong></div>
              <div><span>Avg orders per business</span><strong>{{ avgOrdersPerBusiness(data) }}</strong></div>
              <div><span>Total staff</span><strong>{{ data.totalStaff }}</strong></div>
            </div>
          </article>
        </section>

        <section class="action-board">
          <article class="action-panel">
            <div class="panel-head">
              <div>
                <h3>Priority Actions</h3>
                <p>What needs attention from operations right now.</p>
              </div>
              <span class="badge">{{ pendingCount() }}</span>
            </div>

            <div class="action-feed" *ngIf="hasPendingActions(); else noActions">
              <a class="feed-item" routerLink="/admin/sub-merchants" *ngIf="subMerchantCounts().pending > 0">
                <span class="feed-icon amber">⏳</span>
                <div>
                  <strong>{{ subMerchantCounts().pending }} KYC review pending</strong>
                  <p>Businesses are waiting for sub-merchant activation.</p>
                </div>
                <span class="feed-arrow">&rarr;</span>
              </a>
              <a class="feed-item" routerLink="/admin/sub-merchants" *ngIf="subMerchantCounts().rejected > 0">
                <span class="feed-icon red">⚠️</span>
                <div>
                  <strong>{{ subMerchantCounts().rejected }} KYC rejection{{ subMerchantCounts().rejected > 1 ? 's' : '' }}</strong>
                  <p>Rejected merchants need follow-up or resubmission help.</p>
                </div>
                <span class="feed-arrow">&rarr;</span>
              </a>
            </div>

            <ng-template #noActions>
              <div class="empty-stage compact">
                <div class="empty-stage-icon">✅</div>
                <strong>No urgent admin actions</strong>
                <p>The KYC and settlement queue is currently clear.</p>
              </div>
            </ng-template>
          </article>

          <article class="action-panel">
            <div class="panel-head">
              <div>
                <h3>Jump To</h3>
                <p>Fast paths into the operational pages that matter most.</p>
              </div>
            </div>

            <div class="jump-grid">
              <a class="jump-card primary" routerLink="/admin/businesses">
                <span class="jump-icon">🏪</span>
                <div>
                  <strong>Businesses</strong>
                  <span>{{ data.totalBusinesses }} registered</span>
                </div>
              </a>
              <a class="jump-card" routerLink="/admin/sub-merchants">
                <span class="jump-icon">👥</span>
                <div>
                  <strong>Sub-Merchants</strong>
                  <span>{{ subMerchantCounts().active }} active, {{ subMerchantCounts().pending }} pending</span>
                </div>
              </a>
              <a class="jump-card" routerLink="/admin/payment-dashboard">
                <span class="jump-icon">💳</span>
                <div>
                  <strong>Payments</strong>
                  <span>Gateway performance and payment mix</span>
                </div>
              </a>
              <a class="jump-card" routerLink="/admin/settlements">
                <span class="jump-icon">💸</span>
                <div>
                  <strong>Settlements</strong>
                  <span>Track payout and commission summaries</span>
                </div>
              </a>
            </div>
          </article>
        </section>

        <section class="guidance-panel">
          <div class="panel-head">
            <div>
              <h3>Recommended Admin Flow</h3>
              <p>A simpler sequence for handling daily platform operations.</p>
            </div>
          </div>

          <div class="guidance-grid">
            <div class="guidance-step">
              <span class="step-num">1</span>
              <div>
                <strong>Check KYC queue</strong>
                <p>Review pending and rejected sub-merchants first so payments do not stall.</p>
              </div>
            </div>
            <div class="guidance-step">
              <span class="step-num">2</span>
              <div>
                <strong>Monitor payment health</strong>
                <p>Use the payment dashboard to spot drops in success rate or unusual failure patterns.</p>
              </div>
            </div>
            <div class="guidance-step">
              <span class="step-num">3</span>
              <div>
                <strong>Review business growth</strong>
                <p>Inspect businesses with low activity, missing setup, or slow onboarding progress.</p>
              </div>
            </div>
          </div>
        </section>
      </ng-container>

      <ng-template #loading>
        <div class="load-state">
          <div class="load-spinner"></div>
          <p>Loading dashboard...</p>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .dash-shell {
      display: grid;
      gap: 1.1rem;
      max-width: 1480px;
      margin: 0 auto;
      padding: 1.5rem;
    }

    .hero-panel {
      display: grid;
      grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.9fr);
      gap: 1rem;
      padding: 1.35rem;
      border-radius: 26px;
      border: 1px solid var(--line);
      background:
        radial-gradient(circle at top left, rgba(181, 106, 45, 0.18), transparent 30%),
        radial-gradient(circle at right, rgba(74, 144, 217, 0.08), transparent 24%),
        linear-gradient(180deg, rgba(255,255,255,0.96), rgba(247,239,228,0.98));
      box-shadow: var(--shadow-md);
    }

    .hero-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      margin-bottom: 0.7rem;
      padding: 0.4rem 0.75rem;
      border-radius: 999px;
      background: rgba(29,123,95,0.1);
      color: var(--accent);
      font-size: 0.78rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .hero-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--success);
      box-shadow: 0 0 0 4px rgba(34,197,94,0.18);
    }

    .hero-copy h1 {
      margin: 0;
      font-size: clamp(1.9rem, 3vw, 2.8rem);
      line-height: 1.04;
    }

    .hero-text {
      max-width: 44rem;
      margin: 0.7rem 0 0;
      font-size: 1rem;
      color: var(--muted);
      line-height: 1.55;
    }

    .hero-facts {
      display: flex;
      flex-wrap: wrap;
      gap: 0.6rem;
      margin-top: 1rem;
    }

    .hero-facts span {
      display: inline-flex;
      align-items: center;
      padding: 0.45rem 0.7rem;
      border-radius: 999px;
      background: rgba(255,255,255,0.72);
      border: 1px solid rgba(181, 106, 45, 0.14);
      font-size: 0.84rem;
      font-weight: 600;
    }

    .hero-aside {
      display: grid;
      gap: 0.85rem;
      align-content: start;
    }

    .hero-kpi {
      padding: 1rem 1.1rem;
      border-radius: 20px;
      background: rgba(255,255,255,0.84);
      border: 1px solid var(--line);
    }

    .hero-kpi.slim {
      background: linear-gradient(180deg, rgba(246,241,255,0.88), rgba(255,255,255,0.9));
    }

    .hero-kpi-label {
      display: block;
      margin-bottom: 0.4rem;
      font-size: 0.74rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--muted);
      font-weight: 800;
    }

    .hero-kpi strong {
      display: block;
      font-size: 1.45rem;
      margin-bottom: 0.3rem;
    }

    .hero-kpi p {
      margin: 0;
      color: var(--muted);
      font-size: 0.84rem;
      line-height: 1.45;
    }

    .metric-band {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 0.9rem;
    }

    .metric-card {
      padding: 1.15rem 1.1rem;
      border-radius: 22px;
      border: 1px solid var(--line);
      box-shadow: var(--shadow-sm);
      background: var(--panel);
    }

    .metric-card.gold { background: linear-gradient(180deg, rgba(255, 246, 235, 0.96), rgba(255,255,255,0.98)); }
    .metric-card.green { background: linear-gradient(180deg, rgba(238,249,244,0.96), rgba(255,255,255,0.98)); }
    .metric-card.blue { background: linear-gradient(180deg, rgba(240,247,255,0.96), rgba(255,255,255,0.98)); }
    .metric-card.purple { background: linear-gradient(180deg, rgba(246,241,255,0.96), rgba(255,255,255,0.98)); }

    .metric-kicker {
      display: block;
      margin-bottom: 0.5rem;
      font-size: 0.74rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--muted);
      font-weight: 800;
    }

    .metric-card strong {
      display: block;
      font-size: 2rem;
      line-height: 1;
      margin-bottom: 0.35rem;
    }

    .metric-card p {
      margin: 0;
      font-size: 0.84rem;
      color: var(--muted);
    }

    .ops-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 0.95rem;
    }

    .ops-card {
      padding: 1.15rem;
      border-radius: 22px;
      border: 1px solid var(--line);
      background: var(--panel);
      box-shadow: var(--shadow-sm);
    }

    .ops-card.emphasis {
      background: linear-gradient(180deg, rgba(255,255,255,0.95), rgba(247,239,228,0.92));
    }

    .ops-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 0.85rem;
    }

    .ops-head h3 {
      margin: 0;
      font-size: 1rem;
    }

    .ops-count {
      font-size: 1rem;
      font-weight: 800;
      color: var(--brand);
    }

    .stack-bars {
      display: flex;
      height: 12px;
      border-radius: 999px;
      overflow: hidden;
      background: rgba(181, 106, 45, 0.08);
      margin-bottom: 0.95rem;
    }

    .stack-bar.active { background: var(--accent); }
    .stack-bar.pending { background: var(--warn); }
    .stack-bar.rejected { background: var(--danger); }

    .legend-grid {
      display: grid;
      gap: 0.65rem;
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.65rem;
    }

    .legend-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .legend-dot.active { background: var(--accent); }
    .legend-dot.pending { background: var(--warn); }
    .legend-dot.rejected { background: var(--danger); }

    .legend-item strong {
      display: block;
      font-size: 0.96rem;
      line-height: 1.1;
    }

    .legend-item span:last-child {
      color: var(--muted);
      font-size: 0.8rem;
    }

    .revenue-stage {
      display: flex;
      justify-content: center;
      align-items: end;
      gap: 1.25rem;
      min-height: 150px;
      padding: 0.5rem 0 1rem;
    }

    .revenue-column {
      display: flex;
      flex-direction: column-reverse;
      align-items: center;
      gap: 0.6rem;
    }

    .column-fill {
      width: 56px;
      min-height: 8px;
      border-radius: 16px 16px 6px 6px;
    }

    .column-fill.revenue {
      background: linear-gradient(180deg, var(--brand), var(--brand-deep));
    }

    .column-fill.refunds {
      background: linear-gradient(180deg, var(--danger), var(--danger-dark));
    }

    .column-label {
      font-size: 0.78rem;
      color: var(--muted);
      font-weight: 700;
    }

    .detail-list {
      display: grid;
      gap: 0.55rem;
    }

    .detail-list div {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 0.75rem;
      font-size: 0.84rem;
    }

    .detail-list span { color: var(--muted); }

    .detail-list strong {
      font-size: 0.95rem;
    }

    .detail-list .highlight {
      padding-top: 0.45rem;
      border-top: 1px solid var(--line);
    }

    .detail-list .highlight span,
    .detail-list .highlight strong {
      color: var(--ink);
      font-weight: 800;
    }

    .health-ring {
      --pct: 0;
      width: 144px;
      height: 144px;
      margin: 0 auto 1rem;
      border-radius: 50%;
      background: conic-gradient(var(--accent) calc(var(--pct) * 1%), rgba(29,123,95,0.12) calc(var(--pct) * 1%));
      display: grid;
      place-items: center;
    }

    .health-ring-core {
      width: 114px;
      height: 114px;
      border-radius: 50%;
      background: rgba(255,252,247,0.96);
      border: 1px solid rgba(29,123,95,0.12);
      display: grid;
      place-items: center;
      text-align: center;
    }

    .health-ring-core strong {
      display: block;
      font-size: 1.5rem;
      line-height: 1;
    }

    .health-ring-core span {
      font-size: 0.72rem;
      color: var(--muted);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 700;
    }

    .action-board {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      gap: 1rem;
    }

    .action-panel {
      padding: 1.2rem;
      border-radius: 22px;
      border: 1px solid var(--line);
      background: var(--panel);
      box-shadow: var(--shadow-sm);
    }

    .panel-head {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 0.85rem;
      margin-bottom: 0.9rem;
    }

    .panel-head h3 {
      margin: 0 0 0.24rem;
      font-size: 1.08rem;
    }

    .panel-head p {
      margin: 0;
      color: var(--muted);
      font-size: 0.84rem;
    }

    .badge {
      min-width: 28px;
      height: 28px;
      border-radius: 999px;
      background: var(--danger);
      color: #fff;
      display: inline-grid;
      place-items: center;
      font-size: 0.78rem;
      font-weight: 800;
    }

    .action-feed {
      display: grid;
      gap: 0.7rem;
    }

    .feed-item {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      gap: 0.75rem;
      align-items: center;
      padding: 0.9rem 0.95rem;
      border-radius: 18px;
      border: 1px solid var(--line);
      background: linear-gradient(180deg, rgba(255,255,255,0.84), rgba(247,239,228,0.86));
      text-decoration: none;
      color: inherit;
    }

    .feed-icon {
      width: 38px;
      height: 38px;
      border-radius: 12px;
      display: grid;
      place-items: center;
      font-size: 1rem;
      background: rgba(181, 106, 45, 0.1);
    }

    .feed-icon.amber { background: rgba(230,126,34,0.14); }
    .feed-icon.red { background: rgba(166,55,47,0.14); }

    .feed-item strong {
      display: block;
      margin-bottom: 0.16rem;
      font-size: 0.92rem;
    }

    .feed-item p {
      margin: 0;
      color: var(--muted);
      font-size: 0.8rem;
      line-height: 1.4;
    }

    .feed-arrow {
      color: var(--brand);
      font-weight: 800;
      font-size: 1.1rem;
    }

    .jump-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 0.75rem;
    }

    .jump-card {
      display: flex;
      align-items: center;
      gap: 0.8rem;
      padding: 0.95rem 1rem;
      border-radius: 18px;
      border: 1px solid var(--line);
      background: linear-gradient(180deg, rgba(255,255,255,0.82), rgba(246,240,230,0.84));
      text-decoration: none;
      color: inherit;
      transition: transform 0.18s ease, box-shadow 0.18s ease;
    }

    .jump-card:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-sm);
    }

    .jump-card.primary {
      background: linear-gradient(135deg, rgba(181,106,45,0.12), rgba(255,255,255,0.95));
    }

    .jump-icon {
      width: 42px;
      height: 42px;
      border-radius: 14px;
      display: grid;
      place-items: center;
      background: rgba(181,106,45,0.1);
      font-size: 1.15rem;
      flex-shrink: 0;
    }

    .jump-card strong {
      display: block;
      margin-bottom: 0.14rem;
      font-size: 0.92rem;
    }

    .jump-card span:last-child {
      color: var(--muted);
      font-size: 0.8rem;
      line-height: 1.35;
    }

    .empty-stage {
      min-height: 220px;
      display: grid;
      place-items: center;
      text-align: center;
      padding: 1.75rem 1rem;
      border-radius: 20px;
      border: 1px dashed rgba(181,106,45,0.22);
      background: linear-gradient(180deg, rgba(255,255,255,0.9), rgba(246,240,230,0.82));
    }

    .empty-stage.compact {
      min-height: 180px;
    }

    .empty-stage-icon {
      font-size: 2.3rem;
      margin-bottom: 0.6rem;
    }

    .empty-stage strong {
      display: block;
      margin-bottom: 0.28rem;
      font-size: 1.08rem;
    }

    .empty-stage p {
      margin: 0;
      color: var(--muted);
      max-width: 22rem;
      line-height: 1.45;
    }

    .guidance-panel {
      padding: 1.2rem;
      border-radius: 22px;
      border: 1px solid var(--line);
      background: linear-gradient(180deg, rgba(255,255,255,0.94), rgba(246,240,230,0.88));
      box-shadow: var(--shadow-sm);
    }

    .guidance-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 0.8rem;
    }

    .guidance-step {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.95rem 1rem;
      border-radius: 18px;
      border: 1px solid var(--line);
      background: rgba(255,255,255,0.82);
    }

    .step-num {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      display: grid;
      place-items: center;
      background: rgba(181,106,45,0.12);
      color: var(--brand-deep);
      font-weight: 800;
      flex-shrink: 0;
    }

    .guidance-step strong {
      display: block;
      margin-bottom: 0.16rem;
      font-size: 0.9rem;
    }

    .guidance-step p {
      margin: 0;
      color: var(--muted);
      font-size: 0.8rem;
      line-height: 1.42;
    }

    .load-state {
      text-align: center;
      padding: 4rem 1rem;
      color: var(--muted);
    }

    .load-spinner {
      width: 28px;
      height: 28px;
      border: 3px solid var(--line);
      border-top-color: var(--brand);
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
      margin: 0 auto 0.75rem;
    }

    @media (max-width: 1100px) {
      .hero-panel,
      .action-board {
        grid-template-columns: 1fr;
      }
      .metric-band,
      .ops-grid,
      .guidance-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }

    @media (max-width: 720px) {
      .metric-band,
      .ops-grid,
      .jump-grid,
      .guidance-grid {
        grid-template-columns: 1fr;
      }
      .hero-panel {
        padding: 1rem;
      }
      .hero-copy h1 {
        font-size: 1.75rem;
      }
      .feed-item {
        grid-template-columns: 1fr;
      }
      .feed-arrow {
        display: none;
      }
      .health-ring {
        width: 132px;
        height: 132px;
      }
      .health-ring-core {
        width: 102px;
        height: 102px;
      }
    }
  `]
})
export class PlatformDashboardPageComponent {
  private readonly api = inject(AdminApiService);

  readonly summary = toSignal(
    this.api.getDashboardSummary().pipe(
      catchError(() => of(null)),
      map(s => s ? {
        ...s,
        totalRevenueFormatted: formatCurrency(s.totalRevenue),
        refundedAmountFormatted: formatCurrency(s.refundedAmount)
      } : null)
    )
  );

  readonly subMerchantCounts = toSignal(
    this.api.getSubMerchants().pipe(
      catchError(() => of([])),
      map(list => ({
        total: list.length,
        active: list.filter(s => s.status === 'ACTIVE').length,
        pending: list.filter(s => s.status === 'PENDING_KYC' || s.status === 'KYC_SUBMITTED').length,
        rejected: list.filter(s => s.status === 'REJECTED' || s.status === 'FAILED').length
      }))
    ),
    { initialValue: { total: 0, active: 0, pending: 0, rejected: 0 } }
  );

  get subMerchantPct() {
    const c = this.subMerchantCounts();
    const total = c.active + c.pending + c.rejected || 1;
    return {
      active: (c.active / total) * 100,
      pending: (c.pending / total) * 100,
      rejected: (c.rejected / total) * 100
    };
  }

  get refundPct(): number {
    const s = this.summary();
    if (!s || s.totalRevenue <= 0) return 0;
    return Math.min(100, (s.refundedAmount / s.totalRevenue) * 100);
  }

  hasPendingActions(): boolean {
    const c = this.subMerchantCounts();
    return c.pending > 0 || c.rejected > 0;
  }

  pendingCount(): number {
    const c = this.subMerchantCounts();
    return c.pending + c.rejected;
  }

  netRevenueFormatted(data: any): string {
    return formatCurrency(data.totalRevenue - data.refundedAmount);
  }

  businessLivePct(data: any): number {
    if (data.totalBusinesses <= 0) return 0;
    return Math.round((data.liveBusinesses / data.totalBusinesses) * 100);
  }

  avgOrdersPerBusiness(data: any): string {
    if (data.totalBusinesses <= 0) return '0';
    return Math.round(data.totalOrders / data.totalBusinesses).toLocaleString();
  }
}
