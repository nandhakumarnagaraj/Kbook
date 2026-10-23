package com.khanabook.lite.pos.feature.payments.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khanabook.lite.pos.core.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The KYC screen's two endings, asserted directly.
 *
 * A registered sub-merchant can arrive here with a hosted portal link or with none at all, and
 * before this the second case was indistinguishable from the first: the screen announced that
 * Easebuzz was reviewing documents that had never been sent, and its only button ("Refresh
 * Status") re-read the stored row and could never issue the missing link. These tests pin the
 * copy, the retry action, and the fact that the action is reachable and enabled.
 */
@RunWith(AndroidJUnit4::class)
class KycStatusContentTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val portalUrl = "https://kyc.easebuzz.in/session/abc?access_key=k1"

    private fun status(kycUrl: String = "", state: String = "PENDING_KYC") =
        EasebuzzOnboardingStatusResponse(
            status = state,
            subMerchantId = "S280498XHD5",
            hasSubMerchant = true,
            kycUrl = kycUrl
        )

    private fun show(
        status: EasebuzzOnboardingStatusResponse?,
        isSubmitting: Boolean = false,
        onRefreshLink: () -> Unit = {},
        onRefreshStatus: () -> Unit = {}
    ) {
        compose.setContent {
            KhanaBookLiteTheme {
                KycStatusContent(
                    status = status,
                    isSubmitting = isSubmitting,
                    onRefreshLink = onRefreshLink,
                    onRefreshStatus = onRefreshStatus,
                    onOpenComplianceDocs = null,
                    spacing = KhanaBookTheme.spacing
                )
            }
        }
    }

    @Test
    fun withoutAPortalLinkTheScreenSaysNothingWasSubmitted() {
        show(status(kycUrl = ""))

        compose.onNodeWithText("KYC Documents Not Submitted").assertIsDisplayed()
        // Requesting a link is the only action that can move a merchant out of this state.
        compose.onNodeWithText("Get KYC Portal Link").assertExists()
        // The old claim: a review that Easebuzz never started, because no document reached it.
        compose.onNodeWithText("KYC Verification Pending").assertDoesNotExist()
    }

    @Test
    fun withoutAPortalLinkThePortalButtonIsNotOffered() {
        show(status(kycUrl = ""))

        compose.onNodeWithText("Open Easebuzz KYC Portal (Upload Proofs) ↗").assertDoesNotExist()
    }

    @Test
    fun tappingGetKycPortalLinkAsksTheServerForOne() {
        var requested = 0
        show(status(kycUrl = ""), onRefreshLink = { requested++ })

        compose.onNodeWithText("Get KYC Portal Link").performScrollTo().performClick()

        assertEquals("the retry button must call the access-key endpoint", 1, requested)
    }

    @Test
    fun theRetryButtonIsDisabledWhileARequestIsInFlight() {
        var requested = 0
        show(status(kycUrl = ""), isSubmitting = true, onRefreshLink = { requested++ })

        val retry = compose.onNodeWithText("Get KYC Portal Link").performScrollTo()
        retry.assertIsNotEnabled()
        retry.performClick()

        assertEquals("a second tap while submitting must not fire another request", 0, requested)
    }

    @Test
    fun withAPortalLinkTheScreenOffersThePortalAndNoRetry() {
        show(status(kycUrl = portalUrl))

        compose.onNodeWithText("KYC Verification Pending").assertIsDisplayed()
        compose.onNodeWithText("Open Easebuzz KYC Portal (Upload Proofs) ↗").assertExists()
        compose.onNodeWithText("Get KYC Portal Link").assertDoesNotExist()
    }
}
