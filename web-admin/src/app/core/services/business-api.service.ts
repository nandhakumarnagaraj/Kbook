import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  BusinessDashboard,
  BusinessMarketplaceSetup,
  BusinessMenuItem,
  BusinessCategory,
  BusinessOrder,
  BusinessStaffItem,
  BusinessTerminal,
  CreateMenuItemRequest,
  CreateStaffRequest,
  MarketplaceConfig,
  MarketplaceConfigRequest,
  MenuExtractionJob,
  OrderDetailResponse,
  RecoverTerminalRequest,
  RecoverTerminalResponse,
  RefundOrderRequest,
  RejectTerminalRequest,
  RenameTerminalRequest,
  StaffCreatedResponse,
  TerminalRequest,
  UpdateMenuItemRequest,
  UpdateStaffRequest
} from '../models/api.models';
import { environment } from '../../../environments/environment';

const API_BASE_URL = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class BusinessApiService {
  private readonly http = inject(HttpClient);

  getDashboard(from?: string, to?: string) {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<BusinessDashboard>(`${API_BASE_URL}/business/dashboard`, { params });
  }

  getMarketplaceSetup() {
    return this.http.get<BusinessMarketplaceSetup>(`${API_BASE_URL}/business/marketplace-setup`);
  }

  getMarketplaceConfig() {
    return this.http.get<MarketplaceConfig>(`${API_BASE_URL}/business/marketplace/config`);
  }

  saveMarketplaceConfig(payload: MarketplaceConfigRequest) {
    return this.http.post<MarketplaceConfig>(`${API_BASE_URL}/business/marketplace/config`, payload);
  }

  getOrders() {
    return this.http.get<BusinessOrder[]>(`${API_BASE_URL}/business/orders`);
  }

  getMenu() {
    return this.http.get<BusinessMenuItem[]>(`${API_BASE_URL}/business/menu`);
  }

  getMenuCategories() {
    return this.http.get<BusinessCategory[]>(`${API_BASE_URL}/business/menu/categories`);
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

  approveTerminalRequest(requestId: number, challengeCode?: string) {
    const body = challengeCode ? { challengeCode } : {};
    return this.http.post<void>(`${API_BASE_URL}/business/terminal-requests/${requestId}/approve`, body);
  }

  rejectTerminalRequest(requestId: number, payload?: RejectTerminalRequest) {
    return this.http.post<void>(`${API_BASE_URL}/business/terminal-requests/${requestId}/reject`, payload ?? {});
  }

  recoverTerminal(terminalId: number, payload: RecoverTerminalRequest) {
    return this.http.post<RecoverTerminalResponse>(`${API_BASE_URL}/business/terminals/${terminalId}/recover`, payload);
  }

  uploadMenuFile(file: File) {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ message: string; jobId: number; status: string }>(
      `${API_BASE_URL}/menus/upload`,
      form
    );
  }

  getMenuJobStatus(jobId: number) {
    return this.http.get<MenuExtractionJob>(`${API_BASE_URL}/menus/jobs/${jobId}`);
  }

  // Staff CRUD
  createStaff(payload: CreateStaffRequest) {
    return this.http.post<StaffCreatedResponse>(`${API_BASE_URL}/business/staff`, payload);
  }

  updateStaff(userId: number, payload: UpdateStaffRequest) {
    return this.http.put<void>(`${API_BASE_URL}/business/staff/${userId}`, payload);
  }

  deactivateStaff(userId: number) {
    return this.http.post<void>(`${API_BASE_URL}/business/staff/${userId}/deactivate`, {});
  }

  // Menu CRUD
  createMenuItem(payload: CreateMenuItemRequest) {
    return this.http.post<BusinessMenuItem>(`${API_BASE_URL}/business/menu`, payload);
  }

  updateMenuItem(menuItemId: number, payload: UpdateMenuItemRequest) {
    return this.http.put<BusinessMenuItem>(`${API_BASE_URL}/business/menu/${menuItemId}`, payload);
  }

  deleteMenuItem(menuItemId: number) {
    return this.http.delete<void>(`${API_BASE_URL}/business/menu/${menuItemId}`);
  }

  toggleMenuItemAvailability(menuItemId: number) {
    return this.http.post<BusinessMenuItem>(`${API_BASE_URL}/business/menu/${menuItemId}/toggle-availability`, {});
  }

  // Terminal
  reactivateTerminal(terminalId: number) {
    return this.http.post<void>(`${API_BASE_URL}/business/terminals/${terminalId}/reactivate`, {});
  }

  // Orders
  getOrderDetail(billId: number) {
    return this.http.get<OrderDetailResponse>(`${API_BASE_URL}/business/orders/${billId}`);
  }

}
