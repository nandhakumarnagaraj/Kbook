package com.khanabook.saas.entity;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories", indexes = {
		@Index(name = "idx_categories_tenant_updated", columnList = "restaurant_id, updated_at"),
		@Index(name = "idx_categories_device", columnList = "restaurant_id, device_id, local_id") })
@Getter
@Setter
public class Category extends BaseSyncEntity {

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "is_veg", nullable = false)
	private Boolean isVeg = false;

	@Column(name = "sort_order")
	private Integer sortOrder;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;
}
