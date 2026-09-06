package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.content.SharedPreferences
import com.khanabook.lite.pos.domain.util.KeystoreBackedPreferences
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val prefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    
    private lateinit var sessionManager: SessionManager

    @Before
    fun setup() {
        sessionManager = SessionManager(context)
        
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.remove(any()) } returns prefsEditor
        every { prefsEditor.apply() } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `shouldShowAppLock returns true when elapsed time is greater than 30s`() {
        // Arrange
        val now = System.currentTimeMillis()
        val backgroundTime = now - 31_000L // 31 seconds ago
        
        // We need to mock how sessionManager access the prefs
        // Looking at the code, it uses prefs.getLong("last_background_time", 0L)
        // But sessionManager is a mock, so we need to ensure its internal 
        // 'prefs' access (if it calls original) is handled.
        // Actually, let's just mock the private 'prefs' property if possible, 
        // or mock the methods that use it.
        
        // In SessionManager.kt:
        // val lastBackground = prefs.getLong(KEY_LAST_BACKGROUND_TIME, 0L)
        
        // If we use callOriginal(), it will try to use the real 'prefs' field which is null in the mock.
        // Let's use a better approach: Mock the dependency fields using reflection since they are private val.
        
        val prefsField = SessionManager::class.java.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(sessionManager, prefs)
        
        val securePrefs = mockk<KeystoreBackedPreferences>(relaxed = true)
        val securePrefsField = SessionManager::class.java.getDeclaredField("securePrefs")
        securePrefsField.isAccessible = true
        securePrefsField.set(sessionManager, securePrefs)

        every { prefs.getBoolean("pin_lock_enabled", false) } returns true
        every { securePrefs.getString("pin_hash", null) } returns "some_hash"
        every { prefs.getLong("last_background_time", 0L) } returns backgroundTime

        // Act & Assert
        assertTrue("Should show lock after 31s", sessionManager.shouldShowAppLock())
    }

    @Test
    fun `shouldShowAppLock returns false when elapsed time is less than 30s`() {
        // Arrange
        val prefsField = SessionManager::class.java.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(sessionManager, prefs)
        
        val securePrefs = mockk<KeystoreBackedPreferences>(relaxed = true)
        val securePrefsField = SessionManager::class.java.getDeclaredField("securePrefs")
        securePrefsField.isAccessible = true
        securePrefsField.set(sessionManager, securePrefs)

        every { prefs.getBoolean("pin_lock_enabled", false) } returns true
        every { securePrefs.getString("pin_hash", null) } returns "some_hash"
        
        val now = System.currentTimeMillis()
        val backgroundTime = now // 0 seconds ago
        every { prefs.getLong("last_background_time", 0L) } returns backgroundTime

        // Act & Assert
        val lastBg = prefs.getLong("last_background_time", 0L)
        val shouldShow = sessionManager.shouldShowAppLock()
        println("TEST DEBUG: now=$now, backgroundTime=$backgroundTime, lastBg=$lastBg, shouldShowLock=$shouldShow, diff=${System.currentTimeMillis() - lastBg}")
        assertFalse("Should not show lock after 0s", shouldShow)
    }

    @Test
    fun `onAppBackgrounded persists current timestamp`() {
        // Arrange
        val prefsField = SessionManager::class.java.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(sessionManager, prefs)

        // Act
        sessionManager.onAppBackgrounded()

        // Assert
        verify { 
            prefsEditor.putLong("last_background_time", any())
            prefsEditor.apply()
        }
    }

    @Test
    fun `canWriteMasterData is role-bound to owner only`() {
        val prefsField = SessionManager::class.java.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(sessionManager, prefs)

        fun writeAllowedFor(role: String?): Boolean {
            every { prefs.getString("active_user_role", null) } returns role
            return sessionManager.canWriteMasterData()
        }

        assertTrue("OWNER may write master data", writeAllowedFor("OWNER"))
        assertFalse("SHOP_STAFF may not write master data", writeAllowedFor("SHOP_STAFF"))
        assertFalse("SHOP_ADMIN (legacy staff) may not write master data", writeAllowedFor("SHOP_ADMIN"))
        assertFalse("KBOOK_ADMIN may not write master data", writeAllowedFor("KBOOK_ADMIN"))
        assertFalse("unknown role may not write master data", writeAllowedFor(null))
    }

    @Test
    fun `canWritePrinterAndSettings is allowed for all POS roles`() {
        val prefsField = SessionManager::class.java.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(sessionManager, prefs)

        fun allowedFor(role: String?): Boolean {
            every { prefs.getString("active_user_role", null) } returns role
            return sessionManager.canWritePrinterAndSettings()
        }

        assertTrue("OWNER may write printer/settings", allowedFor("OWNER"))
        assertTrue("SHOP_STAFF may write printer/settings", allowedFor("SHOP_STAFF"))
        assertTrue("SHOP_ADMIN (legacy staff) may write printer/settings", allowedFor("SHOP_ADMIN"))
        assertTrue("MANAGER (legacy staff) may write printer/settings", allowedFor("MANAGER"))
        assertTrue("CASHIER (legacy staff) may write printer/settings", allowedFor("CASHIER"))
        assertTrue("WAITER (legacy staff) may write printer/settings", allowedFor("WAITER"))
        assertTrue("OPERATIONS (legacy staff) may write printer/settings", allowedFor("OPERATIONS"))
        assertFalse("KBOOK_ADMIN is not a POS role", allowedFor("KBOOK_ADMIN"))
        assertFalse("unknown role may not write printer/settings", allowedFor(null))
    }

    @Test
    fun `canUsePos is bound to owner and staff roles`() {
        val prefsField = SessionManager::class.java.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(sessionManager, prefs)

        fun posAllowedFor(role: String?): Boolean {
            every { prefs.getString("active_user_role", null) } returns role
            return sessionManager.canUsePos()
        }

        assertTrue("OWNER may use POS", posAllowedFor("OWNER"))
        assertTrue("SHOP_STAFF may use POS", posAllowedFor("SHOP_STAFF"))
        assertTrue("SHOP_ADMIN (legacy staff) may use POS", posAllowedFor("SHOP_ADMIN"))
        assertTrue("MANAGER (legacy staff) may use POS", posAllowedFor("MANAGER"))
        assertTrue("CASHIER (legacy staff) may use POS", posAllowedFor("CASHIER"))
        assertTrue("WAITER (legacy staff) may use POS", posAllowedFor("WAITER"))
        assertTrue("OPERATIONS (legacy staff) may use POS", posAllowedFor("OPERATIONS"))
        assertFalse("KBOOK_ADMIN may not use POS", posAllowedFor("KBOOK_ADMIN"))
        assertFalse("unknown role may not use POS", posAllowedFor(null))
    }
}
