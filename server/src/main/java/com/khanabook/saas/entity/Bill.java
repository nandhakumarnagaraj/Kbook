package com.khanabook.saas.entity;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bills", indexes = { @Index(name = "idx_bills_tenant_updated", columnList = "restaurant_id, updated_at"),
		@Index(name = "idx_bills_device", columnList = "restaurant_id, device_id, local_id") })
@Getter
@Setter
public class Bill extends BaseSyncEntity {

	@Column(name = "daily_order_id", nullable = false)
	private Long dailyOrderId;

	@Column(name = "daily_order_display")
	private String dailyOrderDisplay;

	@Column(name = "lifetime_order_id", nullable = false)
	private Long lifetimeOrderId;

	@Column(name = "order_type", nullable = false)
	private String orderType;

	@Column(name = "customer_name")
	private String customerName;

	@Column(name = "customer_whatsapp")
	private String customerWhatsapp;

	@Column(name = "subtotal", columnDefinition = "NUMERIC(12,2)", nullable = false)
	private java.math.BigDecimal subtotal;

	@Column(name = "gst_percentage", columnDefinition = "NUMERIC(12,2)")
	private java.math.BigDecimal gstPercentage;

	@Column(name = "cgst_amount", columnDefinition = "NUMERIC(12,2)")
	private java.math.BigDecimal cgstAmount;

	@Column(name = "sgst_amount", columnDefinition = "NUMERIC(12,2)")
	private java.math.BigDecimal sgstAmount;

	@Column(name = "custom_tax_amount", columnDefinition = "NUMERIC(12,2)")
	private java.math.BigDecimal customTaxAmount;

	@Column(name = "total_amount", columnDefinition = "NUMERIC(12,2)", nullable = false)
	private java.math.BigDecimal totalAmount;

	@Column(name = "payment_mode", nullable = false)
	private String paymentMode;

	@Column(name = "part_amount_1", columnDefinition = "NUMERIC(12,2)")
	private java.math.BigDecimal partAmount1;

	@Column(name = "part_amount_2", columnDefinition = "NUMERIC(12,2)")
	private java.math.BigDecimal partAmount2;

	@Column(name = "payment_status", nullable = false)
	private String paymentStatus;

	@Column(name = "order_status", nullable = false)
	private String orderStatus;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "paid_at")
	private Long paidAt;

	@Column(name = "last_reset_date", nullable = false)
	private String lastResetDate;
}
