package com.khanabook.saas.feature.sync.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.MenuItemDTO;
import com.khanabook.saas.feature.sync.data.SyncMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug fix: the device push DTO (MenuItemDTO) omitted foodType, currentStock,
 * lowStockThreshold and barcode, so mapToEntity produced nulls for them.
 *
 * applyChangedFieldsMerge only restores those fields when the incoming mask OMITS them.
 * A stock-only push sends changedFields="currentStock", so the protection at the
 * currentStock/lowStockThreshold/foodType guards was skipped and the never-populated
 * value was saved as null. Every sale that decremented stock therefore erased the
 * server's stock and food type — silently, because the columns are nullable with no
 * constraint. preserveServerOwnedState does not cover these fields, so nothing else
 * caught it.
 */
class GenericSyncMenuFieldMaskTest {

    private static MenuItem mapDevicePush(String json) throws Exception {
        // Spring Boot disables FAIL_ON_UNKNOWN_PROPERTIES, so a field the DTO does not
        // declare is dropped silently rather than rejected. Mirror that here — it is the
        // reason this bug reached production instead of failing loudly at the boundary.
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MenuItemDTO dto = mapper.readValue(json, MenuItemDTO.class);
        return SyncMapper.mapToEntity(dto, MenuItem.class);
    }

    private static MenuItem existingItem() {
        MenuItem existing = new MenuItem();
        existing.setName("Paneer Tikka");
        existing.setBasePrice(new BigDecimal("250.00"));
        existing.setFoodType("veg");
        existing.setCurrentStock(new BigDecimal("50"));
        existing.setLowStockThreshold(new BigDecimal("2"));
        existing.setBarcode("9876543210");
        return existing;
    }

    @Test
    void stockOnlyPushPersistsTheStockValueSentByTheDevice() throws Exception {
        MenuItem incoming = mapDevicePush("{"
                + "\"localId\":42,\"name\":\"Paneer Tikka\",\"basePrice\":\"250.00\","
                + "\"foodType\":\"veg\",\"currentStock\":7,\"lowStockThreshold\":2,"
                + "\"barcode\":\"9876543210\",\"changedFields\":\"currentStock\"}");

        GenericSyncService.applyChangedFieldsMerge(incoming, existingItem());
        GenericSyncService.preserveServerOwnedState(incoming, existingItem());

        // The field the device actually changed must survive the merge.
        assertThat(incoming.getCurrentStock()).isEqualByComparingTo("7");
        // Fields the mask omits must still come from the server row.
        assertThat(incoming.getFoodType()).isEqualTo("veg");
        assertThat(incoming.getLowStockThreshold()).isEqualByComparingTo("2");
        assertThat(incoming.getBarcode()).isEqualTo("9876543210");
    }

    @Test
    void fullPushCarriesEveryFieldInsteadOfNullingIt() throws Exception {
        MenuItem incoming = mapDevicePush("{"
                + "\"localId\":42,\"name\":\"Paneer Tikka\",\"basePrice\":\"250.00\","
                + "\"foodType\":\"nonveg\",\"currentStock\":7,\"lowStockThreshold\":2,"
                + "\"barcode\":\"9876543210\",\"changedFields\":\"all\"}");

        GenericSyncService.applyChangedFieldsMerge(incoming, existingItem());
        GenericSyncService.preserveServerOwnedState(incoming, existingItem());

        assertThat(incoming.getFoodType()).isEqualTo("nonveg");
        assertThat(incoming.getCurrentStock()).isEqualByComparingTo("7");
        assertThat(incoming.getLowStockThreshold()).isEqualByComparingTo("2");
        assertThat(incoming.getBarcode()).isEqualTo("9876543210");
    }

    @Test
    void priceOnlyPushDoesNotDisturbStockOrFoodType() throws Exception {
        MenuItem incoming = mapDevicePush("{"
                + "\"localId\":42,\"name\":\"Paneer Tikka\",\"basePrice\":\"300.00\","
                + "\"foodType\":\"veg\",\"currentStock\":7,\"lowStockThreshold\":2,"
                + "\"barcode\":\"9876543210\",\"changedFields\":\"basePrice\"}");

        GenericSyncService.applyChangedFieldsMerge(incoming, existingItem());
        GenericSyncService.preserveServerOwnedState(incoming, existingItem());

        assertThat(incoming.getBasePrice()).isEqualByComparingTo("300.00");
        assertThat(incoming.getCurrentStock()).isEqualByComparingTo("50");
        assertThat(incoming.getFoodType()).isEqualTo("veg");
    }
}
