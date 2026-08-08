package com.khanabook.saas.sync.validation;

import com.khanabook.saas.entity.*;
import com.khanabook.saas.sync.validation.SyncPayloadValidator.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SyncPayloadValidatorTest {

	// ── Bill validation ─────────────────────────────────────────────────────

	@Nested
	@DisplayName("Bill validation")
	class BillTests {

		private Bill validBill() {
			Bill bill = new Bill();
			bill.setTotalAmount(new BigDecimal("250.00"));
			bill.setSubtotal(new BigDecimal("230.00"));
			bill.setOrderType("dine_in");
			bill.setPaymentMode("upi");
			bill.setOrderStatus("completed");
			bill.setPaymentStatus("success");
			bill.setDailyOrderId(1L);
			bill.setCustomerName("Test Customer");
			bill.setLastResetDate("2026-08-07");
			return bill;
		}

		@Test
		void validBillPasses() {
			var result = SyncPayloadValidator.validate(validBill());
			assertThat(result.valid()).isTrue();
		}

		@Test
		void negativeTotalAmountFails() {
			Bill bill = validBill();
			bill.setTotalAmount(new BigDecimal("-999.00"));
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("totalAmount");
		}

		@Test
		void nullTotalAmountFails() {
			Bill bill = validBill();
			bill.setTotalAmount(null);
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("totalAmount");
		}

		@Test
		void negativeSubtotalFails() {
			Bill bill = validBill();
			bill.setSubtotal(new BigDecimal("-1.00"));
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("subtotal");
		}

		@Test
		void invalidOrderTypeFails() {
			Bill bill = validBill();
			bill.setOrderType("invalid_type");
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("orderType");
		}

		@Test
		void invalidPaymentModeFails() {
			Bill bill = validBill();
			bill.setPaymentMode("bitcoin");
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("paymentMode");
		}

		@Test
		void invalidOrderStatusFails() {
			Bill bill = validBill();
			bill.setOrderStatus("deleted");
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("orderStatus");
		}

		@Test
		void invalidPaymentStatusFails() {
			Bill bill = validBill();
			bill.setPaymentStatus("hacked");
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("paymentStatus");
		}

		@Test
		void zeroDailyOrderIdFails() {
			Bill bill = validBill();
			bill.setDailyOrderId(0L);
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("dailyOrderId");
		}

		@Test
		void customerNameTooLongFails() {
			Bill bill = validBill();
			bill.setCustomerName("x".repeat(501));
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("customerName");
		}

		@Test
		void customerWhatsappTooLongFails() {
			Bill bill = validBill();
			bill.setCustomerWhatsapp("1234567890123456");
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("customerWhatsapp");
		}

		@Test
		void zeroTotalAmountAllowed() {
			Bill bill = validBill();
			bill.setTotalAmount(BigDecimal.ZERO);
			bill.setSubtotal(BigDecimal.ZERO);
			var result = SyncPayloadValidator.validate(bill);
			assertThat(result.valid()).isTrue();
		}
	}

	// ── BillItem validation ─────────────────────────────────────────────────

	@Nested
	@DisplayName("BillItem validation")
	class BillItemTests {

		private BillItem validBillItem() {
			BillItem item = new BillItem();
			item.setItemName("Chicken Biryani");
			item.setPrice(new BigDecimal("180.00"));
			item.setQuantity(2);
			item.setItemTotal(new BigDecimal("360.00"));
			item.setBillId(1L);
			item.setMenuItemId(1L);
			return item;
		}

		@Test
		void validBillItemPasses() {
			var result = SyncPayloadValidator.validate(validBillItem());
			assertThat(result.valid()).isTrue();
		}

		@Test
		void zeroQuantityFails() {
			BillItem item = validBillItem();
			item.setQuantity(0);
			var result = SyncPayloadValidator.validate(item);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("quantity");
		}

		@Test
		void negativeQuantityFails() {
			BillItem item = validBillItem();
			item.setQuantity(-1);
			var result = SyncPayloadValidator.validate(item);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("quantity");
		}

		@Test
		void negativePriceFails() {
			BillItem item = validBillItem();
			item.setPrice(new BigDecimal("-10.00"));
			var result = SyncPayloadValidator.validate(item);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("price");
		}

		@Test
		void blankItemNameFails() {
			BillItem item = validBillItem();
			item.setItemName("");
			var result = SyncPayloadValidator.validate(item);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("itemName");
		}

		@Test
		void itemNameTooLongFails() {
			BillItem item = validBillItem();
			item.setItemName("x".repeat(201));
			var result = SyncPayloadValidator.validate(item);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("itemName");
		}
	}

	// ── BillPayment validation ──────────────────────────────────────────────

	@Nested
	@DisplayName("BillPayment validation")
	class BillPaymentTests {

		private BillPayment validPayment() {
			BillPayment payment = new BillPayment();
			payment.setAmount(new BigDecimal("250.00"));
			payment.setPaymentMode("upi");
			payment.setBillId(1L);
			return payment;
		}

		@Test
		void validPaymentPasses() {
			var result = SyncPayloadValidator.validate(validPayment());
			assertThat(result.valid()).isTrue();
		}

		@Test
		void zeroAmountFails() {
			BillPayment payment = validPayment();
			payment.setAmount(BigDecimal.ZERO);
			var result = SyncPayloadValidator.validate(payment);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("amount");
		}

		@Test
		void negativeAmountFails() {
			BillPayment payment = validPayment();
			payment.setAmount(new BigDecimal("-50.00"));
			var result = SyncPayloadValidator.validate(payment);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("amount");
		}

		@Test
		void blankPaymentModeFails() {
			BillPayment payment = validPayment();
			payment.setPaymentMode("");
			var result = SyncPayloadValidator.validate(payment);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("paymentMode");
		}
	}

	// ── MenuItem validation ─────────────────────────────────────────────────

	@Nested
	@DisplayName("MenuItem validation")
	class MenuItemTests {

		private MenuItem validMenuItem() {
			MenuItem item = new MenuItem();
			item.setName("Masala Dosa");
			item.setBasePrice(new BigDecimal("80.00"));
			item.setCategoryId(1L);
			return item;
		}

		@Test
		void validMenuItemPasses() {
			var result = SyncPayloadValidator.validate(validMenuItem());
			assertThat(result.valid()).isTrue();
		}

		@Test
		void blankNameFails() {
			MenuItem item = validMenuItem();
			item.setName("  ");
			var result = SyncPayloadValidator.validate(item);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("name");
		}

		@Test
		void nameTooLongFails() {
			MenuItem item = validMenuItem();
			item.setName("x".repeat(201));
			var result = SyncPayloadValidator.validate(item);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("name");
		}

		@Test
		void negativeBasePriceFails() {
			MenuItem item = validMenuItem();
			item.setBasePrice(new BigDecimal("-1.00"));
			var result = SyncPayloadValidator.validate(item);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("basePrice");
		}
	}

	// ── ItemVariant validation ──────────────────────────────────────────────

	@Nested
	@DisplayName("ItemVariant validation")
	class ItemVariantTests {

		@Test
		void validVariantPasses() {
			ItemVariant v = new ItemVariant();
			v.setVariantName("Large");
			v.setPrice(new BigDecimal("120.00"));
			v.setMenuItemId(1L);
			var result = SyncPayloadValidator.validate(v);
			assertThat(result.valid()).isTrue();
		}

		@Test
		void blankVariantNameFails() {
			ItemVariant v = new ItemVariant();
			v.setVariantName("");
			v.setPrice(new BigDecimal("120.00"));
			v.setMenuItemId(1L);
			var result = SyncPayloadValidator.validate(v);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("variantName");
		}

		@Test
		void negativePriceFails() {
			ItemVariant v = new ItemVariant();
			v.setVariantName("Large");
			v.setPrice(new BigDecimal("-5.00"));
			v.setMenuItemId(1L);
			var result = SyncPayloadValidator.validate(v);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("price");
		}
	}

	// ── RestaurantProfile validation ────────────────────────────────────────

	@Nested
	@DisplayName("RestaurantProfile validation")
	class RestaurantProfileTests {

		@Test
		void validProfilePasses() {
			RestaurantProfile p = new RestaurantProfile();
			p.setShopName("Test Shop");
			p.setGstPercentage(new BigDecimal("18.00"));
			var result = SyncPayloadValidator.validate(p);
			assertThat(result.valid()).isTrue();
		}

		@Test
		void gstAbove100Fails() {
			RestaurantProfile p = new RestaurantProfile();
			p.setShopName("Test");
			p.setGstPercentage(new BigDecimal("101.00"));
			var result = SyncPayloadValidator.validate(p);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("gstPercentage");
		}

		@Test
		void negativeGstFails() {
			RestaurantProfile p = new RestaurantProfile();
			p.setShopName("Test");
			p.setGstPercentage(new BigDecimal("-1.00"));
			var result = SyncPayloadValidator.validate(p);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("gstPercentage");
		}

		@Test
		void shopNameTooLongFails() {
			RestaurantProfile p = new RestaurantProfile();
			p.setShopName("x".repeat(201));
			var result = SyncPayloadValidator.validate(p);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("shopName");
		}
	}

	// ── Category validation ─────────────────────────────────────────────────

	@Nested
	@DisplayName("Category validation")
	class CategoryTests {

		@Test
		void validCategoryPasses() {
			Category c = new Category();
			c.setName("Starters");
			var result = SyncPayloadValidator.validate(c);
			assertThat(result.valid()).isTrue();
		}

		@Test
		void blankNameFails() {
			Category c = new Category();
			c.setName("");
			var result = SyncPayloadValidator.validate(c);
			assertThat(result.valid()).isFalse();
		}
	}

	// ── User validation ─────────────────────────────────────────────────────

	@Nested
	@DisplayName("User validation")
	class UserTests {

		@Test
		void validUserPasses() {
			User u = new User();
			u.setName("Nandha Kumar");
			var result = SyncPayloadValidator.validate(u);
			assertThat(result.valid()).isTrue();
		}

		@Test
		void nameTooLongFails() {
			User u = new User();
			u.setName("x".repeat(201));
			var result = SyncPayloadValidator.validate(u);
			assertThat(result.valid()).isFalse();
			assertThat(result.reason()).contains("name");
		}
	}
}
