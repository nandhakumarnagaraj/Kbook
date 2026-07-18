package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.webadmin.dto.CreateMenuItemRequest;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: web-admin-full-capability, Property 11: Terminal Reactivation State Transition
 * Feature: web-admin-full-capability, Property 12: Menu Item Availability Toggle
 * Feature: web-admin-full-capability, Property 13: Menu Item Creation
 * Feature: web-admin-full-capability, Property 14: Menu Item Soft Delete
 * Feature: web-admin-full-capability, Property 15: Menu Item Validation
 *
 * Validates: Requirements 10.3, 11.2, 12.2, 12.6, 12.7
 */
class MenuTerminalProperties {

    private UserRepository userRepository;
    private CategoryRepository categoryRepository;
    private MenuItemRepository menuItemRepository;
    private RestaurantTerminalRepository terminalRepository;
    private RestaurantProfileRepository profileRepository;
    private BusinessWriteService service;

    private void setupService() {
        userRepository = mock(UserRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        menuItemRepository = mock(MenuItemRepository.class);
        terminalRepository = mock(RestaurantTerminalRepository.class);
        profileRepository = mock(RestaurantProfileRepository.class);
        when(categoryRepository.findByIdAndRestaurantIdAndIsDeletedFalse(anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    Category category = new Category();
                    category.setId(invocation.getArgument(0));
                    category.setRestaurantId(invocation.getArgument(1));
                    return Optional.of(category);
                });
        RestaurantProfile profile = new RestaurantProfile();
        when(profileRepository.findAndLockByRestaurantId(anyLong())).thenReturn(Optional.of(profile));
        service = new BusinessWriteService(userRepository, categoryRepository, menuItemRepository,
                terminalRepository, profileRepository);
    }

    // ─── Property 11: Terminal Reactivation State Transition ─────────────────────

    /**
     * Property 11: For any deactivated terminal (where active terminal count < 5),
     * reactivation sets status=ACTIVE and increments credentialVersion by 1.
     *
     * Validates: Requirements 10.3
     */
    @Property(tries = 100)
    @Label("Property 11: Deactivated terminal reactivation sets ACTIVE and increments credentialVersion")
    void terminalReactivationSetsActiveAndIncrementsVersion(
            @ForAll("restaurantIds") Long restaurantId,
            @ForAll("terminalIds") Long terminalId,
            @ForAll("credentialVersions") Long initialCredentialVersion,
            @ForAll("activeTerminalCounts") long activeCount
    ) {
        setupService();

        RestaurantTerminal terminal = new RestaurantTerminal();
        terminal.setId(terminalId);
        terminal.setRestaurantId(restaurantId);
        terminal.setStatus("INACTIVE");
        terminal.setIsActive(false);
        terminal.setCredentialVersion(initialCredentialVersion);
        terminal.setCreatedAt(System.currentTimeMillis());

        when(terminalRepository.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE")).thenReturn(activeCount);
        when(terminalRepository.save(any(RestaurantTerminal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reactivateTerminal(restaurantId, terminalId);

        assertEquals("ACTIVE", terminal.getStatus(), "Status must be ACTIVE after reactivation");
        assertTrue(terminal.getIsActive(), "isActive must be true after reactivation");
        assertEquals(initialCredentialVersion + 1, terminal.getCredentialVersion(),
                "credentialVersion must be incremented by exactly 1");

        verify(terminalRepository).save(terminal);
    }

    // ─── Property 12: Menu Item Availability Toggle ─────────────────────────────

    /**
     * Property 12: For any menu item, toggling availability flips isAvailable to its
     * boolean complement. Other fields remain unchanged.
     *
     * Validates: Requirements 11.2
     */
    @Property(tries = 100)
    @Label("Property 12: Toggle availability flips isAvailable, other fields unchanged")
    void toggleAvailabilityFlipsBoolean(
            @ForAll("restaurantIds") Long restaurantId,
            @ForAll("menuItemIds") Long menuItemId,
            @ForAll("booleans") Boolean initialAvailability
    ) {
        setupService();

        MenuItem item = new MenuItem();
        item.setId(menuItemId);
        item.setRestaurantId(restaurantId);
        item.setName("Test Item");
        item.setCategoryId(1L);
        item.setFoodType("veg");
        item.setBasePrice(BigDecimal.valueOf(100));
        item.setDescription("A test item");
        item.setIsAvailable(initialAvailability);
        item.setIsDeleted(false);
        item.setLocalId(1L);
        item.setDeviceId("test-device");
        item.setUpdatedAt(System.currentTimeMillis());
        item.setCreatedAt(System.currentTimeMillis());

        // Capture original values
        String originalName = item.getName();
        Long originalCategoryId = item.getCategoryId();
        String originalFoodType = item.getFoodType();
        BigDecimal originalBasePrice = item.getBasePrice();
        String originalDescription = item.getDescription();

        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuItem result = service.toggleMenuItemAvailability(restaurantId, menuItemId);

        assertEquals(!initialAvailability, result.getIsAvailable(),
                "isAvailable must be flipped from " + initialAvailability + " to " + !initialAvailability);
        assertEquals(originalName, result.getName(), "Name must remain unchanged");
        assertEquals(originalCategoryId, result.getCategoryId(), "CategoryId must remain unchanged");
        assertEquals(originalFoodType, result.getFoodType(), "FoodType must remain unchanged");
        assertEquals(originalBasePrice, result.getBasePrice(), "BasePrice must remain unchanged");
        assertEquals(originalDescription, result.getDescription(), "Description must remain unchanged");

        verify(menuItemRepository).save(item);
    }

    // ─── Property 13: Menu Item Creation ────────────────────────────────────────

    /**
     * Property 13: For any valid CreateMenuItemRequest (name non-empty, basePrice > 0),
     * creates item with generated ID and all fields persisted correctly.
     *
     * Validates: Requirements 12.2
     */
    @Property(tries = 100)
    @Label("Property 13: Valid menu item request creates item with correct fields")
    void validMenuItemRequestCreatesItem(
            @ForAll("validMenuNames") String name,
            @ForAll("validPrices") String basePrice,
            @ForAll("restaurantIds") Long restaurantId,
            @ForAll("categoryIds") Long categoryId,
            @ForAll("foodTypes") String foodType
    ) {
        setupService();

        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(invocation -> {
            MenuItem saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        CreateMenuItemRequest request = new CreateMenuItemRequest(name, categoryId, foodType, basePrice, "A description");
        MenuItem result = service.createMenuItem(restaurantId, request);

        assertNotNull(result.getId(), "Created item must have a generated ID");
        assertEquals(name, result.getName(), "Name must match request");
        assertEquals(categoryId, result.getCategoryId(), "CategoryId must match request");
        assertEquals(foodType, result.getFoodType(), "FoodType must match request");
        assertEquals(new BigDecimal(basePrice), result.getBasePrice(), "BasePrice must match request");
        assertEquals("A description", result.getDescription(), "Description must match request");
        assertTrue(result.getIsAvailable(), "New item must be available by default");
        assertEquals(restaurantId, result.getRestaurantId(), "RestaurantId must match");

        verify(menuItemRepository).save(any(MenuItem.class));
    }

    // ─── Property 14: Menu Item Soft Delete ─────────────────────────────────────

    /**
     * Property 14: For any menu item, confirmed deletion sets isDeleted=true
     * and isAvailable=false.
     *
     * Validates: Requirements 12.6
     */
    @Property(tries = 100)
    @Label("Property 14: Soft delete sets isDeleted=true and isAvailable=false")
    void softDeleteSetsFlags(
            @ForAll("restaurantIds") Long restaurantId,
            @ForAll("menuItemIds") Long menuItemId,
            @ForAll("booleans") Boolean initialAvailability
    ) {
        setupService();

        MenuItem item = new MenuItem();
        item.setId(menuItemId);
        item.setRestaurantId(restaurantId);
        item.setName("Item To Delete");
        item.setCategoryId(1L);
        item.setBasePrice(BigDecimal.valueOf(50));
        item.setIsAvailable(initialAvailability);
        item.setIsDeleted(false);
        item.setLocalId(1L);
        item.setDeviceId("test-device");
        item.setUpdatedAt(System.currentTimeMillis());
        item.setCreatedAt(System.currentTimeMillis());

        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteMenuItem(restaurantId, menuItemId);

        assertTrue(item.getIsDeleted(), "isDeleted must be true after soft delete");
        assertFalse(item.getIsAvailable(), "isAvailable must be false after soft delete");

        verify(menuItemRepository).save(item);
    }

    // ─── Property 15: Menu Item Validation ──────────────────────────────────────

    /**
     * Property 15: For any request where name is empty/whitespace OR basePrice <= 0,
     * the system rejects with exception and menu unchanged.
     *
     * Validates: Requirements 12.7
     */
    @Property(tries = 100)
    @Label("Property 15: Empty name causes rejection, menu unchanged")
    void emptyNameRejected(
            @ForAll("invalidNames") String name,
            @ForAll("validPrices") String basePrice,
            @ForAll("restaurantIds") Long restaurantId
    ) {
        setupService();

        CreateMenuItemRequest request = new CreateMenuItemRequest(name, 1L, "veg", basePrice, null);

        assertThrows(IllegalArgumentException.class, () -> service.createMenuItem(restaurantId, request),
                "Empty/whitespace name '" + name + "' should cause rejection");

        verify(menuItemRepository, never()).save(any(MenuItem.class));
    }

    @Property(tries = 100)
    @Label("Property 15: Invalid price causes rejection, menu unchanged")
    void invalidPriceRejected(
            @ForAll("validMenuNames") String name,
            @ForAll("invalidPrices") String basePrice,
            @ForAll("restaurantIds") Long restaurantId
    ) {
        setupService();

        CreateMenuItemRequest request = new CreateMenuItemRequest(name, 1L, "veg", basePrice, null);

        assertThrows(IllegalArgumentException.class, () -> service.createMenuItem(restaurantId, request),
                "Invalid price '" + basePrice + "' should cause rejection");

        verify(menuItemRepository, never()).save(any(MenuItem.class));
    }

    // ─── Generators ─────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Long> restaurantIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Long> terminalIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Long> menuItemIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Long> categoryIds() {
        return Arbitraries.longs().between(1L, 100L);
    }

    @Provide
    Arbitrary<Long> credentialVersions() {
        return Arbitraries.longs().between(0L, 100L);
    }

    @Provide
    Arbitrary<Long> activeTerminalCounts() {
        return Arbitraries.longs().between(0L, 4L);
    }

    @Provide
    Arbitrary<Boolean> booleans() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<String> validMenuNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> invalidNames() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just("\n"),
                Arbitraries.just("  \t\n  ")
        );
    }

    @Provide
    Arbitrary<String> validPrices() {
        return Arbitraries.doubles()
                .between(0.01, 10000.0)
                .map(d -> String.format("%.2f", d));
    }

    @Provide
    Arbitrary<String> invalidPrices() {
        return Arbitraries.oneOf(
                Arbitraries.just("0"),
                Arbitraries.just("-1"),
                Arbitraries.just("-99.99"),
                Arbitraries.doubles().between(-10000.0, 0.0).map(d -> String.format("%.2f", d))
        );
    }

    @Provide
    Arbitrary<String> foodTypes() {
        return Arbitraries.of("veg", "non-veg");
    }
}
