#!/usr/bin/env node
/**
 * verify-mcp.mjs — prove every config in this folder can actually start.
 *
 * A config being valid JSON proves nothing: the package it launches may not
 * exist, the flag may be wrong, or the runtime may be missing. This script
 * spawns each server exactly as configured and speaks MCP over stdio:
 *
 *   1. initialize  — does the process come up and answer the handshake?
 *   2. tools/list  — does it publish a tool surface?
 *
 * It does not call tools, so no credentials or devices are needed. Servers that
 * need a token still pass here and fail only when a tool is invoked.
 *
 *   node verify-mcp.mjs            check every mcp/*.json
 *   node verify-mcp.mjs git-mcp.json   check one config
 *
 * Exit code 0 when all pass, 1 when any fail. Run it after editing a config.
 */
import fs from 'node:fs';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const DIR = path.dirname(fileURLToPath(import.meta.url));
const HANDSHAKE_TIMEOUT_MS = 240_000;
const TOOLS_TIMEOUT_MS = 60_000;

function check(file) {
  return new Promise((resolve) => {
    const cfgPath = path.join(DIR, file);
    let spec;
    try {
      const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
      const key = Object.keys(cfg.mcpServers ?? {})[0];
      spec = key ? cfg.mcpServers[key] : null;
      if (!spec) return resolve({ file, status: 'invalid', detail: 'no mcpServers entry' });
    } catch (e) {
      return resolve({ file, status: 'invalid', detail: e.message });
    }

    const started = Date.now();
    let child;
    try {
      // Windows needs a shell for npx.cmd; args here are flag-like, never paths with spaces.
      child = spawn(spec.command, spec.args ?? [], {
        env: { ...process.env, ...(spec.env ?? {}) },
        cwd: path.resolve(DIR, '..'), // repo root, so a workspace-scoped server sees the project
        shell: process.platform === 'win32',
      });
    } catch (e) {
      return resolve({ file, status: 'spawn-failed', detail: e.message });
    }

    const replies = new Map();
    let out = '';
    let err = '';
    let noise = 0;
    let settled = false;
    const settle = (result) => {
      if (settled) return;
      settled = true;
      clearTimeout(deadline);
      try {
        if (process.platform === 'win32' && child.pid) {
          spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
        } else {
          child.kill();
        }
      } catch {
        /* already gone */
      }
      resolve({
        file,
        seconds: ((Date.now() - started) / 1000).toFixed(1),
        noise,
        stderr: err
          .split(/\r?\n/)
          .filter((line) => line.trim() && !/^npm (notice|warn)/.test(line))
          .slice(-2)
          .join(' | ')
          .slice(0, 220),
        ...result,
      });
    };
    const deadline = setTimeout(
      () => settle({ status: 'timeout', detail: 'no handshake within 240s' }),
      HANDSHAKE_TIMEOUT_MS,
    );

    child.stdout.on('data', (chunk) => {
      out += chunk.toString();
      let nl;
      while ((nl = out.indexOf('\n')) >= 0) {
        const line = out.slice(0, nl).trim();
        out = out.slice(nl + 1);
        if (!line) continue;
        try {
          const msg = JSON.parse(line);
          if (msg.id !== undefined) replies.set(msg.id, msg);
        } catch {
          noise += 1; // anything non-JSON on stdout breaks framing for strict clients
        }
      }
    });
    child.stderr.on('data', (chunk) => { err += chunk.toString(); });
    child.on('error', (e) => settle({ status: 'spawn-failed', detail: e.message }));
    child.on('exit', (code) => {
      if (!settled && code !== 0) settle({ status: 'exited', detail: `process exited with code ${code}` });
    });

    const send = (msg) => { try { child.stdin.write(`${JSON.stringify(msg)}\n`); } catch { /* closing */ } };
    const waitFor = async (id, ms) => {
      const until = Date.now() + ms;
      while (Date.now() < until) {
        if (replies.has(id)) return replies.get(id);
        if (settled) return null;
        await new Promise((r) => setTimeout(r, 100));
      }
      return null;
    };

    (async () => {
      send({
        jsonrpc: '2.0',
        id: 1,
        method: 'initialize',
        params: {
          protocolVersion: '2024-11-05',
          capabilities: {},
          clientInfo: { name: 'khana-mcp-verify', version: '1.0.0' },
        },
      });
      const init = await waitFor(1, HANDSHAKE_TIMEOUT_MS);
      if (!init) return settle({ status: 'no-handshake', detail: 'never answered initialize' });
      if (init.error) return settle({ status: 'handshake-error', detail: JSON.stringify(init.error).slice(0, 200) });

      send({ jsonrpc: '2.0', method: 'notifications/initialized' });
      send({ jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} });
      const list = await waitFor(2, TOOLS_TIMEOUT_MS);
      if (!list) return settle({ status: 'no-tools', detail: 'handshake ok, tools/list unanswered' });
      if (list.error) return settle({ status: 'tools-error', detail: JSON.stringify(list.error).slice(0, 200) });

      const tools = list.result?.tools ?? [];
      return settle({
        status: 'ok',
        server: init.result?.serverInfo?.name,
        tools: tools.length,
        sample: tools.slice(0, 3).map((t) => t.name).join(', '),
      });
    })();
  });
}

const only = process.argv.slice(2);
const files = (only.length ? only : fs.readdirSync(DIR).filter((f) => f.endsWith('.json'))).sort();

console.log(`checking ${files.length} config(s) from ${DIR}\n`);
const results = [];
for (const file of files) {
  const r = await check(file);
  results.push(r);
  if (r.status === 'ok') {
    const tools = `${r.tools} tool${r.tools === 1 ? '' : 's'}`;
    console.log(`PASS  ${file.padEnd(30)} ${tools.padStart(9)}  ${r.seconds}s  [${r.sample}]${r.noise ? `  ! ${r.noise} non-JSON stdout line(s)` : ''}`);
  } else {
    console.log(`FAIL  ${file.padEnd(30)} ${r.status}: ${r.detail}${r.stderr ? ` :: ${r.stderr}` : ''}`);
  }
}

const pass = results.filter((r) => r.status === 'ok').length;
console.log(`\n${pass}/${results.length} configs start and list tools`);
const noisy = results.filter((r) => r.status === 'ok' && r.noise);
if (noisy.length) console.log(`${noisy.length} passed but wrote non-protocol output to stdout`);
process.exit(pass === results.length ? 0 : 1);
