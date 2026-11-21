# Skill Creator — Scaffolding New Skills

## Overview

A meta-skill that scaffolds new SKILL.md files for the KhanaBook project. Use this when you need to create a new reusable skill for the `.claude/skills/` directory.

---

## Skill Directory Structure

```
.claude/skills/{skill-name}/
  SKILL.md           ← required: the skill instructions
```

The directory name should be hyphenated lowercase (e.g., `brand-guidelines`, `frontend-design`).

---

## SKILL.md Template

Every SKILL.md should follow this structure:

```markdown
# {Skill Title} — KhanaBook

## Overview
{1-2 sentences describing what this skill does and when to use it.}

---

## Prerequisites
{What the developer/AI needs before using this skill — context, tools, files}

---

## Core Rules
{3-5 essential rules that the AI must follow when using this skill. Examples:
- Always use CSS variables over hardcoded values
- Prefer signals over BehaviorSubject
- All API calls must use takeUntilDestroyed()
}

---

## Step-by-Step Workflow

### Step 1: {Step Name}
{Instructions, code examples, edge cases}

### Step 2: {Step Name}
{Instructions, code examples, edge cases}

### Step 3: {Step Name}
{Instructions, code examples, edge cases}

---

## Code Patterns

### Pattern: {Pattern Name}
```typescript
// Complete code example
```

### Pattern: {Pattern Name}
```typescript
// Complete code example
```

---

## Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| {Common mistake} | {How to avoid it} |

---

## Checklist

- [ ] Rule 1
- [ ] Rule 2
- [ ] Rule 3
```

---

## Skills Available in This Project

| Skill | Purpose |
|-------|---------|
| `brand-guidelines` | Brand colors, typography, design tokens, logo usage |
| `frontend-design` | Angular 18 patterns, Material components, responsive rules |
| `theme-factory` | Dark/light theme CSS generation, Angular Material integration |
| `figma` | Figma-to-Angular design token extraction, component mapping |
| `canvas-design` | Chart.js charts, data visualization standards |
| `skill-creator` | Scaffolding new skills (this file) |

---

## When to Create a New Skill

Create a new skill when you find yourself repeating the same complex pattern across multiple files. Good candidates:

1. **API integration patterns** — e.g., how Easebuzz APIs are called with hash generation
2. **Database migration patterns** — e.g., how to add new columns with Liquibase
3. **Testing patterns** — e.g., how to set up Controller tests with MockMvc
4. **Deployment patterns** — e.g., how to deploy to production with zero downtime
5. **Code review criteria** — e.g., what to check for in a frontend PR review

---

## Example: Scaffolding a New Database Migration Skill

```
Prompt: "Create a new skill for Liquibase database migrations"

Result: .claude/skills/database-migrations/SKILL.md
```

The resulting skill would cover:

1. Liquibase changelog file naming convention
2. Adding new columns vs. new tables
3. Rolling back migrations
4. Testing migrations against sandbox database
5. Common migration pitfalls (NOT NULL on existing data, index performance)
