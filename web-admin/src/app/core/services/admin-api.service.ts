import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AdminBusinessDetail, AdminBusinessListItem, AdminDashboardSummary, PaymentConfig } from '../models/api.models';

const API_BASE_URL = 'https://kbook.iadv.cloud/api/v1';

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

  getBusinessPaymentConfig(restaurantId: number) {
    return this.http.get<PaymentConfig>(
      `${API_BASE_URL}/restaurants/payment-config/easebuzz?restaurantId=${restaurantId}`
    );
  }
}
