package com.khanabook.lite.pos.feature.notifications

import com.khanabook.lite.pos.feature.notifications.worker.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One mapping is shared by the tray builder and the in-app banner gate. These
 * tests pin it so a future type cannot silently fall into System Alerts, and so
 * money-critical alerts stay locked regardless of channel state.
 */
class NotificationChannelMappingTest {

    @Test
    fun `payment, qr order and cancelled order use the payment channel`() {
        assertEquals(NotificationHelper.CHANNEL_PAYMENT, NotificationHelper.channelForType("payment_received"))
        assertEquals(NotificationHelper.CHANNEL_PAYMENT, NotificationHelper.channelForType("qr_order"))
        assertEquals(NotificationHelper.CHANNEL_PAYMENT, NotificationHelper.channelForType("order_cancelled"))
    }

    @Test
    fun `refund, kyc and settlement keep their own channels`() {
        assertEquals(NotificationHelper.CHANNEL_REFUND, NotificationHelper.channelForType("refund"))
        assertEquals(NotificationHelper.CHANNEL_KYC, NotificationHelper.channelForType("kyc"))
        assertEquals(NotificationHelper.CHANNEL_SETTLEMENT, NotificationHelper.channelForType("settlement"))
    }

    @Test
    fun `low stock uses the inventory channel instead of system`() {
        assertEquals(NotificationHelper.CHANNEL_INVENTORY, NotificationHelper.channelForType("inventory_low"))
    }

    @Test
    fun `unknown and null types fall back to system`() {
        assertEquals(NotificationHelper.CHANNEL_SYSTEM, NotificationHelper.channelForType("terminal"))
        assertEquals(NotificationHelper.CHANNEL_SYSTEM, NotificationHelper.channelForType("BILL_EDIT_DIGEST"))
        assertEquals(NotificationHelper.CHANNEL_SYSTEM, NotificationHelper.channelForType(null))
    }

    @Test
    fun `money critical alerts stay locked`() {
        assertTrue(NotificationHelper.isLockedType("payment_received"))
        assertTrue(NotificationHelper.isLockedType("qr_order"))
        assertTrue(NotificationHelper.isLockedType("order_cancelled"))
        assertFalse(NotificationHelper.isLockedType("inventory_low"))
        assertFalse(NotificationHelper.isLockedType("kyc"))
        assertFalse(NotificationHelper.isLockedType(null))
    }
}
