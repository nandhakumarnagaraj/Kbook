import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  BusinessDashboard,
  BusinessMenuItem,
  BusinessCategory,
  BusinessItemVariant,
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
  StaffCredentialsResponse,
  TerminalRequest,
  UpdateMenuItemRequest,
  UpdateStaffRequest,
  HourlySalesRow,
  ItemSalesRow,
  SyncTerminalItem,
  NotificationItem,
  GstLedgerResponse,
  GstLedgerEntry
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

  getOrdersPaginated(page: number, size: number, status?: string, from?: string, to?: string, search?: string) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (search) params = params.set('search', search);
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

  /**
   * Issues a fresh generated password, sends it over WhatsApp, and signs the
   * staff member out of every device. Used when the first delivery is lost.
   */
  resendStaffCredentials(userId: number) {
    return this.http.post<StaffCredentialsResponse>(
      `${API_BASE_URL}/business/staff/${userId}/resend-credentials`,
      {},
    );
  }

  activateStaff(userId: number) {
    return this.http.post<void>(`${API_BASE_URL}/business/staff/${userId}/activate`, {});
  }

  /** Signs one staff member out of every device without disabling their account. */
  revokeStaffSessions(userId: number) {
    return this.http.post<{ revoked: number }>(
      `${API_BASE_URL}/business/staff/${userId}/revoke-sessions`,
      {}
    );
  }

  /** Signs everyone out of every device, including the current user. */
  revokeAllSessions() {
    return this.http.post<{ revoked: number }>(`${API_BASE_URL}/business/sessions/revoke-all`, {});
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

  uploadMenuItemImage(menuItemId: number, file: File) {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ menuItemId: number; imageUrl: string; imageVersion: number }>(
      `${API_BASE_URL}/business/menu/${menuItemId}/image`,
      form
    );
  }

  deleteMenuItemImage(menuItemId: number) {
    return this.http.delete<void>(`${API_BASE_URL}/business/menu/${menuItemId}/image`);
  }

  extractMenuFromText(rawText: string) {
    return this.http.post<{ categories: any[]; totalItemsExtracted: number }>(
      `${API_BASE_URL}/menus/ai-extract-text`,
      { rawText }
    );
  }

  bulkImportExtractedMenu(payload: any) {
    return this.http.post<{ categoriesCreated: number; itemsCreated: number; variantsCreated: number }>(
      `${API_BASE_URL}/menus/ai-bulk-import`,
      payload
    );
  }

  // Categories
  createCategory(name: string): Observable<BusinessCategory> {
    return this.http.post<BusinessCategory>(`${API_BASE_URL}/business/menu/categories`, { name });
  }

  updateCategory(categoryId: number, name: string): Observable<BusinessCategory> {
    return this.http.put<BusinessCategory>(`${API_BASE_URL}/business/menu/categories/${categoryId}`, { name });
  }

  deleteCategory(categoryId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/business/menu/categories/${categoryId}`);
  }

  // Variants
  getItemVariants(menuItemId: number): Observable<BusinessItemVariant[]> {
    return this.http.get<BusinessItemVariant[]>(`${API_BASE_URL}/business/menu/${menuItemId}/variants`);
  }

  createVariant(menuItemId: number, variantName: string, price: number): Observable<BusinessItemVariant> {
    return this.http.post<BusinessItemVariant>(`${API_BASE_URL}/business/menu/${menuItemId}/variants`, { variantName, price });
  }

  deleteVariant(menuItemId: number, variantId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/business/menu/${menuItemId}/variants/${variantId}`);
  }

  // Terminal
  reactivateTerminal(terminalId: number) {
    return this.http.post<void>(`${API_BASE_URL}/business/terminals/${terminalId}/reactivate`, {});
  }

  // Orders
  getOrderDetail(billId: number) {
    return this.http.get<OrderDetailResponse>(`${API_BASE_URL}/business/orders/${billId}`);
  }

  // ── Restaurant settings & compliance ─────────────────────────────────────
  requestUpdateMobileOtp(newMobileNumber: string): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/sync/config/users/update-mobile/request`, { newMobileNumber });
  }

  confirmUpdateMobile(newMobileNumber: string, otp: string): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/sync/config/users/update-mobile`, { newMobileNumber, otp });
  }

  uploadLogo(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(`${API_BASE_URL}/business/profile/logo`, formData);
  }

  deleteLogo(): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/business/profile/logo`);
  }

  lookupFssai(fssaiNo: string): Observable<any> {
    return this.http.get<any>(`${API_BASE_URL}/business/lookup/fssai`, { params: { fssaiNo } });
  }

  lookupGst(gstin: string): Observable<any> {
    return this.http.get<any>(`${API_BASE_URL}/business/lookup/gst`, { params: { gstin } });
  }

  lookupBoth(gstin?: string, fssaiNo?: string): Observable<any> {
    const params: Record<string, string> = {};
    if (gstin) params['gstin'] = gstin;
    if (fssaiNo) params['fssaiNo'] = fssaiNo;
    return this.http.get<any>(`${API_BASE_URL}/business/lookup/both`, { params });
  }

  getProfile(): Observable<any> {
    return this.http.get<any>(`${API_BASE_URL}/business/profile`);
  }

  updateProfile(payload: any): Observable<any> {
    return this.http.put<any>(`${API_BASE_URL}/business/profile`, payload);
  }

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

  // ── Analytics ────────────────────────────────────────────────────────────
  getHourlySales(date: string): Observable<HourlySalesRow[]> {
    return this.http.get<HourlySalesRow[]>(`${API_BASE_URL}/analytics/hourly-sales`, { params: { date } });
  }

  getItemSales(from: string, to: string): Observable<ItemSalesRow[]> {
    return this.http.get<ItemSalesRow[]>(`${API_BASE_URL}/analytics/item-sales`, { params: { from, to } });
  }

  getGstLedger(from: string, to: string): Observable<GstLedgerResponse> {
    return this.http.get<GstLedgerResponse>(`${API_BASE_URL}/analytics/gst-ledger`, { params: { from, to } });
  }

  // ── Terminal Fleet List ───────────────────────────────────────────────────
  getSyncTerminalList(): Observable<SyncTerminalItem[]> {
    return this.http.get<SyncTerminalItem[]>(`${API_BASE_URL}/sync/terminal/list`);
  }

  // ── Notifications ────────────────────────────────────────────────────────
  getNotifications(limit: number = 50): Observable<{ status: string; notifications: NotificationItem[]; unreadCount: number }> {
    return this.http.get<{ status: string; notifications: NotificationItem[]; unreadCount: number }>(`${API_BASE_URL}/notifications`, {
      params: { limit: limit.toString() }
    });
  }

  getUnreadNotificationCount(): Observable<{ status: string; unreadCount: number }> {
    return this.http.get<{ status: string; unreadCount: number }>(`${API_BASE_URL}/notifications/unread-count`);
  }

  markNotificationRead(id: number): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/notifications/${id}/read`, {});
  }

  markAllNotificationsRead(): Observable<any> {
    return this.http.post<any>(`${API_BASE_URL}/notifications/mark-all-read`, {});
  }
}

export interface MerchantAgreementStatus {
  hasAgreement: boolean;
  signedAt?: number;
  signerName?: string;
  agreementVersion?: string;
  originalFilename?: string;
}
