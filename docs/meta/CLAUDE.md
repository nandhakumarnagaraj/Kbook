# KhanaBook AI Guide

Use `AGENTS.md` as the source of truth for project conventions, build commands,
test commands, and gstack routing.

## gstack

This repo is configured for gstack-assisted workflows. The active install on this
machine is the OpenCode install at `C:\Users\nandh\.config\opencode\skills`.

Claude Code users should install gstack separately before using gstack slash
commands:

```bash
git clone --depth 1 https://github.com/garrytan/gstack.git ~/.claude/skills/gstack
cd ~/.claude/skills/gstack && ./setup --team
```

## Skill routing

When a request matches a gstack workflow, use the matching skill before acting.
In OpenCode, use the installed `gstack-*` skills under
`C:\Users\nandh\.config\opencode\skills`.

Key routing rules:
- Product ideas/brainstorming -> `gstack-office-hours`
- Strategy/scope -> `gstack-plan-ceo-review`
- Architecture -> `gstack-plan-eng-review`
- Design system/plan review -> `gstack-design-consultation` or `gstack-plan-design-review`
- Full review pipeline -> `gstack-autoplan`
- Bugs/errors -> `gstack-investigate`
- QA/testing site behavior -> `gstack-qa` or `gstack-qa-only`
- Code review/diff check -> `gstack-review`
- Visual polish -> `gstack-design-review`
- Security review -> `gstack-cso`
- Ship/deploy/PR -> `gstack-ship` or `gstack-land-and-deploy`
- Save progress -> `gstack-context-save`
- Resume context -> `gstack-context-restore`
- Author a backlog-ready spec/issue -> `gstack-spec`

## Deploy Configuration (configured by /setup-deploy)
- Platform: Custom VPS (Docker Compose over SSH)
- Production URL: https://kbook.iadv.cloud
- Deploy workflow: manual on VPS — `git pull` then `./deploy-production.sh`
- Deploy status command: `docker compose --env-file .env -f docker-compose.production.yml ps`
- Merge method: squash
- Project type: API (Spring Boot) + Android app + Angular web-admin
- Post-deploy health check: https://kbook.iadv.cloud/api/v1/actuator/health (expects `{"status":"UP"}`)

### Custom deploy hooks
- Pre-merge: `mvn -f server/pom.xml package -DskipTests` (verify server builds; Android/web-admin build in their own dirs)
- Deploy trigger: on VPS `/var/www/kbook.iadv.cloud`: `git pull && ./deploy-production.sh` (builds image, `up -d postgres server`, backend on `127.0.0.1:8081`, Apache proxies `/api/v1/`)
- Deploy status: `docker compose --env-file .env -f docker-compose.production.yml ps` and `... logs -n 100 server`
- Health check: `curl -fsS https://kbook.iadv.cloud/api/v1/actuator/health`
- DB safety: run `./ops/backup_postgres.sh` before any deploy that runs a Flyway migration; rollback with `./ops/restore_postgres.sh <backup.sql.gz>` (JAR rollback alone is NOT enough if schema changed)
- Note: deploys run ON the VPS, not from this dev machine — this machine can only verify via the public health URL
