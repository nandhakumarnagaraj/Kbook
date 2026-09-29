package com.khanabook.lite.pos.feature.sync

import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.data.UserEntity
import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.billing.data.BillPaymentEntity
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity
import com.khanabook.lite.pos.feature.sync.data.BillItemSyncDto
import com.khanabook.lite.pos.feature.sync.data.BillPaymentSyncDto
import com.khanabook.lite.pos.feature.sync.data.BillSyncDto
import com.khanabook.lite.pos.feature.sync.data.CategorySyncDto
import com.khanabook.lite.pos.feature.sync.data.ItemVariantSyncDto
import com.khanabook.lite.pos.feature.sync.data.MenuItemSyncDto
import com.khanabook.lite.pos.feature.sync.data.RestaurantProfileSyncDto
import com.khanabook.lite.pos.feature.sync.data.UserSyncDto
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WHY THIS TEST EXISTS
 * ====================
 * On 2026-09-27 a production incident showed that the sync pipeline silently drops
 * any entity field its hand-written DTO mapper does not declare: HTTP 200, no log,
 * the field written as NULL server-side, and the symptom appears days later as a
 * value that "mysteriously reverted" (stock ledger dead, stock deducted twice,
 * Zomato/Swiggy credentials wiped, kitchen special instructions erased — 20 defects
 * total, see docs/reviews/KHANABOOK_MENU_PRICE_SYNC_DATA_LOSS_2026-09-27.md).
 *
 * The mappers in feature/sync/data/SyncEntityMappers.kt are hand-maintained for nine
 * entities, and the same field lists are hand-copied AGAIN into the quarantine
 * snapshot builders (MasterSyncProcessor.buildBill*Snapshot). A developer adding a
 * column to an entity has to remember three places; missing any one is silent.
 *
 * This test makes forgetting IMPOSSIBLE to merge: it reflects over each Room entity
 * and asserts every field that carries business data is present on the network DTO.
 * No fixture data to rot, no mocks — pure structural assertions using Java
 * reflection (no kotlin-reflect dependency needed).
 *
 * WHEN THIS TEST FAILS
 * ====================
 * You just added a field to a Room entity. Options, in order of preference:
 *  1. Add the field to the entity's SyncDto (SyncRequestDtos.kt) AND map it in
 *     SyncEntityMappers.kt AND update the pull mapper in MasterSyncProcessor.kt.
 *     This is almost always what you want — verify the server entity has the field.
 *  2. If the field is genuinely local-only (device bookkeeping the server must
 *     never see), add its name to LOCAL_ONLY_FIELDS below with a one-line comment
 *     saying why. Never delete an existing entry without proving it is local-only.
 *
 * Run: ./gradlew :app:testDebugUnitTest --tests "*SyncDtoContractTest*"
 */
class SyncDtoContractTest {

    /**
     * Entity properties excluded from the push-DTO assertion. Entries fall into
     * three evidence-backed buckets:
     *  - mapped under a different name (id -> localId, serverId -> serverId)
     *  - server-owned: Android receives on pull, must never push
     *    (2026-09-27: a legacy profile push WIPED the marketplace fields)
     *  - pure client bookkeeping the server does not model
     * Every non-obvious entry needs a reason comment. Never delete an entry
     * without proving it is local-only.
     */
    private val localOnlyFields: Map<String, Set<String>> = mapOf(
        // All entities — engine-written bookkeeping that IS mapped on every DTO;
        // excluded here only because the assertion compares raw names.
        "*" to setOf(
            "id",           // Room autogen PK; travels as localId (mapper re-maps it)
            "serverId",     // mapped (DTO.serverId = entity.serverId)
            "updatedAt",    // mapped; engine-maintained timestamp
            "isSynced",     // DTO intentionally drops — server owns sync state
            "deviceId",     // mapped; device identity, present on all DTOs
            "isDeleted",    // mapped on all DTOs
        ),
        "BillEntity" to setOf(
            // Server Bill.java owns these; Android receives on pull, never pushes
            // (mirrors the refundAmount comment in SyncEntityMappers.kt).
            "refundAmount", "refundReason", "refundStatus",
            "refundRequestedAt", "refundProcessedAt", "refundedBy",
            "terminalId",   // device identity; server derives it from terminal auth
            "isInvoiceAllocated", "invoiceAllocatedAt", // server-side allocation state
            "createdAt",    // mapped on DTO; engine-managed timestamp like updatedAt
            // Android-local state, verified absent from server Bill.java:
            "createdByUserId",  // local user-row id; push remaps to server user id via createdBy
                                // (MasterSyncProcessor.pushBill: userDao.getUserById(it)?.serverId)
            "ownerUserId", "ownerRestaurantId", // local ownership bookkeeping
            "syncStatus", "syncFailureReason", "syncFailedAt", // local sync engine audit
            "lockStatus",       // local terminal lock state
            "recordOrigin", "recordScope", // local provenance tags (set on server pull too)
            "paymentAttemptStatus", "paymentAttemptStartedAt", // local payment UI state machine
        ),
        "BillItemEntity" to setOf(
            "createdAt",    // mapped on DTO
            "serverBillId", "serverMenuItemId", "serverVariantId", // mirrored on push
            // Client-only delta-print flag — ZERO occurrences in server code
            // (verified 2026-09-29). Consumed by the local KOT print pipeline.
            "sentToKot",
        ),
        "BillPaymentEntity" to setOf(
            "createdAt",    // mapped on DTO
            "serverBillId", // mirrored on push
            // Both absent from server BillPayment.java (verified 2026-09-29):
            "billPublicToken",  // local bill linkage convenience
            "syncStatus",       // local sync engine audit
        ),
        "CategoryEntity" to setOf(
            // SERVER GAP (evidence: server Category.java has isActive, CategorySyncDto
            // does not carry it — same class as the 2026-09-27 incident). Recorded so
            // the test documents reality; fix by adding isActive to CategorySyncDto +
            // the server DTO and REMOVING this line.
            "isActive",
            "createdAt",    // mapped on DTO
        ),
        "MenuItemEntity" to setOf(
            "serverCategoryId",             // server-relational id, mirrored on push
            "permissionRevisionAtCreation", // mapped on DTO
            "changedFields",                // mapped on DTO
            "imageUrl", "imageVersion",     // mapped on DTO
            "createdAt",                    // mapped on DTO
            "overwriteExisting",            // request-scoped flag, not persisted state
        ),
        "ItemVariantEntity" to setOf(
            "serverMenuItemId", // mirrored on push
            "createdAt",        // mapped on DTO
        ),
        "UserEntity" to setOf(
            "tokenInvalidatedAt", // local session bookkeeping
            "createdAt",          // mapped on DTO
        ),
        "RestaurantProfileEntity" to setOf(
            "changedFields", // mapped on DTO
            // Server-owned marketplace / Easebuzz sub-merchant config: web-admin
            // writes them, Android only displays pulled state. NEVER push.
            "easebuzzSubMerchantId", "easebuzzOnboardingStatus",
            "easebuzzSettlementStatus", "easebuzzAccountStatus",
            "zomatoEnabled", "zomatoOutletId", "zomatoApiKey",
            "swiggyEnabled", "swiggyOutletId", "swiggyApiKey",
            "emailInvoiceConsent",
            // Device-scoped config, intentionally not synced (each terminal owns its
            // printer + display prefs). If the product ever wants these server-side,
            // add to the DTO + server profile entity and REMOVE from this list.
            "easebuzzEnabled",
            "printerEnabled", "printerName", "printerMac", "paperSize",
            "printCustomerWhatsapp",
            "kitchenPrinterEnabled", "kitchenPrinterName", "kitchenPrinterMac",
            "kitchenPrinterPaperSize",
            "showBranding", "maskCustomerPhone",
        ),
    )

    // ── Entity → DTO pairs (push direction) ──────────────────────────────────

    @Test
    fun billEntityHasNoUnmappedFields() =
        assertAllBusinessFieldsMapped(BillEntity::class.java, BillSyncDto::class.java)

    @Test
    fun billItemEntityHasNoUnmappedFields() =
        assertAllBusinessFieldsMapped(BillItemEntity::class.java, BillItemSyncDto::class.java)

    @Test
    fun billPaymentEntityHasNoUnmappedFields() =
        assertAllBusinessFieldsMapped(BillPaymentEntity::class.java, BillPaymentSyncDto::class.java)

    @Test
    fun categoryEntityHasNoUnmappedFields() =
        assertAllBusinessFieldsMapped(CategoryEntity::class.java, CategorySyncDto::class.java)

    @Test
    fun menuItemEntityHasNoUnmappedFields() =
        assertAllBusinessFieldsMapped(MenuItemEntity::class.java, MenuItemSyncDto::class.java)

    @Test
    fun itemVariantEntityHasNoUnmappedFields() =
        assertAllBusinessFieldsMapped(ItemVariantEntity::class.java, ItemVariantSyncDto::class.java)

    @Test
    fun userEntityHasNoUnmappedFields() =
        assertAllBusinessFieldsMapped(UserEntity::class.java, UserSyncDto::class.java)

    @Test
    fun restaurantProfileEntityHasNoUnmappedFields() =
        assertAllBusinessFieldsMapped(RestaurantProfileEntity::class.java, RestaurantProfileSyncDto::class.java)

    // ── Reverse direction: DTO fields must still exist on the entity ─────────

    @Test
    fun dtoFieldsAreNotStaleAfterEntityChanges() {
        val pairs = listOf(
            BillEntity::class.java to BillSyncDto::class.java,
            BillItemEntity::class.java to BillItemSyncDto::class.java,
            BillPaymentEntity::class.java to BillPaymentSyncDto::class.java,
            CategoryEntity::class.java to CategorySyncDto::class.java,
            MenuItemEntity::class.java to MenuItemSyncDto::class.java,
            ItemVariantEntity::class.java to ItemVariantSyncDto::class.java,
            UserEntity::class.java to UserSyncDto::class.java,
            RestaurantProfileEntity::class.java to RestaurantProfileSyncDto::class.java,
        )
        val stale = mutableListOf<String>()
        for ((entity, dto) in pairs) {
            val entityFieldNames = persistentFieldNames(entity)
            for (dtoField in persistentFieldNames(dto)) {
                if (dtoField in DTO_ONLY_FIELDS) continue
                if (dtoField !in entityFieldNames) {
                    stale += "${dto.simpleName}.$dtoField has no matching entity field"
                }
            }
        }
        assertTrue("Stale DTO fields detected:\n${stale.joinToString("\n")}", stale.isEmpty())
    }

    // ── Core assertion ───────────────────────────────────────────────────────

    private fun assertAllBusinessFieldsMapped(entity: Class<*>, dto: Class<*>) {
        val dtoFieldNames = persistentFieldNames(dto)

        val missing = mutableListOf<String>()
        for (prop in persistentFieldNames(entity)) {
            if (prop in localOnlyFields["*"].orEmpty()) continue
            if (prop in localOnlyFields[entity.simpleName].orEmpty()) continue
            if (prop !in dtoFieldNames) {
                missing += prop
            }
        }

        assertTrue(
            """
            SYNC CONTRACT BROKEN for ${entity.simpleName}:
            These fields exist on the Room entity but are NOT on ${dto.simpleName}.
            They will be silently dropped on push (HTTP 200, field arrives null) —
            the exact failure class from the 2026-09-27 data-loss incident.

            Missing: ${missing.joinToString(", ")}

            Fix: add them to the DTO + SyncEntityMappers.kt + MasterSyncProcessor.kt
            pull mapper, or justify them in LOCAL_ONLY_FIELDS with a reason.
            """.trimIndent(),
            missing.isEmpty()
        )
    }

    /**
     * Field names via Java reflection — Kotlin data/entity classes expose one
     * backing field per constructor/body val with the same name. Statics
     * (companion instances) and synthetic (delegate/`$default`) fields excluded.
     */
    private fun persistentFieldNames(clazz: Class<*>): Set<String> =
        clazz.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

    private companion object {
        /** DTO-only transport fields that never exist on entities. */
        val DTO_ONLY_FIELDS = setOf(
            "localDbId", "localId", "timezone",
            // Declared on DTOs but filled with constants by the mappers
            // (BillItemSyncDto.terminalId = null, .version = 0L):
            "terminalId", "version",
        )
    }
}
