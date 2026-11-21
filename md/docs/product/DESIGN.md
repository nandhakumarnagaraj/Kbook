---
name: KhanaBook POS
description: Offline-first restaurant billing & POS with integrated Easebuzz sub-merchant payments
colors:
  # ── Android POS (Dark Theme) ──
  # Source: apps/android/.../core/theme/Color.kt, Shape.kt
  android:
    brand-primary: '#C8960C'        # PrimaryGold
    brand-secondary: '#F0C040'      # LightGold
    brand-tertiary: '#D4A843'       # TextGold
    background: '#1A0A0A'           # DarkBrown1
    surface: '#2D1010'              # DarkBrown2
    dialog-surface: '#1A0A0A'       # DarkBrown1 (KhanaBookDialog uses background)
    on-surface: '#F5E6C8'           # TextLight
    on-primary: '#000000'           # Black (on PrimaryGold)
    # Semantic (used as borders on DarkBrown2)
    success: '#388E3C'              # SuccessGreen
    danger: '#C62828'               # DangerRed
    warning: '#FFB300'              # WarningYellow
    info: '#C8960C'                 # PrimaryGold (info accent)
    # Food type
    veg: '#4CAF50'                  # VegGreen
    non-veg: '#E53935'              # NonVegRed
    # Part-payment combos
    part-cash-upi: '#00A394'        # PartCashUpiTeal
    part-cash-pos: '#EF6C00'        # PartCashPosOrange
    part-upi-pos: '#4FA3E3'         # PartUpiPosSky
    # Orders surface (scoped)
    orders-bg: '#170809'            # OrdersBg
    orders-card: '#260F10'          # OrdersCard
    orders-gold: '#D9A514'          # OrdersGold
    # Border radius (dp)
    radius-card: '14dp'             # KhanaRadii.lg
    radius-modal: '14dp'            # KhanaRadii.lg (KhanaBookDialog uses xl=20dp, KhanaBookLargeDialog 18dp)
    radius-pill: '999dp'            # KhanaRadii.pill
    radius-input: '8dp'
    radius-badge: '6dp'             # KhanaRadii.sm (KhanaStatusBadge)

  # ── Web Admin (Light Theme) ──
  # Source: apps/web-admin/src/styles.css (:root)
  web:
    # Brand & Interactive
    primary: '#FF5A0A'              # --kb-color-primary (orange/saffron)
    primary-strong: '#C2410C'       # --kb-color-primary-strong (button fill)
    primary-hover: '#E04D05'        # --kb-color-primary-hover
    primary-soft: 'rgba(255,90,10,0.10)'  # --kb-color-primary-soft
    primary-foreground: '#FFFFFF'   # --kb-color-primary-foreground
    primary-rgb: '255,90,10'        # --kb-color-primary-rgb
    brand-purple: '#552AAF'         # --kb-brand-purple (sidebar, links)
    link: '#4A21A6'                 # --kb-color-link
    link-hover: '#38157F'           # --kb-color-link-hover (no --kb-brand-purple-hover)
    brand-orange: '#EC8C17'         # --kb-brand-orange (money accent, NOT for text)
    brand-orange-ink: '#B45309'     # --kb-brand-orange-ink (dark saffron text)
    focus-ring: 'rgba(var(--kb-color-primary-rgb), 0.45)'  # --brand-focus-ring (orange-based, matches primary); --kb-color-focus-ring does not exist

    # Semantic Status (deep steps for light mode)
    success: '#166534'              # --kb-color-success
    success-soft: 'rgba(22,101,52,0.10)'  # --kb-color-success-soft
    warning: '#9A3F08'              # --kb-color-warning
    warning-soft: 'rgba(154,63,8,0.10)'   # --kb-color-warning-soft
    danger: '#B91C1C'               # --kb-color-danger
    danger-soft: 'rgba(185,28,28,0.07)'   # --kb-color-danger-soft
    danger-solid: '#B91C1C'         # --kb-color-danger-solid
    danger-solid-hover: '#991B1B'   # --kb-color-danger-solid-hover
    info: '#4A21A6'                 # --kb-color-info
    info-soft: 'rgba(74,33,166,0.09)'     # --kb-color-info-soft

    # Surfaces & Neutrals
    bg-app: '#F3F8FE'               # --kb-color-bg-app (page canvas)
    surface: '#FFFFFF'              # --kb-color-surface (cards, tables)
    surface-2: '#F1F5F9'            # --kb-color-surface-2 (table header, hover)
    surface-3: '#FFFFFF'            # --kb-color-surface-3 (modals, popovers)
    muted: '#F1F5F9'                # --kb-color-muted (recessed input)
    border: '#D9E5F2'               # --kb-color-border (dividers, card edges)
    border-strong: '#4B5563'        # --kb-color-border-strong (control borders)
    border-control: '#64748B'       # --kb-border-control (input borders)
    foreground: '#111827'           # --kb-color-foreground (primary text)
    foreground-2: '#334155'         # --kb-color-foreground-2 (values, table cells)
    muted-foreground: '#586F94'     # --kb-color-muted-foreground (labels, metadata)

    # Sidebar (dark plane)
    sidebar-bg: '#2E1470'           # --pos-sidebar-bg
    sidebar-hover: 'rgba(255,255,255,0.08)'  # --pos-sidebar-hover
    sidebar-active: '#552AAF'       # --pos-sidebar-active
    sidebar-text: '#DDD6F3'         # --pos-sidebar-text
    sidebar-ink: '#FFFFFF'          # --pos-sidebar-ink
    sidebar-ink-muted: '#B9A8E8'    # --pos-sidebar-ink-muted
    sidebar-border: 'rgba(255,255,255,0.10)'  # --pos-sidebar-border
    sidebar-accent: '#EC8C17'       # --pos-sidebar-accent (saffron)

typography:
  # Android: Poppins via Google Font (Light–ExtraBold), fallback system sans-serif
  # Web: Inter (body) + Plus Jakarta Sans (display)
  android:
    font-family: 'Poppins'
    font-provider: google-fonts
    weights: [Light, Normal, Medium, SemiBold, Bold, ExtraBold]
    fallback: system-ui
    # Responsive scaling per window tier (handled in Type.kt)
    # CompactPhone 0.90/0.95, MediumPhone 1.00/1.00, LargePhone 1.08/1.04, Tablet 1.18/1.08
    # Base sizes (MediumPhone):
    display-large: 57sp
    display-medium: 45sp
    display-small: 36sp
    headline-large: 32sp
    headline-medium: 28sp
    headline-small: 24sp
    title-large: 22sp
    title-medium: 16sp
    title-small: 14sp
    body-large: 16sp
    body-medium: 14sp
    body-small: 12sp
    label-large: 14sp
    label-medium: 12sp
    label-small: 11sp

  web:
    font-family-display: 'Plus Jakarta Sans'
    font-family-body: 'Inter'
    font-family-mono: 'JetBrains Mono'
    # 9-step scale (actual token values)
    text-2xs: '11px'   # --kb-text-2xs (eyebrow, uppercase)
    text-xs: '12px'    # --kb-text-xs (caption, chip)
    text-sm: '13px'    # --kb-text-sm (dense table cell)
    text-md: '14px'    # --kb-text-md (body, default)
    text-lg: '16px'    # --kb-text-lg (card title)
    text-xl: '20px'    # --kb-text-xl (section heading)
    text-2xl: '24px'   # --kb-text-2xl (page heading)
    text-3xl: '28px'   # --kb-text-3xl (KPI figure)
    text-4xl: '32px'   # --kb-text-4xl (hero figure)
    line-height-body: 1.5
    line-height-heading: 1.25

rounded:
  # Android (dp) — KhanaRadii
  android:
    card: '14dp'       # KhanaRadii.lg
    modal: '14dp'      # KhanaRadii.lg (KhanaBookDialog uses xl=20dp, KhanaBookLargeDialog 18dp)
    pill: '999dp'      # KhanaRadii.pill
    input: '8dp'
    badge: '6dp'       # KhanaRadii.sm

  # Web (px) — CSS tokens
  web:
    sm: '4px'          # --kb-radius-sm
    md: '6px'          # --kb-radius-md
    lg: '8px'          # --kb-radius-lg
    xl: '12px'         # --kb-radius-xl
    '2xl': '16px'      # --kb-radius-2xl
    full: '9999px'     # --kb-radius-full
    card: '12px'       # --kb-radius-card

spacing:
  # Android (dp) — base 4dp, scales by tier
  android:
    base: '4dp'
    hairline: '2dp'
    xs: '4dp'
    sm: '8dp'
    sm-md: '12dp'
    md: '16dp'
    md-lg: '20dp'
    lg: '24dp'
    xl: '32dp'
    huge: '48dp'
    xhuge: '64dp'
    screen-content-padding: '16dp'
    bottom-list-padding: '88dp'
    button-height-compact: '48dp'
    button-height: '52dp'        # MediumPhone, scales to 68dp on Tablet
    button-height-large: '56dp'
    input-height: '56dp'

  # Web (px) — CSS tokens
  web:
    space-1: '4px'      # --kb-space-1
    space-2: '8px'      # --kb-space-2
    space-3: '12px'     # --kb-space-3
    space-4: '16px'     # --kb-space-4
    space-5: '20px'     # --kb-space-5
    space-6: '24px'     # --kb-space-6
    space-8: '32px'     # --kb-space-8
    space-10: '40px'    # --kb-space-10
    space-12: '48px'    # --kb-space-12
    space-16: '64px'    # --kb-space-16
    touch-min: '44px'      # --kb-touch-min
    touch-primary: '48px'  # --kb-touch-primary

elevation:
  # Android: Material 3 dark scheme (surface/background separation)
  # Web:
  web:
    xs: '0 1px 2px rgba(15,23,42,0.06)'                               # --kb-shadow-xs
    sm: '0 1px 3px rgba(15,23,42,0.10), 0 1px 2px rgba(15,23,42,0.06)'  # --kb-shadow-sm
    md: '0 4px 12px rgba(15,23,42,0.10)'                              # --kb-shadow-md
    lg: '0 12px 32px rgba(15,23,42,0.16)'                             # --kb-shadow-lg
    card: '0 1px 3px rgba(15,23,42,0.06)'                             # --shadow-card (legacy name)
    elevated: '0 6px 20px rgba(15,23,42,0.12)'                        # --shadow-elevated (legacy name)
    warm: '0 6px 20px rgba(15,23,42,0.12)'                            # --kb-shadow-warm

motion:
  # Android: Material 3 standard easing, respects system animation scale
  # Web:
  web:
    fast: '150ms ease-out'       # --kb-motion-fast
    base: '200ms ease-out'       # --kb-motion-base
    slow: '300ms ease-out'       # --kb-motion-slow
    ease-standard: 'cubic-bezier(0.2,0,0.2,1)'  # --kb-ease-standard
    reduced-motion: '0.01ms'     # @media (prefers-reduced-motion: reduce) — most durations; exceptions: .spinning, .spinner, .skeleton::after set to 1.5s !important + infinite

z-index:
  web:
    raised: 1          # --kb-z-raised
    tooltip: 10        # --kb-z-tooltip
    dock: 30           # --kb-z-dock
    dropdown: 60       # --kb-z-dropdown
    topbar: 100        # --kb-z-topbar
    backdrop: 110      # --kb-z-backdrop
    sidebar: 120       # --kb-z-sidebar
    skip: 130          # --kb-z-skip
    peek: 140          # --kb-z-peek
    modal: 1000        # --kb-z-modal
    toast: 1100        # --kb-z-toast

components:
  # Cross-platform component token mapping (audit-corrected)
  button-primary:
    android: { name: 'KhanaPrimaryButton', background: 'PrimaryGold', text: 'DarkBrown1 (#1A0A0A)', height: 'scales with tier via buttonHeightLarge (56-68dp)', radius: '14dp' }
    web: { class: '.primary-btn', background: 'var(--kb-color-primary-strong)', text: 'var(--kb-color-primary-foreground)', height: 'auto (padding-based)', radius: 'var(--kb-radius-lg)' }
  button-secondary:
    android: { name: 'KhanaSecondaryButton', style: 'outlined', border: 'PrimaryGold', text: 'PrimaryGold' }
    web: { class: '.ghost-btn (bento.css)', background: 'transparent', border: 'var(--kb-border-control)', text: 'var(--kb-color-foreground)' }
  button-danger:
    android: { name: 'KhanaDestructiveButton', background: 'DangerRed', text: 'TextLight (#F5E6C8)' }
    web: { class: '.danger-btn', background: 'var(--kb-color-danger-solid)', text: 'var(--kb-color-primary-foreground)' }
  button-ghost-danger:
    android: 'not implemented'
    web: { class: '.ghost-btn.danger-btn', background: 'transparent', border: 'danger', text: 'danger' }
  icon-button:
    android: { class: 'IconButton (M3)', size: '48dp' }
    web: { classes: ['.icon-btn (32x32)', '.icon-action-btn (38x38)'], note: 'no ax-button[appearance="icon"]' }
  input-field:
    android: { class: 'KhanaBookInputField', style: 'outlined, M3 OutlinedTextField defaults (no explicit height/radius), singleLine: Boolean = true param only' }
    web: { classes: ['.field-control', '.field-select (min-height 44px, 8px radius)', '.field-input (8px radius, no min-height)'] }
  card:
    android: { class: 'KhanaBookCard', background: 'DarkBrown2', radius: '14dp' }
    web: { class: '.panel', background: 'var(--kb-color-surface)', radius: 'var(--kb-radius-xl)', border: 'var(--kb-color-border)' }
  table-row-height:
    android: 'responsive (scales with tier)'
    web: 'no fixed rule (cells use padding: 0.7rem 0.85rem)'
  badge:
    android: { class: 'KhanaStatusBadge', radius: '6dp' }
    web: { classes: ['.chip', '.chip-pill'], radius: 'full', style: 'soft semantic bg + semantic text' }
  toast:
    android: { class: 'KhanaBookSnackbar', background: 'DarkBrown2', border: '1.5dp semantic', kinds: 4, deduplication: '2s', placement: '24dp horizontal' }
    web: { class: '.global-toast', background: 'var(--kb-color-surface-3)', border: '1px solid var(--kb-color-border-strong)', kinds: 3, single: true, max-width: '380px' }
  notification-banner:
    android: { class: 'InAppNotificationBanner', background: 'DarkBrown2', accent: '4dp left bar + 1dp full border', auto-dismiss: '6s', no-offline-string: true }
    web: 'not used (use toast + inline alert)'
  skeleton:
    android: { class: 'Shimmer' }
    web: { class: '.skeleton', background: 'var(--kb-color-surface-2)', animation: 'shimmer 1.3s translateX' }
  drawer-sidebar:
    android: { classes: ['ModalNavigationDrawer', 'PermanentNavigationDrawer'] }
    web: { class: '.sidebar', position: 'sticky', expanded: '232px', collapsed: '64px (--rail-width)', bg: 'var(--pos-sidebar-bg)' }
  tabs:
    android: { classes: ['TabRow', 'ScrollableTabRow'], indicator: 'Lottie' }
    web: { class: '.tab-pill', active: 'filled pill', note: 'no .tab-bar/ax-tabs/underline' }
  modal-dialog:
    android: { classes: ['KhanaBookDialog (DarkBrown1, 20dp radius)', 'KhanaBookLargeDialog (DarkBrown2, 18dp radius)'] }
    web: { class: '.modal-box', background: 'var(--kb-color-surface-3)', shadow: 'lg', max-height: 'min(88vh, 760px)' }

# ── Design System Notes ──────────────────────────────────────────
# Color Hierarchy (Web):
#   Primary 🟠  → CTAs, selected nav, focus rings (sparingly)
#   Brand Violet 🟣 → Sidebar active, links, brand text
#   Brand Saffron 🟡 → Money accents, chips, bars (NOT for text)
#   Semantic → Status only (success/warning/danger/info)
#   Neutral → Text hierarchy, backgrounds, dividers (bulk of UI)

# Android is dark-only, Web is light-only (v1). Token namespaces are separate.
# Source of truth: Android → Color.kt/Theme.kt/Shape.kt; Web → styles.css
# Audit: 2026-11-19 v2.3.1 — all tokens verified against source files