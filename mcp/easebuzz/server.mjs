#!/usr/bin/env node
import { createHash } from 'node:crypto';
import readline from 'node:readline';

const ENV = process.env.EASEBUZZ_ENV || 'prod';
const BASE_URL = (process.env.EASEBUZZ_BASE_URL || 'https://pay.easebuzz.in').replace(/\/+$/, '');
const KEY = process.env.EASEBUZZ_KEY || process.env.EASEBUZZ_MERCHANT_KEY || '';
const SALT = process.env.EASEBUZZ_SALT || '';
const WIRE_KEY = process.env.EASEBUZZ_WIRE_API_KEY || '';
const API = process.env.EASEBUZZ_API || 'payment';

const sha512 = (value) => createHash('sha512').update(value, 'utf8').digest('hex');

const paymentHash = (parts) => sha512(parts.map((p) => (p == null ? '' : String(p))).join('|') + '|' + SALT);

const requireCreds = () => {
  if (!KEY || !SALT) throw new Error('EASEBUZZ_KEY and EASEBUZZ_SALT are not configured');
};

const json = (value) => ({ content: [{ type: 'text', text: JSON.stringify(value, null, 2) }] });

const fail = (message) => ({ content: [{ type: 'text', text: String(message) }], isError: true });

const parseResponse = async (res) => {
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { http_status: res.status, raw: text.slice(0, 2000) };
  }
};

const postForm = async (url, params, headers = {}) => {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded', accept: 'application/json', ...headers },
    body: new URLSearchParams(params).toString()
  });
  return { status: res.status, body: await parseResponse(res) };
};

const TOOLS = [
  {
    name: 'easebuzz_config',
    description: 'Show the active Easebuzz environment (base URL, API kind, credential presence). Secrets are never returned.',
    inputSchema: { type: 'object', properties: {} }
  },
  {
    name: 'easebuzz_payment_link',
    description: 'Create an Easebuzz hosted payment link via /payment/initiateLink and return the payment URL.',
    inputSchema: {
      type: 'object',
      properties: {
        txnid: { type: 'string' },
        amount: { type: 'string', description: 'Amount as string, e.g. "1499.00"' },
        productinfo: { type: 'string' },
        firstname: { type: 'string' },
        email: { type: 'string' },
        phone: { type: 'string' },
        udf1: { type: 'string' },
        udf2: { type: 'string' },
        udf3: { type: 'string' },
        udf4: { type: 'string' },
        udf5: { type: 'string' },
        udf6: { type: 'string' },
        udf7: { type: 'string' },
        udf8: { type: 'string' },
        udf9: { type: 'string' },
        udf10: { type: 'string' },
        sub_merchant_id: { type: 'string' },
        surl: { type: 'string' },
        furl: { type: 'string' }
      },
      required: ['txnid', 'amount', 'productinfo', 'firstname', 'email']
    }
  },
  {
    name: 'easebuzz_transaction_status',
    description: 'Retrieve an Easebuzz transaction from the dashboard API (tries /transaction/v2.1/retrieve then /transaction/v2/retrieve).',
    inputSchema: {
      type: 'object',
      properties: { txnid: { type: 'string' } },
      required: ['txnid']
    }
  },
  {
    name: 'easebuzz_refund_status',
    description: 'Look up refund status. Hash: sha512(key|easebuzz_id|salt).',
    inputSchema: {
      type: 'object',
      properties: { easebuzz_id: { type: 'string' }, merchant_refund_id: { type: 'string' } },
      required: ['easebuzz_id']
    }
  },
  {
    name: 'easebuzz_hash',
    description: 'Compute an Easebuzz SHA-512 hash from raw parts (salt is appended automatically).',
    inputSchema: {
      type: 'object',
      properties: {
        parts: { type: 'array', items: { type: 'string' } },
        raw: { type: 'string', description: 'Use instead of parts to hash an exact string (no salt appended)' }
      }
    }
  },
  {
    name: 'easebuzz_request',
    description: 'Generic form-encoded request. Optionally add key and a hash computed over hash_fields (values in the given order) or hash_parts (raw values).',
    inputSchema: {
      type: 'object',
      properties: {
        path: { type: 'string', description: 'Path beginning with /, e.g. /transaction/v2/refund' },
        params: { type: 'object', additionalProperties: { type: 'string' } },
        method: { type: 'string', enum: ['POST', 'GET'] },
        hash_fields: { type: 'array', items: { type: 'string' } },
        hash_parts: { type: 'array', items: { type: 'string' } },
        add_key: { type: 'boolean' }
      },
      required: ['path']
    }
  },
  {
    name: 'easebuzz_wire_request',
    description: 'Call the Easebuzz WIRE (payouts/InstaCollect) API with Authorization sha512(key|salt) and WIRE-API-KEY headers.',
    inputSchema: {
      type: 'object',
      properties: {
        path: { type: 'string' },
        method: { type: 'string', enum: ['GET', 'POST', 'PUT', 'DELETE'] },
        query: { type: 'object', additionalProperties: { type: 'string' } },
        body: { type: 'object' }
      },
      required: ['path']
    }
  }
];

const handlers = {
  async easebuzz_config() {
    return {
      env: ENV,
      api: API,
      base_url: BASE_URL,
      key_set: Boolean(KEY),
      salt_set: Boolean(SALT),
      wire_key_set: Boolean(WIRE_KEY)
    };
  },

  async easebuzz_payment_link(args) {
    requireCreds();
    const udfs = Array.from({ length: 10 }, (_, i) => args[`udf${i + 1}`] || '');
    const hash = paymentHash([
      KEY,
      args.txnid,
      args.amount,
      args.productinfo,
      args.firstname,
      args.email,
      ...udfs
    ]);
    const params = {
      key: KEY,
      txnid: args.txnid,
      amount: args.amount,
      productinfo: args.productinfo,
      firstname: args.firstname,
      email: args.email,
      phone: args.phone || '',
      surl: args.surl || process.env.EASEBUZZ_RETURN_URL || '',
      furl: args.furl || process.env.EASEBUZZ_RETURN_URL || '',
      notify_url: process.env.EASEBUZZ_NOTIFY_URL || '',
      hash,
      ...Object.fromEntries(udfs.map((v, i) => [`udf${i + 1}`, v]))
    };
    if (args.sub_merchant_id) params.sub_merchant_id = args.sub_merchant_id;
    const res = await postForm(`${BASE_URL}/payment/initiateLink`, params);
    const body = res.body || {};
    const accessKey = body.data;
    const ok = body.status === true || body.status === 'success' || body.status === 1;
    return {
      http_status: res.status,
      status: ok ? 'success' : 'failure',
      access_key: ok ? accessKey : undefined,
      payment_url: ok && typeof accessKey === 'string' ? `${BASE_URL}/pay/${accessKey}` : undefined,
      response: body
    };
  },

  async easebuzz_transaction_status(args) {
    requireCreds();
    const params = { key: KEY, txnid: args.txnid, hash: paymentHash([KEY, args.txnid]) };
    const first = await postForm(`${process.env.EASEBUZZ_DASHBOARD_BASE_URL || 'https://dashboard.easebuzz.in'}/transaction/v2.1/retrieve`, params);
    if ([404, 405].includes(first.status)) {
      const second = await postForm(`${process.env.EASEBUZZ_DASHBOARD_BASE_URL || 'https://dashboard.easebuzz.in'}/transaction/v2/retrieve`, params);
      return { endpoint: 'v2', ...second };
    }
    return { endpoint: 'v2.1', ...first };
  },

  async easebuzz_refund_status(args) {
    requireCreds();
    const params = {
      key: KEY,
      easebuzz_id: args.easebuzz_id,
      hash: paymentHash([KEY, args.easebuzz_id])
    };
    if (args.merchant_refund_id) params.merchant_refund_id = args.merchant_refund_id;
    const dash = process.env.EASEBUZZ_DASHBOARD_BASE_URL || 'https://dashboard.easebuzz.in';
    return postForm(`${dash}/transaction/v2/refund_status`, params);
  },

  async easebuzz_hash(args) {
    if (typeof args.raw === 'string') return { hash: sha512(args.raw) };
    if (!Array.isArray(args.parts)) throw new Error('Provide parts[] or raw');
    return { hash: paymentHash(args.parts) };
  },

  async easebuzz_request(args) {
    const method = (args.method || 'POST').toUpperCase();
    const params = { ...(args.params || {}) };
    if (args.add_key) params.key = KEY;
    if (Array.isArray(args.hash_parts)) {
      params.hash = paymentHash(args.hash_parts);
    } else if (Array.isArray(args.hash_fields)) {
      requireCreds();
      params.hash = paymentHash([KEY, ...args.hash_fields.map((f) => params[f] ?? '')]);
    }
    const url = `${BASE_URL}${args.path}`;
    if (method === 'GET') {
      const qs = new URLSearchParams(params).toString();
      const res = await fetch(qs ? `${url}?${qs}` : url, { headers: { accept: 'application/json' } });
      return { http_status: res.status, response: await parseResponse(res) };
    }
    return postForm(url, params);
  },

  async easebuzz_wire_request(args) {
    requireCreds();
    const method = (args.method || 'GET').toUpperCase();
    const qs = new URLSearchParams(args.query || {}).toString();
    const url = `${(process.env.EASEBUZZ_WIRE_BASE_URL || 'https://wire.easebuzz.in').replace(/\/+$/, '')}${args.path}${qs ? `?${qs}` : ''}`;
    const headers = {
      accept: 'application/json',
      authorization: sha512(`${KEY}|${SALT}`)
    };
    if (WIRE_KEY) headers['wire-api-key'] = WIRE_KEY;
    const init = { method, headers };
    if (args.body && method !== 'GET') {
      headers['content-type'] = 'application/json';
      init.body = JSON.stringify(args.body);
    }
    const res = await fetch(url, init);
    return { http_status: res.status, response: await parseResponse(res) };
  }
};

const send = (message) => process.stdout.write(JSON.stringify(message) + '\n');

const handle = async (line) => {
  let msg;
  try {
    msg = JSON.parse(line);
  } catch {
    return;
  }
  if (!msg || typeof msg !== 'object' || msg.id == null) return;

  if (msg.method === 'initialize') {
    send({
      jsonrpc: '2.0',
      id: msg.id,
      result: {
        protocolVersion: msg.params?.protocolVersion || '2024-11-05',
        capabilities: { tools: {} },
        serverInfo: { name: `easebuzz-${ENV}`, version: '1.0.0' }
      }
    });
    return;
  }

  if (msg.method === 'ping') {
    send({ jsonrpc: '2.0', id: msg.id, result: {} });
    return;
  }

  if (msg.method === 'tools/list') {
    send({ jsonrpc: '2.0', id: msg.id, result: { tools: TOOLS } });
    return;
  }

  if (msg.method === 'tools/call') {
    const name = msg.params?.name;
    const fn = handlers[name];
    try {
      if (!fn) throw new Error(`Unknown tool: ${name}`);
      const result = await fn(msg.params?.arguments || {});
      send({ jsonrpc: '2.0', id: msg.id, result: json(result) });
    } catch (error) {
      send({ jsonrpc: '2.0', id: msg.id, result: fail(error.message) });
    }
    return;
  }

  send({ jsonrpc: '2.0', id: msg.id, error: { code: -32601, message: `Method not found: ${msg.method}` } });
};

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on('line', (line) => {
  if (line.trim()) handle(line);
});
