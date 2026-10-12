import { environment } from '../../../environments/environment';

/**
 * Builds the public invoice link for a bill.
 *
 * Previously this string was duplicated (with a hardcoded production host) in
 * orders, active-orders, reports, the dashboard, and the order-detail modal.
 * Deriving it from `environment.apiBaseUrl` keeps every environment honest and
 * means a host change happens in exactly one place.
 */
export function buildInvoiceLink(
  restaurantId: number | null | undefined,
  orderId: number | null | undefined,
  publicToken?: string | null
): string {
  const tenant = restaurantId ?? 1;
  const id = orderId ?? 0;
  const token = publicToken || 'preview';
  return `${environment.apiBaseUrl}/public/invoice/${tenant}/${id}/${token}`;
}
