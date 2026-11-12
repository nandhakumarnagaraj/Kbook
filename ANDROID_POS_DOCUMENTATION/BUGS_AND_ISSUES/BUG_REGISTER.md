# Bug Register

| Bug ID | Module | Title | Severity | Status | Description & Reproduction |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **BUG-001** | Settings / UI | ShopConfig fallback displaying daytime closing as rollover boundary | Medium (P2) | **RESOLVED** | Prior logic in `ShopConfigComponents.kt` displayed daytime closing times as the reset boundary instead of midnight (`00:00`). Fixed to `DEFAULT_CUTOFF`. |
| **BUG-002** | Core / BusinessDate | Multi-AI comment desync on settlement buffer | High (P1) | **RESOLVED** | Outdated comments claimed 60-min buffers while codebase adopted 30-min buffer. Harmonized and aligned across Android and Server. |
| **BUG-003** | Reports / Orders | Orders tab (Today/Yesterday/Custom) premature midnight split | Critical (P0) | **RESOLVED** | `ReportsViewModel.kt` used raw calendar midnight `00:00:00`, splitting active dinner shifts at midnight. Replaced with `BusinessDate.windowStart` and `windowEnd` honoring restaurant closing time and 30-min buffer. Added dining table name to `ReportViews.kt`. |
