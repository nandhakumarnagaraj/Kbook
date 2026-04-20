package com.khanabook.saas.entity;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "menuitems", indexes = {
		@Index(name = "idx_menuitems_tenant_updated", columnList = "restaurant_id, updated_at"),
		@Index(name = "idx_menuitems_device", columnList = "restaurant_id, device_id, local_id") })
@Getter
@Setter
public class MenuItem extends BaseSyncEntity {

	@Column(name = "category_id", nullable = false)
	private Long categoryId;

	@Column(name = "server_category_id")
	private Long serverCategoryId;

	@jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
	@jakarta.persistence.JoinColumn(name = "server_category_id", insertable = false, updatable = false)
	private Category category;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "base_price", columnDefinition = "NUMERIC(12,2)", nullable = false)
	private java.math.BigDecimal basePrice;

	@Column(name = "food_type")
	private String foodType;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "barcode")
	private String barcode;

	@Column(name = "is_available", nullable = false)
	private Boolean isAvailable = true;

	@Column(name = "current_stock", columnDefinition = "NUMERIC(12,4)")
	private java.math.BigDecimal currentStock;

	@Column(name = "low_stock_threshold", columnDefinition = "NUMERIC(12,4)")
	private java.math.BigDecimal lowStockThreshold;

	@Transient
	private Boolean overwriteExisting = false;

	public enum StockStatus {
		IN_STOCK, RUNNING_LOW, OUT_OF_STOCK
	}

	public StockStatus getStockStatus() {
		if (currentStock == null || currentStock.compareTo(java.math.BigDecimal.ZERO) <= 0) {
			return StockStatus.OUT_OF_STOCK;
		}
		if (lowStockThreshold != null && currentStock.compareTo(lowStockThreshold) <= 0) {
			return StockStatus.RUNNING_LOW;
		}
		return StockStatus.IN_STOCK;
	}
}
