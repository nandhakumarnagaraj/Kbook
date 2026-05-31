import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  AdminBusinessDetail,
  AdminBusinessListItem,
  AdminCommission,
  AdminDashboardSummary,
  AdminSettlement,
  AdminTransaction,
  BeneficiaryDetails,
  CommissionReport,
  EasebuzzSubMerchant,
  EasebuzzSubMerchantRequest,
  KycProfileUrlResponse,
  PagedResponse,
  PaymentDashboard,
  PayoutResponse,
  SplitRetrieveResponse,
  WireLookupResult,
  WirePayoutWebhookConfig,
  WireWebhookConfig,
} from '../models/api.models';
import { environment } from '../../../environments/environment';

const API_BASE_URL = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);

  // ── Dashboard ──────────────────────────────────────────────────────────────

  getDashboardSummary() {
    return this.http.get<AdminDashboardSummary>(`${API_BASE_URL}/admin/dashboard/summary`);
  }

  // ── Businesses ─────────────────────────────────────────────────────────────

  getBusinesses() {
    return this.http.get<AdminBusinessListItem[]>(`${API_BASE_URL}/admin/businesses`);
  }

  getBusinessDetail(restaurantId: number) {
    return this.http.get<AdminBusinessDetail>(`${API_BASE_URL}/admin/businesses/${restaurantId}`);
  }

  // ── Sub-Merchants ──────────────────────────────────────────────────────────

  getSubMerchants() {
    return this.http.get<EasebuzzSubMerchant[]>(`${API_BASE_URL}/admin/sub-merchants`);
  }

  getSubMerchant(id: number) {
    return this.http.get<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}`);
  }

  createSubMerchant(payload: EasebuzzSubMerchantRequest) {
    return this.http.post<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants`, payload);
  }

  updateSubMerchant(id: number, payload: Partial<EasebuzzSubMerchantRequest>) {
    return this.http.put<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}`, payload);
  }

  submitToEasebuzz(id: number) {
    return this.http.post<EasebuzzSubMerchant>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/submit-to-easebuzz`, {}
    );
  }

  updateOnEasebuzz(id: number) {
    return this.http.post<EasebuzzSubMerchant>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/update-on-easebuzz`, {}
    );
  }

  assignSubMerchantId(id: number, subMerchantId: string) {
    return this.http.post<EasebuzzSubMerchant>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/assign-id`, { subMerchantId }
    );
  }

  generateKyc(id: number) {
    return this.http.post<{ status: string; kyc_url: string; sub_merchant_id: string }>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/kyc-access-key`, {}
    );
  }

  createSplitLabel(id: number) {
    return this.http.post<{ status: string; label: string; msg: string }>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/split-label`, {}
    );
  }

  retrieveSplitStatus(id: number, merchantRequestId: string) {
    return this.http.post<SplitRetrieveResponse>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/split-retrieve`, { merchantRequestId }
    );
  }

  updateSubMerchantStatus(id: number, status: string) {
    return this.http.put<EasebuzzSubMerchant>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/status`, { status }
    );
  }

  verifyOtp(id: number, otp: string) {
    return this.http.post<{ status: string; message: string }>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/verify-otp`, { otp }
    );
  }

  resendOtp(id: number) {
    return this.http.post<{ status: string; message: string }>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/resend-otp`, {}
    );
  }

  // ── Settlements & Payouts ──────────────────────────────────────────────────

  onDemandSettlement(amount: string) {
    return this.http.post<{ status: string; message: string; settlementId: string | null }>(
      `${API_BASE_URL}/admin/sub-merchants/settlements/on-demand`, { amount }
    );
  }

  initiatePayout(amount: string, beneficiaryDetails: BeneficiaryDetails) {
    return this.http.post<PayoutResponse>(
      `${API_BASE_URL}/admin/sub-merchants/payout`, { amount, beneficiaryDetails }
    );
  }

  retrieveSettlementsByDate(date: string) {
    return this.http.get<AdminSettlement[]>(
      `${API_BASE_URL}/admin/sub-merchants/settlements/retrieve?date=${date}`
    );
  }

  // ── Transactions ───────────────────────────────────────────────────────────

  getTransactions(page: number, size: number, status?: string, restaurantId?: number) {
    let params = `page=${page}&size=${size}`;
    if (status) params += `&status=${encodeURIComponent(status)}`;
    if (restaurantId != null) params += `&restaurantId=${restaurantId}`;
    return this.http.get<AdminTransaction[]>(`${API_BASE_URL}/admin/transactions?${params}`);
  }

  // ── Settlements ────────────────────────────────────────────────────────────

  getSettlements() {
    return this.http.get<AdminSettlement[]>(`${API_BASE_URL}/admin/settlements`);
  }

  // ── Commission ─────────────────────────────────────────────────────────────

  getCommissions() {
    return this.http.get<AdminCommission[]>(`${API_BASE_URL}/admin/commission`);
  }

  updateCommission(id: number, rate: number) {
    return this.http.put<AdminCommission>(
      `${API_BASE_URL}/admin/commission/${id}`, { commissionRate: rate }
    );
  }

  getCommissionReport() {
    return this.http.get<CommissionReport[]>(`${API_BASE_URL}/admin/reports/commission`);
  }

  getPaymentDashboard() {
    return this.http.get<PaymentDashboard>(`${API_BASE_URL}/admin/reports/payment-dashboard`);
  }

  // ── Payment Metrics (Phase 1.1) ───────────────────────────────────────────

  getPaymentMetricsOverview() {
    return this.http.get<any>(`${API_BASE_URL}/admin/payment-metrics/overview`);
  }

  getPaymentTrends(period: string = 'daily', days: number = 7) {
    return this.http.get<any[]>(`${API_BASE_URL}/admin/payment-metrics/trends?period=${period}&days=${days}`);
  }

  getFailedTransactions(page: number = 0, size: number = 20) {
    return this.http.get<any>(`${API_BASE_URL}/admin/payment-metrics/failed-transactions?page=${page}&size=${size}`);
  }

  getPaymentAnomalies() {
    return this.http.get<any[]>(`${API_BASE_URL}/admin/payment-metrics/anomalies`);
  }

  // ── WIRE Platform ──────────────────────────────────────────────────────────

  wireLookupByEmail(email: string) {
    return this.http.get<WireLookupResult>(
      `${API_BASE_URL}/admin/sub-merchants/wire/lookup-by-email?email=${encodeURIComponent(email)}`
    );
  }

  wireLookupByKey(subMerchantKey: string) {
    return this.http.get<WireLookupResult>(
      `${API_BASE_URL}/admin/sub-merchants/wire/lookup-by-key/${encodeURIComponent(subMerchantKey)}`
    );
  }

  wireGetKycProfileUrl(id: number) {
    return this.http.post<KycProfileUrlResponse>(
      `${API_BASE_URL}/admin/sub-merchants/${id}/wire/kyc-profile-url`, {}
    );
  }

  wireConfigureInstaCollectWebhook(payload: WireWebhookConfig) {
    return this.http.post<{ status: string; message: string }>(
      `${API_BASE_URL}/admin/sub-merchants/wire/insta-collect-webhook`, payload
    );
  }

  wireConfigurePayoutWebhook(payload: WirePayoutWebhookConfig) {
    return this.http.post<{ status: string; message: string }>(
      `${API_BASE_URL}/admin/sub-merchants/wire/payout-webhook`, payload
    );
  }

  // ── Refund Automation ─────────────────────────────────────────────────────
  getRefundReasons() {
    return this.http.get<{ code: string; label: string }[]>(`${API_BASE_URL}/business/refunds/reasons`);
  }
  getRefundableOrders() {
    return this.http.get<any>(`${API_BASE_URL}/business/refunds/refundable`);
  }
  initiateRefund(billId: number, refundAmount: number, reason: string) {
    return this.http.post<any>(`${API_BASE_URL}/business/refunds/bill/${billId}`, { refundAmount, reason });
  }
  cancelAndAutoRefund(billId: number, reason: string, delayMinutes = 0) {
    return this.http.post<any>(`${API_BASE_URL}/business/refunds/bill/${billId}/cancel`, { reason, delayMinutes });
  }
  getRefundSummary() {
    return this.http.get<any>(`${API_BASE_URL}/business/refunds/summary`);
  }

  // ── Webhook Health ────────────────────────────────────────────────────────
  getWebhookHealth() {
    return this.http.get<any>(`${API_BASE_URL}/admin/webhooks/health`);
  }
  getDeadLetterJobs() {
    return this.http.get<any[]>(`${API_BASE_URL}/admin/webhooks/dead-letter`);
  }
  replayDeadLetter(jobId: number) {
    return this.http.post<any>(`${API_BASE_URL}/admin/webhooks/dead-letter/${jobId}/replay`, {});
  }

  // ── Onboarding ────────────────────────────────────────────────────────────
  getOnboardingProgress() {
    return this.http.get<any>(`${API_BASE_URL}/business/onboarding/progress`);
  }
  prefillFromProfile() {
    return this.http.post<any>(`${API_BASE_URL}/business/onboarding/prefill-from-profile`, {});
  }

  // ── Instant Settlement ────────────────────────────────────────────────────
  getSettlementEstimate() {
    return this.http.get<any>(`${API_BASE_URL}/business/settlements/estimate`);
  }
  requestInstantSettlement(amount: number) {
    return this.http.post<any>(`${API_BASE_URL}/business/settlements/instant`, { amount });
  }

  // ── Tax Compliance ────────────────────────────────────────────────────────
  getTaxSummary() {
    return this.http.get<any>(`${API_BASE_URL}/business/tax/summary`);
  }
  getGstReport(year: number, month: number) {
    return this.http.get<any>(`${API_BASE_URL}/business/tax/gst-report?year=${year}&month=${month}`);
  }
  getGstReturn(year: number, quarter: number) {
    return this.http.get<any>(`${API_BASE_URL}/business/tax/gst-return?year=${year}&quarter=${quarter}`);
  }

  // ── Chargeback ────────────────────────────────────────────────────────────
  getChargebackSummary() {
    return this.http.get<any>(`${API_BASE_URL}/business/chargebacks/summary`);
  }
  scoreTransaction(billId: number) {
    return this.http.post<any>(`${API_BASE_URL}/business/chargebacks/score/${billId}`, {});
  }
  createChargeback(billId: number, reasonCode: string, description: string) {
    return this.http.post<any>(`${API_BASE_URL}/business/chargebacks`, { billId, reasonCode, description });
  }
  resolveChargeback(chargebackId: number, resolution: string) {
    return this.http.post<any>(`${API_BASE_URL}/business/chargebacks/${chargebackId}/resolve`, { resolution });
  }

  // ── Financing ─────────────────────────────────────────────────────────────
  getCreditEligibility() {
    return this.http.get<any>(`${API_BASE_URL}/business/financing/eligibility`);
  }
  getLoanOptions(amount: number) {
    return this.http.post<any>(`${API_BASE_URL}/business/financing/options`, { amount });
  }

  // ── Unified Commerce ──────────────────────────────────────────────────────
  getUnifiedDashboard() {
    return this.http.get<any>(`${API_BASE_URL}/business/commerce/unified-dashboard`);
  }
  getCrossChannelInsights() {
    return this.http.get<any>(`${API_BASE_URL}/business/commerce/cross-channel-insights`);
  }

  // ── Customer Data Platform ────────────────────────────────────────────────
  getCustomerInsights() {
    return this.http.get<any>(`${API_BASE_URL}/business/customers/insights`);
  }
  getChurnRisk() {
    return this.http.get<any[]>(`${API_BASE_URL}/business/customers/churn-risk`);
  }

  // ── Developer Portal ──────────────────────────────────────────────────────
  getApiDocs() {
    return this.http.get<any>(`${API_BASE_URL}/admin/developer/docs`);
  }
  getWebhookEvents() {
    return this.http.get<any>(`${API_BASE_URL}/admin/developer/webhook-events`);
  }
  getRateLimits() {
    return this.http.get<any>(`${API_BASE_URL}/admin/developer/rate-limits`);
  }

  // ── Payment Routing ───────────────────────────────────────────────────────
  getPaymentRoutingRecommendations() {
    return this.http.get<any>(`${API_BASE_URL}/admin/payment-routing/recommendations`);
  }
  getPaymentRoutingHistory() {
    return this.http.get<any>(`${API_BASE_URL}/admin/payment-routing/history`);
  }
  selectOptimalPaymentMethod(amount: number, customerVpa?: string) {
    return this.http.post<any>(`${API_BASE_URL}/admin/payment-routing/select-method`, { amount, customerVpa });
  }

  // ── Customer Privacy ──────────────────────────────────────────────────────
  optOutCustomer(phone: string) {
    return this.http.post<any>(`${API_BASE_URL}/business/customers/opt-out`, { phone });
  }
  optInCustomer(phone: string) {
    return this.http.post<any>(`${API_BASE_URL}/business/customers/opt-in`, { phone });
  }
  deleteCustomerData(phone: string) {
    return this.http.post<any>(`${API_BASE_URL}/business/customers/delete`, { phone });
  }
  exportCustomerData(phone: string) {
    return this.http.post<any>(`${API_BASE_URL}/business/customers/export`, { phone });
  }

  // ── Tax CSV Export ────────────────────────────────────────────────────────
  downloadGstReportCsv(year: number, month: number) {
    return this.http.get(`${API_BASE_URL}/business/tax/gst-report/csv`, {
      params: { year: year.toString(), month: month.toString() },
      responseType: 'text'
    });
  }
}
