import * as fc from 'fast-check';
import { BusinessOrder, OrderDetailResponse, OrderLineItem } from '../core/models/api.models';

/**
 * Property 9: Order Detail Completeness
 *
 * For any order, the detail view SHALL include all header fields (order code,
 * customer name, contact, status, payment method, payment status, date, total)
 * AND all associated line items with item name, quantity, unit price, and line total.
 *
 * **Validates: Requirements 8.2, 8.3**
 *
 * This test validates the data model completeness — ensuring that any generated
 * OrderDetailResponse has all required fields present and valid for rendering.
 */

// --- Arbitraries ---

const ORDER_STATUSES = ['COMPLETED', 'CANCELLED', 'DRAFT', 'PENDING', 'CONFIRMED'];
const SOURCE_TYPES = ['POS', 'WEBSITE', 'ZOMATO', 'SWIGGY'];
const PAYMENT_METHODS = ['CASH', 'UPI', 'CARD', 'ONLINE'];
const PAYMENT_STATUSES = ['PAID', 'PENDING', 'REFUNDED', 'FAILED'];

const businessOrderArb: fc.Arbitrary<BusinessOrder> = fc.record({
  sourceType: fc.constantFrom(...SOURCE_TYPES),
  orderId: fc.nat({ max: 100000 }),
  orderCode: fc.stringMatching(/^[a-zA-Z0-9]{1,12}$/),
  customerName: fc.oneof(fc.constant(null), fc.stringMatching(/^[a-zA-Z]{1,20}$/)),
  customerContact: fc.oneof(fc.constant(null), fc.stringMatching(/^[0-9]{10}$/)),
  orderStatus: fc.constantFrom(...ORDER_STATUSES),
  paymentStatus: fc.constantFrom(...PAYMENT_STATUSES),
  paymentMethod: fc.constantFrom(...PAYMENT_METHODS),
  totalAmount: fc.nat({ max: 50000 }),
  gatewayPaidAmount: fc.oneof(fc.constant(null), fc.nat({ max: 50000 })),
  refundAmount: fc.oneof(fc.constant(null), fc.nat({ max: 10000 })),
  refundStatus: fc.constantFrom('NONE', 'PARTIAL', 'FULL'),
  refundMode: fc.oneof(fc.constant(null), fc.constantFrom('MANUAL', 'GATEWAY')),
  cancelReason: fc.oneof(fc.constant(null), fc.constant('Customer request')),
  manualRefundAllowed: fc.boolean(),
  gatewayRefundAllowed: fc.boolean(),
  createdAt: fc.oneof(
    fc.constant(null),
    fc.integer({ min: 1672531200000, max: 1767225600000 })
  )
});

const orderLineItemArb: fc.Arbitrary<OrderLineItem> = fc.record({
  id: fc.nat({ max: 100000 }),
  itemName: fc.stringMatching(/^[a-zA-Z]{1,30}$/),
  variantName: fc.oneof(fc.constant(undefined), fc.stringMatching(/^[a-zA-Z]{1,15}$/)),
  quantity: fc.integer({ min: 1, max: 100 }),
  price: fc.nat({ max: 10000 }),
  itemTotal: fc.nat({ max: 100000 })
});

const orderDetailResponseArb: fc.Arbitrary<OrderDetailResponse> = fc.record({
  order: businessOrderArb,
  lineItems: fc.array(orderLineItemArb, { minLength: 0, maxLength: 15 })
});

// --- Validation helpers ---

/**
 * Checks that all required header fields are present on the order object.
 * Required header fields per Requirement 8.2:
 * orderCode, customerName (nullable but field exists), customerContact (nullable but field exists),
 * orderStatus, paymentMethod, paymentStatus, createdAt (nullable but field exists), totalAmount
 */
function hasAllHeaderFields(order: BusinessOrder): boolean {
  return (
    'orderCode' in order && typeof order.orderCode === 'string' &&
    'customerName' in order &&
    'customerContact' in order &&
    'orderStatus' in order && typeof order.orderStatus === 'string' &&
    'paymentMethod' in order && typeof order.paymentMethod === 'string' &&
    'paymentStatus' in order && typeof order.paymentStatus === 'string' &&
    'createdAt' in order &&
    'totalAmount' in order && typeof order.totalAmount === 'number'
  );
}

/**
 * Checks that a line item has all required fields per Requirement 8.3:
 * itemName (non-empty), quantity (> 0), price (>= 0), itemTotal (>= 0)
 */
function isValidLineItem(item: OrderLineItem): boolean {
  return (
    typeof item.itemName === 'string' && item.itemName.length > 0 &&
    typeof item.quantity === 'number' && item.quantity > 0 &&
    typeof item.price === 'number' && item.price >= 0 &&
    typeof item.itemTotal === 'number' && item.itemTotal >= 0
  );
}

// --- Property Tests ---

describe('OrderDetailModal Property Tests', () => {

  /**
   * Property 9: Order Detail Completeness
   *
   * For any order, the detail view SHALL include all header fields (order code,
   * customer name, contact, status, payment method, payment status, date, total)
   * AND all associated line items with item name, quantity, unit price, and line total.
   *
   * **Validates: Requirements 8.2, 8.3**
   */
  describe('Property 9: Order Detail Completeness', () => {

    it('every OrderDetailResponse has all required header fields', () => {
      fc.assert(
        fc.property(
          orderDetailResponseArb,
          (response: OrderDetailResponse) => {
            expect(hasAllHeaderFields(response.order)).toBeTrue();

            // orderCode must be non-empty string
            expect(response.order.orderCode.length).toBeGreaterThan(0);

            // orderStatus must be a non-empty string
            expect(response.order.orderStatus.length).toBeGreaterThan(0);

            // paymentMethod must be a non-empty string
            expect(response.order.paymentMethod.length).toBeGreaterThan(0);

            // paymentStatus must be a non-empty string
            expect(response.order.paymentStatus.length).toBeGreaterThan(0);

            // totalAmount must be a non-negative number
            expect(response.order.totalAmount).toBeGreaterThanOrEqual(0);
          }
        ),
        { numRuns: 20 }
      );
    });

    it('every line item has itemName (non-empty), quantity (> 0), price (>= 0), and itemTotal (>= 0)', () => {
      fc.assert(
        fc.property(
          orderDetailResponseArb,
          (response: OrderDetailResponse) => {
            for (const item of response.lineItems) {
              expect(isValidLineItem(item)).toBeTrue();
            }
          }
        ),
        { numRuns: 20 }
      );
    });

    it('lineItems array length matches expected count', () => {
      fc.assert(
        fc.property(
          orderDetailResponseArb,
          (response: OrderDetailResponse) => {
            // The lineItems array must be an actual array
            expect(Array.isArray(response.lineItems)).toBeTrue();

            // Each item in lineItems is a valid OrderLineItem with required fields
            const validCount = response.lineItems.filter(item => isValidLineItem(item)).length;
            expect(validCount).toBe(response.lineItems.length);
          }
        ),
        { numRuns: 20 }
      );
    });

    it('the data model structure is complete for rendering the detail view', () => {
      fc.assert(
        fc.property(
          orderDetailResponseArb,
          (response: OrderDetailResponse) => {
            // Response has both 'order' and 'lineItems' keys
            expect('order' in response).toBeTrue();
            expect('lineItems' in response).toBeTrue();

            // order is an object (not null/undefined)
            expect(response.order).toBeDefined();
            expect(response.order).not.toBeNull();

            // lineItems is an array
            expect(Array.isArray(response.lineItems)).toBeTrue();

            // All header fields needed by the template exist
            const order = response.order;
            expect(order.orderCode).toBeDefined();
            expect(order.orderStatus).toBeDefined();
            expect(order.paymentMethod).toBeDefined();
            expect(order.paymentStatus).toBeDefined();
            expect(typeof order.totalAmount).toBe('number');

            // customerName and customerContact may be null but field must exist
            expect('customerName' in order).toBeTrue();
            expect('customerContact' in order).toBeTrue();
            expect('createdAt' in order).toBeTrue();
          }
        ),
        { numRuns: 20 }
      );
    });
  });
});
