# KhanaBook

> Docs live in [`docs/`](docs/README.md) — single parent folder for all documentation.
> Full project README: [`docs/meta/README.md`](docs/meta/README.md) · Agent guide: [`docs/meta/AGENTS.md`](docs/meta/AGENTS.md)

KhanaBook is an offline-first restaurant billing and operations system for small food businesses (Android POS + Spring Boot backend + Angular web admin).

## Repository layout

| Path | What it is | Target Users & Surface Rules |
|---|---|---|
| `apps/android/` | Kotlin/Compose POS app (self-contained Gradle project) | **Counter Staff & Cashiers:** Fast offline-first billing, KOT generation, dynamic UPI/Cash payments, and thermal receipt printing. *Contains zero staff management or configuration screens to safeguard shared physical counter tablets.* |
| `apps/web-admin/` | Angular 18 standalone admin console | **Restaurant Owners Only:** Menu catalog pricing, staff account creation & permission delegation, raw material recipes/inventory, GST/FSSAI settings, and daily shift closings. *Staff credentials strictly rejected (`surface: 'web'`).* |
| `services/server/` | Spring Boot API and Flyway migrations | Central multi-tenant cloud service, edge synchronization engine (`/sync/push`, `/sync/pull`), Easebuzz dynamic payment webhooks, and audit logging. |
| `docs/` | All documentation — start at [`docs/README.md`](docs/README.md) | Architectural specifications, database schemas, and operational runbooks. |
| `ops/` | Deploy scripts, Docker Compose files, Apache/nginx configs, backups | Production infrastructure and container setup. |
| `specs/` · `mcp/` · `cdn/` | Local tooling and runtime uploads (git-ignored) | Tooling, MCP definitions, and local media artifacts. |

See [`docs/README.md`](docs/README.md) for the full documentation index.

## Quick links
- **Agent guide:** `docs/meta/AGENTS.md`
- **Android UI rules:** `docs/meta/ANDROID_UI_RULES.md`
- **Design system:** `docs/design/`
- **Planning:** `docs/planning/PLAN.md`

## Third-party credits

Some web-admin UI is adapted from **[Rare UI](https://rareui.com)**
([source](https://github.com/swamimalode07/rare-ui)), used under its
MIT + Commons Clause + Attribution licence:

| Adapted | Where |
|---|---|
| `animated-counter` — the KPI values roll to their new value | `apps/web-admin/src/app/shared/animated-counter.component.ts` |
| `notification-bell` — bell, unread badge and flyout | `apps/web-admin/src/app/shared/notification-bell.component.ts` |

Copyright (c) 2026 Swami Malode. Permission is hereby granted, free of charge, to any
person obtaining a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, and distribute the Software as part of
an application, website, or product, subject to the following conditions: the copyright
notice and this permission notice are kept in all copies or substantial portions of the
Software; any project shipping any part of the Software credits Rare UI with a visible
link to <https://rareui.com>; and the components themselves are not sold, sublicensed or
redistributed, whether alone, in a bundle, or as a ported version.

The licence text is reproduced from the upstream `LICENSE`; if the two ever disagree,
upstream governs.
