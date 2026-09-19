import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-not-found-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="error-stage">
      <div class="receipt-card" role="main" aria-label="Page Not Found Receipt">
        <!-- Serrated Receipt Paper Top Edge -->
        <div class="zigzag-edge zigzag-edge--top" aria-hidden="true"></div>

        <div class="receipt-content">
          <!-- Receipt Header -->
          <div class="receipt-header">
            <div class="brand-badge">
              <span class="brand-logo-txt">KB</span>
            </div>
            <h1 class="receipt-title">KhanaBook POS</h1>
            <p class="receipt-sub">Terminal #01 &bull; Kitchen Ticket #404</p>
            <div class="dashed-divider"></div>
          </div>

          <!-- Error Ticket Metadata -->
          <div class="ticket-meta-grid">
            <div class="meta-item">
              <span class="meta-label">TABLE</span>
              <strong class="meta-value">VOID / 404</strong>
            </div>
            <div class="meta-item">
              <span class="meta-label">SERVER</span>
              <strong class="meta-value">ROUTER</strong>
            </div>
            <div class="meta-item">
              <span class="meta-label">STATUS</span>
              <strong class="meta-value text-amber">PAGE_NOT_FOUND</strong>
            </div>
            <div class="meta-item">
              <span class="meta-label">TIME</span>
              <strong class="meta-value tabular-num">{{ currentTime }}</strong>
            </div>
          </div>

          <div class="dashed-divider"></div>

          <!-- Line Items Table -->
          <div class="receipt-body">
            <p class="body-message">
              The kitchen could not fulfill this route. The table or endpoint you were looking for has been moved, cleared, or does not exist.
            </p>

            <div class="bill-line">
              <span class="line-name">1x Requested URI</span>
              <span class="line-price tabular-num">₹ 0.00</span>
            </div>
            <div class="bill-line bill-line--secondary">
              <span class="line-sub">{{ currentUrl }}</span>
            </div>
          </div>

          <div class="dashed-divider"></div>

          <!-- Barcode Graphic -->
          <div class="barcode-wrapper" aria-hidden="true">
            <div class="barcode-lines">
              <span class="bar b1"></span><span class="bar b3"></span><span class="bar b2"></span>
              <span class="bar b1"></span><span class="bar b4"></span><span class="bar b2"></span>
              <span class="bar b3"></span><span class="bar b1"></span><span class="bar b2"></span>
              <span class="bar b4"></span><span class="bar b1"></span><span class="bar b3"></span>
              <span class="bar b2"></span><span class="bar b1"></span><span class="bar b3"></span>
              <span class="bar b4"></span><span class="bar b2"></span><span class="bar b1"></span>
              <span class="bar b3"></span><span class="bar b2"></span><span class="bar b4"></span>
              <span class="bar b1"></span><span class="bar b2"></span><span class="bar b3"></span>
            </div>
            <span class="barcode-digits">ERR-404-VOID-ROUTE</span>
          </div>

          <!-- Tactile Actions (CTA.gallery & 60fps.design) -->
          <div class="receipt-actions">
            <button
              type="button"
              class="primary-btn-tactile"
              (click)="navigateToDashboard()">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
              Return to POS Dashboard
            </button>

            <a routerLink="/business/orders" class="ghost-btn-tactile">
              View Active Orders
            </a>
          </div>
        </div>

        <!-- Serrated Receipt Paper Bottom Edge -->
        <div class="zigzag-edge zigzag-edge--bottom" aria-hidden="true"></div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      background: #F1F5F9;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', sans-serif;
    }

    .error-stage {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2.5rem 1.5rem;
      background: radial-gradient(circle at 50% 20%, #EDE9FE 0%, #F1F5F9 60%);
    }

    /* ── The Physical Thermal Receipt Card (Tactile Craft) ── */
    .receipt-card {
      position: relative;
      width: 100%;
      max-width: 440px;
      background: #FFFFFF;
      border-radius: 4px;
      box-shadow:
        0 20px 40px -15px rgba(0, 0, 0, 0.08),
        0 0 0 1px rgba(0, 0, 0, 0.05);
      animation: receiptSlide 280ms cubic-bezier(0.16, 1, 0.3, 1);
    }

    @keyframes receiptSlide {
      from { opacity: 0; transform: translateY(16px) scale(0.98); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }

    /* Zigzag paper edges */
    .zigzag-edge {
      position: absolute;
      left: 0;
      right: 0;
      height: 10px;
      background-size: 16px 10px;
      background-repeat: repeat-x;
    }
    .zigzag-edge--top {
      top: -10px;
      background-image: linear-gradient(135deg, #FFFFFF 50%, transparent 50%),
                        linear-gradient(225deg, #FFFFFF 50%, transparent 50%);
    }
    .zigzag-edge--bottom {
      bottom: -10px;
      background-image: linear-gradient(45deg, #FFFFFF 50%, transparent 50%),
                        linear-gradient(315deg, #FFFFFF 50%, transparent 50%);
    }

    .receipt-content {
      padding: 2.25rem 2rem;
    }

    /* Header */
    .receipt-header {
      text-align: center;
    }
    .brand-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: #5D45FD;
      color: #FFFFFF;
      margin-bottom: 0.75rem;
    }
    .brand-logo-txt {
      font-weight: 800;
      font-size: 0.95rem;
      letter-spacing: -0.02em;
    }
    .receipt-title {
      font-size: 1.25rem;
      font-weight: 800;
      color: #0F172A;
      margin: 0 0 0.25rem;
      letter-spacing: -0.02em;
    }
    .receipt-sub {
      font-size: 0.78rem;
      font-weight: 600;
      color: #64748B;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin: 0;
    }

    .dashed-divider {
      border-bottom: 1.5px dashed #CBD5E1;
      margin: 1.25rem 0;
    }

    /* Meta Grid */
    .ticket-meta-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.85rem 1rem;
    }
    .meta-item {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
    }
    .meta-label {
      font-size: 0.68rem;
      font-weight: 700;
      color: #94A3B8;
      letter-spacing: 0.06em;
    }
    .meta-value {
      font-size: 0.86rem;
      font-weight: 700;
      color: #1E293B;
    }
    .text-amber {
      color: #D97706;
    }

    /* Body */
    .receipt-body {
      padding: 0.25rem 0;
    }
    .body-message {
      font-size: 0.84rem;
      color: #475569;
      line-height: 1.5;
      margin: 0 0 1rem;
    }
    .bill-line {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 0.86rem;
      font-weight: 700;
      color: #0F172A;
    }
    .bill-line--secondary {
      margin-top: 0.25rem;
    }
    .line-sub {
      font-size: 0.76rem;
      font-weight: 500;
      color: #94A3B8;
      word-break: break-all;
    }
    .tabular-num {
      font-variant-numeric: tabular-nums;
    }

    /* Barcode */
    .barcode-wrapper {
      text-align: center;
      padding: 0.5rem 0;
    }
    .barcode-lines {
      display: flex;
      align-items: stretch;
      justify-content: center;
      gap: 3px;
      height: 38px;
    }
    .bar {
      background: #1E293B;
      border-radius: 1px;
    }
    .b1 { width: 1.5px; }
    .b2 { width: 3px; }
    .b3 { width: 4.5px; }
    .b4 { width: 6px; }

    .barcode-digits {
      display: block;
      font-size: 0.68rem;
      font-weight: 700;
      color: #64748B;
      letter-spacing: 0.18em;
      margin-top: 0.45rem;
      font-family: 'Courier New', Courier, monospace;
    }

    /* CTA.gallery Tactile Buttons */
    .receipt-actions {
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      margin-top: 1.5rem;
    }
    .primary-btn-tactile {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      width: 100%;
      padding: 0.85rem 1.25rem;
      background: #5D45FD;
      color: #FFFFFF;
      border: none;
      border-radius: 12px;
      font-size: 0.9rem;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(93, 69, 253, 0.25);
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .primary-btn-tactile:hover {
      background: #4A32F0;
      box-shadow: 0 6px 18px rgba(93, 69, 253, 0.32);
    }
    .primary-btn-tactile:active {
      transform: scale(0.97);
      box-shadow: 0 2px 8px rgba(93, 69, 253, 0.2);
    }

    .ghost-btn-tactile {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      padding: 0.75rem 1.25rem;
      background: transparent;
      color: #475569;
      border: 1px solid #E2E8F0;
      border-radius: 12px;
      font-size: 0.86rem;
      font-weight: 600;
      text-decoration: none;
      transition: all 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .ghost-btn-tactile:hover {
      background: #F8FAFC;
      color: #0F172A;
      border-color: #CBD5E1;
    }
    .ghost-btn-tactile:active {
      transform: scale(0.97);
    }
  `]
})
export class NotFoundPageComponent {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  readonly currentUrl = this.router.url;
  readonly currentTime = new Date().toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: true
  });

  navigateToDashboard(): void {
    const destination = this.auth.getLandingPath();
    this.router.navigateByUrl(destination);
  }
}
