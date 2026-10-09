import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, map } from 'rxjs';
import {
  AdminBusinessDetail, AdminBusinessListItem, AdminDashboardSummary,
  FeatureFlagAdminItem, FeatureFlagAuditItem,
  AdminTransaction, AdminSettlement, AdminCommission,
  EasebuzzSubMerchant, EasebuzzSubMerchantRequest, CommissionReport,
  AdminCreateBusinessRequest
} from '../models/api.models';
import { environment } from '../../../environments/environment';

const API_BASE_URL = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);

  getDashboardSummary() {
    return this.http.get<AdminDashboardSummary>(`${API_BASE_URL}/admin/dashboard/summary`);
  }

  getBusinesses() {
    return this.http.get<AdminBusinessListItem[]>(`${API_BASE_URL}/admin/businesses`);
  }

  createBusiness(payload: AdminCreateBusinessRequest) {
    return this.http.post<AdminBusinessDetail>(`${API_BASE_URL}/admin/businesses`, payload);
  }

  getBusinessDetail(restaurantId: number) {
    return this.http.get<AdminBusinessDetail>(`${API_BASE_URL}/admin/businesses/${restaurantId}`);
  }

  suspendBusiness(restaurantId: number) {
    return this.http.post<void>(`${API_BASE_URL}/admin/businesses/${restaurantId}/suspend`, null);
  }

  activateBusiness(restaurantId: number) {
    return this.http.post<void>(`${API_BASE_URL}/admin/businesses/${restaurantId}/activate`, null);
  }

  getFeatureFlags() {
    return this.http.get<FeatureFlagAdminItem[]>(`${API_BASE_URL}/admin/feature-flags`);
  }

  getFeatureFlagAudit(flagKey: string) {
    return this.http.get<FeatureFlagAuditItem[]>(`${API_BASE_URL}/admin/feature-flags/${flagKey}/audit`);
  }

  setFeatureFlagKillSwitch(flagKey: string, killSwitched: boolean) {
    return this.http.put<void>(`${API_BASE_URL}/admin/feature-flags/${flagKey}/kill-switch`, { killSwitched });
  }

  setFeatureFlagDefault(flagKey: string, defaultEnabled: boolean) {
    return this.http.put<void>(`${API_BASE_URL}/admin/feature-flags/${flagKey}/default`, { defaultEnabled });
  }

  setFeatureFlagOverride(flagKey: string, restaurantId: number, enabled: boolean) {
    return this.http.put<void>(`${API_BASE_URL}/admin/feature-flags/${flagKey}/restaurants/${restaurantId}`, { enabled });
  }

  clearFeatureFlagOverride(flagKey: string, restaurantId: number) {
    return this.http.delete<void>(`${API_BASE_URL}/admin/feature-flags/${flagKey}/restaurants/${restaurantId}`);
  }

  // ── Easebuzz/Payment admin operations ────────────────────────────────────
  getSubMerchants(): Observable<EasebuzzSubMerchant[]> {
    return this.http.get<EasebuzzSubMerchant[]>(`${API_BASE_URL}/admin/sub-merchants`);
  }

  getSubMerchant(id: number): Observable<EasebuzzSubMerchant | null> {
    return this.http.get<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}`);
  }

  createSubMerchant(payload: EasebuzzSubMerchantRequest): Observable<EasebuzzSubMerchant> {
    return this.http.post<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants`, payload);
  }

  updateSubMerchant(id: number, payload: Partial<EasebuzzSubMerchantRequest>): Observable<EasebuzzSubMerchant> {
    return this.http.put<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}`, payload);
  }

  assignSubMerchantId(id: number, subMerchantId: string): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/admin/sub-merchants/${id}/assign-id`, { subMerchantId });
  }

  submitToEasebuzz(id: number): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/admin/sub-merchants/${id}/submit-to-easebuzz`, {});
  }

  updateSubMerchantStatus(id: number, status: string): Observable<any> {
    return this.http.put<any>(`${API_BASE_URL}/admin/sub-merchants/${id}/status`, { status });
  }

  getTransactions(page: number = 0, size: number = 20, status?: string, restaurantId?: number): Observable<AdminTransaction[]> {
    const params: Record<string, string | number> = { page, size };
    if (status) params['status'] = status;
    if (restaurantId) params['restaurantId'] = restaurantId;
    return this.http.get<any>(`${API_BASE_URL}/admin/transactions`, { params }).pipe(
      map((res: any) => res.content ?? res ?? [])
    );
  }

  getSettlements(): Observable<AdminSettlement[]> {
    return this.http.get<AdminSettlement[]>(`${API_BASE_URL}/admin/settlements`);
  }

  getPaymentMetricsOverview(): Observable<any> {
    return this.http.get<any>(`${API_BASE_URL}/admin/payment-metrics/overview`);
  }

  getWebhookHealth(): Observable<any> {
    return this.http.get<any>(`${API_BASE_URL}/admin/webhooks/health`);
  }

  getCommissions(): Observable<AdminCommission[]> { return of([]); }
  updateCommission(id: number, payload: any): Observable<AdminCommission> { return of({} as AdminCommission); }
  getChargebackSummary(): Observable<any> { return of(null); }
  getCommissionReport(from?: string, to?: string): Observable<CommissionReport[]> { return of([]); }
  getCustomerInsights(): Observable<any> { return of(null); }
  getApiDocs(): Observable<any> { return of(null); }
  getWebhookEvents(): Observable<any[]> { return of([]); }
  getRateLimits(): Observable<any> { return of(null); }
  getCreditEligibility(): Observable<any> { return of(null); }
  getLoanOptions(eligibilityId?: any): Observable<any[]> { return of([]); }
  getSettlementEstimate(): Observable<any> { return of(null); }
  requestInstantSettlement(amount: number): Observable<any> { return of(null); }
  getOnboardingProgress(): Observable<any> { return of(null); }
  prefillFromProfile(): Observable<any> { return of(null); }
  getPaymentAnomalies(): Observable<any[]> { return of([]); }
  getFailedTransactions(...args: any[]): Observable<any[]> { return of([]); }
  getPaymentTrends(...args: any[]): Observable<any> { return of(null); }
  getPaymentRoutingRecommendations(): Observable<any[]> { return of([]); }
  getRefundSummary(): Observable<any> { return of(null); }
  getRefundableOrders(): Observable<any[]> { return of([]); }
  initiateRefund(orderId: number, amount: number, reason?: string): Observable<any> { return of(null); }
  generateKyc(...args: any[]): Observable<any> { return of(null); }
  updateOnEasebuzz(...args: any[]): Observable<any> { return of(null); }
  retrieveSplitStatus(...args: any[]): Observable<any> { return of(null); }
  createSplitLabel(...args: any[]): Observable<any> { return of(null); }
  verifyOtp(...args: any[]): Observable<any> { return of(null); }
  resendOtp(...args: any[]): Observable<any> { return of(null); }
  retrieveSettlementsByDate(...args: any[]): Observable<any> { return of(null); }
  onDemandSettlement(...args: any[]): Observable<any> { return of(null); }
  initiatePayout(...args: any[]): Observable<any> { return of(null); }
  getTaxSummary(...args: any[]): Observable<any> { return of(null); }
  getGstReport(...args: any[]): Observable<any[]> { return of([]); }
  getUnifiedDashboard(): Observable<any> { return of(null); }
  getDeadLetterJobs(): Observable<any[]> { return of([]); }
  replayDeadLetter(id: number): Observable<any> { return of(null); }
}
