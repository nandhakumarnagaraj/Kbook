import { Injectable, inject, signal, computed } from '@angular/core';
import { AdminApiService } from '../services/admin-api.service';
import {
  AdminBusinessListItem,
  AdminDashboardSummary,
  AdminTransaction,
  AdminSettlement,
  AdminCommission,
  EasebuzzSubMerchant,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class AdminStore {
  private readonly api = inject(AdminApiService);

  // ── Dashboard Summary ───────────────────────────────────────────────────────

  private readonly _summary = signal<AdminDashboardSummary | null>(null);
  private readonly _summaryLoading = signal(false);
  private _summaryFetchedAt = 0;

  readonly summary = this._summary.asReadonly();
  readonly summaryLoading = this._summaryLoading.asReadonly();

  loadSummary(force = false) {
    const now = Date.now();
    if (!force && this._summary() && now - this._summaryFetchedAt < 300_000) return;
    this._summaryLoading.set(true);
    this.api.getDashboardSummary().subscribe({
      next: (data) => { this._summary.set(data); this._summaryFetchedAt = Date.now(); this._summaryLoading.set(false); },
      error: () => this._summaryLoading.set(false),
    });
  }

  // ── Businesses ──────────────────────────────────────────────────────────────

  private readonly _businesses = signal<AdminBusinessListItem[]>([]);
  private readonly _businessesLoading = signal(false);
  private _businessesFetchedAt = 0;

  readonly businesses = this._businesses.asReadonly();
  readonly businessesLoading = this._businessesLoading.asReadonly();
  readonly totalBusinesses = computed(() => this._businesses().length);
  readonly liveBusinesses = computed(() => this._businesses().filter(b => b.websiteEnabled).length);

  loadBusinesses(force = false) {
    const now = Date.now();
    if (!force && this._businesses().length && now - this._businessesFetchedAt < 300_000) return;
    this._businessesLoading.set(true);
    this.api.getBusinesses().subscribe({
      next: (data) => { this._businesses.set(data); this._businessesFetchedAt = Date.now(); this._businessesLoading.set(false); },
      error: () => this._businessesLoading.set(false),
    });
  }

  updateBusiness(id: number, updates: Partial<AdminBusinessListItem>) {
    this._businesses.update(list =>
      list.map(b => b.restaurantId === id ? { ...b, ...updates } : b)
    );
  }

  // ── Sub-Merchants ───────────────────────────────────────────────────────────

  private readonly _subMerchants = signal<EasebuzzSubMerchant[]>([]);
  private readonly _subMerchantsLoading = signal(false);
  private _subMerchantsFetchedAt = 0;

  readonly subMerchants = this._subMerchants.asReadonly();
  readonly subMerchantsLoading = this._subMerchantsLoading.asReadonly();

  loadSubMerchants(force = false) {
    const now = Date.now();
    if (!force && this._subMerchants().length && now - this._subMerchantsFetchedAt < 300_000) return;
    this._subMerchantsLoading.set(true);
    this.api.getSubMerchants().subscribe({
      next: (data) => { this._subMerchants.set(data); this._subMerchantsFetchedAt = Date.now(); this._subMerchantsLoading.set(false); },
      error: () => this._subMerchantsLoading.set(false),
    });
  }

  // ── Transactions ────────────────────────────────────────────────────────────

  private readonly _transactions = signal<AdminTransaction[]>([]);
  private readonly _transactionsLoading = signal(false);

  readonly transactions = this._transactions.asReadonly();
  readonly transactionsLoading = this._transactionsLoading.asReadonly();

  loadTransactions(page: number, size: number, status?: string, restaurantId?: number) {
    this._transactionsLoading.set(true);
    this.api.getTransactions(page, size, status, restaurantId).subscribe({
      next: (data) => { this._transactions.set(data); this._transactionsLoading.set(false); },
      error: () => this._transactionsLoading.set(false),
    });
  }

  // ── Settlements ─────────────────────────────────────────────────────────────

  private readonly _settlements = signal<AdminSettlement[]>([]);
  private readonly _settlementsLoading = signal(false);

  readonly settlements = this._settlements.asReadonly();
  readonly settlementsLoading = this._settlementsLoading.asReadonly();

  loadSettlements() {
    this._settlementsLoading.set(true);
    this.api.getSettlements().subscribe({
      next: (data) => { this._settlements.set(data); this._settlementsLoading.set(false); },
      error: () => this._settlementsLoading.set(false),
    });
  }

  // ── Commissions ─────────────────────────────────────────────────────────────

  private readonly _commissions = signal<AdminCommission[]>([]);
  private readonly _commissionsLoading = signal(false);

  readonly commissions = this._commissions.asReadonly();
  readonly commissionsLoading = this._commissionsLoading.asReadonly();

  loadCommissions() {
    this._commissionsLoading.set(true);
    this.api.getCommissions().subscribe({
      next: (data) => { this._commissions.set(data); this._commissionsLoading.set(false); },
      error: () => this._commissionsLoading.set(false),
    });
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  invalidateAll() {
    this._summaryFetchedAt = 0;
    this._businessesFetchedAt = 0;
    this._subMerchantsFetchedAt = 0;
  }
}
