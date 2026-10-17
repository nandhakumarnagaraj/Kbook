package com.khanabook.lite.pos.domain.manager
import com.khanabook.lite.pos.feature.payments.domain.PaymentReturnManager

import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PaymentReturnManagerTest {

    @Before
    fun setUp() {
        PaymentReturnManager.clearLatestEvent()
    }

    @Test
    fun `handleIntent parses success return with txnid`() {
        val intent = mockk<Intent>()
        val uri = mockk<Uri>()
        every { intent.data } returns uri
        every { uri.scheme } returns "khanabook"
        every { uri.host } returns "payment"
        every { uri.path } returns "/success"
        every { uri.getQueryParameter("txnid") } returns "TXN_SUCCESS_001"
        every { uri.getQueryParameter("txnId") } returns null

        PaymentReturnManager.handleIntent(intent)

        val event = PaymentReturnManager.latestEvent.value
        assertNotNull(event)
        assertEquals(PaymentReturnManager.Status.SUCCESS, event?.status)
        assertEquals("TXN_SUCCESS_001", event?.txnId)
    }

    @Test
    fun `handleIntent parses failure return`() {
        val intent = mockk<Intent>()
        val uri = mockk<Uri>()
        every { intent.data } returns uri
        every { uri.scheme } returns "khanabook"
        every { uri.host } returns "payment"
        every { uri.path } returns "/failure"
        every { uri.getQueryParameter("txnid") } returns null
        every { uri.getQueryParameter("txnId") } returns null

        PaymentReturnManager.handleIntent(intent)

        val event = PaymentReturnManager.latestEvent.value
        assertNotNull(event)
        assertEquals(PaymentReturnManager.Status.FAILURE, event?.status)
        assertNull(event?.txnId)
    }

    @Test
    fun `handleIntent parses status check return with txnid`() {
        val intent = mockk<Intent>()
        val uri = mockk<Uri>()
        every { intent.data } returns uri
        every { uri.scheme } returns "khanabook"
        every { uri.host } returns "payment"
        every { uri.path } returns "/status"
        every { uri.getQueryParameter("txnid") } returns "TXN_STATUS_PENDING"
        every { uri.getQueryParameter("txnId") } returns null

        PaymentReturnManager.handleIntent(intent)

        val event = PaymentReturnManager.latestEvent.value
        assertNotNull(event)
        assertEquals(PaymentReturnManager.Status.STATUS, event?.status)
        assertEquals("TXN_STATUS_PENDING", event?.txnId)
    }

    @Test
    fun `handleIntent ignores non-khanabook uri`() {
        val intent = mockk<Intent>()
        val uri = mockk<Uri>()
        every { intent.data } returns uri
        every { uri.scheme } returns "https"
        every { uri.host } returns "google.com"

        PaymentReturnManager.handleIntent(intent)

        assertNull(PaymentReturnManager.latestEvent.value)
    }

    @Test
    fun `handleIntent ignores null data or null intent`() {
        PaymentReturnManager.handleIntent(null)
        assertNull(PaymentReturnManager.latestEvent.value)

        val emptyIntent = mockk<Intent>()
        every { emptyIntent.data } returns null
        PaymentReturnManager.handleIntent(emptyIntent)
        assertNull(PaymentReturnManager.latestEvent.value)
    }

    @Test
    fun `clearLatestEvent clears the stored event`() {
        val intent = mockk<Intent>()
        val uri = mockk<Uri>()
        every { intent.data } returns uri
        every { uri.scheme } returns "khanabook"
        every { uri.host } returns "payment"
        every { uri.path } returns "/success"
        every { uri.getQueryParameter("txnid") } returns "TXN_CLEAR"
        every { uri.getQueryParameter("txnId") } returns null

        PaymentReturnManager.handleIntent(intent)
        assertNotNull(PaymentReturnManager.latestEvent.value)

        PaymentReturnManager.clearLatestEvent()
        assertNull(PaymentReturnManager.latestEvent.value)
    }
}