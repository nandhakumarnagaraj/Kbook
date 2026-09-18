package com.khanabook.saas.feature.menu.service;

import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import java.util.List;

public interface ItemVariantService {

	PushSyncResponse pushData(Long tenantId, List<ItemVariant> payload);

	List<ItemVariant> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);
}
