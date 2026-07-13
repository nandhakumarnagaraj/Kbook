package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Server-owned terminal registration for a restaurant.
 *
 * Each billing device is assigned a permanent, restaurant-unique
 * {@code terminalSeries} on first online activation (PLAN §5). Invoice-number
 * allocation is gated on the existence of the terminal's series so that a
 * device cannot mint invoice numbers before it has been provisioned.
 *
 * This is not a synced client entity; it is allocation/registration state that
 * only the server writes.
 */
@Entity
@Table(name = "restaurant_terminal", uniqueConstraints = {
		@UniqueConstraint(name = "ux_restaurant_terminal_series", columnNames = { "restaurant_id", "terminal_series" })
}, indexes = {
		@Index(name = "idx_restaurant_terminal_restaurant", columnList = "restaurant_id")
})
@Getter
@Setter
public class RestaurantTerminal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "restaurant_id", nullable = false)
	private Long restaurantId;

	@Column(name = "terminal_series", nullable = false)
	private String terminalSeries;

	@Column(name = "terminal_name")
	private String terminalName;

	@Column(name = "device_id")
	private String deviceId;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Column(name = "created_at")
	private Long createdAt;

	@Column(name = "updated_at")
	private Long updatedAt;
}
