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

  registerSubMerchant(id: number) {
    return this.http.post<{ message: string }>(`${API_BASE_URL}/admin/sub-merchants/${id}/register`, {});
  }

  generateKyc(id: number) {
    return this.http.post<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}/generate-kyc`, {});
  }

  updateSubMerchantStatus(id: number, status: string) {
    return this.http.put<EasebuzzSubMerchant>(`${API_BASE_URL}/admin/sub-merchants/${id}/status`, { status });
  }
}
