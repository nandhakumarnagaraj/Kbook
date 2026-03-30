package com.khanabook.lite.pos.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuViewModelTest {

    @Test
    fun `parseDraftsFromText should extract items from Bismi Biriyani menu text`() {
        val menuText = """
            BISMI BIRIYANI
            STARTER
            Full Half
            Bismi Fried Chicken - 380 200
            Bismi Tandoori Chicken - 400 210
            Bismi Spl Mutton Seek Kebab - 350
            Bismi Fish Tikka - 320
            BIRIYANI
            Chicken Biriyani - 170
            Mutton Biriyani - 200
            Fish Biriyani - 230
            Prawn Biriyani - 260
            Bismi Spl Chicken Biriyani - 230
            Bismi Spl Mutton Biriyani - 270
            Bismi Spl Prawn Biriyani - 280
            Bismi Spl Fish Biriyani - 290
        """.trimIndent()

        val drafts = MenuViewModel.parseDraftsFromText(menuText)
        drafts.forEach { println("Parsed: ${it.name}, price: ${it.price}, category: ${it.categoryName}, variants: ${it.variants}") }

        // Verify Starters
        val friedChicken = drafts.find { it.name == "Bismi Fried Chicken" }
        assertTrue("Fried Chicken not found", friedChicken != null)
        assertEquals(380.0, friedChicken!!.price, 0.0)
        assertEquals("non-veg", friedChicken.foodType)
        assertEquals(2, friedChicken.variants.size)
        assertEquals("Full", friedChicken.variants[0].name)
        assertEquals(380.0, friedChicken.variants[0].price, 0.0)
        assertEquals("Half", friedChicken.variants[1].name)
        assertEquals(200.0, friedChicken.variants[1].price, 0.0)

        // Verify Biriyanis (category switch should have occurred)
        val chickenBiriyani = drafts.find { it.name == "Chicken Biriyani" }
        assertTrue("Chicken Biriyani not found", chickenBiriyani != null)
        assertEquals(170.0, chickenBiriyani!!.price, 0.0)
        assertEquals("Biriyani", chickenBiriyani.categoryName)

        val fishBiriyani = drafts.find { it.name == "Bismi Spl Fish Biriyani" }
        assertTrue("Fish Biriyani not found", fishBiriyani != null)
        assertEquals(290.0, fishBiriyani!!.price, 0.0)
    }

    @Test
    fun `parseDraftsFromText should handle empty text gracefully`() {
        val drafts = MenuViewModel.parseDraftsFromText("")
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `parseDraftsFromText should skip lines without prices in local mode`() {
        val menuText = """
            Item One
            Item Two - 100
        """.trimIndent()
        val drafts = MenuViewModel.parseDraftsFromText(menuText)
        assertEquals(2, drafts.size)
        assertEquals("Item One", drafts[0].name)
        assertEquals(0.0, drafts[0].price, 0.0)
        assertEquals("Item Two", drafts[1].name)
        assertEquals(100.0, drafts[1].price, 0.0)
    }
}
