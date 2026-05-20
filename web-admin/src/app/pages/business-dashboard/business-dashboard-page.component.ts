import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { combineLatest, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMarketplaceSetup, MarketplaceOrder } from '../../core/models/api.models';
import { formatCurrency } from '../../shared/formatters';

@Component({
  selector: 'app-business-dashboard-page',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="dash-shell">
      <ng-container *ngIf="vm() as data; else loading">
        <section class="hero-panel">
          <div class="hero-copy">
            <div class="hero-badge">
              <span class="hero-dot"></span>
              Business live
            </div>
            <h1>{{ data.shopName || 'Dashboard' }}</h1>
            <p class="hero-text">Track today's sales, monitor online demand, and finish the setup steps blocking growth.</p>
            <div class="hero-facts">
              <span>{{ data.posOrderCount }} orders today</span>
              <span>{{ data.totalMenuItems }} menu items</span>
              <span>{{ data.totalStaff }} staff members</span>
            </div>
          </div>
        </section>

        <section class="command-strip">
          <a class="command-card primary" routerLink="/business/orders">
            <span class="command-icon">📋</span>
            <div>
              <strong>Open Orders</strong>
              <span>Review POS and marketplace activity</span>
            </div>
          </a>
          <a class="command-card" routerLink="/business/marketplace-setup">
            <span class="command-icon">🔗</span>
            <div>
              <strong>Marketplace Setup</strong>
              <span>Connect Swiggy and Zomato credentials</span>
            </div>
          </a>
          <a class="command-card" routerLink="/business/settings">
            <span class="command-icon">⚙️</span>
            <div>
              <strong>Business Settings</strong>
              <span>Update payments, GST, and profile data</span>
            </div>
          </a>
        </section>

        <section class="metric-band">
          <article class="metric-card warm">
            <span class="metric-kicker">Today's Revenue</span>
            <strong>{{ data.todayRevenueFormatted }}</strong>
            <p>{{ data.posOrderCount }} POS transaction{{ data.posOrderCount !== 1 ? 's' : '' }}</p>
          </article>
          <article class="metric-card cool">
            <span class="metric-kicker">Total Revenue</span>
            <strong>{{ data.totalRevenueFormatted }}</strong>
            <p>All recorded business revenue</p>
          </article>
          <article class="metric-card sky">
            <span class="metric-kicker">Online Orders</span>
            <strong>{{ data.marketplaceOrders.total }}</strong>
            <p>{{ data.marketplaceOrders.pending }} pending, {{ data.marketplaceOrders.ready }} ready</p>
          </article>
          <article class="metric-card violet">
            <span class="metric-kicker">Avg Order Value</span>
            <strong>{{ data.avgOrderValueFormatted }}</strong>
            <p>{{ data.totalOrders > 0 ? 'Based on all orders' : 'Waiting for first order' }}</p>
          </article>
        </section>

        <section class="spotlight-grid">
          <article class="spotlight-card pulse" *ngIf="data.marketplaceOrders.pending > 0">
            <div class="spotlight-head">
              <span class="spotlight-icon">🛵</span>
              <span class="spotlight-tag">Immediate Attention</span>
            </div>
            <strong>{{ data.marketplaceOrders.pending }} online order{{ data.marketplaceOrders.pending > 1 ? 's' : '' }} waiting</strong>
            <p>Unaccepted marketplace orders slow prep and hurt service metrics.</p>
            <a routerLink="/business/orders">Take action &rarr;</a>
          </article>

          <article class="spotlight-card">
            <div class="spotlight-head">
              <span class="spotlight-icon">🧾</span>
              <span class="spotlight-tag">Financial Snapshot</span>
            </div>
            <strong>{{ data.refundedAmountFormatted }} refunded</strong>
            <p>Keep an eye on refund volume as orders scale.</p>
          </article>
        </section>

        <section class="next-step-panel">
          <div class="surface-head">
            <div>
              <h3>What To Do Next</h3>
              <p>Recommended actions based on your current business setup.</p>
            </div>
          </div>

          <div class="next-step-grid">
            <a class="next-step-card" routerLink="/business/marketplace-setup" *ngIf="!data.setupChecks[0].ready">
              <span class="next-step-icon">🔗</span>
              <div>
                <strong>Connect marketplaces</strong>
                <p>Enable Zomato and Swiggy credentials to start receiving online orders.</p>
              </div>
            </a>

            <a class="next-step-card" routerLink="/business/settings" *ngIf="data.subMerchantStatus !== 'ACTIVE'">
              <span class="next-step-icon">💸</span>
              <div>
                <strong>Complete settlement setup</strong>
                <p>Finish payment onboarding so your payouts can go live.</p>
              </div>
            </a>

            <a class="next-step-card" routerLink="/business/menu" *ngIf="data.totalMenuItems === 0">
              <span class="next-step-icon">🍽️</span>
              <div>
                <strong>Add menu items</strong>
                <p>Your menu needs at least one item before customers can order.</p>
              </div>
            </a>

            <a class="next-step-card" routerLink="/business/staff" *ngIf="data.totalStaff === 0">
              <span class="next-step-icon">👤</span>
              <div>
                <strong>Add staff access</strong>
                <p>Set up at least one team member to keep daily operations moving.</p>
              </div>
            </a>

            <div class="next-step-card success-card" *ngIf="completedChecks(data) === data.setupChecks.length">
              <span class="next-step-icon">✅</span>
              <div>
                <strong>You're fully set up</strong>
                <p>Your business is ready. Focus on orders, menu updates, and service quality.</p>
              </div>
            </div>
          </div>
        </section>

        <section class="dashboard-grid">
          <article class="surface-card">
            <div class="surface-head">
              <div>
                <h3>Recent Orders</h3>
                <p>Latest POS activity in one place.</p>
              </div>
              <a routerLink="/business/orders">View all &rarr;</a>
            </div>

            <div class="order-feed" *ngIf="data.recentOrders.length > 0; else noOrders">
              <div class="order-row" *ngFor="let order of data.recentOrders">
                <div class="order-main">
                  <strong>{{ order.orderCode }}</strong>
                  <span>{{ order.customerName || 'Walk-in customer' }}</span>
                </div>
                <div class="order-side">
                  <span class="order-status" [class.paid]="order.paymentStatus === 'Paid'" [class.pending]="order.paymentStatus !== 'Paid'">
                    {{ order.orderStatus }}
                  </span>
                  <strong>{{ formatCurrencyValue(order.totalAmount) }}</strong>
                </div>
              </div>
            </div>

            <ng-template #noOrders>
              <div class="empty-stage">
                <div class="empty-stage-icon">📭</div>
                <strong>No orders yet</strong>
                <p>New orders will appear here as soon as billing starts.</p>
              </div>
            </ng-template>
          </article>

          <article class="surface-card">
            <div class="surface-head">
              <div>
                <h3>Setup Progress</h3>
                <p>Complete these steps to unlock payments and marketplaces.</p>
              </div>
              <span class="surface-count">{{ completedChecks(data) }}/{{ data.setupChecks.length }}</span>
            </div>

            <div class="checklist">
              <div class="check-row" *ngFor="let item of data.setupChecks" [class.done]="item.ready">
                <div class="check-bullet">{{ item.ready ? '✓' : '•' }}</div>
                <div class="check-copy">
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.detail }}</span>
                </div>
                <span class="check-pill" [class.done]="item.ready">{{ item.ready ? 'Done' : 'Pending' }}</span>
              </div>
            </div>
          </article>
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
      grid-template-columns: 1fr;
      gap: 1rem;
      padding: 1.35rem;
      border: 1px solid var(--line);
      border-radius: 24px;
      background:
        radial-gradient(circle at top left, rgba(181, 106, 45, 0.18), transparent 28%),
        radial-gradient(circle at bottom right, rgba(29, 123, 95, 0.08), transparent 24%),
        linear-gradient(180deg, rgba(255,255,255,0.95), rgba(247,239,228,0.96));
      box-shadow: var(--shadow-md);
    }

    .hero-copy h1 {
      margin: 0;
      font-size: clamp(1.8rem, 3vw, 2.6rem);
      line-height: 1.05;
    }

    .hero-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      margin-bottom: 0.7rem;
      padding: 0.4rem 0.75rem;
      border-radius: 999px;
      background: rgba(29, 123, 95, 0.1);
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
      box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.18);
    }

    .hero-text {
      max-width: 46rem;
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
      background: rgba(255,255,255,0.7);
      border: 1px solid rgba(181, 106, 45, 0.14);
      font-size: 0.84rem;
      font-weight: 600;
    }

    .command-strip {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 0.85rem;
    }

    .command-card {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      padding: 1rem 1.05rem;
      border: 1px solid var(--line);
      border-radius: 20px;
      background: var(--panel);
      text-decoration: none;
      color: inherit;
      transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
    }

    .command-card:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-sm);
      border-color: var(--line-strong);
    }

    .command-card.primary {
      background: linear-gradient(135deg, var(--brand) 0%, var(--brand-deep) 100%);
      color: #fff;
      border-color: transparent;
      box-shadow: 0 14px 28px rgba(126, 68, 23, 0.2);
    }

    .command-icon {
      width: 42px;
      height: 42px;
      border-radius: 14px;
      display: grid;
      place-items: center;
      background: rgba(181, 106, 45, 0.1);
      font-size: 1.15rem;
      flex-shrink: 0;
    }

    .command-card.primary .command-icon {
      background: rgba(255,255,255,0.18);
    }

    .command-card strong {
      display: block;
      font-size: 0.94rem;
      margin-bottom: 0.18rem;
    }

    .command-card span:last-child {
      font-size: 0.8rem;
      color: var(--muted);
      line-height: 1.35;
    }

    .command-card.primary span:last-child {
      color: rgba(255,255,255,0.86);
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
      background: var(--panel);
      box-shadow: var(--shadow-sm);
    }

    .metric-card.warm { background: linear-gradient(180deg, rgba(255, 246, 235, 0.95), rgba(255,255,255,0.98)); }
    .metric-card.cool { background: linear-gradient(180deg, rgba(238, 249, 244, 0.96), rgba(255,255,255,0.98)); }
    .metric-card.sky { background: linear-gradient(180deg, rgba(240, 247, 255, 0.96), rgba(255,255,255,0.98)); }
    .metric-card.violet { background: linear-gradient(180deg, rgba(246, 241, 255, 0.96), rgba(255,255,255,0.98)); }

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
      color: var(--muted);
      font-size: 0.84rem;
    }

    .spotlight-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 0.9rem;
    }

    .spotlight-card {
      padding: 1rem 1.05rem;
      border-radius: 20px;
      border: 1px solid var(--line);
      background: linear-gradient(180deg, rgba(255,255,255,0.92), rgba(247,239,228,0.82));
    }

    .spotlight-card.pulse {
      background: linear-gradient(135deg, rgba(230, 126, 34, 0.12), rgba(255,255,255,0.96));
      border-color: rgba(230,126,34,0.26);
    }

    .spotlight-head {
      display: flex;
      align-items: center;
      gap: 0.55rem;
      margin-bottom: 0.6rem;
    }

    .spotlight-icon {
      width: 36px;
      height: 36px;
      border-radius: 12px;
      display: grid;
      place-items: center;
      background: rgba(181, 106, 45, 0.1);
    }

    .spotlight-tag {
      font-size: 0.74rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 800;
      color: var(--muted);
    }

    .spotlight-card strong {
      display: block;
      margin-bottom: 0.32rem;
      font-size: 1rem;
    }

    .spotlight-card p {
      margin: 0 0 0.55rem;
      color: var(--muted);
      font-size: 0.84rem;
      line-height: 1.45;
    }

    .spotlight-card a {
      color: var(--brand);
      text-decoration: none;
      font-weight: 700;
      font-size: 0.84rem;
    }

    .dashboard-grid {
      display: grid;
      grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.9fr);
      gap: 1rem;
    }

    .next-step-panel {
      padding: 1.2rem;
      border-radius: 22px;
      border: 1px solid var(--line);
      background: linear-gradient(180deg, rgba(255,255,255,0.94), rgba(246,240,230,0.88));
      box-shadow: var(--shadow-sm);
    }

    .next-step-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 0.8rem;
    }

    .next-step-card {
      display: flex;
      align-items: flex-start;
      gap: 0.8rem;
      padding: 0.95rem 1rem;
      border-radius: 18px;
      border: 1px solid var(--line);
      background: rgba(255,255,255,0.82);
      text-decoration: none;
      color: inherit;
    }

    .next-step-card.success-card {
      background: rgba(232,245,240,0.88);
    }

    .next-step-icon {
      width: 38px;
      height: 38px;
      border-radius: 12px;
      display: grid;
      place-items: center;
      background: rgba(181,106,45,0.1);
      flex-shrink: 0;
    }

    .next-step-card strong {
      display: block;
      margin-bottom: 0.18rem;
      font-size: 0.92rem;
    }

    .next-step-card p {
      margin: 0;
      color: var(--muted);
      font-size: 0.81rem;
      line-height: 1.42;
    }

    .surface-card {
      padding: 1.2rem;
      border-radius: 22px;
      border: 1px solid var(--line);
      background: var(--panel);
      box-shadow: var(--shadow-sm);
    }

    .surface-head {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 0.85rem;
      margin-bottom: 0.95rem;
    }

    .surface-head h3 {
      margin: 0 0 0.22rem;
      font-size: 1.1rem;
    }

    .surface-head p {
      margin: 0;
      color: var(--muted);
      font-size: 0.84rem;
    }

    .surface-head a {
      color: var(--brand);
      font-size: 0.84rem;
      text-decoration: none;
      font-weight: 700;
      white-space: nowrap;
    }

    .surface-count {
      font-size: 1rem;
      font-weight: 800;
      color: var(--brand);
    }

    .order-feed {
      display: grid;
      gap: 0.65rem;
    }

    .order-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      padding: 0.85rem 0.95rem;
      border-radius: 16px;
      border: 1px solid var(--line);
      background: linear-gradient(180deg, rgba(255,255,255,0.72), rgba(246,240,230,0.84));
    }

    .order-main strong {
      display: block;
      margin-bottom: 0.16rem;
      font-size: 0.92rem;
    }

    .order-main span {
      color: var(--muted);
      font-size: 0.82rem;
    }

    .order-side {
      display: flex;
      align-items: center;
      gap: 0.7rem;
      flex-wrap: wrap;
      justify-content: flex-end;
    }

    .order-side strong {
      font-size: 0.94rem;
    }

    .order-status {
      padding: 0.28rem 0.62rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 800;
      background: rgba(181, 106, 45, 0.12);
      color: var(--brand-deep);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .order-status.paid {
      background: rgba(29,123,95,0.14);
      color: var(--accent);
    }

    .order-status.pending {
      background: rgba(230,126,34,0.13);
      color: #b56a2d;
    }

    .empty-stage {
      min-height: 280px;
      display: grid;
      place-items: center;
      text-align: center;
      padding: 2rem 1rem;
      border-radius: 20px;
      background:
        radial-gradient(circle at top, rgba(181, 106, 45, 0.08), transparent 42%),
        linear-gradient(180deg, rgba(255,255,255,0.88), rgba(246,240,230,0.85));
      border: 1px dashed rgba(181, 106, 45, 0.24);
    }

    .empty-stage-icon {
      font-size: 2.3rem;
      margin-bottom: 0.6rem;
    }

    .empty-stage strong {
      display: block;
      font-size: 1.15rem;
      margin-bottom: 0.28rem;
    }

    .empty-stage p {
      margin: 0;
      color: var(--muted);
      max-width: 22rem;
      line-height: 1.45;
    }

    .checklist {
      display: grid;
      gap: 0.65rem;
    }

    .check-row {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      gap: 0.75rem;
      align-items: center;
      padding: 0.85rem 0.9rem;
      border-radius: 16px;
      border: 1px solid var(--line);
      background: rgba(246,240,230,0.72);
    }

    .check-row.done {
      background: rgba(232,245,240,0.88);
    }

    .check-bullet {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      display: grid;
      place-items: center;
      background: rgba(181, 106, 45, 0.12);
      color: var(--brand);
      font-weight: 800;
      flex-shrink: 0;
    }

    .check-row.done .check-bullet {
      background: rgba(29,123,95,0.14);
      color: var(--accent);
    }

    .check-copy strong {
      display: block;
      margin-bottom: 0.14rem;
      font-size: 0.9rem;
    }

    .check-copy span {
      display: block;
      color: var(--muted);
      font-size: 0.8rem;
    }

    .check-pill {
      padding: 0.3rem 0.62rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 800;
      background: rgba(230,126,34,0.13);
      color: #b56a2d;
      white-space: nowrap;
    }

    .check-pill.done {
      background: rgba(29,123,95,0.14);
      color: var(--accent);
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
      .dashboard-grid {
        grid-template-columns: 1fr;
      }
      .metric-band,
      .spotlight-grid,
      .command-strip,
      .next-step-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }

    @media (max-width: 720px) {
      .metric-band,
      .spotlight-grid,
      .command-strip,
      .next-step-grid {
        grid-template-columns: 1fr;
      }
      .metric-card {
        border-color: var(--line-strong);
        box-shadow: var(--shadow-md);
      }
      .hero-panel {
        padding: 1rem;
      }
      .hero-copy h1 {
        font-size: 1.65rem;
      }
      .order-row,
      .check-row {
        grid-template-columns: 1fr;
        align-items: flex-start;
      }
      .order-side {
        justify-content: flex-start;
      }
    }
  `]
})
export class BusinessDashboardPageComponent {
  private readonly api = inject(BusinessApiService);

  readonly vm = toSignal(
    combineLatest([
      this.api.getDashboard().pipe(catchError(() => of(null))),
      this.api.getMarketplaceSetup().pipe(catchError(() => of(null as BusinessMarketplaceSetup | null))),
      this.api.getMarketplaceOrders().pipe(catchError(() => of([] as MarketplaceOrder[])))
    ]).pipe(
      map(([data, setup, orders]) => {
        if (!data) return null;
        const totalMarketplaceOrders = orders.length;
        const pendingMarketplace = orders.filter(o => o.orderStatus === 'pending').length;
        const readyMarketplace = orders.filter(o => o.orderStatus === 'ready').length;
        const completedMarketplace = orders.filter(o => o.orderStatus === 'completed').length;
        const totalOrders = data.posOrderCount + totalMarketplaceOrders;

        return {
          ...data,
          totalRevenueFormatted: formatCurrency(data.totalRevenue),
          todayRevenueFormatted: formatCurrency(data.todayRevenue),
          refundedAmountFormatted: formatCurrency(data.refundedAmount),
          avgOrderValueFormatted: totalOrders > 0 ? formatCurrency(data.totalRevenue / totalOrders) : formatCurrency(0),
          totalOrders,
          subMerchantStatus: setup?.subMerchantStatus || 'NOT_STARTED',
          marketplaceOrders: {
            total: totalMarketplaceOrders,
            pending: pendingMarketplace,
            ready: readyMarketplace,
            completed: completedMarketplace
          },
          setupChecks: [
            { label: 'Marketplace', ready: false, detail: 'Configure in Marketplace Setup' },
            { label: 'Easebuzz', ready: setup?.subMerchantStatus === 'ACTIVE', detail: 'Payment gateway ready for settlements' },
            { label: 'Staff & Menu', ready: data.totalStaff > 0 && data.totalMenuItems > 0, detail: `${data.totalStaff} staff, ${data.totalMenuItems} items` }
          ]
        };
      })
    )
  );

  completedChecks(data: any): number {
    return data.setupChecks?.filter((c: any) => c.ready).length || 0;
  }

  setupPercent(data: any): number {
    const total = data.setupChecks?.length || 1;
    return Math.round((this.completedChecks(data) / total) * 100);
  }

  formatCurrencyValue(v: number): string {
    return formatCurrency(v);
  }
}
