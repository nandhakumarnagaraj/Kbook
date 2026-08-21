package com.khanabook.saas.sync.validation;

import com.khanabook.saas.entity.*;
import com.khanabook.saas.sync.entity.BaseSyncEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Set;

public class SyncPayloadValidator {

	private static final Logger log = LoggerFactory.getLogger(SyncPayloadValidator.class);

	public record ValidationResult(boolean valid, String reason) {
		public static ValidationResult ok() {
			return new ValidationResult(true, null);
		}

		public static ValidationResult fail(String reason) {
			return new ValidationResult(false, reason);
		}
	}

	private static final Set<String> VALID_ORDER_TYPES = Set.of(
			"dine_in", "takeaway", "delivery", "parcel");

	private static final Set<String> VALID_PAYMENT_MODES = Set.of(
			"upi", "cash", "pos",
			"part_payment_upi_cash", "part_payment_cash_pos", "part_payment_upi_pos",
			"part_cash_upi", "part_cash_pos", "part_upi_pos");

	private static final Set<String> VALID_ORDER_STATUSES = Set.of(
			"draft", "completed", "paid", "cancelled");

	private static final Set<String> VALID_PAYMENT_STATUSES = Set.of(
			"pending", "success", "paid", "failed", "refunded");

	private static final int MAX_STRING_LENGTH = 500;
	private static final int MAX_NAME_LENGTH = 200;
	private static final int MAX_PHONE_LENGTH = 15;
	private static final int MAX_PAYMENT_MODE_LENGTH = 50;

	public static ValidationResult validate(BaseSyncEntity record) {
		// Deleted records are soft-delete markers — their field values don't matter
		// for data integrity since they only flip isDeleted=true on existing rows.
		if (Boolean.TRUE.equals(record.getIsDeleted())) {
			return ValidationResult.ok();
		}
		if (record instanceof Bill bill) {
			return validateBill(bill);
		} else if (record instanceof BillItem billItem) {
			return validateBillItem(billItem);
		} else if (record instanceof BillPayment billPayment) {
			return validateBillPayment(billPayment);
		} else if (record instanceof MenuItem menuItem) {
			return validateMenuItem(menuItem);
		} else if (record instanceof ItemVariant variant) {
			return validateItemVariant(variant);
		} else if (record instanceof Category category) {
			return validateCategory(category);
		} else if (record instanceof RestaurantProfile profile) {
			return validateRestaurantProfile(profile);
		} else if (record instanceof User user) {
			return validateUser(user);
		}
		return ValidationResult.ok();
	}

	private static ValidationResult validateBill(Bill bill) {
		if (bill.getTotalAmount() == null) {
			return ValidationResult.fail("totalAmount is required");
		}
		if (bill.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
			return ValidationResult.fail("totalAmount must be >= 0");
		}
		if (bill.getSubtotal() == null) {
			return ValidationResult.fail("subtotal is required");
		}
		if (bill.getSubtotal().compareTo(BigDecimal.ZERO) < 0) {
			return ValidationResult.fail("subtotal must be >= 0");
		}
		if (bill.getOrderType() != null && !VALID_ORDER_TYPES.contains(bill.getOrderType().toLowerCase())) {
			return ValidationResult.fail("orderType must be one of: " + VALID_ORDER_TYPES);
		}
		if (bill.getPaymentMode() != null && !VALID_PAYMENT_MODES.contains(bill.getPaymentMode().toLowerCase())) {
			return ValidationResult.fail("paymentMode must be one of: " + VALID_PAYMENT_MODES);
		}
		if (bill.getOrderStatus() != null && !VALID_ORDER_STATUSES.contains(bill.getOrderStatus().toLowerCase())) {
			return ValidationResult.fail("orderStatus must be one of: " + VALID_ORDER_STATUSES);
		}
		if (bill.getPaymentStatus() != null && !VALID_PAYMENT_STATUSES.contains(bill.getPaymentStatus().toLowerCase())) {
			return ValidationResult.fail("paymentStatus must be one of: " + VALID_PAYMENT_STATUSES);
		}
		if (bill.getDailyOrderId() != null && bill.getDailyOrderId() <= 0) {
			return ValidationResult.fail("dailyOrderId must be > 0");
		}
		if (exceedsLength(bill.getCustomerName(), MAX_STRING_LENGTH)) {
			return ValidationResult.fail("customerName exceeds " + MAX_STRING_LENGTH + " characters");
		}
		if (exceedsLength(bill.getCancelReason(), MAX_STRING_LENGTH)) {
			return ValidationResult.fail("cancelReason exceeds " + MAX_STRING_LENGTH + " characters");
		}
		if (exceedsLength(bill.getCustomerWhatsapp(), MAX_PHONE_LENGTH)) {
			return ValidationResult.fail("customerWhatsapp exceeds " + MAX_PHONE_LENGTH + " characters");
		}
		// Cross-validate bill total
		if (bill.getSubtotal() != null && bill.getTotalAmount() != null) {
			BigDecimal computed = bill.getSubtotal()
				.add(bill.getCgstAmount() != null ? bill.getCgstAmount() : BigDecimal.ZERO)
				.add(bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO)
				.add(bill.getCustomTaxAmount() != null ? bill.getCustomTaxAmount() : BigDecimal.ZERO);
			if (bill.getTotalAmount().subtract(computed).abs().compareTo(new BigDecimal("1.00")) > 0) {
				log.warn("Bill total mismatch: stated={} computed={} billId={}", bill.getTotalAmount(), computed, bill.getId());
			}
		}
		return ValidationResult.ok();
	}

	private static ValidationResult validateBillItem(BillItem item) {
		if (item.getPrice() == null) {
			return ValidationResult.fail("price is required");
		}
		if (item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
			return ValidationResult.fail("price must be >= 0");
		}
		if (item.getQuantity() == null || item.getQuantity() <= 0) {
			return ValidationResult.fail("quantity must be > 0");
		}
		if (isBlank(item.getItemName())) {
			return ValidationResult.fail("itemName is required");
		}
		if (exceedsLength(item.getItemName(), MAX_NAME_LENGTH)) {
			return ValidationResult.fail("itemName exceeds " + MAX_NAME_LENGTH + " characters");
		}
		if (item.getItemTotal() != null && item.getItemTotal().compareTo(BigDecimal.ZERO) < 0) {
			return ValidationResult.fail("itemTotal must be >= 0");
		}
		return ValidationResult.ok();
	}

	private static ValidationResult validateBillPayment(BillPayment payment) {
		if (payment.getAmount() == null) {
			return ValidationResult.fail("amount is required");
		}
		if (payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			return ValidationResult.fail("amount must be > 0");
		}
		if (isBlank(payment.getPaymentMode())) {
			return ValidationResult.fail("paymentMode is required");
		}
		if (exceedsLength(payment.getPaymentMode(), MAX_PAYMENT_MODE_LENGTH)) {
			return ValidationResult.fail("paymentMode exceeds " + MAX_PAYMENT_MODE_LENGTH + " characters");
		}
		return ValidationResult.ok();
	}

	private static ValidationResult validateMenuItem(MenuItem item) {
		if (isBlank(item.getName())) {
			return ValidationResult.fail("name is required");
		}
		if (exceedsLength(item.getName(), MAX_NAME_LENGTH)) {
			return ValidationResult.fail("name exceeds " + MAX_NAME_LENGTH + " characters");
		}
		if (item.getBasePrice() != null && item.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
			return ValidationResult.fail("basePrice must be >= 0");
		}
		return ValidationResult.ok();
	}

	private static ValidationResult validateItemVariant(ItemVariant variant) {
		if (isBlank(variant.getVariantName())) {
			return ValidationResult.fail("variantName is required");
		}
		if (exceedsLength(variant.getVariantName(), MAX_NAME_LENGTH)) {
			return ValidationResult.fail("variantName exceeds " + MAX_NAME_LENGTH + " characters");
		}
		if (variant.getPrice() != null && variant.getPrice().compareTo(BigDecimal.ZERO) < 0) {
			return ValidationResult.fail("price must be >= 0");
		}
		return ValidationResult.ok();
	}

	private static ValidationResult validateCategory(Category category) {
		if (isBlank(category.getName())) {
			return ValidationResult.fail("name is required");
		}
		if (exceedsLength(category.getName(), MAX_NAME_LENGTH)) {
			return ValidationResult.fail("name exceeds " + MAX_NAME_LENGTH + " characters");
		}
		return ValidationResult.ok();
	}

	private static ValidationResult validateRestaurantProfile(RestaurantProfile profile) {
		if (profile.getGstPercentage() != null) {
			if (profile.getGstPercentage().compareTo(BigDecimal.ZERO) < 0
					|| profile.getGstPercentage().compareTo(new BigDecimal("100")) > 0) {
				return ValidationResult.fail("gstPercentage must be between 0 and 100");
			}
		}
		if (exceedsLength(profile.getShopName(), MAX_NAME_LENGTH)) {
			return ValidationResult.fail("shopName exceeds " + MAX_NAME_LENGTH + " characters");
		}
		return ValidationResult.ok();
	}

	private static ValidationResult validateUser(User user) {
		if (exceedsLength(user.getName(), MAX_NAME_LENGTH)) {
			return ValidationResult.fail("name exceeds " + MAX_NAME_LENGTH + " characters");
		}
		return ValidationResult.ok();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static boolean exceedsLength(String value, int maxLength) {
		return value != null && value.length() > maxLength;
	}
}
