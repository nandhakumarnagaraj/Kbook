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
import com.khanabook.lite.pos.feature.auth.data.UserDao
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.menu.data.MenuPullMergePolicy
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * WHY THIS TEST EXISTS
 * ====================
 * A category's favourite pin is SERVER-owned. The owner toggles it in web admin (or on the till,
 * through the owner-gated favourite endpoint), CategoryDTO carries it out on the master pull,
 * CategorySyncDto deliberately omits it on push, and the server restores its own value on every
 * category push instead of trusting a device.
 *
 * The pull half dropped it: insertMasterData rebuilt each pulled category as a fresh
 * CategoryEntity and never named `isFavourite`, so the constructor default (`false`) won. The
 * row that reaches the database is that rebuilt entity, `upsertSyncedCategories` ->
 * MenuPullMergePolicy.resolveCategory mirrors the pulled row whole, and the favourite write
 * deliberately does NOT mark the local row dirty - so the "dirty row wins" protection never
 * applied either. Net effect: the owner pinned a category, the endpoint touched serverUpdatedAt
 * and woke every terminal, and the pull that was meant to deliver the pin was the one that
 * erased it. The star disappeared on the till that had just set it while web-admin still showed
 * it pinned, and the billing strip (ordered `is_favourite DESC`) silently lost the pin too.
 *
 * The reflection test at the bottom is the guard for this whole bug class on this entity: build
 * what the server would send - parsed by the same Gson path production uses, keyed by
 * @SerializedName, every field carrying a sentinel that differs from its default - and demand
 * each sentinel survive into the row the pull hands to the DAO.
 */
class MasterSyncProcessorCategoryFavouriteTest {

    private lateinit var processor: MasterSyncProcessor
    private lateinit var categoryDao: CategoryDao
    private lateinit var db: AppDatabase
    private lateinit var databaseProvider: DatabaseProvider

    private val RESTAURANT = 55L
    private val CATEGORY_SERVER_ID = 10L
    private val gson = Gson()

    /** A base row to read each field's default from (name/isVeg have no default of their own). */
    private val defaultCategory = CategoryEntity(name = "Mains", isVeg = true)

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

        categoryDao = mockk(relaxed = true)

        processor = MasterSyncProcessor(
            api = mockk<KhanaBookApi>(relaxed = true),
            databaseProvider = databaseProvider,
            billDao = mockk<BillDao>(relaxed = true),
            restaurantDao = mockk<RestaurantDao>(relaxed = true),
            userDao = mockk<UserDao>(relaxed = true),
            categoryDao = categoryDao,
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

    private fun pulledCategory(isFavourite: Boolean = false) = CategoryEntity(
        id = CATEGORY_SERVER_ID,
        name = "Mains",
        isVeg = true,
        restaurantId = RESTAURANT,
        serverId = CATEGORY_SERVER_ID,
        isFavourite = isFavourite
    )

    private suspend fun runAndCaptureCategories(
        masterData: MasterSyncResponse
    ): List<CategoryEntity> {
        val captured = slot<List<CategoryEntity>>()
        coEvery { categoryDao.upsertSyncedCategories(capture(captured)) } returns Unit
        processor.insertMasterData(masterData)
        return captured.captured
    }

    @Test
    fun `a category the server pinned is stored as a favourite`() = runTest {
        val categories = runAndCaptureCategories(
            MasterSyncResponse(categories = listOf(pulledCategory(isFavourite = true)))
        )

        assertTrue(
            "the pulled pin must reach the row the DAO is handed, or the star is erased on every sync",
            categories.single { it.serverId == CATEGORY_SERVER_ID }.isFavourite
        )
    }

    @Test
    fun `the server unpinning a category clears the local star`() = runTest {
        // The other direction, so the fix cannot be "always write true": the pull is
        // server-authoritative and a stale local pin must not win.
        val categories = runAndCaptureCategories(
            MasterSyncResponse(categories = listOf(pulledCategory(isFavourite = false)))
        )

        assertFalse(categories.single { it.serverId == CATEGORY_SERVER_ID }.isFavourite)

        // ... and the row the merge hands the database carries that same value, which is what
        // actually clears the star: resolveCategory mirrors the pulled row whole.
        val merged = MenuPullMergePolicy.resolveCategory(
            pulled = categories.single { it.serverId == CATEGORY_SERVER_ID },
            existing = pulledCategory(isFavourite = true).copy(isSynced = true, updatedAt = 1_000L)
        )
        assertFalse(
            "an unpin must not be swallowed by the local pin",
            merged?.isFavourite == true
        )
    }

    // ── field guard: a column the pull forgets cannot go unnoticed again ────────────────

    private val localOwned = mapOf(
        // The pulled row keys on the server id, and upsertSyncedCategories re-keys it to the
        // local row it replaces.
        "id" to "local row key, resolved by the DAO",
        // Set by the rebuild itself: the row it writes came from the server.
        "isSynced" to "set to true by the rebuild",
    )

    private fun fields(): List<Field> = CategoryEntity::class.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) }
        .filter { !it.isSynthetic }
        .sortedBy { it.name }

    /** One sentinel per field, guaranteed different from that field's default. */
    private fun pulledFieldValue(field: Field): Any? {
        val default = field.apply { isAccessible = true }.get(defaultCategory)
        return when (field.type) {
            java.lang.Boolean.TYPE -> !(default as Boolean)
            java.lang.Integer.TYPE -> (default as Int) + 4242
            java.lang.Long.TYPE -> (default as Long) + 4242L
            String::class.java -> "pull_${field.name}"
            java.lang.Long::class.java -> ((default as Long?) ?: 0L) + 4242L
            java.lang.Boolean::class.java -> !((default as Boolean?) ?: true)
            else -> throw AssertionError(
                "MasterSyncProcessorCategoryFavouriteTest does not know how to fake a " +
                    "${field.type} (field '${field.name}'). Teach pulledFieldValue() about it."
            )
        }
    }

    /** The category the server would send, with every field carrying its sentinel. */
    private fun pulledCategoryWithSentinels(): CategoryEntity {
        val json = fields().associate { field ->
            val key = field.getAnnotation(SerializedName::class.java)?.value ?: field.name
            key to pulledFieldValue(field)
        }
        return gson.fromJson(gson.toJson(json), CategoryEntity::class.java)
    }

    @Test
    fun `every field the server can send survives the pull rebuild`() = runTest {
        val pulled = pulledCategoryWithSentinels()

        val stored = slot<List<CategoryEntity>>()
        coEvery { categoryDao.upsertSyncedCategories(capture(stored)) } returns Unit

        processor.insertMasterData(MasterSyncResponse(categories = listOf(pulled)))

        val row = stored.captured.single()
        val dropped = fields().mapNotNull { field ->
            if (localOwned.containsKey(field.name)) return@mapNotNull null
            field.isAccessible = true
            val sent = field.get(pulled)
            val kept = field.get(row)
            if (sent == kept) null else "${field.name}: server sent '$sent', stored row has '$kept'"
        }

        assertTrue(
            "The pull rebuild drops fields the server sent, so the row written over the local " +
                "category loses them:\n" +
                dropped.joinToString("\n") +
                "\n\nMap each field in MasterSyncProcessor.insertMasterData (the category block), " +
                "or add it to localOwned with a reason.",
            dropped.isEmpty()
        )
    }

    /**
     * The regression that motivated this guard, pinned separately so a future edit to the
     * exemption list cannot quietly re-exempt it.
     */
    @Test
    fun `the favourite pin is not exempt`() {
        assertTrue(
            "isFavourite must stay asserted by this contract test",
            !localOwned.containsKey("isFavourite")
        )
    }
}
