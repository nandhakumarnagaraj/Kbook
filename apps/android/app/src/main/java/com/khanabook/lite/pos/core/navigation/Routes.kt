package com.khanabook.lite.pos.core.navigation

/**
 * Single source of truth for every navigation destination.
 *
 * Route *patterns* (used in `composable(route = ...)` and `popUpTo`, with `{arg}`
 * placeholders) are named `*_PATTERN`. Concrete routes used to `navigate(...)`
 * are produced by the `fun ...(args)` builders below so arguments are always
 * rendered with the same encoding the graph expects.
 */
object Routes {

    // ── Auth shell ─────────────────────────────────────────────────────────────
    /** Scheme shared by every `navDeepLink`; keeps URIs in sync with the routes below. */
    const val DEEP_LINK_SCHEME = "khanabook://"

    fun deepLink(route: String): String = DEEP_LINK_SCHEME + route

    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val APP_LOCK = "app_lock"
    const val ROLE_ACCESS = "role_access"

    // ── Onboarding ─────────────────────────────────────────────────────────────
    const val DEVICE_SETUP = "device_setup"
    const val QUICK_START = "quick_start"

    // ── Main shell (bottom tabs) ───────────────────────────────────────────────
    const val MAIN_PATTERN =
        "main/{tab}?source={source}&highlightBillId={highlightBillId}&section={section}"

    fun main(tab: Int, source: String? = null, highlightBillId: Long? = null, section: String? = null): String =
        buildString {
            append("main/").append(tab)
            source?.let { append("?source=").append(it) }
            highlightBillId?.let { append(if (source != null) "&" else "?").append("highlightBillId=").append(it) }
            section?.let {
                val hasQuery = source != null || highlightBillId != null
                append(if (hasQuery) "&" else "?").append("section=").append(it)
            }
        }

    // ── Billing / orders ───────────────────────────────────────────────────────
    const val NEW_BILL_PATTERN =
        "new_bill?resumePayment={resumePayment}&draftBillId={draftBillId}&targetStep={targetStep}&quickMode={quickMode}"

    /**
     * [quickMode] lets the caller hand NewBill a mode it has already resolved, so the screen
     * can open correctly on its first frame. Null means "unknown" — the screen then resolves
     * it from its own profile read. Encoded as -1 unknown / 0 normal / 1 quick because a
     * plain boolean default cannot express "not known yet".
     */
    fun newBill(
        resumePayment: Boolean = false,
        draftBillId: Long? = null,
        targetStep: Int = 1,
        quickMode: Boolean? = null
    ): String {
        val encoded = when (quickMode) {
            null -> -1
            true -> 1
            false -> 0
        }
        return "new_bill?resumePayment=$resumePayment&draftBillId=${draftBillId ?: -1L}&targetStep=$targetStep&quickMode=$encoded"
    }

    const val ACTIVE_ORDERS = "active_orders"
    const val ACTIVE_ORDER_DETAIL_PATTERN = "active_order_detail/{billId}"

    /**
     * Public deep-link path for a single order. Deliberately `bill/{billId}` rather
     * than the route name, because links already printed on receipts use that path.
     */
    const val DEEP_LINK_BILL_PATTERN = "bill/{billId}"

    fun activeOrderDetail(billId: Long): String = "active_order_detail/$billId"

    const val SEARCH_BILL = "search_bill"

    // ── Tools ──────────────────────────────────────────────────────────────────
    const val CALL_CUSTOMER = "call_customer"
    const val REPRINT_KOT = "reprint_kot"
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATIONS_PREFERENCES = "notifications_preferences"

    const val OCR_SCANNER_PATTERN = "ocr_scanner/{source}"

    fun ocrScanner(source: String): String = "ocr_scanner/$source"

    const val OCR_SOURCE_MENU = "menu_config"
    const val OCR_SOURCE_BILLING = "billing"

    // ── Payments / compliance ──────────────────────────────────────────────────
    const val ONLINE_PAYMENTS_SETUP = "online_payments_setup"
    const val COMPLIANCE_DOCUMENTS = "compliance_documents"
}