import * as fc from 'fast-check';
import { BusinessOrder } from '../../core/models/api.models';
import { formatDate } from '../../shared/formatters';
import {
  escapeCsvField as escapeCsvFieldProduction,
  filterBusinessOrders
} from './orders-page.component';
import { getPresetDateRange } from '../../shared/date-range-selector.component';

// --- Extracted pure filter logic (mirrors OrdersPageComponent) ---

interface OrderFilters {
  searchTerm: string;
  statusFilter: string;
  sourceFilter: string;
  dateFrom: string | null;
  dateTo: string | null;
}

function matchesDateRange(order: BusinessOrder, dateFrom: string | null, dateTo: string | null): boolean {
  if (!dateFrom || !dateTo) return true;
  if (!order.createdAt) return false;

  const orderDate = new Date(order.createdAt);
  const fromDate = new Date(dateFrom + 'T00:00:00');
  const toDate = new Date(dateTo + 'T23:59:59.999');
  return orderDate >= fromDate && orderDate <= toDate;
}

function matchesSearch(order: BusinessOrder, searchTerm: string): boolean {
  const search = searchTerm.trim().toLowerCase();
  if (!search) return true;
  return [
    order.orderCode,
    order.customerName ?? '',
    order.customerContact ?? '',
    order.paymentMethod,
    order.paymentStatus
  ].some((value) => value.toLowerCase().includes(search));
}

function matchesStatus(order: BusinessOrder, statusFilter: string): boolean {
  return statusFilter === 'ALL' || order.orderStatus === statusFilter;
}

function matchesSource(order: BusinessOrder, sourceFilter: string): boolean {
  return sourceFilter === 'ALL' || order.sourceType === sourceFilter;
}

function filterOrders(orders: BusinessOrder[], filters: OrderFilters): BusinessOrder[] {
  return filterBusinessOrders(orders, filters);
}

// --- Extracted pure CSV generation logic (mirrors OrdersPageComponent.exportCsv) ---

function escapeCsvField(value: string): string {
  return escapeCsvFieldProduction(value);
}

function generateCsvContent(orders: BusinessOrder[]): string {
  const headers = [
    'Order Code', 'Source', 'Customer Name', 'Customer Contact',
    'Order Status', 'Payment Method', 'Payment Status',
    'Total Amount', 'Refund Amount', 'Created Date'
  ];

  const rows = orders.map(order => [
    order.orderCode,
    order.sourceType,
    order.customerName ?? '',
    order.customerContact ?? '',
    order.orderStatus,
    order.paymentMethod,
    order.paymentStatus,
    String(order.totalAmount ?? 0),
    String(order.refundAmount ?? 0),
    order.createdAt ? formatDate(order.createdAt) : ''
  ]);

  return [headers, ...rows]
    .map(row => row.map(field => escapeCsvField(field)).join(','))
    .join('\n');
}

// --- CSV parser that respects quoted fields ---

function parseCsvRow(row: string): string[] {
  const fields: string[] = [];
  let current = '';
  let inQuotes = false;

  for (let i = 0; i < row.length; i++) {
    const ch = row[i];
    if (inQuotes) {
      if (ch === '"') {
        if (i + 1 < row.length && row[i + 1] === '"') {
          current += '"';
          i++; // skip escaped quote
        } else {
          inQuotes = false;
        }
      } else {
        current += ch;
      }
    } else {
      if (ch === '"') {
        inQuotes = true;
      } else if (ch === ',') {
        fields.push(current);
        current = '';
      } else {
        current += ch;
      }
    }
  }
  fields.push(current);
  return fields;
}

// --- Generators (fast-check v4.x API) ---

const ORDER_STATUSES = ['COMPLETED', 'CANCELLED', 'DRAFT', 'PENDING', 'CONFIRMED'];
const ORDER_SOURCES = ['POS', 'WEBSITE', 'ZOMATO', 'SWIGGY'];
const PAYMENT_METHODS = ['CASH', 'UPI', 'CARD', 'GATEWAY'];
const PAYMENT_STATUSES = ['PAID', 'PENDING', 'REFUNDED', 'FAILED'];

// Alphanumeric string generator using stringMatching
const alphanumericStr = (min: number, max: number): fc.Arbitrary<string> =>
  fc.stringMatching(new RegExp(`^[a-zA-Z0-9]{${min},${max}}$`));

// Alpha-only string generator
const alphaStr = (min: number, max: number): fc.Arbitrary<string> =>
  fc.stringMatching(new RegExp(`^[a-zA-Z]{${min},${max}}$`));

// Digit-only string generator
const digitStr = (len: number): fc.Arbitrary<string> =>
  fc.stringMatching(new RegExp(`^[0-9]{${len}}$`));

const businessOrderArb: fc.Arbitrary<BusinessOrder> = fc.record({
  sourceType: fc.constantFrom(...ORDER_SOURCES),
  orderId: fc.nat({ max: 100000 }),
  orderCode: alphanumericStr(3, 10),
  customerName: fc.option(alphaStr(1, 20), { nil: null }),
  customerContact: fc.option(digitStr(10), { nil: null }),
  orderStatus: fc.constantFrom(...ORDER_STATUSES),
  paymentStatus: fc.constantFrom(...PAYMENT_STATUSES),
  paymentMethod: fc.constantFrom(...PAYMENT_METHODS),
  totalAmount: fc.nat({ max: 50000 }),
  gatewayPaidAmount: fc.option(fc.nat({ max: 50000 }), { nil: null }),
  refundAmount: fc.option(fc.nat({ max: 10000 }), { nil: null }),
  refundStatus: fc.constantFrom('NONE', 'PARTIAL', 'FULL'),
  refundMode: fc.option(fc.constantFrom('MANUAL', 'GATEWAY'), { nil: null }),
  cancelReason: fc.option(alphaStr(1, 20), { nil: null }),
  manualRefundAllowed: fc.boolean(),
  gatewayRefundAllowed: fc.boolean(),
  createdAt: fc.option(
    // Generate timestamps within a 2-year window (2023-2024)
    fc.integer({ min: 1672531200000, max: 1735689599000 }),
    { nil: null }
  )
});

// Generate a valid YYYY-MM-DD date string within the 2023-2024 range
const dateStringArb: fc.Arbitrary<string> = fc.integer({ min: 1672531200000, max: 1735689599000 }).map(ts => {
  const d = new Date(ts);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
});

// Generate a date range where from <= to
const dateRangeArb: fc.Arbitrary<{ from: string; to: string }> = fc.tuple(dateStringArb, dateStringArb).map(([a, b]) => {
  return a <= b ? { from: a, to: b } : { from: b, to: a };
});

// Search text generator (alpha only, safe for search matching)
const searchTextArb: fc.Arbitrary<string> = fc.oneof(
  fc.constant(''),
  alphaStr(1, 5)
);

// --- Tests ---

describe('Orders Page Property Tests', () => {

  it('uses local calendar dates for month presets', () => {
    expect(getPresetDateRange('this-month', new Date(2026, 6, 18, 0, 15)))
      .toEqual({ from: '2026-07-01', to: '2026-07-18' });
  });

  it('neutralizes spreadsheet formulas in CSV fields', () => {
    ['=1+1', '+SUM(A1:A2)', '-2+3', '@cmd'].forEach(value => {
      expect(escapeCsvField(value).startsWith("'")).toBe(true);
    });
  });

  /**
   * **Validates: Requirements 5.4, 7.2**
   *
   * Property 7: Date Range Filtering - For any date range [from, to] and any set of
   * timestamped orders, filtered result contains exactly those records whose createdAt
   * falls within the inclusive range [from 00:00:00, to 23:59:59.999].
   */
  describe('Property 7: Date Range Filtering', () => {
    it('should include exactly those orders whose createdAt falls within [from 00:00:00, to 23:59:59.999]', () => {
      fc.assert(
        fc.property(
          fc.array(businessOrderArb, { minLength: 0, maxLength: 30 }),
          dateRangeArb,
          (orders: BusinessOrder[], dateRange: { from: string; to: string }) => {
            const fromStart = new Date(dateRange.from + 'T00:00:00').getTime();
            const toEnd = new Date(dateRange.to + 'T23:59:59.999').getTime();

            const filtered = filterOrders(orders, {
              searchTerm: '',
              statusFilter: 'ALL',
              sourceFilter: 'ALL',
              dateFrom: dateRange.from,
              dateTo: dateRange.to
            });

            // Every filtered order must have createdAt within range
            for (const order of filtered) {
              expect(order.createdAt).not.toBeNull();
              expect(order.createdAt!).toBeGreaterThanOrEqual(fromStart);
              expect(order.createdAt!).toBeLessThanOrEqual(toEnd);
            }

            // Every order in the original set that has createdAt in range must be in filtered
            const expected = orders.filter(o => {
              if (o.createdAt === null) return false;
              return o.createdAt >= fromStart && o.createdAt <= toEnd;
            });

            expect(filtered.length).toBe(expected.length);
          }
        ),
        { numRuns: 20 }
      );
    });

    it('should exclude orders with null createdAt when a date range is active', () => {
      fc.assert(
        fc.property(
          fc.array(businessOrderArb, { minLength: 1, maxLength: 20 }),
          dateRangeArb,
          (orders: BusinessOrder[], dateRange: { from: string; to: string }) => {
            const filtered = filterOrders(orders, {
              searchTerm: '',
              statusFilter: 'ALL',
              sourceFilter: 'ALL',
              dateFrom: dateRange.from,
              dateTo: dateRange.to
            });

            // No filtered order should have null createdAt
            for (const order of filtered) {
              expect(order.createdAt).not.toBeNull();
            }
          }
        ),
        { numRuns: 20 }
      );
    });
  });

  /**
   * **Validates: Requirements 7.5**
   *
   * Property 8: Filter Composition (AND Logic) - For any combination of active filters
   * (date range, status, source, search text), the resulting order list is the intersection
   * of all individual filter results.
   */
  describe('Property 8: Filter Composition (AND Logic)', () => {
    it('should produce results equal to the intersection of all individual filter results', () => {
      fc.assert(
        fc.property(
          fc.array(businessOrderArb, { minLength: 0, maxLength: 30 }),
          fc.constantFrom(...ORDER_STATUSES),
          fc.constantFrom(...ORDER_SOURCES),
          dateRangeArb,
          searchTextArb,
          (orders: BusinessOrder[], status: string, source: string, dateRange: { from: string; to: string }, searchText: string) => {
            // Combined filter
            const combinedResult = filterOrders(orders, {
              searchTerm: searchText,
              statusFilter: status,
              sourceFilter: source,
              dateFrom: dateRange.from,
              dateTo: dateRange.to
            });

            // Individual filters
            const bySearch = filterOrders(orders, {
              searchTerm: searchText,
              statusFilter: 'ALL',
              sourceFilter: 'ALL',
              dateFrom: null,
              dateTo: null
            });

            const byStatus = filterOrders(orders, {
              searchTerm: '',
              statusFilter: status,
              sourceFilter: 'ALL',
              dateFrom: null,
              dateTo: null
            });

            const bySource = filterOrders(orders, {
              searchTerm: '',
              statusFilter: 'ALL',
              sourceFilter: source,
              dateFrom: null,
              dateTo: null
            });

            const byDate = filterOrders(orders, {
              searchTerm: '',
              statusFilter: 'ALL',
              sourceFilter: 'ALL',
              dateFrom: dateRange.from,
              dateTo: dateRange.to
            });

            // Intersection: an order must appear in ALL individual results
            const intersection = orders.filter(o =>
              bySearch.includes(o) &&
              byStatus.includes(o) &&
              bySource.includes(o) &&
              byDate.includes(o)
            );

            // Combined result should equal the intersection
            expect(combinedResult.length).toBe(intersection.length);

            // Verify same orders (by reference identity since we use includes)
            const combinedIds = combinedResult.map(o => o.orderId).sort((a, b) => a - b);
            const intersectionIds = intersection.map(o => o.orderId).sort((a, b) => a - b);
            expect(combinedIds).toEqual(intersectionIds);
          }
        ),
        { numRuns: 20 }
      );
    });
  });

  /**
   * **Validates: Requirements 9.2, 9.3**
   *
   * Property 10: CSV Export Correctness - For any set of orders, the generated CSV
   * contains exactly those orders with correct columns.
   */
  describe('Property 10: CSV Export Correctness', () => {
    it('should generate CSV with correct header and one row per order', () => {
      fc.assert(
        fc.property(
          fc.array(businessOrderArb, { minLength: 1, maxLength: 30 }),
          (orders: BusinessOrder[]) => {
            const csv = generateCsvContent(orders);
            const lines = csv.split('\n');

            // First line is the header
            const headerLine = lines[0];
            expect(headerLine).toContain('Order Code');
            expect(headerLine).toContain('Source');
            expect(headerLine).toContain('Customer Name');
            expect(headerLine).toContain('Customer Contact');
            expect(headerLine).toContain('Order Status');
            expect(headerLine).toContain('Payment Method');
            expect(headerLine).toContain('Payment Status');
            expect(headerLine).toContain('Total Amount');
            expect(headerLine).toContain('Refund Amount');
            expect(headerLine).toContain('Created Date');

            // Number of data rows equals number of orders
            expect(lines.length).toBe(orders.length + 1);
          }
        ),
        { numRuns: 20 }
      );
    });

    it('should include correct order data in each CSV row', () => {
      fc.assert(
        fc.property(
          fc.array(businessOrderArb, { minLength: 1, maxLength: 15 }),
          (orders: BusinessOrder[]) => {
            const csv = generateCsvContent(orders);
            const lines = csv.split('\n');

            // For each order, verify that key fields appear in the corresponding CSV row
            for (let i = 0; i < orders.length; i++) {
              const order = orders[i];
              const rowLine = lines[i + 1]; // skip header

              // The orderCode should appear in the row (it's alphanumeric, no escaping needed)
              expect(rowLine).toContain(order.orderCode);
              // The sourceType should appear in the row
              expect(rowLine).toContain(order.sourceType);
              // The orderStatus should appear in the row
              expect(rowLine).toContain(order.orderStatus);
              // The paymentMethod should appear in the row
              expect(rowLine).toContain(order.paymentMethod);
              // The totalAmount should appear as string
              expect(rowLine).toContain(String(order.totalAmount ?? 0));
            }
          }
        ),
        { numRuns: 20 }
      );
    });

    it('should produce exactly 10 columns per row (matching the header count)', () => {
      // Use orders with null createdAt to avoid date formatting that contains commas
      const simpleOrderArb: fc.Arbitrary<BusinessOrder> = fc.record({
        sourceType: fc.constantFrom(...ORDER_SOURCES),
        orderId: fc.nat({ max: 100000 }),
        orderCode: alphanumericStr(3, 10),
        customerName: fc.option(alphaStr(1, 10), { nil: null }),
        customerContact: fc.option(digitStr(10), { nil: null }),
        orderStatus: fc.constantFrom(...ORDER_STATUSES),
        paymentStatus: fc.constantFrom(...PAYMENT_STATUSES),
        paymentMethod: fc.constantFrom(...PAYMENT_METHODS),
        totalAmount: fc.nat({ max: 50000 }),
        gatewayPaidAmount: fc.option(fc.nat({ max: 50000 }), { nil: null }),
        refundAmount: fc.option(fc.nat({ max: 10000 }), { nil: null }),
        refundStatus: fc.constantFrom('NONE', 'PARTIAL', 'FULL'),
        refundMode: fc.option(fc.constantFrom('MANUAL', 'GATEWAY'), { nil: null }),
        cancelReason: fc.constant(null as string | null),
        manualRefundAllowed: fc.boolean(),
        gatewayRefundAllowed: fc.boolean(),
        createdAt: fc.constant(null as number | null)
      });

      fc.assert(
        fc.property(
          fc.array(simpleOrderArb, { minLength: 1, maxLength: 15 }),
          (orders: BusinessOrder[]) => {
            const csv = generateCsvContent(orders);
            const lines = csv.split('\n');

            // Header has 10 columns
            const headerCols = lines[0].split(',');
            expect(headerCols.length).toBe(10);

            // Each data row also has 10 columns (no dates means no commas in fields)
            for (let i = 1; i < lines.length; i++) {
              const cols = lines[i].split(',');
              expect(cols.length).toBe(10);
            }
          }
        ),
        { numRuns: 20 }
      );
    });

    it('should properly CSV-escape fields containing commas (like formatted dates)', () => {
      fc.assert(
        fc.property(
          fc.array(businessOrderArb, { minLength: 1, maxLength: 10 }),
          (orders: BusinessOrder[]) => {
            const csv = generateCsvContent(orders);
            const lines = csv.split('\n');

            // Parse CSV properly respecting quoted fields
            for (let i = 1; i < lines.length; i++) {
              const cols = parseCsvRow(lines[i]);
              expect(cols.length).toBe(10);
            }
          }
        ),
        { numRuns: 20 }
      );
    });
  });
});
