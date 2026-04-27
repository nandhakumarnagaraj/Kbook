import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BusinessDashboard, BusinessMenuItem, BusinessOrder, BusinessStaffItem, PaymentConfig, RefundOrderRequest, SavePaymentConfigRequest, StorefrontOrder, StorefrontOrderDetail } from '../models/api.models';
import { environment } from '../../../environments/environment';

const API_BASE_URL = environment.apiBaseUrl;

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

  togglePaymentConfigActive(enabled: boolean) {
    return this.http.patch<PaymentConfig>(
      `${API_BASE_URL}/restaurants/payment-config/easebuzz/toggle?enabled=${enabled}`, {}
    );
  }

  manualRefundOrder(billId: number, payload: RefundOrderRequest) {
    return this.http.post<BusinessOrder>(`${API_BASE_URL}/business/bills/${billId}/manual-refund`, payload);
  }

  gatewayRefundOrder(billId: number, payload: RefundOrderRequest) {
    return this.http.post<BusinessOrder>(`${API_BASE_URL}/business/bills/${billId}/gateway-refund`, payload);
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
