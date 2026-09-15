package com.khanabook.saas.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Append-only ledger of every kitchen-facing KOT revision, keyed by
 * (restaurant_id, public_token, kot_revision). Uploaded idempotently by the
 * originating device; pulled to restored/sibling terminals so kitchen-print
 * history survives device replacement and is available to a future KDS.
 *
 * {@code publicToken} is the owning bill's public token; {@code kotRevision}
 * matches the device's per-bill counter. {@code isPrinted} is monotonic and is
 * OR-merged on upsert so a stale upload can never flip a printed event back.
 */
@Entity
@Table(name = "kot_events", indexes = {
		@Index(name = "idx_kot_events_tenant_device_created", columnList = "restaurant_id, device_id, created_at"),
		@Index(name = "idx_kot_events_tenant_bill", columnList = "restaurant_id, bill_public_token")
})
@Getter
@Setter
public class KotEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty("serverId")
	private Long id;

	@Column(name = "restaurant_id", nullable = false)
	@JsonProperty("restaurantId")
	private Long restaurantId;

	@Column(name = "device_id", nullable = false)
	@JsonProperty("deviceId")
	private String deviceId;

	@Column(name = "terminal_id")
	@JsonProperty("terminalId")
	private String terminalId;

	@Column(name = "terminal_series")
	@JsonProperty("terminalSeries")
	private String terminalSeries;

	@Column(name = "public_token", nullable = false)
	@JsonProperty("publicToken")
	private String publicToken;

	@Column(name = "bill_public_token")
	@JsonProperty("billPublicToken")
	private String billPublicToken;

	@Column(name = "kot_revision", nullable = false)
	@JsonProperty("kotRevision")
	private String kotRevision;

	@Column(name = "event_type", nullable = false)
	@JsonProperty("eventType")
	private String eventType;

	@Column(name = "item_snapshot_json")
	@JsonProperty("itemSnapshotJson")
	private String itemSnapshotJson;

	@Column(name = "originating_device_id")
	@JsonProperty("originatingDeviceId")
	private String originatingDeviceId;

	@Column(name = "origin_terminal_id")
	@JsonProperty("originTerminalId")
	private String originTerminalId;

	@Column(name = "origin_device_id")
	@JsonProperty("originDeviceId")
	private String originDeviceId;

	@Column(name = "event_token")
	@JsonProperty("eventToken")
	private String eventToken;

	@Column(name = "event_version", nullable = false)
	@JsonProperty("eventVersion")
	private Long eventVersion = 0L;

	@Column(name = "is_printed", nullable = false)
	@JsonProperty("isPrinted")
	private Boolean isPrinted = false;

	@Column(name = "created_at", nullable = false)
	@JsonProperty("createdAt")
	private Long createdAt;

	@Column(name = "updated_at", nullable = false)
	@JsonProperty("updatedAt")
	private Long updatedAt;
}