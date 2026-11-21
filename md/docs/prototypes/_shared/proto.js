/* ═══════════════════════════════════════════════════════════════════════════
   KhanaBook web-admin — prototype shell behaviour (shared by every page)

   One state machine for every surface. A page file supplies copy + a render()
   function; this file owns loading / error / stale / empty / success behaviour
   so no page can invent its own (which is how the live app drifted).

   THE CONTRACT — a page file looks like this:

     <link rel="stylesheet" href="_shared/proto.css">
     ...
     <div class="shell">
       <aside class="sidebar" data-proto-nav></aside>
       <main class="main" data-proto-main></main>
     </div>
     <script src="_shared/proto.js"></script>
     <script>
       Proto.boot({
         page: 'orders',                       // key into Proto.PAGES
         title: 'Orders', subtitle: '…', eyebrow: 'Live Operations', live: true,
         headerExtra: '<button class="header-pill" …>…</button>',   // optional
         skeleton: '<div class="skeleton sk-tile"></div>' + …,     // loading HTML
         render: function (ctx) { return '<section>…</section>'; }, // loaded/empty HTML
         actions: { acceptOrder: function (el, ctx) { … Proto.call(…) } }
       });
     </script>

   render(ctx) receives { state, data, busy(key), mode, fail } and may return an
   HTML string. Every `[data-action="name"]` click is routed to actions.name;
   `refresh` is handled here.
   ═══════════════════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  const PAGES = [
    { key: 'index',            file: 'index.html',                              label: 'Prototype hub',        section: '',                        role: '' },
    { key: 'dashboard',        file: 'web-admin-dashboard-states.html',          label: 'Dashboard',            section: 'Owner console',           role: 'OWNER' },
    { key: 'orders',           file: 'web-admin-orders-states.html',             label: 'Orders & Live Floor',  section: 'Owner console',           role: 'OWNER' },
    { key: 'reports',          file: 'web-admin-reports-states.html',            label: 'Reports',              section: 'Owner console',           role: 'OWNER' },
    { key: 'daily-closing',    file: 'web-admin-daily-closing-states.html',      label: 'Daily Closing',        section: 'Owner console',           role: 'OWNER' },
    { key: 'menu',             file: 'web-admin-menu-states.html',               label: 'Menu',                 section: 'Owner console',           role: 'OWNER' },
    { key: 'inventory',        file: 'web-admin-inventory-states.html',          label: 'Inventory',            section: 'Owner console',           role: 'OWNER' },
    { key: 'staff',            file: 'web-admin-staff-states.html',              label: 'Staff & Permissions',  section: 'Owner console',           role: 'OWNER' },
    { key: 'terminals',        file: 'web-admin-terminals-states.html',          label: 'Terminals',            section: 'Owner console',           role: 'OWNER' },
    { key: 'settings',         file: 'web-admin-settings-states.html',           label: 'Business Settings',    section: 'Owner console',           role: 'OWNER' },
    { key: 'platform',         file: 'web-admin-platform-dashboard-states.html', label: 'Platform Dashboard',   section: 'Platform (KBOOK_ADMIN)',  role: 'KBOOK_ADMIN' },
    { key: 'businesses',       file: 'web-admin-businesses-states.html',         label: 'Businesses',           section: 'Platform (KBOOK_ADMIN)',  role: 'KBOOK_ADMIN' },
    { key: 'feature-flags',    file: 'web-admin-feature-flags-states.html',      label: 'Feature Flags',        section: 'Platform (KBOOK_ADMIN)',  role: 'KBOOK_ADMIN' },
    { key: 'login',            file: 'web-admin-login-states.html',              label: 'Login & recovery',     section: 'Public & edge',           role: '' },
    { key: 'limited-access',   file: 'web-admin-limited-access-states.html',     label: 'Limited access',       section: 'Public & edge',           role: '' },
    { key: 'not-found',        file: 'web-admin-404-states.html',                label: '404 / Not found',      section: 'Public & edge',           role: '' }
  ];

  /* Shared failure vocabulary — one sentence per failure kind, everywhere. */
  const COPY = {
    offline: { title: "You're offline",       message: 'No internet connection. Check your network and try again.', kind: 'offline' },
    c400:    { title: "We couldn't do that",  message: 'The request was rejected. Refresh and try again.', kind: 'server' },
    c401:    { title: 'Your session ended',   message: 'Your session has expired. Please sign in again.', kind: 'server' },
    c403:    { title: 'Not your permission',  message: 'You do not have permission to view this. Ask the shop owner to grant it.', kind: 'denied' },
    c500:    { title: "We couldn't load it",  message: 'The server could not complete this request (500). Nothing was changed.', kind: 'server' },
    c0:      { title: 'No connection',        message: 'The server did not respond. Check your connection and try again.', kind: 'offline' }
  };

  const state = {
    page: '', mode: 'loaded', fail: 'ok', slow: false,
    loaded: false, error: '', errorTitle: '', errorKind: 'server',
    staleError: '', refreshing: false, lastLoadedAt: null,
    busyKeys: {}, data: null
  };

  let options = null;
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const now = () => new Date();
  const esc = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const money = (n) => '₹' + Number(n || 0).toLocaleString('en-IN');
  const fmtTime = (d) => (d ? new Date(d).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }) : '');
  const fmtDate = (d) => (d ? new Date(d).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }) : '—');

  /* ── nav ─────────────────────────────────────────────────────────────── */
  function mountNav(container) {
    const groups = [];
    PAGES.forEach((p) => {
      if (p.key === 'index') return;
      const last = groups[groups.length - 1];
      if (last && last.section === p.section) last.items.push(p);
      else groups.push({ section: p.section, items: [p] });
    });
    const icon = (key) => {
      const paths = {
        dashboard: '<rect x="3" y="3" width="7" height="9"/><rect x="14" y="3" width="7" height="5"/><rect x="14" y="12" width="7" height="9"/><rect x="3" y="16" width="7" height="5"/>',
        orders: '<path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/><rect x="9" y="3" width="6" height="4" rx="1"/>',
        reports: '<path d="M3 3v18h18"/><path d="M18 17V9M13 17V5M8 17v-3"/>',
        'daily-closing': '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/><path d="m9 16 2 2 4-4"/>',
        menu: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>',
        inventory: '<path d="M21 8V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-1"/><path d="M16 12h5v4h-5a2 2 0 0 1 0-4z"/>',
        staff: '<path d="M20 7h-9M14 17H5"/><circle cx="17" cy="17" r="3"/><circle cx="7" cy="7" r="3"/>',
        terminals: '<rect x="2" y="4" width="20" height="16" rx="2"/><path d="M6 20v2M18 20v2M7 9h10"/>',
        settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.64-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.64 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.64V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
        platform: '<path d="M3 21h18"/><path d="M5 21V7l7-4 7 4v14"/><path d="M9 21v-6h6v6"/>',
        businesses: '<path d="M3 21h18"/><path d="M9 21V8h6v13"/><path d="M3 21V11h6M15 21V8l6 3v10"/>',
        'feature-flags': '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><path d="M4 22v-7"/>',
        login: '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><path d="m10 17 5-5-5-5M15 12H3"/>',
        'limited-access': '<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
        'not-found': '<circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/>'
      };
      return '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (paths[key] || paths.dashboard) + '</svg>';
    };
    container.innerHTML =
      '<a class="brand" href="index.html">' +
        '<span class="brand__logo">KB</span>' +
        '<span><span class="brand__name">KhanaBook</span><span class="brand__sub">Web-admin prototype</span></span>' +
      '</a>' +
      groups.map((g) => (g.section ? '<div class="nav-section">' + esc(g.section) + '</div>' : '') +
        g.items.map((p) => '<a class="nav-item" href="' + p.file + '"' + (p.key === state.page ? ' aria-current="page"' : '') + '>' +
          icon(p.key) + esc(p.label) + '</a>').join('')).join('') +
      '<div class="sidebar__foot"><a class="proto-exit" href="index.html">← All prototypes &amp; states used</a></div>';
  }

  /* ── shared state renderers ──────────────────────────────────────────── */
  function skeletonBlock(html) {
    return '<div aria-busy="true" aria-live="polite"><span class="visually-hidden" role="status">Loading…</span>' + html + '</div>';
  }
  function errorPanel(opts) {
    const o = opts || {};
    const kind = o.kind || state.errorKind;
    const cls = kind === 'offline' ? ' state-panel--offline' : kind === 'denied' ? ' state-panel--denied' : '';
    const glyph = kind === 'offline' ? '⚠' : kind === 'denied' ? '⛔' : '!';
    const node = document.createElement('section');
    node.className = 'state-panel' + cls;
    node.setAttribute('role', 'alert');
    node.setAttribute('aria-live', 'assertive');
    node.setAttribute('aria-atomic', 'true');
    node.setAttribute('tabindex', '-1');
    node.innerHTML =
      '<div class="state-panel__icon" aria-hidden="true">' + glyph + '</div>' +
      '<div><h3>' + esc(o.title || state.errorTitle || "We couldn't load this page") + '</h3>' +
      '<p>' + esc(o.message || state.error) + '</p>' +
      '<span class="state-panel__meta">' + esc(o.meta || 'Nothing was changed. Your last known figures are safe.') + '</span></div>' +
      '<button class="btn-retry" type="button" data-action="refresh">Try again</button>';
    setTimeout(() => node.focus(), 0);
    return node;
  }
  function offlinePanel() { return errorPanel(Object.assign({}, COPY.offline)); }
  function staleBanner() {
    const node = document.createElement('div');
    node.className = 'stale-banner';
    node.setAttribute('role', 'alert');
    node.setAttribute('aria-live', 'assertive');
    node.innerHTML =
      '<span class="stale-banner__icon" aria-hidden="true">!</span>' +
      '<span><strong>Couldn\u2019t update.</strong> ' + esc(state.staleError) +
      '<span class="state-panel__meta" style="display:inline"> Showing figures from ' + esc(fmtTime(state.lastLoadedAt)) + '.</span></span>' +
      '<span class="stale-banner__spacer"></span>' +
      '<button class="ghost-btn" type="button" data-action="refresh">Retry</button>';
    return node;
  }
  function emptyBox(title, text, actionLabel, actionName) {
    return '<div class="empty-box">' +
      '<svg class="empty-glyph" width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><path d="M3 3v18h18"/><path d="M18 17V9M13 17V5M8 17v-3"/></svg>' +
      '<h4>' + esc(title) + '</h4><p>' + esc(text) + '</p>' +
      (actionLabel ? '<button class="ghost-btn" type="button" data-action="' + esc(actionName || 'refresh') + '">' + esc(actionLabel) + '</button>' : '') +
    '</div>';
  }

  /* ── toasts ──────────────────────────────────────────────────────────── */
  function toast(text, type, meta) {
    let root = $('.toast-root');
    if (!root) { root = document.createElement('div'); root.className = 'toast-root'; root.setAttribute('aria-live', 'polite'); document.body.appendChild(root); }
    const t = type || 'info';
    const node = document.createElement('div');
    node.className = 'toast toast--' + t;
    node.setAttribute('role', t === 'error' ? 'alert' : 'status');
    node.setAttribute('aria-live', t === 'error' ? 'assertive' : 'polite');
    node.setAttribute('aria-atomic', 'true');
    node.innerHTML = '<span class="toast__mark" aria-hidden="true">' + (t === 'error' ? '!' : t === 'success' ? '✓' : 'i') + '</span>' +
      '<span class="toast__msg">' + esc(text) + (meta ? '<span class="toast__meta">' + esc(meta) + '</span>' : '') + '</span>' +
      '<button class="toast__close" type="button" aria-label="Dismiss notification">×</button>';
    node.querySelector('.toast__close').addEventListener('click', () => node.remove());
    root.appendChild(node);
    // Errors stay 7s (long enough to read); successes 3.5s. The live app used 3.5s for both.
    setTimeout(() => node.remove(), t === 'error' ? 7000 : 3500);
  }

  /* ── failure model ───────────────────────────────────────────────────── */
  function failureFor(mode) {
    const f = mode || state.fail;
    if (f === 'ok') return null;
    if (f === 'offline') return COPY.offline;
    if (f === '400') return COPY.c400;
    if (f === '401') return COPY.c401;
    if (f === '403') return COPY.c403;
    if (f === '500') return COPY.c500;
    return COPY.c500;
  }

  function setBusy(key, on) {
    if (on) state.busyKeys[key] = true;
    else delete state.busyKeys[key];
    if (options && options.onBusy) options.onBusy(key, !!on);
  }
  const busy = (key) => !!state.busyKeys[key];
  const isBusy = () => Object.keys(state.busyKeys).length > 0;

  /* One async op. Clears its busy flag on EVERY path, and turns any failure into
     something the user can see and act on. */
  function call(label, opts) {
    const o = opts || {};
    return new Promise((resolve) => {
      const wait = state.slow ? 1800 : 420;
      setTimeout(() => {
        const failure = failureFor(o.failMode);
        if (failure && !o.silent) {
          state.errorKind = failure.kind;
          if (state.loaded && !o.forceBlocking) {
            state.staleError = failure.message;
            state.error = '';
          } else {
            state.error = failure.message;
            state.errorTitle = failure.title;
            state.mode = 'error';
          }
          toast(failure.message, 'error', o.meta || label);
          log('✗ ' + label + ' → ' + failure.kind);
        } else if (!failure) {
          state.error = '';
          state.staleError = '';
          if (o.onOk) o.onOk();
          log('✓ ' + label);
        }
        state.refreshing = false;
        // Notify every cleared key: assigning a fresh object used to bypass
        // setBusy(), so any page relying on onBusy() never heard the release and
        // left its buttons disabled.
        const cleared = Object.keys(state.busyKeys);
        state.busyKeys = {};
        if (options && options.onBusy) cleared.forEach(function (k) { options.onBusy(k, false); });
        render();
        resolve(!failure);
      }, wait);
    });
  }

  function log(line) {
    const box = $('.proto-log');
    if (!box) return;
    const div = document.createElement('div');
    div.textContent = line;
    box.prepend(div);
    while (box.children.length > 40) box.lastElementChild.remove();
  }

  /* ── load / refresh ──────────────────────────────────────────────────── */
  function load(opts) {
    const o = opts || {};
    const isRefresh = state.loaded && !o.forceCold;
    if (isRefresh) {
      state.refreshing = true;
      render();
    } else {
      state.mode = 'loading';
      state.error = '';
      render();
    }
    return call(isRefresh ? 'Refresh' : 'Load ' + state.page, {
      onOk: function () {
        const wasEmpty = state.mode === 'empty';
        state.loaded = true;
        state.mode = wasEmpty ? 'empty' : 'loaded';
        state.lastLoadedAt = now();
        state.refreshing = false;
        state.error = '';
        state.staleError = '';
      }
    });
  }

  /* ── render ──────────────────────────────────────────────────────────── */
  function render() {
    const main = $('[data-proto-main]');
    if (!main || !options) return;

    const refresh = $('#protoRefresh');
    if (refresh) {
      refresh.disabled = state.refreshing;
      const label = refresh.querySelector('span');
      if (label) label.textContent = state.refreshing ? 'Refreshing…' : 'Refresh';
      const icon = refresh.querySelector('svg');
      if (icon) icon.classList.toggle('spinning', state.refreshing);
    }
    const fresh = $('#protoFreshness');
    if (fresh) fresh.textContent = freshnessLabel();

    const banner = $('#protoStaleBanner');
    if (banner) {
      if (state.staleError && state.loaded && state.mode !== 'error' && !state.refreshing) {
        banner.hidden = false;
        banner.replaceChildren(staleBanner());
      } else {
        banner.hidden = true;
        banner.replaceChildren();
      }
    }

    const viewport = $('#protoViewport');
    if (!viewport) return;
    if (state.mode === 'loading' && !state.loaded) {
      viewport.replaceChildren(nodeFromHtml(skeletonBlock(options.skeleton || defaultSkeleton())));
    } else if (state.mode === 'error' && !state.loaded) {
      viewport.replaceChildren(errorPanel({}));
    } else {
      const html = options.render({
        state: state,
        data: state.data || options.data || {},
        busy: busy,
        mode: state.mode,
        fail: state.fail,
        money: money, fmtTime: fmtTime, fmtDate: fmtDate, esc: esc,
        emptyBox: emptyBox
      });
      viewport.replaceChildren(nodeFromHtml(typeof html === 'string' ? html : ''));
    }
    animateCounters(viewport);
  }
  function defaultSkeleton() {
    return '<div class="stack"><div class="skeleton sk-tile"></div></div>';
  }
  function nodeFromHtml(html) {
    const t = document.createElement('template');
    t.innerHTML = html.trim();
    const frag = document.createDocumentFragment();
    while (t.content.firstChild) frag.appendChild(t.content.firstChild);
    return frag;
  }
  function freshnessLabel() {
    if (state.mode === 'loading' && !state.loaded) return 'Loading live data…';
    if (!state.lastLoadedAt) return '';
    const mins = Math.round((now() - state.lastLoadedAt) / 60000);
    return mins < 1 ? 'Updated just now' : 'Updated ' + mins + ' min ago';
  }
  function animateCounters(root) {
    $$('[data-counter]', root).forEach((node) => {
      const target = Number(node.dataset.counter || 0);
      const asMoney = node.dataset.format === 'money';
      const start = performance.now();
      const dur = 900;
      const step = (t) => {
        const p = Math.min(1, (t - start) / dur);
        const eased = 1 - Math.pow(1 - p, 3);
        const val = Math.round(target * eased);
        node.textContent = asMoney ? money(val) : String(val);
        if (p < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    });
  }

  /* ── prototype chrome ────────────────────────────────────────────────── */
  const MODES = [['loading', 'Loading'], ['loaded', 'Loaded'], ['error', 'Error'], ['empty', 'Empty']];
  const FAILS = [['ok', '200 OK'], ['400', '400'], ['401', '401'], ['403', '403'], ['500', '500'], ['offline', 'Offline']];

  function chrome() {
    const panel = document.createElement('aside');
    panel.className = 'proto-panel';
    panel.id = 'protoPanel';
    panel.setAttribute('aria-label', 'Prototype state controls');
    panel.innerHTML =
      '<div class="proto-title"><span>Prototype controls</span><span class="proto-badge">Not product UI</span></div>' +
      '<p class="proto-note">Same four states every surface can be in, plus the failure kinds the API can return. Switching a state never loses the loaded data by accident \u2014 only <em>Loading</em> clears it.</p>' +
      '<div class="proto-group"><div class="proto-label">Screen state</div><div class="proto-btns" id="protoModes">' +
        MODES.map((m) => '<button class="proto-btn" type="button" data-mode="' + m[0] + '">' + m[1] + '</button>').join('') +
      '</div></div>' +
      '<div class="proto-group"><div class="proto-label">Next API call returns</div><div class="proto-btns" id="protoFails">' +
        FAILS.map((f) => '<button class="proto-btn" type="button" data-fail="' + f[0] + '">' + f[1] + '</button>').join('') +
      '</div></div>' +
      '<label class="proto-toggle"><input type="checkbox" id="protoSlow" /> Add 1.8s latency to every call</label>' +
      '<div class="proto-log" id="protoLog" aria-live="off"></div>';
    const openBtn = document.createElement('button');
    openBtn.className = 'proto-open';
    openBtn.id = 'protoOpen';
    openBtn.type = 'button';
    openBtn.textContent = '⚙ Prototype states';
    document.body.append(panel, openBtn);

    openBtn.addEventListener('click', () => { panel.hidden = false; openBtn.hidden = true; });
    panel.addEventListener('dblclick', (e) => { if (e.target === panel) { panel.hidden = true; openBtn.hidden = false; } });
    panel.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-mode],[data-fail]');
      if (!btn) return;
      if (btn.dataset.mode) setMode(btn.dataset.mode);
      if (btn.dataset.fail) { state.fail = btn.dataset.fail; syncChrome(); toast('Next call will return ' + btn.textContent.trim(), 'info'); }
    });
    $('#protoSlow', panel).addEventListener('change', (e) => {
      state.slow = e.target.checked;
      toast(e.target.checked ? 'Latency on: every call now takes 1.8s' : 'Latency off', 'info');
    });
    return { panel: panel, openBtn: openBtn };
  }

  function setMode(mode) {
    state.mode = mode;
    state.staleError = '';
    if (mode === 'loading') { state.loaded = false; state.error = ''; }
    else if (mode === 'error') {
      state.loaded = false;
      const f = failureFor(state.fail === 'ok' ? '500' : state.fail);
      state.error = f.message; state.errorTitle = f.title; state.errorKind = f.kind;
    } else {
      state.loaded = true; state.error = ''; state.errorKind = 'server';
      if (mode === 'loaded' && !state.lastLoadedAt) state.lastLoadedAt = now();
    }
    syncChrome();
    render();
  }
  function syncChrome() {
    $$('#protoModes .proto-btn').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.mode === state.mode)));
    $$('#protoFails .proto-btn').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.fail === state.fail)));
  }

  /* ── events ──────────────────────────────────────────────────────────── */
  function wire() {
    document.addEventListener('click', (e) => {
      const el = e.target.closest('[data-action]');
      if (!el) return;
      const name = el.dataset.action;
      if (name === 'refresh') { e.preventDefault(); load({}); return; }
      if (options.actions && options.actions[name]) { e.preventDefault(); options.actions[name](el, api); return; }
    });
    document.addEventListener('input', (e) => {
      const el = e.target.closest('[data-input]');
      if (!el) return;
      const name = el.dataset.input;
      if (options.inputs && options.inputs[name]) options.inputs[name](el.value, api);
    });
  }

  /* ── boot ────────────────────────────────────────────────────────────── */
  function boot(opts) {
    options = opts;
    state.page = opts.page;
    state.data = opts.data || {};
    state.mode = opts.mode || 'loaded';
    state.loaded = state.mode !== 'loading';

    const nav = $('[data-proto-nav]');
    if (nav) mountNav(nav);
    const main = $('[data-proto-main]');
    const page = PAGES.find((p) => p.key === opts.page) || { label: opts.title || '' };
    main.innerHTML =
      '<header class="page-header">' +
        '<div>' +
          (opts.eyebrow ? '<span class="eyebrow">' + (opts.live ? '<span class="live-dot" aria-hidden="true"></span>' : '') + esc(opts.eyebrow) + '</span>' : '') +
          '<h1 class="page-title" id="protoTitle">' + esc(opts.title || page.label) + '</h1>' +
          '<p class="page-sub">' + esc(opts.subtitle || '') + '</p>' +
        '</div>' +
        '<div class="header-actions">' +
          (opts.headerExtra || '') +
          '<button class="refresh-btn" type="button" id="protoRefresh" data-action="refresh">' +
            '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/></svg>' +
            '<span>Refresh</span>' +
          '</button>' +
          '<span class="freshness" id="protoFreshness" role="status"></span>' +
        '</div>' +
      '</header>' +
      '<div id="protoStaleBanner" hidden></div>' +
      '<div id="protoViewport"></div>';

    if (state.loaded && !state.lastLoadedAt) state.lastLoadedAt = now();
    chrome();
    wire();
    syncChrome();
    render();
    if (opts.onReady) opts.onReady(api);
  }

  const api = {
    PAGES: PAGES, COPY: COPY, state: state, boot: boot, render: render, load: load, call: call,
    toast: toast, busy: busy, isBusy: isBusy, setBusy: setBusy, log: log,
    money: money, fmtTime: fmtTime, fmtDate: fmtDate, esc: esc,
    errorPanel: errorPanel, offlinePanel: offlinePanel, staleBanner: staleBanner,
    emptyBox: emptyBox, skeletonBlock: skeletonBlock, animateCounters: animateCounters,
    setMode: setMode, failureFor: failureFor,
    /* helpers pages can drop into markup */
    retryButton: (label) => '<button class="btn-retry" type="button" data-action="refresh">' + esc(label || 'Try again') + '</button>',
    skeletonRows: (n, cls) => Array.from({ length: n || 5 }, () => '<div class="skeleton ' + (cls || 'sk-row') + '"></div>').join(''),
    modal: function (title, bodyHtml, footHtml, wide) {
      const overlay = document.createElement('div');
      overlay.className = 'modal-overlay';
      overlay.setAttribute('role', 'dialog');
      overlay.setAttribute('aria-modal', 'true');
      overlay.setAttribute('aria-label', title);
      overlay.innerHTML = '<div class="modal-box' + (wide ? ' modal-box--wide' : '') + '">' +
        '<div class="modal-head"><h3>' + esc(title) + '</h3><button class="modal-close" type="button" data-close aria-label="Close dialog">✕</button></div>' +
        '<div class="modal-body">' + bodyHtml + '</div>' +
        (footHtml ? '<div class="modal-foot">' + footHtml + '</div>' : '') + '</div>';
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay || e.target.closest('[data-close]')) overlay.remove();
      });
      document.body.appendChild(overlay);
      const close = overlay.querySelector('[data-close]');
      if (close) close.focus();
      return overlay;
    }
  };

  window.Proto = api;
})();
