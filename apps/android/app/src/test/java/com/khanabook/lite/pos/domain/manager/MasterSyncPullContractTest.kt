package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.khanabook.lite.pos.core.database.AppDatabase
import com.khanabook.lite.pos.core.database.DatabaseProvider
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.data.UserDao
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileDao
import com.khanabook.lite.pos.feature.staff.domain.PermissionManager
import com.khanabook.lite.pos.feature.sync.data.MasterSyncResponse
import com.khanabook.lite.pos.feature.sync.domain.MasterSyncProcessor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * WHY THIS TEST EXISTS
 * ====================
 * On 2026-10-15 the till quietly lost the owner's "Easebuzz Online" switch: the master-sync
 * pull rebuilds the restaurant profile row field by field, never named `easebuzzEnabled`, and
 * `insertSyncedRestaurantProfiles` is an @Insert(REPLACE) — so the column dropped back to its
 * `0` default on every sync. Billing then lost "Send Payment Link" and the settings toggle
 * flipped off while the server still reported the switch as on.
 *
 * The push half of the sync has a guard for exactly this failure mode (`SyncDtoContractTest`
 * reflects over every entity field and demands it appear on the DTO). The pull half had
 * nothing, and the same class of bug had already shipped once before (image_url on menu items,
 * see MasterSyncProcessorImageMergeTest).
 *
 * This test builds a pulled profile whose EVERY field differs from its default - parsed by the
 * same Gson path production uses, keyed by @SerializedName - pushes it through
 * `insertMasterData`, and asserts each field survives into the row that REPLACEs the local
 * profile. A field the server can send but the rebuild forgets shows up as one line in the
 * failure message.
 *
 * WHEN THIS TEST FAILS
 * ====================
 * You just added a column to `RestaurantProfileEntity`. Either map it in the pull rebuild in
 * MasterSyncProcessor.insertMasterData, or - if the device genuinely owns it - add it to
 * LOCAL_OWNED below with a one-line reason. Never add an entry without that reason.
 */
class MasterSyncPullContractTest {

    private lateinit var processor: MasterSyncProcessor
    private lateinit var restaurantDao: RestaurantDao
    private lateinit var db: AppDatabase
    private lateinit var databaseProvider: DatabaseProvider

    private val RESTAURANT = 55L
    private val gson = Gson()
    private val defaultProfile = RestaurantProfileEntity()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0

        // Make androidx.room.withTransaction { } execute its block inline.
        mockkStatic("androidx.room.RoomDatabaseKt")

        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { prefs.getLong("restaurant_id", any()) } returns RESTAURANT
        every { prefs.getString(any(), any()) } returns null

        databaseProvider = mockk(relaxed = true)
        db = mockk(relaxed = true)
        every { databaseProvider.getDatabase() } returns db

        restaurantDao = mockk(relaxed = true)

        processor = MasterSyncProcessor(
            api = mockk<KhanaBookApi>(relaxed = true),
            databaseProvider = databaseProvider,
            billDao = mockk<BillDao>(relaxed = true),
            restaurantDao = restaurantDao,
            userDao = mockk<UserDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            menuDao = mockk<MenuDao>(relaxed = true),
            printerProfileDao = mockk<PrinterProfileDao>(relaxed = true),
            sessionManager = SessionManager(context),
            permissionManager = mockk<PermissionManager>(relaxed = true)
        )

        val txnBlock = slot<suspend () -> Any?>()
        coEvery { db.withTransaction(capture(txnBlock)) } coAnswers { txnBlock.captured.invoke() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    // ── Fields the device owns, so the rebuild is right to ignore the server's value ──────

    private val localOwned = mapOf(
        // The row key. The rebuild keys the row on the server's restaurantId, which IS asserted.
        "id" to "local row key",
        // Set by the rebuild itself: the row it writes came from the server.
        "isSynced" to "set to true by the rebuild",
        // Device-local asset references, reconciled by the logo/QR version guards below.
        "logoPath" to "local asset path chosen by the logoVersion guard",
        "logoUrl" to "falls back to the local url when the pulled version is not newer",
        "upiQrPath" to "local asset path chosen by the upiQrVersion guard",
        "upiQrUrl" to "falls back to the local url when the pulled version is not newer",
        // Printer bindings are per-terminal hardware, never the restaurant's.
        "printerEnabled" to "device-local printer binding",
        "printerName" to "device-local printer binding",
        "printerMac" to "device-local printer binding",
        "paperSize" to "device-local printer binding",
        "autoPrintOnSuccess" to "device-local printer binding",
        "includeLogoInPrint" to "device-local printer binding",
        // Which layout this till prints with. Same hardware rule: a second terminal may
        // have two printers while this one has a single combined device.
        "printerMode" to "device-local printer routing",
        // No server counterpart exists (nothing on the server carries these), and nothing in
        // the app reads them today. Kept out of the assertion so the list stays honest; wire
        // them end to end before giving them a reader, or drop the columns.
        "kitchenPrinterEnabled" to "no server-side counterpart",
        "kitchenPrinterName" to "no server-side counterpart",
        "kitchenPrinterMac" to "no server-side counterpart",
        "kitchenPrinterPaperSize" to "no server-side counterpart",
        // The rebuild deliberately prefers this device's own quick-billing preference.
        "collectCustomerNumber" to "device-local preference, local value wins",
        // Fixed to AppConstants.DEFAULT_TIMEZONE by the rebuild rather than taken from the pull.
        "timezone" to "constant, not taken from the pull",
        // The device's outbound push mask (which fields this till changed). The server never
        // owns it, and the pull ends the local edit by marking the row synced.
        "changedFields" to "device's outbound push mask",
    )

    /**
     * One sentinel per field, guaranteed different from that field's default - including for
     * Booleans whose default is `true`, which a `false` sentinel is what exposes.
     */
    private fun pulledFieldValue(field: Field): Any? {
        val default = field.apply { isAccessible = true }.get(defaultProfile)
        return when (field.type) {
            java.lang.Boolean.TYPE -> !(default as Boolean)
            java.lang.Integer.TYPE ->
                // Version guards stay at their default so the rebuild keeps the local asset
                // paths instead of reaching for the file system.
                if (field.name.endsWith("Version")) default as Int else (default as Int) + 4242
            java.lang.Long.TYPE -> (default as Long) + 4242L
            java.lang.Double.TYPE -> (default as Double) + 4242.0
            String::class.java -> "pull_${field.name}"
            java.lang.Long::class.java -> ((default as Long?) ?: 0L) + 4242L
            java.lang.Boolean::class.java -> !((default as Boolean?) ?: true)
            else -> throw AssertionError(
                "MasterSyncPullContractTest does not know how to fake a ${field.type} " +
                    "(field '${field.name}'). Teach pulledFieldValue() about the new type."
            )
        }
    }

    /** The profile the server would send, with every field carrying its sentinel. */
    private fun pulledProfile(): RestaurantProfileEntity {
        val json = fields().associate { field ->
            val key = field.getAnnotation(SerializedName::class.java)?.value ?: field.name
            key to pulledFieldValue(field)
        }
        return gson.fromJson(gson.toJson(json), RestaurantProfileEntity::class.java)
    }

    private fun fields(): List<Field> = RestaurantProfileEntity::class.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) }
        .filter { !it.isSynthetic }
        .sortedBy { it.name }

    @Test
    fun `every field the server can send survives the pull into the stored profile row`() = runTest {
        val pulled = pulledProfile()

        // A local row at its defaults: anything the rebuild leaves untouched reads as a default
        // and therefore differs from its sentinel.
        val local = RestaurantProfileEntity(id = RESTAURANT, restaurantId = RESTAURANT)
        coEvery { restaurantDao.getProfile(RESTAURANT) } returns local
        coEvery { restaurantDao.getProfile() } returns local

        val stored = slot<List<RestaurantProfileEntity>>()
        coEvery { restaurantDao.insertSyncedRestaurantProfiles(capture(stored)) } returns Unit

        processor.insertMasterData(MasterSyncResponse(profiles = listOf(pulled)))

        val row = stored.captured.single()
        val dropped = fields().mapNotNull { field ->
            if (localOwned.containsKey(field.name)) return@mapNotNull null
            field.isAccessible = true
            val sent = field.get(pulled)
            val kept = field.get(row)
            if (sent == kept) null else "${field.name}: server sent '$sent', stored row has '$kept'"
        }

        assertTrue(
            "The pull rebuild drops fields the server sent, so the REPLACE insert resets them:\n" +
                dropped.joinToString("\n") +
                "\n\nMap each field in MasterSyncProcessor.insertMasterData, or add it to " +
                "LOCAL_OWNED with a reason.",
            dropped.isEmpty()
        )
    }

    /**
     * The regression that motivated this guard, pinned separately so a future edit to the
     * allow-list cannot quietly re-exempt it.
     */
    @Test
    fun `the Easebuzz switch is not exempt`() = runTest {
        assertTrue(
            "easebuzzEnabled must stay asserted by this contract test",
            !localOwned.containsKey("easebuzzEnabled")
        )
    }
}
