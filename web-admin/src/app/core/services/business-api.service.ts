import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  BusinessDashboard,
  BusinessMenuItem,
  BusinessCategory,
  BusinessOrder,
  BusinessStaffItem,
  BusinessTerminal,
  CreateMenuItemRequest,
  DashboardTrends,
  CreateStaffRequest,
  MenuExtractionJob,
  OrderDetailResponse,
  PaginatedOrdersResponse,
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
import { Observable, of } from 'rxjs';

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

  getDashboardTrends() {
    return this.http.get<DashboardTrends>(`${API_BASE_URL}/business/dashboard/trends`);
  }

  getOrders() {
    return this.http.get<BusinessOrder[]>(`${API_BASE_URL}/business/orders`);
  }

  getOrdersPaginated(page: number, size: number, status?: string, from?: string, to?: string) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<PaginatedOrdersResponse>(`${API_BASE_URL}/business/orders/page`, { params });
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

  setPrimaryTerminal(terminalId: number) {
    return this.http.post<BusinessTerminal>(
      `${API_BASE_URL}/business/terminals/${terminalId}/set-primary`, {});
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

  activateStaff(userId: number) {
    return this.http.post<void>(`${API_BASE_URL}/business/staff/${userId}/activate`, {});
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

  // ── Restaurant settings stub methods ──────────────────────────────────────
  requestUpdateMobileOtp(...args: any[]): Observable<any> { return of(null); }
  confirmUpdateMobile(...args: any[]): Observable<any> { return of(null); }
  uploadLogo(file: File): Observable<any> { return of(null); }
  deleteLogo(): Observable<any> { return of(null); }
  lookupFssai(number: string): Observable<any> { return of(null); }
  lookupGst(number: string): Observable<any> { return of(null); }
  lookupBoth(fssai: string, gst: string): Observable<any> { return of(null); }
  getProfile(): Observable<any> { return of(null); }
  updateProfile(payload: any): Observable<any> { return of(null); }

  getUserPermissions(userId: number): Observable<any> {
    return this.http.get(`${API_BASE_URL}/permissions/users/${userId}`);
  }

  updateUserPermissions(userId: number, permissions: string[]): Observable<any> {
    return this.http.post(`${API_BASE_URL}/permissions/bulk-grant`, { userId, permissionKeys: permissions });
  }

  // ── Merchant agreement (KhanaBook <-> restaurant signed PDF) ──────────────
  getMerchantAgreementStatus(): Observable<MerchantAgreementStatus> {
    return this.http.get<MerchantAgreementStatus>(`${API_BASE_URL}/business/merchant-agreement`);
  }

  uploadMerchantAgreement(file: File, signerName?: string, agreementVersion?: string): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    if (signerName) form.append('signerName', signerName);
    if (agreementVersion) form.append('agreementVersion', agreementVersion);
    return this.http.post(`${API_BASE_URL}/business/merchant-agreement`, form);
  }

  downloadMerchantAgreement(): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/business/merchant-agreement/download`, { responseType: 'blob' });
  }

  // ── Role Templates (server DB-backed) ─────────────────────────────────────
  getRoleTemplates(): Observable<any[]> {
    return this.http.get<any[]>(`${API_BASE_URL}/permissions/templates`);
  }

  createRoleTemplate(body: { name: string; description?: string | null; permissions: string[] }): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/permissions/templates`, body);
  }

  applyRoleTemplate(body: { userId: number; templateId: number }): Observable<any> {
    return this.http.post(`${API_BASE_URL}/permissions/apply-template`, body);
  }

  // ── Inventory (raw materials) ─────────────────────────────────────────────
  getInventoryMaterials(): Observable<any[]> {
    return this.http.get<any[]>(`${API_BASE_URL}/inventory/materials`);
  }

  createMaterial(body: { name: string; unit?: string; stockQuantity?: number; lowStockThreshold?: number; costPerUnit?: number }): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/inventory/materials`, body);
  }

  updateMaterial(id: number, body: any): Observable<any> {
    return this.http.put<any>(`${API_BASE_URL}/inventory/materials/${id}`, body);
  }

  deleteMaterial(id: number): Observable<any> {
    return this.http.delete(`${API_BASE_URL}/inventory/materials/${id}`);
  }

  purchaseStock(body: { materialId: number; quantity: number; unitCost?: number; vendorId?: number; expiryDate?: number }): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/inventory/purchase`, body);
  }

  recordWastage(body: { materialId: number; quantity: number; reason: string }): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/inventory/wastage`, body);
  }

  physicalCount(body: { materialId: number; countedQty: number }): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/inventory/physical-count`, body);
  }

  getStockMovements(materialId: number): Observable<any[]> {
    return this.http.get<any[]>(`${API_BASE_URL}/inventory/movements/${materialId}`);
  }

  getInventoryVariance(from: string, to: string): Observable<any> {
    return this.http.get(`${API_BASE_URL}/inventory/variance`, { params: { from, to } });
  }

  // ── Vendors ───────────────────────────────────────────────────────────────
  getVendors(): Observable<any[]> {
    return this.http.get<any[]>(`${API_BASE_URL}/inventory/vendors`);
  }

  createVendor(body: { name: string; phone?: string; notes?: string }): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/inventory/vendors`, body);
  }

  // ── Payment Config (Easebuzz) ─────────────────────────────────────────────
  getPaymentConfig(): Observable<any> {
    return this.http.get<any>(`${API_BASE_URL}/restaurants/payment-config/easebuzz`);
  }

  updatePaymentConfig(body: { easebuzzEnabled?: boolean }): Observable<any> {
    return this.http.put<any>(`${API_BASE_URL}/restaurants/payment-config/easebuzz`, body);
  }

  // ── Daily Closing (server-side) ──────────────────────────────────────────
  getDailyClosing(date: string): Observable<any> {
    return this.http.get(`${API_BASE_URL}/analytics/daily-closing`, { params: { date } });
  }

  // ── Bill Void ─────────────────────────────────────────────────────────────
  voidBill(billId: number, reason?: string): Observable<any> {
    return this.http.post(`${API_BASE_URL}/business/bills/${billId}/void`, reason ? { reason } : {});
  }
}

export interface MerchantAgreementStatus {
  hasAgreement: boolean;
  signedAt?: number;
  signerName?: string;
  agreementVersion?: string;
  originalFilename?: string;
}
