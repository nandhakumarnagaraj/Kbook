package com.khanabook.saas.feature.menu.service;

import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import java.util.List;

public interface MenuItemService {

	PushSyncResponse pushData(Long tenantId, List<MenuItem> payload);

	List<MenuItem> pullData(Long tenantId, Long lastSyncTimestamp, String deviceId, boolean ignoreDeviceId);

	void markItemAsUnavailable(Long tenantId, Long menuItemId);
	void markItemAsAvailable(Long tenantId, Long menuItemId);

	void markAllItemsAsUnavailable(Long tenantId);

	void updateExistingMenuItems(Long tenantId, List<MenuItem> itemsToUpdate);
}
