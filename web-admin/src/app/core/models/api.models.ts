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

export interface EasebuzzSubMerchant {
  id: number;
  restaurantId: number;
  subMerchantId: string | null;
  status: string;
  businessName: string;
  businessType: string | null;
  pan: string | null;
  gst: string | null;
  bankAccountNo: string | null;
  ifsc: string | null;
  beneficiaryName: string | null;
  businessAddress: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  kycStatus: string | null;
  kycPortalUrl: string | null;
  kycSubmittedAt: number | null;
  kycActivatedAt: number | null;
  commissionRate: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface MarketplaceOrder {
  id: number;
  platform: string;
  platformOrderId: string;
  orderStatus: string;
  customerName: string | null;
  customerPhone: string | null;
  subtotal: number | null;
  taxAmount: number | null;
  totalAmount: number;
  paymentMode: string | null;
  createdAt: number;
  acceptedAt: number | null;
  readyAt: number | null;
  rejectedAt: number | null;
  rejectedReason: string | null;
}

export interface MarketplaceOrderCounts {
  pending: number;
  accepted: number;
  ready: number;
  rejected: number;
}

export interface EasebuzzSubMerchantRequest {
  businessName: string;
  businessType: string;
  pan: string;
  gst?: string;
  bankAccountNo: string;
  ifsc: string;
  beneficiaryName: string;
  businessAddress: string;
  contactEmail: string;
  contactPhone: string;
  commissionRate: number;
}

