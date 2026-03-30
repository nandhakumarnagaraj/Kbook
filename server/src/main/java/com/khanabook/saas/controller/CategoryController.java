package com.khanabook.saas.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.khanabook.saas.entity.Category;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.CategoryService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.service.GenericSyncService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sync/menu/categories")
@RequiredArgsConstructor
public class CategoryController {
	private final CategoryService service;
	private final GenericSyncService genericSyncService;
	private final CategoryRepository categoryRepository;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<CategoryDTO> payload) {
		return ResponseEntity.ok(genericSyncService.handlePushSync(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, Category.class), categoryRepository));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<CategoryDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), CategoryDTO.class));
	}
}
