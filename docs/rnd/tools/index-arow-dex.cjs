// Index SDK dexdump output without executing the APK or exposing credential-like literals.
const fs = require('node:fs');
const readline = require('node:readline');
const crypto = require('node:crypto');

const [input, apkPath, output] = process.argv.slice(2);
if (!input || !apkPath || !output) throw new Error('Usage: node index-arow-dex.cjs <disassembly> <apk> <output.json>');
const appPrefix = 'Lcom/arowapp/arowapp/';
const topics = {
  offline: /offline|last.?verified|subscription|last.?sync|last.?online/i,
  kitchen: /assignedStation|station.?wise|kds|kot|kitchen|printer|usb/i,
  dining: /tableId|tableName|floorId|floorName|dine.?in|occupied|seating/i,
  menu: /modifier|add.?on|variant|price.?tier|menu.?upload|extract.?menu/i,
  owner: /coupon|loyalty|expense|wallet|businessDay|dayStart|cutoff|outlet|khata|credit/i,
  payment: /split.?payment|refund|razorpay|paymentStatus|paymentMode|paymentMethod/i
};
const sensitive = /secret|password|credential|api.?key|access.?token|refresh.?token|bearer|private.?key|AIza|sk_live|rzp_live/i;
const classes = [];
const methods = [];
let currentClass = null;
let currentMethod = null;
let currentSection = '';
let field = null;
let lineNumber = 0;
let allMethodCount = 0;
let allClassCount = 0;

function safeString(value) {
  if (sensitive.test(value)) return '[credential-like literal omitted]';
  return value.replace(/https?:\/\/[^\s"<>]+/g, url => {
    try { return new URL(url).origin + '/[path omitted]'; } catch { return '[URL omitted]'; }
  });
}
function finishField() {
  if (field && currentClass && currentSection.includes('fields')) currentClass.fields.push(field);
  field = null;
}
function finishMethod() {
  if (!currentMethod) return;
  allMethodCount++;
  const isApp = currentMethod.class.startsWith(appPrefix);
  const relevant = isApp || currentMethod.calls.some(call => call.includes(appPrefix));
  const haystack = currentMethod.strings.join('\n') + '\n' + currentMethod.signature;
  const tags = Object.entries(topics).filter(([, pattern]) => pattern.test(haystack)).map(([name]) => name);
  if (isApp || (relevant && tags.length) || /14-day offline limit|Cloud function failed, falling back to offline Firestore/.test(haystack)) {
    methods.push({ ...currentMethod, topics: tags });
  }
  currentMethod = null;
}
function finishClass() {
  finishField();
  finishMethod();
  if (currentClass && currentClass.descriptor.startsWith(appPrefix)) classes.push(currentClass);
  currentClass = null;
}

(async () => {
  const reader = readline.createInterface({ input: fs.createReadStream(input), crlfDelay: Infinity });
  for await (const line of reader) {
    lineNumber++;
    let match;
    if ((match = line.match(/^  Class descriptor\s+: '([^']+)'/))) {
      finishClass();
      allClassCount++;
      currentClass = { descriptor: match[1], line: lineNumber, superclass: null, fields: [] };
      currentSection = '';
    } else if ((match = line.match(/^  Superclass\s+: '([^']+)'/))) {
      if (currentClass) currentClass.superclass = match[1];
    } else if ((match = line.match(/^  (Static fields|Instance fields|Direct methods|Virtual methods)\s+-/))) {
      finishField();
      currentSection = match[1];
    } else if (currentSection.includes('fields') && /^    #\d+/.test(line)) {
      finishField();
      field = { name: '', type: '' };
    } else if (currentSection.includes('fields') && (match = line.match(/^      (name|type)\s+: '([^']+)'/))) {
      if (field) field[match[1]] = match[2];
    }
    if ((match = line.match(/^[0-9a-f]+:\s+\|\[([0-9a-f]+)\] (.+)$/))) {
      finishMethod();
      currentMethod = {
        class: currentClass?.descriptor || '', signature: match[2], codeOffset: match[1],
        line: lineNumber, strings: [], calls: [], branchCount: 0, instructionCount: 0
      };
    } else if (currentMethod) {
      if (/\|[0-9a-f]+: /.test(line)) currentMethod.instructionCount++;
      if (/\|[0-9a-f]+: (if-|goto|packed-switch|sparse-switch)/.test(line)) currentMethod.branchCount++;
      if ((match = line.match(/const-string(?:\/jumbo)? v\d+, "(.*)" \/\/ string@/))) {
        const value = safeString(match[1]);
        if (!currentMethod.strings.includes(value)) currentMethod.strings.push(value);
      }
      if ((match = line.match(/invoke-[^ ]+ \{[^}]*\}, (.+) \/\/ method@/))) {
        if (!currentMethod.calls.includes(match[1])) currentMethod.calls.push(match[1]);
      }
    }
  }
  finishClass();
  const result = {
    scope: 'Static SDK dexdump method/field index, not runtime verification; reachability is not established by call-reference presence alone.',
    artifactSha256: crypto.createHash('sha256').update(fs.readFileSync(apkPath)).digest('hex'),
    disassemblyLineCount: lineNumber, allClassCount, allMethodCount,
    appClassCount: classes.length, indexedMethodCount: methods.length,
    note: 'Line numbers refer to the local raw dexdump output, not original source. Sensitive-looking literals and URL paths are omitted. Selected code offsets allow reproduction from the same DEX files.',
    classes, methods
  };
  fs.writeFileSync(output, JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ output, allClassCount, allMethodCount, appClassCount: classes.length, indexedMethodCount: methods.length }));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
