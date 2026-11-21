package com.khanabook.lite.pos.core.theme

import androidx.compose.ui.graphics.Color

// Core brand palette — Dark Brown + Gold + Purple (from logo)
val DarkBrown1 = Color(0xFF1A0A0A)
val DarkBrown2 = Color(0xFF2D1010)
val PrimaryGold = Color(0xFFC8960C)
val LightGold = Color(0xFFF0C040)
val TextLight = Color(0xFFF5E6C8)
val TextGold = Color(0xFFD4A843)
val TextMuted = Color(0xFF8D6E63)
val CardBG = Color(0xCC78461E)
val BorderGold = Color(0x4DC8963C)
val ParchmentBG = Color(0xFFE6D5B8)

// Brand purple — from logo inner chevrons
val BrandPurple = Color(0xFF6835A8)
val BrandPurpleDim = Color(0xFF3D1F66)

// Brown scale
val Brown500 = Color(0xFF5D4037)
val DarkBrownSheet = Color(0xFF1C1008)
val BrownSelected = Color(0xFF42210B)
val RichEspresso = Color(0xFF1A0828)  // dark purple-black — gradient tail matches logo

// Floating notice pill — white surface, dark text.
// These are notice-only tokens: the dark-UI semantic colours above are not all
// legible on white (WarningYellow is 1.79:1 and PrimaryGold 2.68:1), so the pill
// gets its own shades instead of changing tokens other screens already use.
val NoticeSurface = Color(0xFFFFFFFF)
val NoticeText = Color(0xFF201A17)          // 17.19:1 on NoticeSurface
val NoticeTextSecondary = Color(0xFF4A4038) // 10.09:1 on NoticeSurface
val NoticeSuccess = Color(0xFF2E7D32)       // 5.13:1 on NoticeSurface
val NoticeError = Color(0xFFC62828)         // 5.62:1 on NoticeSurface
val NoticeWarning = Color(0xFFB45309)       // 5.02:1 on NoticeSurface
val NoticeInfo = Color(0xFF6D28D9)          // 7.10:1 on NoticeSurface
val NoticeClose = Color(0xFF5C5248)         // 7.62:1 on NoticeSurface

// Compact toast — near-black surface, WhatsApp-style. Contrast measured on
// NoticeDarkSurface (#1B1B1B); every value below clears 4.5:1.
val NoticeDarkSurface = Color(0xFF1B1B1B)
val NoticeDarkText = Color(0xFFFFFFFF)          // 17.22:1
val NoticeDarkTextSecondary = Color(0xFFD6D6D6) // 11.85:1
val NoticeDarkSuccess = Color(0xFF6BDB8A)       // 9.94:1
val NoticeDarkError = Color(0xFFFF6B6B)         // 6.21:1
val NoticeDarkWarning = Color(0xFFFFC44D)       // 10.87:1
val NoticeDarkInfo = Color(0xFF8FC7FF)          // 9.66:1
val NoticeDarkClose = Color(0xFFBDBDBD)         // 9.17:1

// Semantic
val VegGreen = Color(0xFF4CAF50)
val NonVegRed = Color(0xFFE53935)
val SuccessGreen = Color(0xFF388E3C)
val DangerRed = Color(0xFFC62828)
val ErrorPink = Color(0xFFFF6B6B)
val WarningYellow = Color(0xFFFFB300)
val GreenReportBg = Color(0xFF1E3A1E)
val Green800 = Color(0xFF2E7D32)

// Payment-type badges

val GoogleRed = Color(0xFFDB4437)
val WhatsAppGreen = Color(0xFF25D366)
val SmsBlue = Color(0xFF1976D2)

// Part-payment trio — one hue per tender combination so a report row is
// identifiable by colour alone, without reading the label. Hue-spread (teal /
// orange / sky) and light enough to stay legible on the warm card surfaces.
val PartCashUpiTeal = Color(0xFF00A394)
val PartCashPosOrange = Color(0xFFEF6C00)
val PartUpiPosSky = Color(0xFF4FA3E3)

// Orders screen + Order Details modal — premium burgundy / champagne gold.
// Scoped to these two surfaces so the rest of the app keeps its existing palette.
val OrdersBg = Color(0xFF170809)
val OrdersCard = Color(0xFF260F10)
val OrdersGold = Color(0xFFD9A514)
val OrdersBrightGold = Color(0xFFFFD166)
val OrdersTextPrimary = Color(0xFFF1DFAF)
val OrdersTextSecondary = Color(0xFFBBAE9D)
val OrdersDivider = Color(0xFF493025)
// Orders table surfaces: the header sits a shade below the row cards so the band reads as a
// header, while each row lifts off OrdersBg by fill alone (no outline).
val OrdersHeaderBand = Color(0xFF1F0C0D)
val OrdersRowCard = Color(0xFF2A1213)
// Warm bronze for the dialog's payment-mode action: distinct from the gold Print button,
// the green Share and the red Cancel, while staying in the burgundy/gold family.
val OrdersBronze = Color(0xFF8A4B1F)
val OrdersShareGreen = Color(0xFF238B57)
val OrdersShareGreenPressed = Color(0xFF176B42)
