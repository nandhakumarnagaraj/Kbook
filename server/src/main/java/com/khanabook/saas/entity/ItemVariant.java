package com.khanabook.saas.entity;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "itemvariants", indexes = {
		@Index(name = "idx_itemvariants_tenant_updated", columnList = "restaurant_id, updated_at"),
		@Index(name = "idx_itemvariants_device", columnList = "restaurant_id, device_id, local_id") })
@Getter
@Setter
public class ItemVariant extends BaseSyncEntity {

	@Column(name = "menu_item_id", nullable = false)
	private Long menuItemId;

	@Column(name = "server_menu_item_id")
	private Long serverMenuItemId;

	@jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "server_menu_item_id", insertable = false, updatable = false)
	private MenuItem menuItem;

	@Column(name = "variant_name", nullable = false)
	private String variantName;

	@Column(name = "price", columnDefinition = "NUMERIC(12,2)", nullable = false)
	private java.math.BigDecimal price;

	@Column(name = "is_available", nullable = false)
	private Boolean isAvailable = true;

	@Column(name = "sort_order")
	private Integer sortOrder;

	@Column(name = "current_stock", columnDefinition = "NUMERIC(12,4)")
	private java.math.BigDecimal currentStock;

	@Column(name = "low_stock_threshold", columnDefinition = "NUMERIC(12,4)")
	private java.math.BigDecimal lowStockThreshold;

	public MenuItem.StockStatus getStockStatus() {
		if (currentStock == null || currentStock.compareTo(java.math.BigDecimal.ZERO) <= 0) {
			return MenuItem.StockStatus.OUT_OF_STOCK;
		}
		if (lowStockThreshold != null && currentStock.compareTo(lowStockThreshold) <= 0) {
			return MenuItem.StockStatus.RUNNING_LOW;
		}
		return MenuItem.StockStatus.IN_STOCK;
	}
}
