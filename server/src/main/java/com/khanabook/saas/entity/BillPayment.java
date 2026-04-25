package com.khanabook.saas.entity;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bill_payments", uniqueConstraints = {
		@UniqueConstraint(name = "bill_payments_restaurant_id_device_id_local_id_key", columnNames = {
				"restaurant_id", "device_id", "local_id" }) }, indexes = {
		@Index(name = "idx_bill_payments_tenant_updated", columnList = "restaurant_id, updated_at"),
		@Index(name = "idx_bill_payments_device", columnList = "restaurant_id, device_id, local_id") })
@Getter
@Setter
public class BillPayment extends BaseSyncEntity {

	@Column(name = "bill_id", nullable = false)
	private Long billId;

	@Column(name = "server_bill_id")
	private Long serverBillId;

	@jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "server_bill_id", insertable = false, updatable = false)
	private Bill bill;

	@Column(name = "payment_mode", nullable = false)
	private String paymentMode;

	@Column(name = "amount", columnDefinition = "NUMERIC(12,2)", nullable = false)
	private java.math.BigDecimal amount;

	// Easebuzz / gateway fields. Null for manually-confirmed payments.
	@Column(name = "gateway_txn_id")
	private String gatewayTxnId;

	@Column(name = "gateway_status")
	private String gatewayStatus;

	@Column(name = "verified_by", columnDefinition = "TEXT DEFAULT 'manual'")
	private String verifiedBy;
}
