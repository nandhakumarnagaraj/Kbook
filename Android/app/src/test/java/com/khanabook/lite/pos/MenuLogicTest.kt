package com.khanabook.lite.pos

import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import org.junit.Assert.*
import org.junit.Test

class MenuLogicTest {

    @Test
    fun testDuplicateCategoryCheck() {
        val existingCategories = listOf("Testing", "Food")

        fun isDuplicate(name: String): Boolean {
            return existingCategories.any { it.equals(name, ignoreCase = true) }
        }

        assertTrue("Should be duplicate", isDuplicate("testing"))
        assertTrue("Should be duplicate", isDuplicate("TESTING"))
        assertFalse("Should not be duplicate", isDuplicate("New Category"))
    }

    @Test
    fun testMenuImportParserHandlesCommonPriceFormats() {
        val drafts = MenuViewModel.parseDraftsFromText(
            """
            1. Paneer Butter Masala - 220
            Chicken Biryani Rs 180
            Masala Dosa Rs 90
            Filter Coffee INR 45
            Plain Naan 35
            """.trimIndent()
        )

        assertEquals(5, drafts.size)
        assertEquals("Paneer Butter Masala", drafts[0].name)
        assertEquals(220.0, drafts[0].price, 0.001)
        assertEquals("Chicken Biryani", drafts[1].name)
        assertEquals(180.0, drafts[1].price, 0.001)
        assertEquals("Masala Dosa", drafts[2].name)
        assertEquals(90.0, drafts[2].price, 0.001)
        assertEquals("Filter Coffee", drafts[3].name)
        assertEquals(45.0, drafts[3].price, 0.001)
        assertEquals("Plain Naan", drafts[4].name)
        assertEquals(35.0, drafts[4].price, 0.001)
    }

    @Test
    fun testMenuImportParserPreservesEditableLinesWithoutPrices() {
        val drafts = MenuViewModel.parseDraftsFromText(
            """
            MENU
            2) Veg Pulao - 140
            Today's Special Soup
            """.trimIndent()
        )

        assertEquals(2, drafts.size)
        assertEquals("Veg Pulao", drafts[0].name)
        assertEquals(140.0, drafts[0].price, 0.001)
        assertEquals("Today's Special Soup", drafts[1].name)
        assertEquals(0.0, drafts[1].price, 0.001)
    }

    @Test
    fun testMenuImportParserHandlesBulletsAndCurrencySymbols() {
        val drafts = MenuViewModel.parseDraftsFromText(
            """
            MENU
            - ₹125 Tandoori Roti
            * Rs. 99 Butter Naan
            Paneer Lacha ₹152.50
            PAGE 2
            45 Masala Chai
            """.trimIndent()
        )

        assertEquals(4, drafts.size)
        assertEquals("Tandoori Roti", drafts[0].name)
        assertEquals(125.0, drafts[0].price, 0.001)
        assertEquals("Butter Naan", drafts[1].name)
        assertEquals(99.0, drafts[1].price, 0.001)
        assertEquals("Paneer Lacha", drafts[2].name)
        assertEquals(152.5, drafts[2].price, 0.001)
        assertEquals("Masala Chai", drafts[3].name)
        assertEquals(45.0, drafts[3].price, 0.001)
    }
}
