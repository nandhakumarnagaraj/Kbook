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
  onlineOrderCount: number;
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
  refundAmount: number | null;
  cancelReason: string | null;
  createdAt: number | null;
}

export interface RefundOrderRequest {
  refundAmount: number;
  reason: string;
}

export interface BusinessDashboard {
  restaurantId: number;
  shopName: string | null;
  totalStaff: number;
  totalMenuItems: number;
  posOrderCount: number;
  onlineOrderCount: number;
  pendingOnlineOrders: number;
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

export type PaymentGateway = 'EASEBUZZ';
export type PaymentEnvironment = 'TEST' | 'PROD';

export interface PaymentConfig {
  restaurantId: number;
  gateway: PaymentGateway;
  merchantKeyMasked: string;
  environment: PaymentEnvironment;
  active: boolean;
}

export interface SavePaymentConfigRequest {
  merchantKey: string;
  salt: string;
  environment: PaymentEnvironment;
}

export interface StorefrontOrder {
  orderId: number;
  publicOrderCode: string;
  customerName: string;
  customerPhone: string | null;
  fulfillmentType: string;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  sourceChannel: string;
  currency: string;
  totalAmount: number;
  createdAt: number;
  updatedAt: number;
}

export interface StorefrontOrderLineItem {
  menuItemId: number;
  itemVariantId: number | null;
  itemName: string;
  variantName: string | null;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  specialInstruction: string | null;
}

export interface StorefrontOrderDetail extends StorefrontOrder {
  restaurantId: number;
  trackingToken: string;
  customerNote: string | null;
  subtotal: number;
  items: StorefrontOrderLineItem[];
}
