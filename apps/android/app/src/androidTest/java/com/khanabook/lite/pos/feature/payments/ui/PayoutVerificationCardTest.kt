package com.khanabook.lite.pos.feature.payments.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khanabook.lite.pos.core.theme.KhanaBookLiteTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two actions a merchant needs before Easebuzz can verify them.
 *
 * The payments card is the only place the owner sees KYC status outside the onboarding flow, and
 * it used to offer just one button that jumped to the compliance screen. Neither "where do I
 * upload?" nor "Easebuzz never sent me a portal link" could be answered from there: the link has
 * to be minted by an explicit access-key request. These tests pin both actions and their states.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PayoutVerificationCardTest {

    // The instrumentation runner swaps in HiltTestApplication, which refuses to build a component
    // for any activity unless this rule ran first — even for a test that only needs a composable.
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun show(
        kycLifecycle: String = "PENDING_KYC",
        hasBusinessProof: Boolean = false,
        isSubmitting: Boolean = false,
        onOpenComplianceDocs: () -> Unit = {},
        onGetPortalLink: () -> Unit = {}
    ) {
        compose.setContent {
            KhanaBookLiteTheme {
                PayoutVerificationCard(
                    kycLifecycle = kycLifecycle,
                    hasBusinessProof = hasBusinessProof,
                    isSubmitting = isSubmitting,
                    onOpenComplianceDocs = onOpenComplianceDocs,
                    onGetPortalLink = onGetPortalLink
                )
            }
        }
    }

    @Test
    fun beforeAnyProofIsSubmittedBothKycActionsAreOffered() {
        show()

        // Uploading documents starts here; the compliance screen is where the proofs live.
        compose.onNodeWithText("Upload KYC Documents →").assertIsDisplayed()
        // Requesting the portal link is what makes an upload possible in the first place.
        compose.onNodeWithText("Get KYC Portal Link").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun tappingGetKycPortalLinkAsksTheServerForOne() {
        var requests = 0
        show(onGetPortalLink = { requests += 1 })

        compose.onNodeWithText("Get KYC Portal Link").performClick()

        assertEquals(1, requests)
    }

    @Test
    fun theLinkButtonIsDisabledWhileARequestIsInFlight() {
        show(isSubmitting = true)

        compose.onNodeWithText("Get KYC Portal Link").assertIsNotEnabled()
    }

    @Test
    fun onceAProofIsUploadedTheCardOffersToShowIt() {
        show(hasBusinessProof = true)

        compose.onNodeWithText("View Uploaded Proofs").assertIsDisplayed()
    }

    @Test
    fun cpvPendingReplacesTheUploadCopy() {
        show(kycLifecycle = "CPV_PENDING")

        compose.onNodeWithText("Complete Verification →").assertIsDisplayed()
        compose.onNodeWithText("Upload KYC Documents →").assertDoesNotExist()
    }
}
