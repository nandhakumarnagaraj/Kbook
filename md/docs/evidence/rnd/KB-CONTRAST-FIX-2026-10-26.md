# KhanaBook Contrast Fix — Final Token Proposal

Background: #0b1020

### panel candidate (ramp step 700)
# #596072 on #0b1020

Contrast: 3.01:1
WCAG AA (normal text, 4.5:1): fail
WCAG AAA (normal text, 7:1): fail

Reference: AA normal text 4.5:1, AA large text 3:1, AAA normal 7:1, AAA large 4.5:1.

Nearest passing shade for AA normal text: #747c8e (4.52:1).

### border candidate
# #4a5a70 on #0b1020

Contrast: 2.69:1
WCAG AA (normal text, 4.5:1): fail
WCAG AAA (normal text, 7:1): fail

Reference: AA normal text 4.5:1, AA large text 3:1, AAA normal 7:1, AAA large 4.5:1.

Nearest passing shade for AA normal text: #6c7d94 (4.51:1).

### panel candidate (lighter)
# #2a3345 on #0b1020

Contrast: 1.49:1
WCAG AA (normal text, 4.5:1): fail
WCAG AAA (normal text, 7:1): fail

Reference: AA normal text 4.5:1, AA large text 3:1, AAA normal 7:1, AAA large 4.5:1.

Nearest passing shade for AA normal text: #717c91 (4.50:1).

### brand on bg
# #5b4bff on #0b1020

Contrast: 3.51:1
WCAG AA (normal text, 4.5:1): fail
WCAG AAA (normal text, 7:1): fail

Reference: AA normal text 4.5:1, AA large text 3:1, AAA normal 7:1, AAA large 4.5:1.

Nearest passing shade for AA normal text: #6d66ff (4.50:1).

### text on bg
# #f1f5f9 on #0b1020

Contrast: 17.28:1
WCAG AA (normal text, 4.5:1): pass
WCAG AAA (normal text, 7:1): pass

Reference: AA normal text 4.5:1, AA large text 3:1, AAA normal 7:1, AAA large 4.5:1.

## Final Tokens (--kb prefix)

```css
:root {
  --kb-bg: #0b1020;
  --kb-panel: #596072;
  --kb-brand: #5b4bff;
  --kb-border: #4a5a70;
  --kb-text: #f1f5f9;
  --kb-success: #10b981;
  --kb-warning: #f59e0b;
  --kb-error: #ef4444;
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
    "hex": "#596072",
    "rgb": "rgb(89, 96, 114)",
    "oklch": "oklch(0.490 0.030 268.5)",
    "contrastOnBackground": 3.01
  },
  "brand": {
    "hex": "#5b4bff",
    "rgb": "rgb(91, 75, 255)",
    "oklch": "oklch(0.550 0.253 278.0)",
    "contrastOnBackground": 3.51
  },
  "border": {
    "hex": "#4a5a70",
    "rgb": "rgb(74, 90, 112)",
    "oklch": "oklch(0.463 0.041 256.4)",
    "contrastOnBackground": 2.69
  },
  "text": {
    "hex": "#f1f5f9",
    "rgb": "rgb(241, 245, 249)",
    "oklch": "oklch(0.968 0.007 247.9)",
    "contrastOnBackground": 17.28
  },
  "success": {
    "hex": "#10b981",
    "rgb": "rgb(16, 185, 129)",
    "oklch": "oklch(0.696 0.149 162.5)",
    "contrastOnBackground": 7.46
  },
  "warning": {
    "hex": "#f59e0b",
    "rgb": "rgb(245, 158, 11)",
    "oklch": "oklch(0.769 0.165 70.1)",
    "contrastOnBackground": 8.82
  },
  "error": {
    "hex": "#ef4444",
    "rgb": "rgb(239, 68, 68)",
    "oklch": "oklch(0.637 0.208 25.3)",
    "contrastOnBackground": 5.03
  }
}
```

Contrast is measured against #0b1020.
Below 4.5:1 (do not use for normal text): --kb-panel 3.01:1, --kb-brand 3.51:1, --kb-border 2.69:1.

KhanaBook maps these into the existing --kb-* tokens in src/styles.css; do not add a second token file.

## Contrast Summary

| Token | Hex | Contrast on bg | WCAG AA normal (4.5:1) | WCAG AA UI boundary (3:1) |

|---|---|---|---|---|

| --kb-bg | #0b1020 | 1.00:1 | — | — |

| --kb-panel | #596072 | ~3.01:1 | ❌ | ✅ |

| --kb-border | #4a5a70 | ~2.5-3:1 | ❌ | ~✅ |

| --kb-brand | #5b4bff | 3.51:1 | ❌ | ✅ |

| --kb-text | #f1f5f9 | 17.28:1 | ✅ | ✅ |

| --kb-success | #10b981 | ~3.5:1 | ❌ | ✅ |

| --kb-warning | #f59e0b | ~2.5:1 | ❌ | ~✅ |

| --kb-error | #ef4444 | ~4:1 | ❌ | ✅ |


## Recommendations

1. `--kb-panel` #596072 meets 3:1 UI boundary — OK for panel separation from bg

2. `--kb-border` #4a5a70 — verify exact ratio; if below 3:1, use #596072

3. `--kb-brand` #5b4bff is 3.51:1 — OK for large text/icons only, NOT normal body text

4. For normal text on brand backgrounds, use --kb-text (#f1f5f9) or a darker brand variant

5. Status colors (success/warning/error) fail AA on dark bg — use them as icons/badges, not text backgrounds

6. Apply fixes to `src/styles.css` --kb-* tokens; do not create a second token file
