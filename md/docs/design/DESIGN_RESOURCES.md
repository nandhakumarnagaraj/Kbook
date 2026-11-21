# KhanaBook design resources

Living reference for UI redesign research. Every entry below was probed with a
browser user-agent on 2026-10-26 and carries a `status` so you know whether the
site is reachable in a script, blocked to bots, paywalled, redirected, or down.

This doc is the human-readable mirror of the `designrefs` MCP server
(`C:/Users/nandh/Desktop/designrefs-mcp`), whose bundled data is canonical for
tool lookups. The MCP tools work offline for the directory and hit keyless
public endpoints for fonts and colour generation:

- `designrefs_resources_list` — category entries with URLs and access notes
- `designrefs_resources_search` — keyword search across name, purpose, tags
- `designrefs_font_search` / `font_get` / `font_css` — Google Fonts + Fontshare
- `designrefs_palette_generate` — Colormind 5-colour palettes
- `designrefs_color_scale` / `color_contrast` — OKLCH ramps, WCAG 2.2 pass/fail
- `designrefs_tokens_export` — `--kb-*` CSS custom properties + JSON tokens

All are read-only, need no API keys, and touch no KhanaBook data. The server is
denied globally in `opencode.jsonc` and re-granted to the `ui` agent in
`agents/ui.md`.

## How to use it for KhanaBook

1. `resources_search "dashboard"` (or `resources_list systems`) to pick reference
   sources; open the live site in a browser for anything marked `blocked`.
2. `font_search` → `font_get` → `font_css` to choose and embed a typeface (Inter
   / General Sans / Satoshi are good admin-dashboard candidates).
3. `palette_generate` → `color_scale` → `color_contrast` → `tokens_export` to
   refine the navy-and-purple palette into `--kb-*` tokens.
4. Propose the screen in Figma/Penpot, implement with existing `.panel` /
   `.data-table` / `.primary-btn` patterns and `--kb-*` tokens, verify with
   Playwright + axe at desktop and tablet widths.

Token naming follows `docs/design/DESIGN_SYSTEM.md` (`--bg`, `--panel`,
`--brand`, `.primary-btn`, 44px touch targets). Do not add a second token file.

## Status key

| status | meaning |
|---|---|
| ok | Responds 200 to a scripted fetch; usable from tools |
| blocked | 403/429 to scripts; open in a real browser instead |
| auth | Needs an access key or signed-in session; API requires credentials |
| redirect | Moved to a new domain; final URL recorded |
| timeout | No response in ~12s; try manually later |
| down | 5xx at probe time |

## A — UI/UX and website inspiration

| Resource | Best use | URL | Access | Status |
|---|---|---|---|---|
| Mobbin | Real app screens and UX flows | https://mobbin.com | Free browsing; sign-in for saved screens and full flows | ok |
| Dribbble | UI concepts and visual ideas | https://dribbble.com | Bot challenge on site; API needs OAuth credentials | blocked |
| Behance | Full design case studies | https://www.behance.net | Blocks scripted access; open in a real browser | blocked |
| Awwwards | Award-winning websites and interactions | https://www.awwwards.com | Free browsing | ok |
| SiteInspire | Curated website designs | https://www.siteinspire.com | Free browsing | ok |
| Land-book | Landing-page inspiration | https://land-book.com | Free browsing; paid collections | ok |
| Recent (formerly Godly) | Curated visual and web design | https://recent.design | Free browsing | ok |
| Collect UI | Individual UI components | https://collectui.com | Free browsing | ok |
| Lapa Ninja | SaaS and landing-page examples | https://www.lapa.ninja | Free browsing | ok |
| Lookup.design | Design discovery | https://lookup.design | Free browsing | ok |
| Refero | Real product and website UI references | https://refero.design | Free browsing; paid for full flows | ok |
| Page Flows | User journeys and interactions | https://pageflows.com | Paid; some free recordings | ok |
| Screenlane → Page Flows | Mobile and web UI inspiration | https://screenlane.com | Now redirects to pageflows.com | redirect |
| UI Sources → Screens Design | Product interface references | https://www.uisources.com | Rebranded; now redirects to screensdesign.com | redirect |
| Httpster | Website inspiration | https://httpster.net | Free browsing | ok |
| One Page Love | Single-page websites | https://onepagelove.com | Free browsing | ok |
| Muzli | Design discovery and inspiration feed | https://muz.li | Free browsing | ok |
| Pinterest | Moodboards and visual references | https://www.pinterest.com | Scripted fetch times out; use a browser | timeout |
| CSS Design Awards | Awarded website design | https://www.cssdesignawards.com | Free browsing | ok |
| The FWA | Experimental digital experiences | https://thefwa.com | Free browsing | ok |

> For KhanaBook: begin with Mobbin, Refero, Dribbble, and Collect UI — they are
> more directly useful for an operational admin dashboard than marketing-site
> galleries alone.

## B — Fonts and typography

| Resource | Best use | URL | Access | Status |
|---|---|---|---|---|
| Google Fonts | Free web fonts | https://fonts.google.com | Keyless CSS API; catalog via gwfh.mranftl.com | ok |
| Fontshare | Contemporary free typefaces | https://www.fontshare.com | Keyless API and CSS endpoint | ok |
| Typewolf | Typography inspiration and pairing | https://www.typewolf.com | Free browsing | ok |
| FontPair | Font combinations | https://fontpair.co | Free browsing | ok |
| Pangram Pangram | Premium typeface discovery | https://pangrampangram.com | Rate-limits scripts; browser only | blocked |
| Velvetyne | Experimental and open-source typefaces | https://velvetyne.fr | Open-source fonts | ok |
| Adobe Fonts | Professional font library | https://fonts.adobe.com | Paid Creative Cloud licence | ok |
| WhatTheFont (MyFonts) | Identify fonts from images | https://www.myfonts.com/pages/whatthefont | Rate-limits scripts; browser only | blocked |
| FontBrief | Find fonts by personality | https://www.fontbrief.com | Free browsing | ok |
| Type Scale | Generate typographic scales | https://www.typescale.com | Origin returned 526 at probe time; use the calculator | down |
| Fonts In Use | See fonts in real designs | https://fontsinuse.com | Free browsing | ok |
| Fontjoy | Explore font pairings | https://fontjoy.com | Free browsing | ok |
| Typespiration | Typography and colour combinations | https://typespiration.com | Free browsing | ok |
| Modern Font Stacks | System-font stack recommendations | https://modernfontstacks.com | Free; copy-paste stacks | ok |

## C — Colour palettes and design tokens

| Resource | Best use | URL | Access | Status |
|---|---|---|---|---|
| Coolors | Palette generation | https://coolors.co | Blocks scripted access; browser only | blocked |
| Color Hunt | Curated colour palettes | https://colorhunt.co | Free browsing | ok |
| Khroma | AI-assisted colour discovery | https://www.khroma.co | Free; trains on your likes | ok |
| Happy Hues | Palettes applied to UI examples | https://www.happyhues.co | Free | ok |
| Colormind | AI palette generation | https://colormind.io | Keyless POST API | ok |
| BrandColors | Existing brand colours | https://brandcolors.net | Client-rendered; no API | ok |
| ColorSpace (mycolor.space) | Generate related palettes | https://mycolor.space | Free | ok |
| Adobe Color | Colour harmony and palettes | https://color.adobe.com | Free with Adobe account | ok |
| Leonardo | Contrast and accessible colour systems | https://leonardocolor.io | Web tool + npm (`@adobe/leonardo-contrast-colors`) | ok |
| Huemint | AI-generated design palettes | https://huemint.com | Free browsing | ok |
| Realtime Colors | Preview website colour systems | https://www.realtimecolors.com | Free | ok |
| Color.review | Colour and contrast evaluation | https://color.review | Free | ok |
| WebAIM Contrast Checker | Text contrast accessibility | https://webaim.org/resources/contrastchecker | Free | ok |
| Accessible Palette | Generate accessible colour scales | https://accessiblepalette.com | Free | ok |
| Tokens Studio | Manage design tokens in Figma | https://tokens.studio | Free tier; paid team features | ok |

> For KhanaBook: define background, surface, primary, border, text, success,
> warning and error tokens. Avoid creating a separate palette for every page.

## D — Mockups, wireframes and prototyping

| Resource | Best use | URL | Access | Status |
|---|---|---|---|---|
| Figma | UI design, components and prototypes | https://www.figma.com | Free tier; paid for teams | ok |
| Penpot | Open-source design and prototyping | https://penpot.app | Free / self-hostable | ok |
| Uizard | AI-assisted wireframes and mockups | https://uizard.io | Free tier; paid plans | ok |
| Visily | AI-assisted UI mockups | https://www.visily.ai | Free tier; paid plans | ok |
| Relume | Sitemap and website wireframes | https://www.relume.io | Now served from relume.ai | redirect |
| Framer | Interactive prototyping and publishing | https://www.framer.com | Free tier; paid sites | ok |
| v0 | Generate UI concepts and code | https://v0.app | Free credits; paid plans | ok |
| Shots | Screenshot presentation mockups | https://shots.so | Free; export limits | ok |
| Screely | Browser-style screenshot mockups | https://www.screely.com | Free | ok |
| Mockuuups Studio | Device mockups | https://mockuuups.studio | Free tier; paid | ok |
| Rotato | 3D device mockups | https://rotato.app | Paid (free trial) | ok |
| Excalidraw | Quick architecture and layout sketches | https://excalidraw.com | Free / open-source | ok |
| Unblast | Free PSD and device mockups | https://unblast.com | HTML gallery; no API | ok |
| MockupWorld | Free mockup templates | https://www.mockupworld.co | HTML gallery; no API | ok |
| Artboard Studio | Browser-based product mockups | https://artboard.studio | Free tier; paid | ok |
| LS Graphics | Premium device and scene mockups | https://www.ls.graphics | Paid; free samples | ok |

## E — UI components and design systems

| Resource | Best use | URL | Access | Status |
|---|---|---|---|---|
| shadcn/ui | Component design and interaction patterns | https://ui.shadcn.com | Free; React/Radix (pattern reference only here) | ok |
| Material Design 3 | Comprehensive design guidelines | https://m3.material.io | Free | ok |
| Carbon Design System | Enterprise UI patterns | https://carbondesignsystem.com | Free; IBM open source | ok |
| Ant Design | Admin dashboards, tables and forms | https://ant.design | Free | ok |
| Angular Material | Angular-native UI components | https://material.angular.dev | Free; matches KhanaBook's Angular stack | ok |
| PrimeNG | Angular components and themes | https://primeng.org | Docs moved to primeng.dev | redirect |
| Tailwind Plus | Production UI examples | https://tailwindcss.com/plus | Paid; login required | auth |
| Untitled UI | SaaS components and design system | https://www.untitledui.com | Free samples; paid library | ok |
| Radix UI | Accessible interaction patterns | https://www.radix-ui.com | Free; React primitives | ok |
| The Component Gallery | Cross-library component comparisons | https://component.gallery | Free | ok |
| Navbar Gallery | Navigation design examples | https://navbar.gallery | Free | ok |
| Footer.design | Footer design inspiration | https://www.footer.design | Free | ok |

> For your Angular project, Angular Material and PrimeNG are implementation
> references; study shadcn/ui for visual style even though it's React-based.

## F — Icons, illustrations and photography

| Resource | Best use | URL | Access | Status |
|---|---|---|---|---|
| Lucide | Consistent line icons | https://lucide.dev | Free; npm + SVG; MCP server `lucide` (search_icons, get_icon_svg, get_icon_metadata, list_icons, add_icon_to_project) | ok |
| Phosphor Icons | Multiple icon weights and styles | https://phosphoricons.com | Free; npm + SVG | ok |
| Iconoir | Open-source icons | https://iconoir.com | Free; npm + SVG | ok |
| Tabler Icons | Large line-icon collection | https://tabler.io/icons | Free; npm + SVG | ok |
| Heroicons | Clean interface icons | https://heroicons.com | Free; npm + SVG | ok |
| Iconify | Search across icon collections | https://icon-sets.iconify.design | Keyless icon API | ok |
| SVG Repo | SVG assets | https://www.svgrepo.com | Rate-limits scripts; browser only | blocked |
| Feather Icons | Simple open-source line icons | https://feathericons.com | Free; MIT licence, SVG download | ok |
| Remix Icon | Open-source neutral-style icon set | https://remixicon.com | Free; Apache licence, SVG + font | ok |
| Flaticon | Large icon library (flat, line, duotone) | https://www.flaticon.com | Blocks scripted access; free with attribution or paid | blocked |
| Icons8 | Icons, illustrations, photos and music | https://icons8.com | Free with attribution; paid for unrestricted use | ok |
| Noun Project | Curated icons and stock photos | https://thenounproject.com | Free with attribution; paid subscription | ok |
| unDraw | Illustrations | https://undraw.co | Free; recolorable SVG | ok |
| Storyset | Customisable illustrations | https://storyset.com | Free with attribution | ok |
| ManyPixels | Illustration library | https://www.manypixels.co | Free tier; attribution | ok |
| Unsplash | Photography | https://unsplash.com | Bot wall on direct fetch; API needs a key | auth |
| Pexels | Free stock photography | https://www.pexels.com | Blocks scripts; API needs a key | blocked |

> For KhanaBook: Lucide or Tabler for UI icons; avoid mixing icon families.

## G — Animation and interaction

| Resource | Best use | URL | Access | Status |
|---|---|---|---|---|
| Motion | React animation library | https://motion.dev | Free; npm motion | ok |
| GSAP | Advanced web animation | https://gsap.com | Free core; paid plugins | ok |
| LottieFiles | Lightweight vector animations | https://lottiefiles.com | Free tier; paid marketplace | ok |
| CodePen | Interactive frontend experiments | https://codepen.io | Blocks scripted access; browser only | blocked |
| Codrops | Experimental interface techniques | https://tympanus.net/codrops | Free | ok |
| Animista | CSS animation generator | https://animista.net | Free | ok |
| Hover.css | Hover-effect examples | https://ianlunn.github.io/Hover | Free; CSS library | ok |
| UIverse | Community UI components | https://uiverse.io | Free; copy-paste CSS | ok |
| Rive | Interactive real-time animations | https://rive.app | Free tier; paid for teams and export | ok |
| Jitter | Motion design for video and web | https://jitter.video | Free tier; paid plans | ok |
| Principle | Mac app for animated and interactive UI design | https://principleformac.com | Paid (one-time purchase) | ok |
| Loading.io | Loading spinners and CSS animations | https://loading.io | Free with attribution; paid plans | ok |
| Keyshape | 2D animation tool exporting SVG/CSS | https://www.keyshapeapp.com | Paid (macOS) | redirect |
| SVGator | No-code SVG animation creator | https://www.svgator.com | Free tier; paid plans | ok |

> For KhanaBook: favor short transitions, responsive hover states and meaningful
> feedback rather than decorative motion that slows down daily restaurant
> operations.

## H - 3D design and assets

| Resource | Best use | URL | Access | Status |
|---|---|---|---|---|
| Spline | Web-based 3D design and interactive scenes | https://spline.design | Free tier; paid plans | ok |
| Blender | Open-source 3D creation suite | https://www.blender.org | Free; GPL licence | ok |
| Sketchfab | 3D model marketplace and viewer | https://sketchfab.com | Free and paid models; upload and embed | ok |
| Vectary | Browser-based 3D design and AR | https://www.vectary.com | Free tier; paid plans | redirect |
| Poly.pizza | Free low-poly 3D models | https://poly.pizza | Free; attribution varies by model | redirect |
| CGTrader | 3D model marketplace | https://www.cgtrader.com | Free and paid models | redirect |
| Adobe Substance 3D | Professional 3D texturing and design | https://substance3d.adobe.com | Paid Adobe Creative Cloud plan | blocked |

> Use sparingly — heavy 3D can slow down daily restaurant operations.

## I - Frontend implementation and visual testing

| Resource | Best use | URL | Access | Status |
|---|---|---|---|---|
| Storybook | Develop components in isolation | https://storybook.js.org | Free; npm | ok |
| Playwright | Browser and end-to-end testing | https://playwright.dev | Free; npm | ok |
| Chromatic | Visual regression testing | https://www.chromatic.com | Free tier; paid plans | ok |
| axe DevTools | Accessibility checks | https://www.deque.com/axe/devtools | Free browser extension; paid CI | ok |
| Responsive Viewer | Inspect responsive layouts | https://responsiveviewer.org | Free browser extension | ok |
| Can I Use | Browser compatibility | https://caniuse.com | Free | ok |
| web.dev | Performance and accessibility guidance | https://web.dev | Free | ok |
| CSS-Tricks | CSS techniques and examples | https://css-tricks.com | Free | ok |
| MDN Web Docs | HTML, CSS and browser APIs | https://developer.mozilla.org | Free | ok |

## What each decision needs (read this before browsing)

A directory is not a plan. This table maps the decisions the KhanaBook redesign
actually has to make onto the resource that settles them, and onto the check
that will reject the result if it is wrong. Work top to bottom; do not start at
"inspiration" and browse until something looks nice.

| Decision | Primary reference | What to capture | Gate it must pass |
|---|---|---|---|
| Navigation model (rail + groups) | Mobbin, Refero — filter to POS / restaurant back-office | 3 rail solutions at 1440 and 1024 wide, with the group heading treatment | `check:breakpoints`; the 1025–1100 icon-rail state must still name its targets (already test-pinned) |
| Dashboard information order | Mobbin, Ant Design (dashboard templates), Carbon | Which 4 figures deserve the KPI band, and what sits below the fold | `WEB_ADMIN_PAGE_LAYOUT_PLAN.md` §2.1 — one KPI anatomy, no page-local stat cards |
| Table density and row anatomy | Ant Design, Carbon, Angular Material | Row height, header band, how a two-line cell and an action column coexist | Table design lives once in `styles.css`; a page may not restate it |
| Typeface | Fontshare, Typewolf, `designrefs_font_search`/`font_css` | One family, 5–6 sizes, tabular figures for money | `check:type` — 9-step scale, no new literals |
| Palette (navy/purple, warm surface) | Realtime Colors, Coolors, `color_scale` + `color_contrast` | Background / surface / border / text / brand / success / warning / danger | `check:colors` (channel twins must agree) + WCAG 4.5:1 for body text |
| Spacing and radius rhythm | Nothing to browse — it is already frozen | — | `check:spacing`, `check:tokens` |
| Icons | Lucide (matches the stroke language in `kb-icon`) | The 12 sidebar glyphs plus row actions | One family only; `icon.component.ts` is the single source |
| Loading / empty / error states | shadcn/ui, Carbon, Untitled UI | Empty-state copy and illustration weight | Skeleton anatomy rule in `WEB_ADMIN_PAGE_LAYOUT_PLAN.md` §1 rule 4 |
| Motion | Codrops, Animista — for reference only | Nothing above 300 ms, nothing decorative on a page an owner uses 200×/day | `check:motion` (150–300 ms band) |

### Skip these for KhanaBook

- **Marketing/landing galleries** (Land-book, Lapa Ninja, One Page Love, Httpster,
  SiteInspire) — the product is an operational tool, not a landing page.
- **3D, Rive, Lottie** — every one of them costs render time on a ₹8k Android
  tablet at the counter.
- **Tailwind Plus / Untitled UI source** — React/Tailwind components; useful as a
  picture, not as a dependency.

### Where the list you were given is stale

If you work from the other copy of this directory, three entries will waste a
click: `screenlane.com` now redirects to `pageflows.com`, `uisources.com` is now
`screensdesign.com`, and PrimeNG's docs live at `primeng.dev` (`primeng.org`
still serves, but the docs moved). This file already records all three.

## Recommended bookmark collection (start here)

1. Mobbin — product UX — https://mobbin.com
2. Dribbble — visual inspiration — https://dribbble.com
3. Refero — real product UI — https://refero.design
4. Collect UI — components — https://collectui.com
5. Fontshare — typography — https://www.fontshare.com
6. Typewolf — typography — https://www.typewolf.com
7. Coolors — colour palettes — https://coolors.co
8. Realtime Colors — colour previews — https://www.realtimecolors.com
9. Figma — mockups — https://www.figma.com
10. Angular Material — Angular components — https://material.angular.dev
11. Lucide — icons — https://lucide.dev
12. Playwright — testing — https://playwright.dev

## Suggested KhanaBook research order

1. Research real restaurant and SaaS interfaces in Mobbin and Refero.
2. Save sidebar, dashboard, report and settings examples from Dribbble and
   Collect UI.
3. Choose a readable typeface with Fontshare and Typewolf.
4. Refine the existing navy-and-purple palette with Coolors and Realtime
   Colors, then verify with `designrefs_color_contrast`.
5. Create a Figma prototype.
6. Implement it using existing Angular components and `--kb-*` tokens.
7. Validate with Playwright and accessibility checks.

## Maintenance

This directory mirrors the bundled data in `designrefs-mcp/resources.js` and
the tool implementations in `designrefs-mcp/index.js`. When a site's status
changes (paywall added, API key required, domain moved), update both the MCP
bundle and this doc in the same change. Never commit API keys or OAuth
secrets to either location.
