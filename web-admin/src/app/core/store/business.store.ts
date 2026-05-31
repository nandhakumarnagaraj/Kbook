import { Injectable, inject, signal, computed } from '@angular/core';
import { BusinessApiService } from '../services/business-api.service';

@Injectable({ providedIn: 'root' })
export class BusinessStore {
  private readonly api = inject(BusinessApiService);

  // ── Dashboard ───────────────────────────────────────────────────────────────

  private readonly _dashboard = signal<unknown>(null);
  private readonly _dashboardLoading = signal(false);
  private _dashboardFetchedAt = 0;

  readonly dashboard = this._dashboard.asReadonly();
  readonly dashboardLoading = this._dashboardLoading.asReadonly();

  loadDashboard(restaurantId: number, force = false) {
    const now = Date.now();
    if (!force && this._dashboard() && now - this._dashboardFetchedAt < 60_000) return;
    this._dashboardLoading.set(true);
    this.api.getDashboard(restaurantId).subscribe({
      next: (data) => { this._dashboard.set(data); this._dashboardFetchedAt = Date.now(); this._dashboardLoading.set(false); },
      error: () => this._dashboardLoading.set(false),
    });
  }

  invalidateDashboard() {
    this._dashboardFetchedAt = 0;
  }
}
