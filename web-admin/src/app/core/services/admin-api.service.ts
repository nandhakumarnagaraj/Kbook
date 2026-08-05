import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AdminBusinessDetail, AdminBusinessListItem, AdminDashboardSummary, FeatureFlagAdminItem, FeatureFlagAuditItem } from '../models/api.models';
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

}
