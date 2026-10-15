package com.khanabook.lite.pos.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khanabook.lite.pos.core.database.AppDatabase
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The favourites list is expressed entirely in SQL, so it can only be verified against a real
 * SQLite (this is the reason it is an instrumentation test and not a JVM one: the ordering
 * claim - the shop's pins, then this till's own stars, then A-Z - lives in the ORDER BY).
 *
 * Two things are pinned here, because either one silently breaks the feature:
 *   - a dish carrying BOTH flags leads the list (a shop pin is stronger than a counter star),
 *     and a starred dish never appears twice;
 *   - unstarred and soft-deleted dishes stay out of the list, so the "wishlist" cannot show a
 *     dish the owner has retired.
 *
 * The per-category query is asserted next to it: both lists must agree about what leads, or the
 * counter sees two orders for the same dish.
 */
@RunWith(AndroidJUnit4::class)
class MenuFavouriteOrderingTest {

    private lateinit var db: AppDatabase
    private val restaurantId = 91L
    private val categoryId = 5L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.categoryDao().insertCategory(
                CategoryEntity(
                    id = categoryId,
                    name = "Mains",
                    isVeg = true,
                    restaurantId = restaurantId,
                    isActive = true
                )
            )
            // (name, shop pin, till star, deleted)
            listOf(
                "Zinger Bowl" to Triple(true, false, false),
                "Aloo Paratha" to Triple(false, true, false),
                "Momo" to Triple(true, true, false),
                "Tea" to Triple(false, false, false),
                "Retired Special" to Triple(true, false, true),
                "Aam Panna" to Triple(true, false, false),
            ).forEachIndexed { index, (name, flags) ->
                val (shopPin, tillStar, deleted) = flags
                db.menuDao().insertItem(
                    MenuItemEntity(
                        id = (index + 1).toLong(),
                        categoryId = categoryId,
                        name = name,
                        basePrice = "100",
                        restaurantId = restaurantId,
                        isFavourite = shopPin,
                        localFavourite = tillStar,
                        isDeleted = deleted
                    )
                )
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun favourites(): List<String> = runBlocking {
        db.menuDao().getFavouriteMenuWithVariantsFlow(restaurantId).first().map { it.menuItem.name }
    }

    @Test
    fun shopPinsLeadThenTheTillStarsThenAlphabeticalAndNothingElse() {
        assertEquals(
            // A dish carrying both flags leads, then the shop's pins A-Z, then the counter's own
            // star. An unstarred dish and a retired one must not appear at all.
            listOf("Momo", "Aam Panna", "Zinger Bowl", "Aloo Paratha"),
            favourites()
        )
    }

    @Test
    fun aDishCarryingBothFlagsAppearsOnce() {
        val momo = favourites().count { it == "Momo" }

        assertEquals("a dish pinned and starred must not be listed twice", 1, momo)
    }

    @Test
    fun aSoftDeletedStarredDishLeavesTheList() {
        assertFalse("a retired dish must never be listed, however it was starred", favourites().contains("Retired Special"))
    }

    @Test
    fun theCategoryListAgreesWithTheFavouritesListAboutWhatLeads() {
        val inCategory = runBlocking {
            db.menuDao().getMenuWithVariantsByCategoryFlow(categoryId, restaurantId)
                .first()
                .map { it.menuItem.name }
        }

        // First is the till's view of "the dish that leads", in both lists.
        assertEquals(inCategory.first(), favourites().first())
        // ... and the starred dishes form the head of the category list too.
        assertEquals(favourites(), inCategory.take(favourites().size))
    }
}
