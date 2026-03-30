package com.khanabook.lite.pos.ui.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.repository.*
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.domain.manager.BluetoothPrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val categoryRepository: CategoryRepository,
    private val menuRepository: MenuRepository,
    private val userRepository: UserRepository,
    private val btManager: BluetoothPrinterManager
) : ViewModel() {

    val profile: StateFlow<RestaurantProfileEntity?> = restaurantRepository.getProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val btDevices: StateFlow<List<BluetoothDevice>> = btManager.scannedDevices

    val btIsScanning: StateFlow<Boolean> = btManager.isScanning

    private val _btIsConnecting = MutableStateFlow(false)
    val btIsConnecting: StateFlow<Boolean> = _btIsConnecting.asStateFlow()

    private val _btConnectResult = MutableStateFlow<Boolean?>(null)
    val btConnectResult: StateFlow<Boolean?> = _btConnectResult.asStateFlow()

    val btIsConnected: StateFlow<Boolean> = btManager.isConnected

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProfile = restaurantRepository.getProfile()
            val mac = currentProfile?.printerMac
            if (currentProfile?.printerEnabled == true && !mac.isNullOrBlank() && !btManager.isConnected()) {
                btManager.connect(mac)
            }
        }
    }

    fun testPrint() {
        if (!btManager.isConnected()) {
            _btConnectResult.value = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val testData = (
                "\u001b\u0040" + 
                "\u001b\u0061\u0001" + 
                "KHANABOOK\n" +
                "PRINTER TEST OK\n" +
                "--------------------------------\n" +
                "\n\n\n\n" +
                "\u001d\u0056\u0042\u0000" 
            ).toByteArray(Charsets.US_ASCII)
            btManager.printBytes(testData)
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
    fun connectToPrinter(context: Context, device: BluetoothDevice) {
        _btConnectResult.value = null
        _btIsConnecting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val ok = btManager.connect(device)
            _btIsConnecting.value = false
            _btConnectResult.value = ok
            if (ok) {
                val name = try { device.name ?: "BT Printer" } catch (_: Exception) { "BT Printer" }
                val mac  = device.address
                val current = restaurantRepository.getProfile()
                current?.copy(printerName = name, printerMac = mac, printerEnabled = true)?.let {
                    restaurantRepository.saveProfile(it)
                }
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
                // Log and ignore to not block user on network error
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

    fun resetDailyCounter() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            restaurantRepository.resetDailyCounter(0, today)
        }
    }

    fun getItemsByCategory(categoryId: Long) = menuRepository.getItemsByCategoryFlow(categoryId)

    fun getMenuWithVariantsByCategory(categoryId: Long): kotlinx.coroutines.flow.Flow<List<MenuWithVariants>> {
        return menuRepository.getMenuWithVariantsByCategoryFlow(categoryId)
    }

}
