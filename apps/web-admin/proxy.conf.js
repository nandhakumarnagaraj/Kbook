/**
 * Angular dev-server proxy config (dev only — prod is same-origin).
 *
 * Why not proxy.conf.json: the Spring backend has a CORS allowlist that only
 * accepts the production origin, and it rejects forwarded browser Origin
 * headers with 403 "Invalid CORS request". Stripping Origin/Referer here makes
 * Spring see a plain server-side request and skip the CORS check entirely.
 *
 * API note: the Angular 18 esbuild/Vite dev-server passes this config straight
 * to Vite's `server.proxy`, which supports `configure(proxy)` — the old
 * webpack-era `onProxyReq(...)` key is silently ignored.
 */
module.exports = {
  '/api': {
    target: 'https://kbook.iadv.cloud',
    secure: true,
    changeOrigin: true,
    configure: function (proxy) {
      proxy.on('proxyReq', function (proxyReq) {
        proxyReq.removeHeader('origin');
        proxyReq.removeHeader('referer');
      });
    },
  },
};
