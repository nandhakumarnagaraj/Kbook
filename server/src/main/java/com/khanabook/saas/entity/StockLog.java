package com.khanabook.saas.entity;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "stock_logs", indexes = {
		@Index(name = "idx_stock_logs_tenant_updated", columnList = "restaurant_id, updated_at"),
		@Index(name = "idx_stock_logs_device", columnList = "restaurant_id, device_id, local_id") })
@Getter
@Setter
public class StockLog extends BaseSyncEntity {

	@Column(name = "menu_item_id", nullable = false)
	private Long menuItemId;

	@Column(name = "server_menu_item_id")
	private Long serverMenuItemId;

	@jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "server_menu_item_id", insertable = false, updatable = false)
	private MenuItem menuItem;

	@Column(name = "variant_id")
	private Long variantId;

	@Column(name = "server_variant_id")
	private Long serverVariantId;

	@jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "server_variant_id", insertable = false, updatable = false)
	private ItemVariant itemVariant;

	@Column(name = "delta", columnDefinition = "NUMERIC(12,4)", nullable = false)
	private java.math.BigDecimal delta;

	@Column(name = "reason", nullable = false)
	private String reason;
}
