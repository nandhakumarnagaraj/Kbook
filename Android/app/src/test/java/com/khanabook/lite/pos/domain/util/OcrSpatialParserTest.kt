package com.khanabook.lite.pos.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for OcrSpatialParser's classification/validation seams.
 * These avoid faking ML Kit Text/Rect by testing the framework-free helpers directly.
 */
class OcrSpatialParserTest {

    // ── foodType classification ────────────────────────────────────────────

    @Test
    fun `paneer dishes are vegetarian (was misclassified as non-veg)`() {
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Paneer Tikka"))
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Paneer Butter Masala"))
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Kadai Paneer"))
    }

    @Test
    fun `common veg staples are vegetarian`() {
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Aloo Paratha"))
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Dal Tadka"))
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Butter Naan"))
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Gobi Manchurian"))
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Veg Biryani"))
    }

    @Test
    fun `unambiguous non-veg proteins are non-veg`() {
        assertEquals("non-veg", OcrSpatialParser.classifyFoodType("Chicken Biryani"))
        assertEquals("non-veg", OcrSpatialParser.classifyFoodType("Mutton Seekh Kebab"))
        assertEquals("non-veg", OcrSpatialParser.classifyFoodType("Fish Fry"))
        assertEquals("non-veg", OcrSpatialParser.classifyFoodType("Prawn Masala"))
        assertEquals("non-veg", OcrSpatialParser.classifyFoodType("Egg Curry"))
    }

    @Test
    fun `veg marker wins over ambiguous tikka`() {
        // "tikka" appears in both paneer tikka (veg) and chicken tikka (non-veg);
        // an explicit veg marker must win.
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Paneer Tikka"))
        assertEquals("non-veg", OcrSpatialParser.classifyFoodType("Chicken Tikka"))
    }

    @Test
    fun `unknown item defaults to veg`() {
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Masala Dosa"))
        assertEquals("veg", OcrSpatialParser.classifyFoodType("Cold Coffee"))
    }

    // ── price plausibility ─────────────────────────────────────────────────

    @Test
    fun `plausible prices are within band`() {
        assertTrue(OcrSpatialParser.isPlausiblePrice(1.0))
        assertTrue(OcrSpatialParser.isPlausiblePrice(150.0))
        assertTrue(OcrSpatialParser.isPlausiblePrice(100000.0))
    }

    @Test
    fun `implausible prices are rejected`() {
        assertFalse("zero is not a price", OcrSpatialParser.isPlausiblePrice(0.0))
        assertFalse("below one rupee rejected", OcrSpatialParser.isPlausiblePrice(0.5))
        assertFalse("above band rejected", OcrSpatialParser.isPlausiblePrice(100001.0))
        assertFalse("negative rejected", OcrSpatialParser.isPlausiblePrice(-50.0))
    }
}
