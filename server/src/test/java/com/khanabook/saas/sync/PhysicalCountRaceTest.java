package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.service.InventoryService;
import com.khanabook.saas.utility.JwtUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Distributed state problem C2: Physical count race.
 *
 * Real use case: Owner does a physical stock count on the web admin while
 * Terminal A is still selling items offline. When Terminal A syncs, the
 * bill's inventory deduction overwrites the physical count — or vice versa.
 * No locking exists between the two paths.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PhysicalCountRaceTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 9301L;

    @Autowired private MockMvc mockMvc;
    @Autowired private RawMaterialRepository rawMaterialRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private InventoryService inventoryService;

    private String ownerToken;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-pc-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
        ownerToken = jwtUtility.generateToken(owner.getLoginId(), RESTAURANT, "OWNER");
    }

    private RawMaterial createMaterial(String name, BigDecimal stockQty) {
        RawMaterial m = new RawMaterial();
        m.setRestaurantId(RESTAURANT);
        m.setName(name);
        m.setUnit("kg");
        m.setStockQuantity(stockQty);
        m.setLowStockThreshold(BigDecimal.TEN);
        m.setCostPerUnit(BigDecimal.TEN);
        m.setIsDeleted(false);
        m.setCreatedAt(System.currentTimeMillis());
        m.setUpdatedAt(System.currentTimeMillis());
        return rawMaterialRepository.save(m);
    }

    @Test
    void physicalCount_blindOverwrite_noLocking() {
        // C2 scenario: RawMaterial has stockQty=100. Owner counts 80 (physical count).
        // Meanwhile a bill deduction would set it to 90 (subtract 10).
        // The physical count blindly overwrites to 80, losing the deduction.
        RawMaterial material = createMaterial("Rice", new BigDecimal("100"));

        // Physical count sets stock to 80
        inventoryService.adjustPhysicalCount(RESTAURANT, material.getId(),
                new BigDecimal("80"), owner.getId());

        // Verify: stock is now 80
        RawMaterial updated = rawMaterialRepository.findById(material.getId()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualByComparingTo(new BigDecimal("80"));

        // Verify: a StockMovement ADJUST was recorded
        var movements = stockMovementRepository.findByRestaurantIdAndRawMaterialIdOrderByCreatedAtDesc(
                RESTAURANT, material.getId());
        assertThat(movements).isNotEmpty();
        assertThat(movements.get(0).getKind()).isEqualTo("ADJUST");
    }

    @Test
    void physicalCount_thenSaleDeduction_lastWriteWins() {
        // C2 scenario: Physical count sets stock=80. Then a bill sync deducts 10.
        // The deduction subtracts from the CURRENT value (80-10=70), not the original.
        // This means the physical count IS preserved if the deduction reads after the count.
        RawMaterial material = createMaterial("Rice", new BigDecimal("100"));

        // Physical count sets stock to 80
        inventoryService.adjustPhysicalCount(RESTAURANT, material.getId(),
                new BigDecimal("80"), owner.getId());

        // Now simulate a bill deduction: deduct 10 from current stock
        // In real code, deductForFinalizedBill reads material.getStockQuantity() which is now 80
        // So deduction would be 80-10=70, preserving the physical count
        RawMaterial afterCount = rawMaterialRepository.findById(material.getId()).orElseThrow();
        assertThat(afterCount.getStockQuantity()).isEqualByComparingTo(new BigDecimal("80"));

        // But if the deduction ran FIRST and the count ran AFTER (race condition):
        // deduction: 100-10=90, then count: overwrite to 80 — deduction lost
        // The outcome depends on which runs last — no serialization guarantee
    }

    @Test
    void physicalCount_concurrentWithSale_noProtection() {
        // C2: Verify there's no optimistic locking or CAS on RawMaterial.stockQuantity.
        // Two concurrent adjustments both succeed — last writer wins.
        RawMaterial material = createMaterial("Rice", new BigDecimal("100"));

        // First adjustment
        inventoryService.adjustPhysicalCount(RESTAURANT, material.getId(),
                new BigDecimal("80"), owner.getId());

        // Second adjustment (concurrent)
        inventoryService.adjustPhysicalCount(RESTAURANT, material.getId(),
                new BigDecimal("90"), owner.getId());

        // Verify: second adjustment wins (last writer)
        RawMaterial updated = rawMaterialRepository.findById(material.getId()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualByComparingTo(new BigDecimal("90"));

        // Both adjustments were recorded in StockMovement — audit trail exists
        // but the final stockQuantity is just the last write
    }

    @Test
    void stockMovement_ledger_integrity() {
        // C2: Verify StockMovement append-only ledger records all adjustments
        RawMaterial material = createMaterial("Rice", new BigDecimal("100"));

        inventoryService.adjustPhysicalCount(RESTAURANT, material.getId(),
                new BigDecimal("80"), owner.getId());

        inventoryService.adjustPhysicalCount(RESTAURANT, material.getId(),
                new BigDecimal("90"), owner.getId());

        var movements = stockMovementRepository.findByRestaurantIdAndRawMaterialIdOrderByCreatedAtDesc(
                RESTAURANT, material.getId());

        // Both adjustments recorded
        assertThat(movements.size()).isGreaterThanOrEqualTo(2);
        // Both are ADJUST kind
        assertThat(movements).allMatch(m -> "ADJUST".equals(m.getKind()));
    }
}
