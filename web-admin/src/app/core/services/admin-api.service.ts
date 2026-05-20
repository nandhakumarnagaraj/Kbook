import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AdminBusinessDetail, AdminBusinessListItem, AdminDashboardSummary, EasebuzzSubMerchant, EasebuzzSubMerchantRequest } from '../models/api.models';
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

  getBusinessDetail(restaurantId: number) {
    return this.http.get<AdminBusinessDetail>(`${API_BASE_URL}/admin/businesses/${restaurantId}`);
  }

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
    return this.http.post<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}/submit-to-easebuzz`, {});
  }

  updateOnEasebuzz(id: number) {
    return this.http.post<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}/update-on-easebuzz`, {});
  }

  assignSubMerchantId(id: number, subMerchantId: string) {
    return this.http.post<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}/assign-id`, { subMerchantId });
  }

  generateKyc(id: number) {
    return this.http.post<{ status: string; kyc_url: string; sub_merchant_id: string }>(`${API_BASE_URL}/admin/sub-merchants/${id}/kyc-access-key`, {});
  }

  createSplitLabel(id: number) {
    return this.http.post<{ status: string; label: string; msg: string }>(`${API_BASE_URL}/admin/sub-merchants/${id}/split-label`, {});
  }

  retrieveSplitStatus(id: number, merchantRequestId: string) {
    return this.http.post<{ status: string; merchant_request_id: string; split_configuration: any[] }>(`${API_BASE_URL}/admin/sub-merchants/${id}/split-retrieve`, { merchantRequestId });
  }

  updateSubMerchantStatus(id: number, status: string) {
    return this.http.put<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}/status`, { status });
  }

  verifyOtp(id: number, otp: string) {
    return this.http.post<any>(`${API_BASE_URL}/admin/sub-merchants/${id}/verify-otp`, { otp });
  }

  resendOtp(id: number) {
    return this.http.post<any>(`${API_BASE_URL}/admin/sub-merchants/${id}/resend-otp`, {});
  }

  onDemandSettlement(amount: string) {
    return this.http.post<any>(`${API_BASE_URL}/admin/sub-merchants/settlements/on-demand`, { amount });
  }

  initiatePayout(amount: string, beneficiaryDetails: any) {
    return this.http.post<any>(`${API_BASE_URL}/admin/sub-merchants/payout`, { amount, beneficiaryDetails });
  }

  retrieveSettlementsByDate(date: string) {
    return this.http.get<any>(`${API_BASE_URL}/admin/sub-merchants/settlements/retrieve?date=${date}`);
  }

  getTransactions(page: number, size: number, status?: string, restaurantId?: number) {
    let params = `page=${page}&size=${size}`;
    if (status) params += `&status=${encodeURIComponent(status)}`;
    if (restaurantId != null) params += `&restaurantId=${restaurantId}`;
    return this.http.get<any[]>(`${API_BASE_URL}/admin/transactions?${params}`);
  }

  getSettlements() {
    return this.http.get<any[]>(`${API_BASE_URL}/admin/settlements`);
  }

  getCommissions() {
    return this.http.get<any[]>(`${API_BASE_URL}/admin/commission`);
  }

  updateCommission(id: number, rate: number) {
    return this.http.put<any>(`${API_BASE_URL}/admin/commission/${id}`, { commissionRate: rate });
  }

  getCommissionReport() {
    return this.http.get<any[]>(`${API_BASE_URL}/admin/reports/commission`);
  }

  getPaymentDashboard() {
    return this.http.get<any>(`${API_BASE_URL}/admin/reports/payment-dashboard`);
  }

  // ============================================================
  // WIRE Platform API Methods
  // ============================================================

  wireLookupByEmail(email: string) {
    return this.http.get<any>(`${API_BASE_URL}/admin/sub-merchants/wire/lookup-by-email?email=${encodeURIComponent(email)}`);
  }

  wireLookupByKey(subMerchantKey: string) {
    return this.http.get<any>(`${API_BASE_URL}/admin/sub-merchants/wire/lookup-by-key/${encodeURIComponent(subMerchantKey)}`);
  }

  wireGetKycProfileUrl(id: number) {
    return this.http.post<any>(`${API_BASE_URL}/admin/sub-merchants/${id}/wire/kyc-profile-url`, {});
  }

  wireConfigureInstaCollectWebhook(payload: {
    subMerchantId: string;
    merchantEmail?: string;
    eventType: string;
    url: string;
    intervalUnit: string;
    intervalValue: number;
    maxAttempts: number;
  }) {
    return this.http.post<any>(`${API_BASE_URL}/admin/sub-merchants/wire/insta-collect-webhook`, payload);
  }

  wireConfigurePayoutWebhook(payload: {
    subMerchantId: string;
    eventType: string;
    url: string;
    intervalUnit: string;
    intervalValue: number;
    maxAttempts: number;
  }) {
    return this.http.post<any>(`${API_BASE_URL}/admin/sub-merchants/wire/payout-webhook`, payload);
  }
}
