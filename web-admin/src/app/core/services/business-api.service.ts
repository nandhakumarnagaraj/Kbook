import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  BusinessDashboard,
  BusinessMarketplaceSetup,
  BusinessMenuItem,
  BusinessOrder,
  BusinessStaffItem,
  BusinessTerminal,
  MarketplaceConfig,
  MarketplaceConfigRequest,
  RecoverTerminalRequest,
  RefundOrderRequest,
  RejectTerminalRequest,
  RenameTerminalRequest,
  TerminalRequest
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

  getTerminals() {
    return this.http.get<BusinessTerminal[]>(`${API_BASE_URL}/business/terminals`);
  }

  renameTerminal(terminalId: number, payload: RenameTerminalRequest) {
    return this.http.post<BusinessTerminal>(`${API_BASE_URL}/business/terminals/${terminalId}/rename`, payload);
  }

  deactivateTerminal(terminalId: number) {
    return this.http.post<void>(`${API_BASE_URL}/business/terminals/${terminalId}/deactivate`, {});
  }

  getTerminalRequests(status = 'PENDING') {
    return this.http.get<TerminalRequest[]>(`${API_BASE_URL}/business/terminal-requests`, {
      params: { status }
    });
  }

  approveTerminalRequest(requestId: number) {
    return this.http.post<void>(`${API_BASE_URL}/business/terminal-requests/${requestId}/approve`, {});
  }

  rejectTerminalRequest(requestId: number, payload?: RejectTerminalRequest) {
    return this.http.post<void>(`${API_BASE_URL}/business/terminal-requests/${requestId}/reject`, payload ?? {});
  }

  recoverTerminal(terminalId: number, payload: RecoverTerminalRequest) {
    return this.http.post<void>(`${API_BASE_URL}/business/terminals/${terminalId}/recover`, payload);
  }

}
