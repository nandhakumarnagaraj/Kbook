package com.khanabook.saas.entity;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bill_items", indexes = {
		@Index(name = "idx_bill_items_tenant_updated", columnList = "restaurant_id, updated_at"),
		@Index(name = "idx_bill_items_device", columnList = "restaurant_id, device_id, local_id") })
@Getter
@Setter
public class BillItem extends BaseSyncEntity {

	@Column(name = "bill_id", nullable = false)
	private Long billId;

	@Column(name = "server_bill_id")
	private Long serverBillId;

	@jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "server_bill_id", insertable = false, updatable = false)
	private Bill bill;

	@Column(name = "menu_item_id", nullable = false)
	private Long menuItemId;

	@Column(name = "server_menu_item_id")
	private Long serverMenuItemId;

	@jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "server_menu_item_id", insertable = false, updatable = false)
	private MenuItem menuItem;

	@Column(name = "item_name", nullable = false)
	private String itemName;

	@Column(name = "variant_id")
	private Long variantId;

	@Column(name = "server_variant_id")
	private Long serverVariantId;

	@Column(name = "variant_name")
	private String variantName;

	@Column(name = "price", columnDefinition = "NUMERIC(12,2)", nullable = false)
	private java.math.BigDecimal price;

	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	@Column(name = "item_total", columnDefinition = "NUMERIC(12,2)", nullable = false)
	private java.math.BigDecimal itemTotal;

	@Column(name = "special_instruction")
	private String specialInstruction;
}
