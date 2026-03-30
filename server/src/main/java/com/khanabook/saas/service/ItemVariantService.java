package com.khanabook.saas.service;

import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import java.util.List;

public interface ItemVariantService {

	PushSyncResponse pushData(Long tenantId, List<ItemVariant> payload);

	List<ItemVariant> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);
}
