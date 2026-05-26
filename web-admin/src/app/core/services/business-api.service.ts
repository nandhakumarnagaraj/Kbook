import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  BusinessDashboard,
  BusinessMarketplaceSetup,
  BusinessMenuItem,
  BusinessOrder,
  BusinessProfile,
  BusinessStaffItem,
  MarketplaceConfig,
  MarketplaceConfigRequest,
  MarketplaceOrder,
  MarketplaceOrderCounts,
  RefundOrderRequest,
  UpdateBusinessProfileRequest
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

  updateMenuItem(itemId: number, payload: any) {
    return this.http.put<void>(`${API_BASE_URL}/business/menu/${itemId}`, payload);
  }

  updateMenuItemAvailability(itemId: number, available: boolean) {
    return this.http.put<void>(`${API_BASE_URL}/business/menu/${itemId}/availability`, { available });
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

  completeMarketplaceOrder(orderId: number) {
    return this.http.post<MarketplaceOrder>(`${API_BASE_URL}/business/marketplace-orders/${orderId}/complete`, {});
  }

  getProfile() {
    return this.http.get<BusinessProfile>(`${API_BASE_URL}/business/profile`);
  }

  updateProfile(payload: UpdateBusinessProfileRequest) {
    return this.http.put<BusinessProfile>(`${API_BASE_URL}/business/profile`, payload);
  }

  lookupGst(gstin: string) {
    return this.http.get<any>(`${API_BASE_URL}/business/lookup/gst`, { params: { gstin } });
  }

  lookupFssai(fssaiNo: string) {
    return this.http.get<any>(`${API_BASE_URL}/business/lookup/fssai`, { params: { fssaiNo } });
  }

  marketplaceHealthCheck(platform: 'SWIGGY' | 'ZOMATO') {
    return this.http.get<{
      platform: string;
      enabled: boolean;
      apiKeyConfigured: boolean;
      storeIdConfigured?: boolean;
      outletIdConfigured?: boolean;
      webhookUrl: string;
      healthy: boolean;
    }>(`${API_BASE_URL}/marketplace/health`, { params: { platform } });
  }

  uploadLogo(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<{ logoUrl: string; logoVersion: number }>(`${API_BASE_URL}/restaurants/logo`, formData);
  }

  deleteLogo() {
    return this.http.delete<void>(`${API_BASE_URL}/restaurants/logo`);
  }

  requestUpdateMobileOtp(newMobileNumber: string) {
    return this.http.post<any>(`${API_BASE_URL}/sync/config/users/update-mobile/request`, { newMobileNumber });
  }

  confirmUpdateMobile(newMobileNumber: string, otp: string) {
    return this.http.post<any>(`${API_BASE_URL}/sync/config/users/update-mobile`, { newMobileNumber, otp });
  }

  lookupBoth(gstin?: string, fssaiNo?: string) {
    const params: any = {};
    if (gstin) params.gstin = gstin;
    if (fssaiNo) params.fssaiNo = fssaiNo;
    return this.http.get<any>(`${API_BASE_URL}/business/lookup/both`, { params });
  }
}
