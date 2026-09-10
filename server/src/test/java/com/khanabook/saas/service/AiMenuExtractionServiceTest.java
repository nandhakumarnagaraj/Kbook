package com.khanabook.saas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMenuExtractionServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private ItemVariantRepository itemVariantRepository;
    @Mock private PushNotificationService pushNotificationService;

    private AiMenuExtractionService service;

    @BeforeEach
    void setUp() {
        service = new AiMenuExtractionService(
                categoryRepository,
                menuItemRepository,
                itemVariantRepository,
                pushNotificationService,
                new ObjectMapper()
        );
    }

    @Test
    void testExtractMenuFromText_withHeuristics_parsesCategoriesAndPrices() {
        String rawText = """
                STARTERS
                Paneer Tikka 220
                Veg Spring Roll 160
                Chicken Tikka Half 140 Full 260
                
                BREADS
                Butter Naan 45
                Tandoori Roti 25
                """;

        AiMenuExtractionService.ExtractedMenuResponse response = service.extractMenuFromText(rawText);

        assertNotNull(response);
        assertEquals(5, response.totalItemsExtracted());
        assertEquals(2, response.categories().size());

        AiMenuExtractionService.ExtractedCategory starters = response.categories().get(0);
        assertEquals("Starters", starters.name());
        assertEquals(3, starters.items().size());

        AiMenuExtractionService.ExtractedItem paneer = starters.items().get(0);
        assertEquals("Paneer Tikka", paneer.name());
        assertEquals("veg", paneer.foodType());
        assertEquals(new BigDecimal("220"), paneer.basePrice());

        AiMenuExtractionService.ExtractedItem chicken = starters.items().get(2);
        assertEquals("Chicken Tikka", chicken.name());
        assertEquals("non-veg", chicken.foodType());
        assertEquals(new BigDecimal("260"), chicken.basePrice());
        assertEquals(2, chicken.variants().size());
        assertEquals("Half", chicken.variants().get(0).name());
        assertEquals(new BigDecimal("140"), chicken.variants().get(0).price());
        assertEquals("Full", chicken.variants().get(1).name());
        assertEquals(new BigDecimal("260"), chicken.variants().get(1).price());

        AiMenuExtractionService.ExtractedCategory breads = response.categories().get(1);
        assertEquals("Breads", breads.name());
        assertEquals(2, breads.items().size());
        assertEquals("Butter Naan", breads.items().get(0).name());
        assertEquals(new BigDecimal("45"), breads.items().get(0).basePrice());
    }

    @Test
    void testBulkImportMenu_persistsCategoriesAndItems() {
        Long restaurantId = 101L;
        when(categoryRepository.findByRestaurantIdAndServerUpdatedAtGreaterThan(eq(restaurantId), anyLong()))
                .thenReturn(Collections.emptyList());

        Category savedCat = new Category();
        savedCat.setId(10L);
        savedCat.setName("Desserts");
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCat);

        MenuItem savedItem = new MenuItem();
        savedItem.setId(201L);
        savedItem.setName("Gulab Jamun");
        savedItem.setBasePrice(new BigDecimal("80.00"));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(savedItem);

        AiMenuExtractionService.ExtractedMenuResponse payload = new AiMenuExtractionService.ExtractedMenuResponse(
                List.of(new AiMenuExtractionService.ExtractedCategory(
                        "Desserts",
                        List.of(new AiMenuExtractionService.ExtractedItem("Gulab Jamun", "veg", new BigDecimal("80.00"), "2 pieces", Collections.emptyList()))
                )),
                1
        );

        AiMenuExtractionService.BulkImportResult result = service.bulkImportMenu(restaurantId, payload);

        assertEquals(1, result.categoriesCreated());
        assertEquals(1, result.itemsCreated());
        assertEquals(0, result.variantsCreated());
        verify(pushNotificationService).pushSyncNow(restaurantId);
    }
}
