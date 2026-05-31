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
  gstin: string | null;
  fssaiNumber: string | null;
  whatsappNumber: string | null;
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
  zomatoOutletId?: string;
  zomatoEnabled?: boolean;
  swiggyApiKey?: string;
  swiggyWebhookSecret?: string;
  swiggyStoreId?: string;
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
  bankName: string | null;
  branchName: string | null;
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
  upiDeductionLtLimit: number | null;
  dcDeductionGtTwoThousand: number | null;
  idProofUrl: string | null;
  bankProofUrl: string | null;
  splitLabel: string | null;
  easebuzzResponse: string | null;
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
  completed: number;
  rejected: number;
}

export interface BusinessProfile {
  restaurantId: number;
  shopName: string | null;
  shopAddress: string | null;
  whatsappNumber: string | null;
  email: string | null;
  logoUrl: string | null;
  logoVersion: number | null;
  currency: string | null;
  upiEnabled: boolean | null;
  upiHandle: string | null;
  upiMobile: string | null;
  cashEnabled: boolean | null;
  posEnabled: boolean | null;
  zomatoEnabled: boolean | null;
  swiggyEnabled: boolean | null;
  ownWebsiteEnabled: boolean | null;
  country: string | null;
  timezone: string | null;
  gstEnabled: boolean | null;
  gstin: string | null;
  isTaxInclusive: boolean | null;
  gstPercentage: number | null;
  customTaxName: string | null;
  customTaxNumber: string | null;
  customTaxPercentage: number | null;
  fssaiNumber: string | null;
  fssaiExpiryDate: string | null;
  gstExpiryDate: string | null;
  reviewUrl: string | null;
  invoiceFooter: string | null;
  showBranding: boolean | null;
  maskCustomerPhone: boolean | null;
  easebuzzEnabled?: boolean | null;
}

export interface UpdateBusinessProfileRequest {
  shopName?: string;
  shopAddress?: string;
  whatsappNumber?: string;
  email?: string;
  currency?: string;
  upiEnabled?: boolean;
  upiHandle?: string;
  upiMobile?: string;
  cashEnabled?: boolean;
  posEnabled?: boolean;
  zomatoEnabled?: boolean;
  swiggyEnabled?: boolean;
  ownWebsiteEnabled?: boolean;
  easebuzzEnabled?: boolean;
  country?: string;
  timezone?: string;
  gstEnabled?: boolean;
  gstin?: string;
  isTaxInclusive?: boolean;
  gstPercentage?: number;
  customTaxName?: string;
  customTaxNumber?: string;
  customTaxPercentage?: number;
  fssaiNumber?: string;
  fssaiExpiryDate?: string | null;
  gstExpiryDate?: string | null;
  reviewUrl?: string;
  invoiceFooter?: string;
  showBranding?: boolean;
  maskCustomerPhone?: boolean;
}

export interface EasebuzzSubMerchantRequest {
  restaurantId?: number;
  businessName: string;
  businessType: string;
  pan: string;
  gst?: string;
  bankName?: string;
  branchName?: string;
  bankAccountNo: string;
  ifsc: string;
  beneficiaryName: string;
  businessAddress: string;
  contactEmail: string;
  contactPhone: string;
  commissionRate: number;
  upiDeductionLtLimit?: number;
  dcDeductionGtTwoThousand?: number;
}


// ── Pagination wrapper ────────────────────────────────────────────────────────
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;   // current page (0-indexed)
  size: number;
  first: boolean;
  last: boolean;
}

// ── Admin Transaction ─────────────────────────────────────────────────────────
export type TransactionStatus = 'success' | 'pending' | 'failed' | 'refunded' | 'cancelled';

export interface AdminTransaction {
  id: number;
  restaurantId: number;
  restaurantName: string | null;
  shopName: string | null;
  txnId: string;
  easebuzzTxnId: string | null;
  amount: number;
  status: TransactionStatus;
  paymentMode: string;
  gatewayTxnId: string | null;
  refundAmount: number | null;
  refundStatus: string | null;
  createdAt: number;
  updatedAt: number;
}

// ── Admin Settlement ──────────────────────────────────────────────────────────
export type SettlementStatus = 'pending' | 'processed' | 'failed' | 'on_hold';

export interface AdminSettlement {
  id: number;
  restaurantId: number;
  shopName: string | null;
  subMerchantId: string | null;
  amount: number;
  settlementDate: string;
  status: SettlementStatus;
  utr: string | null;
  bankAccount: string | null;
  createdAt: number;
}

// ── Admin Commission ──────────────────────────────────────────────────────────
export interface AdminCommission {
  id: number;
  restaurantId: number;
  shopName: string | null;
  commissionRate: number;
  totalCollected: number;
  pendingAmount: number;
  updatedAt: number;
}

export interface CommissionReport {
  restaurantId: number;
  shopName: string | null;
  totalTransactions: number;
  totalAmount: number;
  commissionRate: number;
  commissionEarned: number;
  period: string;
}

// ── Payment Dashboard ─────────────────────────────────────────────────────────
export interface PaymentDashboard {
  totalVolume: number;
  totalTransactions: number;
  successRate: number;
  pendingSettlements: number;
  totalCommissionEarned: number;
  activeSubMerchants: number;
  kycPendingCount: number;
  recentTransactions: AdminTransaction[];
}

// ── Payment Metrics (Phase 1.1) ──────────────────────────────────────────────
export interface PaymentMetricsOverview {
  totalTransactions: number;
  successfulTransactions: number;
  failedTransactions: number;
  overallSuccessRate: number;
  todayTotal: number;
  todaySuccess: number;
  todayFailed: number;
  todaySuccessRate: number;
  lastHourTotal: number;
  lastHourSuccess: number;
  lastHourSuccessRate: number;
  todayRevenue: number;
  todayOrders: number;
  byPaymentMethod: PaymentMethodMetric[];
}

export interface PaymentMethodMetric {
  method: string;
  total: number;
  successful: number;
  successRate: number;
}

export interface PaymentTrend {
  timestamp: number;
  total: number;
  successful: number;
  failed: number;
  successRate: number;
}

export interface FailedTransaction {
  id: number;
  restaurantId: number;
  shopName: string | null;
  txnId: string;
  easebuzzId: string | null;
  amount: number | null;
  status: string;
  receivedAt: number;
}

export interface PaymentAnomaly {
  type: string;
  severity: string;
  message: string;
  restaurantId?: number;
  shopName?: string;
  yesterdayRate?: number;
  todayRate?: number;
  drop?: number;
  totalTransactions?: number;
  successRate?: number;
}

// ── Split Retrieve ────────────────────────────────────────────────────────────
export interface SplitConfiguration {
  label: string;
  amount: number;
  percentage: number | null;
  status: string;
}

export interface SplitRetrieveResponse {
  status: string;
  merchant_request_id: string;
  split_configuration: SplitConfiguration[];
}

// ── Payout ────────────────────────────────────────────────────────────────────
export interface BeneficiaryDetails {
  name: string;
  accountNumber: string;
  ifsc: string;
  bankName: string;
}

export interface PayoutResponse {
  status: string;
  payoutId: string | null;
  message: string;
  amount: string;
}

// ── WIRE Platform ─────────────────────────────────────────────────────────────
export interface WireWebhookConfig {
  subMerchantId: string;
  merchantEmail?: string;
  eventType: string;
  url: string;
  intervalUnit: string;
  intervalValue: number;
  maxAttempts: number;
}

export interface WirePayoutWebhookConfig {
  subMerchantId: string;
  eventType: string;
  url: string;
  intervalUnit: string;
  intervalValue: number;
  maxAttempts: number;
}

export interface WireLookupResult {
  subMerchantId: string | null;
  businessName: string | null;
  status: string | null;
  kycStatus: string | null;
  email: string | null;
}

export interface KycProfileUrlResponse {
  status: string;
  kyc_url: string | null;
  sub_merchant_id: string;
}

// ── Refund Automation ────────────────────────────────────────────────────────
export interface RefundReason {
  code: string;
  label: string;
}

export interface RefundSummary {
  totalOrders: number;
  refundedOrders: number;
  totalRefundAmount: number;
  totalRevenue: number;
  refundRate: number;
}

// ── Webhook Health ───────────────────────────────────────────────────────────
export interface WebhookHealth {
  byStatus: Record<string, number>;
  pendingRetries: number;
  deadLetterCount: number;
  totalJobs: number;
}

export interface DeadLetterJob {
  id: number;
  webhookType: string;
  attemptCount: number;
  lastError: string | null;
  createdAt: number;
}

// ── Onboarding Progress ──────────────────────────────────────────────────────
export interface OnboardingStep {
  key: string;
  label: string;
  status: string;
}

export interface OnboardingProgress {
  restaurantId: number;
  shopName: string | null;
  steps: OnboardingStep[];
  totalSteps: number;
  completedSteps: number;
  isLive: boolean;
  totalOrders: number;
  totalRevenue: number;
}

// ── Instant Settlement ───────────────────────────────────────────────────────
export interface SettlementEstimate {
  restaurantId: number;
  totalSettled: number;
  fee: number;
  netPayout: number;
  feeRate: number;
  minimumAmount: number;
  eligible: boolean;
}

// ── Tax Compliance ───────────────────────────────────────────────────────────
export interface TaxSummary {
  restaurantId: number;
  gstin: string | null;
  taxRate: number;
  monthRevenue: number;
  monthTax: number;
  yearRevenue: number;
  yearTax: number;
}

export interface GstReport {
  restaurantId: number;
  shopName: string | null;
  gstin: string | null;
  period: string;
  totalOrders: number;
  taxableAmount: number;
  totalCgst: number;
  totalSgst: number;
  totalTax: number;
  totalRevenue: number;
}

// ── Chargeback ───────────────────────────────────────────────────────────────
export interface ChargebackSummary {
  restaurantId: number;
  totalChargebacks: number;
  unresolvedAmount: number;
  byStatus: Record<string, number>;
}

export interface FraudScore {
  score: number;
  risk: string;
  factors: string[];
}

// ── Financing ────────────────────────────────────────────────────────────────
export interface CreditEligibility {
  restaurantId: number;
  threeMonthRevenue: number;
  monthlyAverage: number;
  estimatedCreditLimit: number;
  dailyInterestRate: number;
  totalTransactions: number;
  eligible: boolean;
}

export interface LoanOption {
  days: number;
  requestedAmount: number;
  interest: number;
  totalRepayment: number;
  dailyRate: number;
}

// ── Unified Commerce ─────────────────────────────────────────────────────────
export interface UnifiedDashboard {
  restaurantId: number;
  today: { pos: number; swiggy: number; zomato: number; total: number };
  todayRevenue: { pos: number; marketplace: number; total: number };
  allTime: { pos: number; marketplace: number; total: number };
  channelBreakdown: { channel: string; todayOrders: number; totalOrders: number }[];
}

// ── Customer Data Platform ───────────────────────────────────────────────────
export interface CustomerInsights {
  restaurantId: number;
  totalCustomers: number;
  repeatCustomers: number;
  retentionRate: number;
  averageLtv: number;
  segments: Record<string, number>;
  topCustomers: { displayName: string; totalOrders: number; totalSpend: number; segment: string; lastOrderAt: number }[];
}

// ── Developer Portal ─────────────────────────────────────────────────────────
export interface ApiDocs {
  version: string;
  baseUrl: string;
  endpoints: { group: string; endpoints: { method: string; path: string; description: string; auth: string }[] }[];
  authentication: string;
  rateLimits: Record<string, string>;
}
