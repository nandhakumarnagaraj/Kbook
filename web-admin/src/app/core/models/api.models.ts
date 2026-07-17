export interface AdminDashboardSummary {
  totalBusinesses: number;
  liveBusinesses: number;
  totalStaff: number;
  totalOrders: number;
  totalRevenue: number;
  refundedOrders: number;
  refundedAmount: number;
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
}

export interface AdminBusinessDetail extends AdminBusinessListItem {
  ownerWhatsappNumber: string | null;
  shopAddress: string | null;
  currency: string | null;
  timezone: string | null;
  gstEnabled: boolean;
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
}

export interface RenameTerminalRequest {
  name: string;
}

export interface RejectTerminalRequest {
  reason?: string;
}

export interface RecoverTerminalRequest {
  deviceId: string;
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

