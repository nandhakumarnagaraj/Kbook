// Batch-index every competitor APK found in a folder.
// Usage:
//   node batch-index-competitor-apks.cjs
// Reads C:\Users\nandh\Desktop\competitor_apks for *.apk (non-recursive first pass,
// recursive fallback), decompiles each into a temp/sibling folder with apktool,
// runs index-competitor-apk.cjs, and writes one evidence JSON per APK into
// docs\rnd\<pkg>-\<version>-evidence-<date>.json (prefix derived from package).
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const APK_DIR = process.env.APK_DIR || 'C:\\Users\\nandh\\Desktop\\competitor_apks';
const TOOL = path.join(__dirname, 'index-competitor-apk.cjs');
const OUTDIR = path.join(__dirname, '..');
const APKTOOL = process.env.APKTOOL || 'C:\\Users\\nandh\\Desktop\\apktool_setup\\apktool.jar';
const TEMP = 'C:\\Users\\nandh\\AppData\\Local\\Temp\\opencode';
const date = new Date().toISOString().slice(0, 10);
if (!fs.existsSync(APK_DIR)) { console.error('APK_DIR missing:', APK_DIR); process.exit(1); }
if (!fs.existsSync(OUTDIR)) fs.mkdirSync(OUTDIR, { recursive: true });

function listApks(dir) {
  const found = [];
  try {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      if (e.isDirectory()) found.push(...listApks(path.join(dir, e.name)));
      else if (e.name.toLowerCase().endsWith('.apk')) found.push(path.join(dir, e.name));
    }
  } catch { /* ignore unreadable */ }
  return found;
}
function packageId(decompiledDir) {
  const manifest = path.join(decompiledDir, 'AndroidManifest.xml');
  if (!fs.existsSync(manifest)) return null;
  const text = fs.readFileSync(manifest, 'utf8');
  return text.match(/package="([^"]+)"/)?.[1] || null;
}

(async () => {
  const apks = listApks(APK_DIR)
    .filter((a) => !a.includes('_decompiled'))
    .sort();
  if (apks.length === 0) { console.log('No APKs found in', APK_DIR); process.exit(0); }
  for (const apk of apks) {
    console.log('==>', apk, `(${(fs.statSync(apk).size / 1024 / 1024).toFixed(1)} MB)`);
    const decompiled = path.join(TEMP, 'decompiled', path.basename(apk, '.apk'));
    if (!fs.existsSync(decompiled)) {
      console.log('   decompiling via apktool...');
      execFileSync('java', ['-jar', APKTOOL, 'd', '-f', apk, '-o', decompiled], { stdio: 'pipe', timeout: 900000 });
    }
    const pkg = packageId(decompiled) || 'unknown';
    const stamp = path.basename(apk, '.apk') + '-' + date;
    const out = path.join(OUTDIR, `${stamp}-evidence.json`);
    console.log('   package:', pkg);
    execFileSync('node', [TOOL, decompiled, out, '--apk', apk], { stdio: 'pipe', timeout: 900000 });
    console.log('   wrote:', out);
  }
})().catch((error) => { console.error(error.message); process.exitCode = 1; });