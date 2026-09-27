package com.khanabook.saas.feature.sync.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillDTO;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileDTO;

/**
 * Regression cover for the server-owned fields that are absent from their push DTO.
 *
 * <p>BillDTO has no inventoryDeducted, gatewayTxnId or settlement fields, and
 * RestaurantProfileDTO has no marketplace integration fields. BeanUtils therefore leaves them
 * null on the incoming entity, so an unguarded save writes the entity's field initializer over
 * the server row. For inventoryDeducted that initializer is {@code false}, which is the
 * "deduct this bill's stock a second time" trigger in InventoryService.
 */
class ServerOwnedFieldPreservationTest {

	@Test
	@DisplayName("a push cannot reset inventoryDeducted and cause a second stock deduction")
	void inventoryDeductedSurvivesPush() {
		Bill existing = new Bill();
		existing.setPaymentStatus("paid");
		existing.setInventoryDeducted(true);

		// What the push pipeline builds: DTO mapped onto a fresh entity, exactly as
		// SyncMapper does it. inventoryDeducted is not on BillDTO, so it lands on the
		// field initializer.
		BillDTO dto = new BillDTO();
		dto.setPaymentStatus("paid");
		Bill incoming = new Bill();
		BeanUtils.copyProperties(dto, incoming);

		assertFalse(incoming.getInventoryDeducted(),
				"Precondition: without preservation the flag reads false, which re-arms deduction");

		GenericSyncService.preserveServerOwnedState(incoming, existing);

		assertTrue(incoming.getInventoryDeducted(),
				"inventoryDeducted must be restored from the server row");
	}

	@Test
	@DisplayName("settlement fields survive a push that reports the bill as paid")
	void settlementFieldsSurvivePaidPush() {
		// The old guard was `existing == paid && incoming != paid`. A device that correctly
		// reports the bill as paid satisfied neither half, so the fields were nulled exactly
		// on the pushes most likely to follow a gateway payment.
		Bill existing = new Bill();
		existing.setPaymentStatus("paid");
		existing.setGatewayTxnId("TXN-9911");
		existing.setGatewayStatus("captured");
		existing.setRefundId("RF-22");
		existing.setSettledAmount(new BigDecimal("450.00"));
		existing.setSettledAt(1_700_000_000_000L);
		existing.setCommissionAmount(new BigDecimal("4.50"));

		Bill incoming = new Bill();
		BillDTO dto = new BillDTO();
		dto.setPaymentStatus("paid");
		BeanUtils.copyProperties(dto, incoming);

		GenericSyncService.preserveServerOwnedState(incoming, existing);

		assertEquals("TXN-9911", incoming.getGatewayTxnId());
		assertEquals("captured", incoming.getGatewayStatus());
		assertEquals("RF-22", incoming.getRefundId());
		assertEquals(0, new BigDecimal("450.00").compareTo(incoming.getSettledAmount()));
		assertEquals(1_700_000_000_000L, incoming.getSettledAt());
		assertEquals(0, new BigDecimal("4.50").compareTo(incoming.getCommissionAmount()));
	}

	@Test
	@DisplayName("a null existing flag still yields false rather than null")
	void nullExistingFlagIsCoalesced() {
		Bill existing = new Bill();
		existing.setInventoryDeducted(null);
		Bill incoming = new Bill();

		GenericSyncService.preserveServerOwnedState(incoming, existing);

		assertEquals(Boolean.FALSE, incoming.getInventoryDeducted(),
				"inventoryDeducted is NOT NULL in the schema, so coalesce instead of writing null");
	}

	@Test
	@DisplayName("marketplace integration settings survive a whole-record profile push")
	void marketplaceSettingsSurvivePush() {
		RestaurantProfile existing = new RestaurantProfile();
		existing.setZomatoEnabled(true);
		existing.setSwiggyEnabled(true);
		existing.setZomatoOutletId("OUT-77");
		existing.setSwiggyStoreId("ST-88");
		existing.setZomatoApiKey("zk_live");
		existing.setSwiggyWebhookSecret("whsec_live");
		existing.setMarketplaceNotes("owner configured");
		existing.setOwnWebsiteEnabled(true);

		RestaurantProfile incoming = new RestaurantProfile();
		RestaurantProfileDTO dto = new RestaurantProfileDTO();
		dto.setShopName("Mughal Kitchen");
		BeanUtils.copyProperties(dto, incoming);

		assertNull(incoming.getZomatoApiKey(), "Precondition: the DTO carries no marketplace fields");

		GenericSyncService.preserveServerOwnedState(incoming, existing);

		assertTrue(incoming.getZomatoEnabled());
		assertTrue(incoming.getSwiggyEnabled());
		assertEquals("OUT-77", incoming.getZomatoOutletId());
		assertEquals("ST-88", incoming.getSwiggyStoreId());
		assertEquals("zk_live", incoming.getZomatoApiKey());
		assertEquals("whsec_live", incoming.getSwiggyWebhookSecret());
		assertEquals("owner configured", incoming.getMarketplaceNotes());
		assertTrue(incoming.getOwnWebsiteEnabled());
		assertEquals("Mughal Kitchen", incoming.getShopName(),
				"client-owned fields must still be taken from the push");
	}
}
