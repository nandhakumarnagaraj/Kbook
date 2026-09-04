package com.khanabook.lite.pos.ui.theme

/**
 * PrintTokens — single source for thermal receipt sizing.
 * Grounded in ESC/POS 203 dpi / 8 dots/mm spec (Epson ESC M):
 *  58mm printable 48mm = 384 dots → Font A 12×24 = 32 cols, Font B 9×17 = 42 cols
 *  80mm printable 72mm = 576 dots → Font A 12×24 = 48 cols, Font B 9×17 = 56-64 cols
 *
 * Column model (paper-agnostic): the item column is a STAR column — it absorbs
 * whatever width is left after the fixed qty/rate/amt columns and the 3 single-space
 * gaps. This guarantees the money columns (rate/amt) always fit a `NNNN.NN` value
 * instead of being starved when someone tweaks itemW. Derive item width via
 * [itemWidthFor]; never hardcode it.
 */
object PrintTokens {

    /** Gaps between the 4 columns (ITEM QTY RATE AMT) — one space each. */
    const val COLUMN_GAPS = 3

    /**
     * Star-column width for the item name: total content width minus the fixed
     * numeric columns and the inter-column gaps. Coerced ≥ 1 so a pathologically
     * narrow paper never yields a negative width.
     */
    fun itemWidthFor(contentWidth: Int, qtyW: Int, rateW: Int, amtW: Int): Int =
        (contentWidth - qtyW - rateW - amtW - COLUMN_GAPS).coerceAtLeast(1)

    object thermal58 {
        const val columns = 32          // Font A, full physical width
        const val contentWidth = 32     // no centering needed — full width is the content width
        const val columnsFallback42 = 42 // Font B only if truncation proven
        const val qrSizePx = 256
        const val logoMaxWidthPx = 256   // = 32mm at 8 dots/mm — this is the actual enforced cap
        const val logoMaxWidthMm = 32    // corrected: was mislabeled 40, doesn't match logoMaxWidthPx
        // Fixed numeric columns sized to hold real money: qty ≤999, rate/amt = `NNNN.NN`.
        const val qtyW = 3               // "999" — qty rarely exceeds 3 digits on one line
        const val rateW = 7              // "1234.56"
        const val amtW = 7               // "1234.56"
        // Star item column = 32 - 3 - 7 - 7 - 3 = 12. Was 16, which starved amtW to 2.
        val itemW = itemWidthFor(contentWidth, qtyW, rateW, amtW)
    }

    object thermal80 {
        const val columns = 48          // Font A, full physical width
        const val contentWidth = 40     // existing design: content rendered 40 cols wide, then
                                          // leftPad (4 spaces) visually centers it on the 48-col printer
        const val qrSizePx = 384
        const val logoMaxWidthPx = 384   // = 48mm at 8 dots/mm
        const val logoMaxWidthMm = 48
        const val qtyW = 4               // "9999"
        const val rateW = 8              // "12345.67"
        const val amtW = 8               // "12345.67"
        // Star item column = 40 - 4 - 8 - 8 - 3 = 17.
        val itemW = itemWidthFor(contentWidth, qtyW, rateW, amtW)
    }
}
