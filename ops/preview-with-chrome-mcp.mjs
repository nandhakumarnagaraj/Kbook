import { spawn } from 'node:child_process';
import fs from 'node:fs';

const ARGS = [
  '-y',
  'chrome-devtools-mcp@latest',
  '--headless',
  '--chromeArg=--no-sandbox',
  '--chromeArg=--disable-setuid-sandbox',
  '--chromeArg=--disable-gpu',
  '--chromeArg=--disable-dev-shm-usage',
  '--chromeArg=--ignore-certificate-errors',
  '--acceptInsecureCerts',
  '--allowUnrestrictedPaths',
  '--no-usage-statistics',
  '--viewport=1280x800'
];

console.log('Spawning chrome-devtools-mcp...');
const child = spawn('npx', ARGS, {
  stdio: ['pipe', 'pipe', 'pipe']
});

let msgId = 1;
const pending = new Map();

child.stderr.on('data', (d) => {
  const line = d.toString().trim();
  if (line) console.log('[chrome-mcp:err]', line);
});

let buf = '';
child.stdout.on('data', (d) => {
  buf += d.toString();
  const lines = buf.split('\n');
  buf = lines.pop() || '';
  for (const line of lines) {
    if (!line.trim()) continue;
    try {
      const msg = JSON.parse(line);
      if (msg.id && pending.has(msg.id)) {
        const { resolve, reject } = pending.get(msg.id);
        pending.delete(msg.id);
        if (msg.error) reject(msg.error);
        else resolve(msg.result);
      }
    } catch (e) {}
  }
});

function call(method, params = {}) {
  const id = msgId++;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
  });
}

function notify(method, params = {}) {
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method, params }) + '\n');
}

async function run() {
  try {
    console.log('1. Initializing MCP handshake...');
    await call('initialize', {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: { name: 'kbook-reviewer', version: '1.0.0' }
    });
    notify('notifications/initialized');

    console.log('2. Querying pages...');
    const pagesRes = await call('tools/call', { name: 'list_pages', arguments: {} });
    console.log('Pages:', JSON.stringify(pagesRes));
    const pageId = 1;

    console.log(`3. Navigating page ${pageId} to https://kbook.iadv.cloud/stitch-preview.html ...`);
    const navRes = await call('tools/call', {
      name: 'navigate_page',
      arguments: { pageId, type: 'url', url: 'https://kbook.iadv.cloud/stitch-preview.html' }
    });
    console.log('Navigation result:', JSON.stringify(navRes));

    console.log('Waiting 3s for page rendering...');
    await new Promise((r) => setTimeout(r, 3000));

    console.log('4. Checking console messages...');
    const consoleRes = await call('tools/call', {
      name: 'list_console_messages',
      arguments: { pageId }
    });
    console.log('Console messages:', JSON.stringify(consoleRes, null, 2));

    console.log('5. Taking DOM snapshot...');
    const snapRes = await call('tools/call', {
      name: 'take_snapshot',
      arguments: { pageId, verbose: true }
    });
    fs.writeFileSync('/tmp/kbook-dom-snapshot.txt', JSON.stringify(snapRes, null, 2));
    console.log('DOM snapshot saved to /tmp/kbook-dom-snapshot.txt');

    console.log('6. Capturing screenshot...');
    const shotPath = '/root/.gemini/antigravity-cli/brain/c6a2fc0f-6de4-4a7f-a7eb-a18bccaab48f/stitch-suite-live-preview.png';
    const shotRes = await call('tools/call', {
      name: 'take_screenshot',
      arguments: { pageId, filePath: shotPath, format: 'png' }
    });
    console.log('Screenshot call result:', JSON.stringify(shotRes));
    if (fs.existsSync(shotPath)) {
      console.log('Screenshot successfully written:', shotPath, 'size:', fs.statSync(shotPath).size, 'bytes');
    }

    console.log('7. Listing network requests...');
    const netRes = await call('tools/call', {
      name: 'list_network_requests',
      arguments: { pageId }
    });
    console.log('Network requests:', JSON.stringify(netRes, null, 2).slice(0, 1500));

  } catch (err) {
    console.error('Error during browser preview:', err);
  } finally {
    child.kill();
    process.exit(0);
  }
}

run();
