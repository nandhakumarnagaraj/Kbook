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
        
        Log.e("DB_DIAGNOSTIC", "=== START DIAGNOSTIC ===")
        val dbNames = context.databaseList().filter { it.startsWith("khanabook_lite_db") && !it.endsWith("-wal") && !it.endsWith("-shm") && !it.endsWith("-journal") }
        Log.e("DB_DIAGNOSTIC", "Found databases: $dbNames")
        
        for (dbName in dbNames) {
            Log.e("DB_DIAGNOSTIC", "--------------------------------------------------")
            Log.e("DB_DIAGNOSTIC", "Dumping Database: $dbName")
            try {
                val db = Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    dbName
                )
                .openHelperFactory(factory)
                .build()

                val billDao = db.billDao()
                val allBills = kotlinx.coroutines.runBlocking { 
                    // Let's use a raw query or just fetch all bills by checking the DAO methods,
                    // or let's use a simple SupportSQLiteDatabase query to get all rows.
                    val list = mutableListOf<String>()
                    db.openHelper.readableDatabase.query("SELECT id, server_id, is_synced, order_status, payment_status, updated_at, server_updated_at, daily_order_display FROM bills").use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val serverId = if (cursor.isNull(1)) "null" else cursor.getLong(1).toString()
                            val isSynced = cursor.getInt(2)
                            val orderStatus = cursor.getString(3)
                            val paymentStatus = cursor.getString(4)
                            val updatedAt = cursor.getLong(5)
                            val serverUpdatedAt = cursor.getLong(6)
                            val dailyId = cursor.getString(7)
                            list.add("Bill: id=$id, serverId=$serverId, isSynced=$isSynced, orderStatus=$orderStatus, paymentStatus=$paymentStatus, updatedAt=$updatedAt, serverUpdatedAt=$serverUpdatedAt, dailyId=$dailyId")
                        }
                    }
                    list
                }
                Log.e("DB_DIAGNOSTIC", "Total bills: ${allBills.size}")
                allBills.forEach { billStr ->
                    Log.e("DB_DIAGNOSTIC", billStr)
                }
                db.close()
            } catch (e: Exception) {
                Log.e("DB_DIAGNOSTIC", "Error reading database $dbName", e)
            }
        }
        Log.e("DB_DIAGNOSTIC", "=== END DIAGNOSTIC ===")
    }
}
