package com.khanabook.saas.feature.menu.service;

import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import java.util.List;

public interface CategoryService {

	PushSyncResponse pushData(Long tenantId, List<Category> payload);

	List<Category> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);
}
