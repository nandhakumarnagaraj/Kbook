export interface AdminDashboardSummary {
  totalBusinesses: number;
  liveBusinesses: number;
  totalStaff: number;
  totalOrders: number;
  totalRevenue: number;
}

export interface AdminBusinessListItem {
  restaurantId: number;
  shopName: string | null;
  ownerName: string | null;
  ownerLoginId: string | null;
  whatsappNumber: string | null;
  email: string | null;
  websiteEnabled: boolean;
  easebuzzEnabled: boolean;
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
  createdAt: number | null;
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
