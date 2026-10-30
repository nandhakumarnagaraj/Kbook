# KhanaBook Redesign Research — MCP Results

Generated from live MCP tool calls: designrefs, lucide, icon, playwright.

## 1. Design Reference Search — 'dashboard'

# 12 matches for "dashboard"

- **Mobbin** - https://mobbin.com
  Real app screens and UX flows. Access: Free browsing; sign-in for saved screens and full flows
- **Dribbble** - https://dribbble.com
  UI concepts and visual ideas. Access: Bot challenge on site; API requires OAuth credentials
  Status: blocked
- **Collect UI** - https://collectui.com
  Individual UI components. Access: Free browsing
- **Refero** - https://refero.design
  Real product and website UI references. Access: Free browsing; paid for full flows
- **Page Flows** - https://pageflows.com
  User journeys and interactions. Access: Paid; some free recordings
- **shadcn/ui** - https://ui.shadcn.com
  Component design and interaction patterns. Access: Free; React/Radix (pattern reference only here)

(showing 6 of 12)

## 2. Font Research

### Google Fonts — Inter:
# 2 families for "inter" (Google Fonts)

1. Inter - Google - sans-serif - 0-900 +italic, 7 subsets
   https://fonts.google.com/specimen/Inter
2. Inter Tight - Google - sans-serif - 0-900 +italic, 7 subsets
   https://fonts.google.com/specimen/Inter+Tight

Next: font_get for the full style list, or font_css for a ready @font-face block.

### Fontshare — sans-serif:
# 8 families for "sans" (Fontshare)

1. Bespoke Sans - Fontshare - Sans - 300-800 +italic, 5 weights
   https://www.fontshare.com/fonts/bespoke-sans
2. Fira Sans - Fontshare - Sans - 100-900 +italic, 9 weights
   https://www.fontshare.com/fonts/fira-sans
3. General Sans - Fontshare - Sans - 200-700 +italic, 6 weights
   https://www.fontshare.com/fonts/general-sans

(showing 3 of 8; raise limit or narrow the query)

Next: font_get for the full style list, or font_css for a ready @font-face block.

### Inter details:
# Inter

Source: Google Fonts
Category: sans-serif
Weights: 100, 200, 300, 400, 500, 600, 700, 800, 900 (italic: )
Subsets: cyrillic, cyrillic-ext, greek, greek-ext, latin, latin-ext, vietnamese
Version: v20 (updated 2025-09-10)
Popularity rank: 6 of 1959

Page: https://fonts.google.com/specimen/Inter
CSS: https://fonts.googleapis.com/css2?family=Inter:wght@400;700&display=swap
Self-host zip: https://gwfh.mranftl.com/api/fonts/inter?download=zip&subsets=latin&variants=regular,700&formats=woff2

Direct WOFF2 (Google CDN, latin):
  400: https://fonts.gstatic.com/s/inter/v20/UcCO3FwrK3iLTeHuS_nVMrMxCp50SjIw2boKoduKmMEVuLyfAZ9hiA.woff2
  700: https://fonts.gstatic.com/s/inter/v20/UcCO3FwrK3iLTeHuS_nVMrMxCp50SjIw2boKoduKmMEVuFuYAZ9hiA.woff2

Licence: open source (OFL/Apache/UFL); confirm on the family page before redistribution.
Next: font_css for a paste-ready @font-face block.

### Inter CSS (400/500/700):
# Inter - 400/500/700 - normal

Google Fonts stylesheet. Paste the link tags in index.html:

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;700&display=swap" rel="stylesheet">
```

CSS URL:
https://fonts.googleapis.com/css2?family=Inter:wght@400;500;700&display=swap

```css
/* cyrillic-ext */
@font-face {
  font-family: 'Inter';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url(https://fonts.gstatic.com/s/inter/v20/UcC73FwrK3iLTeHuS_nVMrMxCp50SjIa2JL7SUc.woff2) format('woff2');
  unicode-range: U+0460-052F, U+1C80-1C8A, U+20B4, U+2DE0-2DFF, U+A640-A69F, U+FE2E-FE2F;
}
/* cyrillic */
@font-face {
  font-family: 'Inter';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url(https://fonts.gstatic.com/s/inter/v20/UcC73FwrK3iLTeHuS_nVMrMxCp50SjIa0ZL7SUc.woff2) format('woff2');
  unicode-range: U+0301, U+0400-045F, U+0490-0491, U+04B0-04B1, U+2116;
}
/* greek-ext */
@font-face {
  font-family: 'Inter';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url(https://fonts.gstatic.com/s/inter/v20/UcC73FwrK3iLTeHuS_nVMrMxCp50SjIa2ZL7SUc.woff2) format('woff2');
  unicode-range: U+1F00-1FFF;
}
/* greek */
@font-face {
  font-family: 'Inter';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url(https://fonts.gstatic.com/s/inter/v20/UcC73FwrK3i

## 3. Colour Palette Research

### Colormind palette (locked: #0b1020 navy):
# Colormind palette (model: ui)

Locked: #0b1020

1. #191c27  rgb(25, 28, 39)  oklch(0.229 0.022 272.7)  contrast on white 16.98:1, on black 1.24:1
2. #6e6364  rgb(110, 99, 100)  oklch(0.510 0.014 11.4)  contrast on white 5.78:1, on black 3.63:1
3. #727684  rgb(114, 118, 132)  oklch(0.567 0.022 273.0)  contrast on white 4.53:1, on black 4.64:1
4. #9499a8  rgb(148, 153, 168)  oklch(0.684 0.023 271.1)  contrast on white 2.85:1, on black 7.38:1
5. #e8e8e5  rgb(232, 232, 229)  oklch(0.930 0.004 106.2)  contrast on white 1.23:1, on black 17.11:1

Source: colormind.io. Re-run for a different set; pass input colours to lock them.

### Brand scale #5b4bff on #ffffff:
# Scale for #5b4bff on #ffffff

Base contrast: 5.39:1
Side: darker (auto-selected from background luminance)

| name | target | hex | actual | meets target |
| --- | --- | --- | --- | --- |
| --kb-brand-r300 | 3.00:1 | #8887ff | 3.02:1 | yes |
| --kb-brand-r450 | 4.50:1 | #685eff | 4.53:1 | yes |
| --kb-brand-r700 | 7.00:1 | #4e33ea | 7.03:1 | yes |

```css
:root {
  --kb-brand-r300: #8887ff;
  --kb-brand-r450: #685eff;
  --kb-brand-r700: #4e33ea;
}
```

WCAG 2.2: 4.5:1 for normal text, 3:1 for large text (>=24px, or >=18.66px bold) and UI boundaries.

### Contrast #8a86a8 on #ffffff:
# #8a86a8 on #ffffff

Contrast: 3.47:1
WCAG AA (normal text, 4.5:1): fail
WCAG AAA (normal text, 7:1): fail

Reference: AA normal text 4.5:1, AA large text 3:1, AAA normal 7:1, AAA large 4.5:1.

Nearest passing shade for AA normal text: #777394 (4.50:1).

### Token export (--kb prefix):
```css
:root {
  --kb-bg: #0b1020;
  --kb-panel: #161b2a;
  --kb-brand: #5b4bff;
  --kb-border: #334155;
  --kb-text: #f1f5f9;
}
```

```json
{
  "bg": {
    "hex": "#0b1020",
    "rgb": "rgb(11, 16, 32)",
    "oklch": "oklch(0.177 0.034 269.6)",
    "contrastOnBackground": 1
  },
  "panel": {
    "hex": "#161b2a",
    "rgb": "rgb(22, 27, 42)",
    "oklch": "oklch(0.225 0.030 269.7)",
    "contrastOnBackground": 1.1
  },
  "brand": {
    "hex": "#5b4bff",
    "rgb": "rgb(91, 75, 255)",
    "oklch": "oklch(0.550 0.253 278.0)",
    "contrastOnBackground": 3.51
  },
  "border": {
    "hex": "#334155",
    "rgb": "rgb(51, 65, 85)",
    "oklch": "oklch(0.372 0.039 257.3)",
    "contrastOnBackground": 1.83
  },
  "text": {
    "hex": "#f1f5f9",
    "rgb": "rgb(241, 245, 249)",
    "oklch": "oklch(0.968 0.007 247.9)",
    "contrastOnBackground": 17.28
  }
}
```

Contrast is measured against #0b1020.
Below 4.5:1 (do not use for normal text): --kb-panel 1.10:1, --kb-brand 3.51:1, --kb-border 1.83:1.

KhanaBook maps these into the existing --kb-* tokens in src/styles.css; do not add a second token file.

## 4. Icon Research — Lucide

- `menu` — exists: true — tags: 
    "bars",
    "navigation",
    "hamburger",
    "options"
  
- `sidebar` — exists: true — tags: 
- `sidebar-open` — exists: true — tags: 
- `sidebar-close` — exists: true — tags: 
- `layout-dashboard` — exists: true — tags: 
    "masonry",
    "brick"
  
- `settings` — exists: true — tags: 
    "cog",
    "edit",
    "gear",
    "preferences"
  
- `user` — exists: true — tags: 
    "person",
    "account",
    "contact"
  
- `shopping-cart` — exists: true — tags: 
    "trolley",
    "cart",
    "basket",
    "e commerce",
    "store",
    "purchase",
    "products",
    "items",
    "ingredients"
  
- `bell` — exists: true — tags: 
    "alarm",
    "notification",
    "sound",
    "reminder"
  
- `log-out` — exists: true — tags: 
    "sign out",
    "arrow",
    "exit",
    "auth"
  

## 5. Icon Comparison — icon-mcp (Feather/Tabler/Bootstrap)

{
  "query": "menu",
  "results": [
    {
      "item": {
        "name": "menu",
        "library": "feather",
        "tags": [
          "menu",
          "feather",
          "simple",
          "minimal",
          "hamburger",
          "navigation",
          "list"
        ],
        "path": "node_modules\\feather-icons\\dist\\icons\\menu.svg",
        "svg": "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"feather feather-menu\"><line x1=\"3\" y1=\"12\" x2=\"21\" y2=\"12\"></line><line x1=\"3\" y1=\"6\" x2=\"21\" y2=\"6\"></line><line x1=\"3\" y1=\"18\" x2=\"21\" y2=\"18\"></line></svg>",
        "style": "regular",
        "categories": [
          "navigation"
        ],
        "size": "24x24",
        "source": "https://github.com/feathericons/feather",
        "updatedAt": "2026-10-26T10:10:55.789Z"
      },
      "refIndex": 156,
      "matches": [
        {
          "indices": [
            [
              0,
              3
            ]
          ],
          "value": "menu",
          "key": "name"
        },
        {
          "indices": [
            [
              0,
              3
            ]
          ],
          "value": "menu",
          "key": "tags",
          "refIndex": 0
        }
      ],
      "score": 8.0180257554823e-13
    },
    {
      "item": {
        "name": "menu",
        "library": "tabler-icons",
        "tags": [
          "menu",
          "tabler",
          "outline",
          "stroke"
        ],
        "path": "node_modules\\@tabler\\icons\\icons\\menu.svg",
        "svg": "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"icon icon-tabler icon-tabler-menu\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" stroke-width=\"2\" stroke=\"currentColor\" fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n  <path stroke=\"none\" d=\"M0 0h24v24H0z\" fill=\"none\"/>\n  <path d=\"M4 8l16 0\" />\n  <path d=\"M4 16l16 0\" />\n</svg>\n\n\n",
        "style": "regular",
        "categories": [
          "navigation"
        ],
        "size": "24x24",
        "source": "https://github.com/tabler/tabler-icons",
        "updatedAt": "2026-10-26T10:10:57.960Z"
      },
      "refIndex": 3221,
      "matches": [
        {
          "indices": [
            [
              0,
              3
            ]
          ],
          "value": "menu",
          "key": "name"
        },
        {
          "indices": [
            [
              0,
              3
            ]
          ],
          "value": "menu",
          "key": "tags",
          "refIndex": 0
        }
      ],
      "score": 8.0180257554823e-13
    },
    {
      "item": {
        "name": "menu-app-fill",
        "library": "bootstrap-icons",
        "tags": [
          "menu-app-fill",
          "menu",
          "app",
          "fill",
          "bootstrap",
          "twbs",
          "bi",
          "application",
          "software",
          "program",
          "hamburger",
          "navigation",
          "options"
        ],
        "path": "node_modules\\bootstrap-icons\\icons\\menu-app-fill.svg",
        "svg": "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" fill=\"currentColor\" class=\"bi bi-menu-app-fill\" viewBox=\"0 0 16 16\">\n  <path d=\"M0 1.5A1.5 1.5 0 0 1 1.5 0h2A1.5 1.5 0 0 1 5 1.5v2A1.5 1.5 0 0 1 3.5 5h-2A1.5 1.5 0 0 1 0 3.5zM0 8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2zm1 3v2a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-2zm14-1V8a1 1 0 0 0-1-1H2a1 1 0 0 0-1 1v2zM2 8.5a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5m0 4a.5.5 0 0 1 .5-.5h6a.5.5 0 0 1 0 1h-6a.5.5 0 0 1-.5-.5\"/>\n</svg>",
        "style": "regular",
        "categories": [
          "navigation"
        ],
        "size": "16x16",
        "source": "https://github.com/twbs/icons",
        "updatedAt": "2026-10-26T10:10:56.712Z"
      },
      "refIndex": 1360,
      "matches": [
        {
          "indices": [
            [
              0,
              3
            ]
          ],
          "value": "menu-app-fill",
          "key": "name"
        },
        {
          "indices": [
            [
              0,
              3
            ]
          ],
          "value": "menu-app-fill",
          "key": "tags",
          "refIndex": 0
        },
        {
          "indices": [
            [
              0,
              3
            ]
          ],
          "value": "menu",
          "key": "tags",
          "refIndex": 1
        }
      ],
      "score": 5.025046347534381e-8
    }
  ],
  "totalResults": 12,
  "searchType": "fuzzy",
  "executionTime": 94,
  "libraries": [
    "bootstrap-icons",
    "feather",
    "octicons",
    "tabler-icons"
  ],
  "fuseOptions": {
    "fuzzy": true,
    "threshold": 0.3,
    "limit": 3,
    "includeScore": true,
    "includeMatches": true,
    "useExtendedSearch": false,
    "ignoreLocation": false,
    "minMatchCharLength": 2,
    "isCaseSensitive": false
  }
}

### Available libraries:
{
  "libraries": [
    "bootstrap-icons",
    "feather",
    "octicons",
    "tabler-icons"
  ],
  "count": 4
}
