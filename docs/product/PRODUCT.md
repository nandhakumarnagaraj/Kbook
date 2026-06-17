# Product

## Register

product

## Users

- **Restaurant Owners (OWNER)**: Manage day-to-day operations from the web admin dashboard or Android POS — billing, orders, menu, staff, marketplace integrations (Zomato/Swiggy), and Easebuzz payment settlements. Context: busy restaurant environments, often offline, need reliable tools that sync when connectivity returns.
- **Platform Administrators (KBOOK_ADMIN)**: Oversee the KhanaBook platform — onboard businesses, manage sub-merchant KYC lifecycles, track platform revenue, monitor transactions, configure commission rates, and handle settlements. Context: data-dense workflows, batch operations, compliance tracking.

## Product Purpose

KhanaBook is an offline-first restaurant billing and POS platform with integrated Easebuzz sub-merchant payment processing. It connects restaurant owners to their operations (billing, orders, menu, staff) and to third-party marketplaces (Zomato, Swiggy), while giving platform administrators visibility and control over the payment settlement pipeline (sub-merchant onboarding, KYC, split payments, commissions).

Success means: a restaurant owner opens the POS, creates a bill in under 10 seconds even while offline, and payments flow through Easebuzz sub-merchant splits without manual intervention. The platform admin sees every transaction, every KYC status, and every settlement in a single dashboard.

## Brand Personality

**Warm, Professional, Grounded.**

- **Warm**: The saffron/amber palette feels culinary and inviting, not corporate-blue cold. Dark mode uses warm browns and creams, not harsh blacks and whites.
- **Professional**: Dense data dashboards, precise financial numbers, clear status indicators. This is a serious tool for serious businesses.
- **Grounded**: Earthy neutral tones (warm creams, deep browns, soft tans). No glassmorphism, no neon, no decorative flair that distracts from the task.

## Anti-references

- Generic SaaS dashboards: purple gradients on white backgrounds, hero-metric cards with big numbers and tiny labels, glassmorphism defaults, AI-generated aesthetic
- Dark-mode-first developer tools: terminal-native aesthetic, neon-on-black, dense monospaced interfaces
- Consumer food delivery apps: playful illustrations, excessive motion, gamification
- Enterprise banking UIs: cold blues, rigid grids, no personality

## Design Principles

1. **Offline confidence**: The interface must communicate sync state clearly (live dot, last-synced timestamps, unsynced counts) so users trust the tool even without connectivity.

2. **Density with breath**: Data-dense tables and dashboards are the norm, but every row gets adequate padding, every card has clear separation, and whitespace is used intentionally to group related information.

3. **Status at a glance**: Payment states, KYC statuses, sub-merchant lifecycles, and order statuses use consistent, scannable chips and badges. Color is always paired with text.

4. **Consistent affordance vocabulary**: The same button shape, the same form-control style, the same icon set, the same navigation pattern across web and Android. Users should never wonder "is this clickable?"

5. **Warm restraint**: The saffron accent appears on primary actions and active states only. Neutral creams and browns carry the rest. Decoration is saved for moments, not pages.

## Accessibility & Inclusion

- Target: WCAG 2.2 AA compliance
- All interactive elements have visible `:focus-visible` rings (2px brand color, 2px offset)
- Skip-to-content link present on all authenticated pages
- Touch targets minimum 44px (web) / 48dp (Android)
- `prefers-reduced-motion` respected globally: all animations, transforms, and transitions cut to near-zero duration
- Color never the sole indicator: status chips always include text, icons accompany color-coded badges
- `aria-label` on icon-only buttons, `aria-hidden="true"` on decorative icons
- `tabular-nums` on all financial data, timestamps, and counts
