package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.repository.MenuRepository
import com.khanabook.lite.pos.domain.manager.BillCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages cart state (items, quantities, notes) and bill summary computation.
 * Extracted from BillingViewModel to reduce file size and isolate cart logic.
 *
 * This class is NOT a ViewModel — it's a plain state holder owned by BillingViewModel.
 * It does not have its own lifecycle or scope.
 */
class CartManager(
    private val menuRepository: MenuRepository,
    initialItems: List<BillingViewModel.CartItem> = emptyList()
) {
    private val tag = "CartManager"

    private val _cartItems = MutableStateFlow(initialItems)
    val cartItems: StateFlow<List<BillingViewModel.CartItem>> = _cartItems

    private val _billSummary = MutableStateFlow(BillingViewModel.BillSummary())
    val billSummary: StateFlow<BillingViewModel.BillSummary> = _billSummary

    fun setItems(items: List<BillingViewModel.CartItem>) {
        _cartItems.value = items
    }

    fun clear() {
        _cartItems.value = emptyList()
    }

    val currentItems: List<BillingViewModel.CartItem> get() = _cartItems.value

    suspend fun addToCart(item: MenuItemEntity, variant: ItemVariantEntity? = null) {
        val latestItem = menuRepository.getItemById(item.id) ?: item
        _cartItems.update { current ->
            val mutable = current.toMutableList()
            val existing = mutable.find { it.item.id == item.id && it.variant?.id == variant?.id }

            if (existing != null) {
                val idx = mutable.indexOf(existing)
                mutable[idx] = existing.copy(quantity = existing.quantity + 1)
            } else {
                mutable.add(BillingViewModel.CartItem(latestItem, variant, 1))
            }
            mutable
        }
    }

    fun removeFromCart(item: MenuItemEntity, variant: ItemVariantEntity? = null) {
        _cartItems.update { current ->
            val mutable = current.toMutableList()
            val existing = mutable.find { it.item.id == item.id && it.variant?.id == variant?.id }
            if (existing != null) {
                val index = mutable.indexOf(existing)
                if (existing.quantity > 1) {
                    mutable[index] = existing.copy(quantity = existing.quantity - 1)
                } else {
                    mutable.removeAt(index)
                }
            }
            mutable
        }
    }

    fun updateItemNote(item: MenuItemEntity, variant: ItemVariantEntity?, note: String) {
        _cartItems.update { current ->
            current.map {
                if (it.item.id == item.id && it.variant?.id == variant?.id) it.copy(note = note)
                else it
            }
        }
    }

    suspend fun handleScannedBarcode(barcode: String): String? {
        val menuItem = menuRepository.getMenuItemByCode(barcode)
        return if (menuItem != null) {
            addToCart(menuItem)
            null
        } else {
            "No item found for barcode: $barcode"
        }
    }

    suspend fun addItemByScannedText(text: String) {
        val allItems = menuRepository.getAllMenuItemsOnce()
        val allVariants = menuRepository.getAllVariantsOnce()

        val lines = text.split("\n", "\r").map { it.trim() }.filter { it.length > 2 }

        for (line in lines) {
            val itemMatch = allItems.find { it.name.equals(line, ignoreCase = true) }
            if (itemMatch != null) {
                addToCart(itemMatch)
                continue
            }

            val variantMatch = allVariants.find { it.variantName.equals(line, ignoreCase = true) }
            if (variantMatch != null) {
                val parentItem = allItems.find { it.id == variantMatch.menuItemId }
                if (parentItem != null) {
                    addToCart(parentItem, variantMatch)
                    continue
                }
            }

            val partialItem = allItems.find { line.contains(it.name, ignoreCase = true) }
            if (partialItem != null) {
                val partialVariant = allVariants.filter { it.menuItemId == partialItem.id }
                    .find { line.contains(it.variantName, ignoreCase = true) }
                addToCart(partialItem, partialVariant)
            }
        }
    }

    /**
     * Pure function — computes a BillSummary from current cart items and profile.
     * No DB access, no side-effects.
     */
    fun computeSummary(items: List<BillingViewModel.CartItem>, profile: RestaurantProfileEntity?): BillingViewModel.BillSummary {
        val subtotal = BillCalculator.calculateSubtotal(items.map {
            (it.variant?.price ?: it.item.basePrice) to it.quantity
        })

        var cgst = "0.0"
        var sgst = "0.0"
        var customTax = "0.0"

        if (profile?.gstEnabled == true) {
            val gst = BillCalculator.calculateGST(subtotal, profile.gstPercentage)
            cgst = gst.cgst
            sgst = gst.sgst
        } else if (profile?.customTaxPercentage != null && profile.customTaxPercentage > 0) {
            customTax = BillCalculator.calculateCustomTax(subtotal, profile.customTaxPercentage)
        }

        val total = BillCalculator.calculateTotal(subtotal, cgst, sgst, customTax)
        return BillingViewModel.BillSummary(subtotal, cgst, sgst, customTax, total)
    }

    fun updateSummary(profile: RestaurantProfileEntity?) {
        _billSummary.value = computeSummary(_cartItems.value, profile)
    }

    fun setSummary(summary: BillingViewModel.BillSummary) {
        _billSummary.value = summary
    }
}
