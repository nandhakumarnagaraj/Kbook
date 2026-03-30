package com.khanabook.saas.sync.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseSyncEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty("serverId")
	private Long id;

	@Column(name = "local_id", nullable = false)
	@JsonProperty("localId")
	@JsonAlias({ "id", "localId" })
	private Long localId;

	@Column(name = "device_id", nullable = false)
	@JsonProperty("deviceId")
	private String deviceId;

	@Column(name = "restaurant_id", nullable = false)
	@JsonProperty("restaurantId")
	private Long restaurantId;

	@Column(name = "updated_at", nullable = false)
	@JsonProperty("updatedAt")
	private Long updatedAt;

	@Column(name = "is_deleted", nullable = false)
	@JsonProperty("isDeleted")
	private Boolean isDeleted = false;

	@Column(name = "server_updated_at", nullable = false)
	@JsonProperty("serverUpdatedAt")
	private Long serverUpdatedAt = 0L;

	@Column(name = "created_at", nullable = false)
	@JsonProperty("createdAt")
	private Long createdAt;
}
