export type SubscriptionStatus =
  | 'TRIAL'
  | 'ACTIVE'
  | 'PAST_DUE'
  | 'GRACE_PERIOD'
  | 'EXPIRED'
  | 'CANCELLED';

export type BillingCycle = 'MONTHLY' | 'YEARLY';

export interface SubscriptionPlan {
  code: string;
  name: string;
  description: string;
  priceMonthly: number;
  priceYearly: number;
  maxTerminals: number;
  maxStaffMembers: number;
  inventoryEnabled: boolean;
  aiMenuEnabled: boolean;
  onlinePaymentsEnabled: boolean;
  advancedReportsEnabled: boolean;
  isActive: boolean;
  sortOrder: number;
}

export interface SubscriptionSummary {
  restaurantId: number;
  planCode: string;
  planName: string;
  planDescription: string;
  status: SubscriptionStatus;
  billingCycle: BillingCycle;
  startedAt: number;
  currentPeriodStart: number;
  currentPeriodEnd: number;
  trialEndAt?: number | null;
  gracePeriodEndAt?: number | null;
  daysRemaining: number;
  isExpired: boolean;
  isInGracePeriod: boolean;
  autoRenew: boolean;
  maxTerminals: number;
  activeTerminalsCount: number;
  maxStaffMembers: number;
  activeStaffCount: number;
  inventoryEnabled: boolean;
  aiMenuEnabled: boolean;
  onlinePaymentsEnabled: boolean;
  advancedReportsEnabled: boolean;
  priceMonthly: number;
  priceYearly: number;
  features: Record<string, boolean>;
  notes?: string | null;
}

export interface ChangePlanRequest {
  planCode: string;
  billingCycle: BillingCycle;
}

export interface AdminSubscriptionOverrideRequest {
  planCode?: string;
  status?: SubscriptionStatus;
  billingCycle?: BillingCycle;
  extendDays?: number;
  autoRenew?: boolean;
  notes?: string;
}
