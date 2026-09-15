package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.KotEvent;
import com.khanabook.saas.repository.KotEventRepository;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.KotEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sync for the {@code kot_events} audit ledger.
 *
 * <p>Push is an idempotent native upsert keyed on the unique
 * {@code (restaurant_id, public_token, kot_revision)} tuple — the row is created
 * on first sight and later duplicates only ever OR their {@code isPrinted} flag,
 * so a stale upload (e.g. a re-created device replaying old revisions) can never
 * mark a printed ticket as unprinted. Pull streams whole rows newer than the
 * device's cursor so a recovered device can reconstruct ticket history.
 */
@Service
@RequiredArgsConstructor
public class KotEventSyncService {

	private final KotEventRepository kotEventRepository;
	private final JdbcTemplate jdbcTemplate;

	@Transactional
	public PushSyncResponse push(Long restaurantId, List<KotEventDTO> events) {
		if (events == null || events.isEmpty()) {
			return new PushSyncResponse(List.of(), List.of());
		}
		for (KotEventDTO e : events) {
			if (e.getPublicToken() == null || e.getPublicToken().isBlank()
					|| e.getKotRevision() == null || e.getKotRevision().isBlank()) {
				continue;
			}
			final String eventType = e.getEventType() == null ? "" : e.getEventType().toUpperCase();
			final boolean isPrinted = Boolean.TRUE.equals(e.getIsPrinted());
			final long now = System.currentTimeMillis();
			final long createdAt = e.getCreatedAt() == null ? now : e.getCreatedAt();
			final long updatedAt = e.getUpdatedAt() == null ? createdAt : e.getUpdatedAt();
			jdbcTemplate.update("""
					INSERT INTO kot_events (
					    restaurant_id, device_id, terminal_id, terminal_series,
					    public_token, bill_public_token, kot_revision, event_type,
					    item_snapshot_json, originating_device_id, origin_terminal_id,
					    origin_device_id, event_token, event_version, is_printed,
					    created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 0), ?, ?, ?)
					ON CONFLICT (restaurant_id, public_token, kot_revision)
					DO UPDATE SET
					    is_printed = kot_events.is_printed OR EXCLUDED.is_printed,
					    updated_at = LEAST(kot_events.updated_at, EXCLUDED.updated_at)
					""",
					restaurantId,
					e.getDeviceId() == null ? "" : e.getDeviceId(),
					e.getTerminalId(),
					e.getTerminalSeries(),
					e.getPublicToken(),
					e.getBillPublicToken(),
					e.getKotRevision(),
					eventType,
					e.getItemSnapshotJson(),
					e.getOriginatingDeviceId(),
					e.getOriginTerminalId(),
					e.getOriginDeviceId(),
					e.getEventToken(),
					e.getEventVersion(),
					isPrinted,
					createdAt,
					updatedAt);
		}
		var ids = events.stream().map(KotEventDTO::getId).toList();
		return new PushSyncResponse(ids, List.of());
	}

	@Transactional(readOnly = true)
	public List<KotEventDTO> pull(Long restaurantId, Long lastSyncTimestamp, String deviceId,
								  boolean ignoreDeviceId, Pageable pageable) {
		List<KotEvent> rows;
		if (ignoreDeviceId) {
			rows = kotEventRepository
					.findByRestaurantIdAndCreatedAtGreaterThan(restaurantId, lastSyncTimestamp, pageable)
					.getContent();
		} else {
			rows = kotEventRepository
					.findByRestaurantIdAndCreatedAtGreaterThanAndDeviceIdNot(
							restaurantId, lastSyncTimestamp, deviceId, pageable)
					.getContent();
		}
		return rows.stream().map(KotEventSyncService::toDto).toList();
	}

	private static KotEventDTO toDto(KotEvent e) {
		KotEventDTO dto = new KotEventDTO();
		dto.setId(e.getId());
		dto.setRestaurantId(e.getRestaurantId());
		dto.setDeviceId(e.getDeviceId());
		dto.setTerminalId(e.getTerminalId());
		dto.setTerminalSeries(e.getTerminalSeries());
		dto.setPublicToken(e.getPublicToken());
		dto.setBillPublicToken(e.getBillPublicToken());
		dto.setKotRevision(e.getKotRevision());
		dto.setEventType(e.getEventType());
		dto.setItemSnapshotJson(e.getItemSnapshotJson());
		dto.setOriginatingDeviceId(e.getOriginatingDeviceId());
		dto.setOriginTerminalId(e.getOriginTerminalId());
		dto.setOriginDeviceId(e.getOriginDeviceId());
		dto.setEventToken(e.getEventToken());
		dto.setEventVersion(e.getEventVersion());
		dto.setIsPrinted(e.getIsPrinted());
		dto.setCreatedAt(e.getCreatedAt());
		dto.setUpdatedAt(e.getUpdatedAt());
		return dto;
	}
}