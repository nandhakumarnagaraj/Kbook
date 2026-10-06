package com.khanabook.lite.pos.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationListConsistencyTest {

    @Test
    fun allMigrations_formUnbrokenChainFrom17To79() {
        val migrations = AppDatabase.ALL_MIGRATIONS
        assertTrue("migration list must not be empty", migrations.isNotEmpty())

        val migrationMap = migrations.associateBy { it.startVersion to it.endVersion }

        // Verify every single step from version 17 up to 79 is present with no gaps
        for (v in 17 until 79) {
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