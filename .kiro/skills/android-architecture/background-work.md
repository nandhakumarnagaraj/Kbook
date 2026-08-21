# Background Work Patterns

## Trigger Conditions
- Implementing data sync between local DB and server
- Scheduling periodic tasks (report generation, cleanup)
- Handling long-running operations that survive app kill
- User asks about WorkManager, services, or Doze mode
- Implementing retry logic for network operations

---

## WorkManager Patterns (Primary Choice)

### Sync Worker (KhanaBook)

```kotlin
@HiltWorker
class BillSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val billRepository: BillRepository,
    private val connectivityManager: ConnectivityManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Respect retry limits
        if (runAttemptCount > 3) return Result.failure()

        return try {
            val syncResult = billRepository.syncPendingBills()
            syncResult.fold(
                onSuccess = { count ->
                    setProgress(workDataOf("synced" to count))
                    Result.success(workDataOf("synced" to count))
                },
                onFailure = { Result.retry() }
            )
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to e.message))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            SYNC_NOTIFICATION_ID,
            createSyncNotification("Syncing bills...")
        )
    }
}
```

### Scheduling Workers

```kotlin
@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    // One-time sync (after bill creation)
    fun scheduleBillSync(billId: String) {
        val request = OneTimeWorkRequestBuilder<BillSyncWorker>()
            .setInputData(workDataOf("billId" to billId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                Duration.ofSeconds(30)
            )
            .build()

        workManager.enqueueUniqueWork(
            "bill_sync_$billId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    // Periodic sync (every 15 min when online)
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<FullSyncWorker>(
            repeatInterval = 15, TimeUnit.MINUTES,
            flexInterval = 5, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // Chained work: sync bills → sync menu → generate report
    fun scheduleFullSync() {
        val billSync = OneTimeWorkRequestBuilder<BillSyncWorker>().build()
        val menuSync = OneTimeWorkRequestBuilder<MenuSyncWorker>().build()
        val report = OneTimeWorkRequestBuilder<DailyReportWorker>().build()

        workManager.beginUniqueWork("full_sync", ExistingWorkPolicy.REPLACE, billSync)
            .then(menuSync)
            .then(report)
            .enqueue()
    }
}
```

### Observing Work Status

```kotlin
// In ViewModel
val syncStatus: Flow<SyncState> = workManager
    .getWorkInfosForUniqueWorkFlow("periodic_sync")
    .map { workInfos ->
        when (workInfos.firstOrNull()?.state) {
            WorkInfo.State.RUNNING -> SyncState.Syncing
            WorkInfo.State.SUCCEEDED -> SyncState.Synced
            WorkInfo.State.FAILED -> SyncState.Failed
            else -> SyncState.Idle
        }
    }
```

---

## Foreground Service Types (Android 14+)

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".service.SyncForegroundService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

Use foreground services ONLY for user-visible operations (bulk sync progress).
Prefer WorkManager with `setExpedited()` for most cases.

```kotlin
// Expedited work (replaces foreground service for short tasks)
val request = OneTimeWorkRequestBuilder<BillSyncWorker>()
    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    .build()
```

---

## Doze Mode Handling

**Facts:**
- Doze defers network, alarms, JobScheduler, and syncs
- WorkManager respects Doze — work runs in maintenance windows
- Exact alarms bypass Doze but require `SCHEDULE_EXACT_ALARM` permission

**KhanaBook Strategy:**
- Bills save locally immediately (Room) — no network dependency
- Sync happens when device exits Doze (WorkManager handles this)
- Daily report generation uses `flex` interval to run in maintenance window
- Never rely on exact timing for sync — use constraints instead

```kotlin
// DO: Let WorkManager handle Doze
// DON'T: Use AlarmManager for sync operations

// Exact alarms ONLY for user-facing reminders
class ShiftReminderScheduler @Inject constructor(
    private val alarmManager: AlarmManager,
    @ApplicationContext private val context: Context
) {
    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun scheduleShiftReminder(time: LocalTime) {
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            // Redirect to system settings
            return
        }
        val intent = PendingIntent.getBroadcast(
            context, SHIFT_ALARM_ID,
            Intent(context, ShiftReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.toEpochMillis(),
            intent
        )
    }
}
```

---

## Anti-patterns
- ❌ Using `Thread` or raw coroutines for work that must survive process death
- ❌ Foreground service for background sync (use WorkManager with expedited)
- ❌ Exact alarms for sync operations (wastes battery, breaks on Android 12+)
- ❌ Not handling `Result.retry()` with backoff (infinite fast retries)
- ❌ Ignoring `runAttemptCount` (let workers fail after N retries)
- ❌ Enqueueing duplicate work without `UniqueWork` policy

## Verification Checklist
- [ ] All sync operations use WorkManager (not raw coroutines)
- [ ] Network constraints set for all remote operations
- [ ] Exponential backoff configured for retries
- [ ] Unique work policies prevent duplicate scheduling
- [ ] Foreground service type declared in manifest (Android 14+)
- [ ] Worker is `@HiltWorker` annotated for DI
- [ ] Work status observable from UI via Flow
- [ ] Tested with airplane mode toggle and app kill
