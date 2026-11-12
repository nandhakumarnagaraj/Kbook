# KhanaBook AI Engineering Standard & Code Reality Rules

> **Status:** Mandatory for all AI Models, Autonomous Agents, and Human Engineers.  
> **Applies to:** Claude Code, Gemini/Antigravity, Codex, OpenCode, Nemotron, Cursor, and all PR reviewers.  
> **Repository:** KhanaBook SaaS Platform (Android POS, Spring Boot Server, Web Admin, Shared Contracts).

---

## 1. Core Axiom: Code Reality First

1. **Implementation is the Single Source of Truth for existing behaviour.**
   - The code tells us what the system **actually does**.
   - Comments explain what the code **actually does and why**.
   - Specifications & tickets describe what the system is **expected** to do.
   - Executable tests **verify** whether actual behaviour matches expected behaviour.
2. **Never document intended behaviour as implemented behaviour.**
   - If a prompt instructs: `"Implement a 30-minute grace buffer"`, but the constant is `SETTLEMENT_BUFFER_MINUTES = 60`, comments must state `60 minutes`.
   - Never write a comment claiming a function performs an action that is not present in the execution path.
3. **No Silent Refactoring or Fake Fixes:**
   - When comments, tests, and code disagree, **never silently modify code or write hallucinated comments** to make things look consistent.
   - Record discrepancies explicitly in `REQUIREMENT_IMPLEMENTATION_GAPS.md` or as audit findings.

---

## 2. Three Distinct Documentation Layers

Do not conflate these three layers. They must align, but they serve different purposes:

| Layer | Location | Purpose | Source of Truth For |
| :--- | :--- | :--- | :--- |
| **Code Comments** | Inline in `.kt`, `.java`, `.ts` | Explains what the code actually does and why it was structured this way | Runtime implementation & mechanics |
| **Technical Architecture** | `docs/`, `ADR/`, `openspec/` | System architecture, data flow, synchronization contracts, schema migrations | High-level engineering design |
| **Product Requirements** | `specs/`, PR descriptions | Expected domain behavior, business rules, edge cases | Desired product intent |

---

## 3. Comment Placement Standard

### A. WHERE COMMENTS ARE MANDATORY

| Code Construct | Comment Type | Mandatory Content |
| :--- | :--- | :--- |
| **Classes / Interfaces** | Class-level KDoc/Javadoc | Responsibility, architectural boundary, source of truth (e.g. "Server is authority"), lifecycle. |
| **Public Service & API Methods** | Method KDoc/Javadoc | Behavior, parameter preconditions, return value semantics, side effects, exceptions thrown. |
| **Boundary & Time Math** | Method & inline | Exact mathematical formula, time zone handling, midnight wrap logic, buffer duration, inclusive/exclusive boundaries. |
| **Financial / Tax / Discount Logic** | Method & inline | Rounding rules (e.g. `HALF_UP`), precision, currency units (paise vs rupees), compliance rules. |
| **Database Transactions** | Method & inline | Transaction boundary (`@Transactional`, Room transaction), atomicity assumptions, rollback triggers. |
| **Sync & Conflict Resolution** | Method & inline | Conflict resolution strategy (LWW, append-only, server-wins), idempotency keys, terminal state handling. |
| **Security & Permissions** | Method & inline | Role restrictions (e.g. cashier vs manager), token requirements, bypass guards. |
| **Configuration Constants** | Field KDoc/Javadoc | Business significance, measurement units (ms, sec, min, paise), valid ranges, downstream impact. |

### B. WHERE COMMENTS ARE CONDITIONAL (Only When Non-Obvious)

- **Complex Conditionals:** Explain *why* the condition branch exists, especially when handling rare edge cases or legacy backward compatibility.
- **Exception Catch Blocks:** Explain why an exception is caught and suppressed, or translated into a different domain error.
- **Coroutines & Threading Dispatchers:** Explain why a non-default dispatcher (`Dispatchers.IO`, `newSingleThreadContext`) is strictly required.

### C. WHERE COMMENTS ARE STRICTLY FORBIDDEN (Slop & Anti-Patterns)

- ❌ **Translating obvious code into English:**  
  ```kotlin
  // BAD: Increment count by one
  count++
  ```
- ❌ **Repeating method names:**  
  ```kotlin
  // BAD: Gets the restaurant by id
  fun getRestaurantById(id: String)
  ```
- ❌ **Simple getters, setters, or standard property delegations.**
- ❌ **Standard boilerplate:** Framework lifecycle overrides (`onCreate`, `ngOnInit`) without custom behavior.
- ❌ **Hallucinated or unverified boundary times:** E.g. writing `(until 01:50)` for a 02:10 closing + 30m buffer.

---

## 4. The 5-Element Comment Anatomy

When documenting non-trivial business logic, include the relevant subset of these 5 facets:

1. **WHY:** Why does this logic exist? (Business rationale or bug prevention)
2. **WHAT:** What does it actually do? (Precise execution summary)
3. **HOW:** How does the mechanism work? (Algorithm, wrap-around, or fallback)
4. **WHEN:** Under what conditions does this path execute? (Preconditions)
5. **LIMITATIONS:** What edge cases are intentionally unhandled or delegated elsewhere?

```kotlin
/**
 * Resolves the business date (YYYY-MM-DD) for an order timestamp.
 *
 * WHY: Late-night dining establishments operate past calendar midnight. Splitting orders at 00:00
 * fragments cash reconciliations and daily sales reporting.
 *
 * WHAT: Applies either an active business-day cutoff time or appends the settlement buffer to the shop's
 * closing time to keep the shift on the previous calendar day.
 *
 * HOW:
 * 1. If cutoff is active, bills before cutoff belong to (calendarDate - 1).
 * 2. If no cutoff is set, closingBoundary() adds SETTLEMENT_BUFFER_MINUTES (30 min) to closingTime.
 *    Orders created before this boundary map to (calendarDate - 1).
 *
 * LIMITATIONS: Does not alter bills already stamped by the server (BillSyncService preserves server date).
 */
```

---

## 5. Verification Protocol Before Every AI Commit

Every AI model must perform this 4-step sanity check before proposing or committing code changes:

1. **Source of Truth Check:**  
   Did you inspect the actual implementation rather than relying on existing comments or the developer's prompt description?
2. **Mathematical Verification:**  
   If your comments or test cases involve time, offsets, or currency calculations:
   - Calculate every single boundary manually.
   - Example: Closing at `02:10` + `30m buffer` = `02:40`, **NOT** `03:10` and **NOT** `01:50`.
   - Verify that test assertions (`assertEquals`) match the exact boundary.
3. **Android-Server Parity Check:**  
   When modifying shared domain logic (e.g., `BusinessDate.kt` in Android POS and `BusinessDay.java` in Server):
   - The two implementations MUST remain mathematically identical.
   - If Android uses 30 minutes, Server must use 30 minutes.
   - If boundaries differ, sync reconciliation will corrupt business dates.
4. **No-Slop Check:**  
   Did you add comments that merely restate variable names or trivial assignments? If so, delete them.

---

## 6. Discrepancy Reporting Format

When an AI model identifies that code behavior contradicts requirements or comments, it must produce this structured report:

```markdown
### Code Reality Discrepancy Finding
- **File:** `apps/android/app/src/main/java/com/khanabook/lite/pos/core/util/BusinessDate.kt`
- **Location:** Line 42 vs Line 65
- **Actual Code Behavior:** `SETTLEMENT_BUFFER_MINUTES = 30` (Boundary for 02:10 closing is 02:40)
- **Existing Comment/Doc:** Line 65 claims `(02:10 -> 03:10)` (60-minute buffer math)
- **Test Assertion Status:** `BusinessDateTest.kt:178` asserts `03:10` is the boundary
- **Root Cause:** Buffer constant was changed from 60 to 30, but comments and tests were not updated to match reality.
- **Remediation Options:**
  - Option A: Align code and tests to 30-minute buffer (Boundary: 02:40).
  - Option B: Revert constant to 60-minute buffer (Boundary: 03:10) to match original test specification.
```

---

## 7. Mandatory AI Prompts for Code Reality

Include this directive in all multi-agent prompts:
> *"Adhere strictly to AI_ENGINEERING_RULES.md. Code implementation is the sole source of truth. Document what the code actually does, not what was intended. Verify all math and boundaries before writing comments or tests."*
