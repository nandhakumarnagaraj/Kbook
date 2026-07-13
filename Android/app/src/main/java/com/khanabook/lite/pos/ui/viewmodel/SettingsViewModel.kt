package com.khanabook.lite.pos.ui.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.dao.BillIdConflictBill
import com.khanabook.lite.pos.data.local.dao.BillIdDuplicateGroup
import com.khanabook.lite.pos.data.repository.*
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.domain.manager.BluetoothPrinterManager
import com.khanabook.lite.pos.domain.manager.KitchenPrintQueueManager
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import com.khanabook.lite.pos.domain.model.OrderPaymentFlowMode
import com.khanabook.lite.pos.domain.util.MultipartUtils
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DuplicateIdHealth(
    val duplicateInvoiceGroups: List<BillIdDuplicateGroup> = emptyList(),
    val duplicateDailyGroups: List<BillIdDuplicateGroup> = emptyList(),
    val conflictBills: List<BillIdConflictBill> = emptyList(),
    val isRepairing: Boolean = false,
    val lastRepairMessage: String? = null
) {
    val conflictGroupCount: Int
        get() = duplicateInvoiceGroups.size + duplicateDailyGroups.size
}

@HiltViewModel
@kotlinx.coroutines.ExperimentalCoroutinesApi
class SettingsViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val printerProfileRepository: PrinterProfileRepository,
    private val categoryRepository: CategoryRepository,
    private val menuRepository: MenuRepository,
    private val userRepository: UserRepository,
    private val billDao: BillDao,
    private val btManager: BluetoothPrinterManager,
    private val kitchenPrintQueueManager: KitchenPrintQueueManager,
    private val sessionManager: SessionManager,
    private val syncManager: SyncManager
) : ViewModel() {

    val displayScale = sessionManager.getDisplayScale()

    private val _displayScale = MutableStateFlow(displayScale)
    val displayScaleState: StateFlow<Float> = _displayScale.asStateFlow()

    fun updateDisplayScale(scale: Float) {
        _displayScale.value = scale
        sessionManager.setDisplayScale(scale)
    }

    val profile: StateFlow<RestaurantProfileEntity?> = restaurantRepository.getProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val printerProfiles: StateFlow<List<PrinterProfileEntity>> = printerProfileRepository.getProfilesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerPrinter: StateFlow<PrinterProfileEntity?> = printerProfiles
        .map { printers -> printers.firstOrNull { it.role == PrinterRole.CUSTOMER.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val kitchenPrinter: StateFlow<PrinterProfileEntity?> = printerProfiles
        .map { printers -> printers.firstOrNull { it.role == PrinterRole.KITCHEN.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val btDevices: StateFlow<List<BluetoothDevice>> = btManager.scannedDevices

    val btIsScanning: StateFlow<Boolean> = btManager.isScanning

    private val _btIsConnecting = MutableStateFlow(false)
    val btIsConnecting: StateFlow<Boolean> = _btIsConnecting.asStateFlow()

    private val _btConnectResult = MutableStateFlow<Boolean?>(null)
    val btConnectResult: StateFlow<Boolean?> = _btConnectResult.asStateFlow()

    /** MAC address of the currently connected Bluetooth printer, or null if disconnected. */
    val connectedPrinterMac: StateFlow<String?> = btManager.connectedDeviceMac

    /** All printer MAC addresses currently connected at the Bluetooth ACL level. */
    val connectedPrinterMacs: StateFlow<Set<String>> = btManager.connectedDeviceMacs

    val printerStatusRoles: StateFlow<Set<String>> = combine(
        printerProfiles,
        connectedPrinterMacs
    ) { profiles, liveMacs ->
        profiles
            .filter { !it.macAddress.isNullOrBlank() && liveMacs.contains(it.macAddress) }
            .map { it.role }
            .toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _failedBillSyncs = MutableStateFlow<List<BillEntity>>(emptyList())
    val failedBillSyncs: StateFlow<List<BillEntity>> = _failedBillSyncs.asStateFlow()

    private val _retryingFailedBillIds = MutableStateFlow<Set<Long>>(emptySet())
    val retryingFailedBillIds: StateFlow<Set<Long>> = _retryingFailedBillIds.asStateFlow()

    private val _duplicateIdHealth = MutableStateFlow(DuplicateIdHealth())
    val duplicateIdHealth: StateFlow<DuplicateIdHealth> = _duplicateIdHealth.asStateFlow()

    private val _cancellingConflictBillIds = MutableStateFlow<Set<Long>>(emptySet())
    val cancellingConflictBillIds: StateFlow<Set<Long>> = _cancellingConflictBillIds.asStateFlow()

    private val _syncCenterMessage = MutableStateFlow<String?>(null)
    val syncCenterMessage: StateFlow<String?> = _syncCenterMessage.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(sessionManager.getLastSyncTimestamp())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    val quarantinedSyncRecords: StateFlow<List<SyncQuarantineEntity>> = sessionManager.restaurantId
        .flatMapLatest { restaurantId ->
            if (restaurantId > 0L) {
                billDao.getSyncQuarantineRecordsFlow(restaurantId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            printerProfileRepository.getProfiles()
                .filter { it.enabled && !it.macAddress.isNullOrBlank() }
                .forEach { profile ->
                    // Each printer in its own coroutine — an offline printer's Bluetooth
                    // timeout won't delay the other printer connecting.
                    launch {
                        if (!btManager.isConnectedTo(profile.macAddress)) {
                            btManager.connect(profile.macAddress)
                        }
                    }
                }
        }
        refreshFailedBillSyncs()
        refreshDuplicateIdHealth()
        refreshLastSyncTimestamp()
    }

    fun refreshFailedBillSyncs() {
        viewModelScope.launch(Dispatchers.IO) {
            val restaurantId = sessionManager.getRestaurantId()
            _failedBillSyncs.value = if (restaurantId > 0L) {
                billDao.getPermanentlyFailedBills(restaurantId)
            } else {
                emptyList()
            }
        }
    }

    fun refreshDuplicateIdHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            loadDuplicateIdHealth()
        }
    }

    fun refreshLastSyncTimestamp() {
        _lastSyncTimestamp.value = sessionManager.getLastSyncTimestamp()
    }

    fun repairOrderIdCounters() {
        viewModelScope.launch(Dispatchers.IO) {
            val previous = _duplicateIdHealth.value
            _duplicateIdHealth.value = previous.copy(isRepairing = true, lastRepairMessage = null)
            try {
                val restaurantId = sessionManager.getRestaurantId()
                if (restaurantId <= 0L) {
                    _duplicateIdHealth.value = previous.copy(
                        isRepairing = false,
                        lastRepairMessage = "Restaurant is not ready yet. Try again after login."
                    )
                    return@launch
                }

                val zoneId = java.time.ZoneId.of("Asia/Kolkata")
                val today = java.time.LocalDate.now(zoneId)
                val startTime = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endTime = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                val maxDailyToday = billDao.getMaxDailyOrderIdBetween(restaurantId, sessionManager.getDeviceId(), startTime, endTime)

                // Only the daily order counter is repaired now. Invoice numbers use the
                // per-terminal invoice sequence, not the legacy lifetime counter.
                restaurantRepository.raiseDailyCounterAtLeast(
                    dailyCounter = maxDailyToday,
                    date = today.toString()
                )
                loadDuplicateIdHealth(
                    lastRepairMessage = "Future duplicates prevented. Next order will use #${maxDailyToday + 1}."
                )
            } catch (e: Exception) {
                _duplicateIdHealth.value = previous.copy(
                    isRepairing = false,
                    lastRepairMessage = UserMessageSanitizer.sanitize(
                        e,
                        "Unable to repair counters. Please try again."
                    )
                )
            }
        }
    }

    fun cancelDuplicateConflictBill(billId: Long) {
        viewModelScope.launch {
            _cancellingConflictBillIds.value = _cancellingConflictBillIds.value + billId
            _syncCenterMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    val restaurantId = sessionManager.getRestaurantId()
                    val bill = billDao.getBillById(billId, restaurantId)
                        ?: throw IllegalStateException("Bill not found.")
                    if (bill.orderStatus.equals("completed", ignoreCase = true) ||
                        bill.orderStatus.equals("paid", ignoreCase = true)
                    ) {
                        throw IllegalStateException("Completed bills need manual review. They were not changed.")
                    }
                    billDao.cancelBill(
                        id = billId,
                        reason = "Marked duplicate from Sync Center",
                        updatedAt = System.currentTimeMillis(),
                        restaurantId = restaurantId
                    )
                }
                syncManager.pushUnsyncedDataWithResult()
                _syncCenterMessage.value = "Duplicate bill marked cancelled."
            } catch (e: Exception) {
                _syncCenterMessage.value = UserMessageSanitizer.sanitize(
                    e,
                    "Unable to cancel this duplicate bill."
                )
            } finally {
                _cancellingConflictBillIds.value = _cancellingConflictBillIds.value - billId
                refreshDuplicateIdHealth()
                refreshFailedBillSyncs()
                refreshLastSyncTimestamp()
            }
        }
    }

    private suspend fun loadDuplicateIdHealth(lastRepairMessage: String? = _duplicateIdHealth.value.lastRepairMessage) {
        val restaurantId = sessionManager.getRestaurantId()
        _duplicateIdHealth.value = if (restaurantId > 0L) {
            DuplicateIdHealth(
                duplicateInvoiceGroups = billDao.getDuplicateInvoiceNumberGroups(restaurantId),
                duplicateDailyGroups = billDao.getDuplicateDailyOrderGroups(restaurantId),
                conflictBills = billDao.getDuplicateIdConflictBills(restaurantId),
                isRepairing = false,
                lastRepairMessage = lastRepairMessage
            )
        } else {
            DuplicateIdHealth(lastRepairMessage = lastRepairMessage)
        }
    }

    fun retryFailedBillSync(billId: Long) {
        viewModelScope.launch {
            _retryingFailedBillIds.value = _retryingFailedBillIds.value + billId
            try {
                withContext(Dispatchers.IO) {
                    val restaurantId = sessionManager.getRestaurantId()
                    if (restaurantId > 0L) {
                        billDao.retryFailedBillSync(billId, restaurantId)
                    }
                }
                syncManager.pushUnsyncedDataWithResult()
            } finally {
                _retryingFailedBillIds.value = _retryingFailedBillIds.value - billId
                refreshFailedBillSyncs()
            }
        }
    }

    fun retryAllFailedBillSyncs() {
        viewModelScope.launch {
            val billsToRetry = _failedBillSyncs.value
            if (billsToRetry.isEmpty()) return@launch

            val billIds = billsToRetry.map { it.id }.toSet()
            _retryingFailedBillIds.value = _retryingFailedBillIds.value + billIds
            try {
                withContext(Dispatchers.IO) {
                    val restaurantId = sessionManager.getRestaurantId()
                    if (restaurantId > 0L) {
                        billsToRetry.forEach { bill ->
                            billDao.retryFailedBillSync(bill.id, restaurantId)
                        }
                    }
                }
                syncManager.pushUnsyncedDataWithResult()
            } finally {
                _retryingFailedBillIds.value = _retryingFailedBillIds.value - billIds
                refreshFailedBillSyncs()
            }
        }
    }

    fun testPrint(role: PrinterRole) {
        viewModelScope.launch(Dispatchers.IO) {
            val printer = printerProfileRepository.getByRole(role.name)
            if (printer == null) {
                _btConnectResult.value = false
                return@launch
            }
            if (!btManager.connect(printer.macAddress)) {
                _btConnectResult.value = false
                return@launch
            }
            val testData = (
                "\u001b\u0040" +
                "\u001b\u0061\u0001" +
                "KHANABOOK\n" +
                "${role.name} PRINTER TEST OK\n" +
                "--------------------------------\n" +
                "\n\n\n\n" +
                "\u001d\u0056\u0042\u0000"
            ).toByteArray(Charsets.US_ASCII)
            try {
                btManager.printBytesTo(printer.macAddress, testData)
            } finally {
                btManager.disconnect(printer.macAddress)
            }
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return btManager.isBluetoothEnabled()
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return btManager.hasRequiredPermissions()
    }

    fun startBluetoothScan(context: Context) {
        btManager.startScan()
    }

    fun stopBluetoothScan() {
        btManager.stopScan()
    }

    @Suppress("MissingPermission")
    fun connectToPrinter(context: Context, device: BluetoothDevice, role: PrinterRole, paperSize: String, includeLogo: Boolean) {
        _btConnectResult.value = null
        _btIsConnecting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val ok = btManager.connect(device)
            _btIsConnecting.value = false
            _btConnectResult.value = ok
            if (ok) {
                val name = try { device.name ?: "BT Printer" } catch (_: Exception) { "BT Printer" }
                val mac  = device.address
                val existing = printerProfileRepository.getByRole(role.name)
                printerProfileRepository.saveProfile(
                    PrinterProfileEntity(
                        id = existing?.id ?: 0,
                        role = role.name,
                        name = name,
                        macAddress = mac,
                        enabled = true,
                        autoPrint = existing?.autoPrint ?: true,
                        paperSize = paperSize,
                        includeLogo = includeLogo,
                        copies = existing?.copies ?: 1,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                )
                if (role == PrinterRole.CUSTOMER) {
                    val current = restaurantRepository.getProfile()
                    current?.copy(printerName = name, printerMac = mac, printerEnabled = true)?.let {
                        restaurantRepository.saveProfile(it)
                    }
                } else if (role == PrinterRole.KITCHEN) {
                    kitchenPrintQueueManager.flushPendingForPrinter(mac)
                }
            }
        }
    }

    fun updatePrinterProfile(
        role: PrinterRole,
        enabled: Boolean,
        autoPrint: Boolean,
        paperSize: String,
        includeLogo: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = printerProfileRepository.getByRole(role.name) ?: return@launch
            printerProfileRepository.saveProfile(
                existing.copy(
                    enabled = enabled,
                    autoPrint = autoPrint,
                    paperSize = paperSize,
                    includeLogo = includeLogo
                )
            )
            if (role == PrinterRole.CUSTOMER) {
                restaurantRepository.getProfile()?.copy(
                    printerEnabled = enabled,
                    printerName = existing.name,
                    printerMac = existing.macAddress,
                    paperSize = paperSize,
                    includeLogoInPrint = includeLogo,
                    autoPrintOnSuccess = autoPrint
                )?.let { restaurantRepository.saveProfile(it) }
            }
        }
    }

    fun removePrinter(role: PrinterRole) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = printerProfileRepository.getByRole(role.name)
            existing?.macAddress?.let { mac -> btManager.disconnect(mac) }
            printerProfileRepository.deleteByRole(role.name)
            if (role == PrinterRole.CUSTOMER) {
                restaurantRepository.getProfile()?.copy(
                    printerEnabled = false,
                    printerName = null,
                    printerMac = null
                )?.let { restaurantRepository.saveProfile(it) }
            }
        }
    }

    fun clearBtConnectResult() {
        _btConnectResult.value = null
    }

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveProfileLoading = MutableStateFlow(false)
    val saveProfileLoading: StateFlow<Boolean> = _saveProfileLoading.asStateFlow()

    private val _saveProfileError = MutableStateFlow<String?>(null)
    val saveProfileError: StateFlow<String?> = _saveProfileError.asStateFlow()

    private val _saveProfileSuccess = MutableStateFlow(false)
    val saveProfileSuccess: StateFlow<Boolean> = _saveProfileSuccess.asStateFlow()

    private val _logoUploadLoading = MutableStateFlow(false)
    val logoUploadLoading: StateFlow<Boolean> = _logoUploadLoading.asStateFlow()

    private val _isUserChecking = MutableStateFlow(false)
    val isUserChecking: StateFlow<Boolean> = _isUserChecking.asStateFlow()

    private val _userExistsError = MutableStateFlow<String?>(null)
    val userExistsError: StateFlow<String?> = _userExistsError.asStateFlow()

    fun checkUserExists(phoneNumber: String, currentPhoneNumber: String) {
        if (phoneNumber.length != 10 || phoneNumber == currentPhoneNumber) {
            _userExistsError.value = null
            return
        }
        
        viewModelScope.launch {
            _isUserChecking.value = true
            _userExistsError.value = null
            try {
                val exists = userRepository.checkUserExistsRemotely(phoneNumber)
                if (exists) {
                    _userExistsError.value = "An account with this number already exists."
                }
            } catch (e: Exception) {
                Log.w("SettingsViewModel", "Remote user existence check failed", e)
            } finally {
                _isUserChecking.value = false
            }
        }
    }

    fun clearUserCheck() {
        _userExistsError.value = null
        _isUserChecking.value = false
    }

    fun clearSaveProfileState() {
        _saveProfileError.value = null
        _saveProfileSuccess.value = false
    }

    fun clearLogoUploadState() {
        _logoUploadError.value = null
    }

    fun saveProfile(profile: RestaurantProfileEntity) {
        viewModelScope.launch {
            _saveProfileLoading.value = true
            _saveProfileError.value = null
            _saveProfileSuccess.value = false

            val newNumber = profile.whatsappNumber ?: ""

            restaurantRepository.saveProfile(profile)
            
            userRepository.currentUser.value?.let { current ->
                userRepository.updateWhatsappNumber(current.id, newNumber)
                
                userRepository.setCurrentUser(current.copy(
                    whatsappNumber = newNumber
                ))
            }
            _saveProfileSuccess.value = true
            _saveProfileLoading.value = false
        }
    }

    private val _logoUploadError = MutableStateFlow<String?>(null)
    val logoUploadError: StateFlow<String?> = _logoUploadError.asStateFlow()

    fun uploadLogo(context: Context, uri: Uri, onUploaded: (String) -> Unit) {
        viewModelScope.launch {
            _logoUploadLoading.value = true
            _logoUploadError.value = null
            _saveProfileError.value = null
            try {
                val part = withContext(Dispatchers.IO) {
                    MultipartUtils.imageUriToPart(context.applicationContext, uri)
                }
                val url = restaurantRepository.uploadLogo(part)
                onUploaded(url)
            } catch (e: Exception) {
                _logoUploadError.value = UserMessageSanitizer.sanitize(e, "Logo upload failed. Please try again.")
            } finally {
                _logoUploadLoading.value = false
            }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            categoryRepository.insertCategory(CategoryEntity(name = name, isVeg = true, createdAt = System.currentTimeMillis()))
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categoryRepository.updateCategory(category)
        }
    }

    fun addItem(categoryId: Long, name: String, price: Double, type: String, stock: Double) {
        viewModelScope.launch {
            menuRepository.insertItem(MenuItemEntity(
                categoryId = categoryId,
                name = name,
                basePrice = price.toString(),
                foodType = type,
                currentStock = stock.toString(),
                createdAt = System.currentTimeMillis()
            ))
        }
    }

    fun updateItem(item: MenuItemEntity) {
        viewModelScope.launch {
            menuRepository.updateItem(item)
        }
    }

    fun toggleItemAvailability(id: Long, isAvailable: Boolean) {
        viewModelScope.launch {
            menuRepository.toggleItemAvailability(id, isAvailable)
        }
    }

    fun deleteItem(item: MenuItemEntity) {
        viewModelScope.launch {
            menuRepository.deleteItem(item)
        }
    }

    fun updateAutoPrint(enabled: Boolean) {
        viewModelScope.launch {
            val current = restaurantRepository.getProfile()
            current?.copy(autoPrintOnSuccess = enabled)?.let {
                restaurantRepository.saveProfile(it)
            }
        }
    }

    fun updateMaskCustomerPhone(enabled: Boolean) {
        viewModelScope.launch {
            val current = restaurantRepository.getProfile()
            current?.copy(maskCustomerPhone = enabled)?.let {
                restaurantRepository.saveProfile(it)
            }
        }
    }

    fun updateShowBranding(enabled: Boolean) {
        viewModelScope.launch {
            val current = restaurantRepository.getProfile()
            current?.copy(showBranding = enabled)?.let {
                restaurantRepository.saveProfile(it)
            }
        }
    }

    fun updateOrderPaymentFlowMode(mode: OrderPaymentFlowMode) {
        viewModelScope.launch {
            val current = restaurantRepository.getProfile()
            current?.copy(orderPaymentFlowMode = mode.dbValue)?.let {
                restaurantRepository.saveProfile(it)
            }
        }
    }

    fun updatePaperSize(size: String) {
        viewModelScope.launch {
            val current = restaurantRepository.getProfile()
            current?.copy(paperSize = size)?.let {
                restaurantRepository.saveProfile(it)
            }
        }
    }

    fun getItemsByCategory(categoryId: Long) = menuRepository.getItemsByCategoryFlow(categoryId)

    fun getMenuWithVariantsByCategory(categoryId: Long): kotlinx.coroutines.flow.Flow<List<MenuWithVariants>> {
        return menuRepository.getMenuWithVariantsByCategoryFlow(categoryId)
    }

    fun getLastSyncTimestamp(): Long = sessionManager.getLastSyncTimestamp()

}
