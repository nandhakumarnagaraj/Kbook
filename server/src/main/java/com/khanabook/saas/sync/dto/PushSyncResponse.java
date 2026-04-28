package com.khanabook.saas.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PushSyncResponse {
	private List<Long> successfulLocalIds;
	private List<Long> failedLocalIds;
	private Map<Long, Long> localToServerIdMap;

	public PushSyncResponse(List<Long> successfulLocalIds, List<Long> failedLocalIds) {
		this.successfulLocalIds = successfulLocalIds;
		this.failedLocalIds = failedLocalIds;
		this.localToServerIdMap = new java.util.HashMap<>();
	}
}
