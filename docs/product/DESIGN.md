---
name: KhanaBook POS
description: Offline-first restaurant billing & POS with integrated Easebuzz sub-merchant payments
colors:
  # ── KhanaBook Brand Core ──────────────────────────────────────
  brand-saffron: '#C85A00'
  brand-saffron-light: '#E8832A'
  brand-saffron-dark: '#994500'
  brand-saffron-android: '#D97706'

  # ── Primary: Saffron/Orange 🟠 (use sparingly for CTAs & brand) ─
  primary: '#C85A00'
  on-primary: '#FFFFFF'
  primary-container: '#FFDBCA'
  on-primary-container: '#331200'

  # ── Secondary: Amber/Warm Gold 🟡 (workhorse accent) ──────────
  secondary: '#B8860B'
  on-secondary: '#FFFFFF'
  secondary-container: '#FFF1CC'
  on-secondary-container: '#362900'

  # ── Tertiary: Sky Blue 🔵 (info & data indicators) ───────────
  tertiary: '#0284C7'
  on-tertiary: '#FFFFFF'
  tertiary-container: '#CDE5FF'
  on-tertiary-container: '#001E33'

  # ── Neutral / Surface — Light Mode ────────────────────────────
  background: '#F8F5F0'
  on-background: '#241913'
  surface: '#FFFFFF'
  on-surface: '#241913'
  surface-dim: '#EBE3DC'
  surface-bright: '#FFFFFF'
  surface-variant: '#F4DED4'
  on-surface-variant: '#574237'
  outline: '#8B7265'
  outline-variant: '#DEC0B2'

  # ── Neutral Scale (warm grays) ────────────────────────────────
  warm-cream-bg: '#F7F3EE'
  white-panel: '#FFFFFF'
  warm-tan-surface: '#EAE3DC'
  warm-tan-hover: '#DDD5CC'
  warm-orange-hover: '#FDF0E6'
  deep-brown-ink: '#241913'
  warm-brown-secondary: '#5C5248'
  warm-muted: '#8B7D73'

  # ── Dark Mode ─────────────────────────────────────────────────
  dark-bg: '#17130F'
  dark-panel: '#211A14'
  dark-surface: '#332A22'
  dark-surface-hover: '#3E332A'
  dark-ink: '#F7F3EE'
  dark-ink-secondary: '#C1B7AD'
  dark-muted: '#B0A496'
  dark-primary: '#E8832A'
  dark-secondary: '#E8A838'
  dark-tertiary: '#7FB8F0'

  # ── Semantic Status ───────────────────────────────────────────
  success: '#16A34A'
  success-light: '#22C55E'
  error: '#DC2626'
  warning: '#D97706'
  info: '#0284C7'
  purple-accent: '#7C5CDB'

typography:
  display-lg:
    fontFamily: Inter, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif
    fontSize: 32px
    fontWeight: 800
    lineHeight: 1.2
    letterSpacing: -0.5px
  headline-md:
    fontFamily: Inter, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif
    fontSize: 24px
    fontWeight: 700
    lineHeight: 1.3
  title-sm:
    fontFamily: Inter, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif
    fontSize: 18px
    fontWeight: 600
    lineHeight: 1.4
  body-md:
    fontFamily: Inter, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.5
  label-caps:
    fontFamily: Inter, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif
    fontSize: 12px
    fontWeight: 700
    letterSpacing: 0.5px
    textTransform: uppercase

rounded:
  xs: 4px
  sm: 6px
  md: 10px
  lg: 14px
  xl: 18px
  pill: 9999px

spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 32px

components:
  # ── Buttons ────────────────────────────────────────────────────
  button-primary:
    backgroundColor: '{colors.brand-saffron}'
    textColor: '#FFFFFF'
    rounded: '{rounded.md}'
    padding: '0 24px'
    height: 48px
  button-primary-hover:
    backgroundColor: '{colors.brand-saffron-dark}'
  button-secondary:
    backgroundColor: transparent
    textColor: '{colors.secondary}'
    borderColor: '{colors.secondary}'
    rounded: '{rounded.md}'
    padding: '0 24px'
    height: 40px
  button-tertiary:
    backgroundColor: transparent
    textColor: '{colors.tertiary}'
    borderColor: '{colors.tertiary}'
    rounded: '{rounded.md}'
    padding: '0 24px'
    height: 40px

  # ── Cards ──────────────────────────────────────────────────────
  card-default:
    backgroundColor: '{colors.surface}'
    rounded: '{rounded.xl}'
    padding: 16px
  card-stat:
    backgroundColor: '{colors.surface}'
    rounded: '{rounded.xl}'
    padding: 16px
  card-stat-dark:
    backgroundColor: '{colors.dark-panel}'

  # ── Inputs ─────────────────────────────────────────────────────
  input-field:
    backgroundColor: '{colors.surface}'
    rounded: '{rounded.sm}'
    padding: '0 12px 0 38px'
    height: 48px

  # ── Status Chips ───────────────────────────────────────────────
  status-chip-active:
    backgroundColor: 'rgba(22,163,74,0.12)'
    textColor: '{colors.success}'
  status-chip-pending:
    backgroundColor: 'rgba(217,119,6,0.12)'
    textColor: '{colors.warning}'
  status-chip-rejected:
    backgroundColor: 'rgba(220,38,38,0.12)'
    textColor: '{colors.error}'
  badge-info:
    backgroundColor: 'rgba(2,132,199,0.12)'
    textColor: '{colors.info}'

  # ── Navigation ─────────────────────────────────────────────────
  nav-section-label:
    fontSize: 12px
    fontWeight: 800
    textTransform: uppercase

# ── Design System Notes ──────────────────────────────────────────
# Color Hierarchy:
#   Primary 🟠  → CTAs, selected nav, brand marks (sparingly)
#   Secondary 🟡 → Section titles, badges, highlights, secondary buttons (workhorse)
#   Tertiary 🔵  → Info chips, data indicators, helper links (supporting)
#   Neutral    → Text hierarchy, backgrounds, dividers (bulk of UI)
