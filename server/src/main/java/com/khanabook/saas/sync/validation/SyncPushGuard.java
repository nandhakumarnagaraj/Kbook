package com.khanabook.saas.sync.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

public final class SyncPushGuard {

	private static final int MAX_PUSH_BATCH_SIZE = 200;

	private SyncPushGuard() {}

	public static <T> void validateBatchSize(List<T> payload) {
		if (payload == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		if (payload.size() > MAX_PUSH_BATCH_SIZE) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
					"Maximum " + MAX_PUSH_BATCH_SIZE + " records per push request, got " + payload.size());
		}
	}
}
