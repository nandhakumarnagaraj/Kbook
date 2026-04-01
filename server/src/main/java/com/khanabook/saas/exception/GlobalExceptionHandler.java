package com.khanabook.saas.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationException(
			MethodArgumentNotValidException e, HttpServletRequest request) {
		Map<String, String> fieldErrors = new HashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));
		return ResponseEntity.badRequest().body(Map.of(
				"error", "Validation failed",
				"fields", fieldErrors,
				"path", request.getRequestURI()
		));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(
			IllegalArgumentException e, HttpServletRequest request) {
		log.warn("Bad request [{}]: {}", request.getRequestURI(), e.getMessage());
		return ResponseEntity.badRequest().body(Map.of(
				"error", e.getMessage(),
				"path", request.getRequestURI()
		));
	}

	@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
			org.springframework.security.access.AccessDeniedException e, HttpServletRequest request) {
		log.warn("Access denied [{}]: {}", request.getRequestURI(), e.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
				"error", "Access denied",
				"path", request.getRequestURI()
		));
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<Map<String, Object>> handleOptimisticLock(
			ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
		log.warn("Optimistic lock conflict [{}]: {}", request.getRequestURI(), e.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
				"error", "This record was modified by another device. Please sync and retry.",
				"path", request.getRequestURI()
		));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGeneralException(
			Exception e, HttpServletRequest request) {
		String errorId = UUID.randomUUID().toString().substring(0, 8);
		log.error("Unhandled exception [errorId={}] [{}]", errorId, request.getRequestURI(), e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
				"error", "An unexpected error occurred. Please try again.",
				"errorId", errorId,
				"path", request.getRequestURI()
		));
	}
}
