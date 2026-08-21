# MCP Servers — KhanaBook

This directory contains [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server configurations for AI-assisted development on KhanaBook.

## Available Servers

| Config file | Server | Purpose |
|---|---|---|
| `firebase-mcp.json` | Firebase | Firebase project management, Firestore, Auth, Hosting, Cloud Functions via `firebase-tools` experimental MCP |
| `postgres-mcp.json` | PostgreSQL | Direct database access for schema inspection, queries, and migrations |
| `replit/server.json` | Replit | Remote Replit workspace creation and management |
| `play-store-mcp.json` | Google Play Store | Play Console management — releases, tracks, reviews, and app metadata |
| `android-adb-mcp.json` | Android ADB | Android device/emulator control via ADB for testing and debugging |
| `git-mcp.json` | Git | Git repository operations — log, diff, blame, branch management |
| `sentry-mcp.json` | Sentry | Error monitoring — issue triage, stack traces, release health |
| `docker-mcp.json` | Docker | Container management — build, run, inspect, logs |
| `figma-mcp.json` | Figma | Design file access — inspect components, export assets, read design tokens |
| `droid-agent-kit-mcp.json` | Droid Agent Kit | Android project automation — code generation, refactoring, Gradle tasks |
| `maestro-mcp.json` | Maestro | Mobile UI testing — write and run end-to-end test flows |
| `whatsapp-mcp.json` | WhatsApp | WhatsApp messaging — send order notifications and receipts to customers |
| `android-security-mcp.json` | Android Security | Static security analysis — vulnerability scanning for Android apps |
| `thermal-printer-mcp.json` | Thermal Printer | POS receipt printing — format and send bills to Bluetooth thermal printers |
| `notification-mcp.json` | Notification | Desktop/system notifications — alerts for build completion, errors, etc. |
| `mobile-device-mcp.json` | Mobile Device | UI testing, touch interaction simulation, visual analysis, test generation (49 tools) |
| `android-full-mcp.json` | Android Full | Startup profiling, memory leak detection, UI locator, crash investigation (75 tools) |
| `lovable-mcp.json` | Lovable | AI web app builder — rapid prototyping of admin dashboards and web interfaces |
| `vibe-mcp.json` | VIBE | Massive AI skills library — 853 modes, 5340 skills, 200 agents |
| `design-resources-mcp.json` | Design Resources | UI styles, colors, typography, UX guidelines for consistent design |

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

### Usage

```json
{
  "mcpServers": {
    "postgres": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres"],
      "env": {
        "POSTGRES_CONNECTION_STRING": "postgresql://localhost:5432/khanabook"
      }
    }
  }
}
```

### Connection Details

The actual database credentials are defined in the project root `.env` file (see `.env.example` for the template). Key variables:

| Variable | Description |
|---|---|
| `POSTGRES_DB` | Database name (default: `kbook_saas`) |
| `POSTGRES_USER` | Database user |
| `POSTGRES_PASSWORD` | Database password |

To use the real connection string, update `POSTGRES_CONNECTION_STRING` in `postgres-mcp.json` to:

```
postgresql://<POSTGRES_USER>:<POSTGRES_PASSWORD>@localhost:5432/<POSTGRES_DB>
```

> ⚠️ **Never commit real credentials.** Keep `postgres-mcp.json` in `.gitignore` if it contains secrets, or reference env vars from your shell environment.

---

## Replit MCP

**File:** `replit/server.json`

Remote MCP server for Replit workspace management. Requires a Replit API token configured in `server.json`.

---

## Play Store MCP

**File:** `play-store-mcp.json`

Provides AI agents with access to the Google Play Console for:
- Managing app releases and rollouts across tracks (internal, alpha, beta, production)
- Reading and responding to user reviews
- Inspecting app metadata (descriptions, screenshots, ratings)
- Checking release status and version codes

### Usage

```json
{
  "mcpServers": {
    "play-store": {
      "command": "npx",
      "args": ["-y", "play-store-mcp"],
      "env": {
        "GOOGLE_PLAY_SERVICE_ACCOUNT_KEY": "path/to/service-account.json",
        "GOOGLE_PLAY_PACKAGE_NAME": "com.piquantservices.khanabooklite"
      }
    }
  }
}
```

### Prerequisites
- Node.js 18+
- A Google Cloud service account with **Google Play Developer API** enabled
- Service account JSON key file (download from Google Cloud Console → IAM → Service Accounts)
- Service account must be granted access in Play Console → Settings → API access
- Update `GOOGLE_PLAY_SERVICE_ACCOUNT_KEY` to the actual path of your key file

### Example Usage
- "List the latest releases on the production track"
- "Show recent 1-star reviews for the app"
- "What version code is live on the beta track?"

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
      "args": ["-y", "@modelcontextprotocol/server-git", "--repository", "C:/Users/nandh/Desktop/Khanabook/KhanaBook"]
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Git installed and on PATH
- The repository path must be a valid Git repository

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
        "SENTRY_AUTH_TOKEN": "your-sentry-auth-token",
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
- Replace `your-sentry-auth-token` with the actual token

### Example Usage
- "Show the top unresolved issues in the KhanaBook Android project"
- "What's the stack trace for issue KHANA-142?"
- "What's our crash-free rate for the latest release?"
- "Find all NullPointerException errors from the last 24 hours"

---

## Docker MCP

**File:** `docker-mcp.json`

Provides AI agents with Docker container management for:
- Listing running containers and images
- Building images from Dockerfiles
- Starting, stopping, and removing containers
- Inspecting container logs and stats
- Managing Docker Compose services

### Usage

```json
{
  "mcpServers": {
    "docker": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-docker"]
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Docker Desktop installed and running (or Docker Engine on Linux)
- Docker daemon accessible (user in `docker` group or running as admin)

### Example Usage
- "List all running containers"
- "Show logs from the postgres container"
- "Build the backend Docker image"
- "Stop all KhanaBook containers"

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

## Droid Agent Kit MCP

**File:** `droid-agent-kit-mcp.json`

Provides AI agents with Android project automation capabilities for:
- Generating Kotlin/Java code from natural language descriptions
- Running Gradle tasks (build, test, lint) programmatically
- Refactoring Android code — rename, extract, inline
- Navigating the Android project structure
- Managing dependencies in `build.gradle.kts`

### Usage

```json
{
  "mcpServers": {
    "droid-agent-kit": {
      "command": "npx",
      "args": ["-y", "droid-agent-kit"],
      "env": {
        "ANDROID_PROJECT_PATH": "C:/Users/nandh/Desktop/Khanabook/KhanaBook/Android"
      },
      "timeout": 30000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Android project at the configured `ANDROID_PROJECT_PATH`
- Android SDK installed with `ANDROID_HOME` set
- Gradle wrapper (`gradlew`) present in the Android project

### Example Usage
- "Run a debug build of the Android project"
- "Generate a new ViewModel for the Orders screen"
- "Add the Hilt dependency to the app module"
- "List all Kotlin files in the data layer"

---

## Maestro MCP

**File:** `maestro-mcp.json`

Provides AI agents with mobile UI testing capabilities using Maestro for:
- Writing end-to-end UI test flows in YAML
- Running test suites on connected devices or emulators
- Capturing screenshots during test execution
- Validating UI states and navigation flows
- Generating test reports

### Usage

```json
{
  "mcpServers": {
    "maestro": {
      "command": "npx",
      "args": ["-y", "@mobile-dev/maestro-mcp"],
      "timeout": 60000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Maestro CLI installed (`curl -Ls "https://get.maestro.mobile.dev" | bash`)
- Android emulator running or physical device connected via ADB
- The app must be installed on the target device

### Example Usage
- "Write a Maestro flow to test the login screen"
- "Run all UI tests in the `.maestro/` directory"
- "Create a test that adds an item to the order and checks the total"
- "Take a screenshot after navigating to the menu screen"

---

## WhatsApp MCP

**File:** `whatsapp-mcp.json`

Provides AI agents with WhatsApp messaging capabilities for:
- Sending order confirmation messages to customers
- Sending digital receipts/bills via WhatsApp
- Broadcasting offers or daily specials to customer lists
- Reading incoming messages for order-related queries
- Managing WhatsApp Business templates

### Usage

```json
{
  "mcpServers": {
    "whatsapp": {
      "command": "npx",
      "args": ["-y", "whatsapp-mcp"],
      "timeout": 15000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- WhatsApp Business API access (or WhatsApp Web session for personal use)
- QR code scan required on first launch to link the session
- Phone must remain connected to the internet for WhatsApp Web bridge

### Example Usage
- "Send an order confirmation to +91-9876543210"
- "Broadcast today's lunch special to all registered customers"
- "Send the bill for order #1042 to the customer's WhatsApp"
- "Check if there are any new messages from customers"

---

## Android Security MCP

**File:** `android-security-mcp.json`

Provides AI agents with static security analysis for Android projects:
- Scanning for common vulnerabilities (insecure storage, hardcoded secrets, weak crypto)
- Checking manifest permissions for over-privileging
- Detecting insecure network configurations
- Auditing third-party dependencies for known CVEs
- Generating security reports with remediation suggestions

### Usage

```json
{
  "mcpServers": {
    "android-security": {
      "command": "npx",
      "args": ["-y", "android-security-analyzer-mcp"],
      "env": {
        "PROJECT_PATH": "C:/Users/nandh/Desktop/Khanabook/KhanaBook/Android"
      },
      "timeout": 30000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Android project at the configured `PROJECT_PATH`
- Compiled APK recommended (for deeper analysis), but source-only scanning also supported

### Example Usage
- "Scan the Android project for hardcoded API keys"
- "Check if the network security config allows cleartext traffic"
- "Audit our dependencies for known vulnerabilities"
- "Are there any insecure SharedPreferences usages in the codebase?"

---

## Thermal Printer MCP

**File:** `thermal-printer-mcp.json`

Provides AI agents with POS thermal printer integration for:
- Formatting and printing restaurant bills/receipts
- Printing kitchen order tickets (KOTs)
- Configuring paper width, font size, and alignment
- Managing Bluetooth printer connections
- Printing QR codes for UPI payment on receipts

### Usage

```json
{
  "mcpServers": {
    "thermal-printer": {
      "command": "npx",
      "args": ["-y", "shortorder-mcp"],
      "env": {
        "PRINTER_TYPE": "bluetooth"
      },
      "timeout": 10000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Bluetooth thermal printer paired with the system (or USB printer connected)
- Printer must support ESC/POS command set (most 58mm/80mm thermal printers do)
- `PRINTER_TYPE` can be set to `bluetooth`, `usb`, or `network`

### Example Usage
- "Print a test receipt on the connected Bluetooth printer"
- "Format and print bill for order #2057 with itemized totals"
- "Print a KOT ticket for the kitchen with order items"
- "Add a UPI QR code to the bottom of the receipt"

---

## Notification MCP

**File:** `notification-mcp.json`

Provides AI agents with desktop/system notification capabilities for:
- Sending build completion notifications
- Alerting on test failures or errors
- Notifying when long-running tasks finish
- Displaying reminders and status updates
- Custom notification sounds and urgency levels

### Usage

```json
{
  "mcpServers": {
    "notification": {
      "command": "npx",
      "args": ["-y", "@pinkpixel-dev/notification-mcp"],
      "timeout": 5000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Windows 10/11 (uses native Windows toast notifications)
- No additional configuration required — works out of the box

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

## Android Full MCP

**File:** `android-full-mcp.json`

Provides AI agents with **75 tools** for deep Android development and debugging:
- **Startup profiling** — measure cold/warm/hot start times, identify bottlenecks
- **Memory leak detection** — heap dumps, allocation tracking, leak suspect analysis
- **UI locator** — find elements by ID, text, content description, or XPath
- **Crash investigation** — ANR traces, crash dumps, tombstone analysis
- **Performance monitoring** — CPU, GPU, network, and battery profiling

### Usage

```json
{
  "mcpServers": {
    "android-full": {
      "command": "npx",
      "args": ["-y", "@us-all/android-mcp-server"],
      "env": {
        "ANDROID_SDK_ROOT": "C:/Users/nandh/AppData/Local/Android/Sdk"
      },
      "timeout": 30000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Android SDK installed at `C:/Users/nandh/AppData/Local/Android/Sdk`
- `ANDROID_SDK_ROOT` must be valid (platform-tools, build-tools present)
- Device or emulator running with the app installed

### Example Usage for KhanaBook
- "Profile the cold start time of KhanaBook and identify the slowest init steps"
- "Check for memory leaks after navigating between the Orders and Menu screens 10 times"
- "Find the RecyclerView element on the order list screen by its resource ID"
- "Investigate why the app crashed with a NullPointerException in BillingFragment"
- "Monitor CPU usage while processing a batch of 50 orders"

### Special Notes
- The `ANDROID_SDK_ROOT` is pre-configured for the local machine — update if SDK moves
- Startup profiling integrates with Android's `am start -W` and Perfetto traces
- Memory leak detection can generate LeakCanary-style reports
- Complements the basic `android-adb-mcp.json` with advanced profiling capabilities

---

## Lovable MCP

**File:** `lovable-mcp.json`

Provides AI agents with access to [Lovable.dev](https://lovable.dev) — an AI-powered web app builder for:
- Rapidly prototyping web admin dashboards and internal tools
- Generating React/Next.js UIs from natural language descriptions
- Creating customer-facing web portals (online ordering, menu display)
- Iterating on designs with AI-assisted visual editing
- Deploying prototypes to preview URLs instantly

### Usage

```json
{
  "mcpServers": {
    "lovable": {
      "command": "npx",
      "args": ["-y", "@lovablelabs/mcp"],
      "env": {
        "LOVABLE_TOKEN": "your-lovable-api-token"
      },
      "timeout": 20000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Lovable.dev account with API access
- Replace `your-lovable-api-token` with the actual token from [lovable.dev/settings/api](https://lovable.dev/settings/api)
- **1-year premium plan active** — includes unlimited generations and priority builds

### Example Usage for KhanaBook
- "Create a web admin dashboard showing daily revenue, order count, and top-selling items"
- "Build an online ordering page where customers can browse the menu and place orders"
- "Generate a kitchen display system (KDS) web interface showing active orders"
- "Create a customer-facing digital menu with categories and item images"
- "Prototype a staff management panel with shift scheduling"

### Special Notes
- **Premium plan available** — 1-year premium subscription active, no generation limits
- Ideal for quickly building the Angular web admin companion to the Android POS app
- Generated apps can be exported as code and integrated into the KhanaBook monorepo
- Keep `LOVABLE_TOKEN` secret — do not commit to version control

---

## VIBE MCP

**File:** `vibe-mcp.json`

Provides AI agents with access to the VIBE skills library — a massive collection of:
- **853 modes** — specialized behavior configurations for different tasks
- **5,340 skills** — reusable knowledge modules for coding, testing, design, ops
- **200 agents** — pre-configured AI agent personas with domain expertise
- **30 Android-specific modes** — Kotlin, Jetpack Compose, Room, Hilt, MVVM patterns

### Usage

```json
{
  "mcpServers": {
    "vibe": {
      "command": "npx",
      "args": ["-y", "github:anubhavg-icpl/vibe"]
    }
  }
}
```

### Prerequisites
- Node.js 18+
- Git on PATH (clones from GitHub on first run)
- No API token required — open source skills library

### Example Usage for KhanaBook
- "Use the Android MVVM mode to scaffold a new ViewModel for the Reports screen"
- "Apply the Kotlin coroutines skill for async database operations"
- "Use the Jetpack Compose UI skill to create a KhanaBook order summary card"
- "Switch to the REST API design mode for the Spring Boot backend endpoints"
- "Use the database migration skill for the Room schema upgrade"

### Special Notes
- **30 Android-specific modes** covering Kotlin, Compose, Room, Navigation, Hilt, WorkManager
- No timeout configured — some modes may take longer on first load as they fetch skills
- Skills are cached locally after first download for faster subsequent use
- Covers the full KhanaBook stack: Android (Kotlin), Spring Boot (Java), Angular (TypeScript)
- Community-maintained and regularly updated with new skills

---

## Design Resources MCP

**File:** `design-resources-mcp.json`

Provides AI agents with UI/UX design knowledge and resources for:
- Color palette suggestions and accessibility-compliant color combinations
- Typography scale recommendations for mobile and web
- Spacing and layout grid systems (Material Design 3, custom)
- UX guidelines for common patterns (navigation, forms, lists, modals)
- Component design best practices (buttons, cards, inputs, dialogs)
- Dark mode and theming strategies

### Usage

```json
{
  "mcpServers": {
    "design-resources": {
      "command": "npx",
      "args": ["-y", "ui-ux-pro-max"],
      "timeout": 10000
    }
  }
}
```

### Prerequisites
- Node.js 18+
- No API token required — bundled resource library

### Example Usage for KhanaBook
- "Suggest a warm, restaurant-themed color palette for KhanaBook"
- "What typography scale should I use for the Android app (Material 3)?"
- "Give me UX guidelines for the order checkout flow on a small screen"
- "What are best practices for a POS interface with large touch targets?"
- "Recommend spacing values for the menu item card layout"

### Special Notes
- Complements the Figma MCP by providing design rationale and guidelines (not just file access)
- Follows KhanaBook's existing design system in `docs/design/`
- Useful for ensuring consistency across Android (Material 3) and web admin (Angular Material) apps
- Includes accessibility guidance (WCAG 2.1 AA contrast ratios, touch target sizing)
- Quick timeout (10s) — lightweight resource lookups, no heavy processing

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

Use `test_mcp.ps1` to verify MCP server connectivity (PowerShell):

```powershell
./mcp/test_mcp.ps1
```
