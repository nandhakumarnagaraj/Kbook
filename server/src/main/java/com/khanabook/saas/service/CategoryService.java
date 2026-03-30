package com.khanabook.saas.service;

import com.khanabook.saas.entity.Category;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import java.util.List;

public interface CategoryService {

	PushSyncResponse pushData(Long tenantId, List<Category> payload);

	List<Category> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);
}
