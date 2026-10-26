# MCP Servers — KhanaBook

This directory contains [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server configurations for AI-assisted development on KhanaBook.

## Available Servers

Every config below was started on 2026-10-26 and answered an MCP `initialize` handshake plus
`tools/list`. The tool count is what that server actually published, not a claim from its README.

| Config file | Server | Tools | Purpose |
|---|---|---|---|
| `firebase-mcp.json` | Firebase | 27 | Firestore, Auth, Hosting, Cloud Functions via `firebase-tools` experimental MCP |
| `postgres-mcp.json` | PostgreSQL | 1 | Direct SQL (`query`) against the local database |
| `android-adb-mcp.json` | Android ADB | 25 | Device/emulator control via ADB — list devices, boot emulators, install |
| `android-ui-mcp.json` | Android UI Assist | 2 | Screenshots and connected-device listing |
| `git-mcp.json` | Git | 28 | log, diff, blame, branch, commit, status |
| `sentry-mcp.json` | Sentry | 9 | Issue triage, stack traces, release health |
| `figma-mcp.json` | Figma | 5 | Design file access — nodes, comments, assets |
| `mobile-device-mcp.json` | Mobile Device | 49 | Touch/swipe simulation, UI analysis, test generation |
| `mobile-next-mcp.json` | Mobile Next | 34 | Cross-platform device automation (official `mobile-next`) |
| `notification-mcp.json` | Notification | 2 | Completion sound / desktop notice for long-running tasks |
| `design-resources-mcp.json` | Design Resources | 9 | Font search + CSS, OKLCH colour scales, WCAG contrast, token export, reference directory |

`replit/` is not a server config: `server.json` is a bearer-token placeholder with no
`mcpServers` block and `create-repl.json` is a single tool schema fragment. Eleven configs that
could not run — packages that do not exist, or servers whose required runtime is missing here —
were removed; see [Removed servers](#removed-2026-10-26).

---

## Firebase MCP

**File:** `firebase-mcp.json`

Provides AI agents with access to the Firebase project (`khanabook-lite`) for:
- Firestore document read/write
- Authentication user management
- Hosting deployment status
- Cloud Functions inspection

### Usage

```json
{
  "mcpServers": {
    "firebase": {
      "command": "npx",
      "args": ["-y", "firebase-tools@latest", "experimental:mcp"],
      "env": {
        "FIREBASE_PROJECT": "khanabook-lite"
      }
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Authenticated with `firebase login` (or set `FIREBASE_TOKEN` env var)

---

## PostgreSQL MCP

**File:** `postgres-mcp.json`

Provides AI agents with direct PostgreSQL access for:
- Schema introspection (tables, views, indexes, constraints)
- Running SELECT/INSERT/UPDATE/DELETE queries
- Migration verification

The server takes the connection string as a **positional argument**. It ignores
`POSTGRES_CONNECTION_STRING` entirely — with only the env var set, it exits at startup with
"Please provide a database URL as a command-line argument".

### Usage

```json
{
  "mcpServers": {
    "postgres": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-postgres",
        "postgresql://localhost:5432/khanabook"
      ]
    }
  }
}
```

> `@modelcontextprotocol/server-postgres` is deprecated upstream (npm prints a deprecation
> warning on every start). It still works; `@henkey/postgres-mcp-server` is the maintained
> alternative if this one ever breaks.

### Connection Details

The actual database credentials are defined in the project root `.env` file (see `.env.example` for the template). Key variables:

| Variable | Description |
|---|---|
| `POSTGRES_DB` | Database name (default: `kbook_saas`) |
| `POSTGRES_USER` | Database user |
| `POSTGRES_PASSWORD` | Database password |

Put the real connection string into the `args` array in `postgres-mcp.json`:

```
postgresql://<POSTGRES_USER>:<POSTGRES_PASSWORD>@localhost:5432/<POSTGRES_DB>
```

> ⚠️ **`mcp/` is tracked now.** That DSN lands in Git, so keep the committed copy passwordless
> and put the credentialed URL in your local copy only, or accept that the password is public.

---

## Replit MCP

**File:** `replit/server.json`

Not an MCP server config, and not loadable by any client. `server.json` holds a URL and a
`<YOUR_REPLIT_API_TOKEN>` placeholder with no `mcpServers` block, and `create-repl.json` is a
single tool-schema fragment. Kept as a record of the intended Replit integration only.

---

## Android ADB MCP

**File:** `android-adb-mcp.json`

Provides AI agents with Android Debug Bridge (ADB) access for:
- Installing/uninstalling APKs on connected devices or emulators
- Taking screenshots and screen recordings
- Pulling logs (`logcat`) for debugging
- Executing shell commands on device
- Listing connected devices and their properties

### Usage

```json
{
  "mcpServers": {
    "android-adb": {
      "command": "npx",
      "args": ["-y", "android-mcp-server"],
      "timeout": 15000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Android SDK Platform Tools installed (`adb` on PATH)
- At least one device/emulator connected (`adb devices` shows it)
- USB debugging enabled on physical devices

### Example Usage
- "Take a screenshot of the connected device"
- "Install the debug APK on the emulator"
- "Show logcat output filtered to KhanaBook errors"
- "List all connected Android devices"

---

## Git MCP

**File:** `git-mcp.json`

Provides AI agents with Git repository operations for:
- Viewing commit history and diffs
- Inspecting branches and tags
- Running `git blame` on files
- Checking repository status
- Comparing branches

### Usage

```json
{
  "mcpServers": {
    "git": {
      "command": "npx",
      "args": ["-y", "@cyanheads/git-mcp-server"]
    }
  }
}
```

Registers 28 tools (`git_status`, `git_log`, `git_diff`, `git_blame`, `git_branch`, `git_commit`,
…). Logs go to stderr, so stdout stays a clean JSON-RPC stream.

### Prerequisites
- Node.js 18+
- Git installed and on PATH
- Launch it with the repository as the working directory; the server operates on the directory
  it was started in, so there is no machine-specific path to configure

### Example Usage
- "Show the last 10 commits on the main branch"
- "What changed in the last commit?"
- "Who last modified the OrderRepository.kt file?"
- "Compare the develop branch with main"

---

## Sentry MCP

**File:** `sentry-mcp.json`

Provides AI agents with Sentry error monitoring access for:
- Browsing and triaging unresolved issues
- Reading stack traces and error details
- Checking release health and crash-free rates
- Searching for specific errors by message or fingerprint
- Viewing affected users and event frequency

### Usage

```json
{
  "mcpServers": {
    "sentry": {
      "command": "npx",
      "args": ["-y", "@sentry/mcp-server"],
      "env": {
        "SENTRY_ACCESS_TOKEN": "your-sentry-access-token",
        "SENTRY_ORG": "piquant-consultancy"
      }
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Sentry account with the `piquant-consultancy` organization
- Auth token generated at [sentry.io/settings/auth-tokens](https://sentry.io/settings/auth-tokens/) with scopes: `project:read`, `org:read`, `event:read`, `issue:read`
- The variable is `SENTRY_ACCESS_TOKEN` — not `SENTRY_AUTH_TOKEN`, which the server ignores
- Replace `your-sentry-access-token` with the actual token, or delete the line and let the
  server fall back to its device-code flow

### Example Usage
- "Show the top unresolved issues in the KhanaBook Android project"
- "What's the stack trace for issue KHANA-142?"
- "What's our crash-free rate for the latest release?"
- "Find all NullPointerException errors from the last 24 hours"

---

## Figma MCP

**File:** `figma-mcp.json`

Provides AI agents with Figma design file access for:
- Reading design components, frames, and layers
- Extracting design tokens (colors, typography, spacing)
- Exporting assets (icons, images) from Figma
- Inspecting component properties and variants
- Bridging design-to-code workflows

### Usage

```json
{
  "mcpServers": {
    "figma": {
      "command": "npx",
      "args": ["-y", "figma-mcp"],
      "env": {
        "FIGMA_ACCESS_TOKEN": "your-figma-token"
      }
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Figma account with access to the KhanaBook design files
- Personal access token generated at [figma.com/developers/api#access-tokens](https://www.figma.com/developers/api#access-tokens)
- Replace `your-figma-token` with the actual token

### Example Usage
- "List all components in the KhanaBook design system file"
- "What are the color tokens defined in the design?"
- "Export the app icon at 512x512"
- "Show the properties of the OrderCard component"

---

## Notification MCP

**File:** `notification-mcp.json`

Two tools — `play_notification` and `notify` — that play a completion sound or raise a desktop
notice. Useful as a "this long job is done" signal for builds and test runs; it does not send
messages to other people.

### Usage

```json
{
  "mcpServers": {
    "notification": {
      "command": "npx",
      "args": ["-y", "@pinkpixel/notification-mcp"]
    }
  }
}
```

### Prerequisites
- Node.js 18+
- No token or configuration — it starts and registers both tools immediately

### Example Usage
- "Notify me when the Gradle build finishes"
- "Send a notification that all tests passed"
- "Alert me if the backend server goes down"
- "Show a reminder to check the printer connection"

---

## Mobile Device MCP

**File:** `mobile-device-mcp.json`

Provides AI agents with **49 tools** for mobile UI testing and interaction:
- Simulating touch, swipe, pinch, and scroll gestures on connected devices
- Visual analysis — screenshot comparison, element detection, UI hierarchy inspection
- Automated test generation from screen recordings or descriptions
- Multi-device coordination for testing across form factors
- Accessibility validation (contrast ratios, touch target sizes)

### Usage

```json
{
  "mcpServers": {
    "mobile-device": {
      "command": "npx",
      "args": ["-y", "mobile-device-mcp"],
      "timeout": 30000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- ADB on PATH with at least one device/emulator connected
- App installed on the target device for interaction testing

### Example Usage for KhanaBook
- "Tap on the 'New Order' button and verify the menu screen opens"
- "Swipe left on an order item to reveal the delete action"
- "Compare the current login screen against the Figma design screenshot"
- "Generate a UI test for the complete checkout flow"
- "Check if all buttons meet the 48dp minimum touch target size"

### Special Notes
- Works with both physical devices and emulators
- Visual analysis tools can detect UI regressions by comparing screenshots across builds
- Supports gesture recording and playback for complex interactions

---

## Android UI Assist MCP

**File:** `android-ui-mcp.json`

Two tools over ADB: `take_android_screenshot` and `list_android_devices`. The smallest useful
slice of device visibility when you only need to see what a build looks like on screen — for
touch simulation and test generation use Mobile Device or Mobile Next instead.

### Prerequisites
- Node.js 18+
- `adb` on PATH with a device or emulator attached or started

---

## Mobile Next MCP

**File:** `mobile-next-mcp.json`

Cross-platform device automation — 34 tools covering device listing, app install and launch, and
UI interaction on Android and iOS. This is the official `mobile-next` server; it is the broadest
of the three mobile configs, so pick one rather than enabling all of them.

### Prerequisites
- Node.js 18+
- `adb` on PATH for Android targets; Xcode tooling for iOS

---

## Design Resources MCP

**File:** `design-resources-mcp.json`

Wraps the local `designrefs` server (`Desktop/designrefs-mcp`, source kept outside this repo).
It answers design questions with measured values instead of prose, and needs no API keys.

### Tools

| Tool | Use |
|---|---|
| `resources_list` | Reference directory by category, plus the 12-link shortlist and research order |
| `resources_search` | Find a reference by name, purpose or category, with access warnings |
| `font_search` | Search Google Fonts (1,959 families) and Fontshare (100) by name or category |
| `font_get` | One family in full: weights, italics, subsets, licence, CSS URL, self-host zip, WOFF2 |
| `font_css` | Paste-ready `@font-face` block and link tags for a family |
| `palette_generate` | 5-colour palette from Colormind, with up to 4 colours locked |
| `color_scale` | 50–900 OKLCH ramp, or steps solved to hit target contrast ratios |
| `color_contrast` | WCAG 2.2 AA/AAA pass-fail for a pair, plus the nearest passing shade |
| `tokens_export` | Name→colour map to `--kb-*` CSS variables, JSON with hex/rgb/oklch, contrast audit |

### Usage

```json
{
  "mcpServers": {
    "design-resources": {
      "command": "node",
      "args": ["C:/Users/nandh/Desktop/designrefs-mcp/index.js"],
      "timeout": 30000
    }
  }
}
```

### Prerequisites
- Node.js 18+ (uses the built-in `fetch`)
- `npm install` run once inside `Desktop/designrefs-mcp`
- No API token required — every source is keyless

### Example Usage for KhanaBook
- "Check `--kb-color-muted-foreground` on `--kb-color-surface` at 4.5:1"
- "Build a scale from the brand purple that hits 3, 4.5 and 7 on white"
- "Find a geometric sans with a 400/600 pair and give me the self-host zip"
- "Export these web-admin colours as `--kb-*` tokens and flag anything under 4.5:1"

### Special Notes
- Complements the Figma MCP: Figma gives file access, this gives measured font and colour values
- `tokens_export` already emits the `--kb-*` prefix used in `apps/web-admin/src/styles.css`
- The `args` path is absolute and machine-specific — adjust it on another machine
- 30s timeout: font lookups hit Google Fonts and Fontshare over the network (15s internal limit), so a short client timeout cuts them off
- Self-test without a client: `node Desktop/designrefs-mcp/index.js --selftest`

---

## Removed (2026-10-26)

These configs were deleted because they could not start. Each is restorable — the reason and the
condition are recorded so the intent is not lost with the file.

| Removed config | Why it could not run |
|---|---|
| `docker-mcp.json` | `@modelcontextprotocol/server-docker` does not exist, and the Docker CLI is not installed here — there is nothing for a Docker server to talk to |
| `play-store-mcp.json` | `play-store-mcp` 404s. The real candidate, `google-play-mcp`, is a Python wrapper that builds a venv; this machine has only the Microsoft Store `python` stub, so it dies with exit code 9009 |
| `android-full-mcp.json` | `@us-all/android-mcp-server` 404s and has no maintained equivalent; `android-adb` (25 tools) plus `mobile-device` (49 tools) already cover the device work |
| `android-security-mcp.json` | `android-security-analyzer-mcp` 404s. Android security review runs through the `security-audit` skill instead |
| `droid-agent-kit-mcp.json` | `droid-agent-kit` 404s |
| `maestro-mcp.json` | `@mobile-dev/maestro-mcp` 404s, npm `maestro-mcp` is a reserved-name placeholder at 0.0.0, and the Maestro CLI is not installed |
| `lovable-mcp.json` | `@lovablelabs/mcp` 404s. Lovable is a hosted product with no npm stdio server, so no local config could have been correct |
| `thermal-printer-mcp.json` | `shortorder-mcp` 404s. npm has thermal-printer *libraries* (`node-thermal-printer`, `capacitor-thermal-printer`) but no MCP server, so receipt printing needs a small custom server |
| `strix-mcp.json` | The `strix` binary is not on PATH, and Strix needs Docker plus Python — neither is available here |
| `vibe-mcp.json` | `npx github:anubhavg-icpl/vibe` is refused by npm ("Refusing to fetch"), so it never installs |
| `whatsapp-mcp.json` | The npm `whatsapp-mcp` package is a CLI configurator for other clients ("Configure WhatsApp MCP for Claude Desktop or Cursor"), not a server. The real WhatsApp MCP is Python/uv-based |

To restore one: install the runtime it needs (Docker Desktop, real Python 3.11+, the Maestro CLI),
confirm the package resolves with `npm view <pkg> version`, then add the config back and check it
with the handshake test described under [Testing](#testing).

---

## Adding a New MCP Server

1. Create a new `<name>-mcp.json` file in this directory following the standard schema:
   ```json
   {
     "mcpServers": {
       "<server-name>": {
         "command": "...",
         "args": ["..."],
         "env": { }
       }
     }
   }
   ```
2. Document it in this README.
3. Ensure no secrets are committed — use environment variable references or `.gitignore`.

---

## Testing

Run the handshake test after any config change. It spawns each server exactly as configured and
speaks MCP over stdio — `initialize`, then `tools/list` — so a dead package, a wrong flag or a
missing runtime shows up as a failure instead of a silently empty tool list:

```bash
node mcp/verify-mcp.mjs                 # every config
node mcp/verify-mcp.mjs git-mcp.json    # one config
```

Output is one line per config with the tool count that server actually published, and the exit
code is non-zero if anything fails. It stops after the handshake, so no credentials or devices
are needed — a server that requires a token still passes here and only fails on a real tool call.

`test_mcp.ps1` is the older PowerShell connectivity check. It is deliberately **not tracked**: it
hardcodes a personal `*.sisko.replit.dev` endpoint and calls `list_directory`, which is not an MCP
method (MCP uses `tools/call`), so it cannot reach any of the servers above. `verify-mcp.mjs`
replaces it.
