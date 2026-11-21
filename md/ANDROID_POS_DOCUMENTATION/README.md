# KhanaBook Android POS Operational Knowledge Base

Welcome to the central operational knowledge base and living manual for the **KhanaBook Lite Android POS** application (`com.piquantservices.khanabooklite.debug`).

This repository of documentation is built and maintained by real-world cashier simulation, graphical user interface (UI) automation, and hardware device verification. It reflects **actual observed application behavior**, not assumptions or disconnected specifications.

---

## 1. Documentation Index & Sitemap

### Architecture & Setup
- **[01 Application Overview](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/01_APPLICATION_OVERVIEW.md)**: Core mission, system boundaries, and architectural principles.
- **[02 Device & Environment Setup](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/02_DEVICE_AND_ENVIRONMENT_SETUP.md)**: Physical target devices, ADB configuration, build flavors, and diagnostic toolchains.
- **[03 UI Navigation Map](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/03_UI_NAVIGATION_MAP.md)**: Complete hierarchical screen graph and navigational transitions.
- **[04 Screen Inventory](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/04_SCREEN_INVENTORY.md)**: Comprehensive catalog of UI screens, components, and interactive bounds.

### Operations Reference
- **[OPERATIONS/ Directory](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/OPERATIONS/README.md)**:
  - `HOME_DASHBOARD_OPERATIONS.md`: Dashboard metrics, active cards, and quick launchers.
  - `DINE_IN_OPERATIONS.md`: Table selection, multi-item cart assembly, and table tab holds.
  - `PAYMENT_OPERATIONS.md`: Cash tendered calculation, dynamic UPI QR generation, card POS.
  - `KOT_AND_KITCHEN_OPERATIONS.md`: Kitchen Order Ticket queuing, delta KOTs, and reprints.
  - `ORDER_HISTORY_OPERATIONS.md`: Date filtering (Today/Yesterday/Custom), order cards, and receipt duplicates.
  - `BUSINESS_SETTINGS_OPERATIONS.md`: Operating hours, overnight closing time, settlement buffers.

### End-to-End Workflows
- **[WORKFLOWS/ Directory](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/WORKFLOWS/README.md)**:
  - `COMPLETE_DINE_IN_WORKFLOW.md`: Step-by-step cashier walkthrough from table seating to settlement.
  - `DAILY_CLOSING_WORKFLOW.md`: Night shift crossover, buffer grace period, and sequence reset.

### Verification & Quality Assurance
- **[TEST_CASES/ Directory](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/TEST_CASES/README.md)**: Functional, business logic, payment, and boundary test cases.
- **[BUGS_AND_ISSUES/ Directory](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/BUGS_AND_ISSUES/README.md)**: Bug register with reproduction steps, severity, and root causes.
- **[RECOMMENDATIONS/ Directory](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/RECOMMENDATIONS/README.md)**: Ergonomic, operational, and architectural improvements.
- **[DEVICE_SESSIONS/ Directory](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/DEVICE_SESSIONS/README.md)**: Chronological audit trail of live device testing runs.

### Operational Status
- **[CHANGELOG.md](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/CHANGELOG.md)**: Versioned log of verified behaviors and updates.
- **[KNOWN_LIMITATIONS.md](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/KNOWN_LIMITATIONS.md)**: Environmental and platform boundaries.
- **[UNVERIFIED_ASSUMPTIONS.md](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/UNVERIFIED_ASSUMPTIONS.md)**: Features pending live hardware validation.
- **[NEXT_ACTIONS.md](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/ANDROID_POS_DOCUMENTATION/NEXT_ACTIONS.md)**: Immediate roadmap for next testing sessions.

---

## 2. Policy for AI Agents Operating KhanaBook

Whenever an AI agent is tasked with operating, inspecting, or debugging the KhanaBook POS application:

1. **Read Before Operating**: Consult `03_UI_NAVIGATION_MAP.md` and the relevant `OPERATIONS/` files.
2. **Implementation is Reality**: Never assume documentation or source code comments are true without verifying actual UI behavior.
3. **No Direct Database Bypass**: Test workflows through the Compose UI hierarchy via ADB taps, text entry, and gestures.
4. **Log Evidence**: Record screenshots in `artifacts/android-ui-tests/` and update `DEVICE_SESSIONS/SESSION_LOG.md`.
