# WebAdmin_Baseline_Commit — provenance record

Spec: `.kiro/specs/v2-feature-integration` — Phase 0, task 1.3  
Requirements: Baseline Precondition criteria 1–5

| Field | Value |
|---|---|
| Module path set | `web-admin/` |
| Recorded on | 2026-08-01 |
| Production docroot | `https://kbook.iadv.cloud/` (VPS path `/var/www/kbook.iadv.cloud/web`) |
| Resolution status | **ESTABLISHED — no repository commit is content-equivalent** |
| Provenance kind | **Artifact-derived** |
| Comparable representation | Commit `ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2` plus `web-admin-artifact-derived.patch` |

## Result

The production bundle was built from commit
`ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2` with one uncommitted source edit:

```diff
-const STORAGE_KEY = 'khanabook.webAdmin.session';
+const STORAGE_KEY = 'khanabook.webAdmin.v1.session';
```

The patch is materialised at
`docs/baseline-provenance/web-admin-artifact-derived.patch`. Applying it to the recorded
commit reconstructs the comparable `web-admin/` tree that generated the deployed bundle.

The session-key value `khanabook.webAdmin.v1.session` has never existed in repository
history. The only commits touching `token-storage.service.ts` both contain the unversioned
key. Consequently no commit SHA can truthfully be recorded as
`WebAdmin_Baseline_Commit`; the artifact-derived representation is required by baseline
criterion 3.

## Production bundle manifest

The production index referenced these initial assets:

| Asset | Role |
|---|---|
| `styles-GDAZRWDD.css` | global styles |
| `polyfills-FFHMD2TL.js` | polyfills |
| `main-PWLFC7TN.js` | application entry and routes |
| `chunk-7MINFAET.js` | authentication/session storage |
| `chunk-HY7LPQHD.js` | shared Angular code |
| `chunk-FHV3WKX7.js` | toast service |
| `chunk-PUQE3NAB.js` | Angular runtime/vendor code |

The entry bundle identifies the deployed lazy routes. Notable generated chunks include
`chunk-3MAIAJWX.js` (terminals), `chunk-PZHMV3KM.js` (menu),
`chunk-5NXAAV3C.js` (staff), `chunk-CYPW6TQ6.js` (login), and
`chunk-SJKOO2H3.js` (sidebar layout).

## Correlation with the deploy run

| Event | UTC timestamp |
|---|---|
| Commit `ad0d2623` | 2026-07-23 19:59:32 UTC (2026-07-24 01:29:32 +0530) |
| Live `index.html` and `main-PWLFC7TN.js` last-modified | 2026-07-23 20:02:20 UTC |

The production files were stamped 2 minutes 48 seconds after the commit. Their generated
content matches the `npm ci` plus production `ng build` output from that commit with the
single patch above. This correlates the docroot contents to the `deploy-web.sh` flow, whose
steps are `npm ci`, production build, docroot backup, and `rsync`.

## Reproduction and comparison

1. A clean worktree was created at `ad0d2623`.
2. `npm ci` installed the commit's lockfile-pinned dependency set.
3. An unmodified `npm run build` produced `main-ZZVKTDPY.js` and
   `chunk-6CKZVGY4.js`, proving the repository commit alone does not match production.
4. The live `chunk-7MINFAET.js` was compared with the clean build's authentication chunk.
   The session-storage key was the only source-level behaviour difference.
5. The one-line artifact patch was applied and the production build rerun. It produced the
   full live hashed manifest, including `main-PWLFC7TN.js` and `chunk-7MINFAET.js`.
6. Every generated browser file was fetched from production and compared:
   - 29 JS/CSS assets matched byte-for-byte by SHA-256.
   - `index.html`, `privacy-policy.html`, `privacy/index.html`, and `robots.txt` were
     identical with end-of-line differences ignored (Windows CRLF versus deployed Linux
     LF).
   - No generated file was missing.

## Residual provenance risk for task 1.4

The exact production build proves the bundled application behaviour and public static
assets. It cannot prove the contents of paths excluded from the production build, such as
unit-test sources, editor metadata, local caches, or an unrecorded deploy shell session.
Those paths must remain listed as unverifiable rather than being declared equivalent.

## Reconstructing the deployed representation

```powershell
git worktree add C:\tmp\kbook-web-baseline ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2
git -C C:\tmp\kbook-web-baseline apply `
  C:\Users\nandh\Desktop\Khanabook\KhanaBook\docs\baseline-provenance\web-admin-artifact-derived.patch
Set-Location C:\tmp\kbook-web-baseline\web-admin
npm ci
npm run build
```

The expected entry assets are `main-PWLFC7TN.js`, `chunk-7MINFAET.js`,
`styles-GDAZRWDD.css`, and `polyfills-FFHMD2TL.js`.

## Explicitly not claimed

- That `ad0d2623` alone is content-equivalent to production.
- That the dirty source tree used for deployment contained no changes outside
  `web-admin/`; this record is module-scoped.
- That non-bundled test or development-only paths can be recovered from the docroot.
