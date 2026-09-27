package com.khanabook.saas.feature.menu.data;

import com.khanabook.saas.feature.sync.data.BaseSyncEntity;
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

	@Column(name = "image_url")
	private String imageUrl;

	@Column(name = "image_version", nullable = false)
	private Integer imageVersion = 0;

	@Column(name = "is_available", nullable = false)
	private Boolean isAvailable = true;

	@Column(name = "current_stock", columnDefinition = "NUMERIC(12,4)")
	private java.math.BigDecimal currentStock;

	@Column(name = "low_stock_threshold", columnDefinition = "NUMERIC(12,4)")
	private java.math.BigDecimal lowStockThreshold;

	/**
	 * True when this row is a grouping container for {@link ItemVariant} rows and its
	 * {@code basePrice} is a derived display value rather than a price a customer can
	 * actually pay.
	 *
	 * <p>Deliberately explicit. Pricing mode used to be inferred from a blank or zero
	 * {@code basePrice}, which made every caller guess and is what crashed the menu save
	 * dialog. See docs/design/MENU_ITEM_MODEL_INDIA_FIT_GAP.md section 6.
	 *
	 * <p>{@code basePrice} stays NOT NULL on purpose: every competitor in the reference
	 * set keeps a non-null parent price and puts the nullability on the cart line instead.
	 */
	@Column(name = "has_variants", nullable = false)
	private Boolean hasVariants = false;

	/**
	 * Permission revision the acting user held on-device when this row was
	 * created/edited (P1). Nullable: older clients that do not stamp it fall back
	 * to the grant-only gate in {@link com.khanabook.saas.core.security.authz.MenuPushAuthorizer}.
	 */
	@Column(name = "permission_revision_at_creation")
	private Long permissionRevisionAtCreation;

	@Transient
	private Boolean overwriteExisting = false;

	/**
	 * Comma-separated list of menu fields this device actually changed (field-level
	 * merge). Not persisted — sync-only. Null/blank = legacy whole-record LWW.
	 */
	@Transient
	private String changedFields;

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
