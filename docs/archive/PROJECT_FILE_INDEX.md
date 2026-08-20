# KhanaBook Project File Index

This index documents the current organized top-level layout.

For a deeper file inventory, see `docs/DEEP_FILE_LIST.md`.

## Source Modules

- `Android/` - Android POS app.
- `server/` - Spring Boot backend.
- `web-admin/` - Angular web admin.
- `website/` - Website/frontend assets.
- `kbook/` - Project-specific app/support files.

## Documentation

- `docs/api/api-docs.json` - Generated API schema/export.
- `docs/easebuzz/` - Easebuzz API references, support emails, live test plan, business model, Postman collection.
- `docs/easebuzz-review/` - Internal Easebuzz review package, Mermaid diagrams, and external-safe share pack.
- `docs/mcp/` - MCP installation/reference notes.
- `docs/product/` - Product, design, feature suggestion, and prompt documents.

## Logs And Generated Local Output

- `artifacts/android/` - Loose APK artifacts that are not source code.
- `logs/` - Server and web-admin local run logs moved out of the repository root.
- `graphify-out/` - Graphify analysis output.
- `scratch/` - Temporary working artifacts.

## Deep Folder Organization

The nested source folders are intentionally kept in their framework-standard locations.

### Android

- `Android/app/src/main/java/` - Kotlin source.
- `Android/app/src/main/res/` - Android resources.
- `Android/app/src/test/` - JVM tests.
- `Android/app/src/androidTest/` - Instrumentation tests.
- `Android/app/schemas/` - Room schema exports.
- `Android/gradle/` - Gradle wrapper files.
- `Android/apk-output/` - Android APK output folder.
- `Android/.gradle/`, `Android/.kotlin/`, `Android/app/build/`, `Android/build/` - Generated Gradle/Kotlin/build cache folders; left in place because tools own these paths.

### Server

- `server/src/main/java/` - Spring Boot source.
- `server/src/main/resources/` - Spring configuration and Flyway migrations.
- `server/src/test/` - Backend tests.
- `server/scripts/` - Server verification, sandbox, seed, and debug scripts.
- `server/target/` - Maven build output; left in place because Maven owns this path.

### Web Admin

- `web-admin/src/` - Angular source.
- `web-admin/public/` - Static public assets.
- `web-admin/dist/` - Angular build output; left in place because Angular owns this path.
- `web-admin/node_modules/` - npm dependencies; left in place because npm owns this path.

### Support And Generated Analysis

- `mcp/replit/` - MCP/Replit helper payloads.
- `ops/` - Production/service/backup operations files.
- `scratch/shots/` - Manual UI screenshots.
- `graphify-out/cache/` - Graphify cache files; left in place because Graphify owns this path.

## Config And Operations

- `.env.example` - Environment template.
- `.env` and `.env.v2` - Local environment files.
- `docker-compose.yml` and `docker-compose.production.yml` - Docker orchestration.
- `apache-kbook.conf` - Apache deployment config.
- `ops/` - Operations material.
- `mcp/` - MCP tooling/configuration.
- `maven/` - Local Maven distribution.

## Scripts

- `deploy.sh`
- `deploy-production.sh`
- `start-server-background.sh`
- `sync.sh`
- `create_test_user.ps1`

## Root Files Kept In Place

- `README.md`
- `AGENTS.md`
- `.gitignore`
- `opencode.json`
- `skills-lock.json`

## Notes

- App source code was not moved.
- Build and deployment config files were kept at the root.
- Existing unrelated deleted files shown by Git were not restored or modified.
