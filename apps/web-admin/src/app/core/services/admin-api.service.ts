import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  AdminBusinessDetail, AdminBusinessListItem, AdminDashboardSummary, AdminUserListItem,
  AdminBillListItem, AdminActivityLogItem, AdminDailyTrendItem, AdminNotificationFeed, AdminBackupStatus,
  FeatureFlagAdminItem, FeatureFlagAuditItem,
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

  getUsers() {
    return this.http.get<AdminUserListItem[]>(`${API_BASE_URL}/admin/users`);
  }

  getBills(limit: number = 50) {
    return this.http.get<AdminBillListItem[]>(`${API_BASE_URL}/admin/bills`, { params: { limit } });
  }

  getActivityLogs(limit: number = 50) {
    return this.http.get<AdminActivityLogItem[]>(`${API_BASE_URL}/admin/activity-logs`, { params: { limit } });
  }

  getDailyTrends(days: number = 30) {
    return this.http.get<AdminDailyTrendItem[]>(`${API_BASE_URL}/admin/dashboard/daily-trends`, { params: { days } });
  }

  getNotificationFeed(limit: number = 100) {
    return this.http.get<AdminNotificationFeed>(`${API_BASE_URL}/admin/notifications`, { params: { limit } });
  }

  getBackupStatus() {
    return this.http.get<AdminBackupStatus>(`${API_BASE_URL}/admin/backups`);
  }

  createBusiness(payload: AdminCreateBusinessRequest) {
    return this.http.post<AdminBusinessDetail>(`${API_BASE_URL}/admin/businesses`, payload);
  }

  getBusinessDetail(restaurantId: string | number) {
    return this.http.get<AdminBusinessDetail>(`${API_BASE_URL}/admin/businesses/${restaurantId}`);
  }

  suspendBusiness(restaurantId: string | number) {
    return this.http.post<void>(`${API_BASE_URL}/admin/businesses/${restaurantId}/suspend`, null);
  }

  activateBusiness(restaurantId: string | number) {
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

  setFeatureFlagOverride(flagKey: string, restaurantId: string | number, enabled: boolean) {
    return this.http.put<void>(`${API_BASE_URL}/admin/feature-flags/${flagKey}/restaurants/${restaurantId}`, { enabled });
  }

  clearFeatureFlagOverride(flagKey: string, restaurantId: string | number) {
    return this.http.delete<void>(`${API_BASE_URL}/admin/feature-flags/${flagKey}/restaurants/${restaurantId}`);
  }

  getWebhookHealth() {
    return this.http.get<any>(`${API_BASE_URL}/admin/webhooks/health`);
  }
}
