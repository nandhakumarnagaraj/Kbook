package com.khanabook.lite.pos.ui.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.repository.*
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.domain.manager.BluetoothPrinterManager
import com.khanabook.lite.pos.domain.manager.KitchenPrintQueueManager
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.manager.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val printerProfileRepository: PrinterProfileRepository,
    private val categoryRepository: CategoryRepository,
    private val menuRepository: MenuRepository,
    private val userRepository: UserRepository,
    private val btManager: BluetoothPrinterManager,
    private val kitchenPrintQueueManager: KitchenPrintQueueManager,
    private val sessionManager: SessionManager
) : ViewModel() {

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

    val btIsConnected: StateFlow<Boolean> = btManager.isConnected

    /** MAC address of the currently connected Bluetooth printer, or null if disconnected. */
    val connectedPrinterMac: StateFlow<String?> = btManager.connectedDeviceMac

    /** All printer MAC addresses currently connected at the Bluetooth ACL level. */
    val connectedPrinterMacs: StateFlow<Set<String>> = btManager.connectedDeviceMacs

    private val _stickyConnectedPrinterRoles = MutableStateFlow<Set<String>>(emptySet())
    val printerStatusRoles: StateFlow<Set<String>> = combine(
        printerProfiles,
        connectedPrinterMacs,
        _stickyConnectedPrinterRoles
    ) { profiles, liveMacs, stickyRoles ->
        val liveRoles = profiles
            .filter { !it.macAddress.isNullOrBlank() && liveMacs.contains(it.macAddress) }
            .map { it.role }
            .toSet()
        liveRoles + stickyRoles
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProfile = restaurantRepository.getProfile()
            val mac = currentProfile?.printerMac
            if (currentProfile?.printerEnabled == true && !mac.isNullOrBlank() && !btManager.isConnected()) {
                btManager.connect(mac)
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
            btManager.printBytes(testData)
            _stickyConnectedPrinterRoles.value = _stickyConnectedPrinterRoles.value + role.name
            btManager.disconnect()
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
                _stickyConnectedPrinterRoles.value = _stickyConnectedPrinterRoles.value + role.name
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
            btManager.disconnect()
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
            printerProfileRepository.deleteByRole(role.name)
            _stickyConnectedPrinterRoles.value = _stickyConnectedPrinterRoles.value - role.name
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
