package com.khanabook.lite.pos.domain.manager

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PermissionManagerTest {

    private lateinit var session: SessionManager
    private lateinit var databaseProvider: com.khanabook.lite.pos.data.local.DatabaseProvider
    private lateinit var manager: PermissionManager

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any(), any()) } returns 0
        session = mockk(relaxed = true)
        databaseProvider = mockk(relaxed = true)
        manager = PermissionManager(session, databaseProvider)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `owner has every permission without any grants`() {
        every { session.isOwner() } returns true
        assertTrue(manager.hasPermission(PermissionManager.BILLING_REFUND))
        assertTrue(manager.hasAllPermissions(PermissionManager.STAFF_ADD, PermissionManager.REPORTS_GST))
        assertTrue(manager.hasAnyPermission(PermissionManager.SETTINGS_GST))
    }

    @Test
    fun `non-owner checks the granted set`() {
        every { session.isOwner() } returns false
        manager.updateFromSync(listOf(PermissionManager.BILLING_CREATE, PermissionManager.ORDERS_VIEW))

        assertTrue(manager.hasPermission(PermissionManager.BILLING_CREATE))
        assertFalse(manager.hasPermission(PermissionManager.BILLING_REFUND))
    }

    @Test
    fun `hasAllPermissions requires every key for non-owner`() {
        every { session.isOwner() } returns false
        manager.updateFromSync(listOf(PermissionManager.BILLING_CREATE, PermissionManager.BILLING_SETTLE))

        assertTrue(manager.hasAllPermissions(PermissionManager.BILLING_CREATE, PermissionManager.BILLING_SETTLE))
        assertFalse(manager.hasAllPermissions(PermissionManager.BILLING_CREATE, PermissionManager.BILLING_VOID))
    }

    @Test
    fun `hasAnyPermission needs only one key for non-owner`() {
        every { session.isOwner() } returns false
        manager.updateFromSync(listOf(PermissionManager.MENU_VIEW))

        assertTrue(manager.hasAnyPermission(PermissionManager.MENU_EDIT_PRICE, PermissionManager.MENU_VIEW))
        assertFalse(manager.hasAnyPermission(PermissionManager.MENU_EDIT_PRICE, PermissionManager.MENU_DELETE_ITEM))
    }

    @Test
    fun `updateFromSync with null is a no-op and does not mark loaded change`() {
        every { session.isOwner() } returns false
        manager.updateFromSync(null)
        assertFalse(manager.permissionsLoaded.value)
        assertFalse(manager.hasPermission(PermissionManager.BILLING_CREATE))
    }

    @Test
    fun `updateFromSync marks permissions loaded`() {
        every { session.isOwner() } returns false
        manager.updateFromSync(listOf(PermissionManager.ORDERS_VIEW))
        assertTrue(manager.permissionsLoaded.value)
    }

    @Test
    fun `clear removes grants and resets loaded flag`() {
        every { session.isOwner() } returns false
        manager.updateFromSync(listOf(PermissionManager.BILLING_CREATE))
        manager.clear()
        assertFalse(manager.permissionsLoaded.value)
        assertFalse(manager.hasPermission(PermissionManager.BILLING_CREATE))
    }

    @Test
    fun `getDisplayName maps known keys and falls back to key`() {
        assertEquals("Process Refunds", manager.getDisplayName(PermissionManager.BILLING_REFUND))
        assertEquals("unknown.key", manager.getDisplayName("unknown.key"))
    }

    @Test
    fun `revision defaults to zero and updates from sync`() {
        every { session.isOwner() } returns false
        assertEquals(0L, manager.currentRevision())
        manager.updateFromSync(listOf(PermissionManager.MENU_EDIT_PRICE), 7L)
        assertEquals(7L, manager.currentRevision())
        assertEquals(7L, manager.permissionRevision.value)
    }

    @Test
    fun `revision-only update is applied without changing grants`() {
        every { session.isOwner() } returns false
        manager.updateFromSync(listOf(PermissionManager.MENU_VIEW), 3L)
        manager.updateFromSync(null, 4L)
        assertEquals(4L, manager.currentRevision())
        assertTrue(manager.hasPermission(PermissionManager.MENU_VIEW))
    }

    @Test
    fun `clear resets revision to zero`() {
        every { session.isOwner() } returns false
        manager.updateFromSync(listOf(PermissionManager.MENU_EDIT_PRICE), 9L)
        manager.clear()
        assertEquals(0L, manager.currentRevision())
    }
}
