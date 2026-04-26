import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BusinessDashboard, BusinessMenuItem, BusinessOrder, BusinessStaffItem, PaymentConfig, SavePaymentConfigRequest, StorefrontOrder, StorefrontOrderDetail } from '../models/api.models';

const API_BASE_URL = 'https://kbook.iadv.cloud/api/v1';

@Injectable({ providedIn: 'root' })
export class BusinessApiService {
  private readonly http = inject(HttpClient);

  getDashboard() {
    return this.http.get<BusinessDashboard>(`${API_BASE_URL}/business/dashboard`);
  }

  getOrders() {
    return this.http.get<BusinessOrder[]>(`${API_BASE_URL}/business/orders`);
  }

  getMenu() {
    return this.http.get<BusinessMenuItem[]>(`${API_BASE_URL}/business/menu`);
  }

  getStaff() {
    return this.http.get<BusinessStaffItem[]>(`${API_BASE_URL}/business/staff`);
  }

  getPaymentConfig() {
    return this.http.get<PaymentConfig>(`${API_BASE_URL}/restaurants/payment-config/easebuzz`);
  }

  savePaymentConfig(payload: SavePaymentConfigRequest) {
    return this.http.post<PaymentConfig>(`${API_BASE_URL}/restaurants/payment-config/easebuzz`, payload);
  }

  getStorefrontOrders() {
    return this.http.get<StorefrontOrder[]>(`${API_BASE_URL}/storefront/orders`);
  }

  updateStorefrontOrderStatus(orderId: number, orderStatus: string) {
    return this.http.patch<StorefrontOrderDetail>(
      `${API_BASE_URL}/storefront/orders/${orderId}/status`,
      { orderStatus }
    );
  }
}
