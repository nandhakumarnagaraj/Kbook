export interface AdminDashboardSummary {
  totalBusinesses: number;
  liveBusinesses: number;
  totalStaff: number;
  totalOrders: number;
  totalRevenue: number;
  refundedOrders: number;
  refundedAmount: number;
}

export interface FeatureFlagAdminItem {
  flagKey: string;
  killSwitched: boolean;
  defaultEnabled: boolean;
  description: string | null;
  updatedAt: number;
  effectiveState: boolean;
}

export interface FeatureFlagAuditItem {
  id: number;
  flagKey: string;
  scope: 'KILL_SWITCH' | 'DEFAULT' | 'OVERRIDE';
  restaurantId: number | null;
  previousState: string | null;
  newState: string;
  actorUserId: number | null;
  actorUsername: string | null;
  changedAt: number;
}

export interface AdminBusinessListItem {
  restaurantId: number;
  shopName: string | null;
  ownerName: string | null;
  ownerLoginId: string | null;
  whatsappNumber: string | null;
  email: string | null;
  websiteEnabled: boolean;
  staffCount: number;
  menuCount: number;
  orderCount: number;
  updatedAt: number | null;
  isSuspended: boolean;
}

export interface AdminBusinessDetail extends AdminBusinessListItem {
  ownerWhatsappNumber: string | null;
  shopAddress: string | null;
  currency: string | null;
  timezone: string | null;
  gstEnabled: boolean;
  gstin: string | null;
  printerEnabled: boolean;
  posOrderCount: number;
  totalRevenue: number;
  createdAt: number | null;
}

export interface BusinessOrder {
  sourceType: string;
  orderId: number;
  orderCode: string;
  customerName: string | null;
  customerContact: string | null;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  totalAmount: number;
  gatewayPaidAmount: number | null;
  refundAmount: number | null;
  refundStatus: string;
  refundMode: string | null;
  cancelReason: string | null;
  manualRefundAllowed: boolean;
  gatewayRefundAllowed: boolean;
  createdAt: number | null;
}

export interface PaginatedOrdersResponse {
  content: BusinessOrder[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface RefundOrderRequest {
  refundAmount: number;
  reason: string;
}

export interface BusinessDashboard {
  restaurantId: number;
  shopName: string | null;
  websiteEnabled: boolean;
  printerEnabled: boolean;
  kitchenPrinterEnabled: boolean;
  totalStaff: number;
  totalMenuItems: number;
  posOrderCount: number;
  pendingPosPayments: number;
  totalRevenue: number;
  todayRevenue: number;
  refundedOrders: number;
  refundedAmount: number;
  recentOrders: BusinessOrder[];
}

export interface BusinessMenuItem {
  menuItemId: number;
  categoryId: number;
  categoryName: string | null;
  name: string;
  description: string | null;
  foodType: string | null;
  basePrice: number;
  available: boolean;
  stockStatus: string;
  variantCount: number;
  updatedAt: number | null;
}

export interface BusinessCategory {
  categoryId: number;
  name: string;
}

export interface BusinessStaffItem {
  userId: number;
  name: string;
  loginId: string;
  email: string | null;
  whatsappNumber: string | null;
  role: string;
  active: boolean;
  updatedAt: number | null;
}

export interface BusinessMarketplaceSetup {
  restaurantId: number;
  shopName: string | null;
  paymentManagedByAdmin: boolean | null;
  subMerchantStatus: string | null;
  subMerchantId: string | null;
  kycPortalUrl: string | null;
  kycSubmittedAt: number | null;
  kycActivatedAt: number | null;
}

export interface MarketplaceConfig {
  zomatoEnabled: boolean;
  zomatoApiKeyMasked: string | null;
  zomatoOutletId: string | null;
  zomatoWebhookUrl: string | null;
  swiggyEnabled: boolean;
  swiggyApiKeyMasked: string | null;
  swiggyStoreId: string | null;
  swiggyWebhookUrl: string | null;
}

export interface MarketplaceConfigRequest {
  zomatoApiKey?: string;
  zomatoWebhookSecret?: string;
  zomatoEnabled?: boolean;
  swiggyApiKey?: string;
  swiggyWebhookSecret?: string;
  swiggyEnabled?: boolean;
}

export interface MarketplaceOrder {
  id: number;
  restaurantId: number;
  billId: number | null;
  platform: 'ZOMATO' | 'SWIGGY';
  platformOrderId: string;
  platformStatus: string | null;
  orderStatus: 'pending' | 'accepted' | 'preparing' | 'ready' | 'completed' | 'rejected';
  customerName: string | null;
  customerPhone: string | null;
  customerAddress: string | null;
  subtotal: number | null;
  taxAmount: number | null;
  totalAmount: number | null;
  paymentMode: string | null;
  acceptedAt: number | null;
  rejectedAt: number | null;
  rejectedReason: string | null;
  readyAt: number | null;
  completedAt: number | null;
  createdAt: number;
  updatedAt: number;
  syncedAt: number | null;
}

export interface MarketplaceOrderCounts {
  pending: number;
  accepted: number;
  ready: number;
  completed: number;
  rejected: number;
}

export interface BusinessTerminal {
  id: number;
  terminalSeries: string | null;
  terminalName: string | null;
  status: string;
  isActive: boolean | null;
  deviceId: string | null;
  credentialVersion: number | null;
  createdAt: number | null;
  updatedAt: number | null;
}

export interface TerminalRequest {
  id: number;
  deviceId: string | null;
  deviceModel: string | null;
  deviceName: string | null;
  requestType: string | null;
  status: string;
  matchedTerminalId: number | null;
  requestedAt: number | null;
  processedAt: number | null;
  rejectionReason: string | null;
  assignedTerminalId: number | null;
  challengeRequired?: boolean;
  challengeExpiresAt?: number | null;
}

export interface RenameTerminalRequest {
  name: string;
}

export interface RejectTerminalRequest {
  reason?: string;
}

export interface ApproveTerminalRequest {
  challengeCode?: string;
}

export interface RecoverTerminalRequest {
  deviceId: string;
}

export interface RecoverTerminalResponse {
  terminalId: number;
  terminalSeries: string | null;
  terminalName: string | null;
  terminalToken: string;
}

export type MenuExtractionStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface MenuExtractionJob {
  id: number;
  restaurantId: number;
  status: MenuExtractionStatus;
  extractedDataJson: string | null;
  createdAt: string | null;
  completedAt: string | null;
  errorMessage: string | null;
}

export interface MenuExtractionItem {
  itemName: string;
  halfPrice?: string;
  fullPrice?: string;
  price?: string;
  description?: string;
}

export type StaffRole = 'OWNER' | 'SHOP_ADMIN' | 'WAITER' | 'CASHIER' | 'MANAGER';

export interface CreateStaffRequest {
  name: string;
  phone: string;
  role: StaffRole;
  email?: string;
}

export interface StaffCreatedResponse {
  userId: number;
  name: string;
  phone: string;
  role: string;
  temporaryPassword: string;
}

export interface UpdateStaffRequest {
  name: string;
  phone: string;
  email?: string;
  role: StaffRole;
}

export interface CreateMenuItemRequest {
  name: string;
  categoryId: number;
  foodType: 'veg' | 'non-veg';
  basePrice: number;
  description?: string;
}

export interface UpdateMenuItemRequest {
  name: string;
  categoryId: number;
  foodType: 'veg' | 'non-veg';
  basePrice: number;
  description?: string;
}

export interface OrderDetailResponse {
  order: BusinessOrder;
  lineItems: OrderLineItem[];
}

export interface OrderLineItem {
  id: number;
  itemName: string;
  variantName?: string;
  quantity: number;
  price: number;
  itemTotal: number;
}


export interface AdminTransaction {
  id: number;
  restaurantId: number;
  amount: number;
  status: string;
  paymentMethod: string;
  gatewayTransactionId: string | null;
  createdAt: number;
}

export interface AdminSettlement {
  id: number;
  restaurantId: number;
  amount: number;
  status: string;
  settledAt: number | null;
  utr: string | null;
  shopName: string | null;
  createdAt: number | null;
}

export interface AdminCommission {
  id: number;
  restaurantId: number;
  shopName: string | null;
  ratePercent: number;
  flatFee: number;
  effectiveFrom: number;
  commissionRate: number | null;
  updatedAt: number | null;
}

export interface EasebuzzSubMerchant {
  id: number;
  restaurantId: number;
  shopName: string | null;
  subMerchantId: string | null;
  status: string;
  createdAt: number;
  businessName: string | null;
  businessType: string | null;
  pan: string | null;
  gst: string | null;
  bankAccountNo: string | null;
  ifsc: string | null;
  beneficiaryName: string | null;
  bankName: string | null;
  branchName: string | null;
  businessAddress: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  commissionRate: number | null;
  kycStatus: string | null;
  kycSubmittedAt: number | null;
}

export interface EasebuzzSubMerchantRequest {
  shopName?: string;
  email?: string;
  phone?: string;
  accountNumber?: string;
  ifsc?: string;
  restaurantId?: number;
  businessName?: string;
  businessType?: string;
  pan?: string;
  gst?: string;
  beneficiaryName?: string;
  bankName?: string;
  branchName?: string;
  businessAddress?: string;
  contactEmail?: string;
  contactPhone?: string;
  commissionRate?: number;
  [key: string]: any;
}

export interface CommissionReport {
  restaurantId: number;
  shopName: string | null;
  totalOrders: number;
  totalRevenue: number;
  commissionEarned: number;
  period: string;
  totalAmount: number;
  commissionRate: number;
  totalTransactions: number;
}

export interface UpdateBusinessProfileRequest {
  shopName?: string;
  ownerName?: string;
  email?: string;
  phone?: string;
  address?: string;
  gstNumber?: string;
  fssaiNumber?: string;
  whatsappNumber?: string;
  gstin?: string;
  shopAddress?: string;
  fssaiExpiryDate?: string;
  gstExpiryDate?: string;
  currency?: string;
  [key: string]: any;
}
