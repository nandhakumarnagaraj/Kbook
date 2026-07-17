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
