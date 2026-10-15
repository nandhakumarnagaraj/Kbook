package com.khanabook.lite.pos.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationListConsistencyTest {

    @Test
    fun allMigrations_formUnbrokenChainFrom17ToCurrentVersion() {
        val migrations = AppDatabase.ALL_MIGRATIONS
        assertTrue("migration list must not be empty", migrations.isNotEmpty())

        val migrationMap = migrations.associateBy { it.startVersion to it.endVersion }

        // The upper bound is derived from the chain itself, not written down: the literal this
        // used to carry stopped at 79, so the 79->80 and 80->81 steps were never actually
        // checked. A gap anywhere below the newest migration is a crash on upgrade, so assert
        // every consecutive step from 17 up to the newest end version.
        val highestVersion = migrations.maxOf { it.endVersion }
        for (v in 17 until highestVersion) {
            val migration = migrationMap[v to (v + 1)]
            assertNotNull(
                "Missing migration from version $v to ${v + 1} in AppDatabase.ALL_MIGRATIONS",
                migration
            )
            assertEquals(v, migration!!.startVersion)
            assertEquals(v + 1, migration.endVersion)
        }
    }
}