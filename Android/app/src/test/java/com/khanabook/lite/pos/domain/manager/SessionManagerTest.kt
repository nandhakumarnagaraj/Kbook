package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.content.SharedPreferences
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
        // We need to bypass the constructor's EncryptedSharedPreferences.create
        // and MasterKey.Builder calls because they are hard to mock in plain JUnit.
        // We will mock the whole SessionManager and only test the logic of 
        // shouldShowAppLock, onAppBackgrounded, etc.
        
        sessionManager = mockk<SessionManager>(relaxed = true)
        
        // Setup common behaviors
        every { sessionManager.onAppBackgrounded() } answers { callOriginal() }
        every { sessionManager.shouldShowAppLock() } answers { callOriginal() }
        every { sessionManager.clearBackgroundTime() } answers { callOriginal() }
        
        // Use reflection or private field mocking if needed, 
        // but SessionManager reads from 'prefs' and 'securePrefs' via helper methods.
        // We can mock those helper methods.
        
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
        every { sessionManager.isPinLockEnabled() } returns true
        every { sessionManager.getPinHash() } returns "some_hash"
        
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
        
        val securePrefs = mockk<SharedPreferences>(relaxed = true)
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
        
        val securePrefs = mockk<SharedPreferences>(relaxed = true)
        val securePrefsField = SessionManager::class.java.getDeclaredField("securePrefs")
        securePrefsField.isAccessible = true
        securePrefsField.set(sessionManager, securePrefs)

        every { prefs.getBoolean("pin_lock_enabled", false) } returns true
        every { securePrefs.getString("pin_hash", null) } returns "some_hash"
        
        val now = System.currentTimeMillis()
        val backgroundTime = now - 10_000L // 10 seconds ago
        every { prefs.getLong("last_background_time", 0L) } returns backgroundTime

        // Act & Assert
        assertFalse("Should not show lock after 10s", sessionManager.shouldShowAppLock())
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
}
