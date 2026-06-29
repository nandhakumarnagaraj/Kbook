package com.khanabook.lite.pos.test

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.di.DatabaseModule
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DbDiagnosticTest {

    @Test
    fun dumpBillsAndCheckDuplicates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val passphrase = DatabaseModule.getOrCreateDbPassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)
        
        val dbName = "khanabook_lite_db_1164134026374723071"
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
        .openHelperFactory(factory)
        .build()

        val billDao = db.billDao()
        val unsyncedBills = kotlinx.coroutines.runBlocking { billDao.getUnsyncedBills(1164134026374723071L) }
        Log.e("DB_DIAGNOSTIC", "=== START DIAGNOSTIC ===")
        Log.e("DB_DIAGNOSTIC", "Number of unsynced bills: ${unsyncedBills.size}")
        unsyncedBills.forEach { bill ->
            Log.e("DB_DIAGNOSTIC", "Bill: id=${bill.id}, serverId=${bill.serverId}, isSynced=${bill.isSynced}, dailyId=${bill.dailyOrderId}, lifetimeId=${bill.lifetimeOrderId}, deviceId=${bill.deviceId}, total=${bill.totalAmount}, createdBy=${bill.createdBy}")
        }

        // Count duplicates of lifetimeOrderId
        val lifetimeOrderIds = unsyncedBills.map { it.lifetimeOrderId }
        val duplicates = lifetimeOrderIds.groupBy { it }.filter { it.value.size > 1 }
        Log.e("DB_DIAGNOSTIC", "Duplicate lifetimeOrderIds in unsynced: $duplicates")
        Log.e("DB_DIAGNOSTIC", "=== END DIAGNOSTIC ===")
        
        db.close()
    }
}
