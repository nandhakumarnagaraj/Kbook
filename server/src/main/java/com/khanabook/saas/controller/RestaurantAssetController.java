package com.khanabook.saas.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.AssetStorageService;
import com.khanabook.saas.service.AssetStorageService.AssetUploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantAssetController {

	private final AssetStorageService assetStorageService;

	@PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> uploadLogo(@RequestPart("file") MultipartFile file) {
		AssetUploadResult result = assetStorageService.uploadLogo(TenantContext.getCurrentTenant(), file);
		return ResponseEntity.ok(Map.of("logoUrl", result.url(), "logoVersion", result.version()));
	}

	@DeleteMapping("/logo")
	public ResponseEntity<Void> deleteLogo() {
		assetStorageService.deleteLogo(TenantContext.getCurrentTenant());
		return ResponseEntity.noContent().build();
	}
}
