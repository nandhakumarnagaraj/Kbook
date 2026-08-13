package com.khanabook.saas.service;

import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import java.util.List;

public interface MenuItemService {

	PushSyncResponse pushData(Long tenantId, List<MenuItem> payload);

	List<MenuItem> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	void markItemAsUnavailable(Long tenantId, Long menuItemId);

	void markAllItemsAsUnavailable(Long tenantId);

	void updateExistingMenuItems(Long tenantId, List<MenuItem> itemsToUpdate);
}
