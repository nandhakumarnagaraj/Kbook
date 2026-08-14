package com.khanabook.lite.pos.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationListConsistencyTest {

    @Test
    fun bothMigrationLists_areIdentical() {
        val provider = extractMigrationTokens(PROVIDER)
        val module = extractMigrationTokens(MODULE)

        assertEquals(
            "DatabaseProvider.buildDatabaseWithName migration list must equal DatabaseModule.buildDatabase migration list",
            provider,
            module
        )
        assertTrue("migration list must not be empty", provider.isNotEmpty())
    }

    private fun extractMigrationTokens(file: File): List<String> =
        file.readLines()
            .map { it.trim() }
            .filter { it.startsWith("AppDatabase.MIGRATION_") }
            .map { it.removeSuffix(",") }

    private companion object {
        val PROVIDER = locateFile("app/src/main/java/com/khanabook/lite/pos/data/local/DatabaseProvider.kt")
        val MODULE = locateFile("app/src/main/java/com/khanabook/lite/pos/di/DatabaseModule.kt")

        private fun locateFile(relative: String): File {
            var dir = File(System.getProperty("user.dir"))
            while (dir != null) {
                val candidate = File(dir, relative)
                if (candidate.isFile) return candidate
                dir = dir.parentFile
            }
            throw IllegalStateException("Unable to locate $relative from ${System.getProperty("user.dir")}")
        }
    }
}