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
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val APP_LOCK = "app_lock"
    const val ROLE_ACCESS = "role_access"

    // ── Onboarding ─────────────────────────────────────────────────────────────
    const val INITIAL_SYNC = "initial_sync"
    const val QUICK_START = "quick_start"
    const val BACKGROUND_RELIABILITY = "background_reliability"

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
        "new_bill?resumePayment={resumePayment}&draftBillId={draftBillId}&targetStep={targetStep}"

    fun newBill(resumePayment: Boolean = false, draftBillId: Long? = null, targetStep: Int = 1): String =
        "new_bill?resumePayment=$resumePayment&draftBillId=${draftBillId ?: -1L}&targetStep=$targetStep"

    const val ACTIVE_ORDERS = "active_orders"
    const val ACTIVE_ORDER_DETAIL_PATTERN = "active_order_detail/{billId}"

    fun activeOrderDetail(billId: Long): String = "active_order_detail/$billId"

    const val ORDER_STATUS = "order_status"
    const val SEARCH_BILL = "search_bill"

    // ── Tools ──────────────────────────────────────────────────────────────────
    const val CALL_CUSTOMER = "call_customer"
    const val REPRINT_KDS = "reprint_kds"
    const val NOTIFICATIONS = "notifications"
    const val STAFF_PERMISSIONS = "staff_permissions"

    const val OCR_SCANNER_PATTERN = "ocr_scanner/{source}"

    fun ocrScanner(source: String): String = "ocr_scanner/$source"

    const val OCR_SOURCE_MENU = "menu_config"
    const val OCR_SOURCE_BILLING = "billing"

    // ── Payments / compliance ──────────────────────────────────────────────────
    const val EASEBUZZ_ONBOARDING = "easebuzz_onboarding"
    const val COMPLIANCE_DOCUMENTS = "compliance_documents"
    const val MERCHANT_AGREEMENT = "merchant_agreement"
    const val PAYMENT_LINK_PATTERN = "payment_link?restaurantId={restaurantId}"

    fun paymentLink(restaurantId: Long = 0L): String = "payment_link?restaurantId=$restaurantId"
}