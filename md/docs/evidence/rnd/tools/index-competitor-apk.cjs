// Index a decompiled competitor APK folder into a reproducible evidence JSON.
// Usage:
//   node index-competitor-apk.cjs <decompiledDir> <output.json> [--apk <original.apk>]
// The decompiled dir is the output of `apktool d <apk>`. No APK is executed and
// no backend is contacted. Sensitive-looking literals and URL paths are omitted.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const args = process.argv.slice(2);
const dir = args[0];
const output = args[1];
const apkFlag = args.find((a) => a.startsWith('--apk='));
const apkNext = args.indexOf('--apk');
const apkPath = apkFlag ? apkFlag.slice('--apk='.length) : (apkNext !== -1 ? args[apkNext + 1] : null);
if (!dir || !output) throw new Error('Usage: node index-competitor-apk.cjs <decompiledDir> <output.json> [--apk <original.apk>]');

const topics = {
  offline: /offline|last.?verified|subscription|sync|pending|outbox|retry|queue/i,
  printing: /printer|kot|kds|escpos|thermal|usb|bluetooth|ethernet|wifi|cutter|paper|logo/i,
  dining: /table|floor|area|dine.?in|occupied|session|transfer|reservation|seating/i,
  menu: /modifier|add.?on|variant|portion|extra|category|recipe|ocr|photo|voice|price.?tier/i,
  owner: /coupon|loyalty|expense|wallet|business.?day|rollover|outlet|branch|chain|khata|credit|staff|attendance|payroll|salary/i,
  payment: /split.?payment|refund|upi|razorpay|cashfree|payment.?mode|payment.?status|merchant|settlement|sub.?merchant/i,
  compliance: /gst|fssai|gstr|tax|invoice|receipt|round.?off/i,
  outreach: /whatsapp|email.*receipt|sms|share|customer.?qr|menu.?qr/i
};
const sensitive = /secret|password|credential|api.?key|access.?token|refresh.?token|bearer|private.?key|AIza|_live[A-Z]|_KEY=|client_token|GoogleServices/i;

function safeString(value) {
  if (sensitive.test(value)) return '[credential-like literal omitted]';
  return value.replace(/https?:\/\/[^\s"<>]+/g, (url) => {
    try { return new URL(url).origin + '/[path omitted]'; } catch { return '[URL omitted]'; }
  });
}
function decodeConstString(line) {
  const m = line.match(/^\s*const-string(?:\/jumbo)? v\d+, "((?:[^"\\]|\\.)*)"[\r\n]?$/);
  if (!m) return null;
  const raw = m[1];
  if (raw.length < 2) return null;
  if (/ \([A-Za-z0-9_.]+:\d+\)$/.test(raw)) return null; // smali debug locus
  if (raw.includes('$this$') || raw.includes('$lambda$') || raw.includes('$$r') || raw.includes('.<anonymous>')) return null;
  if (/^(kitchen|pos|customer|order|menu|restaurant)\.|^com\.|^org\.|^androidx\./i.test(raw)) return null;
  return safeString(raw);
}

(async () => {
  const apktoolYml = path.join(dir, 'apktool.yml');
  const manifestPath = path.join(dir, 'AndroidManifest.xml');
  const stringsPath = path.join(dir, 'res', 'values', 'strings.xml');
  const meta = { apktool: {}, appLabel: null, firebase: {} };
  if (fs.existsSync(apktoolYml)) {
    const text = fs.readFileSync(apktoolYml, 'utf8');
    const inVersionInfo = /\bversionInfo\s*:/.test(text);
    for (const line of text.split(/\r?\n/)) {
      const vc = line.match(/^\s*versionCode\s*:\s*'?([^'\s]+)/);
      const vn = line.match(/^\s*versionName\s*:\s*'?([^'\s]+)/);
      const min = line.match(/minSdkVersion\s*:\s*'?(\d+)/);
      const tgt = line.match(/targetSdkVersion\s*:\s*'?(\d+)/);
      if (vc && inVersionInfo) meta.apktool.versionCode = vc[1];
      if (vn && inVersionInfo) meta.apktool.versionName = vn[1];
      if (min) meta.apktool.minSdk = min[1];
      if (tgt) meta.apktool.targetSdk = tgt[1];
    }
  }
  if (fs.existsSync(stringsPath)) {
    const text = fs.readFileSync(stringsPath, 'utf8');
    const label = text.match(/<string name="app_name">([^<]*)<\/string>/);
    if (label) meta.appLabel = label[1].trim();
    const project = text.match(/<string name="project_id">([^<]+)<\/string>/);
    if (project) meta.firebase.projectId = project[1].trim();
    const bucket = text.match(/<string name="google_storage_bucket">([^<]+)<\/string>/);
    if (bucket) meta.firebase.storageBucket = bucket[1].trim();
    const sender = text.match(/<string name="gcm_defaultSenderId">([^<]+)<\/string>/);
    if (sender) meta.firebase.gcmSenderId = sender[1].trim();
  }
  const manifestText = fs.existsSync(manifestPath) ? fs.readFileSync(manifestPath, 'utf8') : '';
  const permissions = [];
  let m;
  const permLine = /<uses-permission[^>]*android:name="([^"]+)"/g;
  while ((m = permLine.exec(manifestText))) permissions.push(m[1]);
  const pkgMatch = manifestText.match(/package="([^"]+)"/);
  const pkg = pkgMatch ? pkgMatch[1] : null;
  const pkgPath = pkg ? pkg.replace(/\./g, '/') : null;
  const requiredSplit = manifestText.match(/android:requiredSplitTypes="([^"]+)"/);
  const launcher = manifestText.match(/<activity[^>]{0,600}?android:name="([^"]+)"[^>]{0,600}?>[\s\S]{0,600}?android.intent.action.MAIN[\s\S]{0,600}?android.intent.category.LAUNCHER/);
  const queries = [];
  const qLine = /<package android:name="([^"]+)"/g;
  while ((m = qLine.exec(manifestText))) queries.push(m[1]);

  const smaliDirs = fs.existsSync(dir) ? fs.readdirSync(dir).filter((d) => /^smali(_classes\d+)?$/.test(d)) : [];
  const sdkRoots = new Set();
  for (const smaliDir of smaliDirs) {
    const root = path.join(dir, smaliDir);
    for (const f of fs.existsSync(root) ? fs.readdirSync(root) : []) {
      const full = path.join(root, f);
      if (!fs.statSync(full).isDirectory() || f === pkgPath) continue;
      if (/^[a-z][a-z0-9]?$/.test(f)) continue; // obfuscated R8 helper roots
      if (!sdkRoots.has(`${smaliDir}/${f}`)) sdkRoots.add(`${smaliDir}/${f}`);
    }
  }

  // Locate the app code root: package dir may nest deeper (e.g. .../arowapp/arowapp).
  function locateAppRoot(pkgDir) {
    let cur = pkgDir;
    for (let depth = 0; depth < 5; depth++) {
      if (!fs.existsSync(cur)) return null;
      const entries = fs.readdirSync(cur, { withFileTypes: true });
      const dirs = entries.filter((e) => e.isDirectory());
      const smalis = entries.filter((e) => e.name.endsWith('.smali'));
      if (smalis.length > 0 || dirs.length !== 1) return cur;
      cur = path.join(cur, dirs[0].name);
    }
    return cur;
  }

  const screens = new Set();
  const models = new Set();
  const utilities = new Set();
  const topicHits = {};
  for (const topic of Object.keys(topics)) topicHits[topic] = { files: 0, samples: [] };
  let firstPartySmaliFiles = 0;
  let totalSmaliFiles = 0;

  for (const smaliDir of smaliDirs) {
    const root = path.join(dir, smaliDir);
    const pkgDir = pkgPath ? path.join(root, pkgPath) : null;
    const appRoot = locateAppRoot(pkgDir);
    if (!appRoot || !fs.existsSync(appRoot)) continue;
    (function walk(cur, relDir) {
      for (const entry of fs.readdirSync(cur, { withFileTypes: true })) {
        const full = path.join(cur, entry.name);
        if (entry.isDirectory()) { walk(full, path.join(relDir, entry.name)); continue; }
        if (!entry.name.endsWith('.smali')) continue;
        totalSmaliFiles++;
        const base = entry.name.slice(0, -'.smali'.length);
        if (!/[A-Z]/.test(base)) continue; // skip obfuscated helpers
        firstPartySmaliFiles++;
        const relBase = (relDir.replace(/\\/g, '/').split('/').pop() || '');
        const stem = /Kt$/.test(base) ? base.slice(0, -2) : base;
        if (/(Screen|Activity|Fragment|Dashboard|Home|Login|Menu|Settings)$/.test(stem)
            && !/^(ComposableSingletons|External|Kotlin|Dagger|Hilt_)/.test(base)) {
          screens.add(base.replace(/Kt$/, ''));
        } else if ((relBase === 'models' || relBase === 'entity') && !/\$/.test(base)) {
          models.add(base);
        }
        if (relBase === 'utils' && /(Utils|Helper|Manager|Worker|Scheduler|Config)$/.test(base)) utilities.add(base);
      }
    })(appRoot, '');
  }
  // Remove synthetic screens that popped out of the generic matcher.
  for (const s of [...screens]) if (/^(Fetch|Parse|Load|Sync|Online|Offline|Cloud|Fire|Query)/.test(s) && !/Screen$/.test(s)) screens.delete(s);

  // Second pass: content-based topic evidence across the app root(s).
  for (const smaliDir of smaliDirs) {
    const root = path.join(dir, smaliDir);
    const appRoot = locateAppRoot(pkgPath ? path.join(root, pkgPath) : null);
    if (!appRoot || !fs.existsSync(appRoot)) continue;
    (function walk(cur) {
      for (const entry of fs.readdirSync(cur, { withFileTypes: true })) {
        const full = path.join(cur, entry.name);
        if (entry.isDirectory()) { walk(full); continue; }
        if (!entry.name.endsWith('.smali')) continue;
        const base = entry.name.slice(0, -'.smali'.length);
        if (!/[A-Z]/.test(base)) continue;
        const text = fs.readFileSync(full, 'utf8');
        for (const topic of Object.keys(topics)) {
          if (!topics[topic].test(text)) continue;
          if (topicHits[topic].files >= 120) continue;
          topicHits[topic].files++;
          for (const line of text.split(/\r?\n/)) {
            if (topicHits[topic].samples.length >= 16) break;
            const value = decodeConstString(line);
            if (value && value.length <= 96 && !topicHits[topic].samples.includes(value)) topicHits[topic].samples.push(value);
          }
          if (topicHits[topic].samples.length >= 16) break;
        }
      }
    })(appRoot);
  }

  const result = {
    scope: 'Static decompiled evidence; no APK execution or backend requests. Bundled code is not proof a feature is enabled. Line references are to smali files under the app package root, not to original source.',
    artifactSha256: apkPath && fs.existsSync(apkPath)
      ? crypto.createHash('sha256').update(fs.readFileSync(apkPath)).digest('hex') : null,
    meta,
    manifest: { packageName: pkg, requiredSplitTypes: requiredSplit ? requiredSplit[1] : null, launcherActivity: launcher ? launcher[1] : null, permissions, siblingPackagesQueried: queries },
    inventory: { smaliDirs: smaliDirs.length, firstPartySmaliFiles, totalSmaliFiles },
    sdkRoots: [...sdkRoots].sort(),
    screens: [...screens].sort(),
    models: [...models].sort(),
    utilities: [...utilities].sort(),
    topics: Object.fromEntries(Object.entries(topicHits).map(([k, v]) => [k, v]))
  };
  fs.writeFileSync(output, JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({
    output, packageName: pkg, firstPartySmaliFiles, totalSmaliFiles,
    screens: screens.size, models: models.size, sdkRoots: sdkRoots.size,
    topics: Object.keys(topicHits).filter((k) => topicHits[k].files > 0)
  }));
})().catch((error) => { console.error(error.message); process.exitCode = 1; });