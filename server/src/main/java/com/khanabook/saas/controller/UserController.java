package com.khanabook.saas.controller;

import com.khanabook.saas.entity.User;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.service.OtpRateLimiter;
import com.khanabook.saas.service.UserService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.dto.payload.*;
import com.khanabook.saas.sync.service.GenericSyncService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import com.khanabook.saas.security.TenantContext;

@RestController
@RequestMapping("/sync/config/users")
@RequiredArgsConstructor
public class UserController {
	private static final Logger log = LoggerFactory.getLogger(UserController.class);

	private final UserService service;
	private final GenericSyncService genericSyncService;
	private final UserRepository userRepository;
	private final OtpRateLimiter otpRateLimiter;

	@PostMapping("/push")
	public ResponseEntity<PushSyncResponse> push(@RequestBody List<UserDTO> payload) {
		return ResponseEntity.ok(genericSyncService.handlePushSync(TenantContext.getCurrentTenant(),
				SyncMapper.mapToEntityList(payload, User.class), userRepository));
	}

	@GetMapping("/pull")
	public ResponseEntity<List<UserDTO>> pull(@RequestParam Long lastSyncTimestamp, @RequestParam String deviceId,
			@RequestParam(defaultValue = "false") boolean ignoreDeviceId) {
		return ResponseEntity.ok(SyncMapper.mapList(service.pullData(TenantContext.getCurrentTenant(), lastSyncTimestamp, deviceId, ignoreDeviceId), UserDTO.class));
	}

	@PostMapping("/update-mobile")
	public ResponseEntity<?> confirmMobileNumberUpdate(@Valid @RequestBody UpdateMobileRequest request) {
		service.confirmMobileNumberUpdate(TenantContext.getCurrentTenant(), request.getNewMobileNumber(), request.getOtp());
		return ResponseEntity.ok(Map.of("message", "Mobile number updated successfully."));
	}

	@PostMapping("/update-mobile/request")
	public ResponseEntity<?> requestMobileNumberUpdateOtp(@Valid @RequestBody UpdateMobileOtpRequest request) {
		if (!otpRateLimiter.tryConsume(request.getNewMobileNumber())) {
			log.warn("OTP rate limit exceeded for update-mobile phone={}***", request.getNewMobileNumber().substring(0, 3));
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		service.requestMobileNumberUpdateOtp(TenantContext.getCurrentTenant(), request.getNewMobileNumber());
		return ResponseEntity.ok(Map.of("message", "OTP sent successfully."));
	}
}
