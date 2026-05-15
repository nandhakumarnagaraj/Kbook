import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  BusinessDashboard,
  BusinessMarketplaceSetup,
  BusinessMenuItem,
  BusinessOrder,
  BusinessStaffItem,
  MarketplaceConfig,
  MarketplaceConfigRequest,
  MarketplaceOrder,
  MarketplaceOrderCounts,
  RefundOrderRequest
} from '../models/api.models';
import { environment } from '../../../environments/environment';

const API_BASE_URL = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class BusinessApiService {
  private readonly http = inject(HttpClient);

  getDashboard() {
    return this.http.get<BusinessDashboard>(`${API_BASE_URL}/business/dashboard`);
  }

  getMarketplaceSetup() {
    return this.http.get<BusinessMarketplaceSetup>(`${API_BASE_URL}/business/marketplace-setup`);
  }

  getMarketplaceConfig() {
    return this.http.get<MarketplaceConfig>(`${API_BASE_URL}/marketplace/config`);
  }

  saveMarketplaceConfig(payload: MarketplaceConfigRequest) {
    return this.http.post<MarketplaceConfig>(`${API_BASE_URL}/marketplace/config`, payload);
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

  manualRefundOrder(billId: number, payload: RefundOrderRequest) {
    return this.http.post<BusinessOrder>(`${API_BASE_URL}/business/bills/${billId}/manual-refund`, payload);
  }

  getMarketplaceOrders() {
    return this.http.get<MarketplaceOrder[]>(`${API_BASE_URL}/business/marketplace-orders`);
  }

  getPendingMarketplaceOrders() {
    return this.http.get<MarketplaceOrder[]>(`${API_BASE_URL}/business/marketplace-orders/pending`);
  }

  getMarketplaceOrderCounts() {
    return this.http.get<MarketplaceOrderCounts>(`${API_BASE_URL}/business/marketplace-orders/counts`);
  }

  acceptMarketplaceOrder(orderId: number) {
    return this.http.post<MarketplaceOrder>(`${API_BASE_URL}/business/marketplace-orders/${orderId}/accept`, {});
  }

  rejectMarketplaceOrder(orderId: number, reason?: string) {
    return this.http.post<MarketplaceOrder>(`${API_BASE_URL}/business/marketplace-orders/${orderId}/reject`, { reason });
  }

  markMarketplaceOrderReady(orderId: number) {
    return this.http.post<MarketplaceOrder>(`${API_BASE_URL}/business/marketplace-orders/${orderId}/mark-ready`, {});
  }

  lookupGst(gstin: string) {
    return this.http.get<any>(`${API_BASE_URL}/business/lookup/gst`, { params: { gstin } });
  }

  lookupFssai(fssaiNo: string) {
    return this.http.get<any>(`${API_BASE_URL}/business/lookup/fssai`, { params: { fssaiNo } });
  }
}
