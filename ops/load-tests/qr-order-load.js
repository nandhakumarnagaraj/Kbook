import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// QR self-service ordering load test — no auth required.
// Usage: k6 run -e BASE=https://staging.example.com -e RID=2247597590390850619 qr-order-load.js
// Get real menuItemId/variantId values from GET /menu first (see setup()).

const BASE = __ENV.BASE || 'http://localhost:8081';
const RID = __ENV.RID || '1';

const orderLatency = new Trend('qr_order_latency_ms');
const menuLatency = new Trend('qr_menu_latency_ms');
const errorRate = new Rate('errors');

export let options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '5m',
  thresholds: {
    qr_order_latency_ms: ['p(95)<400'],
    errors: ['rate<0.01'],
  },
};

let cachedItemIds = null;

function pickItems() {
  if (cachedItemIds && cachedItemIds.length > 0) return cachedItemIds;
  const res = http.get(`${BASE}/api/v1/public/restaurants/${RID}/menu`);
  menuLatency.add(res.timings.duration);
  if (res.status !== 200) return [];
  try {
    const items = res.json('items') || [];
    cachedItemIds = items
      .filter(i => i.id)
      .slice(0, 5)
      .map(i => {
        const variants = i.variants || [];
        return {
          menuItemId: i.id,
          variantId: variants.length > 0 ? variants[0].id : null,
          quantity: 1 + Math.floor(Math.random() * 3),
        };
      });
  } catch (e) {
    cachedItemIds = [];
  }
  return cachedItemIds;
}

export default function () {
  const lines = pickItems();
  if (lines.length === 0) {
    sleep(2);
    return;
  }
  // Random subset of 1-3 lines per order
  const count = 1 + Math.floor(Math.random() * Math.min(3, lines.length));
  const payload = JSON.stringify({
    items: lines.slice(0, count),
    orderType: Math.random() < 0.5 ? 'dine_in' : 'takeaway',
    tableLabel: `T${Math.floor(Math.random() * 20) + 1}`,
    customerNote: null,
  });

  const res = http.post(
    `${BASE}/api/v1/public/restaurants/${RID}/orders`,
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  );
  orderLatency.add(res.timings.duration);

  const ok = check(res, {
    'order created (200)': r => r.status === 200,
    'has orderId': r => r.json('orderId') !== undefined,
    'total is number': r => typeof r.json('total') === 'number',
  });
  errorRate.add(!ok);
  if (!ok && res.status === 429) {
    // Rate limited — this VU is done for a while; back off hard.
    sleep(60);
    return;
  }

  sleep(Math.random() * 2 + 0.5);
}

export function handleSummary(data) {
  const t = data.metrics.qr_order_latency_ms
    ? data.metrics.qr_order_latency_ms.values['p(95)']
    : -1;
  return {
    stdout: `\nQR ORDER P95: ${t} ms (target <= 400)\n` +
      `Errors: ${data.metrics.errors ? data.metrics.errors.values.rate : 'n/a'}\n`,
  };
}
