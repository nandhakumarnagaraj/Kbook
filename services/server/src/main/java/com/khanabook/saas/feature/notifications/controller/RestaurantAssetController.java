package com.khanabook.saas.feature.notifications.controller;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.menu.service.AssetStorageService;
import com.khanabook.saas.feature.menu.service.AssetStorageService.AssetUploadResult;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
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
	private final SubMerchantService subMerchantService;

	@PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> uploadLogo(@RequestPart("file") MultipartFile file) {
		AssetUploadResult result = assetStorageService.uploadLogo(TenantContext.getCurrentTenant(), file);
		return ResponseEntity.ok(Map.of("logoUrl", result.url(), "logoVersion", result.version()));
	}

	/**
	 * Legacy upload path, kept for app builds that still ship the bytes. The response carries the
	 * hosted KYC portal URL, which is an access credential, so it is owner-only — otherwise a
	 * counter terminal could spend a tiny upload to obtain the merchant's KYC session link.
	 */
	@PostMapping(value = "/kyc-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> uploadKycDocument(
			@RequestParam("type") String docType,
			@RequestParam(value = "proofType", required = false) String proofType,
			@RequestPart("file") MultipartFile file) {
		if (!"OWNER".equals(TenantContext.getCurrentRole())) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "Restaurant owner access required");
		}
		Map<String, Object> result = subMerchantService.submitKycDocument(
				TenantContext.getCurrentTenant(), docType, proofType, file);
		return ResponseEntity.ok(result);
	}

	@DeleteMapping("/logo")
	public ResponseEntity<Void> deleteLogo() {
		assetStorageService.deleteLogo(TenantContext.getCurrentTenant());
		return ResponseEntity.noContent().build();
	}
}

